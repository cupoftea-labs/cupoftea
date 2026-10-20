package com.CupOfTea.table.converters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.CupOfTea.exceptions.ConversionException;

public class DateConverterTest {

    private DateConverter converter;

    @BeforeEach
    void setUp() {
        converter = new DateConverter();
    }

    @ParameterizedTest(name = "convert date string ''{0}'' should return parsed Date")
    @ValueSource(strings = {"2024-05-15", "2000-01-01", "1999-12-31"})
    @DisplayName("Should convert valid date strings (yyyy-MM-dd) to Date")
    void testConvertValidDateStringsToDate(String dateString) throws Exception {
        Date expected = new SimpleDateFormat("yyyy-MM-dd").parse(dateString);
        Date result = converter.convert(dateString, String.class);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("Should return same Date instance when converting Date type")
    void testConvertDateToSameDate() {
        Date now = new Date();
        Date result = converter.convert(now, Date.class);
        assertThat(result).isEqualTo(now);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-date", "invalid-format", "abc", "", "   "})
    @DisplayName("Should throw ConversionException for invalid date strings")
    void testConvertInvalidDateStringThrowsException(String input) {
        assertThatThrownBy(() -> converter.convert(input, String.class))
            .isInstanceOf(ConversionException.class);
    }

    @ParameterizedTest
    @ValueSource(classes = {Integer.class, Double.class, Boolean.class, Object.class})
    @DisplayName("Should throw ConversionException for unsupported types")
    void testConvertUnsupportedTypeThrowsException(Class<?> unsupportedClass) {
        assertThatThrownBy(() -> converter.convert(12345, (Class) unsupportedClass))
            .isInstanceOf(ConversionException.class);
    }
}
