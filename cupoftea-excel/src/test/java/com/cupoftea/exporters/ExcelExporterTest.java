package com.cupoftea.exporters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exporters.configurations.ExcelExporterConfiguration;
import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

public class ExcelExporterTest {

    @Test
    @DisplayName("Should export and re-import DataFrame with all standard types")
    void testExportAndImportCycle() throws Exception {
        IntColumnVector idVec = new IntColumnVector("id", 4, false);
        idVec.appendInt(1);
        idVec.appendInt(2);
        idVec.appendInt(3);
        idVec.appendInt(4);

        StringColumnVector nameVec = new StringColumnVector("name", 4, true);
        nameVec.appendString("Alice");
        nameVec.appendNull();
        nameVec.appendString("Charlie");
        nameVec.appendString("Diana");

        DoubleColumnVector scoreVec = new DoubleColumnVector("score", 4, true);
        scoreVec.appendDouble(95.5);
        scoreVec.appendDouble(88.0);
        scoreVec.appendNull();
        scoreVec.appendDouble(92.25);

        BooleanColumnVector activeVec = new BooleanColumnVector("active", 4, false);
        activeVec.appendBoolean(true);
        activeVec.appendBoolean(false);
        activeVec.appendBoolean(true);
        activeVec.appendBoolean(false);

        CupOfTea df = CupOfTea.fromVectors(List.of(idVec, nameVec, scoreVec, activeVec));

        File tempFile = File.createTempFile("test_export_", ".xlsx");
        tempFile.deleteOnExit();

        df.exportToExcel(tempFile);
        assertThat(tempFile.exists()).isTrue();
        assertThat(tempFile.length()).isGreaterThan(0);

        CupOfTea imported = CupOfTea.read().excel(tempFile.getPath());
        assertThat(imported.size()).isEqualTo(4);
        assertThat(imported.getHeaders()).hasSize(4);
        assertThat(imported.getRow(0).get("id")).isEqualTo(1);
        assertThat(imported.getRow(0).get("name")).isEqualTo("Alice");
        assertThat(imported.getRow(0).get("score")).isEqualTo(95.5);
        assertThat(imported.getRow(0).get("active")).isEqualTo(true);
        assertThat(imported.getRow(1).get("name")).isNull();
        assertThat(imported.getRow(2).get("score")).isNull();
    }

    @Test
    @DisplayName("Should export using custom configuration via df.write().excel(...)")
    void testExportWithCustomConfiguration() throws Exception {
        IntColumnVector col = new IntColumnVector("x", 2, false);
        col.appendInt(10);
        col.appendInt(20);
        CupOfTea df = CupOfTea.fromVectors(List.of(col));

        File tempFile = File.createTempFile("test_config_", ".xlsx");
        tempFile.deleteOnExit();

        ExcelExporterConfiguration config = new ExcelExporterConfiguration()
                .setSheetName("CustomReport")
                .setRowAccessWindowSize(10)
                .setCompressTempFiles(true);

        df.write().excel(tempFile, config);

        CupOfTea imported = new CupOfTea().importFromExcel(tempFile,
                new com.cupoftea.importers.configurations.ExcelImporterConfiguration().setSheetName("CustomReport"));
        assertThat(imported.size()).isEqualTo(2);
        assertThat(imported.getRow(0).get("x")).isEqualTo(10);
    }

    @Test
    @DisplayName("Should throw ExportException on invalid inputs")
    void testExportErrorHandling() {
        CupOfTea df = new CupOfTea();
        assertThatThrownBy(() -> df.exportToExcel(null))
                .isInstanceOf(ExportException.class);

        ExcelExporter exporter = new ExcelExporter(df);
        assertThatThrownBy(() -> exporter.exportToExcel((File) null))
                .isInstanceOf(ExportException.class);
    }
}
