package com.CupOfTea.importers;

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

import com.CupOfTea.CupOfTea;
import com.CupOfTea.exceptions.ImportException;
import com.CupOfTea.importers.interfaces.IParquetImporter;
import com.CupOfTea.table.Column;

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

                        Object value;

                        switch(fieldType){
                            case INT -> value = record.get(field.name());
                            case STRING -> value = record.get(field.name()) != null ? record.get(field.name()).toString() : null;
                            default -> value = record.get(field.name()) != null ? record.get(field.name()).toString() : null;
                        }

                        row.put(field.name(), value);

                    }
                    if(setHeaders){
                        this.dataFrame.setHeaders(headers);
                        setHeaders = false;
                    }
                    this.dataFrame.addRow(row);

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
