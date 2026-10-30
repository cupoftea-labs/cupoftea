package com.cupoftea;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.LongUnaryOperator;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.exceptions.SchemaMismatchException;
import com.cupoftea.io.DataFrameReader;
import com.cupoftea.io.DataFrameWriter;
import com.cupoftea.plot.Plot;
import com.cupoftea.table.Aggregate;
import com.cupoftea.table.Aggregation;
import com.cupoftea.table.Column;
import com.cupoftea.table.ColumnRef;
import com.cupoftea.table.ConcatAxis;
import com.cupoftea.table.DataFrame;
import com.cupoftea.table.DataFrameConcatenator;
import com.cupoftea.table.GroupBy;
import com.cupoftea.table.ILoc;
import com.cupoftea.table.JoinType;
import com.cupoftea.table.Keep;
import com.cupoftea.table.Loc;
import com.cupoftea.table.Row;
import com.cupoftea.table.Schema;
import com.cupoftea.table.interfaces.IDataFrame;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.window.ExpandingWindow;
import com.cupoftea.table.window.RollingWindow;
import com.cupoftea.table.window.Window;
import com.cupoftea.utils.Printer;
import com.cupoftea.utils.configurations.PrinterConfiguration;
import com.cupoftea.utils.configurations.TableHtmlConfiguration;
import com.cupoftea.utils.interfaces.IColumnCreator;
import com.cupoftea.utils.interfaces.IFilter;
import com.cupoftea.utils.interfaces.Orderer;

public class CupOfTea {

    private IDataFrame dataFrame;
    private MutationMode mutationMode = MutationMode.IN_PLACE;

    public CupOfTea() {
        this(MutationMode.IN_PLACE);
    }

    public CupOfTea(MutationMode mutationMode) {
        this.mutationMode = mutationMode != null ? mutationMode : MutationMode.IN_PLACE;
        this.dataFrame = new DataFrame();
    }

    public CupOfTea(Schema schema) {
        this(schema, MutationMode.IN_PLACE);
    }

    public CupOfTea(Schema schema, MutationMode mutationMode) {
        this.dataFrame = new DataFrame(schema);
        this.mutationMode = mutationMode != null ? mutationMode : MutationMode.IN_PLACE;
    }

    public static CupOfTea withSchema(Schema schema) {
        return new CupOfTea(schema);
    }

    public static CupOfTea fromVectors(List<ColumnVector> vectors) {
        return new CupOfTea(DataFrame.fromVectors(vectors));
    }

    public CupOfTea(IDataFrame dataFrame) {
        this(dataFrame, MutationMode.IN_PLACE);
    }

    public CupOfTea(IDataFrame dataFrame, MutationMode mutationMode) {
        this.dataFrame = dataFrame != null ? dataFrame : new DataFrame();
        this.mutationMode = mutationMode != null ? mutationMode : MutationMode.IN_PLACE;
    }

    public MutationMode getMutationMode() {
        return this.mutationMode;
    }

    public CupOfTea setMutationMode(MutationMode mutationMode) {
        this.mutationMode = mutationMode != null ? mutationMode : MutationMode.IN_PLACE;
        return this;
    }

    public CupOfTea withMutationMode(MutationMode mutationMode) {
        return new CupOfTea(this.dataFrame.copy(), mutationMode != null ? mutationMode : MutationMode.IN_PLACE);
    }

    public CupOfTea asInPlace() {
        this.mutationMode = MutationMode.IN_PLACE;
        return this;
    }

    public CupOfTea asCopyOnWrite() {
        this.mutationMode = MutationMode.COPY_ON_WRITE;
        return this;
    }

    // --- Parallel Execution Mode ---

    /**
     * Enables multithreaded parallel evaluation mode (defaulting to {@code ForkJoinPool.commonPool()}).
     */
    public CupOfTea parallel() {
        CupOfTea target = targetForMutation();
        target.dataFrame.parallel();
        return target;
    }

    /**
     * Enables multithreaded parallel evaluation mode with a custom {@link ExecutorService}.
     */
    public CupOfTea parallel(@NotNull ExecutorService executor) {
        CupOfTea target = targetForMutation();
        target.dataFrame.parallel(executor);
        return target;
    }

    /**
     * Returns a copy of this DataFrame with multithreaded parallel evaluation mode enabled.
     */
    public CupOfTea asParallel() {
        CupOfTea copy = new CupOfTea(this.dataFrame.copy(), this.mutationMode);
        copy.dataFrame.parallel();
        return copy;
    }

    /**
     * Returns a copy of this DataFrame with multithreaded parallel evaluation mode enabled using the given executor.
     */
    public CupOfTea asParallel(@NotNull ExecutorService executor) {
        CupOfTea copy = new CupOfTea(this.dataFrame.copy(), this.mutationMode);
        copy.dataFrame.parallel(executor);
        return copy;
    }

    /**
     * Switches evaluation mode back to single-threaded sequential execution.
     */
    public CupOfTea sequential() {
        CupOfTea target = targetForMutation();
        target.dataFrame.sequential();
        return target;
    }

    /**
     * Returns a copy of this DataFrame with single-threaded sequential evaluation mode enabled.
     */
    public CupOfTea asSequential() {
        CupOfTea copy = new CupOfTea(this.dataFrame.copy(), this.mutationMode);
        copy.dataFrame.sequential();
        return copy;
    }

    /**
     * Returns {@code true} if multithreaded parallel execution mode is enabled.
     */
    public boolean isParallel() {
        return this.dataFrame.isParallel();
    }

    /**
     * Returns the active {@link ExecutorService} for parallel execution.
     */
    public ExecutorService getExecutor() {
        return this.dataFrame.getExecutor();
    }

    /**
     * Returns the minimum row count threshold below which execution stays sequential.
     */
    public int getParallelThreshold() {
        return this.dataFrame.getParallelThreshold();
    }

    /**
     * Sets the minimum row count threshold below which execution stays sequential.
     */
    public CupOfTea setParallelThreshold(int threshold) {
        CupOfTea target = targetForMutation();
        target.dataFrame.setParallelThreshold(threshold);
        return target;
    }

    /**
     * Fluent alias for {@link #setParallelThreshold(int)}.
     */
    public CupOfTea withParallelThreshold(int threshold) {
        CupOfTea target = targetForMutation();
        target.dataFrame.withParallelThreshold(threshold);
        return target;
    }

    public CupOfTea copy() {
        return new CupOfTea(this.dataFrame.copy(), this.mutationMode);
    }

    private CupOfTea targetForMutation() {
        if (this.mutationMode == MutationMode.COPY_ON_WRITE) {
            return new CupOfTea(this.dataFrame.copy(), this.mutationMode);
        }
        return this;
    }

    public Schema getSchema() {
        return this.dataFrame.getSchema();
    }

    public CupOfTea setSchema(Schema schema) {
        CupOfTea target = targetForMutation();
        target.dataFrame.setSchema(schema);
        return target;
    }

    /**
     * Entry point for reading tabular data formats.
     * Example: {@code CupOfTea df = CupOfTea.read().csv("data.csv");}
     */
    public static DataFrameReader read() {
        return new DataFrameReader();
    }

    /**
     * Entry point for exporting tabular data formats.
     * Example: {@code df.write().csv("data.csv");}
     */
    public DataFrameWriter write() {
        return new DataFrameWriter(this.dataFrame);
    }

    public IDataFrame getDataFrame() {
        return this.dataFrame;
    }

    public void setDataFrame(IDataFrame dataFrame) {
        this.dataFrame = dataFrame != null ? dataFrame : new DataFrame();
    }

    public CupOfTea clear() {
        this.dataFrame.clear();
        return this;
    }

    public ArrayList<Column> getHeaders() {
        return this.dataFrame.getHeaders();
    }

    public List<String> getColumnNames() {
        List<Column> headers = this.dataFrame.getHeaders();
        List<String> names = new ArrayList<>(headers.size());
        for (Column col : headers) {
            names.add(col.columnName());
        }
        return Collections.unmodifiableList(names);
    }

    public List<ColumnVector> getColumnVectors() {
        return this.dataFrame.getColumnVectors();
    }

    public ColumnVector getColumnVector(String columnName) {
        return this.dataFrame.getColumnVector(columnName);
    }

    public ColumnVector getColumnVector(int index) {
        return this.dataFrame.getColumnVector(index);
    }

    public Column getColumnHeader(String columnName) {
        return this.dataFrame.getColumnHeader(columnName);
    }

    public ColumnRef col(String columnName) {
        return this.dataFrame.col(columnName);
    }

    public Integer size() {
        return this.dataFrame.size();
    }

    public Row getRow(int index) {
        return this.dataFrame.getRow(index);
    }

    public List<Row> getRows() {
        return this.dataFrame.getRows();
    }

    public CupOfTea moveColumn(String columnName, int finalIndex) {
        CupOfTea target = targetForMutation();
        target.dataFrame.moveColumn(columnName, finalIndex);
        return target;
    }

    public CupOfTea moveColumn(int initialIndex, int finalIndex) {
        CupOfTea target = targetForMutation();
        target.dataFrame.moveColumn(initialIndex, finalIndex);
        return target;
    }

    public CupOfTea renameColumn(String oldName, String newName) {
        CupOfTea target = targetForMutation();
        target.dataFrame.renameColumn(oldName, newName);
        return target;
    }

    public CupOfTea renameColumn(int index, String newName) {
        CupOfTea target = targetForMutation();
        target.dataFrame.renameColumn(index, newName);
        return target;
    }

    public CupOfTea renameColumn(HashMap<String, String> names) {
        CupOfTea target = targetForMutation();
        target.dataFrame.renameColumn(names);
        return target;
    }

    public CupOfTea addColumn(String columnName) {
        CupOfTea target = targetForMutation();
        target.dataFrame.addColumn(columnName);
        return target;
    }

    public CupOfTea addColumn(String columnName, Class<?> columnType) {
        CupOfTea target = targetForMutation();
        target.dataFrame.addColumn(columnName, columnType);
        return target;
    }

    public CupOfTea addColumn(int index, String columnName) {
        CupOfTea target = targetForMutation();
        target.dataFrame.addColumn(index, columnName);
        return target;
    }

    public CupOfTea addColumn(int index, String columnName, Class<?> columnType) {
        CupOfTea target = targetForMutation();
        target.dataFrame.addColumn(index, columnName, columnType);
        return target;
    }

    public CupOfTea addColumn(int index, String columnName, @Nullable Object data) {
        CupOfTea target = targetForMutation();
        target.dataFrame.addColumn(index, columnName, data);
        return target;
    }

    public CupOfTea addColumn(int index, String columnName, @Nullable ArrayList<Object> data) {
        CupOfTea target = targetForMutation();
        target.dataFrame.addColumn(index, columnName, data);
        return target;
    }

    public CupOfTea addColumn(Column column, IColumnCreator columnCreator) {
        if (column == null) {
            throw new SchemaMismatchException("Column cannot be null");
        }
        CupOfTea target = targetForMutation();
        target.dataFrame.addColumn(column.columnName(), column.columnType(), columnCreator);
        return target;
    }

    public CupOfTea addColumn(String columnName, IColumnCreator columnCreator) {
        CupOfTea target = targetForMutation();
        target.dataFrame.addColumn(columnName, columnCreator);
        return target;
    }

    public CupOfTea addColumn(String columnName, Class<?> columnType, IColumnCreator columnCreator) {
        CupOfTea target = targetForMutation();
        target.dataFrame.addColumn(columnName, columnType, columnCreator);
        return target;
    }

    public CupOfTea addColumn(String columnName, IColumnCreator columnCreator, Class<?> columnType) {
        CupOfTea target = targetForMutation();
        target.dataFrame.addColumn(columnName, columnCreator, columnType);
        return target;
    }

    public CupOfTea addColumn(int index, String columnName, IColumnCreator columnCreator) {
        CupOfTea target = targetForMutation();
        target.dataFrame.addColumn(index, columnName, columnCreator);
        return target;
    }

    public CupOfTea addColumn(int index, String columnName, Class<?> columnType, IColumnCreator columnCreator) {
        CupOfTea target = targetForMutation();
        target.dataFrame.addColumn(index, columnName, columnType, columnCreator);
        return target;
    }

    public CupOfTea addColumn(int index, String columnName, IColumnCreator columnCreator, Class<?> columnType) {
        CupOfTea target = targetForMutation();
        target.dataFrame.addColumn(index, columnName, columnCreator, columnType);
        return target;
    }

    public CupOfTea withColumn(String columnName, ColumnVector vector) {
        CupOfTea target = targetForMutation();
        target.dataFrame.withColumn(columnName, vector);
        return target;
    }

    public CupOfTea withColumn(String columnName, ColumnRef columnRef) {
        CupOfTea target = targetForMutation();
        target.dataFrame.withColumn(columnName, columnRef);
        return target;
    }

    public CupOfTea withColumn(String columnName, IColumnCreator columnCreator) {
        CupOfTea target = targetForMutation();
        target.dataFrame.withColumn(columnName, columnCreator);
        return target;
    }

    public CupOfTea withColumn(String columnName, Class<?> columnType, IColumnCreator columnCreator) {
        CupOfTea target = targetForMutation();
        target.dataFrame.withColumn(columnName, columnType, columnCreator);
        return target;
    }

    public CupOfTea transformColumn(String columnName, ColumnVector vector) {
        CupOfTea target = targetForMutation();
        target.dataFrame.transformColumn(columnName, vector);
        return target;
    }

    public CupOfTea transformColumn(String columnName, ColumnRef columnRef) {
        CupOfTea target = targetForMutation();
        target.dataFrame.transformColumn(columnName, columnRef);
        return target;
    }

    public CupOfTea transformColumn(String columnName, Function<ColumnVector, ColumnVector> transformer) {
        CupOfTea target = targetForMutation();
        target.dataFrame.transformColumn(columnName, transformer);
        return target;
    }

    public CupOfTea alterColumnType(String columnName, Class<?> newColumnType) {
        CupOfTea target = targetForMutation();
        target.dataFrame.alterColumnType(columnName, newColumnType);
        return target;
    }

    public CupOfTea removeColumn(String columnName) {
        CupOfTea target = targetForMutation();
        target.dataFrame.removeColumn(columnName);
        return target;
    }

    public CupOfTea removeColumn(int index) {
        CupOfTea target = targetForMutation();
        target.dataFrame.removeColumn(index);
        return target;
    }

    public CupOfTea addRow(@NotNull Map<String, Object> row) {
        CupOfTea target = targetForMutation();
        target.dataFrame.addRow(row);
        return target;
    }

    public CupOfTea removeRow(int index) {
        CupOfTea target = targetForMutation();
        target.dataFrame.removeRow(index);
        return target;
    }

    public CupOfTea removeRows(IFilter filter) {
        CupOfTea target = targetForMutation();
        target.dataFrame.removeRows(filter);
        return target;
    }

    public CupOfTea filter(IFilter filter) {
        return new CupOfTea(this.dataFrame.filter(filter), this.mutationMode);
    }

    // --- Dictionary / Categorical Encoding & Code-Accelerated Filtering ---

    public CupOfTea encodeDictionary(String... columnNames) {
        CupOfTea target = targetForMutation();
        target.dataFrame.encodeDictionary(columnNames);
        return target;
    }

    public CupOfTea encodeDictionary(boolean useShortCodes, String... columnNames) {
        CupOfTea target = targetForMutation();
        target.dataFrame.encodeDictionary(useShortCodes, columnNames);
        return target;
    }

    public CupOfTea encodeDictionary() {
        return encodeDictionary(false);
    }

    public CupOfTea encodeDictionary(boolean useShortCodes) {
        return encodeDictionary(useShortCodes, (String[]) null);
    }

    public CupOfTea decodeDictionary(String... columnNames) {
        CupOfTea target = targetForMutation();
        target.dataFrame.decodeDictionary(columnNames);
        return target;
    }

    public CupOfTea decodeDictionary() {
        return decodeDictionary((String[]) null);
    }

    public CupOfTea categorize(String... columnNames) {
        return encodeDictionary(columnNames);
    }

    public CupOfTea toCategorical(String... columnNames) {
        return encodeDictionary(columnNames);
    }

    public boolean isDictionaryEncoded(String columnName) {
        return this.dataFrame.isDictionaryEncoded(columnName);
    }

    public boolean isCategorical(String columnName) {
        return this.dataFrame.isCategorical(columnName);
    }

    public CupOfTea filterEquals(String columnName, Object value) {
        return new CupOfTea(this.dataFrame.filterEquals(columnName, value), this.mutationMode);
    }

    public CupOfTea filterNotEquals(String columnName, Object value) {
        return new CupOfTea(this.dataFrame.filterNotEquals(columnName, value), this.mutationMode);
    }

    public CupOfTea filterIn(String columnName, Collection<?> values) {
        return new CupOfTea(this.dataFrame.filterIn(columnName, values), this.mutationMode);
    }

    public CupOfTea filterIn(String columnName, Object... values) {
        return new CupOfTea(this.dataFrame.filterIn(columnName, values), this.mutationMode);
    }

    // --- Vectorized Map / Transformations ---

    public CupOfTea map(String columnName, IColumnCreator columnCreator) {
        CupOfTea target = targetForMutation();
        target.dataFrame.map(columnName, columnCreator);
        return target;
    }

    public CupOfTea map(String columnName, @Nullable Class<?> columnType, IColumnCreator columnCreator) {
        CupOfTea target = targetForMutation();
        target.dataFrame.map(columnName, columnType, columnCreator);
        return target;
    }

    public CupOfTea mapColumn(String columnName, Function<Object, ?> valueMapper) {
        CupOfTea target = targetForMutation();
        target.dataFrame.mapColumn(columnName, valueMapper);
        return target;
    }

    @SuppressWarnings("unchecked")
    public <T> CupOfTea mapColumn(String columnName, @Nullable Class<T> targetType, Function<T, ?> valueMapper) {
        CupOfTea target = targetForMutation();
        target.dataFrame.mapColumn(columnName, targetType, (Function<Object, ?>) valueMapper);
        return target;
    }

    public CupOfTea mapInt(String columnName, IntUnaryOperator mapper) {
        CupOfTea target = targetForMutation();
        target.dataFrame.mapInt(columnName, mapper);
        return target;
    }

    public CupOfTea mapLong(String columnName, LongUnaryOperator mapper) {
        CupOfTea target = targetForMutation();
        target.dataFrame.mapLong(columnName, mapper);
        return target;
    }

    public CupOfTea mapDouble(String columnName, DoubleUnaryOperator mapper) {
        CupOfTea target = targetForMutation();
        target.dataFrame.mapDouble(columnName, mapper);
        return target;
    }

    public CupOfTea map(Function<Row, ? extends Map<String, Object>> rowMapper) {
        return new CupOfTea(this.dataFrame.map(rowMapper), this.mutationMode);
    }

    // --- Vectorized Aggregations ---

    public CupOfTea agg(Aggregate... aggregates) {
        return new CupOfTea(this.dataFrame.agg(aggregates), this.mutationMode);
    }

    public Object sumOf(String column) {
        return this.dataFrame.sumOf(column);
    }

    public Double meanOf(String column) {
        return this.dataFrame.meanOf(column);
    }

    public Double averageOf(String column) {
        return this.dataFrame.averageOf(column);
    }

    public Object minOf(String column) {
        return this.dataFrame.minOf(column);
    }

    public Object maxOf(String column) {
        return this.dataFrame.maxOf(column);
    }

    public int countOf(String column) {
        return this.dataFrame.countOf(column);
    }

    public CupOfTea update(Map<String, Object> updateColumns) {
        CupOfTea target = targetForMutation();
        target.dataFrame.update(updateColumns);
        return target;
    }

    public CupOfTea orderBy(Orderer... orderer) {
        CupOfTea target = targetForMutation();
        target.dataFrame.orderBy(orderer);
        return target;
    }

    // --- Core pandas-like ops ---

    public CupOfTea select(String... columnNames) {
        CupOfTea target = targetForMutation();
        target.dataFrame.select(columnNames);
        return target;
    }

    public CupOfTea select(List<String> columnNames) {
        CupOfTea target = targetForMutation();
        target.dataFrame.select(columnNames);
        return target;
    }

    public CupOfTea drop(String... columnNames) {
        CupOfTea target = targetForMutation();
        target.dataFrame.drop(columnNames);
        return target;
    }

    public CupOfTea drop(List<String> columnNames) {
        CupOfTea target = targetForMutation();
        target.dataFrame.drop(columnNames);
        return target;
    }

    public CupOfTea head() {
        return head(5);
    }

    public CupOfTea head(int n) {
        return new CupOfTea(this.dataFrame.head(n), this.mutationMode);
    }

    public CupOfTea tail() {
        return tail(5);
    }

    public CupOfTea tail(int n) {
        return new CupOfTea(this.dataFrame.tail(n), this.mutationMode);
    }

    public CupOfTea slice(int fromRow, int toRow) {
        return new CupOfTea(this.dataFrame.slice(fromRow, toRow), this.mutationMode);
    }

    public Row iloc(int index) {
        return this.dataFrame.iloc(index);
    }

    public Object iloc(int rowIndex, int colIndex) {
        return this.dataFrame.iloc(rowIndex, colIndex);
    }

    public Object iloc(int rowIndex, String columnName) {
        return this.dataFrame.iloc(rowIndex, columnName);
    }

    public ILoc iloc() {
        return new ILoc(this, this.dataFrame);
    }

    public String getIndex() {
        return this.dataFrame.getIndex();
    }

    public CupOfTea setIndex(String columnName) {
        CupOfTea target = targetForMutation();
        target.dataFrame.setIndex(columnName);
        return target;
    }

    public CupOfTea resetIndex() {
        CupOfTea target = targetForMutation();
        target.dataFrame.resetIndex();
        return target;
    }

    public Row loc(Object key) {
        return this.dataFrame.loc(key);
    }

    public Object loc(Object key, String column) {
        return this.dataFrame.loc(key, column);
    }

    public Loc loc() {
        return new Loc(this, this.dataFrame);
    }

    public CupOfTea fillna(Object value) {
        CupOfTea target = targetForMutation();
        target.dataFrame.fillna(value);
        return target;
    }

    public CupOfTea fillna(String column, Object value) {
        CupOfTea target = targetForMutation();
        target.dataFrame.fillna(column, value);
        return target;
    }

    public CupOfTea fillna(Map<String, Object> values) {
        CupOfTea target = targetForMutation();
        target.dataFrame.fillna(values);
        return target;
    }

    public CupOfTea dropna() {
        CupOfTea target = targetForMutation();
        target.dataFrame.dropna();
        return target;
    }

    public CupOfTea dropna(String how) {
        CupOfTea target = targetForMutation();
        target.dataFrame.dropna(how);
        return target;
    }

    public CupOfTea dropna(String... columns) {
        CupOfTea target = targetForMutation();
        target.dataFrame.dropna(columns);
        return target;
    }

    public CupOfTea dropna(List<String> columns) {
        CupOfTea target = targetForMutation();
        target.dataFrame.dropna(columns);
        return target;
    }

    public CupOfTea dropna(String how, String... columns) {
        CupOfTea target = targetForMutation();
        target.dataFrame.dropna(how, columns);
        return target;
    }

    public CupOfTea dropna(String how, List<String> columns) {
        CupOfTea target = targetForMutation();
        target.dataFrame.dropna(how, columns);
        return target;
    }

    // --- Deduplication ---

    public CupOfTea dropDuplicates() {
        CupOfTea target = targetForMutation();
        target.dataFrame.dropDuplicates();
        return target;
    }

    public CupOfTea dropDuplicates(Keep keep) {
        CupOfTea target = targetForMutation();
        target.dataFrame.dropDuplicates(keep);
        return target;
    }

    public CupOfTea dropDuplicates(String... subset) {
        CupOfTea target = targetForMutation();
        target.dataFrame.dropDuplicates(subset);
        return target;
    }

    public CupOfTea dropDuplicates(Keep keep, String... subset) {
        CupOfTea target = targetForMutation();
        target.dataFrame.dropDuplicates(keep, subset);
        return target;
    }

    public CupOfTea dropDuplicates(List<String> subset) {
        CupOfTea target = targetForMutation();
        target.dataFrame.dropDuplicates(subset);
        return target;
    }

    public CupOfTea dropDuplicates(List<String> subset, Keep keep) {
        CupOfTea target = targetForMutation();
        target.dataFrame.dropDuplicates(subset, keep);
        return target;
    }

    public CupOfTea distinct() {
        return dropDuplicates();
    }

    public CupOfTea distinct(Keep keep) {
        return dropDuplicates(keep);
    }

    public CupOfTea distinct(String... subset) {
        return dropDuplicates(subset);
    }

    public CupOfTea distinct(Keep keep, String... subset) {
        return dropDuplicates(keep, subset);
    }

    public CupOfTea distinct(List<String> subset) {
        return dropDuplicates(subset);
    }

    public CupOfTea distinct(List<String> subset, Keep keep) {
        return dropDuplicates(subset, keep);
    }

    // --- Reshaping: Pivot ---

    public CupOfTea pivot(String indexCol, String columnsCol, String valuesCol) {
        return pivot(indexCol, columnsCol, valuesCol, (Aggregate) null);
    }

    public CupOfTea pivot(String indexCol, String columnsCol, String valuesCol, Aggregation agg) {
        IDataFrame result = this.dataFrame.pivot(indexCol, columnsCol, valuesCol, agg);
        if (this.mutationMode == MutationMode.IN_PLACE) {
            this.dataFrame = result;
            return this;
        }
        return new CupOfTea(result, this.mutationMode);
    }

    public CupOfTea pivot(String indexCol, String columnsCol, String valuesCol, Aggregate agg) {
        IDataFrame result = this.dataFrame.pivot(indexCol, columnsCol, valuesCol, agg);
        if (this.mutationMode == MutationMode.IN_PLACE) {
            this.dataFrame = result;
            return this;
        }
        return new CupOfTea(result, this.mutationMode);
    }

    // --- Reshaping: Melt ---

    public CupOfTea melt() {
        return melt(null, null, "variable", "value");
    }

    public CupOfTea melt(List<String> idVars) {
        return melt(idVars, null, "variable", "value");
    }

    public CupOfTea melt(List<String> idVars, List<String> valueVars) {
        return melt(idVars, valueVars, "variable", "value");
    }

    public CupOfTea melt(List<String> idVars, List<String> valueVars, String varName, String valueName) {
        IDataFrame result = this.dataFrame.melt(idVars, valueVars, varName, valueName);
        if (this.mutationMode == MutationMode.IN_PLACE) {
            this.dataFrame = result;
            return this;
        }
        return new CupOfTea(result, this.mutationMode);
    }

    // --- Random Sampling ---

    public CupOfTea sample(int n) {
        return new CupOfTea(this.dataFrame.sample(n), this.mutationMode);
    }

    public CupOfTea sample(int n, boolean replace) {
        return new CupOfTea(this.dataFrame.sample(n, replace), this.mutationMode);
    }

    public CupOfTea sample(int n, long seed) {
        return new CupOfTea(this.dataFrame.sample(n, seed), this.mutationMode);
    }

    public CupOfTea sample(int n, boolean replace, long seed) {
        return new CupOfTea(this.dataFrame.sample(n, replace, seed), this.mutationMode);
    }

    public CupOfTea sample(double fraction) {
        return new CupOfTea(this.dataFrame.sample(fraction), this.mutationMode);
    }

    public CupOfTea sample(double fraction, long seed) {
        return new CupOfTea(this.dataFrame.sample(fraction, seed), this.mutationMode);
    }

    public CupOfTea sample(double fraction, boolean replace, long seed) {
        return new CupOfTea(this.dataFrame.sample(fraction, replace, seed), this.mutationMode);
    }

    public CupOfTea print() {
        Printer printer = new Printer(this);
        printer.print();
        return this;
    }

    public CupOfTea print(PrinterConfiguration configuration) {
        Printer printer = new Printer(this, configuration);
        printer.print();
        return this;
    }

    public CupOfTea print(int maxRows) {
        PrinterConfiguration config = new PrinterConfiguration();
        config.setMaxRows(maxRows);
        return print(config);
    }

    public String printToString() {
        return printToString(new PrinterConfiguration());
    }

    public String printToString(PrinterConfiguration configuration) {
        Printer printer = new Printer(this, configuration);
        return printer.format(configuration);
    }

    // --- CSV Importers / Exporters ---

    public CupOfTea importFromCsv(File file) {
        return importFromCsv(file, true);
    }

    public CupOfTea importFromCsv(File file, boolean hasHeaders) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).csv(file, hasHeaders);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromCsv(File file, Schema schema) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).schema(schema).csv(file);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromCsv(File file, boolean hasHeaders, Schema schema) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).schema(schema).csv(file, hasHeaders);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromCsv(File file, Object configuration) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).csv(file, configuration);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea exportToCsv(File file) {
        if (file == null) {
            throw new ExportException("File cannot be null");
        }
        write().csv(file);
        return this;
    }

    public CupOfTea exportToCsv(File file, Object configuration) {
        if (file == null) {
            throw new ExportException("File cannot be null");
        }
        write().csv(file, configuration);
        return this;
    }

    // --- Excel Importers ---

    public CupOfTea importFromExcel(File file) {
        return importFromExcel(file, true);
    }

    public CupOfTea importFromExcel(File file, boolean hasHeaders) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).excel(file, hasHeaders);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromExcel(File file, Schema schema) {
        return importFromExcel(file, true, schema);
    }

    public CupOfTea importFromExcel(File file, boolean hasHeaders, Schema schema) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).schema(schema).excel(file, hasHeaders);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromExcel(File file, Object configuration) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).excel(file, configuration);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    // --- Parquet Importers / Exporters ---

    public CupOfTea importFromParquet(File file) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).parquet(file);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea exportToParquet(File file) {
        if (file == null) {
            throw new ExportException("File cannot be null");
        }
        write().parquet(file);
        return this;
    }

    // --- Arrow / Feather Importers / Exporters ---

    public CupOfTea importFromArrow(File file) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).arrow(file);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea exportToArrow(File file) {
        if (file == null) {
            throw new ExportException("File cannot be null");
        }
        write().arrow(file);
        return this;
    }

    public CupOfTea importFromFeather(File file) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).feather(file);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea exportToFeather(File file) {
        if (file == null) {
            throw new ExportException("File cannot be null");
        }
        write().feather(file);
        return this;
    }

    // --- SQL / JDBC Importers / Exporters ---

    public CupOfTea importFromSql(java.sql.ResultSet resultSet) {
        if (resultSet == null) {
            throw new ImportException("ResultSet cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).sql(resultSet);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromSql(java.sql.Connection conn, String query) {
        if (conn == null) {
            throw new ImportException("Connection cannot be null");
        }
        if (query == null) {
            throw new ImportException("Query cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).sql(conn, query);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromSql(javax.sql.DataSource dataSource, String query) {
        if (dataSource == null) {
            throw new ImportException("DataSource cannot be null");
        }
        if (query == null) {
            throw new ImportException("Query cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).sql(dataSource, query);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea exportToSql(java.sql.Connection conn, String tableName) {
        if (conn == null) {
            throw new ExportException("Connection cannot be null");
        }
        if (tableName == null) {
            throw new ExportException("Table name cannot be null");
        }
        write().sql(conn, tableName);
        return this;
    }

    public CupOfTea exportToSql(javax.sql.DataSource dataSource, String tableName) {
        if (dataSource == null) {
            throw new ExportException("DataSource cannot be null");
        }
        if (tableName == null) {
            throw new ExportException("Table name cannot be null");
        }
        write().sql(dataSource, tableName);
        return this;
    }

    // --- JSON Importers / Exporters ---

    public CupOfTea importFromJson(String json) {
        return importFromJson(json, Collections.emptyMap());
    }

    public CupOfTea importFromJson(String json, Map<String, Class<?>> schema) {
        if (json == null) {
            throw new ImportException("JSON string cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).json(json, schema);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromJson(String json, Schema schema) {
        if (json == null) {
            throw new ImportException("JSON string cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).schema(schema).json(json);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromJson(File file) {
        return importFromJson(file, Collections.emptyMap());
    }

    public CupOfTea importFromJson(File file, Map<String, Class<?>> schema) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).json(file, schema);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromJson(File file, Schema schema) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).schema(schema).json(file);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromJson(Object json) {
        return importFromJson(json, Collections.emptyMap());
    }

    public CupOfTea importFromJson(Object json, Map<String, Class<?>> schema) {
        if (json == null) {
            throw new ImportException("JSON object cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).schema(schema).json(json);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromJson(Object json, Schema schema) {
        if (json == null) {
            throw new ImportException("JSON object cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).schema(schema).json(json);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea exportToJson(File file) {
        if (file == null) {
            throw new ExportException("File cannot be null");
        }
        write().json(file);
        return this;
    }

    public Object exportToJson() {
        return this.dataFrame.getRows();
    }

    // --- HTML Importers ---

    public CupOfTea importFromHtml(File file) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).html(file);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromHtml(String html) {
        if (html == null) {
            throw new ImportException("HTML string cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).html(html);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromHtml(URL url) {
        if (url == null) {
            throw new ImportException("URL cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).html(url);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromHtml(URL url, int timeoutMillis, int maxBodySizeBytes) {
        if (url == null) {
            throw new ImportException("URL cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).html(url, timeoutMillis, maxBodySizeBytes);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromHtml(Object doc) {
        if (doc == null) {
            throw new ImportException("HTML document cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).html(doc);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    // --- YAML Importers / Exporters ---

    public CupOfTea importFromYaml(File file) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).yaml(file);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromYaml(String yamlText) {
        if (yamlText == null) {
            throw new ImportException("YAML text cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).yaml(yamlText);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea exportToYaml(File file) {
        if (file == null) {
            throw new ExportException("File cannot be null");
        }
        write().yaml(file);
        return this;
    }

    // --- XML Importers ---

    public CupOfTea importFromXml(File file) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).xml(file);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromXml(String xml) {
        if (xml == null) {
            throw new ImportException("XML text cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).xml(xml);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromXml(Object doc) {
        if (doc == null) {
            throw new ImportException("XML document cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).xml(doc);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    // --- DTO & Record Importers / Exporters ---

    public <T> CupOfTea importFromDtos(java.util.Collection<T> dtos) {
        CupOfTea target = targetForMutation();
        new com.cupoftea.importers.DtoImporter(target).importFromDtos(dtos);
        return target;
    }

    public <T extends Record> CupOfTea importFromRecords(java.util.Collection<T> records) {
        CupOfTea target = targetForMutation();
        new com.cupoftea.importers.RecordImporter(target).importFromRecords(records);
        return target;
    }

    public <T> java.util.List<T> exportToDtos(Class<T> dtoClass) {
        return new com.cupoftea.exporters.DtoExporter(this).exportToDtos(dtoClass);
    }

    public <T extends Record> java.util.List<T> exportToRecords(Class<T> recordClass) {
        return new com.cupoftea.exporters.RecordExporter(this).exportToRecords(recordClass);
    }

    @SuppressWarnings("unchecked")
    public CupOfTea setHeaders(ArrayList<?> arrayList) {
        if (arrayList == null || arrayList.isEmpty()) {
            throw new SchemaMismatchException("Headers list cannot be null or empty");
        }
        ArrayList<Column> columns;
        if (arrayList.get(0) instanceof String) {
            columns = (ArrayList<Column>) ((ArrayList<String>) arrayList).stream()
                    .map(column -> new Column(column, null))
                    .collect(Collectors.toList());
        } else if (arrayList.get(0) instanceof Column) {
            columns = new ArrayList<>((ArrayList<Column>) arrayList);
        } else {
            throw new SchemaMismatchException("Unsupported header array type: elements must be String or Column instances");
        }
        CupOfTea target = targetForMutation();
        target.dataFrame.setHeaders(columns);
        return target;
    }

    /**
     * Returns a GroupBy object to perform hash partitioning and aggregations.
     *
     * @param columns column names to group by
     * @return a {@link GroupBy} instance
     */
    public GroupBy groupBy(String... columns) {
        return new GroupBy(this, columns);
    }

    public CupOfTea sum(String column) {
        CupOfTea target = targetForMutation();
        target.dataFrame.sum(column);
        return target;
    }

    public CupOfTea average(String column) {
        CupOfTea target = targetForMutation();
        target.dataFrame.average(column);
        return target;
    }

    public CupOfTea maximum(String column) {
        CupOfTea target = targetForMutation();
        target.dataFrame.maximum(column);
        return target;
    }

    public CupOfTea minimum(String column) {
        CupOfTea target = targetForMutation();
        target.dataFrame.minimum(column);
        return target;
    }

    public CupOfTea count(String column) {
        CupOfTea target = targetForMutation();
        target.dataFrame.count(column);
        return target;
    }

    public CupOfTea aggregate(String column, String separator) {
        CupOfTea target = targetForMutation();
        target.dataFrame.aggregate(column, separator);
        return target;
    }


    // --- Join operations ---

    public CupOfTea join(CupOfTea other) {
        if (other == null) throw new IllegalArgumentException("Right DataFrame cannot be null");
        return new CupOfTea(this.dataFrame.join(other.getDataFrame()), this.mutationMode);
    }

    public CupOfTea join(CupOfTea other, String on) {
        if (other == null) throw new IllegalArgumentException("Right DataFrame cannot be null");
        return new CupOfTea(this.dataFrame.join(other.getDataFrame(), on), this.mutationMode);
    }

    public CupOfTea join(CupOfTea other, List<String> on) {
        if (other == null) throw new IllegalArgumentException("Right DataFrame cannot be null");
        return new CupOfTea(this.dataFrame.join(other.getDataFrame(), on), this.mutationMode);
    }

    public CupOfTea join(CupOfTea other, JoinType how, String on) {
        if (other == null) throw new IllegalArgumentException("Right DataFrame cannot be null");
        return new CupOfTea(this.dataFrame.join(other.getDataFrame(), how, on), this.mutationMode);
    }

    public CupOfTea join(CupOfTea other, JoinType how, List<String> on) {
        if (other == null) throw new IllegalArgumentException("Right DataFrame cannot be null");
        return new CupOfTea(this.dataFrame.join(other.getDataFrame(), how, on), this.mutationMode);
    }

    public CupOfTea join(CupOfTea other, String how, List<String> on) {
        if (other == null) throw new IllegalArgumentException("Right DataFrame cannot be null");
        return new CupOfTea(this.dataFrame.join(other.getDataFrame(), how, on), this.mutationMode);
    }

    public CupOfTea join(CupOfTea other, String leftOn, String rightOn) {
        if (other == null) throw new IllegalArgumentException("Right DataFrame cannot be null");
        return new CupOfTea(this.dataFrame.join(other.getDataFrame(), leftOn, rightOn), this.mutationMode);
    }

    public CupOfTea join(CupOfTea other, JoinType how, String leftOn, String rightOn) {
        if (other == null) throw new IllegalArgumentException("Right DataFrame cannot be null");
        return new CupOfTea(this.dataFrame.join(other.getDataFrame(), how, leftOn, rightOn), this.mutationMode);
    }

    public CupOfTea join(CupOfTea other, String how, String leftOn, String rightOn) {
        if (other == null) throw new IllegalArgumentException("Right DataFrame cannot be null");
        return new CupOfTea(this.dataFrame.join(other.getDataFrame(), how, leftOn, rightOn), this.mutationMode);
    }

    public CupOfTea join(CupOfTea other, List<String> leftOn, List<String> rightOn) {
        if (other == null) throw new IllegalArgumentException("Right DataFrame cannot be null");
        return new CupOfTea(this.dataFrame.join(other.getDataFrame(), leftOn, rightOn), this.mutationMode);
    }

    public CupOfTea join(CupOfTea other, JoinType how, List<String> leftOn, List<String> rightOn) {
        if (other == null) throw new IllegalArgumentException("Right DataFrame cannot be null");
        return new CupOfTea(this.dataFrame.join(other.getDataFrame(), how, leftOn, rightOn), this.mutationMode);
    }

    public CupOfTea join(CupOfTea other, String how, List<String> leftOn, List<String> rightOn) {
        if (other == null) throw new IllegalArgumentException("Right DataFrame cannot be null");
        return new CupOfTea(this.dataFrame.join(other.getDataFrame(), how, leftOn, rightOn), this.mutationMode);
    }

    public CupOfTea join(CupOfTea other, JoinType how, List<String> leftOn, List<String> rightOn, String leftSuffix, String rightSuffix) {
        if (other == null) throw new IllegalArgumentException("Right DataFrame cannot be null");
        return new CupOfTea(this.dataFrame.join(other.getDataFrame(), how, leftOn, rightOn, leftSuffix, rightSuffix), this.mutationMode);
    }

    public CupOfTea join(CupOfTea other, String how, List<String> leftOn, List<String> rightOn, String leftSuffix, String rightSuffix) {
        if (other == null) throw new IllegalArgumentException("Right DataFrame cannot be null");
        return new CupOfTea(this.dataFrame.join(other.getDataFrame(), how, leftOn, rightOn, leftSuffix, rightSuffix), this.mutationMode);
    }

    public CupOfTea innerJoin(CupOfTea other, String... on) {
        if (other == null) throw new IllegalArgumentException("Right DataFrame cannot be null");
        return new CupOfTea(this.dataFrame.innerJoin(other.getDataFrame(), on), this.mutationMode);
    }

    public CupOfTea innerJoin(CupOfTea other, List<String> on) {
        if (other == null) throw new IllegalArgumentException("Right DataFrame cannot be null");
        return new CupOfTea(this.dataFrame.innerJoin(other.getDataFrame(), on), this.mutationMode);
    }

    public CupOfTea innerJoin(CupOfTea other, String leftOn, String rightOn) {
        if (other == null) throw new IllegalArgumentException("Right DataFrame cannot be null");
        return new CupOfTea(this.dataFrame.innerJoin(other.getDataFrame(), leftOn, rightOn), this.mutationMode);
    }

    public CupOfTea innerJoin(CupOfTea other, List<String> leftOn, List<String> rightOn) {
        if (other == null) throw new IllegalArgumentException("Right DataFrame cannot be null");
        return new CupOfTea(this.dataFrame.innerJoin(other.getDataFrame(), leftOn, rightOn), this.mutationMode);
    }

    public CupOfTea leftJoin(CupOfTea other, String... on) {
        if (other == null) throw new IllegalArgumentException("Right DataFrame cannot be null");
        return new CupOfTea(this.dataFrame.leftJoin(other.getDataFrame(), on), this.mutationMode);
    }

    public CupOfTea leftJoin(CupOfTea other, List<String> on) {
        if (other == null) throw new IllegalArgumentException("Right DataFrame cannot be null");
        return new CupOfTea(this.dataFrame.leftJoin(other.getDataFrame(), on), this.mutationMode);
    }

    public CupOfTea leftJoin(CupOfTea other, String leftOn, String rightOn) {
        if (other == null) throw new IllegalArgumentException("Right DataFrame cannot be null");
        return new CupOfTea(this.dataFrame.leftJoin(other.getDataFrame(), leftOn, rightOn), this.mutationMode);
    }

    public CupOfTea leftJoin(CupOfTea other, List<String> leftOn, List<String> rightOn) {
        if (other == null) throw new IllegalArgumentException("Right DataFrame cannot be null");
        return new CupOfTea(this.dataFrame.leftJoin(other.getDataFrame(), leftOn, rightOn), this.mutationMode);
    }

    public CupOfTea rightJoin(CupOfTea other, String... on) {
        if (other == null) throw new IllegalArgumentException("Right DataFrame cannot be null");
        return new CupOfTea(this.dataFrame.rightJoin(other.getDataFrame(), on), this.mutationMode);
    }

    public CupOfTea rightJoin(CupOfTea other, List<String> on) {
        if (other == null) throw new IllegalArgumentException("Right DataFrame cannot be null");
        return new CupOfTea(this.dataFrame.rightJoin(other.getDataFrame(), on), this.mutationMode);
    }

    public CupOfTea rightJoin(CupOfTea other, String leftOn, String rightOn) {
        if (other == null) throw new IllegalArgumentException("Right DataFrame cannot be null");
        return new CupOfTea(this.dataFrame.rightJoin(other.getDataFrame(), leftOn, rightOn), this.mutationMode);
    }

    public CupOfTea rightJoin(CupOfTea other, List<String> leftOn, List<String> rightOn) {
        if (other == null) throw new IllegalArgumentException("Right DataFrame cannot be null");
        return new CupOfTea(this.dataFrame.rightJoin(other.getDataFrame(), leftOn, rightOn), this.mutationMode);
    }

    public CupOfTea outerJoin(CupOfTea other, String... on) {
        if (other == null) throw new IllegalArgumentException("Right DataFrame cannot be null");
        return new CupOfTea(this.dataFrame.outerJoin(other.getDataFrame(), on), this.mutationMode);
    }

    public CupOfTea outerJoin(CupOfTea other, List<String> on) {
        if (other == null) throw new IllegalArgumentException("Right DataFrame cannot be null");
        return new CupOfTea(this.dataFrame.outerJoin(other.getDataFrame(), on), this.mutationMode);
    }

    public CupOfTea outerJoin(CupOfTea other, String leftOn, String rightOn) {
        if (other == null) throw new IllegalArgumentException("Right DataFrame cannot be null");
        return new CupOfTea(this.dataFrame.outerJoin(other.getDataFrame(), leftOn, rightOn), this.mutationMode);
    }

    public CupOfTea outerJoin(CupOfTea other, List<String> leftOn, List<String> rightOn) {
        if (other == null) throw new IllegalArgumentException("Right DataFrame cannot be null");
        return new CupOfTea(this.dataFrame.outerJoin(other.getDataFrame(), leftOn, rightOn), this.mutationMode);
    }

    public CupOfTea crossJoin(CupOfTea other) {
        if (other == null) throw new IllegalArgumentException("Right DataFrame cannot be null");
        return new CupOfTea(this.dataFrame.crossJoin(other.getDataFrame()), this.mutationMode);
    }

    // --- Overloads accepting IDataFrame ---

    public CupOfTea join(IDataFrame other) {
        return new CupOfTea(this.dataFrame.join(other), this.mutationMode);
    }

    public CupOfTea join(IDataFrame other, String on) {
        return new CupOfTea(this.dataFrame.join(other, on), this.mutationMode);
    }

    public CupOfTea join(IDataFrame other, List<String> on) {
        return new CupOfTea(this.dataFrame.join(other, on), this.mutationMode);
    }

    public CupOfTea join(IDataFrame other, JoinType how, String on) {
        return new CupOfTea(this.dataFrame.join(other, how, on), this.mutationMode);
    }

    public CupOfTea join(IDataFrame other, JoinType how, List<String> on) {
        return new CupOfTea(this.dataFrame.join(other, how, on), this.mutationMode);
    }

    public CupOfTea join(IDataFrame other, String how, List<String> on) {
        return new CupOfTea(this.dataFrame.join(other, how, on), this.mutationMode);
    }

    public CupOfTea join(IDataFrame other, String leftOn, String rightOn) {
        return new CupOfTea(this.dataFrame.join(other, leftOn, rightOn), this.mutationMode);
    }

    public CupOfTea join(IDataFrame other, JoinType how, String leftOn, String rightOn) {
        return new CupOfTea(this.dataFrame.join(other, how, leftOn, rightOn), this.mutationMode);
    }

    public CupOfTea join(IDataFrame other, String how, String leftOn, String rightOn) {
        return new CupOfTea(this.dataFrame.join(other, how, leftOn, rightOn), this.mutationMode);
    }

    public CupOfTea join(IDataFrame other, List<String> leftOn, List<String> rightOn) {
        return new CupOfTea(this.dataFrame.join(other, leftOn, rightOn), this.mutationMode);
    }

    public CupOfTea join(IDataFrame other, JoinType how, List<String> leftOn, List<String> rightOn) {
        return new CupOfTea(this.dataFrame.join(other, how, leftOn, rightOn), this.mutationMode);
    }

    public CupOfTea join(IDataFrame other, String how, List<String> leftOn, List<String> rightOn) {
        return new CupOfTea(this.dataFrame.join(other, how, leftOn, rightOn), this.mutationMode);
    }

    public CupOfTea join(IDataFrame other, JoinType how, List<String> leftOn, List<String> rightOn, String leftSuffix, String rightSuffix) {
        return new CupOfTea(this.dataFrame.join(other, how, leftOn, rightOn, leftSuffix, rightSuffix), this.mutationMode);
    }

    public CupOfTea join(IDataFrame other, String how, List<String> leftOn, List<String> rightOn, String leftSuffix, String rightSuffix) {
        return new CupOfTea(this.dataFrame.join(other, how, leftOn, rightOn, leftSuffix, rightSuffix), this.mutationMode);
    }

    public CupOfTea innerJoin(IDataFrame other, String... on) {
        return new CupOfTea(this.dataFrame.innerJoin(other, on), this.mutationMode);
    }

    public CupOfTea innerJoin(IDataFrame other, List<String> on) {
        return new CupOfTea(this.dataFrame.innerJoin(other, on), this.mutationMode);
    }

    public CupOfTea innerJoin(IDataFrame other, String leftOn, String rightOn) {
        return new CupOfTea(this.dataFrame.innerJoin(other, leftOn, rightOn), this.mutationMode);
    }

    public CupOfTea innerJoin(IDataFrame other, List<String> leftOn, List<String> rightOn) {
        return new CupOfTea(this.dataFrame.innerJoin(other, leftOn, rightOn), this.mutationMode);
    }

    public CupOfTea leftJoin(IDataFrame other, String... on) {
        return new CupOfTea(this.dataFrame.leftJoin(other, on), this.mutationMode);
    }

    public CupOfTea leftJoin(IDataFrame other, List<String> on) {
        return new CupOfTea(this.dataFrame.leftJoin(other, on), this.mutationMode);
    }

    public CupOfTea leftJoin(IDataFrame other, String leftOn, String rightOn) {
        return new CupOfTea(this.dataFrame.leftJoin(other, leftOn, rightOn), this.mutationMode);
    }

    public CupOfTea leftJoin(IDataFrame other, List<String> leftOn, List<String> rightOn) {
        return new CupOfTea(this.dataFrame.leftJoin(other, leftOn, rightOn), this.mutationMode);
    }

    public CupOfTea rightJoin(IDataFrame other, String... on) {
        return new CupOfTea(this.dataFrame.rightJoin(other, on), this.mutationMode);
    }

    public CupOfTea rightJoin(IDataFrame other, List<String> on) {
        return new CupOfTea(this.dataFrame.rightJoin(other, on), this.mutationMode);
    }

    public CupOfTea rightJoin(IDataFrame other, String leftOn, String rightOn) {
        return new CupOfTea(this.dataFrame.rightJoin(other, leftOn, rightOn), this.mutationMode);
    }

    public CupOfTea rightJoin(IDataFrame other, List<String> leftOn, List<String> rightOn) {
        return new CupOfTea(this.dataFrame.rightJoin(other, leftOn, rightOn), this.mutationMode);
    }

    public CupOfTea outerJoin(IDataFrame other, String... on) {
        return new CupOfTea(this.dataFrame.outerJoin(other, on), this.mutationMode);
    }

    public CupOfTea outerJoin(IDataFrame other, List<String> on) {
        return new CupOfTea(this.dataFrame.outerJoin(other, on), this.mutationMode);
    }

    public CupOfTea outerJoin(IDataFrame other, String leftOn, String rightOn) {
        return new CupOfTea(this.dataFrame.outerJoin(other, leftOn, rightOn), this.mutationMode);
    }

    public CupOfTea outerJoin(IDataFrame other, List<String> leftOn, List<String> rightOn) {
        return new CupOfTea(this.dataFrame.outerJoin(other, leftOn, rightOn), this.mutationMode);
    }

    public CupOfTea crossJoin(IDataFrame other) {
        return new CupOfTea(this.dataFrame.crossJoin(other), this.mutationMode);
    }

    // --- Concat Operations ---

    public CupOfTea concat(CupOfTea other) {
        return concat(other != null ? other.getDataFrame() : null, ConcatAxis.ROWS, JoinType.OUTER, false);
    }

    public CupOfTea concat(CupOfTea other, ConcatAxis axis) {
        return concat(other != null ? other.getDataFrame() : null, axis, JoinType.OUTER, false);
    }

    public CupOfTea concat(CupOfTea other, int axis) {
        return concat(other != null ? other.getDataFrame() : null, ConcatAxis.fromValue(axis), JoinType.OUTER, false);
    }

    public CupOfTea concat(CupOfTea other, JoinType how) {
        return concat(other != null ? other.getDataFrame() : null, ConcatAxis.ROWS, how, false);
    }

    public CupOfTea concat(CupOfTea other, String how) {
        return concat(other != null ? other.getDataFrame() : null, ConcatAxis.ROWS, JoinType.fromString(how), false);
    }

    public CupOfTea concat(CupOfTea other, ConcatAxis axis, JoinType how) {
        return concat(other != null ? other.getDataFrame() : null, axis, how, false);
    }

    public CupOfTea concat(CupOfTea other, int axis, JoinType how) {
        return concat(other != null ? other.getDataFrame() : null, ConcatAxis.fromValue(axis), how, false);
    }

    public CupOfTea concat(CupOfTea other, int axis, String how) {
        return concat(other != null ? other.getDataFrame() : null, ConcatAxis.fromValue(axis), JoinType.fromString(how), false);
    }

    public CupOfTea concat(CupOfTea other, ConcatAxis axis, String how) {
        return concat(other != null ? other.getDataFrame() : null, axis, JoinType.fromString(how), false);
    }

    public CupOfTea concat(CupOfTea other, boolean ignoreIndex) {
        return concat(other != null ? other.getDataFrame() : null, ConcatAxis.ROWS, JoinType.OUTER, ignoreIndex);
    }

    public CupOfTea concat(CupOfTea other, ConcatAxis axis, JoinType how, boolean ignoreIndex) {
        return concat(other != null ? other.getDataFrame() : null, axis, how, ignoreIndex);
    }

    public CupOfTea concatWith(CupOfTea... others) {
        List<IDataFrame> frames = new ArrayList<>();
        frames.add(this.dataFrame);
        if (others != null) {
            for (CupOfTea o : others) {
                if (o != null) {
                    frames.add(o.getDataFrame());
                } else {
                    throw new IllegalArgumentException("DataFrame element cannot be null");
                }
            }
        }
        return new CupOfTea(DataFrameConcatenator.concat(frames, ConcatAxis.ROWS, JoinType.OUTER, false), this.mutationMode);
    }

    public CupOfTea concatWith(List<CupOfTea> others) {
        List<IDataFrame> frames = new ArrayList<>();
        frames.add(this.dataFrame);
        if (others != null) {
            for (CupOfTea o : others) {
                if (o != null) {
                    frames.add(o.getDataFrame());
                } else {
                    throw new IllegalArgumentException("DataFrame element cannot be null");
                }
            }
        }
        return new CupOfTea(DataFrameConcatenator.concat(frames, ConcatAxis.ROWS, JoinType.OUTER, false), this.mutationMode);
    }

    public CupOfTea concatHorizontal(CupOfTea other, String leftSuffix, String rightSuffix) {
        return concatHorizontal(other != null ? other.getDataFrame() : null, leftSuffix, rightSuffix);
    }

    public CupOfTea concat(IDataFrame other) {
        return concat(other, ConcatAxis.ROWS, JoinType.OUTER, false);
    }

    public CupOfTea concat(IDataFrame other, ConcatAxis axis) {
        return concat(other, axis, JoinType.OUTER, false);
    }

    public CupOfTea concat(IDataFrame other, int axis) {
        return concat(other, ConcatAxis.fromValue(axis), JoinType.OUTER, false);
    }

    public CupOfTea concat(IDataFrame other, JoinType how) {
        return concat(other, ConcatAxis.ROWS, how, false);
    }

    public CupOfTea concat(IDataFrame other, String how) {
        return concat(other, ConcatAxis.ROWS, JoinType.fromString(how), false);
    }

    public CupOfTea concat(IDataFrame other, ConcatAxis axis, JoinType how) {
        return concat(other, axis, how, false);
    }

    public CupOfTea concat(IDataFrame other, int axis, JoinType how) {
        return concat(other, ConcatAxis.fromValue(axis), how, false);
    }

    public CupOfTea concat(IDataFrame other, int axis, String how) {
        return concat(other, ConcatAxis.fromValue(axis), JoinType.fromString(how), false);
    }

    public CupOfTea concat(IDataFrame other, ConcatAxis axis, String how) {
        return concat(other, axis, JoinType.fromString(how), false);
    }

    public CupOfTea concat(IDataFrame other, boolean ignoreIndex) {
        return concat(other, ConcatAxis.ROWS, JoinType.OUTER, ignoreIndex);
    }

    public CupOfTea concat(IDataFrame other, ConcatAxis axis, JoinType how, boolean ignoreIndex) {
        if (other == null) {
            throw new IllegalArgumentException("Other DataFrame cannot be null");
        }
        IDataFrame concatenated = this.dataFrame.concat(other, axis, how, ignoreIndex);
        return new CupOfTea(concatenated, this.mutationMode);
    }

    public CupOfTea concatHorizontal(IDataFrame other, String leftSuffix, String rightSuffix) {
        if (other == null) {
            throw new IllegalArgumentException("Other DataFrame cannot be null");
        }
        IDataFrame concatenated = this.dataFrame.concatHorizontal(other, leftSuffix, rightSuffix);
        return new CupOfTea(concatenated, this.mutationMode);
    }

    // --- Static Concat Methods on CupOfTea ---

    public static CupOfTea concat(CupOfTea... dfs) {
        return concat(dfs != null ? Arrays.asList(dfs) : Collections.emptyList(), ConcatAxis.ROWS, JoinType.OUTER, false);
    }

    public static CupOfTea concat(List<CupOfTea> dfs) {
        return concat(dfs, ConcatAxis.ROWS, JoinType.OUTER, false);
    }

    public static CupOfTea concat(ConcatAxis axis, CupOfTea... dfs) {
        return concat(dfs != null ? Arrays.asList(dfs) : Collections.emptyList(), axis, JoinType.OUTER, false);
    }

    public static CupOfTea concat(int axis, CupOfTea... dfs) {
        return concat(dfs != null ? Arrays.asList(dfs) : Collections.emptyList(), ConcatAxis.fromValue(axis), JoinType.OUTER, false);
    }

    public static CupOfTea concat(List<CupOfTea> dfs, ConcatAxis axis) {
        return concat(dfs, axis, JoinType.OUTER, false);
    }

    public static CupOfTea concat(List<CupOfTea> dfs, int axis) {
        return concat(dfs, ConcatAxis.fromValue(axis), JoinType.OUTER, false);
    }

    public static CupOfTea concat(List<CupOfTea> dfs, JoinType how) {
        return concat(dfs, ConcatAxis.ROWS, how, false);
    }

    public static CupOfTea concat(List<CupOfTea> dfs, ConcatAxis axis, JoinType how) {
        return concat(dfs, axis, how, false);
    }

    public static CupOfTea concat(List<CupOfTea> dfs, ConcatAxis axis, JoinType how, boolean ignoreIndex) {
        if (dfs == null || dfs.isEmpty()) {
            throw new IllegalArgumentException("DataFrames list cannot be null or empty");
        }
        List<IDataFrame> rawFrames = new ArrayList<>(dfs.size());
        MutationMode mode = dfs.get(0) != null ? dfs.get(0).getMutationMode() : MutationMode.IN_PLACE;
        for (CupOfTea cot : dfs) {
            if (cot == null) {
                throw new IllegalArgumentException("DataFrame element cannot be null");
            }
            rawFrames.add(cot.getDataFrame());
        }
        IDataFrame concatenated = DataFrameConcatenator.concat(rawFrames, axis, how, ignoreIndex);
        return new CupOfTea(concatenated, mode);
    }

    // ==========================================
    // Statistical Operations & Parity with Pandas
    // ==========================================

    public Double mean(String column) {
        return this.dataFrame.mean(column);
    }

    public Double min(String column) {
        return this.dataFrame.min(column);
    }

    public Double max(String column) {
        return this.dataFrame.max(column);
    }

    public Double median(String column) {
        return this.dataFrame.median(column);
    }

    public Double variance(String column) {
        return this.dataFrame.variance(column);
    }

    public Double variance(String column, boolean isSample) {
        return this.dataFrame.variance(column, isSample);
    }

    public Double var(String column) {
        return this.dataFrame.var(column);
    }

    public Double var(String column, boolean isSample) {
        return this.dataFrame.var(column, isSample);
    }

    public Double standardDeviation(String column) {
        return this.dataFrame.standardDeviation(column);
    }

    public Double standardDeviation(String column, boolean isSample) {
        return this.dataFrame.standardDeviation(column, isSample);
    }

    public Double std(String column) {
        return this.dataFrame.std(column);
    }

    public Double std(String column, boolean isSample) {
        return this.dataFrame.std(column, isSample);
    }

    public Double quantile(String column, double q) {
        return this.dataFrame.quantile(column, q);
    }

    public Double skewness(String column) {
        return this.dataFrame.skewness(column);
    }

    public Double skewness(String column, boolean isSample) {
        return this.dataFrame.skewness(column, isSample);
    }

    public Double skew(String column) {
        return this.dataFrame.skew(column);
    }

    public Double kurtosis(String column) {
        return this.dataFrame.kurtosis(column);
    }

    public Double kurtosis(String column, boolean isSample) {
        return this.dataFrame.kurtosis(column, isSample);
    }

    public Double kurt(String column) {
        return this.dataFrame.kurt(column);
    }

    public Double covariance(String col1, String col2) {
        return this.dataFrame.covariance(col1, col2);
    }

    public Double covariance(String col1, String col2, boolean isSample) {
        return this.dataFrame.covariance(col1, col2, isSample);
    }

    public Double cov(String col1, String col2) {
        return this.dataFrame.cov(col1, col2);
    }

    public Double cov(String col1, String col2, boolean isSample) {
        return this.dataFrame.cov(col1, col2, isSample);
    }

    public Double correlation(String col1, String col2) {
        return this.dataFrame.correlation(col1, col2);
    }

    public Double corr(String col1, String col2) {
        return this.dataFrame.corr(col1, col2);
    }

    public CupOfTea corr() {
        return new CupOfTea(this.dataFrame.corr(), this.mutationMode);
    }

    public CupOfTea describe() {
        return new CupOfTea(this.dataFrame.describe(), this.mutationMode);
    }

    // ==========================================
    // Window, Rolling, and Analytical Operations
    // ==========================================

    public RollingWindow rolling(int windowSize) {
        return new RollingWindow(this, windowSize);
    }

    public ExpandingWindow expanding() {
        return new ExpandingWindow(this, 1);
    }

    public ExpandingWindow expanding(int minPeriods) {
        return new ExpandingWindow(this, minPeriods);
    }

    public Window window() {
        return new Window(this);
    }

    public CupOfTea cumsum(String... columns) {
        CupOfTea target = targetForMutation();
        target.dataFrame.cumsum(columns);
        return target;
    }

    public CupOfTea cumprod(String... columns) {
        CupOfTea target = targetForMutation();
        target.dataFrame.cumprod(columns);
        return target;
    }

    public CupOfTea cummin(String... columns) {
        CupOfTea target = targetForMutation();
        target.dataFrame.cummin(columns);
        return target;
    }

    public CupOfTea cummax(String... columns) {
        CupOfTea target = targetForMutation();
        target.dataFrame.cummax(columns);
        return target;
    }

    public CupOfTea lead(String column, int offset, String outputColumn) {
        CupOfTea target = targetForMutation();
        target.dataFrame.lead(column, offset, outputColumn);
        return target;
    }

    public CupOfTea lead(String column, int offset) {
        return lead(column, offset, column);
    }

    public CupOfTea lead(String column) {
        return lead(column, 1, column);
    }

    public CupOfTea lag(String column, int offset, String outputColumn) {
        CupOfTea target = targetForMutation();
        target.dataFrame.lag(column, offset, outputColumn);
        return target;
    }

    public CupOfTea lag(String column, int offset) {
        return lag(column, offset, column);
    }

    public CupOfTea lag(String column) {
        return lag(column, 1, column);
    }

    public CupOfTea diff(String column, int periods, String outputColumn) {
        CupOfTea target = targetForMutation();
        target.dataFrame.diff(column, periods, outputColumn);
        return target;
    }

    public CupOfTea diff(String column, int periods) {
        return diff(column, periods, column);
    }

    public CupOfTea diff(String column) {
        return diff(column, 1, column);
    }

    public CupOfTea diff(String... columns) {
        CupOfTea target = targetForMutation();
        target.dataFrame.diff(columns);
        return target;
    }

    public CupOfTea pctChange(String column, int periods, String outputColumn) {
        CupOfTea target = targetForMutation();
        target.dataFrame.pctChange(column, periods, outputColumn);
        return target;
    }

    public CupOfTea pctChange(String column, int periods) {
        return pctChange(column, periods, column);
    }

    public CupOfTea pctChange(String column) {
        return pctChange(column, 1, column);
    }

    public CupOfTea pctChange(String... columns) {
        CupOfTea target = targetForMutation();
        target.dataFrame.pctChange(columns);
        return target;
    }

    public CupOfTea rank(String column, String outputColumn) {
        CupOfTea target = targetForMutation();
        target.dataFrame.rank(column, outputColumn);
        return target;
    }

    public CupOfTea rank(String column) {
        return rank(column, "rank");
    }

    public CupOfTea denseRank(String column, String outputColumn) {
        CupOfTea target = targetForMutation();
        target.dataFrame.denseRank(column, outputColumn);
        return target;
    }

    public CupOfTea denseRank(String column) {
        return denseRank(column, "dense_rank");
    }

    public CupOfTea percentRank(String column, String outputColumn) {
        CupOfTea target = targetForMutation();
        target.dataFrame.percentRank(column, outputColumn);
        return target;
    }

    public CupOfTea percentRank(String column) {
        return percentRank(column, "percent_rank");
    }

    public CupOfTea rowNumber(String outputColumn) {
        CupOfTea target = targetForMutation();
        target.dataFrame.rowNumber(outputColumn);
        return target;
    }

    public CupOfTea rowNumber() {
        return rowNumber("row_number");
    }

    /**
     * Obtains a declarative plotting builder for visualizing this DataFrame.
     * Requires the optional {@code cupoftea-plot} module on the classpath.
     *
     * @return a {@link Plot} instance
     */
    public Plot plot() {
        return this.dataFrame.plot();
    }

    /**
     * Obtains a declarative plotting builder backed by a specific plotting engine.
     *
     * @param engine plotting engine name (e.g. "vegalite")
     * @return a {@link Plot} instance
     */
    public Plot plot(String engine) {
        return this.dataFrame.plot(engine);
    }

    /**
     * Renders this DataFrame as a clean, responsive HTML table with sticky headers,
     * data type indicators, and client-side pagination.
     *
     * @return HTML string representation
     */
    public String toHtml() {
        return this.dataFrame.toHtml();
    }

    /**
     * Renders this DataFrame as an HTML table displaying up to {@code maxRows} rows.
     *
     * @param maxRows maximum number of rows to include
     * @return HTML string representation
     */
    public String toHtml(int maxRows) {
        return this.dataFrame.toHtml(maxRows);
    }

    /**
     * Renders this DataFrame as an HTML table using the specified configuration.
     *
     * @param configuration table HTML rendering configuration
     * @return HTML string representation
     */
    public String toHtml(TableHtmlConfiguration configuration) {
        return this.dataFrame.toHtml(configuration);
    }

    /**
     * Jupyter / IJava notebook MIME representation method.
     * Automatically invoked by Jupyter kernels when this DataFrame is evaluated in a cell.
     *
     * @return HTML table string
     */
    public String renderHtml() {
        return this.dataFrame.renderHtml();
    }

    /**
     * Jupyter / Polyglot notebook MIME representation bundle.
     *
     * @return map mapping MIME type ("text/html") to HTML representation
     */
    public Map<String, String> render() {
        return this.dataFrame.render();
    }
}
