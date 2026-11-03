package com.cupoftea.benchmark;

import java.io.File;
import java.util.Arrays;

import org.openjdk.jmh.Main;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.CommandLineOptions;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import com.cupoftea.benchmark.data.SyntheticDataGenerator;
import com.cupoftea.benchmark.profile.HardwareCountersProfiler;

/**
 * Main command-line launcher for the CupOfTea JMH benchmark suite.
 * Supports preset target suites (with full JMH flag forwarding) as well as
 * deterministic dataset generation for cross-engine (CupOfTea vs. Polars) benchmarks.
 */
public class BenchmarkRunner {

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            String target = args[0].toLowerCase();

            if ("generate-datasets".equals(target)) {
                File outDir = args.length > 1 ? new File(args[1]) : new File("benchmarks/results/datasets");
                int rows = args.length > 2 ? Integer.parseInt(args[2]) : 100_000;
                if (!outDir.exists() && !outDir.mkdirs()) {
                    throw new IllegalStateException("Could not create dataset directory: " + outDir.getAbsolutePath());
                }
                System.out.println("Generating deterministic datasets (SEED=0x"
                        + Long.toHexString(SyntheticDataGenerator.DEFAULT_SEED) + ", rows=" + rows + ") in "
                        + outDir.getAbsolutePath() + "...");

                File csvFile = new File(outDir, "mixed_analytics_" + rows + ".csv");
                SyntheticDataGenerator.writeMixedDelimitedFile(csvFile, rows, ',', 0.05);

                File tsvFile = new File(outDir, "mixed_analytics_" + rows + ".tsv");
                SyntheticDataGenerator.writeMixedDelimitedFile(tsvFile, rows, '\t', 0.05);

                File brc1 = new File(outDir, "1brc_scale1_" + rows + ".txt");
                SyntheticDataGenerator.write1BrcFile(brc1, rows, 1, 0);

                File brc2 = new File(outDir, "1brc_scale2_" + rows + ".txt");
                SyntheticDataGenerator.write1BrcFile(brc2, rows, 2, 0);

                File brcHybrid = new File(outDir, "1brc_hybrid3_" + rows + ".txt");
                SyntheticDataGenerator.write1BrcFile(brcHybrid, rows, 1, Math.max(100, rows / 20));

                System.out.println("Datasets generated successfully.");
                return;
            }

            String pattern = resolvePresetPattern(target);
            if (pattern != null) {
                String[] remainingArgs = Arrays.copyOfRange(args, 1, args.length);
                CommandLineOptions cmdOptions = new CommandLineOptions(remainingArgs);
                ChainedOptionsBuilder builder = new OptionsBuilder()
                        .parent(cmdOptions)
                        .include(pattern)
                        .addProfiler(HardwareCountersProfiler.class)
                        .jvmArgsAppend("--enable-native-access=ALL-UNNAMED");

                if (!cmdOptions.getForkCount().hasValue()) {
                    builder.forks(1);
                }
                if (!cmdOptions.getWarmupIterations().hasValue()) {
                    builder.warmupIterations(1);
                }
                if (!cmdOptions.getMeasurementIterations().hasValue()) {
                    builder.measurementIterations(2);
                }

                Options opt = builder.build();
                new Runner(opt).run();
                return;
            }
        }

        // Standard JMH CLI invocation
        Main.main(args);
    }

    public static String resolvePresetPattern(String target) {
        if (target == null) {
            return null;
        }
        return switch (target.toLowerCase()) {
            case "micro" -> "com.cupoftea.benchmark.micro.*";
            case "simd", "csv" -> "com.cupoftea.benchmark.micro.SimdCsvScannerBenchmark.*";
            case "macro", "pipeline" -> "com.cupoftea.benchmark.macro.EndToEndPipelineBenchmark.*";
            case "io" -> "com.cupoftea.benchmark.macro.EndToEndIoBenchmark.*";
            case "scaling", "scale" -> "com.cupoftea.benchmark.scaling.*";
            case "competitive" -> "com.cupoftea.benchmark.scaling.CompetitiveBaselineBenchmark.*";
            case "polars", "polars-comparison" -> "com.cupoftea.benchmark.polars.*";
            case "polars-ingest" -> "com.cupoftea.benchmark.polars.PolarsIngestBenchmark.*";
            case "polars-groupby" -> "com.cupoftea.benchmark.polars.PolarsGroupByBenchmark.*";
            case "polars-join" -> "com.cupoftea.benchmark.polars.PolarsJoinBenchmark.*";
            case "polars-filter" -> "com.cupoftea.benchmark.polars.PolarsFilterVectorBenchmark.*";
            case "1brc" -> "com.cupoftea.benchmark.polars.OneBrcStandardBenchmark.*";
            case "all" -> "com.cupoftea.benchmark.*";
            default -> null;
        };
    }
}
