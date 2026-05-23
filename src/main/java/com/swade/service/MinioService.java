package com.swade.service;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
        return downloadFromBucket(bucketName, objectName);
    }

    public byte[] downloadFromBucket(String bucket, String objectName) throws Exception {
        try (InputStream in = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .build()
        )) {
            return in.readAllBytes();
        }
    }

    /**
     * Cheap metadata-only call: returns size + ETag without opening a body stream.
     * Used by controllers to evaluate {@code If-None-Match} and validate
     * {@code Range} requests before pulling bytes.
     *
     * @throws MinioObjectNotFoundException if the bucket or object does not exist
     */
    public MinioObjectInfo statObject(String bucket, String objectName) throws Exception {
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build()
            );
            return new MinioObjectInfo(stat.size(), stat.etag());
        } catch (ErrorResponseException e) {
            if (NOT_FOUND_CODES.contains(e.errorResponse().code())) {
                throw new MinioObjectNotFoundException(bucket, objectName, e);
            }
            throw e;
        }
    }

    /**
     * Open a streaming handle on an object without buffering its contents in memory.
     *
     * <p>The returned {@link StreamedMinioObject} owns the underlying InputStream and
     * MUST be closed by the caller (typically by handing it to a
     * {@code StreamingResponseBody}).</p>
     *
     * @throws MinioObjectNotFoundException if the bucket or object does not exist
     */
    public StreamedMinioObject openObjectStream(String bucket, String objectName) throws Exception {
        MinioObjectInfo info = statObject(bucket, objectName);

        try {
            GetObjectResponse stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build()
            );
            return new StreamedMinioObject(stream, info.size(), info.etag());
        } catch (ErrorResponseException e) {
            if (NOT_FOUND_CODES.contains(e.errorResponse().code())) {
                throw new MinioObjectNotFoundException(bucket, objectName, e);
            }
            throw e;
        }
    }

    /**
     * Open a streaming handle on a byte range of an object (for HTTP 206 partial-content
     * responses). The returned stream contains exactly {@code length} bytes starting at
     * {@code offset}; callers should use {@code length} as the {@code Content-Length} of
     * their response.
     *
     * <p>This method does <em>not</em> validate that {@code offset + length <= objectSize};
     * callers should call {@link #statObject(String, String)} first and reject the request
     * with HTTP 416 if it falls outside the object.</p>
     *
     * @throws MinioObjectNotFoundException if the bucket or object does not exist
     */
    public StreamedMinioObject openObjectStream(String bucket, String objectName,
                                                long offset, long length) throws Exception {
        try {
            GetObjectResponse stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .offset(offset)
                            .length(length)
                            .build()
            );
            return new StreamedMinioObject(stream, length, null);
        } catch (ErrorResponseException e) {
            if (NOT_FOUND_CODES.contains(e.errorResponse().code())) {
                throw new MinioObjectNotFoundException(bucket, objectName, e);
            }
            throw e;
        }
    }

    private static final Set<String> NOT_FOUND_CODES = Set.of("NoSuchKey", "NoSuchBucket");

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

