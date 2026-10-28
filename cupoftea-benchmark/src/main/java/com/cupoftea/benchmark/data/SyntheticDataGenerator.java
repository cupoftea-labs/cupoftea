package com.cupoftea.benchmark.data;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import com.cupoftea.CupOfTea;
import com.cupoftea.MutationMode;
import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.FloatColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

import tech.tablesaw.api.BooleanColumn;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.FloatColumn;
import tech.tablesaw.api.IntColumn;
import tech.tablesaw.api.LongColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;

/**
 * Synthetic data generator for micro-, macro-, and competitive benchmarks.
 */
public final class SyntheticDataGenerator {

    private SyntheticDataGenerator() {
    }

    /**
     * Generates a columnar {@link CupOfTea} DataFrame with primitive vectors.
     */
    public static CupOfTea createNumericDataFrame(int rows) {
        int[] ids = new int[rows];
        double[] values = new double[rows];
        long[] amounts = new long[rows];
        float[] ratios = new float[rows];
        boolean[] flags = new boolean[rows];
        String[] categories = new String[rows];

        for (int i = 0; i < rows; i++) {
            ids[i] = i;
            values[i] = (i % 1000) * 0.5 + 1.25;
            amounts[i] = (long) i * 100L;
            ratios[i] = (float) ((i % 500) * 0.1f);
            flags[i] = (i % 2) == 0;
            categories[i] = "CAT_" + (i % 10);
        }

        List<ColumnVector> vectors = new ArrayList<>();
        vectors.add(new IntColumnVector("id", ids, new BitSet(), rows));
        vectors.add(new DoubleColumnVector("val", values, new BitSet(), rows));
        vectors.add(new LongColumnVector("amount", amounts, new BitSet(), rows));
        vectors.add(new FloatColumnVector("ratio", ratios, new BitSet(), rows));
        vectors.add(new BooleanColumnVector("flag", flags, new BitSet(), rows));
        vectors.add(new StringColumnVector("category", categories, new BitSet(), rows));

        return CupOfTea.fromVectors(vectors).withMutationMode(MutationMode.COPY_ON_WRITE);
    }

    /**
     * Generates a DataFrame with nullable slots for testing fillna / dropna.
     */
    public static CupOfTea createNullableDataFrame(int rows, double nullRatio) {
        int[] ids = new int[rows];
        double[] values = new double[rows];
        String[] categories = new String[rows];
        BitSet nulls = new BitSet(rows);

        int nullStep = nullRatio > 0 ? (int) Math.max(1, Math.round(1.0 / nullRatio)) : Integer.MAX_VALUE;

        for (int i = 0; i < rows; i++) {
            ids[i] = i;
            values[i] = (i % 1000) * 0.5 + 1.25;
            categories[i] = "CAT_" + (i % 10);
            if (nullStep != Integer.MAX_VALUE && (i % nullStep) == 0) {
                nulls.set(i);
            }
        }

        List<ColumnVector> vectors = List.of(
            new IntColumnVector("id", false, ids, new BitSet(), rows),
            new DoubleColumnVector("val", true, values, (BitSet) nulls.clone(), rows),
            new StringColumnVector("category", true, categories, (BitSet) nulls.clone(), rows)
        );

        return CupOfTea.fromVectors(vectors).withMutationMode(MutationMode.COPY_ON_WRITE);
    }

    /**
     * Generates a DataFrame with high or low cardinality groupings.
     */
    public static CupOfTea createGroupingDataFrame(int rows, int numGroups) {
        int[] ids = new int[rows];
        double[] values = new double[rows];
        String[] groups = new String[rows];

        for (int i = 0; i < rows; i++) {
            ids[i] = i;
            values[i] = (i % 1000) * 0.5 + 1.25;
            groups[i] = "GRP_" + (i % numGroups);
        }

        List<ColumnVector> vectors = List.of(
            new IntColumnVector("id", ids, new BitSet(), rows),
            new DoubleColumnVector("val", values, new BitSet(), rows),
            new StringColumnVector("group", groups, new BitSet(), rows)
        );

        return CupOfTea.fromVectors(vectors).withMutationMode(MutationMode.COPY_ON_WRITE);
    }

    /**
     * Generates an equivalent Tablesaw {@link Table} for competitive benchmarks.
     */
    public static Table createTablesawTable(int rows) {
        int[] ids = new int[rows];
        double[] values = new double[rows];
        long[] amounts = new long[rows];
        float[] ratios = new float[rows];
        boolean[] flags = new boolean[rows];
        String[] categories = new String[rows];

        for (int i = 0; i < rows; i++) {
            ids[i] = i;
            values[i] = (i % 1000) * 0.5 + 1.25;
            amounts[i] = (long) i * 100L;
            ratios[i] = (float) ((i % 500) * 0.1f);
            flags[i] = (i % 2) == 0;
            categories[i] = "CAT_" + (i % 10);
        }

        return Table.create("benchmark_table",
            IntColumn.create("id", ids),
            DoubleColumn.create("val", values),
            LongColumn.create("amount", amounts),
            FloatColumn.create("ratio", ratios),
            BooleanColumn.create("flag", flags),
            StringColumn.create("category", categories)
        );
    }

    /**
     * Writes synthetic rows to a temporary CSV file.
     */
    public static File generateTempCsv(int rows) throws IOException {
        File file = File.createTempFile("bench_", ".csv");
        file.deleteOnExit();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("id,val,amount,ratio,flag,category\n");
            for (int i = 0; i < rows; i++) {
                writer.write(i + "," + ((i % 1000) * 0.5 + 1.25) + "," + ((long) i * 100L) + ","
                        + ((float) ((i % 500) * 0.1f)) + "," + ((i % 2) == 0) + ",CAT_" + (i % 10) + "\n");
            }
        }
        return file;
    }

    /**
     * Writes synthetic rows to a temporary JSON file (array of JSON objects).
     */
    public static File generateTempJson(int rows) throws IOException {
        File file = File.createTempFile("bench_", ".json");
        file.deleteOnExit();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("[\n");
            for (int i = 0; i < rows; i++) {
                writer.write("  {\"id\":" + i + ",\"val\":" + ((i % 1000) * 0.5 + 1.25)
                        + ",\"amount\":" + ((long) i * 100L) + ",\"ratio\":" + ((float) ((i % 500) * 0.1f))
                        + ",\"flag\":" + ((i % 2) == 0) + ",\"category\":\"CAT_" + (i % 10) + "\"}");
                if (i < rows - 1) {
                    writer.write(",\n");
                } else {
                    writer.write("\n");
                }
            }
            writer.write("]\n");
        }
        return file;
    }

    /**
     * Checks if Hadoop UserGroupInformation is supported on the current Java runtime.
     */
    public static boolean isHadoopSupported() {
        try {
            org.apache.hadoop.security.UserGroupInformation.getCurrentUser();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Writes synthetic rows to a temporary Parquet file using CupOfTea's Parquet writer.
     * Returns null if Hadoop is not supported on this Java runtime.
     */
    public static File generateTempParquet(int rows) throws IOException {
        if (!isHadoopSupported()) {
            return null;
        }
        File file = File.createTempFile("bench_", ".parquet");
        file.delete();
        file.deleteOnExit();
        CupOfTea df = createNumericDataFrame(rows);
        df.write().parquet(file.getPath());
        return file;
    }

    /**
     * Writes synthetic rows to a temporary Excel (.xlsx) file using Apache POI SXSSFWorkbook.
     */
    public static File generateTempExcel(int rows) throws IOException {
        File file = File.createTempFile("bench_", ".xlsx");
        file.deleteOnExit();
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet("data");
            Row headerRow = sheet.createRow(0);
            String[] headers = {"id", "val", "amount", "ratio", "flag", "category"};
            for (int c = 0; c < headers.length; c++) {
                Cell cell = headerRow.createCell(c);
                cell.setCellValue(headers[c]);
            }

            for (int i = 0; i < rows; i++) {
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(i);
                row.createCell(1).setCellValue((i % 1000) * 0.5 + 1.25);
                row.createCell(2).setCellValue((long) i * 100L);
                row.createCell(3).setCellValue((float) ((i % 500) * 0.1f));
                row.createCell(4).setCellValue((i % 2) == 0);
                row.createCell(5).setCellValue("CAT_" + (i % 10));
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                workbook.write(fos);
            }
            workbook.dispose();
        }
        return file;
    }

    /**
     * Writes synthetic rows to a temporary XML file.
     */
    public static File generateTempXml(int rows) throws IOException {
        File file = File.createTempFile("bench_", ".xml");
        file.deleteOnExit();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<dataset>\n");
            for (int i = 0; i < rows; i++) {
                writer.write("  <record>\n");
                writer.write("    <id>" + i + "</id>\n");
                writer.write("    <val>" + ((i % 1000) * 0.5 + 1.25) + "</val>\n");
                writer.write("    <amount>" + ((long) i * 100L) + "</amount>\n");
                writer.write("    <ratio>" + ((float) ((i % 500) * 0.1f)) + "</ratio>\n");
                writer.write("    <flag>" + ((i % 2) == 0) + "</flag>\n");
                writer.write("    <category>CAT_" + (i % 10) + "</category>\n");
                writer.write("  </record>\n");
            }
            writer.write("</dataset>\n");
        }
        return file;
    }
}
