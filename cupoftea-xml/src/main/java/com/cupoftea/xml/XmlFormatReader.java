package com.cupoftea.xml;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.importers.configurations.XmlImporterConfiguration;
import com.cupoftea.spi.FormatReader;
import com.cupoftea.spi.ReadOptions;
import com.cupoftea.table.Column;
import com.cupoftea.table.DataFrame;
import com.cupoftea.table.Schema;
import com.cupoftea.table.interfaces.IDataFrame;
import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.FloatColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

/**
 * High-performance streaming XML format reader powered by StAX {@link XMLStreamReader}.
 * Parses XML streams directly into columnar {@link ColumnVector} representations with
 * minimal memory overhead ($O(1)$ intermediate buffering).
 */
public class XmlFormatReader implements FormatReader {

    private static final XMLInputFactory XML_INPUT_FACTORY = createXmlInputFactory();

    private static XMLInputFactory createXmlInputFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        try {
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
            factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        } catch (Exception ignored) {
        }
        return factory;
    }

    @Override
    public String format() {
        return "xml";
    }

    @Override
    public IDataFrame read(ReadOptions options) {
        if (options == null) {
            throw new ImportException("ReadOptions cannot be null");
        }

        if (options.getSourceObject() instanceof Document doc) {
            CupOfTea target = options.getTargetDataFrame() != null
                    ? new CupOfTea(options.getTargetDataFrame())
                    : new CupOfTea();
            readFromDom(doc, target);
            return target.getDataFrame();
        }

        try {
            return readStreaming(options);
        } catch (ImportException e) {
            throw e;
        } catch (Exception e) {
            throw new ImportException("Failed to read XML: " + e.getMessage(), e);
        }
    }

    private IDataFrame readStreaming(ReadOptions options) throws Exception {
        XmlImporterConfiguration config = resolveConfiguration(options);

        // Open input stream or reader
        Object rawStream = options.getOption("inputStream");
        Object rawReader = options.getOption("reader");

        if (options.getFile() != null) {
            try (InputStream in = new BufferedInputStream(new FileInputStream(options.getFile()), config.getBufferSize())) {
                XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(in);
                try {
                    return parseStream(reader, options, config);
                } finally {
                    reader.close();
                }
            }
        } else if (rawStream instanceof InputStream in) {
            XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(in);
            try {
                return parseStream(reader, options, config);
            } finally {
                reader.close();
            }
        } else if (rawReader instanceof Reader r) {
            XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(r);
            try {
                return parseStream(reader, options, config);
            } finally {
                reader.close();
            }
        } else if (options.getText() != null) {
            XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(new StringReader(options.getText()));
            try {
                return parseStream(reader, options, config);
            } finally {
                reader.close();
            }
        } else if (options.getPath() != null) {
            File file = new File(options.getPath());
            if (file.exists() && file.isFile()) {
                try (InputStream in = new BufferedInputStream(new FileInputStream(file), config.getBufferSize())) {
                    XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(in);
                    try {
                        return parseStream(reader, options, config);
                    } finally {
                        reader.close();
                    }
                }
            } else {
                XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(new StringReader(options.getPath()));
                try {
                    return parseStream(reader, options, config);
                } finally {
                    reader.close();
                }
            }
        } else if (options.getSourceObject() != null) {
            Object src = options.getSourceObject();
            if (src instanceof File f) {
                try (InputStream in = new BufferedInputStream(new FileInputStream(f), config.getBufferSize())) {
                    XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(in);
                    try {
                        return parseStream(reader, options, config);
                    } finally {
                        reader.close();
                    }
                }
            } else if (src instanceof byte[] bytes) {
                try (InputStream in = new ByteArrayInputStream(bytes)) {
                    XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(in);
                    try {
                        return parseStream(reader, options, config);
                    } finally {
                        reader.close();
                    }
                }
            } else {
                XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(new StringReader(src.toString()));
                try {
                    return parseStream(reader, options, config);
                } finally {
                    reader.close();
                }
            }
        } else {
            throw new ImportException("No XML source provided in ReadOptions");
        }
    }

    private IDataFrame parseStream(XMLStreamReader reader, ReadOptions options, XmlImporterConfiguration config) throws XMLStreamException {
        IDataFrame targetDf = options.getTargetDataFrame() != null ? options.getTargetDataFrame() : new DataFrame();

        Map<String, Class<?>> explicitTypes = new HashMap<>();
        if (options.getSchema() != null && !options.getSchema().isEmpty()) {
            explicitTypes.putAll(options.getSchema());
        } else if (config.getSchema() != null) {
            explicitTypes.putAll(config.getSchema().toMap());
        }

        String targetRowTag = config.getRowElement();

        LinkedHashMap<String, ColumnVector> vectorMap = new LinkedHashMap<>();
        int rowCount = 0;
        int initialCapacity = Math.max(32, config.getBatchSize());

        for (Map.Entry<String, Class<?>> entry : explicitTypes.entrySet()) {
            vectorMap.put(entry.getKey(), ColumnVector.create(entry.getKey(), entry.getValue(), initialCapacity, true));
        }

        int depth = 0;
        String rootTag = null;
        String currentElement = null;
        StringBuilder textBuffer = new StringBuilder();

        Map<String, String> currentRowFields = new LinkedHashMap<>();
        boolean insideRow = false;
        int rowDepth = -1;

        while (reader.hasNext()) {
            int event = reader.next();

            switch (event) {
                case XMLStreamConstants.START_ELEMENT:
                    depth++;
                    String localName = reader.getLocalName();

                    if (depth == 1) {
                        rootTag = localName;
                        break;
                    }

                    if (!insideRow) {
                        if (targetRowTag != null) {
                            if (localName.equals(targetRowTag)) {
                                insideRow = true;
                                rowDepth = depth;
                                currentRowFields.clear();
                                collectAttributes(reader, localName, currentRowFields);
                            }
                        } else {
                            if (depth == 2) {
                                targetRowTag = localName;
                                insideRow = true;
                                rowDepth = depth;
                                currentRowFields.clear();
                                collectAttributes(reader, localName, currentRowFields);
                            }
                        }
                    } else {
                        currentElement = localName;
                        textBuffer.setLength(0);
                        collectAttributes(reader, localName, currentRowFields);
                    }
                    break;

                case XMLStreamConstants.CHARACTERS:
                case XMLStreamConstants.CDATA:
                    if (insideRow && currentElement != null) {
                        textBuffer.append(reader.getText());
                    }
                    break;

                case XMLStreamConstants.END_ELEMENT:
                    String endLocalName = reader.getLocalName();

                    if (insideRow) {
                        if (depth == rowDepth && endLocalName.equals(targetRowTag)) {
                            appendRow(currentRowFields, vectorMap, rowCount, explicitTypes);
                            rowCount++;
                            insideRow = false;
                            rowDepth = -1;
                        } else if (currentElement != null && endLocalName.equals(currentElement)) {
                            String text = textBuffer.toString().trim();
                            currentRowFields.put(currentElement, text);
                            currentElement = null;
                            textBuffer.setLength(0);
                        }
                    }

                    depth--;
                    break;

                case XMLStreamConstants.END_DOCUMENT:
                    break;
            }
        }

        populateDataFrame(targetDf, vectorMap, rowCount);
        return targetDf;
    }

    private static void collectAttributes(XMLStreamReader reader, String elementTagName, Map<String, String> fields) {
        int attrCount = reader.getAttributeCount();
        for (int i = 0; i < attrCount; i++) {
            String attrName = reader.getAttributeLocalName(i);
            String attrVal = reader.getAttributeValue(i);
            String colName = elementTagName + "." + attrName;
            fields.put(colName, attrVal);
        }
    }

    private static void appendRow(Map<String, String> rowFields,
                                  LinkedHashMap<String, ColumnVector> vectorMap,
                                  int currentTotalRows,
                                  Map<String, Class<?>> explicitTypes) {
        for (Map.Entry<String, String> entry : rowFields.entrySet()) {
            String colName = entry.getKey();
            if (!vectorMap.containsKey(colName)) {
                Class<?> type = explicitTypes.getOrDefault(colName, String.class);
                if (!explicitTypes.containsKey(colName)) {
                    type = inferType(entry.getValue());
                }
                ColumnVector newVec = ColumnVector.create(colName, type, Math.max(32, currentTotalRows + 16), true);
                for (int r = 0; r < currentTotalRows; r++) {
                    newVec.appendNull();
                }
                vectorMap.put(colName, newVec);
            }
        }

        for (Map.Entry<String, ColumnVector> entry : vectorMap.entrySet()) {
            String colName = entry.getKey();
            ColumnVector vec = entry.getValue();
            String rawVal = rowFields.get(colName);

            if (rawVal == null) {
                vec.appendNull();
            } else {
                if (!explicitTypes.containsKey(colName)) {
                    vec = checkAndPromote(vec, rawVal, currentTotalRows);
                    vectorMap.put(colName, vec);
                }
                appendValue(vec, rawVal);
            }
        }
    }

    private static Class<?> inferType(String val) {
        if (val == null || val.isEmpty()) {
            return String.class;
        }
        if ("true".equalsIgnoreCase(val) || "false".equalsIgnoreCase(val)) {
            return Boolean.class;
        }
        try {
            long l = Long.parseLong(val);
            if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                return Integer.class;
            }
            return Long.class;
        } catch (NumberFormatException ignored) {
        }
        try {
            Double.parseDouble(val);
            return Double.class;
        } catch (NumberFormatException ignored) {
        }
        return String.class;
    }

    private static ColumnVector checkAndPromote(ColumnVector vec, String val, int currentRows) {
        if (val == null || val.isEmpty()) {
            return vec;
        }
        Class<?> currentType = vec.getType();
        if (currentType == Integer.class || currentType == int.class) {
            try {
                int i = Integer.parseInt(val);
                return vec;
            } catch (NumberFormatException e) {
                try {
                    Long.parseLong(val);
                    return promote(vec, Long.class, currentRows);
                } catch (NumberFormatException e2) {
                    try {
                        Double.parseDouble(val);
                        return promote(vec, Double.class, currentRows);
                    } catch (NumberFormatException e3) {
                        return promote(vec, String.class, currentRows);
                    }
                }
            }
        } else if (currentType == Long.class || currentType == long.class) {
            try {
                Long.parseLong(val);
                return vec;
            } catch (NumberFormatException e) {
                try {
                    Double.parseDouble(val);
                    return promote(vec, Double.class, currentRows);
                } catch (NumberFormatException e2) {
                    return promote(vec, String.class, currentRows);
                }
            }
        } else if (currentType == Double.class || currentType == double.class) {
            try {
                Double.parseDouble(val);
                return vec;
            } catch (NumberFormatException e) {
                return promote(vec, String.class, currentRows);
            }
        } else if (currentType == Boolean.class || currentType == boolean.class) {
            if (!"true".equalsIgnoreCase(val) && !"false".equalsIgnoreCase(val)) {
                return promote(vec, String.class, currentRows);
            }
        }
        return vec;
    }

    private static ColumnVector promote(ColumnVector oldVec, Class<?> targetType, int currentRows) {
        ColumnVector newVec = ColumnVector.create(oldVec.getName(), targetType, Math.max(32, currentRows + 16), true);
        for (int r = 0; r < currentRows; r++) {
            if (oldVec.isNull(r)) {
                newVec.appendNull();
            } else {
                Object val = oldVec.getObject(r);
                if (newVec instanceof DoubleColumnVector dcv) {
                    dcv.appendDouble(((Number) val).doubleValue());
                } else if (newVec instanceof LongColumnVector lcv) {
                    lcv.appendLong(((Number) val).longValue());
                } else if (newVec instanceof IntColumnVector icv) {
                    icv.appendInt(((Number) val).intValue());
                } else if (newVec instanceof StringColumnVector scv) {
                    scv.appendString(val.toString());
                } else {
                    newVec.appendObject(val);
                }
            }
        }
        return newVec;
    }

    private static void appendValue(ColumnVector vec, String val) {
        if (val == null || (val.trim().isEmpty() && !(vec instanceof StringColumnVector))) {
            vec.appendNull();
            return;
        }

        if (vec instanceof IntColumnVector icv) {
            try {
                icv.appendInt(Integer.parseInt(val.trim()));
            } catch (Exception e) {
                icv.appendInt((int) Double.parseDouble(val.trim()));
            }
        } else if (vec instanceof LongColumnVector lcv) {
            try {
                lcv.appendLong(Long.parseLong(val.trim()));
            } catch (Exception e) {
                lcv.appendLong((long) Double.parseDouble(val.trim()));
            }
        } else if (vec instanceof DoubleColumnVector dcv) {
            dcv.appendDouble(Double.parseDouble(val.trim()));
        } else if (vec instanceof FloatColumnVector fcv) {
            fcv.appendFloat(Float.parseFloat(val.trim()));
        } else if (vec instanceof BooleanColumnVector bcv) {
            bcv.appendBoolean(Boolean.parseBoolean(val.trim()));
        } else if (vec instanceof StringColumnVector scv) {
            scv.appendString(val);
        } else {
            Class<?> type = vec.getType();
            if (type == BigDecimal.class) {
                vec.appendObject(new BigDecimal(val.trim()));
            } else if (type == LocalDate.class) {
                vec.appendObject(LocalDate.parse(val.trim()));
            } else if (type == LocalDateTime.class) {
                vec.appendObject(LocalDateTime.parse(val.trim()));
            } else {
                vec.appendObject(val);
            }
        }
    }

    private static void populateDataFrame(IDataFrame targetDf, LinkedHashMap<String, ColumnVector> vectorMap, int rowCount) {
        ArrayList<Column> headers = new ArrayList<>();
        List<ColumnVector> vectors = new ArrayList<>();

        for (Map.Entry<String, ColumnVector> entry : vectorMap.entrySet()) {
            String name = entry.getKey();
            ColumnVector vec = entry.getValue();
            headers.add(new Column(name, vec.getType(), true));
            vectors.add(vec);
        }

        if (targetDf instanceof DataFrame df) {
            for (ColumnVector vec : vectors) {
                df.withColumn(vec.getName(), vec);
            }
        } else {
            for (Column col : headers) {
                targetDf.getHeaders().add(col);
            }
            for (int r = 0; r < rowCount; r++) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (Column col : headers) {
                    ColumnVector vec = vectorMap.get(col.columnName());
                    row.put(col.columnName(), vec != null ? vec.getObject(r) : null);
                }
                targetDf.addRow(row);
            }
        }
    }

    private void readFromDom(Document doc, CupOfTea target) {
        doc.getDocumentElement().normalize();
        NodeList outNodes = doc.getChildNodes();
        for (int indexOutNodes = 0; indexOutNodes < outNodes.getLength(); indexOutNodes++) {
            Node innerNode = outNodes.item(indexOutNodes);
            boolean firstRow = true;

            if (innerNode.getNodeType() == Node.ELEMENT_NODE) {
                Element eElement = (Element) innerNode;
                NodeList rows = eElement.getChildNodes();
                for (int j = 0; j < rows.getLength(); ++j) {
                    HashMap<String, Object> rowObj = new HashMap<>();
                    Node row = rows.item(j);
                    if (row.getNodeType() == Node.ELEMENT_NODE) {
                        Element element = (Element) row;
                        NamedNodeMap rowAttributes = element.getAttributes();
                        for (int i = 0; i < rowAttributes.getLength(); ++i) {
                            Node attr = rowAttributes.item(i);
                            String columnName = element.getTagName() + '.' + attr.getNodeName();
                            if (firstRow) target.getHeaders().add(new Column(columnName, null));
                            rowObj.put(columnName, attr.getNodeValue());
                        }

                        NodeList rowColumns = element.getChildNodes();
                        for (int i = 0; i < rowColumns.getLength(); ++i) {
                            if (rowColumns.item(i) instanceof Element rowColumn) {
                                String columnName = rowColumn.getTagName();
                                if (firstRow) target.getHeaders().add(new Column(columnName, null));
                                rowObj.put(columnName, rowColumn.getTextContent());
                            }
                        }
                        target.getDataFrame().addRow(rowObj);
                        firstRow = false;
                    }
                }
            }
        }
    }

    private XmlImporterConfiguration resolveConfiguration(ReadOptions options) {
        Object rawConfig = options.getOption("configuration");
        if (rawConfig instanceof XmlImporterConfiguration cfg) {
            return cfg;
        }
        XmlImporterConfiguration config = new XmlImporterConfiguration();
        Object rowEl = options.getOption("rowElement");
        if (rowEl instanceof String s) {
            config.setRowElement(s);
        }
        if (options.getSchema() != null && !options.getSchema().isEmpty()) {
            config.setSchema(Schema.of(options.getSchema()));
        }
        return config;
    }
}
