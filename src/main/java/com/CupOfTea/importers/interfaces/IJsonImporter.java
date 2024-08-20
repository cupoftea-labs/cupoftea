package com.CupOfTea.importers.interfaces;

import java.io.File;
import java.io.IOException;

import org.json.JSONArray;

public interface IJsonImporter {

    public void importFromJson(File file) throws IOException, Exception;
    public void importFromJson(String json) throws Exception;
    public void importFromJson(JSONArray json) throws Exception;

}
