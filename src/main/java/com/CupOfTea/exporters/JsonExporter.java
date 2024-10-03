package com.CupOfTea.exporters;

import java.io.File;
import java.io.FileWriter;

import org.json.JSONArray;

import com.CupOfTea.CupOfTea;
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
    public void exportToJson(File file) throws Exception {

        try(FileWriter writer = new FileWriter(file)){
            writer.write(this.exportToJson().toString(4));
            writer.flush();
        }

    }


    /**
     * Create a JSONArray Object from the data frame.
     */
    @Override
    public JSONArray exportToJson() throws Exception {

        JSONArray json = new JSONArray();

        for(var row : this.dataFrame.getRows()){
            // ToDo Ajustar a saida para o formato de dado da coluna
            json.put(row);
        }

        return json;
    }

    
}
