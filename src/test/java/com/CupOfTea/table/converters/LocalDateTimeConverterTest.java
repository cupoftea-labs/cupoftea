package com.CupOfTea.table.converters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.CupOfTea.exceptions.ConversionException;

public class LocalDateTimeConverterTest {

    private LocalDateTimeConverter converter;

    @BeforeEach
    void setUp() {
        converter = new LocalDateTimeConverter();
    }

    @Test
    @DisplayName("Should convert ISO date-time string to LocalDateTime")
    void test_GivenAnIsoDateTimeString_WhenConvertingToLocalDateTime_ShouldReturnParsedDateTime() {
        LocalDateTime result = converter.convert("2023-05-15T14:30:00", String.class);
        assertThat(result).isEqualTo(LocalDateTime.of(2023, 5, 15, 14, 30, 0));
    }

    @Test
    @DisplayName("Should convert date string to LocalDateTime at start of day")
    void test_GivenADateString_WhenConvertingToLocalDateTime_ShouldReturnParsedDateTimeAtStartOfDay() {
        LocalDateTime result = converter.convert("2023-05-15", String.class);
        assertThat(result).isEqualTo(LocalDate.of(2023, 5, 15).atStartOfDay());
    }

    @Test
    @DisplayName("Should convert LocalDate to LocalDateTime at start of day")
    void test_GivenALocalDate_WhenConvertingToLocalDateTime_ShouldReturnDateTimeAtStartOfDay() {
        LocalDate localDate = LocalDate.of(2023, 5, 15);
        LocalDateTime result = converter.convert(localDate, LocalDate.class);
        assertThat(result).isEqualTo(localDate.atStartOfDay());
    }

    @Test
    @DisplayName("Should convert java.util.Date to corresponding LocalDateTime")
    void test_GivenADate_WhenConvertingToLocalDateTime_ShouldReturnCorrespondingDateTime() {
        LocalDate localDate = LocalDate.of(2023, 5, 15);
        Date date = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

        LocalDateTime result = converter.convert(date, Date.class);
        assertThat(result).isEqualTo(localDate.atStartOfDay());
    }

    @ParameterizedTest
    @ValueSource(classes = {Integer.class, Double.class, Boolean.class, Object.class})
    @DisplayName("Should throw ConversionException for unsupported types")
    void test_GivenAnUnsupportedType_WhenConvertingToLocalDateTime_ShouldThrowException(Class<?> unsupportedClass) {
        assertThatThrownBy(() -> converter.convert(12345, (Class) unsupportedClass))
            .isInstanceOf(ConversionException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid-datetime", "not-a-datetime", "", " "})
    @DisplayName("Should throw ConversionException for invalid date-time strings")
    void test_GivenAnInvalidDateString_WhenConvertingToLocalDateTime_ShouldThrowException(String input) {
        assertThatThrownBy(() -> converter.convert(input, String.class))
            .isInstanceOf(ConversionException.class);
    }
}
