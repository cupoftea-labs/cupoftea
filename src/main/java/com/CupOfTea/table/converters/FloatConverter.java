package com.CupOfTea.table.converters;

import com.CupOfTea.table.interfaces.IDataTypeConverter;

public class FloatConverter implements IDataTypeConverter<Float> {

    @Override
    public Float convert(Object value, Class<?> oldColumnType) throws Exception {
        if (Float.class.isAssignableFrom(value.getClass())) return (Float) value;

        Float convertedValue;

        if (oldColumnType == Integer.class) convertedValue = ((Integer) value).floatValue();
        else if (oldColumnType == Double.class) convertedValue = ((Double) value).floatValue();
        else if (oldColumnType == String.class) convertedValue = Float.valueOf((String) value);
        else throw new Exception("Column Type not Suported for Conversion");

        return convertedValue;
    }

}
