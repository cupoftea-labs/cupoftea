package com.cupoftea.exporters;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.hadoop.util.HadoopOutputFile;
import org.json.JSONArray;
import org.json.JSONObject;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exporters.interfaces.IParquetExporter;
import com.cupoftea.table.Column;
import com.cupoftea.table.Row;


public class ParquetExporter implements IParquetExporter {

    private CupOfTea dataFrame;

    public ParquetExporter(CupOfTea dataFrame){
        this.dataFrame = dataFrame;
    }


    /**
     * Create a PARQUET file from the data frame.
     * 
     * @param file path to your PARQUET file. Can't be null.
     */
    @Override
    public void exportToParquet(File file) {
        if (file == null) {
            throw new ExportException("File cannot be null");
        }

        try {
            ArrayList<Column> headers = this.dataFrame.getHeaders();

            JSONArray fields = new JSONArray();

            for(var column : this.dataFrame.getHeaders()){
                Class<?> columnType = column.columnType();
                String type;

                if(columnType == String.class){
                    type = "string";
                } else if (columnType == Integer.class) {
                    type = "int";
                } else {
                    type = "union";
                }

                fields.put(new JSONObject().put("name", column.columnName()).put("type", type));
            }
            
            JSONObject schemaJson = new JSONObject();

            schemaJson
                    .put("type", "record")
                    .put("name", "DataFrame")
                    .put("fields", fields)
                ;

            Schema schema = new Schema.Parser().parse(schemaJson.toString());
            
            Path path = new Path(file.toString());
            Configuration conf = new Configuration();

            HadoopOutputFile outputFile = HadoopOutputFile.fromPath(path, conf);
            try (ParquetWriter<GenericRecord> writer = AvroParquetWriter.<GenericRecord>builder(outputFile)
                    .withSchema(schema)
                    .withCompressionCodec(CompressionCodecName.SNAPPY)
                    .withPageSize(1 * 1024 * 1024)
                    .withConf(conf)
                    .build()) {
                for (Row data : this.dataFrame.getRows()){
                    GenericRecord record = new GenericData.Record(schema);
                    for (int i = 0; i < data.size(); i++){
                        String header = headers.get(i).columnName();
                        record.put(header, data.get(header));
                    }
                    writer.write(record);
                }
            }
        } catch (IOException e) {
            throw new ExportException("Failed to export to Parquet file: " + file.getPath(), e);
        } catch (Exception e) {
            if (e instanceof ExportException) {
                throw (ExportException) e;
            }
            throw new ExportException("Failed to export to Parquet file: " + file.getPath(), e);
        }
    }

}
