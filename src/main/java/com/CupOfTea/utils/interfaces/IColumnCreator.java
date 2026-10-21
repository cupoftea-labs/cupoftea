package com.CupOfTea.utils.interfaces;

import com.CupOfTea.table.Row;

@FunctionalInterface
public interface IColumnCreator {

    Object createColumn(Row row);

}
