package com.CupOfTea.table.converters;

import com.CupOfTea.exceptions.ConversionException;
import com.CupOfTea.table.interfaces.IDataTypeConverter;

public class BooleanConverter implements IDataTypeConverter<Boolean> {

    @Override
    public Boolean convert(Object value, Class<?> oldColumnType) throws ConversionException {
        if (value == null) {
            return null;
        }

        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        Boolean convertedValue;

        if (oldColumnType == String.class) {
            switch (((String) value).toLowerCase()) {
                case "true" -> convertedValue = true;
                case "false" -> convertedValue = false;
                default -> throw new ConversionException("Value cannot be converted to Boolean: " + value);
            }
        } else if (oldColumnType == Integer.class || oldColumnType == int.class) {
            switch ((Integer) value) {
                case 1 -> convertedValue = true;
                case 0 -> convertedValue = false;
                default -> throw new ConversionException("Value cannot be converted to Boolean: " + value);
            }
        } else {
            throw new ConversionException("Column type not supported for Boolean conversion: "
                    + (oldColumnType != null ? oldColumnType.getName() : "null"));
        }

        return convertedValue;
    }
}
