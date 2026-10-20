package com.CupOfTea.table.converters;

import static org.junit.Assert.assertThrows;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import com.CupOfTea.exceptions.ConversionException;

import junit.framework.TestCase;

public class DateConverterTest extends TestCase {

    public void test_GivenAValidDateString_WhenConvertingToDate_ShouldReturnParsedDate() throws Exception {
        DateConverter converter = new DateConverter();
        Date result = converter.convert("2023-05-15", String.class);

        LocalDate expectedLocalDate = LocalDate.of(2023, 5, 15);
        Date expectedDate = Date.from(expectedLocalDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

        assertEquals(expectedDate, result);
    }

    public void test_GivenADate_WhenConvertingToDate_ShouldReturnSameDate() throws Exception {
        DateConverter converter = new DateConverter();
        Date original = new Date();
        Date result = converter.convert(original, Date.class);

        assertEquals(original, result);
    }

    public void test_GivenALocalDate_WhenConvertingToDate_ShouldReturnCorrespondingDate() throws Exception {
        DateConverter converter = new DateConverter();
        LocalDate localDate = LocalDate.of(2023, 5, 15);
        Date result = converter.convert(localDate, LocalDate.class);

        Date expectedDate = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        assertEquals(expectedDate, result);
    }

    public void test_GivenAnUnsupportedType_WhenConvertingToDate_ShouldThrowException() {
        DateConverter converter = new DateConverter();
        assertThrows(ConversionException.class, () -> converter.convert(12345, Integer.class));
    }

    public void test_GivenAnInvalidDateString_WhenConvertingToDate_ShouldThrowException() {
        DateConverter converter = new DateConverter();
        assertThrows(ConversionException.class, () -> converter.convert("invalid-date", String.class));
    }

}
