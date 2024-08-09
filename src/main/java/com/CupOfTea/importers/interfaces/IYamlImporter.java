package com.CupOfTea.importers.interfaces;

import java.io.File;
import java.io.IOException;

public interface IYamlImporter {

    public void importFromYaml(File file) throws IOException, Exception;
    public void importFromYaml(String yamlText) throws Exception;

}
