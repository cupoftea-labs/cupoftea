package com.CupOfTea.exporters;

import java.io.File;

import com.CupOfTea.exporters.interfaces.IJsonExporter;

public class JsonExporter implements IJsonExporter {


    /**
     * Create a JSON file from the data frame.
     * 
     * @param file path to your JSON file. Can't be null.
     * @throws if the file already exists throws an Exception
     */
    @Override
    public void exportToJson(File file) throws Exception {
        throw new Exception();
    }
    
}
