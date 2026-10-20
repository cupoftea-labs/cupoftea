package com.CupOfTea.table.converters;

import com.CupOfTea.table.interfaces.IDataTypeConverter;

public class StringConverter implements IDataTypeConverter<String> {

    @Override
    public String convert(Object value, Class<?> oldColumnType) throws Exception {
        if (String.class.isAssignableFrom(value.getClass())) return (String) value;

        String convertedValue;
        convertedValue = value.toString();

        return convertedValue;
    }

}
