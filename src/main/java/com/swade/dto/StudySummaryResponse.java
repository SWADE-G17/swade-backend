package com.swade.dto;

import com.swade.model.StudyStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Summary of a study")
public record StudySummaryResponse(
        @Schema(description = "Study ID")
        Long id,
        @Schema(description = "Original filename")
        String originalFilename,
        @Schema(description = "Current status")
        StudyStatus status,
        @Schema(description = "Creation timestamp (UTC)")
        Instant createdAt
) {}
