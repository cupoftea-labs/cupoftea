package com.cupoftea.arrow;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.DateDayVector;
import org.apache.arrow.vector.DecimalVector;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.VarCharVector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.ObjectColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

public class ArrowVectorMapperTest {

    @Test
    @DisplayName("Should map IntVector with nulls directly to IntColumnVector")
    void testIntVectorMapping() {
        try (BufferAllocator allocator = new RootAllocator();
             IntVector iv = new IntVector("numbers", allocator)) {
            iv.allocateNew(3);
            iv.set(0, 42);
            iv.setNull(1);
            iv.set(2, 999);
            iv.setValueCount(3);

            ColumnVector cv = ArrowVectorMapper.toColumnVector(iv);
            assertThat(cv).isInstanceOf(IntColumnVector.class);
            assertThat(cv.size()).isEqualTo(3);
            assertThat(cv.getInt(0)).isEqualTo(42);
            assertThat(cv.isNull(1)).isTrue();
            assertThat(cv.getObject(1)).isNull();
            assertThat(cv.getInt(2)).isEqualTo(999);
        }
    }

    @Test
    @DisplayName("Should map BigIntVector directly to LongColumnVector")
    void testBigIntVectorMapping() {
        try (BufferAllocator allocator = new RootAllocator();
             BigIntVector bv = new BigIntVector("longs", allocator)) {
            bv.allocateNew(2);
            bv.set(0, 10000000000L);
            bv.set(1, 20000000000L);
            bv.setValueCount(2);

            ColumnVector cv = ArrowVectorMapper.toColumnVector(bv);
            assertThat(cv).isInstanceOf(LongColumnVector.class);
            assertThat(cv.getLong(0)).isEqualTo(10000000000L);
            assertThat(cv.getLong(1)).isEqualTo(20000000000L);
        }
    }

    @Test
    @DisplayName("Should map Float8Vector directly to DoubleColumnVector")
    void testFloat8VectorMapping() {
        try (BufferAllocator allocator = new RootAllocator();
             Float8Vector f8 = new Float8Vector("doubles", allocator)) {
            f8.allocateNew(2);
            f8.set(0, 3.14159);
            f8.setNull(1);
            f8.setValueCount(2);

            ColumnVector cv = ArrowVectorMapper.toColumnVector(f8);
            assertThat(cv).isInstanceOf(DoubleColumnVector.class);
            assertThat(cv.getDouble(0)).isEqualTo(3.14159);
            assertThat(cv.isNull(1)).isTrue();
        }
    }

    @Test
    @DisplayName("Should map BitVector directly to BooleanColumnVector")
    void testBitVectorMapping() {
        try (BufferAllocator allocator = new RootAllocator();
             BitVector bv = new BitVector("flags", allocator)) {
            bv.allocateNew(3);
            bv.set(0, 1);
            bv.set(1, 0);
            bv.setNull(2);
            bv.setValueCount(3);

            ColumnVector cv = ArrowVectorMapper.toColumnVector(bv);
            assertThat(cv).isInstanceOf(BooleanColumnVector.class);
            assertThat(cv.getBoolean(0)).isTrue();
            assertThat(cv.getBoolean(1)).isFalse();
            assertThat(cv.isNull(2)).isTrue();
        }
    }

    @Test
    @DisplayName("Should map VarCharVector directly to StringColumnVector")
    void testVarCharVectorMapping() {
        try (BufferAllocator allocator = new RootAllocator();
             VarCharVector vcv = new VarCharVector("texts", allocator)) {
            vcv.allocateNew();
            vcv.setSafe(0, "hello".getBytes(StandardCharsets.UTF_8));
            vcv.setNull(1);
            vcv.setSafe(2, "world".getBytes(StandardCharsets.UTF_8));
            vcv.setValueCount(3);

            ColumnVector cv = ArrowVectorMapper.toColumnVector(vcv);
            assertThat(cv).isInstanceOf(StringColumnVector.class);
            assertThat(cv.getString(0)).isEqualTo("hello");
            assertThat(cv.isNull(1)).isTrue();
            assertThat(cv.getString(2)).isEqualTo("world");
        }
    }

    @Test
    @DisplayName("Should map DateDayVector and DecimalVector to ObjectColumnVector")
    void testDateAndDecimalMapping() {
        try (BufferAllocator allocator = new RootAllocator();
             DateDayVector ddv = new DateDayVector("dates", allocator);
             DecimalVector dec = new DecimalVector("prices", allocator, 38, 2)) {
            ddv.allocateNew(2);
            ddv.set(0, (int) LocalDate.of(2026, 10, 23).toEpochDay());
            ddv.setNull(1);
            ddv.setValueCount(2);

            dec.allocateNew(2);
            dec.set(0, new BigDecimal("123.45"));
            dec.setNull(1);
            dec.setValueCount(2);

            ColumnVector dateCv = ArrowVectorMapper.toColumnVector(ddv);
            assertThat(dateCv).isInstanceOf(ObjectColumnVector.class);
            assertThat(dateCv.getObject(0)).isEqualTo(LocalDate.of(2026, 10, 23));
            assertThat(dateCv.isNull(1)).isTrue();

            ColumnVector decCv = ArrowVectorMapper.toColumnVector(dec);
            assertThat(decCv).isInstanceOf(ObjectColumnVector.class);
            assertThat(decCv.getObject(0)).isEqualTo(new BigDecimal("123.45"));
            assertThat(decCv.isNull(1)).isTrue();
        }
    }
}
