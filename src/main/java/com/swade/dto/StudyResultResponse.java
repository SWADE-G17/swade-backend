package com.swade.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of a completed study (prediction + URLs to artifacts)")
public record StudyResultResponse(
        @Schema(description = "AI model prediction result", example = "Prediction result")
        String prediction,
        @Schema(description = "Backend-proxied URL for the Grad-CAM heatmap volume (.nii.gz). " +
                "Niivue should load this as an overlay on top of origUrl. " +
                "Null when the study has no heatmap stored yet.")
        String heatmapUrl,
        @Schema(description = "Backend-proxied URL for the FastSurfer-conformed T1 volume (.mgz). " +
                "Niivue should load this as the base image. " +
                "Null when the study has no original volume stored yet.")
        String origUrl,
        @Schema(description = "Route to download the PDF report for this study")
        String reportPath
) {}
