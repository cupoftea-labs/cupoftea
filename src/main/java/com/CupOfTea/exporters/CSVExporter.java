package com.CupOfTea.exporters;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;

import com.CupOfTea.CupOfTea;
import com.CupOfTea.exporters.configurations.CSVExporterConfiguration;
import com.CupOfTea.exporters.interfaces.ICSVExporter;

public class CSVExporter implements ICSVExporter {

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
    @Override
    public void exportToCsv(File file) throws Exception {
        // ToDo String deve gerar o CSV com ""?
        // ToDo Throws an Exception in case the file already exists
        // ToDo Throws an Exception in case the file is null

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            String header = String.join(this.configuration.getSeparator(), this.dataFrame.getHeaders().stream().map(a -> a.columnName()).toList()) + this.configuration.getLineEndString();
        
            writer.write(header);
            
            for (HashMap<String, Object> data :  this.dataFrame.getRows()){
                ArrayList<String> dataArr = (ArrayList<String>) this.dataFrame.getHeaders().stream()
                        .map((i -> data.get(i.columnName()).toString()))
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
    @Override
    public void exportToCsv(File file, CSVExporterConfiguration configuration) throws Exception {
        this.configuration = configuration;

        this.exportToCsv(file);

    }


}
