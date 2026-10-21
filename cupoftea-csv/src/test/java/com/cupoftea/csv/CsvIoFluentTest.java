package com.cupoftea.csv;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.table.DataFrame;

public class CsvIoFluentTest {

    @Test
    @DisplayName("Should read CSV using CupOfTea.read().csv(path) and write using df.write().csv(path)")
    void testReadAndWriteFluentCsv() throws Exception {
        File sample = new File("src/test/resources/testFiles/csv_sample.csv");
        if (!sample.exists()) {
            sample = new File("cupoftea-csv/src/test/resources/testFiles/csv_sample.csv");
        }

        // Test CupOfTea.read().csv(path)
        CupOfTea df = CupOfTea.read().csv(sample.getPath());
        assertThat(df.size()).isEqualTo(500);
        assertThat(df.getHeaders()).hasSize(3);

        // Test df.write().csv(path)
        File tempOut = File.createTempFile("fluent_csv_out_", ".csv");
        tempOut.deleteOnExit();

        df.write().csv(tempOut.getPath());
        assertThat(tempOut.length()).isGreaterThan(0);

        // Read written file back
        CupOfTea reread = CupOfTea.read().csv(tempOut.getPath());
        assertThat(reread.size()).isEqualTo(500);
        assertThat(reread.getRow(0).get("nome")).isEqualTo("Pedro Lucas");

        // Test writing from DataFrame directly: df.write().csv(path)
        DataFrame rawDf = (DataFrame) df.getDataFrame();
        File tempOut2 = File.createTempFile("fluent_df_out_", ".csv");
        tempOut2.deleteOnExit();
        rawDf.write().csv(tempOut2.getPath());
        assertThat(tempOut2.length()).isGreaterThan(0);
    }
}
