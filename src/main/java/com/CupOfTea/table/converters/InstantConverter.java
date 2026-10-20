package com.CupOfTea.table.converters;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Date;

import com.CupOfTea.exceptions.ConversionException;
import com.CupOfTea.table.interfaces.IDataTypeConverter;

public class InstantConverter implements IDataTypeConverter<Instant> {

    @Override
    public Instant convert(Object value, Class<?> oldColumnType) throws ConversionException {
        if (value == null) {
            return null;
        }

        if (value instanceof Instant) {
            return (Instant) value;
        }

        if (value instanceof Date) {
            return ((Date) value).toInstant();
        }

        if (value instanceof LocalDate) {
            return ((LocalDate) value).atStartOfDay(ZoneOffset.UTC).toInstant();
        }

        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).toInstant(ZoneOffset.UTC);
        }

        if (value instanceof Number number) {
            return Instant.ofEpochMilli(number.longValue());
        }

        if (value instanceof String str) {
            String trimmed = str.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            try {
                return Instant.parse(trimmed);
            } catch (DateTimeParseException ignored) {
            }

            try {
                return LocalDateTime.parse(trimmed).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) {
            }

            try {
                return LocalDate.parse(trimmed).atStartOfDay(ZoneOffset.UTC).toInstant();
            } catch (DateTimeParseException ignored) {
            }

            try {
                long epoch = Long.parseLong(trimmed);
                return Instant.ofEpochMilli(epoch);
            } catch (NumberFormatException ignored) {
            }

            throw new ConversionException("Cannot convert string '" + value + "' to Instant");
        }

        throw new ConversionException(
            "Column type not supported for Instant conversion: "
                + (oldColumnType != null ? oldColumnType.getName() : value.getClass().getName())
        );
    }
}
