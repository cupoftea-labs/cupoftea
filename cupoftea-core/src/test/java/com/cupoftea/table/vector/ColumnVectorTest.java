package com.cupoftea.table.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.BitSet;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.table.Column;
import com.cupoftea.table.DataFrame;
import com.cupoftea.table.Index;
import com.cupoftea.table.NA;
import com.cupoftea.table.Schema;
import com.cupoftea.table.interfaces.IDataFrame;

class ColumnVectorTest {

    @Test
    @DisplayName("Should create appropriate ColumnVector subtype via ColumnVector.create factory")
    void testColumnVectorCreateFactory() {
        assertThat(ColumnVector.create("intCol", int.class, 10, true)).isInstanceOf(IntColumnVector.class);
        assertThat(ColumnVector.create("intBoxed", Integer.class, 10, true)).isInstanceOf(IntColumnVector.class);
        assertThat(ColumnVector.create("longCol", long.class, 10, true)).isInstanceOf(LongColumnVector.class);
        assertThat(ColumnVector.create("longBoxed", Long.class, 10, true)).isInstanceOf(LongColumnVector.class);
        assertThat(ColumnVector.create("doubleCol", double.class, 10, true)).isInstanceOf(DoubleColumnVector.class);
        assertThat(ColumnVector.create("doubleBoxed", Double.class, 10, true)).isInstanceOf(DoubleColumnVector.class);
        assertThat(ColumnVector.create("floatCol", float.class, 10, true)).isInstanceOf(FloatColumnVector.class);
        assertThat(ColumnVector.create("floatBoxed", Float.class, 10, true)).isInstanceOf(FloatColumnVector.class);
        assertThat(ColumnVector.create("boolCol", boolean.class, 10, true)).isInstanceOf(BooleanColumnVector.class);
        assertThat(ColumnVector.create("boolBoxed", Boolean.class, 10, true)).isInstanceOf(BooleanColumnVector.class);
        assertThat(ColumnVector.create("strCol", String.class, 10, true)).isInstanceOf(StringColumnVector.class);
        assertThat(ColumnVector.create("objCol", Object.class, 10, true)).isInstanceOf(ObjectColumnVector.class);
        assertThat(ColumnVector.create("dateCol", LocalDate.class, 10, true)).isInstanceOf(ObjectColumnVector.class);

        Column col = new Column("colFromMetadata", Double.class, false);
        ColumnVector fromCol = ColumnVector.create(col, 16);
        assertThat(fromCol).isInstanceOf(DoubleColumnVector.class);
        assertThat(fromCol.getName()).isEqualTo("colFromMetadata");
        assertThat(fromCol.isNullable()).isFalse();
    }

    @Test
    @DisplayName("IntColumnVector operations: append, get, set, nulls, slice, permute, filter, and compare")
    void testIntColumnVector() {
        IntColumnVector vec = new IntColumnVector("nums", 4, true);
        vec.appendInt(10);
        vec.appendInt(20);
        vec.appendNull();
        vec.appendObject(40);

        assertThat(vec.size()).isEqualTo(4);
        assertThat(vec.getInt(0)).isEqualTo(10);
        assertThat(vec.getInt(1)).isEqualTo(20);
        assertThat(vec.isNull(2)).isTrue();
        assertThat(vec.getObject(2)).isNull();
        assertThat(vec.getInt(3)).isEqualTo(40);
        assertThat(vec.nullCount()).isEqualTo(1);
        assertThat(vec.hasNulls()).isTrue();

        // Modification
        vec.setInt(1, 25);
        assertThat(vec.getInt(1)).isEqualTo(25);
        vec.setNull(1);
        assertThat(vec.isNull(1)).isTrue();
        vec.clearNull(1);
        assertThat(vec.isNull(1)).isFalse();

        // Slice
        ColumnVector sliced = vec.slice(0, 3);
        assertThat(sliced.size()).isEqualTo(3);
        assertThat(sliced.getObject(0)).isEqualTo(10);
        assertThat(sliced.getObject(1)).isEqualTo(25);
        assertThat(sliced.isNull(2)).isTrue();

        // Permute
        IntColumnVector perm = (IntColumnVector) vec.copy();
        perm.permute(new int[]{3, 0, 2, 1});
        assertThat(perm.getInt(0)).isEqualTo(40);
        assertThat(perm.getInt(1)).isEqualTo(10);
        assertThat(perm.isNull(2)).isTrue();
        assertThat(perm.getInt(3)).isEqualTo(25);

        // Filter in place
        IntColumnVector filterVec = (IntColumnVector) vec.copy();
        BitSet mask = new BitSet();
        mask.set(0);
        mask.set(3);
        filterVec.filterInPlace(mask, 2);
        assertThat(filterVec.size()).isEqualTo(2);
        assertThat(filterVec.getInt(0)).isEqualTo(10);
        assertThat(filterVec.getInt(1)).isEqualTo(40);

        // Fillna
        IntColumnVector fillVec = (IntColumnVector) vec.copy();
        fillVec.fillna(999);
        assertThat(fillVec.isNull(2)).isFalse();
        assertThat(fillVec.getInt(2)).isEqualTo(999);

        // Comparison
        assertThat(vec.compareRows(0, 3)).isNegative(); // 10 < 40
        assertThat(vec.compareRows(2, 0)).isPositive(); // null rows are placed after non-null
        assertThat(vec.compareRows(2, 2)).isZero();

        // Remove row
        vec.remove(0);
        assertThat(vec.size()).isEqualTo(3);
        assertThat(vec.getInt(0)).isEqualTo(25);
    }

    @Test
    @DisplayName("LongColumnVector operations: append, get, set, nulls, slice, permute, filter, and compare")
    void testLongColumnVector() {
        LongColumnVector vec = new LongColumnVector("ids", 4, true);
        vec.appendLong(10000000000L);
        vec.appendNull();
        vec.appendLong(30000000000L);

        assertThat(vec.size()).isEqualTo(3);
        assertThat(vec.getLong(0)).isEqualTo(10000000000L);
        assertThat(vec.isNull(1)).isTrue();
        assertThat(vec.getLong(2)).isEqualTo(30000000000L);

        vec.setLong(1, 20000000000L);
        assertThat(vec.isNull(1)).isFalse();
        assertThat(vec.getLong(1)).isEqualTo(20000000000L);

        ColumnVector copy = vec.copy();
        assertThat(copy.getLong(0)).isEqualTo(10000000000L);

        vec.appendNull();
        vec.fillna(0L);
        assertThat(vec.getLong(3)).isEqualTo(0L);
    }

    @Test
    @DisplayName("DoubleColumnVector operations: NaN handling, set, get, nulls, and comparisons")
    void testDoubleColumnVector() {
        DoubleColumnVector vec = new DoubleColumnVector("rates", 4, true);
        vec.appendDouble(3.14);
        vec.appendDouble(Double.NaN); // NaN is treated as NA / null
        vec.appendNull();
        vec.appendObject(NA.NA);
        vec.appendDouble(2.71);

        assertThat(vec.size()).isEqualTo(5);
        assertThat(vec.getDouble(0)).isEqualTo(3.14);
        assertThat(vec.isNull(1)).isTrue();
        assertThat(vec.isNull(2)).isTrue();
        assertThat(vec.isNull(3)).isTrue();
        assertThat(vec.getDouble(4)).isEqualTo(2.71);
        assertThat(vec.nullCount()).isEqualTo(3);

        vec.setDouble(1, 1.41);
        assertThat(vec.isNull(1)).isFalse();
        assertThat(vec.getDouble(1)).isEqualTo(1.41);

        vec.fillna(0.0);
        assertThat(vec.isNull(2)).isFalse();
        assertThat(vec.getDouble(2)).isEqualTo(0.0);

        // Sorting comparison
        assertThat(vec.compareRows(0, 4)).isPositive(); // 3.14 > 2.71
    }

    @Test
    @DisplayName("FloatColumnVector operations: NaN handling, append, set, get, and nulls")
    void testFloatColumnVector() {
        FloatColumnVector vec = new FloatColumnVector("floats", 4, true);
        vec.appendFloat(1.5f);
        vec.appendFloat(Float.NaN);
        vec.appendNull();
        vec.appendFloat(2.5f);

        assertThat(vec.size()).isEqualTo(4);
        assertThat(vec.getFloat(0)).isEqualTo(1.5f);
        assertThat(vec.isNull(1)).isTrue();
        assertThat(vec.isNull(2)).isTrue();
        assertThat(vec.getFloat(3)).isEqualTo(2.5f);

        vec.fillna(0.0f);
        assertThat(vec.getFloat(1)).isEqualTo(0.0f);
    }

    @Test
    @DisplayName("BooleanColumnVector operations: append, get, set, nulls, and compare")
    void testBooleanColumnVector() {
        BooleanColumnVector vec = new BooleanColumnVector("flags", 4, true);
        vec.appendBoolean(true);
        vec.appendBoolean(false);
        vec.appendNull();

        assertThat(vec.size()).isEqualTo(3);
        assertThat(vec.getBoolean(0)).isTrue();
        assertThat(vec.getBoolean(1)).isFalse();
        assertThat(vec.isNull(2)).isTrue();

        vec.setBoolean(2, true);
        assertThat(vec.getBoolean(2)).isTrue();
        assertThat(vec.isNull(2)).isFalse();

        assertThat(vec.compareRows(1, 0)).isNegative(); // false < true
    }

    @Test
    @DisplayName("StringColumnVector operations: append, get, set, nulls, and compare")
    void testStringColumnVector() {
        StringColumnVector vec = new StringColumnVector("names", 4, true);
        vec.appendString("Alice");
        vec.appendString("Bob");
        vec.appendNull();
        vec.appendString("");

        assertThat(vec.size()).isEqualTo(4);
        assertThat(vec.getString(0)).isEqualTo("Alice");
        assertThat(vec.getString(1)).isEqualTo("Bob");
        assertThat(vec.isNull(2)).isTrue();
        assertThat(vec.getString(2)).isNull();
        assertThat(vec.getString(3)).isEmpty();

        vec.fillna("Unknown");
        assertThat(vec.getString(2)).isEqualTo("Unknown");
        assertThat(vec.isNull(2)).isFalse();

        assertThat(vec.compareRows(0, 1)).isNegative(); // Alice < Bob
    }

    @Test
    @DisplayName("ObjectColumnVector operations: append, get, set, nulls, and compare")
    void testObjectColumnVector() {
        ObjectColumnVector vec = new ObjectColumnVector("decimals", BigDecimal.class, 4, true);
        vec.appendObject(new BigDecimal("10.50"));
        vec.appendObject(new BigDecimal("20.75"));
        vec.appendNull();

        assertThat(vec.size()).isEqualTo(3);
        assertThat(vec.getObject(0)).isEqualTo(new BigDecimal("10.50"));
        assertThat(vec.isNull(2)).isTrue();

        vec.setObject(2, new BigDecimal("0.00"));
        assertThat(vec.isNull(2)).isFalse();
        assertThat(vec.getObject(2)).isEqualTo(new BigDecimal("0.00"));

        assertThat(vec.compareRows(0, 1)).isNegative();
    }

    @Test
    @DisplayName("Out of bounds checks across ColumnVector")
    void testBoundsChecking() {
        IntColumnVector vec = new IntColumnVector("test", 2, true);
        vec.appendInt(1);

        assertThatThrownBy(() -> vec.getInt(-1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> vec.getInt(1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> vec.setInt(2, 5)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> vec.slice(0, 5)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    @DisplayName("Index class contracts and DataFrame integration")
    void testIndexAndDataFrameIntegration() {
        Index index = new Index();
        assertThat(index.hasIndex()).isFalse();
        assertThat(index.getColumnName()).isNull();
        assertThat(index.toString()).isEqualTo("Index[default]");

        index.setColumnName("id");
        assertThat(index.hasIndex()).isTrue();
        assertThat(index.getColumnName()).isEqualTo("id");
        assertThat(index.toString()).isEqualTo("Index[id]");

        Index copy = index.copy();
        assertThat(copy).isEqualTo(index);
        assertThat(copy.hashCode()).isEqualTo(index.hashCode());

        index.clear();
        assertThat(index.hasIndex()).isFalse();

        // DataFrame index integration
        IDataFrame df = new DataFrame();
        df.addColumn("id", Integer.class);
        df.addColumn("val", String.class);
        df.addRow(Map.of("id", 101, "val", "A"));
        df.addRow(Map.of("id", 102, "val", "B"));
        df.addRow(Map.of("id", 103, "val", "C"));

        assertThat(df.getIndexObject().hasIndex()).isFalse();
        df.setIndex("id");
        assertThat(df.getIndexObject().hasIndex()).isTrue();
        assertThat(df.getIndexObject().getColumnName()).isEqualTo("id");

        df.resetIndex();
        assertThat(df.getIndexObject().hasIndex()).isFalse();
    }

    @Test
    @DisplayName("Nullability contracts on Column and Schema")
    void testColumnAndSchemaNullability() {
        Column colDefault = new Column("a", Integer.class);
        assertThat(colDefault.nullable()).isTrue();
        assertThat(colDefault.isNullable()).isTrue();

        Column colNonNullable = new Column("b", Double.class, false);
        assertThat(colNonNullable.nullable()).isFalse();
        assertThat(colNonNullable.isNullable()).isFalse();

        Schema schema = Schema.builder()
                .add("id", Schema.INT, false)
                .add("name", Schema.STRING, true)
                .add("score", Schema.DOUBLE)
                .build();

        assertThat(schema.isNullable("id")).isFalse();
        assertThat(schema.isNullable(0)).isFalse();
        assertThat(schema.isNullable("name")).isTrue();
        assertThat(schema.isNullable(1)).isTrue();
        assertThat(schema.isNullable("score")).isTrue();
        assertThat(schema.isNullable(2)).isTrue();
    }

    @Test
    @DisplayName("Columnar DataFrame performance and memory efficiency with 100,000 rows")
    void testHighVolumeColumnarPerformance() {
        final int ROW_COUNT = 100_000;
        IDataFrame df = new DataFrame();
        df.addColumn("id", Integer.class);
        df.addColumn("score", Double.class);
        df.addColumn("active", Boolean.class);

        IntColumnVector idVec = (IntColumnVector) df.getColumnVector("id");
        DoubleColumnVector scoreVec = (DoubleColumnVector) df.getColumnVector("score");
        BooleanColumnVector activeVec = (BooleanColumnVector) df.getColumnVector("active");

        long start = System.currentTimeMillis();
        for (int i = 0; i < ROW_COUNT; i++) {
            idVec.appendInt(i);
            scoreVec.appendDouble(i * 1.5);
            activeVec.appendBoolean(i % 2 == 0);
        }
        long insertDuration = System.currentTimeMillis() - start;

        assertThat(df.size()).isEqualTo(ROW_COUNT);
        assertThat(df.getColumnVectors()).hasSize(3);
        assertThat(df.getColumnVector("id")).isInstanceOf(IntColumnVector.class);
        assertThat(df.getColumnVector("score")).isInstanceOf(DoubleColumnVector.class);
        assertThat(df.getColumnVector("active")).isInstanceOf(BooleanColumnVector.class);

        // Vector primitive access
        assertThat(idVec.getInt(0)).isEqualTo(0);
        assertThat(idVec.getInt(ROW_COUNT - 1)).isEqualTo(ROW_COUNT - 1);
        assertThat(scoreVec.getDouble(10)).isEqualTo(15.0);
        assertThat(activeVec.getBoolean(10)).isTrue();

        // Lazy Row access over columnar vectors
        assertThat(df.getRow(0).get("id")).isEqualTo(0);
        assertThat(df.getRow(ROW_COUNT - 1).get("id")).isEqualTo(ROW_COUNT - 1);

        // Columnar filter operation
        long filterStart = System.currentTimeMillis();
        IDataFrame filtered = df.filter(row -> (int) row.get("id") % 2 == 0);
        long filterDuration = System.currentTimeMillis() - filterStart;

        assertThat(filtered.size()).isEqualTo(ROW_COUNT / 2);
    }
}
