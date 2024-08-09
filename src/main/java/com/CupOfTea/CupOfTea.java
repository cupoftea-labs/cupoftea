package com.CupOfTea;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;

import com.CupOfTea.exporters.CSVExporter;
import com.CupOfTea.exporters.ParquetExporter;
import com.CupOfTea.exporters.interfaces.ICSVExporter;
import com.CupOfTea.exporters.interfaces.IJsonExporter;
import com.CupOfTea.exporters.interfaces.IParquetExporter;
import com.CupOfTea.exporters.interfaces.IYamlExporter;
import com.CupOfTea.importers.CSVImporter;
import com.CupOfTea.importers.JsonImporter;
import com.CupOfTea.importers.ParquetImporter;
import com.CupOfTea.importers.YamlImporter;
import com.CupOfTea.importers.interfaces.ICSVImporter;
import com.CupOfTea.importers.interfaces.IJsonImporter;
import com.CupOfTea.importers.interfaces.IParquetImporter;
import com.CupOfTea.importers.interfaces.IYamlImporter;
import com.CupOfTea.utils.Printer;
import com.CupOfTea.utils.DataFrame;
import com.CupOfTea.utils.interfaces.IDataFrame;


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


    public CupOfTea() {
        this.dataFrame = new DataFrame();
    }

    public ArrayList<Object> getRow(int index){
        return this.dataFrame.getDataFrame().get(index);
    }

    public ArrayList<ArrayList<Object>> getRows(){
        return this.dataFrame.getRows();
    }

    public void moveColumn (String columnName, int finalIndex){
        this.dataFrame.moveColumn(columnName, finalIndex);
    }
    
    public void moveColumn (int inicialIndex, int finalIndex){
        this.dataFrame.moveColumn(inicialIndex, finalIndex);
    }

    public void renameColumn (String oldName, String newName) {
        this.dataFrame.renameColumn(oldName, newName);
    }

    public void renameColumn (int index, String newName) {
        this.dataFrame.renameColumn(index, newName);
    }

    public void renameColumn (HashMap<String,String> name){
        this.dataFrame.renameColumn(name);
    }
    
    public void addColumn(String columnName) {
        this.dataFrame.addColumn(columnName);
    }

    public void addColumn(int index, String columnName){
        this.dataFrame.addColumn(index, columnName);
    }

    public void addColumn(int index, String columnName, @Nullable Object data){
        this.dataFrame.addColumn(index, columnName, data);
    }

    public void addColumn(int index, String columnName, @Nullable ArrayList<Object> data){
        this.dataFrame.addColumn(index, columnName, data);
    }

    public void removeColumn(String columnName) {
        this.dataFrame.removeColumn(columnName);
    }

    public void removeColumn(int index) {
        this.dataFrame.removeColumn(index);
    }

    public void addRow(@NotNull ArrayList<Object> row) throws Exception{
        this.dataFrame.addRow(row);
    }

    public void removeRow(int index){
        this.dataFrame.removeRow(index);
    }

    
    public void print(){
        
        Printer printer = new Printer(this);
        printer.print();

    }

    
    public void exportToCsv(File file){

        csvExporter = new CSVExporter(this);
        csvExporter.exportToCsv(file);

    }

    
    public void importFromCsv(File file, boolean hasHeaders) throws Exception {

        csvImporter = new CSVImporter(this);
        csvImporter.importFromCsv(file, hasHeaders);

    }

    public void importFromParquet(File file) throws Exception {

        parquetImporter = new ParquetImporter(this);
        parquetImporter.importFromParquet(file);

    }

    public void exportToParquet(File file) {       
        
        parquetExporter = new ParquetExporter(this);
        parquetExporter.exportToParquet(file);

    }

    public void importFromJson(String json) {
        jsonImporter = new JsonImporter(this);
        jsonImporter.importFromJson(json);
    }

    /**
     * @param file
     * @throws IOException
     */
    public void importFromJson(File file) throws IOException{

        jsonImporter = new JsonImporter(this);
        jsonImporter.importFromJson(file);

    }

    /**
     * @param json
     */
    public void importFromJson(JSONArray json){
        
        jsonImporter = new JsonImporter(this);
        jsonImporter.importFromJson(json);

    }

    /**
     * @param yamlText
     * @throws Exception 
     */
    public void importFromYaml(String yamlText) throws Exception{
        
        yamlImporter = new YamlImporter(this);
        yamlImporter.importFromYaml(yamlText);
 
    }

    /**
     * @param file
     * @throws Exception 
     */
    public void importFromYaml(File file) throws Exception{
        
        yamlImporter = new YamlImporter(this);
        yamlImporter.importFromYaml(file);

    }

    public ArrayList<String> getHeaders(){
        return this.dataFrame.getHeaders();
    }

    public Integer size(){
        return this.dataFrame.size();
    }

	public void setHeaders(ArrayList<String> arrayList) {
		this.dataFrame.setHeaders(arrayList);
	}

}