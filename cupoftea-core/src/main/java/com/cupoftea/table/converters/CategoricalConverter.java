package com.cupoftea.table.converters;

import com.cupoftea.table.interfaces.IDataTypeConverter;

/**
 * Converter for {@link com.cupoftea.table.Categorical} columns.
 * Normalizes input values to {@link String} for dictionary encoding.
 */
public class CategoricalConverter implements IDataTypeConverter<String> {

    @Override
    public String convert(Object value, Class<?> columnType) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }
}
