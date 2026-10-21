package com.cupoftea.table;

import java.util.ArrayList;

import lombok.Getter;

public class Header {

    @Getter
    private ArrayList<Column> header;

    public ArrayList<Column> getHeader() {
        return this.header;
    }

    public Header(){
        this.header = new ArrayList<>();
    }

    public void addColumn(Column column) {
        this.header.add(column);
    }

    public void addColumn(int index, Column column) {
        this.header.add(index, column);
    }

    /*
     * @params columnName
     * The column will be created in the last position with null value
     */
    public void addColumn(String columnName) {
        int index = header.size();
        this.addColumn(index, columnName);
    }

    public void addColumn(int index, String columnName) {
        header.add(index, new Column(columnName, null));
    }

    /**
     * Create a new column whose values are defined by the Column Creator Function
     * The column will be added to the end of the Data Frame
     * 
     * @param columnName Name of the new Column
     * @param columnCreator Function that defines the values of the new column
     */
    public void addColumn(String columnName, Class<?> columnType) {
        int index = header.size();
        header.add(index, new Column(columnName, columnType));
    }

    public void moveColumn(String columnName, int finalIndex) {
        int columnIndex = header.indexOf(new Column(columnName, null));
        this.moveColumn(columnIndex, finalIndex);
    }

    public void moveColumn(int inicialIndex, int finalIndex) {
        // A Ordem da Coluna é definida pela ordem dos header
        Column current = this.header.remove(inicialIndex);
        this.header.add(finalIndex, current);
    }

    public void renameColumn(String oldName, String newName) {
        int index = this.header.indexOf(new Column(oldName, null));
        this.renameColumn(index, newName);
    }

    public void renameColumn(int index, String newName) {
        Column current = this.header.get(index);
        this.header.set(index, new Column(newName, current.columnType()));
    }

    public void removeColumn(String columnName) {
        // ToDo Tratar caso nao exista a coluna
        int columnIndex = header.indexOf(new Column(columnName, null));
        this.header.remove(columnIndex);
    }

    public void removeColumn(int index) {
        // ToDo tratar out of bond?
        String columnName = this.header.get(index).columnName();
        this.removeColumn(columnName);
    }

    public int columnCount(){
        return this.header.size();
    }

    public int size() {
        return this.header.size();
    }

    public Column get(int index) {
        return this.header.get(index);
    }

    public Column get(String columnName) {
        int index = header.indexOf(new Column(columnName, null));
        if (index == -1) {
            return null;
        }
        return this.header.get(index);
    }

    public void setHeader(ArrayList<Column> header){
        this.header = new ArrayList<>(header);
    }

    public void alterColumnType(String columnName, Class columnType){
        int index = this.header.indexOf(new Column(columnName, null));
        this.header.set(index, new Column(columnName, columnType));
    }

    public Header copy() {
        Header copy = new Header();
        copy.setHeader(new ArrayList<>(this.header));
        return copy;
    }

    public void clear() {
        this.header.clear();
    }
}
