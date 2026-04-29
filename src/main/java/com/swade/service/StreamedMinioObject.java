package com.swade.service;

import java.io.IOException;
import java.io.InputStream;

/**
 * Lightweight handle around an open InputStream from MinIO together with
 * the bytes-on-the-wire size for this stream and (optionally) the object's ETag.
 *
 * <p>For full-object streams {@link #size()} is the full object size. For
 * range-limited streams (see {@link MinioService#openObjectStream(String, String, long, long)})
 * it is the requested length, which is what should go on the
 * {@code Content-Length} header.</p>
 *
 * <p>Implements {@link AutoCloseable} so callers can use it inside a
 * try-with-resources block to make sure the underlying HTTP connection
 * is released back to MinIO's pool.</p>
 */
public final class StreamedMinioObject implements AutoCloseable {

    private final InputStream stream;
    private final long size;
    private final String etag;

    public StreamedMinioObject(InputStream stream, long size) {
        this(stream, size, null);
    }

    public StreamedMinioObject(InputStream stream, long size, String etag) {
        this.stream = stream;
        this.size = size;
        this.etag = etag;
    }

    public InputStream stream() {
        return stream;
    }

    /** Bytes on the wire for this stream, or {@code -1} if unknown. */
    public long size() {
        return size;
    }

    /** Raw ETag from MinIO (no surrounding quotes), or {@code null} if not available. */
    public String etag() {
        return etag;
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }
}
