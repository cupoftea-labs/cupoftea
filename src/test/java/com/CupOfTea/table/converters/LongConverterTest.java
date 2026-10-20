package com.CupOfTea.table.converters;

import static org.junit.Assert.assertThrows;

import com.CupOfTea.exceptions.ConversionException;

import junit.framework.TestCase;

public class LongConverterTest extends TestCase {

    public void test_GivenALong_WhenConvertingToLong_ShouldReturnSameLong() throws Exception {
        LongConverter converter = new LongConverter();
        Long value = 42L;
        assertEquals(value, converter.convert(value, Long.class));
    }

    public void test_GivenAString_WhenConvertingToLong_ShouldReturnParsedLong() throws Exception {
        LongConverter converter = new LongConverter();
        assertEquals(Long.valueOf(42L), converter.convert("42", String.class));
    }

    public void test_GivenAnInteger_WhenConvertingToLong_ShouldReturnLongValue() throws Exception {
        LongConverter converter = new LongConverter();
        assertEquals(Long.valueOf(42L), converter.convert(42, Integer.class));
    }

    public void test_GivenAnUnsupportedType_WhenConvertingToLong_ShouldThrowException() {
        LongConverter converter = new LongConverter();
        assertThrows(ConversionException.class, () -> converter.convert(true, Boolean.class));
    }

    public void test_GivenAnInvalidString_WhenConvertingToLong_ShouldThrowException() {
        LongConverter converter = new LongConverter();
        assertThrows(ConversionException.class, () -> converter.convert("not-a-long", String.class));
    }

}
