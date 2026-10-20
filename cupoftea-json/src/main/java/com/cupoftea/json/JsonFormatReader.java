package com.cupoftea.json;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.spi.FormatReader;
import com.cupoftea.spi.ReadOptions;
import com.cupoftea.table.Column;
import com.cupoftea.table.DataFrame;
import com.cupoftea.table.interfaces.IDataFrame;

public class JsonFormatReader implements FormatReader {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String format() {
        return "json";
    }

    @Override
    public IDataFrame read(ReadOptions options) {
        if (options == null) {
            throw new ImportException("ReadOptions cannot be null");
        }

        JsonNode root;
        try {
            if (options.getFile() != null) {
                root = mapper.readTree(options.getFile());
            } else if (options.getText() != null) {
                root = mapper.readTree(options.getText());
            } else if (options.getPath() != null) {
                File file = new File(options.getPath());
                if (file.exists() && file.isFile()) {
                    root = mapper.readTree(file);
                } else {
                    root = mapper.readTree(options.getPath());
                }
            } else if (options.getSourceObject() != null) {
                Object src = options.getSourceObject();
                if (src instanceof File) {
                    root = mapper.readTree((File) src);
                } else {
                    root = mapper.readTree(src.toString());
                }
            } else {
                throw new ImportException("No JSON input source provided in ReadOptions");
            }
        } catch (IOException e) {
            throw new ImportException("Failed to parse JSON", e);
        } catch (Exception e) {
            if (e instanceof ImportException) throw (ImportException) e;
            throw new ImportException("Failed to read JSON: " + e.getMessage(), e);
        }

        if (!root.isArray()) {
            throw new ImportException("JSON root must be an array of objects");
        }

        ArrayNode array = (ArrayNode) root;
        CupOfTea target = options.getTargetDataFrame() != null
                ? new CupOfTea(options.getTargetDataFrame())
                : new CupOfTea();

        if (array.isEmpty()) {
            return target.getDataFrame();
        }

        Map<String, Class<?>> schema = options.getSchema();
        Map<String, Class<?>> columnTypes = new LinkedHashMap<>();
        Set<String> fixedSchemaColumns = new HashSet<>();

        for (Column existingColumn : target.getHeaders()) {
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

        List<Map<String, Object>> normalizedRows = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JsonNode item = array.get(i);
            if (!item.isObject()) {
                continue;
            }
            Map<String, Object> rowValues = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = item.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey();
                Object val = normalizeJsonNode(field.getValue());
                rowValues.put(key, val);

                if (!fixedSchemaColumns.contains(key)) {
                    Class<?> valueType = val == null ? null : val.getClass();
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
            boolean exists = target.getHeaders().stream()
                    .anyMatch(col -> columnName.equals(col.columnName()));
            if (!exists) {
                target.addColumn(columnName, columnType);
            } else {
                Column existing = target.getColumnHeader(columnName);
                if (columnType != null && !columnType.equals(existing.columnType())) {
                    int idx = target.getHeaders().indexOf(existing);
                    target.getHeaders().set(idx, new Column(columnName, columnType));
                }
            }
        }

        for (Map<String, Object> rawRow : normalizedRows) {
            HashMap<String, Object> row = new HashMap<>();
            for (Map.Entry<String, Class<?>> entry : columnTypes.entrySet()) {
                String columnName = entry.getKey();
                Class<?> columnType = target.getColumnHeader(columnName).columnType();
                Object value = rawRow.get(columnName);
                row.put(columnName, coerceToColumnType(value, columnType));
            }
            target.addRow(row);
        }

        return target.getDataFrame();
    }

    private static Object normalizeJsonNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isIntegralNumber()) {
            if (node.canConvertToInt()) {
                return node.asInt();
            }
            if (node.canConvertToLong()) {
                return node.asLong();
            }
            BigInteger bi = node.bigIntegerValue();
            if (bi.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) >= 0
                    && bi.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0) {
                return bi.intValue();
            }
            if (bi.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) >= 0
                    && bi.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0) {
                return bi.longValue();
            }
            return bi.doubleValue();
        }
        if (node.isFloatingPointNumber()) {
            return node.asDouble();
        }
        if (node.isTextual()) {
            return node.asText();
        }
        return node.toString();
    }

    private static Class<?> mergeColumnType(Class<?> currentType, Class<?> candidateType) {
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

    private static Object coerceToColumnType(Object value, Class<?> columnType) {
        if (value == null || columnType == null) {
            return value;
        }
        if (columnType.isInstance(value)) {
            return value;
        }
        if (columnType == Double.class) {
            if (value instanceof Number num) {
                return num.doubleValue();
            }
            return Double.valueOf(value.toString());
        }
        if (columnType == Float.class) {
            if (value instanceof Number num) {
                return num.floatValue();
            }
            return Float.valueOf(value.toString());
        }
        if (columnType == Long.class) {
            if (value instanceof Number num) {
                return num.longValue();
            }
            return Long.valueOf(value.toString());
        }
        if (columnType == Integer.class) {
            if (value instanceof Number num) {
                return num.intValue();
            }
            return Integer.valueOf(value.toString());
        }
        if (columnType == Boolean.class) {
            if (value instanceof Boolean) {
                return value;
            }
            return Boolean.valueOf(value.toString());
        }
        if (columnType == BigDecimal.class) {
            if (value instanceof BigDecimal) {
                return value;
            }
            return new BigDecimal(value.toString());
        }
        if (columnType == LocalDate.class) {
            if (value instanceof LocalDate) {
                return value;
            }
            return LocalDate.parse(value.toString());
        }
        if (columnType == LocalDateTime.class) {
            if (value instanceof LocalDateTime) {
                return value;
            }
            return LocalDateTime.parse(value.toString());
        }
        if (columnType == Instant.class) {
            if (value instanceof Instant) {
                return value;
            }
            return Instant.parse(value.toString());
        }
        if (columnType == String.class) {
            return value.toString();
        }
        return value;
    }
}
