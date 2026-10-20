package com.CupOfTea.importers;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.json.JSONArray;

import com.CupOfTea.CupOfTea;
import com.CupOfTea.table.Column;

import junit.framework.TestCase;

public class JsonImporterTest extends TestCase {

    public void test_GivenAJsonFile_WhenImporting_ShouldReturnACupOfTeaDataFrameWithPreservedTypes() throws Exception {

        int expectedColumnsNumber = 3;
        int expectedRowsNumber = 4;

        File testFile = new File("src/test/java/com/CupOfTea/testFiles/json_sample.json");

        CupOfTea cupOfTea = new CupOfTea();

        cupOfTea.importFromJson(testFile);

        assertEquals(expectedColumnsNumber, cupOfTea.getHeaders().size());
        assertEquals(expectedRowsNumber, cupOfTea.getRows().size());
        assertEquals(String.class, cupOfTea.getColumnHeader("car").columnType());
        assertEquals(Integer.class, cupOfTea.getColumnHeader("year").columnType());
        assertEquals(String.class, cupOfTea.getColumnHeader("color").columnType());
        assertEquals("Mustang", cupOfTea.getRow(0).get("car"));
        assertEquals(Integer.valueOf(2019), cupOfTea.getRow(0).get("year"));
        assertEquals("white", cupOfTea.getRow(0).get("color"));

    }

    public void test_GivenAJsonStringWithPrimitivesAndNull_WhenImporting_ShouldPreserveTypesAndMissingValues() throws Exception {
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

        assertEquals(2, df.size().intValue());
        assertEquals(String.class, df.getColumnHeader("name").columnType());
        assertEquals(Integer.class, df.getColumnHeader("age").columnType());
        assertEquals(Long.class, df.getColumnHeader("bigId").columnType());
        assertEquals(Double.class, df.getColumnHeader("balance").columnType());
        assertEquals(Boolean.class, df.getColumnHeader("active").columnType());
        assertEquals(String.class, df.getColumnHeader("nickname").columnType());
        assertNull(df.getColumnHeader("emptyCol").columnType());

        assertEquals("Alice", df.getRow(0).get("name"));
        assertEquals(Integer.valueOf(30), df.getRow(0).get("age"));
        assertEquals(Long.valueOf(9876543210L), df.getRow(0).get("bigId"));
        assertEquals(Double.valueOf(1250.75), df.getRow(0).get("balance"));
        assertEquals(Boolean.TRUE, df.getRow(0).get("active"));
        assertNull(df.getRow(0).get("nickname"));
        assertNull(df.getRow(0).get("emptyCol"));

        assertEquals("Bobby", df.getRow(1).get("nickname"));
        assertEquals(Boolean.FALSE, df.getRow(1).get("active"));
    }

    public void test_GivenJsonWithMixedIntegerAndDouble_WhenImporting_ShouldPromoteColumnToDouble() throws Exception {
        String json = """
            [
                {"item": "A", "price": 100},
                {"item": "B", "price": 150.5}
            ]
            """;

        CupOfTea df = new CupOfTea().importFromJson(json);

        assertEquals(Double.class, df.getColumnHeader("price").columnType());
        assertEquals(Double.valueOf(100.0), df.getRow(0).get("price"));
        assertEquals(Double.valueOf(150.5), df.getRow(1).get("price"));
    }

    public void test_GivenJsonWithMixedIntegerAndLong_WhenImporting_ShouldPromoteColumnToLong() throws Exception {
        String json = """
            [
                {"item": "A", "count": 42},
                {"item": "B", "count": 5000000000}
            ]
            """;

        CupOfTea df = new CupOfTea().importFromJson(json);

        assertEquals(Long.class, df.getColumnHeader("count").columnType());
        assertEquals(Long.valueOf(42L), df.getRow(0).get("count"));
        assertEquals(Long.valueOf(5000000000L), df.getRow(1).get("count"));
    }

    public void test_GivenJsonWithExplicitSchema_WhenImporting_ShouldApplySchemaTypes() throws Exception {
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

        assertEquals(Integer.class, df.getColumnHeader("year").columnType());
        assertEquals(Double.class, df.getColumnHeader("price").columnType());
        assertEquals(Boolean.class, df.getColumnHeader("inStock").columnType());

        assertEquals(Integer.valueOf(2020), df.getRow(0).get("year"));
        assertEquals(Double.valueOf(100.0), df.getRow(0).get("price"));
        assertEquals(Boolean.TRUE, df.getRow(0).get("inStock"));
        assertEquals(Boolean.FALSE, df.getRow(1).get("inStock"));
    }

    public void test_GivenPreconfiguredHeadersOnDataFrame_WhenImportingJson_ShouldRespectHeaderTypes() throws Exception {
        ArrayList<Column> headers = new ArrayList<>();
        headers.add(new Column("year", Long.class));
        headers.add(new Column("price", Float.class));

        CupOfTea df = new CupOfTea()
            .setHeaders(headers)
            .importFromJson(new JSONArray("[{\"year\": 2022, \"price\": 99.5}]"));

        assertEquals(Long.class, df.getColumnHeader("year").columnType());
        assertEquals(Float.class, df.getColumnHeader("price").columnType());
        assertEquals(Long.valueOf(2022L), df.getRow(0).get("year"));
        assertEquals(Float.valueOf(99.5f), df.getRow(0).get("price"));
    }

    public void test_GivenEmptyJsonArray_WhenImporting_ShouldProduceEmptyDataFrame() throws Exception {
        CupOfTea df = new CupOfTea().importFromJson("[]");

        assertEquals(0, df.getHeaders().size());
        assertEquals(0, df.size().intValue());
    }

}
