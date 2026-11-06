package com.cupoftea.xml.lazy;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.importers.configurations.XmlImporterConfiguration;
import com.cupoftea.lazy.LazySource;
import com.cupoftea.lazy.PartitionReader;
import com.cupoftea.lazy.ScanContext;
import com.cupoftea.spi.ScanOptions;
import com.cupoftea.table.Column;
import com.cupoftea.table.DataFrame;
import com.cupoftea.table.Schema;
import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.FloatColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

/**
 * High-performance streaming and lazy XML data source.
 * Supports:
 * - Chunked / batch streaming scans via StAX {@link XMLStreamReader}.
 * - Projection pushdown (select columns) skipping unnecessary XML elements/attributes.
 * - Minimal heap footprint with on-demand chunk materialization.
 */
public class XmlLazySource implements LazySource {

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

    private final File file;
    private final XmlImporterConfiguration config;
    private final ScanOptions scanOptions;

    private Schema schema;
    private List<Column> headers;
    private long estimatedRowCount = -1;
    private boolean initialized = false;

    public XmlLazySource(File file, XmlImporterConfiguration config, ScanOptions scanOptions) {
        if (file == null || !file.exists()) {
            throw new ImportException("XML file does not exist: " + (file != null ? file.getPath() : "null"));
        }
        this.file = file;
        this.config = config != null ? config : new XmlImporterConfiguration();
        this.scanOptions = scanOptions;
        initMetadata();
    }

    private void initMetadata() {
        if (initialized) return;
        initialized = true;

        if (file.length() == 0) {
            this.schema = Schema.empty();
            this.headers = new ArrayList<>();
            this.estimatedRowCount = 0;
            return;
        }

        // Fast metadata probe by sampling initial rows
        Map<String, Class<?>> detectedTypes = new LinkedHashMap<>();
        Schema explicitSchema = scanOptions != null ? scanOptions.getSchema() : null;
        if (explicitSchema == null && config.getSchema() != null) {
            explicitSchema = config.getSchema();
        }

        if (explicitSchema != null) {
            this.schema = explicitSchema;
            this.headers = new ArrayList<>(explicitSchema.getColumns());
            return;
        }

        int sampleRowsLimit = 50;
        int sampledRows = 0;
        String targetRowTag = config.getRowElement();

        try (InputStream in = new BufferedInputStream(new FileInputStream(file), 32 * 1024)) {
            XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(in);
            try {
                int depth = 0;
                boolean insideRow = false;
                int rowDepth = -1;
                String currentElement = null;
                StringBuilder textBuf = new StringBuilder();
                Map<String, String> rowFields = new LinkedHashMap<>();

                while (reader.hasNext()) {
                    int event = reader.next();
                    switch (event) {
                        case XMLStreamConstants.START_ELEMENT:
                            depth++;
                            String localName = reader.getLocalName();
                            if (depth == 1) {
                                break;
                            }
                            if (!insideRow) {
                                if (targetRowTag != null) {
                                    if (localName.equals(targetRowTag)) {
                                        insideRow = true;
                                        rowDepth = depth;
                                        rowFields.clear();
                                        collectSampleAttributes(reader, localName, rowFields);
                                    }
                                } else if (depth == 2) {
                                    targetRowTag = localName;
                                    config.setRowElement(targetRowTag);
                                    insideRow = true;
                                    rowDepth = depth;
                                    rowFields.clear();
                                    collectSampleAttributes(reader, localName, rowFields);
                                }
                            } else {
                                currentElement = localName;
                                textBuf.setLength(0);
                                collectSampleAttributes(reader, localName, rowFields);
                            }
                            break;

                        case XMLStreamConstants.CHARACTERS:
                        case XMLStreamConstants.CDATA:
                            if (insideRow && currentElement != null) {
                                textBuf.append(reader.getText());
                            }
                            break;

                        case XMLStreamConstants.END_ELEMENT:
                            String endName = reader.getLocalName();
                            if (insideRow) {
                                if (depth == rowDepth && endName.equals(targetRowTag)) {
                                    sampledRows++;
                                    for (Map.Entry<String, String> entry : rowFields.entrySet()) {
                                        String colName = entry.getKey();
                                        Class<?> currentType = detectedTypes.get(colName);
                                        Class<?> newType = inferType(entry.getValue());
                                        detectedTypes.put(colName, mergeTypes(currentType, newType));
                                    }
                                    insideRow = false;
                                    rowDepth = -1;
                                    if (sampledRows >= sampleRowsLimit) {
                                        break;
                                    }
                                } else if (currentElement != null && endName.equals(currentElement)) {
                                    rowFields.put(currentElement, textBuf.toString().trim());
                                    currentElement = null;
                                    textBuf.setLength(0);
                                }
                            }
                            depth--;
                            break;
                    }

                    if (sampledRows >= sampleRowsLimit) {
                        break;
                    }
                }
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            throw new ImportException("Failed to inspect XML schema for " + file.getPath() + ": " + e.getMessage(), e);
        }

        List<Column> cols = new ArrayList<>();
        for (Map.Entry<String, Class<?>> entry : detectedTypes.entrySet()) {
            cols.add(new Column(entry.getKey(), entry.getValue()));
        }
        this.headers = cols;
        this.schema = Schema.of(cols);
    }

    private static void collectSampleAttributes(XMLStreamReader reader, String tagName, Map<String, String> fields) {
        int attrCount = reader.getAttributeCount();
        for (int i = 0; i < attrCount; i++) {
            String attrName = reader.getAttributeLocalName(i);
            String attrVal = reader.getAttributeValue(i);
            fields.put(tagName + "." + attrName, attrVal);
        }
    }

    private static Class<?> inferType(String val) {
        if (val == null || val.isEmpty()) return String.class;
        if ("true".equalsIgnoreCase(val) || "false".equalsIgnoreCase(val)) return Boolean.class;
        try {
            long l = Long.parseLong(val);
            if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) return Integer.class;
            return Long.class;
        } catch (NumberFormatException ignored) {}
        try {
            Double.parseDouble(val);
            return Double.class;
        } catch (NumberFormatException ignored) {}
        return String.class;
    }

    private static Class<?> mergeTypes(Class<?> a, Class<?> b) {
        if (a == null) return b != null ? b : String.class;
        if (b == null || a == b) return a;
        if (a == String.class || b == String.class) return String.class;
        if ((a == Integer.class && b == Long.class) || (a == Long.class && b == Integer.class)) return Long.class;
        if ((a == Integer.class || a == Long.class) && b == Double.class) return Double.class;
        if ((b == Integer.class || b == Long.class) && a == Double.class) return Double.class;
        return String.class;
    }

    @Override
    public Schema getSchema() {
        return schema;
    }

    @Override
    public List<Column> getHeaders() {
        return headers;
    }

    @Override
    public long estimatedSize() {
        return estimatedRowCount;
    }

    @Override
    public String describe() {
        return "XmlLazySource(" + file.getName() + ")";
    }

    @Override
    public List<PartitionReader> getPartitions(ScanContext context) {
        int batchSize = (context != null && context.getBatchSize() > 0) ? context.getBatchSize() : config.getBatchSize();
        List<String> projectedColumns = (context != null) ? context.getProjectedColumns() : null;
        return List.of(new XmlPartitionReader(file, config, schema, headers, batchSize, projectedColumns));
    }

    private static class XmlPartitionReader implements PartitionReader {
        private final File file;
        private final XmlImporterConfiguration config;
        private final Schema schema;
        private final List<Column> headers;
        private final int batchSize;
        private final Set<String> projectedSet;
        private final List<Column> activeHeaders;

        private InputStream inputStream;
        private XMLStreamReader reader;
        private boolean exhausted = false;

        private int depth = 0;
        private boolean insideRow = false;
        private int rowDepth = -1;
        private String targetRowTag;
        private String currentElement = null;
        private final StringBuilder textBuf = new StringBuilder();
        private final Map<String, String> currentRowFields = new LinkedHashMap<>();

        XmlPartitionReader(File file, XmlImporterConfiguration config, Schema schema,
                           List<Column> headers, int batchSize, List<String> projectedColumns) {
            this.file = file;
            this.config = config;
            this.schema = schema;
            this.headers = headers;
            this.batchSize = batchSize > 0 ? batchSize : 65_536;
            this.targetRowTag = config.getRowElement();

            if (projectedColumns != null && !projectedColumns.isEmpty()) {
                this.projectedSet = Set.copyOf(projectedColumns);
                List<Column> filtered = new ArrayList<>();
                for (Column col : headers) {
                    if (this.projectedSet.contains(col.columnName())) {
                        filtered.add(col);
                    }
                }
                this.activeHeaders = filtered;
            } else {
                this.projectedSet = null;
                this.activeHeaders = headers != null ? headers : new ArrayList<>();
            }
        }

        private void ensureOpen() throws IOException {
            if (reader == null && !exhausted) {
                try {
                    this.inputStream = new BufferedInputStream(new FileInputStream(file), config.getBufferSize());
                    this.reader = XML_INPUT_FACTORY.createXMLStreamReader(inputStream);
                } catch (XMLStreamException e) {
                    throw new IOException("Failed to initialize XML parser for " + file.getPath(), e);
                }
            }
        }

        @Override
        public CupOfTea nextBatch() throws IOException {
            if (exhausted) {
                return null;
            }
            ensureOpen();

            LinkedHashMap<String, ColumnVector> vectorMap = new LinkedHashMap<>();
            for (Column col : activeHeaders) {
                vectorMap.put(col.columnName(), ColumnVector.create(col.columnName(), col.columnType(), Math.min(batchSize, 1024), true));
            }

            int rowsInBatch = 0;

            try {
                while (reader.hasNext()) {
                    int event = reader.next();

                    switch (event) {
                        case XMLStreamConstants.START_ELEMENT:
                            depth++;
                            String localName = reader.getLocalName();
                            if (depth == 1) {
                                break;
                            }
                            if (!insideRow) {
                                if (targetRowTag != null) {
                                    if (localName.equals(targetRowTag)) {
                                        insideRow = true;
                                        rowDepth = depth;
                                        currentRowFields.clear();
                                        collectAttributes(reader, localName);
                                    }
                                } else if (depth == 2) {
                                    targetRowTag = localName;
                                    insideRow = true;
                                    rowDepth = depth;
                                    currentRowFields.clear();
                                    collectAttributes(reader, localName);
                                }
                            } else {
                                currentElement = localName;
                                textBuf.setLength(0);
                                collectAttributes(reader, localName);
                            }
                            break;

                        case XMLStreamConstants.CHARACTERS:
                        case XMLStreamConstants.CDATA:
                            if (insideRow && currentElement != null) {
                                textBuf.append(reader.getText());
                            }
                            break;

                        case XMLStreamConstants.END_ELEMENT:
                            String endName = reader.getLocalName();
                            if (insideRow) {
                                if (depth == rowDepth && endName.equals(targetRowTag)) {
                                    // Row complete! Append to vectors
                                    appendActiveRow(currentRowFields, vectorMap, activeHeaders);
                                    rowsInBatch++;
                                    insideRow = false;
                                    rowDepth = -1;

                                    if (rowsInBatch >= batchSize) {
                                        return createBatchDataFrame(vectorMap, activeHeaders, rowsInBatch);
                                    }
                                } else if (currentElement != null && endName.equals(currentElement)) {
                                    if (projectedSet == null || projectedSet.contains(currentElement)) {
                                        currentRowFields.put(currentElement, textBuf.toString().trim());
                                    }
                                    currentElement = null;
                                    textBuf.setLength(0);
                                }
                            }
                            depth--;
                            break;

                        case XMLStreamConstants.END_DOCUMENT:
                            exhausted = true;
                            break;
                    }
                }
            } catch (XMLStreamException e) {
                throw new IOException("Error while streaming XML: " + e.getMessage(), e);
            }

            if (rowsInBatch > 0) {
                return createBatchDataFrame(vectorMap, activeHeaders, rowsInBatch);
            }

            exhausted = true;
            return null;
        }

        private void collectAttributes(XMLStreamReader r, String tagName) {
            int count = r.getAttributeCount();
            for (int i = 0; i < count; i++) {
                String col = tagName + "." + r.getAttributeLocalName(i);
                if (projectedSet == null || projectedSet.contains(col)) {
                    currentRowFields.put(col, r.getAttributeValue(i));
                }
            }
        }

        private static void appendActiveRow(Map<String, String> rowFields,
                                            LinkedHashMap<String, ColumnVector> vectorMap,
                                            List<Column> headers) {
            for (Column col : headers) {
                String name = col.columnName();
                ColumnVector vec = vectorMap.get(name);
                String val = rowFields.get(name);
                if (val == null || (val.trim().isEmpty() && !(vec instanceof StringColumnVector))) {
                    vec.appendNull();
                } else {
                    if (vec instanceof IntColumnVector iv) {
                        try {
                            iv.appendInt(Integer.parseInt(val));
                        } catch (Exception e) {
                            iv.appendInt((int) Double.parseDouble(val));
                        }
                    } else if (vec instanceof LongColumnVector lv) {
                        try {
                            lv.appendLong(Long.parseLong(val));
                        } catch (Exception e) {
                            lv.appendLong((long) Double.parseDouble(val));
                        }
                    } else if (vec instanceof DoubleColumnVector dv) {
                        dv.appendDouble(Double.parseDouble(val));
                    } else if (vec instanceof FloatColumnVector fv) {
                        fv.appendFloat(Float.parseFloat(val));
                    } else if (vec instanceof BooleanColumnVector bv) {
                        bv.appendBoolean(Boolean.parseBoolean(val));
                    } else if (vec instanceof StringColumnVector sv) {
                        sv.appendString(val);
                    } else {
                        vec.appendObject(val);
                    }
                }
            }
        }

        private static CupOfTea createBatchDataFrame(LinkedHashMap<String, ColumnVector> vectorMap,
                                                    List<Column> activeHeaders,
                                                    int rowCount) {
            List<ColumnVector> vectors = new ArrayList<>(vectorMap.values());
            ArrayList<Column> finalHeaders = new ArrayList<>(activeHeaders);
            DataFrame df = DataFrame.fromVectors(vectors);
            df.setHeaders(finalHeaders);
            return new CupOfTea(df);
        }

        @Override
        public void close() throws IOException {
            exhausted = true;
            if (reader != null) {
                try {
                    reader.close();
                } catch (XMLStreamException ignored) {
                }
                reader = null;
            }
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
            }
        }
    }
}
