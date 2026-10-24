package com.cupoftea.io;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import com.cupoftea.spi.FormatWriter;
import com.cupoftea.spi.WriteOptions;
import com.cupoftea.table.interfaces.IDataFrame;

/**
 * Fluent API for writing data frames into various formats.
 * Example usage:
 * <pre>{@code
 * df.write().csv("data.csv");
 * }</pre>
 */
public class DataFrameWriter {

    private final IDataFrame dataFrame;
    private final Map<String, Object> options = new HashMap<>();

    public DataFrameWriter(IDataFrame dataFrame) {
        this.dataFrame = dataFrame;
    }

    public DataFrameWriter option(String key, Object value) {
        this.options.put(key, value);
        return this;
    }

    public DataFrameWriter options(Map<String, Object> options) {
        if (options != null) {
            this.options.putAll(options);
        }
        return this;
    }

    public void write(String format, WriteOptions writeOptions) {
        FormatWriter writer = FormatRegistry.requireWriter(format);
        writer.write(dataFrame, writeOptions);
    }

    private WriteOptions.Builder buildOptions() {
        return WriteOptions.builder().options(options);
    }

    // --- CSV ---

    public void csv(String path) {
        csv(new File(path));
    }

    public void csv(File file) {
        write("csv", buildOptions().file(file).build());
    }

    public void csv(File file, Object configuration) {
        write("csv", buildOptions().file(file).option("configuration", configuration).build());
    }

    // --- JSON ---

    public void json(String path) {
        json(new File(path));
    }

    public void json(File file) {
        write("json", buildOptions().file(file).build());
    }

    // --- PARQUET ---

    public void parquet(String path) {
        parquet(new File(path));
    }

    public void parquet(File file) {
        write("parquet", buildOptions().file(file).build());
    }

    // --- ARROW / FEATHER ---

    public void arrow(String path) {
        arrow(new File(path));
    }

    public void arrow(File file) {
        write("arrow", buildOptions().file(file).build());
    }

    public void feather(String path) {
        feather(new File(path));
    }

    public void feather(File file) {
        write("feather", buildOptions().file(file).build());
    }

    // --- YAML ---

    public void yaml(String path) {
        yaml(new File(path));
    }

    public void yaml(File file) {
        write("yaml", buildOptions().file(file).build());
    }

    // --- GENERIC FORMAT ---

    public void format(String format, String path) {
        format(format, new File(path));
    }

    public void format(String format, File file) {
        write(format, buildOptions().file(file).build());
    }
}
