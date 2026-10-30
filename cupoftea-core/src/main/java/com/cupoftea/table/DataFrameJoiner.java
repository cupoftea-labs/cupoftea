package com.cupoftea.table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.cupoftea.exceptions.SchemaMismatchException;
import com.cupoftea.table.interfaces.IDataFrame;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.DictionaryColumnVector;

public final class DataFrameJoiner {

    private DataFrameJoiner() {
    }

    public static IDataFrame join(
            IDataFrame left,
            IDataFrame right,
            JoinType how,
            List<String> leftOn,
            List<String> rightOn,
            String leftSuffix,
            String rightSuffix) {

        if (left == null) {
            throw new IllegalArgumentException("Left DataFrame cannot be null");
        }
        if (right == null) {
            throw new IllegalArgumentException("Right DataFrame cannot be null");
        }

        JoinType joinType = (how != null) ? how : JoinType.INNER;
        String lSuffix = (leftSuffix != null) ? leftSuffix : "_left";
        String rSuffix = (rightSuffix != null) ? rightSuffix : "_right";

        List<String> resolvedLeftOn;
        List<String> resolvedRightOn;

        if (joinType == JoinType.CROSS) {
            resolvedLeftOn = Collections.emptyList();
            resolvedRightOn = Collections.emptyList();
        } else {
            if ((leftOn == null || leftOn.isEmpty()) && (rightOn == null || rightOn.isEmpty())) {
                List<String> common = findCommonColumns(left, right);
                if (common.isEmpty()) {
                    if (left.getIndex() != null && right.getIndex() != null) {
                        resolvedLeftOn = Collections.singletonList(left.getIndex());
                        resolvedRightOn = Collections.singletonList(right.getIndex());
                    } else {
                        throw new IllegalArgumentException(
                            "No common columns found between DataFrames. Specify join column(s) explicitly using on or leftOn/rightOn, or use crossJoin().");
                    }
                } else {
                    resolvedLeftOn = common;
                    resolvedRightOn = common;
                }
            } else if (leftOn != null && !leftOn.isEmpty() && (rightOn == null || rightOn.isEmpty())) {
                resolvedLeftOn = leftOn;
                resolvedRightOn = leftOn;
            } else if (rightOn != null && !rightOn.isEmpty() && (leftOn == null || leftOn.isEmpty())) {
                resolvedLeftOn = rightOn;
                resolvedRightOn = rightOn;
            } else {
                resolvedLeftOn = leftOn;
                resolvedRightOn = rightOn;
            }

            if (resolvedLeftOn.size() != resolvedRightOn.size()) {
                throw new IllegalArgumentException(
                    "leftOn and rightOn must have the same number of columns, got "
                    + resolvedLeftOn.size() + " and " + resolvedRightOn.size());
            }

            for (String col : resolvedLeftOn) {
                if (left.getColumnHeader(col) == null) {
                    throw new SchemaMismatchException("Column '" + col + "' does not exist in left DataFrame");
                }
            }
            for (String col : resolvedRightOn) {
                if (right.getColumnHeader(col) == null) {
                    throw new SchemaMismatchException("Column '" + col + "' does not exist in right DataFrame");
                }
            }
        }

        return executeJoin(left, right, joinType, resolvedLeftOn, resolvedRightOn, lSuffix, rSuffix);
    }

    private static List<String> findCommonColumns(IDataFrame left, IDataFrame right) {
        Set<String> rightCols = new HashSet<>();
        if (right.getHeaders() != null) {
            for (Column c : right.getHeaders()) {
                rightCols.add(c.columnName());
            }
        }
        List<String> common = new ArrayList<>();
        if (left.getHeaders() != null) {
            for (Column c : left.getHeaders()) {
                if (rightCols.contains(c.columnName())) {
                    common.add(c.columnName());
                }
            }
        }
        return common;
    }

    private static IDataFrame executeJoin(
            IDataFrame left,
            IDataFrame right,
            JoinType joinType,
            List<String> leftOn,
            List<String> rightOn,
            String lSuffix,
            String rSuffix) {

        List<Column> leftHeaders = left.getHeaders() != null ? left.getHeaders() : Collections.emptyList();
        List<Column> rightHeaders = right.getHeaders() != null ? right.getHeaders() : Collections.emptyList();

        Set<String> sharedRightKeys = new HashSet<>();
        if (joinType != JoinType.CROSS) {
            for (int i = 0; i < leftOn.size(); i++) {
                String lCol = leftOn.get(i);
                String rCol = rightOn.get(i);
                if (lCol.equals(rCol)) {
                    sharedRightKeys.add(rCol);
                }
            }
        }

        Set<String> leftColNames = new HashSet<>();
        for (Column col : leftHeaders) {
            leftColNames.add(col.columnName());
        }

        Set<String> rightColNames = new HashSet<>();
        for (Column col : rightHeaders) {
            rightColNames.add(col.columnName());
        }

        Set<String> collidingColNames = new HashSet<>();
        for (String colName : leftColNames) {
            if (rightColNames.contains(colName) && !sharedRightKeys.contains(colName)) {
                collidingColNames.add(colName);
            }
        }

        Map<String, String> leftOutMap = new LinkedHashMap<>();
        for (Column col : leftHeaders) {
            String name = col.columnName();
            if (collidingColNames.contains(name)) {
                leftOutMap.put(name, name + lSuffix);
            } else {
                leftOutMap.put(name, name);
            }
        }

        Map<String, String> rightOutMap = new LinkedHashMap<>();
        for (Column col : rightHeaders) {
            String name = col.columnName();
            if (sharedRightKeys.contains(name)) {
                rightOutMap.put(name, leftOutMap.get(name));
            } else if (collidingColNames.contains(name)) {
                rightOutMap.put(name, name + rSuffix);
            } else {
                rightOutMap.put(name, name);
            }
        }

        ArrayList<Column> outputHeaders = new ArrayList<>();
        Set<String> seenOutputNames = new HashSet<>();

        // Add left columns
        for (Column col : leftHeaders) {
            String outName = leftOutMap.get(col.columnName());
            if (!seenOutputNames.add(outName)) {
                throw new IllegalArgumentException("Duplicate column name in joined DataFrame: '" + outName + "'");
            }
            Class<?> colType = col.columnType();
            if (colType == null && joinType != JoinType.CROSS && leftOn.contains(col.columnName())) {
                int idx = leftOn.indexOf(col.columnName());
                Column rCol = right.getColumnHeader(rightOn.get(idx));
                if (rCol != null && rCol.columnType() != null) {
                    colType = rCol.columnType();
                }
            }
            boolean nullable = col.nullable() || joinType == JoinType.RIGHT || joinType == JoinType.OUTER;
            outputHeaders.add(new Column(outName, colType, nullable));
        }

        // Add right columns (skip shared keys)
        for (Column col : rightHeaders) {
            if (sharedRightKeys.contains(col.columnName())) {
                continue;
            }
            String outName = rightOutMap.get(col.columnName());
            if (!seenOutputNames.add(outName)) {
                throw new IllegalArgumentException("Duplicate column name in joined DataFrame: '" + outName + "'");
            }
            boolean nullable = col.nullable() || joinType == JoinType.LEFT || joinType == JoinType.OUTER;
            outputHeaders.add(new Column(outName, col.columnType(), nullable));
        }

        DataFrame result = new DataFrame();
        result.setHeaders(outputHeaders);

        int outputSize = outputHeaders.size();

        if (joinType == JoinType.CROSS) {
            for (Row leftRow : left.getRows()) {
                for (Row rightRow : right.getRows()) {
                    HashMap<String, Object> merged = mergeRows(
                        leftRow, rightRow, leftHeaders, rightHeaders, leftOutMap, rightOutMap, sharedRightKeys, outputSize);
                    result.addRow(merged);
                }
            }
        } else {
            JoinKeyExtractor keyExtractor = new JoinKeyExtractor(left, right, leftOn, rightOn);

            if (joinType == JoinType.INNER) {
                Map<JoinKey, List<Row>> rightIndex = new HashMap<>(right.size());
                List<Row> rightRows = right.getRows();
                for (int r = 0; r < rightRows.size(); r++) {
                    Row rightRow = rightRows.get(r);
                    JoinKey key = keyExtractor.extractRightKey(rightRow, r);
                    if (!key.hasNA()) {
                        rightIndex.computeIfAbsent(key, k -> new ArrayList<>()).add(rightRow);
                    }
                }

                List<Row> leftRows = left.getRows();
                for (int l = 0; l < leftRows.size(); l++) {
                    Row leftRow = leftRows.get(l);
                    JoinKey key = keyExtractor.extractLeftKey(leftRow, l);
                    if (key.hasNA()) {
                        continue;
                    }
                    List<Row> matches = rightIndex.get(key);
                    if (matches != null) {
                        for (Row match : matches) {
                            result.addRow(mergeRows(
                                leftRow, match, leftHeaders, rightHeaders, leftOutMap, rightOutMap, sharedRightKeys, outputSize));
                        }
                    }
                }
            } else if (joinType == JoinType.LEFT) {
                Map<JoinKey, List<Row>> rightIndex = new HashMap<>(right.size());
                List<Row> rightRows = right.getRows();
                for (int r = 0; r < rightRows.size(); r++) {
                    Row rightRow = rightRows.get(r);
                    JoinKey key = keyExtractor.extractRightKey(rightRow, r);
                    if (!key.hasNA()) {
                        rightIndex.computeIfAbsent(key, k -> new ArrayList<>()).add(rightRow);
                    }
                }

                List<Row> leftRows = left.getRows();
                for (int l = 0; l < leftRows.size(); l++) {
                    Row leftRow = leftRows.get(l);
                    JoinKey key = keyExtractor.extractLeftKey(leftRow, l);
                    List<Row> matches = !key.hasNA() ? rightIndex.get(key) : null;
                    if (matches != null && !matches.isEmpty()) {
                        for (Row match : matches) {
                            result.addRow(mergeRows(
                                leftRow, match, leftHeaders, rightHeaders, leftOutMap, rightOutMap, sharedRightKeys, outputSize));
                        }
                    } else {
                        result.addRow(mergeLeftWithNullRight(
                            leftRow, leftHeaders, rightHeaders, leftOutMap, rightOutMap, sharedRightKeys, outputSize));
                    }
                }
            } else if (joinType == JoinType.RIGHT) {
                Map<JoinKey, List<Row>> leftIndex = new HashMap<>(left.size());
                List<Row> leftRows = left.getRows();
                for (int l = 0; l < leftRows.size(); l++) {
                    Row leftRow = leftRows.get(l);
                    JoinKey key = keyExtractor.extractLeftKey(leftRow, l);
                    if (!key.hasNA()) {
                        leftIndex.computeIfAbsent(key, k -> new ArrayList<>()).add(leftRow);
                    }
                }

                List<Row> rightRows = right.getRows();
                for (int r = 0; r < rightRows.size(); r++) {
                    Row rightRow = rightRows.get(r);
                    JoinKey key = keyExtractor.extractRightKey(rightRow, r);
                    List<Row> matches = !key.hasNA() ? leftIndex.get(key) : null;
                    if (matches != null && !matches.isEmpty()) {
                        for (Row match : matches) {
                            result.addRow(mergeRows(
                                match, rightRow, leftHeaders, rightHeaders, leftOutMap, rightOutMap, sharedRightKeys, outputSize));
                        }
                    } else {
                        result.addRow(mergeNullLeftWithRight(
                            rightRow, leftHeaders, rightHeaders, leftOutMap, rightOutMap, sharedRightKeys, leftOn, rightOn, outputSize));
                    }
                }
            } else if (joinType == JoinType.OUTER) {
                Set<Integer> matchedRightRowIndices = new HashSet<>();
                Map<JoinKey, List<IndexedRow>> rightIndexedMap = new HashMap<>(right.size());
                for (int i = 0; i < right.size(); i++) {
                    Row rightRow = right.getRow(i);
                    JoinKey key = keyExtractor.extractRightKey(rightRow, i);
                    if (!key.hasNA()) {
                        rightIndexedMap.computeIfAbsent(key, k -> new ArrayList<>()).add(new IndexedRow(i, rightRow));
                    }
                }

                List<Row> leftRows = left.getRows();
                for (int l = 0; l < leftRows.size(); l++) {
                    Row leftRow = leftRows.get(l);
                    JoinKey key = keyExtractor.extractLeftKey(leftRow, l);
                    List<IndexedRow> matches = !key.hasNA() ? rightIndexedMap.get(key) : null;
                    if (matches != null && !matches.isEmpty()) {
                        for (IndexedRow match : matches) {
                            matchedRightRowIndices.add(match.index);
                            result.addRow(mergeRows(
                                leftRow, match.row, leftHeaders, rightHeaders, leftOutMap, rightOutMap, sharedRightKeys, outputSize));
                        }
                    } else {
                        result.addRow(mergeLeftWithNullRight(
                            leftRow, leftHeaders, rightHeaders, leftOutMap, rightOutMap, sharedRightKeys, outputSize));
                    }
                }

                for (int i = 0; i < right.size(); i++) {
                    if (!matchedRightRowIndices.contains(i)) {
                        result.addRow(mergeNullLeftWithRight(
                            right.getRow(i), leftHeaders, rightHeaders, leftOutMap, rightOutMap, sharedRightKeys, leftOn, rightOn, outputSize));
                    }
                }
            }
        }

        if (left.getIndex() != null && leftOutMap.containsKey(left.getIndex())) {
            result.setIndex(leftOutMap.get(left.getIndex()));
        }

        return result;
    }

    private static HashMap<String, Object> mergeRows(
            Row leftRow,
            Row rightRow,
            List<Column> leftHeaders,
            List<Column> rightHeaders,
            Map<String, String> leftOutMap,
            Map<String, String> rightOutMap,
            Set<String> sharedRightKeys,
            int outputSize) {

        HashMap<String, Object> merged = new HashMap<>(outputSize);
        for (Column col : leftHeaders) {
            merged.put(leftOutMap.get(col.columnName()), leftRow.get(col.columnName()));
        }
        for (Column col : rightHeaders) {
            if (!sharedRightKeys.contains(col.columnName())) {
                merged.put(rightOutMap.get(col.columnName()), rightRow.get(col.columnName()));
            }
        }
        return merged;
    }

    private static HashMap<String, Object> mergeLeftWithNullRight(
            Row leftRow,
            List<Column> leftHeaders,
            List<Column> rightHeaders,
            Map<String, String> leftOutMap,
            Map<String, String> rightOutMap,
            Set<String> sharedRightKeys,
            int outputSize) {

        HashMap<String, Object> merged = new HashMap<>(outputSize);
        for (Column col : leftHeaders) {
            merged.put(leftOutMap.get(col.columnName()), leftRow.get(col.columnName()));
        }
        for (Column col : rightHeaders) {
            if (!sharedRightKeys.contains(col.columnName())) {
                merged.put(rightOutMap.get(col.columnName()), null);
            }
        }
        return merged;
    }

    private static HashMap<String, Object> mergeNullLeftWithRight(
            Row rightRow,
            List<Column> leftHeaders,
            List<Column> rightHeaders,
            Map<String, String> leftOutMap,
            Map<String, String> rightOutMap,
            Set<String> sharedRightKeys,
            List<String> leftOn,
            List<String> rightOn,
            int outputSize) {

        HashMap<String, Object> merged = new HashMap<>(outputSize);
        for (Column col : leftHeaders) {
            String lName = col.columnName();
            String outName = leftOutMap.get(lName);
            int keyIdx = leftOn.indexOf(lName);
            if (keyIdx >= 0 && rightOn.get(keyIdx).equals(lName)) {
                String rKeyName = rightOn.get(keyIdx);
                merged.put(outName, rightRow.get(rKeyName));
            } else {
                merged.put(outName, null);
            }
        }
        for (Column col : rightHeaders) {
            if (!sharedRightKeys.contains(col.columnName())) {
                merged.put(rightOutMap.get(col.columnName()), rightRow.get(col.columnName()));
            }
        }
        return merged;
    }

    /**
     * Extracts join keys from left and right rows, accelerating dictionary-encoded columns by mapping
     * right dictionary codes into left dictionary code space so join probes compare primitive integer codes
     * instead of hashing and comparing strings.
     */
    private static final class JoinKeyExtractor {
        private final List<String> leftOn;
        private final List<String> rightOn;
        private final DictionaryColumnVector[] leftDicts;
        private final DictionaryColumnVector[] rightDicts;
        private final int[][] rightToUnifiedCode;

        JoinKeyExtractor(IDataFrame left, IDataFrame right, List<String> leftOn, List<String> rightOn) {
            this.leftOn = leftOn;
            this.rightOn = rightOn;
            int n = leftOn.size();
            this.leftDicts = new DictionaryColumnVector[n];
            this.rightDicts = new DictionaryColumnVector[n];
            this.rightToUnifiedCode = new int[n][];

            for (int i = 0; i < n; i++) {
                ColumnVector lVec = left.getColumnVector(leftOn.get(i));
                ColumnVector rVec = right.getColumnVector(rightOn.get(i));
                if (lVec instanceof DictionaryColumnVector lDict && rVec instanceof DictionaryColumnVector rDict) {
                    this.leftDicts[i] = lDict;
                    this.rightDicts[i] = rDict;
                    int lDictSize = lDict.getDictionarySize();
                    int rDictSize = rDict.getDictionarySize();
                    int[] codeMap = new int[rDictSize];
                    for (int rCode = 0; rCode < rDictSize; rCode++) {
                        String cat = rDict.getCategory(rCode);
                        int lCode = lDict.indexOfCategory(cat);
                        codeMap[rCode] = (lCode >= 0) ? lCode : (lDictSize + rCode);
                    }
                    this.rightToUnifiedCode[i] = codeMap;
                }
            }
        }

        JoinKey extractLeftKey(Row leftRow, int rowIndex) {
            int n = leftOn.size();
            Object[] values = new Object[n];
            for (int i = 0; i < n; i++) {
                DictionaryColumnVector lDict = leftDicts[i];
                if (lDict != null && rowIndex < lDict.size()) {
                    int code = lDict.getCode(rowIndex);
                    values[i] = code < 0 ? null : Integer.valueOf(code);
                } else {
                    values[i] = leftRow.get(leftOn.get(i));
                }
            }
            return new JoinKey(values);
        }

        JoinKey extractRightKey(Row rightRow, int rowIndex) {
            int n = rightOn.size();
            Object[] values = new Object[n];
            for (int i = 0; i < n; i++) {
                DictionaryColumnVector rDict = rightDicts[i];
                if (rDict != null && rowIndex < rDict.size()) {
                    int rCode = rDict.getCode(rowIndex);
                    values[i] = rCode < 0 ? null : Integer.valueOf(rightToUnifiedCode[i][rCode]);
                } else {
                    values[i] = rightRow.get(rightOn.get(i));
                }
            }
            return new JoinKey(values);
        }
    }

    private static Object canonicalKey(Object val) {
        if (NA.isNA(val)) {
            return null;
        }
        if (val instanceof Number n) {
            double d = n.doubleValue();
            if (!Double.isNaN(d) && !Double.isInfinite(d) && d == (long) d) {
                return Long.valueOf(n.longValue());
            }
            return Double.valueOf(d);
        }
        return val;
    }

    private static boolean keysEqual(Object a, Object b) {
        if (NA.isNA(a) || NA.isNA(b)) {
            return false;
        }
        Object ca = canonicalKey(a);
        Object cb = canonicalKey(b);
        return Objects.equals(ca, cb);
    }

    private static final class JoinKey {
        private final Object[] values;
        private final boolean hasNA;
        private final int hashCode;

        JoinKey(Object[] values) {
            this.values = values;
            boolean na = false;
            int hash = 1;
            for (Object val : values) {
                if (NA.isNA(val)) {
                    na = true;
                    break;
                }
                Object canonical = canonicalKey(val);
                hash = 31 * hash + (canonical != null ? canonical.hashCode() : 0);
            }
            this.hasNA = na;
            this.hashCode = hash;
        }

        boolean hasNA() {
            return hasNA;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            JoinKey other = (JoinKey) o;
            if (this.hasNA || other.hasNA) return false;
            if (this.values.length != other.values.length) return false;
            for (int i = 0; i < values.length; i++) {
                if (!keysEqual(this.values[i], other.values[i])) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    private record IndexedRow(int index, Row row) {
    }
}
