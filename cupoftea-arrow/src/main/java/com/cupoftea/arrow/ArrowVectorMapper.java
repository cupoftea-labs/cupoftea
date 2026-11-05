package com.cupoftea.arrow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.apache.arrow.c.ArrowArray;
import org.apache.arrow.c.ArrowSchema;
import org.apache.arrow.c.CDataDictionaryProvider;
import org.apache.arrow.c.Data;
import org.apache.arrow.memory.ArrowBuf;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.util.MemoryUtil;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.DateDayVector;
import org.apache.arrow.vector.DateMilliVector;
import org.apache.arrow.vector.DecimalVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.Float4Vector;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.LargeVarCharVector;
import org.apache.arrow.vector.SmallIntVector;
import org.apache.arrow.vector.TimeStampMicroVector;
import org.apache.arrow.vector.TimeStampMilliVector;
import org.apache.arrow.vector.TimeStampNanoVector;
import org.apache.arrow.vector.TimeStampSecVector;
import org.apache.arrow.vector.TimeStampVector;
import org.apache.arrow.vector.TinyIntVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorLoader;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.VectorUnloader;
import org.apache.arrow.vector.ipc.message.ArrowRecordBatch;
import org.apache.arrow.vector.types.pojo.Schema;

import com.cupoftea.table.DataFrame;
import com.cupoftea.table.interfaces.IDataFrame;
import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.FloatColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.ObjectColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

/**
 * Handles fast, zero-copy buffer mapping between Apache Arrow {@link FieldVector} / {@link ArrowRecordBatch}
 * / {@link VectorSchemaRoot} instances and CupOfTea {@link ColumnVector} / {@link IDataFrame} instances,
 * including Apache Arrow C Data Interface export and import.
 */
public final class ArrowVectorMapper {

    private ArrowVectorMapper() {
    }

    /**
     * Maps an Arrow {@link FieldVector} directly into a new CupOfTea {@link ColumnVector}.
     * For primitive numeric types and contiguous UTF-8 VarChar vectors, extracts contiguous
     * byte/data buffers in direct bulk memory transfers without per-row object allocations.
     */
    public static ColumnVector toColumnVector(FieldVector vector) {
        String name = vector.getName();
        boolean nullable = vector.getField().isNullable();
        int valueCount = vector.getValueCount();

        BitSet nulls = extractNulls(vector, valueCount);

        if (vector instanceof IntVector iv) {
            int[] data = new int[valueCount];
            if (valueCount > 0 && iv.getDataBuffer() != null) {
                iv.getDataBuffer().nioBuffer(0, valueCount * 4).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(data);
            }
            return new IntColumnVector(name, nullable, data, nulls, valueCount);
        } else if (vector instanceof BigIntVector bv) {
            long[] data = new long[valueCount];
            if (valueCount > 0 && bv.getDataBuffer() != null) {
                bv.getDataBuffer().nioBuffer(0, valueCount * 8).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer().get(data);
            }
            return new LongColumnVector(name, nullable, data, nulls, valueCount);
        } else if (vector instanceof Float8Vector f8) {
            double[] data = new double[valueCount];
            if (valueCount > 0 && f8.getDataBuffer() != null) {
                f8.getDataBuffer().nioBuffer(0, valueCount * 8).order(ByteOrder.LITTLE_ENDIAN).asDoubleBuffer().get(data);
            }
            return new DoubleColumnVector(name, nullable, data, nulls, valueCount);
        } else if (vector instanceof Float4Vector f4) {
            float[] data = new float[valueCount];
            if (valueCount > 0 && f4.getDataBuffer() != null) {
                f4.getDataBuffer().nioBuffer(0, valueCount * 4).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(data);
            }
            return new FloatColumnVector(name, nullable, data, nulls, valueCount);
        } else if (vector instanceof BitVector bitVector) {
            boolean[] data = new boolean[valueCount];
            for (int i = 0; i < valueCount; i++) {
                if (!nulls.get(i)) {
                    data[i] = bitVector.get(i) == 1;
                }
            }
            return new BooleanColumnVector(name, nullable, data, nulls, valueCount);
        } else if (vector instanceof VarCharVector vcv) {
            if (valueCount == 0) {
                return new StringColumnVector(name, 0, nullable);
            }
            int[] offsets = new int[valueCount + 1];
            ArrowBuf offsetBuf = vcv.getOffsetBuffer();
            if (offsetBuf != null) {
                offsetBuf.nioBuffer(0, (valueCount + 1) * 4).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(offsets);
            }
            int totalBytes = offsets[valueCount];
            byte[] rawBytes = new byte[totalBytes];
            ArrowBuf dataBuf = vcv.getDataBuffer();
            if (dataBuf != null && totalBytes > 0) {
                dataBuf.getBytes(0, rawBytes, 0, totalBytes);
            }
            return new StringColumnVector(name, nullable, rawBytes, totalBytes, offsets, nulls, valueCount);
        } else if (vector instanceof LargeVarCharVector lvcv) {
            StringColumnVector scv = new StringColumnVector(name, valueCount, nullable);
            for (int i = 0; i < valueCount; i++) {
                if (lvcv.isNull(i)) {
                    scv.appendNull();
                } else {
                    byte[] bytes = lvcv.get(i);
                    scv.appendUtf8Bytes(bytes, 0, bytes.length);
                }
            }
            return scv;
        } else if (vector instanceof SmallIntVector sv) {
            int[] data = new int[valueCount];
            for (int i = 0; i < valueCount; i++) {
                if (!nulls.get(i)) {
                    data[i] = sv.get(i);
                }
            }
            return new IntColumnVector(name, nullable, data, nulls, valueCount);
        } else if (vector instanceof TinyIntVector tv) {
            int[] data = new int[valueCount];
            for (int i = 0; i < valueCount; i++) {
                if (!nulls.get(i)) {
                    data[i] = tv.get(i);
                }
            }
            return new IntColumnVector(name, nullable, data, nulls, valueCount);
        } else if (vector instanceof DateDayVector ddv) {
            LocalDate[] data = new LocalDate[valueCount];
            for (int i = 0; i < valueCount; i++) {
                if (!nulls.get(i)) {
                    data[i] = LocalDate.ofEpochDay(ddv.get(i));
                }
            }
            return new ObjectColumnVector(name, LocalDate.class, nullable, data, nulls, valueCount);
        } else if (vector instanceof DateMilliVector dmv) {
            Date[] data = new Date[valueCount];
            for (int i = 0; i < valueCount; i++) {
                if (!nulls.get(i)) {
                    data[i] = new Date(dmv.get(i));
                }
            }
            return new ObjectColumnVector(name, Date.class, nullable, data, nulls, valueCount);
        } else if (vector instanceof TimeStampVector tsv) {
            Object[] data = new Object[valueCount];
            boolean hasTz = tsv.getField().getFieldType().getType() instanceof org.apache.arrow.vector.types.pojo.ArrowType.Timestamp ts
                    && ts.getTimezone() != null;
            for (int i = 0; i < valueCount; i++) {
                if (!nulls.get(i)) {
                    Instant inst = extractInstant(tsv, i);
                    data[i] = hasTz ? inst : LocalDateTime.ofInstant(inst, ZoneOffset.UTC);
                }
            }
            Class<?> clazz = hasTz ? Instant.class : LocalDateTime.class;
            return new ObjectColumnVector(name, clazz, nullable, data, nulls, valueCount);
        } else if (vector instanceof DecimalVector dec) {
            BigDecimal[] data = new BigDecimal[valueCount];
            for (int i = 0; i < valueCount; i++) {
                if (!nulls.get(i)) {
                    data[i] = dec.getObject(i);
                }
            }
            return new ObjectColumnVector(name, BigDecimal.class, nullable, data, nulls, valueCount);
        } else {
            Object[] data = new Object[valueCount];
            for (int i = 0; i < valueCount; i++) {
                if (!nulls.get(i)) {
                    data[i] = vector.getObject(i);
                }
            }
            return new ObjectColumnVector(name, Object.class, nullable, data, nulls, valueCount);
        }
    }

    /**
     * Extracts null bit positions from a {@link FieldVector} into a CupOfTea {@link BitSet}.
     */
    private static BitSet extractNulls(FieldVector vector, int valueCount) {
        BitSet nulls = new BitSet(valueCount);
        if (vector.getNullCount() == 0 || valueCount == 0) {
            return nulls;
        }
        ArrowBuf validityBuf = vector.getValidityBuffer();
        if (validityBuf != null && validityBuf.capacity() > 0) {
            int validityBytes = (valueCount + 7) / 8;
            byte[] bytes = new byte[validityBytes];
            validityBuf.getBytes(0, bytes, 0, validityBytes);
            BitSet validMask = BitSet.valueOf(bytes);
            nulls.set(0, valueCount);
            nulls.andNot(validMask);
        } else {
            for (int i = 0; i < valueCount; i++) {
                if (vector.isNull(i)) {
                    nulls.set(i);
                }
            }
        }
        return nulls;
    }

    /**
     * Appends a batch of rows from an Arrow {@link FieldVector} into an existing CupOfTea {@link ColumnVector}.
     */
    public static void appendBatch(ColumnVector target, FieldVector source, int rowCount) {
        if (target instanceof IntColumnVector) {
            for (int i = 0; i < rowCount; i++) {
                if (source.isNull(i)) {
                    target.appendNull();
                } else if (source instanceof IntVector iv) {
                    target.appendInt(iv.get(i));
                } else if (source instanceof SmallIntVector sv) {
                    target.appendInt(sv.get(i));
                } else if (source instanceof TinyIntVector tv) {
                    target.appendInt(tv.get(i));
                } else {
                    target.appendObject(source.getObject(i));
                }
            }
        } else if (target instanceof LongColumnVector) {
            for (int i = 0; i < rowCount; i++) {
                if (source.isNull(i)) {
                    target.appendNull();
                } else if (source instanceof BigIntVector bv) {
                    target.appendLong(bv.get(i));
                } else {
                    target.appendObject(source.getObject(i));
                }
            }
        } else if (target instanceof DoubleColumnVector) {
            for (int i = 0; i < rowCount; i++) {
                if (source.isNull(i)) {
                    target.appendNull();
                } else if (source instanceof Float8Vector f8) {
                    target.appendDouble(f8.get(i));
                } else {
                    target.appendObject(source.getObject(i));
                }
            }
        } else if (target instanceof FloatColumnVector) {
            for (int i = 0; i < rowCount; i++) {
                if (source.isNull(i)) {
                    target.appendNull();
                } else if (source instanceof Float4Vector f4) {
                    target.appendFloat(f4.get(i));
                } else {
                    target.appendObject(source.getObject(i));
                }
            }
        } else if (target instanceof BooleanColumnVector) {
            for (int i = 0; i < rowCount; i++) {
                if (source.isNull(i)) {
                    target.appendNull();
                } else if (source instanceof BitVector bv) {
                    target.appendBoolean(bv.get(i) == 1);
                } else {
                    target.appendObject(source.getObject(i));
                }
            }
        } else if (target instanceof StringColumnVector scvTarget) {
            for (int i = 0; i < rowCount; i++) {
                if (source.isNull(i)) {
                    scvTarget.appendNull();
                } else if (source instanceof VarCharVector vcv) {
                    byte[] bytes = vcv.get(i);
                    scvTarget.appendUtf8Bytes(bytes, 0, bytes.length);
                } else if (source instanceof LargeVarCharVector lvcv) {
                    byte[] bytes = lvcv.get(i);
                    scvTarget.appendUtf8Bytes(bytes, 0, bytes.length);
                } else {
                    Object obj = source.getObject(i);
                    scvTarget.appendString(obj != null ? obj.toString() : null);
                }
            }
        } else {
            for (int i = 0; i < rowCount; i++) {
                if (source.isNull(i)) {
                    target.appendNull();
                } else {
                    target.appendObject(readObject(source, i));
                }
            }
        }
    }

    /**
     * Populates an Arrow {@link FieldVector} directly from a CupOfTea {@link ColumnVector}.
     * Employs direct bulk memory transfer for primitive numeric vectors and contiguous UTF-8 byte
     * offsets for String vectors, eliminating redundant JVM element iterations.
     */
    public static void populateArrowVector(FieldVector arrowVector, ColumnVector colVector, int rowCount) {
        arrowVector.reset();
        if (arrowVector instanceof IntVector iv) {
            iv.allocateNew(rowCount);
            if (colVector instanceof IntColumnVector icv && rowCount > 0) {
                int[] rawData = icv.getRawData();
                iv.getDataBuffer().nioBuffer(0, rowCount * 4).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(rawData, 0, rowCount);
                populateValidityBuffer(iv.getValidityBuffer(), colVector, rowCount);
            } else {
                for (int i = 0; i < rowCount; i++) {
                    if (colVector.isNull(i)) {
                        iv.setNull(i);
                    } else {
                        iv.set(i, colVector.getInt(i));
                    }
                }
            }
            iv.setValueCount(rowCount);
        } else if (arrowVector instanceof BigIntVector bv) {
            bv.allocateNew(rowCount);
            if (colVector instanceof LongColumnVector lcv && rowCount > 0) {
                long[] rawData = lcv.getRawData();
                bv.getDataBuffer().nioBuffer(0, rowCount * 8).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer().put(rawData, 0, rowCount);
                populateValidityBuffer(bv.getValidityBuffer(), colVector, rowCount);
            } else {
                for (int i = 0; i < rowCount; i++) {
                    if (colVector.isNull(i)) {
                        bv.setNull(i);
                    } else {
                        bv.set(i, colVector.getLong(i));
                    }
                }
            }
            bv.setValueCount(rowCount);
        } else if (arrowVector instanceof Float8Vector f8) {
            f8.allocateNew(rowCount);
            if (colVector instanceof DoubleColumnVector dcv && rowCount > 0) {
                double[] rawData = dcv.getRawData();
                f8.getDataBuffer().nioBuffer(0, rowCount * 8).order(ByteOrder.LITTLE_ENDIAN).asDoubleBuffer().put(rawData, 0, rowCount);
                populateValidityBuffer(f8.getValidityBuffer(), colVector, rowCount);
            } else {
                for (int i = 0; i < rowCount; i++) {
                    if (colVector.isNull(i)) {
                        f8.setNull(i);
                    } else {
                        f8.set(i, colVector.getDouble(i));
                    }
                }
            }
            f8.setValueCount(rowCount);
        } else if (arrowVector instanceof Float4Vector f4) {
            f4.allocateNew(rowCount);
            if (colVector instanceof FloatColumnVector fcv && rowCount > 0) {
                float[] rawData = fcv.getRawData();
                f4.getDataBuffer().nioBuffer(0, rowCount * 4).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().put(rawData, 0, rowCount);
                populateValidityBuffer(f4.getValidityBuffer(), colVector, rowCount);
            } else {
                for (int i = 0; i < rowCount; i++) {
                    if (colVector.isNull(i)) {
                        f4.setNull(i);
                    } else {
                        f4.set(i, colVector.getFloat(i));
                    }
                }
            }
            f4.setValueCount(rowCount);
        } else if (arrowVector instanceof BitVector bitVector) {
            bitVector.allocateNew(rowCount);
            for (int i = 0; i < rowCount; i++) {
                if (colVector.isNull(i)) {
                    bitVector.setNull(i);
                } else {
                    bitVector.set(i, colVector.getBoolean(i) ? 1 : 0);
                }
            }
            bitVector.setValueCount(rowCount);
        } else if (arrowVector instanceof VarCharVector vcv) {
            if (colVector instanceof StringColumnVector scv && rowCount > 0) {
                int totalBytes = scv.getOffsets()[rowCount];
                vcv.allocateNew(totalBytes, rowCount);
                byte[] rawValues = scv.getValues();
                int[] offsets = scv.getOffsets();
                for (int i = 0; i < rowCount; i++) {
                    if (scv.isNull(i)) {
                        vcv.setNull(i);
                    } else {
                        int start = offsets[i];
                        int len = offsets[i + 1] - start;
                        vcv.setSafe(i, rawValues, start, len);
                    }
                }
            } else {
                vcv.allocateNew(rowCount);
                for (int i = 0; i < rowCount; i++) {
                    if (colVector.isNull(i)) {
                        vcv.setNull(i);
                    } else {
                        String str = colVector.getString(i);
                        if (str == null) {
                            vcv.setNull(i);
                        } else {
                            byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
                            vcv.setSafe(i, bytes, 0, bytes.length);
                        }
                    }
                }
            }
            vcv.setValueCount(rowCount);
        } else if (arrowVector instanceof DateDayVector ddv) {
            ddv.allocateNew(rowCount);
            for (int i = 0; i < rowCount; i++) {
                if (colVector.isNull(i)) {
                    ddv.setNull(i);
                } else {
                    Object obj = colVector.getObject(i);
                    if (obj instanceof LocalDate ld) {
                        ddv.set(i, (int) ld.toEpochDay());
                    } else if (obj instanceof Date d) {
                        ddv.set(i, (int) (d.getTime() / 86400000L));
                    } else {
                        ddv.setNull(i);
                    }
                }
            }
            ddv.setValueCount(rowCount);
        } else if (arrowVector instanceof DateMilliVector dmv) {
            dmv.allocateNew(rowCount);
            for (int i = 0; i < rowCount; i++) {
                if (colVector.isNull(i)) {
                    dmv.setNull(i);
                } else {
                    Object obj = colVector.getObject(i);
                    if (obj instanceof Date d) {
                        dmv.set(i, d.getTime());
                    } else if (obj instanceof LocalDate ld) {
                        dmv.set(i, ld.toEpochDay() * 86400000L);
                    } else {
                        dmv.setNull(i);
                    }
                }
            }
            dmv.setValueCount(rowCount);
        } else if (arrowVector instanceof TimeStampVector tsv) {
            tsv.allocateNew(rowCount);
            for (int i = 0; i < rowCount; i++) {
                if (colVector.isNull(i)) {
                    tsv.setNull(i);
                } else {
                    Object obj = colVector.getObject(i);
                    setTimeStampValue(tsv, i, obj);
                }
            }
            tsv.setValueCount(rowCount);
        } else if (arrowVector instanceof DecimalVector dec) {
            dec.allocateNew(rowCount);
            int scale = dec.getScale();
            for (int i = 0; i < rowCount; i++) {
                if (colVector.isNull(i)) {
                    dec.setNull(i);
                } else {
                    Object obj = colVector.getObject(i);
                    if (obj instanceof BigDecimal bd) {
                        if (bd.scale() != scale) {
                            bd = bd.setScale(scale, RoundingMode.HALF_UP);
                        }
                        dec.set(i, bd);
                    } else if (obj instanceof Number num) {
                        BigDecimal bd = BigDecimal.valueOf(num.doubleValue()).setScale(scale, RoundingMode.HALF_UP);
                        dec.set(i, bd);
                    } else {
                        dec.setNull(i);
                    }
                }
            }
            dec.setValueCount(rowCount);
        } else {
            arrowVector.allocateNew();
            for (int i = 0; i < rowCount; i++) {
                if (colVector.isNull(i)) {
                    arrowVector.setNull(i);
                } else {
                    Object obj = colVector.getObject(i);
                    if (arrowVector instanceof VarCharVector vcvFallback) {
                        byte[] bytes = obj.toString().getBytes(StandardCharsets.UTF_8);
                        vcvFallback.setSafe(i, bytes, 0, bytes.length);
                    }
                }
            }
            arrowVector.setValueCount(rowCount);
        }
    }

    /**
     * Efficiently writes the validity bitmask into an Arrow vector validity buffer.
     * Inverts the CupOfTea nulls {@link BitSet} (where 1=null) into Arrow validity bits (where 1=valid).
     */
    private static void populateValidityBuffer(ArrowBuf validityBuf, ColumnVector colVector, int rowCount) {
        if (validityBuf == null || rowCount == 0) {
            return;
        }
        int validityWords = (rowCount + 63) / 64;
        BitSet nulls = colVector.getNulls();
        if (nulls == null || nulls.isEmpty()) {
            for (int w = 0; w < validityWords - 1; w++) {
                validityBuf.setLong((long) w * 8L, 0xFFFFFFFFFFFFFFFFL);
            }
            int rem = rowCount % 64;
            long lastWord = (rem == 0) ? 0xFFFFFFFFFFFFFFFFL : ((1L << rem) - 1L);
            validityBuf.setLong((long) (validityWords - 1) * 8L, lastWord);
        } else {
            BitSet validity = new BitSet(rowCount);
            validity.set(0, rowCount);
            validity.andNot(nulls);
            long[] words = validity.toLongArray();
            for (int w = 0; w < words.length; w++) {
                validityBuf.setLong((long) w * 8L, words[w]);
            }
            for (int w = words.length; w < validityWords; w++) {
                validityBuf.setLong((long) w * 8L, 0L);
            }
        }
    }

    // --- High-Level Zero-Copy Conversions: RecordBatch, VectorSchemaRoot & C Data Interface ---

    /**
     * Exports a CupOfTea {@link IDataFrame} into an Apache Arrow {@link VectorSchemaRoot}
     * using zero-copy / direct buffer transfers.
     */
    public static VectorSchemaRoot toVectorSchemaRoot(IDataFrame dataFrame, BufferAllocator allocator) {
        Objects.requireNonNull(dataFrame, "dataFrame cannot be null");
        Objects.requireNonNull(allocator, "allocator cannot be null");

        Schema arrowSchema = ArrowSchemaConverter.toArrowSchema(dataFrame);
        VectorSchemaRoot root = VectorSchemaRoot.create(arrowSchema, allocator);
        int rowCount = dataFrame.size();

        for (FieldVector fv : root.getFieldVectors()) {
            ColumnVector cv = dataFrame.getColumnVector(fv.getName());
            if (cv != null) {
                populateArrowVector(fv, cv, rowCount);
            }
        }
        root.setRowCount(rowCount);
        return root;
    }

    /**
     * Exports a CupOfTea {@link IDataFrame} into an Apache Arrow {@link ArrowRecordBatch}
     * using zero-copy / direct buffer transfers.
     */
    public static ArrowRecordBatch toRecordBatch(IDataFrame dataFrame, BufferAllocator allocator) {
        try (VectorSchemaRoot root = toVectorSchemaRoot(dataFrame, allocator)) {
            VectorUnloader unloader = new VectorUnloader(root);
            return unloader.getRecordBatch();
        }
    }

    /**
     * Imports an Apache Arrow {@link VectorSchemaRoot} into a CupOfTea {@link DataFrame}
     * using zero-copy / direct buffer transfers.
     */
    public static DataFrame fromVectorSchemaRoot(VectorSchemaRoot root) {
        Objects.requireNonNull(root, "root cannot be null");
        List<ColumnVector> vectors = new ArrayList<>(root.getFieldVectors().size());
        for (FieldVector fv : root.getFieldVectors()) {
            vectors.add(toColumnVector(fv));
        }
        return new DataFrame(vectors);
    }

    /**
     * Imports an Apache Arrow {@link ArrowRecordBatch} with a given {@link Schema} into a CupOfTea {@link DataFrame}
     * using zero-copy / direct buffer transfers.
     */
    public static DataFrame fromRecordBatch(ArrowRecordBatch recordBatch, Schema schema, BufferAllocator allocator) {
        Objects.requireNonNull(recordBatch, "recordBatch cannot be null");
        Objects.requireNonNull(schema, "schema cannot be null");
        Objects.requireNonNull(allocator, "allocator cannot be null");

        try (VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator)) {
            VectorLoader loader = new VectorLoader(root);
            loader.load(recordBatch);
            return fromVectorSchemaRoot(root);
        }
    }

    /**
     * Exports a CupOfTea {@link IDataFrame} into Apache Arrow C Data Interface structures
     * ({@link ArrowArray} and {@link ArrowSchema}) for seamless, zero-copy cross-runtime FFI transfer.
     */
    public static void exportToCData(IDataFrame dataFrame, BufferAllocator allocator, ArrowArray outArray, ArrowSchema outSchema) {
        Objects.requireNonNull(dataFrame, "dataFrame cannot be null");
        Objects.requireNonNull(allocator, "allocator cannot be null");
        Objects.requireNonNull(outArray, "outArray cannot be null");
        Objects.requireNonNull(outSchema, "outSchema cannot be null");

        try (VectorSchemaRoot root = toVectorSchemaRoot(dataFrame, allocator)) {
            Data.exportVectorSchemaRoot(allocator, root, null, outArray, outSchema);
        }
    }

    /**
     * Imports Apache Arrow C Data Interface structures ({@link ArrowArray} and {@link ArrowSchema})
     * into a CupOfTea {@link DataFrame} via zero-copy buffer transfer.
     */
    public static DataFrame importFromCData(ArrowArray inArray, ArrowSchema inSchema, BufferAllocator allocator) {
        Objects.requireNonNull(inArray, "inArray cannot be null");
        Objects.requireNonNull(inSchema, "inSchema cannot be null");
        Objects.requireNonNull(allocator, "allocator cannot be null");

        try (CDataDictionaryProvider provider = new CDataDictionaryProvider();
             VectorSchemaRoot root = Data.importVectorSchemaRoot(allocator, inArray, inSchema, provider)) {
            return fromVectorSchemaRoot(root);
        }
    }

    private static Object readObject(FieldVector source, int row) {
        if (source instanceof DateDayVector ddv) {
            return LocalDate.ofEpochDay(ddv.get(row));
        } else if (source instanceof DateMilliVector dmv) {
            return new Date(dmv.get(row));
        } else if (source instanceof TimeStampVector tsv) {
            Instant inst = extractInstant(tsv, row);
            boolean hasTz = tsv.getField().getFieldType().getType() instanceof org.apache.arrow.vector.types.pojo.ArrowType.Timestamp ts
                    && ts.getTimezone() != null;
            return hasTz ? inst : LocalDateTime.ofInstant(inst, ZoneOffset.UTC);
        } else if (source instanceof DecimalVector dec) {
            return dec.getObject(row);
        }
        return source.getObject(row);
    }

    private static Instant extractInstant(TimeStampVector tsv, int row) {
        org.apache.arrow.vector.types.pojo.ArrowType.Timestamp tsType =
                (org.apache.arrow.vector.types.pojo.ArrowType.Timestamp) tsv.getField().getFieldType().getType();
        long val = tsv.get(row);
        return switch (tsType.getUnit()) {
            case SECOND -> Instant.ofEpochSecond(val);
            case MILLISECOND -> Instant.ofEpochMilli(val);
            case MICROSECOND -> Instant.ofEpochSecond(val / 1_000_000L, (val % 1_000_000L) * 1_000L);
            case NANOSECOND -> Instant.ofEpochSecond(val / 1_000_000_000L, val % 1_000_000_000L);
        };
    }

    private static void setTimeStampValue(TimeStampVector tsv, int row, Object obj) {
        Instant inst = null;
        if (obj instanceof Instant i) {
            inst = i;
        } else if (obj instanceof LocalDateTime ldt) {
            inst = ldt.toInstant(ZoneOffset.UTC);
        } else if (obj instanceof Date d) {
            inst = d.toInstant();
        }

        if (inst == null) {
            tsv.setNull(row);
            return;
        }

        org.apache.arrow.vector.types.pojo.ArrowType.Timestamp tsType =
                (org.apache.arrow.vector.types.pojo.ArrowType.Timestamp) tsv.getField().getFieldType().getType();
        long units = switch (tsType.getUnit()) {
            case SECOND -> inst.getEpochSecond();
            case MILLISECOND -> inst.toEpochMilli();
            case MICROSECOND -> inst.getEpochSecond() * 1_000_000L + inst.getNano() / 1_000L;
            case NANOSECOND -> inst.getEpochSecond() * 1_000_000_000L + inst.getNano();
        };
        tsv.set(row, units);
    }
}
