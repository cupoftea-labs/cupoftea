package com.cupoftea.table.converters;

import com.cupoftea.exceptions.ConversionException;
import com.cupoftea.table.interfaces.IDataTypeConverter;

public class StringConverter implements IDataTypeConverter<String> {

    @Override
    public String convert(Object value, Class<?> oldColumnType) throws ConversionException {
        if (value == null) {
            return null;
        }

        if (value instanceof String) {
            return (String) value;
        }

        return value.toString();
    }
}
