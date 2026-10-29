package com.cupoftea.table.converters;

import com.cupoftea.exceptions.ConversionException;
import com.cupoftea.table.interfaces.IDataTypeConverter;

public class FloatConverter implements IDataTypeConverter<Float> {

    @Override
    public Float convert(Object value, Class<?> oldColumnType) throws ConversionException {
        if (value == null) {
            return null;
        }

        if (value instanceof Float) {
            return (Float) value;
        }

        Float convertedValue;

        if (value instanceof Number) {
            convertedValue = ((Number) value).floatValue();
        } else if (oldColumnType == String.class) {
            try {
                convertedValue = Float.valueOf((String) value);
            } catch (NumberFormatException e) {
                throw new ConversionException("Cannot convert string '" + value + "' to Float", e);
            }
        } else {
            throw new ConversionException("Column type not supported for Float conversion: "
                    + (oldColumnType != null ? oldColumnType.getName() : "null"));
        }

        return convertedValue;
    }
}
