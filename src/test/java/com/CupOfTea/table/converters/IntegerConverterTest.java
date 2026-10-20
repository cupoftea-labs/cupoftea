package com.CupOfTea.table.converters;

import static org.junit.Assert.assertThrows;

import com.CupOfTea.exceptions.ConversionException;

import junit.framework.TestCase;

public class IntegerConverterTest extends TestCase {

    public void test_GivenAnInteger_WhenConvertingToInteger_ShouldReturnSameInteger() throws Exception {
        IntegerConverter converter = new IntegerConverter();
        Integer value = 42;
        assertEquals(value, converter.convert(value, Integer.class));
    }

    public void test_GivenAString_WhenConvertingToInteger_ShouldReturnParsedInteger() throws Exception {
        IntegerConverter converter = new IntegerConverter();
        assertEquals(Integer.valueOf(42), converter.convert("42", String.class));
    }

    public void test_GivenADouble_WhenConvertingToInteger_ShouldReturnIntValue() throws Exception {
        IntegerConverter converter = new IntegerConverter();
        assertEquals(Integer.valueOf(42), converter.convert(42.9, Double.class));
    }

    public void test_GivenAFloat_WhenConvertingToInteger_ShouldReturnIntValue() throws Exception {
        IntegerConverter converter = new IntegerConverter();
        assertEquals(Integer.valueOf(42), converter.convert(42.9f, Float.class));
    }

    public void test_GivenALong_WhenConvertingToInteger_ShouldReturnIntValue() throws Exception {
        IntegerConverter converter = new IntegerConverter();
        assertEquals(Integer.valueOf(42), converter.convert(42L, Long.class));
    }

    public void test_GivenAnUnsupportedType_WhenConvertingToInteger_ShouldThrowException() {
        IntegerConverter converter = new IntegerConverter();
        assertThrows(ConversionException.class, () -> converter.convert(true, Boolean.class));
    }

    public void test_GivenAnInvalidString_WhenTryingToConvertToInteger_ShouldThrowException() {
        IntegerConverter ic = new IntegerConverter();
        ConversionException e = assertThrows(ConversionException.class, () -> ic.convert("abc", String.class));
        assertTrue(e.getCause() instanceof NumberFormatException);
    }

}
