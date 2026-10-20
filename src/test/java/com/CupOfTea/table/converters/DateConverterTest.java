package com.CupOfTea.table.converters;

import static org.junit.Assert.assertThrows;

import java.text.SimpleDateFormat;
import java.util.Date;

import junit.framework.TestCase;

public class DateConverterTest extends TestCase {

    public void test_GivenADateString_WhenTryingToConvertToDate_ShouldReturnDate() throws Exception {
        DateConverter dc = new DateConverter();
        Date expected = new SimpleDateFormat("yyyy-MM-dd").parse("2024-05-15");
        assertEquals(expected, dc.convert("2024-05-15", String.class));
    }

    public void test_GivenADate_WhenTryingToConvertToDate_ShouldReturnSameDate() throws Exception {
        DateConverter dc = new DateConverter();
        Date now = new Date();
        assertEquals(now, dc.convert(now, Date.class));
    }

    public void test_GivenAnUnsupportedType_WhenTryingToConvertToDate_ShouldThrowException() {
        DateConverter dc = new DateConverter();
        assertThrows(Exception.class, () -> dc.convert(12345, Integer.class));
    }

}
