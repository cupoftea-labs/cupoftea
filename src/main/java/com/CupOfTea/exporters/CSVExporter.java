package com.CupOfTea.exporters;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;

import com.CupOfTea.CupOfTea;
import com.CupOfTea.exporters.interfaces.ICSVExporter;
import com.opencsv.CSVWriter;

public class CSVExporter implements ICSVExporter {

    private final CupOfTea dataFrame;

    public CSVExporter(final CupOfTea dataFrame){
        this.dataFrame = dataFrame;
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
        FileWriter outputfile = new FileWriter(file);
        try (CSVWriter writer = new CSVWriter(outputfile)) {
            String[] header = this.dataFrame.getHeaders().toArray(String[]::new);
            writer.writeNext(header);
            
            for (HashMap<String, Object> data :  this.dataFrame.getRows()){
                ArrayList<String> dataArr = (ArrayList<String>) this.dataFrame.getHeaders().stream()
                        .map((i -> data.get(i).toString()))
                        .collect(Collectors.toList())
                        ;
                
                String[] dataStr = dataArr.toArray(String[]::new);
                writer.writeNext(dataStr);
            }
        }
    }


}
