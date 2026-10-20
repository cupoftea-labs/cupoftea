package com.CupOfTea.exporters;

import java.io.File;

import com.CupOfTea.exceptions.ExportException;
import com.CupOfTea.exporters.interfaces.IYamlExporter;

public class YamlExporter implements IYamlExporter {

    /**
     * Create a YAML file from the data frame.
     * 
     * @param file path to your YAML file. Can't be null.
     */
    @Override
    public void exportToYaml(File file) {
        throw new ExportException("YAML export is not supported yet");
    }

}
