package com.CupOfTea.utils.interfaces;

import java.util.HashMap;

@FunctionalInterface
public interface IFilter {

    public Boolean filter(HashMap<String, Object> row);

}
