package com.cupoftea.table.converters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.cupoftea.exceptions.ConversionException;

public class LongConverterTest {

    private LongConverter converter;

    @BeforeEach
    void setUp() {
        converter = new LongConverter();
    }

    @ParameterizedTest(name = "convert string ''{0}'' should return {1}L")
    @CsvSource({
        "42, 42",
        "0, 0",
        "-100, -100",
        "9223372036854775807, 9223372036854775807",
        "-9223372036854775808, -9223372036854775808"
    })
    @DisplayName("Should convert valid numeric strings to Long")
    void testConvertValidStringsToLong(String input, long expected) {
        Long result = converter.convert(input, String.class);
        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest(name = "convert long {0} should return same long")
    @ValueSource(longs = {42L, 0L, -100L, Long.MIN_VALUE, Long.MAX_VALUE})
    @DisplayName("Should convert Long to same Long")
    void testConvertLongToSameLong(long input) {
        Long result = converter.convert(input, Long.class);
        assertThat(result).isEqualTo(input);
    }

    static Stream<Arguments> numericInputsProvider() {
        return Stream.of(
            Arguments.of(42, Integer.class, 42L),
            Arguments.of(0, Integer.class, 0L),
            Arguments.of(42.5, Double.class, 42L),
            Arguments.of(42.5f, Float.class, 42L)
        );
    }

    @ParameterizedTest(name = "convert {0} of type {1} should return {2}L")
    @MethodSource("numericInputsProvider")
    @DisplayName("Should convert numeric types (Integer, Double, Float) to Long")
    void testConvertNumericsToLong(Object input, Class<?> type, long expected) {
        Long result = converter.convert(input, (Class) type);
        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "not-a-long", "", " "})
    @DisplayName("Should throw ConversionException for invalid numeric strings")
    void testConvertInvalidStringToLongThrowsException(String input) {
        assertThatThrownBy(() -> converter.convert(input, String.class))
            .isInstanceOf(ConversionException.class);
    }

    @ParameterizedTest
    @ValueSource(classes = {Boolean.class, DateConverter.class, Object.class})
    @DisplayName("Should throw ConversionException for unsupported types")
    void testConvertUnsupportedTypeThrowsException(Class<?> unsupportedClass) {
        assertThatThrownBy(() -> converter.convert(true, (Class) unsupportedClass))
            .isInstanceOf(ConversionException.class);
    }
}
