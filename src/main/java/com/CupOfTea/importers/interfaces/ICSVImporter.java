package com.CupOfTea.importers.interfaces;

import java.io.File;

public interface ICSVImporter {
    
    public void importFromCsv(File file, boolean hasHeaders) throws Exception;

}
