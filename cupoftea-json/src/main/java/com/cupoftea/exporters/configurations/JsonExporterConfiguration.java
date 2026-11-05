package com.cupoftea.exporters.configurations;

import java.util.Objects;

/**
 * Configuration options for JSON export.
 */
public class JsonExporterConfiguration {

    public enum Mode {
        COMPACT,
        PRETTY,
        LINES
    }

    private Mode mode = Mode.COMPACT;
    private int bufferSize = 128 * 1024;

    public JsonExporterConfiguration() {
    }

    public static JsonExporterConfiguration builder() {
        return new JsonExporterConfiguration();
    }

    public Mode getMode() {
        return mode;
    }

    public JsonExporterConfiguration setMode(Mode mode) {
        this.mode = mode != null ? mode : Mode.COMPACT;
        return this;
    }

    public JsonExporterConfiguration mode(Mode mode) {
        return setMode(mode);
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public JsonExporterConfiguration setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize > 0 ? bufferSize : 128 * 1024;
        return this;
    }

    public JsonExporterConfiguration bufferSize(int bufferSize) {
        return setBufferSize(bufferSize);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JsonExporterConfiguration that = (JsonExporterConfiguration) o;
        return bufferSize == that.bufferSize && mode == that.mode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, bufferSize);
    }
}
