package com.cupoftea.json;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.spi.FormatWriter;
import com.cupoftea.spi.WriteOptions;
import com.cupoftea.table.interfaces.IDataFrame;

public class JsonFormatWriter implements FormatWriter {

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public String format() {
        return "json";
    }

    @Override
    public void write(IDataFrame dataFrame, WriteOptions options) {
        if (options == null) {
            throw new ExportException("WriteOptions cannot be null");
        }
        File file = options.getFile();
        if (file == null && options.getPath() != null) {
            file = new File(options.getPath());
        }
        if (file == null) {
            throw new ExportException("Target file or path must be provided");
        }

        try {
            mapper.writeValue(file, dataFrame.getRows());
        } catch (IOException e) {
            throw new ExportException("Failed to write JSON file: " + file.getPath(), e);
        } catch (Exception e) {
            if (e instanceof ExportException) throw (ExportException) e;
            throw new ExportException("Failed to export JSON file: " + file.getPath(), e);
        }
    }
}
