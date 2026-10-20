package com.CupOfTea.table.converters;

import junit.framework.TestCase;

public class StringConverterTest extends TestCase {

    public void test_GivenAnInteger_WhenTryingToConvertToString_ShouldReturnString() throws Exception {
        StringConverter sc = new StringConverter();
        assertEquals("123", sc.convert(123, Integer.class));
    }

    public void test_GivenAString_WhenTryingToConvertToString_ShouldReturnSameString() throws Exception {
        StringConverter sc = new StringConverter();
        assertEquals("hello", sc.convert("hello", String.class));
    }

}
