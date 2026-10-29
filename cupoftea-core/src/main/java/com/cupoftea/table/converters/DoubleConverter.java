package com.cupoftea.table.converters;

import com.cupoftea.exceptions.ConversionException;
import com.cupoftea.table.interfaces.IDataTypeConverter;

public class DoubleConverter implements IDataTypeConverter<Double> {

    @Override
    public Double convert(Object value, Class<?> oldColumnType) throws ConversionException {
        if (value == null) {
            return null;
        }

        if (value instanceof Double) {
            return (Double) value;
        }

        Double convertedValue;

        if (value instanceof Number) {
            convertedValue = ((Number) value).doubleValue();
        } else if (oldColumnType == String.class) {
            try {
                convertedValue = Double.valueOf((String) value);
            } catch (NumberFormatException e) {
                throw new ConversionException("Cannot convert string '" + value + "' to Double", e);
            }
        } else {
            throw new ConversionException("Column type not supported for Double conversion: "
                    + (oldColumnType != null ? oldColumnType.getName() : "null"));
        }

        return convertedValue;
    }
}
