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
import com.CupOfTea.importers.interfaces.IParquetImporter;

public class ParquetImporter implements IParquetImporter {

    private CupOfTea dataFrame;

    public ParquetImporter(CupOfTea dataFrame){
        this.dataFrame = dataFrame;
    }


    @Override
    public void importFromParquet(File file) throws Exception {

        Path path = new Path(file.toString());
        Configuration conf = new Configuration();
        InputFile iptf = HadoopInputFile.fromPath(path, conf);

        try (ParquetReader<GenericRecord> reader = AvroParquetReader.<GenericRecord>builder(iptf).build()) {
            GenericRecord record;
            Boolean setHeaders = true;
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
                
                ArrayList<String> headers = new ArrayList<>();
                HashMap<String, Object> row = new HashMap<>();
                Schema schema = record.getSchema();
                for (Schema.Field field : schema.getFields()) {
                    if(setHeaders){
                        headers.add(field.name());
                    }
                    Schema fieldSchema = field.schema();
                    String fieldType = fieldSchema.getType().getName();
                    row.put(field.name(), record.get(field.name()).toString());
                }
                if(setHeaders){
                    this.dataFrame.setHeaders(headers);
                    setHeaders = false;
                }
                this.dataFrame.addRow(row);

            }
        }
    }

}
