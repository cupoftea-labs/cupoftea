package com.CupOfTea.table;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class HeaderTest {

    @Test
    @DisplayName("Should preserve column type when renaming column by name")
    void test_givenAHeader_WhenRenamingColumnByName_ThenColumnTypeShouldBePreserved() {
        Header header = new Header();
        header.addColumn("year", Integer.class);
        header.addColumn("price", Double.class);

        header.renameColumn("year", "modelYear");

        assertThat(header.get(0).columnName()).isEqualTo("modelYear");
        assertThat(header.get(0).columnType()).isEqualTo(Integer.class);
        assertThat(header.get("modelYear").columnType()).isEqualTo(Integer.class);
    }

    @Test
    @DisplayName("Should preserve column type when renaming column by index")
    void test_givenAHeader_WhenRenamingColumnByIndex_ThenColumnTypeShouldBePreserved() {
        Header header = new Header();
        header.addColumn("year", Integer.class);
        header.addColumn("price", Double.class);

        header.renameColumn(1, "amount");

        assertThat(header.get(1).columnName()).isEqualTo("amount");
        assertThat(header.get(1).columnType()).isEqualTo(Double.class);
        assertThat(header.get("amount").columnType()).isEqualTo(Double.class);
    }

    @Test
    @DisplayName("Should preserve column order and column type when moving column by name")
    void test_givenAHeader_WhenMovingColumnByName_ThenColumnOrderAndColumnTypeShouldBePreserved() {
        Header header = new Header();
        header.addColumn("car", String.class);
        header.addColumn("year", Integer.class);
        header.addColumn("price", Double.class);

        header.moveColumn("price", 0);

        assertThat(header.get(0).columnName()).isEqualTo("price");
        assertThat(header.get(0).columnType()).isEqualTo(Double.class);
        assertThat(header.get(1).columnName()).isEqualTo("car");
        assertThat(header.get(1).columnType()).isEqualTo(String.class);
        assertThat(header.get(2).columnName()).isEqualTo("year");
        assertThat(header.get(2).columnType()).isEqualTo(Integer.class);
    }

    @Test
    @DisplayName("Should preserve column order and column type when moving column by index")
    void test_givenAHeader_WhenMovingColumnByIndex_ThenColumnOrderAndColumnTypeShouldBePreserved() {
        Header header = new Header();
        header.addColumn("car", String.class);
        header.addColumn("year", Integer.class);
        header.addColumn("price", Double.class);

        header.moveColumn(0, 2);

        assertThat(header.get(0).columnName()).isEqualTo("year");
        assertThat(header.get(0).columnType()).isEqualTo(Integer.class);
        assertThat(header.get(1).columnName()).isEqualTo("price");
        assertThat(header.get(1).columnType()).isEqualTo(Double.class);
        assertThat(header.get(2).columnName()).isEqualTo("car");
        assertThat(header.get(2).columnType()).isEqualTo(String.class);
    }
}
