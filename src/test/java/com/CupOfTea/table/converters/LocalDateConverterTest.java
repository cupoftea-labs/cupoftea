package com.CupOfTea.table.converters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.CupOfTea.exceptions.ConversionException;

public class LocalDateConverterTest {

    private LocalDateConverter converter;

    @BeforeEach
    void setUp() {
        converter = new LocalDateConverter();
    }

    @ParameterizedTest(name = "convert date string ''{0}'' should return parsed LocalDate")
    @ValueSource(strings = {"2023-05-15", "2000-01-01", "1999-12-31"})
    @DisplayName("Should convert valid date string to LocalDate")
    void testConvertValidDateStringToLocalDate(String dateString) {
        LocalDate result = converter.convert(dateString, String.class);
        assertThat(result).isEqualTo(LocalDate.parse(dateString));
    }

    @Test
    @DisplayName("Should return same LocalDate instance")
    void testConvertLocalDateToSameLocalDate() {
        LocalDate original = LocalDate.of(2023, 5, 15);
        LocalDate result = converter.convert(original, LocalDate.class);
        assertThat(result).isEqualTo(original);
    }

    @Test
    @DisplayName("Should convert java.util.Date to corresponding LocalDate")
    void testConvertDateToCorrespondingLocalDate() {
        LocalDate localDate = LocalDate.of(2023, 5, 15);
        Date date = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

        LocalDate result = converter.convert(date, Date.class);
        assertThat(result).isEqualTo(localDate);
    }

    @ParameterizedTest
    @ValueSource(classes = {Integer.class, Double.class, Boolean.class, Object.class})
    @DisplayName("Should throw ConversionException for unsupported types")
    void testConvertUnsupportedTypeThrowsException(Class<?> unsupportedClass) {
        assertThatThrownBy(() -> converter.convert(12345, (Class) unsupportedClass))
            .isInstanceOf(ConversionException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid-date", "not-a-date", "", " "})
    @DisplayName("Should throw ConversionException for invalid date strings")
    void testConvertInvalidDateStringThrowsException(String input) {
        assertThatThrownBy(() -> converter.convert(input, String.class))
            .isInstanceOf(ConversionException.class);
    }
}
