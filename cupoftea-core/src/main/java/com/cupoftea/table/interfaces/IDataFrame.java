package com.cupoftea.table.interfaces;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.cupoftea.table.Column;
import com.cupoftea.table.ColumnRef;
import com.cupoftea.table.ConcatAxis;
import com.cupoftea.table.DataFrameConcatenator;
import com.cupoftea.table.Header;
import com.cupoftea.table.ILoc;
import com.cupoftea.table.Index;
import com.cupoftea.table.JoinType;
import com.cupoftea.table.Loc;
import com.cupoftea.table.Row;
import com.cupoftea.table.Schema;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.utils.interfaces.IColumnCreator;
import com.cupoftea.utils.interfaces.IFilter;
import com.cupoftea.utils.interfaces.Orderer;

public abstract class IDataFrame {

    protected Header header;
    protected ArrayList<HashMap<String, Object>> rows;
    protected String indexColumn;
    protected Index index;

    public IDataFrame() {
        this.rows = new ArrayList<>();
        this.header = new Header();
        this.indexColumn = null;
        this.index = new Index();
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
}
