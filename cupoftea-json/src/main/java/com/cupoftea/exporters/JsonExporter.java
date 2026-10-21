package com.cupoftea.exporters;

import java.io.File;

import org.json.JSONArray;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exporters.interfaces.IJsonExporter;
import com.cupoftea.json.JsonFormatWriter;
import com.cupoftea.spi.WriteOptions;

public class JsonExporter implements IJsonExporter {

    private final CupOfTea dataFrame;
    private final JsonFormatWriter writer = new JsonFormatWriter();

    public JsonExporter(final CupOfTea dataFrame) {
        this.dataFrame = dataFrame;
    }

    @Override
    public void exportToJson(File file) {
        if (file == null) {
            throw new ExportException("File cannot be null");
        }
        writer.write(this.dataFrame.getDataFrame(), WriteOptions.builder().file(file).build());
    }

    @Override
    public JSONArray exportToJson() {
        JSONArray array = new JSONArray();
        for (var row : this.dataFrame.getRows()) {
            array.put(row);
        }
        return array;
    }
}
