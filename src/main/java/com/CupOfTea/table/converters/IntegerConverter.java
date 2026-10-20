package com.CupOfTea.table.converters;

import com.CupOfTea.table.interfaces.IDataTypeConverter;

public class IntegerConverter implements IDataTypeConverter<Integer> {

    @Override
    public Integer convert(Object value, Class<?> oldColumnType) throws Exception {

        if (Integer.class.isAssignableFrom(value.getClass())) return (Integer) value;

        Integer convertedValue;
        
        if (oldColumnType == String.class) convertedValue = Integer.valueOf(((String) value));
        else throw new Exception("Column Type not Suported for Conversion");

        return convertedValue;
        
    }

}
