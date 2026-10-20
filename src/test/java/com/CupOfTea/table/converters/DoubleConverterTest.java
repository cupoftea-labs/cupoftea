package com.CupOfTea.table.converters;

import static org.junit.Assert.assertThrows;

import com.CupOfTea.exceptions.ConversionException;

import junit.framework.TestCase;

public class DoubleConverterTest extends TestCase {

    public void test_GivenADouble_WhenConvertingToDouble_ShouldReturnSameDouble() throws Exception {
        DoubleConverter converter = new DoubleConverter();
        Double value = 42.5;
        assertEquals(value, converter.convert(value, Double.class));
    }

    public void test_GivenAString_WhenConvertingToDouble_ShouldReturnParsedDouble() throws Exception {
        DoubleConverter converter = new DoubleConverter();
        assertEquals(Double.valueOf(42.5), converter.convert("42.5", String.class));
    }

    public void test_GivenAnInteger_WhenConvertingToDouble_ShouldReturnDoubleValue() throws Exception {
        DoubleConverter converter = new DoubleConverter();
        assertEquals(Double.valueOf(42.0), converter.convert(42, Integer.class));
    }

    public void test_GivenAFloat_WhenConvertingToDouble_ShouldReturnDoubleValue() throws Exception {
        DoubleConverter converter = new DoubleConverter();
        assertEquals(Double.valueOf(42.5), converter.convert(42.5f, Float.class));
    }

    public void test_GivenALong_WhenConvertingToDouble_ShouldReturnDoubleValue() throws Exception {
        DoubleConverter converter = new DoubleConverter();
        assertEquals(Double.valueOf(42.0), converter.convert(42L, Long.class));
    }

    public void test_GivenAnUnsupportedType_WhenConvertingToDouble_ShouldThrowException() {
        DoubleConverter converter = new DoubleConverter();
        assertThrows(ConversionException.class, () -> converter.convert(true, Boolean.class));
    }

    public void test_GivenAnInvalidString_WhenConvertingToDouble_ShouldThrowException() {
        DoubleConverter converter = new DoubleConverter();
        assertThrows(ConversionException.class, () -> converter.convert("not-a-number", String.class));
    }

}
