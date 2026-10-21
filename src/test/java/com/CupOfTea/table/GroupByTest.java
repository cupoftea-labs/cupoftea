package com.CupOfTea.table;

import static com.CupOfTea.table.Aggregations.average;
import static com.CupOfTea.table.Aggregations.count;
import static com.CupOfTea.table.Aggregations.listAgg;
import static com.CupOfTea.table.Aggregations.max;
import static com.CupOfTea.table.Aggregations.maximum;
import static com.CupOfTea.table.Aggregations.mean;
import static com.CupOfTea.table.Aggregations.min;
import static com.CupOfTea.table.Aggregations.minimum;
import static com.CupOfTea.table.Aggregations.sum;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;

import org.json.JSONArray;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.CupOfTea.CupOfTea;
import com.CupOfTea.exceptions.SchemaMismatchException;

public class GroupByTest {

    final String testData = """
        [
            {
                "car": "Lexus",
                "model": "LFA",
                "year": 2018,
                "price": 3500000.99
            },
            {
                "car": "Bugatti",
                "model": "Veyron",
                "year": 2006,
                "price": 7000000.77
            },
            {
                "car": "Mitsubishi",
                "model": "Lancer",
                "year": 2014,
                "price": 58749.87
            },
            {
                "car": "Mitsubishi",
                "model": "Lancer",
                "year": 2016,
                "price": 65000.33
            },
            {
                "car": "Fiat",
                "model": "Uno",
                "year": 1999,
                "price": 500.01
            },
            {
                "car": "Renault",
                "model": "Scenic",
                "year": 2008,
                "price": 25000.00
            }
        ]
        """;

    @Test
    @DisplayName("Should perform pandas-style groupBy.agg(sum, count, mean)")
    void test_pandasStyle_groupBy_agg_sum_count_mean() {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData)
          .alterColumnType("price", Double.class)
          .alterColumnType("year", Integer.class);

        // Pandas-style: df.groupBy("car").agg(sum("price"), count("model"), mean("price"));
        CupOfTea result = df.groupBy("car").agg(sum("price"), count("model"), mean("price"));

        assertThat(result.size()).isEqualTo(5);

        // Headers check: car, priceSum, modelCount, priceMean
        assertThat(result.getHeaders()).hasSize(4);
        assertThat(result.getHeaders().get(0).columnName()).isEqualTo("car");
        assertThat(result.getHeaders().get(1).columnName()).isEqualTo("priceSum");
        assertThat(result.getHeaders().get(2).columnName()).isEqualTo("modelCount");
        assertThat(result.getHeaders().get(3).columnName()).isEqualTo("priceMean");

        // Header types check
        assertThat(result.getColumnHeader("priceSum").columnType()).isEqualTo(Double.class);
        assertThat(result.getColumnHeader("modelCount").columnType()).isEqualTo(Integer.class);
        assertThat(result.getColumnHeader("priceMean").columnType()).isEqualTo(Double.class);

        // Verify Mitsubishi (2 rows aggregated)
        CupOfTea mitsubishi = result.filter(r -> "Mitsubishi".equals(r.get("car")));
        assertThat(mitsubishi.size()).isEqualTo(1);
        assertThat(((Number) mitsubishi.getRow(0).get("priceSum")).doubleValue()).isCloseTo(123750.2, offset(1e-4));
        assertThat(mitsubishi.getRow(0).get("modelCount")).isEqualTo(2);
        assertThat(((Number) mitsubishi.getRow(0).get("priceMean")).doubleValue()).isCloseTo(61875.1, offset(1e-4));

        // Verify Lexus (1 row)
        CupOfTea lexus = result.filter(r -> "Lexus".equals(r.get("car")));
        assertThat(lexus.size()).isEqualTo(1);
        assertThat(((Number) lexus.getRow(0).get("priceSum")).doubleValue()).isCloseTo(3500000.99, offset(1e-4));
        assertThat(lexus.getRow(0).get("modelCount")).isEqualTo(1);
        assertThat(((Number) lexus.getRow(0).get("priceMean")).doubleValue()).isCloseTo(3500000.99, offset(1e-4));
    }

    @Test
    @DisplayName("Should support custom aliases on aggregate columns with .as()")
    void test_groupBy_with_custom_aliases() {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData)
          .alterColumnType("price", Double.class);

        CupOfTea result = df.groupBy("car").agg(
            sum("price").as("total_price"),
            mean("price").as("avg_price"),
            count("model").as("models_count")
        );

        assertThat(result.getHeaders()).hasSize(4);
        assertThat(result.getColumnHeader("total_price")).isNotNull();
        assertThat(result.getColumnHeader("avg_price")).isNotNull();
        assertThat(result.getColumnHeader("models_count")).isNotNull();

        CupOfTea mitsubishi = result.filter(r -> "Mitsubishi".equals(r.get("car")));
        assertThat(((Number) mitsubishi.getRow(0).get("total_price")).doubleValue()).isCloseTo(123750.2, offset(1e-4));
        assertThat(((Number) mitsubishi.getRow(0).get("avg_price")).doubleValue()).isCloseTo(61875.1, offset(1e-4));
        assertThat(mitsubishi.getRow(0).get("models_count")).isEqualTo(2);
    }

    @Test
    @DisplayName("Should group by multiple columns")
    void test_groupBy_multi_columns() {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData)
          .alterColumnType("price", Double.class)
          .alterColumnType("year", Integer.class);

        CupOfTea result = df.groupBy("car", "model").agg(
            count().as("total_records"),
            sum("price")
        );

        assertThat(result.size()).isEqualTo(5);
        assertThat(result.getHeaders()).hasSize(4);
        assertThat(result.getHeaders().get(0).columnName()).isEqualTo("car");
        assertThat(result.getHeaders().get(1).columnName()).isEqualTo("model");
        assertThat(result.getHeaders().get(2).columnName()).isEqualTo("total_records");
        assertThat(result.getHeaders().get(3).columnName()).isEqualTo("priceSum");

        CupOfTea mitsubishiLancer = result.filter(r -> "Mitsubishi".equals(r.get("car")) && "Lancer".equals(r.get("model")));
        assertThat(mitsubishiLancer.size()).isEqualTo(1);
        assertThat(mitsubishiLancer.getRow(0).get("total_records")).isEqualTo(2);
        assertThat(((Number) mitsubishiLancer.getRow(0).get("priceSum")).doubleValue()).isCloseTo(123750.2, offset(1e-4));
    }

    @Test
    @DisplayName("Should aggregate with min, max, average, and listAgg")
    void test_groupBy_min_max_average_listAgg() {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData)
          .alterColumnType("price", Double.class)
          .alterColumnType("year", Integer.class);

        CupOfTea result = df.groupBy("car").agg(
            min("year"),
            maximum("year"),
            average("price"),
            listAgg("model", " | ")
        );

        CupOfTea mitsubishi = result.filter(r -> "Mitsubishi".equals(r.get("car")));
        assertThat(mitsubishi.getRow(0).get("yearMin")).isEqualTo(2014);
        assertThat(mitsubishi.getRow(0).get("yearMaximum")).isEqualTo(2016);
        assertThat(((Number) mitsubishi.getRow(0).get("priceAverage")).doubleValue()).isCloseTo(61875.1, offset(1e-4));
        assertThat(mitsubishi.getRow(0).get("modelListAgg")).isEqualTo("Lancer | Lancer");
    }

    @Test
    @DisplayName("Should support convenience aggregate methods directly on GroupBy")
    void test_groupBy_convenience_methods_on_GroupBy() {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData)
          .alterColumnType("price", Double.class)
          .alterColumnType("year", Integer.class);

        CupOfTea sumResult = df.groupBy("car").sum("price");
        assertThat(((Number) sumResult.filter(r -> "Mitsubishi".equals(r.get("car"))).getRow(0).get("priceSum")).doubleValue()).isCloseTo(123750.2, offset(1e-4));

        CupOfTea meanResult = df.groupBy("car").mean("price");
        assertThat(((Number) meanResult.filter(r -> "Mitsubishi".equals(r.get("car"))).getRow(0).get("priceMean")).doubleValue()).isCloseTo(61875.1, offset(1e-4));

        CupOfTea countResult = df.groupBy("car").count("model");
        assertThat(countResult.filter(r -> "Mitsubishi".equals(r.get("car"))).getRow(0).get("modelCount")).isEqualTo(2);

        CupOfTea minResult = df.groupBy("car").min("year");
        assertThat(minResult.filter(r -> "Mitsubishi".equals(r.get("car"))).getRow(0).get("yearMin")).isEqualTo(2014);

        CupOfTea maxResult = df.groupBy("car").max("year");
        assertThat(maxResult.filter(r -> "Mitsubishi".equals(r.get("car"))).getRow(0).get("yearMax")).isEqualTo(2016);
    }

    @Test
    @DisplayName("Should not store Group objects in cells and should export cleanly")
    void test_groupBy_no_Group_objects_in_cells_and_exports_work() throws Exception {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData)
          .alterColumnType("price", Double.class);

        CupOfTea result = df.groupBy("car").agg(sum("price"), count("model"));

        for (Row row : result.getRows()) {
            for (Object val : row.values()) {
                assertThat(val).isNotInstanceOf(Group.class);
            }
        }

        JSONArray json = result.exportToJson();
        assertThat(json.length()).isEqualTo(5);

        File tempCsv = File.createTempFile("group_by_export", ".csv");
        tempCsv.deleteOnExit();
        result.exportToCsv(tempCsv);
        String csvContent = Files.readString(tempCsv.toPath());
        assertThat(csvContent)
            .contains("car")
            .contains("priceSum")
            .contains("Mitsubishi")
            .contains("123750.2");
    }

    @Test
    @DisplayName("Should ensure aggregated frame is independent of source frame (#127)")
    void test_groupBy_independence_from_source_dataframe() {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData);

        CupOfTea result = df.groupBy("car").agg(count("model"));

        HashMap<String, Object> newRow = new HashMap<>();
        newRow.put("car", "Tesla");
        newRow.put("modelCount", 10);
        result = result.addRow(newRow);

        assertThat(df.size()).isEqualTo(6);
        assertThat(result.size()).isEqualTo(6);
    }

    @Test
    @DisplayName("Should handle null group keys and null values in aggregations")
    void test_groupBy_with_null_group_keys_and_null_aggregate_values() {
        CupOfTea df = new CupOfTea();
        df = df.addColumn("dept")
               .addColumn("salary")
               .alterColumnType("salary", Double.class);

        HashMap<String, Object> r1 = new HashMap<>();
        r1.put("dept", "IT");
        r1.put("salary", 1000.0);
        df = df.addRow(r1);

        HashMap<String, Object> r2 = new HashMap<>();
        r2.put("dept", "IT");
        r2.put("salary", null);
        df = df.addRow(r2);

        HashMap<String, Object> r3 = new HashMap<>();
        r3.put("dept", null);
        r3.put("salary", 2000.0);
        df = df.addRow(r3);

        HashMap<String, Object> r4 = new HashMap<>();
        r4.put("dept", null);
        r4.put("salary", 3000.0);
        df = df.addRow(r4);

        CupOfTea result = df.groupBy("dept").agg(sum("salary"), count("salary"), mean("salary"));

        assertThat(result.size()).isEqualTo(2);

        CupOfTea itDept = result.filter(r -> "IT".equals(r.get("dept")));
        assertThat(((Number) itDept.getRow(0).get("salarySum")).doubleValue()).isCloseTo(1000.0, offset(1e-4));
        assertThat(itDept.getRow(0).get("salaryCount")).isEqualTo(1);
        assertThat(((Number) itDept.getRow(0).get("salaryMean")).doubleValue()).isCloseTo(1000.0, offset(1e-4));

        CupOfTea nullDept = result.filter(r -> r.get("dept") == null);
        assertThat(((Number) nullDept.getRow(0).get("salarySum")).doubleValue()).isCloseTo(5000.0, offset(1e-4));
        assertThat(nullDept.getRow(0).get("salaryCount")).isEqualTo(2);
        assertThat(((Number) nullDept.getRow(0).get("salaryMean")).doubleValue()).isCloseTo(2500.0, offset(1e-4));
    }

    @Test
    @DisplayName("Should add aggregate header once and not duplicate per row (#129)")
    void test_groupBy_header_not_duplicated_issue_129() {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData).alterColumnType("price", Double.class);

        CupOfTea result = df.groupBy("car").agg(average("price"));

        assertThat(result.getHeaders()).hasSize(2);
        assertThat(result.getHeaders().get(0).columnName()).isEqualTo("car");
        assertThat(result.getHeaders().get(1).columnName()).isEqualTo("priceAverage");

        long count = result.getHeaders().stream()
                .filter(c -> "priceAverage".equals(c.columnName()))
                .count();
        assertThat(count).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should handle grouping on empty DataFrame")
    void test_groupBy_empty_dataframe() {
        CupOfTea df = new CupOfTea();
        df = df.addColumn("car")
               .addColumn("price");

        CupOfTea result = df.groupBy("car").agg(sum("price"), count("price"));
        assertThat(result.size()).isEqualTo(0);
        assertThat(result.getHeaders()).hasSize(3);
        assertThat(result.getHeaders().get(0).columnName()).isEqualTo("car");
        assertThat(result.getHeaders().get(1).columnName()).isEqualTo("priceSum");
        assertThat(result.getHeaders().get(2).columnName()).isEqualTo("priceCount");
    }

    @Test
    @DisplayName("Should throw SchemaMismatchException on non-existent columns")
    void test_groupBy_invalid_columns_throw() {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData);

        assertThatThrownBy(() -> df.groupBy("non_existing_col"))
            .isInstanceOf(SchemaMismatchException.class)
            .hasMessageContaining("does not exist");

        assertThatThrownBy(() -> df.groupBy("car").agg(sum("non_existing_price")))
            .isInstanceOf(SchemaMismatchException.class)
            .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("Should ignore NA values in aggregations")
    void test_groupBy_ignore_NA() {
        CupOfTea df = new CupOfTea();
        df.addColumn("group", String.class);
        df.addColumn("val", Double.class);
        df.addColumn("label", String.class);

        HashMap<String, Object> r1 = new HashMap<>();
        r1.put("group", "A");
        r1.put("val", 10.0);
        r1.put("label", "x");
        df.addRow(r1);

        HashMap<String, Object> r2 = new HashMap<>();
        r2.put("group", "A");
        r2.put("val", NA.NA);
        r2.put("label", NA.NA);
        df.addRow(r2);

        HashMap<String, Object> r3 = new HashMap<>();
        r3.put("group", "A");
        r3.put("val", 20.0);
        r3.put("label", "y");
        df.addRow(r3);

        CupOfTea result = df.groupBy("group").agg(
            sum("val"),
            mean("val"),
            count("val"),
            min("val"),
            max("val"),
            listAgg("label", ",")
        );

        assertThat(result.size()).isEqualTo(1);
        Row row = result.getRow(0);
        assertThat(row.get("valSum")).isEqualTo(30.0);
        assertThat(row.get("valMean")).isEqualTo(15.0);
        assertThat(row.get("valCount")).isEqualTo(2);
        assertThat(row.get("valMin")).isEqualTo(10.0);
        assertThat(row.get("valMax")).isEqualTo(20.0);
        assertThat(row.get("labelListAgg")).isEqualTo("x,y");
    }
}
