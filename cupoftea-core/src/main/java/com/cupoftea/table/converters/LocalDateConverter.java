package com.cupoftea.table.converters;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

import com.cupoftea.exceptions.ConversionException;
import com.cupoftea.table.interfaces.IDataTypeConverter;

public class LocalDateConverter implements IDataTypeConverter<LocalDate> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public LocalDate convert(Object value, Class<?> oldColumnType) throws ConversionException {
        if (value == null) {
            return null;
        }

        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }

        if (value instanceof Instant) {
            return ((Instant) value).atZone(ZoneId.systemDefault()).toLocalDate();
        }

        LocalDate convertedValue;

        if (oldColumnType == String.class) {
            try {
                convertedValue = LocalDate.parse((String) value, FORMATTER);
            } catch (DateTimeParseException e) {
                throw new ConversionException("Cannot convert date string '" + value + "' using pattern 'yyyy-MM-dd'", e);
            }
        } else if (oldColumnType == Date.class || value instanceof Date) {
            convertedValue = ((Date) value).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        } else {
            throw new ConversionException("Column type not supported for LocalDate conversion: "
                    + (oldColumnType != null ? oldColumnType.getName() : "null"));
        }

        return convertedValue;
    }
}
