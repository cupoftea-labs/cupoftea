package com.cupoftea.csv.fast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.BitSet;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.importers.configurations.CSVImporterConfiguration;
import com.cupoftea.table.Schema;
import com.cupoftea.table.vector.StringColumnVector;
import static com.cupoftea.table.Schema.DOUBLE;
import static com.cupoftea.table.Schema.INT;
import static com.cupoftea.table.Schema.STRING;

public class FastCsvReaderTest {

    private File createTempCsv(String content) throws IOException {
        File file = File.createTempFile("fast_csv_test_", ".csv");
        file.deleteOnExit();
        try (FileWriter fw = new FileWriter(file, StandardCharsets.UTF_8)) {
            fw.write(content);
        }
        return file;
    }

    @Test
    @DisplayName("FastCsvReader directly parses numeric CSV into primitive columnar vectors")
    void testFastCsvReaderDirectNumericParsing() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("id,temperature,station\n");
        for (int i = 0; i < 50_000; i++) {
            sb.append(i).append(",").append(String.format(Locale.ROOT, "%.2f", (i % 100) * 0.5)).append(",Station_").append(i % 10).append("\n");
        }
        File file = createTempCsv(sb.toString());

        CSVImporterConfiguration config = new CSVImporterConfiguration()
            .setFastEngine(true)
            .setInferNumericTypes(true);

        CupOfTea df = new CupOfTea();
        df.importFromCsv(file, config);

        assertThat(df.size()).isEqualTo(50_000);
        assertThat(df.getColumnHeader("id").columnType()).isEqualTo(Integer.class);
        assertThat(df.getColumnHeader("temperature").columnType()).isEqualTo(Double.class);
        assertThat(df.getColumnHeader("station").columnType()).isEqualTo(String.class);

        assertThat(df.getRow(0).get("id")).isEqualTo(0);
        assertThat(df.getRow(0).get("temperature")).isEqualTo(0.0);
        assertThat(df.getRow(0).get("station")).isEqualTo("Station_0");
    }

    @Test
    @DisplayName("FastCsvReader works with explicit Schema")
    void testFastCsvReaderWithExplicitSchema() throws IOException {
        String content = "city,pop,lat\n"
            + "Tokyo,37000000,35.6762\n"
            + "Delhi,32000000,28.7041\n"
            + "Shanghai,28000000,31.2304\n";
        File file = createTempCsv(content);

        Schema schema = Schema.of("city", STRING, "pop", INT, "lat", DOUBLE);
        CupOfTea df = new CupOfTea().importFromCsv(file, schema);

        assertThat(df.size()).isEqualTo(3);
        assertThat(df.getColumnHeader("pop").columnType()).isEqualTo(Integer.class);
        assertThat(df.getColumnHeader("lat").columnType()).isEqualTo(Double.class);
        assertThat(df.getRow(0).get("city")).isEqualTo("Tokyo");
        assertThat(df.getRow(0).get("pop")).isEqualTo(37000000);
        assertThat(df.getRow(0).get("lat")).isEqualTo(35.6762);
    }

    @Test
    @DisplayName("FastCsvReader handles missing values and empty cells")
    void testFastCsvReaderMissingValues() throws IOException {
        String content = "a,b,c\n"
            + "1,1.5,foo\n"
            + ",NA,bar\n"
            + "3,3.5,\n";
        File file = createTempCsv(content);

        CSVImporterConfiguration config = new CSVImporterConfiguration()
            .setFastEngine(true)
            .setInferNumericTypes(true);

        CupOfTea df = new CupOfTea();
        df.importFromCsv(file, config);

        assertThat(df.size()).isEqualTo(3);
        assertThat(df.getRow(1).get("a")).isNull();
        assertThat(df.getRow(1).get("b")).isNull();
        assertThat(df.getRow(2).get("c")).isNull();
    }

    @Test
    @DisplayName("FastCsvReader streams raw UTF-8 chunk bytes directly into StringColumnVector across parallel chunks")
    void testDirectUtf8ByteStreamingMultiChunk() throws IOException {
        int rows = 100_000;
        StringBuilder sb = new StringBuilder(rows * 24);
        sb.append("station;temperature;region\n");
        String[] stations = {
            "Hamburg", "Bulawayo", "Palembang", "St. John's", "Cracow",
            "Bridgetown", "Istanbul", "Roseau", "Conakry", "Münster",
            "Zürich", "São Paulo", "Tokyo", "Nairobi", "New York"
        };
        for (int i = 0; i < rows; i++) {
            String st = (i % 17 == 0) ? "NA" : stations[i % stations.length];
            String rg = (i % 23 == 0) ? "\"\"" : ("\"Reg_" + (i % 8) + "\"");
            sb.append(st).append(';')
              .append(String.format(Locale.ROOT, "%.1f", (i % 400) * 0.1 - 10.0)).append(';')
              .append(rg).append('\n');
        }
        File file = createTempCsv(sb.toString());
        assertThat(file.length()).isGreaterThan(1024 * 1024);

        for (boolean simd : new boolean[]{true, false}) {
            CSVImporterConfiguration config = new CSVImporterConfiguration()
                .setDelimiter(';')
                .setFastEngine(true)
                .setSimdEnabled(simd)
                .setPreserveEmptyStrings(true)
                .setInferNumericTypes(true);

            CupOfTea df = new CupOfTea();
            df.importFromCsv(file, config);

            assertThat(df.size()).isEqualTo(rows);
            StringColumnVector stationVec = (StringColumnVector) df.getDataFrame().getColumnVector("station");
            StringColumnVector regionVec = (StringColumnVector) df.getDataFrame().getColumnVector("region");

            assertThat(stationVec.getValuesCount()).isGreaterThan(0);
            assertThat(stationVec.getOffsets().length).isGreaterThanOrEqualTo(rows + 1);

            for (int r = 0; r < rows; r += 97) {
                if (r % 17 == 0) {
                    assertThat(stationVec.isNull(r)).isTrue();
                    assertThat(stationVec.getString(r)).isNull();
                } else {
                    assertThat(stationVec.isNull(r)).isFalse();
                    assertThat(stationVec.getString(r)).isEqualTo(stations[r % stations.length]);
                }
                if (r % 23 == 0) {
                    assertThat(regionVec.isNull(r)).isFalse();
                    assertThat(regionVec.getString(r)).isEqualTo("");
                } else {
                    assertThat(regionVec.getString(r)).isEqualTo("Reg_" + (r % 8));
                }
            }
        }
    }

    @Test
    @DisplayName("SimdCsvScanner.parseChunkSimdMapped streams across 1MB window boundaries accurately")
    void testMappedWindowBoundaryStreaming() throws IOException {
        int rows = 120_000;
        StringBuilder sb = new StringBuilder(rows * 26);
        for (int i = 0; i < rows; i++) {
            sb.append("Station_Name_").append(i % 500)
              .append(",\"Quoted_Value_").append(i % 100).append("\",")
              .append(i)
              .append("\r\n");
        }
        File file = createTempCsv(sb.toString());
        int fileLen = (int) file.length();
        assertThat(fileLen).isGreaterThan(2 * 1024 * 1024 + 100_000);

        CSVImporterConfiguration config = new CSVImporterConfiguration();
        Class<?>[] colTypes = new Class<?>[]{String.class, String.class, Integer.class};
        byte[] colTypeIds = new byte[]{SimdCsvScanner.TYPE_STRING, SimdCsvScanner.TYPE_STRING, SimdCsvScanner.TYPE_INT};

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             FileChannel ch = raf.getChannel()) {
            MappedByteBuffer mapped = ch.map(FileChannel.MapMode.READ_ONLY, 0, fileLen);
            FastCsvReader.ChunkResult res = SimdCsvScanner.parseChunkSimdMapped(
                    mapped, 0, fileLen, (byte) ',', (byte) '"', (byte) '\\',
                    3, colTypes, colTypeIds, StandardCharsets.UTF_8, config);

            assertThat(res.rowCount).isEqualTo(rows);
            FastCsvReader.StringChunkBuffer col0 = (FastCsvReader.StringChunkBuffer) res.columnData[0];
            FastCsvReader.StringChunkBuffer col1 = (FastCsvReader.StringChunkBuffer) res.columnData[1];
            int[] col2 = (int[]) res.columnData[2];

            StringColumnVector v0 = new StringColumnVector("s0", true, col0.values, col0.valuesCount, col0.offsets, res.nullBitsets[0], rows);
            StringColumnVector v1 = new StringColumnVector("s1", true, col1.values, col1.valuesCount, col1.offsets, res.nullBitsets[1], rows);

            for (int r = 0; r < rows; r += 113) {
                assertThat(v0.getString(r)).isEqualTo("Station_Name_" + (r % 500));
                assertThat(v1.getString(r)).isEqualTo("Quoted_Value_" + (r % 100));
                assertThat(col2[r]).isEqualTo(r);
            }
            assertThat(v0.getString(rows - 1)).isEqualTo("Station_Name_" + ((rows - 1) % 500));
            assertThat(col2[rows - 1]).isEqualTo(rows - 1);
        }
    }

    @Test
    @DisplayName("FastCsvReader transcodes ISO-8859-1 non-ASCII bytes into UTF-8 StringColumnVector")
    void testIso88591TranscodingToUtf8Vector() throws IOException {
        String csv = "city,temp\nZürich,12.5\nSão Paulo,26.0\nMünster,9.5\nPlainASCII,15.0\n";
        File file = File.createTempFile("fast_csv_iso_", ".csv");
        file.deleteOnExit();
        Files.write(file.toPath(), csv.getBytes(StandardCharsets.ISO_8859_1));

        for (boolean simd : new boolean[]{true, false}) {
            CSVImporterConfiguration config = new CSVImporterConfiguration()
                .setCharset(StandardCharsets.ISO_8859_1)
                .setFastEngine(true)
                .setSimdEnabled(simd)
                .setInferNumericTypes(true);

            CupOfTea df = new CupOfTea();
            df.importFromCsv(file, config);

            assertThat(df.size()).isEqualTo(4);
            assertThat(df.getRow(0).get("city")).isEqualTo("Zürich");
            assertThat(df.getRow(1).get("city")).isEqualTo("São Paulo");
            assertThat(df.getRow(2).get("city")).isEqualTo("Münster");
            assertThat(df.getRow(3).get("city")).isEqualTo("PlainASCII");
        }
    }
}
