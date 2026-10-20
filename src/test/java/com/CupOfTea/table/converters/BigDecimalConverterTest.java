package com.CupOfTea.table.converters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.CupOfTea.exceptions.ConversionException;

class BigDecimalConverterTest {

    private final BigDecimalConverter converter = new BigDecimalConverter();

    @Test
    @DisplayName("Should convert null to null")
    void testConvertNull() {
        assertThat(converter.convert(null, null)).isNull();
    }

    @Test
    @DisplayName("Should convert BigDecimal to itself")
    void testConvertBigDecimal() {
        BigDecimal val = new BigDecimal("123.456");
        assertThat(converter.convert(val, BigDecimal.class)).isSameAs(val);
    }

    @Test
    @DisplayName("Should convert BigInteger to BigDecimal")
    void testConvertBigInteger() {
        BigInteger bi = new BigInteger("9876543210");
        assertThat(converter.convert(bi, BigInteger.class)).isEqualTo(new BigDecimal("9876543210"));
    }

    @Test
    @DisplayName("Should convert Numbers to BigDecimal")
    void testConvertNumbers() {
        assertThat(converter.convert(100, Integer.class)).isEqualTo(new BigDecimal("100"));
        assertThat(converter.convert(123456789L, Long.class)).isEqualTo(new BigDecimal("123456789"));
        assertThat(converter.convert(12.5, Double.class)).isEqualTo(new BigDecimal("12.5"));
    }

    @Test
    @DisplayName("Should convert String to BigDecimal")
    void testConvertString() {
        assertThat(converter.convert("1234.56", String.class)).isEqualTo(new BigDecimal("1234.56"));
        assertThat(converter.convert("   -99.99  ", String.class)).isEqualTo(new BigDecimal("-99.99"));
        assertThat(converter.convert("", String.class)).isNull();
    }

    @Test
    @DisplayName("Should throw ConversionException on invalid String")
    void testConvertInvalidString() {
        assertThatThrownBy(() -> converter.convert("not-a-number", String.class))
            .isInstanceOf(ConversionException.class)
            .hasMessageContaining("Cannot convert string");
    }

    @Test
    @DisplayName("Should throw ConversionException on unsupported type")
    void testConvertUnsupportedType() {
        assertThatThrownBy(() -> converter.convert(new Object(), Object.class))
            .isInstanceOf(ConversionException.class)
            .hasMessageContaining("Column type not supported");
    }
}
