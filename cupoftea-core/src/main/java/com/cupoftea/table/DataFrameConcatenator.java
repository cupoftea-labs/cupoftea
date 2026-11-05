package com.cupoftea.table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.cupoftea.table.interfaces.IDataFrame;
import com.cupoftea.table.interfaces.IDataTypeConverter;
import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.DictionaryColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.FloatColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.ObjectColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

/**
 * High-performance columnar concatenation engine for CupOfTea DataFrames.
 * Supports vertical (axis=0, row stacking) and horizontal (axis=1, column stacking) concatenation
 * with outer and inner join semantics, type widening, and missing value imputation.
 */
public final class DataFrameConcatenator {

    private DataFrameConcatenator() {
    }

    /**
     * Concatenates multiple DataFrames along the given axis with the specified join type and index handling.
     *
     * @param frames      list of DataFrames to concatenate
     * @param axis        axis along which to concatenate (ROWS / axis=0 or COLUMNS / axis=1)
     * @param joinType    join type for non-concatenation dimension (OUTER or INNER)
     * @param ignoreIndex whether to reset the index in the resulting DataFrame
     * @return a new concatenated DataFrame
     */
    public static IDataFrame concat(
            List<? extends IDataFrame> frames,
            ConcatAxis axis,
            JoinType joinType,
            boolean ignoreIndex) {

        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("DataFrames list cannot be null or empty");
        }

        for (int i = 0; i < frames.size(); i++) {
            if (frames.get(i) == null) {
                throw new IllegalArgumentException("DataFrame at index " + i + " cannot be null");
            }
        }

        ConcatAxis resolvedAxis = axis != null ? axis : ConcatAxis.ROWS;
        JoinType resolvedJoin = joinType != null ? joinType : JoinType.OUTER;

        if (frames.size() == 1) {
            IDataFrame singleCopy = frames.get(0).copy();
            if (ignoreIndex) {
                singleCopy.resetIndex();
            }
            return singleCopy;
        }

        if (resolvedAxis == ConcatAxis.ROWS) {
            return concatVertical(frames, resolvedJoin, ignoreIndex);
        } else {
            return concatHorizontalMultiple(frames, resolvedJoin, ignoreIndex, "_x", "_y");
        }
    }

    /**
     * Concatenates two DataFrames horizontally with custom column disambiguation suffixes.
     */
    public static IDataFrame concatHorizontal(
            IDataFrame left,
            IDataFrame right,
            String leftSuffix,
            String rightSuffix,
            JoinType joinType,
            boolean ignoreIndex) {

        if (left == null) {
            throw new IllegalArgumentException("Left DataFrame cannot be null");
        }
        if (right == null) {
            throw new IllegalArgumentException("Right DataFrame cannot be null");
        }

        String lSuffix = leftSuffix != null ? leftSuffix : "_left";
        String rSuffix = rightSuffix != null ? rightSuffix : "_right";
        JoinType resolvedJoin = joinType != null ? joinType : JoinType.OUTER;

        List<IDataFrame> frames = List.of(left, right);
        return concatHorizontalTwo(left, right, lSuffix, rSuffix, resolvedJoin, ignoreIndex);
    }

    // --- Vertical Concatenation (axis = 0 / ROWS) ---

    private static IDataFrame concatVertical(
            List<? extends IDataFrame> frames,
            JoinType joinType,
            boolean ignoreIndex) {

        // 1. Determine columns based on join type
        List<String> orderedColNames = resolveVerticalColumnNames(frames, joinType);

        if (orderedColNames.isEmpty()) {
            return new DataFrame();
        }

        // 2. Resolve column types and nullability
        List<Column> outputHeaders = new ArrayList<>(orderedColNames.size());
        for (String colName : orderedColNames) {
            Class<?> resolvedType = null;
            boolean nullable = false;

            for (IDataFrame frame : frames) {
                Column col = frame.getColumnHeader(colName);
                if (col == null) {
                    if (frame.size() > 0) {
                        nullable = true;
                    }
                } else {
                    if (col.isNullable()) {
                        nullable = true;
                    }
                    resolvedType = resolveCompatibleType(resolvedType, col.columnType());
                }
            }

            if (resolvedType == null) {
                resolvedType = Object.class;
            }

            outputHeaders.add(new Column(colName, resolvedType, nullable));
        }

        // 3. Precalculate total row count
        int totalRows = 0;
        for (IDataFrame frame : frames) {
            totalRows += frame.size();
        }

        // 4. Create result DataFrame and allocate column vectors
        DataFrame result = new DataFrame();
        List<ColumnVector> vectors = new ArrayList<>(outputHeaders.size());

        for (int colIdx = 0; colIdx < outputHeaders.size(); colIdx++) {
            Column outCol = outputHeaders.get(colIdx);
            ColumnVector vec = tryConcatColumnVectorDirect(frames, outCol, totalRows);
            if (vec == null) {
                vec = ColumnVector.create(outCol.columnName(), outCol.columnType(), Math.max(16, totalRows), outCol.nullable());
                Class<?> targetType = vec.getType();
                for (IDataFrame frame : frames) {
                    int frameRows = frame.size();
                    if (frameRows == 0) continue;
                    ColumnVector srcVec = frame.getColumnVector(outCol.columnName());
                    if (srcVec == null) {
                        for (int r = 0; r < frameRows; r++) {
                            vec.appendNull();
                        }
                    } else {
                        for (int r = 0; r < frameRows; r++) {
                            if (srcVec.isNull(r)) {
                                vec.appendNull();
                            } else {
                                Object val = srcVec.getObject(r);
                                if (NA.isNA(val)) {
                                    vec.appendNull();
                                } else {
                                    appendConvertedValue(vec, targetType, val);
                                }
                            }
                        }
                    }
                }
            }
            vectors.add(vec);
        }

        result.setHeaders(new ArrayList<>(outputHeaders));
        result.columns = vectors;

        // 6. Index preservation
        if (!ignoreIndex) {
            String candidateIndex = null;
            boolean indexConsistent = true;

            for (IDataFrame frame : frames) {
                String frameIndex = frame.getIndex();
                if (frameIndex != null) {
                    if (candidateIndex == null) {
                        candidateIndex = frameIndex;
                    } else if (!candidateIndex.equals(frameIndex)) {
                        indexConsistent = false;
                        break;
                    }
                }
            }

            if (indexConsistent && candidateIndex != null && orderedColNames.contains(candidateIndex)) {
                result.setIndex(candidateIndex);
            }
        }

        return result;
    }

    private static List<String> resolveVerticalColumnNames(
            List<? extends IDataFrame> frames,
            JoinType joinType) {

        if (joinType == JoinType.INNER) {
            // Columns present in ALL frames
            Set<String> common = null;
            for (IDataFrame frame : frames) {
                Set<String> frameCols = new LinkedHashSet<>();
                if (frame.getHeaders() != null) {
                    for (Column col : frame.getHeaders()) {
                        frameCols.add(col.columnName());
                    }
                }
                if (common == null) {
                    common = frameCols;
                } else {
                    common.retainAll(frameCols);
                }
                if (common.isEmpty()) {
                    break;
                }
            }
            return common != null ? new ArrayList<>(common) : Collections.emptyList();
        } else {
            // OUTER: Union of all columns in order of appearance
            Set<String> seen = new LinkedHashSet<>();
            for (IDataFrame frame : frames) {
                if (frame.getHeaders() != null) {
                    for (Column col : frame.getHeaders()) {
                        seen.add(col.columnName());
                    }
                }
            }
            return new ArrayList<>(seen);
        }
    }

    // --- Horizontal Concatenation (axis = 1 / COLUMNS) ---

    private static IDataFrame concatHorizontalMultiple(
            List<? extends IDataFrame> frames,
            JoinType joinType,
            boolean ignoreIndex,
            String defaultLeftSuffix,
            String defaultRightSuffix) {

        if (frames.size() == 2) {
            return concatHorizontalTwo(
                frames.get(0), frames.get(1), defaultLeftSuffix, defaultRightSuffix, joinType, ignoreIndex);
        }

        int targetRows;
        if (joinType == JoinType.INNER) {
            targetRows = frames.stream().mapToInt(IDataFrame::size).min().orElse(0);
        } else {
            targetRows = frames.stream().mapToInt(IDataFrame::size).max().orElse(0);
        }

        // Count occurrences of column names to know which need suffixes
        Map<String, Integer> colCounts = new HashMap<>();
        for (IDataFrame frame : frames) {
            if (frame.getHeaders() != null) {
                for (Column col : frame.getHeaders()) {
                    colCounts.merge(col.columnName(), 1, Integer::sum);
                }
            }
        }

        List<Column> outputHeaders = new ArrayList<>();
        List<ColumnVector> vectors = new ArrayList<>();
        Map<String, Integer> colOccurrence = new HashMap<>();

        for (int frameIdx = 0; frameIdx < frames.size(); frameIdx++) {
            IDataFrame frame = frames.get(frameIdx);
            int frameRows = frame.size();

            if (frame.getHeaders() != null) {
                for (Column col : frame.getHeaders()) {
                    String originalName = col.columnName();
                    String outName = originalName;

                    if (colCounts.getOrDefault(originalName, 0) > 1) {
                        int occ = colOccurrence.getOrDefault(originalName, 0);
                        outName = originalName + "_" + occ;
                        colOccurrence.put(originalName, occ + 1);
                    }

                    boolean isNullable = col.isNullable() || (frameRows < targetRows);
                    Column outCol = new Column(outName, col.columnType(), isNullable);
                    outputHeaders.add(outCol);

                    ColumnVector vec = ColumnVector.create(
                        outCol.columnName(), outCol.columnType(), Math.max(16, targetRows), outCol.nullable());
                    ColumnVector srcVec = frame.getColumnVector(originalName);

                    int rowsToCopy = Math.min(frameRows, targetRows);
                    if (srcVec != null) {
                        for (int r = 0; r < rowsToCopy; r++) {
                            if (srcVec.isNull(r)) {
                                vec.appendNull();
                            } else {
                                Object val = srcVec.getObject(r);
                                if (NA.isNA(val)) {
                                    vec.appendNull();
                                } else {
                                    appendConvertedValue(vec, outCol.columnType(), val);
                                }
                            }
                        }
                    } else {
                        for (int r = 0; r < rowsToCopy; r++) {
                            vec.appendNull();
                        }
                    }

                    // Pad remaining rows with null if shorter than targetRows
                    for (int r = rowsToCopy; r < targetRows; r++) {
                        vec.appendNull();
                    }

                    vectors.add(vec);
                }
            }
        }

        DataFrame result = new DataFrame();
        result.setHeaders(new ArrayList<>(outputHeaders));
        result.columns = vectors;

        if (!ignoreIndex && !frames.isEmpty()) {
            String firstIndex = frames.get(0).getIndex();
            if (firstIndex != null) {
                for (Column col : outputHeaders) {
                    if (col.columnName().equals(firstIndex)) {
                        result.setIndex(firstIndex);
                        break;
                    }
                }
            }
        }

        return result;
    }

    private static IDataFrame concatHorizontalTwo(
            IDataFrame left,
            IDataFrame right,
            String leftSuffix,
            String rightSuffix,
            JoinType joinType,
            boolean ignoreIndex) {

        int targetRows;
        if (joinType == JoinType.INNER) {
            targetRows = Math.min(left.size(), right.size());
        } else {
            targetRows = Math.max(left.size(), right.size());
        }

        Set<String> leftCols = new HashSet<>();
        if (left.getHeaders() != null) {
            for (Column c : left.getHeaders()) {
                leftCols.add(c.columnName());
            }
        }

        Set<String> rightCols = new HashSet<>();
        if (right.getHeaders() != null) {
            for (Column c : right.getHeaders()) {
                rightCols.add(c.columnName());
            }
        }

        Set<String> collidingCols = new HashSet<>(leftCols);
        collidingCols.retainAll(rightCols);

        List<Column> outputHeaders = new ArrayList<>();
        List<ColumnVector> vectors = new ArrayList<>();

        // Add left columns
        if (left.getHeaders() != null) {
            for (Column col : left.getHeaders()) {
                String outName = collidingCols.contains(col.columnName())
                    ? col.columnName() + leftSuffix
                    : col.columnName();

                boolean isNullable = col.isNullable() || (left.size() < targetRows);
                Column outCol = new Column(outName, col.columnType(), isNullable);
                outputHeaders.add(outCol);

                ColumnVector vec = ColumnVector.create(outCol.columnName(), outCol.columnType(), Math.max(16, targetRows), outCol.nullable());
                ColumnVector srcVec = left.getColumnVector(col.columnName());
                int rowsToCopy = Math.min(left.size(), targetRows);

                if (srcVec != null) {
                    for (int r = 0; r < rowsToCopy; r++) {
                        if (srcVec.isNull(r)) {
                            vec.appendNull();
                        } else {
                            Object val = srcVec.getObject(r);
                            if (NA.isNA(val)) {
                                vec.appendNull();
                            } else {
                                appendConvertedValue(vec, outCol.columnType(), val);
                            }
                        }
                    }
                }
                for (int r = rowsToCopy; r < targetRows; r++) {
                    vec.appendNull();
                }
                vectors.add(vec);
            }
        }

        // Add right columns
        if (right.getHeaders() != null) {
            for (Column col : right.getHeaders()) {
                String outName = collidingCols.contains(col.columnName())
                    ? col.columnName() + rightSuffix
                    : col.columnName();

                boolean isNullable = col.isNullable() || (right.size() < targetRows);
                Column outCol = new Column(outName, col.columnType(), isNullable);
                outputHeaders.add(outCol);

                ColumnVector vec = ColumnVector.create(outCol.columnName(), outCol.columnType(), Math.max(16, targetRows), outCol.nullable());
                ColumnVector srcVec = right.getColumnVector(col.columnName());
                int rowsToCopy = Math.min(right.size(), targetRows);

                if (srcVec != null) {
                    for (int r = 0; r < rowsToCopy; r++) {
                        if (srcVec.isNull(r)) {
                            vec.appendNull();
                        } else {
                            Object val = srcVec.getObject(r);
                            if (NA.isNA(val)) {
                                vec.appendNull();
                            } else {
                                appendConvertedValue(vec, outCol.columnType(), val);
                            }
                        }
                    }
                }
                for (int r = rowsToCopy; r < targetRows; r++) {
                    vec.appendNull();
                }
                vectors.add(vec);
            }
        }

        DataFrame result = new DataFrame();
        result.setHeaders(new ArrayList<>(outputHeaders));
        result.columns = vectors;

        if (!ignoreIndex) {
            String leftIndex = left.getIndex();
            if (leftIndex != null) {
                String resolvedIndexName = collidingCols.contains(leftIndex) ? leftIndex + leftSuffix : leftIndex;
                result.setIndex(resolvedIndexName);
            }
        }

        return result;
    }

    // --- Type Widening & Value Conversion Helpers ---

    private static void appendConvertedValue(ColumnVector targetVec, Class<?> targetType, Object val) {
        if (val == null) {
            targetVec.appendNull();
            return;
        }

        if (targetType == null || targetType == Object.class) {
            targetVec.appendObject(val);
            return;
        }

        if (targetType.isInstance(val)) {
            targetVec.appendObject(val);
            return;
        }

        if (targetType == Double.class || targetType == double.class) {
            if (val instanceof Number n) {
                targetVec.appendDouble(n.doubleValue());
            } else {
                targetVec.appendDouble(Double.parseDouble(val.toString().trim()));
            }
            return;
        }

        if (targetType == Long.class || targetType == long.class) {
            if (val instanceof Number n) {
                targetVec.appendLong(n.longValue());
            } else {
                targetVec.appendLong(Long.parseLong(val.toString().trim()));
            }
            return;
        }

        if (targetType == Integer.class || targetType == int.class) {
            if (val instanceof Number n) {
                targetVec.appendInt(n.intValue());
            } else {
                targetVec.appendInt(Integer.parseInt(val.toString().trim()));
            }
            return;
        }

        if (targetType == Float.class || targetType == float.class) {
            if (val instanceof Number n) {
                targetVec.appendFloat(n.floatValue());
            } else {
                targetVec.appendFloat(Float.parseFloat(val.toString().trim()));
            }
            return;
        }

        if (targetType == BigDecimal.class) {
            if (val instanceof BigDecimal bd) {
                targetVec.appendObject(bd);
            } else {
                targetVec.appendObject(new BigDecimal(val.toString().trim()));
            }
            return;
        }

        if (targetType == String.class) {
            targetVec.appendString(val.toString());
            return;
        }

        IDataTypeConverter<?> converter = DataFrame.CONVERTERS.get(targetType);
        if (converter != null) {
            targetVec.appendObject(converter.convert(val, val.getClass()));
            return;
        }

        targetVec.appendObject(val);
    }

    /**
     * Resolves the most compatible type between two classes, promoting narrower numeric types to wider ones.
     */
    public static Class<?> resolveCompatibleType(Class<?> t1, Class<?> t2) {
        if (t1 == null) return t2;
        if (t2 == null) return t1;
        if (t1.equals(t2)) return t1;

        Class<?> b1 = box(t1);
        Class<?> b2 = box(t2);
        if (b1.equals(b2)) return b1;

        if (isNumeric(b1) && isNumeric(b2)) {
            if (b1 == BigDecimal.class || b2 == BigDecimal.class) {
                return BigDecimal.class;
            }
            if (b1 == Double.class || b2 == Double.class) {
                return Double.class;
            }
            if (b1 == Float.class || b2 == Float.class) {
                return Double.class;
            }
            if (b1 == Long.class || b2 == Long.class) {
                return Long.class;
            }
            if (b1 == Integer.class || b2 == Integer.class) {
                return Integer.class;
            }
        }

        if (b1 == String.class && b2 == String.class) {
            return String.class;
        }

        if ((b1 == Categorical.class && b2 == String.class) || (b1 == String.class && b2 == Categorical.class)) {
            return Categorical.class;
        }

        return Object.class;
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static boolean isNumeric(Class<?> type) {
        return Number.class.isAssignableFrom(type);
    }

    /**
     * Attempts direct vectorized columnar concatenation for homogeneous columns across frames.
     * Returns null if fallback to row-by-row conversion is required.
     */
    private static ColumnVector tryConcatColumnVectorDirect(
            List<? extends IDataFrame> frames,
            Column targetCol,
            int totalRows) {

        String colName = targetCol.columnName();
        Class<?> targetType = targetCol.columnType();
        boolean nullable = targetCol.nullable();

        // 1. Verify all non-empty frames have matching ColumnVector class and type
        Class<? extends ColumnVector> commonVecClass = null;
        for (IDataFrame frame : frames) {
            if (frame.size() == 0) continue;
            ColumnVector srcVec = frame.getColumnVector(colName);
            if (srcVec == null) {
                // Column missing in one of the frames -> requires padding with nulls/fallback
                return null;
            }
            if (!targetType.equals(srcVec.getType())) {
                // Type widening/coercion needed -> fallback
                return null;
            }
            if (commonVecClass == null) {
                commonVecClass = srcVec.getClass();
            } else if (!commonVecClass.equals(srcVec.getClass())) {
                // Vector class mismatch -> fallback
                return null;
            }
        }

        if (commonVecClass == null) {
            return ColumnVector.create(colName, targetType, Math.max(16, totalRows), nullable);
        }

        // 2. Direct vectorized copy per vector type
        if (commonVecClass == IntColumnVector.class) {
            int[] dst = new int[totalRows];
            BitSet nulls = new BitSet(totalRows);
            int currRow = 0;
            for (IDataFrame frame : frames) {
                int frameRows = frame.size();
                if (frameRows == 0) continue;
                IntColumnVector src = (IntColumnVector) frame.getColumnVector(colName);
                System.arraycopy(src.getRawData(), 0, dst, currRow, frameRows);
                if (src.hasNulls()) {
                    BitSet srcNulls = src.getNulls();
                    for (int r = srcNulls.nextSetBit(0); r >= 0 && r < frameRows; r = srcNulls.nextSetBit(r + 1)) {
                        nulls.set(currRow + r);
                    }
                }
                currRow += frameRows;
            }
            return new IntColumnVector(colName, nullable, dst, nulls, totalRows);
        }

        if (commonVecClass == LongColumnVector.class) {
            long[] dst = new long[totalRows];
            BitSet nulls = new BitSet(totalRows);
            int currRow = 0;
            for (IDataFrame frame : frames) {
                int frameRows = frame.size();
                if (frameRows == 0) continue;
                LongColumnVector src = (LongColumnVector) frame.getColumnVector(colName);
                System.arraycopy(src.getRawData(), 0, dst, currRow, frameRows);
                if (src.hasNulls()) {
                    BitSet srcNulls = src.getNulls();
                    for (int r = srcNulls.nextSetBit(0); r >= 0 && r < frameRows; r = srcNulls.nextSetBit(r + 1)) {
                        nulls.set(currRow + r);
                    }
                }
                currRow += frameRows;
            }
            return new LongColumnVector(colName, nullable, dst, nulls, totalRows);
        }

        if (commonVecClass == DoubleColumnVector.class) {
            double[] dst = new double[totalRows];
            BitSet nulls = new BitSet(totalRows);
            int currRow = 0;
            for (IDataFrame frame : frames) {
                int frameRows = frame.size();
                if (frameRows == 0) continue;
                DoubleColumnVector src = (DoubleColumnVector) frame.getColumnVector(colName);
                System.arraycopy(src.getRawData(), 0, dst, currRow, frameRows);
                if (src.hasNulls()) {
                    BitSet srcNulls = src.getNulls();
                    for (int r = srcNulls.nextSetBit(0); r >= 0 && r < frameRows; r = srcNulls.nextSetBit(r + 1)) {
                        nulls.set(currRow + r);
                    }
                }
                currRow += frameRows;
            }
            return new DoubleColumnVector(colName, nullable, dst, nulls, totalRows);
        }

        if (commonVecClass == FloatColumnVector.class) {
            float[] dst = new float[totalRows];
            BitSet nulls = new BitSet(totalRows);
            int currRow = 0;
            for (IDataFrame frame : frames) {
                int frameRows = frame.size();
                if (frameRows == 0) continue;
                FloatColumnVector src = (FloatColumnVector) frame.getColumnVector(colName);
                System.arraycopy(src.getRawData(), 0, dst, currRow, frameRows);
                if (src.hasNulls()) {
                    BitSet srcNulls = src.getNulls();
                    for (int r = srcNulls.nextSetBit(0); r >= 0 && r < frameRows; r = srcNulls.nextSetBit(r + 1)) {
                        nulls.set(currRow + r);
                    }
                }
                currRow += frameRows;
            }
            return new FloatColumnVector(colName, nullable, dst, nulls, totalRows);
        }

        if (commonVecClass == BooleanColumnVector.class) {
            boolean[] dst = new boolean[totalRows];
            BitSet nulls = new BitSet(totalRows);
            int currRow = 0;
            for (IDataFrame frame : frames) {
                int frameRows = frame.size();
                if (frameRows == 0) continue;
                BooleanColumnVector src = (BooleanColumnVector) frame.getColumnVector(colName);
                System.arraycopy(src.getRawData(), 0, dst, currRow, frameRows);
                if (src.hasNulls()) {
                    BitSet srcNulls = src.getNulls();
                    for (int r = srcNulls.nextSetBit(0); r >= 0 && r < frameRows; r = srcNulls.nextSetBit(r + 1)) {
                        nulls.set(currRow + r);
                    }
                }
                currRow += frameRows;
            }
            return new BooleanColumnVector(colName, nullable, dst, nulls, totalRows);
        }

        if (commonVecClass == StringColumnVector.class) {
            long totalBytes = 0;
            for (IDataFrame frame : frames) {
                if (frame.size() == 0) continue;
                StringColumnVector src = (StringColumnVector) frame.getColumnVector(colName);
                totalBytes += src.getValuesCount();
            }

            if (totalBytes > Integer.MAX_VALUE - 8) {
                // Exceeds single array limit -> fallback
                return null;
            }

            byte[] values = new byte[(int) totalBytes];
            int[] offsets = new int[totalRows + 1];
            BitSet nulls = new BitSet(totalRows);

            int currRow = 0;
            int currByte = 0;
            for (IDataFrame frame : frames) {
                int frameRows = frame.size();
                if (frameRows == 0) continue;
                StringColumnVector src = (StringColumnVector) frame.getColumnVector(colName);
                int bBytes = src.getValuesCount();
                if (bBytes > 0) {
                    System.arraycopy(src.getValues(), 0, values, currByte, bBytes);
                }
                int[] srcOffsets = src.getOffsets();
                if (currByte == 0) {
                    System.arraycopy(srcOffsets, 1, offsets, 1, frameRows);
                } else {
                    for (int r = 1; r <= frameRows; r++) {
                        offsets[currRow + r] = srcOffsets[r] + currByte;
                    }
                }
                if (src.hasNulls()) {
                    BitSet srcNulls = src.getNulls();
                    for (int r = srcNulls.nextSetBit(0); r >= 0 && r < frameRows; r = srcNulls.nextSetBit(r + 1)) {
                        nulls.set(currRow + r);
                    }
                }
                currRow += frameRows;
                currByte += bBytes;
            }
            return new StringColumnVector(colName, nullable, values, (int) totalBytes, offsets, nulls, totalRows);
        }

        if (commonVecClass == ObjectColumnVector.class) {
            Object[] dst = new Object[totalRows];
            BitSet nulls = new BitSet(totalRows);
            int currRow = 0;
            for (IDataFrame frame : frames) {
                int frameRows = frame.size();
                if (frameRows == 0) continue;
                ObjectColumnVector src = (ObjectColumnVector) frame.getColumnVector(colName);
                System.arraycopy(src.getRawData(), 0, dst, currRow, frameRows);
                if (src.hasNulls()) {
                    BitSet srcNulls = src.getNulls();
                    for (int r = srcNulls.nextSetBit(0); r >= 0 && r < frameRows; r = srcNulls.nextSetBit(r + 1)) {
                        nulls.set(currRow + r);
                    }
                }
                currRow += frameRows;
            }
            return new ObjectColumnVector(colName, targetType, nullable, dst, nulls, totalRows);
        }

        return null;
    }
}
