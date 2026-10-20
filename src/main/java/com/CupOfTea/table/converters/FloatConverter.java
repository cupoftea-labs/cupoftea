package com.CupOfTea.table.converters;

import com.CupOfTea.table.interfaces.IDataTypeConverter;

public class FloatConverter implements IDataTypeConverter<Float> {

    @Override
    public Float convert(Object value, Class<?> oldColumnType) throws Exception {
        if(Class.forName("java.lang.Float").isAssignableFrom(value.getClass())) return (Float) value;

        Float convertedValue;

        if(oldColumnType == Class.forName("java.lang.Integer")) convertedValue = ((Integer) value).floatValue();
        else if(oldColumnType == Class.forName("java.lang.Double")) convertedValue = ((Double) value).floatValue();
        else if(oldColumnType == Class.forName("java.lang.String")) convertedValue = Float.valueOf((String) value);
        else throw new Exception("Column Type not Suported for Conversion");

        return convertedValue;
    }

}
