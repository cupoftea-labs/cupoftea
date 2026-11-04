package com.cupoftea.exporters.interfaces;

import java.io.File;

import org.json.JSONArray;

import com.cupoftea.exporters.configurations.JsonExporterConfiguration;

public interface IJsonExporter {

    public void exportToJson(File file);
    public void exportToJson(File file, JsonExporterConfiguration configuration);
    public JSONArray exportToJson();
    
}
