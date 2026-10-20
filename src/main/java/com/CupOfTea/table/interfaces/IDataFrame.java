package com.CupOfTea.table.interfaces;

import java.util.ArrayList;
import java.util.HashMap;

import javax.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import com.CupOfTea.table.Column;
import com.CupOfTea.table.Header;
import com.CupOfTea.table.Schema;
import com.CupOfTea.utils.interfaces.IColumnCreator;
import com.CupOfTea.utils.interfaces.IFilter;
import com.CupOfTea.utils.interfaces.Orderer;

import lombok.Getter;

public abstract class IDataFrame {
    // ToDo ajustar tamanho dos headers para ter o mesmo numero de colunas que o body
    protected Header header;

    // ToDo tratar casos que as linhas tem diferentes quantidades de colunas
    // ToDo ? uma coluna pode ter diferentes tipos de dados ?
    // ToDo liberar o setter?
    // ToDo Usar Generics
    @Getter
    protected ArrayList<HashMap<String, Object>> rows;

    public ArrayList<HashMap<String, Object>> getRows() {
        return this.rows;
    }

    public IDataFrame(){
        // ToDo lidar com o limite de memoria do Java com (sun.misc.Unsafe class)
        // https://blogs.oracle.com/javamagazine/post/the-unsafe-class-unsafe-at-any-speed
        this.rows = new ArrayList<>();
        this.header = new Header();
    }

    public IDataFrame(Schema schema) {
        this();
        if (schema != null) {
            this.header = schema.toHeader();
        }
    }

    public abstract ArrayList<Column> getHeaders();
    public abstract Column getColumnHeader(String columnName);
    public abstract Schema getSchema();
    public abstract IDataFrame setSchema(Schema schema);
    public abstract IDataFrame moveColumn(String columnName, int finalIndex);
    public abstract IDataFrame moveColumn(int inicialIndex, int finalIndex);
    public abstract IDataFrame renameColumn(String oldName, String newName);
    public abstract IDataFrame renameColumn(int index, String newName);
    public abstract IDataFrame renameColumn(HashMap<String,String> name);
    public abstract IDataFrame addColumn(String columnName);
    public abstract IDataFrame addColumn(String columnName, Class<?> columnType);
    public abstract IDataFrame addColumn(int index, String columnName);
    public abstract IDataFrame addColumn(int index, String columnName, Class<?> columnType);
    public abstract IDataFrame addColumn(int index, String columnName, @Nullable Object data);
    public abstract IDataFrame addColumn(int index, String columnName, @Nullable ArrayList<Object> data);
    public abstract IDataFrame addColumn(String columnName, IColumnCreator columnCreator);
    public abstract IDataFrame addColumn(String columnName, Class<?> columnType, IColumnCreator columnCreator);
    public abstract IDataFrame addColumn(String columnName, IColumnCreator columnCreator, Class<?> columnType);
    public abstract IDataFrame addColumn(int index, String columnName, IColumnCreator columnCreator);
    public abstract IDataFrame addColumn(int index, String columnName, Class<?> columnType, IColumnCreator columnCreator);
    public abstract IDataFrame addColumn(int index, String columnName, IColumnCreator columnCreator, Class<?> columnType);
    public abstract IDataFrame removeColumn(String columnName);
    public abstract IDataFrame removeColumn(int index);
    public abstract IDataFrame alterColumnType(String columnName, Class<?> newColumnType);
    public abstract IDataFrame addRow(@NotNull HashMap<String, Object> row);
    public abstract IDataFrame removeRow(int index);
    public abstract IDataFrame removeRows(IFilter filter);
    public abstract IDataFrame filter(IFilter filter);
    public abstract IDataFrame update(HashMap<String, Object> updateColumns);
    public abstract IDataFrame orderBy(Orderer... orderer);
    

    public Integer size(){
        return this.rows.size();
    }


    public void setHeaders(ArrayList<Column> headers){
        this.header.setHeader(headers);
    }
}
