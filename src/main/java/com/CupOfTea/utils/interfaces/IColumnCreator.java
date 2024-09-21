package com.CupOfTea.utils.interfaces;

import java.util.HashMap;

@FunctionalInterface
public interface IColumnCreator {

    public Object createColumn(HashMap<String, Object> row);

}
