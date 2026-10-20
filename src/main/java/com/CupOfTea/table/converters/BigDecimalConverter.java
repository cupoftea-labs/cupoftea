package com.CupOfTea.table.converters;

import java.math.BigDecimal;
import java.math.BigInteger;

import com.CupOfTea.exceptions.ConversionException;
import com.CupOfTea.table.interfaces.IDataTypeConverter;

public class BigDecimalConverter implements IDataTypeConverter<BigDecimal> {

    @Override
    public BigDecimal convert(Object value, Class<?> oldColumnType) throws ConversionException {
        if (value == null) {
            return null;
        }

        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }

        if (value instanceof BigInteger bigInteger) {
            return new BigDecimal(bigInteger);
        }

        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }

        if (value instanceof String str) {
            String trimmed = str.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            try {
                return new BigDecimal(trimmed);
            } catch (NumberFormatException e) {
                throw new ConversionException("Cannot convert string '" + value + "' to BigDecimal", e);
            }
        }

        throw new ConversionException(
            "Column type not supported for BigDecimal conversion: "
                + (oldColumnType != null ? oldColumnType.getName() : value.getClass().getName())
        );
    }
}
