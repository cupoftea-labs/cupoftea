package com.cupoftea.parquet;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

import org.apache.parquet.io.InputFile;
import org.apache.parquet.io.SeekableInputStream;

/**
 * High-performance NIO {@link InputFile} backed by a local file and {@link FileChannel}.
 * Provides direct byte channel reads without the RandomAccessFile or heap buffer overhead
 * present in default Parquet input streams.
 */
public class NioChannelInputFile implements InputFile {

    private final File file;

    public NioChannelInputFile(File file) {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }
        this.file = file;
    }

    @Override
    public long getLength() throws IOException {
        return file.length();
    }

    @Override
    public SeekableInputStream newStream() throws IOException {
        FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ);
        return new SeekableInputStream() {
            private long position = 0;

            @Override
            public long getPos() throws IOException {
                return position;
            }

            @Override
            public void seek(long newPos) throws IOException {
                position = newPos;
            }

            @Override
            public int read() throws IOException {
                byte[] b = new byte[1];
                int n = read(b, 0, 1);
                return n < 0 ? -1 : (b[0] & 0xFF);
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                ByteBuffer buf = ByteBuffer.wrap(b, off, len);
                int read = channel.read(buf, position);
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
                    int read = channel.read(buf, position);
                    if (read < 0) {
                        throw new EOFException("Reached EOF while reading fully from channel");
                    }
                    position += read;
                }
            }

            @Override
            public int read(ByteBuffer buf) throws IOException {
                int read = channel.read(buf, position);
                if (read > 0) {
                    position += read;
                }
                return read;
            }

            @Override
            public void readFully(ByteBuffer buf) throws IOException {
                while (buf.hasRemaining()) {
                    int read = channel.read(buf, position);
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
