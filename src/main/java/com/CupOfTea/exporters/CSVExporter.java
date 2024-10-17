package com.CupOfTea.exporters;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlElement.DEFAULT;

import com.CupOfTea.CupOfTea;
import com.CupOfTea.exporters.configurations.CSVExporterConfiguration;
import com.CupOfTea.exporters.configurations.CSVExporterConfiguration.QuoteBehaviour;

public class CSVExporter {

    private final CupOfTea dataFrame;
    private CSVExporterConfiguration configuration;

    public CSVExporter(final CupOfTea dataFrame){
        this.dataFrame = dataFrame;
        this.configuration = new CSVExporterConfiguration();
    }


    /**
     * Create a CSV file from the data frame.
     * 
     * @param file path to your CSV file. Can't be null.
     * @throws if the file already exists throws an Exception
     */
    public void exportToCsv(File file) throws Exception {
        // ToDo Throws an Exception in case the file already exists?

        if (file == null) throw new Exception("File can not be null");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            String header = String.join(
                this.configuration.getSeparator(),
                this.dataFrame.getHeaders().stream()
                    .map(a -> a.columnName())
                    .toList()
                ) + this.configuration.getLineEndString()
            ;

            writer.write(header);
            
            for (HashMap<String, Object> data :  this.dataFrame.getRows()){
                ArrayList<String> dataArr = (ArrayList<String>) this.dataFrame.getHeaders().stream()
                        .map(i -> 
                            {   
                                String quote;

                                switch(this.configuration.getQuoteBehaviour()) {
                                    case ALL -> quote = this.configuration.getQuoteString();
                                    case ALL_BUT_NUMBERS -> quote = Number.class.isAssignableFrom(i.columnType()) ? "" : this.configuration.getQuoteString();
                                    case STRING_ONLY -> quote = i.columnType() == String.class ? this.configuration.getQuoteString() : "";
                                    case NONE -> quote = "";
                                    default -> quote = "";
                                }

                                String field = quote + data.get(i.columnName()).toString() + quote;
                                return field;
                            }
                        )
                        .collect(Collectors.toList())
                        ;
                
                String dataStr = String.join(this.configuration.getSeparator(), dataArr.toArray(String[]::new)) + this.configuration.getLineEndString();
                writer.write(dataStr);
            }
        }
    }

    /**
     * Create a CSV file from the data frame.
     * 
     * @param file path to your CSV file. Can't be null.
     * @throws if the file already exists throws an Exception
     */
    public void exportToCsv(File file, CSVExporterConfiguration configuration) throws Exception {
        this.configuration = configuration;

        this.exportToCsv(file);

    }


}
