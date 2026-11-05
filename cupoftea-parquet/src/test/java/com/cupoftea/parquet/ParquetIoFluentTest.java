package com.cupoftea.parquet;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

public class ParquetIoFluentTest {

    @Test
    @DisplayName("Should read Parquet using CupOfTea.read().parquet(path) natively")
    void testReadParquetFluent() {
        File sample = new File("src/test/resources/testFiles/parquet_sample.parquet");
        if (!sample.exists()) {
            sample = new File("cupoftea-parquet/src/test/resources/testFiles/parquet_sample.parquet");
        }

        CupOfTea df = CupOfTea.read().parquet(sample.getPath());
        assertThat(df.getHeaders()).hasSize(3);
        assertThat(df.size()).isEqualTo(499);
        assertThat(df.getColumnVector("nome")).isInstanceOf(StringColumnVector.class);
        assertThat(df.getColumnVector("idade")).isInstanceOf(LongColumnVector.class);
        assertThat(df.getColumnVector("cidade")).isInstanceOf(StringColumnVector.class);
        assertThat(df.getRow(0).get("nome")).isEqualTo("Pedro Lucas");
        assertThat(df.getRow(0).get("idade")).isEqualTo(11L);
    }

    @Test
    @DisplayName("Should scan Parquet lazily with projection pushdown")
    void testScanParquetLazyPushdown() {
        File sample = new File("src/test/resources/testFiles/parquet_sample.parquet");
        if (!sample.exists()) {
            sample = new File("cupoftea-parquet/src/test/resources/testFiles/parquet_sample.parquet");
        }

        CupOfTea df = CupOfTea.scan()
                .parquet(sample.getPath())
                .select("nome", "idade")
                .collect();

        assertThat(df.getHeaders()).hasSize(2);
        assertThat(df.size()).isEqualTo(499);
        assertThat(df.getHeaders().get(0).columnName()).isEqualTo("nome");
        assertThat(df.getHeaders().get(1).columnName()).isEqualTo("idade");
    }

    @Test
    @DisplayName("Should scan Parquet lazily with columnar predicate pushdown")
    void testScanParquetLazyPredicatePushdown() {
        File sample = new File("src/test/resources/testFiles/parquet_sample.parquet");
        if (!sample.exists()) {
            sample = new File("cupoftea-parquet/src/test/resources/testFiles/parquet_sample.parquet");
        }

        CupOfTea df = CupOfTea.scan()
                .parquet(sample.getPath())
                .select("nome", "idade")
                .filter(r -> r.getLong("idade") > 50L)
                .collect();

        assertThat(df.getHeaders()).hasSize(2);
        assertThat(df.size()).isGreaterThan(0);
        assertThat(df.size()).isLessThan(499);
        for (int i = 0; i < df.size(); i++) {
            assertThat(df.getRow(i).getLong("idade")).isGreaterThan(50L);
        }
    }
}
