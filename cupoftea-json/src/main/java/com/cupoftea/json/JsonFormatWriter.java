package com.cupoftea.json;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exporters.configurations.JsonExporterConfiguration;
import com.cupoftea.spi.FormatWriter;
import com.cupoftea.spi.WriteOptions;
import com.cupoftea.table.Column;
import com.cupoftea.table.interfaces.IDataFrame;
import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.DictionaryColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.FloatColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

public class JsonFormatWriter implements FormatWriter {

    private final JsonFactory jsonFactory = new JsonFactory();
    private final ObjectMapper fallbackMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public String format() {
        return "json";
    }

    @Override
    public void write(IDataFrame dataFrame, WriteOptions options) {
        if (options == null) {
            throw new ExportException("WriteOptions cannot be null");
        }
        File file = options.getFile();
        if (file == null && options.getPath() != null) {
            file = new File(options.getPath());
        }
        if (file == null) {
            throw new ExportException("Target file or path must be provided");
        }

        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        JsonExporterConfiguration config = resolveConfiguration(options);

        try {
            writeDirectStreaming(dataFrame, file, config);
        } catch (IOException e) {
            throw new ExportException("Failed to write JSON file: " + file.getPath(), e);
        } catch (Exception e) {
            if (e instanceof ExportException) throw (ExportException) e;
            throw new ExportException("Failed to export JSON file: " + file.getPath(), e);
        }
    }

    private JsonExporterConfiguration resolveConfiguration(WriteOptions options) {
        Object cfgObj = options.getOption("configuration");
        if (cfgObj instanceof JsonExporterConfiguration cfg) {
            return cfg;
        }

        JsonExporterConfiguration config = new JsonExporterConfiguration();

        Object modeObj = options.getOption("mode");
        if (modeObj instanceof JsonExporterConfiguration.Mode mode) {
            config.setMode(mode);
        } else if (modeObj instanceof String strMode) {
            try {
                config.setMode(JsonExporterConfiguration.Mode.valueOf(strMode.trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }

        Boolean compact = options.getOption("compact", null);
        if (compact != null) {
            config.setMode(compact ? JsonExporterConfiguration.Mode.COMPACT : JsonExporterConfiguration.Mode.PRETTY);
        }

        Boolean pretty = options.getOption("pretty", null);
        if (pretty != null) {
            config.setMode(pretty ? JsonExporterConfiguration.Mode.PRETTY : JsonExporterConfiguration.Mode.COMPACT);
        }

        Boolean lines = options.getOption("lines", null);
        if (lines != null && lines) {
            config.setMode(JsonExporterConfiguration.Mode.LINES);
        }

        return config;
    }

    private void writeDirectStreaming(IDataFrame df, File file, JsonExporterConfiguration config) throws IOException {
        if (df == null) {
            fallbackMapper.writeValue(file, null);
            return;
        }

        int rowCount = df.size();
        List<Column> headers = df.getHeaders();
        if (headers == null || headers.isEmpty()) {
            try (OutputStream fos = new BufferedOutputStream(new FileOutputStream(file), config.getBufferSize());
                 JsonGenerator g = jsonFactory.createGenerator(fos)) {
                if (config.getMode() == JsonExporterConfiguration.Mode.PRETTY) {
                    g.useDefaultPrettyPrinter();
                }
                if (config.getMode() != JsonExporterConfiguration.Mode.LINES) {
                    g.writeStartArray();
                    for (int r = 0; r < rowCount; r++) {
                        g.writeStartObject();
                        g.writeEndObject();
                    }
                    g.writeEndArray();
                } else {
                    for (int r = 0; r < rowCount; r++) {
                        g.writeStartObject();
                        g.writeEndObject();
                        g.writeRaw('\n');
                    }
                }
            }
            return;
        }

        int numCols = headers.size();
        SerializedString[] fieldNames = new SerializedString[numCols];
        ColumnVector[] vectors = new ColumnVector[numCols];
        SerializedString[][] dictCache = new SerializedString[numCols][];

        for (int c = 0; c < numCols; c++) {
            Column col = headers.get(c);
            fieldNames[c] = new SerializedString(col.columnName());
            ColumnVector vec = df.getColumnVector(col.columnName());
            vectors[c] = vec;
            if (vec instanceof DictionaryColumnVector dcv) {
                int dictSize = dcv.getDictionarySize();
                String[] rawDict = dcv.getDictionary();
                dictCache[c] = new SerializedString[dictSize];
                for (int d = 0; d < dictSize; d++) {
                    if (rawDict[d] != null) {
                        dictCache[c][d] = new SerializedString(rawDict[d]);
                    }
                }
            }
        }

        try (OutputStream fos = new BufferedOutputStream(new FileOutputStream(file), config.getBufferSize());
             JsonGenerator g = jsonFactory.createGenerator(fos)) {

            if (config.getMode() == JsonExporterConfiguration.Mode.PRETTY) {
                g.useDefaultPrettyPrinter();
            }

            boolean isLines = config.getMode() == JsonExporterConfiguration.Mode.LINES;
            if (!isLines) {
                g.writeStartArray();
            }

            for (int r = 0; r < rowCount; r++) {
                g.writeStartObject();
                for (int c = 0; c < numCols; c++) {
                    g.writeFieldName(fieldNames[c]);
                    ColumnVector vec = vectors[c];

                    if (vec == null || vec.isNull(r)) {
                        g.writeNull();
                    } else if (vec instanceof IntColumnVector iv) {
                        g.writeNumber(iv.getInt(r));
                    } else if (vec instanceof LongColumnVector lv) {
                        g.writeNumber(lv.getLong(r));
                    } else if (vec instanceof DoubleColumnVector dv) {
                        g.writeNumber(dv.getDouble(r));
                    } else if (vec instanceof FloatColumnVector fv) {
                        g.writeNumber(fv.getFloat(r));
                    } else if (vec instanceof BooleanColumnVector bv) {
                        g.writeBoolean(bv.getBoolean(r));
                    } else if (vec instanceof DictionaryColumnVector dcv) {
                        int code = dcv.getCodeUnchecked(r);
                        SerializedString[] cache = dictCache[c];
                        if (cache != null && code >= 0 && code < cache.length && cache[code] != null) {
                            g.writeString(cache[code]);
                        } else {
                            String s = dcv.getString(r);
                            if (s != null) {
                                g.writeString(s);
                            } else {
                                g.writeNull();
                            }
                        }
                    } else if (vec instanceof StringColumnVector sv) {
                        String s = sv.getString(r);
                        if (s != null) {
                            g.writeString(s);
                        } else {
                            g.writeNull();
                        }
                    } else {
                        Object obj = vec.getObject(r);
                        writeGenericObject(g, obj);
                    }
                }
                g.writeEndObject();
                if (isLines) {
                    g.writeRaw('\n');
                }
            }

            if (!isLines) {
                g.writeEndArray();
            }
        }
    }

    private static void writeGenericObject(JsonGenerator g, Object obj) throws IOException {
        if (obj == null) {
            g.writeNull();
        } else if (obj instanceof Number num) {
            if (num instanceof Integer || num instanceof Short || num instanceof Byte) {
                g.writeNumber(num.intValue());
            } else if (num instanceof Long) {
                g.writeNumber(num.longValue());
            } else if (num instanceof Double) {
                g.writeNumber(num.doubleValue());
            } else if (num instanceof Float) {
                g.writeNumber(num.floatValue());
            } else {
                g.writeNumber(num.toString());
            }
        } else if (obj instanceof Boolean b) {
            g.writeBoolean(b);
        } else {
            g.writeString(obj.toString());
        }
    }
}
