package com.swade.service;

/**
 * Lightweight metadata snapshot returned by {@link MinioService#statObject(String, String)}.
 *
 * <p>Used by the controller layer to issue HTTP {@code ETag} / {@code If-None-Match}
 * conditional GETs and to validate {@code Range} requests before opening a stream.</p>
 *
 * @param size object size in bytes (always known, MinIO returns it from {@code statObject})
 * @param etag raw ETag value as returned by MinIO (no surrounding quotes); may be {@code null}
 *             for the rare case where MinIO does not echo one
 */
public record MinioObjectInfo(long size, String etag) {}
