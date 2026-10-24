package com.cupoftea.io;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.spi.FormatReader;
import com.cupoftea.spi.ReadOptions;
import com.cupoftea.table.interfaces.IDataFrame;

/**
 * Fluent API for reading data frames from various formats.
 * Example usage:
 * <pre>{@code
 * CupOfTea df = CupOfTea.read().csv("data.csv");
 * }</pre>
 */
public class DataFrameReader {

    private final Map<String, Object> options = new HashMap<>();
    private Map<String, Class<?>> schema;
    private Boolean hasHeaders;
    private IDataFrame targetDataFrame;

    public DataFrameReader() {
    }

    public DataFrameReader option(String key, Object value) {
        this.options.put(key, value);
        return this;
    }

    public DataFrameReader options(Map<String, Object> options) {
        if (options != null) {
            this.options.putAll(options);
        }
        return this;
    }

    public DataFrameReader schema(Map<String, Class<?>> schema) {
        this.schema = schema;
        return this;
    }

    public DataFrameReader schema(com.cupoftea.table.Schema schema) {
        if (schema != null) {
            this.schema = schema.toMap();
            this.options.put("schema", schema);
        }
        return this;
    }

    public DataFrameReader hasHeaders(boolean hasHeaders) {
        this.hasHeaders = hasHeaders;
        return this;
    }

    public DataFrameReader target(IDataFrame targetDataFrame) {
        this.targetDataFrame = targetDataFrame;
        return this;
    }

    public CupOfTea read(String format, ReadOptions readOptions) {
        FormatReader reader = FormatRegistry.requireReader(format);
        IDataFrame df = reader.read(readOptions);
        return new CupOfTea(df);
    }

    private ReadOptions.Builder buildOptions() {
        ReadOptions.Builder b = ReadOptions.builder()
                .options(options)
                .schema(schema)
                .targetDataFrame(targetDataFrame);
        if (hasHeaders != null) {
            b.hasHeaders(hasHeaders);
        }
        return b;
    }

    // --- CSV ---

    public CupOfTea csv(String path) {
        return csv(new File(path));
    }

    public CupOfTea csv(File file) {
        return read("csv", buildOptions().file(file).build());
    }

    public CupOfTea csv(File file, boolean hasHeaders) {
        return read("csv", buildOptions().file(file).hasHeaders(hasHeaders).build());
    }

    public CupOfTea csv(File file, Object configuration) {
        return read("csv", buildOptions().file(file).option("configuration", configuration).build());
    }

    // --- JSON ---

    public CupOfTea json(String pathOrJson) {
        File file = new File(pathOrJson);
        if (file.exists() && file.isFile()) {
            return json(file);
        }
        return read("json", buildOptions().text(pathOrJson).build());
    }

    public CupOfTea json(File file) {
        return read("json", buildOptions().file(file).build());
    }

    public CupOfTea json(File file, Map<String, Class<?>> schema) {
        return read("json", buildOptions().file(file).schema(schema).build());
    }

    public CupOfTea json(String json, Map<String, Class<?>> schema) {
        File file = new File(json);
        if (file.exists() && file.isFile()) {
            return read("json", buildOptions().file(file).schema(schema).build());
        }
        return read("json", buildOptions().text(json).schema(schema).build());
    }

    public CupOfTea json(Object source) {
        if (source instanceof File) {
            return json((File) source);
        }
        if (source instanceof String) {
            return json((String) source);
        }
        return read("json", buildOptions().sourceObject(source).build());
    }

    // --- EXCEL ---

    public CupOfTea excel(String path) {
        return excel(new File(path), true);
    }

    public CupOfTea excel(String path, boolean hasHeaders) {
        return excel(new File(path), hasHeaders);
    }

    public CupOfTea excel(File file) {
        return excel(file, true);
    }

    public CupOfTea excel(File file, boolean hasHeaders) {
        return read("excel", buildOptions().file(file).hasHeaders(hasHeaders).build());
    }

    public CupOfTea excel(File file, Object configuration) {
        return read("excel", buildOptions().file(file).option("configuration", configuration).build());
    }

    // --- PARQUET ---

    public CupOfTea parquet(String path) {
        return parquet(new File(path));
    }

    public CupOfTea parquet(File file) {
        return read("parquet", buildOptions().file(file).build());
    }

    // --- ARROW / FEATHER ---

    public CupOfTea arrow(String path) {
        return arrow(new File(path));
    }

    public CupOfTea arrow(File file) {
        return read("arrow", buildOptions().file(file).build());
    }

    public CupOfTea feather(String path) {
        return feather(new File(path));
    }

    public CupOfTea feather(File file) {
        return read("feather", buildOptions().file(file).build());
    }

    // --- HTML ---

    public CupOfTea html(String pathOrHtml) {
        File file = new File(pathOrHtml);
        if (file.exists() && file.isFile()) {
            return html(file);
        }
        return read("html", buildOptions().text(pathOrHtml).build());
    }

    public CupOfTea html(File file) {
        return read("html", buildOptions().file(file).build());
    }

    public CupOfTea html(URL url) {
        return read("html", buildOptions().url(url).build());
    }

    public CupOfTea html(URL url, int timeoutMillis, int maxBodySizeBytes) {
        return read("html", buildOptions().url(url)
                .option("timeoutMillis", timeoutMillis)
                .option("maxBodySizeBytes", maxBodySizeBytes)
                .build());
    }

    public CupOfTea html(Object doc) {
        return read("html", buildOptions().sourceObject(doc).build());
    }

    // --- YAML ---

    public CupOfTea yaml(String pathOrYaml) {
        File file = new File(pathOrYaml);
        if (file.exists() && file.isFile()) {
            return yaml(file);
        }
        return read("yaml", buildOptions().text(pathOrYaml).build());
    }

    public CupOfTea yaml(File file) {
        return read("yaml", buildOptions().file(file).build());
    }

    // --- XML ---

    public CupOfTea xml(String pathOrXml) {
        File file = new File(pathOrXml);
        if (file.exists() && file.isFile()) {
            return xml(file);
        }
        return read("xml", buildOptions().text(pathOrXml).build());
    }

    public CupOfTea xml(File file) {
        return read("xml", buildOptions().file(file).build());
    }

    public CupOfTea xml(Object doc) {
        return read("xml", buildOptions().sourceObject(doc).build());
    }

    // --- GENERIC FORMAT ---

    public FormatReaderSpec format(String format) {
        return new FormatReaderSpec(this, format);
    }

    public static class FormatReaderSpec {
        private final DataFrameReader reader;
        private final String format;

        public FormatReaderSpec(DataFrameReader reader, String format) {
            this.reader = reader;
            this.format = format;
        }

        public FormatReaderSpec option(String key, Object value) {
            reader.option(key, value);
            return this;
        }

        public CupOfTea load(String path) {
            return load(new File(path));
        }

        public CupOfTea load(File file) {
            return reader.read(format, reader.buildOptions().file(file).build());
        }
    }
}
