package com.cupoftea.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.exporters.SqlExporter;
import com.cupoftea.importers.SqlImporter;
import com.cupoftea.sql.configurations.SqlExporterConfiguration;
import com.cupoftea.sql.configurations.SqlExporterConfiguration.SaveMode;
import com.cupoftea.sql.configurations.SqlImporterConfiguration;
import com.cupoftea.table.Schema;

public class SqlRoundTripTest {

    private Connection conn;
    private JdbcDataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        conn = DriverManager.getConnection("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null && !conn.isClosed()) {
            try (Statement st = conn.createStatement()) {
                st.execute("DROP ALL OBJECTS");
            }
            conn.close();
        }
    }

    private CupOfTea createSampleDataFrame() {
        CupOfTea cot = new CupOfTea(Schema.of(
                "id", Integer.class,
                "amount", Long.class,
                "score", Double.class,
                "factor", Float.class,
                "flag", Boolean.class,
                "city", String.class,
                "date", LocalDate.class,
                "timestamp", LocalDateTime.class,
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
        row1.put("timestamp", LocalDateTime.of(2026, 1, 15, 10, 30, 0));
        row1.put("price", new BigDecimal("199.99"));

        Map<String, Object> row2 = new HashMap<>();
        row2.put("id", 2);
        row2.put("amount", 2500L);
        row2.put("score", 82.0);
        row2.put("factor", 0.75f);
        row2.put("flag", false);
        row2.put("city", "Rio de Janeiro");
        row2.put("date", LocalDate.of(2026, 2, 20));
        row2.put("timestamp", LocalDateTime.of(2026, 2, 20, 14, 0, 0));
        row2.put("price", new BigDecimal("49.50"));

        Map<String, Object> row3 = new HashMap<>();
        row3.put("id", 3);
        row3.put("amount", null);
        row3.put("score", null);
        row3.put("factor", null);
        row3.put("flag", null);
        row3.put("city", null);
        row3.put("date", null);
        row3.put("timestamp", null);
        row3.put("price", null);

        cot.addRow(row1);
        cot.addRow(row2);
        cot.addRow(row3);
        return cot;
    }

    @Test
    @DisplayName("Should export DataFrame to SQL table and read it back with full fidelity")
    void testExportAndImportRoundTrip() throws Exception {
        CupOfTea source = createSampleDataFrame();

        // Export via SqlExporter
        SqlExporter exporter = new SqlExporter(source);
        exporter.exportToTable(conn, "SAMPLE_DATA");

        // Read back via SqlImporter
        CupOfTea target = new CupOfTea();
        SqlImporter importer = new SqlImporter(target);
        importer.importFromTable(conn, "SAMPLE_DATA");

        assertThat(target.size()).isEqualTo(3);
        assertThat(target.getHeaders().size()).isEqualTo(9);

        // Row 1 assertions
        assertThat(target.getRow(0).get("ID")).isEqualTo(1);
        assertThat(target.getRow(0).get("AMOUNT")).isEqualTo(1000L);
        assertThat((Double) target.getRow(0).get("SCORE")).isEqualTo(95.5);
        assertThat((Float) target.getRow(0).get("FACTOR")).isEqualTo(1.25f);
        assertThat(target.getRow(0).get("FLAG")).isEqualTo(true);
        assertThat(target.getRow(0).get("CITY")).isEqualTo("São Paulo");
        assertThat(target.getRow(0).get("DATE")).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(target.getRow(0).get("TIMESTAMP")).isEqualTo(LocalDateTime.of(2026, 1, 15, 10, 30, 0));
        assertThat((BigDecimal) target.getRow(0).get("PRICE")).isEqualByComparingTo(new BigDecimal("199.99"));

        // Row 3 null assertions
        assertThat(target.getRow(2).get("ID")).isEqualTo(3);
        assertThat(target.getRow(2).get("AMOUNT")).isNull();
        assertThat(target.getRow(2).get("SCORE")).isNull();
        assertThat(target.getRow(2).get("FACTOR")).isNull();
        assertThat(target.getRow(2).get("FLAG")).isNull();
        assertThat(target.getRow(2).get("CITY")).isNull();
        assertThat(target.getRow(2).get("DATE")).isNull();
        assertThat(target.getRow(2).get("TIMESTAMP")).isNull();
        assertThat(target.getRow(2).get("PRICE")).isNull();
    }

    @Test
    @DisplayName("Should support fluent read and write API")
    void testFluentSqlReadWrite() {
        CupOfTea source = createSampleDataFrame();

        // Fluent write
        source.write().sql(conn, "FLUENT_TABLE");

        // Fluent read by query
        CupOfTea queryResult = CupOfTea.read().sql(conn, "SELECT * FROM FLUENT_TABLE WHERE ID = 1");
        assertThat(queryResult.size()).isEqualTo(1);
        assertThat(queryResult.getRow(0).get("CITY")).isEqualTo("São Paulo");

        // Fluent read by table
        CupOfTea tableResult = CupOfTea.read().sqlTable(conn, "FLUENT_TABLE");
        assertThat(tableResult.size()).isEqualTo(3);

        // Fluent read/write with DataSource
        source.write().sql(dataSource, "DS_TABLE");
        CupOfTea dsResult = CupOfTea.read().sql(dataSource, "SELECT * FROM DS_TABLE WHERE ID = 2");
        assertThat(dsResult.size()).isEqualTo(1);
        assertThat(dsResult.getRow(0).get("CITY")).isEqualTo("Rio de Janeiro");
    }

    @Test
    @DisplayName("Should test CupOfTea direct convenience methods")
    void testDirectCupOfTeaConvenienceMethods() throws Exception {
        CupOfTea source = createSampleDataFrame();

        source.exportToSql(conn, "CONVENIENCE_TABLE");

        CupOfTea imported = new CupOfTea().importFromSql(conn, "SELECT * FROM CONVENIENCE_TABLE");
        assertThat(imported.size()).isEqualTo(3);

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT ID, CITY FROM CONVENIENCE_TABLE WHERE ID = 1")) {
            CupOfTea fromRs = new CupOfTea().importFromSql(rs);
            assertThat(fromRs.size()).isEqualTo(1);
            assertThat(fromRs.getRow(0).get("CITY")).isEqualTo("São Paulo");
        }
    }

    @Test
    @DisplayName("Should handle SaveMode APPEND, OVERWRITE, and FAIL_IF_EXISTS")
    void testSaveModes() {
        CupOfTea source = createSampleDataFrame();

        SqlExporter exporter = new SqlExporter(source);
        exporter.exportToTable(conn, "MODES_TABLE");

        // APPEND
        SqlExporterConfiguration appendConfig = new SqlExporterConfiguration().setSaveMode(SaveMode.APPEND);
        exporter.exportToTable(conn, "MODES_TABLE", appendConfig);

        CupOfTea appended = CupOfTea.read().sqlTable(conn, "MODES_TABLE");
        assertThat(appended.size()).isEqualTo(6);

        // OVERWRITE
        SqlExporterConfiguration overwriteConfig = new SqlExporterConfiguration().setSaveMode(SaveMode.OVERWRITE);
        exporter.exportToTable(conn, "MODES_TABLE", overwriteConfig);

        CupOfTea overwritten = CupOfTea.read().sqlTable(conn, "MODES_TABLE");
        assertThat(overwritten.size()).isEqualTo(3);

        // FAIL_IF_EXISTS
        SqlExporterConfiguration failConfig = new SqlExporterConfiguration().setSaveMode(SaveMode.FAIL_IF_EXISTS);
        assertThatThrownBy(() -> exporter.exportToTable(conn, "MODES_TABLE", failConfig))
                .isInstanceOf(ExportException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Should handle maxRows and query options in SqlImporter")
    void testSqlImporterConfiguration() {
        CupOfTea source = createSampleDataFrame();
        source.write().sql(conn, "OPTIONS_TABLE");

        SqlImporterConfiguration config = new SqlImporterConfiguration()
                .maxRows(2)
                .fetchSize(100);

        CupOfTea target = new CupOfTea();
        new SqlImporter(target).importFromTable(conn, "OPTIONS_TABLE", config);
        assertThat(target.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should work with SQLite database")
    void testSqliteDatabase() throws Exception {
        try (Connection sqliteConn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            CupOfTea source = createSampleDataFrame();
            source.write().sql(sqliteConn, "MY_SQLITE_TABLE");

            CupOfTea loaded = CupOfTea.read().sqlTable(sqliteConn, "MY_SQLITE_TABLE");
            assertThat(loaded.size()).isEqualTo(3);
            assertThat(loaded.getRow(0).get("city")).isEqualTo("São Paulo");
        }
    }
}
