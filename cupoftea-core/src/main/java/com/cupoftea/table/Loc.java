package com.cupoftea.table;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import com.cupoftea.CupOfTea;
import com.cupoftea.table.interfaces.IDataFrame;

/**
 * Label/Index-based indexer for selection (similar to pandas loc).
 * <p>
 * Requires an index column to be designated via {@link CupOfTea#setIndex(String)}.
 */
public class Loc implements Serializable {

    private static final long serialVersionUID = 1L;

    private final CupOfTea cupOfTea;
    private final IDataFrame dataFrame;

    public Loc(CupOfTea cupOfTea, IDataFrame dataFrame) {
        this.cupOfTea = cupOfTea;
        this.dataFrame = dataFrame;
    }

    /**
     * Returns the {@link Row} matching the specified index key.
     */
    public Row row(Object key) {
        return this.dataFrame.loc(key);
    }

    /**
     * Alias for {@link #row(Object)}.
     */
    public Row get(Object key) {
        return row(key);
    }

    /**
     * Returns the cell value for the given index key and column name.
     */
    public Object cell(Object key, String column) {
        return this.dataFrame.loc(key, column);
    }

    /**
     * Alias for {@link #cell(Object, String)}.
     */
    public Object get(Object key, String column) {
        return cell(key, column);
    }

    /**
     * Returns a new DataFrame containing rows whose index value matches any of the specified keys.
     */
    public CupOfTea rows(Object... keys) {
        String indexCol = this.dataFrame.getIndex();
        if (indexCol == null) {
            throw new IllegalStateException("Cannot use loc without setting an index column. Call setIndex(columnName) first.");
        }
        Set<Object> keySet = new HashSet<>(Arrays.asList(keys));
        DataFrame result = new DataFrame();
        result.setHeaders(new ArrayList<>(this.dataFrame.getHeaders()));
        result.setIndex(indexCol);

        for (var rowMap : this.dataFrame.getInternalRows()) {
            if (keySet.contains(rowMap.get(indexCol))) {
                result.getInternalRows().add(new HashMap<>(rowMap));
            }
        }
        return wrap(result);
    }

    private CupOfTea wrap(IDataFrame df) {
        if (this.cupOfTea != null) {
            return new CupOfTea(df, this.cupOfTea.getMutationMode());
        }
        return new CupOfTea(df);
    }
}
