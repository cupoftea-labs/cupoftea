package com.CupOfTea.table.converters;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

import com.CupOfTea.exceptions.ConversionException;
import com.CupOfTea.table.interfaces.IDataTypeConverter;

/**
 * @deprecated Use {@link LocalDateConverter}, {@link LocalDateTimeConverter}, or {@link InstantConverter}
 * based on {@code java.time} instead of legacy {@link java.util.Date}.
 */
@Deprecated
public class DateConverter implements IDataTypeConverter<Date> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public Date convert(Object value, Class<?> oldColumnType) throws ConversionException {
        if (value == null) {
            return null;
        }

        if (value instanceof Date) {
            return (Date) value;
        }

        if (value instanceof Instant) {
            return Date.from((Instant) value);
        }

        if (value instanceof LocalDate) {
            return Date.from(((LocalDate) value).atStartOfDay(ZoneId.systemDefault()).toInstant());
        }

        Date convertedValue;

        if (oldColumnType == String.class) {
            try {
                LocalDate localDate = LocalDate.parse((String) value, FORMATTER);
                convertedValue = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
            } catch (DateTimeParseException e) {
                throw new ConversionException("Cannot convert date string '" + value + "' using pattern 'yyyy-MM-dd'", e);
            }
        } else if (oldColumnType == LocalDate.class) {
            convertedValue = Date.from(((LocalDate) value).atStartOfDay(ZoneId.systemDefault()).toInstant());
        } else {
            throw new ConversionException("Column type not supported for Date conversion: "
                    + (oldColumnType != null ? oldColumnType.getName() : "null"));
        }

        return convertedValue;
    }
}
