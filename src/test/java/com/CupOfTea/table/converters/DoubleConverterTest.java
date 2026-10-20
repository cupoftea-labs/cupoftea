package com.CupOfTea.table.converters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.CupOfTea.exceptions.ConversionException;

public class DoubleConverterTest {

    private DoubleConverter converter;

    @BeforeEach
    void setUp() {
        converter = new DoubleConverter();
    }

    @ParameterizedTest(name = "convert string ''{0}'' should return {1}")
    @CsvSource({
        "3.14159, 3.14159",
        "0.0, 0.0",
        "-42.5, -42.5",
        "100, 100.0",
        "1e3, 1000.0"
    })
    @DisplayName("Should convert valid strings to Double")
    void testConvertValidStringsToDouble(String input, double expected) {
        Double result = converter.convert(input, String.class);
        assertThat(result).isCloseTo(expected, offset(1e-9));
    }

    static Stream<Arguments> numericInputsProvider() {
        return Stream.of(
            Arguments.of(42, Integer.class, 42.0),
            Arguments.of(0, Integer.class, 0.0),
            Arguments.of(-10, Integer.class, -10.0),
            Arguments.of(42L, Long.class, 42.0),
            Arguments.of(2.5f, Float.class, 2.5),
            Arguments.of(0.0f, Float.class, 0.0),
            Arguments.of(9.99d, Double.class, 9.99),
            Arguments.of(-123.456d, Double.class, -123.456)
        );
    }

    @ParameterizedTest(name = "convert {0} of type {1} should return {2}")
    @MethodSource("numericInputsProvider")
    @DisplayName("Should convert numeric types (Integer, Long, Float, Double) to Double")
    void testConvertNumericsToDouble(Object input, Class<?> type, double expected) {
        Double result = converter.convert(input, (Class) type);
        assertThat(result).isCloseTo(expected, offset(1e-9));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "invalid_double", "", " "})
    @DisplayName("Should throw ConversionException for invalid double strings")
    void testConvertInvalidStringToDoubleThrowsException(String input) {
        assertThatThrownBy(() -> converter.convert(input, String.class))
            .isInstanceOf(ConversionException.class);
    }

    @ParameterizedTest
    @ValueSource(classes = {Boolean.class, DateConverter.class, Object.class})
    @DisplayName("Should throw ConversionException for unsupported types")
    void testConvertUnsupportedTypeThrowsException(Class<?> unsupportedClass) {
        assertThatThrownBy(() -> converter.convert(Boolean.TRUE, (Class) unsupportedClass))
            .isInstanceOf(ConversionException.class);
    }
}
