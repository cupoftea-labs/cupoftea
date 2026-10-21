package com.cupoftea.importers;

import static com.cupoftea.table.Schema.DATE;
import static com.cupoftea.table.Schema.DECIMAL;
import static com.cupoftea.table.Schema.DOUBLE;
import static com.cupoftea.table.Schema.INT;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.json.JSONArray;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.table.Column;
import com.cupoftea.table.Schema;

public class JsonImporterTest {

    
    private File getSampleJson() {
        File[] candidates = new File[]{
            new File("src/test/resources/testFiles/json_sample.json"),
            new File("cupoftea-json/src/test/resources/testFiles/json_sample.json"),
            new File("src/test/java/com/cupoftea/testFiles/json_sample.json"),
            new File("src/test/java/com/CupOfTea/testFiles/json_sample.json")
        };
        for (File candidate : candidates) {
            if (candidate.exists()) return candidate;
        }
        return candidates[0];
    }

    @Test
    @DisplayName("Should import JSON file and preserve types")
    void test_GivenAJsonFile_WhenImporting_ShouldReturnACupOfTeaDataFrameWithPreservedTypes() {
        int expectedColumnsNumber = 3;
        int expectedRowsNumber = 4;

        File testFile = getSampleJson();
        CupOfTea cupOfTea = new CupOfTea();

        cupOfTea.importFromJson(testFile);

        assertThat(cupOfTea.getHeaders()).hasSize(expectedColumnsNumber);
        assertThat(cupOfTea.getRows()).hasSize(expectedRowsNumber);
        assertThat(cupOfTea.getColumnHeader("car").columnType()).isEqualTo(String.class);
        assertThat(cupOfTea.getColumnHeader("year").columnType()).isEqualTo(Integer.class);
        assertThat(cupOfTea.getColumnHeader("color").columnType()).isEqualTo(String.class);
        assertThat(cupOfTea.getRow(0).get("car")).isEqualTo("Mustang");
        assertThat(cupOfTea.getRow(0).get("year")).isEqualTo(2019);
        assertThat(cupOfTea.getRow(0).get("color")).isEqualTo("white");
    }

    @Test
    @DisplayName("Should preserve primitive types and missing values when importing JSON string")
    void test_GivenAJsonStringWithPrimitivesAndNull_WhenImporting_ShouldPreserveTypesAndMissingValues() {
        String json = """
            [
                {
                    "name": "Alice",
                    "age": 30,
                    "bigId": 9876543210,
                    "balance": 1250.75,
                    "active": true,
                    "nickname": null,
                    "emptyCol": null
                },
                {
                    "name": "Bob",
                    "age": 25,
                    "bigId": 9876543211,
                    "balance": 500.25,
                    "active": false,
                    "nickname": "Bobby",
                    "emptyCol": null
                }
            ]
            """;

        CupOfTea df = new CupOfTea().importFromJson(json);

        assertThat(df.size()).isEqualTo(2);
        assertThat(df.getColumnHeader("name").columnType()).isEqualTo(String.class);
        assertThat(df.getColumnHeader("age").columnType()).isEqualTo(Integer.class);
        assertThat(df.getColumnHeader("bigId").columnType()).isEqualTo(Long.class);
        assertThat(df.getColumnHeader("balance").columnType()).isEqualTo(Double.class);
        assertThat(df.getColumnHeader("active").columnType()).isEqualTo(Boolean.class);
        assertThat(df.getColumnHeader("nickname").columnType()).isEqualTo(String.class);
        assertThat(df.getColumnHeader("emptyCol").columnType()).isNull();

        assertThat(df.getRow(0).get("name")).isEqualTo("Alice");
        assertThat(df.getRow(0).get("age")).isEqualTo(30);
        assertThat(df.getRow(0).get("bigId")).isEqualTo(9876543210L);
        assertThat(df.getRow(0).get("balance")).isEqualTo(1250.75);
        assertThat(df.getRow(0).get("active")).isEqualTo(Boolean.TRUE);
        assertThat(df.getRow(0).get("nickname")).isNull();
        assertThat(df.getRow(0).get("emptyCol")).isNull();

        assertThat(df.getRow(1).get("nickname")).isEqualTo("Bobby");
        assertThat(df.getRow(1).get("active")).isEqualTo(Boolean.FALSE);
    }

    @Test
    @DisplayName("Should promote mixed Integer and Double column to Double")
    void test_GivenJsonWithMixedIntegerAndDouble_WhenImporting_ShouldPromoteColumnToDouble() {
        String json = """
            [
                {"item": "A", "price": 100},
                {"item": "B", "price": 150.5}
            ]
            """;

        CupOfTea df = new CupOfTea().importFromJson(json);

        assertThat(df.getColumnHeader("price").columnType()).isEqualTo(Double.class);
        assertThat(df.getRow(0).get("price")).isEqualTo(100.0);
        assertThat(df.getRow(1).get("price")).isEqualTo(150.5);
    }

    @Test
    @DisplayName("Should promote mixed Integer and Long column to Long")
    void test_GivenJsonWithMixedIntegerAndLong_WhenImporting_ShouldPromoteColumnToLong() {
        String json = """
            [
                {"item": "A", "count": 42},
                {"item": "B", "count": 5000000000}
            ]
            """;

        CupOfTea df = new CupOfTea().importFromJson(json);

        assertThat(df.getColumnHeader("count").columnType()).isEqualTo(Long.class);
        assertThat(df.getRow(0).get("count")).isEqualTo(42L);
        assertThat(df.getRow(1).get("count")).isEqualTo(5000000000L);
    }

    @Test
    @DisplayName("Should apply explicit schema types on JSON import")
    void test_GivenJsonWithExplicitSchema_WhenImporting_ShouldApplySchemaTypes() {
        String json = """
            [
                {"year": "2020", "price": 100, "inStock": "true"},
                {"year": "2021", "price": 200, "inStock": "false"}
            ]
            """;

        Map<String, Class<?>> schema = new LinkedHashMap<>();
        schema.put("year", Integer.class);
        schema.put("price", Double.class);
        schema.put("inStock", Boolean.class);

        CupOfTea df = new CupOfTea().importFromJson(json, schema);

        assertThat(df.getColumnHeader("year").columnType()).isEqualTo(Integer.class);
        assertThat(df.getColumnHeader("price").columnType()).isEqualTo(Double.class);
        assertThat(df.getColumnHeader("inStock").columnType()).isEqualTo(Boolean.class);

        assertThat(df.getRow(0).get("year")).isEqualTo(2020);
        assertThat(df.getRow(0).get("price")).isEqualTo(100.0);
        assertThat(df.getRow(0).get("inStock")).isEqualTo(Boolean.TRUE);
        assertThat(df.getRow(1).get("inStock")).isEqualTo(Boolean.FALSE);
    }

    @Test
    @DisplayName("Should respect preconfigured headers on DataFrame when importing JSON")
    void test_GivenPreconfiguredHeadersOnDataFrame_WhenImportingJson_ShouldRespectHeaderTypes() {
        ArrayList<Column> headers = new ArrayList<>();
        headers.add(new Column("year", Long.class));
        headers.add(new Column("price", Float.class));

        CupOfTea df = new CupOfTea()
            .setHeaders(headers)
            .importFromJson(new JSONArray("[{\"year\": 2022, \"price\": 99.5}]"));

        assertThat(df.getColumnHeader("year").columnType()).isEqualTo(Long.class);
        assertThat(df.getColumnHeader("price").columnType()).isEqualTo(Float.class);
        assertThat(df.getRow(0).get("year")).isEqualTo(2022L);
        assertThat(df.getRow(0).get("price")).isEqualTo(99.5f);
    }

    @Test
    @DisplayName("Should produce empty DataFrame when importing empty JSON array")
    void test_GivenEmptyJsonArray_WhenImporting_ShouldProduceEmptyDataFrame() {
        CupOfTea df = new CupOfTea().importFromJson("[]");

        assertThat(df.getHeaders()).isEmpty();
        assertThat(df.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should import JSON with explicit Schema.of and coerce types")
    void test_GivenJsonWithSchemaOf_WhenImporting_ShouldApplySchemaTypes() {
        String json = """
            [
                {"year": "2023", "price": "199.99", "salary": "5000.50", "created": "2023-11-20"},
                {"year": "2024", "price": "299.99", "salary": "6000.75", "created": "2024-05-10"}
            ]
            """;

        Schema schema = Schema.of("year", INT, "price", DOUBLE, "salary", DECIMAL, "created", DATE);
        CupOfTea df = new CupOfTea().importFromJson(json, schema);

        assertThat(df.getColumnHeader("year").columnType()).isEqualTo(Integer.class);
        assertThat(df.getColumnHeader("price").columnType()).isEqualTo(Double.class);
        assertThat(df.getColumnHeader("salary").columnType()).isEqualTo(BigDecimal.class);
        assertThat(df.getColumnHeader("created").columnType()).isEqualTo(LocalDate.class);

        assertThat(df.getRow(0).get("year")).isEqualTo(2023);
        assertThat(df.getRow(0).get("price")).isEqualTo(199.99);
        assertThat(df.getRow(0).get("salary")).isEqualTo(new BigDecimal("5000.50"));
        assertThat(df.getRow(0).get("created")).isEqualTo(LocalDate.of(2023, 11, 20));
    }
}
