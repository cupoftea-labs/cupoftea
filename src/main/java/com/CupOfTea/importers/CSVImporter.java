package com.CupOfTea.importers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import com.CupOfTea.CupOfTea;
import com.CupOfTea.importers.interfaces.ICSVImporter;

public class CSVImporter implements ICSVImporter {

    private final CupOfTea dataFrame;

    public CSVImporter(final CupOfTea dataFrame){
        this.dataFrame = dataFrame;
    }


    /**
     * Dumps the data from the CSV file into a CupOfTea data frame.
     * Any previous data in the data frame will be lost!
     * 
     * All the lines of the CSV must have the same amount of columns, otherwise an Exception will be thrown.
     * 
     * @param file path to the CSV file. Can't be null.
     * @param hasHeaders if true, the first line of the CSV will be used for the headers. If False, an auto generated column name will be used.
     */
    @Override
    public void importFromCsv(File file, boolean hasHeaders) throws Exception {
        // ToDo importar numeros no formato de numero
        // ToDo verificar o tamanho de cada linha, nao podem ter linhas de tamanho diferente
        // ToDo hasHeaders false criar cabeçalho com valores sequenciais
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            if(hasHeaders){
                line = br.readLine();
                String[] values = line.split(",");
                this.dataFrame.setHeaders(new ArrayList<>(Arrays.asList(values)));
            }
            while ((line = br.readLine()) != null) {
                Object[] values = line.split(",");
                HashMap<String, Object> row = new HashMap<>();
                for(int i = 0; i < this.dataFrame.getHeaders().size(); i++){
                    row.put(this.dataFrame.getHeaders().get(i).columnName(), values[i]);
                }
                this.dataFrame.addRow(row);
            }
        }
    }

}
