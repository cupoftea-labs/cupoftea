package com.cupoftea.importers.interfaces;

import java.io.File;

import org.w3c.dom.Document;

public interface IXmlImporter {

    public void importFromXml(File file);
    public void importFromXml(String xml);
    public void importFromXml(Document doc);

}
