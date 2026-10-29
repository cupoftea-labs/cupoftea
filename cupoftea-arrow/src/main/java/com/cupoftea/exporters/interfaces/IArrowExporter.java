package com.cupoftea.exporters.interfaces;

import java.io.File;

import com.cupoftea.arrow.ArrowIpcFormat;

public interface IArrowExporter {

    void exportToArrow(File file);

    void exportToArrow(File file, ArrowIpcFormat format);

}
