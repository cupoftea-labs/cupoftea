package com.CupOfTea.utils.interfaces;

import java.util.ArrayList;
import java.util.HashMap;

import javax.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import lombok.Getter;
import lombok.Setter;

public abstract class IDataFrame {

    // ToDo ajustar tamanho dos headers para ter o mesmo numero de colunas que o body
    // ToDo Criar classe do tipo Header?
    @Getter @Setter
    protected ArrayList<String> headers;
    // ToDo tratar casos que as linhas tem diferentes quantidades de colunas
    // ToDo ? uma coluna pode ter diferentes tipos de dados ?
    // ToDo liberar o setter?
    // ToDo Usar Generics
    @Getter
    protected ArrayList<ArrayList<Object>> dataFrame;

    public IDataFrame(){
        // ToDo lidar com o limite de memoria do Java com (sun.misc.Unsafe class)
        // https://blogs.oracle.com/javamagazine/post/the-unsafe-class-unsafe-at-any-speed
        this.dataFrame = new ArrayList<>();
        this.headers = new ArrayList<>();
        
    }

    public abstract void moveColumn(String columnName, int finalIndex);
    public abstract void moveColumn(int inicialIndex, int finalIndex);
    public abstract void renameColumn(String oldName, String newName);
    public abstract void renameColumn(int index, String newName);
    public abstract void renameColumn(HashMap<String,String> name);
    public abstract void addColumn(String columnName);
    public abstract void addColumn(int index, String columnName);
    public abstract void addColumn(int index, String columnName, @Nullable Object data);
    public abstract void addColumn(int index, String columnName, @Nullable ArrayList<Object> data);
    public abstract void removeColumn(String columnName);
    public abstract void removeColumn(int index);
    public abstract void addRow(@NotNull ArrayList<Object> row) throws Exception;
    public abstract void removeRow(int index);
    public abstract ArrayList<String> getHeaders();
    public abstract IDataFrame filter(IFilter filter) throws Exception;
    public abstract IDataFrame update(HashMap<String, Object> updateColumns) throws Exception;
    public abstract IDataFrame orderBy(Orderer... orderer) throws Exception;

    public Integer size(){
        return dataFrame.size();
    }

    public ArrayList<ArrayList<Object>> getRows(){
        return this.dataFrame;
    }

    public void setHeaders(ArrayList<String> headers){
        this.headers = headers;
    }
}
