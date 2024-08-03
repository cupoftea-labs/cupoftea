package com.CupOfTea;

import com.opencsv.CSVWriter;

import lombok.Getter;
import lombok.Setter;

import org.apache.parquet.io.InputFile;
import org.apache.hadoop.fs.Path;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.SchemaBuilder.FieldAssembler;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.hadoop.util.HadoopInputFile;
import org.apache.parquet.hadoop.util.HadoopOutputFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Collectors;


public class DataFrame {

    // ToDo ajustar tamanho dos headers para ter o mesmo numero de colunas que o body
    // ToDo Criar classe do tipo Header?
    @Getter @Setter
    private ArrayList<String> headers;
    // ToDo tratar casos que as linhas tem diferentes quantidades de colunas
    // ToDo ? uma coluna pode ter diferentes tipos de dados ?
    // ToDo liberar o setter?
    // ToDo Usar Generics
    @Getter
    private ArrayList<ArrayList<Object>> dataFrame;

    public DataFrame() {
        // ToDo lidar com o limite de memoria do Java com (sun.misc.Unsafe class)
        // https://blogs.oracle.com/javamagazine/post/the-unsafe-class-unsafe-at-any-speed
        this.dataFrame = new ArrayList<>();
        this.headers = new ArrayList<>();
    }


    public void moveColumn (String columnName, int finalIndex){
        int columnIndex = headers.indexOf(columnName);
        this.moveColumn(columnIndex, finalIndex);
    }
    
    public void moveColumn (int inicialIndex, int finalIndex){
        String columnName = this.headers.get(inicialIndex);
        this.headers.remove(inicialIndex);
        this.headers.add(finalIndex, columnName);
        for (ArrayList<Object> dataRow: dataFrame) {
            Object data = dataRow.get(inicialIndex);
            dataRow.remove(inicialIndex);
            dataRow.add(finalIndex, data);
        }
    }

    public void renameColumn (String oldName, String newName) {
        int index = this.headers.indexOf(oldName);
        renameColumn(index, newName);
    }

    public void renameColumn (int index, String newName) {
        this.headers.set(index, newName);
    }

    public void renameColumn (HashMap<String,String> name){
        name.forEach((key, value) -> {
            this.renameColumn(key, value);
        });
    }


    // The column will be created in the last position
    // The column will be set with value null
    public void addColumn(String columnName) {
        int index = headers.size();
        addColumn(index, columnName);
    }

    public void addColumn(int index, String columnName){
        addColumn(index, columnName, (Object) null);
    }

    public void addColumn(int index, String columnName, @Nullable Object data){
        ArrayList<Object> dataArray = new ArrayList<>();
        for (@SuppressWarnings("unused") var row : dataFrame) {
            dataArray.add(data);
        }
        addColumn(index,columnName,dataArray);
    }

    public void addColumn(int index, String columnName, @Nullable ArrayList<Object> data){
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

    public void removeColumn(String columnName) {
        // ToDo lidar com colunas com mesmo nome? ou remover apenas a primeira que aparece
        // ToDo Tratar caso nao exista a coluna
        int columnIndex = headers.indexOf(columnName);
        removeColumn(columnIndex);
    }
    public void removeColumn(int index) {
        // ToDo tratar out of bond?
        headers.remove(index);
        for (int i = 0; i < dataFrame.size(); i++) {
            dataFrame.get(i).remove(index);
        }
    }

    public void addRow(@NotNull ArrayList<Object> row) throws Exception{
        if(row.size() != this.headers.size()) {
            // ToDo Criar uma exception mais especifica
            throw new ArrayIndexOutOfBoundsException();
        }
        // ToDo testar se for usado a mesma variavel para criar colunas da row se da problema
        dataFrame.add((ArrayList<Object>) row.clone());
    }

    public void removeRow(int index){
        dataFrame.remove(index);
    }

    // ToDo ajustar tamanho das colunas
    // ToDo refatorar esse metodo
    // ToDo criar sobrecarga para escolhar quantas linhas printar
    // ToDo adicionar ao lado do nome da coluna o formato da coluna
    // ToDo melhorar descrição da tabela
    public void print(){
        // ToDo verificar qual logger usar
        System.out.println("Showing only the first 10 registers");
        // ToDo printar menos colunas
        System.out.print('\u250C');
        System.out.print(String.valueOf('\u2500').repeat((22)));
        for(int i = 0; i < this.headers.size() - 1; i++) {
            System.out.print('\u252C');
            System.out.print(String.valueOf('\u2500').repeat((22)));
        }
        System.out.println('\u2510');
        System.out.print('\u2502' + " ");
        this.headers.forEach((header) -> {
            int length = header.length();
            int spaces = 21 - length ;
            if(spaces <= 0){
                spaces = 1;
                header = header.substring(0,17) + "...";
            }
            String space = " ".repeat(spaces);
            System.out.print(header + space + '\u2502' + " ");
        });
        System.out.println();
        System.out.print('\u251C');
        System.out.print(String.valueOf('\u2500').repeat((22)));
        for(int i = 0; i < this.headers.size() - 1; i++) {
            System.out.print('\u253C');
            System.out.print(String.valueOf('\u2500').repeat((22)));
        }
        System.out.println('\u2524');
        for (int i = 0; i < 10 && i < this.dataFrame.size(); i++) {
            System.out.print('\u2502'+" ");

            this.dataFrame.get(i).forEach((rowColumn) -> {
                String rowData = rowColumn == null ? "null" : rowColumn.toString();
                int length = rowData.length();
                int spaces = 21 - length ;
                if(spaces <= 0){
                    spaces = 1;
                    rowColumn = rowData.substring(0,17) + "...";
                }
                String space = " ".repeat(spaces);
                System.out.print(rowColumn + space + '\u2502'+" ");
            });
            System.out.println();
        }
        System.out.print('\u2514');
        System.out.print(String.valueOf('\u2500').repeat((22)));
        for(int i = 0; i < this.headers.size() - 1; i++) {
            System.out.print('\u2534');
            System.out.print(String.valueOf('\u2500').repeat((22)));
        }
        System.out.println('\u2518');
    }

    // ToDo String deve gerar o CSV com ""?
    // ToDo está dando erro para exportar outros formatos de dados
    public void exportToCsv(File file){
            // first create file object for file placed at location
            // specified by filepath
        try {
            // create FileWriter object with file as parameter
            FileWriter outputfile = new FileWriter(file);

            // create CSVWriter object filewriter object as parameter
            CSVWriter writer = new CSVWriter(outputfile);

            // adding header to csv
            String[] header = headers.toArray(new String[0]);
            writer.writeNext(header);

            for (ArrayList<Object> data :  dataFrame){
                ArrayList<String> dataArr = (ArrayList<String>) data.stream()
                                                .map((i -> i.toString()))
                                                .collect(Collectors.toList())
                                                ;

                String[] dataStr = dataArr.toArray(new String[0]);
                writer.writeNext(dataStr);
            }

            // closing writer connection
            writer.close();
        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    // ToDo importar numeros no formato de numero
    public void importFromCsv(File file, boolean hasHeaders){
        // ToDo verificar o tamanho de cada linha, nao podem ter linhas de tamanho diferente
        // ToDo hasHeaders false criar cabeçalho com valores nulo
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            if(hasHeaders){
                line = br.readLine();
                String[] values = line.split(",");
                headers = new ArrayList<>(Arrays.asList(values));
            }
            while ((line = br.readLine()) != null) {
                Object[] values = line.split(",");
                dataFrame.add(new ArrayList<>(Arrays.asList(values)));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void importFromParquet(File file) throws IOException{
        Path path = new Path(file.toString());
        Configuration conf = new Configuration();
        InputFile iptf = HadoopInputFile.fromPath(path, conf);

        try (ParquetReader<GenericRecord> reader = AvroParquetReader.<GenericRecord>builder(iptf).build()) {
            GenericRecord record;
            while ((record = reader.read()) != null) {
                // The Schema.Type enum in Avro defines several types including:
                // NULL
                // BOOLEAN
                // INT
                // LONG
                // FLOAT
                // DOUBLE
                // BYTES
                // STRING
                // RECORD
                // ENUM
                // ARRAY
                // MAP
                // UNION -> Pode ser de um ou vários tipos, equivalente ao Object do Java
                // FIXED
                
                Boolean setHeaders = true;

                headers = new ArrayList<>();
                ArrayList<Object> values = new ArrayList<>();
                Schema schema = record.getSchema();
                for (Schema.Field field : schema.getFields()) {
                    if(setHeaders){
                        headers.add(field.name());
                    }
                    // Schema fieldSchema = field.schema();
                    // String fieldType = fieldSchema.getType().getName();
                    values.add(record.get(field.name()));
                }
                setHeaders = false;
                dataFrame.add(values);

            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void exportToParquet(File file) {       
        FieldAssembler assembler = SchemaBuilder
                                    .record("DataFrame") // Todo Ver melhor forma de definir record name
                                    .namespace("com.CupOfTea") // Todo ver melhor forma de definir namespace
                                    .fields();

        for (String header : headers){
            assembler.requiredString(header);
        }

        Schema schema = (Schema) assembler.endRecord();
        
        Path path = new Path(file.toString());
        Configuration conf = new Configuration();

        try {
            // Configure the Parquet writer
            HadoopOutputFile outputFile = HadoopOutputFile.fromPath(path, conf);
            ParquetWriter<GenericRecord> writer = AvroParquetWriter.<GenericRecord>builder(outputFile)
                .withSchema(schema)
                .withCompressionCodec(CompressionCodecName.SNAPPY)
                .withPageSize(1 * 1024 * 1024)
                .withConf(conf)
                .build();

            for (ArrayList<Object> data :  dataFrame){
                GenericRecord record = new GenericData.Record(schema);
                for (int i = 0; i < data.size(); i++){
                    record.put(headers.get(i), data.get(i).toString());
                }
                writer.write(record);
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void importFromJson(String json){
        this.importFromJson(new JSONArray(json));
    }

    public void importFromJson(File file){

    }

    public void importFromJson(JSONArray json){
        for (var key : (json.getJSONObject(0)).keySet()){
            headers.add(key);
        }
        for(int i = 0; i < json.length(); i++){
            ArrayList<Object> values = new ArrayList<>();
            for(var column : headers){
                var jsonObj = json.getJSONObject(i);
                values.add(jsonObj.get(column));
            }
            dataFrame.add(values);
        }
    }

}