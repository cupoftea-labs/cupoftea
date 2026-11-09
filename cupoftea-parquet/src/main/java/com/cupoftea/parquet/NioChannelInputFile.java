package com.cupoftea.parquet;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.StandardOpenOption;

import org.apache.parquet.io.InputFile;
import org.apache.parquet.io.SeekableInputStream;

import com.cupoftea.storage.StorageRegistry;

/**
 * High-performance NIO {@link InputFile} backed by a local file or remote storage {@link SeekableByteChannel}.
 * Provides direct byte channel reads without the RandomAccessFile or heap buffer overhead
 * present in default Parquet input streams, with full byte-range seekability for cloud object storage.
 */
public class NioChannelInputFile implements InputFile {

    private final File file;
    private final URI uri;
    private final Long explicitLength;

    public NioChannelInputFile(File file) {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }
        this.file = file;
        this.uri = null;
        this.explicitLength = null;
    }

    public NioChannelInputFile(URI uri) {
        this(uri, null);
    }

    public NioChannelInputFile(URI uri, Long explicitLength) {
        if (uri == null) {
            throw new IllegalArgumentException("URI cannot be null");
        }
        this.file = null;
        this.uri = uri;
        this.explicitLength = explicitLength;
    }

    @Override
    public long getLength() throws IOException {
        if (file != null) {
            return file.length();
        }
        if (explicitLength != null && explicitLength >= 0) {
            return explicitLength;
        }
        return StorageRegistry.requireFileSystem(uri).size(uri);
    }

    @Override
    public SeekableInputStream newStream() throws IOException {
        final SeekableByteChannel channel;
        if (file != null) {
            channel = FileChannel.open(file.toPath(), StandardOpenOption.READ);
        } else {
            channel = StorageRegistry.requireFileSystem(uri).openSeekable(uri);
        }

        return new SeekableInputStream() {
            private long position = 0;

            @Override
            public long getPos() throws IOException {
                return position;
            }

            @Override
            public void seek(long newPos) throws IOException {
                position = newPos;
                channel.position(newPos);
            }

            @Override
            public int read() throws IOException {
                byte[] b = new byte[1];
                int n = read(b, 0, 1);
                return n < 0 ? -1 : (b[0] & 0xFF);
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (channel instanceof FileChannel fc) {
                    ByteBuffer buf = ByteBuffer.wrap(b, off, len);
                    int read = fc.read(buf, position);
                    if (read > 0) {
                        position += read;
                    }
                    return read;
                }
                channel.position(position);
                ByteBuffer buf = ByteBuffer.wrap(b, off, len);
                int read = channel.read(buf);
                if (read > 0) {
                    position += read;
                }
                return read;
            }

            @Override
            public void readFully(byte[] bytes) throws IOException {
                readFully(bytes, 0, bytes.length);
            }

            @Override
            public void readFully(byte[] bytes, int start, int len) throws IOException {
                ByteBuffer buf = ByteBuffer.wrap(bytes, start, len);
                while (buf.hasRemaining()) {
                    int read;
                    if (channel instanceof FileChannel fc) {
                        read = fc.read(buf, position);
                    } else {
                        channel.position(position);
                        read = channel.read(buf);
                    }
                    if (read < 0) {
                        throw new EOFException("Reached EOF while reading fully from channel");
                    }
                    position += read;
                }
            }

            @Override
            public int read(ByteBuffer buf) throws IOException {
                int read;
                if (channel instanceof FileChannel fc) {
                    read = fc.read(buf, position);
                } else {
                    channel.position(position);
                    read = channel.read(buf);
                }
                if (read > 0) {
                    position += read;
                }
                return read;
            }

            @Override
            public void readFully(ByteBuffer buf) throws IOException {
                while (buf.hasRemaining()) {
                    int read;
                    if (channel instanceof FileChannel fc) {
                        read = fc.read(buf, position);
                    } else {
                        channel.position(position);
                        read = channel.read(buf);
                    }
                    if (read < 0) {
                        throw new EOFException("Reached EOF while reading fully from channel");
                    }
                    position += read;
                }
            }

            @Override
            public void close() throws IOException {
                channel.close();
            }
        };
    }
}
