package com.CupOfTea.table.converters;

import com.CupOfTea.table.interfaces.IDataTypeConverter;

public class DoubleConverter implements IDataTypeConverter<Double> {

    @Override
    public Double convert(Object value, Class<?> oldColumnType) throws Exception {
        if (Double.class.isAssignableFrom(value.getClass())) return (Double) value;

        Double convertedValue;

        if (oldColumnType == Integer.class) convertedValue = ((Integer) value).doubleValue();
        else if (oldColumnType == String.class) convertedValue = Double.valueOf((String) value);
        else if (oldColumnType == Float.class) convertedValue = ((Float) value).doubleValue();
        else throw new Exception("Column Type not Suported for Conversion");

        return convertedValue;
    }

}
