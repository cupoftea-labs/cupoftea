package com.cupoftea.table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.cupoftea.exceptions.ConversionException;
import com.cupoftea.exceptions.SchemaMismatchException;
import com.cupoftea.table.converters.BigDecimalConverter;
import com.cupoftea.table.converters.BooleanConverter;
import com.cupoftea.table.converters.DateConverter;
import com.cupoftea.table.converters.DoubleConverter;
import com.cupoftea.table.converters.FloatConverter;
import com.cupoftea.table.converters.InstantConverter;
import com.cupoftea.table.converters.IntegerConverter;
import com.cupoftea.table.converters.LocalDateConverter;
import com.cupoftea.table.converters.LocalDateTimeConverter;
import com.cupoftea.table.converters.LongConverter;
import com.cupoftea.table.converters.StringConverter;
import com.cupoftea.table.interfaces.IDataFrame;
import com.cupoftea.table.interfaces.IDataTypeConverter;
import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.FloatColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.ObjectColumnVector;
import com.cupoftea.table.vector.StringColumnVector;
import com.cupoftea.utils.interfaces.IColumnCreator;
import com.cupoftea.utils.interfaces.IFilter;
import com.cupoftea.utils.interfaces.Order;
import com.cupoftea.utils.interfaces.Orderer;

/**
 * Enterprise columnar DataFrame implementation.
 * Stores data in contiguous typed {@link ColumnVector} instances rather than row HashMaps.
 */
public class DataFrame extends IDataFrame {

    private static final IntegerConverter INTEGER_CONVERTER = new IntegerConverter();
    private static final DoubleConverter DOUBLE_CONVERTER = new DoubleConverter();
    private static final FloatConverter FLOAT_CONVERTER = new FloatConverter();
    private static final LongConverter LONG_CONVERTER = new LongConverter();
    private static final BooleanConverter BOOLEAN_CONVERTER = new BooleanConverter();
    private static final StringConverter STRING_CONVERTER = new StringConverter();
    private static final DateConverter DATE_CONVERTER = new DateConverter();
    private static final LocalDateConverter LOCAL_DATE_CONVERTER = new LocalDateConverter();
    private static final LocalDateTimeConverter LOCAL_DATE_TIME_CONVERTER = new LocalDateTimeConverter();
    private static final InstantConverter INSTANT_CONVERTER = new InstantConverter();
    private static final BigDecimalConverter BIG_DECIMAL_CONVERTER = new BigDecimalConverter();

    public static final Map<Class<?>, IDataTypeConverter<?>> CONVERTERS = Map.ofEntries(
        Map.entry(Integer.class, INTEGER_CONVERTER),
        Map.entry(int.class, INTEGER_CONVERTER),
        Map.entry(Double.class, DOUBLE_CONVERTER),
        Map.entry(double.class, DOUBLE_CONVERTER),
        Map.entry(Float.class, FLOAT_CONVERTER),
        Map.entry(float.class, FLOAT_CONVERTER),
        Map.entry(Long.class, LONG_CONVERTER),
        Map.entry(long.class, LONG_CONVERTER),
        Map.entry(Boolean.class, BOOLEAN_CONVERTER),
        Map.entry(boolean.class, BOOLEAN_CONVERTER),
        Map.entry(String.class, STRING_CONVERTER),
        Map.entry(Date.class, DATE_CONVERTER),
        Map.entry(LocalDate.class, LOCAL_DATE_CONVERTER),
        Map.entry(LocalDateTime.class, LOCAL_DATE_TIME_CONVERTER),
        Map.entry(Instant.class, INSTANT_CONVERTER),
        Map.entry(BigDecimal.class, BIG_DECIMAL_CONVERTER)
    );

    protected List<ColumnVector> columns = new ArrayList<>();
    private final HashMap<String, IColumnCreator> columnCreators = new HashMap<>();
    private final HashSet<String> pendingTypeInferenceColumns = new HashSet<>();

    public DataFrame() {
        super();
    }

    public DataFrame(Schema schema) {
        super(schema);
        if (schema != null) {
            for (Column col : schema.getColumns()) {
                this.columns.add(ColumnVector.create(col, 16));
            }
        }
    }

    @Override
    public List<ColumnVector> getColumnVectors() {
        return Collections.unmodifiableList(this.columns);
    }

    @Override
    public ColumnVector getColumnVector(String columnName) {
        return getVector(columnName);
    }

    @Override
    public ColumnVector getColumnVector(int index) {
        return this.columns.get(index);
    }

    private ColumnVector getVector(String columnName) {
        if (columnName == null) {
            return null;
        }
        for (ColumnVector vector : this.columns) {
            if (columnName.equals(vector.getName())) {
                return vector;
            }
        }
        return null;
    }

    private int findVectorIndex(String columnName) {
        if (columnName == null) {
            return -1;
        }
        for (int i = 0; i < this.columns.size(); i++) {
            if (columnName.equals(this.columns.get(i).getName())) {
                return i;
            }
        }
        return -1;
    }

    private void syncColumnsWithHeader() {
        int currentSize = this.columns.isEmpty() ? 0 : this.columns.get(0).size();
        List<ColumnVector> newVectors = new ArrayList<>(this.header.size());
        for (Column col : this.header.getHeader()) {
            ColumnVector vec = getVector(col.columnName());
            if (vec == null) {
                vec = ColumnVector.create(col, Math.max(16, currentSize));
                for (int i = 0; i < currentSize; i++) {
                    vec.appendNull();
                }
            }
            newVectors.add(vec);
        }
        this.columns = newVectors;
    }

    @Override
    public Integer size() {
        if (this.header.size() > 0 && this.columns.size() != this.header.size()) {
            syncColumnsWithHeader();
        }
        return this.columns.isEmpty() ? 0 : this.columns.get(0).size();
    }

    @Override
    public Schema getSchema() {
        return Schema.fromHeader(this.header);
    }

    @Override
    public IDataFrame setSchema(Schema schema) {
        if (schema == null) {
            return this;
        }
        ArrayList<Column> newHeaders = new ArrayList<>(schema.getColumns());
        this.setHeaders(newHeaders);
        return this;
    }

    @Override
    public ArrayList<Column> getHeaders() {
        return this.header.getHeader();
    }

    @Override
    public Column getColumnHeader(String columnName) {
        return this.header.get(columnName);
    }

    @Override
    public void setHeaders(ArrayList<Column> headers) {
        if (headers == null) {
            this.header.clear();
            this.columns.clear();
            return;
        }

        int currentSize = this.size();
        List<ColumnVector> newVectors = new ArrayList<>(headers.size());

        for (Column col : headers) {
            ColumnVector existing = getVector(col.columnName());
            if (existing != null) {
                if (col.columnType() != null && !Objects.equals(col.columnType(), existing.getType())) {
                    IDataTypeConverter<?> converter = CONVERTERS.get(col.columnType());
                    ColumnVector converted = ColumnVector.create(col.columnName(), col.columnType(), currentSize, col.nullable());
                    for (int i = 0; i < currentSize; i++) {
                        if (existing.isNull(i)) {
                            converted.appendNull();
                        } else {
                            Object val = existing.getObject(i);
                            Object convVal = (converter != null && val != null && !col.columnType().isInstance(val))
                                    ? converter.convert(val, existing.getType() != null ? existing.getType() : String.class)
                                    : val;
                            converted.appendObject(convVal);
                        }
                    }
                    newVectors.add(converted);
                } else {
                    existing.setName(col.columnName());
                    existing.setNullable(col.nullable());
                    newVectors.add(existing);
                }
            } else {
                ColumnVector vec = ColumnVector.create(col, Math.max(16, currentSize));
                for (int i = 0; i < currentSize; i++) {
                    vec.appendNull();
                }
                newVectors.add(vec);
            }
        }

        this.header.setHeader(headers);
        this.columns = newVectors;
    }

    @Override
    public IDataFrame moveColumn(String columnName, int finalIndex) {
        this.header.moveColumn(columnName, finalIndex);
        int fromIdx = findVectorIndex(columnName);
        if (fromIdx != -1) {
            ColumnVector vec = this.columns.remove(fromIdx);
            this.columns.add(finalIndex, vec);
        }
        return this;
    }

    @Override
    public IDataFrame moveColumn(int initialIndex, int finalIndex) {
        this.header.moveColumn(initialIndex, finalIndex);
        ColumnVector vec = this.columns.remove(initialIndex);
        this.columns.add(finalIndex, vec);
        return this;
    }

    @Override
    public IDataFrame renameColumn(String oldName, String newName) {
        this.header.renameColumn(oldName, newName);
        ColumnVector vec = getVector(oldName);
        if (vec != null) {
            vec.setName(newName);
        }
        if (this.columnCreators.containsKey(oldName)) {
            this.columnCreators.put(newName, this.columnCreators.remove(oldName));
        }
        if (this.pendingTypeInferenceColumns.remove(oldName)) {
            this.pendingTypeInferenceColumns.add(newName);
        }
        if (Objects.equals(this.indexColumn, oldName)) {
            this.indexColumn = newName;
            this.index.setColumnName(newName);
        }
        return this;
    }

    @Override
    public IDataFrame renameColumn(int index, String newName) {
        String oldName = this.header.get(index).columnName();
        return renameColumn(oldName, newName);
    }

    @Override
    public IDataFrame renameColumn(HashMap<String, String> name) {
        if (name != null) {
            name.forEach(this::renameColumn);
        }
        return this;
    }

    @Override
    public IDataFrame addColumn(String columnName) {
        return addColumn(this.header.columnCount(), columnName, (Class<?>) null);
    }

    @Override
    public IDataFrame addColumn(String columnName, Class<?> columnType) {
        return addColumn(this.header.columnCount(), columnName, columnType);
    }

    @Override
    public IDataFrame addColumn(int index, String columnName) {
        return addColumn(index, columnName, (Class<?>) null);
    }

    @Override
    public IDataFrame addColumn(int index, String columnName, Class<?> columnType) {
        this.header.addColumn(index, new Column(columnName, columnType));
        int rowCount = this.size();
        Class<?> vecType = columnType != null ? columnType : Object.class;
        ColumnVector vec = ColumnVector.create(columnName, vecType, Math.max(16, rowCount), true);
        for (int i = 0; i < rowCount; i++) {
            vec.appendNull();
        }
        this.columns.add(index, vec);
        return this;
    }

    @Override
    public IDataFrame addColumn(int index, String columnName, @Nullable Object data) {
        ArrayList<Object> dataArray = new ArrayList<>();
        int currentSize = this.size();
        for (int i = 0; i < currentSize; i++) {
            dataArray.add(data);
        }
        return this.addColumn(index, columnName, dataArray);
    }

    @Override
    public IDataFrame addColumn(int index, String columnName, @Nullable ArrayList<Object> data) {
        if (data == null) {
            return this.addColumn(index, columnName, (Object) null);
        }
        if (data.size() != this.size()) {
            throw new SchemaMismatchException("Column data size (" + data.size() + ") does not match DataFrame size (" + this.size() + ")");
        }

        Class<?> columnType = String.class;
        for (Object value : data) {
            if (value != null) {
                columnType = value.getClass();
                break;
            }
        }
        this.header.addColumn(index, new Column(columnName, columnType));
        ColumnVector vec = ColumnVector.create(columnName, columnType, Math.max(16, data.size()), true);
        for (Object val : data) {
            vec.appendObject(val);
        }
        this.columns.add(index, vec);
        return this;
    }

    @Override
    public IDataFrame addColumn(String columnName, IColumnCreator columnCreator) {
        return this.addColumn(this.header.columnCount(), columnName, (Class<?>) null, columnCreator);
    }

    @Override
    public IDataFrame addColumn(String columnName, Class<?> columnType, IColumnCreator columnCreator) {
        return this.addColumn(this.header.columnCount(), columnName, columnType, columnCreator);
    }

    @Override
    public IDataFrame addColumn(String columnName, IColumnCreator columnCreator, Class<?> columnType) {
        return this.addColumn(this.header.columnCount(), columnName, columnType, columnCreator);
    }

    @Override
    public IDataFrame addColumn(int index, String columnName, IColumnCreator columnCreator) {
        return this.addColumn(index, columnName, (Class<?>) null, columnCreator);
    }

    @Override
    public IDataFrame addColumn(int index, String columnName, IColumnCreator columnCreator, Class<?> columnType) {
        return this.addColumn(index, columnName, columnType, columnCreator);
    }

    @Override
    public IDataFrame addColumn(int index, String columnName, @Nullable Class<?> columnType, IColumnCreator columnCreator) {
        Class<?> resolvedType = columnType;
        int rowCount = this.size();
        List<Object> values = new ArrayList<>(rowCount);

        for (int i = 0; i < rowCount; i++) {
            Row row = getRow(i);
            Object value = columnCreator.createColumn(row);
            values.add(value);
            if (resolvedType == null && value != null) {
                resolvedType = value.getClass();
            }
        }

        if (resolvedType == null) {
            resolvedType = Object.class;
            this.pendingTypeInferenceColumns.add(columnName);
        } else {
            this.pendingTypeInferenceColumns.remove(columnName);
        }

        this.columnCreators.put(columnName, columnCreator);
        this.header.addColumn(index, new Column(columnName, resolvedType));
        ColumnVector vec = ColumnVector.create(columnName, resolvedType, Math.max(16, rowCount), true);
        for (Object val : values) {
            vec.appendObject(val);
        }
        this.columns.add(index, vec);
        return this;
    }

    @Override
    public IDataFrame removeColumn(String columnName) {
        this.header.removeColumn(columnName);
        this.columnCreators.remove(columnName);
        this.pendingTypeInferenceColumns.remove(columnName);
        int idx = findVectorIndex(columnName);
        if (idx != -1) {
            this.columns.remove(idx);
        }
        if (Objects.equals(this.indexColumn, columnName)) {
            this.indexColumn = null;
            this.index.clear();
        }
        return this;
    }

    @Override
    public IDataFrame removeColumn(int index) {
        String columnName = this.header.get(index).columnName();
        return this.removeColumn(columnName);
    }

    @Override
    public IDataFrame alterColumnType(String columnName, Class<?> newColumnType) {
        Column column = this.getColumnHeader(columnName);
        if (column == null) {
            throw new SchemaMismatchException("Column not found: " + columnName);
        }
        Class<?> oldColumnType = column.columnType();

        IDataTypeConverter<?> converter = CONVERTERS.get(newColumnType);
        if (converter == null) {
            throw new ConversionException("Column Type not Suported for Conversion");
        }

        int vecIdx = findVectorIndex(columnName);
        if (vecIdx != -1) {
            ColumnVector oldVec = this.columns.get(vecIdx);
            int rowCount = oldVec.size();
            ColumnVector newVec = ColumnVector.create(columnName, newColumnType, Math.max(16, rowCount), oldVec.isNullable());

            for (int i = 0; i < rowCount; i++) {
                if (oldVec.isNull(i)) {
                    newVec.appendNull();
                } else {
                    Object val = oldVec.getObject(i);
                    Class<?> actualOldType = oldColumnType;
                    if (actualOldType == null && val != null) {
                        actualOldType = val.getClass();
                    }
                    if (actualOldType == null) {
                        actualOldType = String.class;
                    }
                    Object converted = (val != null) ? converter.convert(val, actualOldType) : null;
                    newVec.appendObject(converted);
                }
            }
            this.columns.set(vecIdx, newVec);
        }

        this.pendingTypeInferenceColumns.remove(columnName);
        this.header.alterColumnType(columnName, newColumnType);
        return this;
    }

    private boolean canAccept(ColumnVector vec, Object val) {
        if (val == null || NA.isNA(val)) {
            return true;
        }
        if (vec instanceof IntColumnVector) {
            if (val instanceof Number) return true;
            try {
                Integer.parseInt(val.toString().trim());
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        if (vec instanceof LongColumnVector) {
            if (val instanceof Number) return true;
            try {
                Long.parseLong(val.toString().trim());
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        if (vec instanceof DoubleColumnVector) {
            if (val instanceof Number) return true;
            try {
                Double.parseDouble(val.toString().trim());
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        if (vec instanceof FloatColumnVector) {
            if (val instanceof Number) return true;
            try {
                Float.parseFloat(val.toString().trim());
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        if (vec instanceof BooleanColumnVector) {
            if (val instanceof Boolean) return true;
            String s = val.toString().trim();
            return "true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s);
        }
        if (vec instanceof StringColumnVector) {
            return val instanceof String;
        }
        return true;
    }

    private ColumnVector promoteToObjectVector(ColumnVector vec) {
        int sz = vec.size();
        ColumnVector objVec = new ObjectColumnVector(vec.getName(), Object.class, Math.max(16, sz), vec.isNullable());
        for (int i = 0; i < sz; i++) {
            if (vec.isNull(i)) {
                objVec.appendNull();
            } else {
                objVec.appendObject(vec.getObject(i));
            }
        }
        return objVec;
    }

    @Override
    public IDataFrame addRow(@NotNull Map<String, Object> row) {
        if (this.header.size() == 0 && !row.isEmpty()) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String colName = entry.getKey();
                Object val = entry.getValue();
                Class<?> colType = val != null ? val.getClass() : Object.class;
                this.header.addColumn(new Column(colName, colType));
                this.columns.add(ColumnVector.create(colName, colType, 16, true));
            }
        } else if (this.header.size() > 0 && this.columns.size() != this.header.size()) {
            syncColumnsWithHeader();
        }

        Map<String, Object> evaluatedRow = new HashMap<>(row);
        for (var entry : this.columnCreators.entrySet()) {
            String columnName = entry.getKey();
            IColumnCreator creator = entry.getValue();
            if (!evaluatedRow.containsKey(columnName)) {
                evaluatedRow.put(columnName, creator.createColumn(new Row(evaluatedRow)));
            }
        }

        if (!this.pendingTypeInferenceColumns.isEmpty()) {
            List<String> toInfer = new ArrayList<>(this.pendingTypeInferenceColumns);
            for (String columnName : toInfer) {
                Object value = evaluatedRow.get(columnName);
                if (value != null) {
                    this.alterColumnType(columnName, value.getClass());
                }
            }
        }

        for (int i = 0; i < this.columns.size(); i++) {
            ColumnVector vector = this.columns.get(i);
            Object val = evaluatedRow.get(vector.getName());
            if (val != null && !canAccept(vector, val)) {
                ColumnVector promoted = promoteToObjectVector(vector);
                this.columns.set(i, promoted);
                this.header.alterColumnType(vector.getName(), Object.class);
                vector = promoted;
            }
            if (val instanceof Group group) {
                vector.appendObject(new Group(group));
            } else {
                vector.appendObject(val);
            }
        }

        return this;
    }

    @Override
    public Row getRow(int index) {
        int total = this.size();
        if (index < 0 || index >= total) {
            throw new IndexOutOfBoundsException("Row index " + index + " out of bounds for DataFrame with " + total + " rows");
        }

        Map<String, Object> orderedMap = new LinkedHashMap<>(this.header.size());
        for (Column col : this.header.getHeader()) {
            ColumnVector vec = getVector(col.columnName());
            orderedMap.put(col.columnName(), vec != null ? vec.getObject(index) : null);
        }
        return new Row(orderedMap);
    }

    @Override
    public IDataFrame removeRow(int index) {
        int total = this.size();
        if (index < 0 || index >= total) {
            throw new IndexOutOfBoundsException("Row index " + index + " out of bounds for DataFrame with " + total + " rows");
        }
        for (ColumnVector vec : this.columns) {
            vec.remove(index);
        }
        return this;
    }

    @Override
    public IDataFrame removeRows(IFilter filter) {
        int total = this.size();
        java.util.BitSet retainMask = new java.util.BitSet(total);
        int retainedCount = 0;
        for (int i = 0; i < total; i++) {
            Row row = getRow(i);
            if (!Boolean.TRUE.equals(filter.filter(row))) {
                retainMask.set(i);
                retainedCount++;
            }
        }
        for (ColumnVector vec : this.columns) {
            vec.filterInPlace(retainMask, retainedCount);
        }
        return this;
    }

    @Override
    public IDataFrame filter(IFilter filter) {
        DataFrame filteredDF = new DataFrame();
        filteredDF.header = this.header.copy();
        filteredDF.columnCreators.putAll(this.columnCreators);
        filteredDF.pendingTypeInferenceColumns.addAll(this.pendingTypeInferenceColumns);
        filteredDF.index = this.index.copy();
        filteredDF.indexColumn = this.indexColumn;

        int total = this.size();
        java.util.BitSet matchMask = new java.util.BitSet(total);
        int matchCount = 0;
        for (int i = 0; i < total; i++) {
            Row row = getRow(i);
            if (Boolean.TRUE.equals(filter.filter(row))) {
                matchMask.set(i);
                matchCount++;
            }
        }

        for (ColumnVector vec : this.columns) {
            ColumnVector copy = vec.copy();
            copy.filterInPlace(matchMask, matchCount);
            filteredDF.columns.add(copy);
        }

        return filteredDF;
    }

    @Override
    public IDataFrame update(Map<String, Object> updateColumns) {
        if (updateColumns != null) {
            int total = this.size();
            for (Map.Entry<String, Object> entry : updateColumns.entrySet()) {
                int vecIdx = findVectorIndex(entry.getKey());
                if (vecIdx != -1) {
                    ColumnVector vec = this.columns.get(vecIdx);
                    Object val = entry.getValue();
                    if (val != null && !canAccept(vec, val)) {
                        vec = promoteToObjectVector(vec);
                        this.columns.set(vecIdx, vec);
                        this.header.alterColumnType(vec.getName(), Object.class);
                    }
                    for (int i = 0; i < total; i++) {
                        vec.setObject(i, val);
                    }
                }
            }
        }
        return this;
    }

    @Override
    public IDataFrame select(String... columnNames) {
        if (columnNames == null || columnNames.length == 0) {
            throw new IllegalArgumentException("Column names to select cannot be null or empty");
        }
        return select(Arrays.asList(columnNames));
    }

    @Override
    public IDataFrame select(List<String> columnNames) {
        if (columnNames == null || columnNames.isEmpty()) {
            throw new IllegalArgumentException("Column names to select cannot be null or empty");
        }
        for (String col : columnNames) {
            if (this.header.get(col) == null) {
                throw new IllegalArgumentException("Column '" + col + "' does not exist");
            }
        }

        Header newHeader = new Header();
        List<ColumnVector> newVectors = new ArrayList<>(columnNames.size());
        for (String col : columnNames) {
            Column existing = this.header.get(col);
            newHeader.addColumn(new Column(existing.columnName(), existing.columnType(), existing.nullable()));
            newVectors.add(getVector(col));
        }

        this.header = newHeader;
        this.columns = newVectors;

        Set<String> keepSet = new HashSet<>(columnNames);
        this.columnCreators.keySet().removeIf(c -> !keepSet.contains(c));
        this.pendingTypeInferenceColumns.removeIf(c -> !keepSet.contains(c));

        if (this.indexColumn != null && !keepSet.contains(this.indexColumn)) {
            this.indexColumn = null;
            this.index.clear();
        }

        return this;
    }

    @Override
    public IDataFrame drop(String... columnNames) {
        if (columnNames == null || columnNames.length == 0) {
            return this;
        }
        return drop(Arrays.asList(columnNames));
    }

    @Override
    public IDataFrame drop(List<String> columnNames) {
        if (columnNames == null || columnNames.isEmpty()) {
            return this;
        }
        for (String col : columnNames) {
            if (this.header.get(col) == null) {
                throw new IllegalArgumentException("Column '" + col + "' does not exist");
            }
        }
        for (String col : columnNames) {
            this.removeColumn(col);
        }
        if (this.indexColumn != null && columnNames.contains(this.indexColumn)) {
            this.indexColumn = null;
            this.index.clear();
        }
        return this;
    }

    @Override
    public IDataFrame head() {
        return head(5);
    }

    @Override
    public IDataFrame head(int n) {
        if (n <= 0 || this.size() == 0) {
            return slice(0, 0);
        }
        int limit = Math.min(n, this.size());
        return slice(0, limit);
    }

    @Override
    public IDataFrame tail() {
        return tail(5);
    }

    @Override
    public IDataFrame tail(int n) {
        if (n <= 0 || this.size() == 0) {
            return slice(0, 0);
        }
        int start = Math.max(0, this.size() - n);
        return slice(start, this.size());
    }

    @Override
    public IDataFrame slice(int fromRow, int toRow) {
        DataFrame result = new DataFrame();
        result.header = this.header.copy();
        result.columnCreators.putAll(this.columnCreators);
        result.pendingTypeInferenceColumns.addAll(this.pendingTypeInferenceColumns);
        result.index = this.index.copy();
        result.indexColumn = this.indexColumn;

        int total = this.size();
        int resolvedFrom = fromRow < 0 ? total + fromRow : fromRow;
        int resolvedTo = toRow < 0 ? total + toRow : toRow;

        resolvedFrom = Math.max(0, Math.min(resolvedFrom, total));
        resolvedTo = Math.max(resolvedFrom, Math.min(resolvedTo, total));

        for (ColumnVector vec : this.columns) {
            result.columns.add(vec.slice(resolvedFrom, resolvedTo));
        }
        return result;
    }

    @Override
    public Row iloc(int index) {
        int total = this.size();
        int resolved = index < 0 ? total + index : index;
        if (resolved < 0 || resolved >= total) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for DataFrame with " + total + " rows");
        }
        return getRow(resolved);
    }

    @Override
    public Object iloc(int rowIndex, int colIndex) {
        int total = this.size();
        int resolvedRow = rowIndex < 0 ? total + rowIndex : rowIndex;
        if (resolvedRow < 0 || resolvedRow >= total) {
            throw new IndexOutOfBoundsException("Index " + rowIndex + " out of bounds for DataFrame with " + total + " rows");
        }
        int totalCols = this.header.size();
        int resolvedCol = colIndex < 0 ? totalCols + colIndex : colIndex;
        if (resolvedCol < 0 || resolvedCol >= totalCols) {
            throw new IndexOutOfBoundsException("Column index " + colIndex + " out of bounds for length " + totalCols);
        }
        return this.columns.get(resolvedCol).getObject(resolvedRow);
    }

    @Override
    public Object iloc(int rowIndex, String columnName) {
        int total = this.size();
        int resolvedRow = rowIndex < 0 ? total + rowIndex : rowIndex;
        if (resolvedRow < 0 || resolvedRow >= total) {
            throw new IndexOutOfBoundsException("Index " + rowIndex + " out of bounds for DataFrame with " + total + " rows");
        }
        ColumnVector vec = getVector(columnName);
        if (vec == null) {
            throw new IllegalArgumentException("Column '" + columnName + "' does not exist");
        }
        return vec.getObject(resolvedRow);
    }

    @Override
    public ILoc iloc() {
        return new ILoc(null, this);
    }

    @Override
    public IDataFrame setIndex(String columnName) {
        if (columnName != null && this.header.get(columnName) == null) {
            throw new IllegalArgumentException("Column '" + columnName + "' does not exist in DataFrame");
        }
        this.indexColumn = columnName;
        this.index.setColumnName(columnName);
        return this;
    }

    @Override
    public IDataFrame resetIndex() {
        this.indexColumn = null;
        this.index.clear();
        return this;
    }

    @Override
    public Row loc(Object key) {
        if (this.indexColumn == null) {
            throw new IllegalStateException("Cannot use loc(key) without setting an index column. Call setIndex(columnName) first.");
        }
        ColumnVector indexVec = getVector(this.indexColumn);
        int total = this.size();
        for (int i = 0; i < total; i++) {
            Object val = indexVec != null ? indexVec.getObject(i) : null;
            if (Objects.equals(val, key) || (val != null && key != null && val.toString().equals(key.toString()))) {
                return getRow(i);
            }
        }
        throw new IllegalArgumentException("Key '" + key + "' not found in index column '" + this.indexColumn + "'");
    }

    @Override
    public Object loc(Object key, String column) {
        Row row = loc(key);
        if (this.header.get(column) == null) {
            throw new IllegalArgumentException("Column '" + column + "' does not exist");
        }
        return row.get(column);
    }

    @Override
    public Loc loc() {
        return new Loc(null, this);
    }

    @Override
    public IDataFrame fillna(Object value) {
        for (int i = 0; i < this.columns.size(); i++) {
            ColumnVector vec = this.columns.get(i);
            if (!canAccept(vec, value)) {
                ColumnVector promoted = promoteToObjectVector(vec);
                this.columns.set(i, promoted);
                this.header.alterColumnType(promoted.getName(), Object.class);
                promoted.fillna(value);
            } else {
                vec.fillna(value);
            }
        }
        return this;
    }

    @Override
    public IDataFrame fillna(String column, Object value) {
        if (this.header.get(column) == null) {
            throw new IllegalArgumentException("Column '" + column + "' does not exist");
        }
        int vecIdx = findVectorIndex(column);
        if (vecIdx != -1) {
            ColumnVector vec = this.columns.get(vecIdx);
            if (!canAccept(vec, value)) {
                ColumnVector promoted = promoteToObjectVector(vec);
                this.columns.set(vecIdx, promoted);
                this.header.alterColumnType(promoted.getName(), Object.class);
                promoted.fillna(value);
            } else {
                vec.fillna(value);
            }
        }
        return this;
    }

    @Override
    public IDataFrame fillna(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return this;
        }
        for (var entry : values.entrySet()) {
            fillna(entry.getKey(), entry.getValue());
        }
        return this;
    }

    @Override
    public IDataFrame dropna() {
        return dropna("any", (List<String>) null);
    }

    @Override
    public IDataFrame dropna(String how) {
        if ("any".equalsIgnoreCase(how) || "all".equalsIgnoreCase(how)) {
            return dropna(how, (List<String>) null);
        }
        return dropna("any", Collections.singletonList(how));
    }

    @Override
    public IDataFrame dropna(String... columns) {
        return dropna("any", columns != null ? Arrays.asList(columns) : null);
    }

    @Override
    public IDataFrame dropna(List<String> columns) {
        return dropna("any", columns);
    }

    @Override
    public IDataFrame dropna(String how, String... columns) {
        return dropna(how, columns != null ? Arrays.asList(columns) : null);
    }

    @Override
    public IDataFrame dropna(String how, List<String> columns) {
        boolean matchAll;
        if ("all".equalsIgnoreCase(how)) {
            matchAll = true;
        } else if ("any".equalsIgnoreCase(how) || how == null) {
            matchAll = false;
        } else {
            throw new IllegalArgumentException("how must be either 'any' or 'all', got: " + how);
        }

        List<String> targetColumns;
        if (columns == null || columns.isEmpty()) {
            targetColumns = new ArrayList<>();
            for (Column col : this.header.getHeader()) {
                targetColumns.add(col.columnName());
            }
        } else {
            for (String col : columns) {
                if (this.header.get(col) == null) {
                    throw new IllegalArgumentException("Column '" + col + "' does not exist");
                }
            }
            targetColumns = columns;
        }

        if (targetColumns.isEmpty() || this.size() == 0) {
            return this;
        }

        int total = this.size();
        java.util.BitSet dropMask = new java.util.BitSet(total);

        if (matchAll) {
            dropMask.set(0, total);
            for (String colName : targetColumns) {
                ColumnVector vec = getVector(colName);
                if (vec != null) {
                    dropMask.and(vec.getNulls());
                }
            }
        } else {
            for (String colName : targetColumns) {
                ColumnVector vec = getVector(colName);
                if (vec != null) {
                    dropMask.or(vec.getNulls());
                }
            }
        }

        java.util.BitSet retainMask = new java.util.BitSet(total);
        retainMask.set(0, total);
        retainMask.andNot(dropMask);
        int retainedCount = retainMask.cardinality();

        for (ColumnVector vec : this.columns) {
            vec.filterInPlace(retainMask, retainedCount);
        }

        return this;
    }

    @Override
    public IDataFrame sum(String column) {
        Column groupedColumn = this.getColumnHeader(column);
        if (groupedColumn == null || groupedColumn.columnType() != Group.class) {
            throw new SchemaMismatchException("Column '" + column + "' is not a grouped column");
        }
        ColumnVector srcVec = getVector(column);
        Class<?> finalColumnType = null;
        String columnName = column + "Sum";
        int total = this.size();
        Object[] results = new Object[total];

        for (int i = 0; i < total; i++) {
            Group value = srcVec != null ? (Group) srcVec.getObject(i) : null;
            if (value == null) {
                results[i] = null;
                continue;
            }
            if (value.getColumnType() == Integer.class) {
                finalColumnType = Integer.class;
                int integerSum = 0;
                for (var v : value.groupedValues) {
                    integerSum += (Integer) v;
                }
                results[i] = integerSum;
            } else if (value.getColumnType() == Double.class) {
                finalColumnType = Double.class;
                double doubleSum = 0.0;
                for (var v : value.groupedValues) {
                    doubleSum += (Double) v;
                }
                results[i] = doubleSum;
            }
        }

        ColumnVector newVec = ColumnVector.create(columnName, finalColumnType, Math.max(16, total), true);
        for (int i = 0; i < total; i++) {
            newVec.appendObject(results[i]);
        }
        this.columns.add(newVec);
        this.header.addColumn(new Column(columnName, finalColumnType));
        return this;
    }

    @Override
    public IDataFrame average(String column) {
        Column groupedColumn = this.getColumnHeader(column);
        if (groupedColumn == null || groupedColumn.columnType() != Group.class) {
            throw new SchemaMismatchException("Column '" + column + "' is not a grouped column");
        }
        ColumnVector srcVec = getVector(column);
        Class<?> finalColumnType = Double.class;
        String columnName = column + "Average";
        int total = this.size();
        Object[] results = new Object[total];

        for (int i = 0; i < total; i++) {
            Group value = srcVec != null ? (Group) srcVec.getObject(i) : null;
            if (value == null || value.groupedValues.isEmpty()) {
                results[i] = null;
                continue;
            }
            if (value.getColumnType() == Integer.class) {
                int integerSum = 0;
                for (var v : value.groupedValues) {
                    integerSum += (Integer) v;
                }
                results[i] = Double.valueOf(integerSum) / Double.valueOf(value.groupedValues.size());
            } else if (value.getColumnType() == Double.class) {
                double doubleSum = 0.0;
                for (var v : value.groupedValues) {
                    doubleSum += (Double) v;
                }
                results[i] = doubleSum / Double.valueOf(value.groupedValues.size());
            }
        }

        ColumnVector newVec = ColumnVector.create(columnName, finalColumnType, Math.max(16, total), true);
        for (int i = 0; i < total; i++) {
            newVec.appendObject(results[i]);
        }
        this.columns.add(newVec);
        this.header.addColumn(new Column(columnName, finalColumnType));
        return this;
    }

    @Override
    public IDataFrame maximum(String column) {
        Column groupedColumn = this.getColumnHeader(column);
        if (groupedColumn == null || groupedColumn.columnType() != Group.class) {
            throw new SchemaMismatchException("Column '" + column + "' is not a grouped column");
        }
        ColumnVector srcVec = getVector(column);
        Class<?> finalColumnType = null;
        String columnName = column + "Maximum";
        int total = this.size();
        Object[] results = new Object[total];

        for (int i = 0; i < total; i++) {
            Group value = srcVec != null ? (Group) srcVec.getObject(i) : null;
            if (value == null || value.groupedValues.isEmpty()) {
                results[i] = null;
                continue;
            }
            if (value.getColumnType() == Integer.class) {
                finalColumnType = Integer.class;
                results[i] = Collections.max(value.groupedValues.stream().map(a -> (Integer) a).toList());
            } else if (value.getColumnType() == Double.class) {
                finalColumnType = Double.class;
                results[i] = Collections.max(value.groupedValues.stream().map(a -> (Double) a).toList());
            }
        }

        ColumnVector newVec = ColumnVector.create(columnName, finalColumnType, Math.max(16, total), true);
        for (int i = 0; i < total; i++) {
            newVec.appendObject(results[i]);
        }
        this.columns.add(newVec);
        this.header.addColumn(new Column(columnName, finalColumnType));
        return this;
    }

    @Override
    public IDataFrame minimum(String column) {
        Column groupedColumn = this.getColumnHeader(column);
        if (groupedColumn == null || groupedColumn.columnType() != Group.class) {
            throw new SchemaMismatchException("Column '" + column + "' is not a grouped column");
        }
        ColumnVector srcVec = getVector(column);
        Class<?> finalColumnType = null;
        String columnName = column + "Minimum";
        int total = this.size();
        Object[] results = new Object[total];

        for (int i = 0; i < total; i++) {
            Group value = srcVec != null ? (Group) srcVec.getObject(i) : null;
            if (value == null || value.groupedValues.isEmpty()) {
                results[i] = null;
                continue;
            }
            if (value.getColumnType() == Integer.class) {
                finalColumnType = Integer.class;
                results[i] = Collections.min(value.groupedValues.stream().map(a -> (Integer) a).toList());
            } else if (value.getColumnType() == Double.class) {
                finalColumnType = Double.class;
                results[i] = Collections.min(value.groupedValues.stream().map(a -> (Double) a).toList());
            }
        }

        ColumnVector newVec = ColumnVector.create(columnName, finalColumnType, Math.max(16, total), true);
        for (int i = 0; i < total; i++) {
            newVec.appendObject(results[i]);
        }
        this.columns.add(newVec);
        this.header.addColumn(new Column(columnName, finalColumnType));
        return this;
    }

    @Override
    public IDataFrame count(String column) {
        Column groupedColumn = this.getColumnHeader(column);
        if (groupedColumn == null || groupedColumn.columnType() != Group.class) {
            throw new SchemaMismatchException("Column '" + column + "' is not a grouped column");
        }
        ColumnVector srcVec = getVector(column);
        Class<?> finalColumnType = Integer.class;
        String columnName = column + "Count";
        int total = this.size();
        Object[] results = new Object[total];

        for (int i = 0; i < total; i++) {
            Group value = srcVec != null ? (Group) srcVec.getObject(i) : null;
            results[i] = value != null ? value.groupedValues.size() : 0;
        }

        ColumnVector newVec = ColumnVector.create(columnName, finalColumnType, Math.max(16, total), true);
        for (int i = 0; i < total; i++) {
            newVec.appendObject(results[i]);
        }
        this.columns.add(newVec);
        this.header.addColumn(new Column(columnName, finalColumnType));
        return this;
    }

    @Override
    public IDataFrame aggregate(String column, String separator) {
        Column groupedColumn = this.getColumnHeader(column);
        if (groupedColumn == null || groupedColumn.columnType() != Group.class) {
            throw new SchemaMismatchException("Column '" + column + "' is not a grouped column");
        }
        ColumnVector srcVec = getVector(column);
        Class<?> finalColumnType = String.class;
        String columnName = column + "Aggregate";
        int total = this.size();
        Object[] results = new Object[total];

        for (int i = 0; i < total; i++) {
            Group value = srcVec != null ? (Group) srcVec.getObject(i) : null;
            if (value != null) {
                results[i] = value.groupedValues.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(separator));
            } else {
                results[i] = "";
            }
        }

        ColumnVector newVec = ColumnVector.create(columnName, finalColumnType, Math.max(16, total), true);
        for (int i = 0; i < total; i++) {
            newVec.appendObject(results[i]);
        }
        this.columns.add(newVec);
        this.header.addColumn(new Column(columnName, finalColumnType));
        return this;
    }

    @Override
    public IDataFrame orderBy(Orderer... orderer) {
        int total = this.size();
        if (total <= 1 || orderer == null || orderer.length == 0) {
            return this;
        }

        Integer[] indices = new Integer[total];
        for (int i = 0; i < total; i++) {
            indices[i] = i;
        }

        Arrays.sort(indices, (idxA, idxB) -> {
            for (var order : orderer) {
                if (order == null || order.column() == null) {
                    continue;
                }
                String columnName = order.column();
                ColumnVector vec = getVector(columnName);
                if (vec == null) {
                    continue;
                }
                Object valA = vec.getObject(idxA);
                Object valB = vec.getObject(idxB);

                boolean nullA = NA.isNA(valA);
                boolean nullB = NA.isNA(valB);

                if (nullA && nullB) {
                    continue;
                }
                if (nullA) {
                    return 1; // nulls always placed last
                }
                if (nullB) {
                    return -1; // nulls always placed last
                }

                Class<?> declaredType = findDeclaredColumnType(columnName);
                int cmp = compareNonNullValues(valA, valB, declaredType);
                if (cmp != 0) {
                    return order.type() == Order.DESCENDING ? -cmp : cmp;
                }
            }
            return 0;
        });

        int[] primitiveIndices = new int[total];
        for (int i = 0; i < total; i++) {
            primitiveIndices[i] = indices[i];
        }

        for (ColumnVector vec : this.columns) {
            vec.permute(primitiveIndices);
        }

        return this;
    }

    private Class<?> findDeclaredColumnType(String columnName) {
        for (Column column : this.header.getHeader()) {
            if (column != null && columnName.equals(column.columnName())) {
                return column.columnType();
            }
        }
        return null;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private int compareNonNullValues(Object rawA, Object rawB, Class<?> declaredType) {
        Object valA = this.coerceToDeclaredType(rawA, declaredType);
        Object valB = this.coerceToDeclaredType(rawB, declaredType);

        if (valA instanceof Integer intA && valB instanceof Integer intB) {
            return Integer.compare(intA, intB);
        }
        if (valA instanceof Long longA && valB instanceof Long longB) {
            return Long.compare(longA, longB);
        }
        if (valA instanceof Double doubleA && valB instanceof Double doubleB) {
            return Double.compare(doubleA, doubleB);
        }
        if (valA instanceof Float floatA && valB instanceof Float floatB) {
            return Float.compare(floatA, floatB);
        }
        if (valA instanceof Short shortA && valB instanceof Short shortB) {
            return Short.compare(shortA, shortB);
        }
        if (valA instanceof Byte byteA && valB instanceof Byte byteB) {
            return Byte.compare(byteA, byteB);
        }
        if (valA instanceof Boolean boolA && valB instanceof Boolean boolB) {
            return Boolean.compare(boolA, boolB);
        }
        if (valA instanceof Instant instA && valB instanceof Instant instB) {
            return instA.compareTo(instB);
        }
        if (valA instanceof LocalDate dateA && valB instanceof LocalDate dateB) {
            return dateA.compareTo(dateB);
        }
        if (valA instanceof LocalDateTime dtA && valB instanceof LocalDateTime dtB) {
            return dtA.compareTo(dtB);
        }
        if (valA instanceof BigDecimal decA && valB instanceof BigDecimal decB) {
            return decA.compareTo(decB);
        }
        if (valA instanceof Date dateA && valB instanceof Date dateB) {
            return Long.compare(dateA.getTime(), dateB.getTime());
        }
        if (valA instanceof Number numA && valB instanceof Number numB) {
            return this.compareNumbers(numA, numB);
        }
        if (valA instanceof Comparable comparableA && valB instanceof Comparable) {
            return comparableA.compareTo(valB);
        }
        throw new IllegalArgumentException(
            "Unsupported or incompatible column values for orderBy: "
                + valA.getClass().getName() + " and " + valB.getClass().getName()
        );
    }

    private Object coerceToDeclaredType(Object value, Class<?> declaredType) {
        if (value == null || declaredType == null || declaredType == String.class || declaredType.isInstance(value)) {
            return value;
        }
        try {
            if (value instanceof String str) {
                if (declaredType == Integer.class || declaredType == int.class) return Integer.valueOf(str);
                if (declaredType == Long.class || declaredType == long.class) return Long.valueOf(str);
                if (declaredType == Double.class || declaredType == double.class) return Double.valueOf(str);
                if (declaredType == Float.class || declaredType == float.class) return Float.valueOf(str);
                if (declaredType == Short.class || declaredType == short.class) return Short.valueOf(str);
                if (declaredType == Byte.class || declaredType == byte.class) return Byte.valueOf(str);
                if (declaredType == Boolean.class || declaredType == boolean.class) return Boolean.valueOf(str);
                if (declaredType == BigDecimal.class) return new BigDecimal(str);
                if (declaredType == Instant.class) return Instant.parse(str);
                if (declaredType == LocalDate.class) return LocalDate.parse(str);
                if (declaredType == LocalDateTime.class) return LocalDateTime.parse(str);
            }
        } catch (Exception ignored) {
            return value;
        }
        return value;
    }

    @Override
    public IDataFrame join(IDataFrame other, JoinType how, List<String> leftOn, List<String> rightOn, String leftSuffix, String rightSuffix) {
        return DataFrameJoiner.join(this, other, how, leftOn, rightOn, leftSuffix, rightSuffix);
    }

    private int compareNumbers(Number numA, Number numB) {
        if (numA instanceof Double || numB instanceof Double || numA instanceof Float || numB instanceof Float) {
            double dA = numA.doubleValue();
            double dB = numB.doubleValue();
            if (Double.isNaN(dA) || Double.isNaN(dB) || Double.isInfinite(dA) || Double.isInfinite(dB)) {
                return Double.compare(dA, dB);
            }
        }
        return new BigDecimal(numA.toString()).compareTo(new BigDecimal(numB.toString()));
    }

    @Override
    public DataFrame copy() {
        DataFrame copy = new DataFrame();
        copy.header = this.header.copy();
        copy.columnCreators.putAll(this.columnCreators);
        copy.pendingTypeInferenceColumns.addAll(this.pendingTypeInferenceColumns);
        copy.index = this.index.copy();
        copy.indexColumn = this.indexColumn;
        for (ColumnVector vec : this.columns) {
            copy.columns.add(vec.copy());
        }
        return copy;
    }

    @Override
    public void clear() {
        this.columns.clear();
        this.header.clear();
        this.index.clear();
        this.indexColumn = null;
        this.columnCreators.clear();
        this.pendingTypeInferenceColumns.clear();
    }

    @Override
    public void clearRows() {
        for (ColumnVector vec : this.columns) {
            vec.clear();
        }
    }
}
