package com.CupOfTea.table.converters;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public class StringConverterTest {

    private StringConverter converter;

    @BeforeEach
    void setUp() {
        converter = new StringConverter();
    }

    static Stream<Arguments> typesToStringProvider() {
        return Stream.of(
            Arguments.of(123, Integer.class, "123"),
            Arguments.of(-456, Integer.class, "-456"),
            Arguments.of(42, Integer.class, "42"),
            Arguments.of(42.5, Double.class, "42.5"),
            Arguments.of(3.14, Double.class, "3.14"),
            Arguments.of(2.5f, Float.class, "2.5"),
            Arguments.of(true, Boolean.class, "true"),
            Arguments.of(false, Boolean.class, "false")
        );
    }

    @ParameterizedTest(name = "convert {0} of type {1} should return \"{2}\"")
    @MethodSource("typesToStringProvider")
    @DisplayName("Should convert various types to String")
    void testConvertTypesToString(Object input, Class<?> type, String expected) {
        String result = converter.convert(input, (Class) type);
        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"hello", "world", "", "  spaces  "})
    @DisplayName("Should convert String to same String")
    void testConvertStringToSameString(String input) {
        String result = converter.convert(input, String.class);
        assertThat(result).isEqualTo(input);
    }
}
