package com.CupOfTea.importers;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.CupOfTea.CupOfTea;
import com.CupOfTea.exceptions.ImportException;
import com.CupOfTea.importers.interfaces.IJsonImporter;
import com.CupOfTea.table.Column;
import com.CupOfTea.table.Schema;

public class JsonImporter implements IJsonImporter {

    private final CupOfTea dataFrame;

    public JsonImporter(final CupOfTea dataFrame){
        this.dataFrame = dataFrame;
    }
    

    /**
     * Dumps the Data from a JSON file into the CupOfTea data frame.
     * The json must be a json array for this method to work.
     * 
     * @param file JSON file.
     */
    @Override
    public void importFromJson(final File file) {
        this.importFromJson(file, Collections.emptyMap());
    }

    /**
     * Dumps the Data from a JSON file into the CupOfTea data frame using an explicit schema.
     * 
     * @param file JSON file.
     * @param schema Explicit mapping of column names to Java types.
     */
    @Override
    public void importFromJson(final File file, final Map<String, Class<?>> schema) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        try {
            String jsonText = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            JSONArray jsonArray = new JSONArray(jsonText);
            this.importFromJson(jsonArray, schema);
        } catch (IOException e) {
            throw new ImportException("Failed to read JSON file: " + file.getPath(), e);
        } catch (JSONException e) {
            throw new ImportException("Failed to parse JSON file: " + file.getPath(), e);
        } catch (Exception e) {
            if (e instanceof ImportException) {
                throw (ImportException) e;
            }
            throw new ImportException("Failed to import JSON file: " + file.getPath(), e);
        }
    }

    /**
     * Dumps the Data from a JSON String into the CupOfTea data frame.
     * The json must be a json array for this method to work.
     * 
     * @param json JSON String.
     */
    @Override
    public void importFromJson(final String json) {
        this.importFromJson(json, Collections.emptyMap());
    }

    /**
     * Dumps the Data from a JSON String into the CupOfTea data frame using an explicit schema.
     * 
     * @param json JSON String.
     * @param schema Explicit mapping of column names to Java types.
     */
    @Override
    public void importFromJson(final String json, final Map<String, Class<?>> schema) {
        if (json == null) {
            throw new ImportException("JSON string cannot be null");
        }
        try {
            this.importFromJson(new JSONArray(json), schema);
        } catch (JSONException e) {
            throw new ImportException("Failed to parse JSON string", e);
        } catch (Exception e) {
            if (e instanceof ImportException) {
                throw (ImportException) e;
            }
            throw new ImportException("Failed to import JSON string", e);
        }
    }

    /**
     * Dumps the Data from a JSONArray Object into the CupOfTea data frame.
     * 
     * @param json JSONArray Object.
     */
    @Override
    public void importFromJson(final JSONArray json) {
        this.importFromJson(json, Collections.emptyMap());
    }

    /**
     * Dumps the Data from a JSONArray Object into the CupOfTea data frame using an explicit schema.
     * 
     * @param json JSONArray Object.
     * @param schema Explicit mapping of column names to Java types.
     */
    @Override
    public void importFromJson(final JSONArray json, final Map<String, Class<?>> schema) {
        if (json == null) {
            throw new ImportException("JSONArray cannot be null");
        }
        try {
            if (json.isEmpty()) {
                return;
            }

            Map<String, Class<?>> columnTypes = new LinkedHashMap<>();
            Set<String> fixedSchemaColumns = new HashSet<>();

            for (Column existingColumn : this.dataFrame.getHeaders()) {
                columnTypes.put(existingColumn.columnName(), existingColumn.columnType());
                if (existingColumn.columnType() != null) {
                    fixedSchemaColumns.add(existingColumn.columnName());
                }
            }

            if (schema != null) {
                for (Map.Entry<String, Class<?>> entry : schema.entrySet()) {
                    columnTypes.put(entry.getKey(), entry.getValue());
                    if (entry.getValue() != null) {
                        fixedSchemaColumns.add(entry.getKey());
                    }
                }
            }

            List<Map<String, Object>> normalizedRows = new ArrayList<>(json.length());
            for (int i = 0; i < json.length(); i++) {
                JSONObject jsonObject = json.getJSONObject(i);
                Map<String, Object> rowValues = new LinkedHashMap<>();
                for (String key : jsonObject.keySet()) {
                    Object normalizedValue = normalizeJsonValue(jsonObject.opt(key));
                    rowValues.put(key, normalizedValue);
                    if (!fixedSchemaColumns.contains(key)) {
                        Class<?> valueType = normalizedValue == null ? null : normalizedValue.getClass();
                        if (!columnTypes.containsKey(key)) {
                            columnTypes.put(key, valueType);
                        } else {
                            columnTypes.put(key, mergeColumnType(columnTypes.get(key), valueType));
                        }
                    }
                }
                normalizedRows.add(rowValues);
            }

            for (Map.Entry<String, Class<?>> entry : columnTypes.entrySet()) {
                String columnName = entry.getKey();
                Class<?> columnType = entry.getValue();
                boolean existsInHeader = this.dataFrame.getHeaders().stream()
                        .anyMatch(col -> columnName.equals(col.columnName()));
                if (!existsInHeader) {
                    this.dataFrame.getDataFrame().addColumn(columnName, columnType);
                } else {
                    Column existing = this.dataFrame.getColumnHeader(columnName);
                    if (columnType != null && !columnType.equals(existing.columnType())) {
                        int idx = this.dataFrame.getHeaders().indexOf(existing);
                        this.dataFrame.getHeaders().set(idx, new Column(columnName, columnType));
                    }
                }
            }

            for (Map.Entry<String, Class<?>> entry : columnTypes.entrySet()) {
                String columnName = entry.getKey();
                for (Map<String, Object> rawRow : normalizedRows) {
                    rawRow.putIfAbsent(columnName, null);
                }
            }

            for (Map<String, Object> rawRow : normalizedRows) {
                HashMap<String, Object> row = new HashMap<>();
                for (Map.Entry<String, Class<?>> entry : columnTypes.entrySet()) {
                    String columnName = entry.getKey();
                    Class<?> columnType = this.dataFrame.getColumnHeader(columnName).columnType();
                    Object value = rawRow.get(columnName);
                    row.put(columnName, coerceToColumnType(value, columnType));
                }
                this.dataFrame.getDataFrame().addRow(row);
            }
        } catch (JSONException e) {
            throw new ImportException("Failed to process JSONArray", e);
        } catch (Exception e) {
            if (e instanceof ImportException) {
                throw (ImportException) e;
            }
            throw new ImportException("Failed to import JSONArray", e);
        }
    }

    private static Object normalizeJsonValue(final Object value) {
        if (value == null || JSONObject.NULL.equals(value)) {
            return null;
        }
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long || value instanceof String) {
            return value;
        }
        if (value instanceof Byte || value instanceof Short) {
            return ((Number) value).intValue();
        }
        if (value instanceof BigInteger bigInt) {
            if (bigInt.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) >= 0
                    && bigInt.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0) {
                return bigInt.intValue();
            }
            if (bigInt.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) >= 0
                    && bigInt.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0) {
                return bigInt.longValue();
            }
            return bigInt.doubleValue();
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return value.toString();
    }

    private static Class<?> mergeColumnType(final Class<?> currentType, final Class<?> candidateType) {
        if (currentType == null) {
            return candidateType;
        }
        if (candidateType == null || currentType.equals(candidateType)) {
            return currentType;
        }
        if (Number.class.isAssignableFrom(currentType) && Number.class.isAssignableFrom(candidateType)) {
            if (currentType == Double.class || candidateType == Double.class
                    || currentType == Float.class || candidateType == Float.class) {
                return Double.class;
            }
            if (currentType == Long.class || candidateType == Long.class) {
                return Long.class;
            }
            return Integer.class;
        }
        return String.class;
    }

    private static Object coerceToColumnType(final Object value, final Class<?> columnType) {
        if (value == null || columnType == null || columnType.isInstance(value)) {
            return value;
        }
        if (columnType == Double.class) {
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value instanceof String str) {
                return Double.valueOf(str);
            }
        }
        if (columnType == Float.class) {
            if (value instanceof Number number) {
                return number.floatValue();
            }
            if (value instanceof String str) {
                return Float.valueOf(str);
            }
        }
        if (columnType == Long.class) {
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value instanceof String str) {
                return Long.valueOf(str);
            }
        }
        if (columnType == Integer.class) {
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String str) {
                return Integer.valueOf(str);
            }
        }
        if (columnType == Boolean.class) {
            if (value instanceof String str) {
                return Boolean.parseBoolean(str);
            }
            if (value instanceof Number number) {
                return number.intValue() != 0;
            }
        }
        if (columnType == BigDecimal.class) {
            if (value instanceof BigDecimal bd) {
                return bd;
            }
            return new BigDecimal(value.toString().trim());
        }
        if (columnType == LocalDate.class) {
            if (value instanceof String str) {
                return LocalDate.parse(str.trim());
            }
        }
        if (columnType == LocalDateTime.class) {
            if (value instanceof String str) {
                return LocalDateTime.parse(str.trim());
            }
        }
        if (columnType == Instant.class) {
            if (value instanceof String str) {
                return Instant.parse(str.trim());
            }
            if (value instanceof Number num) {
                return Instant.ofEpochMilli(num.longValue());
            }
        }
        if (columnType == String.class) {
            return value.toString();
        }
        return value;
    }
}
