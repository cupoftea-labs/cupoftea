# CupOfTea vs. Polars Performance Benchmark Harness

Automated, deterministic, and reproducible benchmarking harness comparing **CupOfTea** against **Polars** (`py-polars` / `polars-lazy`) and the **1BRC (One Billion Row Challenge)** standard suite.

---

## 1. Directory Structure

```text
/benchmarks
├── README.md                          # Execution guide, OS isolation & profiling reference
├── run_benchmarks.sh                  # Master orchestrator (datasets -> JMH -> Polars -> Markdown report)
├── scripts/
│   ├── setup_env.sh                   # CPU governor, Turbo Boost check, taskset & OS page cache control
│   ├── profile_flamegraph.sh          # async-profiler wrapper (CPU, Heap Allocation & Lock FlameGraphs)
│   ├── run_perf_stat.sh               # Linux perf PMU counters (IPC, L1d/LLC/branch misses) & Peak RSS
│   └── generate_report.py             # JSON consolidator -> Markdown comparison matrix
├── polars/
│   ├── requirements.txt               # Python dependencies (polars, pyarrow, psutil)
│   ├── run_polars_benchmarks.py       # Equivalent Polars benchmark suite (Eager & Lazy Streaming)
│   └── run_1brc_polars.py             # Standalone 1BRC Polars runner (1M, 10M, 100M, 1B rows)
└── results/                           # Output directory for JSON results, FlameGraphs, and Markdown reports
```

---

## 2. Quick Start

### 2.1. Run the Complete Comparative Suite
```bash
chmod +x benchmarks/run_benchmarks.sh benchmarks/scripts/*.sh
./benchmarks/run_benchmarks.sh --target polars --rows 100000 --warmup 2 --iterations 3
```

### 2.2. Run Specific Benchmark Presets via JMH JAR
```bash
# Build the shaded JMH JAR
mvn clean package -pl cupoftea-benchmark -am -DskipTests

# Run all Polars comparative benchmarks (Ingest, Egress, GroupBy, Join, Filter, Window, 1BRC)
java --add-opens=java.base/java.nio=ALL-UNNAMED --add-modules=jdk.incubator.vector \
     -jar cupoftea-benchmark/target/benchmarks.jar polars

# Run individual workload suites
java --add-opens=java.base/java.nio=ALL-UNNAMED --add-modules=jdk.incubator.vector \
     -jar cupoftea-benchmark/target/benchmarks.jar polars-ingest
java --add-opens=java.base/java.nio=ALL-UNNAMED --add-modules=jdk.incubator.vector \
     -jar cupoftea-benchmark/target/benchmarks.jar polars-egress
java --add-opens=java.base/java.nio=ALL-UNNAMED --add-modules=jdk.incubator.vector \
     -jar cupoftea-benchmark/target/benchmarks.jar polars-groupby
java --add-opens=java.base/java.nio=ALL-UNNAMED --add-modules=jdk.incubator.vector \
     -jar cupoftea-benchmark/target/benchmarks.jar polars-join
java --add-opens=java.base/java.nio=ALL-UNNAMED --add-modules=jdk.incubator.vector \
     -jar cupoftea-benchmark/target/benchmarks.jar polars-filter
java --add-opens=java.base/java.nio=ALL-UNNAMED --add-modules=jdk.incubator.vector \
     -jar cupoftea-benchmark/target/benchmarks.jar polars-window
java --add-opens=java.base/java.nio=ALL-UNNAMED --add-modules=jdk.incubator.vector \
     -jar cupoftea-benchmark/target/benchmarks.jar tpch
java --add-opens=java.base/java.nio=ALL-UNNAMED --add-modules=jdk.incubator.vector \
     -jar cupoftea-benchmark/target/benchmarks.jar 1brc
```

### 2.3. CPU & Heap Allocation FlameGraphs (`async-profiler`)
```bash
./benchmarks/scripts/profile_flamegraph.sh cpu 1brc
./benchmarks/scripts/profile_flamegraph.sh alloc polars-groupby
```

### 2.4. Hardware PMU Counters (`Linux perf` / Peak RSS)
```bash
./benchmarks/scripts/run_perf_stat.sh 1brc
```
