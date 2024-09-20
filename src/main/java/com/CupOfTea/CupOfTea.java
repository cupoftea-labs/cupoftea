package com.CupOfTea;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.w3c.dom.Document;

import com.CupOfTea.exporters.CSVExporter;
import com.CupOfTea.exporters.ParquetExporter;
import com.CupOfTea.exporters.interfaces.ICSVExporter;
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
import com.CupOfTea.importers.interfaces.ICSVImporter;
import com.CupOfTea.importers.interfaces.IHTMLImporter;
import com.CupOfTea.importers.interfaces.IJsonImporter;
import com.CupOfTea.importers.interfaces.IParquetImporter;
import com.CupOfTea.importers.interfaces.IXmlImporter;
import com.CupOfTea.importers.interfaces.IYamlImporter;
import com.CupOfTea.table.Column;
import com.CupOfTea.table.DataFrame;
import com.CupOfTea.table.interfaces.IDataFrame;
import com.CupOfTea.utils.Printer;
import com.CupOfTea.utils.interfaces.IFilter;
import com.CupOfTea.utils.interfaces.Orderer;


public class CupOfTea {

    private IDataFrame dataFrame;

    private ICSVExporter csvExporter;
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

    public CupOfTea addColumn(int index, String columnName) throws Exception{
        this.dataFrame.addColumn(index, columnName);
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

    public CupOfTea removeColumn(String columnName) {
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

    
    public CupOfTea exportToCsv(File file) throws Exception {

        csvExporter = new CSVExporter(this);
        csvExporter.exportToCsv(file);
        return this;

    }

    
    public CupOfTea importFromCsv(File file, boolean hasHeaders) throws Exception {

        csvImporter = new CSVImporter(this);
        csvImporter.importFromCsv(file, hasHeaders);
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

    /**
     * @param file
     * @throws Exception 
     */
    public CupOfTea importFromJson(File file) throws Exception{

        jsonImporter = new JsonImporter(this);
        jsonImporter.importFromJson(file);
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

    public CupOfTea importFromHtml(URL url) throws Exception {

        htmlImporter = new HTMLImporter(this);
        htmlImporter.importFromHtml(url);
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


	public CupOfTea setHeaders(ArrayList<String> arrayList) {
        ArrayList<Column> columns = (ArrayList<Column>) arrayList.stream().map(column -> new Column(column)).collect(Collectors.toList());
		this.dataFrame.setHeaders(columns);
        return this;
	}

}