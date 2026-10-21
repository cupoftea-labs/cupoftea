package com.cupoftea.importers;

import java.io.File;
import java.util.Collections;
import java.util.Map;

import org.json.JSONArray;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.importers.interfaces.IJsonImporter;
import com.cupoftea.json.JsonFormatReader;
import com.cupoftea.spi.ReadOptions;

public class JsonImporter implements IJsonImporter {

    private final CupOfTea dataFrame;
    private final JsonFormatReader reader = new JsonFormatReader();

    public JsonImporter(final CupOfTea dataFrame) {
        this.dataFrame = dataFrame;
    }

    @Override
    public void importFromJson(final File file) {
        this.importFromJson(file, Collections.emptyMap());
    }

    @Override
    public void importFromJson(final File file, final Map<String, Class<?>> schema) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        ReadOptions options = ReadOptions.builder()
                .file(file)
                .schema(schema)
                .targetDataFrame(this.dataFrame.getDataFrame())
                .build();
        reader.read(options);
    }

    @Override
    public void importFromJson(final String json) {
        this.importFromJson(json, Collections.emptyMap());
    }

    @Override
    public void importFromJson(final String json, final Map<String, Class<?>> schema) {
        if (json == null) {
            throw new ImportException("JSON string cannot be null");
        }
        ReadOptions options = ReadOptions.builder()
                .text(json)
                .schema(schema)
                .targetDataFrame(this.dataFrame.getDataFrame())
                .build();
        reader.read(options);
    }

    @Override
    public void importFromJson(final JSONArray json) {
        this.importFromJson(json, Collections.emptyMap());
    }

    @Override
    public void importFromJson(final JSONArray json, final Map<String, Class<?>> schema) {
        if (json == null) {
            throw new ImportException("JSONArray cannot be null");
        }
        ReadOptions options = ReadOptions.builder()
                .sourceObject(json)
                .schema(schema)
                .targetDataFrame(this.dataFrame.getDataFrame())
                .build();
        reader.read(options);
    }
}
