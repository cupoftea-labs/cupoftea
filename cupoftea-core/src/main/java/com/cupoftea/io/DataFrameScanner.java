package com.cupoftea.io;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import com.cupoftea.lazy.LazyCupOfTea;
import com.cupoftea.lazy.LazySource;
import com.cupoftea.spi.FormatScanner;
import com.cupoftea.spi.ScanOptions;
import com.cupoftea.table.Schema;

/**
 * Fluent API builder for creating lazy streaming DataFrames from various tabular formats.
 * Example usage:
 * <pre>{@code
 * CupOfTea result = CupOfTea.scan()
 *     .csv("measurements.txt")
 *     .groupBy("station")
 *     .agg(Aggregation.MIN.of("temperature"), Aggregation.MEAN.of("temperature"), Aggregation.MAX.of("temperature"))
 *     .collect();
 * }</pre>
 */
public class DataFrameScanner {

    private final Map<String, Object> options = new HashMap<>();
    private Schema schema;
    private Map<String, Class<?>> schemaMap;
    private Boolean hasHeaders;
    private Character delimiter;
    private int batchSize = 65_536;
    private int chunkSizeBytes = 32 * 1024 * 1024;
    private boolean parallel = true;
    private ExecutorService executor = null;

    public DataFrameScanner() {
    }

    public DataFrameScanner option(String key, Object value) {
        if (key != null) {
            this.options.put(key, value);
        }
        return this;
    }

    public DataFrameScanner options(Map<String, Object> options) {
        if (options != null) {
            this.options.putAll(options);
        }
        return this;
    }

    public DataFrameScanner schema(Schema schema) {
        this.schema = schema;
        if (schema != null) {
            this.schemaMap = schema.toMap();
            this.options.put("schema", schema);
        }
        return this;
    }

    public DataFrameScanner schema(Map<String, Class<?>> schemaMap) {
        this.schemaMap = schemaMap;
        return this;
    }

    public DataFrameScanner hasHeaders(boolean hasHeaders) {
        this.hasHeaders = hasHeaders;
        return this;
    }

    public DataFrameScanner delimiter(char delimiter) {
        this.delimiter = delimiter;
        this.options.put("delimiter", delimiter);
        return this;
    }

    public DataFrameScanner batchSize(int batchSize) {
        if (batchSize > 0) {
            this.batchSize = batchSize;
        }
        return this;
    }

    public DataFrameScanner chunkSize(int chunkSizeBytes) {
        if (chunkSizeBytes > 0) {
            this.chunkSizeBytes = chunkSizeBytes;
        }
        return this;
    }

    public DataFrameScanner parallel(boolean parallel) {
        this.parallel = parallel;
        return this;
    }

    public DataFrameScanner executor(ExecutorService executor) {
        this.executor = executor;
        this.parallel = true;
        return this;
    }

    public LazyCupOfTea scan(String format, ScanOptions scanOptions) {
        FormatScanner scanner = FormatRegistry.requireScanner(format);
        LazySource source = scanner.scan(scanOptions);
        return new LazyCupOfTea(source, scanOptions);
    }

    private ScanOptions.Builder buildOptions() {
        ScanOptions.Builder b = ScanOptions.builder()
                .options(options)
                .schema(schema)
                .schema(schemaMap)
                .delimiter(delimiter)
                .batchSize(batchSize)
                .chunkSizeBytes(chunkSizeBytes)
                .parallel(parallel)
                .executor(executor);
        if (hasHeaders != null) {
            b.hasHeaders(hasHeaders);
        }
        return b;
    }

    // --- CSV Scanning ---

    public LazyCupOfTea csv(String path) {
        return csv(new File(path));
    }

    public LazyCupOfTea csv(File file) {
        return scan("csv", buildOptions().file(file).build());
    }

    public LazyCupOfTea csv(String path, boolean hasHeaders) {
        return csv(new File(path), hasHeaders);
    }

    public LazyCupOfTea csv(File file, boolean hasHeaders) {
        return scan("csv", buildOptions().file(file).hasHeaders(hasHeaders).build());
    }

    public LazyCupOfTea csv(String path, Schema schema) {
        return csv(new File(path), schema);
    }

    public LazyCupOfTea csv(File file, Schema schema) {
        return scan("csv", buildOptions().file(file).schema(schema).build());
    }

    public LazyCupOfTea csv(String path, boolean hasHeaders, Schema schema) {
        return csv(new File(path), hasHeaders, schema);
    }

    public LazyCupOfTea csv(File file, boolean hasHeaders, Schema schema) {
        return scan("csv", buildOptions().file(file).hasHeaders(hasHeaders).schema(schema).build());
    }

    public LazyCupOfTea csv(String path, Object configuration) {
        return csv(new File(path), configuration);
    }

    public LazyCupOfTea csv(File file, Object configuration) {
        if (configuration instanceof ScanOptions so) {
            return scan("csv", so);
        }
        return scan("csv", buildOptions().file(file).option("configuration", configuration).build());
    }

    // --- Extensibility for other formats ---

    public LazyCupOfTea json(String path) {
        return json(new File(path));
    }

    public LazyCupOfTea json(File file) {
        return scan("json", buildOptions().file(file).build());
    }

    public LazyCupOfTea parquet(String path) {
        return parquet(new File(path));
    }

    public LazyCupOfTea parquet(File file) {
        return scan("parquet", buildOptions().file(file).build());
    }
}
