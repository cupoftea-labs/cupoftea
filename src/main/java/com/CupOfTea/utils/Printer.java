package com.CupOfTea.utils;

import java.util.ArrayList;

import com.CupOfTea.CupOfTea;
import com.CupOfTea.utils.interfaces.IDataFrame;
import com.CupOfTea.utils.interfaces.IPrinter;

public class Printer implements IPrinter {

    
    private CupOfTea dataFrame;

    public Printer(CupOfTea dataFrame){
        this.dataFrame = dataFrame;
    }


    // ToDo ajustar tamanho das colunas
    // ToDo refatorar esse metodo
    // ToDo criar sobrecarga para escolhar quantas linhas printar
    // ToDo adicionar ao lado do nome da coluna o formato da coluna
    // ToDo melhorar descrição da tabela

    @Override
    public void print() {
        // ToDo verificar qual logger usar
        System.out.println("Showing only the first 10 registers");
        // ToDo printar menos colunas
        System.out.print('\u250C');
        System.out.print(String.valueOf('\u2500').repeat((22)));

        ArrayList<String> headers = this.dataFrame.getHeaders();

        for(int i = 0; i < headers.size() - 1; i++) {
            System.out.print('\u252C');
            System.out.print(String.valueOf('\u2500').repeat((22)));
        }
        System.out.println('\u2510');
        System.out.print('\u2502' + " ");
        headers.forEach((header) -> {
            int length = header.length();
            int spaces = 21 - length ;
            if(spaces <= 0){
                spaces = 1;
                header = header.substring(0,17) + "...";
            }
            String space = " ".repeat(spaces);
            System.out.print(header + space + '\u2502' + " ");
        });
        System.out.println();
        System.out.print('\u251C');
        System.out.print(String.valueOf('\u2500').repeat((22)));
        for(int i = 0; i < headers.size() - 1; i++) {
            System.out.print('\u253C');
            System.out.print(String.valueOf('\u2500').repeat((22)));
        }
        System.out.println('\u2524');
        for (int i = 0; i < 10 && i < dataFrame.size(); i++) {
            System.out.print('\u2502'+" ");

            this.dataFrame.getRow(i).forEach((rowColumn) -> {
                String rowData = rowColumn == null ? "null" : rowColumn.toString();
                int length = rowData.length();
                int spaces = 21 - length ;
                if(spaces <= 0){
                    spaces = 1;
                    rowColumn = rowData.substring(0,17) + "...";
                }
                String space = " ".repeat(spaces);
                System.out.print(rowColumn + space + '\u2502'+" ");
            });
            System.out.println();
        }
        System.out.print('\u2514');
        System.out.print(String.valueOf('\u2500').repeat((22)));
        for(int i = 0; i < headers.size() - 1; i++) {
            System.out.print('\u2534');
            System.out.print(String.valueOf('\u2500').repeat((22)));
        }
        System.out.println('\u2518');
    }

}
