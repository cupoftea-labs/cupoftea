package com.CupOfTea.table;

import java.util.ArrayList;
import java.util.HashMap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.CupOfTea.table.interfaces.IDataFrame;
import com.CupOfTea.utils.interfaces.IColumnCreator;
import com.CupOfTea.utils.interfaces.IFilter;
import com.CupOfTea.utils.interfaces.Orderer;

public class DataFrame extends IDataFrame {

    public DataFrame(){
        super();
    }

    @Override
    public IDataFrame moveColumn(String columnName, int finalIndex) {
        this.header.moveColumn(columnName, finalIndex);
        return this;
    }

    @Override
    public IDataFrame moveColumn(int initialIndex, int finalIndex) {
        this.header.moveColumn(initialIndex, finalIndex);
        return this;
    }

    @Override
    public IDataFrame renameColumn(String oldName, String newName) {
        this.header.renameColumn(oldName, newName);
        return this;
    }

    @Override
    public IDataFrame renameColumn(int index, String newName) {
        String oldName = this.header.get(index).columnName();
        this.header.renameColumn(index, newName);
        for(var row : this.getRows()){
            row.put(newName, row.get(oldName));
            row.remove(oldName);
        }
        return this;
    }

    @Override
    public IDataFrame renameColumn(HashMap<String, String> name) {
        name.forEach((key, value) -> {
            this.renameColumn(key, value);
        });
        return this;
    }

    /*
     * @params columnName
     * The column will be created in the last position with null value
     */
    @Override
    public IDataFrame addColumn(String columnName) throws Exception {
        int index = this.header.columnCount();
        this.addColumn(index, columnName);
        return this;
    }

    @Override
    public IDataFrame addColumn(int index, String columnName) throws Exception {
        this.addColumn(index, columnName, (Object) null);
        return this;
    }

    @Override
    public IDataFrame addColumn(int index, String columnName, @Nullable Object data) throws Exception {
        ArrayList<Object> dataArray = new ArrayList<>();
        for (@SuppressWarnings("unused") var row : this.rows) {
            dataArray.add(data);
        }
        this.addColumn(index,columnName,dataArray);
        return this;
    }

    @Override
    public IDataFrame addColumn(int index, String columnName, @Nullable ArrayList<Object> data) throws Exception {

        if (data == null) return this.addColumn(index, columnName, (Object) data);
        if (data.size() != this.size()) throw new ArrayIndexOutOfBoundsException();

        header.addColumn(index, new Column(columnName, null));
        int i = 0;
        for (HashMap<String, Object> row : this.rows) {
            row.put(columnName, data.get(i));
            i++;
        }
        return this;
    }

    /**
     * Create a new column whose values are defined by the Column Creator Function
     * The column will be added to the end of the Data Frame
     * 
     * @param columnName Name of the new Column
     * @param columnCreator Function that defines the values of the new column
     */
    @Override
    public IDataFrame addColumn(String columnName, IColumnCreator columnCreator) throws Exception {
        int index = header.columnCount();

        this.header.addColumn(index, new Column(columnName, columnCreator.createColumn(this.getRows().get(0)).getClass()));

        for(var row : this.getRows()){
            row.put(columnName, columnCreator.createColumn(row));
        }
        return this;
    }

    @Override
    public IDataFrame removeColumn(String columnName) {
        this.header.removeColumn(columnName);
        for(var row : this.rows) {
            row.remove(columnName);
        }

        return this;
    }

    @Override
    public IDataFrame removeColumn(int index){
        // ToDo tratar out of bond?
        String columnName = this.header.get(index).columnName();
        return this.removeColumn(columnName);
    }

    @Override
    public IDataFrame addRow(@NotNull HashMap<String, Object> row) throws Exception {
        // ToDo Verificar se todas as colunas batem com as colunas do header
        return this.addRow(row, true);
    }

    private IDataFrame addRow(@NotNull HashMap<String, Object> row, boolean clone) throws Exception {
        // ToDo Verificar se todas as colunas batem com as colunas do header
        if (clone) rows.add((HashMap<String, Object>) row.clone());
        else rows.add(row);
        return this;
    }

    @Override
    public IDataFrame removeRow(int index) {
        rows.remove(index);
        return this;
    }


    /**
     * Removes the filtered rows from the DataFrame
     * 
     * @param filter the rows mathing this condition will be removed out of the DataFrame
     * @return the same DataFrame but without the filtered rows
     * @throws Exception
     */
    @Override
    public IDataFrame removeRows(IFilter filter) throws Exception {
        for(int i = 0; i < this.rows.size(); i++){
            if(filter.filter(this.rows.get(i))){
                this.removeRow(i);
            }
        }
        return this;
    }

    @Override
    public ArrayList<Column> getHeaders() {
        return this.header.getHeader();
    }

    @Override
    public IDataFrame filter(IFilter filter) throws Exception {

        DataFrame filteredDF = new DataFrame();

        for(var row : this.rows){
            if(filter.filter(row)){
                filteredDF.addRow(row, false);
            }
        }

        return filteredDF;
    }

    @Override
    public IDataFrame update(HashMap<String, Object> updateColumns) throws Exception {
        for(var row : this.rows){
            row.putAll(updateColumns);
        }

        return this;
    }

    @Override
    public IDataFrame orderBy(Orderer... orderer) throws Exception {
        this.rows.sort((a, b) -> {
                int result = 0;
                for(var order : orderer) {
                    if(rows.get(0).get(order.column()) instanceof Integer) {

                        result = order.type().value * ((Integer)a.get(order.column()) - (Integer)b.get(order.column()));

                    } else if (rows.get(0).get(order.column()) instanceof String) {

                        result = order.type().value * ((String) a.get(order.column())).compareTo((String)b.get(order.column()));

                    }
                    if (result != 0) {
                        return result;
                    }
                }
                return result;
            }
        );
        return this;
    }

    

}
