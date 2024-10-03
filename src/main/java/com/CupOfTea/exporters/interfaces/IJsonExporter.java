package com.CupOfTea.exporters.interfaces;

import java.io.File;

import org.json.JSONArray;

public interface IJsonExporter {

    public void exportToJson(File file) throws Exception;
    public JSONArray exportToJson() throws Exception;
    
}
