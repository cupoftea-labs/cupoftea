package com.CupOfTea.importers.interfaces;

import java.io.File;
import java.net.URL;

public interface IHTMLImporter {

    public void importFromHtml(File file) throws Exception;
    public void importFromHtml(URL url) throws Exception;

}
