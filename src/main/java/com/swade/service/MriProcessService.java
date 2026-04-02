package com.swade.service;

import org.springframework.stereotype.Service;

/**
 * Simulates AI model processing of NIfTI MRI files.
 */
@Service
public class MriProcessService {

    public record SimulatedResult(String prediction, byte[] processedNiftiBytes) {}

    private static final String SIMULATED_PREDICTION = "Prediction result";

    /** Minimal NIfTI-1 header (348 bytes) + small data block for a valid dummy .nii file */
    private static final byte[] DUMMY_NIFTI_BYTES = createDummyNiftiBytes();

    public SimulatedResult simulateProcessing(byte[] niftiBytes, String originalFilename) {
        return new SimulatedResult(SIMULATED_PREDICTION, DUMMY_NIFTI_BYTES);
    }

    public static byte[] getDummyNiftiBytes() {
        return DUMMY_NIFTI_BYTES.clone();
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
        return buf;
    }
}
