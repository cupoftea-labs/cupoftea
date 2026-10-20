package com.CupOfTea.table.converters;

import static org.junit.Assert.assertThrows;

import junit.framework.TestCase;

public class DoubleConverterTest extends TestCase {

    public void test_GivenAStringNumber_WhenTryingToConvertToDouble_ShouldReturnDouble() throws Exception {
        DoubleConverter dc = new DoubleConverter();
        assertEquals(Double.valueOf(3.14159), dc.convert("3.14159", String.class));
    }

    public void test_GivenAnInteger_WhenTryingToConvertToDouble_ShouldReturnDouble() throws Exception {
        DoubleConverter dc = new DoubleConverter();
        assertEquals(Double.valueOf(42.0), dc.convert(42, Integer.class));
    }

    public void test_GivenAFloat_WhenTryingToConvertToDouble_ShouldReturnDouble() throws Exception {
        DoubleConverter dc = new DoubleConverter();
        assertEquals(Double.valueOf(2.5), dc.convert(2.5f, Float.class));
    }

    public void test_GivenADouble_WhenTryingToConvertToDouble_ShouldReturnSameDouble() throws Exception {
        DoubleConverter dc = new DoubleConverter();
        assertEquals(Double.valueOf(9.99), dc.convert(9.99d, Double.class));
    }

    public void test_GivenAnUnsupportedType_WhenTryingToConvertToDouble_ShouldThrowException() {
        DoubleConverter dc = new DoubleConverter();
        assertThrows(Exception.class, () -> dc.convert(Boolean.TRUE, Boolean.class));
    }

}
