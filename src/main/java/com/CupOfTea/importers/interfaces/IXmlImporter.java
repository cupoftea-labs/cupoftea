package com.CupOfTea.importers.interfaces;

import java.io.File;

import org.w3c.dom.Document;

public interface IXmlImporter {

    public void importFromXml(File file) throws Exception;
    public void importFromXml(String xml) throws Exception;
    public void importFromXml(Document doc) throws Exception;

}
