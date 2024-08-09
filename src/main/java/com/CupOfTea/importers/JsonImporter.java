package com.CupOfTea.importers;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;

import org.json.JSONArray;

import com.CupOfTea.CupOfTea;
import com.CupOfTea.importers.interfaces.IJsonImporter;

public class JsonImporter implements IJsonImporter {

    private CupOfTea dataFrame;

    public JsonImporter(CupOfTea dataFrame){
        this.dataFrame = dataFrame;
    }

    @Override
    public void importFromJson(File file) throws IOException {
        String jsonText = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        JSONArray jsonArray = new JSONArray(jsonText);
        this.importFromJson(jsonArray);
    }

    @Override
    public void importFromJson(String json) {
        this.importFromJson(new JSONArray(json));
    }

    @Override
    public void importFromJson(JSONArray json) {
        for (var key : (json.getJSONObject(0)).keySet()){
            this.dataFrame.addColumn(key);
        }
        for(int i = 0; i < json.length(); i++){
            ArrayList<Object> values = new ArrayList<>();
            for(var column : this.dataFrame.getHeaders()){
                var jsonObj = json.getJSONObject(i);
                values.add(jsonObj.get(column));
            }
            try {
                this.dataFrame.addRow(values);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }



}
