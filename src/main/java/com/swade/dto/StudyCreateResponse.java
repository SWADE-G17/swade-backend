package com.swade.dto;

import com.swade.model.StudyStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Response after creating a new study and enqueuing the AI processing job")
public record StudyCreateResponse(
        @Schema(description = "Study ID", example = "f3f9a1c7-5ef3-4a6a-9f4b-0c5b1f8a2b7e")
        String id,
        @Schema(description = "Current status", example = "QUEUED")
        StudyStatus status,
        @Schema(description = "MinIO object path where the uploaded MRI was stored", example = "inputs/f3f9a1c7-5ef3-4a6a-9f4b-0c5b1f8a2b7e/abc123.nii")
        String filePath,
        @Schema(description = "Creation timestamp (UTC)")
        Instant createdAt
) {}

