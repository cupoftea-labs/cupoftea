package com.CupOfTea.exporters;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONException;

import com.CupOfTea.CupOfTea;
import com.CupOfTea.exceptions.ExportException;
import com.CupOfTea.exporters.interfaces.IJsonExporter;

public class JsonExporter implements IJsonExporter {

    private final CupOfTea dataFrame;

    public JsonExporter (final CupOfTea dataFrame) {
        this.dataFrame = dataFrame;
    }

    /**
     * Create a JSON file from the data frame.
     * 
     * @param file path to your JSON file. Can't be null.
     */
    @Override
    public void exportToJson(File file) {
        if (file == null) {
            throw new ExportException("File cannot be null");
        }

        try(FileWriter writer = new FileWriter(file)){
            writer.write(this.exportToJson().toString(4));
            writer.flush();
        } catch (IOException e) {
            throw new ExportException("Failed to write JSON file: " + file.getPath(), e);
        } catch (Exception e) {
            if (e instanceof ExportException) {
                throw (ExportException) e;
            }
            throw new ExportException("Failed to export JSON file: " + file.getPath(), e);
        }
    }


    /**
     * Create a JSONArray Object from the data frame.
     */
    @Override
    public JSONArray exportToJson() {
        try {
            JSONArray json = new JSONArray();

            for(var row : this.dataFrame.getRows()){
                // ToDo Ajustar a saida para o formato de dado da coluna
                json.put(row);
            }

            return json;
        } catch (JSONException e) {
            throw new ExportException("Failed to create JSONArray from DataFrame", e);
        }
    }

    
}
