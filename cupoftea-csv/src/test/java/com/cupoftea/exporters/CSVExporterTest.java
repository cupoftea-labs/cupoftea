package com.cupoftea.exporters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.nio.file.Files;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exporters.configurations.CSVExporterConfiguration;
import static com.cupoftea.exporters.configurations.CSVExporterConfiguration.HeadersPlacement.BOTTOM;
import static com.cupoftea.exporters.configurations.CSVExporterConfiguration.HeadersPlacement.NO_HEADER;
import static com.cupoftea.exporters.configurations.CSVExporterConfiguration.HeadersPlacement.TOP;
import static com.cupoftea.exporters.configurations.CSVExporterConfiguration.QuoteBehaviour.ALL;
import static com.cupoftea.exporters.configurations.CSVExporterConfiguration.QuoteBehaviour.ALL_BUT_NUMBERS;
import static com.cupoftea.exporters.configurations.CSVExporterConfiguration.QuoteBehaviour.NONE;
import com.cupoftea.table.DataFrame;
import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.FloatColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

public class CSVExporterTest {

    @Test
    @DisplayName("Should export standard DataFrame to CSV using default configuration")
    void testExportDefault() throws Exception {
        CupOfTea df = new CupOfTea();
        df.addColumn("id", Integer.class);
        df.addColumn("name", String.class);
        df.addColumn("val", Double.class);

        Map<String, Object> r1 = new HashMap<>();
        r1.put("id", 1);
        r1.put("name", "alpha");
        r1.put("val", 10.5);
        df.addRow(r1);

        Map<String, Object> r2 = new HashMap<>();
        r2.put("id", 2);
        r2.put("name", "beta");
        r2.put("val", 20.25);
        df.addRow(r2);

        File temp = File.createTempFile("test_csv_export", ".csv");
        temp.deleteOnExit();

        df.write().csv(temp);
        assertThat(temp.exists()).isTrue();

        List<String> lines = Files.readAllLines(temp.toPath());
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).isEqualTo("id,name,val");
        assertThat(lines.get(1)).isEqualTo("1,alpha,10.5");
        assertThat(lines.get(2)).isEqualTo("2,beta,20.25");
    }

    @Test
    @DisplayName("Should export with quoting behaviours")
    void testExportQuoting() throws Exception {
        CupOfTea df = new CupOfTea();
        df.addColumn("id", Integer.class);
        df.addColumn("name", String.class);

        Map<String, Object> r1 = new HashMap<>();
        r1.put("id", 100);
        r1.put("name", "foo");
        df.addRow(r1);

        File temp = File.createTempFile("test_csv_quote", ".csv");
        temp.deleteOnExit();

        CSVExporterConfiguration config = new CSVExporterConfiguration()
                .setQuoteString("\"")
                .setQuoteBehaviour(ALL);

        new CSVExporter(df).exportToCsv(temp, config);
        List<String> lines = Files.readAllLines(temp.toPath());
        assertThat(lines.get(0)).isEqualTo("\"id\",\"name\"");
        assertThat(lines.get(1)).isEqualTo("\"100\",\"foo\"");

        // ALL_BUT_NUMBERS
        config.setQuoteBehaviour(ALL_BUT_NUMBERS);
        new CSVExporter(df).exportToCsv(temp, config);
        lines = Files.readAllLines(temp.toPath());
        assertThat(lines.get(0)).isEqualTo("\"id\",\"name\"");
        assertThat(lines.get(1)).isEqualTo("100,\"foo\"");
    }

    @Test
    @DisplayName("Should handle headers placement options (TOP, BOTTOM, NO_HEADER)")
    void testHeadersPlacement() throws Exception {
        CupOfTea df = new CupOfTea();
        df.addColumn("v", String.class);
        Map<String, Object> r = new HashMap<>();
        r.put("v", "data");
        df.addRow(r);

        File temp = File.createTempFile("test_csv_placement", ".csv");
        temp.deleteOnExit();

        // NO_HEADER
        CSVExporterConfiguration cfg = new CSVExporterConfiguration().setHeadersPlacement(NO_HEADER);
        new CSVExporter(df).exportToCsv(temp, cfg);
        List<String> lines = Files.readAllLines(temp.toPath());
        assertThat(lines).containsExactly("data");

        // BOTTOM
        cfg.setHeadersPlacement(BOTTOM);
        new CSVExporter(df).exportToCsv(temp, cfg);
        lines = Files.readAllLines(temp.toPath());
        assertThat(lines).containsExactly("data", "v");
    }

    @Test
    @DisplayName("Should handle nulls correctly")
    void testNullValues() throws Exception {
        CupOfTea df = new CupOfTea();
        df.addColumn("id", Integer.class);
        df.addColumn("comment", String.class);

        Map<String, Object> r = new HashMap<>();
        r.put("id", 1);
        r.put("comment", null);
        df.addRow(r);

        File temp = File.createTempFile("test_csv_null", ".csv");
        temp.deleteOnExit();

        CSVExporterConfiguration cfg = new CSVExporterConfiguration().setStringNull("N/A");
        new CSVExporter(df).exportToCsv(temp, cfg);
        List<String> lines = Files.readAllLines(temp.toPath());
        assertThat(lines.get(1)).isEqualTo("1,N/A");
    }

    @Test
    @DisplayName("Should export large DataFrame in parallel chunked egress matching sequential output")
    void testParallelChunkedEgressFidelity() throws Exception {
        int n = 40_000;
        int[] ids = new int[n];
        long[] timestamps = new long[n];
        double[] vals = new double[n];
        float[] ratios = new float[n];
        boolean[] flags = new boolean[n];
        String[] labels = new String[n];

        BitSet nulls = new BitSet();
        BitSet labelNulls = new BitSet();
        for (int i = 0; i < n; i++) {
            ids[i] = i;
            timestamps[i] = 1700000000000L + i;
            vals[i] = i * 1.5;
            ratios[i] = i * 0.1f;
            flags[i] = (i % 2) == 0;
            if (i % 7 == 0) {
                labelNulls.set(i);
                labels[i] = null;
            } else {
                labels[i] = "item_" + i;
            }
        }

        DataFrame innerDf = new DataFrame(List.of(
            new IntColumnVector("id", false, ids, nulls, n),
            new LongColumnVector("ts", false, timestamps, nulls, n),
            new DoubleColumnVector("val", false, vals, nulls, n),
            new FloatColumnVector("ratio", false, ratios, nulls, n),
            new BooleanColumnVector("flag", false, flags, nulls, n),
            new StringColumnVector("label", true, labels, labelNulls, n)
        ));
        CupOfTea df = new CupOfTea(innerDf);

        File tempPar = File.createTempFile("test_csv_par", ".csv");
        tempPar.deleteOnExit();

        df.write().csv(tempPar);
        assertThat(tempPar.exists()).isTrue();
        assertThat(tempPar.length()).isGreaterThan(0);

        List<String> lines = Files.readAllLines(tempPar.toPath());
        assertThat(lines).hasSize(n + 1);
        assertThat(lines.get(0)).isEqualTo("id,ts,val,ratio,flag,label");
        assertThat(lines.get(1)).isEqualTo("0,1700000000000,0.0,0.0,true,null");
        assertThat(lines.get(2)).isEqualTo("1,1700000000001,1.5,0.1,false,item_1");
        assertThat(lines.get(n)).isEqualTo((n - 1) + "," + (1700000000000L + n - 1) + "," + ((n - 1) * 1.5) + "," + ((n - 1) * 0.1f) + "," + (((n - 1) % 2) == 0) + "," + ((n - 1) % 7 == 0 ? "null" : "item_" + (n - 1)));
    }

    @Test
    @DisplayName("Should throw ExportException when file is null")
    void testNullFile() {
        CupOfTea df = new CupOfTea();
        CSVExporter exporter = new CSVExporter(df);
        assertThatThrownBy(() -> exporter.exportToCsv(null)).isInstanceOf(ExportException.class);
    }
}
