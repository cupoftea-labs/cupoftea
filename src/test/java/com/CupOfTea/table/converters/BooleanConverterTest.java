package com.CupOfTea.table.converters;

import static org.junit.Assert.assertThrows;

import junit.framework.TestCase;

public class BooleanConverterTest extends TestCase {

    public void test_GivenAStringTrue_WhenTryingToConvertToBoolean_ShouldReturnABooleanTrue() throws Exception {
        
        Boolean expectedValue = true;

        String valueToConvert = "True";

        BooleanConverter bc = new BooleanConverter();

        Boolean convertedValue = bc.convert(valueToConvert, String.class);

        assertEquals(expectedValue, convertedValue);

    }

    public void test_GivenAStringFalse_WhenTryingToConvertToBoolean_ShouldReturnABooleanFalse() throws Exception {
        
        Boolean expectedValue = false;

        String valueToConvert = "fAlse";

        BooleanConverter bc = new BooleanConverter();

        Boolean convertedValue = bc.convert(valueToConvert, String.class);

        assertEquals(expectedValue, convertedValue);

    }

    public void test_GivenAString_WhenTryingToConvertToBoolean_ShouldReturnAException() throws Exception {
        
        Boolean expectedValue = true;

        String valueToConvert = "AnyValue";

        BooleanConverter bc = new BooleanConverter();

        assertThrows(Exception.class, () -> bc.convert(valueToConvert, String.class));

    }

    public void test_GivenAInterger1_WhenTryingToConvertToBoolean_ShouldReturnABooleanTrue() throws Exception {
        
        Boolean expectedValue = true;

        Integer valueToConvert = 1;

        BooleanConverter bc = new BooleanConverter();

        Boolean convertedValue = bc.convert(valueToConvert, Integer.class);

        assertEquals(expectedValue, convertedValue);

    }

    public void test_GivenAInteger0_WhenTryingToConvertToBoolean_ShouldReturnABooleanFalse() throws Exception {
        
        Boolean expectedValue = false;

        Integer valueToConvert = 0;

        BooleanConverter bc = new BooleanConverter();

        Boolean convertedValue = bc.convert(valueToConvert, Integer.class);

        assertEquals(expectedValue, convertedValue);

    }

    public void test_GivenAInteger_WhenTryingToConvertToBoolean_ShouldReturnAException() throws Exception {
        
        Boolean expectedValue = true;

        Integer valueToConvert = 1234;

        BooleanConverter bc = new BooleanConverter();

        assertThrows(Exception.class, () -> bc.convert(valueToConvert, Integer.class));

    }

}
