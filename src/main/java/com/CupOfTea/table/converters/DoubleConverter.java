package com.CupOfTea.table.converters;

import com.CupOfTea.table.interfaces.IDataTypeConverter;

public class DoubleConverter implements IDataTypeConverter<Double> {

    @Override
    public Double convert(Object value, Class<?> oldColumnType) throws Exception {
        if(Class.forName("java.lang.Double").isAssignableFrom(value.getClass())) return (Double) value;

        Double convertedValue;

        if (oldColumnType == Class.forName("java.lang.Integer")) convertedValue = (Double) value;
        else if(oldColumnType == Class.forName("java.lang.String")) convertedValue = Double.valueOf((String) value);
        else if(oldColumnType == Class.forName("java.lang.Float")) convertedValue = ((Float) value).doubleValue();
        else throw new Exception("Column Type not Suported for Conversion");

        return convertedValue;
    }

}
