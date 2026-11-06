package com.cupoftea.exporters;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Objects;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exporters.configurations.XmlExporterConfiguration;
import com.cupoftea.exporters.interfaces.IXmlExporter;
import com.cupoftea.spi.WriteOptions;
import com.cupoftea.xml.XmlFormatWriter;

/**
 * High-performance streaming XML exporter powered by StAX {@link javax.xml.stream.XMLStreamWriter}.
 * Writes tabular DataFrame content continuously without intermediate DOM tree allocations.
 */
public class XmlExporter implements IXmlExporter {

    private final CupOfTea dataFrame;
    private final XmlFormatWriter writer = new XmlFormatWriter();

    public XmlExporter(final CupOfTea dataFrame) {
        this.dataFrame = Objects.requireNonNull(dataFrame, "dataFrame cannot be null");
    }

    @Override
    public void exportToXml(File file) {
        exportToXml(file, new XmlExporterConfiguration());
    }

    @Override
    public void exportToXml(File file, XmlExporterConfiguration configuration) {
        if (file == null) {
            throw new ExportException("File cannot be null");
        }
        XmlExporterConfiguration cfg = configuration != null ? configuration : new XmlExporterConfiguration();
        WriteOptions options = WriteOptions.builder()
                .file(file)
                .option("configuration", cfg)
                .build();
        writer.write(this.dataFrame.getDataFrame(), options);
    }

    @Override
    public void exportToXml(OutputStream outputStream) {
        exportToXml(outputStream, new XmlExporterConfiguration());
    }

    @Override
    public void exportToXml(OutputStream outputStream, XmlExporterConfiguration configuration) {
        if (outputStream == null) {
            throw new ExportException("OutputStream cannot be null");
        }
        XmlExporterConfiguration cfg = configuration != null ? configuration : new XmlExporterConfiguration();
        writer.writeStream(this.dataFrame.getDataFrame(), outputStream, cfg);
    }

    @Override
    public void exportToXml(Writer customWriter) {
        exportToXml(customWriter, new XmlExporterConfiguration());
    }

    @Override
    public void exportToXml(Writer customWriter, XmlExporterConfiguration configuration) {
        if (customWriter == null) {
            throw new ExportException("Writer cannot be null");
        }
        XmlExporterConfiguration cfg = configuration != null ? configuration : new XmlExporterConfiguration();
        writer.writeWriter(this.dataFrame.getDataFrame(), customWriter, cfg);
    }

    @Override
    public String exportToXml() {
        return exportToXml(new XmlExporterConfiguration());
    }

    @Override
    public String exportToXml(XmlExporterConfiguration configuration) {
        StringWriter sw = new StringWriter();
        exportToXml(sw, configuration);
        return sw.toString();
    }
}
