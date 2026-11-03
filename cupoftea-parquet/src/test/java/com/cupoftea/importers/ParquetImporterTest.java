package com.cupoftea.importers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;

public class ParquetImporterTest {

    private File getSampleParquet() {
        File[] candidates = new File[]{
            new File("src/test/resources/testFiles/parquet_sample.parquet"),
            new File("cupoftea-parquet/src/test/resources/testFiles/parquet_sample.parquet"),
            new File("src/test/java/com/cupoftea/testFiles/parquet_sample.parquet"),
            new File("src/test/java/com/CupOfTea/testFiles/parquet_sample.parquet")
        };
        for (File candidate : candidates) {
            if (candidate.exists()) return candidate;
        }
        return candidates[0];
    }

    @Test
    @DisplayName("Should import DataFrame from Parquet file using native columnar vectors")
    void test_GivenAParquetFile_WhenImporting_ShouldReturnACupOfTeaDataFrame() {
        int expectedColumnsNumber = 3;
        int expectedRowsNumber = 499;

        File testFile = getSampleParquet();

        CupOfTea cupOfTea = new CupOfTea();
        cupOfTea.importFromParquet(testFile);

        assertThat(cupOfTea.getHeaders()).hasSize(expectedColumnsNumber);
        assertThat(cupOfTea.getRows()).hasSize(expectedRowsNumber);
        assertThat(cupOfTea.getHeaders().get(0).columnType()).isEqualTo(String.class);
        assertThat(cupOfTea.getHeaders().get(1).columnType()).isEqualTo(Long.class);
        assertThat(cupOfTea.getHeaders().get(2).columnType()).isEqualTo(String.class);
    }
}
