package com.cupoftea.table;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.cupoftea.table.vector.ColumnVector;

/**
 * Holds group partition state and supports on-demand lazy group indexing and row-map materialization.
 */
public class GroupPartitionState {

    private final int numGroups;
    private final int[] firstRowOfGroup;
    private final int[] rowGroups;
    private final int totalRows;
    private final List<Column> sourceHeaders;
    private final ColumnVector[] allSourceVectors;

    // Lazily initialized for non-single-pass or row-map aggregations
    private int[][] groupIndices;
    private List<HashMap<String, Object>>[] lazyGroupRowMaps;

    public GroupPartitionState(
            int numGroups,
            int[] firstRowOfGroup,
            int[] rowGroups,
            int totalRows,
            List<Column> sourceHeaders,
            ColumnVector[] allSourceVectors) {
        this.numGroups = numGroups;
        this.firstRowOfGroup = firstRowOfGroup;
        this.rowGroups = rowGroups;
        this.totalRows = totalRows;
        this.sourceHeaders = sourceHeaders;
        this.allSourceVectors = allSourceVectors;
    }

    public int getNumGroups() {
        return numGroups;
    }

    public int[] getFirstRowOfGroup() {
        return firstRowOfGroup;
    }

    public int[] getRowGroups() {
        return rowGroups;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public synchronized int[][] getGroupIndices() {
        if (groupIndices == null) {
            int[][] indices = new int[numGroups][];
            int[] counts = new int[numGroups];
            for (int r = 0; r < totalRows; r++) {
                counts[rowGroups[r]]++;
            }
            for (int g = 0; g < numGroups; g++) {
                indices[g] = new int[counts[g]];
            }
            int[] pos = new int[numGroups];
            for (int r = 0; r < totalRows; r++) {
                int g = rowGroups[r];
                indices[g][pos[g]++] = r;
            }
            this.groupIndices = indices;
        }
        return groupIndices;
    }

    @SuppressWarnings("unchecked")
    public synchronized List<HashMap<String, Object>> getGroupRowMaps(int groupId) {
        if (lazyGroupRowMaps == null) {
            lazyGroupRowMaps = new List[numGroups];
        }
        if (lazyGroupRowMaps[groupId] == null) {
            int[] indices = getGroupIndices()[groupId];
            List<HashMap<String, Object>> list = new ArrayList<>(indices.length);
            for (int r : indices) {
                HashMap<String, Object> map = new HashMap<>(sourceHeaders.size());
                for (int c = 0; c < sourceHeaders.size(); c++) {
                    Column col = sourceHeaders.get(c);
                    ColumnVector vec = allSourceVectors[c];
                    map.put(col.columnName(), vec != null ? vec.getObject(r) : null);
                }
                list.add(map);
            }
            lazyGroupRowMaps[groupId] = list;
        }
        return lazyGroupRowMaps[groupId];
    }
}
