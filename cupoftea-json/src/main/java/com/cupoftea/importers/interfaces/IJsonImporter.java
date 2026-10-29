package com.cupoftea.importers.interfaces;

import java.io.File;
import java.util.Collections;
import java.util.Map;

import org.json.JSONArray;

import com.cupoftea.table.Schema;

public interface IJsonImporter {

    void importFromJson(File file);
    void importFromJson(File file, Map<String, Class<?>> schema);
    default void importFromJson(File file, Schema schema) {
        importFromJson(file, schema != null ? schema.toMap() : Collections.emptyMap());
    }

    void importFromJson(String json);
    void importFromJson(String json, Map<String, Class<?>> schema);
    default void importFromJson(String json, Schema schema) {
        importFromJson(json, schema != null ? schema.toMap() : Collections.emptyMap());
    }

    void importFromJson(JSONArray json);
    void importFromJson(JSONArray json, Map<String, Class<?>> schema);
    default void importFromJson(JSONArray json, Schema schema) {
        importFromJson(json, schema != null ? schema.toMap() : Collections.emptyMap());
    }

}
