package com.CupOfTea.table.converters;

import static org.junit.Assert.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import com.CupOfTea.exceptions.ConversionException;

import junit.framework.TestCase;

public class LocalDateTimeConverterTest extends TestCase {

    public void test_GivenAnIsoDateTimeString_WhenConvertingToLocalDateTime_ShouldReturnParsedDateTime() throws Exception {
        LocalDateTimeConverter converter = new LocalDateTimeConverter();
        LocalDateTime result = converter.convert("2023-05-15T14:30:00", String.class);

        assertEquals(LocalDateTime.of(2023, 5, 15, 14, 30, 0), result);
    }

    public void test_GivenADateString_WhenConvertingToLocalDateTime_ShouldReturnParsedDateTimeAtStartOfDay() throws Exception {
        LocalDateTimeConverter converter = new LocalDateTimeConverter();
        LocalDateTime result = converter.convert("2023-05-15", String.class);

        assertEquals(LocalDate.of(2023, 5, 15).atStartOfDay(), result);
    }

    public void test_GivenALocalDate_WhenConvertingToLocalDateTime_ShouldReturnDateTimeAtStartOfDay() throws Exception {
        LocalDateTimeConverter converter = new LocalDateTimeConverter();
        LocalDate localDate = LocalDate.of(2023, 5, 15);
        LocalDateTime result = converter.convert(localDate, LocalDate.class);

        assertEquals(localDate.atStartOfDay(), result);
    }

    public void test_GivenADate_WhenConvertingToLocalDateTime_ShouldReturnCorrespondingDateTime() throws Exception {
        LocalDateTimeConverter converter = new LocalDateTimeConverter();
        LocalDate localDate = LocalDate.of(2023, 5, 15);
        Date date = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

        LocalDateTime result = converter.convert(date, Date.class);
        assertEquals(localDate.atStartOfDay(), result);
    }

    public void test_GivenAnUnsupportedType_WhenConvertingToLocalDateTime_ShouldThrowException() {
        LocalDateTimeConverter converter = new LocalDateTimeConverter();
        assertThrows(ConversionException.class, () -> converter.convert(12345, Integer.class));
    }

    public void test_GivenAnInvalidDateString_WhenConvertingToLocalDateTime_ShouldThrowException() {
        LocalDateTimeConverter converter = new LocalDateTimeConverter();
        assertThrows(ConversionException.class, () -> converter.convert("invalid-datetime", String.class));
    }

}
