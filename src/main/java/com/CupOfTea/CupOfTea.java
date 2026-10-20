package com.CupOfTea;

import java.io.File;
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

import com.CupOfTea.exporters.CSVExporter;
import com.CupOfTea.exporters.JsonExporter;
import com.CupOfTea.exporters.ParquetExporter;
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
import com.CupOfTea.importers.interfaces.ICSVImporter;
import com.CupOfTea.importers.interfaces.IHTMLImporter;
import com.CupOfTea.importers.interfaces.IJsonImporter;
import com.CupOfTea.importers.interfaces.IParquetImporter;
import com.CupOfTea.importers.interfaces.IXmlImporter;
import com.CupOfTea.importers.interfaces.IYamlImporter;
import com.CupOfTea.table.Column;
import com.CupOfTea.table.DataFrame;
import com.CupOfTea.table.Group;
import com.CupOfTea.table.interfaces.IDataFrame;
import com.CupOfTea.utils.Printer;
import com.CupOfTea.utils.interfaces.IColumnCreator;
import com.CupOfTea.utils.interfaces.IFilter;
import com.CupOfTea.utils.interfaces.Orderer;


public class CupOfTea {

    private IDataFrame dataFrame;

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
        this.dataFrame = new DataFrame();
    }

    private CupOfTea(IDataFrame dataFrame){
        this.dataFrame = dataFrame;
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

    public HashMap<String, Object> getRow(int index){
        return this.dataFrame.getRows().get(index);
    }

    public ArrayList<HashMap<String, Object>> getRows(){
        return this.dataFrame.getRows();
    }

    public CupOfTea moveColumn (String columnName, int finalIndex){
        this.dataFrame.moveColumn(columnName, finalIndex);
        return this;
    }
    
    public CupOfTea moveColumn (int inicialIndex, int finalIndex){
        this.dataFrame.moveColumn(inicialIndex, finalIndex);
        return this;
    }

    public CupOfTea renameColumn (String oldName, String newName) {
        this.dataFrame.renameColumn(oldName, newName);
        return this;
    }

    public CupOfTea renameColumn (int index, String newName) {
        this.dataFrame.renameColumn(index, newName);
        return this;
    }

    public CupOfTea renameColumn (HashMap<String,String> name){
        this.dataFrame.renameColumn(name);
        return this;
    }
    
    public CupOfTea addColumn(String columnName) throws Exception {
        this.dataFrame.addColumn(columnName);
        return this;
    }

    public CupOfTea addColumn(String columnName, Class<?> columnType) throws Exception {
        this.dataFrame.addColumn(columnName, columnType);
        return this;
    }

    public CupOfTea addColumn(int index, String columnName) throws Exception{
        this.dataFrame.addColumn(index, columnName);
        return this;
    }

    public CupOfTea addColumn(int index, String columnName, Class<?> columnType) throws Exception{
        this.dataFrame.addColumn(index, columnName, columnType);
        return this;
    }

    public CupOfTea addColumn(int index, String columnName, @Nullable Object data) throws Exception{
        this.dataFrame.addColumn(index, columnName, data);
        return this;
    }

    public CupOfTea addColumn(int index, String columnName, @Nullable ArrayList<Object> data) throws Exception{
        this.dataFrame.addColumn(index, columnName, data);
        return this;
    }

    public CupOfTea alterColumnType(String columnName, Class newColumnType) throws Exception {
        this.dataFrame.alterColumnType(columnName, newColumnType);
        return this;
    }

    /**
     * Create a new column whose values are defined by the Column Creator Function
     * The column will be added to the end of the Data Frame
     * 
     * @param columnName Name of the new Column
     * @param columnCreator Function that defines the values of the new column
     */
    public CupOfTea addColumn(String columnName, IColumnCreator columnCreator) throws Exception {
        this.dataFrame.addColumn(columnName, columnCreator);
        return this;
    }

    public CupOfTea addColumn(String columnName, Class<?> columnType, IColumnCreator columnCreator) throws Exception {
        this.dataFrame.addColumn(columnName, columnType, columnCreator);
        return this;
    }

    public CupOfTea addColumn(String columnName, IColumnCreator columnCreator, Class<?> columnType) throws Exception {
        this.dataFrame.addColumn(columnName, columnCreator, columnType);
        return this;
    }

    public CupOfTea addColumn(int index, String columnName, IColumnCreator columnCreator) throws Exception {
        this.dataFrame.addColumn(index, columnName, columnCreator);
        return this;
    }

    public CupOfTea addColumn(int index, String columnName, Class<?> columnType, IColumnCreator columnCreator) throws Exception {
        this.dataFrame.addColumn(index, columnName, columnType, columnCreator);
        return this;
    }

    public CupOfTea addColumn(int index, String columnName, IColumnCreator columnCreator, Class<?> columnType) throws Exception {
        this.dataFrame.addColumn(index, columnName, columnCreator, columnType);
        return this;
    }

    public CupOfTea removeColumn(String columnName) throws Exception {
        this.dataFrame.removeColumn(columnName);
        return this;
    }

    public CupOfTea removeColumn(int index) {
        this.dataFrame.removeColumn(index);
        return this;
    }

    public CupOfTea addRow(@NotNull HashMap<String, Object> row) throws Exception{
        this.dataFrame.addRow(row);
        return this;
    }

    public CupOfTea removeRow(int index){
        this.dataFrame.removeRow(index);
        return this;
    }


    /**
     * Removes the filtered rows from the DataFrame
     * 
     * @param filter the rows mathing this condition will be removed out of the DataFrame
     * @return the same DataFrame but without the filtered rows
     * @throws Exception
     */
    public CupOfTea removeRows(IFilter filter) throws Exception {
        this.dataFrame.removeRows(filter);
        return this;
    }

    public CupOfTea filter(IFilter filter) throws Exception{
        return new CupOfTea(this.dataFrame.filter(filter));
    }

    public CupOfTea update(HashMap<String, Object> updateColumns) throws Exception {
        return new CupOfTea(this.dataFrame.update(updateColumns));
    }

    public CupOfTea orderBy(Orderer... orderer) throws Exception{
        this.dataFrame.orderBy(orderer);
        return this;
    }
    
    public CupOfTea print(){
        
        Printer printer = new Printer(this);
        printer.print();
        return this;

    }

    public CupOfTea exportToJson(File file) throws Exception {

        jsonExporter = new JsonExporter(this);
        jsonExporter.exportToJson(file);
        return this;

    }

    public JSONArray exportToJson() throws Exception {

        jsonExporter = new JsonExporter(this);
        JSONArray json = jsonExporter.exportToJson();
        return json;

    }
    
    public CupOfTea exportToCsv(File file) throws Exception {

        csvExporter = new CSVExporter(this);
        csvExporter.exportToCsv(file);
        return this;

    }

    public CupOfTea exportToCsv(File file, CSVExporterConfiguration configuration) throws Exception {

        csvExporter = new CSVExporter(this);
        csvExporter.exportToCsv(file, configuration);
        return this;

    }

    
    public CupOfTea importFromCsv(File file) throws Exception {

        csvImporter = new CSVImporter(this);
        csvImporter.importFromCsv(file);
        return this;

    }

    public CupOfTea importFromCsv(File file, boolean hasHeaders) throws Exception {

        csvImporter = new CSVImporter(this);
        csvImporter.importFromCsv(file, hasHeaders);
        return this;

    }

    public CupOfTea importFromCsv(File file, CSVImporterConfiguration configuration) throws Exception {

        csvImporter = new CSVImporter(this, configuration);
        csvImporter.importFromCsv(file, configuration);
        return this;

    }

    public CupOfTea importFromExcel(File file, boolean hasHeaders) throws Exception {

        ExcelImporter excelImporter = new ExcelImporter(this);
        excelImporter.importFromExcel(file, hasHeaders);
        return this;

    }

    public CupOfTea importFromParquet(File file) throws Exception {

        parquetImporter = new ParquetImporter(this);
        parquetImporter.importFromParquet(file);
        return this;

    }

    public CupOfTea exportToParquet(File file) throws Exception { 
        
        parquetExporter = new ParquetExporter(this);
        parquetExporter.exportToParquet(file);
        return this;

    }

    public CupOfTea importFromJson(String json) throws Exception {

        jsonImporter = new JsonImporter(this);
        jsonImporter.importFromJson(json);
        return this;

    }

    public CupOfTea importFromJson(String json, Map<String, Class<?>> schema) throws Exception {

        jsonImporter = new JsonImporter(this);
        jsonImporter.importFromJson(json, schema);
        return this;

    }

    /**
     * @param file
     * @throws Exception 
     */
    public CupOfTea importFromJson(File file) throws Exception{

        jsonImporter = new JsonImporter(this);
        jsonImporter.importFromJson(file);
        return this;

    }

    public CupOfTea importFromJson(File file, Map<String, Class<?>> schema) throws Exception{

        jsonImporter = new JsonImporter(this);
        jsonImporter.importFromJson(file, schema);
        return this;

    }

    public CupOfTea importFromXml(File file) throws Exception{

        xmlImporter = new XmlImporter(this);
        xmlImporter.importFromXml(file);
        return this;

    }

    public CupOfTea importFromXml(String xml) throws Exception{

        xmlImporter = new XmlImporter(this);
        xmlImporter.importFromXml(xml);
        return this;

    }

    public CupOfTea importFromXml(Document doc) throws Exception{

        xmlImporter = new XmlImporter(this);
        xmlImporter.importFromXml(doc);
        return this;

    }

    public CupOfTea importFromHtml(File file) throws Exception{

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
    public CupOfTea importFromHtml(URL url) throws Exception {

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
    public CupOfTea importFromHtml(URL url, int timeoutMillis, int maxBodySizeBytes) throws Exception {

        htmlImporter = new HTMLImporter(this, timeoutMillis, maxBodySizeBytes);
        htmlImporter.importFromHtml(url, timeoutMillis, maxBodySizeBytes);
        return this;

    }

    public CupOfTea importFromHtml(String html) throws Exception {

        htmlImporter = new HTMLImporter(this);
        htmlImporter.importFromHtml(html);
        return this;

    }

    public CupOfTea importFromHtml(org.jsoup.nodes.Document doc) throws Exception {

        htmlImporter = new HTMLImporter(this);
        htmlImporter.importFromHtml(doc);
        return this;

    }

    /**
     * @param json
     * @throws Exception 
     */
    public CupOfTea importFromJson(JSONArray json) throws Exception{
        
        jsonImporter = new JsonImporter(this);
        jsonImporter.importFromJson(json);
        return this;

    }

    public CupOfTea importFromJson(JSONArray json, Map<String, Class<?>> schema) throws Exception{
        
        jsonImporter = new JsonImporter(this);
        jsonImporter.importFromJson(json, schema);
        return this;

    }

    /**
     * @param yamlText
     * @throws Exception 
     */
    public CupOfTea importFromYaml(String yamlText) throws Exception{
        
        yamlImporter = new YamlImporter(this);
        yamlImporter.importFromYaml(yamlText);
        return this;
 
    }

    /**
     * @param file
     * @throws Exception 
     */
    public CupOfTea importFromYaml(File file) throws Exception{
        
        yamlImporter = new YamlImporter(this);
        yamlImporter.importFromYaml(file);
        return this;

    }

    /**
     * Set the Headers of the Data Frame
     * 
     * @param arrayList Suports Column and String ArrayList Types
     */
	public CupOfTea setHeaders(ArrayList<?> arrayList) throws Exception {
        ArrayList<Column> columns;
        if(arrayList.isEmpty()) {
            columns = new ArrayList<>();
        } else if(arrayList.get(0) instanceof String) {
            columns = (ArrayList<Column>) ((ArrayList<String>) arrayList).stream().map(column -> new Column(column, null)).collect(Collectors.toList());
        } else if(arrayList.get(0) instanceof Column) {
            columns = new ArrayList<>((ArrayList<Column>) arrayList);
        } else {
            throw new Exception("Tipo de array nao suportado");
        }
		this.dataFrame.setHeaders(columns);
        return this;
    }


    /**
     * Retorna um novo DataFrame com colunas de dados agrupados, que podem ser tratados posteriormente
     * 
     */
    public CupOfTea groupBy(String... columns) throws Exception {

        CupOfTea groupedDataFrame = new CupOfTea();

        List<Column> columnsToGroup = this.getHeaders().stream().filter((a) -> {
            for(var column : columns){
                if(a.columnName().equals(column)) return false;
            }
            return true;
            })
            .collect(Collectors.toList());

        ArrayList<Column> groupedHeaders = new ArrayList<>();
        for(var column : columns) {
            Column columnHeader = this.getColumnHeader(column);
            groupedHeaders.add(new Column(columnHeader.columnName(), columnHeader.columnType()));
        }

        for(var row : this.getRows()){
            HashMap<String, Object> existingGroupRow = null;
            for (var candidate : groupedDataFrame.getRows()) {
                boolean matches = true;
                for (var column : columns) {
                    if (!Objects.equals(candidate.get(column), row.get(column))) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    existingGroupRow = candidate;
                    break;
                }
            }

            if(existingGroupRow != null) {
                for(var column : columnsToGroup) {
                    Object value = row.get(column.columnName());
                    Group groupedValue = (Group) existingGroupRow.get(column.columnName());
                    groupedValue.groupedValues.add(value);
                }
            } else {
                HashMap<String, Object> groupedRow = new HashMap<>();

                for (var column : columnsToGroup) {
                    Group group = new Group(row.get(column.columnName()), column.columnType());
                    groupedRow.put(column.columnName(), group);
                }
                for (var column : columns) {
                    groupedRow.put(column, row.get(column));
                }
                groupedDataFrame.addRow(groupedRow);
            }
        }

        columnsToGroup.stream()
            .map((a) -> new Column(a.columnName(), Group.class))
            .forEach(groupedHeaders::add);

        groupedDataFrame.setHeaders(groupedHeaders);

        return groupedDataFrame;
    }

    public CupOfTea sum(String column) throws Exception { 

        Column groupedColumn = this.getColumnHeader(column);
        Class<?> finalColumnType = null;
        String columnName = column+"Sum";

        if(groupedColumn.columnType() != Group.class) throw new Exception("This is not a grouped column");

        for(var row : this.getRows()) {
            Object sum = null;
            Group value = (Group) row.get(column);

            if(value.getColumnType() == Integer.class) {
                Integer integerSum = 0;
                finalColumnType = Integer.class;
                for(var valuesToSum : ((Group) row.get(column)).groupedValues) {
                    integerSum += (Integer) valuesToSum;
                }             
                sum = integerSum;    
            } else if (value.getColumnType() == Double.class){
                finalColumnType = Double.class;
                Double doubleSum = 0.0;
                for(var valuesToSum : ((Group) row.get(column)).groupedValues) {
                    doubleSum += (Double) valuesToSum;
                }             
                sum = doubleSum;
            }

            row.put(columnName, sum);
        }

        this.getHeaders().add(new Column(columnName, finalColumnType));

        return this;
    }

    public CupOfTea average(String column) throws Exception {

        Column groupedColumn = this.getColumnHeader(column);
        Class<?> finalColumnType = null;
        String columnName = column+"Average";

        if(groupedColumn.columnType() != Group.class) throw new Exception("This is not a grouped column");

        for(var row : this.getRows()) {
            Object average = null;
            Group value = (Group) row.get(column);

            if(value.getColumnType() == Integer.class) {
                finalColumnType = Double.class;
                Integer integerSum = 0;
                for(var valuesToSum : ((Group) row.get(column)).groupedValues) {
                    integerSum += (Integer) valuesToSum;
                }
                
                average = Double.valueOf(integerSum) / Double.valueOf(((Group) row.get(column)).groupedValues.size());

            } else if (value.getColumnType() == Double.class){
                finalColumnType = Double.class;
                Double doubleSum = 0.0;
                for(var valuesToSum : ((Group) row.get(column)).groupedValues) {
                    doubleSum += (Double) valuesToSum;
                }             
                average = doubleSum / Double.valueOf(((Group) row.get(column)).groupedValues.size());
            }


            row.put(columnName, average);
        }

        this.getHeaders().add(new Column(columnName, finalColumnType));

        return this;
    }

    public CupOfTea maximum(String column) throws Exception {

        Column groupedColumn = this.getColumnHeader(column);
        Class<?> finalColumnType = null;
        String columnName = column+"Maximum";

        if(groupedColumn.columnType() != Group.class) throw new Exception("This is not a grouped column");

        for(var row : this.getRows()) {
            Object maximum = null;
            Group value = (Group) row.get(column);

            if(value.getColumnType() == Integer.class) {
                Integer maximumValue = 0;
                finalColumnType = Integer.class;

                maximumValue = Collections.max(((Group) row.get(column)).groupedValues.stream().map(a -> (Integer) a).collect(Collectors.toList()));
        
                maximum = maximumValue;    

            } else if (value.getColumnType() == Double.class){
                Double maximumValue = 0.0;
                finalColumnType = Double.class;

                maximumValue = Collections.max(((Group) row.get(column)).groupedValues.stream().map(a -> (Double) a).collect(Collectors.toList()));
        
                maximum = maximumValue;    
            }

            row.put(columnName, maximum);
        }

        this.getHeaders().add(new Column(columnName, finalColumnType));

        return this;
    }

    public CupOfTea minimum (String column) throws Exception {

        
        Column groupedColumn = this.getColumnHeader(column);
        Class<?> finalColumnType = null;
        String columnName = column+"Minimum";

        if(groupedColumn.columnType() != Group.class) throw new Exception("This is not a grouped column");

        for(var row : this.getRows()) {
            Object minimum = null;
            Group value = (Group) row.get(column);

            if(value.getColumnType() == Integer.class) {
                Integer minimumValue = 0;
                finalColumnType = Integer.class;

                minimumValue = Collections.min(((Group) row.get(column)).groupedValues.stream().map(a -> (Integer) a).collect(Collectors.toList()));
        
                minimum = minimumValue;    

            } else if (value.getColumnType() == Double.class){
                Double minimumValue = 0.0;
                finalColumnType = Double.class;

                minimumValue = Collections.min(((Group) row.get(column)).groupedValues.stream().map(a -> (Double) a).collect(Collectors.toList()));
        
                minimum = minimumValue;    
            }

            row.put(columnName, minimum);
        }

        this.getHeaders().add(new Column(columnName, finalColumnType));

        return this;
    }

    public CupOfTea count(String column) throws Exception {
        Column groupedColumn = this.getColumnHeader(column);
        Class<?> finalColumnType = null;
        String columnName = column+"Count";

        if(groupedColumn.columnType() != Group.class) throw new Exception("This is not a grouped column");

        for(var row : this.getRows()) {
            Object count = null;
            Group value = (Group) row.get(column);

            finalColumnType = Integer.class;

            count = ((Group) row.get(column)).groupedValues.size();   
            
            row.put(columnName, count);
        }

        this.getHeaders().add(new Column(columnName, finalColumnType));

        return this;
    }

    public CupOfTea aggregate(String column, String separator) throws Exception {
        Column groupedColumn = this.getColumnHeader(column);
        Class<?> finalColumnType = String.class;
        String columnName = column+"Aggregate";

        if(groupedColumn.columnType() != Group.class) throw new Exception("This is not a grouped column");

        for(var row : this.getRows()) {
            Group value = (Group) row.get(column);

            String aggValue = value.groupedValues.stream().map(a -> a.toString()).collect(Collectors.joining(separator));

            row.put(columnName, aggValue);
        }
        
        this.getHeaders().add(new Column(columnName, finalColumnType));

        return this;
    }
}