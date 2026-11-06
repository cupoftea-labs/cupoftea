package com.cupoftea.json;

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.spi.FormatReader;
import com.cupoftea.spi.ReadOptions;
import com.cupoftea.table.Column;
import com.cupoftea.table.DataFrame;
import com.cupoftea.table.Schema;
import com.cupoftea.table.interfaces.IDataFrame;
import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.FloatColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

/**
 * Low-level streaming JSON format reader.
 * Parses JSON directly via Jackson {@link JsonParser} tokens into {@link ColumnVector} instances
 * without constructing intermediate DOM structures (e.g. {@code JsonNode} or {@code List<Map<String, Object>>}).
 */
public class JsonFormatReader implements FormatReader {

    private final JsonFactory jsonFactory = new JsonFactory();

    @Override
    public String format() {
        return "json";
    }

    @Override
    public IDataFrame read(ReadOptions options) {
        if (options == null) {
            throw new ImportException("ReadOptions cannot be null");
        }

        try {
            JsonParser parser = createParser(options);
            return readStream(parser, options);
        } catch (ImportException e) {
            throw e;
        } catch (Exception e) {
            throw new ImportException("Failed to read JSON: " + e.getMessage(), e);
        }
    }

    private JsonParser createParser(ReadOptions options) throws Exception {
        if (options.getFile() != null) {
            return jsonFactory.createParser(options.getFile());
        } else if (options.getText() != null) {
            return jsonFactory.createParser(options.getText());
        } else if (options.getPath() != null) {
            File file = new File(options.getPath());
            if (file.exists() && file.isFile()) {
                return jsonFactory.createParser(file);
            } else {
                return jsonFactory.createParser(options.getPath());
            }
        } else if (options.getSourceObject() != null) {
            Object src = options.getSourceObject();
            if (src instanceof File f) {
                return jsonFactory.createParser(f);
            } else if (src instanceof InputStream in) {
                return jsonFactory.createParser(in);
            } else if (src instanceof Reader r) {
                return jsonFactory.createParser(r);
            } else {
                return jsonFactory.createParser(src.toString());
            }
        }
        throw new ImportException("No JSON input source provided in ReadOptions");
    }

    public IDataFrame readStream(JsonParser parser, ReadOptions options) throws Exception {
        CupOfTea target = options.getTargetDataFrame() != null
                ? new CupOfTea(options.getTargetDataFrame())
                : new CupOfTea();

        Map<String, Class<?>> schema = options.getSchema();
        List<Column> existingHeaders = target.getHeaders();
        Map<String, Class<?>> fixedSchema = new LinkedHashMap<>();
        if (existingHeaders != null) {
            for (Column col : existingHeaders) {
                if (col.columnType() != null) {
                    fixedSchema.put(col.columnName(), col.columnType());
                }
            }
        }
        if (schema != null) {
            for (Map.Entry<String, Class<?>> entry : schema.entrySet()) {
                if (entry.getValue() != null) {
                    fixedSchema.put(entry.getKey(), entry.getValue());
                }
            }
        }

        JsonToken token = parser.nextToken();
        if (token == null) {
            return target.getDataFrame();
        }

        // Support standard JSON array [ {...}, {...} ] or JSON Lines / NDJSON (concatenated objects)
        boolean isArray = (token == JsonToken.START_ARRAY);
        if (!isArray && token != JsonToken.START_OBJECT) {
            throw new ImportException("JSON root must be an array of objects or JSON Lines stream of objects");
        }

        List<String> columnNames = new ArrayList<>(fixedSchema.keySet());
        Map<String, Integer> colIndexMap = new LinkedHashMap<>();
        for (int i = 0; i < columnNames.size(); i++) {
            colIndexMap.put(columnNames.get(i), i);
        }

        List<ColumnVector> vectors = new ArrayList<>();
        for (String name : columnNames) {
            vectors.add(ColumnVector.create(name, fixedSchema.get(name), 64, true));
        }

        int rowCount = 0;

        while (true) {
            if (isArray) {
                token = parser.nextToken();
                if (token == null || token == JsonToken.END_ARRAY) {
                    break;
                }
            } else {
                if (rowCount > 0) {
                    token = parser.nextToken();
                    if (token == null) {
                        break;
                    }
                }
            }

            if (token != JsonToken.START_OBJECT) {
                // Skip primitive array elements or empty lines if any
                if (token.isScalarValue()) {
                    continue;
                }
                if (token == JsonToken.START_ARRAY) {
                    parser.skipChildren();
                    continue;
                }
                break;
            }

            // Mark which columns received values in this row
            int initialColCount = vectors.size();
            boolean[] received = new boolean[Math.max(64, initialColCount * 2)];

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.currentName();
                JsonToken valueToken = parser.nextToken();

                Integer colIdx = colIndexMap.get(fieldName);
                if (colIdx == null) {
                    // New discovered column
                    colIdx = columnNames.size();
                    columnNames.add(fieldName);
                    colIndexMap.put(fieldName, colIdx);

                    Class<?> inferredType = fixedSchema.get(fieldName);
                    if (inferredType == null) {
                        inferredType = inferTypeFromToken(valueToken, parser);
                    }
                    ColumnVector newVec = ColumnVector.create(fieldName, inferredType, Math.max(64, rowCount + 16), true);
                    // Backfill nulls for previous rows
                    for (int r = 0; r < rowCount; r++) {
                        newVec.appendNull();
                    }
                    vectors.add(newVec);

                    if (colIdx >= received.length) {
                        boolean[] nr = new boolean[Math.max(colIdx * 2, received.length * 2)];
                        System.arraycopy(received, 0, nr, 0, received.length);
                        received = nr;
                    }
                } else if (colIdx >= received.length) {
                    boolean[] nr = new boolean[Math.max(colIdx * 2, received.length * 2)];
                    System.arraycopy(received, 0, nr, 0, received.length);
                    received = nr;
                }

                received[colIdx] = true;
                ColumnVector vec = vectors.get(colIdx);
                boolean fixed = fixedSchema.containsKey(fieldName);

                // Check for type promotion if not fixed
                if (!fixed) {
                    ColumnVector promoted = checkAndPromoteVector(vec, valueToken, parser, rowCount);
                    if (promoted != vec) {
                        vectors.set(colIdx, promoted);
                        vec = promoted;
                    }
                }

                appendTokenToVector(vec, valueToken, parser);
            }

            // Fill nulls for any columns that were not present in this object
            for (int c = 0; c < vectors.size(); c++) {
                if (!received[c]) {
                    vectors.get(c).appendNull();
                }
            }

            rowCount++;
        }

        parser.close();

        // Populate target DataFrame
        ArrayList<Column> finalHeaders = new ArrayList<>(vectors.size());
        for (ColumnVector vec : vectors) {
            Class<?> colType = vec.getType();
            if (colType == Object.class && !fixedSchema.containsKey(vec.getName())) {
                boolean allNull = true;
                for (int r = 0; r < rowCount; r++) {
                    if (!vec.isNull(r)) {
                        allNull = false;
                        break;
                    }
                }
                if (allNull) {
                    colType = null;
                }
            }
            finalHeaders.add(new Column(vec.getName(), colType, true));
        }

        DataFrame df = DataFrame.fromVectors(vectors);
        df.setHeaders(finalHeaders);
        CupOfTea result = new CupOfTea(df);

        if (target.getHeaders() != null && !target.getHeaders().isEmpty()) {
            for (Column col : target.getHeaders()) {
                if (df.getColumnHeader(col.columnName()) == null) {
                    result.addColumn(col.columnName(), col.columnType());
                }
            }
        }

        return result.getDataFrame();
    }

    private static Class<?> inferTypeFromToken(JsonToken token, JsonParser parser) throws Exception {
        if (token == null || token == JsonToken.VALUE_NULL) {
            return null; // undecided yet
        }
        if (token == JsonToken.VALUE_TRUE || token == JsonToken.VALUE_FALSE) {
            return Boolean.class;
        }
        if (token == JsonToken.VALUE_NUMBER_INT) {
            try {
                parser.getIntValue();
                return Integer.class;
            } catch (Exception e1) {
                try {
                    parser.getLongValue();
                    return Long.class;
                } catch (Exception e2) {
                    return Double.class;
                }
            }
        }
        if (token == JsonToken.VALUE_NUMBER_FLOAT) {
            return Double.class;
        }
        if (token == JsonToken.VALUE_STRING) {
            return String.class;
        }
        return String.class;
    }

    private static ColumnVector checkAndPromoteVector(ColumnVector vec, JsonToken token, JsonParser parser, int currentRows) throws Exception {
        if (token == null || token == JsonToken.VALUE_NULL) {
            return vec;
        }

        Class<?> currentType = vec.getType();
        if (currentType == null) {
            Class<?> inferred = inferTypeFromToken(token, parser);
            if (inferred != null) {
                return promoteVector(vec, inferred, currentRows);
            }
            return vec;
        }

        if (token == JsonToken.VALUE_NUMBER_INT) {
            if (currentType == Integer.class) {
                try {
                    parser.getIntValue();
                } catch (Exception e1) {
                    try {
                        parser.getLongValue();
                        return promoteVector(vec, Long.class, currentRows);
                    } catch (Exception e2) {
                        return promoteVector(vec, Double.class, currentRows);
                    }
                }
            } else if (currentType != Long.class && currentType != Double.class && currentType != Float.class) {
                return promoteVector(vec, String.class, currentRows);
            }
        } else if (token == JsonToken.VALUE_NUMBER_FLOAT) {
            if (currentType == Integer.class || currentType == Long.class || currentType == Float.class) {
                return promoteVector(vec, Double.class, currentRows);
            } else if (currentType != Double.class) {
                return promoteVector(vec, String.class, currentRows);
            }
        } else if (token == JsonToken.VALUE_STRING) {
            if (currentType != String.class) {
                return promoteVector(vec, String.class, currentRows);
            }
        } else if (token == JsonToken.VALUE_TRUE || token == JsonToken.VALUE_FALSE) {
            if (currentType != Boolean.class) {
                return promoteVector(vec, String.class, currentRows);
            }
        } else if (token == JsonToken.START_ARRAY || token == JsonToken.START_OBJECT) {
            if (currentType != String.class) {
                return promoteVector(vec, String.class, currentRows);
            }
        }

        return vec;
    }

    private static ColumnVector promoteVector(ColumnVector oldVec, Class<?> targetType, int currentRows) {
        ColumnVector newVec = ColumnVector.create(oldVec.getName(), targetType, Math.max(64, currentRows + 16), true);
        for (int r = 0; r < currentRows; r++) {
            if (oldVec.isNull(r)) {
                newVec.appendNull();
            } else {
                Object val = oldVec.getObject(r);
                appendObjectCoerced(newVec, val, targetType);
            }
        }
        return newVec;
    }

    private static void appendObjectCoerced(ColumnVector vec, Object val, Class<?> targetType) {
        if (val == null) {
            vec.appendNull();
            return;
        }
        if (vec instanceof DoubleColumnVector dcv) {
            if (val instanceof Number n) {
                dcv.appendDouble(n.doubleValue());
            } else {
                dcv.appendDouble(Double.parseDouble(val.toString()));
            }
        } else if (vec instanceof LongColumnVector lcv) {
            if (val instanceof Number n) {
                lcv.appendLong(n.longValue());
            } else {
                lcv.appendLong(Long.parseLong(val.toString()));
            }
        } else if (vec instanceof IntColumnVector icv) {
            if (val instanceof Number n) {
                icv.appendInt(n.intValue());
            } else {
                icv.appendInt(Integer.parseInt(val.toString()));
            }
        } else if (vec instanceof BooleanColumnVector bcv) {
            if (val instanceof Boolean b) {
                bcv.appendBoolean(b);
            } else {
                bcv.appendBoolean(Boolean.parseBoolean(val.toString()));
            }
        } else if (vec instanceof StringColumnVector scv) {
            scv.appendString(val.toString());
        } else {
            vec.appendObject(val);
        }
    }

    private static void appendTokenToVector(ColumnVector vec, JsonToken token, JsonParser parser) throws Exception {
        if (token == null || token == JsonToken.VALUE_NULL) {
            vec.appendNull();
            return;
        }

        Class<?> type = vec.getType();

        if (vec instanceof IntColumnVector icv) {
            if (token == JsonToken.VALUE_NUMBER_INT) {
                icv.appendInt(parser.getIntValue());
            } else if (token == JsonToken.VALUE_NUMBER_FLOAT) {
                icv.appendInt((int) parser.getDoubleValue());
            } else {
                String text = parser.getText();
                icv.appendInt(Integer.parseInt(text.trim()));
            }
        } else if (vec instanceof LongColumnVector lcv) {
            if (token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT) {
                lcv.appendLong(parser.getLongValue());
            } else {
                String text = parser.getText();
                lcv.appendLong(Long.parseLong(text.trim()));
            }
        } else if (vec instanceof DoubleColumnVector dcv) {
            if (token == JsonToken.VALUE_NUMBER_FLOAT || token == JsonToken.VALUE_NUMBER_INT) {
                dcv.appendDouble(parser.getDoubleValue());
            } else {
                String text = parser.getText();
                dcv.appendDouble(Double.parseDouble(text.trim()));
            }
        } else if (vec instanceof FloatColumnVector fcv) {
            if (token == JsonToken.VALUE_NUMBER_FLOAT || token == JsonToken.VALUE_NUMBER_INT) {
                fcv.appendFloat(parser.getFloatValue());
            } else {
                String text = parser.getText();
                fcv.appendFloat(Float.parseFloat(text.trim()));
            }
        } else if (vec instanceof BooleanColumnVector bcv) {
            if (token == JsonToken.VALUE_TRUE) {
                bcv.appendBoolean(true);
            } else if (token == JsonToken.VALUE_FALSE) {
                bcv.appendBoolean(false);
            } else {
                String text = parser.getText();
                bcv.appendBoolean(Boolean.parseBoolean(text.trim()));
            }
        } else if (vec instanceof StringColumnVector scv) {
            if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                // If nested structure, capture raw JSON text
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                scv.appendString(om.readTree(parser).toString());
            } else {
                scv.appendString(parser.getText());
            }
        } else {
            // Coerce to target explicit type if known
            if (type == BigDecimal.class) {
                vec.appendObject(new BigDecimal(parser.getText().trim()));
            } else if (type == LocalDate.class) {
                vec.appendObject(LocalDate.parse(parser.getText().trim()));
            } else if (type == LocalDateTime.class) {
                vec.appendObject(LocalDateTime.parse(parser.getText().trim()));
            } else if (type == Instant.class) {
                vec.appendObject(Instant.parse(parser.getText().trim()));
            } else {
                if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                    com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                    vec.appendObject(om.readTree(parser).toString());
                } else {
                    vec.appendObject(parser.getText());
                }
            }
        }
    }
}
