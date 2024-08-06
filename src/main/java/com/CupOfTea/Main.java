package com.CupOfTea;

import java.io.File;

public class Main {
    public static void main(String[] args) throws Exception {

        DataFrame df = new DataFrame();

        // JsonObject json;

        // String jsonString = """
        //     [
        //         {
        //             "car": "Mustang",
        //             "year": 2019,
        //             "color": "white"
        //         },
        //         {
        //             "car": "fusca",
        //             "year": 1970,
        //             "color": "blue"
        //         },
        //         {
        //             "car": "brasilia",
        //             "year": 1972,
        //             "color": "yellow"
        //         }
        //     ]
        // """;

        File file = new File("yaml.yaml");

        df.importFromYaml(file);

        df.print();

        df.moveColumn(0, 2);

        df.print();

        // File file = new File("csv_sample.csv");
        // df.importFromCsv(file, true);
        // df.print();
        // df.addColumn(1,"carro", "Civic");
        // df.print();

        // File file2 = new File("parquet_file2.parquet");
        // df.exportToParquet(file2);
        // df.importFromParquet(file2);

        // df.print();
        // df.addColumn(1,"carro", "Civic");
        // df.print();
        // File file_export = new File("result.csv");
        // df.exportToCsv(file_export);

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