package com.CupOfTea.table.converters;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

import com.CupOfTea.exceptions.ConversionException;
import com.CupOfTea.table.interfaces.IDataTypeConverter;

public class LocalDateTimeConverter implements IDataTypeConverter<LocalDateTime> {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public LocalDateTime convert(Object value, Class<?> oldColumnType) throws ConversionException {
        if (value == null) {
            return null;
        }

        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }

        if (value instanceof Instant) {
            return ((Instant) value).atZone(ZoneId.systemDefault()).toLocalDateTime();
        }

        LocalDateTime convertedValue;

        if (oldColumnType == String.class) {
            String str = (String) value;
            try {
                convertedValue = LocalDateTime.parse(str, ISO_FORMATTER);
            } catch (DateTimeParseException e) {
                try {
                    convertedValue = LocalDate.parse(str, DATE_FORMATTER).atStartOfDay();
                } catch (DateTimeParseException ex) {
                    throw new ConversionException("Cannot convert date string '" + value + "' to LocalDateTime", ex);
                }
            }
        } else if (oldColumnType == LocalDate.class || value instanceof LocalDate) {
            convertedValue = ((LocalDate) value).atStartOfDay();
        } else if (oldColumnType == Date.class || value instanceof Date) {
            convertedValue = ((Date) value).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        } else {
            throw new ConversionException("Column type not supported for LocalDateTime conversion: "
                    + (oldColumnType != null ? oldColumnType.getName() : "null"));
        }

        return convertedValue;
    }
}
