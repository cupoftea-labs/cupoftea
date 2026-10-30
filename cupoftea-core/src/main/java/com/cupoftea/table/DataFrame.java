package com.cupoftea.table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.LongUnaryOperator;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.cupoftea.exceptions.ConversionException;
import com.cupoftea.exceptions.SchemaMismatchException;
import com.cupoftea.table.converters.BigDecimalConverter;
import com.cupoftea.table.converters.BooleanConverter;
import com.cupoftea.table.converters.CategoricalConverter;
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
import com.cupoftea.table.vector.DictionaryColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.FloatColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.Introselect;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.ObjectColumnVector;
import com.cupoftea.table.vector.ParallelVectorEngine;
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
    private static final CategoricalConverter CATEGORICAL_CONVERTER = new CategoricalConverter();
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
        Map.entry(Categorical.class, CATEGORICAL_CONVERTER),
        Map.entry(Date.class, DATE_CONVERTER),
        Map.entry(LocalDate.class, LOCAL_DATE_CONVERTER),
        Map.entry(LocalDateTime.class, LOCAL_DATE_TIME_CONVERTER),
        Map.entry(Instant.class, INSTANT_CONVERTER),
        Map.entry(BigDecimal.class, BIG_DECIMAL_CONVERTER)
    );

    protected List<ColumnVector> columns = new ArrayList<>();
    private final HashMap<String, IColumnCreator> columnCreators = new HashMap<>();
    private final HashSet<String> pendingTypeInferenceColumns = new HashSet<>();

    private record ColumnarRowContext(
            ColumnVector[] vectors,
            Map<String, Integer> columnIndex,
            List<String> columnOrder) {
        Row rowAt(int rowIndex) {
            return new Row(vectors, columnIndex, columnOrder, rowIndex);
        }
    }

    public static final int DEFAULT_PARALLEL_THRESHOLD = ParallelVectorEngine.DEFAULT_PARALLEL_THRESHOLD;

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

    public DataFrame(List<ColumnVector> vectors) {
        super();
        if (vectors != null) {
            for (ColumnVector vec : vectors) {
                this.columns.add(vec);
                this.header.addColumn(new Column(vec.getName(), vec.getType(), vec.isNullable()));
            }
        }
    }

    public static DataFrame fromVectors(List<ColumnVector> vectors) {
        DataFrame df = new DataFrame();
        if (vectors == null || vectors.isEmpty()) {
            return df;
        }
        int expectedSize = vectors.get(0).size();
        for (ColumnVector vec : vectors) {
            Objects.requireNonNull(vec, "ColumnVector cannot be null");
            if (vec.size() != expectedSize) {
                throw new SchemaMismatchException(
                    "ColumnVector '" + vec.getName() + "' has size " + vec.size()
                        + ", expected " + expectedSize
                );
            }
            df.header.addColumn(new Column(vec.getName(), vec.getType(), vec.isNullable()));
            df.columns.add(vec);
        }
        return df;
    }

    private ColumnarRowContext buildRowContext() {
        if (this.header.size() > 0 && this.columns.size() != this.header.size()) {
            syncColumnsWithHeader();
        }
        int colCount = this.header.size();
        ColumnVector[] vecArray = new ColumnVector[colCount];
        Map<String, Integer> colIndexMap = new LinkedHashMap<>(colCount * 2);
        List<String> colOrder = new ArrayList<>(colCount);
        ArrayList<Column> hdrs = this.header.getHeader();
        for (int i = 0; i < colCount; i++) {
            String name = hdrs.get(i).columnName();
            colOrder.add(name);
            colIndexMap.put(name, i);
            vecArray[i] = (i < this.columns.size() && name.equals(this.columns.get(i).getName()))
                    ? this.columns.get(i)
                    : getVector(name);
        }
        return new ColumnarRowContext(vecArray, colIndexMap, Collections.unmodifiableList(colOrder));
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
                if (col.columnType() == Categorical.class || (col.columnType() != null && DictionaryColumnVector.class.isAssignableFrom(col.columnType()))) {
                    DictionaryColumnVector dictVec = (existing instanceof DictionaryColumnVector dcv) ? dcv : existing.toDictionary(false);
                    dictVec.setName(col.columnName());
                    dictVec.setNullable(col.nullable());
                    newVectors.add(dictVec);
                } else if (col.columnType() != null && !Objects.equals(col.columnType(), existing.getType())) {
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
        ColumnVector vec = computeColumnVector(columnName, columnType, columnCreator);
        Class<?> resolvedType = vec.getType();
        this.columnCreators.put(columnName, columnCreator);
        this.header.addColumn(index, new Column(columnName, resolvedType));
        this.columns.add(index, vec);
        return this;
    }

    private ColumnVector computeColumnVector(String columnName, @Nullable Class<?> columnType, IColumnCreator columnCreator) {
        Class<?> resolvedType = columnType;
        int rowCount = this.size();
        Object[] values = new Object[rowCount];

        if (rowCount > 0) {
            ColumnarRowContext ctx = buildRowContext();
            if (shouldExecuteParallel(rowCount)) {
                ParallelVectorEngine.executeChunks(rowCount, getExecutor(), (chunkIdx, start, end) -> {
                    for (int i = start; i < end; i++) {
                        values[i] = columnCreator.createColumn(ctx.rowAt(i));
                    }
                    return null;
                });
            } else {
                for (int i = 0; i < rowCount; i++) {
                    values[i] = columnCreator.createColumn(ctx.rowAt(i));
                }
            }

            if (resolvedType == null) {
                for (int i = 0; i < rowCount; i++) {
                    if (values[i] != null && !NA.isNA(values[i])) {
                        resolvedType = values[i].getClass();
                        break;
                    }
                }
            }
        }

        if (resolvedType == null) {
            resolvedType = Object.class;
            this.pendingTypeInferenceColumns.add(columnName);
        } else {
            this.pendingTypeInferenceColumns.remove(columnName);
        }

        ColumnVector vec = ColumnVector.create(columnName, resolvedType, Math.max(16, rowCount), true);
        for (int i = 0; i < rowCount; i++) {
            vec.appendObject(values[i]);
        }
        return vec;
    }

    // --- Vectorized Map & Column Transformations ---

    @Override
    public IDataFrame map(String columnName, IColumnCreator columnCreator) {
        return map(columnName, null, columnCreator);
    }

    @Override
    public IDataFrame map(String columnName, @Nullable Class<?> columnType, IColumnCreator columnCreator) {
        Objects.requireNonNull(columnName, "Column name cannot be null");
        Objects.requireNonNull(columnCreator, "Column creator cannot be null");

        int existingIdx = findVectorIndex(columnName);
        if (existingIdx == -1) {
            return addColumn(this.header.columnCount(), columnName, columnType, columnCreator);
        }

        Class<?> targetType = columnType != null ? columnType : this.header.get(existingIdx).columnType();
        ColumnVector newVec = computeColumnVector(columnName, targetType, columnCreator);
        this.columns.set(existingIdx, newVec);
        this.header.alterColumnType(columnName, newVec.getType());
        this.columnCreators.put(columnName, columnCreator);
        return this;
    }

    @Override
    public ColumnRef col(String columnName) {
        ColumnVector vec = getColumnVector(columnName);
        if (vec == null) {
            throw new IllegalArgumentException("Column '" + columnName + "' does not exist in DataFrame");
        }
        return new ColumnRef(this, vec);
    }

    @Override
    public IDataFrame withColumn(String columnName, ColumnVector vector) {
        Objects.requireNonNull(columnName, "Column name cannot be null");
        Objects.requireNonNull(vector, "Column vector cannot be null");
        int currentSize = this.size();
        if (this.columns.isEmpty() && currentSize == 0) {
            vector.setName(columnName);
            this.header.addColumn(columnName, vector.getType(), vector.isNullable());
            this.columns.add(vector);
            return this;
        }
        if (vector.size() != currentSize) {
            throw new SchemaMismatchException("Column data size (" + vector.size() + ") does not match DataFrame size (" + currentSize + ")");
        }
        int existingIdx = findVectorIndex(columnName);
        vector.setName(columnName);
        if (existingIdx != -1) {
            this.columns.set(existingIdx, vector);
            this.header.alterColumnType(columnName, vector.getType());
            this.columnCreators.remove(columnName);
            this.pendingTypeInferenceColumns.remove(columnName);
        } else {
            this.header.addColumn(columnName, vector.getType(), vector.isNullable());
            this.columns.add(vector);
        }
        return this;
    }

    @Override
    public IDataFrame withColumn(String columnName, ColumnRef columnRef) {
        Objects.requireNonNull(columnRef, "ColumnRef cannot be null");
        return withColumn(columnName, columnRef.getVector().copy());
    }

    @Override
    public IDataFrame withColumn(String columnName, IColumnCreator columnCreator) {
        return withColumn(columnName, null, columnCreator);
    }

    @Override
    public IDataFrame withColumn(String columnName, Class<?> columnType, IColumnCreator columnCreator) {
        int existingIdx = findVectorIndex(columnName);
        if (existingIdx != -1) {
            removeColumn(existingIdx);
            return addColumn(existingIdx, columnName, columnType, columnCreator);
        }
        return addColumn(columnName, columnType, columnCreator);
    }

    @Override
    public IDataFrame transformColumn(String columnName, ColumnVector vector) {
        Objects.requireNonNull(columnName, "Column name cannot be null");
        Objects.requireNonNull(vector, "Column vector cannot be null");
        int idx = findVectorIndex(columnName);
        if (idx == -1) {
            throw new IllegalArgumentException("Column '" + columnName + "' does not exist in DataFrame");
        }
        if (vector.size() != this.size()) {
            throw new SchemaMismatchException("Transformed vector size (" + vector.size() + ") does not match DataFrame size (" + this.size() + ")");
        }
        vector.setName(columnName);
        this.columns.set(idx, vector);
        this.header.alterColumnType(columnName, vector.getType());
        this.columnCreators.remove(columnName);
        this.pendingTypeInferenceColumns.remove(columnName);
        return this;
    }

    @Override
    public IDataFrame transformColumn(String columnName, ColumnRef columnRef) {
        Objects.requireNonNull(columnRef, "ColumnRef cannot be null");
        return transformColumn(columnName, columnRef.getVector());
    }

    @Override
    public IDataFrame transformColumn(String columnName, Function<ColumnVector, ColumnVector> transformer) {
        Objects.requireNonNull(transformer, "transformer cannot be null");
        ColumnVector current = getColumnVector(columnName);
        if (current == null) {
            throw new IllegalArgumentException("Column '" + columnName + "' does not exist in DataFrame");
        }
        ColumnVector transformed = transformer.apply(current);
        if (transformed == null) {
            throw new NullPointerException("Transformer returned null for column '" + columnName + "'");
        }
        return transformColumn(columnName, transformed);
    }

    @Override
    public IDataFrame mapColumn(String columnName, Function<Object, ?> valueMapper) {
        return mapColumn(columnName, null, valueMapper);
    }

    @Override
    public IDataFrame mapColumn(String columnName, @Nullable Class<?> targetType, Function<Object, ?> valueMapper) {
        Objects.requireNonNull(columnName, "Column name cannot be null");
        Objects.requireNonNull(valueMapper, "Mapper function cannot be null");
        int vecIdx = findVectorIndex(columnName);
        if (vecIdx == -1) {
            throw new SchemaMismatchException("Column not found: " + columnName);
        }
        ColumnVector oldVec = this.columns.get(vecIdx);
        Class<?> resolvedType = targetType != null ? targetType : oldVec.getType();
        ColumnVector newVec = isParallel()
                ? oldVec.map(valueMapper, resolvedType, getExecutor(), this.parallelThreshold)
                : oldVec.map(valueMapper, resolvedType);
        this.columns.set(vecIdx, newVec);
        this.header.alterColumnType(columnName, resolvedType);
        return this;
    }

    @Override
    public IDataFrame mapInt(String columnName, IntUnaryOperator mapper) {
        Objects.requireNonNull(columnName, "Column name cannot be null");
        Objects.requireNonNull(mapper, "Mapper cannot be null");
        int vecIdx = findVectorIndex(columnName);
        if (vecIdx == -1) {
            throw new SchemaMismatchException("Column not found: " + columnName);
        }
        ColumnVector vec = this.columns.get(vecIdx);
        if (vec instanceof IntColumnVector intVec) {
            IntColumnVector mapped = isParallel()
                    ? intVec.mapInt(mapper, getExecutor(), this.parallelThreshold)
                    : intVec.mapInt(mapper);
            this.columns.set(vecIdx, mapped);
            return this;
        }
        return mapColumn(columnName, Integer.class, v -> v == null ? null : mapper.applyAsInt(((Number) v).intValue()));
    }

    @Override
    public IDataFrame mapLong(String columnName, LongUnaryOperator mapper) {
        Objects.requireNonNull(columnName, "Column name cannot be null");
        Objects.requireNonNull(mapper, "Mapper cannot be null");
        int vecIdx = findVectorIndex(columnName);
        if (vecIdx == -1) {
            throw new SchemaMismatchException("Column not found: " + columnName);
        }
        ColumnVector vec = this.columns.get(vecIdx);
        if (vec instanceof LongColumnVector longVec) {
            LongColumnVector mapped = isParallel()
                    ? longVec.mapLong(mapper, getExecutor(), this.parallelThreshold)
                    : longVec.mapLong(mapper);
            this.columns.set(vecIdx, mapped);
            return this;
        }
        return mapColumn(columnName, Long.class, v -> v == null ? null : mapper.applyAsLong(((Number) v).longValue()));
    }

    @Override
    public IDataFrame mapDouble(String columnName, DoubleUnaryOperator mapper) {
        Objects.requireNonNull(columnName, "Column name cannot be null");
        Objects.requireNonNull(mapper, "Mapper cannot be null");
        int vecIdx = findVectorIndex(columnName);
        if (vecIdx == -1) {
            throw new SchemaMismatchException("Column not found: " + columnName);
        }
        ColumnVector vec = this.columns.get(vecIdx);
        if (vec instanceof DoubleColumnVector doubleVec) {
            DoubleColumnVector mapped = isParallel()
                    ? doubleVec.mapDouble(mapper, getExecutor(), this.parallelThreshold)
                    : doubleVec.mapDouble(mapper);
            this.columns.set(vecIdx, mapped);
            return this;
        }
        return mapColumn(columnName, Double.class, v -> v == null ? null : mapper.applyAsDouble(((Number) v).doubleValue()));
    }

    @Override
    @SuppressWarnings("unchecked")
    public IDataFrame map(Function<Row, ? extends Map<String, Object>> rowMapper) {
        Objects.requireNonNull(rowMapper, "Row mapper cannot be null");
        int rowCount = this.size();
        DataFrame result = new DataFrame();
        copyParallelConfigTo(result);
        result.header = this.header.copy();
        for (Column col : result.header.getHeader()) {
            result.columns.add(ColumnVector.create(col, Math.max(16, rowCount)));
        }
        if (rowCount == 0) {
            return result;
        }

        ColumnarRowContext ctx = buildRowContext();
        Map<String, Object>[] mappedRows = new Map[rowCount];
        if (shouldExecuteParallel(rowCount)) {
            ParallelVectorEngine.executeChunks(rowCount, getExecutor(), (chunkIdx, start, end) -> {
                for (int i = start; i < end; i++) {
                    mappedRows[i] = rowMapper.apply(ctx.rowAt(i));
                }
                return null;
            });
        } else {
            for (int i = 0; i < rowCount; i++) {
                mappedRows[i] = rowMapper.apply(ctx.rowAt(i));
            }
        }

        for (int i = 0; i < rowCount; i++) {
            Map<String, Object> r = mappedRows[i];
            if (r != null) {
                result.addRow(r);
            }
        }
        return result;
    }

    // --- Vectorized Whole-DataFrame Aggregations ---

    @Override
    public IDataFrame agg(Aggregate... aggregates) {
        if (aggregates == null || aggregates.length == 0) {
            throw new IllegalArgumentException("At least one aggregate must be specified");
        }
        List<Column> srcHeaders = this.header.getHeader();
        for (Aggregate agg : aggregates) {
            if (agg.getColumn() != null && this.header.get(agg.getColumn()) == null) {
                throw new SchemaMismatchException("Column '" + agg.getColumn() + "' not found in DataFrame");
            }
        }

        DataFrame resultDf = new DataFrame();
        copyParallelConfigTo(resultDf);
        ArrayList<Column> newHeaders = new ArrayList<>(aggregates.length);
        for (Aggregate agg : aggregates) {
            newHeaders.add(new Column(agg.getOutputColumnName(), agg.getResultType(srcHeaders)));
        }
        resultDf.setHeaders(newHeaders);

        int totalRows = this.size();
        boolean runParallel = shouldExecuteParallel(totalRows);
        ExecutorService pool = getExecutor();
        HashMap<String, Object> summaryRow = new HashMap<>(aggregates.length);
        ArrayList<HashMap<String, Object>> lazyRows = null;

        for (Aggregate agg : aggregates) {
            Object val;
            if (agg.supportsVectorizedExecution()) {
                ColumnVector vec = agg.getColumn() != null ? getVector(agg.getColumn()) : null;
                val = agg.computeVector(vec, totalRows, srcHeaders, runParallel, pool, this.parallelThreshold);
            } else {
                if (lazyRows == null) {
                    lazyRows = getInternalRows();
                }
                val = agg.compute(lazyRows, srcHeaders);
            }
            summaryRow.put(agg.getOutputColumnName(), val);
        }

        resultDf.addRow(summaryRow);
        return resultDf;
    }

    private ColumnVector requireColumnVector(String column) {
        if (column == null || this.header.get(column) == null) {
            throw new SchemaMismatchException("Column '" + column + "' not found in DataFrame");
        }
        return getVector(column);
    }

    private ColumnVector requireNumericColumnVector(String column) {
        ColumnVector vec = requireColumnVector(column);
        Class<?> t = vec.getType();
        if (t != null && t != Object.class
                && !Number.class.isAssignableFrom(t)
                && t != int.class && t != long.class && t != double.class && t != float.class && t != short.class && t != byte.class) {
            throw new SchemaMismatchException("Column '" + column + "' of type " + t.getSimpleName() + " is not numeric");
        }
        return vec;
    }

    @Override
    public Object sumOf(String column) {
        ColumnVector vec = requireNumericColumnVector(column);
        return isParallel() ? vec.sum(getExecutor(), this.parallelThreshold) : vec.sum(0, vec.size());
    }

    @Override
    public Double meanOf(String column) {
        ColumnVector vec = requireNumericColumnVector(column);
        Object res = isParallel() ? vec.mean(getExecutor(), this.parallelThreshold) : vec.mean(0, vec.size());
        return res instanceof Number n ? n.doubleValue() : null;
    }

    @Override
    public Object minOf(String column) {
        ColumnVector vec = requireColumnVector(column);
        return isParallel() ? vec.min(getExecutor(), this.parallelThreshold) : vec.min(0, vec.size());
    }

    @Override
    public Object maxOf(String column) {
        ColumnVector vec = requireColumnVector(column);
        return isParallel() ? vec.max(getExecutor(), this.parallelThreshold) : vec.max(0, vec.size());
    }

    @Override
    public int countOf(String column) {
        ColumnVector vec = requireColumnVector(column);
        return isParallel() ? vec.countNonNull(getExecutor(), this.parallelThreshold) : vec.countNonNull();
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

        if (newColumnType == Categorical.class || (newColumnType != null && DictionaryColumnVector.class.isAssignableFrom(newColumnType))) {
            int vecIdx = findVectorIndex(columnName);
            if (vecIdx != -1) {
                ColumnVector oldVec = this.columns.get(vecIdx);
                DictionaryColumnVector dictVec = (oldVec instanceof DictionaryColumnVector dcv) ? dcv : oldVec.toDictionary(false);
                this.columns.set(vecIdx, dictVec);
            }
            this.pendingTypeInferenceColumns.remove(columnName);
            this.header.alterColumnType(columnName, newColumnType);
            return this;
        }

        if (newColumnType == String.class) {
            int vecIdx = findVectorIndex(columnName);
            if (vecIdx != -1 && this.columns.get(vecIdx) instanceof DictionaryColumnVector dcv) {
                this.columns.set(vecIdx, dcv.toStringColumnVector());
                this.pendingTypeInferenceColumns.remove(columnName);
                this.header.alterColumnType(columnName, String.class);
                return this;
            }
        }

        IDataTypeConverter<?> converter = CONVERTERS.get(newColumnType);
        if (converter == null) {
            throw new ConversionException("Column Type not Suported for Conversion");
        }

        int vecIdx = findVectorIndex(columnName);
        if (vecIdx != -1) {
            ColumnVector oldVec = this.columns.get(vecIdx);
            int rowCount = oldVec.size();
            ColumnVector newVec = ColumnVector.create(columnName, newColumnType, Math.max(16, rowCount), oldVec.isNullable());

            if (shouldExecuteParallel(rowCount)) {
                Object[] convertedValues = new Object[rowCount];
                ParallelVectorEngine.executeChunks(rowCount, getExecutor(), (chunkIdx, start, end) -> {
                    for (int i = start; i < end; i++) {
                        if (!oldVec.isNull(i)) {
                            Object val = oldVec.getObject(i);
                            if (val != null) {
                                Class<?> actualOldType = oldColumnType != null ? oldColumnType : val.getClass();
                                convertedValues[i] = converter.convert(val, actualOldType);
                            }
                        }
                    }
                    return null;
                });
                for (int i = 0; i < rowCount; i++) {
                    if (oldVec.isNull(i)) {
                        newVec.appendNull();
                    } else {
                        newVec.appendObject(convertedValues[i]);
                    }
                }
            } else {
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
        if (vec instanceof DictionaryColumnVector) {
            return val instanceof String || val instanceof Categorical || val instanceof CharSequence;
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
        for (int i = 0; i < this.header.size(); i++) {
            Column col = this.header.get(i);
            ColumnVector vec = (i < this.columns.size() && col.columnName().equals(this.columns.get(i).getName()))
                    ? this.columns.get(i)
                    : getVector(col.columnName());
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
        if (total == 0) {
            return this;
        }
        ColumnarRowContext ctx = buildRowContext();

        if (shouldExecuteParallel(total)) {
            this.columns = executeParallelFilterVectors(ctx, row -> !Boolean.TRUE.equals(filter.filter(row)), total);
            return this;
        }

        BitSet retainMask = new BitSet(total);
        int retainedCount = 0;
        for (int i = 0; i < total; i++) {
            if (!Boolean.TRUE.equals(filter.filter(ctx.rowAt(i)))) {
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
        copyParallelConfigTo(filteredDF);
        filteredDF.header = this.header.copy();
        filteredDF.columnCreators.putAll(this.columnCreators);
        filteredDF.pendingTypeInferenceColumns.addAll(this.pendingTypeInferenceColumns);
        filteredDF.index = this.index.copy();
        filteredDF.indexColumn = this.indexColumn;

        int total = this.size();
        if (total == 0) {
            for (ColumnVector vec : this.columns) {
                filteredDF.columns.add(vec.copy());
            }
            return filteredDF;
        }

        ColumnarRowContext ctx = buildRowContext();
        if (shouldExecuteParallel(total)) {
            filteredDF.columns = executeParallelFilterVectors(ctx, filter, total);
            return filteredDF;
        }

        BitSet matchMask = new BitSet(total);
        int matchCount = 0;
        for (int i = 0; i < total; i++) {
            if (Boolean.TRUE.equals(filter.filter(ctx.rowAt(i)))) {
                matchMask.set(i);
                matchCount++;
            }
        }

        for (ColumnVector vec : this.columns) {
            filteredDF.columns.add(vec.filter(matchMask, matchCount));
        }

        return filteredDF;
    }

    // --- Dictionary / Categorical Encoding & Code-Accelerated Filtering ---

    @Override
    public IDataFrame encodeDictionary(String... columnNames) {
        return encodeDictionary(false, columnNames);
    }

    @Override
    public IDataFrame encodeDictionary(boolean useShortCodes, String... columnNames) {
        List<String> targets;
        if (columnNames == null || columnNames.length == 0) {
            targets = new ArrayList<>();
            for (ColumnVector vec : this.columns) {
                if (vec instanceof StringColumnVector || vec instanceof DictionaryColumnVector || vec.getType() == String.class || vec.getType() == Categorical.class) {
                    targets.add(vec.getName());
                }
            }
        } else {
            targets = Arrays.asList(columnNames);
            for (String col : targets) {
                if (col == null || getVector(col) == null) {
                    throw new SchemaMismatchException("Column '" + col + "' does not exist in DataFrame");
                }
            }
        }

        for (String col : targets) {
            int idx = findVectorIndex(col);
            if (idx != -1) {
                ColumnVector vec = this.columns.get(idx);
                DictionaryColumnVector dictVec = vec.toDictionary(useShortCodes);
                this.columns.set(idx, dictVec);
            }
        }
        return this;
    }

    @Override
    public IDataFrame decodeDictionary(String... columnNames) {
        List<String> targets;
        if (columnNames == null || columnNames.length == 0) {
            targets = new ArrayList<>();
            for (ColumnVector vec : this.columns) {
                if (vec instanceof DictionaryColumnVector) {
                    targets.add(vec.getName());
                }
            }
        } else {
            targets = Arrays.asList(columnNames);
            for (String col : targets) {
                if (col == null || getVector(col) == null) {
                    throw new SchemaMismatchException("Column '" + col + "' does not exist in DataFrame");
                }
            }
        }

        for (String col : targets) {
            int idx = findVectorIndex(col);
            if (idx != -1 && this.columns.get(idx) instanceof DictionaryColumnVector dcv) {
                this.columns.set(idx, dcv.toStringColumnVector());
                if (this.header.get(col) != null && this.header.get(col).columnType() == Categorical.class) {
                    this.header.alterColumnType(col, String.class);
                }
            }
        }
        return this;
    }

    private DataFrame applyBitSetMaskFilter(BitSet matchMask, int matchCount) {
        DataFrame filteredDF = new DataFrame();
        copyParallelConfigTo(filteredDF);
        filteredDF.header = this.header.copy();
        filteredDF.columnCreators.putAll(this.columnCreators);
        filteredDF.pendingTypeInferenceColumns.addAll(this.pendingTypeInferenceColumns);
        filteredDF.index = this.index.copy();
        filteredDF.indexColumn = this.indexColumn;

        for (ColumnVector vec : this.columns) {
            filteredDF.columns.add(vec.filter(matchMask, matchCount));
        }
        return filteredDF;
    }

    @Override
    public IDataFrame filterEquals(String columnName, Object value) {
        ColumnVector vec = requireVector(columnName);
        if (vec instanceof DictionaryColumnVector dcv) {
            String target = (value == null || NA.isNA(value)) ? null : value.toString();
            BitSet mask = dcv.computeEqualsMask(target);
            return applyBitSetMaskFilter(mask, mask.cardinality());
        }
        int total = this.size();
        BitSet mask = new BitSet(total);
        int matched = 0;
        boolean targetIsNull = (value == null || NA.isNA(value));
        for (int i = 0; i < total; i++) {
            if (vec.isNull(i)) {
                if (targetIsNull) {
                    mask.set(i);
                    matched++;
                }
            } else if (!targetIsNull && Objects.equals(vec.getObject(i), value)) {
                mask.set(i);
                matched++;
            }
        }
        return applyBitSetMaskFilter(mask, matched);
    }

    @Override
    public IDataFrame filterNotEquals(String columnName, Object value) {
        ColumnVector vec = requireVector(columnName);
        if (vec instanceof DictionaryColumnVector dcv) {
            String target = (value == null || NA.isNA(value)) ? null : value.toString();
            BitSet mask = dcv.computeNotEqualsMask(target);
            return applyBitSetMaskFilter(mask, mask.cardinality());
        }
        int total = this.size();
        BitSet mask = new BitSet(total);
        int matched = 0;
        boolean targetIsNull = (value == null || NA.isNA(value));
        for (int i = 0; i < total; i++) {
            if (vec.isNull(i)) {
                if (!targetIsNull) {
                    mask.set(i);
                    matched++;
                }
            } else if (targetIsNull || !Objects.equals(vec.getObject(i), value)) {
                mask.set(i);
                matched++;
            }
        }
        return applyBitSetMaskFilter(mask, matched);
    }

    @Override
    public IDataFrame filterIn(String columnName, Collection<?> values) {
        Objects.requireNonNull(values, "values collection cannot be null");
        ColumnVector vec = requireVector(columnName);
        if (vec instanceof DictionaryColumnVector dcv) {
            List<String> strTargets = new ArrayList<>(values.size());
            for (Object v : values) {
                strTargets.add((v == null || NA.isNA(v)) ? null : v.toString());
            }
            BitSet mask = dcv.computeInMask(strTargets);
            return applyBitSetMaskFilter(mask, mask.cardinality());
        }
        Set<Object> valueSet = new HashSet<>();
        boolean matchNull = false;
        for (Object v : values) {
            if (v == null || NA.isNA(v)) {
                matchNull = true;
            } else {
                valueSet.add(v);
            }
        }
        int total = this.size();
        BitSet mask = new BitSet(total);
        int matched = 0;
        for (int i = 0; i < total; i++) {
            if (vec.isNull(i)) {
                if (matchNull) {
                    mask.set(i);
                    matched++;
                }
            } else if (valueSet.contains(vec.getObject(i))) {
                mask.set(i);
                matched++;
            }
        }
        return applyBitSetMaskFilter(mask, matched);
    }

    /**
     * Evaluates a filter predicate across contiguous vector chunks in parallel, computes prefix sums (parallel scan),
     * and consolidates matching elements into new contiguous {@link ColumnVector} instances.
     */
    private List<ColumnVector> executeParallelFilterVectors(ColumnarRowContext ctx, IFilter predicate, int total) {
        ExecutorService pool = getExecutor();
        int numChunks = ParallelVectorEngine.computeChunkCount(total, pool);
        int[][] ranges = ParallelVectorEngine.partitionRanges(total, numChunks);
        int[] chunkCounts = new int[ranges.length];

        // Phase 1: Parallel predicate evaluation per chunk
        List<int[]> chunkIndices = ParallelVectorEngine.executeRanges(ranges, pool, (chunkIdx, start, end) -> {
            int[] localMatches = new int[end - start];
            int count = 0;
            for (int r = start; r < end; r++) {
                if (Boolean.TRUE.equals(predicate.filter(ctx.rowAt(r)))) {
                    localMatches[count++] = r;
                }
            }
            chunkCounts[chunkIdx] = count;
            return localMatches;
        });

        // Phase 2: Prefix-sum (parallel scan) offset calculation
        int[] offsets = new int[ranges.length];
        int totalMatched = ParallelVectorEngine.computePrefixSums(chunkCounts, offsets);

        // Phase 3: Parallel vector compaction across chunks
        int colCount = this.columns.size();
        List<ColumnVector> targetVectors = new ArrayList<>(colCount);
        for (ColumnVector srcVec : this.columns) {
            ColumnVector dstVec = srcVec.createEmptyWithSameEncoding(Math.max(16, totalMatched));
            dstVec.setSizeUnchecked(totalMatched);
            targetVectors.add(dstVec);
        }

        if (totalMatched > 0) {
            ParallelVectorEngine.executeRanges(ranges, pool, (chunkIdx, start, end) -> {
                int count = chunkCounts[chunkIdx];
                if (count > 0) {
                    int[] indices = chunkIndices.get(chunkIdx);
                    int dstOffset = offsets[chunkIdx];
                    for (int colIdx = 0; colIdx < colCount; colIdx++) {
                        targetVectors.get(colIdx).copyIndexedValuesOnly(this.columns.get(colIdx), indices, count, dstOffset);
                    }
                }
                return null;
            });

            for (int colIdx = 0; colIdx < colCount; colIdx++) {
                ColumnVector srcVec = this.columns.get(colIdx);
                if (srcVec.hasNulls()) {
                    ColumnVector dstVec = targetVectors.get(colIdx);
                    for (int c = 0; c < ranges.length; c++) {
                        int count = chunkCounts[c];
                        if (count > 0) {
                            dstVec.applyNullsFromIndexed(srcVec, chunkIndices.get(c), count, offsets[c]);
                        }
                    }
                }
            }
        }

        return targetVectors;
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
        copyParallelConfigTo(result);
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
        BitSet dropMask = new BitSet(total);

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

        BitSet retainMask = new BitSet(total);
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

    // ==========================================
    // Statistical Operations & Parity with Pandas
    // ==========================================

    private ColumnVector requireVector(String column) {
        ColumnVector vec = getVector(column);
        if (vec == null) {
            throw new SchemaMismatchException("Column '" + column + "' does not exist in DataFrame");
        }
        return vec;
    }

    private ColumnVector requireNumericVector(String column) {
        ColumnVector vec = requireVector(column);
        if (!isNumericType(vec.getType())) {
            throw new IllegalArgumentException("Column '" + column + "' of type " + (vec.getType() != null ? vec.getType().getSimpleName() : "unknown") + " is not numeric");
        }
        return vec;
    }

    private static boolean isNumericType(Class<?> type) {
        if (type == null) return false;
        return Number.class.isAssignableFrom(type)
            || type == int.class
            || type == long.class
            || type == double.class
            || type == float.class
            || type == short.class
            || type == byte.class;
    }

    private static boolean isTemporalType(Class<?> type) {
        if (type == null) return false;
        return LocalDate.class.isAssignableFrom(type)
            || LocalDateTime.class.isAssignableFrom(type)
            || Instant.class.isAssignableFrom(type)
            || Date.class.isAssignableFrom(type);
    }

    @Override
    public Double mean(String column) {
        return requireNumericVector(column).mean();
    }

    @Override
    public Double min(String column) {
        return requireNumericVector(column).min();
    }

    @Override
    public Double max(String column) {
        return requireNumericVector(column).max();
    }

    @Override
    public Double median(String column) {
        return requireNumericVector(column).median();
    }

    @Override
    public Double variance(String column) {
        return requireNumericVector(column).variance(true);
    }

    @Override
    public Double variance(String column, boolean isSample) {
        return requireNumericVector(column).variance(isSample);
    }

    @Override
    public Double var(String column) {
        return requireNumericVector(column).var();
    }

    @Override
    public Double var(String column, boolean isSample) {
        return requireNumericVector(column).var(isSample);
    }

    @Override
    public Double standardDeviation(String column) {
        return requireNumericVector(column).standardDeviation(true);
    }

    @Override
    public Double standardDeviation(String column, boolean isSample) {
        return requireNumericVector(column).standardDeviation(isSample);
    }

    @Override
    public Double std(String column) {
        return requireNumericVector(column).std();
    }

    @Override
    public Double std(String column, boolean isSample) {
        return requireNumericVector(column).std(isSample);
    }

    @Override
    public Double quantile(String column, double q) {
        return requireNumericVector(column).quantile(q);
    }

    @Override
    public Double skewness(String column) {
        return requireNumericVector(column).skewness(true);
    }

    @Override
    public Double skewness(String column, boolean isSample) {
        return requireNumericVector(column).skewness(isSample);
    }

    @Override
    public Double skew(String column) {
        return requireNumericVector(column).skew();
    }

    @Override
    public Double kurtosis(String column) {
        return requireNumericVector(column).kurtosis(true);
    }

    @Override
    public Double kurtosis(String column, boolean isSample) {
        return requireNumericVector(column).kurtosis(isSample);
    }

    @Override
    public Double kurt(String column) {
        return requireNumericVector(column).kurt();
    }

    @Override
    public Double covariance(String col1, String col2) {
        return covariance(col1, col2, true);
    }

    @Override
    public Double covariance(String col1, String col2, boolean isSample) {
        ColumnVector v1 = requireNumericVector(col1);
        ColumnVector v2 = requireNumericVector(col2);
        int total = Math.min(v1.size(), v2.size());
        int n = 0;
        double sum1 = 0.0;
        double sum2 = 0.0;
        for (int i = 0; i < total; i++) {
            if (!v1.isNull(i) && !v2.isNull(i)) {
                double val1 = v1.getDouble(i);
                double val2 = v2.getDouble(i);
                if (!Double.isNaN(val1) && !Double.isNaN(val2)) {
                    sum1 += val1;
                    sum2 += val2;
                    n++;
                }
            }
        }
        if (n < (isSample ? 2 : 1)) {
            return Double.NaN;
        }
        double mean1 = sum1 / n;
        double mean2 = sum2 / n;
        double s12 = 0.0;
        for (int i = 0; i < total; i++) {
            if (!v1.isNull(i) && !v2.isNull(i)) {
                double val1 = v1.getDouble(i);
                double val2 = v2.getDouble(i);
                if (!Double.isNaN(val1) && !Double.isNaN(val2)) {
                    s12 += (val1 - mean1) * (val2 - mean2);
                }
            }
        }
        return s12 / (isSample ? (n - 1) : n);
    }

    @Override
    public Double cov(String col1, String col2) {
        return covariance(col1, col2, true);
    }

    @Override
    public Double cov(String col1, String col2, boolean isSample) {
        return covariance(col1, col2, isSample);
    }

    @Override
    public Double correlation(String col1, String col2) {
        ColumnVector v1 = requireNumericVector(col1);
        ColumnVector v2 = requireNumericVector(col2);
        int total = Math.min(v1.size(), v2.size());
        int n = 0;
        double sum1 = 0.0;
        double sum2 = 0.0;
        for (int i = 0; i < total; i++) {
            if (!v1.isNull(i) && !v2.isNull(i)) {
                double val1 = v1.getDouble(i);
                double val2 = v2.getDouble(i);
                if (!Double.isNaN(val1) && !Double.isNaN(val2)) {
                    sum1 += val1;
                    sum2 += val2;
                    n++;
                }
            }
        }
        if (n < 2) {
            return Double.NaN;
        }
        double mean1 = sum1 / n;
        double mean2 = sum2 / n;
        double s12 = 0.0;
        double s11 = 0.0;
        double s22 = 0.0;
        for (int i = 0; i < total; i++) {
            if (!v1.isNull(i) && !v2.isNull(i)) {
                double val1 = v1.getDouble(i);
                double val2 = v2.getDouble(i);
                if (!Double.isNaN(val1) && !Double.isNaN(val2)) {
                    double d1 = val1 - mean1;
                    double d2 = val2 - mean2;
                    s12 += d1 * d2;
                    s11 += d1 * d1;
                    s22 += d2 * d2;
                }
            }
        }
        if (s11 == 0.0 || s22 == 0.0) {
            return Double.NaN;
        }
        double r = s12 / Math.sqrt(s11 * s22);
        return Math.max(-1.0, Math.min(1.0, r));
    }

    @Override
    public Double corr(String col1, String col2) {
        return correlation(col1, col2);
    }

    @Override
    public DataFrame corr() {
        List<String> numericCols = new ArrayList<>();
        for (Column col : this.header.getHeader()) {
            if (isNumericType(col.columnType())) {
                numericCols.add(col.columnName());
            }
        }
        DataFrame matrix = new DataFrame();
        if (numericCols.isEmpty()) {
            return matrix;
        }
        ArrayList<Column> matrixHeaders = new ArrayList<>();
        matrixHeaders.add(new Column("column", String.class));
        for (String colName : numericCols) {
            matrixHeaders.add(new Column(colName, Double.class));
        }
        matrix.setHeaders(matrixHeaders);

        for (String rowCol : numericCols) {
            HashMap<String, Object> rowMap = new HashMap<>();
            rowMap.put("column", rowCol);
            for (String colCol : numericCols) {
                if (rowCol.equals(colCol)) {
                    ColumnVector v = getVector(rowCol);
                    if (v != null && v.validCount() >= 2 && v.variance() > 0.0) {
                        rowMap.put(colCol, 1.0);
                    } else if (v != null && v.validCount() == 1) {
                        rowMap.put(colCol, 1.0);
                    } else {
                        rowMap.put(colCol, Double.NaN);
                    }
                } else {
                    rowMap.put(colCol, correlation(rowCol, colCol));
                }
            }
            matrix.addRow(rowMap);
        }
        matrix.setIndex("column");
        return matrix;
    }

    @Override
    public DataFrame describe() {
        List<Column> targetCols = new ArrayList<>();
        for (Column col : this.header.getHeader()) {
            if (isNumericType(col.columnType()) || isTemporalType(col.columnType())) {
                targetCols.add(col);
            }
        }
        DataFrame summary = new DataFrame();
        if (targetCols.isEmpty()) {
            return summary;
        }
        ArrayList<Column> summaryHeaders = new ArrayList<>();
        summaryHeaders.add(new Column("stat", String.class));
        for (Column col : targetCols) {
            summaryHeaders.add(new Column(col.columnName(), isNumericType(col.columnType()) ? Double.class : Object.class));
        }
        summary.setHeaders(summaryHeaders);

        String[] statNames = new String[] { "count", "mean", "std", "min", "25%", "50%", "75%", "max" };
        Map<String, Map<String, Object>> colSummaries = new LinkedHashMap<>();

        for (Column col : targetCols) {
            String colName = col.columnName();
            ColumnVector vec = getVector(colName);
            Map<String, Object> stats = new HashMap<>();
            if (isNumericType(col.columnType())) {
                int count = vec != null ? vec.validCount() : 0;
                stats.put("count", (double) count);
                stats.put("mean", count > 0 ? vec.mean() : Double.NaN);
                stats.put("std", count > 1 ? vec.std() : Double.NaN);
                stats.put("min", count > 0 ? vec.min() : Double.NaN);
                stats.put("25%", count > 0 ? vec.quantile(0.25) : Double.NaN);
                stats.put("50%", count > 0 ? vec.median() : Double.NaN);
                stats.put("75%", count > 0 ? vec.quantile(0.75) : Double.NaN);
                stats.put("max", count > 0 ? vec.max() : Double.NaN);
            } else if (isTemporalType(col.columnType())) {
                computeTemporalSummary(vec, col.columnType(), stats);
            }
            colSummaries.put(colName, stats);
        }

        for (String statName : statNames) {
            HashMap<String, Object> row = new HashMap<>();
            row.put("stat", statName);
            for (Column col : targetCols) {
                row.put(col.columnName(), colSummaries.get(col.columnName()).get(statName));
            }
            summary.addRow(row);
        }
        summary.setIndex("stat");
        return summary;
    }

    private void computeTemporalSummary(ColumnVector vec, Class<?> type, Map<String, Object> stats) {
        if (vec == null || vec.validCount() == 0) {
            stats.put("count", 0.0);
            stats.put("mean", null);
            stats.put("std", null);
            stats.put("min", null);
            stats.put("25%", null);
            stats.put("50%", null);
            stats.put("75%", null);
            stats.put("max", null);
            return;
        }
        int count = vec.validCount();
        stats.put("count", (double) count);
        stats.put("std", null);

        double[] epochs = new double[count];
        int idx = 0;
        for (int i = 0; i < vec.size(); i++) {
            if (!vec.isNull(i)) {
                Object obj = vec.getObject(i);
                if (obj != null && !NA.isNA(obj)) {
                    epochs[idx++] = toTemporalEpoch(obj, type);
                }
            }
        }
        if (idx == 0) {
            stats.put("count", 0.0);
            return;
        }
        double meanEpoch = 0.0;
        for (int i = 0; i < idx; i++) meanEpoch += epochs[i];
        meanEpoch /= idx;

        double minEpoch = Introselect.quantile(Arrays.copyOf(epochs, idx), 0, idx, 0.0);
        double q25Epoch = Introselect.quantile(Arrays.copyOf(epochs, idx), 0, idx, 0.25);
        double q50Epoch = Introselect.quantile(Arrays.copyOf(epochs, idx), 0, idx, 0.50);
        double q75Epoch = Introselect.quantile(Arrays.copyOf(epochs, idx), 0, idx, 0.75);
        double maxEpoch = Introselect.quantile(Arrays.copyOf(epochs, idx), 0, idx, 1.0);

        stats.put("mean", fromTemporalEpoch(meanEpoch, type));
        stats.put("min", fromTemporalEpoch(minEpoch, type));
        stats.put("25%", fromTemporalEpoch(q25Epoch, type));
        stats.put("50%", fromTemporalEpoch(q50Epoch, type));
        stats.put("75%", fromTemporalEpoch(q75Epoch, type));
        stats.put("max", fromTemporalEpoch(maxEpoch, type));
    }

    private static double toTemporalEpoch(Object obj, Class<?> type) {
        if (obj instanceof LocalDate ld) {
            return ld.toEpochDay();
        } else if (obj instanceof LocalDateTime ldt) {
            return ldt.toEpochSecond(java.time.ZoneOffset.UTC);
        } else if (obj instanceof Instant inst) {
            return inst.toEpochMilli();
        } else if (obj instanceof Date d) {
            return d.getTime();
        }
        return 0.0;
    }

    private static Object fromTemporalEpoch(double epoch, Class<?> type) {
        if (Double.isNaN(epoch)) return null;
        long val = Math.round(epoch);
        if (LocalDate.class.isAssignableFrom(type)) {
            return LocalDate.ofEpochDay(val);
        } else if (LocalDateTime.class.isAssignableFrom(type)) {
            return LocalDateTime.ofEpochSecond(val, 0, java.time.ZoneOffset.UTC);
        } else if (Instant.class.isAssignableFrom(type)) {
            return Instant.ofEpochMilli(val);
        } else if (Date.class.isAssignableFrom(type)) {
            return new Date(val);
        }
        return val;
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
                if (vec instanceof DictionaryColumnVector dcv) {
                    boolean nullA = dcv.isNull(idxA);
                    boolean nullB = dcv.isNull(idxB);
                    if (nullA && nullB) {
                        continue;
                    }
                    if (nullA) {
                        return 1;
                    }
                    if (nullB) {
                        return -1;
                    }
                    int cmp = dcv.compareRows(idxA, idxB);
                    if (cmp != 0) {
                        return order.type() == Order.DESCENDING ? -cmp : cmp;
                    }
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
        copyParallelConfigTo(copy);
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

    // ==========================================
    // Window, Cumulative, and Analytical Operations
    // ==========================================

    public void replaceOrAddColumn(String columnName, Class<?> type, Object[] values) {
        int total = values.length;
        ColumnVector newVec = ColumnVector.create(columnName, type != null ? type : Object.class, Math.max(16, total), true);
        for (int i = 0; i < total; i++) {
            if (values[i] != null && !NA.isNA(values[i])) {
                newVec.appendObject(values[i]);
            } else {
                newVec.appendNull();
            }
        }
        int existingIdx = findVectorIndex(columnName);
        if (existingIdx != -1) {
            this.columns.set(existingIdx, newVec);
            this.header.alterColumnType(columnName, type != null ? type : Object.class);
        } else {
            this.header.addColumn(new Column(columnName, type != null ? type : Object.class));
            this.columns.add(newVec);
        }
    }

    private List<String> resolveNumericColumns(String... columns) {
        List<String> targetCols = new ArrayList<>();
        if (columns == null || columns.length == 0) {
            for (Column col : this.header.getHeader()) {
                if (isNumericType(col.columnType())) {
                    targetCols.add(col.columnName());
                }
            }
        } else {
            for (String col : columns) {
                ColumnVector vec = getVector(col);
                if (vec == null) {
                    throw new SchemaMismatchException("Column '" + col + "' does not exist in DataFrame");
                }
                if (!isNumericType(vec.getType())) {
                    throw new IllegalArgumentException("Column '" + col + "' of type " + (vec.getType() != null ? vec.getType().getSimpleName() : "unknown") + " is not numeric");
                }
                targetCols.add(col);
            }
        }
        return targetCols;
    }

    @Override
    public IDataFrame cumsum(String... columns) {
        List<String> targetCols = resolveNumericColumns(columns);
        int total = this.size();
        for (String col : targetCols) {
            ColumnVector vec = getVector(col);
            Object[] results = new Object[total];
            if (vec.getType() == BigDecimal.class) {
                BigDecimal sum = null;
                for (int i = 0; i < total; i++) {
                    if (vec.isNull(i)) {
                        results[i] = null;
                    } else {
                        BigDecimal v = (BigDecimal) vec.getObject(i);
                        sum = (sum == null) ? v : sum.add(v);
                        results[i] = sum;
                    }
                }
            } else if (vec.getType() == Integer.class || vec.getType() == int.class) {
                Integer sum = null;
                for (int i = 0; i < total; i++) {
                    if (vec.isNull(i)) {
                        results[i] = null;
                    } else {
                        int v = vec.getInt(i);
                        sum = (sum == null) ? v : (sum + v);
                        results[i] = sum;
                    }
                }
            } else if (vec.getType() == Long.class || vec.getType() == long.class) {
                Long sum = null;
                for (int i = 0; i < total; i++) {
                    if (vec.isNull(i)) {
                        results[i] = null;
                    } else {
                        long v = vec.getLong(i);
                        sum = (sum == null) ? v : (sum + v);
                        results[i] = sum;
                    }
                }
            } else {
                Double sum = null;
                for (int i = 0; i < total; i++) {
                    if (vec.isNull(i)) {
                        results[i] = null;
                    } else {
                        double v = vec.getDouble(i);
                        sum = (sum == null) ? v : (sum + v);
                        results[i] = sum;
                    }
                }
            }
            replaceOrAddColumn(col, vec.getType(), results);
        }
        return this;
    }

    @Override
    public IDataFrame cumprod(String... columns) {
        List<String> targetCols = resolveNumericColumns(columns);
        int total = this.size();
        for (String col : targetCols) {
            ColumnVector vec = getVector(col);
            Object[] results = new Object[total];
            if (vec.getType() == BigDecimal.class) {
                BigDecimal prod = null;
                for (int i = 0; i < total; i++) {
                    if (vec.isNull(i)) {
                        results[i] = null;
                    } else {
                        BigDecimal v = (BigDecimal) vec.getObject(i);
                        prod = (prod == null) ? v : prod.multiply(v);
                        results[i] = prod;
                    }
                }
            } else if (vec.getType() == Integer.class || vec.getType() == int.class) {
                Integer prod = null;
                for (int i = 0; i < total; i++) {
                    if (vec.isNull(i)) {
                        results[i] = null;
                    } else {
                        int v = vec.getInt(i);
                        prod = (prod == null) ? v : (prod * v);
                        results[i] = prod;
                    }
                }
            } else if (vec.getType() == Long.class || vec.getType() == long.class) {
                Long prod = null;
                for (int i = 0; i < total; i++) {
                    if (vec.isNull(i)) {
                        results[i] = null;
                    } else {
                        long v = vec.getLong(i);
                        prod = (prod == null) ? v : (prod * v);
                        results[i] = prod;
                    }
                }
            } else {
                Double prod = null;
                for (int i = 0; i < total; i++) {
                    if (vec.isNull(i)) {
                        results[i] = null;
                    } else {
                        double v = vec.getDouble(i);
                        prod = (prod == null) ? v : (prod * v);
                        results[i] = prod;
                    }
                }
            }
            replaceOrAddColumn(col, vec.getType(), results);
        }
        return this;
    }

    @Override
    public IDataFrame cummin(String... columns) {
        List<String> targetCols = resolveNumericColumns(columns);
        int total = this.size();
        for (String col : targetCols) {
            ColumnVector vec = getVector(col);
            Object[] results = new Object[total];
            Object minVal = null;
            for (int i = 0; i < total; i++) {
                if (vec.isNull(i)) {
                    results[i] = null;
                } else {
                    Object v = vec.getObject(i);
                    if (minVal == null || compareNonNullValues(v, minVal, vec.getType()) < 0) {
                        minVal = v;
                    }
                    results[i] = minVal;
                }
            }
            replaceOrAddColumn(col, vec.getType(), results);
        }
        return this;
    }

    @Override
    public IDataFrame cummax(String... columns) {
        List<String> targetCols = resolveNumericColumns(columns);
        int total = this.size();
        for (String col : targetCols) {
            ColumnVector vec = getVector(col);
            Object[] results = new Object[total];
            Object maxVal = null;
            for (int i = 0; i < total; i++) {
                if (vec.isNull(i)) {
                    results[i] = null;
                } else {
                    Object v = vec.getObject(i);
                    if (maxVal == null || compareNonNullValues(v, maxVal, vec.getType()) > 0) {
                        maxVal = v;
                    }
                    results[i] = maxVal;
                }
            }
            replaceOrAddColumn(col, vec.getType(), results);
        }
        return this;
    }

    @Override
    public IDataFrame lead(String column, int offset, String outputColumn) {
        ColumnVector vec = requireVector(column);
        String targetCol = outputColumn != null ? outputColumn : column;
        int total = this.size();
        Object[] results = new Object[total];
        for (int i = 0; i < total; i++) {
            int targetIdx = i + offset;
            if (targetIdx >= 0 && targetIdx < total) {
                results[i] = vec.getObject(targetIdx);
            } else {
                results[i] = null;
            }
        }
        replaceOrAddColumn(targetCol, vec.getType(), results);
        return this;
    }

    @Override
    public IDataFrame lag(String column, int offset, String outputColumn) {
        return lead(column, -offset, outputColumn);
    }

    @Override
    public IDataFrame diff(String column, int periods, String outputColumn) {
        ColumnVector vec = requireNumericVector(column);
        String targetCol = outputColumn != null ? outputColumn : column;
        int total = this.size();
        Object[] results = new Object[total];
        boolean isDecimal = (vec.getType() == BigDecimal.class);

        for (int i = 0; i < total; i++) {
            int priorIdx = i - periods;
            if (priorIdx >= 0 && priorIdx < total) {
                if (vec.isNull(i) || vec.isNull(priorIdx)) {
                    results[i] = null;
                } else if (isDecimal) {
                    BigDecimal curr = (BigDecimal) vec.getObject(i);
                    BigDecimal prior = (BigDecimal) vec.getObject(priorIdx);
                    results[i] = curr.subtract(prior);
                } else if (vec.getType() == Integer.class || vec.getType() == int.class) {
                    results[i] = vec.getInt(i) - vec.getInt(priorIdx);
                } else if (vec.getType() == Long.class || vec.getType() == long.class) {
                    results[i] = vec.getLong(i) - vec.getLong(priorIdx);
                } else {
                    results[i] = vec.getDouble(i) - vec.getDouble(priorIdx);
                }
            } else {
                results[i] = null;
            }
        }
        Class<?> outType = isDecimal ? BigDecimal.class : vec.getType();
        replaceOrAddColumn(targetCol, outType, results);
        return this;
    }

    @Override
    public IDataFrame diff(String... columns) {
        List<String> targetCols = resolveNumericColumns(columns);
        for (String col : targetCols) {
            diff(col, 1, col);
        }
        return this;
    }

    @Override
    public IDataFrame pctChange(String column, int periods, String outputColumn) {
        ColumnVector vec = requireNumericVector(column);
        String targetCol = outputColumn != null ? outputColumn : column;
        int total = this.size();
        Object[] results = new Object[total];

        for (int i = 0; i < total; i++) {
            int priorIdx = i - periods;
            if (priorIdx >= 0 && priorIdx < total) {
                if (vec.isNull(i) || vec.isNull(priorIdx)) {
                    results[i] = null;
                } else {
                    double prior = vec.getDouble(priorIdx);
                    double curr = vec.getDouble(i);
                    if (prior == 0.0) {
                        results[i] = curr > 0 ? Double.POSITIVE_INFINITY : (curr < 0 ? Double.NEGATIVE_INFINITY : Double.NaN);
                    } else {
                        results[i] = (curr - prior) / prior;
                    }
                }
            } else {
                results[i] = null;
            }
        }
        replaceOrAddColumn(targetCol, Double.class, results);
        return this;
    }

    @Override
    public IDataFrame pctChange(String... columns) {
        List<String> targetCols = resolveNumericColumns(columns);
        for (String col : targetCols) {
            pctChange(col, 1, col);
        }
        return this;
    }

    @Override
    public IDataFrame rank(String column, String outputColumn) {
        requireVector(column);
        new com.cupoftea.table.window.Window(this).orderBy(column).rank(outputColumn != null ? outputColumn : "rank");
        return this;
    }

    @Override
    public IDataFrame denseRank(String column, String outputColumn) {
        requireVector(column);
        new com.cupoftea.table.window.Window(this).orderBy(column).denseRank(outputColumn != null ? outputColumn : "dense_rank");
        return this;
    }

    @Override
    public IDataFrame percentRank(String column, String outputColumn) {
        requireVector(column);
        new com.cupoftea.table.window.Window(this).orderBy(column).percentRank(outputColumn != null ? outputColumn : "percent_rank");
        return this;
    }

    @Override
    public IDataFrame rowNumber(String outputColumn) {
        new com.cupoftea.table.window.Window(this).rowNumber(outputColumn != null ? outputColumn : "row_number");
        return this;
    }

    // --- Deduplication ---

    @Override
    public IDataFrame dropDuplicates() {
        return dropDuplicates(Keep.FIRST, (String[]) null);
    }

    @Override
    public IDataFrame dropDuplicates(Keep keep) {
        return dropDuplicates(keep, (String[]) null);
    }

    @Override
    public IDataFrame dropDuplicates(String... subset) {
        return dropDuplicates(Keep.FIRST, subset);
    }

    @Override
    public IDataFrame dropDuplicates(Keep keep, String... subset) {
        List<String> subList = (subset != null && subset.length > 0) ? Arrays.asList(subset) : Collections.emptyList();
        return dropDuplicates(subList, keep);
    }

    @Override
    public IDataFrame dropDuplicates(List<String> subset) {
        return dropDuplicates(subset, Keep.FIRST);
    }

    @Override
    public IDataFrame dropDuplicates(List<String> subset, Keep keep) {
        if (keep == null) {
            keep = Keep.FIRST;
        }
        int total = this.size();
        if (total == 0) {
            return this;
        }

        List<ColumnVector> targetVectors = new ArrayList<>();
        if (subset == null || subset.isEmpty()) {
            targetVectors.addAll(this.columns);
        } else {
            for (String col : subset) {
                if (col == null) {
                    throw new IllegalArgumentException("Column name in subset cannot be null");
                }
                ColumnVector vec = getVector(col);
                if (vec == null) {
                    throw new IllegalArgumentException("Column '" + col + "' does not exist");
                }
                targetVectors.add(vec);
            }
        }

        BitSet retainMask = new BitSet(total);
        int retainedCount = 0;

        if (keep == Keep.FIRST) {
            Set<List<Object>> seen = new HashSet<>();
            for (int i = 0; i < total; i++) {
                List<Object> key = extractRowKey(targetVectors, i);
                if (seen.add(key)) {
                    retainMask.set(i);
                    retainedCount++;
                }
            }
        } else if (keep == Keep.LAST) {
            Set<List<Object>> seen = new HashSet<>();
            for (int i = total - 1; i >= 0; i--) {
                List<Object> key = extractRowKey(targetVectors, i);
                if (seen.add(key)) {
                    retainMask.set(i);
                    retainedCount++;
                }
            }
        } else if (keep == Keep.NONE) {
            Map<List<Object>, Integer> frequencies = new HashMap<>();
            for (int i = 0; i < total; i++) {
                List<Object> key = extractRowKey(targetVectors, i);
                frequencies.merge(key, 1, Integer::sum);
            }
            for (int i = 0; i < total; i++) {
                List<Object> key = extractRowKey(targetVectors, i);
                if (frequencies.get(key) == 1) {
                    retainMask.set(i);
                    retainedCount++;
                }
            }
        }

        for (ColumnVector vec : this.columns) {
            vec.filterInPlace(retainMask, retainedCount);
        }

        return this;
    }

    private List<Object> extractRowKey(List<ColumnVector> vectors, int row) {
        List<Object> key = new ArrayList<>(vectors.size());
        for (ColumnVector vec : vectors) {
            if (vec instanceof DictionaryColumnVector dcv) {
                key.add(dcv.getCode(row));
            } else {
                key.add(vec.isNull(row) ? null : vec.getObject(row));
            }
        }
        return key;
    }

    @Override
    public IDataFrame distinct() {
        return dropDuplicates();
    }

    @Override
    public IDataFrame distinct(Keep keep) {
        return dropDuplicates(keep);
    }

    @Override
    public IDataFrame distinct(String... subset) {
        return dropDuplicates(subset);
    }

    @Override
    public IDataFrame distinct(Keep keep, String... subset) {
        return dropDuplicates(keep, subset);
    }

    @Override
    public IDataFrame distinct(List<String> subset) {
        return dropDuplicates(subset);
    }

    @Override
    public IDataFrame distinct(List<String> subset, Keep keep) {
        return dropDuplicates(subset, keep);
    }

    // --- Reshaping: Pivot ---

    @Override
    public IDataFrame pivot(String indexCol, String columnsCol, String valuesCol) {
        return pivot(indexCol, columnsCol, valuesCol, (Aggregate) null);
    }

    @Override
    public IDataFrame pivot(String indexCol, String columnsCol, String valuesCol, Aggregation agg) {
        if (agg == null) {
            return pivot(indexCol, columnsCol, valuesCol, (Aggregate) null);
        }
        return pivot(indexCol, columnsCol, valuesCol, agg.toAggregate(valuesCol));
    }

    @Override
    public IDataFrame pivot(String indexCol, String columnsCol, String valuesCol, Aggregate agg) {
        if (indexCol == null || columnsCol == null || valuesCol == null) {
            throw new IllegalArgumentException("Index, columns, and values column names cannot be null");
        }
        ColumnVector idxVec = getVector(indexCol);
        if (idxVec == null) {
            throw new IllegalArgumentException("Column '" + indexCol + "' does not exist");
        }
        ColumnVector colVec = getVector(columnsCol);
        if (colVec == null) {
            throw new IllegalArgumentException("Column '" + columnsCol + "' does not exist");
        }
        ColumnVector valVec = getVector(valuesCol);
        if (valVec == null) {
            throw new IllegalArgumentException("Column '" + valuesCol + "' does not exist");
        }

        Aggregate aggregate = agg;
        if (aggregate == null) {
            Class<?> valType = valVec.getType();
            boolean isNumeric = valType != null && (Number.class.isAssignableFrom(valType)
                    || valType == int.class || valType == long.class || valType == double.class || valType == float.class);
            aggregate = isNumeric ? Aggregations.mean(valuesCol) : Aggregations.count(valuesCol);
        }

        DataFrame result = new DataFrame();
        Class<?> idxType = idxVec.getType() != null ? idxVec.getType() : Object.class;
        result.addColumn(indexCol, idxType);
        result.setIndex(indexCol);

        int total = this.size();
        if (total == 0) {
            return result;
        }

        LinkedHashSet<Object> uniqueIndexValues = new LinkedHashSet<>();
        LinkedHashSet<String> uniquePivotCols = new LinkedHashSet<>();
        Map<Object, Map<String, List<HashMap<String, Object>>>> partitioned = new LinkedHashMap<>();

        List<Column> srcHeaders = this.getHeaders();
        for (int i = 0; i < total; i++) {
            Object idxVal = idxVec.isNull(i) ? null : idxVec.getObject(i);
            Object cVal = colVec.isNull(i) ? null : colVec.getObject(i);
            String colName = cVal != null ? String.valueOf(cVal) : "null";

            uniqueIndexValues.add(idxVal);
            uniquePivotCols.add(colName);

            partitioned
                .computeIfAbsent(idxVal, k -> new LinkedHashMap<>())
                .computeIfAbsent(colName, k -> new ArrayList<>())
                .add(new HashMap<>(getRow(i)));
        }

        Class<?> pivotColType = aggregate.getResultType(srcHeaders);
        for (String colName : uniquePivotCols) {
            result.addColumn(colName, pivotColType);
        }

        for (Object idxVal : uniqueIndexValues) {
            HashMap<String, Object> newRow = new HashMap<>();
            newRow.put(indexCol, idxVal);
            Map<String, List<HashMap<String, Object>>> colMap = partitioned.get(idxVal);
            for (String colName : uniquePivotCols) {
                List<HashMap<String, Object>> cellRows = colMap != null ? colMap.get(colName) : null;
                Object computedVal = (cellRows != null && !cellRows.isEmpty())
                        ? aggregate.compute(cellRows, srcHeaders)
                        : null;
                newRow.put(colName, computedVal);
            }
            result.addRow(newRow);
        }

        return result;
    }

    // --- Reshaping: Melt ---

    @Override
    public IDataFrame melt() {
        return melt(null, null, "variable", "value");
    }

    @Override
    public IDataFrame melt(List<String> idVars) {
        return melt(idVars, null, "variable", "value");
    }

    @Override
    public IDataFrame melt(List<String> idVars, List<String> valueVars) {
        return melt(idVars, valueVars, "variable", "value");
    }

    @Override
    public IDataFrame melt(List<String> idVars, List<String> valueVars, String varName, String valueName) {
        List<String> resolvedIdVars = (idVars != null) ? idVars : Collections.emptyList();
        for (String col : resolvedIdVars) {
            if (col == null || getVector(col) == null) {
                throw new IllegalArgumentException("Column '" + col + "' does not exist");
            }
        }

        List<String> resolvedValueVars = new ArrayList<>();
        if (valueVars == null || valueVars.isEmpty()) {
            for (Column col : this.getHeaders()) {
                if (!resolvedIdVars.contains(col.columnName())) {
                    resolvedValueVars.add(col.columnName());
                }
            }
        } else {
            for (String col : valueVars) {
                if (col == null || getVector(col) == null) {
                    throw new IllegalArgumentException("Column '" + col + "' does not exist");
                }
                resolvedValueVars.add(col);
            }
        }

        String actualVarName = (varName != null && !varName.trim().isEmpty()) ? varName : "variable";
        String actualValueName = (valueName != null && !valueName.trim().isEmpty()) ? valueName : "value";

        int n = this.size();
        int k = resolvedValueVars.size();
        int targetCapacity = Math.max(16, n * k);

        // Determine value type
        Class<?> commonValueType = null;
        if (!resolvedValueVars.isEmpty()) {
            Class<?> firstType = getVector(resolvedValueVars.get(0)).getType();
            boolean allSame = true;
            for (int i = 1; i < resolvedValueVars.size(); i++) {
                if (!Objects.equals(firstType, getVector(resolvedValueVars.get(i)).getType())) {
                    allSame = false;
                    break;
                }
            }
            commonValueType = allSame ? firstType : Object.class;
        } else {
            commonValueType = Object.class;
        }

        // Columnar vector recreation
        List<ColumnVector> createdIdVectors = new ArrayList<>();
        for (String idCol : resolvedIdVars) {
            ColumnVector src = getVector(idCol);
            ColumnVector dest = ColumnVector.create(idCol, src.getType(), targetCapacity, true);
            for (int v = 0; v < k; v++) {
                for (int r = 0; r < n; r++) {
                    if (src.isNull(r)) {
                        dest.appendNull();
                    } else {
                        dest.appendObject(src.getObject(r));
                    }
                }
            }
            createdIdVectors.add(dest);
        }

        StringColumnVector createdVarVector = new StringColumnVector(actualVarName, targetCapacity, false);
        for (String valCol : resolvedValueVars) {
            for (int r = 0; r < n; r++) {
                createdVarVector.appendString(valCol);
            }
        }

        ColumnVector createdValVector = ColumnVector.create(actualValueName, commonValueType, targetCapacity, true);
        for (String valCol : resolvedValueVars) {
            ColumnVector src = getVector(valCol);
            for (int r = 0; r < n; r++) {
                if (src.isNull(r)) {
                    createdValVector.appendNull();
                } else {
                    createdValVector.appendObject(src.getObject(r));
                }
            }
        }

        DataFrame melted = new DataFrame();
        for (ColumnVector vec : createdIdVectors) {
            melted.header.addColumn(new Column(vec.getName(), vec.getType()));
            melted.columns.add(vec);
        }
        melted.header.addColumn(new Column(createdVarVector.getName(), createdVarVector.getType()));
        melted.columns.add(createdVarVector);
        melted.header.addColumn(new Column(createdValVector.getName(), createdValVector.getType()));
        melted.columns.add(createdValVector);

        return melted;
    }

    // --- Random Sampling ---

    @Override
    public IDataFrame sample(int n) {
        return sample(n, false, null);
    }

    @Override
    public IDataFrame sample(int n, boolean replace) {
        return sample(n, replace, null);
    }

    @Override
    public IDataFrame sample(int n, long seed) {
        return sample(n, false, seed);
    }

    @Override
    public IDataFrame sample(int n, boolean replace, long seed) {
        return sample(n, replace, Long.valueOf(seed));
    }

    @Override
    public IDataFrame sample(double fraction) {
        return sample(fraction, false, null);
    }

    @Override
    public IDataFrame sample(double fraction, long seed) {
        return sample(fraction, false, Long.valueOf(seed));
    }

    @Override
    public IDataFrame sample(double fraction, boolean replace, long seed) {
        return sample(fraction, replace, Long.valueOf(seed));
    }

    private IDataFrame sample(double fraction, boolean replace, Long seed) {
        if (fraction < 0.0) {
            throw new IllegalArgumentException("Sample fraction cannot be negative: " + fraction);
        }
        if (!replace && fraction > 1.0) {
            throw new IllegalArgumentException("Sample fraction cannot be greater than 1.0 when replace=false (got: " + fraction + ")");
        }
        int total = this.size();
        int n = (int) Math.round(fraction * total);
        return sample(n, replace, seed);
    }

    private IDataFrame sample(int n, boolean replace, Long seed) {
        if (n < 0) {
            throw new IllegalArgumentException("Sample count n cannot be negative: " + n);
        }
        int total = this.size();
        if (total == 0) {
            if (n == 0) {
                return copy().slice(0, 0);
            }
            throw new IllegalArgumentException("Cannot sample " + n + " rows from an empty DataFrame");
        }
        if (!replace && n > total) {
            throw new IllegalArgumentException("Cannot take a larger sample than population when replace=false (sample size: " + n + ", population: " + total + ")");
        }

        Random rng = (seed != null) ? new Random(seed) : new Random();
        int[] sampledIndices = new int[n];

        if (replace) {
            for (int i = 0; i < n; i++) {
                sampledIndices[i] = rng.nextInt(total);
            }
        } else {
            if (n == total) {
                for (int i = 0; i < total; i++) sampledIndices[i] = i;
                for (int i = 0; i < total; i++) {
                    int r = i + rng.nextInt(total - i);
                    int tmp = sampledIndices[i];
                    sampledIndices[i] = sampledIndices[r];
                    sampledIndices[r] = tmp;
                }
            } else if (n <= total / 2) {
                Set<Integer> chosen = new HashSet<>(n);
                int count = 0;
                while (count < n) {
                    int candidate = rng.nextInt(total);
                    if (chosen.add(candidate)) {
                        sampledIndices[count++] = candidate;
                    }
                }
            } else {
                int[] pool = new int[total];
                for (int i = 0; i < total; i++) pool[i] = i;
                for (int i = 0; i < n; i++) {
                    int r = i + rng.nextInt(total - i);
                    int tmp = pool[i];
                    pool[i] = pool[r];
                    pool[r] = tmp;
                    sampledIndices[i] = pool[i];
                }
            }
        }

        DataFrame sampled = new DataFrame();
        sampled.header = this.header.copy();
        sampled.columnCreators.putAll(this.columnCreators);
        sampled.pendingTypeInferenceColumns.addAll(this.pendingTypeInferenceColumns);
        sampled.index = this.index.copy();
        sampled.indexColumn = this.indexColumn;

        for (ColumnVector vec : this.columns) {
            ColumnVector newVec = vec.createEmptyWithSameEncoding(Math.max(16, n));
            if (vec instanceof DictionaryColumnVector srcDict && newVec instanceof DictionaryColumnVector dstDict) {
                dstDict.setSizeUnchecked(n);
                dstDict.copyIndexedValuesOnly(srcDict, sampledIndices, n, 0);
                if (srcDict.hasNulls()) {
                    dstDict.applyNullsFromIndexed(srcDict, sampledIndices, n, 0);
                }
            } else {
                for (int idx : sampledIndices) {
                    if (vec.isNull(idx)) {
                        newVec.appendNull();
                    } else {
                        newVec.appendObject(vec.getObject(idx));
                    }
                }
            }
            sampled.columns.add(newVec);
        }

        return sampled;
    }
}
