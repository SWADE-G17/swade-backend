package com.swade.model;

import java.time.Instant;

public class Study {
    private final String id;
    private final String originalFilename;
    private final Instant createdAt;

    private volatile StudyStatus status;
    private volatile String error;

    private volatile String prediction;
    private volatile String inputFilePath;
    private volatile byte[] processedNiftiBytes;
    private volatile byte[] reportPdfBytes;

    public Study(String id, String originalFilename, Instant createdAt, StudyStatus status) {
        this.id = id;
        this.originalFilename = originalFilename;
        this.createdAt = createdAt;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public StudyStatus getStatus() {
        return status;
    }

    public void setStatus(StudyStatus status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getPrediction() {
        return prediction;
    }

    public void setPrediction(String prediction) {
        this.prediction = prediction;
    }

    public String getInputFilePath() {
        return inputFilePath;
    }

    public void setInputFilePath(String inputFilePath) {
        this.inputFilePath = inputFilePath;
    }

    public byte[] getProcessedNiftiBytes() {
        return processedNiftiBytes;
    }

    public void setProcessedNiftiBytes(byte[] processedNiftiBytes) {
        this.processedNiftiBytes = processedNiftiBytes;
    }

    public byte[] getReportPdfBytes() {
        return reportPdfBytes;
    }

    public void setReportPdfBytes(byte[] reportPdfBytes) {
        this.reportPdfBytes = reportPdfBytes;
    }
}

