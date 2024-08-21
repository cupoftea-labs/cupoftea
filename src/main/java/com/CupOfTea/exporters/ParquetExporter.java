package com.CupOfTea.exporters;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.SchemaBuilder.FieldAssembler;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.hadoop.util.HadoopOutputFile;

import com.CupOfTea.CupOfTea;
import com.CupOfTea.exporters.interfaces.IParquetExporter;
import com.CupOfTea.table.Column;


public class ParquetExporter implements IParquetExporter {

    private CupOfTea dataFrame;

    public ParquetExporter(CupOfTea dataFrame){
        this.dataFrame = dataFrame;
    }

    @Override
    public void exportToParquet(File file) throws Exception {
        FieldAssembler assembler = SchemaBuilder
                                    .record("DataFrame") // Todo Ver melhor forma de definir record name
                                    .namespace("com.CupOfTea") // Todo ver melhor forma de definir namespace
                                    .fields();

        ArrayList<Column> headers = this.dataFrame.getHeaders();

        for (Column header : headers){
            assembler.requiredString(header.columnName());
        }

        Schema schema = (Schema) assembler.endRecord();
        
        Path path = new Path(file.toString());
        Configuration conf = new Configuration();

        // Configure the Parquet writer
        HadoopOutputFile outputFile = HadoopOutputFile.fromPath(path, conf);
        try (ParquetWriter<GenericRecord> writer = AvroParquetWriter.<GenericRecord>builder(outputFile)
                .withSchema(schema)
                .withCompressionCodec(CompressionCodecName.SNAPPY)
                .withPageSize(1 * 1024 * 1024)
                .withConf(conf)
                .build()) {
            for (HashMap<String, Object> data :  this.dataFrame.getRows()){
                GenericRecord record = new GenericData.Record(schema);
                for (int i = 0; i < data.size(); i++){
                    String header = headers.get(i).columnName();
                    record.put(header, data.get(header).toString());
                }
                writer.write(record);
            }
        }
    }

}
