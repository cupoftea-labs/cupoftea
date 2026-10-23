package com.cupoftea.table;

import java.io.Serializable;
import java.util.Objects;

import com.cupoftea.table.interfaces.IDataFrame;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.StringAccessor;
import com.cupoftea.table.vector.TemporalAccessor;

/**
 * Fluent reference to a DataFrame column providing ergonomic vectorized string (.str)
 * and temporal (.dt) operations.
 */
public class ColumnRef implements Serializable {

    private static final long serialVersionUID = 1L;

    private final IDataFrame df;
    private final ColumnVector vector;
    private final String columnName;

    public final StringAccessor str;
    public final TemporalAccessor dt;

    public ColumnRef(IDataFrame df, ColumnVector vector) {
        this.df = df;
        this.vector = Objects.requireNonNull(vector, "ColumnVector cannot be null");
        this.columnName = vector.getName();
        this.str = new StringAccessor(this);
        this.dt = new TemporalAccessor(this);
    }

    public ColumnRef(ColumnVector vector) {
        this(null, vector);
    }

    public StringAccessor str() {
        return this.str;
    }

    public TemporalAccessor dt() {
        return this.dt;
    }

    public IDataFrame getDataFrame() {
        return this.df;
    }

    public ColumnVector getVector() {
        return this.vector;
    }

    public String getName() {
        return this.columnName;
    }

    public Class<?> getType() {
        return this.vector.getType();
    }

    public int size() {
        return this.vector.size();
    }

    public boolean isNull(int row) {
        return this.vector.isNull(row);
    }

    public Object getObject(int row) {
        return this.vector.getObject(row);
    }

    public String getString(int row) {
        return this.vector.getString(row);
    }

    public int getInt(int row) {
        return this.vector.getInt(row);
    }

    public long getLong(int row) {
        return this.vector.getLong(row);
    }

    public double getDouble(int row) {
        return this.vector.getDouble(row);
    }

    public float getFloat(int row) {
        return this.vector.getFloat(row);
    }

    public boolean getBoolean(int row) {
        return this.vector.getBoolean(row);
    }

    @Override
    public String toString() {
        return "ColumnRef(name='" + columnName + "', type=" + (vector.getType() != null ? vector.getType().getSimpleName() : "Object") + ", size=" + vector.size() + ")";
    }
}
