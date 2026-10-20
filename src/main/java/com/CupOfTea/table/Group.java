package com.CupOfTea.table;

import java.util.ArrayList;


// ToDo Group Columns don`t show in any exports
public class Group {

    public ArrayList<Object> groupedValues;
    private Class columnType;

    public Group(Object value, Class columnType){
        groupedValues = new ArrayList<>();
        groupedValues.add(value);
        this.columnType = columnType;
    }

    public Group(Group other){
        this.groupedValues = new ArrayList<>(other.groupedValues);
        this.columnType = other.columnType;
    }

    @Override
    public String toString(){
        return this.groupedValues.toString();
    }

    public Class getColumnType(){
        return this.columnType;
    }
}
