package com.cupoftea.exporters.interfaces;

import java.io.File;
import java.io.OutputStream;
import java.io.Writer;

import com.cupoftea.exporters.configurations.XmlExporterConfiguration;

public interface IXmlExporter {

    void exportToXml(File file);

    void exportToXml(File file, XmlExporterConfiguration configuration);

    void exportToXml(OutputStream outputStream);

    void exportToXml(OutputStream outputStream, XmlExporterConfiguration configuration);

    void exportToXml(Writer writer);

    void exportToXml(Writer writer, XmlExporterConfiguration configuration);

    String exportToXml();

    String exportToXml(XmlExporterConfiguration configuration);
}
