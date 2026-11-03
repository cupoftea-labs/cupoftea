#!/usr/bin/env bash
# ==============================================================================
# CupOfTea async-profiler FlameGraph Wrapper (Issue #251)
# Attaches async-profiler to JMH benchmark runs to produce interactive HTML
# FlameGraphs and JFR recordings for CPU cycles, Heap Allocations, or Locks.
# ==============================================================================
set -euo pipefail

EVENT="${1:-cpu}"          # cpu, alloc, lock, cache-misses
TARGET="${2:-1brc}"        # JMH preset or regex
OUT_DIR="${3:-benchmarks/results/profiles}"
BENCH_JAR="cupoftea-benchmark/target/benchmarks.jar"

mkdir -p "$OUT_DIR"

if [[ ! -f "$BENCH_JAR" ]]; then
    echo "Building $BENCH_JAR..."
    mvn -B clean package -pl cupoftea-benchmark -am -DskipTests
fi

PROFILER_LIB="${ASYNC_PROFILER_LIB:-}"
if [[ -z "$PROFILER_LIB" ]]; then
    for candidate in \
        "/usr/local/lib/libasyncProfiler.so" \
        "/opt/async-profiler/lib/libasyncProfiler.so" \
        "/opt/homebrew/lib/libasyncProfiler.dylib" \
        "$HOME/async-profiler/lib/libasyncProfiler.dylib" \
        "$HOME/async-profiler/lib/libasyncProfiler.so"; do
        if [[ -f "$candidate" ]]; then
            PROFILER_LIB="$candidate"
            break
        fi
    done
fi

if [[ -n "$PROFILER_LIB" && -f "$PROFILER_LIB" ]]; then
    echo "=== Running JMH with async-profiler (event=$EVENT, lib=$PROFILER_LIB) ==="
    java --add-opens=java.base/java.nio=ALL-UNNAMED \
         --add-modules=jdk.incubator.vector \
         -jar "$BENCH_JAR" "$TARGET" \
         -prof "async:libPath=$PROFILER_LIB;event=$EVENT;output=flamegraph;dir=$OUT_DIR"
    echo "FlameGraph generated in $OUT_DIR"
else
    echo "WARNING: libasyncProfiler not found (set ASYNC_PROFILER_LIB=/path/to/libasyncProfiler.so)."
    echo "Falling back to built-in JMH GC and Stack profilers (-prof gc -prof stack)..."
    java --add-opens=java.base/java.nio=ALL-UNNAMED \
         --add-modules=jdk.incubator.vector \
         -jar "$BENCH_JAR" "$TARGET" \
         -prof gc -prof stack \
         -rf json -rff "$OUT_DIR/jmh-profile-$EVENT.json"
fi
