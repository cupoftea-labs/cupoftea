package com.CupOfTea.table.interfaces;

import com.CupOfTea.exceptions.ConversionException;

public interface IDataTypeConverter <T> {

    public T convert(Object value, Class<?> oldColumnType) throws ConversionException;

}
