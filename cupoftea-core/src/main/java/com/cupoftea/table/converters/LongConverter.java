package com.cupoftea.table.converters;

import com.cupoftea.exceptions.ConversionException;
import com.cupoftea.table.interfaces.IDataTypeConverter;

public class LongConverter implements IDataTypeConverter<Long> {

    @Override
    public Long convert(Object value, Class<?> oldColumnType) throws ConversionException {
        if (value == null) {
            return null;
        }

        if (value instanceof Long) {
            return (Long) value;
        }

        Long convertedValue;

        if (oldColumnType == String.class) {
            try {
                convertedValue = Long.valueOf((String) value);
            } catch (NumberFormatException e) {
                throw new ConversionException("Cannot convert string '" + value + "' to Long", e);
            }
        } else if (value instanceof Number) {
            convertedValue = ((Number) value).longValue();
        } else {
            throw new ConversionException("Column type not supported for Long conversion: "
                    + (oldColumnType != null ? oldColumnType.getName() : "null"));
        }

        return convertedValue;
    }
}
