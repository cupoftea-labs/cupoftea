package com.cupoftea.lazy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.cupoftea.CupOfTea;
import com.cupoftea.table.Column;
import com.cupoftea.table.Schema;

/**
 * {@link LazySource} backed by an in-memory {@link CupOfTea} DataFrame.
 */
public class InMemoryLazySource implements LazySource {

    private final CupOfTea source;

    public InMemoryLazySource(CupOfTea source) {
        if (source == null) {
            throw new IllegalArgumentException("Source DataFrame cannot be null");
        }
        this.source = source;
    }

    @Override
    public Schema getSchema() {
        return source.getSchema();
    }

    @Override
    public List<Column> getHeaders() {
        return source.getHeaders();
    }

    @Override
    public long estimatedSize() {
        return source.size();
    }

    @Override
    public String describe() {
        return "InMemoryDataFrame(rows=" + source.size() + ")";
    }

    @Override
    public List<PartitionReader> getPartitions(ScanContext context) {
        int totalRows = source.size();
        if (totalRows == 0) {
            return Collections.emptyList();
        }

        int batchSize = context.getBatchSize();
        int numPartitions = (int) Math.ceil((double) totalRows / batchSize);
        List<PartitionReader> readers = new ArrayList<>(numPartitions);

        for (int p = 0; p < numPartitions; p++) {
            final int start = p * batchSize;
            final int end = Math.min(start + batchSize, totalRows);
            readers.add(new PartitionReader() {
                private boolean read = false;

                @Override
                public CupOfTea nextBatch() throws IOException {
                    if (read) {
                        return null;
                    }
                    read = true;
                    // Slice batch from source without full copy
                    return source.slice(start, end);
                }
            });
        }

        return readers;
    }
}
