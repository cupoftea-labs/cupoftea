package com.cupoftea.exporters;

import java.io.File;

import org.json.JSONArray;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exporters.configurations.JsonExporterConfiguration;
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
        exportToJson(file, null);
    }

    @Override
    public void exportToJson(File file, JsonExporterConfiguration configuration) {
        if (file == null) {
            throw new ExportException("File cannot be null");
        }
        WriteOptions.Builder builder = WriteOptions.builder().file(file);
        if (configuration != null) {
            builder.option("configuration", configuration);
        }
        writer.write(this.dataFrame.getDataFrame(), builder.build());
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
