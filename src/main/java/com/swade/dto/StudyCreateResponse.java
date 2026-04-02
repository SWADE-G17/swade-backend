package com.swade.dto;

import com.swade.model.StudyStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Response after creating a new study and enqueuing the AI processing job")
public record StudyCreateResponse(
        @Schema(description = "Study ID", example = "1")
        Long id,
        @Schema(description = "Current status", example = "QUEUED")
        StudyStatus status,
        @Schema(description = "MinIO object path where the uploaded MRI was stored")
        String filePath,
        @Schema(description = "Creation timestamp (UTC)")
        Instant createdAt
) {}
