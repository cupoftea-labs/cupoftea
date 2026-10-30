package com.cupoftea.csv.lazy;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.BitSet;

import com.cupoftea.CupOfTea;
import com.cupoftea.csv.fast.FastCsvReader;
import com.cupoftea.csv.fast.SimdDetector;
import com.cupoftea.importers.configurations.CSVImporterConfiguration;
import com.cupoftea.lazy.PartitionReader;

/**
 * Memory-mapped streaming partition reader for high-performance out-of-core CSV evaluation.
 */
public class CsvMmapPartitionReader implements PartitionReader {

    private final FileChannel channel;
    private final long startOffset;
    private final long endOffset;
    private final String[] colNames;
    private final Class<?>[] colTypes;
    private final byte[] colTypeIds;
    private final CSVImporterConfiguration config;
    private boolean consumed = false;

    public CsvMmapPartitionReader(
            FileChannel channel,
            long startOffset,
            long endOffset,
            String[] colNames,
            Class<?>[] colTypes,
            byte[] colTypeIds,
            CSVImporterConfiguration config) {
        this.channel = channel;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.colNames = colNames;
        this.colTypes = colTypes;
        this.colTypeIds = colTypeIds;
        this.config = config;
    }

    @Override
    public CupOfTea nextBatch() throws IOException {
        if (consumed) {
            return null;
        }
        consumed = true;

        long len = endOffset - startOffset;
        if (len <= 0) {
            return null;
        }

        MappedByteBuffer mapped = channel.map(FileChannel.MapMode.READ_ONLY, startOffset, len);
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

    @Override
    public void close() throws IOException {
        // Channel is managed by the CsvLazySource or closed on completion
    }
}
