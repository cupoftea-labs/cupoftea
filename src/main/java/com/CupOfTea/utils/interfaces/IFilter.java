package com.CupOfTea.utils.interfaces;

import java.util.HashMap;

import org.json.JSONObject;

public interface IFilter {

    public Boolean filter(HashMap<String, Object> row);

}
