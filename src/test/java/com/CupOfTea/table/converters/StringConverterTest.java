package com.CupOfTea.table.converters;

import junit.framework.TestCase;

public class StringConverterTest extends TestCase {

    public void test_GivenAString_WhenConvertingToString_ShouldReturnSameString() throws Exception {
        StringConverter converter = new StringConverter();
        String value = "hello";
        assertEquals(value, converter.convert(value, String.class));
    }

    public void test_GivenAnInteger_WhenConvertingToString_ShouldReturnStringRepresentation() throws Exception {
        StringConverter converter = new StringConverter();
        assertEquals("42", converter.convert(42, Integer.class));
    }

    public void test_GivenADouble_WhenConvertingToString_ShouldReturnStringRepresentation() throws Exception {
        StringConverter converter = new StringConverter();
        assertEquals("42.5", converter.convert(42.5, Double.class));
    }

    public void test_GivenABoolean_WhenConvertingToString_ShouldReturnStringRepresentation() throws Exception {
        StringConverter converter = new StringConverter();
        assertEquals("true", converter.convert(true, Boolean.class));
    }

}
