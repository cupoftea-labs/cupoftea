package com.cupoftea.benchmark.data;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import com.cupoftea.CupOfTea;
import com.cupoftea.MutationMode;
import com.cupoftea.table.Column;
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
 * Deterministic synthetic data generator for micro-, macro-, 1BRC, and Polars comparative benchmarks.
 * Uses a fixed seed ({@link #DEFAULT_SEED}) to ensure byte-for-byte reproducibility across JVM and Polars runs.
 */
public final class SyntheticDataGenerator {

    /**
     * Fixed PRNG seed for deterministic benchmark dataset generation.
     */
    public static final long DEFAULT_SEED = 0xCAFEBABE12345678L;

    private static final String[] SAMPLE_STATIONS = {
        "A Coruña", "A Yun Pa", "Aabenraa", "Aachen", "Aadorf", "Aalborg", "Aalen", "Aalsmeer",
        "Aarau", "Aarschot", "Abadan", "Abakan", "Abancay", "Abashiri", "Abbeville", "Abbotsford",
        "Abeokuta", "Aberdeen", "Abha", "Abidjan", "Abiko", "Abilene", "Abingdon", "Abomey",
        "Abu Dhabi", "Abuja", "Acapulco", "Acarigua", "Accra", "Achinsk", "Adana", "Addis Ababa",
        "Adelaide", "Aden", "Adiyaman", "Adler", "Afyonkarahisar", "Agadir", "Agana", "Agartala",
        "Agboville", "Agen", "Agra", "Agrigento", "Aguascalientes", "Ahmedabad", "Ahvaz", "Aachen-Nord",
        "Aix-en-Provence", "Aizawl", "Ajaccio", "Ajman", "Ajmer", "Akashi", "Akita", "Akola",
        "Akron", "Aksaray", "Aksu", "Aktau", "Aktobe", "Aku", "Akure", "Akureyri",
        "Al Ain", "Al Amarah", "Al Arish", "Al Basrah", "Al Fallujah", "Al Fayyum", "Al Hasakah", "Al Hillah",
        "Berlin", "Paris", "London", "Tokyo", "New York", "São Paulo", "Zürich", "München",
        "København", "Göteborg", "Malmö", "Reykjavík", "Wrocław", "Gdańsk", "Kraków", "Łódź",
        "İstanbul", "İzmir", "Bursa", "Antalya", "Moskva", "Sankt-Peterburg", "Novosibirsk", "Yekaterinburg",
        "Beijing", "Shanghai", "Guangzhou", "Shenzhen", "Seoul", "Busan", "Incheon", "Daegu"
    };

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
     * Generates a rich mixed-analytics DataFrame (10 columns) with deterministic PRNG seed,
     * supporting low-cardinality, high-cardinality, skewed Zipf keys, fixed-point & float metrics,
     * booleans, ISO timestamps, short SSO strings, and quoted strings with configurable null ratio.
     */
    public static CupOfTea createMixedAnalyticsDataFrame(int rows, double nullRatio) {
        SplittableRandom rng = new SplittableRandom(DEFAULT_SEED);
        int highCard = Math.max(10, rows / 5);

        int[] idCol = new int[rows];
        long[] tsEpochCol = new long[rows];
        int[] kZipfCol = new int[rows];
        double[] valFixedCol = new double[rows];
        double[] valFloatCol = new double[rows];
        boolean[] flagCol = new boolean[rows];
        String[] kLowCol = new String[rows];
        String[] kHighCol = new String[rows];
        String[] shortStrCol = new String[rows];
        String[] commentCol = new String[rows];

        String[] lowPool = new String[100];
        for (int i = 0; i < 100; i++) {
            lowPool[i] = String.format(Locale.ROOT, "REG_%03d", i);
        }

        String[] shortPool = new String[64];
        for (int i = 0; i < 64; i++) {
            shortPool[i] = String.format(Locale.ROOT, "SSO_KEY_%04d", i);
        }

        BitSet nullMask = new BitSet(rows);
        int nullThreshold = (int) Math.round(nullRatio * 1000.0);

        for (int i = 0; i < rows; i++) {
            idCol[i] = i;
            tsEpochCol[i] = 1700000000000L + (long) i * 1000L;
            // Skewed distribution where small bucket indices appear much more frequently
            int u = rng.nextInt(1000);
            kZipfCol[i] = (u < 500) ? (u % 10) : (u < 850 ? (u % 100) : u);
            valFixedCol[i] = (rng.nextInt(-5000, 50000)) / 10.0;
            valFloatCol[i] = rng.nextDouble(-1000.0, 10000.0);
            flagCol[i] = (i & 1) == 0;
            kLowCol[i] = lowPool[i % 100];
            kHighCol[i] = "USR_" + (i % highCard);
            shortStrCol[i] = shortPool[i & 63];
            commentCol[i] = (i % 10 == 0)
                    ? "note, with; delimiter " + (i % 50)
                    : "standard_payload_" + (i % 200);

            if (nullThreshold > 0 && rng.nextInt(1000) < nullThreshold) {
                nullMask.set(i);
            }
        }

        boolean nullable = nullRatio > 0.0;
        List<ColumnVector> vectors = List.of(
            new IntColumnVector("id", false, idCol, new BitSet(), rows),
            new LongColumnVector("ts_epoch", false, tsEpochCol, new BitSet(), rows),
            new IntColumnVector("k_zipf", nullable, kZipfCol, (BitSet) nullMask.clone(), rows),
            new DoubleColumnVector("val_fixed", nullable, valFixedCol, (BitSet) nullMask.clone(), rows),
            new DoubleColumnVector("val_float", nullable, valFloatCol, (BitSet) nullMask.clone(), rows),
            new BooleanColumnVector("flag", false, flagCol, new BitSet(), rows),
            new StringColumnVector("k_low", false, kLowCol, new BitSet(), rows),
            new StringColumnVector("k_high", false, kHighCol, new BitSet(), rows),
            new StringColumnVector("short_str", nullable, shortStrCol, (BitSet) nullMask.clone(), rows),
            new StringColumnVector("comment", nullable, commentCol, (BitSet) nullMask.clone(), rows)
        );

        return CupOfTea.fromVectors(vectors).withMutationMode(MutationMode.COPY_ON_WRITE);
    }

    /**
     * Generates a deterministic left-side DataFrame for Join benchmarks (Int & UTF-8 String keys).
     */
    public static CupOfTea createJoinLeftDataFrame(int rows, int keyCardinality) {
        SplittableRandom rng = new SplittableRandom(DEFAULT_SEED ^ 0x1111L);
        int card = Math.max(1, keyCardinality);

        int[] intKeys = new int[rows];
        String[] strKeys = new String[rows];
        double[] leftMetrics = new double[rows];

        String[] keyPool = new String[Math.min(card, 100_000)];
        for (int i = 0; i < keyPool.length; i++) {
            keyPool[i] = String.format(Locale.ROOT, "KEY_%08d", i);
        }

        for (int i = 0; i < rows; i++) {
            int k = i % card;
            intKeys[i] = k;
            strKeys[i] = (k < keyPool.length) ? keyPool[k] : ("KEY_" + k);
            leftMetrics[i] = (rng.nextInt(1, 10000)) * 0.25;
        }

        List<ColumnVector> vectors = List.of(
            new IntColumnVector("key_int", intKeys, new BitSet(), rows),
            new StringColumnVector("key_str", strKeys, new BitSet(), rows),
            new DoubleColumnVector("left_val", leftMetrics, new BitSet(), rows)
        );
        return CupOfTea.fromVectors(vectors).withMutationMode(MutationMode.COPY_ON_WRITE);
    }

    /**
     * Generates a deterministic right-side DataFrame for Join benchmarks supporting 1:1, 1:N, and N:M cardinalities.
     *
     * @param rows           number of rows in right table
     * @param keyCardinality number of distinct keys in left table
     * @param mode           "1:1" (unique keys), "1:N" (unique dimension keys), or "N:M" (duplicate keys on both sides)
     */
    public static CupOfTea createJoinRightDataFrame(int rows, int keyCardinality, String mode) {
        SplittableRandom rng = new SplittableRandom(DEFAULT_SEED ^ 0x2222L);
        int card = Math.max(1, keyCardinality);

        int[] intKeys = new int[rows];
        String[] strKeys = new String[rows];
        double[] rightMetrics = new double[rows];

        String[] keyPool = new String[Math.min(card, 100_000)];
        for (int i = 0; i < keyPool.length; i++) {
            keyPool[i] = String.format(Locale.ROOT, "KEY_%08d", i);
        }

        boolean duplicateRight = "N:M".equalsIgnoreCase(mode);
        int effectiveCard = duplicateRight ? Math.max(1, rows / 4) : Math.max(rows, card);

        for (int i = 0; i < rows; i++) {
            // 90% match rate against left keys, 10% unmatched keys for Left Join testing
            int rawKey = duplicateRight ? (i % effectiveCard) : i;
            int k = (i % 10 == 9) ? (card + rawKey) : (rawKey % card);
            intKeys[i] = k;
            strKeys[i] = (k < keyPool.length) ? keyPool[k] : String.format(Locale.ROOT, "KEY_%08d", k);
            rightMetrics[i] = (rng.nextInt(1, 5000)) * 0.5;
        }

        List<ColumnVector> vectors = List.of(
            new IntColumnVector("key_int", intKeys, new BitSet(), rows),
            new StringColumnVector("key_str", strKeys, new BitSet(), rows),
            new DoubleColumnVector("right_val", rightMetrics, new BitSet(), rows)
        );
        return CupOfTea.fromVectors(vectors).withMutationMode(MutationMode.COPY_ON_WRITE);
    }

    /**
     * Writes a deterministic mixed-types CSV or TSV file (with integers, floats, timestamps,
     * quoted strings with embedded delimiters, and configurable nulls).
     */
    public static File generateTempMixedCsv(int rows, char delimiter, double nullRatio) throws IOException {
        String ext = (delimiter == '\t') ? ".tsv" : ".csv";
        File file = File.createTempFile("bench_mixed_", ext);
        file.deleteOnExit();
        writeMixedDelimitedFile(file, rows, delimiter, nullRatio);
        return file;
    }

    /**
     * Writes the deterministic mixed-types delimited dataset to a target file.
     */
    public static void writeMixedDelimitedFile(File file, int rows, char delimiter, double nullRatio) throws IOException {
        SplittableRandom rng = new SplittableRandom(DEFAULT_SEED);
        int highCard = Math.max(10, rows / 5);
        int nullThreshold = (int) Math.round(nullRatio * 1000.0);

        String[] lowPool = new String[100];
        for (int i = 0; i < 100; i++) {
            lowPool[i] = String.format(Locale.ROOT, "REG_%03d", i);
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8), 65536)) {
            writer.write("id" + delimiter + "ts_epoch" + delimiter + "k_zipf" + delimiter
                    + "val_fixed" + delimiter + "val_float" + delimiter + "flag" + delimiter
                    + "k_low" + delimiter + "k_high" + delimiter + "comment\n");

            StringBuilder sb = new StringBuilder(160);
            for (int i = 0; i < rows; i++) {
                sb.setLength(0);
                boolean isNullRow = (nullThreshold > 0 && rng.nextInt(1000) < nullThreshold);
                int u = rng.nextInt(1000);
                int zipf = (u < 500) ? (u % 10) : (u < 850 ? (u % 100) : u);
                int fixedScaled = rng.nextInt(-5000, 50000);
                double floatVal = rng.nextDouble(-1000.0, 10000.0);

                sb.append(i).append(delimiter);
                sb.append(1700000000000L + (long) i * 1000L).append(delimiter);
                if (isNullRow) {
                    sb.append(delimiter);
                } else {
                    sb.append(zipf).append(delimiter);
                }
                if (isNullRow) {
                    sb.append(delimiter);
                } else {
                    sb.append(fixedScaled / 10).append('.').append(Math.abs(fixedScaled % 10)).append(delimiter);
                }
                if (isNullRow) {
                    sb.append(delimiter);
                } else {
                    sb.append(String.format(Locale.ROOT, "%.4f", floatVal)).append(delimiter);
                }
                sb.append((i & 1) == 0 ? "true" : "false").append(delimiter);
                sb.append(lowPool[i % 100]).append(delimiter);
                sb.append("USR_").append(i % highCard).append(delimiter);
                if (isNullRow) {
                    sb.append('\n');
                } else if (i % 10 == 0) {
                    sb.append("\"quoted").append(delimiter).append(" text ").append(i % 50).append("\"\n");
                } else {
                    sb.append("payload_").append(i % 200).append('\n');
                }
                writer.write(sb.toString());
            }
        }
    }

    /**
     * Generates a temporary 1BRC (One Billion Row Challenge) format file ({@code <station>;<temp>\n}).
     *
     * @param rows                     number of rows to generate
     * @param decimalScale             1 for standard 1BRC (e.g. {@code 20.5}), 2 for 2 decimals (e.g. {@code 20.53})
     * @param injectHighPrecisionEvery if {@code > 0}, injects a 3-decimal value (e.g. {@code 12.345}) every N rows
     *                                 to test dynamic promotion from scaled integer to double mode
     */
    public static File generateTemp1BrcFile(int rows, int decimalScale, int injectHighPrecisionEvery) throws IOException {
        File file = File.createTempFile("bench_1brc_", ".txt");
        file.deleteOnExit();
        write1BrcFile(file, rows, decimalScale, injectHighPrecisionEvery);
        return file;
    }

    /**
     * Writes a deterministic 1BRC dataset to the specified file.
     */
    public static void write1BrcFile(File file, int rows, int decimalScale, int injectHighPrecisionEvery) throws IOException {
        SplittableRandom rng = new SplittableRandom(DEFAULT_SEED ^ 0x1B2C3D4EL);
        int stationCount = SAMPLE_STATIONS.length;
        byte[][] stationBytes = new byte[stationCount][];
        for (int i = 0; i < stationCount; i++) {
            stationBytes[i] = (SAMPLE_STATIONS[i] + ";").getBytes(StandardCharsets.UTF_8);
        }

        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file), 65536)) {
            StringBuilder numBuf = new StringBuilder(16);
            for (int i = 0; i < rows; i++) {
                byte[] st = stationBytes[i % stationCount];
                out.write(st);

                numBuf.setLength(0);
                if (injectHighPrecisionEvery > 0 && i > 0 && (i % injectHighPrecisionEvery) == 0) {
                    int milli = rng.nextInt(-40000, 50000);
                    if (milli < 0) {
                        numBuf.append('-');
                        milli = -milli;
                    }
                    int intPart = milli / 1000;
                    int fracPart = milli % 1000;
                    if (fracPart % 10 == 0) {
                        fracPart += 5; // guarantee 3 non-zero decimal digits
                    }
                    numBuf.append(intPart).append('.');
                    if (fracPart < 100) numBuf.append('0');
                    if (fracPart < 10) numBuf.append('0');
                    numBuf.append(fracPart);
                } else if (decimalScale == 2) {
                    int centi = rng.nextInt(-4000, 5000);
                    if (centi < 0) {
                        numBuf.append('-');
                        centi = -centi;
                    }
                    numBuf.append(centi / 100).append('.');
                    int frac = centi % 100;
                    if (frac < 10) numBuf.append('0');
                    numBuf.append(frac);
                } else {
                    int deci = rng.nextInt(-400, 500);
                    if (deci < 0) {
                        numBuf.append('-');
                        deci = -deci;
                    }
                    numBuf.append(deci / 10).append('.').append(deci % 10);
                }
                numBuf.append('\n');
                for (int c = 0; c < numBuf.length(); c++) {
                    out.write((byte) numBuf.charAt(c));
                }
            }
        }
    }

    /**
     * Computes a deterministic SHA-256 hex checksum of a DataFrame's dimensions, column names, and sampled values
     * to verify semantic equivalence across benchmark runs.
     */
    public static String computeChecksum(CupOfTea df) {
        if (df == null) {
            return "null";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            int rows = df.size();
            List<Column> headers = df.getHeaders();
            md.update(("rows=" + rows + ";cols=" + (headers != null ? headers.size() : 0) + "\n")
                    .getBytes(StandardCharsets.UTF_8));
            if (headers != null) {
                for (Column col : headers) {
                    ColumnVector vec = df.getColumnVector(col.columnName());
                    md.update((col.columnName() + ":").getBytes(StandardCharsets.UTF_8));
                    if (vec != null) {
                        int step = Math.max(1, rows / 32);
                        for (int r = 0; r < rows; r += step) {
                            Object val = vec.getObject(r);
                            String str = (val instanceof Number num)
                                    ? String.format(Locale.ROOT, "%.4f", num.doubleValue())
                                    : String.valueOf(val);
                            md.update((str + ",").getBytes(StandardCharsets.UTF_8));
                        }
                    }
                    md.update((byte) '\n');
                }
            }
            return HexFormat.of().formatHex(md.digest()).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
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
