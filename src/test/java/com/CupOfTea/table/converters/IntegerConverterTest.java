package com.CupOfTea.table.converters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.CupOfTea.exceptions.ConversionException;

public class IntegerConverterTest {

    private IntegerConverter converter;

    @BeforeEach
    void setUp() {
        converter = new IntegerConverter();
    }

    @ParameterizedTest(name = "convert string ''{0}'' should return {1}")
    @CsvSource({
        "42, 42",
        "0, 0",
        "-100, -100",
        "2147483647, 2147483647",
        "-2147483648, -2147483648"
    })
    @DisplayName("Should convert valid integer strings to Integer")
    void testConvertValidStringsToInteger(String input, int expected) {
        Integer result = converter.convert(input, String.class);
        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest(name = "convert integer {0} should return same integer")
    @ValueSource(ints = {42, 0, -100, Integer.MIN_VALUE, Integer.MAX_VALUE})
    @DisplayName("Should convert Integer to same Integer")
    void testConvertIntegerToSameInteger(int input) {
        Integer result = converter.convert(input, Integer.class);
        assertThat(result).isEqualTo(input);
    }

    @ParameterizedTest(name = "convert invalid string ''{0}'' should throw ConversionException")
    @ValueSource(strings = {"abc", "12.34", "99999999999999999999", "", "   "})
    @DisplayName("Should throw ConversionException for invalid numeric strings")
    void testConvertInvalidStringToIntegerThrowsException(String input) {
        assertThatThrownBy(() -> converter.convert(input, String.class))
            .isInstanceOf(ConversionException.class)
            .hasCauseInstanceOf(NumberFormatException.class);
    }

    @ParameterizedTest(name = "convert unsupported type {0} should throw ConversionException")
    @ValueSource(classes = {Boolean.class, DateConverter.class, Object.class})
    @DisplayName("Should throw ConversionException for unsupported types")
    void testConvertUnsupportedTypeThrowsException(Class<?> unsupportedClass) {
        assertThatThrownBy(() -> converter.convert(Boolean.TRUE, (Class) unsupportedClass))
            .isInstanceOf(ConversionException.class);
    }
}
