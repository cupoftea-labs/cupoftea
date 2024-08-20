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

    private CupOfTea dataFrame;

    public CSVImporter(CupOfTea dataFrame){
        this.dataFrame = dataFrame;
    }

    @Override
    public void importFromCsv(File file, boolean hasHeaders) throws Exception {
        // ToDo importar numeros no formato de numero
        // ToDo verificar o tamanho de cada linha, nao podem ter linhas de tamanho diferente
        // ToDo hasHeaders false criar cabeçalho com valores nulo
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
                    row.put(this.dataFrame.getHeaders().get(i), values[i]);
                }
                this.dataFrame.addRow(row);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
