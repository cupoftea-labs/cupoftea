package com.CupOfTea.utils;

import java.util.ArrayList;
import java.util.HashMap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.CupOfTea.utils.interfaces.IDataFrame;
import com.CupOfTea.utils.interfaces.IFilter;
import com.CupOfTea.utils.interfaces.Orderer;

public class DataFrame extends IDataFrame {

    public DataFrame(){
        super();
    }

    @Override
    public void moveColumn(String columnName, int finalIndex) {
        int columnIndex = headers.indexOf(columnName);
        this.moveColumn(columnIndex, finalIndex);
    }

    @Override
    public void moveColumn(int inicialIndex, int finalIndex) {
        String columnName = this.headers.get(inicialIndex);
        this.headers.remove(inicialIndex);
        this.headers.add(finalIndex, columnName);
        for (ArrayList<Object> dataRow: dataFrame) {
            Object data = dataRow.get(inicialIndex);
            dataRow.remove(inicialIndex);
            dataRow.add(finalIndex, data);
        }
    }

    @Override
    public void renameColumn(String oldName, String newName) {
        int index = this.headers.indexOf(oldName);
        renameColumn(index, newName);
    }

    @Override
    public void renameColumn(int index, String newName) {
        this.headers.set(index, newName);
    }

    @Override
    public void renameColumn(HashMap<String, String> name) {
        name.forEach((key, value) -> {
            this.renameColumn(key, value);
        });
    }

    /*
     * @params columnName
     * The column will be created in the last position with null value
     */
    @Override
    public void addColumn(String columnName) {
        int index = headers.size();
        addColumn(index, columnName);
    }

    @Override
    public void addColumn(int index, String columnName) {
        addColumn(index, columnName, (Object) null);
    }

    @Override
    public void addColumn(int index, String columnName, @Nullable Object data) {
        ArrayList<Object> dataArray = new ArrayList<>();
        for (@SuppressWarnings("unused") var row : dataFrame) {
            dataArray.add(data);
        }
        addColumn(index,columnName,dataArray);
    }

    @Override
    public void addColumn(int index, String columnName, @Nullable ArrayList<Object> data) {
        if (data == null) {
            addColumn(index, columnName, (Object) data);
            return;
        }
        headers.add(index, columnName);
        int i = 0;
        // ToDo procurar forma mais eficiente de preencher esses dados
        for (ArrayList<Object> objects : dataFrame) {
            objects.add(index,data.get(i));
        }
        // ToDo lidar com casos em que é utilizado a mesma variável para criar colunas -> Evitar que ao editar uma linha outras sejam editadas ao mesmo tempo
    }

    @Override
    public void removeColumn(String columnName) {
        // ToDo lidar com colunas com mesmo nome? ou remover apenas a primeira que aparece
        // ToDo Tratar caso nao exista a coluna
        int columnIndex = headers.indexOf(columnName);
        removeColumn(columnIndex);
    }

    @Override
    public void removeColumn(int index) {
        // ToDo tratar out of bond?
        headers.remove(index);
        for (int i = 0; i < dataFrame.size(); i++) {
            dataFrame.get(i).remove(index);
        }
    }

    @Override
    public void addRow(@NotNull ArrayList<Object> row) throws Exception {
        if(row.size() != this.headers.size()) {
            // ToDo Criar uma exception mais especifica
            throw new ArrayIndexOutOfBoundsException();
        }
        // ToDo testar se for usado a mesma variavel para criar colunas da row se da problema
        dataFrame.add((ArrayList<Object>) row.clone());
    }

    private void addRowWithoutCloning(@NotNull ArrayList<Object> row) throws Exception {
        if(row.size() != this.headers.size()) {
            // ToDo Criar uma exception mais especifica
            throw new ArrayIndexOutOfBoundsException();
        }
        // ToDo testar se for usado a mesma variavel para criar colunas da row se da problema
        dataFrame.add(row);
    }

    @Override
    public void removeRow(int index) {
        dataFrame.remove(index);
    }

    @Override
    public ArrayList<String> getHeaders() {
        return this.headers;
    }

    @Override
    public IDataFrame filter(IFilter filter) throws Exception {

        DataFrame filteredDF = new DataFrame();

        filteredDF.setHeaders(this.headers);

        for(var row : dataFrame){

            HashMap<String, Object> map = new HashMap<>();
            for(int i = 0; i < this.headers.size(); i++){
                map.put(this.headers.get(i), row.get(i));
            }

            if(filter.filter(map)){
                filteredDF.addRowWithoutCloning(row);
            }
        }

        return filteredDF;
    }

    @Override
    public IDataFrame update(HashMap<String, Object> updateColumns) throws Exception {
        for(var row : dataFrame){
            for(int i = 0; i < this.headers.size(); i++){
                if (updateColumns.containsKey(this.headers.get(i))){
                    row.set(i, updateColumns.get(this.headers.get(i)));
                }
            }  
        }

        return this;
    }

    @Override
    public IDataFrame orderBy(Orderer... orderer) throws Exception {
        int index = this.headers.indexOf(orderer[0].column());
        

        if(dataFrame.get(0).get(index) instanceof Integer) {
            this.dataFrame.sort((a, b) -> orderer[0].type().value * ((Integer)a.get(index) - (Integer)b.get(index)));
        } else {
            throw new Exception("Not Supported data type for ordering");
        }

        return this;
    }

}
