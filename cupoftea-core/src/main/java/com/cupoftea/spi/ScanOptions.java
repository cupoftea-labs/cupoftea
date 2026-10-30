package com.cupoftea.spi;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import com.cupoftea.table.Schema;

/**
 * Options passed to {@link FormatScanner} when configuring lazy tabular scans.
 */
public class ScanOptions {

    private final File file;
    private final String path;
    private final Schema schema;
    private final Map<String, Class<?>> schemaMap;
    private final boolean hasHeaders;
    private final Character delimiter;
    private final int batchSize;
    private final int chunkSizeBytes;
    private final boolean parallel;
    private final ExecutorService executor;
    private final Map<String, Object> options;

    private ScanOptions(Builder builder) {
        this.file = builder.file;
        this.path = builder.path != null ? builder.path : (builder.file != null ? builder.file.getPath() : null);
        this.schema = builder.schema;
        this.schemaMap = builder.schemaMap != null ? Collections.unmodifiableMap(builder.schemaMap) : Collections.emptyMap();
        this.hasHeaders = builder.hasHeaders;
        this.delimiter = builder.delimiter;
        this.batchSize = builder.batchSize;
        this.chunkSizeBytes = builder.chunkSizeBytes;
        this.parallel = builder.parallel;
        this.executor = builder.executor;
        this.options = Collections.unmodifiableMap(new HashMap<>(builder.options));
    }

    public static Builder builder() {
        return new Builder();
    }

    public File getFile() {
        return file;
    }

    public String getPath() {
        return path;
    }

    public Schema getSchema() {
        return schema;
    }

    public Map<String, Class<?>> getSchemaMap() {
        return schemaMap;
    }

    public boolean hasHeaders() {
        return hasHeaders;
    }

    public Character getDelimiter() {
        return delimiter;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public int getChunkSizeBytes() {
        return chunkSizeBytes;
    }

    public boolean isParallel() {
        return parallel;
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public Object getOption(String key) {
        return options.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getOption(String key, T defaultValue) {
        Object val = options.get(key);
        if (val == null) {
            return defaultValue;
        }
        return (T) val;
    }

    public static class Builder {
        private File file;
        private String path;
        private Schema schema;
        private Map<String, Class<?>> schemaMap;
        private boolean hasHeaders = true;
        private Character delimiter;
        private int batchSize = 65_536;
        private int chunkSizeBytes = 32 * 1024 * 1024;
        private boolean parallel = true;
        private ExecutorService executor;
        private final Map<String, Object> options = new HashMap<>();

        public Builder file(File file) {
            this.file = file;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder schema(Schema schema) {
            this.schema = schema;
            if (schema != null) {
                this.schemaMap = schema.toMap();
            }
            return this;
        }

        public Builder schema(Map<String, Class<?>> schemaMap) {
            this.schemaMap = schemaMap;
            return this;
        }

        public Builder hasHeaders(boolean hasHeaders) {
            this.hasHeaders = hasHeaders;
            return this;
        }

        public Builder delimiter(Character delimiter) {
            this.delimiter = delimiter;
            return this;
        }

        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public Builder chunkSizeBytes(int chunkSizeBytes) {
            this.chunkSizeBytes = chunkSizeBytes;
            return this;
        }

        public Builder parallel(boolean parallel) {
            this.parallel = parallel;
            return this;
        }

        public Builder executor(ExecutorService executor) {
            this.executor = executor;
            return this;
        }

        public Builder option(String key, Object value) {
            if (key != null) {
                this.options.put(key, value);
            }
            return this;
        }

        public Builder options(Map<String, Object> options) {
            if (options != null) {
                this.options.putAll(options);
            }
            return this;
        }

        public ScanOptions build() {
            return new ScanOptions(this);
        }
    }
}
