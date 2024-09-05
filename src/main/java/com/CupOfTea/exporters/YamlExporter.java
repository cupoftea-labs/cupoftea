package com.CupOfTea.exporters;

import java.io.File;

import com.CupOfTea.exporters.interfaces.IYamlExporter;

public class YamlExporter implements IYamlExporter {


    /**
     * Create a YAML file from the data frame.
     * 
     * @param file path to your YAML file. Can't be null.
     * @throws if the file already exists throws an Exception
     */
    @Override
    public void exportToYaml(File file) throws Exception {
        throw new Exception();
    }

}
