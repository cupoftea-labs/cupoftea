package com.cupoftea;

import java.io.File;
import java.math.BigDecimal;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.exceptions.SchemaMismatchException;
import com.cupoftea.io.DataFrameReader;
import com.cupoftea.io.DataFrameWriter;
import com.cupoftea.table.Column;
import com.cupoftea.table.DataFrame;
import com.cupoftea.table.GroupBy;
import com.cupoftea.table.ILoc;
import com.cupoftea.table.Loc;
import com.cupoftea.table.Row;
import com.cupoftea.table.Schema;
import com.cupoftea.table.interfaces.IDataFrame;
import com.cupoftea.utils.Printer;
import com.cupoftea.utils.configurations.PrinterConfiguration;
import com.cupoftea.utils.interfaces.IColumnCreator;
import com.cupoftea.utils.interfaces.IFilter;
import com.cupoftea.utils.interfaces.Orderer;

public class CupOfTea {

    private IDataFrame dataFrame;
    private MutationMode mutationMode = MutationMode.IN_PLACE;

    public CupOfTea() {
        this(MutationMode.IN_PLACE);
    }

    public CupOfTea(MutationMode mutationMode) {
        this.mutationMode = mutationMode != null ? mutationMode : MutationMode.IN_PLACE;
        this.dataFrame = new DataFrame();
    }

    public CupOfTea(Schema schema) {
        this(schema, MutationMode.IN_PLACE);
    }

    public CupOfTea(Schema schema, MutationMode mutationMode) {
        this.dataFrame = new DataFrame(schema);
        this.mutationMode = mutationMode != null ? mutationMode : MutationMode.IN_PLACE;
    }

    public static CupOfTea withSchema(Schema schema) {
        return new CupOfTea(schema);
    }

    public CupOfTea(IDataFrame dataFrame) {
        this(dataFrame, MutationMode.IN_PLACE);
    }

    public CupOfTea(IDataFrame dataFrame, MutationMode mutationMode) {
        this.dataFrame = dataFrame != null ? dataFrame : new DataFrame();
        this.mutationMode = mutationMode != null ? mutationMode : MutationMode.IN_PLACE;
    }

    public MutationMode getMutationMode() {
        return this.mutationMode;
    }

    public CupOfTea setMutationMode(MutationMode mutationMode) {
        this.mutationMode = mutationMode != null ? mutationMode : MutationMode.IN_PLACE;
        return this;
    }

    public CupOfTea asInPlace() {
        this.mutationMode = MutationMode.IN_PLACE;
        return this;
    }

    public CupOfTea asCopyOnWrite() {
        this.mutationMode = MutationMode.COPY_ON_WRITE;
        return this;
    }

    public CupOfTea copy() {
        return new CupOfTea(this.dataFrame.copy(), this.mutationMode);
    }

    private CupOfTea targetForMutation() {
        if (this.mutationMode == MutationMode.COPY_ON_WRITE) {
            return new CupOfTea(this.dataFrame.copy(), this.mutationMode);
        }
        return this;
    }

    public Schema getSchema() {
        return this.dataFrame.getSchema();
    }

    public CupOfTea setSchema(Schema schema) {
        CupOfTea target = targetForMutation();
        target.dataFrame.setSchema(schema);
        return target;
    }

    /**
     * Entry point for reading tabular data formats.
     * Example: {@code CupOfTea df = CupOfTea.read().csv("data.csv");}
     */
    public static DataFrameReader read() {
        return new DataFrameReader();
    }

    /**
     * Entry point for exporting tabular data formats.
     * Example: {@code df.write().csv("data.csv");}
     */
    public DataFrameWriter write() {
        return new DataFrameWriter(this.dataFrame);
    }

    public IDataFrame getDataFrame() {
        return this.dataFrame;
    }

    public void setDataFrame(IDataFrame dataFrame) {
        this.dataFrame = dataFrame != null ? dataFrame : new DataFrame();
    }

    public CupOfTea clear() {
        this.dataFrame.clear();
        return this;
    }

    public ArrayList<Column> getHeaders() {
        return this.dataFrame.getHeaders();
    }

    public Column getColumnHeader(String columnName) {
        return this.dataFrame.getColumnHeader(columnName);
    }

    public Integer size() {
        return this.dataFrame.size();
    }

    public Row getRow(int index) {
        return this.dataFrame.getRow(index);
    }

    public List<Row> getRows() {
        return this.dataFrame.getRows();
    }

    public CupOfTea moveColumn(String columnName, int finalIndex) {
        CupOfTea target = targetForMutation();
        target.dataFrame.moveColumn(columnName, finalIndex);
        return target;
    }

    public CupOfTea moveColumn(int initialIndex, int finalIndex) {
        CupOfTea target = targetForMutation();
        target.dataFrame.moveColumn(initialIndex, finalIndex);
        return target;
    }

    public CupOfTea renameColumn(String oldName, String newName) {
        CupOfTea target = targetForMutation();
        target.dataFrame.renameColumn(oldName, newName);
        return target;
    }

    public CupOfTea renameColumn(int index, String newName) {
        CupOfTea target = targetForMutation();
        target.dataFrame.renameColumn(index, newName);
        return target;
    }

    public CupOfTea renameColumn(HashMap<String, String> names) {
        CupOfTea target = targetForMutation();
        target.dataFrame.renameColumn(names);
        return target;
    }

    public CupOfTea addColumn(String columnName) {
        CupOfTea target = targetForMutation();
        target.dataFrame.addColumn(columnName);
        return target;
    }

    public CupOfTea addColumn(String columnName, Class<?> columnType) {
        CupOfTea target = targetForMutation();
        target.dataFrame.addColumn(columnName, columnType);
        return target;
    }

    public CupOfTea addColumn(int index, String columnName) {
        CupOfTea target = targetForMutation();
        target.dataFrame.addColumn(index, columnName);
        return target;
    }

    public CupOfTea addColumn(int index, String columnName, Class<?> columnType) {
        CupOfTea target = targetForMutation();
        target.dataFrame.addColumn(index, columnName, columnType);
        return target;
    }

    public CupOfTea addColumn(int index, String columnName, @Nullable Object data) {
        CupOfTea target = targetForMutation();
        target.dataFrame.addColumn(index, columnName, data);
        return target;
    }

    public CupOfTea addColumn(int index, String columnName, @Nullable ArrayList<Object> data) {
        CupOfTea target = targetForMutation();
        target.dataFrame.addColumn(index, columnName, data);
        return target;
    }

    public CupOfTea addColumn(String columnName, IColumnCreator columnCreator) {
        CupOfTea target = targetForMutation();
        target.dataFrame.addColumn(columnName, columnCreator);
        return target;
    }

    public CupOfTea addColumn(String columnName, Class<?> columnType, IColumnCreator columnCreator) {
        CupOfTea target = targetForMutation();
        target.dataFrame.addColumn(columnName, columnType, columnCreator);
        return target;
    }

    public CupOfTea addColumn(String columnName, IColumnCreator columnCreator, Class<?> columnType) {
        CupOfTea target = targetForMutation();
        target.dataFrame.addColumn(columnName, columnCreator, columnType);
        return target;
    }

    public CupOfTea addColumn(int index, String columnName, IColumnCreator columnCreator) {
        CupOfTea target = targetForMutation();
        target.dataFrame.addColumn(index, columnName, columnCreator);
        return target;
    }

    public CupOfTea addColumn(int index, String columnName, Class<?> columnType, IColumnCreator columnCreator) {
        CupOfTea target = targetForMutation();
        target.dataFrame.addColumn(index, columnName, columnType, columnCreator);
        return target;
    }

    public CupOfTea addColumn(int index, String columnName, IColumnCreator columnCreator, Class<?> columnType) {
        CupOfTea target = targetForMutation();
        target.dataFrame.addColumn(index, columnName, columnCreator, columnType);
        return target;
    }

    public CupOfTea alterColumnType(String columnName, Class<?> newColumnType) {
        CupOfTea target = targetForMutation();
        target.dataFrame.alterColumnType(columnName, newColumnType);
        return target;
    }

    public CupOfTea removeColumn(String columnName) {
        CupOfTea target = targetForMutation();
        target.dataFrame.removeColumn(columnName);
        return target;
    }

    public CupOfTea removeColumn(int index) {
        CupOfTea target = targetForMutation();
        target.dataFrame.removeColumn(index);
        return target;
    }

    public CupOfTea addRow(@NotNull Map<String, Object> row) {
        CupOfTea target = targetForMutation();
        target.dataFrame.addRow(row);
        return target;
    }

    public CupOfTea removeRow(int index) {
        CupOfTea target = targetForMutation();
        target.dataFrame.removeRow(index);
        return target;
    }

    public CupOfTea removeRows(IFilter filter) {
        CupOfTea target = targetForMutation();
        target.dataFrame.removeRows(filter);
        return target;
    }

    public CupOfTea filter(IFilter filter) {
        return new CupOfTea(this.dataFrame.filter(filter), this.mutationMode);
    }

    public CupOfTea update(Map<String, Object> updateColumns) {
        CupOfTea target = targetForMutation();
        target.dataFrame.update(updateColumns);
        return target;
    }

    public CupOfTea orderBy(Orderer... orderer) {
        CupOfTea target = targetForMutation();
        target.dataFrame.orderBy(orderer);
        return target;
    }

    // --- Core pandas-like ops ---

    public CupOfTea select(String... columnNames) {
        CupOfTea target = targetForMutation();
        target.dataFrame.select(columnNames);
        return target;
    }

    public CupOfTea select(List<String> columnNames) {
        CupOfTea target = targetForMutation();
        target.dataFrame.select(columnNames);
        return target;
    }

    public CupOfTea drop(String... columnNames) {
        CupOfTea target = targetForMutation();
        target.dataFrame.drop(columnNames);
        return target;
    }

    public CupOfTea drop(List<String> columnNames) {
        CupOfTea target = targetForMutation();
        target.dataFrame.drop(columnNames);
        return target;
    }

    public CupOfTea head() {
        return head(5);
    }

    public CupOfTea head(int n) {
        return new CupOfTea(this.dataFrame.head(n), this.mutationMode);
    }

    public CupOfTea tail() {
        return tail(5);
    }

    public CupOfTea tail(int n) {
        return new CupOfTea(this.dataFrame.tail(n), this.mutationMode);
    }

    public CupOfTea slice(int fromRow, int toRow) {
        return new CupOfTea(this.dataFrame.slice(fromRow, toRow), this.mutationMode);
    }

    public Row iloc(int index) {
        return this.dataFrame.iloc(index);
    }

    public Object iloc(int rowIndex, int colIndex) {
        return this.dataFrame.iloc(rowIndex, colIndex);
    }

    public Object iloc(int rowIndex, String columnName) {
        return this.dataFrame.iloc(rowIndex, columnName);
    }

    public ILoc iloc() {
        return new ILoc(this, this.dataFrame);
    }

    public String getIndex() {
        return this.dataFrame.getIndex();
    }

    public CupOfTea setIndex(String columnName) {
        CupOfTea target = targetForMutation();
        target.dataFrame.setIndex(columnName);
        return target;
    }

    public CupOfTea resetIndex() {
        CupOfTea target = targetForMutation();
        target.dataFrame.resetIndex();
        return target;
    }

    public Row loc(Object key) {
        return this.dataFrame.loc(key);
    }

    public Object loc(Object key, String column) {
        return this.dataFrame.loc(key, column);
    }

    public Loc loc() {
        return new Loc(this, this.dataFrame);
    }

    public CupOfTea fillna(Object value) {
        CupOfTea target = targetForMutation();
        target.dataFrame.fillna(value);
        return target;
    }

    public CupOfTea fillna(String column, Object value) {
        CupOfTea target = targetForMutation();
        target.dataFrame.fillna(column, value);
        return target;
    }

    public CupOfTea fillna(Map<String, Object> values) {
        CupOfTea target = targetForMutation();
        target.dataFrame.fillna(values);
        return target;
    }

    public CupOfTea dropna() {
        CupOfTea target = targetForMutation();
        target.dataFrame.dropna();
        return target;
    }

    public CupOfTea dropna(String how) {
        CupOfTea target = targetForMutation();
        target.dataFrame.dropna(how);
        return target;
    }

    public CupOfTea dropna(String... columns) {
        CupOfTea target = targetForMutation();
        target.dataFrame.dropna(columns);
        return target;
    }

    public CupOfTea dropna(List<String> columns) {
        CupOfTea target = targetForMutation();
        target.dataFrame.dropna(columns);
        return target;
    }

    public CupOfTea dropna(String how, String... columns) {
        CupOfTea target = targetForMutation();
        target.dataFrame.dropna(how, columns);
        return target;
    }

    public CupOfTea dropna(String how, List<String> columns) {
        CupOfTea target = targetForMutation();
        target.dataFrame.dropna(how, columns);
        return target;
    }

    public CupOfTea print() {
        Printer printer = new Printer(this);
        printer.print();
        return this;
    }

    public CupOfTea print(PrinterConfiguration configuration) {
        Printer printer = new Printer(this, configuration);
        printer.print();
        return this;
    }

    public CupOfTea print(int maxRows) {
        PrinterConfiguration config = new PrinterConfiguration();
        config.setMaxRows(maxRows);
        return print(config);
    }

    public String printToString() {
        return printToString(new PrinterConfiguration());
    }

    public String printToString(PrinterConfiguration configuration) {
        Printer printer = new Printer(this, configuration);
        return printer.format(configuration);
    }

    // --- CSV Importers / Exporters ---

    public CupOfTea importFromCsv(File file) {
        return importFromCsv(file, true);
    }

    public CupOfTea importFromCsv(File file, boolean hasHeaders) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).csv(file, hasHeaders);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromCsv(File file, Schema schema) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).schema(schema).csv(file);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromCsv(File file, boolean hasHeaders, Schema schema) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).schema(schema).csv(file, hasHeaders);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromCsv(File file, Object configuration) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).csv(file, configuration);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea exportToCsv(File file) {
        if (file == null) {
            throw new ExportException("File cannot be null");
        }
        write().csv(file);
        return this;
    }

    public CupOfTea exportToCsv(File file, Object configuration) {
        if (file == null) {
            throw new ExportException("File cannot be null");
        }
        write().csv(file, configuration);
        return this;
    }

    // --- Excel Importers ---

    public CupOfTea importFromExcel(File file) {
        return importFromExcel(file, true);
    }

    public CupOfTea importFromExcel(File file, boolean hasHeaders) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).excel(file, hasHeaders);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromExcel(File file, Schema schema) {
        return importFromExcel(file, true, schema);
    }

    public CupOfTea importFromExcel(File file, boolean hasHeaders, Schema schema) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).schema(schema).excel(file, hasHeaders);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromExcel(File file, Object configuration) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).excel(file, configuration);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    // --- Parquet Importers / Exporters ---

    public CupOfTea importFromParquet(File file) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).parquet(file);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea exportToParquet(File file) {
        if (file == null) {
            throw new ExportException("File cannot be null");
        }
        write().parquet(file);
        return this;
    }

    // --- JSON Importers / Exporters ---

    public CupOfTea importFromJson(String json) {
        return importFromJson(json, Collections.emptyMap());
    }

    public CupOfTea importFromJson(String json, Map<String, Class<?>> schema) {
        if (json == null) {
            throw new ImportException("JSON string cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).json(json, schema);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromJson(String json, Schema schema) {
        if (json == null) {
            throw new ImportException("JSON string cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).schema(schema).json(json);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromJson(File file) {
        return importFromJson(file, Collections.emptyMap());
    }

    public CupOfTea importFromJson(File file, Map<String, Class<?>> schema) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).json(file, schema);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromJson(File file, Schema schema) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).schema(schema).json(file);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromJson(Object json) {
        return importFromJson(json, Collections.emptyMap());
    }

    public CupOfTea importFromJson(Object json, Map<String, Class<?>> schema) {
        if (json == null) {
            throw new ImportException("JSON object cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).schema(schema).json(json);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromJson(Object json, Schema schema) {
        if (json == null) {
            throw new ImportException("JSON object cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).schema(schema).json(json);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea exportToJson(File file) {
        if (file == null) {
            throw new ExportException("File cannot be null");
        }
        write().json(file);
        return this;
    }

    public Object exportToJson() {
        return this.dataFrame.getRows();
    }

    // --- HTML Importers ---

    public CupOfTea importFromHtml(File file) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).html(file);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromHtml(String html) {
        if (html == null) {
            throw new ImportException("HTML string cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).html(html);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromHtml(URL url) {
        if (url == null) {
            throw new ImportException("URL cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).html(url);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromHtml(URL url, int timeoutMillis, int maxBodySizeBytes) {
        if (url == null) {
            throw new ImportException("URL cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).html(url, timeoutMillis, maxBodySizeBytes);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromHtml(Object doc) {
        if (doc == null) {
            throw new ImportException("HTML document cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).html(doc);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    // --- YAML Importers / Exporters ---

    public CupOfTea importFromYaml(File file) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).yaml(file);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromYaml(String yamlText) {
        if (yamlText == null) {
            throw new ImportException("YAML text cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).yaml(yamlText);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea exportToYaml(File file) {
        if (file == null) {
            throw new ExportException("File cannot be null");
        }
        write().yaml(file);
        return this;
    }

    // --- XML Importers ---

    public CupOfTea importFromXml(File file) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).xml(file);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromXml(String xml) {
        if (xml == null) {
            throw new ImportException("XML text cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).xml(xml);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    public CupOfTea importFromXml(Object doc) {
        if (doc == null) {
            throw new ImportException("XML document cannot be null");
        }
        CupOfTea loaded = read().target(this.dataFrame).xml(doc);
        this.dataFrame = loaded.dataFrame;
        return this;
    }

    @SuppressWarnings("unchecked")
    public CupOfTea setHeaders(ArrayList<?> arrayList) {
        if (arrayList == null || arrayList.isEmpty()) {
            throw new SchemaMismatchException("Headers list cannot be null or empty");
        }
        ArrayList<Column> columns;
        if (arrayList.get(0) instanceof String) {
            columns = (ArrayList<Column>) ((ArrayList<String>) arrayList).stream()
                    .map(column -> new Column(column, null))
                    .collect(Collectors.toList());
        } else if (arrayList.get(0) instanceof Column) {
            columns = new ArrayList<>((ArrayList<Column>) arrayList);
        } else {
            throw new SchemaMismatchException("Unsupported header array type: elements must be String or Column instances");
        }
        CupOfTea target = targetForMutation();
        target.dataFrame.setHeaders(columns);
        return target;
    }

    /**
     * Retorna um objeto GroupBy para realizar particionamento e agregacoes hash.
     *
     * @param columns colunas pelas quais agrupar
     * @return GroupBy
     */
    public GroupBy groupBy(String... columns) {
        return new GroupBy(this, columns);
    }

    public CupOfTea sum(String column) {
        CupOfTea target = targetForMutation();
        target.dataFrame.sum(column);
        return target;
    }

    public CupOfTea average(String column) {
        CupOfTea target = targetForMutation();
        target.dataFrame.average(column);
        return target;
    }

    public CupOfTea maximum(String column) {
        CupOfTea target = targetForMutation();
        target.dataFrame.maximum(column);
        return target;
    }

    public CupOfTea minimum(String column) {
        CupOfTea target = targetForMutation();
        target.dataFrame.minimum(column);
        return target;
    }

    public CupOfTea count(String column) {
        CupOfTea target = targetForMutation();
        target.dataFrame.count(column);
        return target;
    }

    public CupOfTea aggregate(String column, String separator) {
        CupOfTea target = targetForMutation();
        target.dataFrame.aggregate(column, separator);
        return target;
    }
}
