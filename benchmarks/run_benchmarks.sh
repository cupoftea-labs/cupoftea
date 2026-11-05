#!/usr/bin/env bash
# ==============================================================================
# CupOfTea vs. Polars Master Benchmark Orchestrator (Issue #251)
#
# Usage:
#   ./benchmarks/run_benchmarks.sh [options]
#
# Options:
#   --target <preset>       Benchmark target preset (default: polars)
#                           (polars, polars-ingest, polars-egress, polars-groupby, polars-join,
#                            polars-filter, polars-window, 1brc, micro, io, pipeline, scaling, all)
#   --rows <count>          Dataset row scale (default: 100000)
#   --warmup <n>            Warmup iterations (default: 2)
#   --iterations <n>        Measurement iterations (default: 3)
#   --forks <n>             JMH forks (default: 1)
#   --cache <warm|cold>     OS page cache mode (default: warm)
#   --profile-cpu           Attach async-profiler for CPU FlameGraph
#   --profile-alloc         Attach async-profiler for Heap Allocation FlameGraph
#   --perf-stat             Collect dedicated PMU counters (Linux perf / macOS sudo kperf)
#   --skip-polars           Skip running Python Polars suite
#   --skip-build            Skip Maven package step if benchmarks.jar already exists
# ==============================================================================
set -euo pipefail

TARGET="polars"
ROWS=100000
WARMUP=2
ITERATIONS=3
FORKS=1
CACHE_MODE="warm"
PROFILE_CPU=false
PROFILE_ALLOC=false
PERF_STAT=false
SKIP_POLARS=false
SKIP_BUILD=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --target) TARGET="$2"; shift 2 ;;
        --rows) ROWS="$2"; shift 2 ;;
        --warmup) WARMUP="$2"; shift 2 ;;
        --iterations) ITERATIONS="$2"; shift 2 ;;
        --forks) FORKS="$2"; shift 2 ;;
        --cache) CACHE_MODE="$2"; shift 2 ;;
        --profile-cpu) PROFILE_CPU=true; shift ;;
        --profile-alloc) PROFILE_ALLOC=true; shift ;;
        --perf-stat) PERF_STAT=true; shift ;;
        --skip-polars) SKIP_POLARS=true; shift ;;
        --skip-build) SKIP_BUILD=true; shift ;;
        *) echo "Unknown argument: $1"; exit 1 ;;
    esac
done

RESULTS_DIR="benchmarks/results"
DATASET_DIR="$RESULTS_DIR/datasets"
BENCH_JAR="cupoftea-benchmark/target/benchmarks.jar"

mkdir -p "$RESULTS_DIR" "$DATASET_DIR"

# 1. Build Shaded JMH JAR
if [[ "$SKIP_BUILD" == "false" || ! -f "$BENCH_JAR" ]]; then
    echo "=== [1/5] Building CupOfTea Benchmark JAR ==="
    mvn -B clean package -pl cupoftea-benchmark -am -DskipTests -Dmaven.javadoc.skip=true
fi

# 2. Generate Deterministic Datasets (Fixed Seed 0xCAFEBABE12345678L)
echo "=== [2/5] Generating Deterministic Datasets (rows=$ROWS) ==="
java --enable-native-access=ALL-UNNAMED \
     --add-opens=java.base/java.nio=ALL-UNNAMED \
     --add-modules=jdk.incubator.vector \
     -jar "$BENCH_JAR" generate-datasets "$DATASET_DIR" "$ROWS"

# 3. Environment Isolation & Page Cache Preparation
echo "=== [3/5] Preparing Hardware & OS Environment (cache=$CACHE_MODE) ==="
bash benchmarks/scripts/setup_env.sh "$CACHE_MODE" "$DATASET_DIR"

# 4. Execute CupOfTea JMH Suite (with GC + HardwareCountersProfiler enabled)
echo "=== [4/5] Running CupOfTea JMH Suite (target=$TARGET, rows=$ROWS) ==="
JMH_CMD=(
    java
    --enable-native-access=ALL-UNNAMED
    --add-opens=java.base/java.nio=ALL-UNNAMED
    --add-modules=jdk.incubator.vector
    -jar "$BENCH_JAR" "$TARGET"
    -f "$FORKS" -wi "$WARMUP" -i "$ITERATIONS"
    -p "rows=$ROWS"
    -prof gc
    -rf json -rff "$RESULTS_DIR/jmh-results.json"
)

OS_NAME="$(uname -s)"
if [[ "$OS_NAME" == "Darwin" ]] && [[ -x /usr/bin/time ]]; then
    # Always capture macOS /usr/bin/time -l hardware counters (instructions, cycles, RSS) during JMH run
    python3 benchmarks/scripts/macos_kperf_pmu.py "$RESULTS_DIR/kperf-pmu.json" true >/dev/null 2>&1 || true
    /usr/bin/time -l "${JMH_CMD[@]}" 2> >(tee "$RESULTS_DIR/perf-stat.txt" >&2)
else
    "${JMH_CMD[@]}"
fi

if [[ "$PROFILE_CPU" == "true" ]]; then
    bash benchmarks/scripts/profile_flamegraph.sh cpu "$TARGET" "$RESULTS_DIR/profiles"
fi

if [[ "$PROFILE_ALLOC" == "true" ]]; then
    bash benchmarks/scripts/profile_flamegraph.sh alloc "$TARGET" "$RESULTS_DIR/profiles"
fi

if [[ "$PERF_STAT" == "true" ]]; then
    if [[ "$OS_NAME" != "Darwin" ]] || [[ "${EUID:-$(id -u)}" -eq 0 ]]; then
        bash benchmarks/scripts/run_perf_stat.sh "$TARGET" "$RESULTS_DIR/perf-stat.txt" "$ROWS"
    fi
fi

# 5. Execute Polars Suite & Generate Consolidated Markdown Report
PYTHON_BIN="${PYTHON_BIN:-python3}"
if ! "$PYTHON_BIN" -c "import polars" >/dev/null 2>&1; then
    for candidate in \
        ".venv/bin/python3" \
        "$HOME/programas/onebillionrow/.venv/bin/python3"; do
        if [[ -x "$candidate" ]] && "$candidate" -c "import polars" >/dev/null 2>&1; then
            PYTHON_BIN="$candidate"
            break
        fi
    done
fi

if [[ "$SKIP_POLARS" == "false" ]]; then
    echo "=== [5/5] Running Polars Suite (using $PYTHON_BIN) & Generating Comparative Report ==="
    "$PYTHON_BIN" benchmarks/polars/run_polars_benchmarks.py \
        --dataset-dir "$DATASET_DIR" \
        --rows "$ROWS" \
        --warmup "$WARMUP" \
        --iterations "$ITERATIONS" \
        --output "$RESULTS_DIR/polars-results.json"
else
    echo "=== [5/5] Skipping Polars Execution (--skip-polars) ==="
fi

"$PYTHON_BIN" benchmarks/scripts/generate_report.py \
    --jmh-json "$RESULTS_DIR/jmh-results.json" \
    --polars-json "$RESULTS_DIR/polars-results.json" \
    --perf-log "$RESULTS_DIR/perf-stat.txt" \
    --kperf-json "$RESULTS_DIR/kperf-pmu.json" \
    --dataset-dir "$DATASET_DIR" \
    --output "$RESULTS_DIR/comparison-report.md"

echo "Done! Report written to $RESULTS_DIR/comparison-report.md"
