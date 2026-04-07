package com.swade.service;

import io.minio.*;
import io.minio.messages.Item;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class MinioService {

    private final MinioClient minioClient;
    private final String bucketName;

    public MinioService(MinioClient minioClient, @Value("${minio.bucket.name}") String bucketName) throws Exception {
        this.minioClient = minioClient;
        this.bucketName = bucketName;
    }

    public String uploadFile(byte[] bytes, String originalFilename, String studyId) throws Exception {
        ensureBucketExists();
        String extension = extractExtension(originalFilename);
        String objectName = "inputs/" + studyId + "/" + UUID.randomUUID() + extension;

        try (InputStream in = new ByteArrayInputStream(bytes)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(in, bytes.length, -1)
                            .contentType("application/octet-stream")
                            .build()
            );
        }
        return objectName;
    }

    public byte[] downloadFile(String objectName) throws Exception {
        try (InputStream in = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()
        )) {
            return in.readAllBytes();
        }
    }

    public List<String> listFiles() throws Exception {
        ensureBucketExists();
        List<String> files = new ArrayList<>();
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(bucketName)
                        .recursive(true)
                        .build()
        );
        for (Result<Item> result : results) {
            Item item = result.get();
            files.add(item.objectName());
        }
        return files;
    }

    private void ensureBucketExists() throws Exception {
        boolean found = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucketName).build()
        );
        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }

    private static String extractExtension(String filename) {
        if (filename == null) return ".nii";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".nii.gz")) return ".nii.gz";
        int i = filename.lastIndexOf('.');
        if (i < 0) return ".nii";
        return filename.substring(i);
    }
}

