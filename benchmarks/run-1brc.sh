#!/usr/bin/env bash
# ==============================================================================
# Run 1 Billion Row Challenge (1BRC) Benchmark for CupOfTea & Polars
#
# Usage:
#   ./benchmarks/run-1brc.sh [rows] [scale]
# Examples:
#   ./benchmarks/run-1brc.sh 10000000
#   ./benchmarks/run-1brc.sh 1000000
# ==============================================================================
set -euo pipefail

ROWS="${1:-10000000}"
BENCH_JAR="cupoftea-benchmark/target/benchmarks.jar"

echo "========================================================================"
echo " Running 1 Billion Row Challenge (1BRC) Benchmark Suite"
echo " Rows: $ROWS"
echo "========================================================================"

if [[ ! -f "$BENCH_JAR" ]]; then
    echo "Building benchmark JAR..."
    mvn clean package -pl cupoftea-benchmark -am -DskipTests
fi

echo "--- [1/2] Running CupOfTea 1BRC (JMH with Vector API & SWAR scanning) ---"
java --enable-native-access=ALL-UNNAMED \
     --add-opens=java.base/java.nio=ALL-UNNAMED \
     --add-modules=jdk.incubator.vector \
     -jar "$BENCH_JAR" 1brc -f 1 -wi 1 -i 2 -p "rows=$ROWS"

if command -v python3 >/dev/null 2>&1 && python3 -c "import polars" >/dev/null 2>&1; then
    echo "--- [2/2] Running Polars 1BRC Comparison ---"
    python3 benchmarks/polars/run_polars_benchmarks.py --rows "$ROWS" --target 1brc || true
fi

echo "1BRC Benchmark completed."
