package com.CupOfTea.table.converters;

import static org.junit.Assert.assertThrows;

import junit.framework.TestCase;

public class IntegerConverterTest extends TestCase {

    public void test_GivenAStringNumber_WhenTryingToConvertToInteger_ShouldReturnInteger() throws Exception {
        IntegerConverter ic = new IntegerConverter();
        assertEquals(Integer.valueOf(42), ic.convert("42", String.class));
    }

    public void test_GivenAnInteger_WhenTryingToConvertToInteger_ShouldReturnSameInteger() throws Exception {
        IntegerConverter ic = new IntegerConverter();
        assertEquals(Integer.valueOf(42), ic.convert(42, Integer.class));
    }

    public void test_GivenAnInvalidString_WhenTryingToConvertToInteger_ShouldThrowException() {
        IntegerConverter ic = new IntegerConverter();
        assertThrows(NumberFormatException.class, () -> ic.convert("abc", String.class));
    }

    public void test_GivenAnUnsupportedType_WhenTryingToConvertToInteger_ShouldThrowException() {
        IntegerConverter ic = new IntegerConverter();
        assertThrows(Exception.class, () -> ic.convert(Boolean.TRUE, Boolean.class));
    }

}
