package com.CupOfTea.table.converters;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.CupOfTea.exceptions.ConversionException;
import com.CupOfTea.table.interfaces.IDataTypeConverter;

public class DateConverter implements IDataTypeConverter<Date> {

    @Override
    public Date convert(Object value, Class<?> oldColumnType) throws ConversionException {
        if (value == null) {
            return null;
        }

        if (Date.class.isAssignableFrom(value.getClass())) {
            return (Date) value;
        }

        Date convertedValue;

        if (oldColumnType == String.class) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                convertedValue = sdf.parse((String) value);
            } catch (ParseException e) {
                throw new ConversionException("Cannot convert date string '" + value + "' using pattern 'yyyy-MM-dd'", e);
            }
        } else {
            throw new ConversionException("Column type not supported for Date conversion: "
                    + (oldColumnType != null ? oldColumnType.getName() : "null"));
        }

        return convertedValue;
    }
}
