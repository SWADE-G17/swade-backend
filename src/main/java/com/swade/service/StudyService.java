package com.swade.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.swade.entity.EstudioEntity;
import com.swade.entity.EventoEntity;
import com.swade.entity.ResultadoEntity;
import com.swade.entity.UsuarioEntity;
import com.swade.messaging.RabbitMqConfig;
import com.swade.messaging.StudyJobMessage;
import com.swade.model.EstudioStatusMapper;
import com.swade.model.StudyStatus;
import com.swade.repository.EstudioRepository;
import com.swade.repository.EventoRepository;
import com.swade.repository.ResultadoRepository;
import com.swade.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class StudyService {

    private static final Logger log = LoggerFactory.getLogger(StudyService.class);

    private final RabbitTemplate rabbitTemplate;
    private final MinioService minioService;
    private final EstudioRepository estudioRepository;
    private final ResultadoRepository resultadoRepository;
    private final UsuarioRepository usuarioRepository;
    private final EventoRepository eventoRepository;

    public StudyService(RabbitTemplate rabbitTemplate,
                        MinioService minioService,
                        EstudioRepository estudioRepository,
                        ResultadoRepository resultadoRepository,
                        UsuarioRepository usuarioRepository,
                        EventoRepository eventoRepository) {
        this.rabbitTemplate = rabbitTemplate;
        this.minioService = minioService;
        this.estudioRepository = estudioRepository;
        this.resultadoRepository = resultadoRepository;
        this.usuarioRepository = usuarioRepository;
        this.eventoRepository = eventoRepository;
    }

    // ── Create ──────────────────────────────────────────────────────────

    public EstudioEntity createAndEnqueue(UUID usuarioId, byte[] niftiBytes, String originalFilename) throws Exception {
        UsuarioEntity usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalStateException("Usuario not found: " + usuarioId));

        String uploadKey = UUID.randomUUID().toString();
        String mriPath = minioService.uploadFile(niftiBytes, originalFilename, uploadKey);

        EstudioEntity estudio = new EstudioEntity();
        estudio.setUsuario(usuario);
        estudio.setMriPath(mriPath);
        estudio.setOriginalFilename(originalFilename);
        estudio.setStatus(EstudioStatusMapper.toDb(StudyStatus.QUEUED));
        estudio = estudioRepository.save(estudio);

        logEvent(estudio, "CREATED", null);

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.MRI_PROCESSING_QUEUE,
                new StudyJobMessage(estudio.getId(), mriPath));

        logEvent(estudio, "QUEUED", null);
        return estudio;
    }

    // ── Queries ─────────────────────────────────────────────────────────

    public List<EstudioEntity> listByUsuario(UUID usuarioId) {
        return estudioRepository.findByUsuarioIdOrderByCreatedAtDesc(usuarioId);
    }

    public Optional<EstudioEntity> getByIdAndUsuario(Long id, UUID usuarioId) {
        return estudioRepository.findByIdAndUsuarioId(id, usuarioId);
    }

    public Optional<ResultadoEntity> getResultado(Long estudioId) {
        return resultadoRepository.findByEstudioId(estudioId);
    }

    public List<String> listMinioFiles() throws Exception {
        return minioService.listFiles();
    }

    // ── MinIO artifact downloads ───────────────────────────────────────

    /**
     * Cheap metadata-only lookup for an artifact: returns size + ETag without
     * opening a body stream. Use before
     * {@link #openArtifactStream(String, String)} when you need to evaluate
     * {@code If-None-Match} or validate a {@code Range} request.
     */
    public MinioObjectInfo statArtifact(String storedPath, String defaultBucket) throws Exception {
        BucketKey bk = parseStoredPath(storedPath, defaultBucket);
        return minioService.statObject(bk.bucket(), bk.objectName());
    }

    /**
     * Open a streaming handle to an artifact stored in MinIO.
     *
     * <p>{@code storedPath} is the value persisted by the Python worker in
     * {@code resultado.heatmap_path} or {@code resultado.orig_path}. It is
     * formatted as {@code "<bucket>/<object-key>"} (e.g.
     * {@code "heatmaps/123_heatmap.nii.gz"}). When {@code storedPath} does
     * not contain a {@code /}, the {@code defaultBucket} is used and the
     * whole value is treated as the object key.</p>
     *
     * @throws MinioObjectNotFoundException if the path is malformed or the
     *         object/bucket does not exist in MinIO
     */
    public StreamedMinioObject openArtifactStream(String storedPath, String defaultBucket) throws Exception {
        BucketKey bk = parseStoredPath(storedPath, defaultBucket);
        return minioService.openObjectStream(bk.bucket(), bk.objectName());
    }

    /**
     * Range-limited variant of {@link #openArtifactStream(String, String)}: returns a stream
     * containing exactly {@code length} bytes starting at {@code offset}. Used to serve
     * HTTP 206 Partial Content responses for progressive Niivue loading.
     */
    public StreamedMinioObject openArtifactStream(String storedPath, String defaultBucket,
                                                  long offset, long length) throws Exception {
        BucketKey bk = parseStoredPath(storedPath, defaultBucket);
        return minioService.openObjectStream(bk.bucket(), bk.objectName(), offset, length);
    }

    private static BucketKey parseStoredPath(String storedPath, String defaultBucket) {
        if (storedPath == null || storedPath.isBlank()) {
            throw new MinioObjectNotFoundException(defaultBucket, String.valueOf(storedPath), null);
        }
        String bucket;
        String objectName;
        int slash = storedPath.indexOf('/');
        if (slash < 0) {
            bucket = defaultBucket;
            objectName = storedPath;
        } else {
            bucket = storedPath.substring(0, slash);
            objectName = storedPath.substring(slash + 1);
        }
        if (bucket == null || bucket.isBlank() || objectName == null || objectName.isBlank()) {
            throw new MinioObjectNotFoundException(bucket, objectName, null);
        }
        return new BucketKey(bucket, objectName);
    }

    private record BucketKey(String bucket, String objectName) {}

    // ── Helpers ─────────────────────────────────────────────────────────

    private void logEvent(EstudioEntity estudio, String eventType, JsonNode eventData) {
        try {
            EventoEntity evento = new EventoEntity();
            evento.setEstudio(estudio);
            evento.setEventType(eventType);
            evento.setEventData(eventData);
            eventoRepository.save(evento);
        } catch (Exception e) {
            log.warn("Failed to log event {} for study {}: {}", eventType, estudio.getId(), e.getMessage());
        }
    }

}
