package com.swade.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class StudyService {

    private static final Logger log = LoggerFactory.getLogger(StudyService.class);

    private final RabbitTemplate rabbitTemplate;
    private final MriProcessService mriProcessService;
    private final MinioService minioService;
    private final EstudioRepository estudioRepository;
    private final ResultadoRepository resultadoRepository;
    private final UsuarioRepository usuarioRepository;
    private final EventoRepository eventoRepository;
    private final ObjectMapper objectMapper;

    public StudyService(RabbitTemplate rabbitTemplate,
                        MriProcessService mriProcessService,
                        MinioService minioService,
                        EstudioRepository estudioRepository,
                        ResultadoRepository resultadoRepository,
                        UsuarioRepository usuarioRepository,
                        EventoRepository eventoRepository,
                        ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.mriProcessService = mriProcessService;
        this.minioService = minioService;
        this.estudioRepository = estudioRepository;
        this.resultadoRepository = resultadoRepository;
        this.usuarioRepository = usuarioRepository;
        this.eventoRepository = eventoRepository;
        this.objectMapper = objectMapper;
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

    // ── Async processing (called by RabbitMQ consumer) ──────────────────

    public void processJob(Long studyId, String filePath) {
        EstudioEntity estudio = estudioRepository.findById(studyId).orElse(null);
        if (estudio == null) {
            log.warn("Study {} not found in DB, skipping", studyId);
            return;
        }

        estudio.setStatus(EstudioStatusMapper.toDb(StudyStatus.PROCESSING));
        estudioRepository.save(estudio);
        logEvent(estudio, "PROCESSING", null);

        try {
            byte[] original = minioService.downloadFile(filePath);
            if (original == null || original.length == 0) {
                throw new IllegalStateException("Original file not found in MinIO");
            }

            var simResult = mriProcessService.simulateProcessing(original, estudio.getOriginalFilename());

            JsonNode predictionJson = objectMapper.valueToTree(
                    Map.of("prediction", simResult.prediction()));

            ResultadoEntity resultado = new ResultadoEntity();
            resultado.setEstudio(estudio);
            resultado.setPrediction(predictionJson);
            resultado.setHeatmapPath("/estudios/" + studyId + "/resultado/heatmap");
            resultado.setReportPath("/estudios/" + studyId + "/reporte");
            resultadoRepository.save(resultado);

            estudio.setResult(predictionJson);
            estudio.setStatus(EstudioStatusMapper.toDb(StudyStatus.COMPLETED));
            estudioRepository.save(estudio);

            logEvent(estudio, "COMPLETED", predictionJson);
            log.info("Study {} processed successfully", studyId);

        } catch (Exception e) {
            log.error("Study {} processing failed: {}", studyId, e.getMessage(), e);
            estudio.setStatus(EstudioStatusMapper.toDb(StudyStatus.FAILED));
            estudio.setErrorMessage(e.getMessage());
            estudioRepository.save(estudio);
            logEvent(estudio, "FAILED", objectMapper.valueToTree(Map.of("error", e.getMessage())));
        }
    }

    // ── Mocked artifact generation ──────────────────────────────────────

    public byte[] generateMockedHeatmapBytes() {
        return MriProcessService.getDummyNiftiBytes();
    }

    public byte[] generateMockedReportPdf(Long studyId) {
        return createDummyPdfBytes(studyId);
    }

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

    private static byte[] createDummyPdfBytes(Long studyId) {
        String body = "%PDF-1.4\n"
                + "1 0 obj<< /Type /Catalog /Pages 2 0 R >>endobj\n"
                + "2 0 obj<< /Type /Pages /Kids [3 0 R] /Count 1 >>endobj\n"
                + "3 0 obj<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                + "/Contents 4 0 R /Resources<< >> >>endobj\n"
                + "4 0 obj<< /Length 44 >>stream\n"
                + "BT /F1 18 Tf 72 720 Td (Study " + studyId + ") Tj ET\n"
                + "endstream endobj\n"
                + "xref\n0 5\n0000000000 65535 f \n"
                + "trailer<< /Root 1 0 R /Size 5 >>\n"
                + "startxref\n0\n%%EOF\n";
        return body.getBytes(StandardCharsets.US_ASCII);
    }
}
