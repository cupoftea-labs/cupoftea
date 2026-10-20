package com.CupOfTea.importers.interfaces;

import java.io.File;

public interface IYamlImporter {

    public void importFromYaml(File file);
    public void importFromYaml(String yamlText);

}
