package com.CupOfTea.table.converters;

import com.CupOfTea.exceptions.ConversionException;
import com.CupOfTea.table.interfaces.IDataTypeConverter;

public class IntegerConverter implements IDataTypeConverter<Integer> {

    @Override
    public Integer convert(Object value, Class<?> oldColumnType) throws ConversionException {
        if (value == null) {
            return null;
        }

        if (Integer.class.isAssignableFrom(value.getClass())) {
            return (Integer) value;
        }

        Integer convertedValue;

        if (oldColumnType == String.class) {
            try {
                convertedValue = Integer.valueOf((String) value);
            } catch (NumberFormatException e) {
                throw new ConversionException("Cannot convert string '" + value + "' to Integer", e);
            }
        } else if (Number.class.isAssignableFrom(value.getClass())) {
            convertedValue = ((Number) value).intValue();
        } else {
            throw new ConversionException("Column type not supported for Integer conversion: "
                    + (oldColumnType != null ? oldColumnType.getName() : "null"));
        }

        return convertedValue;
    }
}
