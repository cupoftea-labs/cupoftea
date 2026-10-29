package com.cupoftea.table.interfaces;

import com.cupoftea.exceptions.ConversionException;

public interface IDataTypeConverter <T> {

    public T convert(Object value, Class<?> oldColumnType) throws ConversionException;

}
