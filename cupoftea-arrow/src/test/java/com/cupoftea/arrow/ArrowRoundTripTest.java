package com.cupoftea.arrow;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cupoftea.CupOfTea;
import com.cupoftea.io.FormatRegistry;
import com.cupoftea.table.Schema;

public class ArrowRoundTripTest {

    @TempDir
    Path tempDir;

    private CupOfTea createSampleDataFrame() {
        CupOfTea cot = new CupOfTea(Schema.of(
                "id", Integer.class,
                "amount", Long.class,
                "score", Double.class,
                "factor", Float.class,
                "flag", Boolean.class,
                "city", String.class,
                "date", LocalDate.class,
                "timestamp", Instant.class,
                "price", BigDecimal.class
        ));

        Map<String, Object> row1 = new HashMap<>();
        row1.put("id", 1);
        row1.put("amount", 1000L);
        row1.put("score", 95.5);
        row1.put("factor", 1.25f);
        row1.put("flag", true);
        row1.put("city", "São Paulo");
        row1.put("date", LocalDate.of(2026, 1, 15));
        row1.put("timestamp", Instant.ofEpochMilli(1700000000000L));
        row1.put("price", new BigDecimal("199.99"));
        cot.addRow(row1);

        Map<String, Object> row2 = new HashMap<>();
        row2.put("id", 2);
        row2.put("amount", null);
        row2.put("score", null);
        row2.put("factor", null);
        row2.put("flag", false);
        row2.put("city", null);
        row2.put("date", null);
        row2.put("timestamp", null);
        row2.put("price", null);
        cot.addRow(row2);

        Map<String, Object> row3 = new HashMap<>();
        row3.put("id", 3);
        row3.put("amount", 3000L);
        row3.put("score", 82.0);
        row3.put("factor", 0.5f);
        row3.put("flag", null);
        row3.put("city", "London");
        row3.put("date", LocalDate.of(2026, 10, 23));
        row3.put("timestamp", Instant.ofEpochMilli(1800000000000L));
        row3.put("price", new BigDecimal("49.50"));
        cot.addRow(row3);

        return cot;
    }

    @Test
    @DisplayName("Should round-trip Arrow File (Feather) format via fluent read().arrow() and write().arrow()")
    void testArrowFileRoundTrip() {
        CupOfTea source = createSampleDataFrame();
        File file = tempDir.resolve("test_roundtrip.arrow").toFile();

        source.write().arrow(file.getPath());
        assertThat(file).exists().isNotEmpty();

        CupOfTea loaded = CupOfTea.read().arrow(file.getPath());
        assertDataFramesMatch(source, loaded);
    }

    @Test
    @DisplayName("Should round-trip Feather format via read().feather() and write().feather()")
    void testFeatherRoundTrip() {
        CupOfTea source = createSampleDataFrame();
        File file = tempDir.resolve("test_roundtrip.feather").toFile();

        source.write().feather(file);
        assertThat(file).exists().isNotEmpty();

        CupOfTea loaded = CupOfTea.read().feather(file);
        assertDataFramesMatch(source, loaded);
    }

    @Test
    @DisplayName("Should round-trip Arrow Stream IPC format")
    void testArrowStreamRoundTrip() {
        CupOfTea source = createSampleDataFrame();
        File file = tempDir.resolve("test_stream.ipc").toFile();

        source.write().option("ipcFormat", "stream").arrow(file.getPath());
        assertThat(file).exists().isNotEmpty();

        // Reading back with auto-detection
        CupOfTea loadedAuto = CupOfTea.read().arrow(file.getPath());
        assertDataFramesMatch(source, loadedAuto);

        // Reading back with explicit stream option
        CupOfTea loadedExplicit = CupOfTea.read().option("ipcFormat", "stream").arrow(file);
        assertDataFramesMatch(source, loadedExplicit);
    }

    @Test
    @DisplayName("Should export and import using direct CupOfTea methods")
    void testDirectCupOfTeaMethods() {
        CupOfTea source = createSampleDataFrame();
        File arrowFile = tempDir.resolve("direct.arrow").toFile();
        File featherFile = tempDir.resolve("direct.feather").toFile();

        source.exportToArrow(arrowFile);
        CupOfTea loadedArrow = new CupOfTea();
        loadedArrow.importFromArrow(arrowFile);
        assertDataFramesMatch(source, loadedArrow);

        source.exportToFeather(featherFile);
        CupOfTea loadedFeather = new CupOfTea();
        loadedFeather.importFromFeather(featherFile);
        assertDataFramesMatch(source, loadedFeather);
    }

    @Test
    @DisplayName("Should register Arrow and Feather readers and writers in FormatRegistry SPI")
    void testServiceLoaderRegistration() {
        FormatRegistry.reload();
        assertThat(FormatRegistry.getReader("arrow")).isPresent();
        assertThat(FormatRegistry.getReader("feather")).isPresent();
        assertThat(FormatRegistry.getWriter("arrow")).isPresent();
        assertThat(FormatRegistry.getWriter("feather")).isPresent();
    }

    private void assertDataFramesMatch(CupOfTea expected, CupOfTea actual) {
        assertThat(actual.size()).isEqualTo(expected.size());
        assertThat(actual.getHeaders().size()).isEqualTo(expected.getHeaders().size());

        for (int i = 0; i < expected.size(); i++) {
            Map<String, Object> expRow = expected.getRow(i);
            Map<String, Object> actRow = actual.getRow(i);

            assertThat(actRow.get("id")).isEqualTo(expRow.get("id"));
            assertThat(actRow.get("amount")).isEqualTo(expRow.get("amount"));
            assertThat(actRow.get("score")).isEqualTo(expRow.get("score"));
            assertThat(actRow.get("factor")).isEqualTo(expRow.get("factor"));
            assertThat(actRow.get("flag")).isEqualTo(expRow.get("flag"));
            assertThat(actRow.get("city")).isEqualTo(expRow.get("city"));
            assertThat(actRow.get("date")).isEqualTo(expRow.get("date"));
            assertThat(actRow.get("timestamp")).isEqualTo(expRow.get("timestamp"));
            if (expRow.get("price") == null) {
                assertThat(actRow.get("price")).isNull();
            } else {
                assertThat((BigDecimal) actRow.get("price")).isEqualByComparingTo((BigDecimal) expRow.get("price"));
            }
        }
    }
}
