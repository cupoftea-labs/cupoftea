package com.CupOfTea.importers.interfaces;

import java.io.File;
import java.io.IOException;

public interface IParquetImporter {

    public void importFromParquet(File file) throws IOException, Exception;

}
