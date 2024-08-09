package com.CupOfTea.importers;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;

import org.yaml.snakeyaml.Yaml;

import com.CupOfTea.CupOfTea;
import com.CupOfTea.importers.interfaces.IYamlImporter;

public class YamlImporter implements IYamlImporter {

    private CupOfTea dataFrame;

    public YamlImporter(CupOfTea dataFrame){
        this.dataFrame = dataFrame;
    }


    @Override
    public void importFromYaml(File file) throws Exception {
        String yamlText = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        this.importFromYaml(yamlText);
    }

    @Override
    public void importFromYaml(String yamlText) throws Exception {
        Yaml yaml = new Yaml();
        Iterable<Object> list = yaml.loadAll(yamlText);

        for(var element: list){
            ArrayList<HashMap<String, Object>> array = (ArrayList<HashMap<String, Object>>) element;
            for (var key : (array.get(0)).keySet()){
                this.dataFrame.addColumn(key);
            }
            for(var map : array){
                ArrayList<Object> values = new ArrayList<>();
                for(var key : map.keySet()){
                    values.add(map.get(key));
                }
                this.dataFrame.addRow(values);
            }
        }
    }

}
