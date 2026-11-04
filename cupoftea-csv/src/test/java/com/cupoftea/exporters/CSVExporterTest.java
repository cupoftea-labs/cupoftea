package com.cupoftea.exporters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exporters.configurations.CSVExporterConfiguration;
import static com.cupoftea.exporters.configurations.CSVExporterConfiguration.HeadersPlacement.BOTTOM;
import static com.cupoftea.exporters.configurations.CSVExporterConfiguration.HeadersPlacement.NO_HEADER;
import static com.cupoftea.exporters.configurations.CSVExporterConfiguration.HeadersPlacement.TOP;
import static com.cupoftea.exporters.configurations.CSVExporterConfiguration.QuoteBehaviour.ALL;
import static com.cupoftea.exporters.configurations.CSVExporterConfiguration.QuoteBehaviour.ALL_BUT_NUMBERS;
import static com.cupoftea.exporters.configurations.CSVExporterConfiguration.QuoteBehaviour.NONE;

public class CSVExporterTest {

    @Test
    @DisplayName("Should export standard DataFrame to CSV using default configuration")
    void testExportDefault() throws Exception {
        CupOfTea df = new CupOfTea();
        df.addColumn("id", Integer.class);
        df.addColumn("name", String.class);
        df.addColumn("val", Double.class);

        Map<String, Object> r1 = new HashMap<>();
        r1.put("id", 1);
        r1.put("name", "alpha");
        r1.put("val", 10.5);
        df.addRow(r1);

        Map<String, Object> r2 = new HashMap<>();
        r2.put("id", 2);
        r2.put("name", "beta");
        r2.put("val", 20.25);
        df.addRow(r2);

        File temp = File.createTempFile("test_csv_export", ".csv");
        temp.deleteOnExit();

        df.write().csv(temp);
        assertThat(temp.exists()).isTrue();

        List<String> lines = Files.readAllLines(temp.toPath());
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).isEqualTo("id,name,val");
        assertThat(lines.get(1)).isEqualTo("1,alpha,10.5");
        assertThat(lines.get(2)).isEqualTo("2,beta,20.25");
    }

    @Test
    @DisplayName("Should export with quoting behaviours")
    void testExportQuoting() throws Exception {
        CupOfTea df = new CupOfTea();
        df.addColumn("id", Integer.class);
        df.addColumn("name", String.class);

        Map<String, Object> r1 = new HashMap<>();
        r1.put("id", 100);
        r1.put("name", "foo");
        df.addRow(r1);

        File temp = File.createTempFile("test_csv_quote", ".csv");
        temp.deleteOnExit();

        CSVExporterConfiguration config = new CSVExporterConfiguration()
                .setQuoteString("\"")
                .setQuoteBehaviour(ALL);

        new CSVExporter(df).exportToCsv(temp, config);
        List<String> lines = Files.readAllLines(temp.toPath());
        assertThat(lines.get(0)).isEqualTo("\"id\",\"name\"");
        assertThat(lines.get(1)).isEqualTo("\"100\",\"foo\"");

        // ALL_BUT_NUMBERS
        config.setQuoteBehaviour(ALL_BUT_NUMBERS);
        new CSVExporter(df).exportToCsv(temp, config);
        lines = Files.readAllLines(temp.toPath());
        assertThat(lines.get(0)).isEqualTo("\"id\",\"name\"");
        assertThat(lines.get(1)).isEqualTo("100,\"foo\"");
    }

    @Test
    @DisplayName("Should handle headers placement options (TOP, BOTTOM, NO_HEADER)")
    void testHeadersPlacement() throws Exception {
        CupOfTea df = new CupOfTea();
        df.addColumn("v", String.class);
        Map<String, Object> r = new HashMap<>();
        r.put("v", "data");
        df.addRow(r);

        File temp = File.createTempFile("test_csv_placement", ".csv");
        temp.deleteOnExit();

        // NO_HEADER
        CSVExporterConfiguration cfg = new CSVExporterConfiguration().setHeadersPlacement(NO_HEADER);
        new CSVExporter(df).exportToCsv(temp, cfg);
        List<String> lines = Files.readAllLines(temp.toPath());
        assertThat(lines).containsExactly("data");

        // BOTTOM
        cfg.setHeadersPlacement(BOTTOM);
        new CSVExporter(df).exportToCsv(temp, cfg);
        lines = Files.readAllLines(temp.toPath());
        assertThat(lines).containsExactly("data", "v");
    }

    @Test
    @DisplayName("Should handle nulls correctly")
    void testNullValues() throws Exception {
        CupOfTea df = new CupOfTea();
        df.addColumn("id", Integer.class);
        df.addColumn("comment", String.class);

        Map<String, Object> r = new HashMap<>();
        r.put("id", 1);
        r.put("comment", null);
        df.addRow(r);

        File temp = File.createTempFile("test_csv_null", ".csv");
        temp.deleteOnExit();

        CSVExporterConfiguration cfg = new CSVExporterConfiguration().setStringNull("N/A");
        new CSVExporter(df).exportToCsv(temp, cfg);
        List<String> lines = Files.readAllLines(temp.toPath());
        assertThat(lines.get(1)).isEqualTo("1,N/A");
    }

    @Test
    @DisplayName("Should throw ExportException when file is null")
    void testNullFile() {
        CupOfTea df = new CupOfTea();
        CSVExporter exporter = new CSVExporter(df);
        assertThatThrownBy(() -> exporter.exportToCsv(null)).isInstanceOf(ExportException.class);
    }
}
