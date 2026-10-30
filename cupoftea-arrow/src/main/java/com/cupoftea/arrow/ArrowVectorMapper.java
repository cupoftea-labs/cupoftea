package com.cupoftea.arrow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.BitSet;
import java.util.Date;

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

import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.FloatColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.ObjectColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

/**
 * Handles fast, zero-copy buffer mapping between Apache Arrow {@link FieldVector} instances
 * and CupOfTea {@link ColumnVector} instances.
 */
public final class ArrowVectorMapper {

    private ArrowVectorMapper() {
    }

    /**
     * Maps an Arrow {@link FieldVector} directly into a new CupOfTea {@link ColumnVector}.
     */
    public static ColumnVector toColumnVector(FieldVector vector) {
        String name = vector.getName();
        boolean nullable = vector.getField().isNullable();
        int valueCount = vector.getValueCount();

        BitSet nulls = new BitSet(valueCount);
        if (vector.getNullCount() > 0) {
            for (int i = 0; i < valueCount; i++) {
                if (vector.isNull(i)) {
                    nulls.set(i);
                }
            }
        }

        if (vector instanceof IntVector iv) {
            int[] data = new int[valueCount];
            if (valueCount > 0 && iv.getDataBuffer() != null) {
                iv.getDataBuffer().nioBuffer().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(data);
            }
            return new IntColumnVector(name, nullable, data, nulls, valueCount);
        } else if (vector instanceof BigIntVector bv) {
            long[] data = new long[valueCount];
            if (valueCount > 0 && bv.getDataBuffer() != null) {
                bv.getDataBuffer().nioBuffer().order(ByteOrder.LITTLE_ENDIAN).asLongBuffer().get(data);
            }
            return new LongColumnVector(name, nullable, data, nulls, valueCount);
        } else if (vector instanceof Float8Vector f8) {
            double[] data = new double[valueCount];
            if (valueCount > 0 && f8.getDataBuffer() != null) {
                f8.getDataBuffer().nioBuffer().order(ByteOrder.LITTLE_ENDIAN).asDoubleBuffer().get(data);
            }
            return new DoubleColumnVector(name, nullable, data, nulls, valueCount);
        } else if (vector instanceof Float4Vector f4) {
            float[] data = new float[valueCount];
            if (valueCount > 0 && f4.getDataBuffer() != null) {
                f4.getDataBuffer().nioBuffer().order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(data);
            }
            return new FloatColumnVector(name, nullable, data, nulls, valueCount);
        } else if (vector instanceof BitVector bitVector) {
            boolean[] data = new boolean[valueCount];
            for (int i = 0; i < valueCount; i++) {
                if (!bitVector.isNull(i)) {
                    data[i] = bitVector.get(i) == 1;
                }
            }
            return new BooleanColumnVector(name, nullable, data, nulls, valueCount);
        } else if (vector instanceof VarCharVector vcv) {
            StringColumnVector scv = new StringColumnVector(name, valueCount, nullable);
            for (int i = 0; i < valueCount; i++) {
                if (vcv.isNull(i)) {
                    scv.appendNull();
                } else {
                    byte[] bytes = vcv.get(i);
                    scv.appendUtf8Bytes(bytes, 0, bytes.length);
                }
            }
            return scv;
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
                if (!sv.isNull(i)) {
                    data[i] = sv.get(i);
                }
            }
            return new IntColumnVector(name, nullable, data, nulls, valueCount);
        } else if (vector instanceof TinyIntVector tv) {
            int[] data = new int[valueCount];
            for (int i = 0; i < valueCount; i++) {
                if (!tv.isNull(i)) {
                    data[i] = tv.get(i);
                }
            }
            return new IntColumnVector(name, nullable, data, nulls, valueCount);
        } else if (vector instanceof DateDayVector ddv) {
            LocalDate[] data = new LocalDate[valueCount];
            for (int i = 0; i < valueCount; i++) {
                if (!ddv.isNull(i)) {
                    data[i] = LocalDate.ofEpochDay(ddv.get(i));
                }
            }
            return new ObjectColumnVector(name, LocalDate.class, nullable, data, nulls, valueCount);
        } else if (vector instanceof DateMilliVector dmv) {
            Date[] data = new Date[valueCount];
            for (int i = 0; i < valueCount; i++) {
                if (!dmv.isNull(i)) {
                    data[i] = new Date(dmv.get(i));
                }
            }
            return new ObjectColumnVector(name, Date.class, nullable, data, nulls, valueCount);
        } else if (vector instanceof TimeStampVector tsv) {
            Object[] data = new Object[valueCount];
            boolean hasTz = tsv.getField().getFieldType().getType() instanceof org.apache.arrow.vector.types.pojo.ArrowType.Timestamp ts
                    && ts.getTimezone() != null;
            for (int i = 0; i < valueCount; i++) {
                if (!tsv.isNull(i)) {
                    Instant inst = extractInstant(tsv, i);
                    data[i] = hasTz ? inst : LocalDateTime.ofInstant(inst, ZoneOffset.UTC);
                }
            }
            Class<?> clazz = hasTz ? Instant.class : LocalDateTime.class;
            return new ObjectColumnVector(name, clazz, nullable, data, nulls, valueCount);
        } else if (vector instanceof DecimalVector dec) {
            BigDecimal[] data = new BigDecimal[valueCount];
            for (int i = 0; i < valueCount; i++) {
                if (!dec.isNull(i)) {
                    data[i] = dec.getObject(i);
                }
            }
            return new ObjectColumnVector(name, BigDecimal.class, nullable, data, nulls, valueCount);
        } else {
            Object[] data = new Object[valueCount];
            for (int i = 0; i < valueCount; i++) {
                if (!vector.isNull(i)) {
                    data[i] = vector.getObject(i);
                }
            }
            return new ObjectColumnVector(name, Object.class, nullable, data, nulls, valueCount);
        }
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
     * Populates an Arrow {@link FieldVector} from a CupOfTea {@link ColumnVector}.
     */
    public static void populateArrowVector(FieldVector arrowVector, ColumnVector colVector, int rowCount) {
        arrowVector.reset();
        if (arrowVector instanceof IntVector iv) {
            iv.allocateNew(rowCount);
            for (int i = 0; i < rowCount; i++) {
                if (colVector.isNull(i)) {
                    iv.setNull(i);
                } else {
                    iv.set(i, colVector.getInt(i));
                }
            }
            iv.setValueCount(rowCount);
        } else if (arrowVector instanceof BigIntVector bv) {
            bv.allocateNew(rowCount);
            for (int i = 0; i < rowCount; i++) {
                if (colVector.isNull(i)) {
                    bv.setNull(i);
                } else {
                    bv.set(i, colVector.getLong(i));
                }
            }
            bv.setValueCount(rowCount);
        } else if (arrowVector instanceof Float8Vector f8) {
            f8.allocateNew(rowCount);
            for (int i = 0; i < rowCount; i++) {
                if (colVector.isNull(i)) {
                    f8.setNull(i);
                } else {
                    f8.set(i, colVector.getDouble(i));
                }
            }
            f8.setValueCount(rowCount);
        } else if (arrowVector instanceof Float4Vector f4) {
            f4.allocateNew(rowCount);
            for (int i = 0; i < rowCount; i++) {
                if (colVector.isNull(i)) {
                    f4.setNull(i);
                } else {
                    f4.set(i, colVector.getFloat(i));
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
            vcv.allocateNew(rowCount);
            if (colVector instanceof StringColumnVector scv) {
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
