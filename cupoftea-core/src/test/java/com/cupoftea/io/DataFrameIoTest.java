package com.cupoftea.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.spi.FormatReader;
import com.cupoftea.spi.FormatWriter;
import com.cupoftea.spi.ReadOptions;
import com.cupoftea.spi.WriteOptions;
import com.cupoftea.table.DataFrame;
import com.cupoftea.table.interfaces.IDataFrame;

public class DataFrameIoTest {

    @Test
    @DisplayName("Should throw ImportException with helpful message when format reader is missing")
    void testMissingFormatReaderThrowsInformativeException() {
        assertThatThrownBy(() -> CupOfTea.read().csv("some_file.csv"))
                .isInstanceOf(ImportException.class)
                .hasMessageContaining("No format reader found for format 'csv'")
                .hasMessageContaining("cupoftea-csv");
    }

    @Test
    @DisplayName("Should throw ExportException with helpful message when format writer is missing")
    void testMissingFormatWriterThrowsInformativeException() {
        CupOfTea df = new CupOfTea();
        assertThatThrownBy(() -> df.write().csv("out.csv"))
                .isInstanceOf(ExportException.class)
                .hasMessageContaining("No format writer found for format 'csv'")
                .hasMessageContaining("cupoftea-csv");
    }

    @Test
    @DisplayName("Should allow custom format registration and execution via SPI")
    void testCustomFormatRegistration() {
        FormatRegistry.registerReader(new FormatReader() {
            @Override
            public String format() {
                return "mock";
            }

            @Override
            public IDataFrame read(ReadOptions options) {
                DataFrame df = new DataFrame();
                df.addColumn("dummy");
                return df;
            }
        });

        CupOfTea loaded = CupOfTea.read().format("mock").load("test.mock");
        assertThat(loaded.getHeaders()).hasSize(1);
        assertThat(loaded.getColumnHeader("dummy")).isNotNull();
    }
}
