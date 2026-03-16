package com.swade.service;

import com.swade.dto.MriProcessResponse;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulates AI model processing of NIfTI MRI files.
 * Returns a fake prediction and a dummy processed NIfTI file.
 */
@Service
public class MriProcessService {

    private static final String SIMULATED_PREDICTION = "Prediction result";
    private static final String PROCESSED_FILENAME = "processed_mri.nii";

    /** Minimal NIfTI-1 header (348 bytes) + small data block for a valid dummy .nii file */
    private static final byte[] DUMMY_NIFTI_BYTES = createDummyNiftiBytes();

    private final Map<String, byte[]> resultFileStore = new ConcurrentHashMap<>();

    /**
     * Simulates sending the uploaded NIfTI to an AI model and returns
     * a prediction and a stored processed file reference.
     */
    public MriProcessResponse process(byte[] niftiBytes, String originalFilename) {
        // Simulate AI processing (in real implementation would call external model)
        String resultFileId = UUID.randomUUID().toString();
        resultFileStore.put(resultFileId, DUMMY_NIFTI_BYTES);
        return new MriProcessResponse(SIMULATED_PREDICTION, resultFileId);
    }

    /**
     * Returns the processed NIfTI file bytes for the given result ID, or null if not found.
     */
    public byte[] getProcessedFile(String resultFileId) {
        return resultFileStore.get(resultFileId);
    }

    public String getProcessedFileName() {
        return PROCESSED_FILENAME;
    }

    private static byte[] createDummyNiftiBytes() {
        // NIfTI-1 single file (.nii): 348-byte header + optional data
        // Magic "n+1" (0x6E 0x2B 0x31 0x00) at offset 0
        int headerSize = 348;
        int dataSize = 64; // minimal dummy data
        byte[] buf = new byte[headerSize + dataSize];
        buf[0] = 0x6E; // 'n'
        buf[1] = 0x2B; // '+'
        buf[2] = 0x31; // '1'
        buf[3] = 0x00;
        // Dims at offset 40: 3 dimensions, e.g. 2,2,2
        buf[40] = 3;
        buf[44] = 2;
        buf[48] = 2;
        buf[52] = 2;
        // Rest can be zeros; parsers may still accept for testing
        return buf;
    }
}
