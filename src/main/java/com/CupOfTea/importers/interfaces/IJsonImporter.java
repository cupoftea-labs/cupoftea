package com.CupOfTea.importers.interfaces;

import java.io.File;
import java.util.Map;

import org.json.JSONArray;

public interface IJsonImporter {

    public void importFromJson(File file);
    public void importFromJson(File file, Map<String, Class<?>> schema);
    public void importFromJson(String json);
    public void importFromJson(String json, Map<String, Class<?>> schema);
    public void importFromJson(JSONArray json);
    public void importFromJson(JSONArray json, Map<String, Class<?>> schema);

}
