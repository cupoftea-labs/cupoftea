package com.cupoftea.spi;

import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.cupoftea.table.interfaces.IDataFrame;

/**
 * Options passed to {@link FormatReader} when ingesting tabular data.
 */
public class ReadOptions {

    private final File file;
    private final String path;
    private final String text;
    private final URL url;
    private final Object sourceObject;
    private final Map<String, Class<?>> schema;
    private final boolean hasHeaders;
    private final IDataFrame targetDataFrame;
    private final Map<String, Object> options;

    private ReadOptions(Builder builder) {
        this.file = builder.file;
        this.path = builder.path != null ? builder.path : (builder.file != null ? builder.file.getPath() : null);
        this.text = builder.text;
        this.url = builder.url;
        this.sourceObject = builder.sourceObject;
        this.schema = builder.schema != null ? Collections.unmodifiableMap(builder.schema) : Collections.emptyMap();
        this.hasHeaders = builder.hasHeaders;
        this.targetDataFrame = builder.targetDataFrame;
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

    public String getText() {
        return text;
    }

    public URL getUrl() {
        return url;
    }

    public Object getSourceObject() {
        return sourceObject;
    }

    public Map<String, Class<?>> getSchema() {
        return schema;
    }

    public boolean hasHeaders() {
        return hasHeaders;
    }

    public IDataFrame getTargetDataFrame() {
        return targetDataFrame;
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
        private String text;
        private URL url;
        private Object sourceObject;
        private Map<String, Class<?>> schema;
        private boolean hasHeaders = true;
        private IDataFrame targetDataFrame;
        private final Map<String, Object> options = new HashMap<>();

        public Builder file(File file) {
            this.file = file;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder url(URL url) {
            this.url = url;
            return this;
        }

        public Builder sourceObject(Object sourceObject) {
            this.sourceObject = sourceObject;
            return this;
        }

        public Builder schema(Map<String, Class<?>> schema) {
            this.schema = schema;
            return this;
        }

        public Builder hasHeaders(boolean hasHeaders) {
            this.hasHeaders = hasHeaders;
            return this;
        }

        public Builder targetDataFrame(IDataFrame targetDataFrame) {
            this.targetDataFrame = targetDataFrame;
            return this;
        }

        public Builder option(String key, Object value) {
            this.options.put(key, value);
            return this;
        }

        public Builder options(Map<String, Object> options) {
            if (options != null) {
                this.options.putAll(options);
            }
            return this;
        }

        public ReadOptions build() {
            return new ReadOptions(this);
        }
    }
}
