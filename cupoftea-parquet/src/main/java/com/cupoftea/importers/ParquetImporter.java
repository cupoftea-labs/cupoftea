package com.cupoftea.importers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.util.HadoopInputFile;
import org.apache.parquet.io.InputFile;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.importers.interfaces.IParquetImporter;
import com.cupoftea.table.Column;

public class ParquetImporter implements IParquetImporter {

    private final CupOfTea dataFrame;

    public ParquetImporter(final CupOfTea dataFrame){
        this.dataFrame = dataFrame;
    }

    /**
     * Dumps the Data from a Parquet file into the CupOfTea data frame.
     * 
     * @param file Parquet file.
     */
    @Override
    public void importFromParquet(final File file) {
        if (file == null) {
            throw new ImportException("File cannot be null");
        }

        try {
            Path path = new Path(file.toString());
            Configuration conf = new Configuration();
            InputFile iptf = HadoopInputFile.fromPath(path, conf);

            try (ParquetReader<GenericRecord> reader = AvroParquetReader.<GenericRecord>builder(iptf).build()) {
                GenericRecord record;
                Boolean setHeaders = true;
                while ((record = reader.read()) != null) {
                    ArrayList<Column> headers = new ArrayList<>();
                    HashMap<String, Object> row = new HashMap<>();
                    Schema schema = record.getSchema();
                    for (Schema.Field field : schema.getFields()) {
                        Schema fieldSchema = field.schema();
                        Schema.Type fieldType = fieldSchema.getType();
                        if (fieldType == Schema.Type.UNION) {
                            for (Schema s : fieldSchema.getTypes()) {
                                if (s.getType() != Schema.Type.NULL) {
                                    fieldType = s.getType();
                                    break;
                                }
                            }
                        }

                        if(setHeaders){
                            Class<?> columnType;
                            
                            switch(fieldType){
                                case NULL -> columnType = null;
                                case BOOLEAN -> columnType = Boolean.class;
                                case INT -> columnType = Integer.class;
                                case LONG -> columnType = Long.class;
                                case FLOAT -> columnType = Float.class;
                                case DOUBLE -> columnType = Double.class;
                                case BYTES -> columnType = Byte.class;
                                case STRING -> columnType = String.class;
                                case RECORD -> columnType = null; // Not Implemented Yet
                                case ENUM -> columnType = null; // Not Implemented Yet
                                case ARRAY -> columnType = null; // Not Implemented Yet
                                case MAP -> columnType = null; // Not Implemented Yet
                                case UNION -> columnType = Object.class; 
                                case FIXED -> columnType = null; // Not Implemented Yet
                                default -> columnType = null;
                            }

                            headers.add(new Column(field.name(), columnType));
                        }

                        Object rawVal = record.get(field.name());
                        Object value;
                        if (rawVal == null) {
                            value = null;
                        } else {
                            switch(fieldType){
                                case INT -> value = rawVal instanceof Integer ? rawVal : Integer.parseInt(rawVal.toString());
                                case LONG -> value = rawVal instanceof Long ? rawVal : Long.parseLong(rawVal.toString());
                                case FLOAT -> value = rawVal instanceof Float ? rawVal : Float.parseFloat(rawVal.toString());
                                case DOUBLE -> value = rawVal instanceof Double ? rawVal : Double.parseDouble(rawVal.toString());
                                case BOOLEAN -> value = rawVal instanceof Boolean ? rawVal : Boolean.parseBoolean(rawVal.toString());
                                case STRING -> value = rawVal.toString();
                                default -> value = rawVal.toString();
                            }
                        }

                        row.put(field.name(), value);

                    }
                    if(setHeaders){
                        this.dataFrame.getDataFrame().setHeaders(headers);
                        setHeaders = false;
                    }
                    this.dataFrame.getDataFrame().addRow(row);

                }
            }
        } catch (IOException e) {
            throw new ImportException("Failed to read Parquet file: " + file.getPath(), e);
        } catch (Exception e) {
            if (e instanceof ImportException) {
                throw (ImportException) e;
            }
            throw new ImportException("Failed to parse Parquet file: " + file.getPath(), e);
        }
    }

}
