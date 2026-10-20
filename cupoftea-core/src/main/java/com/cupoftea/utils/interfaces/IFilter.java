package com.cupoftea.utils.interfaces;

import com.cupoftea.table.Row;

@FunctionalInterface
public interface IFilter {

    Boolean filter(Row row);

}
