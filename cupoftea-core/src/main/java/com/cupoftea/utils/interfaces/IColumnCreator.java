package com.cupoftea.utils.interfaces;

import com.cupoftea.table.Row;

@FunctionalInterface
public interface IColumnCreator {

    Object createColumn(Row row);

}
