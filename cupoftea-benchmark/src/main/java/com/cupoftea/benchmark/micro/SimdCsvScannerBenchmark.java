package com.cupoftea.benchmark.micro;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import com.cupoftea.CupOfTea;
import com.cupoftea.csv.fast.FastCsvReader;
import com.cupoftea.csv.fast.SimdCsvScanner;
import com.cupoftea.importers.configurations.CSVImporterConfiguration;
import com.opencsv.ICSVParser;

/**
 * JMH benchmark suite comparing Scalar vs SIMD ({@code jdk.incubator.vector.ByteVector}) CSV scanning and parsing across:
 * <ul>
 *   <li><b>Standard wide CSV:</b> 20 columns, mostly numeric/unquoted values.</li>
 *   <li><b>High-density quoted CSV:</b> Text fields with embedded commas, escaped quotes, and multiline records.</li>
 *   <li><b>Mixed CSV with varied row lengths:</b> Row sizes ranging from ~50 bytes to ~2 KB.</li>
 * </ul>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 2, time = 1)
@Fork(value = 1, jvmArgsAppend = {"--add-modules=jdk.incubator.vector", "--add-opens=java.base/java.nio=ALL-UNNAMED"})
public class SimdCsvScannerBenchmark {

    @Param({"10000"})
    public int rows;

    public byte[] standardWideBytes;
    public byte[] highDensityQuotedBytes;
    public byte[] mixedVariedBytes;

    public File standardWideFile;
    public File highDensityQuotedFile;
    public File mixedVariedFile;

    private int[] reusableOffsetBuffer;

    @Setup(Level.Trial)
    public void setupTrial() throws IOException {
        standardWideBytes = generateStandardWideCsv(rows);
        highDensityQuotedBytes = generateHighDensityQuotedCsv(rows);
        mixedVariedBytes = generateMixedVariedLengthCsv(rows);

        standardWideFile = writeTempFile("bench_wide_", standardWideBytes);
        highDensityQuotedFile = writeTempFile("bench_quoted_", highDensityQuotedBytes);
        mixedVariedFile = writeTempFile("bench_mixed_", mixedVariedBytes);

        reusableOffsetBuffer = new int[64];
    }

    @TearDown(Level.Trial)
    public void tearDownTrial() {
        safeDelete(standardWideFile);
        safeDelete(highDensityQuotedFile);
        safeDelete(mixedVariedFile);
    }

    private static File writeTempFile(String prefix, byte[] content) throws IOException {
        File f = File.createTempFile(prefix, ".csv");
        f.deleteOnExit();
        Files.write(f.toPath(), content);
        return f;
    }

    private static void safeDelete(File f) {
        if (f != null && f.exists()) {
            f.delete();
        }
    }

    static byte[] generateStandardWideCsv(int rowCount) {
        StringBuilder sb = new StringBuilder(rowCount * 140);
        for (int c = 0; c < 20; c++) {
            if (c > 0) sb.append(',');
            sb.append("col_").append(c);
        }
        sb.append('\n');

        Random rng = new Random(42L);
        for (int r = 0; r < rowCount; r++) {
            sb.append(r).append(',')
              .append(r * 10L + 1000L).append(',')
              .append(String.format(Locale.ROOT, "%.4f", rng.nextDouble() * 1000.0)).append(',')
              .append(String.format(Locale.ROOT, "%.2f", rng.nextDouble() * 50.0)).append(',')
              .append(rng.nextInt(100_000)).append(',')
              .append(rng.nextInt(50_000)).append(',')
              .append(String.format(Locale.ROOT, "%.3f", rng.nextDouble() * 10.0)).append(',')
              .append((r & 1) == 0 ? "true" : "false").append(',')
              .append("STATION_").append(r % 32).append(',')
              .append(r + 5000).append(',')
              .append(String.format(Locale.ROOT, "%.2f", (r % 200) * 1.25)).append(',')
              .append(r % 1000).append(',')
              .append(r * 3).append(',')
              .append(String.format(Locale.ROOT, "%.1f", (r % 50) * 0.5)).append(',')
              .append(r % 256).append(',')
              .append("REGION_").append(r % 8).append(',')
              .append(10_000 + (r % 90_000)).append(',')
              .append(String.format(Locale.ROOT, "%.5f", (r % 100) * 0.01234)).append(',')
              .append((r % 3 == 0) ? "true" : "false").append(',')
              .append(r * 7)
              .append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    static byte[] generateHighDensityQuotedCsv(int rowCount) {
        StringBuilder sb = new StringBuilder(rowCount * 220);
        sb.append("id,author,title,abstract_text,tags,location,price,verified\n");

        for (int r = 0; r < rowCount; r++) {
            sb.append(r).append(',')
              .append("\"Last_").append(r % 500).append(", First_").append(r % 200).append("\"," )
              .append("\"Report #").append(r).append(": \"\"SIMD, Vector & Parallel\"\" Edition\",")
              .append("\"Line 1: summary of record ").append(r).append(", with commas; and details.\n")
              .append("Line 2: continuation with embedded newline and \"\"quoted\"\" token.\",")
              .append("\"csv,simd,vector,avx512,neon,row_").append(r % 50).append("\",")
              .append("\"City_").append(r % 100).append(", State_").append(r % 25).append(", Country\",")
              .append(String.format(Locale.ROOT, "%.2f", (r % 500) * 1.75)).append(',')
              .append((r & 1) == 0 ? "true" : "false")
              .append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    static byte[] generateMixedVariedLengthCsv(int rowCount) {
        StringBuilder sb = new StringBuilder(rowCount * 350);
        sb.append("id,type,code,val1,val2,val3,flag,payload,notes,checksum\n");

        String mediumPayload = "P".repeat(180);
        String largeQuotedPayload = "\"" + ("Segment_A_with_text_data_1234567890, ".repeat(25))
                + "\n" + ("Segment_B_with_more_text_data_0987654321; ".repeat(20)) + "\"";

        for (int r = 0; r < rowCount; r++) {
            int mod = r % 10;
            sb.append(r).append(',')
              .append("TYPE_").append(mod).append(',')
              .append(1000 + (r % 9000)).append(',')
              .append(String.format(Locale.ROOT, "%.2f", r * 0.5)).append(',')
              .append(r * 100L).append(',')
              .append(String.format(Locale.ROOT, "%.4f", (r % 77) * 1.125)).append(',')
              .append((r & 1) == 0 ? "true" : "false").append(',');

            if (mod == 0) {
                // ~1.8 KB row
                sb.append(largeQuotedPayload).append(',')
                  .append("\"Long row note with, comma and \"\"quote\"\"\"").append(',');
            } else if (mod <= 3) {
                // ~250 B row
                sb.append(mediumPayload).append(',')
                  .append("\"Medium note, item ").append(r % 40).append("\"").append(',');
            } else {
                // ~60 B compact row
                sb.append("short_").append(r % 20).append(',')
                  .append("ok").append(',');
            }
            sb.append(r ^ 0x5A5A5A).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Baseline scalar structural scanner (byte-by-byte delimiter, quote, and newline state machine).
     */
    private static int scanScalarBaseline(byte[] buf, int start, int end, byte delim, byte quote, byte escape) {
        boolean inQuote = false;
        int boundaryCount = 0;
        for (int i = start; i < end; i++) {
            byte b = buf[i];
            if (b == escape && escape != ICSVParser.NULL_CHARACTER && i + 1 < end) {
                i++;
                continue;
            }
            if (b == quote && quote != ICSVParser.NULL_CHARACTER) {
                if (inQuote && i + 1 < end && buf[i + 1] == quote) {
                    i++;
                    continue;
                }
                inQuote = !inQuote;
                continue;
            }
            if (!inQuote && (b == delim || b == '\n' || b == '\r')) {
                boundaryCount++;
            }
        }
        return boundaryCount;
    }

    // =========================================================================
    // 1. Structural Scanner Benchmarks (Scalar vs Two-Stage SIMD)
    // =========================================================================

    @Benchmark
    public void scanStandardWideScalar(Blackhole bh) {
        int count = scanScalarBaseline(standardWideBytes, 0, standardWideBytes.length, (byte) ',', (byte) '"', (byte) '\\');
        bh.consume(count);
    }

    @Benchmark
    public void scanStandardWideSimd(Blackhole bh) {
        int count = SimdCsvScanner.scanToOffsets(
                standardWideBytes, 0, standardWideBytes.length, (byte) ',', (byte) '"', (byte) '\\', reusableOffsetBuffer);
        bh.consume(count);
    }

    @Benchmark
    public void scanHighDensityQuotedScalar(Blackhole bh) {
        int count = scanScalarBaseline(highDensityQuotedBytes, 0, highDensityQuotedBytes.length, (byte) ',', (byte) '"', (byte) '\\');
        bh.consume(count);
    }

    @Benchmark
    public void scanHighDensityQuotedSimd(Blackhole bh) {
        int count = SimdCsvScanner.scanToOffsets(
                highDensityQuotedBytes, 0, highDensityQuotedBytes.length, (byte) ',', (byte) '"', (byte) '\\', reusableOffsetBuffer);
        bh.consume(count);
    }

    @Benchmark
    public void scanMixedVariedScalar(Blackhole bh) {
        int count = scanScalarBaseline(mixedVariedBytes, 0, mixedVariedBytes.length, (byte) ',', (byte) '"', (byte) '\\');
        bh.consume(count);
    }

    @Benchmark
    public void scanMixedVariedSimd(Blackhole bh) {
        int count = SimdCsvScanner.scanToOffsets(
                mixedVariedBytes, 0, mixedVariedBytes.length, (byte) ',', (byte) '"', (byte) '\\', reusableOffsetBuffer);
        bh.consume(count);
    }

    // =========================================================================
    // 2. End-to-End CSV Reader Benchmarks (Scalar Reader vs SIMD Reader)
    // =========================================================================

    @Benchmark
    public void readStandardWideScalar(Blackhole bh) {
        CSVImporterConfiguration cfg = new CSVImporterConfiguration()
                .setFastEngine(true)
                .setSimdEnabled(false)
                .setInferNumericTypes(true);
        CupOfTea df = new CupOfTea();
        new FastCsvReader(cfg).readInto(standardWideBytes, df);
        bh.consume(df.size());
    }

    @Benchmark
    public void readStandardWideSimd(Blackhole bh) {
        CSVImporterConfiguration cfg = new CSVImporterConfiguration()
                .setFastEngine(true)
                .setSimdEnabled(true)
                .setInferNumericTypes(true);
        CupOfTea df = new CupOfTea();
        new FastCsvReader(cfg).readInto(standardWideBytes, df);
        bh.consume(df.size());
    }

    @Benchmark
    public void readHighDensityQuotedScalar(Blackhole bh) {
        CSVImporterConfiguration cfg = new CSVImporterConfiguration()
                .setFastEngine(true)
                .setSimdEnabled(false)
                .setInferNumericTypes(true);
        CupOfTea df = new CupOfTea();
        new FastCsvReader(cfg).readInto(highDensityQuotedBytes, df);
        bh.consume(df.size());
    }

    @Benchmark
    public void readHighDensityQuotedSimd(Blackhole bh) {
        CSVImporterConfiguration cfg = new CSVImporterConfiguration()
                .setFastEngine(true)
                .setSimdEnabled(true)
                .setInferNumericTypes(true);
        CupOfTea df = new CupOfTea();
        new FastCsvReader(cfg).readInto(highDensityQuotedBytes, df);
        bh.consume(df.size());
    }

    @Benchmark
    public void readMixedVariedScalar(Blackhole bh) {
        CSVImporterConfiguration cfg = new CSVImporterConfiguration()
                .setFastEngine(true)
                .setSimdEnabled(false)
                .setInferNumericTypes(true);
        CupOfTea df = new CupOfTea();
        new FastCsvReader(cfg).readInto(mixedVariedBytes, df);
        bh.consume(df.size());
    }

    @Benchmark
    public void readMixedVariedSimd(Blackhole bh) {
        CSVImporterConfiguration cfg = new CSVImporterConfiguration()
                .setFastEngine(true)
                .setSimdEnabled(true)
                .setInferNumericTypes(true);
        CupOfTea df = new CupOfTea();
        new FastCsvReader(cfg).readInto(mixedVariedBytes, df);
        bh.consume(df.size());
    }
}
