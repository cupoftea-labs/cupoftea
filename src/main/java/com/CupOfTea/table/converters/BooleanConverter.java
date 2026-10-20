package com.CupOfTea.table.converters;

import com.CupOfTea.table.interfaces.IDataTypeConverter;

public class BooleanConverter implements IDataTypeConverter<Boolean> {

    @Override
    public Boolean convert(Object value, Class<?> oldColumnType) throws Exception {

        if (Boolean.class.isAssignableFrom(value.getClass())) return (Boolean) value;

        Boolean convertedValue;

        if (oldColumnType == String.class) {

            switch (((String) value).toLowerCase()) {
                case "true" -> convertedValue = true;
                case "false" -> convertedValue = false;
                default -> throw new Exception("Value can not be converted to Boolean");
            }

        } else if (oldColumnType == Integer.class) {

            switch ((Integer) value) {
                case 1 -> convertedValue = true;
                case 0 -> convertedValue = false;
                default -> throw new Exception("Value can not be converted to Boolean");
            }

        }
        else throw new Exception("Column Type not Suported for Conversion");

        return convertedValue;

    }

}
