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

public class FloatConverterTest {

    private FloatConverter converter;

    @BeforeEach
    void setUp() {
        converter = new FloatConverter();
    }

    @ParameterizedTest(name = "convert string ''{0}'' should return {1}f")
    @CsvSource({
        "3.14, 3.14",
        "0.0, 0.0",
        "-42.5, -42.5",
        "100, 100.0"
    })
    @DisplayName("Should convert valid strings to Float")
    void testConvertValidStringsToFloat(String input, float expected) {
        Float result = converter.convert(input, String.class);
        assertThat(result).isCloseTo(expected, offset(1e-5f));
    }

    static Stream<Arguments> numericInputsProvider() {
        return Stream.of(
            Arguments.of(42, Integer.class, 42.0f),
            Arguments.of(0, Integer.class, 0.0f),
            Arguments.of(-10, Integer.class, -10.0f),
            Arguments.of(42L, Long.class, 42.0f),
            Arguments.of(3.5d, Double.class, 3.5f),
            Arguments.of(0.0d, Double.class, 0.0f),
            Arguments.of(1.5f, Float.class, 1.5f),
            Arguments.of(-99.25f, Float.class, -99.25f)
        );
    }

    @ParameterizedTest(name = "convert {0} of type {1} should return {2}f")
    @MethodSource("numericInputsProvider")
    @DisplayName("Should convert numeric types (Integer, Long, Double, Float) to Float")
    void testConvertNumericsToFloat(Object input, Class<?> type, float expected) {
        Float result = converter.convert(input, (Class) type);
        assertThat(result).isCloseTo(expected, offset(1e-5f));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "invalid_float", "", " "})
    @DisplayName("Should throw ConversionException for invalid float strings")
    void testConvertInvalidStringToFloatThrowsException(String input) {
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
