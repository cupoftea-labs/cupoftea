package com.cupoftea.exporters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.CategoricalColumnVector;
import com.cupoftea.table.vector.DictionaryColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.FloatColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

public class ParquetExporterTest {

    @Test
    @DisplayName("Should throw ExportException when file is null")
    void testExportNullFileThrows() {
        CupOfTea df = new CupOfTea();
        assertThatThrownBy(() -> new ParquetExporter(df).exportToParquet(null))
                .isInstanceOf(ExportException.class)
                .hasMessageContaining("File cannot be null");
    }

    @Test
    @DisplayName("Should export and re-import Parquet file natively")
    void testExportAndImportParquet() throws Exception {
        CupOfTea df = new CupOfTea();
        df.addColumn("id", Integer.class);
        df.addColumn("nome", String.class);
        df.addColumn("score", Double.class);

        Map<String, Object> r1 = new HashMap<>();
        r1.put("id", 1);
        r1.put("nome", "Alice");
        r1.put("score", 95.5);
        df.addRow(r1);

        Map<String, Object> r2 = new HashMap<>();
        r2.put("id", 2);
        r2.put("nome", "Bob");
        r2.put("score", 87.0);
        df.addRow(r2);

        File temp = File.createTempFile("test_parquet_export", ".parquet");
        temp.delete();
        temp.deleteOnExit();

        df.write().parquet(temp.getPath());
        assertThat(temp.exists()).isTrue();
        assertThat(temp.length()).isGreaterThan(0);

        CupOfTea readBack = CupOfTea.read().parquet(temp.getPath());
        assertThat(readBack.size()).isEqualTo(2);
        assertThat(readBack.getHeaders()).hasSize(3);
        assertThat(readBack.getRow(0).get("nome")).isEqualTo("Alice");
        assertThat(readBack.getRow(1).get("score")).isEqualTo(87.0);
    }

    @Test
    @DisplayName("Should export all primitive column vector types including nulls and dictionary")
    void testExportAllColumnVectorTypes() throws Exception {
        CupOfTea df = new CupOfTea();
        df.addColumn("int_col", Integer.class);
        df.addColumn("long_col", Long.class);
        df.addColumn("double_col", Double.class);
        df.addColumn("float_col", Float.class);
        df.addColumn("bool_col", Boolean.class);
        df.addColumn("str_col", String.class);

        IntColumnVector intVec = (IntColumnVector) df.getDataFrame().getColumnVector("int_col");
        LongColumnVector longVec = (LongColumnVector) df.getDataFrame().getColumnVector("long_col");
        DoubleColumnVector doubleVec = (DoubleColumnVector) df.getDataFrame().getColumnVector("double_col");
        FloatColumnVector floatVec = (FloatColumnVector) df.getDataFrame().getColumnVector("float_col");
        BooleanColumnVector boolVec = (BooleanColumnVector) df.getDataFrame().getColumnVector("bool_col");
        StringColumnVector strVec = (StringColumnVector) df.getDataFrame().getColumnVector("str_col");

        // Row 0: valid values
        intVec.appendInt(42);
        longVec.appendLong(100_000_000_000L);
        doubleVec.appendDouble(3.14159);
        floatVec.appendFloat(2.718f);
        boolVec.appendBoolean(true);
        strVec.appendString("CupOfTea");

        // Row 1: null values
        intVec.appendNull();
        longVec.appendNull();
        doubleVec.appendNull();
        floatVec.appendNull();
        boolVec.appendNull();
        strVec.appendNull();

        // Row 2: another row
        intVec.appendInt(-7);
        longVec.appendLong(-999L);
        doubleVec.appendDouble(-0.5);
        floatVec.appendFloat(-1.5f);
        boolVec.appendBoolean(false);
        strVec.appendString("NativeColumnar");

        File temp = File.createTempFile("test_parquet_all_types", ".parquet");
        temp.delete();
        temp.deleteOnExit();

        df.write().parquet(temp.getPath());

        CupOfTea readBack = CupOfTea.read().parquet(temp.getPath());
        assertThat(readBack.size()).isEqualTo(3);
        assertThat(readBack.getHeaders()).hasSize(6);

        // Verify Row 0
        assertThat(readBack.getRow(0).get("int_col")).isEqualTo(42);
        assertThat(readBack.getRow(0).get("long_col")).isEqualTo(100_000_000_000L);
        assertThat(readBack.getRow(0).get("double_col")).isEqualTo(3.14159);
        assertThat(((Float) readBack.getRow(0).get("float_col"))).isCloseTo(2.718f, org.assertj.core.data.Offset.offset(0.001f));
        assertThat(readBack.getRow(0).get("bool_col")).isEqualTo(true);
        assertThat(readBack.getRow(0).get("str_col")).isEqualTo("CupOfTea");

        // Verify Row 1 (nulls)
        assertThat(readBack.getRow(1).get("int_col")).isNull();
        assertThat(readBack.getRow(1).get("long_col")).isNull();
        assertThat(readBack.getRow(1).get("double_col")).isNull();
        assertThat(readBack.getRow(1).get("float_col")).isNull();
        assertThat(readBack.getRow(1).get("bool_col")).isNull();
        assertThat(readBack.getRow(1).get("str_col")).isNull();

        // Verify Row 2
        assertThat(readBack.getRow(2).get("int_col")).isEqualTo(-7);
        assertThat(readBack.getRow(2).get("long_col")).isEqualTo(-999L);
        assertThat(readBack.getRow(2).get("double_col")).isEqualTo(-0.5);
        assertThat(((Float) readBack.getRow(2).get("float_col"))).isCloseTo(-1.5f, org.assertj.core.data.Offset.offset(0.001f));
        assertThat(readBack.getRow(2).get("bool_col")).isEqualTo(false);
        assertThat(readBack.getRow(2).get("str_col")).isEqualTo("NativeColumnar");
    }

    @Test
    @DisplayName("Should export DictionaryColumnVector correctly")
    void testExportDictionaryColumnVector() throws Exception {
        CupOfTea df = new CupOfTea();
        DictionaryColumnVector dictVec = new DictionaryColumnVector("category");
        dictVec.appendObject("Electronics");
        dictVec.appendNull();
        dictVec.appendObject("Books");
        dictVec.appendObject("Electronics");

        df.withColumn("category", dictVec);

        File temp = File.createTempFile("test_parquet_dict", ".parquet");
        temp.delete();
        temp.deleteOnExit();

        df.write().parquet(temp);

        CupOfTea readBack = CupOfTea.read().parquet(temp);
        assertThat(readBack.size()).isEqualTo(4);
        assertThat(readBack.getRow(0).get("category")).isEqualTo("Electronics");
        assertThat(readBack.getRow(1).get("category")).isNull();
        assertThat(readBack.getRow(2).get("category")).isEqualTo("Books");
        assertThat(readBack.getRow(3).get("category")).isEqualTo("Electronics");
    }
}
