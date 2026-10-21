package com.CupOfTea;

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
import org.json.JSONArray;
import org.w3c.dom.Document;

import com.CupOfTea.exceptions.SchemaMismatchException;
import com.CupOfTea.exporters.CSVExporter;
import com.CupOfTea.exporters.JsonExporter;
import com.CupOfTea.exporters.ParquetExporter;
import com.CupOfTea.exporters.YamlExporter;
import com.CupOfTea.exporters.configurations.CSVExporterConfiguration;
import com.CupOfTea.exporters.interfaces.IJsonExporter;
import com.CupOfTea.exporters.interfaces.IParquetExporter;
import com.CupOfTea.exporters.interfaces.IYamlExporter;
import com.CupOfTea.importers.CSVImporter;
import com.CupOfTea.importers.ExcelImporter;
import com.CupOfTea.importers.HTMLImporter;
import com.CupOfTea.importers.JsonImporter;
import com.CupOfTea.importers.ParquetImporter;
import com.CupOfTea.importers.XmlImporter;
import com.CupOfTea.importers.YamlImporter;
import com.CupOfTea.importers.configurations.CSVImporterConfiguration;
import com.CupOfTea.importers.configurations.ExcelImporterConfiguration;
import com.CupOfTea.importers.interfaces.ICSVImporter;
import com.CupOfTea.importers.interfaces.IHTMLImporter;
import com.CupOfTea.importers.interfaces.IJsonImporter;
import com.CupOfTea.importers.interfaces.IParquetImporter;
import com.CupOfTea.importers.interfaces.IXmlImporter;
import com.CupOfTea.importers.interfaces.IYamlImporter;
import com.CupOfTea.table.Aggregate;
import com.CupOfTea.table.Aggregations;
import com.CupOfTea.table.Column;
import com.CupOfTea.table.DataFrame;
import com.CupOfTea.table.Group;
import com.CupOfTea.table.GroupBy;
import com.CupOfTea.table.NA;
import com.CupOfTea.table.Row;
import com.CupOfTea.table.Schema;
import com.CupOfTea.table.interfaces.IDataFrame;
import com.CupOfTea.utils.Printer;
import com.CupOfTea.utils.configurations.PrinterConfiguration;
import com.CupOfTea.utils.interfaces.IColumnCreator;
import com.CupOfTea.utils.interfaces.IFilter;
import com.CupOfTea.utils.interfaces.Orderer;


public class CupOfTea {

    private IDataFrame dataFrame;
    private MutationMode mutationMode = MutationMode.IN_PLACE;

    private CSVExporter csvExporter;
    private IJsonExporter jsonExporter;
    private IParquetExporter parquetExporter;
    private IYamlExporter yamlExporter;
    
    private ICSVImporter csvImporter;
    private IJsonImporter jsonImporter;
    private IParquetImporter parquetImporter;
    private IYamlImporter yamlImporter;
    private IHTMLImporter htmlImporter;
    private IXmlImporter xmlImporter;


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
        this.dataFrame = dataFrame;
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

    public IDataFrame getDataFrame() {
        return this.dataFrame;
    }

    public CupOfTea clear() {
        this.dataFrame.clear();
        return this;
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

    public ArrayList<Column> getHeaders(){
        return this.dataFrame.getHeaders();
    }

    public Column getColumnHeader(String columnName){
        return this.dataFrame.getColumnHeader(columnName);
    }

    public Integer size(){
        return this.dataFrame.size();
    }

    public Row getRow(int index){
        return this.dataFrame.getRow(index);
    }

    public List<Row> getRows(){
        return this.dataFrame.getRows();
    }

    public CupOfTea moveColumn (String columnName, int finalIndex){
        CupOfTea target = targetForMutation();
        target.dataFrame.moveColumn(columnName, finalIndex);
        return target;
    }
    
    public CupOfTea moveColumn (int inicialIndex, int finalIndex){
        CupOfTea target = targetForMutation();
        target.dataFrame.moveColumn(inicialIndex, finalIndex);
        return target;
    }

    public CupOfTea renameColumn (String oldName, String newName) {
        CupOfTea target = targetForMutation();
        target.dataFrame.renameColumn(oldName, newName);
        return target;
    }

    public CupOfTea renameColumn (int index, String newName) {
        CupOfTea target = targetForMutation();
        target.dataFrame.renameColumn(index, newName);
        return target;
    }

    public CupOfTea renameColumn (HashMap<String,String> name){
        CupOfTea target = targetForMutation();
        target.dataFrame.renameColumn(name);
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

    public CupOfTea alterColumnType(String columnName, Class newColumnType) {
        CupOfTea target = targetForMutation();
        target.dataFrame.alterColumnType(columnName, newColumnType);
        return target;
    }

    /**
     * Create a new column whose values are defined by the Column Creator Function
     * The column will be added to the end of the Data Frame
     * 
     * @param columnName Name of the new Column
     * @param columnCreator Function that defines the values of the new column
     */
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

    public CupOfTea removeRow(int index){
        CupOfTea target = targetForMutation();
        target.dataFrame.removeRow(index);
        return target;
    }


    /**
     * Removes the filtered rows from the DataFrame
     * 
     * @param filter the rows mathing this condition will be removed out of the DataFrame
     * @return the same DataFrame but without the filtered rows
     */
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
        Printer printer = new Printer(this, new PrinterConfiguration().setMaxRows(maxRows));
        printer.print();
        return this;
    }

    public String printToString() {
        Printer printer = new Printer(this);
        return printer.format();
    }

    public String printToString(PrinterConfiguration configuration) {
        Printer printer = new Printer(this, configuration);
        return printer.format();
    }

    public CupOfTea exportToJson(File file) {

        jsonExporter = new JsonExporter(this);
        jsonExporter.exportToJson(file);
        return this;

    }

    public JSONArray exportToJson() {

        jsonExporter = new JsonExporter(this);
        JSONArray json = jsonExporter.exportToJson();
        return json;

    }
    
    public CupOfTea exportToCsv(File file) {

        csvExporter = new CSVExporter(this);
        csvExporter.exportToCsv(file);
        return this;

    }

    public CupOfTea exportToCsv(File file, CSVExporterConfiguration configuration) {

        csvExporter = new CSVExporter(this);
        csvExporter.exportToCsv(file, configuration);
        return this;

    }

    
    public CupOfTea importFromCsv(File file) {

        csvImporter = new CSVImporter(this);
        csvImporter.importFromCsv(file);
        return this;

    }

    public CupOfTea importFromCsv(File file, boolean hasHeaders) {

        csvImporter = new CSVImporter(this);
        csvImporter.importFromCsv(file, hasHeaders);
        return this;

    }

    public CupOfTea importFromCsv(File file, Schema schema) {

        csvImporter = new CSVImporter(this);
        csvImporter.importFromCsv(file, schema);
        return this;

    }

    public CupOfTea importFromCsv(File file, boolean hasHeaders, Schema schema) {

        csvImporter = new CSVImporter(this);
        csvImporter.importFromCsv(file, hasHeaders, schema);
        return this;

    }

    public CupOfTea importFromCsv(File file, CSVImporterConfiguration configuration) {

        csvImporter = new CSVImporter(this, configuration);
        csvImporter.importFromCsv(file, configuration);
        return this;

    }

    public CupOfTea importFromExcel(File file) {

        ExcelImporter excelImporter = new ExcelImporter(this);
        excelImporter.importFromExcel(file, true);
        return this;

    }

    public CupOfTea importFromExcel(File file, boolean hasHeaders) {

        ExcelImporter excelImporter = new ExcelImporter(this);
        excelImporter.importFromExcel(file, hasHeaders);
        return this;

    }

    public CupOfTea importFromExcel(File file, Schema schema) {

        ExcelImporter excelImporter = new ExcelImporter(this);
        excelImporter.importFromExcel(file, schema);
        return this;

    }

    public CupOfTea importFromExcel(File file, boolean hasHeaders, Schema schema) {

        ExcelImporter excelImporter = new ExcelImporter(this);
        excelImporter.importFromExcel(file, hasHeaders, schema);
        return this;

    }

    public CupOfTea importFromExcel(File file, ExcelImporterConfiguration configuration) {

        ExcelImporter excelImporter = new ExcelImporter(this, configuration);
        excelImporter.importFromExcel(file, configuration);
        return this;

    }

    public CupOfTea importFromParquet(File file) {

        parquetImporter = new ParquetImporter(this);
        parquetImporter.importFromParquet(file);
        return this;

    }

    public CupOfTea exportToParquet(File file) { 

        parquetExporter = new ParquetExporter(this);
        parquetExporter.exportToParquet(file);
        return this;

    }

    public CupOfTea importFromJson(String json) {
        
        jsonImporter = new JsonImporter(this);
        jsonImporter.importFromJson(json);
        return this;

    }

    public CupOfTea importFromJson(String json, Map<String, Class<?>> schema) {

        jsonImporter = new JsonImporter(this);
        jsonImporter.importFromJson(json, schema);
        return this;

    }

    public CupOfTea importFromJson(String json, Schema schema) {

        jsonImporter = new JsonImporter(this);
        jsonImporter.importFromJson(json, schema);
        return this;

    }

    /**
     * Dumps the Data from a JSON file into the CupOfTea data frame.
     * The json must be a json array for this method to work.
     * 
     * @param file JSON file.
     */
    public CupOfTea importFromJson(File file) {

        jsonImporter = new JsonImporter(this);
        jsonImporter.importFromJson(file);
        return this;

    }

    public CupOfTea importFromJson(File file, Map<String, Class<?>> schema) {

        jsonImporter = new JsonImporter(this);
        jsonImporter.importFromJson(file, schema);
        return this;

    }

    public CupOfTea importFromJson(File file, Schema schema) {

        jsonImporter = new JsonImporter(this);
        jsonImporter.importFromJson(file, schema);
        return this;

    }

    public CupOfTea importFromXml(File file) {

        xmlImporter = new XmlImporter(this);
        xmlImporter.importFromXml(file);
        return this;

    }

    public CupOfTea importFromXml(String xml) {

        xmlImporter = new XmlImporter(this);
        xmlImporter.importFromXml(xml);
        return this;

    }

    public CupOfTea importFromXml(Document doc) {

        xmlImporter = new XmlImporter(this);
        xmlImporter.importFromXml(doc);
        return this;

    }

    public CupOfTea importFromHtml(File file) {

        htmlImporter = new HTMLImporter(this);
        htmlImporter.importFromHtml(file);
        return this;

    }

    /**
     * Imports an HTML table from a URL using default connect/read timeout and max body size limits.
     * <p>
     * <b>Security warning:</b> Fetching arbitrary or user-supplied URLs on a server is unsafe
     * (SSRF / slowloris risk). Prefer {@link #importFromHtml(org.jsoup.nodes.Document)} or
     * {@link #importFromHtml(String)} in untrusted environments.
     */
    public CupOfTea importFromHtml(URL url) {

        htmlImporter = new HTMLImporter(this);
        htmlImporter.importFromHtml(url);
        return this;

    }

    /**
     * Imports an HTML table from a URL using explicit connect/read timeout and max body size limits.
     * <p>
     * <b>Security warning:</b> Fetching arbitrary or user-supplied URLs on a server is unsafe
     * (SSRF / slowloris risk). Prefer {@link #importFromHtml(org.jsoup.nodes.Document)} or
     * {@link #importFromHtml(String)} in untrusted environments.
     */
    public CupOfTea importFromHtml(URL url, int timeoutMillis, int maxBodySizeBytes) {

        htmlImporter = new HTMLImporter(this, timeoutMillis, maxBodySizeBytes);
        htmlImporter.importFromHtml(url, timeoutMillis, maxBodySizeBytes);
        return this;

    }

    public CupOfTea importFromHtml(String html) {

        htmlImporter = new HTMLImporter(this);
        htmlImporter.importFromHtml(html);
        return this;

    }

    public CupOfTea importFromHtml(org.jsoup.nodes.Document doc) {

        htmlImporter = new HTMLImporter(this);
        htmlImporter.importFromHtml(doc);
        return this;

    }

    /**
     * Dumps the Data from a JSONArray Object into the CupOfTea data frame.
     * 
     * @param json JSONArray Object.
     */
    public CupOfTea importFromJson(JSONArray json) {

        jsonImporter = new JsonImporter(this);
        jsonImporter.importFromJson(json);
        return this;

    }

    public CupOfTea importFromJson(JSONArray json, Map<String, Class<?>> schema) {
        
        jsonImporter = new JsonImporter(this);
        jsonImporter.importFromJson(json, schema);
        return this;

    }

    public CupOfTea importFromJson(JSONArray json, Schema schema) {

        jsonImporter = new JsonImporter(this);
        jsonImporter.importFromJson(json, schema);
        return this;

    }

    /**
     * Dumps the Data from a Yaml Text into the CupOfTea data frame.
     * 
     * @param yamlText Yaml Text.
     */
    public CupOfTea importFromYaml(String yamlText) {

        yamlImporter = new YamlImporter(this);
        yamlImporter.importFromYaml(yamlText);
        return this;

    }

    /**
     * Dumps the Data from a Yaml File into the CupOfTea data frame.
     * 
     * @param file Yaml File.
     */
    public CupOfTea importFromYaml(File file) {
        
        yamlImporter = new YamlImporter(this);
        yamlImporter.importFromYaml(file);
        return this;

    }

    /**
     * Set the Headers of the Data Frame
     * 
     * @param arrayList Supports Column and String ArrayList Types
     */
	public CupOfTea setHeaders(ArrayList<?> arrayList) {
        if (arrayList == null || arrayList.isEmpty()) {
            throw new SchemaMismatchException("Headers list cannot be null or empty");
        }
        ArrayList<Column> columns;
        if(arrayList.isEmpty()) {
            columns = new ArrayList<>();
        } else if(arrayList.get(0) instanceof String) {
            columns = (ArrayList<Column>) ((ArrayList<String>) arrayList).stream().map(column -> new Column(column, null)).collect(Collectors.toList());
        } else if(arrayList.get(0) instanceof Column) {
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
