#!/usr/bin/env python3
"""
Standalone 1BRC (One Billion Row Challenge) Polars Benchmark Runner (1M, 10M, 100M, 1B rows).
Compares Polars Lazy Streaming (scan_csv) and Polars Eager (read_csv) on <station>;<measure> files.
"""

import sys
import time
from pathlib import Path

import polars as pl


def run_1brc_polars(file_path: str, run_eager: bool = False):
    p = Path(file_path)
    if not p.exists():
        print(f"File not found: {file_path}")
        sys.exit(1)

    mb = p.stat().st_size / (1024.0 * 1024.0)
    print(f"--- Running Polars 1BRC on {p.name} ({mb:.1f} MB) ---")

    t0 = time.perf_counter()
    res = (
        pl.scan_csv(file_path, separator=";", has_header=False, new_columns=["station", "measure"])
        .group_by("station")
        .agg([
            pl.col("measure").min().alias("min"),
            pl.col("measure").mean().alias("mean"),
            pl.col("measure").max().alias("max"),
        ])
        .sort("station")
        .collect()
    )
    t1 = time.perf_counter()
    elapsed_ms = (t1 - t0) * 1000.0
    print(f"Polars (Lazy scan_csv): {elapsed_ms:.1f} ms ({elapsed_ms / 1000.0:.3f} s) | Stations={len(res)}")

    if run_eager:
        t0 = time.perf_counter()
        df = pl.read_csv(file_path, separator=";", has_header=False, new_columns=["station", "measure"])
        res_eager = (
            df.group_by("station")
            .agg([
                pl.col("measure").min().alias("min"),
                pl.col("measure").mean().alias("mean"),
                pl.col("measure").max().alias("max"),
            ])
            .sort("station")
        )
        t1 = time.perf_counter()
        eager_ms = (t1 - t0) * 1000.0
        print(f"Polars (Eager read_csv): {eager_ms:.1f} ms ({eager_ms / 1000.0:.3f} s) | Stations={len(res_eager)}")


if __name__ == "__main__":
    target_file = sys.argv[1] if len(sys.argv) > 1 else "measurements_10m.txt"
    eager_flag = "--eager" in sys.argv
    run_1brc_polars(target_file, eager_flag)
