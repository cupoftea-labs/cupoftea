package com.cupoftea.exporters.interfaces;

import java.io.File;

import org.json.JSONArray;

public interface IJsonExporter {

    public void exportToJson(File file);
    public JSONArray exportToJson();
    
}
