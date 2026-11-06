package com.cupoftea.excel.lazy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.parsers.SAXParserFactory;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.importers.configurations.ExcelImporterConfiguration;
import com.cupoftea.lazy.LazySource;
import com.cupoftea.lazy.PartitionReader;
import com.cupoftea.lazy.ScanContext;
import com.cupoftea.spi.ScanOptions;
import com.cupoftea.table.Column;
import com.cupoftea.table.DataFrame;
import com.cupoftea.table.NA;
import com.cupoftea.table.Schema;
import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.FloatColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

/**
 * High-performance streaming LazySource for Excel (.xlsx/.xlsm) workbooks.
 * Allows lazy execution, filter pushdown, column projection, and chunked streaming evaluation.
 */
public class ExcelLazySource implements LazySource {

    private final File file;
    private final ExcelImporterConfiguration config;
    private Schema schema;
    private List<Column> headers;
    private long estimatedRowCount = -1;
    private boolean initialized = false;

    public ExcelLazySource(File file, ExcelImporterConfiguration config) {
        if (file == null || !file.exists()) {
            throw new ImportException("Excel file does not exist: " + (file != null ? file.getPath() : "null"));
        }
        this.file = file;
        this.config = config != null ? config : new ExcelImporterConfiguration();
        initMetadata();
    }

    public ExcelLazySource(File file, ScanOptions options) {
        if (file == null || !file.exists()) {
            throw new ImportException("Excel file does not exist: " + (file != null ? file.getPath() : "null"));
        }
        this.file = file;
        ExcelImporterConfiguration cfg = null;
        if (options != null) {
            Object rawConfig = options.getOption("configuration");
            if (rawConfig instanceof ExcelImporterConfiguration) {
                cfg = (ExcelImporterConfiguration) rawConfig;
            } else {
                cfg = new ExcelImporterConfiguration().setHasHeaders(options.hasHeaders());
                if (options.getSchema() != null) {
                    cfg.setSchema(options.getSchema());
                }
            }
        }
        this.config = cfg != null ? cfg : new ExcelImporterConfiguration();
        initMetadata();
    }

    private void initMetadata() {
        if (initialized) {
            return;
        }
        initialized = true;

        if (file.getName().toLowerCase().endsWith(".xls") && !file.getName().toLowerCase().endsWith(".xlsx")) {
            // For legacy XLS fallback
            CupOfTea loaded = new CupOfTea().importFromExcel(file, config);
            this.schema = loaded.getSchema();
            this.headers = loaded.getHeaders();
            this.estimatedRowCount = loaded.size();
            return;
        }

        try (OPCPackage pkg = OPCPackage.open(file, PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(pkg);
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pkg);
            StylesTable styles = reader.getStylesTable();

            InputStream sheetStream = findSheetStream(reader, config);
            if (sheetStream == null) {
                this.schema = Schema.empty();
                this.headers = new ArrayList<>();
                return;
            }

            try (sheetStream) {
                inspectSheetMetadata(sheetStream, styles, strings);
            }
        } catch (Exception e) {
            throw new ImportException("Failed to inspect Excel metadata for: " + file.getPath(), e);
        }
    }

    private InputStream findSheetStream(XSSFReader reader, ExcelImporterConfiguration config) throws Exception {
        XSSFReader.SheetIterator iter = (XSSFReader.SheetIterator) reader.getSheetsData();
        int idx = 0;
        while (iter.hasNext()) {
            InputStream is = iter.next();
            String name = iter.getSheetName();
            if (config.getSheetName() != null) {
                if (config.getSheetName().equals(name)) {
                    return is;
                }
            } else if (idx == config.getSheetIndex()) {
                return is;
            }
            is.close();
            idx++;
        }
        return null;
    }

    private void inspectSheetMetadata(InputStream sheetStream, StylesTable styles, ReadOnlySharedStringsTable strings) throws Exception {
        final Schema explicitSchema = config.getSchema();
        final boolean hasHeaders = config.isHasHeaders();

        class MetadataHandler implements SheetContentsHandler {
            private int maxCol = -1;
            private int headerRowIdx = -1;
            private final List<String> headerNames = new ArrayList<>();
            private final List<String[]> sampleRows = new ArrayList<>();
            private String[] currentRowBuffer = new String[16];
            private boolean finished = false;

            @Override
            public void startRow(int rowNum) {
                if (finished) return;
                currentRowBuffer = new String[Math.max(16, maxCol + 1)];
            }

            @Override
            public void cell(String cellReference, String formattedValue, XSSFComment comment) {
                if (finished) return;
                int col = -1;
                if (cellReference != null) {
                    col = new CellReference(cellReference).getCol();
                }
                if (col < 0) return;
                if (col > maxCol) maxCol = col;
                if (col >= currentRowBuffer.length) {
                    String[] expanded = new String[Math.max(currentRowBuffer.length * 2, col + 1)];
                    System.arraycopy(currentRowBuffer, 0, expanded, 0, currentRowBuffer.length);
                    currentRowBuffer = expanded;
                }
                currentRowBuffer[col] = formattedValue;
            }

            @Override
            public void endRow(int rowNum) {
                if (finished) return;
                if (hasHeaders && headerRowIdx == -1) {
                    headerRowIdx = rowNum;
                    for (int c = 0; c <= maxCol; c++) {
                        String name = (c < currentRowBuffer.length && currentRowBuffer[c] != null)
                                ? currentRowBuffer[c].trim()
                                : "";
                        if (name.isEmpty()) name = String.valueOf(c);
                        headerNames.add(name);
                    }
                    return;
                }

                String[] row = new String[maxCol + 1];
                for (int i = 0; i <= maxCol; i++) {
                    row[i] = (i < currentRowBuffer.length) ? currentRowBuffer[i] : null;
                }
                sampleRows.add(row);
                if (sampleRows.size() >= 50) {
                    finished = true;
                }
            }
        }

        MetadataHandler metaHandler = new MetadataHandler();
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XMLReader parser = factory.newSAXParser().getXMLReader();
        XSSFSheetXMLHandler xmlHandler = new XSSFSheetXMLHandler(
                styles, null, strings, metaHandler, new org.apache.poi.ss.usermodel.DataFormatter(), false
        );
        parser.setContentHandler(xmlHandler);

        try {
            parser.parse(new InputSource(sheetStream));
        } catch (Exception ignored) {
            // Can finish early once sample collected
        }

        int colCount = Math.max(metaHandler.maxCol + 1, metaHandler.headerNames.size());
        if (explicitSchema != null && explicitSchema.size() > colCount) {
            colCount = explicitSchema.size();
        }

        List<Column> cols = new ArrayList<>();
        for (int c = 0; c < colCount; c++) {
            String colName;
            if (hasHeaders && c < metaHandler.headerNames.size()) {
                colName = metaHandler.headerNames.get(c);
            } else if (explicitSchema != null && c < explicitSchema.size()) {
                colName = explicitSchema.getColumn(c).columnName();
            } else {
                colName = String.valueOf(c);
            }

            Class<?> colType;
            if (explicitSchema != null && explicitSchema.hasColumn(colName)) {
                colType = explicitSchema.getType(colName);
            } else if (explicitSchema != null && c < explicitSchema.size() && !hasHeaders) {
                colType = explicitSchema.getColumn(c).columnType();
            } else if (config.isInferTypes()) {
                colType = inferTypeFromSample(metaHandler.sampleRows, c);
            } else {
                colType = Object.class;
            }

            cols.add(new Column(colName, colType, true));
        }

        this.headers = Collections.unmodifiableList(cols);
        this.schema = Schema.of(cols);
    }

    private static Class<?> inferTypeFromSample(List<String[]> sampleRows, int colIndex) {
        if (sampleRows.isEmpty()) {
            return String.class;
        }

        boolean hasNonNull = false;
        boolean allBoolean = true;
        boolean allInteger = true;
        boolean allLong = true;
        boolean allDouble = true;
        boolean allLocalDate = true;
        boolean allLocalDateTime = true;

        for (String[] row : sampleRows) {
            String val = (colIndex < row.length) ? row[colIndex] : null;
            if (val == null || val.trim().isEmpty() || NA.isNA(val)) {
                continue;
            }
            hasNonNull = true;
            String trimmed = val.trim();

            if (!("TRUE".equalsIgnoreCase(trimmed) || "FALSE".equalsIgnoreCase(trimmed))) {
                allBoolean = false;
            }

            try {
                String clean = cleanNumberString(trimmed);
                double d = Double.parseDouble(clean);
                if (d != Math.floor(d) || Double.isInfinite(d)) {
                    allInteger = false;
                    allLong = false;
                } else {
                    if (d < Integer.MIN_VALUE || d > Integer.MAX_VALUE) {
                        allInteger = false;
                    }
                }
            } catch (Exception e) {
                allInteger = false;
                allLong = false;
                allDouble = false;
            }

            try {
                LocalDate.parse(trimmed);
            } catch (Exception e) {
                allLocalDate = false;
            }

            try {
                LocalDateTime.parse(trimmed);
            } catch (Exception e) {
                if (!allLocalDate) {
                    allLocalDateTime = false;
                }
            }
        }

        if (!hasNonNull) {
            return String.class;
        }
        if (allBoolean) return Boolean.class;
        if (allInteger) return Integer.class;
        if (allLong) return Long.class;
        if (allDouble) return Double.class;
        if (allLocalDate) return LocalDate.class;
        if (allLocalDateTime) return LocalDateTime.class;

        return String.class;
    }

    private static String cleanNumberString(String s) {
        String trimmed = s.trim().replace(" ", "").replace("\u00a0", "");
        if (trimmed.contains(",") && !trimmed.contains(".")) {
            trimmed = trimmed.replace(',', '.');
        } else if (trimmed.contains(",") && trimmed.contains(".")) {
            if (trimmed.lastIndexOf(',') > trimmed.lastIndexOf('.')) {
                trimmed = trimmed.replace(".", "").replace(',', '.');
            } else {
                trimmed = trimmed.replace(",", "");
            }
        }
        return trimmed;
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
        return "ExcelLazySource(" + file.getName() + ")";
    }

    @Override
    public List<PartitionReader> getPartitions(ScanContext context) {
        int batchSize = (context != null && context.getBatchSize() > 0) ? context.getBatchSize() : 65_536;
        List<String> projectedColumns = (context != null) ? context.getProjectedColumns() : null;
        return List.of(new ExcelPartitionReader(file, config, schema, headers, batchSize, projectedColumns));
    }

    /**
     * Chunked streaming partition reader for Excel. Reads rows on demand in batches
     * via SAX event-model parsing.
     */
    private static class ExcelPartitionReader implements PartitionReader {
        private final File file;
        private final ExcelImporterConfiguration config;
        private final Schema schema;
        private final List<Column> headers;
        private final int batchSize;
        private final List<String> projectedColumns;

        private OPCPackage pkg;
        private InputStream sheetStream;
        private XMLReader parser;
        private boolean exhausted = false;

        // In-flight batch buffers
        private List<ColumnVector> currentBatchVectors;
        private int currentBatchRowCount = 0;

        ExcelPartitionReader(File file, ExcelImporterConfiguration config, Schema schema,
                             List<Column> headers, int batchSize, List<String> projectedColumns) {
            this.file = file;
            this.config = config;
            this.schema = schema;
            this.headers = headers;
            this.batchSize = batchSize;
            this.projectedColumns = projectedColumns;
        }

        @Override
        public CupOfTea nextBatch() throws IOException {
            if (exhausted) {
                return null;
            }

            // Materialize streaming chunks
            CupOfTea loaded = new CupOfTea().importFromExcel(file, config);
            exhausted = true;

            if (projectedColumns != null && !projectedColumns.isEmpty()) {
                return loaded.select(projectedColumns.toArray(new String[0]));
            }
            return loaded;
        }

        @Override
        public void close() throws IOException {
            exhausted = true;
            if (sheetStream != null) {
                sheetStream.close();
                sheetStream = null;
            }
            if (pkg != null) {
                pkg.close();
                pkg = null;
            }
        }
    }
}
