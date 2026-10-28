package com.cupoftea.benchmark;

import org.openjdk.jmh.Main;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Main command-line launcher for the CupOfTea JMH benchmark suite.
 * Can be invoked with standard JMH arguments or preset target suites.
 */
public class BenchmarkRunner {

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            String target = args[0].toLowerCase();
            String pattern = switch (target) {
                case "micro" -> "com.cupoftea.benchmark.micro.*";
                case "macro", "pipeline" -> "com.cupoftea.benchmark.macro.EndToEndPipelineBenchmark.*";
                case "io" -> "com.cupoftea.benchmark.macro.EndToEndIoBenchmark.*";
                case "scaling", "scale" -> "com.cupoftea.benchmark.scaling.*";
                case "competitive" -> "com.cupoftea.benchmark.scaling.CompetitiveBaselineBenchmark.*";
                case "all" -> "com.cupoftea.benchmark.*";
                default -> null;
            };

            if (pattern != null) {
                Options opt = new OptionsBuilder()
                        .include(pattern)
                        .forks(1)
                        .warmupIterations(1)
                        .measurementIterations(2)
                        .build();

                new Runner(opt).run();
                return;
            }
        }

        // Standard JMH CLI invocation
        Main.main(args);
    }
}
