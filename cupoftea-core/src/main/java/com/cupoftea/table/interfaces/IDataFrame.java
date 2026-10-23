package com.cupoftea.table.interfaces;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.cupoftea.table.Column;
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

    public void setHeaders(ArrayList<Column> headers) {
        this.header.setHeader(headers);
    }

    public com.cupoftea.io.DataFrameWriter write() {
        return new com.cupoftea.io.DataFrameWriter(this);
    }
}
