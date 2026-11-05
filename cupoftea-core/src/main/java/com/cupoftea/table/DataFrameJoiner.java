package com.cupoftea.table;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import com.cupoftea.exceptions.SchemaMismatchException;
import com.cupoftea.table.interfaces.IDataFrame;
import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.DictionaryColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.FloatColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.ParallelVectorEngine;
import com.cupoftea.table.vector.StringColumnVector;

/**
 * Vectorized Columnar Hash Join engine operating directly on {@link ColumnVector} storage.
 * <p>
 * Eliminates row-level object materialization ({@code Row}, {@code JoinKey}, {@code HashMap}) by:
 * <ol>
 *   <li><b>Build Phase:</b> Constructing a flat primitive-array chained hash table ({@code int[] bucketHead},
 *       {@code int[] nextRowIdx}) with specialized zero-boxing fast-paths for {@code int}, {@code long},
 *       dictionary codes, and UTF-8 byte slices ({@link StringColumnVector}).</li>
 *   <li><b>Probe Phase:</b> Partitioning the probe table across parallel worker chunks to emit primitive
 *       gather index pairs {@code (leftMatchIdx, rightMatchIdx)} (with {@code -1} indicating unmatched null rows).</li>
 *   <li><b>Gather Phase:</b> Materializing output {@link ColumnVector}s in bulk via indexed array gather
 *       and lock-free two-pass UTF-8 byte compaction.</li>
 * </ol>
 */
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

    private record OutputColSpec(
            String outName,
            Class<?> outType,
            boolean nullable,
            ColumnVector primaryVec,
            ColumnVector fallbackRightKeyVec,
            boolean fromLeft,
            boolean mayHaveNegativeIndices) {
    }

    private static final class ProbeChunk {
        int[] leftIndices;
        int[] rightIndices;
        int count;

        ProbeChunk(int[] leftIndices, int[] rightIndices, int count) {
            this.leftIndices = leftIndices;
            this.rightIndices = rightIndices;
            this.count = count;
        }
    }

    private static IDataFrame executeJoin(
            IDataFrame left,
            IDataFrame right,
            JoinType joinType,
            List<String> leftOn,
            List<String> rightOn,
            String lSuffix,
            String rSuffix) {

        int leftSize = left.size();
        int rightSize = right.size();

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

        ArrayList<Column> outputHeaders = new ArrayList<>(leftHeaders.size() + rightHeaders.size());
        List<OutputColSpec> outputSpecs = new ArrayList<>(leftHeaders.size() + rightHeaders.size());
        Set<String> seenOutputNames = new HashSet<>();

        boolean leftNullableByJoin = (joinType == JoinType.RIGHT || joinType == JoinType.OUTER);
        boolean rightNullableByJoin = (joinType == JoinType.LEFT || joinType == JoinType.OUTER);

        // Add left columns
        for (Column col : leftHeaders) {
            String lName = col.columnName();
            String outName = leftOutMap.get(lName);
            if (!seenOutputNames.add(outName)) {
                throw new IllegalArgumentException("Duplicate column name in joined DataFrame: '" + outName + "'");
            }
            Class<?> colType = col.columnType();
            ColumnVector fallbackRightVec = null;
            if (joinType != JoinType.CROSS) {
                int idx = leftOn.indexOf(lName);
                if (idx >= 0) {
                    String rKeyName = rightOn.get(idx);
                    if (colType == null) {
                        Column rCol = right.getColumnHeader(rKeyName);
                        if (rCol != null && rCol.columnType() != null) {
                            colType = rCol.columnType();
                        }
                    }
                    if (rKeyName.equals(lName) && leftNullableByJoin) {
                        fallbackRightVec = right.getColumnVector(rKeyName);
                    }
                }
            }
            boolean nullable = col.nullable() || leftNullableByJoin;
            outputHeaders.add(new Column(outName, colType, nullable));
            outputSpecs.add(new OutputColSpec(
                outName,
                colType,
                nullable,
                left.getColumnVector(lName),
                fallbackRightVec,
                true,
                leftNullableByJoin
            ));
        }

        // Add right columns (skip shared keys)
        for (Column col : rightHeaders) {
            String rName = col.columnName();
            if (sharedRightKeys.contains(rName)) {
                continue;
            }
            String outName = rightOutMap.get(rName);
            if (!seenOutputNames.add(outName)) {
                throw new IllegalArgumentException("Duplicate column name in joined DataFrame: '" + outName + "'");
            }
            boolean nullable = col.nullable() || rightNullableByJoin;
            outputHeaders.add(new Column(outName, col.columnType(), nullable));
            outputSpecs.add(new OutputColSpec(
                outName,
                col.columnType(),
                nullable,
                right.getColumnVector(rName),
                null,
                false,
                rightNullableByJoin
            ));
        }

        // Fast-path for empty result joins
        if ((joinType == JoinType.INNER && (leftSize == 0 || rightSize == 0))
                || (joinType == JoinType.CROSS && (leftSize == 0 || rightSize == 0))
                || (joinType == JoinType.LEFT && leftSize == 0)
                || (joinType == JoinType.RIGHT && rightSize == 0)
                || (joinType == JoinType.OUTER && leftSize == 0 && rightSize == 0)) {
            return createEmptyResult(left, outputHeaders, outputSpecs, leftOutMap);
        }

        boolean parallel = left.isParallel() || right.isParallel();
        ExecutorService executor = left.isParallel() ? left.getExecutor() : right.getExecutor();
        int threshold = Math.min(left.getParallelThreshold(), right.getParallelThreshold());

        List<ColumnVector> resultVectors;

        if (joinType == JoinType.CROSS) {
            resultVectors = executeCrossJoin(leftSize, rightSize, outputSpecs, parallel, executor, threshold);
        } else if (joinType == JoinType.RIGHT) {
            ColumnVector[] leftKeyVecs = extractKeyVectors(left, leftOn);
            ColumnVector[] rightKeyVecs = extractKeyVectors(right, rightOn);
            HashJoinIndex leftIndex = buildHashIndex(leftKeyVecs, rightKeyVecs, leftSize, parallel, executor, threshold);
            resultVectors = executeRightJoin(
                leftIndex, leftSize, rightSize, outputSpecs, parallel, executor, threshold);
        } else {
            // INNER, LEFT, OUTER build on right and probe with left
            ColumnVector[] leftKeyVecs = extractKeyVectors(left, leftOn);
            ColumnVector[] rightKeyVecs = extractKeyVectors(right, rightOn);
            HashJoinIndex rightIndex = buildHashIndex(rightKeyVecs, leftKeyVecs, rightSize, parallel, executor, threshold);

            if (joinType == JoinType.LEFT && rightIndex.uniqueBuildKeys) {
                resultVectors = executeLeftJoinUniqueRight(
                    rightIndex, leftSize, outputSpecs, parallel, executor, threshold);
            } else if (joinType == JoinType.INNER) {
                resultVectors = executeInnerJoin(
                    rightIndex, leftSize, outputSpecs, parallel, executor, threshold);
            } else if (joinType == JoinType.LEFT) {
                resultVectors = executeLeftJoinGeneral(
                    rightIndex, leftSize, outputSpecs, parallel, executor, threshold);
            } else {
                resultVectors = executeOuterJoin(
                    rightIndex, leftSize, rightSize, outputSpecs, parallel, executor, threshold);
            }
        }

        DataFrame result = DataFrame.fromHeadersAndVectors(outputHeaders, resultVectors);
        if (left.getIndex() != null && leftOutMap.containsKey(left.getIndex())) {
            result.setIndex(leftOutMap.get(left.getIndex()));
        }
        return result;
    }

    private static DataFrame createEmptyResult(
            IDataFrame left,
            ArrayList<Column> outputHeaders,
            List<OutputColSpec> outputSpecs,
            Map<String, String> leftOutMap) {
        List<ColumnVector> emptyVectors = new ArrayList<>(outputSpecs.size());
        for (OutputColSpec spec : outputSpecs) {
            emptyVectors.add(createOutputVector(spec, 0));
        }
        DataFrame result = DataFrame.fromHeadersAndVectors(outputHeaders, emptyVectors);
        if (left.getIndex() != null && leftOutMap.containsKey(left.getIndex())) {
            result.setIndex(leftOutMap.get(left.getIndex()));
        }
        return result;
    }

    private static ColumnVector createOutputVector(OutputColSpec spec, int exactCapacity) {
        int cap = Math.max(16, exactCapacity);
        ColumnVector src = spec.primaryVec;
        if (src instanceof DictionaryColumnVector dictSrc && spec.fallbackRightKeyVec == null) {
            ColumnVector out = dictSrc.createEmptyWithSameEncoding(cap);
            out.setName(spec.outName);
            out.setNullable(spec.nullable);
            return out;
        }
        Class<?> type = spec.outType != null ? spec.outType : (src != null ? src.getType() : Object.class);
        return ColumnVector.create(spec.outName, type, cap, spec.nullable);
    }

    private static ColumnVector[] extractKeyVectors(IDataFrame df, List<String> keyCols) {
        int n = keyCols.size();
        ColumnVector[] vecs = new ColumnVector[n];
        for (int i = 0; i < n; i++) {
            vecs[i] = df.getColumnVector(keyCols.get(i));
        }
        return vecs;
    }

    // =========================================================================
    // Join Execution Paths (Probe + Vectorized Gather)
    // =========================================================================

    /**
     * Specialized 1:1 / 1:N Left Join fast-path when the right (build) side has unique keys.
     * Every left row emits exactly 1 output row at its original index {@code l}, so left columns
     * are copied via flat {@code System.arraycopy} ({@link ColumnVector#copy()}) and only right columns
     * require index gathering.
     */
    private static List<ColumnVector> executeLeftJoinUniqueRight(
            HashJoinIndex rightIndex,
            int leftSize,
            List<OutputColSpec> outputSpecs,
            boolean parallel,
            ExecutorService executor,
            int threshold) {

        int numCols = outputSpecs.size();
        ColumnVector[] resultVectors = new ColumnVector[numCols];

        // Identify right-side columns to gather
        List<Integer> rightPrimCols = new ArrayList<>(numCols);
        List<Integer> rightStrCols = new ArrayList<>(numCols);

        for (int c = 0; c < numCols; c++) {
            OutputColSpec spec = outputSpecs.get(c);
            if (spec.fromLeft) {
                ColumnVector copied = spec.primaryVec.copy();
                copied.setName(spec.outName);
                copied.setNullable(spec.nullable);
                resultVectors[c] = copied;
            } else if (spec.primaryVec instanceof StringColumnVector) {
                rightStrCols.add(c);
            } else {
                ColumnVector outVec = createOutputVector(spec, leftSize);
                outVec.setSizeUnchecked(leftSize);
                resultVectors[c] = outVec;
                rightPrimCols.add(c);
            }
        }

        boolean runParallel = parallel && leftSize >= threshold && leftSize > 1;
        ExecutorService pool = ParallelVectorEngine.resolveExecutor(executor);
        int numChunks = runParallel ? ParallelVectorEngine.computeChunkCount(leftSize, pool) : 1;
        int[][] ranges = ParallelVectorEngine.partitionRanges(leftSize, numChunks);

        int numRightStr = rightStrCols.size();
        int[][] strChunkByteCounts = numRightStr > 0 ? new int[numRightStr][ranges.length] : null;

        // Barrier 1: Probe right index per chunk, gather primitive right columns immediately, and count UTF-8 bytes
        List<int[]> rightIndexChunks = ParallelVectorEngine.executeRanges(ranges, pool, (chunkIdx, start, end) -> {
            int len = end - start;
            int[] rightIdx = new int[len];
            rightIndex.probeUniqueLeft(start, end, rightIdx);

            for (int i = 0; i < rightPrimCols.size(); i++) {
                int colIdx = rightPrimCols.get(i);
                OutputColSpec spec = outputSpecs.get(colIdx);
                resultVectors[colIdx].copyNullableIndexedValuesOnly(spec.primaryVec, rightIdx, len, start);
            }

            for (int s = 0; s < numRightStr; s++) {
                int colIdx = rightStrCols.get(s);
                StringColumnVector strSrc = (StringColumnVector) outputSpecs.get(colIdx).primaryVec;
                strChunkByteCounts[s][chunkIdx] = strSrc.computeNullableIndexedByteCount(rightIdx, len);
            }
            return rightIdx;
        });

        // Barrier 2 (only if right side has StringColumnVectors): lock-free UTF-8 byte gather
        if (numRightStr > 0) {
            int[][] strByteOffsets = new int[numRightStr][ranges.length];
            for (int s = 0; s < numRightStr; s++) {
                int colIdx = rightStrCols.get(s);
                OutputColSpec spec = outputSpecs.get(colIdx);
                int totalBytes = ParallelVectorEngine.computePrefixSums(strChunkByteCounts[s], strByteOffsets[s]);
                resultVectors[colIdx] = StringColumnVector.allocateForCompaction(
                    spec.outName, spec.nullable, leftSize, totalBytes);
            }

            ParallelVectorEngine.executeRanges(ranges, pool, (chunkIdx, start, end) -> {
                int len = end - start;
                int[] rightIdx = rightIndexChunks.get(chunkIdx);
                for (int s = 0; s < numRightStr; s++) {
                    int colIdx = rightStrCols.get(s);
                    StringColumnVector srcStr = (StringColumnVector) outputSpecs.get(colIdx).primaryVec;
                    StringColumnVector dstStr = (StringColumnVector) resultVectors[colIdx];
                    dstStr.copyNullableIndexedBytesLockFree(
                        srcStr, rightIdx, len, start, strByteOffsets[s][chunkIdx]);
                }
                return null;
            });
        }

        // Apply null BitSets for right columns
        for (int c = 0; c < numCols; c++) {
            OutputColSpec spec = outputSpecs.get(c);
            if (!spec.fromLeft) {
                ColumnVector dstVec = resultVectors[c];
                for (int chunkIdx = 0; chunkIdx < ranges.length; chunkIdx++) {
                    int start = ranges[chunkIdx][0];
                    int len = ranges[chunkIdx][1] - start;
                    dstVec.applyNullsFromNullableIndexed(
                        spec.primaryVec, rightIndexChunks.get(chunkIdx), len, start);
                }
            }
        }

        return Arrays.asList(resultVectors);
    }

    private static List<ColumnVector> executeInnerJoin(
            HashJoinIndex rightIndex,
            int leftSize,
            List<OutputColSpec> outputSpecs,
            boolean parallel,
            ExecutorService executor,
            int threshold) {

        boolean runParallel = parallel && leftSize >= threshold && leftSize > 1;
        ExecutorService pool = ParallelVectorEngine.resolveExecutor(executor);
        int numChunks = runParallel ? ParallelVectorEngine.computeChunkCount(leftSize, pool) : 1;
        int[][] ranges = ParallelVectorEngine.partitionRanges(leftSize, numChunks);

        int numCols = outputSpecs.size();
        int[][] strChunkByteCounts = new int[numCols][];
        for (int c = 0; c < numCols; c++) {
            if (outputSpecs.get(c).primaryVec instanceof StringColumnVector) {
                strChunkByteCounts[c] = new int[ranges.length];
            }
        }

        int[] chunkCounts = new int[ranges.length];

        // Barrier 1: Parallel Probe + UTF-8 byte counting per chunk
        List<ProbeChunk> chunks = ParallelVectorEngine.executeRanges(ranges, pool, (chunkIdx, start, end) -> {
            ProbeChunk chunk = rightIndex.probeInner(start, end);
            int count = chunk.count;
            chunkCounts[chunkIdx] = count;
            if (count > 0) {
                for (int c = 0; c < numCols; c++) {
                    if (strChunkByteCounts[c] != null) {
                        OutputColSpec spec = outputSpecs.get(c);
                        StringColumnVector strSrc = (StringColumnVector) spec.primaryVec;
                        int[] idx = spec.fromLeft ? chunk.leftIndices : chunk.rightIndices;
                        strChunkByteCounts[c][chunkIdx] = strSrc.computeIndexedByteCount(idx, count);
                    }
                }
            }
            return chunk;
        });

        return gatherFromProbeChunks(chunks, chunkCounts, strChunkByteCounts, outputSpecs, ranges, pool);
    }

    private static List<ColumnVector> executeLeftJoinGeneral(
            HashJoinIndex rightIndex,
            int leftSize,
            List<OutputColSpec> outputSpecs,
            boolean parallel,
            ExecutorService executor,
            int threshold) {

        boolean runParallel = parallel && leftSize >= threshold && leftSize > 1;
        ExecutorService pool = ParallelVectorEngine.resolveExecutor(executor);
        int numChunks = runParallel ? ParallelVectorEngine.computeChunkCount(leftSize, pool) : 1;
        int[][] ranges = ParallelVectorEngine.partitionRanges(leftSize, numChunks);

        int numCols = outputSpecs.size();
        int[][] strChunkByteCounts = new int[numCols][];
        for (int c = 0; c < numCols; c++) {
            if (outputSpecs.get(c).primaryVec instanceof StringColumnVector) {
                strChunkByteCounts[c] = new int[ranges.length];
            }
        }

        int[] chunkCounts = new int[ranges.length];

        List<ProbeChunk> chunks = ParallelVectorEngine.executeRanges(ranges, pool, (chunkIdx, start, end) -> {
            ProbeChunk chunk = rightIndex.probeOuter(start, end, true, null);
            int count = chunk.count;
            chunkCounts[chunkIdx] = count;
            if (count > 0) {
                for (int c = 0; c < numCols; c++) {
                    if (strChunkByteCounts[c] != null) {
                        OutputColSpec spec = outputSpecs.get(c);
                        StringColumnVector strSrc = (StringColumnVector) spec.primaryVec;
                        if (spec.fromLeft) {
                            strChunkByteCounts[c][chunkIdx] = strSrc.computeIndexedByteCount(chunk.leftIndices, count);
                        } else {
                            strChunkByteCounts[c][chunkIdx] = strSrc.computeNullableIndexedByteCount(chunk.rightIndices, count);
                        }
                    }
                }
            }
            return chunk;
        });

        return gatherFromProbeChunks(chunks, chunkCounts, strChunkByteCounts, outputSpecs, ranges, pool);
    }

    private static List<ColumnVector> executeRightJoin(
            HashJoinIndex leftIndex,
            int leftSize,
            int rightSize,
            List<OutputColSpec> outputSpecs,
            boolean parallel,
            ExecutorService executor,
            int threshold) {

        boolean runParallel = parallel && rightSize >= threshold && rightSize > 1;
        ExecutorService pool = ParallelVectorEngine.resolveExecutor(executor);
        int numChunks = runParallel ? ParallelVectorEngine.computeChunkCount(rightSize, pool) : 1;
        int[][] ranges = ParallelVectorEngine.partitionRanges(rightSize, numChunks);

        int numCols = outputSpecs.size();
        int[][] strChunkByteCounts = new int[numCols][];
        for (int c = 0; c < numCols; c++) {
            OutputColSpec spec = outputSpecs.get(c);
            if (spec.primaryVec instanceof StringColumnVector && spec.fallbackRightKeyVec == null) {
                strChunkByteCounts[c] = new int[ranges.length];
            }
        }

        int[] chunkCounts = new int[ranges.length];

        List<ProbeChunk> chunks = ParallelVectorEngine.executeRanges(ranges, pool, (chunkIdx, start, end) -> {
            // Probe leftIndex using right rows [start, end); swap so leftIndices has left row (or -1) and rightIndices has right row
            ProbeChunk raw = leftIndex.probeOuter(start, end, true, null);
            ProbeChunk chunk = new ProbeChunk(raw.rightIndices, raw.leftIndices, raw.count);
            int count = chunk.count;
            chunkCounts[chunkIdx] = count;
            if (count > 0) {
                for (int c = 0; c < numCols; c++) {
                    if (strChunkByteCounts[c] != null) {
                        OutputColSpec spec = outputSpecs.get(c);
                        StringColumnVector strSrc = (StringColumnVector) spec.primaryVec;
                        if (spec.fromLeft) {
                            strChunkByteCounts[c][chunkIdx] = strSrc.computeNullableIndexedByteCount(chunk.leftIndices, count);
                        } else {
                            strChunkByteCounts[c][chunkIdx] = strSrc.computeIndexedByteCount(chunk.rightIndices, count);
                        }
                    }
                }
            }
            return chunk;
        });

        return gatherFromProbeChunks(chunks, chunkCounts, strChunkByteCounts, outputSpecs, ranges, pool);
    }

    private static List<ColumnVector> executeOuterJoin(
            HashJoinIndex rightIndex,
            int leftSize,
            int rightSize,
            List<OutputColSpec> outputSpecs,
            boolean parallel,
            ExecutorService executor,
            int threshold) {

        boolean runParallel = parallel && leftSize >= threshold && leftSize > 1;
        ExecutorService pool = ParallelVectorEngine.resolveExecutor(executor);
        int numLeftChunks = leftSize > 0
                ? (runParallel ? ParallelVectorEngine.computeChunkCount(leftSize, pool) : 1)
                : 0;
        int[][] leftRanges = ParallelVectorEngine.partitionRanges(leftSize, numLeftChunks);

        boolean[] rightMatched = new boolean[rightSize];
        List<ProbeChunk> allChunks = new ArrayList<>(leftRanges.length + 1);

        if (leftRanges.length > 0) {
            List<ProbeChunk> leftChunks = ParallelVectorEngine.executeRanges(leftRanges, pool, (chunkIdx, start, end) ->
                rightIndex.probeOuter(start, end, true, rightMatched)
            );
            allChunks.addAll(leftChunks);
        }

        // Collect unmatched right rows
        int unmatchedRightCount = 0;
        for (int r = 0; r < rightSize; r++) {
            if (!rightMatched[r]) {
                unmatchedRightCount++;
            }
        }
        if (unmatchedRightCount > 0) {
            int[] uLeft = new int[unmatchedRightCount];
            Arrays.fill(uLeft, -1);
            int[] uRight = new int[unmatchedRightCount];
            int pos = 0;
            for (int r = 0; r < rightSize; r++) {
                if (!rightMatched[r]) {
                    uRight[pos++] = r;
                }
            }
            allChunks.add(new ProbeChunk(uLeft, uRight, unmatchedRightCount));
        }

        int totalChunks = allChunks.size();
        int[][] allRanges = new int[totalChunks][2];
        int[] chunkCounts = new int[totalChunks];
        for (int i = 0; i < totalChunks; i++) {
            allRanges[i][0] = i;
            allRanges[i][1] = i + 1;
            chunkCounts[i] = allChunks.get(i).count;
        }

        int numCols = outputSpecs.size();
        int[][] strChunkByteCounts = new int[numCols][];
        for (int c = 0; c < numCols; c++) {
            OutputColSpec spec = outputSpecs.get(c);
            if (spec.primaryVec instanceof StringColumnVector && spec.fallbackRightKeyVec == null) {
                strChunkByteCounts[c] = new int[totalChunks];
                StringColumnVector strSrc = (StringColumnVector) spec.primaryVec;
                for (int k = 0; k < totalChunks; k++) {
                    ProbeChunk ch = allChunks.get(k);
                    if (ch.count > 0) {
                        int[] idx = spec.fromLeft ? ch.leftIndices : ch.rightIndices;
                        strChunkByteCounts[c][k] = spec.mayHaveNegativeIndices
                                ? strSrc.computeNullableIndexedByteCount(idx, ch.count)
                                : strSrc.computeIndexedByteCount(idx, ch.count);
                    }
                }
            }
        }

        return gatherFromProbeChunks(allChunks, chunkCounts, strChunkByteCounts, outputSpecs, allRanges, pool);
    }

    private static List<ColumnVector> executeCrossJoin(
            int leftSize,
            int rightSize,
            List<OutputColSpec> outputSpecs,
            boolean parallel,
            ExecutorService executor,
            int threshold) {

        int totalRows = Math.multiplyExact(leftSize, rightSize);
        int[] leftIdx = new int[totalRows];
        int[] rightIdx = new int[totalRows];
        int pos = 0;
        for (int l = 0; l < leftSize; l++) {
            Arrays.fill(leftIdx, pos, pos + rightSize, l);
            for (int r = 0; r < rightSize; r++) {
                rightIdx[pos + r] = r;
            }
            pos += rightSize;
        }

        ProbeChunk singleChunk = new ProbeChunk(leftIdx, rightIdx, totalRows);
        List<ProbeChunk> chunks = Collections.singletonList(singleChunk);
        int[] chunkCounts = new int[]{totalRows};
        int[][] ranges = new int[][]{{0, totalRows}};

        int numCols = outputSpecs.size();
        int[][] strChunkByteCounts = new int[numCols][];
        for (int c = 0; c < numCols; c++) {
            OutputColSpec spec = outputSpecs.get(c);
            if (spec.primaryVec instanceof StringColumnVector strSrc) {
                int[] idx = spec.fromLeft ? leftIdx : rightIdx;
                strChunkByteCounts[c] = new int[]{strSrc.computeIndexedByteCount(idx, totalRows)};
            }
        }

        return gatherFromProbeChunks(
            chunks, chunkCounts, strChunkByteCounts, outputSpecs, ranges, ParallelVectorEngine.resolveExecutor(executor));
    }

    /**
     * Materializes all output {@link ColumnVector}s from chunked gather index arrays in a single parallel barrier.
     */
    private static List<ColumnVector> gatherFromProbeChunks(
            List<ProbeChunk> chunks,
            int[] chunkCounts,
            int[][] strChunkByteCounts,
            List<OutputColSpec> outputSpecs,
            int[][] ranges,
            ExecutorService pool) {

        int numChunks = chunks.size();
        int[] rowOffsets = new int[numChunks];
        int totalRows = ParallelVectorEngine.computePrefixSums(chunkCounts, rowOffsets);

        int numCols = outputSpecs.size();
        if (totalRows == 0) {
            List<ColumnVector> empty = new ArrayList<>(numCols);
            for (OutputColSpec spec : outputSpecs) {
                empty.add(createOutputVector(spec, 0));
            }
            return empty;
        }

        ColumnVector[] outVectors = new ColumnVector[numCols];
        int[][] strByteOffsets = new int[numCols][];

        for (int c = 0; c < numCols; c++) {
            OutputColSpec spec = outputSpecs.get(c);
            if (strChunkByteCounts[c] != null) {
                strByteOffsets[c] = new int[numChunks];
                int totalBytes = ParallelVectorEngine.computePrefixSums(strChunkByteCounts[c], strByteOffsets[c]);
                outVectors[c] = StringColumnVector.allocateForCompaction(
                    spec.outName, spec.nullable, totalRows, totalBytes);
            } else {
                ColumnVector vec = createOutputVector(spec, totalRows);
                vec.setSizeUnchecked(totalRows);
                outVectors[c] = vec;
            }
        }

        // Barrier 2: Parallel lock-free value gather across all chunks and columns
        ParallelVectorEngine.executeRanges(ranges, pool, (chunkIdx, start, end) -> {
            ProbeChunk chunk = chunks.get(chunkIdx);
            int count = chunk.count;
            if (count <= 0) {
                return null;
            }
            int dstRowOffset = rowOffsets[chunkIdx];
            int[] lIdx = chunk.leftIndices;
            int[] rIdx = chunk.rightIndices;

            for (int c = 0; c < numCols; c++) {
                OutputColSpec spec = outputSpecs.get(c);
                ColumnVector dstVec = outVectors[c];

                if (spec.fallbackRightKeyVec != null) {
                    gatherSharedKeyWithFallback(dstVec, spec.primaryVec, spec.fallbackRightKeyVec, lIdx, rIdx, count, dstRowOffset);
                } else if (strChunkByteCounts[c] != null) {
                    StringColumnVector srcStr = (StringColumnVector) spec.primaryVec;
                    StringColumnVector dstStr = (StringColumnVector) dstVec;
                    int[] idx = spec.fromLeft ? lIdx : rIdx;
                    int dstByteOffset = strByteOffsets[c][chunkIdx];
                    if (spec.mayHaveNegativeIndices) {
                        dstStr.copyNullableIndexedBytesLockFree(srcStr, idx, count, dstRowOffset, dstByteOffset);
                    } else {
                        dstStr.copyIndexedBytesLockFree(srcStr, idx, count, dstRowOffset, dstByteOffset);
                    }
                } else {
                    int[] idx = spec.fromLeft ? lIdx : rIdx;
                    if (spec.mayHaveNegativeIndices) {
                        dstVec.copyNullableIndexedValuesOnly(spec.primaryVec, idx, count, dstRowOffset);
                    } else {
                        dstVec.copyIndexedValuesOnly(spec.primaryVec, idx, count, dstRowOffset);
                    }
                }
            }
            return null;
        });

        // Apply null bitmaps sequentially across chunks (fast BitSet word updates, zero thread contention)
        for (int c = 0; c < numCols; c++) {
            OutputColSpec spec = outputSpecs.get(c);
            if (spec.fallbackRightKeyVec != null) {
                continue; // handled inside gatherSharedKeyWithFallback
            }
            ColumnVector dstVec = outVectors[c];
            ColumnVector srcVec = spec.primaryVec;
            if (spec.mayHaveNegativeIndices) {
                for (int chunkIdx = 0; chunkIdx < numChunks; chunkIdx++) {
                    ProbeChunk chunk = chunks.get(chunkIdx);
                    if (chunk.count > 0) {
                        int[] idx = spec.fromLeft ? chunk.leftIndices : chunk.rightIndices;
                        dstVec.applyNullsFromNullableIndexed(srcVec, idx, chunk.count, rowOffsets[chunkIdx]);
                    }
                }
            } else if (srcVec.hasNulls()) {
                for (int chunkIdx = 0; chunkIdx < numChunks; chunkIdx++) {
                    ProbeChunk chunk = chunks.get(chunkIdx);
                    if (chunk.count > 0) {
                        int[] idx = spec.fromLeft ? chunk.leftIndices : chunk.rightIndices;
                        dstVec.applyNullsFromIndexed(srcVec, idx, chunk.count, rowOffsets[chunkIdx]);
                    }
                }
            }
        }

        return Arrays.asList(outVectors);
    }

    private static void gatherSharedKeyWithFallback(
            ColumnVector dstVec,
            ColumnVector leftKeyVec,
            ColumnVector rightKeyVec,
            int[] leftIdx,
            int[] rightIdx,
            int count,
            int dstOffset) {
        synchronized (dstVec) {
            for (int i = 0; i < count; i++) {
                int l = leftIdx[i];
                Object val;
                if (l >= 0) {
                    val = leftKeyVec.isNull(l) ? null : leftKeyVec.getObject(l);
                } else {
                    int r = rightIdx[i];
                    val = (r >= 0 && !rightKeyVec.isNull(r)) ? rightKeyVec.getObject(r) : null;
                }
                dstVec.setObject(dstOffset + i, val);
            }
        }
    }

    // =========================================================================
    // Primitive Hash Join Index Hierarchy (Zero-Boxing Build & Probe)
    // =========================================================================

    private static HashJoinIndex buildHashIndex(
            ColumnVector[] buildKeyVecs,
            ColumnVector[] probeKeyVecs,
            int buildSize,
            boolean parallel,
            ExecutorService executor,
            int threshold) {

        if (buildKeyVecs.length == 1) {
            ColumnVector bVec = buildKeyVecs[0];
            ColumnVector pVec = probeKeyVecs[0];

            if (bVec instanceof IntColumnVector bInt && pVec instanceof IntColumnVector pInt) {
                return new IntHashJoinIndex(bInt, pInt, buildSize);
            }
            if (bVec instanceof LongColumnVector bLong && pVec instanceof LongColumnVector pLong) {
                return new LongHashJoinIndex(bLong, pLong, buildSize);
            }
            if (bVec instanceof StringColumnVector bStr && pVec instanceof StringColumnVector pStr) {
                return new StringHashJoinIndex(bStr, pStr, buildSize, parallel, executor, threshold);
            }
            if (bVec instanceof DictionaryColumnVector bDict && pVec instanceof DictionaryColumnVector pDict) {
                return new DictionaryHashJoinIndex(bDict, pDict, buildSize);
            }
            if (bVec instanceof DictionaryColumnVector bDict && pVec instanceof StringColumnVector pStr) {
                return new DictBuildStringProbeHashJoinIndex(bDict, pStr, buildSize);
            }
            if (bVec instanceof StringColumnVector bStr && pVec instanceof DictionaryColumnVector pDict) {
                return new StringBuildDictProbeHashJoinIndex(bStr, pDict, buildSize, parallel, executor, threshold);
            }
        }
        return new GeneralColumnarHashJoinIndex(buildKeyVecs, probeKeyVecs, buildSize);
    }

    private abstract static class HashJoinIndex {
        final int buildSize;
        boolean uniqueBuildKeys;

        HashJoinIndex(int buildSize) {
            this.buildSize = buildSize;
        }

        abstract void probeUniqueLeft(int start, int end, int[] outRightIdx);

        abstract ProbeChunk probeInner(int start, int end);

        abstract ProbeChunk probeOuter(int start, int end, boolean emitUnmatchedProbe, boolean[] buildMatched);
    }

    // -------------------------------------------------------------------------
    // 1. IntColumnVector Specialized Hash Join Index
    // -------------------------------------------------------------------------

    private static final class IntHashJoinIndex extends HashJoinIndex {
        private final int[] buildData;
        private final int[] probeData;
        private final BitSet probeNulls;
        private final boolean probeHasNulls;
        private final int mask;
        private final int[] bucketHead;
        private final int[] nextRowIdx;

        IntHashJoinIndex(IntColumnVector buildVec, IntColumnVector probeVec, int buildSize) {
            super(buildSize);
            int[] bData = buildVec.getRawData();
            boolean bHasNulls = buildVec.hasNulls();
            BitSet bNulls = buildVec.getNulls();

            int cap = nextPowerOfTwo(Math.max(16, buildSize));
            int m = cap - 1;
            int[] heads = new int[cap];
            Arrays.fill(heads, -1);
            int[] next = new int[buildSize];
            boolean unique = true;

            if (!bHasNulls) {
                for (int r = buildSize - 1; r >= 0; r--) {
                    int k = bData[r];
                    int b = mixInt(k) & m;
                    int head = heads[b];
                    if (unique && head != -1) {
                        for (int c = head; c != -1; c = next[c]) {
                            if (bData[c] == k) {
                                unique = false;
                                break;
                            }
                        }
                    }
                    next[r] = head;
                    heads[b] = r;
                }
            } else {
                for (int r = buildSize - 1; r >= 0; r--) {
                    if (bNulls.get(r)) {
                        next[r] = -1;
                        continue;
                    }
                    int k = bData[r];
                    int b = mixInt(k) & m;
                    int head = heads[b];
                    if (unique && head != -1) {
                        for (int c = head; c != -1; c = next[c]) {
                            if (bData[c] == k) {
                                unique = false;
                                break;
                            }
                        }
                    }
                    next[r] = head;
                    heads[b] = r;
                }
            }

            this.uniqueBuildKeys = unique;
            this.buildData = bData;
            this.probeData = probeVec.getRawData();
            this.probeHasNulls = probeVec.hasNulls();
            this.probeNulls = probeVec.getNulls();
            this.mask = m;
            this.bucketHead = heads;
            this.nextRowIdx = next;
        }

        @Override
        void probeUniqueLeft(int start, int end, int[] outRightIdx) {
            int[] pData = this.probeData;
            int[] bData = this.buildData;
            int[] heads = this.bucketHead;
            int[] next = this.nextRowIdx;
            int m = this.mask;

            if (!this.probeHasNulls) {
                for (int l = start; l < end; l++) {
                    int k = pData[l];
                    int matched = -1;
                    for (int r = heads[mixInt(k) & m]; r != -1; r = next[r]) {
                        if (bData[r] == k) {
                            matched = r;
                            break;
                        }
                    }
                    outRightIdx[l - start] = matched;
                }
            } else {
                BitSet pNulls = this.probeNulls;
                for (int l = start; l < end; l++) {
                    if (pNulls.get(l)) {
                        outRightIdx[l - start] = -1;
                        continue;
                    }
                    int k = pData[l];
                    int matched = -1;
                    for (int r = heads[mixInt(k) & m]; r != -1; r = next[r]) {
                        if (bData[r] == k) {
                            matched = r;
                            break;
                        }
                    }
                    outRightIdx[l - start] = matched;
                }
            }
        }

        @Override
        ProbeChunk probeInner(int start, int end) {
            int len = end - start;
            int[] leftIdx = new int[len];
            int[] rightIdx = new int[len];
            int count = 0;

            int[] pData = this.probeData;
            int[] bData = this.buildData;
            int[] heads = this.bucketHead;
            int[] next = this.nextRowIdx;
            int m = this.mask;

            if (this.uniqueBuildKeys && !this.probeHasNulls) {
                for (int l = start; l < end; l++) {
                    int k = pData[l];
                    for (int r = heads[mixInt(k) & m]; r != -1; r = next[r]) {
                        if (bData[r] == k) {
                            leftIdx[count] = l;
                            rightIdx[count] = r;
                            count++;
                            break;
                        }
                    }
                }
            } else {
                boolean pHasNulls = this.probeHasNulls;
                BitSet pNulls = this.probeNulls;
                for (int l = start; l < end; l++) {
                    if (pHasNulls && pNulls.get(l)) {
                        continue;
                    }
                    int k = pData[l];
                    for (int r = heads[mixInt(k) & m]; r != -1; r = next[r]) {
                        if (bData[r] == k) {
                            if (count == leftIdx.length) {
                                int newCap = leftIdx.length + (leftIdx.length >> 1) + 16;
                                leftIdx = Arrays.copyOf(leftIdx, newCap);
                                rightIdx = Arrays.copyOf(rightIdx, newCap);
                            }
                            leftIdx[count] = l;
                            rightIdx[count] = r;
                            count++;
                        }
                    }
                }
            }
            return new ProbeChunk(leftIdx, rightIdx, count);
        }

        @Override
        ProbeChunk probeOuter(int start, int end, boolean emitUnmatchedProbe, boolean[] buildMatched) {
            int len = end - start;
            int[] leftIdx = new int[len];
            int[] rightIdx = new int[len];
            int count = 0;

            int[] pData = this.probeData;
            int[] bData = this.buildData;
            int[] heads = this.bucketHead;
            int[] next = this.nextRowIdx;
            int m = this.mask;
            boolean pHasNulls = this.probeHasNulls;
            BitSet pNulls = this.probeNulls;

            for (int l = start; l < end; l++) {
                boolean matchedAny = false;
                if (!pHasNulls || !pNulls.get(l)) {
                    int k = pData[l];
                    for (int r = heads[mixInt(k) & m]; r != -1; r = next[r]) {
                        if (bData[r] == k) {
                            if (count == leftIdx.length) {
                                int newCap = leftIdx.length + (leftIdx.length >> 1) + 16;
                                leftIdx = Arrays.copyOf(leftIdx, newCap);
                                rightIdx = Arrays.copyOf(rightIdx, newCap);
                            }
                            leftIdx[count] = l;
                            rightIdx[count] = r;
                            count++;
                            matchedAny = true;
                            if (buildMatched != null) {
                                buildMatched[r] = true;
                            }
                        }
                    }
                }
                if (!matchedAny && emitUnmatchedProbe) {
                    if (count == leftIdx.length) {
                        int newCap = leftIdx.length + (leftIdx.length >> 1) + 16;
                        leftIdx = Arrays.copyOf(leftIdx, newCap);
                        rightIdx = Arrays.copyOf(rightIdx, newCap);
                    }
                    leftIdx[count] = l;
                    rightIdx[count] = -1;
                    count++;
                }
            }
            return new ProbeChunk(leftIdx, rightIdx, count);
        }
    }

    // -------------------------------------------------------------------------
    // 2. LongColumnVector Specialized Hash Join Index
    // -------------------------------------------------------------------------

    private static final class LongHashJoinIndex extends HashJoinIndex {
        private final long[] buildData;
        private final long[] probeData;
        private final BitSet probeNulls;
        private final boolean probeHasNulls;
        private final int mask;
        private final int[] bucketHead;
        private final int[] nextRowIdx;

        LongHashJoinIndex(LongColumnVector buildVec, LongColumnVector probeVec, int buildSize) {
            super(buildSize);
            long[] bData = buildVec.getRawData();
            boolean bHasNulls = buildVec.hasNulls();
            BitSet bNulls = buildVec.getNulls();

            int cap = nextPowerOfTwo(Math.max(16, buildSize));
            int m = cap - 1;
            int[] heads = new int[cap];
            Arrays.fill(heads, -1);
            int[] next = new int[buildSize];
            boolean unique = true;

            for (int r = buildSize - 1; r >= 0; r--) {
                if (bHasNulls && bNulls.get(r)) {
                    next[r] = -1;
                    continue;
                }
                long k = bData[r];
                int b = mixLong(k) & m;
                int head = heads[b];
                if (unique && head != -1) {
                    for (int c = head; c != -1; c = next[c]) {
                        if (bData[c] == k) {
                            unique = false;
                            break;
                        }
                    }
                }
                next[r] = head;
                heads[b] = r;
            }

            this.uniqueBuildKeys = unique;
            this.buildData = bData;
            this.probeData = probeVec.getRawData();
            this.probeHasNulls = probeVec.hasNulls();
            this.probeNulls = probeVec.getNulls();
            this.mask = m;
            this.bucketHead = heads;
            this.nextRowIdx = next;
        }

        @Override
        void probeUniqueLeft(int start, int end, int[] outRightIdx) {
            long[] pData = this.probeData;
            long[] bData = this.buildData;
            int[] heads = this.bucketHead;
            int[] next = this.nextRowIdx;
            int m = this.mask;
            boolean pHasNulls = this.probeHasNulls;
            BitSet pNulls = this.probeNulls;

            for (int l = start; l < end; l++) {
                if (pHasNulls && pNulls.get(l)) {
                    outRightIdx[l - start] = -1;
                    continue;
                }
                long k = pData[l];
                int matched = -1;
                for (int r = heads[mixLong(k) & m]; r != -1; r = next[r]) {
                    if (bData[r] == k) {
                        matched = r;
                        break;
                    }
                }
                outRightIdx[l - start] = matched;
            }
        }

        @Override
        ProbeChunk probeInner(int start, int end) {
            int len = end - start;
            int[] leftIdx = new int[len];
            int[] rightIdx = new int[len];
            int count = 0;

            long[] pData = this.probeData;
            long[] bData = this.buildData;
            int[] heads = this.bucketHead;
            int[] next = this.nextRowIdx;
            int m = this.mask;
            boolean pHasNulls = this.probeHasNulls;
            BitSet pNulls = this.probeNulls;
            boolean unique = this.uniqueBuildKeys;

            for (int l = start; l < end; l++) {
                if (pHasNulls && pNulls.get(l)) {
                    continue;
                }
                long k = pData[l];
                for (int r = heads[mixLong(k) & m]; r != -1; r = next[r]) {
                    if (bData[r] == k) {
                        if (count == leftIdx.length) {
                            int newCap = leftIdx.length + (leftIdx.length >> 1) + 16;
                            leftIdx = Arrays.copyOf(leftIdx, newCap);
                            rightIdx = Arrays.copyOf(rightIdx, newCap);
                        }
                        leftIdx[count] = l;
                        rightIdx[count] = r;
                        count++;
                        if (unique) {
                            break;
                        }
                    }
                }
            }
            return new ProbeChunk(leftIdx, rightIdx, count);
        }

        @Override
        ProbeChunk probeOuter(int start, int end, boolean emitUnmatchedProbe, boolean[] buildMatched) {
            int len = end - start;
            int[] leftIdx = new int[len];
            int[] rightIdx = new int[len];
            int count = 0;

            long[] pData = this.probeData;
            long[] bData = this.buildData;
            int[] heads = this.bucketHead;
            int[] next = this.nextRowIdx;
            int m = this.mask;
            boolean pHasNulls = this.probeHasNulls;
            BitSet pNulls = this.probeNulls;

            for (int l = start; l < end; l++) {
                boolean matchedAny = false;
                if (!pHasNulls || !pNulls.get(l)) {
                    long k = pData[l];
                    for (int r = heads[mixLong(k) & m]; r != -1; r = next[r]) {
                        if (bData[r] == k) {
                            if (count == leftIdx.length) {
                                int newCap = leftIdx.length + (leftIdx.length >> 1) + 16;
                                leftIdx = Arrays.copyOf(leftIdx, newCap);
                                rightIdx = Arrays.copyOf(rightIdx, newCap);
                            }
                            leftIdx[count] = l;
                            rightIdx[count] = r;
                            count++;
                            matchedAny = true;
                            if (buildMatched != null) {
                                buildMatched[r] = true;
                            }
                        }
                    }
                }
                if (!matchedAny && emitUnmatchedProbe) {
                    if (count == leftIdx.length) {
                        int newCap = leftIdx.length + (leftIdx.length >> 1) + 16;
                        leftIdx = Arrays.copyOf(leftIdx, newCap);
                        rightIdx = Arrays.copyOf(rightIdx, newCap);
                    }
                    leftIdx[count] = l;
                    rightIdx[count] = -1;
                    count++;
                }
            }
            return new ProbeChunk(leftIdx, rightIdx, count);
        }
    }

    // -------------------------------------------------------------------------
    // 3. StringColumnVector UTF-8 Byte Slice Specialized Hash Join Index
    // -------------------------------------------------------------------------

    private static final class StringHashJoinIndex extends HashJoinIndex {
        private static final int SSO_MAX_LEN = 16;
        private static final VarHandle LONG_LE =
                MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

        private final byte[] buildValues;
        private final int[] buildOffsets;
        private final int[] buildHashes;
        private final int[] buildLengths;
        private final long[] buildP1;
        private final long[] buildP2;

        private final byte[] probeValues;
        private final int[] probeOffsets;
        private final BitSet probeNulls;
        private final boolean probeHasNulls;
        private final int mask;
        private final int[] bucketHead;
        private final int[] nextRowIdx;

        private static long loadWord(byte[] b, int off, int len, int wordIdx) {
            int start = wordIdx << 3;
            int n = len - start;
            if (n <= 0) {
                return 0L;
            }
            int idx = off + start;
            if (n >= 8) {
                return (long) LONG_LE.get(b, idx);
            }
            if (idx + 8 <= b.length) {
                return ((long) LONG_LE.get(b, idx)) & (~0L >>> ((8 - n) << 3));
            }
            long v = 0L;
            for (int i = n - 1; i >= 0; i--) {
                v = (v << 8) | (b[idx + i] & 0xFFL);
            }
            return v;
        }

        StringHashJoinIndex(
                StringColumnVector buildVec,
                StringColumnVector probeVec,
                int buildSize,
                boolean parallel,
                ExecutorService executor,
                int threshold) {
            super(buildSize);
            byte[] bValues = buildVec.getValues();
            int[] bOffsets = buildVec.getOffsets();
            boolean bHasNulls = buildVec.hasNulls();
            BitSet bNulls = buildVec.getNulls();

            int[] bHashes = new int[buildSize];
            int[] bLengths = new int[buildSize];
            long[] bP1 = new long[buildSize];
            long[] bP2 = new long[buildSize];

            if (parallel && buildSize >= threshold && buildSize > 1) {
                ParallelVectorEngine.executeChunks(buildSize, executor, (chunkIdx, start, end) -> {
                    for (int r = start; r < end; r++) {
                        if (!bHasNulls || !bNulls.get(r)) {
                            int s = bOffsets[r];
                            int len = bOffsets[r + 1] - s;
                            bLengths[r] = len;
                            if (len <= SSO_MAX_LEN) {
                                long p1 = loadWord(bValues, s, len, 0);
                                long p2 = loadWord(bValues, s, len, 1);
                                bP1[r] = p1;
                                bP2[r] = p2;
                                bHashes[r] = StringColumnVector.hashShortWords(p1, p2, len);
                            } else {
                                bHashes[r] = StringColumnVector.hashBytes(bValues, s, len);
                            }
                        } else {
                            bLengths[r] = -1;
                        }
                    }
                    return null;
                });
            } else {
                for (int r = 0; r < buildSize; r++) {
                    if (!bHasNulls || !bNulls.get(r)) {
                        int s = bOffsets[r];
                        int len = bOffsets[r + 1] - s;
                        bLengths[r] = len;
                        if (len <= SSO_MAX_LEN) {
                            long p1 = loadWord(bValues, s, len, 0);
                            long p2 = loadWord(bValues, s, len, 1);
                            bP1[r] = p1;
                            bP2[r] = p2;
                            bHashes[r] = StringColumnVector.hashShortWords(p1, p2, len);
                        } else {
                            bHashes[r] = StringColumnVector.hashBytes(bValues, s, len);
                        }
                    } else {
                        bLengths[r] = -1;
                    }
                }
            }

            int cap = nextPowerOfTwo(Math.max(16, buildSize));
            int m = cap - 1;
            int[] heads = new int[cap];
            Arrays.fill(heads, -1);
            int[] next = new int[buildSize];
            boolean unique = true;

            for (int r = buildSize - 1; r >= 0; r--) {
                if (bHasNulls && bNulls.get(r)) {
                    next[r] = -1;
                    continue;
                }
                int h = bHashes[r];
                int b = h & m;
                int head = heads[b];
                if (unique && head != -1) {
                    int rLen = bLengths[r];
                    long rP1 = bP1[r];
                    long rP2 = bP2[r];
                    int rStart = (rLen > SSO_MAX_LEN) ? bOffsets[r] : 0;

                    for (int c = head; c != -1; c = next[c]) {
                        if (bHashes[c] == h && bLengths[c] == rLen) {
                            if (rLen <= SSO_MAX_LEN) {
                                if (bP1[c] == rP1 && bP2[c] == rP2) {
                                    unique = false;
                                    break;
                                }
                            } else {
                                int cStart = bOffsets[c];
                                if (Arrays.equals(bValues, rStart, rStart + rLen, bValues, cStart, cStart + rLen)) {
                                    unique = false;
                                    break;
                                }
                            }
                        }
                    }
                }
                next[r] = head;
                heads[b] = r;
            }

            this.uniqueBuildKeys = unique;
            this.buildValues = bValues;
            this.buildOffsets = bOffsets;
            this.buildHashes = bHashes;
            this.buildLengths = bLengths;
            this.buildP1 = bP1;
            this.buildP2 = bP2;
            this.probeValues = probeVec.getValues();
            this.probeOffsets = probeVec.getOffsets();
            this.probeHasNulls = probeVec.hasNulls();
            this.probeNulls = probeVec.getNulls();
            this.mask = m;
            this.bucketHead = heads;
            this.nextRowIdx = next;
        }

        @Override
        void probeUniqueLeft(int start, int end, int[] outRightIdx) {
            byte[] pValues = this.probeValues;
            int[] pOffsets = this.probeOffsets;
            byte[] bValues = this.buildValues;
            int[] bOffsets = this.buildOffsets;
            int[] bHashes = this.buildHashes;
            int[] bLengths = this.buildLengths;
            long[] bP1 = this.buildP1;
            long[] bP2 = this.buildP2;
            int[] heads = this.bucketHead;
            int[] next = this.nextRowIdx;
            int m = this.mask;

            if (!this.probeHasNulls) {
                for (int l = start; l < end; l++) {
                    int pStart = pOffsets[l];
                    int pLen = pOffsets[l + 1] - pStart;
                    int h;
                    long p1 = 0L;
                    long p2 = 0L;
                    if (pLen <= SSO_MAX_LEN) {
                        p1 = loadWord(pValues, pStart, pLen, 0);
                        p2 = loadWord(pValues, pStart, pLen, 1);
                        h = StringColumnVector.hashShortWords(p1, p2, pLen);
                    } else {
                        h = StringColumnVector.hashBytes(pValues, pStart, pLen);
                    }
                    int matched = -1;
                    if (pLen <= SSO_MAX_LEN) {
                        for (int r = heads[h & m]; r != -1; r = next[r]) {
                            if (bHashes[r] == h && bLengths[r] == pLen && bP1[r] == p1 && bP2[r] == p2) {
                                matched = r;
                                break;
                            }
                        }
                    } else {
                        for (int r = heads[h & m]; r != -1; r = next[r]) {
                            if (bHashes[r] == h && bLengths[r] == pLen) {
                                int bStart = bOffsets[r];
                                if (Arrays.equals(pValues, pStart, pStart + pLen, bValues, bStart, bStart + pLen)) {
                                    matched = r;
                                    break;
                                }
                            }
                        }
                    }
                    outRightIdx[l - start] = matched;
                }
            } else {
                BitSet pNulls = this.probeNulls;
                for (int l = start; l < end; l++) {
                    if (pNulls.get(l)) {
                        outRightIdx[l - start] = -1;
                        continue;
                    }
                    int pStart = pOffsets[l];
                    int pLen = pOffsets[l + 1] - pStart;
                    int h;
                    long p1 = 0L;
                    long p2 = 0L;
                    if (pLen <= SSO_MAX_LEN) {
                        p1 = loadWord(pValues, pStart, pLen, 0);
                        p2 = loadWord(pValues, pStart, pLen, 1);
                        h = StringColumnVector.hashShortWords(p1, p2, pLen);
                    } else {
                        h = StringColumnVector.hashBytes(pValues, pStart, pLen);
                    }
                    int matched = -1;
                    if (pLen <= SSO_MAX_LEN) {
                        for (int r = heads[h & m]; r != -1; r = next[r]) {
                            if (bHashes[r] == h && bLengths[r] == pLen && bP1[r] == p1 && bP2[r] == p2) {
                                matched = r;
                                break;
                            }
                        }
                    } else {
                        for (int r = heads[h & m]; r != -1; r = next[r]) {
                            if (bHashes[r] == h && bLengths[r] == pLen) {
                                int bStart = bOffsets[r];
                                if (Arrays.equals(pValues, pStart, pStart + pLen, bValues, bStart, bStart + pLen)) {
                                    matched = r;
                                    break;
                                }
                            }
                        }
                    }
                    outRightIdx[l - start] = matched;
                }
            }
        }

        @Override
        ProbeChunk probeInner(int start, int end) {
            int len = end - start;
            int[] leftIdx = new int[len];
            int[] rightIdx = new int[len];
            int count = 0;

            byte[] pValues = this.probeValues;
            int[] pOffsets = this.probeOffsets;
            byte[] bValues = this.buildValues;
            int[] bOffsets = this.buildOffsets;
            int[] bHashes = this.buildHashes;
            int[] bLengths = this.buildLengths;
            long[] bP1 = this.buildP1;
            long[] bP2 = this.buildP2;
            int[] heads = this.bucketHead;
            int[] next = this.nextRowIdx;
            int m = this.mask;

            if (this.uniqueBuildKeys && !this.probeHasNulls) {
                for (int l = start; l < end; l++) {
                    int pStart = pOffsets[l];
                    int pLen = pOffsets[l + 1] - pStart;
                    int h;
                    long p1 = 0L;
                    long p2 = 0L;
                    if (pLen <= SSO_MAX_LEN) {
                        p1 = loadWord(pValues, pStart, pLen, 0);
                        p2 = loadWord(pValues, pStart, pLen, 1);
                        h = StringColumnVector.hashShortWords(p1, p2, pLen);
                        for (int r = heads[h & m]; r != -1; r = next[r]) {
                            if (bHashes[r] == h && bLengths[r] == pLen && bP1[r] == p1 && bP2[r] == p2) {
                                leftIdx[count] = l;
                                rightIdx[count] = r;
                                count++;
                                break;
                            }
                        }
                    } else {
                        h = StringColumnVector.hashBytes(pValues, pStart, pLen);
                        for (int r = heads[h & m]; r != -1; r = next[r]) {
                            if (bHashes[r] == h && bLengths[r] == pLen) {
                                int bStart = bOffsets[r];
                                if (Arrays.equals(pValues, pStart, pStart + pLen, bValues, bStart, bStart + pLen)) {
                                    leftIdx[count] = l;
                                    rightIdx[count] = r;
                                    count++;
                                    break;
                                }
                            }
                        }
                    }
                }
            } else {
                boolean pHasNulls = this.probeHasNulls;
                BitSet pNulls = this.probeNulls;
                for (int l = start; l < end; l++) {
                    if (pHasNulls && pNulls.get(l)) {
                        continue;
                    }
                    int pStart = pOffsets[l];
                    int pLen = pOffsets[l + 1] - pStart;
                    int h;
                    long p1 = 0L;
                    long p2 = 0L;
                    if (pLen <= SSO_MAX_LEN) {
                        p1 = loadWord(pValues, pStart, pLen, 0);
                        p2 = loadWord(pValues, pStart, pLen, 1);
                        h = StringColumnVector.hashShortWords(p1, p2, pLen);
                        for (int r = heads[h & m]; r != -1; r = next[r]) {
                            if (bHashes[r] == h && bLengths[r] == pLen && bP1[r] == p1 && bP2[r] == p2) {
                                if (count == leftIdx.length) {
                                    int newCap = leftIdx.length + (leftIdx.length >> 1) + 16;
                                    leftIdx = Arrays.copyOf(leftIdx, newCap);
                                    rightIdx = Arrays.copyOf(rightIdx, newCap);
                                }
                                leftIdx[count] = l;
                                rightIdx[count] = r;
                                count++;
                            }
                        }
                    } else {
                        h = StringColumnVector.hashBytes(pValues, pStart, pLen);
                        for (int r = heads[h & m]; r != -1; r = next[r]) {
                            if (bHashes[r] == h && bLengths[r] == pLen) {
                                int bStart = bOffsets[r];
                                if (Arrays.equals(pValues, pStart, pStart + pLen, bValues, bStart, bStart + pLen)) {
                                    if (count == leftIdx.length) {
                                        int newCap = leftIdx.length + (leftIdx.length >> 1) + 16;
                                        leftIdx = Arrays.copyOf(leftIdx, newCap);
                                        rightIdx = Arrays.copyOf(rightIdx, newCap);
                                    }
                                    leftIdx[count] = l;
                                    rightIdx[count] = r;
                                    count++;
                                }
                            }
                        }
                    }
                }
            }
            return new ProbeChunk(leftIdx, rightIdx, count);
        }

        @Override
        ProbeChunk probeOuter(int start, int end, boolean emitUnmatchedProbe, boolean[] buildMatched) {
            int len = end - start;
            int[] leftIdx = new int[len];
            int[] rightIdx = new int[len];
            int count = 0;

            byte[] pValues = this.probeValues;
            int[] pOffsets = this.probeOffsets;
            byte[] bValues = this.buildValues;
            int[] bOffsets = this.buildOffsets;
            int[] bHashes = this.buildHashes;
            int[] bLengths = this.buildLengths;
            long[] bP1 = this.buildP1;
            long[] bP2 = this.buildP2;
            int[] heads = this.bucketHead;
            int[] next = this.nextRowIdx;
            int m = this.mask;
            boolean pHasNulls = this.probeHasNulls;
            BitSet pNulls = this.probeNulls;

            for (int l = start; l < end; l++) {
                boolean matchedAny = false;
                if (!pHasNulls || !pNulls.get(l)) {
                    int pStart = pOffsets[l];
                    int pLen = pOffsets[l + 1] - pStart;
                    int h;
                    long p1 = 0L;
                    long p2 = 0L;
                    if (pLen <= SSO_MAX_LEN) {
                        p1 = loadWord(pValues, pStart, pLen, 0);
                        p2 = loadWord(pValues, pStart, pLen, 1);
                        h = StringColumnVector.hashShortWords(p1, p2, pLen);
                        for (int r = heads[h & m]; r != -1; r = next[r]) {
                            if (bHashes[r] == h && bLengths[r] == pLen && bP1[r] == p1 && bP2[r] == p2) {
                                if (count == leftIdx.length) {
                                    int newCap = leftIdx.length + (leftIdx.length >> 1) + 16;
                                    leftIdx = Arrays.copyOf(leftIdx, newCap);
                                    rightIdx = Arrays.copyOf(rightIdx, newCap);
                                }
                                leftIdx[count] = l;
                                rightIdx[count] = r;
                                count++;
                                matchedAny = true;
                                if (buildMatched != null) {
                                    buildMatched[r] = true;
                                }
                            }
                        }
                    } else {
                        h = StringColumnVector.hashBytes(pValues, pStart, pLen);
                        for (int r = heads[h & m]; r != -1; r = next[r]) {
                            if (bHashes[r] == h && bLengths[r] == pLen) {
                                int bStart = bOffsets[r];
                                if (Arrays.equals(pValues, pStart, pStart + pLen, bValues, bStart, bStart + pLen)) {
                                    if (count == leftIdx.length) {
                                        int newCap = leftIdx.length + (leftIdx.length >> 1) + 16;
                                        leftIdx = Arrays.copyOf(leftIdx, newCap);
                                        rightIdx = Arrays.copyOf(rightIdx, newCap);
                                    }
                                    leftIdx[count] = l;
                                    rightIdx[count] = r;
                                    count++;
                                    matchedAny = true;
                                    if (buildMatched != null) {
                                        buildMatched[r] = true;
                                    }
                                }
                            }
                        }
                    }
                }
                if (!matchedAny && emitUnmatchedProbe) {
                    if (count == leftIdx.length) {
                        int newCap = leftIdx.length + (leftIdx.length >> 1) + 16;
                        leftIdx = Arrays.copyOf(leftIdx, newCap);
                        rightIdx = Arrays.copyOf(rightIdx, newCap);
                    }
                    leftIdx[count] = l;
                    rightIdx[count] = -1;
                    count++;
                }
            }
            return new ProbeChunk(leftIdx, rightIdx, count);
        }
    }

    // -------------------------------------------------------------------------
    // 4. DictionaryColumnVector Direct-Code Specialized Hash Join Index
    // -------------------------------------------------------------------------

    private static final class DictionaryHashJoinIndex extends HashJoinIndex {
        private final DictionaryColumnVector probeVec;
        private final int probeDictSize;
        private final int[] codeHead;
        private final int[] nextRowIdx;

        DictionaryHashJoinIndex(DictionaryColumnVector buildVec, DictionaryColumnVector probeVec, int buildSize) {
            super(buildSize);
            int pDictSize = probeVec.getDictionarySize();
            int bDictSize = buildVec.getDictionarySize();
            int[] buildCodeToProbeCode = new int[bDictSize];
            for (int bCode = 0; bCode < bDictSize; bCode++) {
                String cat = buildVec.getCategory(bCode);
                buildCodeToProbeCode[bCode] = probeVec.indexOfCategory(cat);
            }

            int[] heads = new int[Math.max(1, pDictSize)];
            Arrays.fill(heads, -1);
            int[] next = new int[buildSize];
            boolean unique = true;

            for (int r = buildSize - 1; r >= 0; r--) {
                int bCode = buildVec.getCode(r);
                if (bCode < 0) {
                    next[r] = -1;
                    continue;
                }
                int pCode = buildCodeToProbeCode[bCode];
                if (pCode < 0) {
                    next[r] = -1;
                    continue;
                }
                int head = heads[pCode];
                if (head != -1) {
                    unique = false;
                }
                next[r] = head;
                heads[pCode] = r;
            }

            this.uniqueBuildKeys = unique;
            this.probeVec = probeVec;
            this.probeDictSize = pDictSize;
            this.codeHead = heads;
            this.nextRowIdx = next;
        }

        @Override
        void probeUniqueLeft(int start, int end, int[] outRightIdx) {
            DictionaryColumnVector pVec = this.probeVec;
            int pDictSize = this.probeDictSize;
            int[] heads = this.codeHead;
            for (int l = start; l < end; l++) {
                int pCode = pVec.getCode(l);
                outRightIdx[l - start] = (pCode >= 0 && pCode < pDictSize) ? heads[pCode] : -1;
            }
        }

        @Override
        ProbeChunk probeInner(int start, int end) {
            int len = end - start;
            int[] leftIdx = new int[len];
            int[] rightIdx = new int[len];
            int count = 0;

            DictionaryColumnVector pVec = this.probeVec;
            int pDictSize = this.probeDictSize;
            int[] heads = this.codeHead;
            int[] next = this.nextRowIdx;

            for (int l = start; l < end; l++) {
                int pCode = pVec.getCode(l);
                if (pCode >= 0 && pCode < pDictSize) {
                    for (int r = heads[pCode]; r != -1; r = next[r]) {
                        if (count == leftIdx.length) {
                            int newCap = leftIdx.length + (leftIdx.length >> 1) + 16;
                            leftIdx = Arrays.copyOf(leftIdx, newCap);
                            rightIdx = Arrays.copyOf(rightIdx, newCap);
                        }
                        leftIdx[count] = l;
                        rightIdx[count] = r;
                        count++;
                    }
                }
            }
            return new ProbeChunk(leftIdx, rightIdx, count);
        }

        @Override
        ProbeChunk probeOuter(int start, int end, boolean emitUnmatchedProbe, boolean[] buildMatched) {
            int len = end - start;
            int[] leftIdx = new int[len];
            int[] rightIdx = new int[len];
            int count = 0;

            DictionaryColumnVector pVec = this.probeVec;
            int pDictSize = this.probeDictSize;
            int[] heads = this.codeHead;
            int[] next = this.nextRowIdx;

            for (int l = start; l < end; l++) {
                boolean matchedAny = false;
                int pCode = pVec.getCode(l);
                if (pCode >= 0 && pCode < pDictSize) {
                    for (int r = heads[pCode]; r != -1; r = next[r]) {
                        if (count == leftIdx.length) {
                            int newCap = leftIdx.length + (leftIdx.length >> 1) + 16;
                            leftIdx = Arrays.copyOf(leftIdx, newCap);
                            rightIdx = Arrays.copyOf(rightIdx, newCap);
                        }
                        leftIdx[count] = l;
                        rightIdx[count] = r;
                        count++;
                        matchedAny = true;
                        if (buildMatched != null) {
                            buildMatched[r] = true;
                        }
                    }
                }
                if (!matchedAny && emitUnmatchedProbe) {
                    if (count == leftIdx.length) {
                        int newCap = leftIdx.length + (leftIdx.length >> 1) + 16;
                        leftIdx = Arrays.copyOf(leftIdx, newCap);
                        rightIdx = Arrays.copyOf(rightIdx, newCap);
                    }
                    leftIdx[count] = l;
                    rightIdx[count] = -1;
                    count++;
                }
            }
            return new ProbeChunk(leftIdx, rightIdx, count);
        }
    }

    // -------------------------------------------------------------------------
    // 5. Mixed DictionaryColumnVector & StringColumnVector Specialized Hash Join Indexes
    // -------------------------------------------------------------------------

    private static final class DictBuildStringProbeHashJoinIndex extends HashJoinIndex {
        private final DictionaryColumnVector buildVec;
        private final StringColumnVector probeVec;
        private final int dictSize;
        private final int[] codeHead;
        private final int[] nextRowIdx;

        DictBuildStringProbeHashJoinIndex(DictionaryColumnVector buildVec, StringColumnVector probeVec, int buildSize) {
            super(buildSize);
            this.buildVec = buildVec;
            this.probeVec = probeVec;
            int dSize = buildVec.getDictionarySize();
            this.dictSize = dSize;

            int[] heads = new int[Math.max(1, dSize)];
            Arrays.fill(heads, -1);
            int[] next = new int[buildSize];
            boolean unique = true;

            for (int r = buildSize - 1; r >= 0; r--) {
                int bCode = buildVec.getCode(r);
                if (bCode < 0) {
                    next[r] = -1;
                    continue;
                }
                int head = heads[bCode];
                if (head != -1) {
                    unique = false;
                }
                next[r] = head;
                heads[bCode] = r;
            }

            this.uniqueBuildKeys = unique;
            this.codeHead = heads;
            this.nextRowIdx = next;
        }

        @Override
        void probeUniqueLeft(int start, int end, int[] outRightIdx) {
            StringColumnVector pVec = this.probeVec;
            DictionaryColumnVector bVec = this.buildVec;
            int dSize = this.dictSize;
            int[] heads = this.codeHead;
            boolean pHasNulls = pVec.hasNulls();
            BitSet pNulls = pVec.getNulls();

            for (int l = start; l < end; l++) {
                if (pHasNulls && pNulls.get(l)) {
                    outRightIdx[l - start] = -1;
                    continue;
                }
                String s = pVec.getString(l);
                int code = (s != null) ? bVec.encode(s) : -1;
                outRightIdx[l - start] = (code >= 0 && code < dSize) ? heads[code] : -1;
            }
        }

        @Override
        ProbeChunk probeInner(int start, int end) {
            int len = end - start;
            int[] leftIdx = new int[len];
            int[] rightIdx = new int[len];
            int count = 0;

            StringColumnVector pVec = this.probeVec;
            DictionaryColumnVector bVec = this.buildVec;
            int dSize = this.dictSize;
            int[] heads = this.codeHead;
            int[] next = this.nextRowIdx;
            boolean pHasNulls = pVec.hasNulls();
            BitSet pNulls = pVec.getNulls();

            for (int l = start; l < end; l++) {
                if (pHasNulls && pNulls.get(l)) {
                    continue;
                }
                String s = pVec.getString(l);
                int code = (s != null) ? bVec.encode(s) : -1;
                if (code >= 0 && code < dSize) {
                    for (int r = heads[code]; r != -1; r = next[r]) {
                        if (count == leftIdx.length) {
                            int newCap = leftIdx.length + (leftIdx.length >> 1) + 16;
                            leftIdx = Arrays.copyOf(leftIdx, newCap);
                            rightIdx = Arrays.copyOf(rightIdx, newCap);
                        }
                        leftIdx[count] = l;
                        rightIdx[count] = r;
                        count++;
                    }
                }
            }
            return new ProbeChunk(leftIdx, rightIdx, count);
        }

        @Override
        ProbeChunk probeOuter(int start, int end, boolean emitUnmatchedProbe, boolean[] buildMatched) {
            int len = end - start;
            int[] leftIdx = new int[len];
            int[] rightIdx = new int[len];
            int count = 0;

            StringColumnVector pVec = this.probeVec;
            DictionaryColumnVector bVec = this.buildVec;
            int dSize = this.dictSize;
            int[] heads = this.codeHead;
            int[] next = this.nextRowIdx;
            boolean pHasNulls = pVec.hasNulls();
            BitSet pNulls = pVec.getNulls();

            for (int l = start; l < end; l++) {
                boolean matchedAny = false;
                if (!pHasNulls || !pNulls.get(l)) {
                    String s = pVec.getString(l);
                    int code = (s != null) ? bVec.encode(s) : -1;
                    if (code >= 0 && code < dSize) {
                        for (int r = heads[code]; r != -1; r = next[r]) {
                            if (count == leftIdx.length) {
                                int newCap = leftIdx.length + (leftIdx.length >> 1) + 16;
                                leftIdx = Arrays.copyOf(leftIdx, newCap);
                                rightIdx = Arrays.copyOf(rightIdx, newCap);
                            }
                            leftIdx[count] = l;
                            rightIdx[count] = r;
                            count++;
                            matchedAny = true;
                            if (buildMatched != null) {
                                buildMatched[r] = true;
                            }
                        }
                    }
                }
                if (!matchedAny && emitUnmatchedProbe) {
                    if (count == leftIdx.length) {
                        int newCap = leftIdx.length + (leftIdx.length >> 1) + 16;
                        leftIdx = Arrays.copyOf(leftIdx, newCap);
                        rightIdx = Arrays.copyOf(rightIdx, newCap);
                    }
                    leftIdx[count] = l;
                    rightIdx[count] = -1;
                    count++;
                }
            }
            return new ProbeChunk(leftIdx, rightIdx, count);
        }
    }

    private static final class StringBuildDictProbeHashJoinIndex extends HashJoinIndex {
        private final StringHashJoinIndex delegate;
        private final DictionaryColumnVector probeVec;

        StringBuildDictProbeHashJoinIndex(
                StringColumnVector buildVec,
                DictionaryColumnVector probeVec,
                int buildSize,
                boolean parallel,
                ExecutorService executor,
                int threshold) {
            super(buildSize);
            this.probeVec = probeVec;
            StringColumnVector probeAsStr = probeVec.toStringColumnVector();
            this.delegate = new StringHashJoinIndex(buildVec, probeAsStr, buildSize, parallel, executor, threshold);
            this.uniqueBuildKeys = delegate.uniqueBuildKeys;
        }

        @Override
        void probeUniqueLeft(int start, int end, int[] outRightIdx) {
            delegate.probeUniqueLeft(start, end, outRightIdx);
        }

        @Override
        ProbeChunk probeInner(int start, int end) {
            return delegate.probeInner(start, end);
        }

        @Override
        ProbeChunk probeOuter(int start, int end, boolean emitUnmatchedProbe, boolean[] buildMatched) {
            return delegate.probeOuter(start, end, emitUnmatchedProbe, buildMatched);
        }
    }

    // -------------------------------------------------------------------------
    // 6. General / Multi-Column / Cross-Type Columnar Hash Join Index
    // -------------------------------------------------------------------------

    private static final class GeneralColumnarHashJoinIndex extends HashJoinIndex {
        private final ColumnVector[] buildKeyVecs;
        private final ColumnVector[] probeKeyVecs;
        private final int[] buildHashes;
        private final int mask;
        private final int[] bucketHead;
        private final int[] nextRowIdx;

        GeneralColumnarHashJoinIndex(ColumnVector[] buildKeyVecs, ColumnVector[] probeKeyVecs, int buildSize) {
            super(buildSize);
            int cap = nextPowerOfTwo(Math.max(16, buildSize));
            int m = cap - 1;
            int[] heads = new int[cap];
            Arrays.fill(heads, -1);
            int[] next = new int[buildSize];
            int[] bHashes = new int[buildSize];
            boolean unique = true;

            for (int r = buildSize - 1; r >= 0; r--) {
                if (hasAnyNull(buildKeyVecs, r)) {
                    next[r] = -1;
                    continue;
                }
                int h = computeCompositeHash(buildKeyVecs, r);
                bHashes[r] = h;
                int b = h & m;
                int head = heads[b];
                if (unique && head != -1) {
                    for (int c = head; c != -1; c = next[c]) {
                        if (bHashes[c] == h && rowsEqualAcrossVectors(buildKeyVecs, r, buildKeyVecs, c)) {
                            unique = false;
                            break;
                        }
                    }
                }
                next[r] = head;
                heads[b] = r;
            }

            this.uniqueBuildKeys = unique;
            this.buildKeyVecs = buildKeyVecs;
            this.probeKeyVecs = probeKeyVecs;
            this.buildHashes = bHashes;
            this.mask = m;
            this.bucketHead = heads;
            this.nextRowIdx = next;
        }

        @Override
        void probeUniqueLeft(int start, int end, int[] outRightIdx) {
            ColumnVector[] pVecs = this.probeKeyVecs;
            ColumnVector[] bVecs = this.buildKeyVecs;
            int[] bHashes = this.buildHashes;
            int[] heads = this.bucketHead;
            int[] next = this.nextRowIdx;
            int m = this.mask;

            for (int l = start; l < end; l++) {
                if (hasAnyNull(pVecs, l)) {
                    outRightIdx[l - start] = -1;
                    continue;
                }
                int h = computeCompositeHash(pVecs, l);
                int matched = -1;
                for (int r = heads[h & m]; r != -1; r = next[r]) {
                    if (bHashes[r] == h && rowsEqualAcrossVectors(pVecs, l, bVecs, r)) {
                        matched = r;
                        break;
                    }
                }
                outRightIdx[l - start] = matched;
            }
        }

        @Override
        ProbeChunk probeInner(int start, int end) {
            int len = end - start;
            int[] leftIdx = new int[len];
            int[] rightIdx = new int[len];
            int count = 0;

            ColumnVector[] pVecs = this.probeKeyVecs;
            ColumnVector[] bVecs = this.buildKeyVecs;
            int[] bHashes = this.buildHashes;
            int[] heads = this.bucketHead;
            int[] next = this.nextRowIdx;
            int m = this.mask;
            boolean unique = this.uniqueBuildKeys;

            for (int l = start; l < end; l++) {
                if (hasAnyNull(pVecs, l)) {
                    continue;
                }
                int h = computeCompositeHash(pVecs, l);
                for (int r = heads[h & m]; r != -1; r = next[r]) {
                    if (bHashes[r] == h && rowsEqualAcrossVectors(pVecs, l, bVecs, r)) {
                        if (count == leftIdx.length) {
                            int newCap = leftIdx.length + (leftIdx.length >> 1) + 16;
                            leftIdx = Arrays.copyOf(leftIdx, newCap);
                            rightIdx = Arrays.copyOf(rightIdx, newCap);
                        }
                        leftIdx[count] = l;
                        rightIdx[count] = r;
                        count++;
                        if (unique) {
                            break;
                        }
                    }
                }
            }
            return new ProbeChunk(leftIdx, rightIdx, count);
        }

        @Override
        ProbeChunk probeOuter(int start, int end, boolean emitUnmatchedProbe, boolean[] buildMatched) {
            int len = end - start;
            int[] leftIdx = new int[len];
            int[] rightIdx = new int[len];
            int count = 0;

            ColumnVector[] pVecs = this.probeKeyVecs;
            ColumnVector[] bVecs = this.buildKeyVecs;
            int[] bHashes = this.buildHashes;
            int[] heads = this.bucketHead;
            int[] next = this.nextRowIdx;
            int m = this.mask;

            for (int l = start; l < end; l++) {
                boolean matchedAny = false;
                if (!hasAnyNull(pVecs, l)) {
                    int h = computeCompositeHash(pVecs, l);
                    for (int r = heads[h & m]; r != -1; r = next[r]) {
                        if (bHashes[r] == h && rowsEqualAcrossVectors(pVecs, l, bVecs, r)) {
                            if (count == leftIdx.length) {
                                int newCap = leftIdx.length + (leftIdx.length >> 1) + 16;
                                leftIdx = Arrays.copyOf(leftIdx, newCap);
                                rightIdx = Arrays.copyOf(rightIdx, newCap);
                            }
                            leftIdx[count] = l;
                            rightIdx[count] = r;
                            count++;
                            matchedAny = true;
                            if (buildMatched != null) {
                                buildMatched[r] = true;
                            }
                        }
                    }
                }
                if (!matchedAny && emitUnmatchedProbe) {
                    if (count == leftIdx.length) {
                        int newCap = leftIdx.length + (leftIdx.length >> 1) + 16;
                        leftIdx = Arrays.copyOf(leftIdx, newCap);
                        rightIdx = Arrays.copyOf(rightIdx, newCap);
                    }
                    leftIdx[count] = l;
                    rightIdx[count] = -1;
                    count++;
                }
            }
            return new ProbeChunk(leftIdx, rightIdx, count);
        }
    }

    // =========================================================================
    // Low-Level Columnar Hashing & Key Equality Helpers
    // =========================================================================

    private static boolean hasAnyNull(ColumnVector[] vecs, int row) {
        for (int i = 0; i < vecs.length; i++) {
            ColumnVector v = vecs[i];
            if (v == null || row >= v.size() || v.isNull(row)) {
                return true;
            }
        }
        return false;
    }

    private static int computeCompositeHash(ColumnVector[] vecs, int row) {
        int h = 1;
        for (int i = 0; i < vecs.length; i++) {
            h = 31 * h + hashCell(vecs[i], row);
        }
        return mixInt(h);
    }

    private static int hashCell(ColumnVector vec, int row) {
        if (vec instanceof IntColumnVector icv) {
            return Long.hashCode(icv.getRawData()[row]);
        }
        if (vec instanceof LongColumnVector lcv) {
            return Long.hashCode(lcv.getRawData()[row]);
        }
        if (vec instanceof StringColumnVector scv) {
            return scv.hashRow(row);
        }
        if (vec instanceof DoubleColumnVector dcv) {
            double d = dcv.getRawData()[row];
            if (!Double.isNaN(d) && !Double.isInfinite(d) && d == (long) d) {
                return Long.hashCode((long) d);
            }
            return Double.hashCode(d);
        }
        if (vec instanceof FloatColumnVector fcv) {
            double d = fcv.getRawData()[row];
            if (!Double.isNaN(d) && !Double.isInfinite(d) && d == (long) d) {
                return Long.hashCode((long) d);
            }
            return Double.hashCode(d);
        }
        if (vec instanceof BooleanColumnVector bcv) {
            return bcv.getRawData()[row] ? 1231 : 1237;
        }
        if (vec instanceof DictionaryColumnVector dcv) {
            String s = dcv.getString(row);
            if (s == null) {
                return 0;
            }
            byte[] utf8 = s.getBytes( java.nio.charset.StandardCharsets.UTF_8);
            return StringColumnVector.hashBytes(utf8, 0, utf8.length);
        }
        Object val = vec.getObject(row);
        Object canonical = canonicalKey(val);
        if (canonical instanceof String s) {
            byte[] utf8 = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return StringColumnVector.hashBytes(utf8, 0, utf8.length);
        }
        return canonical != null ? canonical.hashCode() : 0;
    }

    private static boolean rowsEqualAcrossVectors(ColumnVector[] vecsA, int rowA, ColumnVector[] vecsB, int rowB) {
        for (int i = 0; i < vecsA.length; i++) {
            if (!cellsEqual(vecsA[i], rowA, vecsB[i], rowB)) {
                return false;
            }
        }
        return true;
    }

    private static boolean cellsEqual(ColumnVector vecA, int rowA, ColumnVector vecB, int rowB) {
        if (vecA instanceof IntColumnVector icvA && vecB instanceof IntColumnVector icvB) {
            return icvA.getRawData()[rowA] == icvB.getRawData()[rowB];
        }
        if (vecA instanceof LongColumnVector lcvA && vecB instanceof LongColumnVector lcvB) {
            return lcvA.getRawData()[rowA] == lcvB.getRawData()[rowB];
        }
        if (vecA instanceof StringColumnVector scvA && vecB instanceof StringColumnVector scvB) {
            int sA = scvA.getOffsets()[rowA];
            int lenA = scvA.getOffsets()[rowA + 1] - sA;
            int sB = scvB.getOffsets()[rowB];
            int lenB = scvB.getOffsets()[rowB + 1] - sB;
            return lenA == lenB && Arrays.equals(
                scvA.getValues(), sA, sA + lenA,
                scvB.getValues(), sB, sB + lenB
            );
        }
        if (vecA instanceof IntColumnVector icvA && vecB instanceof LongColumnVector lcvB) {
            return (long) icvA.getRawData()[rowA] == lcvB.getRawData()[rowB];
        }
        if (vecA instanceof LongColumnVector lcvA && vecB instanceof IntColumnVector icvB) {
            return lcvA.getRawData()[rowA] == (long) icvB.getRawData()[rowB];
        }
        if (vecA instanceof BooleanColumnVector bcvA && vecB instanceof BooleanColumnVector bcvB) {
            return bcvA.getRawData()[rowA] == bcvB.getRawData()[rowB];
        }
        return keysEqual(vecA.getObject(rowA), vecB.getObject(rowB));
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

    private static int mixInt(int k) {
        int h = k * 0x9E3779B9;
        return h ^ (h >>> 16);
    }

    private static int mixLong(long k) {
        long h = (k ^ (k >>> 32)) * 0x9E3779B97F4A7C15L;
        return (int) (h ^ (h >>> 32));
    }

    private static int nextPowerOfTwo(int n) {
        if (n <= 1) {
            return 1;
        }
        return 1 << (32 - Integer.numberOfLeadingZeros(n - 1));
    }

    // =========================================================================
    // As-Of Join Implementation (Issue #283)
    // =========================================================================

    public static IDataFrame asOfJoin(
            IDataFrame left,
            IDataFrame right,
            String leftOn,
            String rightOn,
            List<String> leftBy,
            List<String> rightBy,
            AsOfDirection direction,
            String leftSuffix,
            String rightSuffix) {

        if (left == null) {
            throw new IllegalArgumentException("Left DataFrame cannot be null");
        }
        if (right == null) {
            throw new IllegalArgumentException("Right DataFrame cannot be null");
        }
        if (leftOn == null || leftOn.isBlank()) {
            throw new IllegalArgumentException("leftOn column must be specified for asOfJoin");
        }
        if (rightOn == null || rightOn.isBlank()) {
            throw new IllegalArgumentException("rightOn column must be specified for asOfJoin");
        }

        if (left.getColumnHeader(leftOn) == null) {
            throw new SchemaMismatchException("Column '" + leftOn + "' does not exist in left DataFrame");
        }
        if (right.getColumnHeader(rightOn) == null) {
            throw new SchemaMismatchException("Column '" + rightOn + "' does not exist in right DataFrame");
        }

        List<String> lBy = leftBy != null ? leftBy : Collections.emptyList();
        List<String> rBy = rightBy != null ? rightBy : Collections.emptyList();

        if (lBy.size() != rBy.size()) {
            throw new IllegalArgumentException("leftBy and rightBy must have the same number of columns, got "
                    + lBy.size() + " and " + rBy.size());
        }

        for (String col : lBy) {
            if (left.getColumnHeader(col) == null) {
                throw new SchemaMismatchException("Column '" + col + "' does not exist in left DataFrame");
            }
        }
        for (String col : rBy) {
            if (right.getColumnHeader(col) == null) {
                throw new SchemaMismatchException("Column '" + col + "' does not exist in right DataFrame");
            }
        }

        AsOfDirection dir = direction != null ? direction : AsOfDirection.BACKWARD;
        String lSuffix = leftSuffix != null ? leftSuffix : "_left";
        String rSuffix = rightSuffix != null ? rightSuffix : "_right";

        return executeAsOfJoin(left, right, leftOn, rightOn, lBy, rBy, dir, lSuffix, rSuffix);
    }

    private static IDataFrame executeAsOfJoin(
            IDataFrame left,
            IDataFrame right,
            String leftOn,
            String rightOn,
            List<String> leftBy,
            List<String> rightBy,
            AsOfDirection direction,
            String lSuffix,
            String rSuffix) {

        int leftSize = left.size();
        int rightSize = right.size();

        List<Column> leftHeaders = left.getHeaders() != null ? left.getHeaders() : Collections.emptyList();
        List<Column> rightHeaders = right.getHeaders() != null ? right.getHeaders() : Collections.emptyList();

        Set<String> sharedRightKeys = new HashSet<>();
        if (leftOn.equals(rightOn)) {
            sharedRightKeys.add(rightOn);
        }
        for (int i = 0; i < leftBy.size(); i++) {
            if (leftBy.get(i).equals(rightBy.get(i))) {
                sharedRightKeys.add(rightBy.get(i));
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

        ArrayList<Column> outputHeaders = new ArrayList<>(leftHeaders.size() + rightHeaders.size());
        List<OutputColSpec> outputSpecs = new ArrayList<>(leftHeaders.size() + rightHeaders.size());
        Set<String> seenOutputNames = new HashSet<>();

        // Add left columns (preserved as in left table)
        for (Column col : leftHeaders) {
            String lName = col.columnName();
            String outName = leftOutMap.get(lName);
            if (!seenOutputNames.add(outName)) {
                throw new IllegalArgumentException("Duplicate column name in joined DataFrame: '" + outName + "'");
            }
            Class<?> colType = col.columnType();
            ColumnVector leftVec = left.getColumnVector( lName);
            outputHeaders.add(new Column(outName, colType));
            outputSpecs.add(new OutputColSpec(outName, colType, leftVec != null && leftVec.isNullable(), leftVec, null, true, false));
        }

        // Add right columns (unmatched right rows become null in left-outer semantics)
        for (Column col : rightHeaders) {
            String rName = col.columnName();
            if (sharedRightKeys.contains(rName)) {
                continue;
            }
            String outName = rightOutMap.get(rName);
            if (!seenOutputNames.add(outName)) {
                throw new IllegalArgumentException("Duplicate column name in joined DataFrame: '" + outName + "'");
            }
            Class<?> colType = col.columnType();
            ColumnVector rightVec = right.getColumnVector( rName);
            outputHeaders.add(new Column(outName, colType));
            outputSpecs.add(new OutputColSpec(outName, colType, true, rightVec, null, false, true));
        }

        if (leftSize == 0) {
            return createEmptyResult(left, outputHeaders, outputSpecs, leftOutMap);
        }

        boolean parallel = left.isParallel() || right.isParallel();
        ExecutorService executor = left.isParallel() ? left.getExecutor() : right.getExecutor();
        int threshold = Math.min(left.getParallelThreshold(), right.getParallelThreshold());

        List<ColumnVector> resultVectors = executeAsOfJoinProbe(
                left, right, leftSize, rightSize, leftOn, rightOn, leftBy, rightBy,
                direction, outputSpecs, parallel, executor, threshold);

        DataFrame result = DataFrame.fromHeadersAndVectors(outputHeaders, resultVectors);
        if (left.getIndex() != null && leftOutMap.containsKey(left.getIndex())) {
            result.setIndex(leftOutMap.get(left.getIndex()));
        }
        return result;
    }

    private static List<ColumnVector> executeAsOfJoinProbe(
            IDataFrame left,
            IDataFrame right,
            int leftSize,
            int rightSize,
            String leftOn,
            String rightOn,
            List<String> leftBy,
            List<String> rightBy,
            AsOfDirection direction,
            List<OutputColSpec> outputSpecs,
            boolean parallel,
            ExecutorService executor,
            int threshold) {

        int numCols = outputSpecs.size();
        ColumnVector[] resultVectors = new ColumnVector[numCols];

        List<Integer> rightPrimCols = new ArrayList<>(numCols);
        List<Integer> rightStrCols = new ArrayList<>(numCols);

        for (int c = 0; c < numCols; c++) {
            OutputColSpec spec = outputSpecs.get(c);
            if (spec.fromLeft) {
                ColumnVector copied = spec.primaryVec.copy();
                copied.setName(spec.outName);
                copied.setNullable(spec.nullable);
                resultVectors[c] = copied;
            } else if (spec.primaryVec instanceof StringColumnVector) {
                rightStrCols.add(c);
            } else {
                ColumnVector outVec = createOutputVector(spec, leftSize);
                outVec.setSizeUnchecked(leftSize);
                resultVectors[c] = outVec;
                rightPrimCols.add(c);
            }
        }

        ColumnVector leftOnVec = left.getColumnVector( leftOn);
        ColumnVector rightOnVec = right.getColumnVector( rightOn);

        ColumnVector[] leftByVecs = extractKeyVectors(left, leftBy);
        ColumnVector[] rightByVecs = extractKeyVectors(right, rightBy);

        AsOfProbeMatcher matcher = AsOfProbeMatcher.create(
                leftOnVec, rightOnVec, leftByVecs, rightByVecs, rightSize, direction);

        boolean runParallel = parallel && leftSize >= threshold && leftSize > 1;
        ExecutorService pool = ParallelVectorEngine.resolveExecutor(executor);
        int numChunks = runParallel ? ParallelVectorEngine.computeChunkCount(leftSize, pool) : 1;
        int[][] ranges = ParallelVectorEngine.partitionRanges(leftSize, numChunks);

        int numRightStr = rightStrCols.size();
        int[][] strChunkByteCounts = numRightStr > 0 ? new int[numRightStr][ranges.length] : null;

        List<int[]> rightIndexChunks = ParallelVectorEngine.executeRanges(ranges, pool, (chunkIdx, start, end) -> {
            int len = end - start;
            int[] rightIdx = new int[len];
            matcher.matchRange(start, end, rightIdx);

            for (int i = 0; i < rightPrimCols.size(); i++) {
                int colIdx = rightPrimCols.get(i);
                OutputColSpec spec = outputSpecs.get(colIdx);
                resultVectors[colIdx].copyNullableIndexedValuesOnly(spec.primaryVec, rightIdx, len, start);
            }

            for (int s = 0; s < numRightStr; s++) {
                int colIdx = rightStrCols.get(s);
                StringColumnVector strSrc = (StringColumnVector) outputSpecs.get(colIdx).primaryVec;
                strChunkByteCounts[s][chunkIdx] = strSrc.computeNullableIndexedByteCount(rightIdx, len);
            }
            return rightIdx;
        });

        if (numRightStr > 0) {
            int[][] strByteOffsets = new int[numRightStr][ranges.length];
            for (int s = 0; s < numRightStr; s++) {
                int colIdx = rightStrCols.get(s);
                OutputColSpec spec = outputSpecs.get(colIdx);
                int totalBytes = ParallelVectorEngine.computePrefixSums(strChunkByteCounts[s], strByteOffsets[s]);
                resultVectors[colIdx] = StringColumnVector.allocateForCompaction(
                        spec.outName, spec.nullable, leftSize, totalBytes);
            }

            ParallelVectorEngine.executeRanges(ranges, pool, (chunkIdx, start, end) -> {
                int len = end - start;
                int[] rightIdx = rightIndexChunks.get(chunkIdx);
                for (int s = 0; s < numRightStr; s++) {
                    int colIdx = rightStrCols.get(s);
                    StringColumnVector srcStr = (StringColumnVector) outputSpecs.get(colIdx).primaryVec;
                    StringColumnVector dstStr = (StringColumnVector) resultVectors[colIdx];
                    dstStr.copyNullableIndexedBytesLockFree(
                            srcStr, rightIdx, len, start, strByteOffsets[s][chunkIdx]);
                }
                return null;
            });
        }

        for (int c = 0; c < numCols; c++) {
            OutputColSpec spec = outputSpecs.get(c);
            if (!spec.fromLeft) {
                ColumnVector dstVec = resultVectors[c];
                ColumnVector srcVec = spec.primaryVec;
                for (int chunkIdx = 0; chunkIdx < ranges.length; chunkIdx++) {
                    int[] rightIdx = rightIndexChunks.get(chunkIdx);
                    int start = ranges[chunkIdx][0];
                    dstVec.applyNullsFromNullableIndexed(srcVec, rightIdx, rightIdx.length, start);
                }
            }
        }

        return Arrays.asList(resultVectors);
    }

    private abstract static class AsOfProbeMatcher {
        abstract void matchRange(int start, int end, int[] outRightIdx);

        static AsOfProbeMatcher create(
                ColumnVector leftOnVec,
                ColumnVector rightOnVec,
                ColumnVector[] leftByVecs,
                ColumnVector[] rightByVecs,
                int rightSize,
                AsOfDirection direction) {

            if (leftByVecs == null || leftByVecs.length == 0) {
                return createUnpartitioned(leftOnVec, rightOnVec, rightSize, direction);
            }
            return new PartitionedAsOfMatcher(leftOnVec, rightOnVec, leftByVecs, rightByVecs, rightSize, direction);
        }

        private static AsOfProbeMatcher createUnpartitioned(
                ColumnVector leftOnVec, ColumnVector rightOnVec, int rightSize, AsOfDirection direction) {
            if (leftOnVec instanceof LongColumnVector lLong && rightOnVec instanceof LongColumnVector rLong) {
                return new LongAsOfMatcher(lLong, rLong, rightSize, direction);
            }
            if (leftOnVec instanceof IntColumnVector lInt && rightOnVec instanceof IntColumnVector rInt) {
                return new IntAsOfMatcher(lInt, rInt, rightSize, direction);
            }
            if (leftOnVec instanceof DoubleColumnVector lDbl && rightOnVec instanceof DoubleColumnVector rDbl) {
                return new DoubleAsOfMatcher(lDbl, rDbl, rightSize, direction);
            }
            return new GeneralAsOfMatcher(leftOnVec, rightOnVec, rightSize, direction);
        }
    }

    private static final class LongAsOfMatcher extends AsOfProbeMatcher {
        private final long[] leftData;
        private final BitSet leftNulls;
        private final long[] rightData;
        private final BitSet rightNulls;
        private final int rightSize;
        private final AsOfDirection direction;
        private final boolean linearPassEligible;

        LongAsOfMatcher(LongColumnVector lVec, LongColumnVector rVec, int rightSize, AsOfDirection direction) {
            this.leftData = lVec.getRawData();
            this.leftNulls = lVec.getNulls();
            this.rightData = rVec.getRawData();
            this.rightNulls = rVec.getNulls();
            this.rightSize = rightSize;
            this.direction = direction;
            this.linearPassEligible = isArraySorted(rightData, rightNulls, rightSize);
        }

        private static boolean isArraySorted(long[] arr, BitSet nulls, int len) {
            if (len <= 1) return true;
            for (int i = 1; i < len; i++) {
                if (nulls != null && (nulls.get(i) || nulls.get(i - 1))) return false;
                if (arr[i] < arr[i - 1]) return false;
            }
            return true;
        }

        @Override
        void matchRange(int start, int end, int[] outRightIdx) {
            if (rightSize == 0) {
                Arrays.fill(outRightIdx, -1);
                return;
            }
            for (int i = start; i < end; i++) {
                int outOffset = i - start;
                if (leftNulls != null && leftNulls.get(i)) {
                    outRightIdx[outOffset] = -1;
                    continue;
                }
                long target = leftData[i];
                outRightIdx[outOffset] = search(target);
            }
        }

        private int search(long target) {
            int low = 0;
            int high = rightSize - 1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                long midVal = rightData[mid];
                if (midVal <= target) {
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }
            // high is the last index where rightData[high] <= target
            if (direction == AsOfDirection.BACKWARD) {
                return (high >= 0) ? high : -1;
            } else if (direction == AsOfDirection.FORWARD) {
                int fwdIdx = (high + 1 < rightSize) ? (high + 1) : -1;
                if (high >= 0 && rightData[high] == target) {
                    return high; // exact match
                }
                return fwdIdx;
            } else {
                // NEAREST
                if (high < 0) return 0;
                if (high >= rightSize - 1) return rightSize - 1;
                int cand1 = high;
                int cand2 = high + 1;
                long d1 = Math.abs(target - rightData[cand1]);
                long d2 = Math.abs(rightData[cand2] - target);
                return (d2 <= d1) ? cand2 : cand1; // prefer later in tie
            }
        }
    }

    private static final class IntAsOfMatcher extends AsOfProbeMatcher {
        private final int[] leftData;
        private final BitSet leftNulls;
        private final int[] rightData;
        private final BitSet rightNulls;
        private final int rightSize;
        private final AsOfDirection direction;

        IntAsOfMatcher(IntColumnVector lVec, IntColumnVector rVec, int rightSize, AsOfDirection direction) {
            this.leftData = lVec.getRawData();
            this.leftNulls = lVec.getNulls();
            this.rightData = rVec.getRawData();
            this.rightNulls = rVec.getNulls();
            this.rightSize = rightSize;
            this.direction = direction;
        }

        @Override
        void matchRange(int start, int end, int[] outRightIdx) {
            if (rightSize == 0) {
                Arrays.fill(outRightIdx, -1);
                return;
            }
            for (int i = start; i < end; i++) {
                int outOffset = i - start;
                if (leftNulls != null && leftNulls.get(i)) {
                    outRightIdx[outOffset] = -1;
                    continue;
                }
                int target = leftData[i];
                outRightIdx[outOffset] = search(target);
            }
        }

        private int search(int target) {
            int low = 0;
            int high = rightSize - 1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                int midVal = rightData[mid];
                if (midVal <= target) {
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }
            if (direction == AsOfDirection.BACKWARD) {
                return (high >= 0) ? high : -1;
            } else if (direction == AsOfDirection.FORWARD) {
                int fwdIdx = (high + 1 < rightSize) ? (high + 1) : -1;
                if (high >= 0 && rightData[high] == target) {
                    return high;
                }
                return fwdIdx;
            } else {
                if (high < 0) return 0;
                if (high >= rightSize - 1) return rightSize - 1;
                int cand1 = high;
                int cand2 = high + 1;
                long d1 = Math.abs((long) target - rightData[cand1]);
                long d2 = Math.abs((long) rightData[cand2] - target);
                return (d2 <= d1) ? cand2 : cand1;
            }
        }
    }

    private static final class DoubleAsOfMatcher extends AsOfProbeMatcher {
        private final double[] leftData;
        private final BitSet leftNulls;
        private final double[] rightData;
        private final BitSet rightNulls;
        private final int rightSize;
        private final AsOfDirection direction;

        DoubleAsOfMatcher(DoubleColumnVector lVec, DoubleColumnVector rVec, int rightSize, AsOfDirection direction) {
            this.leftData = lVec.getRawData();
            this.leftNulls = lVec.getNulls();
            this.rightData = rVec.getRawData();
            this.rightNulls = rVec.getNulls();
            this.rightSize = rightSize;
            this.direction = direction;
        }

        @Override
        void matchRange(int start, int end, int[] outRightIdx) {
            if (rightSize == 0) {
                Arrays.fill(outRightIdx, -1);
                return;
            }
            for (int i = start; i < end; i++) {
                int outOffset = i - start;
                if (leftNulls != null && leftNulls.get(i)) {
                    outRightIdx[outOffset] = -1;
                    continue;
                }
                double target = leftData[i];
                outRightIdx[outOffset] = search(target);
            }
        }

        private int search(double target) {
            int low = 0;
            int high = rightSize - 1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                double midVal = rightData[mid];
                if (Double.compare(midVal, target) <= 0) {
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }
            if (direction == AsOfDirection.BACKWARD) {
                return (high >= 0) ? high : -1;
            } else if (direction == AsOfDirection.FORWARD) {
                int fwdIdx = (high + 1 < rightSize) ? (high + 1) : -1;
                if (high >= 0 && Double.compare(rightData[high], target) == 0) {
                    return high;
                }
                return fwdIdx;
            } else {
                if (high < 0) return 0;
                if (high >= rightSize - 1) return rightSize - 1;
                int cand1 = high;
                int cand2 = high + 1;
                double d1 = Math.abs(target - rightData[cand1]);
                double d2 = Math.abs(rightData[cand2] - target);
                return (d2 <= d1) ? cand2 : cand1;
            }
        }
    }

    private static final class GeneralAsOfMatcher extends AsOfProbeMatcher {
        private final ColumnVector leftOnVec;
        private final ColumnVector rightOnVec;
        private final int rightSize;
        private final AsOfDirection direction;

        GeneralAsOfMatcher(ColumnVector leftOnVec, ColumnVector rightOnVec, int rightSize, AsOfDirection direction) {
            this.leftOnVec = leftOnVec;
            this.rightOnVec = rightOnVec;
            this.rightSize = rightSize;
            this.direction = direction;
        }

        @Override
        void matchRange(int start, int end, int[] outRightIdx) {
            if (rightSize == 0) {
                Arrays.fill(outRightIdx, -1);
                return;
            }
            for (int i = start; i < end; i++) {
                int outOffset = i - start;
                if (leftOnVec.isNull(i)) {
                    outRightIdx[outOffset] = -1;
                    continue;
                }
                Object target = leftOnVec.getObject(i);
                outRightIdx[outOffset] = search(target);
            }
        }

        private int search(Object target) {
            int low = 0;
            int high = rightSize - 1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                if (rightOnVec.isNull(mid)) {
                    high = mid - 1;
                    continue;
                }
                Object midVal = rightOnVec.getObject(mid);
                int cmp = compareAsOfKeys(midVal, target);
                if (cmp <= 0) {
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }
            if (direction == AsOfDirection.BACKWARD) {
                return (high >= 0) ? high : -1;
            } else if (direction == AsOfDirection.FORWARD) {
                int fwdIdx = (high + 1 < rightSize) ? (high + 1) : -1;
                if (high >= 0 && compareAsOfKeys(rightOnVec.getObject(high), target) == 0) {
                    return high;
                }
                return fwdIdx;
            } else {
                if (high < 0) return 0;
                if (high >= rightSize - 1) return rightSize - 1;
                int cand1 = high;
                int cand2 = high + 1;
                double d1 = numericDiff(target, rightOnVec.getObject(cand1));
                double d2 = numericDiff(rightOnVec.getObject(cand2), target);
                return (d2 <= d1) ? cand2 : cand1;
            }
        }
    }

    private static final class PartitionedAsOfMatcher extends AsOfProbeMatcher {
        private final ColumnVector leftOnVec;
        private final ColumnVector rightOnVec;
        private final ColumnVector[] leftByVecs;
        private final ColumnVector[] rightByVecs;
        private final AsOfDirection direction;
        private final Map<AsOfKey, int[]> partitionIndices = new HashMap<>();

        PartitionedAsOfMatcher(
                ColumnVector leftOnVec,
                ColumnVector rightOnVec,
                ColumnVector[] leftByVecs,
                ColumnVector[] rightByVecs,
                int rightSize,
                AsOfDirection direction) {
            this.leftOnVec = leftOnVec;
            this.rightOnVec = rightOnVec;
            this.leftByVecs = leftByVecs;
            this.rightByVecs = rightByVecs;
            this.direction = direction;

            // Group right rows into partitions
            Map<AsOfKey, List<Integer>> temp = new HashMap<>();
            for (int r = 0; r < rightSize; r++) {
                Object[] byVals = new Object[rightByVecs.length];
                boolean hasNull = false;
                for (int b = 0; b < rightByVecs.length; b++) {
                    if (rightByVecs[b].isNull(r)) {
                        hasNull = true;
                        break;
                    }
                    byVals[b] = canonicalKey(rightByVecs[b].getObject(r));
                }
                if (hasNull) continue;
                AsOfKey key = new AsOfKey(byVals);
                temp.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
            }
            for (Map.Entry<AsOfKey, List<Integer>> entry : temp.entrySet()) {
                List<Integer> list = entry.getValue();
                int[] arr = new int[list.size()];
                for (int i = 0; i < arr.length; i++) {
                    arr[i] = list.get(i);
                }
                partitionIndices.put(entry.getKey(), arr);
            }
        }

        @Override
        void matchRange(int start, int end, int[] outRightIdx) {
            for (int i = start; i < end; i++) {
                int outOffset = i - start;
                if (leftOnVec.isNull(i)) {
                    outRightIdx[outOffset] = -1;
                    continue;
                }
                Object[] byVals = new Object[leftByVecs.length];
                boolean hasNull = false;
                for (int b = 0; b < leftByVecs.length; b++) {
                    if (leftByVecs[b].isNull(i)) {
                        hasNull = true;
                        break;
                    }
                    byVals[b] = canonicalKey(leftByVecs[b].getObject(i));
                }
                if (hasNull) {
                    outRightIdx[outOffset] = -1;
                    continue;
                }
                AsOfKey key = new AsOfKey(byVals);
                int[] rightIndices = partitionIndices.get(key);
                if (rightIndices == null || rightIndices.length == 0) {
                    outRightIdx[outOffset] = -1;
                    continue;
                }
                outRightIdx[outOffset] = searchPartition(rightIndices, leftOnVec.getObject(i));
            }
        }

        private int searchPartition(int[] rightIndices, Object target) {
            int len = rightIndices.length;
            int low = 0;
            int high = len - 1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                int rRow = rightIndices[mid];
                if (rightOnVec.isNull(rRow)) {
                    high = mid - 1;
                    continue;
                }
                Object midVal = rightOnVec.getObject(rRow);
                int cmp = compareAsOfKeys(midVal, target);
                if (cmp <= 0) {
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }
            if (direction == AsOfDirection.BACKWARD) {
                return (high >= 0) ? rightIndices[high] : -1;
            } else if (direction == AsOfDirection.FORWARD) {
                int fwdIdx = (high + 1 < len) ? rightIndices[high + 1] : -1;
                if (high >= 0 && compareAsOfKeys(rightOnVec.getObject(rightIndices[high]), target) == 0) {
                    return rightIndices[high];
                }
                return fwdIdx;
            } else {
                if (high < 0) return rightIndices[0];
                if (high >= len - 1) return rightIndices[len - 1];
                int cand1 = rightIndices[high];
                int cand2 = rightIndices[high + 1];
                double d1 = numericDiff(target, rightOnVec.getObject(cand1));
                double d2 = numericDiff(rightOnVec.getObject(cand2), target);
                return (d2 <= d1) ? cand2 : cand1;
            }
        }
    }

    private static final class AsOfKey {
        private final Object[] values;
        private final int hash;

        AsOfKey(Object[] values) {
            this.values = values;
            this.hash = Arrays.hashCode(values);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AsOfKey other)) return false;
            return Arrays.equals(this.values, other.values);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int compareAsOfKeys(Object valA, Object valB) {
        if (valA instanceof Number numA && valB instanceof Number numB) {
            if (numA instanceof Long || numB instanceof Long || numA instanceof Integer || numB instanceof Integer) {
                return Long.compare(numA.longValue(), numB.longValue());
            }
            return Double.compare(numA.doubleValue(), numB.doubleValue());
        }
        if (valA instanceof Comparable compA && valB instanceof Comparable compB) {
            try {
                return compA.compareTo(compB);
            } catch (ClassCastException ignored) {}
        }
        return valA.toString().compareTo(valB.toString());
    }

    private static double numericDiff(Object a, Object b) {
        if (a instanceof Number numA && b instanceof Number numB) {
            return Math.abs(numA.doubleValue() - numB.doubleValue());
        }
        return 0.0;
    }

}
