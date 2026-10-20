package com.CupOfTea.table.converters;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.CupOfTea.table.interfaces.IDataTypeConverter;

public class DateConverter implements IDataTypeConverter<Date> {

    @Override
    public Date convert(Object value, Class<?> oldColumnType) throws Exception {
        if (Date.class.isAssignableFrom(value.getClass())) return (Date) value;

        Date convertedValue;

        if (oldColumnType == String.class) {

            // Verificar os tipos de formato de data, usando regex

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

            convertedValue = sdf.parse((String) value);

        }
        else throw new Exception("Column Type not Suported for Conversion");

        return convertedValue;
    }

}
