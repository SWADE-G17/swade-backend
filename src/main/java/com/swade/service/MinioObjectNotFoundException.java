package com.swade.service;

/**
 * Thrown when a requested MinIO object (or its bucket) does not exist.
 * Controllers should map this to HTTP 404.
 */
public class MinioObjectNotFoundException extends RuntimeException {

    public MinioObjectNotFoundException(String bucket, String objectName, Throwable cause) {
        super("MinIO object not found: " + bucket + "/" + objectName, cause);
    }
}
