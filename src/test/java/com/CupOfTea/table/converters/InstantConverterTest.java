package com.CupOfTea.table.converters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.CupOfTea.exceptions.ConversionException;

class InstantConverterTest {

    private final InstantConverter converter = new InstantConverter();

    @Test
    @DisplayName("Should convert null to null")
    void testConvertNull() {
        assertThat(converter.convert(null, null)).isNull();
    }

    @Test
    @DisplayName("Should convert Instant to itself")
    void testConvertInstant() {
        Instant now = Instant.now();
        assertThat(converter.convert(now, Instant.class)).isSameAs(now);
    }

    @Test
    @DisplayName("Should convert Date to Instant")
    void testConvertDate() {
        Date date = new Date(1700000000000L);
        assertThat(converter.convert(date, Date.class)).isEqualTo(Instant.ofEpochMilli(1700000000000L));
    }

    @Test
    @DisplayName("Should convert LocalDate to Instant")
    void testConvertLocalDate() {
        LocalDate ld = LocalDate.of(2024, 1, 1);
        Instant expected = ld.atStartOfDay(ZoneOffset.UTC).toInstant();
        assertThat(converter.convert(ld, LocalDate.class)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Should convert LocalDateTime to Instant")
    void testConvertLocalDateTime() {
        LocalDateTime ldt = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
        Instant expected = ldt.toInstant(ZoneOffset.UTC);
        assertThat(converter.convert(ldt, LocalDateTime.class)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Should convert Number (epoch millis) to Instant")
    void testConvertNumber() {
        long epoch = 1700000000000L;
        assertThat(converter.convert(epoch, Long.class)).isEqualTo(Instant.ofEpochMilli(epoch));
    }

    @Test
    @DisplayName("Should convert ISO String to Instant")
    void testConvertString() {
        Instant expected = Instant.parse("2024-01-01T12:00:00Z");
        assertThat(converter.convert("2024-01-01T12:00:00Z", String.class)).isEqualTo(expected);
        assertThat(converter.convert("", String.class)).isNull();
    }

    @Test
    @DisplayName("Should throw ConversionException on invalid String")
    void testConvertInvalidString() {
        assertThatThrownBy(() -> converter.convert("invalid-instant", String.class))
            .isInstanceOf(ConversionException.class)
            .hasMessageContaining("Cannot convert string");
    }
}
