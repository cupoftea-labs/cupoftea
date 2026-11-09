package com.cupoftea.spi;

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Options passed to {@link FormatWriter} when exporting tabular data.
 */
public class WriteOptions {

    private final File file;
    private final String path;
    private final URI uri;
    private final Map<String, Object> options;

    private WriteOptions(Builder builder) {
        this.file = builder.file;
        this.path = builder.path != null ? builder.path : (builder.file != null ? builder.file.getPath() : null);
        this.uri = builder.uri != null ? builder.uri : (this.path != null && this.path.contains("://") ? tryParseUri(this.path) : null);
        this.options = Collections.unmodifiableMap(new HashMap<>(builder.options));
    }

    private static URI tryParseUri(String p) {
        try {
            return URI.create(p);
        } catch (Exception ignored) {
            return null;
        }
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

    public URI getUri() {
        return uri;
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
        private URI uri;
        private final Map<String, Object> options = new HashMap<>();

        public Builder file(File file) {
            this.file = file;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder uri(URI uri) {
            this.uri = uri;
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

        public WriteOptions build() {
            return new WriteOptions(this);
        }
    }
}
