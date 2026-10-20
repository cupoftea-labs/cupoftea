package com.CupOfTea.importers.interfaces;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.json.JSONArray;

public interface IJsonImporter {

    public void importFromJson(File file) throws IOException, Exception;
    public void importFromJson(File file, Map<String, Class<?>> schema) throws IOException, Exception;
    public void importFromJson(String json) throws Exception;
    public void importFromJson(String json, Map<String, Class<?>> schema) throws Exception;
    public void importFromJson(JSONArray json) throws Exception;
    public void importFromJson(JSONArray json, Map<String, Class<?>> schema) throws Exception;

}
