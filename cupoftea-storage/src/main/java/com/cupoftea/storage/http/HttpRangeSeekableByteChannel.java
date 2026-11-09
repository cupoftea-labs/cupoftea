package com.cupoftea.storage.http;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;

/**
 * High-performance {@link SeekableByteChannel} implementation backed by HTTP(S) Range Requests
 * (RFC 7233 / 9110 Range Requests).
 *
 * <p>Enables random-access byte reading over HTTP/HTTPS/S3 presigned URLs without downloading
 * entire gigabytes of analytical files, reading selectively Parquet footers and row groups.</p>
 */
public class HttpRangeSeekableByteChannel implements SeekableByteChannel {

    private final URI uri;
    private final long totalLength;
    private final int bufferChunkSize;
    private long position;
    private boolean open;

    // Small read buffer cache to reduce roundtrips for consecutive reads
    private byte[] cachedBuffer;
    private long cachedStart = -1;
    private int cachedLen = 0;

    public HttpRangeSeekableByteChannel(URI uri, long totalLength) {
        this(uri, totalLength, 64 * 1024);
    }

    public HttpRangeSeekableByteChannel(URI uri, long totalLength, int bufferChunkSize) {
        if (uri == null) {
            throw new IllegalArgumentException("URI cannot be null");
        }
        this.uri = uri;
        this.totalLength = totalLength;
        this.bufferChunkSize = Math.max(8192, bufferChunkSize);
        this.position = 0;
        this.open = true;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        ensureOpen();
        if (totalLength >= 0 && position >= totalLength) {
            return -1;
        }

        int requested = dst.remaining();
        if (requested == 0) {
            return 0;
        }

        // Check if current position can be served from cached buffer
        if (cachedBuffer != null && position >= cachedStart && position < cachedStart + cachedLen) {
            int cacheOffset = (int) (position - cachedStart);
            int availableInCache = cachedLen - cacheOffset;
            int toCopy = Math.min(requested, availableInCache);
            dst.put(cachedBuffer, cacheOffset, toCopy);
            position += toCopy;
            return toCopy;
        }

        // Fetch chunk via Range request
        long rangeStart = position;
        long fetchLen = Math.max(requested, bufferChunkSize);
        long rangeEnd = (totalLength >= 0) ? Math.min(rangeStart + fetchLen - 1, totalLength - 1) : (rangeStart + fetchLen - 1);

        byte[] fetched = fetchRange(rangeStart, rangeEnd);
        if (fetched == null || fetched.length == 0) {
            return -1;
        }

        this.cachedBuffer = fetched;
        this.cachedStart = rangeStart;
        this.cachedLen = fetched.length;

        int toCopy = Math.min(requested, fetched.length);
        dst.put(fetched, 0, toCopy);
        position += toCopy;
        return toCopy;
    }

    private byte[] fetchRange(long start, long end) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Range", "bytes=" + start + "-" + (end >= 0 ? end : ""));
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(30_000);

        int code = conn.getResponseCode();
        if (code != HttpURLConnection.HTTP_PARTIAL && code != HttpURLConnection.HTTP_OK) {
            if (code == 416) { // Range Not Satisfiable -> EOF
                return new byte[0];
            }
            throw new IOException("HTTP Range request failed: " + code + " " + conn.getResponseMessage() + " for " + uri);
        }

        try (InputStream in = conn.getInputStream()) {
            return in.readAllBytes();
        } finally {
            conn.disconnect();
        }
    }

    @Override
    public int write(ByteBuffer src) {
        throw new NonWritableChannelException();
    }

    @Override
    public long position() throws IOException {
        ensureOpen();
        return position;
    }

    @Override
    public SeekableByteChannel position(long newPosition) throws IOException {
        ensureOpen();
        if (newPosition < 0) {
            throw new IllegalArgumentException("Position cannot be negative: " + newPosition);
        }
        this.position = newPosition;
        return this;
    }

    @Override
    public long size() throws IOException {
        ensureOpen();
        return totalLength;
    }

    @Override
    public SeekableByteChannel truncate(long size) {
        throw new NonWritableChannelException();
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void close() {
        this.open = false;
        this.cachedBuffer = null;
    }

    private void ensureOpen() throws ClosedChannelException {
        if (!open) {
            throw new ClosedChannelException();
        }
    }
}
