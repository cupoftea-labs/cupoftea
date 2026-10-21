package com.CupOfTea.utils.interfaces;

import com.CupOfTea.table.Row;

@FunctionalInterface
public interface IFilter {

    Boolean filter(Row row);

}
