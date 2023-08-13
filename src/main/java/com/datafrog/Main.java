package com.datafrog;

import java.io.File;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {

        DataFrame df = new DataFrame();
        File file = new File("I:\\Users\\filip\\DataFrog\\csv_sample.csv");
        df.importFromCsv(file, true);
        df.print();
        df.addColumn(1,"carro", 1);
        df.print();

        File file_export = new File("I:\\Users\\filip\\DataFrog\\csv_sample2.csv");
        df.exportToCsv(file_export);

//        ArrayList<String> headers = new ArrayList<>();
//        headers.add("a");
//        headers.add("b");
//        headers.add("c");
//
//        System.out.println(headers);
//        headers.add(3,"1");
//        System.out.println(headers);

//        ArrayList<Object> data = new ArrayList<>();
//        for (int i = 0; i< 5; i++) {
//            df.addColumn("Coluna"+i);
//        }
//        for (int i = 0; i< 5; i++) {
//            data.add("Hello World!"+i);
//        }
//        for (int i = 0; i< 1000; i++) {
//            df.addRow(data);
//        }
//
//
//        System.out.println(df.getHeaders());
//        System.out.println(df.getDataFrame());
//
//        df.addColumn("Coluna3");
//        df.addColumn("Coluna4");
//
//        System.out.println(df.getHeaders());
//        System.out.println(df.getDataFrame());
//
//        df.removeColumn("Coluna1");
//
//        System.out.println(df.getHeaders());
//        System.out.println(df.getDataFrame());
//
//        df.removeColumn(1);
//        df.removeRow(100);
//
//        df.print();

    }
}