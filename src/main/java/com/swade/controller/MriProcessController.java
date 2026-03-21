package com.swade.controller;

import com.swade.dto.*;
import com.swade.model.Study;
import com.swade.model.StudyStatus;
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
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.List;

@RestController
@RequestMapping("/estudios")
@Tag(name = "Estudios", description = "Carga de MRI, estado del estudio, resultados y reporte")
public class MriProcessController {

    private final StudyService studyService;

    public MriProcessController(StudyService studyService) {
        this.studyService = studyService;
    }

    @CrossOrigin(origins = "http://localhost:3000")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Crear estudio", description = "Recibe el archivo MRI (NIfTI), valida el formato, registra el estudio y publica el job en RabbitMQ.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Estudio creado y encolado",
                    content = @Content(schema = @Schema(implementation = StudyCreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Archivo inválido o faltante")
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

        Study study = studyService.createAndEnqueue(file.getBytes(), originalFilename);
        return ResponseEntity.accepted()
                .body(new StudyCreateResponse(study.getId(), study.getStatus(), study.getInputFilePath(), study.getCreatedAt()));
    }

    @CrossOrigin(origins = "http://localhost:3000")
    @GetMapping("/minio/files")
    @Operation(summary = "Listar archivos en MinIO", description = "Endpoint de prueba para validar que los NIfTI se están guardando en MinIO.")
    public ResponseEntity<List<String>> listMinioFiles() throws Exception {
        return ResponseEntity.ok(studyService.listMinioFiles());
    }

    @CrossOrigin(origins = "http://localhost:3000")
    @GetMapping
    @Operation(summary = "Listar estudios", description = "Retorna el listado de estudios asociados al usuario autenticado (simulado: lista global).")
    public ResponseEntity<List<StudySummaryResponse>> listStudies() {
        List<StudySummaryResponse> res = studyService.list().stream()
                .map(s -> new StudySummaryResponse(s.getId(), s.getOriginalFilename(), s.getStatus(), s.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(res);
    }

    @CrossOrigin(origins = "http://localhost:3000")
    @GetMapping("/{id}")
    @Operation(summary = "Detalle de estudio", description = "Retorna los metadatos y estado actual de un estudio específico.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = StudyDetailResponse.class))),
            @ApiResponse(responseCode = "404", description = "No encontrado")
    })
    public ResponseEntity<StudyDetailResponse> getStudy(@PathVariable String id) {
        Study s = studyService.get(id);
        if (s == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(new StudyDetailResponse(s.getId(), s.getOriginalFilename(), s.getStatus(), s.getCreatedAt(), s.getError()));
    }

    @CrossOrigin(origins = "http://localhost:3000")
    @GetMapping("/{id}/resultado")
    @Operation(summary = "Resultado del estudio", description = "Retorna la predicción, rutas del heatmap y reporte de un estudio completado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = StudyResultResponse.class))),
            @ApiResponse(responseCode = "404", description = "No encontrado"),
            @ApiResponse(responseCode = "409", description = "Aún no completado")
    })
    public ResponseEntity<StudyResultResponse> getResult(@PathVariable String id) {
        Study s = studyService.get(id);
        if (s == null) return ResponseEntity.notFound().build();
        if (s.getStatus() != StudyStatus.COMPLETED) return ResponseEntity.status(409).build();

        String heatmapPath = "/estudios/" + id + "/resultado/heatmap";
        String reportPath = "/estudios/" + id + "/reporte";
        return ResponseEntity.ok(new StudyResultResponse(s.getPrediction(), heatmapPath, reportPath));
    }

    @CrossOrigin(origins = "http://localhost:3000")
    @GetMapping("/{id}/resultado/heatmap")
    @Operation(summary = "Descargar heatmap (NIfTI)", description = "Descarga el NIfTI procesado (simulado) para el estudio.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Archivo NIfTI"),
            @ApiResponse(responseCode = "404", description = "No encontrado"),
            @ApiResponse(responseCode = "409", description = "Aún no completado")
    })
    public ResponseEntity<byte[]> downloadHeatmap(@PathVariable String id) {
        Study s = studyService.get(id);
        if (s == null) return ResponseEntity.notFound().build();
        if (s.getStatus() != StudyStatus.COMPLETED) return ResponseEntity.status(409).build();

        byte[] bytes = studyService.getProcessedNifti(id);
        if (bytes == null) return ResponseEntity.notFound().build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "processed_mri.nii");
        headers.setContentLength(bytes.length);

        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    @CrossOrigin(origins = "http://localhost:3000")
    @GetMapping("/{id}/reporte")
    @Operation(summary = "Descargar reporte PDF", description = "Sirve el archivo PDF del reporte (simulado; luego puede venir de MinIO).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF"),
            @ApiResponse(responseCode = "404", description = "No encontrado"),
            @ApiResponse(responseCode = "409", description = "Aún no completado")
    })
    public ResponseEntity<byte[]> downloadReport(@PathVariable String id) {
        Study s = studyService.get(id);
        if (s == null) return ResponseEntity.notFound().build();
        if (s.getStatus() != StudyStatus.COMPLETED) return ResponseEntity.status(409).build();

        byte[] pdf = studyService.getReportPdf(id);
        if (pdf == null) return ResponseEntity.notFound().build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "reporte.pdf");
        headers.setContentLength(pdf.length);
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    private static boolean isNiftiFilename(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".nii") || lower.endsWith(".nii.gz");
    }
}
