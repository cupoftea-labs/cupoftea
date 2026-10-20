package com.CupOfTea.table;

import junit.framework.TestCase;

public class HeaderTest extends TestCase {

    public void test_givenAHeader_WhenRenamingColumnByName_ThenColumnTypeShouldBePreserved() throws Exception {
        Header header = new Header();
        header.addColumn("year", Integer.class);
        header.addColumn("price", Double.class);

        header.renameColumn("year", "modelYear");

        assertEquals("modelYear", header.get(0).columnName());
        assertEquals(Integer.class, header.get(0).columnType());
        assertEquals(Integer.class, header.get("modelYear").columnType());
    }

    public void test_givenAHeader_WhenRenamingColumnByIndex_ThenColumnTypeShouldBePreserved() throws Exception {
        Header header = new Header();
        header.addColumn("year", Integer.class);
        header.addColumn("price", Double.class);

        header.renameColumn(1, "amount");

        assertEquals("amount", header.get(1).columnName());
        assertEquals(Double.class, header.get(1).columnType());
        assertEquals(Double.class, header.get("amount").columnType());
    }

    public void test_givenAHeader_WhenMovingColumnByName_ThenColumnOrderAndColumnTypeShouldBePreserved() throws Exception {
        Header header = new Header();
        header.addColumn("car", String.class);
        header.addColumn("year", Integer.class);
        header.addColumn("price", Double.class);

        header.moveColumn("price", 0);

        assertEquals("price", header.get(0).columnName());
        assertEquals(Double.class, header.get(0).columnType());
        assertEquals("car", header.get(1).columnName());
        assertEquals(String.class, header.get(1).columnType());
        assertEquals("year", header.get(2).columnName());
        assertEquals(Integer.class, header.get(2).columnType());
    }

    public void test_givenAHeader_WhenMovingColumnByIndex_ThenColumnOrderAndColumnTypeShouldBePreserved() throws Exception {
        Header header = new Header();
        header.addColumn("car", String.class);
        header.addColumn("year", Integer.class);
        header.addColumn("price", Double.class);

        header.moveColumn(0, 2);

        assertEquals("year", header.get(0).columnName());
        assertEquals(Integer.class, header.get(0).columnType());
        assertEquals("price", header.get(1).columnName());
        assertEquals(Double.class, header.get(1).columnType());
        assertEquals("car", header.get(2).columnName());
        assertEquals(String.class, header.get(2).columnType());
    }
}
