package com.CupOfTea.table.converters;

import com.CupOfTea.exceptions.ConversionException;
import com.CupOfTea.table.interfaces.IDataTypeConverter;

public class StringConverter implements IDataTypeConverter<String> {

    @Override
    public String convert(Object value, Class<?> oldColumnType) throws ConversionException {
        if (value == null) {
            return null;
        }

        if (String.class.isAssignableFrom(value.getClass())) {
            return (String) value;
        }

        return value.toString();
    }
}
