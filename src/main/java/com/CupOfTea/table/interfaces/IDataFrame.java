package com.CupOfTea.table.interfaces;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import com.CupOfTea.table.Column;
import com.CupOfTea.table.Header;
import com.CupOfTea.table.Row;
import com.CupOfTea.table.Schema;
import com.CupOfTea.utils.interfaces.IColumnCreator;
import com.CupOfTea.utils.interfaces.IFilter;
import com.CupOfTea.utils.interfaces.Orderer;

public abstract class IDataFrame {

    protected Header header;
    protected ArrayList<HashMap<String, Object>> rows;

    public IDataFrame() {
        this.rows = new ArrayList<>();
        this.header = new Header();
    }

    /**
     * Returns an unmodifiable list of {@link Row} views.
     * Internal row storage cannot be mutated through this list or the returned rows.
     */
    public List<Row> getRows() {
        List<Row> rowViews = new ArrayList<>(this.rows.size());
        for (var rowMap : this.rows) {
            rowViews.add(new Row(rowMap));
        }
        return Collections.unmodifiableList(rowViews);
    }

    /**
     * Returns the unmodifiable {@link Row} at the specified index.
     */
    public Row getRow(int index) {
        return new Row(this.rows.get(index));
    }

    public ArrayList<HashMap<String, Object>> getInternalRows() {
        return this.rows;
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

    public abstract IDataFrame sum(String column);
    public abstract IDataFrame average(String column);
    public abstract IDataFrame maximum(String column);
    public abstract IDataFrame minimum(String column);
    public abstract IDataFrame count(String column);
    public abstract IDataFrame aggregate(String column, String separator);

    public Integer size() {
        return this.rows.size();
    }

    public void setHeaders(ArrayList<Column> headers) {
        this.header.setHeader(headers);
    }
}
