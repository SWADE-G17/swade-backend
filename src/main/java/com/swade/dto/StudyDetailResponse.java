package com.swade.dto;

import com.swade.model.StudyStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Detailed metadata and state of a study")
public record StudyDetailResponse(
        @Schema(description = "Study ID")
        String id,
        @Schema(description = "Original filename")
        String originalFilename,
        @Schema(description = "Current status")
        StudyStatus status,
        @Schema(description = "Creation timestamp (UTC)")
        Instant createdAt,
        @Schema(description = "Error message, if FAILED", nullable = true)
        String error
) {}

