package com.cupoftea.exporters;

import java.io.File;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exporters.interfaces.IYamlExporter;

public class YamlExporter implements IYamlExporter {

    public YamlExporter() {
    }

    public YamlExporter(CupOfTea dataFrame) {
    }

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
