package com.CupOfTea.exporters;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;

import com.CupOfTea.CupOfTea;
import com.CupOfTea.exporters.interfaces.ICSVExporter;
import com.opencsv.CSVWriter;

public class CSVExporter implements ICSVExporter {

    private CupOfTea dataFrame;

    public CSVExporter(CupOfTea dataFrame){
        this.dataFrame = dataFrame;
    }

    @Override
    public void exportToCsv(File file) {
        // ToDo String deve gerar o CSV com ""?
        // first create file object for file placed at location
        // specified by filepath
        try {
            FileWriter outputfile = new FileWriter(file);
            CSVWriter writer = new CSVWriter(outputfile);

            String[] header = this.dataFrame.getHeaders().toArray(new String[0]);
            writer.writeNext(header);

            for (HashMap<String, Object> data :  this.dataFrame.getRows()){
                ArrayList<String> dataArr = (ArrayList<String>) this.dataFrame.getHeaders().stream()
                                                .map((i -> data.get(i).toString()))
                                                .collect(Collectors.toList())
                                                ;

                String[] dataStr = dataArr.toArray(String[]::new);
                writer.writeNext(dataStr);
            }

            writer.close();
        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }


}
