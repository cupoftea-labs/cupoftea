package com.cupoftea.importers.configurations;

import java.util.Objects;

import com.cupoftea.table.Schema;

/**
 * Configuration options for JSON and NDJSON / JSON Lines import and lazy scanning.
 */
public class JsonImporterConfiguration {

    private Schema schema = null;
    private int bufferSize = 128 * 1024;
    private int batchSize = 65_536;
    private boolean lines = false; // true if NDJSON / JSON Lines mode

    public JsonImporterConfiguration() {
    }

    public static JsonImporterConfiguration builder() {
        return new JsonImporterConfiguration();
    }

    public Schema getSchema() {
        return schema;
    }

    public JsonImporterConfiguration setSchema(Schema schema) {
        this.schema = schema;
        return this;
    }

    public JsonImporterConfiguration schema(Schema schema) {
        return setSchema(schema);
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public JsonImporterConfiguration setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize > 0 ? bufferSize : 128 * 1024;
        return this;
    }

    public JsonImporterConfiguration bufferSize(int bufferSize) {
        return setBufferSize(bufferSize);
    }

    public int getBatchSize() {
        return batchSize;
    }

    public JsonImporterConfiguration setBatchSize(int batchSize) {
        this.batchSize = batchSize > 0 ? batchSize : 65_536;
        return this;
    }

    public JsonImporterConfiguration batchSize(int batchSize) {
        return setBatchSize(batchSize);
    }

    public boolean isLines() {
        return lines;
    }

    public JsonImporterConfiguration setLines(boolean lines) {
        this.lines = lines;
        return this;
    }

    public JsonImporterConfiguration lines(boolean lines) {
        return setLines(lines);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JsonImporterConfiguration that = (JsonImporterConfiguration) o;
        return bufferSize == that.bufferSize &&
                batchSize == that.batchSize &&
                lines == that.lines &&
                Objects.equals(schema, that.schema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schema, bufferSize, batchSize, lines);
    }
}
