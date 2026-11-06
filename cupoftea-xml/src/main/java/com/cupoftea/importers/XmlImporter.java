package com.cupoftea.importers;

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.util.Collections;
import java.util.Map;

import org.w3c.dom.Document;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.importers.configurations.XmlImporterConfiguration;
import com.cupoftea.importers.interfaces.IXmlImporter;
import com.cupoftea.spi.ReadOptions;
import com.cupoftea.table.Schema;
import com.cupoftea.xml.XmlFormatReader;

/**
 * Streaming XML Importer powered by StAX XMLStreamReader and FormatReader SPI.
 */
public class XmlImporter implements IXmlImporter {

    private final CupOfTea dataFrame;
    private final XmlFormatReader reader = new XmlFormatReader();

    public XmlImporter(final CupOfTea dataFrame) {
        this.dataFrame = dataFrame;
    }

    @Override
    public void importFromXml(final File file) {
        importFromXml(file, new XmlImporterConfiguration());
    }

    public void importFromXml(final File file, final XmlImporterConfiguration configuration) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        ReadOptions.Builder builder = ReadOptions.builder()
                .file(file)
                .targetDataFrame(this.dataFrame.getDataFrame());
        if (configuration != null) {
            builder.option("configuration", configuration);
            if (configuration.getSchema() != null) {
                builder.schema(configuration.getSchema().toMap());
            }
            if (configuration.getRowElement() != null) {
                builder.option("rowElement", configuration.getRowElement());
            }
        }
        reader.read(builder.build());
    }

    public void importFromXml(final File file, final Schema schema) {
        importFromXml(file, new XmlImporterConfiguration().schema(schema));
    }

    public void importFromXml(final File file, final Map<String, Class<?>> schemaMap) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        ReadOptions options = ReadOptions.builder()
                .file(file)
                .schema(schemaMap)
                .targetDataFrame(this.dataFrame.getDataFrame())
                .build();
        reader.read(options);
    }

    @Override
    public void importFromXml(final String xml) {
        importFromXml(xml, new XmlImporterConfiguration());
    }

    public void importFromXml(final String xml, final XmlImporterConfiguration configuration) {
        if (xml == null) {
            throw new ImportException("XML string cannot be null");
        }
        ReadOptions.Builder builder = ReadOptions.builder()
                .text(xml)
                .targetDataFrame(this.dataFrame.getDataFrame());
        if (configuration != null) {
            builder.option("configuration", configuration);
            if (configuration.getSchema() != null) {
                builder.schema(configuration.getSchema().toMap());
            }
            if (configuration.getRowElement() != null) {
                builder.option("rowElement", configuration.getRowElement());
            }
        }
        reader.read(builder.build());
    }

    public void importFromXml(final String xml, final Schema schema) {
        importFromXml(xml, new XmlImporterConfiguration().schema(schema));
    }

    public void importFromXml(final String xml, final Map<String, Class<?>> schemaMap) {
        if (xml == null) {
            throw new ImportException("XML string cannot be null");
        }
        ReadOptions options = ReadOptions.builder()
                .text(xml)
                .schema(schemaMap)
                .targetDataFrame(this.dataFrame.getDataFrame())
                .build();
        reader.read(options);
    }

    public void importFromXml(final InputStream inputStream) {
        importFromXml(inputStream, new XmlImporterConfiguration());
    }

    public void importFromXml(final InputStream inputStream, final XmlImporterConfiguration configuration) {
        if (inputStream == null) {
            throw new ImportException("InputStream cannot be null");
        }
        ReadOptions.Builder builder = ReadOptions.builder()
                .option("inputStream", inputStream)
                .targetDataFrame(this.dataFrame.getDataFrame());
        if (configuration != null) {
            builder.option("configuration", configuration);
            if (configuration.getSchema() != null) {
                builder.schema(configuration.getSchema().toMap());
            }
            if (configuration.getRowElement() != null) {
                builder.option("rowElement", configuration.getRowElement());
            }
        }
        reader.read(builder.build());
    }

    public void importFromXml(final Reader customReader) {
        importFromXml(customReader, new XmlImporterConfiguration());
    }

    public void importFromXml(final Reader customReader, final XmlImporterConfiguration configuration) {
        if (customReader == null) {
            throw new ImportException("Reader cannot be null");
        }
        ReadOptions.Builder builder = ReadOptions.builder()
                .option("reader", customReader)
                .targetDataFrame(this.dataFrame.getDataFrame());
        if (configuration != null) {
            builder.option("configuration", configuration);
            if (configuration.getSchema() != null) {
                builder.schema(configuration.getSchema().toMap());
            }
            if (configuration.getRowElement() != null) {
                builder.option("rowElement", configuration.getRowElement());
            }
        }
        reader.read(builder.build());
    }

    @Override
    public void importFromXml(final Document doc) {
        if (doc == null) {
            throw new ImportException("XML Document cannot be null");
        }
        ReadOptions options = ReadOptions.builder()
                .sourceObject(doc)
                .targetDataFrame(this.dataFrame.getDataFrame())
                .build();
        reader.read(options);
    }
}
