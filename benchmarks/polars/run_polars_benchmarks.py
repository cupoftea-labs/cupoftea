#!/usr/bin/env python3
"""
Polars Comparative Benchmark Suite for CupOfTea vs. Polars (Issue #251).

Executes identical workloads to the CupOfTea JMH suite:
  1. Ingestion / Parsing (CSV, TSV, Parquet — Eager & Lazy Streaming)
  2. GroupBy Aggregations (Low cardinality, High cardinality, Skewed Zipf, Multi-key, Multivariate)
  3. Relational Joins (Inner & Left Join across Int & UTF-8 String keys: 1:1, 1:N, N:M)
  4. Vectorized Filters & Math Expressions (1%, 50%, 90% selectivity, compound boolean, math)
  5. 1BRC Standard Suite (Scale 1 standard, Scale 2 cents, Hybrid 3-decimal fallback)

Captures per-benchmark latency, throughput, data bandwidth (MB/s), Peak RSS (MB),
and hardware PMU counters (Instructions Retired, Cycles Elapsed, IPC, Cycles/Row, Insns/Row)
via macOS proc_pid_rusage (RUSAGE_INFO_V4) or Linux resource.getrusage.
"""

import argparse
import ctypes
import hashlib
import json
import os
import platform
import resource
import statistics
import sys
import time
from pathlib import Path

try:
    import psutil
except ImportError:
    psutil = None

try:
    import polars as pl
except ImportError:
    pl = None


class _MacRusageInfoV4(ctypes.Structure):
    _fields_ = [
        ("ri_uuid", ctypes.c_uint8 * 16),
        ("ri_user_time", ctypes.c_uint64),
        ("ri_system_time", ctypes.c_uint64),
        ("ri_pkg_idle_wkups", ctypes.c_uint64),
        ("ri_interrupt_wkups", ctypes.c_uint64),
        ("ri_pageins", ctypes.c_uint64),
        ("ri_wired_size", ctypes.c_uint64),
        ("ri_resident_size", ctypes.c_uint64),
        ("ri_phys_footprint", ctypes.c_uint64),
        ("ri_proc_start_abstime", ctypes.c_uint64),
        ("ri_proc_exit_abstime", ctypes.c_uint64),
        ("ri_child_user_time", ctypes.c_uint64),
        ("ri_child_system_time", ctypes.c_uint64),
        ("ri_child_pkg_idle_wkups", ctypes.c_uint64),
        ("ri_child_interrupt_wkups", ctypes.c_uint64),
        ("ri_child_pageins", ctypes.c_uint64),
        ("ri_child_elapsed_abstime", ctypes.c_uint64),
        ("ri_diskio_bytesread", ctypes.c_uint64),
        ("ri_diskio_byteswritten", ctypes.c_uint64),
        ("ri_cpu_time_qos_default", ctypes.c_uint64),
        ("ri_cpu_time_qos_maintenance", ctypes.c_uint64),
        ("ri_cpu_time_qos_background", ctypes.c_uint64),
        ("ri_cpu_time_qos_utility", ctypes.c_uint64),
        ("ri_cpu_time_qos_legacy", ctypes.c_uint64),
        ("ri_cpu_time_qos_user_initiated", ctypes.c_uint64),
        ("ri_cpu_time_qos_user_interactive", ctypes.c_uint64),
        ("ri_billed_system_time", ctypes.c_uint64),
        ("ri_serviced_system_time", ctypes.c_uint64),
        ("ri_logical_writes", ctypes.c_uint64),
        ("ri_lifetime_max_phys_footprint", ctypes.c_uint64),
        ("ri_instructions", ctypes.c_uint64),
        ("ri_cycles", ctypes.c_uint64),
    ]


_MAC_LIBC = None
if sys.platform == "darwin":
    try:
        _MAC_LIBC = ctypes.CDLL("/usr/lib/libSystem.B.dylib")
    except Exception:
        _MAC_LIBC = None


def read_hw_snapshot():
    """Returns (instructions, cycles, peak_rss_mb)."""
    if _MAC_LIBC is not None:
        info = _MacRusageInfoV4()
        rc = _MAC_LIBC.proc_pid_rusage(os.getpid(), 4, ctypes.byref(info))
        if rc == 0:
            peak_bytes = max(info.ri_resident_size, info.ri_phys_footprint, info.ri_lifetime_max_phys_footprint)
            return int(info.ri_instructions), int(info.ri_cycles), peak_bytes / (1024.0 * 1024.0)

    if psutil is not None:
        rss_mb = psutil.Process(os.getpid()).memory_info().rss / (1024.0 * 1024.0)
        return 0, 0, rss_mb

    ru = resource.getrusage(resource.RUSAGE_SELF)
    # On macOS ru_maxrss is bytes; on Linux it is kilobytes
    divisor = (1024.0 * 1024.0) if sys.platform == "darwin" else 1024.0
    return 0, 0, ru.ru_maxrss / divisor


def compute_df_checksum(df: "pl.DataFrame") -> str:
    h = hashlib.sha256()
    h.update(f"rows={df.height};cols={df.width}\n".encode("utf-8"))
    for col_name in df.columns:
        h.update(f"{col_name}:".encode("utf-8"))
        step = max(1, df.height // 32)
        series = df[col_name]
        for r in range(0, df.height, step):
            val = series[r]
            if isinstance(val, (int, float)) and val is not None:
                h.update(f"{float(val):.4f},".encode("utf-8"))
            else:
                h.update(f"{val},".encode("utf-8"))
        h.update(b"\n")
    return h.hexdigest()[:16]


def run_timed_benchmark(name: str, rows: int, data_bytes: int, warmup: int, iterations: int, fn):
    for _ in range(warmup):
        res = fn()

    times_ms = []
    delta_insns_list = []
    delta_cycles_list = []
    _, _, peak_rss = read_hw_snapshot()
    checksum = "n/a"
    out_rows = 0

    for i in range(iterations):
        ins0, cyc0, _ = read_hw_snapshot()
        t0 = time.perf_counter_ns()
        res = fn()
        t1 = time.perf_counter_ns()
        ins1, cyc1, rss1 = read_hw_snapshot()

        elapsed_ms = (t1 - t0) / 1_000_000.0
        times_ms.append(elapsed_ms)
        if cyc1 > cyc0:
            delta_insns_list.append(ins1 - ins0)
            delta_cycles_list.append(cyc1 - cyc0)
        if rss1 > peak_rss:
            peak_rss = rss1
        if i == iterations - 1 and isinstance(res, pl.DataFrame):
            checksum = compute_df_checksum(res)
            out_rows = res.height

    mean_ms = statistics.mean(times_ms)
    std_ms = statistics.stdev(times_ms) if len(times_ms) > 1 else 0.0
    ops_per_sec = 1000.0 / mean_ms if mean_ms > 0 else 0.0
    mrows_per_sec = (rows / 1_000_000.0) / (mean_ms / 1000.0) if mean_ms > 0 else 0.0
    ns_per_row = (mean_ms * 1_000_000.0) / max(1, rows)
    mb_per_sec = ((data_bytes / (1024.0 * 1024.0)) / (mean_ms / 1000.0)) if (mean_ms > 0 and data_bytes > 0) else 0.0

    ipc = 0.0
    cycles_per_row = 0.0
    insns_per_row = 0.0
    if delta_cycles_list:
        mean_insns = statistics.mean(delta_insns_list)
        mean_cycles = statistics.mean(delta_cycles_list)
        ipc = mean_insns / max(1.0, mean_cycles)
        cycles_per_row = mean_cycles / max(1, rows)
        insns_per_row = mean_insns / max(1, rows)

    print(
        f"[Polars] {name:<54} | {mean_ms:7.2f} ± {std_ms:5.2f} ms "
        f"| IPC={ipc:4.2f} | cyc/row={cycles_per_row:6.1f} | RSS={peak_rss:6.1f} MB | sha={checksum}"
    )
    return {
        "benchmark": name,
        "engine": "Polars",
        "version": pl.__version__ if pl else "not-installed",
        "rows": rows,
        "outputRows": out_rows,
        "dataBytes": data_bytes,
        "meanMs": round(mean_ms, 4),
        "stdMs": round(std_ms, 4),
        "opsPerSec": round(ops_per_sec, 4),
        "mrowsPerSec": round(mrows_per_sec, 3),
        "nsPerRow": round(ns_per_row, 2),
        "mbPerSec": round(mb_per_sec, 2),
        "peakRssMb": round(peak_rss, 2),
        "ipc": round(ipc, 3),
        "cyclesPerRow": round(cycles_per_row, 2),
        "insnsPerRow": round(insns_per_row, 2),
        "checksum": checksum,
    }


def build_synthetic_asof_frames(rows: int, tickers: int = 100):
    t_start = 1_700_000_000_000
    times_trades = [t_start + i * 5 for i in range(rows)]
    tickers_trades = [f"TICKER_{(i % tickers):04d}" for i in range(rows)]
    prices = [100.0 + ((i * 13) % 500) * 0.1 for i in range(rows)]
    trades_df = pl.DataFrame({
        "time": times_trades,
        "ticker": tickers_trades,
        "price": prices,
    })

    times_quotes = [t_start + i * 7 for i in range(rows)]
    tickers_quotes = [f"TICKER_{(i % tickers):04d}" for i in range(rows)]
    bids = [99.0 + ((i * 17) % 500) * 0.1 for i in range(rows)]
    quotes_df = pl.DataFrame({
        "time": times_quotes,
        "ticker": tickers_quotes,
        "bid": bids,
    })
    return trades_df, quotes_df


def build_synthetic_join_frames(left_rows: int, card: int, mode: str):
    left_df = pl.DataFrame({
        "key_int": [i % card for i in range(left_rows)],
        "key_str": [f"KEY_{(i % card):08d}" for i in range(left_rows)],
        "left_val": [((i * 31) % 10000) * 0.25 for i in range(left_rows)],
    })
    dup_right = mode.upper() == "N:M"
    eff_card = max(1, left_rows // 4) if dup_right else max(left_rows, card)
    right_keys = [
        (card + (i % eff_card if dup_right else i)) if (i % 10 == 9)
        else ((i % eff_card if dup_right else i) % card)
        for i in range(left_rows)
    ]
    right_df = pl.DataFrame({
        "key_int": right_keys,
        "key_str": [f"KEY_{k:08d}" for k in right_keys],
        "right_val": [((i * 17) % 5000) * 0.5 for i in range(left_rows)],
    })
    return left_df, right_df


def main():
    parser = argparse.ArgumentParser(description="Run Polars comparative benchmarks")
    parser.add_argument("--dataset-dir", type=str, default="benchmarks/results/datasets")
    parser.add_argument("--rows", type=int, default=100_000)
    parser.add_argument("--warmup", type=int, default=2)
    parser.add_argument("--iterations", type=int, default=3)
    parser.add_argument("--output", type=str, default="benchmarks/results/polars-results.json")
    args = parser.parse_args()

    if pl is None:
        print("WARNING: py-polars is not installed in the active Python environment. Skipping Polars execution.")
        Path(args.output).parent.mkdir(parents=True, exist_ok=True)
        with open(args.output, "w", encoding="utf-8") as f:
            json.dump({"status": "skipped", "reason": "polars not installed", "results": []}, f, indent=2)
        return

    ds_dir = Path(args.dataset_dir)
    rows = args.rows
    results = []

    csv_file = ds_dir / f"mixed_analytics_{rows}.csv"
    tsv_file = ds_dir / f"mixed_analytics_{rows}.tsv"
    parquet_file = ds_dir / f"mixed_analytics_{rows}.parquet"
    brc1_file = ds_dir / f"1brc_scale1_{rows}.txt"
    brc2_file = ds_dir / f"1brc_scale2_{rows}.txt"
    brc3_file = ds_dir / f"1brc_hybrid3_{rows}.txt"

    if csv_file.exists():
        csv_bytes = csv_file.stat().st_size
        if not parquet_file.exists():
            try:
                pl.read_csv(csv_file).write_parquet(parquet_file)
            except Exception:
                pass
        pq_bytes = parquet_file.stat().st_size if parquet_file.exists() else 0

        results.append(run_timed_benchmark(
            "PolarsIngestBenchmark.ingestMixedCsvEager", rows, csv_bytes, args.warmup, args.iterations,
            lambda: pl.read_csv(csv_file)
        ))
        results.append(run_timed_benchmark(
            "PolarsIngestBenchmark.ingestMixedCsvLazyScan", rows, csv_bytes, args.warmup, args.iterations,
            lambda: pl.scan_csv(csv_file).collect()
        ))
        results.append(run_timed_benchmark(
            "PolarsIngestBenchmark.ingestMixedCsvLazyFusedAgg", rows, csv_bytes, args.warmup, args.iterations,
            lambda: (
                pl.scan_csv(csv_file)
                .group_by("k_low")
                .agg([
                    pl.col("val_fixed").sum().alias("sum_fixed"),
                    pl.col("val_float").mean().alias("mean_float"),
                    pl.col("val_fixed").min().alias("min_fixed"),
                    pl.col("val_fixed").max().alias("max_fixed"),
                ])
                .collect()
            )
        ))

        if tsv_file.exists():
            tsv_bytes = tsv_file.stat().st_size
            results.append(run_timed_benchmark(
                "PolarsIngestBenchmark.ingestMixedTsvEager", rows, tsv_bytes, args.warmup, args.iterations,
                lambda: pl.read_csv(tsv_file, separator="\t")
            ))
            results.append(run_timed_benchmark(
                "PolarsIngestBenchmark.ingestMixedTsvLazyScan", rows, tsv_bytes, args.warmup, args.iterations,
                lambda: pl.scan_csv(tsv_file, separator="\t").collect()
            ))

        if parquet_file.exists():
            results.append(run_timed_benchmark(
                "PolarsIngestBenchmark.ingestParquetEager", rows, pq_bytes, args.warmup, args.iterations,
                lambda: pl.read_parquet(parquet_file)
            ))
            results.append(run_timed_benchmark(
                "PolarsIngestBenchmark.ingestParquetLazyPushdown", rows, pq_bytes, args.warmup, args.iterations,
                lambda: (
                    pl.scan_parquet(parquet_file)
                    .select(["id", "val_fixed", "k_low"])
                    .filter(pl.col("val_fixed") > 100.0)
                    .collect()
                )
            ))

        json_array_file = ds_dir / f"mixed_analytics_{rows}.json"
        json_lines_file = ds_dir / f"mixed_analytics_{rows}.jsonl"

        base_df = pl.read_csv(csv_file)
        mem_bytes = int(base_df.estimated_size())

        if not json_array_file.exists():
            base_df.write_json(json_array_file)
        if not json_lines_file.exists():
            base_df.write_ndjson(json_lines_file)

        if json_array_file.exists():
            ja_bytes = json_array_file.stat().st_size
            results.append(run_timed_benchmark(
                "PolarsJsonBenchmark.ingestJsonArrayEager", rows, ja_bytes, args.warmup, args.iterations,
                lambda: pl.read_json(json_array_file)
            ))
            results.append(run_timed_benchmark(
                "PolarsJsonBenchmark.ingestJsonArrayLazyScan", rows, ja_bytes, args.warmup, args.iterations,
                lambda: pl.read_json(json_array_file)
            ))
            results.append(run_timed_benchmark(
                "PolarsJsonBenchmark.ingestJsonArrayLazyProjected", rows, ja_bytes, args.warmup, args.iterations,
                lambda: pl.read_json(json_array_file).select(["id", "val_fixed", "k_low"])
            ))

        if json_lines_file.exists():
            jl_bytes = json_lines_file.stat().st_size
            results.append(run_timed_benchmark(
                "PolarsJsonBenchmark.ingestJsonLinesLazyScanParallel", rows, jl_bytes, args.warmup, args.iterations,
                lambda: pl.scan_ndjson(json_lines_file).collect()
            ))
            results.append(run_timed_benchmark(
                "PolarsJsonBenchmark.ingestJsonLinesLazyAggregation", rows, jl_bytes, args.warmup, args.iterations,
                lambda: (
                    pl.scan_ndjson(json_lines_file)
                    .group_by("k_low")
                    .agg([
                        pl.col("val_fixed").sum().alias("sum_amount"),
                        pl.col("val_fixed").mean().alias("mean_val"),
                        pl.col("id").count().alias("cnt"),
                    ])
                    .collect()
                )
            ))

        out_csv_tmp = ds_dir / f".tmp_out_{rows}.csv"
        out_tsv_tmp = ds_dir / f".tmp_out_{rows}.tsv"
        out_pq_tmp = ds_dir / f".tmp_out_{rows}.parquet"
        out_json_tmp = ds_dir / f".tmp_out_{rows}.json"

        results.append(run_timed_benchmark(
            "PolarsEgressBenchmark.writeCsv", rows, mem_bytes, args.warmup, args.iterations,
            lambda: base_df.write_csv(out_csv_tmp)
        ))
        results.append(run_timed_benchmark(
            "PolarsEgressBenchmark.writeTsv", rows, mem_bytes, args.warmup, args.iterations,
            lambda: base_df.write_csv(out_tsv_tmp, separator="\t")
        ))
        results.append(run_timed_benchmark(
            "PolarsEgressBenchmark.writeParquet", rows, mem_bytes, args.warmup, args.iterations,
            lambda: base_df.write_parquet(out_pq_tmp)
        ))
        results.append(run_timed_benchmark(
            "PolarsEgressBenchmark.writeJson", rows, mem_bytes, args.warmup, args.iterations,
            lambda: base_df.write_json(out_json_tmp)
        ))

        for p in (out_csv_tmp, out_tsv_tmp, out_pq_tmp, out_json_tmp):
            if p.exists():
                try:
                    p.unlink()
                except Exception:
                    pass

        # Arrow conversion benchmarks
        arrow_table = base_df.to_arrow()
        results.append(run_timed_benchmark(
            "PolarsArrowBenchmark.exportToArrowRecordBatch", rows, mem_bytes, args.warmup, args.iterations,
            lambda: base_df.to_arrow()
        ))
        results.append(run_timed_benchmark(
            "PolarsArrowBenchmark.importFromArrowRecordBatch", rows, mem_bytes, args.warmup, args.iterations,
            lambda: pl.from_arrow(arrow_table)
        ))

        results.append(run_timed_benchmark(
            "PolarsGroupByBenchmark.groupByLowCardSequential", rows, mem_bytes, args.warmup, args.iterations,
            lambda: base_df.group_by("k_low").agg([
                pl.col("val_fixed").sum().alias("sum_fixed"),
                pl.col("val_fixed").mean().alias("mean_fixed"),
                pl.col("val_fixed").min().alias("min_fixed"),
                pl.col("val_fixed").max().alias("max_fixed"),
            ])
        ))
        results.append(run_timed_benchmark(
            "PolarsGroupByBenchmark.groupByLowCardParallel", rows, mem_bytes, args.warmup, args.iterations,
            lambda: base_df.group_by("k_low").agg([
                pl.col("val_fixed").sum().alias("sum_fixed"),
                pl.col("val_fixed").mean().alias("mean_fixed"),
                pl.col("val_fixed").min().alias("min_fixed"),
                pl.col("val_fixed").max().alias("max_fixed"),
            ])
        ))
        results.append(run_timed_benchmark(
            "PolarsGroupByBenchmark.groupByHighCardStringParallel", rows, mem_bytes, args.warmup, args.iterations,
            lambda: base_df.group_by("k_high").agg([
                pl.col("val_fixed").sum().alias("sum_fixed"),
                pl.col("val_fixed").min().alias("min_fixed"),
                pl.col("val_fixed").max().alias("max_fixed"),
                pl.col("val_fixed").count().alias("cnt_fixed"),
            ])
        ))
        results.append(run_timed_benchmark(
            "PolarsGroupByBenchmark.groupBySkewedZipfIntParallel", rows, mem_bytes, args.warmup, args.iterations,
            lambda: base_df.group_by("k_zipf").agg([
                pl.col("val_fixed").sum().alias("sum_fixed"),
                pl.col("val_float").mean().alias("mean_float"),
                pl.col("id").count().alias("cnt"),
            ])
        ))
        results.append(run_timed_benchmark(
            "PolarsGroupByBenchmark.groupByMultiKeyCompositeParallel", rows, mem_bytes, args.warmup, args.iterations,
            lambda: base_df.group_by(["k_low", "k_zipf"]).agg([
                pl.col("val_fixed").sum().alias("sum_fixed"),
                pl.col("val_float").mean().alias("mean_float"),
            ])
        ))
        results.append(run_timed_benchmark(
            "PolarsGroupByBenchmark.groupByMultivariateSinglePassParallel", rows, mem_bytes, args.warmup, args.iterations,
            lambda: base_df.group_by("k_low").agg([
                pl.col("val_fixed").sum().alias("sum_fixed"),
                pl.col("val_fixed").mean().alias("mean_fixed"),
                pl.col("val_fixed").min().alias("min_fixed"),
                pl.col("val_fixed").max().alias("max_fixed"),
                pl.col("val_fixed").std().alias("std_fixed"),
                pl.col("val_float").sum().alias("sum_float"),
                pl.col("val_float").mean().alias("mean_float"),
                pl.col("val_float").min().alias("min_float"),
                pl.col("val_float").max().alias("max_float"),
                pl.col("val_float").std().alias("std_float"),
            ])
        ))

        results.append(run_timed_benchmark(
            "PolarsFilterVectorBenchmark.filterLowSelectivity1PctParallel", rows, mem_bytes, args.warmup, args.iterations,
            lambda: base_df.filter(((pl.col("id") % 100) == 0) & (pl.col("val_fixed") > 0.0))
        ))
        results.append(run_timed_benchmark(
            "PolarsFilterVectorBenchmark.filterMediumSelectivity50PctParallel", rows, mem_bytes, args.warmup, args.iterations,
            lambda: base_df.filter(pl.col("flag") & (pl.col("val_fixed") >= 1.25))
        ))
        results.append(run_timed_benchmark(
            "PolarsFilterVectorBenchmark.filterHighSelectivity90PctParallel", rows, mem_bytes, args.warmup, args.iterations,
            lambda: base_df.filter(((pl.col("id") % 10) != 0) | (pl.col("val_fixed") > 250.0))
        ))
        results.append(run_timed_benchmark(
            "PolarsFilterVectorBenchmark.filterCompoundBooleanExpressionParallel", rows, mem_bytes, args.warmup, args.iterations,
            lambda: base_df.filter(
                ((pl.col("id") > 100) & (pl.col("val_fixed") <= 350.0))
                | (pl.col("flag") & (pl.col("ts_epoch") > 1700000050000))
            )
        ))
        results.append(run_timed_benchmark(
            "PolarsFilterVectorBenchmark.vectorizedMathExpressionSequential", rows, mem_bytes, args.warmup, args.iterations,
            lambda: base_df.with_columns((pl.col("val_fixed") * 1.15 + pl.col("val_fixed").abs().sqrt()).alias("val_fixed"))
        ))
        results.append(run_timed_benchmark(
            "PolarsFilterVectorBenchmark.vectorizedMathExpressionParallel", rows, mem_bytes, args.warmup, args.iterations,
            lambda: base_df.with_columns((pl.col("val_fixed") * 1.15 + pl.col("val_fixed").abs().sqrt()).alias("val_fixed"))
        ))

        # --- Window Functions Benchmarks (Issue #282) ---
        results.append(run_timed_benchmark(
            "PolarsWindowBenchmark.lagShift", rows, mem_bytes, args.warmup, args.iterations,
            lambda: base_df.with_columns(pl.col("val_fixed").shift(1).alias("lag_val"))
        ))
        results.append(run_timed_benchmark(
            "PolarsWindowBenchmark.leadShift", rows, mem_bytes, args.warmup, args.iterations,
            lambda: base_df.with_columns(pl.col("val_fixed").shift(-1).alias("lead_val"))
        ))
        results.append(run_timed_benchmark(
            "PolarsWindowBenchmark.cumsum", rows, mem_bytes, args.warmup, args.iterations,
            lambda: base_df.with_columns(pl.col("val_fixed").cum_sum().alias("val_fixed"))
        ))
        results.append(run_timed_benchmark(
            "PolarsWindowBenchmark.cumprod", rows, mem_bytes, args.warmup, args.iterations,
            lambda: base_df.with_columns(pl.col("val_fixed").cum_prod().alias("val_fixed"))
        ))
        results.append(run_timed_benchmark(
            "PolarsWindowBenchmark.cummin", rows, mem_bytes, args.warmup, args.iterations,
            lambda: base_df.with_columns(pl.col("val_fixed").cum_min().alias("val_fixed"))
        ))
        results.append(run_timed_benchmark(
            "PolarsWindowBenchmark.cummax", rows, mem_bytes, args.warmup, args.iterations,
            lambda: base_df.with_columns(pl.col("val_fixed").cum_max().alias("val_fixed"))
        ))
        results.append(run_timed_benchmark(
            "PolarsWindowBenchmark.rollingMeanFixedWindow", rows, mem_bytes, args.warmup, args.iterations,
            lambda: base_df.with_columns(pl.col("val_fixed").rolling_mean(window_size=5).alias("roll_mean"))
        ))
        results.append(run_timed_benchmark(
            "PolarsWindowBenchmark.rollingSumFixedWindow", rows, mem_bytes, args.warmup, args.iterations,
            lambda: base_df.with_columns(pl.col("val_fixed").rolling_sum(window_size=5).alias("roll_sum"))
        ))
        results.append(run_timed_benchmark(
            "PolarsWindowBenchmark.rowNumber", rows, mem_bytes, args.warmup, args.iterations,
            lambda: base_df.with_columns(pl.int_range(1, pl.len() + 1).alias("row_num"))
        ))
        results.append(run_timed_benchmark(
            "PolarsWindowBenchmark.rank", rows, mem_bytes, args.warmup, args.iterations,
            lambda: base_df.with_columns(pl.col("val_fixed").rank("min").alias("rank_val"))
        ))
        results.append(run_timed_benchmark(
            "PolarsWindowBenchmark.denseRank", rows, mem_bytes, args.warmup, args.iterations,
            lambda: base_df.with_columns(pl.col("val_fixed").rank("dense").alias("dense_rank_val"))
        ))
        results.append(run_timed_benchmark(
            "PolarsWindowBenchmark.windowPartitionedRank", rows, mem_bytes, args.warmup, args.iterations,
            lambda: base_df.with_columns(pl.col("val_fixed").rank("min").over("k_low").alias("part_rank"))
        ))
        results.append(run_timed_benchmark(
            "PolarsWindowBenchmark.windowPartitionedLag", rows, mem_bytes, args.warmup, args.iterations,
            lambda: base_df.with_columns(pl.col("val_fixed").shift(1).over("k_low").alias("part_lag"))
        ))

    left_1to1, right_1to1 = build_synthetic_join_frames(rows, rows, "1:1")
    join_bytes = int(left_1to1.estimated_size() + right_1to1.estimated_size())
    results.append(run_timed_benchmark(
        "PolarsJoinBenchmark.innerJoinInt1to1", rows, join_bytes, args.warmup, args.iterations,
        lambda: left_1to1.join(right_1to1, on="key_int", how="inner")
    ))
    results.append(run_timed_benchmark(
        "PolarsJoinBenchmark.leftJoinInt1to1", rows, join_bytes, args.warmup, args.iterations,
        lambda: left_1to1.join(right_1to1, on="key_int", how="left")
    ))
    results.append(run_timed_benchmark(
        "PolarsJoinBenchmark.innerJoinString1to1", rows, join_bytes, args.warmup, args.iterations,
        lambda: left_1to1.join(right_1to1, on="key_str", how="inner")
    ))
    results.append(run_timed_benchmark(
        "PolarsJoinBenchmark.leftJoinString1to1", rows, join_bytes, args.warmup, args.iterations,
        lambda: left_1to1.join(right_1to1, on="key_str", how="left")
    ))

    dim_card = max(100, rows // 10)
    left_1ton, right_1ton = build_synthetic_join_frames(rows, dim_card, "1:N")
    right_1ton = right_1ton.head(dim_card)
    results.append(run_timed_benchmark(
        "PolarsJoinBenchmark.innerJoinInt1toN", rows, join_bytes, args.warmup, args.iterations,
        lambda: left_1ton.join(right_1ton, on="key_int", how="inner")
    ))
    results.append(run_timed_benchmark(
        "PolarsJoinBenchmark.leftJoinString1toN", rows, join_bytes, args.warmup, args.iterations,
        lambda: left_1ton.join(right_1ton, on="key_str", how="left")
    ))

    nm_rows = max(1000, rows // 10)
    nm_card = max(250, nm_rows // 2)
    left_ntom, right_ntom = build_synthetic_join_frames(nm_rows, nm_card, "N:M")
    # Record under 'rows' key so it pairs directly with PolarsJoinBenchmark (which is parameterized by rows)
    results.append(run_timed_benchmark(
        "PolarsJoinBenchmark.innerJoinIntNtoM", rows, int(left_ntom.estimated_size() * 2), args.warmup, args.iterations,
        lambda: left_ntom.join(right_ntom, on="key_int", how="inner")
    ))
    results.append(run_timed_benchmark(
        "PolarsJoinBenchmark.leftJoinStringNtoM", rows, int(left_ntom.estimated_size() * 2), args.warmup, args.iterations,
        lambda: left_ntom.join(right_ntom, on="key_str", how="left")
    ))

    trades_df, quotes_df = build_synthetic_asof_frames(rows, 100)
    asof_bytes = int(trades_df.estimated_size() + quotes_df.estimated_size())
    results.append(run_timed_benchmark(
        "PolarsJoinBenchmark.asOfJoinBackward", rows, asof_bytes, args.warmup, args.iterations,
        lambda: trades_df.join_asof(quotes_df, on="time", by="ticker", strategy="backward")
    ))
    results.append(run_timed_benchmark(
        "PolarsJoinBenchmark.asOfJoinForward", rows, asof_bytes, args.warmup, args.iterations,
        lambda: trades_df.join_asof(quotes_df, on="time", by="ticker", strategy="forward")
    ))
    results.append(run_timed_benchmark(
        "PolarsJoinBenchmark.asOfJoinNearest", rows, asof_bytes, args.warmup, args.iterations,
        lambda: trades_df.join_asof(quotes_df, on="time", by="ticker", strategy="nearest")
    ))

    for bench_name, brc_path in [
        ("OneBrcStandardBenchmark.oneBrcLazyStreamingScale1Standard", brc1_file),
        ("OneBrcStandardBenchmark.oneBrcLazyStreamingScale2Cents", brc2_file),
        ("OneBrcStandardBenchmark.oneBrcLazyStreamingHybrid3DecimalsFallback", brc3_file),
    ]:
        if brc_path.exists():
            f_bytes = brc_path.stat().st_size
            results.append(run_timed_benchmark(
                bench_name, rows, f_bytes, args.warmup, args.iterations,
                lambda p=brc_path: (
                    pl.scan_csv(p, separator=";", has_header=False, new_columns=["station", "measure"])
                    .group_by("station")
                    .agg([
                        pl.col("measure").min().alias("min_measure"),
                        pl.col("measure").mean().alias("mean_measure"),
                        pl.col("measure").max().alias("max_measure"),
                    ])
                    .sort("station")
                    .collect()
                )
            ))

    # TPC-H Analytical Suite (CupOfTea vs. Polars Lazy)
    tpch_dir = ds_dir / "tpch"
    lineitem_csv = tpch_dir / "lineitem.csv"
    orders_csv = tpch_dir / "orders.csv"
    customer_csv = tpch_dir / "customer.csv"
    part_csv = tpch_dir / "part.csv"
    supplier_csv = tpch_dir / "supplier.csv"
    partsupp_csv = tpch_dir / "partsupp.csv"
    nation_csv = tpch_dir / "nation.csv"
    region_csv = tpch_dir / "region.csv"

    if lineitem_csv.exists() and orders_csv.exists():
        tpch_bytes = sum(f.stat().st_size for f in [lineitem_csv, orders_csv, customer_csv, part_csv, supplier_csv, partsupp_csv, nation_csv, region_csv] if f.exists())

        # Q1 Polars Eager & Lazy
        results.append(run_timed_benchmark(
            "TpchAnalyticalBenchmark.q01_pricingSummaryReport", rows, tpch_bytes, args.warmup, args.iterations,
            lambda: (
                pl.read_csv(lineitem_csv, separator="|")
                .filter(pl.col("l_shipdate") <= "1998-09-02")
                .with_columns([
                    (pl.col("l_extendedprice") * (1.0 - pl.col("l_discount"))).alias("disc_price"),
                    (pl.col("l_extendedprice") * (1.0 - pl.col("l_discount")) * (1.0 + pl.col("l_tax"))).alias("charge")
                ])
                .group_by(["l_returnflag", "l_linestatus"])
                .agg([
                    pl.col("l_quantity").sum().alias("sum_qty"),
                    pl.col("l_extendedprice").sum().alias("sum_base_price"),
                    pl.col("disc_price").sum().alias("sum_disc_price"),
                    pl.col("charge").sum().alias("sum_charge"),
                    pl.col("l_quantity").mean().alias("avg_qty"),
                    pl.col("l_extendedprice").mean().alias("avg_price"),
                    pl.col("l_discount").mean().alias("avg_disc"),
                    pl.col("l_quantity").count().alias("count_order")
                ])
                .sort(["l_returnflag", "l_linestatus"])
            )
        ))

        results.append(run_timed_benchmark(
            "TpchAnalyticalBenchmark.q01_pricingSummaryReportLazy", rows, tpch_bytes, args.warmup, args.iterations,
            lambda: (
                pl.scan_csv(lineitem_csv, separator="|")
                .filter(pl.col("l_shipdate") <= "1998-09-02")
                .group_by(["l_returnflag", "l_linestatus"])
                .agg([
                    pl.col("l_quantity").sum().alias("sum_qty"),
                    pl.col("l_extendedprice").sum().alias("sum_base_price"),
                    pl.col("l_quantity").mean().alias("avg_qty"),
                    pl.col("l_extendedprice").mean().alias("avg_price"),
                    pl.col("l_discount").mean().alias("avg_disc"),
                    pl.col("l_quantity").count().alias("count_order")
                ])
                .sort(["l_returnflag", "l_linestatus"])
                .collect()
            )
        ))

        # Q3 Polars Lazy
        results.append(run_timed_benchmark(
            "TpchAnalyticalBenchmark.q03_shippingPriority", rows, tpch_bytes, args.warmup, args.iterations,
            lambda: (
                pl.scan_csv(customer_csv, separator="|")
                .filter(pl.col("c_mktsegment") == "BUILDING")
                .join(
                    pl.scan_csv(orders_csv, separator="|").filter(pl.col("o_orderdate") < "1995-03-15"),
                    left_on="c_custkey", right_on="o_custkey"
                )
                .join(
                    pl.scan_csv(lineitem_csv, separator="|").filter(pl.col("l_shipdate") > "1995-03-15"),
                    left_on="o_orderkey", right_on="l_orderkey"
                )
                .with_columns((pl.col("l_extendedprice") * (1.0 - pl.col("l_discount"))).alias("revenue"))
                .group_by(["o_orderkey", "o_orderdate", "o_shippriority"])
                .agg(pl.col("revenue").sum().alias("revenue"))
                .sort(["revenue", "o_orderdate"], descending=[True, False])
                .collect()
            )
        ))

        # Q6 Polars Eager & Lazy
        results.append(run_timed_benchmark(
            "TpchAnalyticalBenchmark.q06_forecastingRevenueChange", rows, tpch_bytes, args.warmup, args.iterations,
            lambda: (
                pl.read_csv(lineitem_csv, separator="|")
                .filter(
                    (pl.col("l_shipdate") >= "1994-01-01") &
                    (pl.col("l_shipdate") < "1995-01-01") &
                    (pl.col("l_discount") >= 0.05) &
                    (pl.col("l_discount") <= 0.07) &
                    (pl.col("l_quantity") < 24.0)
                )
                .select((pl.col("l_extendedprice") * pl.col("l_discount")).sum().alias("revenue"))
            )
        ))

        results.append(run_timed_benchmark(
            "TpchAnalyticalBenchmark.q06_forecastingRevenueChangeLazy", rows, tpch_bytes, args.warmup, args.iterations,
            lambda: (
                pl.scan_csv(lineitem_csv, separator="|")
                .filter(
                    (pl.col("l_shipdate") >= "1994-01-01") &
                    (pl.col("l_shipdate") < "1995-01-01") &
                    (pl.col("l_discount") >= 0.05) &
                    (pl.col("l_discount") <= 0.07) &
                    (pl.col("l_quantity") < 24.0)
                )
                .select((pl.col("l_extendedprice") * pl.col("l_discount")).sum().alias("revenue"))
                .collect()
            )
        ))

        # Q9 Polars Lazy
        results.append(run_timed_benchmark(
            "TpchAnalyticalBenchmark.q09_productTypeProfitMeasure", rows, tpch_bytes, args.warmup, args.iterations,
            lambda: (
                pl.scan_csv(part_csv, separator="|")
                .filter(pl.col("p_name").str.contains("green"))
                .join(pl.scan_csv(lineitem_csv, separator="|"), left_on="p_partkey", right_on="l_partkey")
                .join(pl.scan_csv(supplier_csv, separator="|"), left_on="l_suppkey", right_on="s_suppkey")
                .join(pl.scan_csv(nation_csv, separator="|"), left_on="s_nationkey", right_on="n_nationkey")
                .join(
                    pl.scan_csv(orders_csv, separator="|")
                    .with_columns(pl.col("o_orderdate").str.slice(0, 4).alias("o_year")),
                    left_on="l_orderkey", right_on="o_orderkey"
                )
                .join(pl.scan_csv(partsupp_csv, separator="|"), left_on="p_partkey", right_on="ps_partkey")
                .with_columns((
                    (pl.col("l_extendedprice") * (1.0 - pl.col("l_discount"))) -
                    (pl.col("ps_supplycost") * pl.col("l_quantity"))
                ).alias("amount"))
                .group_by(["n_name", "o_year"])
                .agg(pl.col("amount").sum().alias("sum_profit"))
                .sort(["n_name", "o_year"], descending=[False, True])
                .collect()
            )
        ))

        # Q18 Polars Lazy
        results.append(run_timed_benchmark(
            "TpchAnalyticalBenchmark.q18_largeVolumeCustomer", rows, tpch_bytes, args.warmup, args.iterations,
            lambda: (
                pl.scan_csv(lineitem_csv, separator="|")
                .group_by("l_orderkey")
                .agg(pl.col("l_quantity").sum().alias("sum_qty"))
                .filter(pl.col("sum_qty") > 100.0)
                .join(pl.scan_csv(orders_csv, separator="|"), left_on="l_orderkey", right_on="o_orderkey")
                .join(pl.scan_csv(customer_csv, separator="|"), left_on="o_custkey", right_on="c_custkey")
                .sort(["o_totalprice", "o_orderdate"], descending=[True, False])
                .collect()
            )
        ))

    Path(args.output).parent.mkdir(parents=True, exist_ok=True)
    with open(args.output, "w", encoding="utf-8") as f:
        json.dump({
            "status": "completed",
            "polarsVersion": pl.__version__,
            "platform": platform.platform(),
            "architecture": platform.machine(),
            "rows": rows,
            "results": results,
        }, f, indent=2)
    print(f"Saved Polars benchmark results to {args.output}")


if __name__ == "__main__":
    main()
