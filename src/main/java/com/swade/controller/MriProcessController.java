package com.swade.controller;

import com.swade.dto.*;
import com.swade.entity.EstudioEntity;
import com.swade.entity.ResultadoEntity;
import com.swade.model.EstudioStatusMapper;

import com.swade.model.StudyStatus;
import com.swade.security.AuthService;
import com.swade.service.MinioObjectInfo;
import com.swade.service.MinioObjectNotFoundException;
import com.swade.service.StreamedMinioObject;
import com.swade.service.StudyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@RestController
@RequestMapping("/estudios")
@Tag(name = "Estudios", description = "Carga de MRI, estado del estudio, resultados y reporte")
public class MriProcessController {

    private static final MediaType APPLICATION_GZIP = MediaType.parseMediaType("application/gzip");

    private final StudyService studyService;
    private final AuthService authService;
    private final String heatmapBucket;

    public MriProcessController(StudyService studyService,
                                AuthService authService,
                                @Value("${mri.minio.heatmap-bucket:heatmaps}") String heatmapBucket) {
        this.studyService = studyService;
        this.authService = authService;
        this.heatmapBucket = heatmapBucket;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Crear estudio",
            description = "Recibe el archivo MRI (NIfTI), valida el formato, registra el estudio y publica el job en RabbitMQ.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Estudio creado y encolado",
                    content = @Content(schema = @Schema(implementation = StudyCreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Archivo inválido o faltante"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    public ResponseEntity<StudyCreateResponse> createStudy(
            @Parameter(description = "NIfTI file (.nii or .nii.gz)")
            @RequestParam("file") MultipartFile file) throws Exception {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.nii";
        if (!isNiftiFilename(originalFilename)) {
            return ResponseEntity.badRequest().build();
        }

        UUID usuarioId = authService.getCurrentUserId();
        EstudioEntity estudio = studyService.createAndEnqueue(usuarioId, file.getBytes(), originalFilename);

        return ResponseEntity.accepted()
                .body(new StudyCreateResponse(
                        estudio.getId(),
                        EstudioStatusMapper.toApi(estudio.getStatus()),
                        estudio.getMriPath(),
                        estudio.getCreatedAt()));
    }

    @GetMapping
    @Operation(summary = "Listar estudios del usuario autenticado",
            description = "Retorna el listado de estudios del usuario autenticado, ordenados del más reciente al más antiguo.")
    public ResponseEntity<List<StudySummaryResponse>> listStudies() {
        UUID usuarioId = authService.getCurrentUserId();
        List<StudySummaryResponse> res = studyService.listByUsuario(usuarioId).stream()
                .map(e -> new StudySummaryResponse(
                        e.getId(),
                        e.getOriginalFilename(),
                        EstudioStatusMapper.toApi(e.getStatus()),
                        e.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(res);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de estudio",
            description = "Retorna los metadatos y estado actual de un estudio específico del usuario autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = StudyDetailResponse.class))),
            @ApiResponse(responseCode = "404", description = "No encontrado o no pertenece al usuario")
    })
    public ResponseEntity<StudyDetailResponse> getStudy(@PathVariable Long id) {
        UUID usuarioId = authService.getCurrentUserId();
        return studyService.getByIdAndUsuario(id, usuarioId)
                .map(e -> ResponseEntity.ok(new StudyDetailResponse(
                        e.getId(),
                        e.getOriginalFilename(),
                        EstudioStatusMapper.toApi(e.getStatus()),
                        e.getCreatedAt(),
                        e.getErrorMessage())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/resultado")
    @Operation(summary = "Resultado del estudio",
            description = "Retorna la predicción y URLs (proxied por el backend) al heatmap y al volumen base " +
                    "para que el frontend los cargue en Niivue. Las URLs son null cuando el artefacto " +
                    "correspondiente todavía no existe.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = StudyResultResponse.class))),
            @ApiResponse(responseCode = "404", description = "No encontrado"),
            @ApiResponse(responseCode = "409", description = "Aún no completado")
    })
    public ResponseEntity<StudyResultResponse> getResult(@PathVariable Long id) {
        UUID usuarioId = authService.getCurrentUserId();
        EstudioEntity estudio = studyService.getByIdAndUsuario(id, usuarioId).orElse(null);
        if (estudio == null) return ResponseEntity.notFound().build();

        StudyStatus apiStatus = EstudioStatusMapper.toApi(estudio.getStatus());
        if (apiStatus != StudyStatus.COMPLETED) return ResponseEntity.status(409).build();

        ResultadoEntity resultado = studyService.getResultado(estudio.getId()).orElse(null);
        if (resultado == null) return ResponseEntity.notFound().build();

        String prediction = resultado.getPrediction() != null
                ? resultado.getPrediction().toString()
                : "N/A";

        String base = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/estudios/" + id)
                .toUriString();
        String heatmapUrl = resultado.getHeatmapPath() != null ? base + "/heatmap" : null;
        String origUrl = resultado.getOrigPath() != null ? base + "/orig" : null;

        return ResponseEntity.ok(new StudyResultResponse(
                prediction,
                heatmapUrl,
                origUrl,
                resultado.getReportPath()));
    }

    @GetMapping("/{id}/heatmap")
    @Operation(summary = "Stream del heatmap (Grad-CAM)",
            description = "Proxy-stream del volumen Grad-CAM (.nii.gz) almacenado en MinIO. " +
                    "Niivue lo carga como overlay sobre el volumen base devuelto por /orig. " +
                    "Soporta `Range` (HTTP 206) para carga progresiva e `If-None-Match` (HTTP 304) " +
                    "para evitar redescargar el volumen entre vistas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Volumen heatmap NIfTI gzip"),
            @ApiResponse(responseCode = "206", description = "Rango parcial del volumen"),
            @ApiResponse(responseCode = "304", description = "Sin cambios respecto al ETag enviado"),
            @ApiResponse(responseCode = "404", description = "Estudio o artefacto no encontrado"),
            @ApiResponse(responseCode = "409", description = "Estudio aún no completado"),
            @ApiResponse(responseCode = "416", description = "Rango fuera del tamaño del objeto")
    })
    public ResponseEntity<?> streamHeatmap(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatchHeader
    ) throws Exception {
        return streamArtifact(id, ResultadoEntity::getHeatmapPath, "heatmap.nii.gz",
                rangeHeader, ifNoneMatchHeader);
    }

    @GetMapping("/{id}/orig")
    @Operation(summary = "Stream del volumen base (T1 conformed)",
            description = "Proxy-stream del volumen T1 conformado por FastSurfer (.mgz) almacenado en MinIO. " +
                    "Niivue lo carga como base; el heatmap (/heatmap) se monta encima como overlay. " +
                    "Soporta `Range` (HTTP 206) e `If-None-Match` (HTTP 304).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Volumen base MGZ"),
            @ApiResponse(responseCode = "206", description = "Rango parcial del volumen"),
            @ApiResponse(responseCode = "304", description = "Sin cambios respecto al ETag enviado"),
            @ApiResponse(responseCode = "404", description = "Estudio o artefacto no encontrado"),
            @ApiResponse(responseCode = "409", description = "Estudio aún no completado"),
            @ApiResponse(responseCode = "416", description = "Rango fuera del tamaño del objeto")
    })
    public ResponseEntity<?> streamOrig(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatchHeader
    ) throws Exception {
        return streamArtifact(id, ResultadoEntity::getOrigPath, "orig.mgz",
                rangeHeader, ifNoneMatchHeader);
    }

    @GetMapping("/minio/files")
    @Operation(summary = "Listar archivos en MinIO",
            description = "Endpoint de prueba para validar que los NIfTI se están guardando en MinIO.")
    public ResponseEntity<List<String>> listMinioFiles() throws Exception {
        return ResponseEntity.ok(studyService.listMinioFiles());
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /**
     * Shared implementation for {@code /{id}/heatmap} and {@code /{id}/orig}: enforces
     * ownership + status + path-not-null checks, then handles HTTP conditional GET
     * ({@code If-None-Match} → 304) and byte-range requests
     * ({@code Range} → 206 / 416). On a body response the bytes are piped through a
     * {@link StreamingResponseBody} so we never buffer the whole volume in memory.
     */
    private ResponseEntity<?> streamArtifact(Long id,
                                             Function<ResultadoEntity, String> pathExtractor,
                                             String fallbackFilename,
                                             String rangeHeader,
                                             String ifNoneMatchHeader) throws Exception {
        UUID usuarioId = authService.getCurrentUserId();
        EstudioEntity estudio = studyService.getByIdAndUsuario(id, usuarioId).orElse(null);
        if (estudio == null) return ResponseEntity.notFound().build();

        if (EstudioStatusMapper.toApi(estudio.getStatus()) != StudyStatus.COMPLETED) {
            return ResponseEntity.status(409).build();
        }

        ResultadoEntity resultado = studyService.getResultado(id).orElse(null);
        if (resultado == null) return ResponseEntity.notFound().build();

        String storedPath = pathExtractor.apply(resultado);
        if (storedPath == null || storedPath.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        MinioObjectInfo info;
        try {
            info = studyService.statArtifact(storedPath, heatmapBucket);
        } catch (MinioObjectNotFoundException ex) {
            return ResponseEntity.notFound().build();
        }

        String quotedEtag = quoteEtag(info.etag());
        String filename = deriveFilename(storedPath, fallbackFilename);

        if (ifNoneMatchMatches(ifNoneMatchHeader, quotedEtag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .headers(baseHeaders(filename, quotedEtag))
                    .build();
        }

        ByteRange range = null;
        if (rangeHeader != null && !rangeHeader.isBlank()) {
            range = parseRange(rangeHeader, info.size());
            if (range == null) {
                HttpHeaders rangeHeaders = baseHeaders(filename, quotedEtag);
                rangeHeaders.set(HttpHeaders.CONTENT_RANGE, "bytes */" + info.size());
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .headers(rangeHeaders)
                        .build();
            }
        }

        StreamedMinioObject minioObject;
        try {
            if (range != null) {
                long length = range.end() - range.start() + 1;
                minioObject = studyService.openArtifactStream(storedPath, heatmapBucket, range.start(), length);
            } else {
                minioObject = studyService.openArtifactStream(storedPath, heatmapBucket);
            }
        } catch (MinioObjectNotFoundException ex) {
            return ResponseEntity.notFound().build();
        }

        StreamingResponseBody body = (OutputStream out) -> {
            try (InputStream in = minioObject.stream()) {
                in.transferTo(out);
            }
        };

        HttpHeaders headers = baseHeaders(filename, quotedEtag);

        if (range != null) {
            long length = range.end() - range.start() + 1;
            headers.setContentLength(length);
            headers.set(HttpHeaders.CONTENT_RANGE,
                    "bytes " + range.start() + "-" + range.end() + "/" + info.size());
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).headers(headers).body(body);
        }

        if (info.size() >= 0) {
            headers.setContentLength(info.size());
        }
        return ResponseEntity.ok().headers(headers).body(body);
    }

    private static HttpHeaders baseHeaders(String filename, String quotedEtag) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(APPLICATION_GZIP);
        headers.setContentDisposition(ContentDisposition.inline().filename(filename).build());
        headers.setCacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate().immutable());
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        if (quotedEtag != null) {
            headers.setETag(quotedEtag);
        }
        return headers;
    }

    /** Wrap a raw MinIO ETag in HTTP-quoted form. Idempotent if already quoted. */
    private static String quoteEtag(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String trimmed = raw.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed;
        }
        return "\"" + trimmed + "\"";
    }

    /**
     * Implements RFC 7232 {@code If-None-Match} matching against our current ETag.
     * Treats {@code *} as a wildcard match and accepts comma-separated tag lists,
     * tolerating {@code W/} weak prefixes.
     */
    private static boolean ifNoneMatchMatches(String header, String currentQuotedEtag) {
        if (header == null || currentQuotedEtag == null) return false;
        String trimmed = header.trim();
        if (trimmed.isEmpty()) return false;
        if (trimmed.equals("*")) return true;
        for (String token : trimmed.split(",")) {
            String t = token.trim();
            if (t.startsWith("W/")) t = t.substring(2).trim();
            if (t.equals(currentQuotedEtag)) return true;
        }
        return false;
    }

    /**
     * Parse a single-range {@code Range: bytes=...} header. Returns {@code null} if the
     * header is malformed, multi-range (which we don't support), or falls outside the
     * object — all of which should be answered with HTTP 416.
     */
    private static ByteRange parseRange(String header, long size) {
        if (header == null || size <= 0) return null;
        if (!header.startsWith("bytes=")) return null;
        String spec = header.substring("bytes=".length()).trim();
        if (spec.isEmpty() || spec.contains(",")) return null;
        int dash = spec.indexOf('-');
        if (dash < 0) return null;
        String startStr = spec.substring(0, dash).trim();
        String endStr = spec.substring(dash + 1).trim();
        long start;
        long end;
        try {
            if (startStr.isEmpty()) {
                long suffix = Long.parseLong(endStr);
                if (suffix <= 0) return null;
                start = Math.max(0, size - suffix);
                end = size - 1;
            } else if (endStr.isEmpty()) {
                start = Long.parseLong(startStr);
                end = size - 1;
            } else {
                start = Long.parseLong(startStr);
                end = Long.parseLong(endStr);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        if (start < 0 || start >= size || end < start) return null;
        if (end >= size) end = size - 1;
        return new ByteRange(start, end);
    }

    private record ByteRange(long start, long end) {}

    /** Pull the trailing path segment out of the stored MinIO key, or fall back. */
    private static String deriveFilename(String storedPath, String fallback) {
        if (storedPath == null) return fallback;
        int slash = storedPath.lastIndexOf('/');
        String tail = (slash >= 0 && slash < storedPath.length() - 1)
                ? storedPath.substring(slash + 1)
                : storedPath;
        return tail.isBlank() ? fallback : tail;
    }

    private static boolean isNiftiFilename(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".nii") || lower.endsWith(".nii.gz");
    }
}
