package com.CupOfTea.table.converters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.CupOfTea.exceptions.ConversionException;

public class BooleanConverterTest {

    private BooleanConverter converter;

    @BeforeEach
    void setUp() {
        converter = new BooleanConverter();
    }

    @ParameterizedTest(name = "convert string ''{0}'' should return {1}")
    @CsvSource({
        "True, true",
        "true, true",
        "TRUE, true",
        "tRuE, true",
        "False, false",
        "false, false",
        "FALSE, false",
        "fAlse, false"
    })
    @DisplayName("Should convert valid string representations to boolean")
    void testConvertValidStringsToBoolean(String input, boolean expected) {
        Boolean result = converter.convert(input, String.class);
        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest(name = "convert integer {0} should return {1}")
    @CsvSource({
        "1, true",
        "0, false"
    })
    @DisplayName("Should convert integer 1 and 0 to boolean")
    void testConvertValidIntegersToBoolean(int input, boolean expected) {
        Boolean result = converter.convert(input, Integer.class);
        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"AnyValue", "yes", "no", "2", "-1", "1", "0", "", " "})
    @DisplayName("Should throw ConversionException for invalid string representations")
    void testConvertInvalidStringToBooleanThrowsException(String input) {
        assertThatThrownBy(() -> converter.convert(input, String.class))
            .isInstanceOf(ConversionException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {1234, -1, 2, 100})
    @DisplayName("Should throw ConversionException for invalid integer values")
    void testConvertInvalidIntegerToBooleanThrowsException(int input) {
        assertThatThrownBy(() -> converter.convert(input, Integer.class))
            .isInstanceOf(ConversionException.class);
    }

    @ParameterizedTest
    @ValueSource(classes = {Double.class, Float.class, Object.class})
    @DisplayName("Should throw ConversionException for unsupported types")
    void testConvertUnsupportedTypeThrowsException(Class<?> unsupportedClass) {
        assertThatThrownBy(() -> converter.convert(12.34, (Class) unsupportedClass))
            .isInstanceOf(ConversionException.class);
    }
}
