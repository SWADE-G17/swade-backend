package com.swade.service;

import com.swade.messaging.RabbitMqConfig;
import com.swade.messaging.StudyJobMessage;
import com.swade.model.Study;
import com.swade.model.StudyStatus;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StudyService {

    private final RabbitTemplate rabbitTemplate;
    private final MriProcessService mriProcessService;
    private final MinioService minioService;

    private final Map<String, Study> studies = new ConcurrentHashMap<>();

    public StudyService(RabbitTemplate rabbitTemplate, MriProcessService mriProcessService, MinioService minioService) {
        this.rabbitTemplate = rabbitTemplate;
        this.mriProcessService = mriProcessService;
        this.minioService = minioService;
    }

    public Study createAndEnqueue(byte[] niftiBytes, String originalFilename) throws Exception {
        String id = UUID.randomUUID().toString();
        Study study = new Study(id, originalFilename, Instant.now(), StudyStatus.QUEUED);
        studies.put(id, study);
        String filePath = minioService.uploadFile(niftiBytes, originalFilename, id);
        study.setInputFilePath(filePath);

        rabbitTemplate.convertAndSend(RabbitMqConfig.MRI_PROCESSING_QUEUE, new StudyJobMessage(id, filePath));
        return study;
    }

    public List<Study> list() {
        ArrayList<Study> list = new ArrayList<>(studies.values());
        list.sort(Comparator.comparing(Study::getCreatedAt).reversed());
        return list;
    }

    public Study get(String id) {
        return studies.get(id);
    }

    public byte[] getProcessedNifti(String id) {
        Study s = studies.get(id);
        return s != null ? s.getProcessedNiftiBytes() : null;
    }

    public byte[] getReportPdf(String id) {
        Study s = studies.get(id);
        return s != null ? s.getReportPdfBytes() : null;
    }

    public List<String> listMinioFiles() throws Exception {
        return minioService.listFiles();
    }

    /**
     * Executed by the RabbitMQ consumer to process one study job at a time.
     */
    public void processJob(String studyId, String filePath) {
        Study study = studies.get(studyId);
        if (study == null) return;

        study.setStatus(StudyStatus.PROCESSING);
        try {
            byte[] original = minioService.downloadFile(filePath);
            if (original == null || original.length == 0) {
                throw new IllegalStateException("Original file not found in MinIO");
            }

            var result = mriProcessService.simulateProcessing(original, study.getOriginalFilename());
            study.setPrediction(result.prediction());
            study.setProcessedNiftiBytes(result.processedNiftiBytes());
            study.setReportPdfBytes(createDummyPdfBytes(studyId));
            study.setStatus(StudyStatus.COMPLETED);
        } catch (Exception e) {
            study.setError(e.getMessage());
            study.setStatus(StudyStatus.FAILED);
        }
    }

    private static byte[] createDummyPdfBytes(String studyId) {
        // Minimal PDF structure enough for download testing
        String body = "%PDF-1.4\n" +
                "1 0 obj<< /Type /Catalog /Pages 2 0 R >>endobj\n" +
                "2 0 obj<< /Type /Pages /Kids [3 0 R] /Count 1 >>endobj\n" +
                "3 0 obj<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources<< >> >>endobj\n" +
                "4 0 obj<< /Length 44 >>stream\n" +
                "BT /F1 18 Tf 72 720 Td (Study " + studyId + ") Tj ET\n" +
                "endstream endobj\n" +
                "xref\n0 5\n0000000000 65535 f \n" +
                "trailer<< /Root 1 0 R /Size 5 >>\n" +
                "startxref\n0\n%%EOF\n";
        return body.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }
}

