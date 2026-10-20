package com.cupoftea.table.converters;

import com.cupoftea.exceptions.ConversionException;
import com.cupoftea.table.interfaces.IDataTypeConverter;

public class IntegerConverter implements IDataTypeConverter<Integer> {

    @Override
    public Integer convert(Object value, Class<?> oldColumnType) throws ConversionException {
        if (value == null) {
            return null;
        }

        if (value instanceof Integer) {
            return (Integer) value;
        }

        Integer convertedValue;

        if (oldColumnType == String.class) {
            try {
                convertedValue = Integer.valueOf((String) value);
            } catch (NumberFormatException e) {
                throw new ConversionException("Cannot convert string '" + value + "' to Integer", e);
            }
        } else if (value instanceof Number) {
            convertedValue = ((Number) value).intValue();
        } else {
            throw new ConversionException("Column type not supported for Integer conversion: "
                    + (oldColumnType != null ? oldColumnType.getName() : "null"));
        }

        return convertedValue;
    }
}
