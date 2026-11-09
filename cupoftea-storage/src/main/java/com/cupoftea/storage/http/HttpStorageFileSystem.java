package com.cupoftea.storage.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;

import com.cupoftea.storage.StorageFileSystem;

/**
 * {@link StorageFileSystem} implementation for standard HTTP and HTTPS URIs.
 * Supports byte-range random access reading via HTTP Range Requests and streaming upload via HTTP PUT/POST.
 */
public class HttpStorageFileSystem implements StorageFileSystem {

    @Override
    public boolean supports(String scheme) {
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    @Override
    public SeekableByteChannel openSeekable(URI uri) throws IOException {
        long len = size(uri);
        return new HttpRangeSeekableByteChannel(uri, len);
    }

    @Override
    public InputStream openInputStream(URI uri) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(30_000);

        int code = conn.getResponseCode();
        if (code >= 400) {
            throw new IOException("HTTP request failed: " + code + " " + conn.getResponseMessage() + " for " + uri);
        }
        return conn.getInputStream();
    }

    @Override
    public OutputStream openOutputStream(URI uri) throws IOException {
        // Return a buffered streaming output stream that executes HTTP PUT upon close
        return new ByteArrayOutputStream() {
            @Override
            public void close() throws IOException {
                super.close();
                byte[] data = toByteArray();
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestMethod("PUT");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/octet-stream");
                conn.setRequestProperty("Content-Length", String.valueOf(data.length));
                conn.setConnectTimeout(15_000);
                conn.setReadTimeout(30_000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(data);
                }

                int code = conn.getResponseCode();
                if (code >= 400) {
                    throw new IOException("HTTP PUT failed: " + code + " " + conn.getResponseMessage() + " for " + uri);
                }
                conn.disconnect();
            }
        };
    }

    @Override
    public long size(URI uri) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod("HEAD");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);
        try {
            int code = conn.getResponseCode();
            if (code >= 400) {
                return -1;
            }
            long len = conn.getContentLengthLong();
            return len >= 0 ? len : -1;
        } finally {
            conn.disconnect();
        }
    }

    @Override
    public boolean exists(URI uri) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod("HEAD");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);
        try {
            int code = conn.getResponseCode();
            return code >= 200 && code < 400;
        } finally {
            conn.disconnect();
        }
    }
}
