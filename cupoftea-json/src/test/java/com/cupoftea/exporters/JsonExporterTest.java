package com.cupoftea.exporters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exporters.configurations.JsonExporterConfiguration;

public class JsonExporterTest {

    @Test
    @DisplayName("Should export DataFrame to JSON file")
    void testExportToJsonFile() throws Exception {
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

        File temp = File.createTempFile("test_json_export", ".json");
        temp.deleteOnExit();

        df.write().json(temp);
        assertThat(temp.exists()).isTrue();
        assertThat(temp.length()).isGreaterThan(0);

        String content = Files.readString(temp.toPath());
        assertThat(content).contains("alpha").contains("beta").contains("10.5");
    }

    @Test
    @DisplayName("Should export DataFrame to JSON with compact and pretty configuration")
    void testExportWithModes() throws Exception {
        CupOfTea df = new CupOfTea();
        df.addColumn("id", Integer.class);
        df.addColumn("active", Boolean.class);
        df.addColumn("nullVal", String.class);

        Map<String, Object> r1 = new HashMap<>();
        r1.put("id", 100);
        r1.put("active", true);
        r1.put("nullVal", null);
        df.addRow(r1);

        // Compact mode
        File tempCompact = File.createTempFile("test_compact", ".json");
        tempCompact.deleteOnExit();
        df.exportToJson(tempCompact, JsonExporterConfiguration.builder().mode(JsonExporterConfiguration.Mode.COMPACT));
        String compactContent = Files.readString(tempCompact.toPath());
        assertThat(compactContent).contains("[{\"id\":100,\"active\":true,\"nullVal\":null}]");

        // Pretty mode
        File tempPretty = File.createTempFile("test_pretty", ".json");
        tempPretty.deleteOnExit();
        df.exportToJson(tempPretty, JsonExporterConfiguration.builder().mode(JsonExporterConfiguration.Mode.PRETTY));
        String prettyContent = Files.readString(tempPretty.toPath());
        assertThat(prettyContent).contains("\n").contains("\"id\" : 100");

        // Lines mode (NDJSON)
        File tempLines = File.createTempFile("test_lines", ".json");
        tempLines.deleteOnExit();
        df.write().option("lines", true).json(tempLines);
        String linesContent = Files.readString(tempLines.toPath());
        assertThat(linesContent).doesNotStartWith("[").contains("{\"id\":100,\"active\":true,\"nullVal\":null}\n");
    }

    @Test
    @DisplayName("Should export DataFrame to JSONArray")
    void testExportToJsonArray() {
        CupOfTea df = new CupOfTea();
        df.addColumn("id", Integer.class);
        df.addColumn("name", String.class);

        Map<String, Object> r = new HashMap<>();
        r.put("id", 42);
        r.put("name", "cupoftea");
        df.addRow(r);

        JsonExporter exporter = new JsonExporter(df);
        JSONArray arr = exporter.exportToJson();
        assertThat(arr.length()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should throw ExportException when file is null")
    void testNullFile() {
        CupOfTea df = new CupOfTea();
        JsonExporter exporter = new JsonExporter(df);
        assertThatThrownBy(() -> exporter.exportToJson(null)).isInstanceOf(ExportException.class);
    }
}
