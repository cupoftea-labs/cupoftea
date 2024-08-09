package com.CupOfTea.exporters;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

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


public class ParquetExporter implements IParquetExporter {

    private CupOfTea dataFrame;

    public ParquetExporter(CupOfTea dataFrame){
        this.dataFrame = dataFrame;
    }

    @Override
    public void exportToParquet(File file) {
        FieldAssembler assembler = SchemaBuilder
                                    .record("DataFrame") // Todo Ver melhor forma de definir record name
                                    .namespace("com.CupOfTea") // Todo ver melhor forma de definir namespace
                                    .fields();

        ArrayList<String> headers = this.dataFrame.getHeaders();

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

            for (ArrayList<Object> data :  this.dataFrame.getRows()){
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

}
