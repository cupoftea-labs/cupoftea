package com.CupOfTea.table.converters;

import static org.junit.Assert.assertThrows;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import com.CupOfTea.exceptions.ConversionException;

import junit.framework.TestCase;

public class LocalDateConverterTest extends TestCase {

    public void test_GivenAValidDateString_WhenConvertingToLocalDate_ShouldReturnParsedLocalDate() throws Exception {
        LocalDateConverter converter = new LocalDateConverter();
        LocalDate result = converter.convert("2023-05-15", String.class);

        assertEquals(LocalDate.of(2023, 5, 15), result);
    }

    public void test_GivenALocalDate_WhenConvertingToLocalDate_ShouldReturnSameLocalDate() throws Exception {
        LocalDateConverter converter = new LocalDateConverter();
        LocalDate original = LocalDate.of(2023, 5, 15);
        LocalDate result = converter.convert(original, LocalDate.class);

        assertEquals(original, result);
    }

    public void test_GivenADate_WhenConvertingToLocalDate_ShouldReturnCorrespondingLocalDate() throws Exception {
        LocalDateConverter converter = new LocalDateConverter();
        LocalDate localDate = LocalDate.of(2023, 5, 15);
        Date date = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

        LocalDate result = converter.convert(date, Date.class);
        assertEquals(localDate, result);
    }

    public void test_GivenAnUnsupportedType_WhenConvertingToLocalDate_ShouldThrowException() {
        LocalDateConverter converter = new LocalDateConverter();
        assertThrows(ConversionException.class, () -> converter.convert(12345, Integer.class));
    }

    public void test_GivenAnInvalidDateString_WhenConvertingToLocalDate_ShouldThrowException() {
        LocalDateConverter converter = new LocalDateConverter();
        assertThrows(ConversionException.class, () -> converter.convert("invalid-date", String.class));
    }

}
