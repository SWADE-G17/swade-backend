package com.swade.controller;

import com.swade.dto.*;
import com.swade.entity.EstudioEntity;
import com.swade.entity.ResultadoEntity;
import com.swade.model.EstudioStatusMapper;
import com.swade.model.StudyStatus;
import com.swade.security.AuthService;
import com.swade.service.StudyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/estudios")
@Tag(name = "Estudios", description = "Carga de MRI, estado del estudio, resultados y reporte")
public class MriProcessController {

    private final StudyService studyService;
    private final AuthService authService;

    public MriProcessController(StudyService studyService, AuthService authService) {
        this.studyService = studyService;
        this.authService = authService;
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
            description = "Retorna la predicción, rutas del heatmap y reporte de un estudio completado.")
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
                ? resultado.getPrediction().path("prediction").asText("N/A")
                : "N/A";

        return ResponseEntity.ok(new StudyResultResponse(
                prediction,
                "/estudios/" + id + "/resultado/heatmap",
                "/estudios/" + id + "/reporte"));
    }

    @GetMapping("/{id}/resultado/heatmap")
    @Operation(summary = "Descargar heatmap (NIfTI)",
            description = "Descarga el NIfTI procesado (simulado) para el estudio.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Archivo NIfTI"),
            @ApiResponse(responseCode = "404", description = "No encontrado"),
            @ApiResponse(responseCode = "409", description = "Aún no completado")
    })
    public ResponseEntity<byte[]> downloadHeatmap(@PathVariable Long id) {
        UUID usuarioId = authService.getCurrentUserId();
        EstudioEntity estudio = studyService.getByIdAndUsuario(id, usuarioId).orElse(null);
        if (estudio == null) return ResponseEntity.notFound().build();

        if (EstudioStatusMapper.toApi(estudio.getStatus()) != StudyStatus.COMPLETED) {
            return ResponseEntity.status(409).build();
        }

        byte[] bytes = studyService.generateMockedHeatmapBytes();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "processed_mri.nii");
        headers.setContentLength(bytes.length);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    @GetMapping("/{id}/reporte")
    @Operation(summary = "Descargar reporte PDF",
            description = "Sirve el archivo PDF del reporte (simulado).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF"),
            @ApiResponse(responseCode = "404", description = "No encontrado"),
            @ApiResponse(responseCode = "409", description = "Aún no completado")
    })
    public ResponseEntity<byte[]> downloadReport(@PathVariable Long id) {
        UUID usuarioId = authService.getCurrentUserId();
        EstudioEntity estudio = studyService.getByIdAndUsuario(id, usuarioId).orElse(null);
        if (estudio == null) return ResponseEntity.notFound().build();

        if (EstudioStatusMapper.toApi(estudio.getStatus()) != StudyStatus.COMPLETED) {
            return ResponseEntity.status(409).build();
        }

        byte[] pdf = studyService.generateMockedReportPdf(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "reporte.pdf");
        headers.setContentLength(pdf.length);
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    @GetMapping("/minio/files")
    @Operation(summary = "Listar archivos en MinIO",
            description = "Endpoint de prueba para validar que los NIfTI se están guardando en MinIO.")
    public ResponseEntity<List<String>> listMinioFiles() throws Exception {
        return ResponseEntity.ok(studyService.listMinioFiles());
    }

    private static boolean isNiftiFilename(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".nii") || lower.endsWith(".nii.gz");
    }
}
