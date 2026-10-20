package com.CupOfTea.table.converters;

import static org.junit.Assert.assertThrows;

import junit.framework.TestCase;

public class FloatConverterTest extends TestCase {

    public void test_GivenAStringNumber_WhenTryingToConvertToFloat_ShouldReturnFloat() throws Exception {
        FloatConverter fc = new FloatConverter();
        assertEquals(Float.valueOf(3.14f), fc.convert("3.14", String.class));
    }

    public void test_GivenAnInteger_WhenTryingToConvertToFloat_ShouldReturnFloat() throws Exception {
        FloatConverter fc = new FloatConverter();
        assertEquals(Float.valueOf(42.0f), fc.convert(42, Integer.class));
    }

    public void test_GivenADouble_WhenTryingToConvertToFloat_ShouldReturnFloat() throws Exception {
        FloatConverter fc = new FloatConverter();
        assertEquals(Float.valueOf(3.5f), fc.convert(3.5d, Double.class));
    }

    public void test_GivenAFloat_WhenTryingToConvertToFloat_ShouldReturnSameFloat() throws Exception {
        FloatConverter fc = new FloatConverter();
        assertEquals(Float.valueOf(1.5f), fc.convert(1.5f, Float.class));
    }

    public void test_GivenAnUnsupportedType_WhenTryingToConvertToFloat_ShouldThrowException() {
        FloatConverter fc = new FloatConverter();
        assertThrows(Exception.class, () -> fc.convert(Boolean.TRUE, Boolean.class));
    }

}
