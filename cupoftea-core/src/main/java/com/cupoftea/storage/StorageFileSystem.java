package com.cupoftea.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;

/**
 * Service Provider Interface (SPI) for cloud object storage and remote filesystems.
 * Implementations provide uniform access to S3, GCS, Azure Blob, HTTP(S), HDFS, and local files.
 */
public interface StorageFileSystem {

    /**
     * Checks if this filesystem supports the given URI scheme (e.g. "s3", "gs", "abfss", "http", "https").
     *
     * @param scheme the URI scheme, case-insensitive.
     * @return {@code true} if supported.
     */
    boolean supports(String scheme);

    /**
     * Opens a {@link SeekableByteChannel} for random-access and byte-range reads on the remote resource.
     * Essential for columnar formats like Parquet to selectively read footers and row groups.
     *
     * @param uri remote resource URI.
     * @return seekable byte channel.
     * @throws IOException on I/O error.
     */
    SeekableByteChannel openSeekable(URI uri) throws IOException;

    /**
     * Opens a sequential input stream for reading the remote resource.
     *
     * @param uri remote resource URI.
     * @return input stream.
     * @throws IOException on I/O error.
     */
    InputStream openInputStream(URI uri) throws IOException;

    /**
     * Opens a streaming output stream for writing data directly to the remote resource.
     *
     * @param uri remote resource URI.
     * @return output stream.
     * @throws IOException on I/O error.
     */
    OutputStream openOutputStream(URI uri) throws IOException;

    /**
     * Returns the size of the remote resource in bytes, or -1 if unknown.
     *
     * @param uri remote resource URI.
     * @return file size in bytes.
     * @throws IOException on I/O error.
     */
    long size(URI uri) throws IOException;

    /**
     * Checks whether the remote resource exists.
     *
     * @param uri remote resource URI.
     * @return {@code true} if exists.
     * @throws IOException on I/O error.
     */
    boolean exists(URI uri) throws IOException;
}
