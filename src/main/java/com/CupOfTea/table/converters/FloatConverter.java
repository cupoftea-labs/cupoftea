package com.CupOfTea.table.converters;

import com.CupOfTea.exceptions.ConversionException;
import com.CupOfTea.table.interfaces.IDataTypeConverter;

public class FloatConverter implements IDataTypeConverter<Float> {

    @Override
    public Float convert(Object value, Class<?> oldColumnType) throws ConversionException {
        if (value == null) {
            return null;
        }

        if (Float.class.isAssignableFrom(value.getClass())) {
            return (Float) value;
        }

        Float convertedValue;

        if (oldColumnType == Integer.class || (value instanceof Number && !(value instanceof Float))) {
            convertedValue = ((Number) value).floatValue();
        } else if (oldColumnType == Double.class) {
            convertedValue = ((Double) value).floatValue();
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
