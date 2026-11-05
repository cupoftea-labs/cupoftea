#!/usr/bin/env bash
# ==============================================================================
# CupOfTea Hardware PMU Counter & RSS Profiler Wrapper (Issue #251)
# Collects:
#   - Linux: perf stat PMU hardware counters (IPC, cycles, instructions,
#            L1d/LLC cache-misses, dTLB-load-misses, branch-misses)
#   - macOS (Apple Silicon / Darwin):
#       * Unprivileged: /usr/bin/time -l (PMC0 cycles, PMC1 instructions,
#         maximum resident set size, peak memory footprint, page faults)
#       * Root (sudo): kperf.framework / kperfdata.framework configurable PMCs
#         (PMC2..PMC9: L1D_CACHE_MISS_LD_NONSPEC, L1D_CACHE_MISS_ST_NONSPEC,
#         INST_BRANCH, BRANCH_MISPRED_NONSPEC) via macos_kperf_pmu.py
# ==============================================================================
set -euo pipefail

TARGET="${1:-polars}"
OUT_FILE="${2:-benchmarks/results/perf-stat.txt}"
ROWS="${3:-100000}"
BENCH_JAR="cupoftea-benchmark/target/benchmarks.jar"
KPERF_JSON="$(dirname "$OUT_FILE")/kperf-pmu.json"

mkdir -p "$(dirname "$OUT_FILE")"

CMD=(
    java
    --enable-native-access=ALL-UNNAMED
    --add-opens=java.base/java.nio=ALL-UNNAMED
    --add-modules=jdk.incubator.vector
    -Xms4g -Xmx8g -XX:+UseG1GC
    -jar "$BENCH_JAR" "$TARGET"
    -wi 1 -i 2 -f 1
    -p "rows=$ROWS"
)

OS_NAME="$(uname -s)"
if [[ "$OS_NAME" == "Linux" ]] && command -v perf >/dev/null 2>&1; then
    echo "=== Collecting Linux perf PMU hardware counters for target '$TARGET' ==="
    perf stat -e cycles,instructions,cache-references,cache-misses,L1-dcache-loads,L1-dcache-load-misses,LLC-loads,LLC-load-misses,dTLB-load-misses,branches,branch-misses \
        -o "$OUT_FILE" \
        "${CMD[@]}"
    cat "$OUT_FILE"
elif [[ "$OS_NAME" == "Darwin" ]]; then
    if [[ "${EUID:-$(id -u)}" -eq 0 ]]; then
        echo "=== Collecting macOS Apple Silicon L1d & Branch PMU counters via kperf (sudo) ==="
        python3 benchmarks/scripts/macos_kperf_pmu.py "$KPERF_JSON" /usr/bin/time -l "${CMD[@]}" 2> "$OUT_FILE"
    elif [[ -x /usr/bin/time ]]; then
        echo "=== Collecting macOS Fixed PMU (PMC0/PMC1) & Peak RSS via /usr/bin/time -l ==="
        echo "    (Note: L1d cache miss & branch miss PMCs PMC2..PMC9 on Apple Silicon require 'sudo' via kperf or 'xctrace')"
        python3 benchmarks/scripts/macos_kperf_pmu.py "$KPERF_JSON" true || true
        /usr/bin/time -l "${CMD[@]}" 2> "$OUT_FILE"
    fi
    tail -n 22 "$OUT_FILE"
else
    echo "=== Running benchmark target '$TARGET' (perf not available on $OS_NAME) ==="
    "${CMD[@]}" | tee "$OUT_FILE"
fi
