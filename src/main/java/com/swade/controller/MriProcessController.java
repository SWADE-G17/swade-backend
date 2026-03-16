package com.swade.controller;

import com.swade.dto.MriProcessResponse;
import com.swade.service.MriProcessService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
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

import java.io.IOException;

@RestController
@RequestMapping("/api/mri")
@Tag(name = "MRI Processing", description = "Upload NIfTI files for AI processing and download results")
public class MriProcessController {

    private final MriProcessService mriProcessService;

    public MriProcessController(MriProcessService mriProcessService) {
        this.mriProcessService = mriProcessService;
    }

    @PostMapping(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Process NIfTI file",
            description = "Upload a NIfTI (.nii or .nii.gz) file. The file is forwarded to the AI model (simulated). " +
                    "Returns a JSON prediction and a resultFileId. Use the resultFileId with GET /api/mri/process/result/{resultFileId} to download the processed NIfTI file."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Processing complete",
                    content = @Content(schema = @Schema(implementation = MriProcessResponse.class))),
            @ApiResponse(responseCode = "400", description = "No file or invalid file provided")
    })
    public ResponseEntity<MriProcessResponse> process(
            @Parameter(description = "NIfTI file (.nii or .nii.gz)")
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        byte[] bytes = file.getBytes();
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.nii";
        MriProcessResponse response = mriProcessService.process(bytes, originalFilename);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/process/result/{resultFileId}")
    @Operation(
            summary = "Download processed NIfTI file",
            description = "Download the processed NIfTI file (processed_mri.nii) using the resultFileId returned from POST /api/mri/process."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Processed NIfTI file"),
            @ApiResponse(responseCode = "404", description = "Result not found or expired")
    })
    public ResponseEntity<byte[]> downloadResult(@PathVariable String resultFileId) {
        byte[] fileBytes = mriProcessService.getProcessedFile(resultFileId);
        if (fileBytes == null) {
            return ResponseEntity.notFound().build();
        }

        String filename = mriProcessService.getProcessedFileName();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(fileBytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(fileBytes);
    }
}
