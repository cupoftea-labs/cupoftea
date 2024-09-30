package com.CupOfTea.table.interfaces;

public interface IDataTypeConverter <T> {

    public T convert(Object value, Class<?> oldColumnType) throws Exception;

}
