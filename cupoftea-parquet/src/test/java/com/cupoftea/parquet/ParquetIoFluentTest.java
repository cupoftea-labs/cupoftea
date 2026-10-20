package com.cupoftea.parquet;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.apache.hadoop.security.UserGroupInformation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;

public class ParquetIoFluentTest {

    @Test
    @DisplayName("Should read Parquet using CupOfTea.read().parquet(path)")
    void testReadParquetFluent() {
        try {
            UserGroupInformation.getCurrentUser();
        } catch (Throwable t) {
            Assumptions.assumeTrue(false, "Hadoop UserGroupInformation not supported on this Java runtime: " + t.getMessage());
        }

        File sample = new File("src/test/resources/testFiles/parquet_sample.parquet");
        if (!sample.exists()) {
            sample = new File("cupoftea-parquet/src/test/resources/testFiles/parquet_sample.parquet");
        }

        CupOfTea df = CupOfTea.read().parquet(sample.getPath());
        assertThat(df.getHeaders()).hasSize(3);
        assertThat(df.size()).isEqualTo(499);
    }
}
