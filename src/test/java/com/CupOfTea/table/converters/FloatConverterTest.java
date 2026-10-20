package com.CupOfTea.table.converters;

import static org.junit.Assert.assertThrows;

import com.CupOfTea.exceptions.ConversionException;

import junit.framework.TestCase;

public class FloatConverterTest extends TestCase {

    public void test_GivenAFloat_WhenConvertingToFloat_ShouldReturnSameFloat() throws Exception {
        FloatConverter converter = new FloatConverter();
        Float value = 42.5f;
        assertEquals(value, converter.convert(value, Float.class));
    }

    public void test_GivenAString_WhenConvertingToFloat_ShouldReturnParsedFloat() throws Exception {
        FloatConverter converter = new FloatConverter();
        assertEquals(Float.valueOf(42.5f), converter.convert("42.5", String.class));
    }

    public void test_GivenAnInteger_WhenConvertingToFloat_ShouldReturnFloatValue() throws Exception {
        FloatConverter converter = new FloatConverter();
        assertEquals(Float.valueOf(42.0f), converter.convert(42, Integer.class));
    }

    public void test_GivenADouble_WhenConvertingToFloat_ShouldReturnFloatValue() throws Exception {
        FloatConverter converter = new FloatConverter();
        assertEquals(Float.valueOf(42.5f), converter.convert(42.5, Double.class));
    }

    public void test_GivenALong_WhenConvertingToFloat_ShouldReturnFloatValue() throws Exception {
        FloatConverter converter = new FloatConverter();
        assertEquals(Float.valueOf(42.0f), converter.convert(42L, Long.class));
    }

    public void test_GivenAnUnsupportedType_WhenConvertingToFloat_ShouldThrowException() {
        FloatConverter converter = new FloatConverter();
        assertThrows(ConversionException.class, () -> converter.convert(true, Boolean.class));
    }

    public void test_GivenAnInvalidString_WhenConvertingToFloat_ShouldThrowException() {
        FloatConverter converter = new FloatConverter();
        assertThrows(ConversionException.class, () -> converter.convert("not-a-number", String.class));
    }

}
