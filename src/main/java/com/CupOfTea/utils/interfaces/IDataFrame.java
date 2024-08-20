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
    protected ArrayList<HashMap<String, Object>> rows;

    public IDataFrame(){
        // ToDo lidar com o limite de memoria do Java com (sun.misc.Unsafe class)
        // https://blogs.oracle.com/javamagazine/post/the-unsafe-class-unsafe-at-any-speed
        this.rows = new ArrayList<>();
        this.headers = new ArrayList<>();
        
    }

    public abstract IDataFrame moveColumn(String columnName, int finalIndex);
    public abstract IDataFrame moveColumn(int inicialIndex, int finalIndex);
    public abstract IDataFrame renameColumn(String oldName, String newName);
    public abstract IDataFrame renameColumn(int index, String newName);
    public abstract IDataFrame renameColumn(HashMap<String,String> name);
    public abstract IDataFrame addColumn(String columnName) throws Exception;
    public abstract IDataFrame addColumn(int index, String columnName) throws Exception;
    public abstract IDataFrame addColumn(int index, String columnName, @Nullable Object data) throws Exception;
    public abstract IDataFrame addColumn(int index, String columnName, @Nullable ArrayList<Object> data) throws Exception;
    public abstract IDataFrame removeColumn(String columnName);
    public abstract IDataFrame removeColumn(int index);
    public abstract IDataFrame addRow(@NotNull HashMap<String, Object> row) throws Exception;
    public abstract IDataFrame removeRow(int index);
    public abstract ArrayList<String> getHeaders();
    public abstract IDataFrame filter(IFilter filter) throws Exception;
    public abstract IDataFrame update(HashMap<String, Object> updateColumns) throws Exception;
    public abstract IDataFrame orderBy(Orderer... orderer) throws Exception;

    public Integer size(){
        return this.rows.size();
    }


    public void setHeaders(ArrayList<String> headers){
        this.headers = headers;
    }
}
