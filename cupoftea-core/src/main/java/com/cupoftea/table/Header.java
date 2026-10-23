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

    /**
     * Creates a new column with the given name in the last position with a null type.
     *
     * @param columnName the name of the column
     */
    public void addColumn(String columnName) {
        int index = header.size();
        this.addColumn(index, columnName);
    }

    public void addColumn(int index, String columnName) {
        header.add(index, new Column(columnName, null));
    }

    /**
     * Create a new column with a specific name and type.
     * The column will be added to the end of the Data Frame
     * 
     * @param columnName Name of the new Column
     * @param columnType Class type of the new Column
     */
    public void addColumn(String columnName, Class<?> columnType) {
        this.addColumn(columnName, columnType, true);
    }

    public void addColumn(String columnName, Class<?> columnType, boolean nullable) {
        int index = header.size();
        header.add(index, new Column(columnName, columnType, nullable));
    }

    public void addColumn(int index, String columnName, Class<?> columnType, boolean nullable) {
        header.add(index, new Column(columnName, columnType, nullable));
    }

    public void moveColumn(String columnName, int finalIndex) {
        int columnIndex = header.indexOf(new Column(columnName, null));
        this.moveColumn(columnIndex, finalIndex);
    }

    public void moveColumn(int inicialIndex, int finalIndex) {
        // Column order is defined by the header order
        Column current = this.header.remove(inicialIndex);
        this.header.add(finalIndex, current);
    }

    public void renameColumn(String oldName, String newName) {
        int index = this.header.indexOf(new Column(oldName, null));
        this.renameColumn(index, newName);
    }

    public void renameColumn(int index, String newName) {
        Column current = this.header.get(index);
        this.header.set(index, new Column(newName, current.columnType(), current.nullable()));
    }

    public void removeColumn(String columnName) {
        int columnIndex = header.indexOf(new Column(columnName, null));
        this.header.remove(columnIndex);
    }

    public void removeColumn(int index) {
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
        boolean nullable = index != -1 ? this.header.get(index).nullable() : true;
        this.header.set(index, new Column(columnName, columnType, nullable));
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
