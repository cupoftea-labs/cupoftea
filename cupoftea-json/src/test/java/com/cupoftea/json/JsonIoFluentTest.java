package com.cupoftea.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.table.DataFrame;

public class JsonIoFluentTest {

    @Test
    @DisplayName("Should read JSON using CupOfTea.read().json(path) and write using df.write().json(path)")
    void testReadAndWriteFluentJson() throws Exception {
        File sample = new File("src/test/resources/testFiles/json_sample.json");
        if (!sample.exists()) {
            sample = new File("cupoftea-json/src/test/resources/testFiles/json_sample.json");
        }

        // Test CupOfTea.read().json(path)
        CupOfTea df = CupOfTea.read().json(sample.getPath());
        assertThat(df.size()).isEqualTo(4);
        assertThat(df.getHeaders()).hasSize(3);
        assertThat(df.getColumnHeader("car").columnType()).isEqualTo(String.class);
        assertThat(df.getColumnHeader("year").columnType()).isEqualTo(Integer.class);

        // Test df.write().json(path)
        File tempOut = File.createTempFile("fluent_json_out_", ".json");
        tempOut.deleteOnExit();

        df.write().json(tempOut.getPath());
        assertThat(tempOut.length()).isGreaterThan(0);

        // Read back
        CupOfTea reread = CupOfTea.read().json(tempOut.getPath());
        assertThat(reread.size()).isEqualTo(4);
        assertThat(reread.getRow(0).get("car")).isEqualTo("Mustang");
        assertThat(reread.getRow(0).get("year")).isEqualTo(2019);
    }

    @Test
    @DisplayName("Should read JSON string with schema using CupOfTea.read().json(str, schema)")
    void testReadJsonWithSchemaFluent() {
        String json = """
            [
                {"id": 1, "value": "100.5"}
            ]
            """;
        Map<String, Class<?>> schema = new LinkedHashMap<>();
        schema.put("id", Long.class);
        schema.put("value", Double.class);

        CupOfTea df = CupOfTea.read().json(json, schema);
        assertThat(df.getColumnHeader("id").columnType()).isEqualTo(Long.class);
        assertThat(df.getColumnHeader("value").columnType()).isEqualTo(Double.class);
        assertThat(df.getRow(0).get("id")).isEqualTo(1L);
        assertThat(df.getRow(0).get("value")).isEqualTo(100.5);
    }
}
