package com.swade.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of a completed study (prediction + routes to artifacts)")
public record StudyResultResponse(
        @Schema(description = "AI model prediction result", example = "Prediction result")
        String prediction,
        @Schema(description = "Route to download the heatmap/processed NIfTI for this study")
        String heatmapPath,
        @Schema(description = "Route to download the PDF report for this study")
        String reportPath
) {}

