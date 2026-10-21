package com.cupoftea.table;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.cupoftea.CupOfTea;
import com.cupoftea.table.interfaces.IDataFrame;

/**
 * Pure integer-location based indexer for selection by position (similar to pandas iloc).
 * <p>
 * Supports both 0-based indexing and negative index wrapping (-1 refers to the last element).
 */
public class ILoc implements Serializable {

    private static final long serialVersionUID = 1L;

    private final CupOfTea cupOfTea;
    private final IDataFrame dataFrame;

    public ILoc(CupOfTea cupOfTea, IDataFrame dataFrame) {
        this.cupOfTea = cupOfTea;
        this.dataFrame = dataFrame;
    }

    /**
     * Returns the {@link Row} at the specified integer position.
     * Supports negative indexing (-1 is the last row).
     */
    public Row row(int index) {
        return this.dataFrame.iloc(index);
    }

    /**
     * Alias for {@link #row(int)}.
     */
    public Row get(int index) {
        return row(index);
    }

    /**
     * Returns the cell value at the specified row and column indices.
     * Supports negative indexing for both row and column.
     */
    public Object cell(int rowIndex, int colIndex) {
        return this.dataFrame.iloc(rowIndex, colIndex);
    }

    /**
     * Alias for {@link #cell(int, int)}.
     */
    public Object get(int rowIndex, int colIndex) {
        return cell(rowIndex, colIndex);
    }

    /**
     * Returns the cell value at the specified row index for the named column.
     * Supports negative row indexing.
     */
    public Object cell(int rowIndex, String columnName) {
        return this.dataFrame.iloc(rowIndex, columnName);
    }

    /**
     * Alias for {@link #cell(int, String)}.
     */
    public Object get(int rowIndex, String columnName) {
        return cell(rowIndex, columnName);
    }

    /**
     * Returns a new DataFrame slice containing rows from {@code fromRow} (inclusive)
     * to {@code toRow} (exclusive). Supports negative indexing.
     */
    public CupOfTea slice(int fromRow, int toRow) {
        IDataFrame sliced = this.dataFrame.slice(fromRow, toRow);
        return wrap(sliced);
    }

    /**
     * Returns a new 2D DataFrame slice containing rows [fromRow, toRow) and columns [fromCol, toCol).
     * Supports negative indexing.
     */
    public CupOfTea slice(int fromRow, int toRow, int fromCol, int toCol) {
        IDataFrame sliced = this.dataFrame.slice(fromRow, toRow);
        int totalCols = sliced.getHeaders().size();
        int rFromCol = fromCol < 0 ? totalCols + fromCol : fromCol;
        int rToCol = toCol < 0 ? totalCols + toCol : toCol;

        rFromCol = Math.max(0, Math.min(rFromCol, totalCols));
        rToCol = Math.max(rFromCol, Math.min(rToCol, totalCols));

        List<String> cols = new ArrayList<>();
        for (int i = rFromCol; i < rToCol; i++) {
            cols.add(sliced.getHeaders().get(i).columnName());
        }
        sliced.select(cols);
        return wrap(sliced);
    }

    /**
     * Returns a new DataFrame containing only the specified rows in the given order.
     * Supports negative indexing.
     */
    public CupOfTea rows(int... rowIndices) {
        DataFrame result = new DataFrame();
        result.setHeaders(new ArrayList<>(this.dataFrame.getHeaders()));
        int total = this.dataFrame.size();
        for (int idx : rowIndices) {
            int rIdx = idx < 0 ? total + idx : idx;
            if (rIdx < 0 || rIdx >= total) {
                throw new IndexOutOfBoundsException("Row index " + idx + " out of bounds for length " + total);
            }
            result.getInternalRows().add(new HashMap<>(this.dataFrame.getInternalRows().get(rIdx)));
        }
        return wrap(result);
    }

    /**
     * Returns a new DataFrame containing only the specified columns in the given order.
     * Supports negative indexing.
     */
    public CupOfTea columns(int... colIndices) {
        int totalCols = this.dataFrame.getHeaders().size();
        List<String> colNames = new ArrayList<>();
        for (int idx : colIndices) {
            int rIdx = idx < 0 ? totalCols + idx : idx;
            if (rIdx < 0 || rIdx >= totalCols) {
                throw new IndexOutOfBoundsException("Column index " + idx + " out of bounds for length " + totalCols);
            }
            colNames.add(this.dataFrame.getHeaders().get(rIdx).columnName());
        }
        IDataFrame copy = this.dataFrame.copy();
        copy.select(colNames);
        return wrap(copy);
    }

    private CupOfTea wrap(IDataFrame df) {
        if (this.cupOfTea != null) {
            return new CupOfTea(df, this.cupOfTea.getMutationMode());
        }
        return new CupOfTea(df);
    }
}
