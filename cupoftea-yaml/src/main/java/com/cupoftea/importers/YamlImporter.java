package com.cupoftea.importers;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;

import org.yaml.snakeyaml.Yaml;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.importers.interfaces.IYamlImporter;

public class YamlImporter implements IYamlImporter {

    private final CupOfTea dataFrame;

    public YamlImporter(final CupOfTea dataFrame){
        this.dataFrame = dataFrame;
    }

    /**
     * Dumps the Data from a Yaml File into the CupOfTea data frame.
     * 
     * @param file Yaml File.
     */
    @Override
    public void importFromYaml(final File file) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        try {
            String yamlText = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            this.importFromYaml(yamlText);
        } catch (IOException e) {
            throw new ImportException("Failed to read YAML file: " + file.getPath(), e);
        } catch (Exception e) {
            if (e instanceof ImportException) {
                throw (ImportException) e;
            }
            throw new ImportException("Failed to import YAML file: " + file.getPath(), e);
        }
    }

    /**
     * Dumps the Data from a Yaml Text into the CupOfTea data frame.
     * 
     * @param yamlText Yaml Text.
     */
    @Override
    public void importFromYaml(final String yamlText) {
        if (yamlText == null) {
            throw new ImportException("YAML text cannot be null");
        }
        try {
            Yaml yaml = new Yaml();
            Iterable<Object> list = yaml.loadAll(yamlText);

            for(var element: list){
                ArrayList<HashMap<String, Object>> array = (ArrayList<HashMap<String, Object>>) element;
                if (array != null && !array.isEmpty()) {
                    for (var key : (array.get(0)).keySet()){
                        this.dataFrame.getDataFrame().addColumn(key);
                    }
                    for(var row : array){
                        this.dataFrame.getDataFrame().addRow(row);
                    }
                }
            }
        } catch (Exception e) {
            if (e instanceof ImportException) {
                throw (ImportException) e;
            }
            throw new ImportException("Failed to parse YAML text", e);
        }
    }

}
