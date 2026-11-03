package com.cupoftea.exporters;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;

public class ParquetExporterTest {

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
}
