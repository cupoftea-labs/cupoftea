package com.cupoftea.csv.lazy;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.BitSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.cupoftea.CupOfTea;
import com.cupoftea.csv.fast.FastCsvReader;
import com.cupoftea.csv.fast.SimdDetector;
import com.cupoftea.importers.configurations.CSVImporterConfiguration;
import com.cupoftea.lazy.PartitionReader;
import com.cupoftea.lazy.StreamingDirectAggregator;

/**
 * Memory-mapped streaming partition reader for high-performance out-of-core CSV evaluation.
 * Supports dynamic work-stealing partition execution with an {@link AtomicLong} cursor
 * across memory-mapped channel segments.
 */
public class CsvMmapPartitionReader implements PartitionReader {

    private final FileChannel channel;
    private final AtomicLong cursor;
    private final long fileLength;
    private final long dataStartOffset;
    private final int segmentSize;
    private final AtomicInteger activeReaders;
    private final String[] colNames;
    private final Class<?>[] colTypes;
    private final byte[] colTypeIds;
    private final CSVImporterConfiguration config;

    private final long staticStart;
    private final long staticEnd;
    private boolean consumed = false;
    private boolean closed = false;

    /**
     * Legacy constructor for static chunk allocation.
     */
    public CsvMmapPartitionReader(
            FileChannel channel,
            long startOffset,
            long endOffset,
            String[] colNames,
            Class<?>[] colTypes,
            byte[] colTypeIds,
            CSVImporterConfiguration config) {
        this(channel, null, 0, startOffset, (int) Math.max(0, endOffset - startOffset), null,
                colNames, colTypes, colTypeIds, config, startOffset, endOffset);
    }

    /**
     * Dynamic work-stealing constructor using an AtomicLong cursor.
     */
    public CsvMmapPartitionReader(
            FileChannel channel,
            AtomicLong cursor,
            long fileLength,
            long dataStartOffset,
            int segmentSize,
            AtomicInteger activeReaders,
            String[] colNames,
            Class<?>[] colTypes,
            byte[] colTypeIds,
            CSVImporterConfiguration config) {
        this(channel, cursor, fileLength, dataStartOffset, segmentSize, activeReaders,
                colNames, colTypes, colTypeIds, config, -1, -1);
    }

    private CsvMmapPartitionReader(
            FileChannel channel,
            AtomicLong cursor,
            long fileLength,
            long dataStartOffset,
            int segmentSize,
            AtomicInteger activeReaders,
            String[] colNames,
            Class<?>[] colTypes,
            byte[] colTypeIds,
            CSVImporterConfiguration config,
            long staticStart,
            long staticEnd) {
        this.channel = channel;
        this.cursor = cursor;
        this.fileLength = fileLength;
        this.dataStartOffset = dataStartOffset;
        this.segmentSize = segmentSize;
        this.activeReaders = activeReaders;
        this.colNames = colNames;
        this.colTypes = colTypes;
        this.colTypeIds = colTypeIds;
        this.config = config;
        this.staticStart = staticStart;
        this.staticEnd = staticEnd;
    }

    @Override
    public CupOfTea nextBatch() throws IOException {
        if (cursor == null) {
            if (consumed) {
                return null;
            }
            consumed = true;

            long len = staticEnd - staticStart;
            if (len <= 0) {
                return null;
            }

            MappedByteBuffer mapped = channel.map(FileChannel.MapMode.READ_ONLY, staticStart, len);
            boolean useSimd = config.isSimdEnabled() && SimdDetector.isSupported();
            Charset charset = config.getCharset();

            FastCsvReader.ChunkResult res = FastCsvReader.executeChunk(
                    null, mapped, 0, (int) len,
                    (byte) config.getDelimiter(),
                    (byte) config.getQuoteChar(),
                    (byte) config.getEscapeChar(),
                    colNames.length, colTypes, colTypeIds,
                    charset, config, useSimd
            );

            return FastCsvReader.assembleChunk(colNames, colTypes, colTypeIds, res);
        }

        boolean useSimd = config.isSimdEnabled() && SimdDetector.isSupported();
        Charset charset = config.getCharset();

        while (true) {
            long current = cursor.addAndGet(segmentSize) - segmentSize;
            if (current >= fileLength) {
                return null;
            }

            long start = (current == dataStartOffset) ? dataStartOffset : CsvLazySource.findNextLineBreak(channel, current, fileLength);
            long end = (current + segmentSize >= fileLength) ? fileLength : CsvLazySource.findNextLineBreak(channel, current + segmentSize, fileLength);

            if (end <= start) {
                continue;
            }

            long len = end - start;
            MappedByteBuffer mapped = channel.map(FileChannel.MapMode.READ_ONLY, start, len);

            FastCsvReader.ChunkResult res = FastCsvReader.executeChunk(
                    null, mapped, 0, (int) len,
                    (byte) config.getDelimiter(),
                    (byte) config.getQuoteChar(),
                    (byte) config.getEscapeChar(),
                    colNames.length, colTypes, colTypeIds,
                    charset, config, useSimd
            );

            return FastCsvReader.assembleChunk(colNames, colTypes, colTypeIds, res);
        }
    }

    @Override
    public boolean supportsDirectAggregation() {
        return true;
    }

    @Override
    public void aggregateDirect(StreamingDirectAggregator aggregator) throws IOException {
        aggregator.bindSource(colNames, colTypes);
        boolean useSimd = config.isSimdEnabled() && SimdDetector.isSupported();
        Charset charset = config.getCharset();

        if (cursor == null) {
            if (consumed) {
                return;
            }
            consumed = true;

            long len = staticEnd - staticStart;
            if (len <= 0) {
                return;
            }

            MappedByteBuffer mapped = channel.map(FileChannel.MapMode.READ_ONLY, staticStart, len);

            FastCsvReader.executeChunkDirect(
                    null, mapped, 0, (int) len,
                    (byte) config.getDelimiter(),
                    (byte) config.getQuoteChar(),
                    (byte) config.getEscapeChar(),
                    colNames, colTypes, colTypeIds,
                    charset, config, useSimd, aggregator
            );
            return;
        }

        while (true) {
            long current = cursor.addAndGet(segmentSize) - segmentSize;
            if (current >= fileLength) {
                break;
            }

            long start = (current == dataStartOffset) ? dataStartOffset : CsvLazySource.findNextLineBreak(channel, current, fileLength);
            long end = (current + segmentSize >= fileLength) ? fileLength : CsvLazySource.findNextLineBreak(channel, current + segmentSize, fileLength);

            if (end <= start) {
                continue;
            }

            long len = end - start;
            MappedByteBuffer mapped = channel.map(FileChannel.MapMode.READ_ONLY, start, len);

            FastCsvReader.executeChunkDirect(
                    null, mapped, 0, (int) len,
                    (byte) config.getDelimiter(),
                    (byte) config.getQuoteChar(),
                    (byte) config.getEscapeChar(),
                    colNames, colTypes, colTypeIds,
                    charset, config, useSimd, aggregator
            );
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            if (activeReaders != null && activeReaders.decrementAndGet() == 0) {
                if (channel.isOpen()) {
                    channel.close();
                }
            }
        }
    }
}
