package com.cupoftea.xml;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.List;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exporters.configurations.XmlExporterConfiguration;
import com.cupoftea.spi.FormatWriter;
import com.cupoftea.spi.WriteOptions;
import com.cupoftea.table.Column;
import com.cupoftea.table.interfaces.IDataFrame;
import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.DictionaryColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.FloatColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

/**
 * High-performance streaming XML format writer powered by StAX {@link XMLStreamWriter}.
 * Writes columnar DataFrame data directly to an {@link OutputStream} or {@link Writer}
 * with minimal memory overhead ($O(1)$ heap complexity).
 */
public class XmlFormatWriter implements FormatWriter {

    private static final XMLOutputFactory XML_OUTPUT_FACTORY = XMLOutputFactory.newFactory();

    @Override
    public String format() {
        return "xml";
    }

    @Override
    public void write(IDataFrame dataFrame, WriteOptions options) {
        if (options == null) {
            throw new ExportException("WriteOptions cannot be null");
        }

        XmlExporterConfiguration config = resolveConfiguration(options);

        File file = options.getFile();
        if (file == null && options.getPath() != null) {
            file = new File(options.getPath());
        }

        if (file != null) {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (OutputStream fos = new BufferedOutputStream(new FileOutputStream(file), config.getBufferSize())) {
                writeStream(dataFrame, fos, config);
            } catch (IOException e) {
                throw new ExportException("Failed to write XML file: " + file.getPath(), e);
            } catch (Exception e) {
                if (e instanceof ExportException ee) throw ee;
                throw new ExportException("Failed to export XML: " + e.getMessage(), e);
            }
            return;
        }

        Object outStreamObj = options.getOption("outputStream");
        if (outStreamObj instanceof OutputStream os) {
            try {
                writeStream(dataFrame, os, config);
            } catch (Exception e) {
                if (e instanceof ExportException ee) throw ee;
                throw new ExportException("Failed to export XML to OutputStream: " + e.getMessage(), e);
            }
            return;
        }

        Object writerObj = options.getOption("writer");
        if (writerObj instanceof Writer w) {
            try {
                writeWriter(dataFrame, w, config);
            } catch (Exception e) {
                if (e instanceof ExportException ee) throw ee;
                throw new ExportException("Failed to export XML to Writer: " + e.getMessage(), e);
            }
            return;
        }

        throw new ExportException("Target file, path, outputStream or writer must be provided");
    }

    public void writeStream(IDataFrame dataFrame, OutputStream outputStream, XmlExporterConfiguration config) {
        if (outputStream == null) {
            throw new ExportException("OutputStream cannot be null");
        }
        XmlExporterConfiguration cfg = config != null ? config : new XmlExporterConfiguration();
        try {
            XMLStreamWriter writer = XML_OUTPUT_FACTORY.createXMLStreamWriter(outputStream, cfg.getCharset().name());
            try {
                writeDocument(dataFrame, writer, cfg);
            } finally {
                writer.close();
            }
        } catch (XMLStreamException e) {
            throw new ExportException("StAX XML streaming error: " + e.getMessage(), e);
        }
    }

    public void writeWriter(IDataFrame dataFrame, Writer writer, XmlExporterConfiguration config) {
        if (writer == null) {
            throw new ExportException("Writer cannot be null");
        }
        XmlExporterConfiguration cfg = config != null ? config : new XmlExporterConfiguration();
        try {
            XMLStreamWriter xmlWriter = XML_OUTPUT_FACTORY.createXMLStreamWriter(writer);
            try {
                writeDocument(dataFrame, xmlWriter, cfg);
            } finally {
                xmlWriter.close();
            }
        } catch (XMLStreamException e) {
            throw new ExportException("StAX XML streaming error: " + e.getMessage(), e);
        }
    }

    private void writeDocument(IDataFrame df, XMLStreamWriter writer, XmlExporterConfiguration config) throws XMLStreamException {
        boolean formatted = config.isFormatted();
        String indent = config.getIndent();
        String rootElement = sanitizeXmlTag(config.getRootElement());
        String rowElement = sanitizeXmlTag(config.getRowElement());

        writer.writeStartDocument(config.getCharset().name(), "1.0");
        if (formatted) {
            writer.writeCharacters("\n");
        }

        writer.writeStartElement(rootElement);

        if (df == null) {
            if (formatted) {
                writer.writeCharacters("\n");
            }
            writer.writeEndElement();
            writer.writeEndDocument();
            writer.flush();
            return;
        }

        List<Column> headers = df.getHeaders();
        int rowCount = df.size();

        if (headers == null || headers.isEmpty() || rowCount == 0) {
            if (rowCount > 0 && (headers == null || headers.isEmpty())) {
                for (int r = 0; r < rowCount; r++) {
                    if (formatted) {
                        writer.writeCharacters("\n" + indent);
                    }
                    writer.writeEmptyElement(rowElement);
                }
            }
            if (formatted) {
                writer.writeCharacters("\n");
            }
            writer.writeEndElement();
            writer.writeEndDocument();
            writer.flush();
            return;
        }

        int numCols = headers.size();
        String[] tagNames = new String[numCols];
        ColumnVector[] vectors = new ColumnVector[numCols];

        for (int c = 0; c < numCols; c++) {
            tagNames[c] = sanitizeXmlTag(headers.get(c).columnName());
            vectors[c] = df.getColumnVector(headers.get(c).columnName());
        }

        String nullValue = config.getNullValue();

        for (int r = 0; r < rowCount; r++) {
            if (formatted) {
                writer.writeCharacters("\n" + indent);
            }
            writer.writeStartElement(rowElement);

            for (int c = 0; c < numCols; c++) {
                ColumnVector vec = vectors[c];
                String tag = tagNames[c];

                if (formatted) {
                    writer.writeCharacters("\n" + indent + indent);
                }
                writer.writeStartElement(tag);

                if (vec == null || vec.isNull(r)) {
                    if (!nullValue.isEmpty()) {
                        writer.writeCharacters(nullValue);
                    }
                } else if (vec instanceof IntColumnVector iv) {
                    writer.writeCharacters(Integer.toString(iv.getInt(r)));
                } else if (vec instanceof LongColumnVector lv) {
                    writer.writeCharacters(Long.toString(lv.getLong(r)));
                } else if (vec instanceof DoubleColumnVector dv) {
                    writer.writeCharacters(Double.toString(dv.getDouble(r)));
                } else if (vec instanceof FloatColumnVector fv) {
                    writer.writeCharacters(Float.toString(fv.getFloat(r)));
                } else if (vec instanceof BooleanColumnVector bv) {
                    writer.writeCharacters(Boolean.toString(bv.getBoolean(r)));
                } else if (vec instanceof DictionaryColumnVector dcv) {
                    String str = dcv.getString(r);
                    if (str != null) {
                        writer.writeCharacters(str);
                    } else if (!nullValue.isEmpty()) {
                        writer.writeCharacters(nullValue);
                    }
                } else if (vec instanceof StringColumnVector sv) {
                    String str = sv.getString(r);
                    if (str != null) {
                        writer.writeCharacters(str);
                    } else if (!nullValue.isEmpty()) {
                        writer.writeCharacters(nullValue);
                    }
                } else {
                    Object val = vec.getObject(r);
                    if (val != null) {
                        writer.writeCharacters(val.toString());
                    } else if (!nullValue.isEmpty()) {
                        writer.writeCharacters(nullValue);
                    }
                }

                writer.writeEndElement();
            }

            if (formatted) {
                writer.writeCharacters("\n" + indent);
            }
            writer.writeEndElement();
        }

        if (formatted) {
            writer.writeCharacters("\n");
        }
        writer.writeEndElement();
        writer.writeEndDocument();
        writer.flush();
    }

    private static String sanitizeXmlTag(String name) {
        if (name == null || name.isEmpty()) {
            return "field";
        }
        // XML tag name must start with letter or underscore
        StringBuilder sb = new StringBuilder();
        char first = name.charAt(0);
        if (Character.isLetter(first) || first == '_') {
            sb.append(first);
        } else {
            sb.append('_').append(first);
        }
        for (int i = 1; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '-' || ch == '.') {
                sb.append(ch);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }

    private XmlExporterConfiguration resolveConfiguration(WriteOptions options) {
        Object cfgObj = options.getOption("configuration");
        if (cfgObj instanceof XmlExporterConfiguration cfg) {
            return cfg;
        }

        XmlExporterConfiguration config = new XmlExporterConfiguration();
        Object root = options.getOption("rootElement");
        if (root instanceof String s) {
            config.setRootElement(s);
        }
        Object row = options.getOption("rowElement");
        if (row instanceof String s) {
            config.setRowElement(s);
        }
        Boolean formatted = options.getOption("formatted", null);
        if (formatted != null) {
            config.setFormatted(formatted);
        }
        return config;
    }
}
