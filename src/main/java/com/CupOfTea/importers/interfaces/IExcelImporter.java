package com.CupOfTea.importers.interfaces;

import java.io.File;

public interface IExcelImporter {

    public void importFromExcel(File file, boolean hasHeaders);

}
