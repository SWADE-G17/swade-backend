package com.swade.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response containing AI prediction and reference to download the processed NIfTI file")
public record MriProcessResponse(

        @Schema(description = "AI model prediction result", example = "Prediction result")
        String prediction,

        @Schema(description = "Unique ID to download the processed NIfTI file via GET /api/mri/process/result/{resultFileId}")
        String resultFileId
) {}
