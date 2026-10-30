package com.cupoftea.table.interfaces;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.LongUnaryOperator;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.cupoftea.plot.Plot;
import com.cupoftea.plot.PlotRegistry;
import com.cupoftea.table.Aggregate;
import com.cupoftea.table.Aggregation;
import com.cupoftea.table.Column;
import com.cupoftea.table.ColumnRef;
import com.cupoftea.table.ConcatAxis;
import com.cupoftea.table.DataFrameConcatenator;
import com.cupoftea.table.Header;
import com.cupoftea.table.ILoc;
import com.cupoftea.table.Index;
import com.cupoftea.table.JoinType;
import com.cupoftea.table.Keep;
import com.cupoftea.table.Loc;
import com.cupoftea.table.Row;
import com.cupoftea.table.Schema;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.ParallelVectorEngine;
import com.cupoftea.utils.TableHtmlRenderer;
import com.cupoftea.utils.configurations.TableHtmlConfiguration;
import com.cupoftea.utils.interfaces.IColumnCreator;
import com.cupoftea.utils.interfaces.IFilter;
import com.cupoftea.utils.interfaces.Orderer;

public abstract class IDataFrame {

    protected Header header;
    protected ArrayList<HashMap<String, Object>> rows;
    protected String indexColumn;
    protected Index index;

    protected boolean parallel = false;
    protected transient ExecutorService executor = null;
    protected int parallelThreshold = ParallelVectorEngine.DEFAULT_PARALLEL_THRESHOLD;

    public IDataFrame() {
        this.rows = new ArrayList<>();
        this.header = new Header();
        this.indexColumn = null;
        this.index = new Index();
    }

    // --- Parallel Execution Configuration ---

    /**
     * Enables multithreaded parallel execution mode using {@link ForkJoinPool#commonPool()} (or any previously configured executor).
     */
    public IDataFrame parallel() {
        this.parallel = true;
        return this;
    }

    /**
     * Enables multithreaded parallel execution mode using the provided {@link ExecutorService}.
     */
    public IDataFrame parallel(@NotNull ExecutorService executor) {
        this.executor = Objects.requireNonNull(executor, "ExecutorService cannot be null");
        this.parallel = true;
        return this;
    }

    /**
     * Alias for {@link #parallel()}.
     */
    public IDataFrame asParallel() {
        return parallel();
    }

    /**
     * Alias for {@link #parallel(ExecutorService)}.
     */
    public IDataFrame asParallel(@NotNull ExecutorService executor) {
        return parallel(executor);
    }

    /**
     * Switches execution mode back to single-threaded sequential evaluation.
     */
    public IDataFrame sequential() {
        this.parallel = false;
        return this;
    }

    /**
     * Alias for {@link #sequential()}.
     */
    public IDataFrame asSequential() {
        return sequential();
    }

    /**
     * Returns {@code true} if parallel execution mode is enabled.
     */
    public boolean isParallel() {
        return this.parallel;
    }

    /**
     * Returns the configured {@link ExecutorService}, or {@link ForkJoinPool#commonPool()} if none was explicitly set.
     */
    public ExecutorService getExecutor() {
        return ParallelVectorEngine.resolveExecutor(this.executor);
    }

    /**
     * Returns the minimum row count threshold below which execution stays sequential.
     */
    public int getParallelThreshold() {
        return this.parallelThreshold;
    }

    /**
     * Sets the minimum row count threshold below which execution automatically stays sequential.
     */
    public IDataFrame setParallelThreshold(int threshold) {
        if (threshold < 0) {
            throw new IllegalArgumentException("Parallel threshold cannot be negative, got: " + threshold);
        }
        this.parallelThreshold = threshold;
        return this;
    }

    /**
     * Fluent alias for {@link #setParallelThreshold(int)}.
     */
    public IDataFrame withParallelThreshold(int threshold) {
        return setParallelThreshold(threshold);
    }

    protected boolean shouldExecuteParallel(int rowCount) {
        return this.parallel && rowCount >= this.parallelThreshold && rowCount > 1;
    }

    protected void copyParallelConfigTo(IDataFrame target) {
        if (target != null) {
            target.parallel = this.parallel;
            target.executor = this.executor;
            target.parallelThreshold = this.parallelThreshold;
        }
    }

    /**
     * Returns an unmodifiable list of {@link Row} views.
     * Evaluated lazily over the underlying columnar storage without allocating intermediate row maps.
     */
    public List<Row> getRows() {
        return Collections.unmodifiableList(new AbstractList<Row>() {
            @Override
            public Row get(int index) {
                if (index < 0 || index >= size()) {
                    throw new IndexOutOfBoundsException("Index " + index + " out of bounds for length " + size());
                }
                return getRow(index);
            }

            @Override
            public int size() {
                return IDataFrame.this.size();
            }
        });
    }

    /**
     * Returns the unmodifiable {@link Row} at the specified index.
     * The row columns are ordered according to the DataFrame header.
     */
    public abstract Row getRow(int index);

    /**
     * Returns row interchange maps.
     */
    public ArrayList<HashMap<String, Object>> getInternalRows() {
        ArrayList<HashMap<String, Object>> list = new ArrayList<>(this.size());
        for (int i = 0; i < this.size(); i++) {
            list.add(new HashMap<>(this.getRow(i)));
        }
        return list;
    }

    public List<ColumnVector> getColumnVectors() {
        return Collections.emptyList();
    }

    public ColumnVector getColumnVector(String columnName) {
        return null;
    }

    public ColumnVector getColumnVector(int index) {
        return null;
    }

    public Index getIndexObject() {
        return this.index;
    }

    public IDataFrame(Schema schema) {
        this();
        if (schema != null) {
            this.header = schema.toHeader();
        }
    }

    public void clear() {
        this.rows.clear();
        this.header.clear();
        this.indexColumn = null;
    }

    public void clearRows() {
        this.rows.clear();
    }

    public abstract IDataFrame copy();

    public abstract ArrayList<Column> getHeaders();
    public abstract Column getColumnHeader(String columnName);
    public abstract Schema getSchema();
    public abstract IDataFrame setSchema(Schema schema);
    public abstract IDataFrame moveColumn(String columnName, int finalIndex);
    public abstract IDataFrame moveColumn(int inicialIndex, int finalIndex);
    public abstract IDataFrame renameColumn(String oldName, String newName);
    public abstract IDataFrame renameColumn(int index, String newName);
    public abstract IDataFrame renameColumn(HashMap<String,String> name);
    public abstract IDataFrame addColumn(String columnName);
    public abstract IDataFrame addColumn(String columnName, Class<?> columnType);
    public abstract IDataFrame addColumn(int index, String columnName);
    public abstract IDataFrame addColumn(int index, String columnName, Class<?> columnType);
    public abstract IDataFrame addColumn(int index, String columnName, @Nullable Object data);
    public abstract IDataFrame addColumn(int index, String columnName, @Nullable ArrayList<Object> data);
    public abstract IDataFrame addColumn(String columnName, IColumnCreator columnCreator);
    public abstract IDataFrame addColumn(String columnName, Class<?> columnType, IColumnCreator columnCreator);
    public abstract IDataFrame addColumn(String columnName, IColumnCreator columnCreator, Class<?> columnType);
    public abstract IDataFrame addColumn(int index, String columnName, IColumnCreator columnCreator);
    public abstract IDataFrame addColumn(int index, String columnName, Class<?> columnType, IColumnCreator columnCreator);
    public abstract IDataFrame addColumn(int index, String columnName, IColumnCreator columnCreator, Class<?> columnType);
    public abstract IDataFrame removeColumn(String columnName);
    public abstract IDataFrame removeColumn(int index);
    public abstract IDataFrame alterColumnType(String columnName, Class<?> newColumnType);

    public abstract ColumnRef col(String columnName);
    public abstract IDataFrame withColumn(String columnName, ColumnVector vector);
    public abstract IDataFrame withColumn(String columnName, ColumnRef columnRef);
    public abstract IDataFrame withColumn(String columnName, IColumnCreator columnCreator);
    public abstract IDataFrame withColumn(String columnName, Class<?> columnType, IColumnCreator columnCreator);
    public abstract IDataFrame transformColumn(String columnName, ColumnVector vector);
    public abstract IDataFrame transformColumn(String columnName, ColumnRef columnRef);
    public abstract IDataFrame transformColumn(String columnName, Function<ColumnVector, ColumnVector> transformer);

    public abstract IDataFrame addRow(@NotNull Map<String, Object> row);
    public abstract IDataFrame removeRow(int index);
    public abstract IDataFrame removeRows(IFilter filter);
    public abstract IDataFrame filter(IFilter filter);
    public abstract IDataFrame update(Map<String, Object> updateColumns);
    public abstract IDataFrame orderBy(Orderer... orderer);

    // Dictionary / Categorical Encoding & Code-Accelerated Filtering

    public IDataFrame encodeDictionary(String... columnNames) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public IDataFrame encodeDictionary(boolean useShortCodes, String... columnNames) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public IDataFrame encodeDictionary() {
        return encodeDictionary(false);
    }

    public IDataFrame encodeDictionary(boolean useShortCodes) {
        return encodeDictionary(useShortCodes, (String[]) null);
    }

    public IDataFrame decodeDictionary(String... columnNames) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public IDataFrame decodeDictionary() {
        return decodeDictionary((String[]) null);
    }

    public IDataFrame categorize(String... columnNames) {
        return encodeDictionary(columnNames);
    }

    public IDataFrame toCategorical(String... columnNames) {
        return encodeDictionary(columnNames);
    }

    public boolean isDictionaryEncoded(String columnName) {
        ColumnVector vec = getColumnVector(columnName);
        return vec != null && vec.isDictionaryEncoded();
    }

    public boolean isCategorical(String columnName) {
        return isDictionaryEncoded(columnName);
    }

    public IDataFrame filterEquals(String columnName, Object value) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public IDataFrame filterNotEquals(String columnName, Object value) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public IDataFrame filterIn(String columnName, Collection<?> values) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public IDataFrame filterIn(String columnName, Object... values) {
        return filterIn(columnName, values != null ? Arrays.asList(values) : Collections.emptyList());
    }

    // Vectorized Map / Transformations

    public IDataFrame map(String columnName, IColumnCreator columnCreator) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public IDataFrame map(String columnName, @Nullable Class<?> columnType, IColumnCreator columnCreator) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public IDataFrame mapColumn(String columnName, Function<Object, ?> valueMapper) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public IDataFrame mapColumn(String columnName, @Nullable Class<?> targetType, Function<Object, ?> valueMapper) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public IDataFrame mapInt(String columnName, IntUnaryOperator mapper) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public IDataFrame mapLong(String columnName, LongUnaryOperator mapper) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public IDataFrame mapDouble(String columnName, DoubleUnaryOperator mapper) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public IDataFrame map(Function<Row, ? extends Map<String, Object>> rowMapper) {
        throw new UnsupportedOperationException("Not implemented");
    }

    // Vectorized Whole-DataFrame Aggregations

    public IDataFrame agg(Aggregate... aggregates) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public Object sumOf(String column) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public Double meanOf(String column) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public Double averageOf(String column) {
        return meanOf(column);
    }

    public Object minOf(String column) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public Object maxOf(String column) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public int countOf(String column) {
        throw new UnsupportedOperationException("Not implemented");
    }

    // Pandas-like core operations

    public abstract IDataFrame select(String... columnNames);
    public abstract IDataFrame select(List<String> columnNames);
    public abstract IDataFrame drop(String... columnNames);
    public abstract IDataFrame drop(List<String> columnNames);

    public abstract IDataFrame head();
    public abstract IDataFrame head(int n);
    public abstract IDataFrame tail();
    public abstract IDataFrame tail(int n);
    public abstract IDataFrame slice(int fromRow, int toRow);

    public abstract Row iloc(int index);
    public abstract Object iloc(int rowIndex, int colIndex);
    public abstract Object iloc(int rowIndex, String columnName);
    public abstract ILoc iloc();

    public String getIndex() {
        return this.index != null && this.index.hasIndex() ? this.index.getColumnName() : this.indexColumn;
    }
    public abstract IDataFrame setIndex(String columnName);
    public abstract IDataFrame resetIndex();
    public abstract Row loc(Object key);
    public abstract Object loc(Object key, String column);
    public abstract Loc loc();

    public abstract IDataFrame fillna(Object value);
    public abstract IDataFrame fillna(String column, Object value);
    public abstract IDataFrame fillna(Map<String, Object> values);

    public abstract IDataFrame dropna();
    public abstract IDataFrame dropna(String how);
    public abstract IDataFrame dropna(String... columns);
    public abstract IDataFrame dropna(List<String> columns);
    public abstract IDataFrame dropna(String how, String... columns);
    public abstract IDataFrame dropna(String how, List<String> columns);

    public abstract IDataFrame sum(String column);
    public abstract IDataFrame average(String column);
    public abstract IDataFrame maximum(String column);
    public abstract IDataFrame minimum(String column);
    public abstract IDataFrame count(String column);
    public abstract IDataFrame aggregate(String column, String separator);

    // Statistical scalar operations
    public abstract Double mean(String column);
    public abstract Double min(String column);
    public abstract Double max(String column);
    public abstract Double median(String column);
    public abstract Double variance(String column);
    public abstract Double variance(String column, boolean isSample);
    public abstract Double var(String column);
    public abstract Double var(String column, boolean isSample);
    public abstract Double standardDeviation(String column);
    public abstract Double standardDeviation(String column, boolean isSample);
    public abstract Double std(String column);
    public abstract Double std(String column, boolean isSample);
    public abstract Double quantile(String column, double q);
    public abstract Double skewness(String column);
    public abstract Double skewness(String column, boolean isSample);
    public abstract Double skew(String column);
    public abstract Double kurtosis(String column);
    public abstract Double kurtosis(String column, boolean isSample);
    public abstract Double kurt(String column);

    // Bivariate statistics
    public abstract Double covariance(String col1, String col2);
    public abstract Double covariance(String col1, String col2, boolean isSample);
    public abstract Double cov(String col1, String col2);
    public abstract Double cov(String col1, String col2, boolean isSample);
    public abstract Double correlation(String col1, String col2);
    public abstract Double corr(String col1, String col2);

    // Correlation matrix & summary
    public abstract IDataFrame corr();
    public abstract IDataFrame describe();

    // Deduplication
    public abstract IDataFrame dropDuplicates();
    public abstract IDataFrame dropDuplicates(Keep keep);
    public abstract IDataFrame dropDuplicates(String... subset);
    public abstract IDataFrame dropDuplicates(Keep keep, String... subset);
    public abstract IDataFrame dropDuplicates(List<String> subset);
    public abstract IDataFrame dropDuplicates(List<String> subset, Keep keep);
    public abstract IDataFrame distinct();
    public abstract IDataFrame distinct(Keep keep);
    public abstract IDataFrame distinct(String... subset);
    public abstract IDataFrame distinct(Keep keep, String... subset);
    public abstract IDataFrame distinct(List<String> subset);
    public abstract IDataFrame distinct(List<String> subset, Keep keep);

    // Reshaping - Pivot
    public abstract IDataFrame pivot(String indexCol, String columnsCol, String valuesCol);
    public abstract IDataFrame pivot(String indexCol, String columnsCol, String valuesCol, Aggregation agg);
    public abstract IDataFrame pivot(String indexCol, String columnsCol, String valuesCol, Aggregate agg);

    // Reshaping - Melt
    public abstract IDataFrame melt();
    public abstract IDataFrame melt(List<String> idVars);
    public abstract IDataFrame melt(List<String> idVars, List<String> valueVars);
    public abstract IDataFrame melt(List<String> idVars, List<String> valueVars, String varName, String valueName);

    // Random Sampling
    public abstract IDataFrame sample(int n);
    public abstract IDataFrame sample(int n, boolean replace);
    public abstract IDataFrame sample(int n, long seed);
    public abstract IDataFrame sample(int n, boolean replace, long seed);
    public abstract IDataFrame sample(double fraction);
    public abstract IDataFrame sample(double fraction, long seed);
    public abstract IDataFrame sample(double fraction, boolean replace, long seed);

    public Integer size() {
        return this.rows.size();
    }

    // Join operations

    public IDataFrame join(IDataFrame other) {
        return join(other, JoinType.INNER, (List<String>) null, (List<String>) null, "_left", "_right");
    }

    public IDataFrame join(IDataFrame other, String on) {
        return join(other, JoinType.INNER, on != null ? Collections.singletonList(on) : null, on != null ? Collections.singletonList(on) : null, "_left", "_right");
    }

    public IDataFrame join(IDataFrame other, List<String> on) {
        return join(other, JoinType.INNER, on, on, "_left", "_right");
    }

    public IDataFrame join(IDataFrame other, JoinType how, String on) {
        return join(other, how, on != null ? Collections.singletonList(on) : null, on != null ? Collections.singletonList(on) : null, "_left", "_right");
    }

    public IDataFrame join(IDataFrame other, JoinType how, List<String> on) {
        return join(other, how, on, on, "_left", "_right");
    }

    public IDataFrame join(IDataFrame other, String how, List<String> on) {
        return join(other, JoinType.fromString(how), on, on, "_left", "_right");
    }

    public IDataFrame join(IDataFrame other, String leftOn, String rightOn) {
        return join(other, JoinType.INNER, leftOn != null ? Collections.singletonList(leftOn) : null, rightOn != null ? Collections.singletonList(rightOn) : null, "_left", "_right");
    }

    public IDataFrame join(IDataFrame other, JoinType how, String leftOn, String rightOn) {
        return join(other, how, leftOn != null ? Collections.singletonList(leftOn) : null, rightOn != null ? Collections.singletonList(rightOn) : null, "_left", "_right");
    }

    public IDataFrame join(IDataFrame other, String how, String leftOn, String rightOn) {
        return join(other, JoinType.fromString(how), leftOn != null ? Collections.singletonList(leftOn) : null, rightOn != null ? Collections.singletonList(rightOn) : null, "_left", "_right");
    }

    public IDataFrame join(IDataFrame other, List<String> leftOn, List<String> rightOn) {
        return join(other, JoinType.INNER, leftOn, rightOn, "_left", "_right");
    }

    public IDataFrame join(IDataFrame other, JoinType how, List<String> leftOn, List<String> rightOn) {
        return join(other, how, leftOn, rightOn, "_left", "_right");
    }

    public IDataFrame join(IDataFrame other, String how, List<String> leftOn, List<String> rightOn) {
        return join(other, JoinType.fromString(how), leftOn, rightOn, "_left", "_right");
    }

    public IDataFrame join(IDataFrame other, String how, List<String> leftOn, List<String> rightOn, String leftSuffix, String rightSuffix) {
        return join(other, JoinType.fromString(how), leftOn, rightOn, leftSuffix, rightSuffix);
    }

    public abstract IDataFrame join(IDataFrame other, JoinType how, List<String> leftOn, List<String> rightOn, String leftSuffix, String rightSuffix);

    public IDataFrame innerJoin(IDataFrame other, String... on) {
        List<String> onList = (on != null && on.length > 0) ? Arrays.asList(on) : null;
        return join(other, JoinType.INNER, onList, onList, "_left", "_right");
    }

    public IDataFrame innerJoin(IDataFrame other, List<String> on) {
        return join(other, JoinType.INNER, on, on, "_left", "_right");
    }

    public IDataFrame innerJoin(IDataFrame other, String leftOn, String rightOn) {
        return join(other, JoinType.INNER, leftOn, rightOn);
    }

    public IDataFrame innerJoin(IDataFrame other, List<String> leftOn, List<String> rightOn) {
        return join(other, JoinType.INNER, leftOn, rightOn);
    }

    public IDataFrame leftJoin(IDataFrame other, String... on) {
        List<String> onList = (on != null && on.length > 0) ? Arrays.asList(on) : null;
        return join(other, JoinType.LEFT, onList, onList, "_left", "_right");
    }

    public IDataFrame leftJoin(IDataFrame other, List<String> on) {
        return join(other, JoinType.LEFT, on, on, "_left", "_right");
    }

    public IDataFrame leftJoin(IDataFrame other, String leftOn, String rightOn) {
        return join(other, JoinType.LEFT, leftOn, rightOn);
    }

    public IDataFrame leftJoin(IDataFrame other, List<String> leftOn, List<String> rightOn) {
        return join(other, JoinType.LEFT, leftOn, rightOn);
    }

    public IDataFrame rightJoin(IDataFrame other, String... on) {
        List<String> onList = (on != null && on.length > 0) ? Arrays.asList(on) : null;
        return join(other, JoinType.RIGHT, onList, onList, "_left", "_right");
    }

    public IDataFrame rightJoin(IDataFrame other, List<String> on) {
        return join(other, JoinType.RIGHT, on, on, "_left", "_right");
    }

    public IDataFrame rightJoin(IDataFrame other, String leftOn, String rightOn) {
        return join(other, JoinType.RIGHT, leftOn, rightOn);
    }

    public IDataFrame rightJoin(IDataFrame other, List<String> leftOn, List<String> rightOn) {
        return join(other, JoinType.RIGHT, leftOn, rightOn);
    }

    public IDataFrame outerJoin(IDataFrame other, String... on) {
        List<String> onList = (on != null && on.length > 0) ? Arrays.asList(on) : null;
        return join(other, JoinType.OUTER, onList, onList, "_left", "_right");
    }

    public IDataFrame outerJoin(IDataFrame other, List<String> on) {
        return join(other, JoinType.OUTER, on, on, "_left", "_right");
    }

    public IDataFrame outerJoin(IDataFrame other, String leftOn, String rightOn) {
        return join(other, JoinType.OUTER, leftOn, rightOn);
    }

    public IDataFrame outerJoin(IDataFrame other, List<String> leftOn, List<String> rightOn) {
        return join(other, JoinType.OUTER, leftOn, rightOn);
    }

    public IDataFrame crossJoin(IDataFrame other) {
        return join(other, JoinType.CROSS, null, null, "_left", "_right");
    }

    // --- Concat Operations ---

    /**
     * Concatenates this DataFrame with another DataFrame along rows (vertical concatenation) using outer join semantics.
     *
     * @param other DataFrame to concatenate with
     * @return a new concatenated DataFrame
     */
    public IDataFrame concat(IDataFrame other) {
        return concat(other, ConcatAxis.ROWS, JoinType.OUTER, false);
    }

    /**
     * Concatenates this DataFrame with another DataFrame along the specified axis using outer join semantics.
     *
     * @param other DataFrame to concatenate with
     * @param axis  axis along which to concatenate (ROWS or COLUMNS)
     * @return a new concatenated DataFrame
     */
    public IDataFrame concat(IDataFrame other, ConcatAxis axis) {
        return concat(other, axis, JoinType.OUTER, false);
    }

    /**
     * Concatenates this DataFrame with another DataFrame along the specified axis (0 for ROWS, 1 for COLUMNS) using outer join semantics.
     *
     * @param other DataFrame to concatenate with
     * @param axis  0 for ROWS, 1 for COLUMNS
     * @return a new concatenated DataFrame
     */
    public IDataFrame concat(IDataFrame other, int axis) {
        return concat(other, ConcatAxis.fromValue(axis), JoinType.OUTER, false);
    }

    /**
     * Concatenates this DataFrame with another DataFrame along rows using the specified join type.
     *
     * @param other DataFrame to concatenate with
     * @param how   join type (OUTER or INNER)
     * @return a new concatenated DataFrame
     */
    public IDataFrame concat(IDataFrame other, JoinType how) {
        return concat(other, ConcatAxis.ROWS, how, false);
    }

    /**
     * Concatenates this DataFrame with another DataFrame along rows using the specified join type name ("outer" or "inner").
     *
     * @param other DataFrame to concatenate with
     * @param how   join type name ("outer", "inner")
     * @return a new concatenated DataFrame
     */
    public IDataFrame concat(IDataFrame other, String how) {
        return concat(other, ConcatAxis.ROWS, JoinType.fromString(how), false);
    }

    /**
     * Concatenates this DataFrame with another DataFrame along the specified axis using the given join type.
     *
     * @param other DataFrame to concatenate with
     * @param axis  axis along which to concatenate
     * @param how   join type (OUTER or INNER)
     * @return a new concatenated DataFrame
     */
    public IDataFrame concat(IDataFrame other, ConcatAxis axis, JoinType how) {
        return concat(other, axis, how, false);
    }

    /**
     * Concatenates this DataFrame with another DataFrame along the specified axis using the given join type.
     *
     * @param other DataFrame to concatenate with
     * @param axis  0 for ROWS, 1 for COLUMNS
     * @param how   join type (OUTER or INNER)
     * @return a new concatenated DataFrame
     */
    public IDataFrame concat(IDataFrame other, int axis, JoinType how) {
        return concat(other, ConcatAxis.fromValue(axis), how, false);
    }

    /**
     * Concatenates this DataFrame with another DataFrame along the specified axis using the given join type name.
     *
     * @param other DataFrame to concatenate with
     * @param axis  0 for ROWS, 1 for COLUMNS
     * @param how   join type name ("outer", "inner")
     * @return a new concatenated DataFrame
     */
    public IDataFrame concat(IDataFrame other, int axis, String how) {
        return concat(other, ConcatAxis.fromValue(axis), JoinType.fromString(how), false);
    }

    /**
     * Concatenates this DataFrame with another DataFrame along the specified axis using the given join type name.
     *
     * @param other DataFrame to concatenate with
     * @param axis  axis along which to concatenate
     * @param how   join type name ("outer", "inner")
     * @return a new concatenated DataFrame
     */
    public IDataFrame concat(IDataFrame other, ConcatAxis axis, String how) {
        return concat(other, axis, JoinType.fromString(how), false);
    }

    /**
     * Concatenates this DataFrame with another DataFrame along rows with optional index resetting.
     *
     * @param other       DataFrame to concatenate with
     * @param ignoreIndex whether to reset the index
     * @return a new concatenated DataFrame
     */
    public IDataFrame concat(IDataFrame other, boolean ignoreIndex) {
        return concat(other, ConcatAxis.ROWS, JoinType.OUTER, ignoreIndex);
    }

    /**
     * Concatenates this DataFrame with another DataFrame with full control over axis, join type, and index resetting.
     *
     * @param other       DataFrame to concatenate with
     * @param axis        axis along which to concatenate
     * @param how         join type
     * @param ignoreIndex whether to reset the index
     * @return a new concatenated DataFrame
     */
    public IDataFrame concat(IDataFrame other, ConcatAxis axis, JoinType how, boolean ignoreIndex) {
        if (other == null) {
            throw new IllegalArgumentException("Other DataFrame cannot be null");
        }
        return DataFrameConcatenator.concat(List.of(this, other), axis, how, ignoreIndex);
    }

    /**
     * Concatenates this DataFrame with another DataFrame horizontally with custom suffixes for colliding columns.
     *
     * @param other       DataFrame to concatenate with
     * @param leftSuffix  suffix for colliding columns from this DataFrame
     * @param rightSuffix suffix for colliding columns from other DataFrame
     * @return a new concatenated DataFrame
     */
    public IDataFrame concatHorizontal(IDataFrame other, String leftSuffix, String rightSuffix) {
        return DataFrameConcatenator.concatHorizontal(this, other, leftSuffix, rightSuffix, JoinType.OUTER, false);
    }

    /**
     * Concatenates this DataFrame with multiple other DataFrames along rows.
     *
     * @param others other DataFrames to concatenate
     * @return a new concatenated DataFrame
     */
    public IDataFrame concatWith(IDataFrame... others) {
        List<IDataFrame> frames = new ArrayList<>();
        frames.add(this);
        if (others != null) {
            Collections.addAll(frames, others);
        }
        return DataFrameConcatenator.concat(frames, ConcatAxis.ROWS, JoinType.OUTER, false);
    }

    /**
     * Concatenates this DataFrame with a list of other DataFrames along rows.
     *
     * @param others other DataFrames to concatenate
     * @return a new concatenated DataFrame
     */
    public IDataFrame concatWith(List<? extends IDataFrame> others) {
        List<IDataFrame> frames = new ArrayList<>();
        frames.add(this);
        if (others != null) {
            frames.addAll(others);
        }
        return DataFrameConcatenator.concat(frames, ConcatAxis.ROWS, JoinType.OUTER, false);
    }

    // --- Static Concat Methods on IDataFrame ---

    public static IDataFrame concat(List<? extends IDataFrame> frames) {
        return DataFrameConcatenator.concat(frames, ConcatAxis.ROWS, JoinType.OUTER, false);
    }

    public static IDataFrame concat(List<? extends IDataFrame> frames, ConcatAxis axis) {
        return DataFrameConcatenator.concat(frames, axis, JoinType.OUTER, false);
    }

    public static IDataFrame concat(List<? extends IDataFrame> frames, int axis) {
        return DataFrameConcatenator.concat(frames, ConcatAxis.fromValue(axis), JoinType.OUTER, false);
    }

    public static IDataFrame concat(List<? extends IDataFrame> frames, JoinType how) {
        return DataFrameConcatenator.concat(frames, ConcatAxis.ROWS, how, false);
    }

    public static IDataFrame concat(List<? extends IDataFrame> frames, ConcatAxis axis, JoinType how) {
        return DataFrameConcatenator.concat(frames, axis, how, false);
    }

    public static IDataFrame concat(List<? extends IDataFrame> frames, ConcatAxis axis, JoinType how, boolean ignoreIndex) {
        return DataFrameConcatenator.concat(frames, axis, how, ignoreIndex);
    }

    public static IDataFrame concat(IDataFrame... frames) {
        return DataFrameConcatenator.concat(frames != null ? List.of(frames) : Collections.emptyList(), ConcatAxis.ROWS, JoinType.OUTER, false);
    }

    public static IDataFrame concat(ConcatAxis axis, IDataFrame... frames) {
        return DataFrameConcatenator.concat(frames != null ? List.of(frames) : Collections.emptyList(), axis, JoinType.OUTER, false);
    }

    public static IDataFrame concat(int axis, IDataFrame... frames) {
        return DataFrameConcatenator.concat(frames != null ? List.of(frames) : Collections.emptyList(), ConcatAxis.fromValue(axis), JoinType.OUTER, false);
    }

    public void setHeaders(ArrayList<Column> headers) {
        this.header.setHeader(headers);
    }

    public com.cupoftea.io.DataFrameWriter write() {
        return new com.cupoftea.io.DataFrameWriter(this);
    }

    // ==========================================
    // Window, Rolling, and Analytical Operations
    // ==========================================

    public com.cupoftea.table.window.RollingWindow rolling(int windowSize) {
        return new com.cupoftea.table.window.RollingWindow(this, windowSize);
    }

    public com.cupoftea.table.window.ExpandingWindow expanding() {
        return new com.cupoftea.table.window.ExpandingWindow(this, 1);
    }

    public com.cupoftea.table.window.ExpandingWindow expanding(int minPeriods) {
        return new com.cupoftea.table.window.ExpandingWindow(this, minPeriods);
    }

    public com.cupoftea.table.window.Window window() {
        return new com.cupoftea.table.window.Window(this);
    }

    public abstract IDataFrame cumsum(String... columns);
    public abstract IDataFrame cumprod(String... columns);
    public abstract IDataFrame cummin(String... columns);
    public abstract IDataFrame cummax(String... columns);

    public abstract IDataFrame lead(String column, int offset, String outputColumn);
    public IDataFrame lead(String column, int offset) {
        return lead(column, offset, column);
    }
    public IDataFrame lead(String column) {
        return lead(column, 1, column);
    }

    public abstract IDataFrame lag(String column, int offset, String outputColumn);
    public IDataFrame lag(String column, int offset) {
        return lag(column, offset, column);
    }
    public IDataFrame lag(String column) {
        return lag(column, 1, column);
    }

    public abstract IDataFrame diff(String column, int periods, String outputColumn);
    public IDataFrame diff(String column, int periods) {
        return diff(column, periods, column);
    }
    public IDataFrame diff(String column) {
        return diff(column, 1, column);
    }
    public abstract IDataFrame diff(String... columns);

    public abstract IDataFrame pctChange(String column, int periods, String outputColumn);
    public IDataFrame pctChange(String column, int periods) {
        return pctChange(column, periods, column);
    }
    public IDataFrame pctChange(String column) {
        return pctChange(column, 1, column);
    }
    public abstract IDataFrame pctChange(String... columns);

    public abstract IDataFrame rank(String column, String outputColumn);
    public IDataFrame rank(String column) {
        return rank(column, "rank");
    }

    public abstract IDataFrame denseRank(String column, String outputColumn);
    public IDataFrame denseRank(String column) {
        return denseRank(column, "dense_rank");
    }

    public abstract IDataFrame percentRank(String column, String outputColumn);
    public IDataFrame percentRank(String column) {
        return percentRank(column, "percent_rank");
    }

    public abstract IDataFrame rowNumber(String outputColumn);
    public IDataFrame rowNumber() {
        return rowNumber("row_number");
    }

    /**
     * Obtains a declarative plotting builder for visualizing this DataFrame.
     * Requires the optional {@code cupoftea-plot} module on the classpath.
     *
     * @return a {@link Plot} instance
     */
    public Plot plot() {
        return PlotRegistry.createPlot(this);
    }

    /**
     * Obtains a declarative plotting builder backed by a specific plotting engine.
     *
     * @param engine plotting engine name (e.g. "vegalite")
     * @return a {@link Plot} instance
     */
    public Plot plot(String engine) {
        return PlotRegistry.createPlot(this, engine);
    }

    /**
     * Renders this DataFrame as a clean, responsive HTML table with sticky headers,
     * data type indicators, and client-side pagination.
     *
     * @return HTML string representation
     */
    public String toHtml() {
        return toHtml(new TableHtmlConfiguration());
    }

    /**
     * Renders this DataFrame as an HTML table displaying up to {@code maxRows} rows.
     *
     * @param maxRows maximum number of rows to include
     * @return HTML string representation
     */
    public String toHtml(int maxRows) {
        return toHtml(TableHtmlConfiguration.builder().maxRows(maxRows).build());
    }

    /**
     * Renders this DataFrame as an HTML table using the specified configuration.
     *
     * @param configuration table HTML rendering configuration
     * @return HTML string representation
     */
    public String toHtml(TableHtmlConfiguration configuration) {
        return TableHtmlRenderer.render(this, configuration);
    }

    /**
     * Jupyter / IJava notebook MIME representation method.
     * Automatically invoked by Jupyter kernels when this DataFrame is evaluated in a cell.
     *
     * @return HTML table string
     */
    public String renderHtml() {
        return toHtml();
    }

    /**
     * Jupyter / Polyglot notebook MIME representation bundle.
     *
     * @return map mapping MIME type ("text/html") to HTML representation
     */
    public Map<String, String> render() {
        return Collections.singletonMap("text/html", toHtml());
    }
}
