package com.CupOfTea.table;

import java.util.HashMap;
import java.util.List;

public interface Aggregate {
    String getColumn();
    String getOutputColumnName();
    Aggregate as(String alias);
    Object compute(List<HashMap<String, Object>> rows, List<Column> sourceHeaders);
    Class<?> getResultType(List<Column> sourceHeaders);
}
