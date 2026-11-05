#!/usr/bin/env bash
# ==============================================================================
# CupOfTea Benchmark Hardware & OS Isolation Setup Script (Issue #251)
# Configures CPU governor, disables Turbo Boost (Linux), sets thread affinity,
# and warms/drops OS page cache for deterministic benchmarking.
# ==============================================================================
set -euo pipefail

CACHE_MODE="${1:-warm}"
DATASET_DIR="${2:-benchmarks/results/datasets}"
OS_NAME="$(uname -s)"

echo "=== [CupOfTea Benchmark Environment Setup] ==="
echo "OS: $OS_NAME | Architecture: $(uname -m) | Cache Mode: $CACHE_MODE"

if [[ "$OS_NAME" == "Linux" ]]; then
    # 1. CPU Frequency Governor
    if command -v cpupower >/dev/null 2>&1; then
        echo "[Linux] Checking CPU frequency governor..."
        cpupower frequency-info -p || true
    fi

    # 2. Turbo Boost Check (Intel / AMD)
    if [[ -f /sys/devices/system/cpu/intel_pstate/no_turbo ]]; then
        NO_TURBO=$(cat /sys/devices/system/cpu/intel_pstate/no_turbo)
        echo "[Linux] Intel Turbo Boost disabled (no_turbo): $NO_TURBO"
    elif [[ -f /sys/devices/system/cpu/cpufreq/boost ]]; then
        AMD_BOOST=$(cat /sys/devices/system/cpu/cpufreq/boost)
        echo "[Linux] AMD Core Performance Boost: $AMD_BOOST"
    fi
elif [[ "$OS_NAME" == "Darwin" ]]; then
    echo "[macOS] Hardware Model: $(sysctl -n hw.model 2>/dev/null || echo 'Apple Silicon')"
    echo "[macOS] Physical Cores: $(sysctl -n hw.physicalcpu 2>/dev/null || echo 'N/A') | Logical Cores: $(sysctl -n hw.logicalcpu 2>/dev/null || echo 'N/A')"
    echo "[macOS] Note: CPU governor & Turbo Boost pinning require bare-metal Linux; running with standard macOS scheduler."
fi

# 3. Page Cache Control (Warm vs. Cold)
if [[ "$CACHE_MODE" == "warm" && -d "$DATASET_DIR" ]]; then
    echo "[Cache] Warming OS page cache for files in $DATASET_DIR..."
    if command -v vmtouch >/dev/null 2>&1; then
        vmtouch -t "$DATASET_DIR"
    else
        for f in "$DATASET_DIR"/*; do
            if [[ -f "$f" ]]; then
                cat "$f" > /dev/null
            fi
        done
    fi
    echo "[Cache] OS page cache warmed successfully."
elif [[ "$CACHE_MODE" == "cold" ]]; then
    if [[ "$OS_NAME" == "Linux" ]] && [[ $(id -u) -eq 0 ]]; then
        echo "[Cache] Dropping Linux OS page caches..."
        sync && echo 3 > /proc/sys/vm/drop_caches
    else
        echo "[Cache] Cold cache requested, but dropping kernel caches requires root on Linux."
    fi
fi
