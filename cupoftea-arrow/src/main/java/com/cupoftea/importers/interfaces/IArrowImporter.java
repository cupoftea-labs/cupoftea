package com.cupoftea.importers.interfaces;

import java.io.File;

import com.cupoftea.arrow.ArrowIpcFormat;

public interface IArrowImporter {

    void importFromArrow(File file);

    void importFromArrow(File file, ArrowIpcFormat format);

}
