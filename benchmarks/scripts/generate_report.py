#!/usr/bin/env python3
"""
Automated Comparative Benchmark Report Generator (Issue #251).

Consumes:
  - JMH JSON results (jmh-results.json, including -prof gc and HardwareCountersProfiler)
  - Polars JSON results (polars-results.json, including proc_pid_rusage hardware counters)
  - Optional Linux perf / macOS /usr/bin/time -l logs (perf-stat.txt)
  - Optional macOS Apple Silicon kperf PMU JSON (kperf-pmu.json)

Generates a consolidated Markdown report comparing CupOfTea vs. Polars across:
  - Latency (ms ± SD), Throughput (ops/s & Mrows/s), Latency per Row (ns/row)
  - Normalized Data Bandwidth (MB/s)
  - Hardware Microarchitectural Counters: Cycles/Row, Insns/Row, IPC (Insn/Cycle)
  - Memory & GC Diagnostics: Peak RSS (MB), Allocation Rate (MB/s | B/row), GC Count/Time
  - PMU Cache & Branch Diagnostics: L1d Miss, LLC Miss, Branch Miss
  - Relative Speedup (CupOfTea vs. Polars)
"""

import argparse
import json
import platform
import re
from datetime import datetime, timezone
from pathlib import Path


def parse_perf_log(perf_path: Path, kperf_path: Path | None = None) -> dict:
    info = {
        "ipc": "N/A",
        "l1_miss": "N/A",
        "llc_miss": "N/A",
        "branch_miss": "N/A",
        "peak_rss_mb": "N/A",
        "peak_footprint_mb": "N/A",
        "total_cycles": None,
        "total_insns": None,
        "page_faults": None,
        "page_reclaims": None,
        "pmu_backend": "Linux perf" if platform.system() == "Linux" else "macOS proc_pid_rusage (RUSAGE_INFO_V4)",
        "pmu_note": "",
    }

    if kperf_path and kperf_path.exists():
        try:
            kdata = json.loads(kperf_path.read_text(encoding="utf-8"))
            status = kdata.get("status", "")
            metrics = kdata.get("metrics", {})
            if status == "ok":
                info["pmu_backend"] = "macOS Apple Silicon kperf (PMC0..PMC9 privileged)"
                if "ipc" in metrics:
                    info["ipc"] = f"{metrics['ipc']:.2f}"
                if "l1d_miss_per_1k_inst" in metrics:
                    info["l1_miss"] = f"{metrics['l1d_miss_per_1k_inst']:.2f} MPKI"
                if "branch_miss_pct" in metrics:
                    info["branch_miss"] = metrics["branch_miss_pct"]
            elif "requires_sudo" in status:
                info["l1_miss"] = "Req. sudo (PMC2)"
                info["llc_miss"] = "N/A (Apple M-series SLC)"
                info["branch_miss"] = "Req. sudo (PMC5)"
                info["pmu_note"] = (
                    "On macOS Apple Silicon (Darwin/XNU), FIXED_CYCLES (PMC0) and FIXED_INSTRUCTIONS (PMC1) "
                    "are exposed unprivileged via proc_pid_rusage(RUSAGE_INFO_V4), providing exact IPC, "
                    "Cycles/Row, and Insns/Row. Configurable PMCs (PMC2..PMC9: L1D_CACHE_MISS_LD_NONSPEC, "
                    "BRANCH_MISPRED_NONSPEC) require root ('sudo ./benchmarks/scripts/run_perf_stat.sh') or Xcode xctrace."
                )
        except Exception:
            pass

    if not perf_path or not perf_path.exists():
        return info

    text = perf_path.read_text(encoding="utf-8", errors="replace")

    # 1. Linux perf stat patterns
    ipc_match = re.search(r"([0-9]+[.,][0-9]+)\s+insn per cycle", text)
    if ipc_match:
        info["ipc"] = ipc_match.group(1).replace(",", ".")

    l1_match = re.search(r"([0-9]+[.,][0-9]+%)\s+of all L1-dcache", text)
    if l1_match:
        info["l1_miss"] = l1_match.group(1)

    llc_match = re.search(r"([0-9]+[.,][0-9]+%)\s+of all LL-cache", text)
    if llc_match:
        info["llc_miss"] = llc_match.group(1)

    br_match = re.search(r"([0-9]+[.,][0-9]+%)\s+of all branches", text)
    if br_match:
        info["branch_miss"] = br_match.group(1)

    # 2. macOS /usr/bin/time -l patterns
    rss_mac = re.search(r"([0-9]+)\s+maximum resident set size", text)
    if rss_mac:
        rss_bytes = int(rss_mac.group(1))
        if rss_bytes > 10_000_000:
            info["peak_rss_mb"] = f"{rss_bytes / (1024.0 * 1024.0):.1f}"
        else:
            info["peak_rss_mb"] = f"{rss_bytes / 1024.0:.1f}"

    fp_mac = re.search(r"([0-9]+)\s+peak memory footprint", text)
    if fp_mac:
        fp_bytes = int(fp_mac.group(1))
        info["peak_footprint_mb"] = f"{fp_bytes / (1024.0 * 1024.0):.1f}"

    insns_mac = re.search(r"([0-9]+)\s+instructions retired", text)
    cycles_mac = re.search(r"([0-9]+)\s+cycles elapsed", text)
    if insns_mac and cycles_mac:
        insns_val = int(insns_mac.group(1))
        cycles_val = int(cycles_mac.group(1))
        info["total_insns"] = insns_val
        info["total_cycles"] = cycles_val
        if cycles_val > 0 and info["ipc"] == "N/A":
            info["ipc"] = f"{insns_val / cycles_val:.2f}"

    reclaims_mac = re.search(r"([0-9]+)\s+page reclaims", text)
    if reclaims_mac:
        info["page_reclaims"] = int(reclaims_mac.group(1))

    faults_mac = re.search(r"([0-9]+)\s+page faults", text)
    if faults_mac:
        info["page_faults"] = int(faults_mac.group(1))

    return info


def short_benchmark_name(full_name: str) -> str:
    parts = full_name.split(".")
    if len(parts) >= 2:
        return f"{parts[-2]}.{parts[-1]}"
    return full_name


def get_secondary_score(secondary: dict, key: str) -> float | None:
    for candidate in (key, f"·{key}"):
        if candidate in secondary and isinstance(secondary[candidate], dict):
            val = secondary[candidate].get("score")
            if val is not None and val == val:  # check not NaN
                return float(val)
    return None


def estimate_dataset_bytes(bname: str, rows: int, dataset_dir: Path | None, polars_entry: dict | None) -> int:
    if polars_entry and polars_entry.get("dataBytes"):
        return int(polars_entry["dataBytes"])
    if dataset_dir and dataset_dir.exists():
        if "OneBrc" in bname:
            p = dataset_dir / f"measurements_{rows}.txt"
            if p.exists():
                return p.stat().st_size
        elif "Ingest" in bname or "Egress" in bname:
            if "Csv" in bname:
                p = dataset_dir / f"mixed_{rows}.csv"
                if p.exists():
                    return p.stat().st_size
            elif "Tsv" in bname:
                p = dataset_dir / f"mixed_{rows}.tsv"
                if p.exists():
                    return p.stat().st_size
            elif "Parquet" in bname:
                p = dataset_dir / f"mixed_{rows}.parquet"
                if p.exists():
                    return p.stat().st_size
    return rows * 64


def load_jmh_results(jmh_path: Path) -> list:
    if not jmh_path or not jmh_path.exists():
        return []
    raw = json.loads(jmh_path.read_text(encoding="utf-8"))
    entries = []
    for item in raw:
        bname = short_benchmark_name(item.get("benchmark", ""))
        params = item.get("params", {})
        rows = int(params.get("rows", params.get("leftRows", params.get("size", 100000))))
        primary = item.get("primaryMetric", {})
        score = float(primary.get("score", 0.0))
        error = float(primary.get("scoreError", 0.0))
        if error != error:  # NaN check when measurementIterations < 3
            error = 0.0

        secondary = item.get("secondaryMetrics", {})
        alloc_rate = get_secondary_score(secondary, "gc.alloc.rate")
        alloc_norm = get_secondary_score(secondary, "gc.alloc.rate.norm")
        gc_count = get_secondary_score(secondary, "gc.count")
        gc_time = get_secondary_score(secondary, "gc.time")

        hw_ipc = get_secondary_score(secondary, "hw.ipc")
        hw_cycles_op = get_secondary_score(secondary, "hw.cycles.per.op")
        hw_insns_op = get_secondary_score(secondary, "hw.insns.per.op")
        hw_rss_mb = get_secondary_score(secondary, "hw.rss.peak.mb")

        alloc_str = "N/A"
        if alloc_rate is not None and alloc_norm is not None:
            b_per_row = float(alloc_norm) / max(1, rows)
            alloc_str = f"{float(alloc_rate):.1f} | {b_per_row:.2f}"

        gc_str = "0 / 0ms"
        if gc_count is not None and gc_count > 0:
            t_ms = int(gc_time) if gc_time is not None else 0
            gc_str = f"{int(gc_count)} / {t_ms}ms"

        ops_per_s = 1000.0 / score if score > 0 else 0.0
        mrows_per_s = (rows / 1_000_000.0) / (score / 1000.0) if score > 0 else 0.0
        ns_per_row = (score * 1_000_000.0) / max(1, rows)

        cycles_per_row = (hw_cycles_op / max(1, rows)) if hw_cycles_op is not None else None
        insns_per_row = (hw_insns_op / max(1, rows)) if hw_insns_op is not None else None

        entries.append({
            "key": (bname, rows),
            "benchmark": bname,
            "rows": rows,
            "params": params,
            "meanMs": score,
            "stdMs": error,
            "opsPerSec": ops_per_s,
            "mrowsPerSec": mrows_per_s,
            "nsPerRow": ns_per_row,
            "allocStr": alloc_str,
            "gcStr": gc_str,
            "ipc": f"{hw_ipc:.2f}" if hw_ipc is not None else None,
            "cyclesPerRow": f"{cycles_per_row:.1f}" if cycles_per_row is not None else "N/A",
            "insnsPerRow": f"{insns_per_row:.1f}" if insns_per_row is not None else "N/A",
            "peakRssMb": f"{hw_rss_mb:.1f}" if hw_rss_mb is not None else None,
        })
    return entries


def load_polars_results(polars_path: Path) -> dict:
    if not polars_path or not polars_path.exists():
        return {}
    raw = json.loads(polars_path.read_text(encoding="utf-8"))
    mapping = {}
    for item in raw.get("results", []):
        bname = item.get("benchmark", "")
        rows = int(item.get("rows", 0))
        mapping[(bname, rows)] = item
    return mapping


def generate_markdown(
    jmh_entries: list,
    polars_map: dict,
    perf_info: dict,
    dataset_dir: Path | None = None,
) -> str:
    now_str = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")
    cache_str = f"{perf_info['l1_miss']} / {perf_info['llc_miss']}"

    lines = [
        "# CupOfTea vs. Polars — Automated Performance Benchmark Report",
        "",
        f"- **Generated:** `{now_str}`",
        f"- **Deterministic Seed:** `0xCAFEBABE12345678L`",
        f"- **PMU Hardware Counter Backend:** `{perf_info['pmu_backend']}`",
        f"- **Process Summary (PMU / OS):** IPC=`{perf_info['ipc']}`, Peak RSS=`{perf_info['peak_rss_mb']} MB`, "
        f"L1d Miss=`{perf_info['l1_miss']}`, LLC Miss=`{perf_info['llc_miss']}`, Branch Miss=`{perf_info['branch_miss']}`",
        "",
        "## 1. Head-to-Head Benchmark Comparison Matrix",
        "",
        "| Workload / Cenário | Escala (Linhas) | Engine & Modo | Latência (ms ± DP) | Throughput (Mrows/s) | Latência/Linha (ns/row) | Vazão (MB/s) | Ciclos/Linha (cyc/row) | Insn/Linha (ins/row) | IPC | Pico RSS (MB) | Alocação JVM (MB/s \\| B/row) | GC (cnt/ms) | L1d / LLC Miss | Branch Miss | Speedup (vs Polars) |",
        "| :--- | :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |",
    ]

    for cot in jmh_entries:
        bname = cot["benchmark"]
        rows = cot["rows"]
        pl_entry = polars_map.get((bname, rows))

        data_bytes = estimate_dataset_bytes(bname, rows, dataset_dir, pl_entry)
        data_mb = data_bytes / (1024.0 * 1024.0)
        cot_mb_s = data_mb / (cot["meanMs"] / 1000.0) if cot["meanMs"] > 0 else 0.0

        cot_ipc = cot["ipc"] if cot["ipc"] is not None else perf_info["ipc"]
        cot_rss = cot["peakRssMb"] if cot["peakRssMb"] is not None else perf_info["peak_rss_mb"]

        speedup_str = "N/A"
        if pl_entry and cot["meanMs"] > 0:
            ratio = pl_entry["meanMs"] / cot["meanMs"]
            speedup_str = f"**`{ratio:.2f}x`**"

        lines.append(
            f"| `{bname}` | {rows:,} | **CupOfTea** | `{cot['meanMs']:.2f} ± {cot['stdMs']:.2f}` | "
            f"`{cot['mrowsPerSec']:.2f}` | `{cot['nsPerRow']:.1f}` | `{cot_mb_s:.1f}` | "
            f"`{cot['cyclesPerRow']}` | `{cot['insnsPerRow']}` | `{cot_ipc}` | `{cot_rss}` | "
            f"`{cot['allocStr']}` | `{cot['gcStr']}` | `{cache_str}` | `{perf_info['branch_miss']}` | {speedup_str} |"
        )

        if pl_entry:
            pl_ms = float(pl_entry["meanMs"])
            pl_std = float(pl_entry["stdMs"])
            pl_mrows = float(pl_entry.get("mrowsPerSec", (rows / 1_000_000.0) / (pl_ms / 1000.0) if pl_ms > 0 else 0.0))
            pl_ns_row = float(pl_entry.get("nsPerRow", (pl_ms * 1_000_000.0) / max(1, rows)))
            pl_mb_s = data_mb / (pl_ms / 1000.0) if pl_ms > 0 else 0.0
            pl_cyc = pl_entry.get("cyclesPerRow")
            pl_ins = pl_entry.get("insnsPerRow")
            pl_ipc = pl_entry.get("ipc")
            pl_rss = pl_entry.get("peakRssMb", "N/A")

            pl_cyc_str = f"{pl_cyc:.1f}" if pl_cyc is not None else "N/A"
            pl_ins_str = f"{pl_ins:.1f}" if pl_ins is not None else "N/A"
            pl_ipc_str = f"{pl_ipc:.2f}" if pl_ipc is not None else "N/A"

            lines.append(
                f"| `{bname}` | {rows:,} | Polars (`{pl_entry.get('version', '1.x')}`) | "
                f"`{pl_ms:.2f} ± {pl_std:.2f}` | `{pl_mrows:.2f}` | `{pl_ns_row:.1f}` | `{pl_mb_s:.1f}` | "
                f"`{pl_cyc_str}` | `{pl_ins_str}` | `{pl_ipc_str}` | `{pl_rss}` | "
                f"`Native (Rust)` | `N/A` | `{cache_str}` | `{perf_info['branch_miss']}` | `1.00x (Ref)` |"
            )

    lines.extend([
        "",
        "---",
        "",
        "## 2. Standard 1BRC (One Billion Row Challenge) Historical Baseline",
        "",
        "| Escala do Dataset | Tablesaw `0.43.1` | DFLib `1.3.0` | Pandas `2.2` | Polars (Eager `read_csv`) | Polars (Lazy `scan_csv`) | CupOfTea `0.15.1` *(Legacy)* | CupOfTea `0.20.8` *(Interleaved #237)* | **CupOfTea `0.20.9+` *(Pure Int + Fast Cap)*** |",
        "| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |",
        "| **1M linhas** (`~15 MB`) | 1.584 ms | 485 ms | 200 ms | 32 ms | 36 ms | 775 ms | 80 ms | **~75 ms** |",
        "| **10M linhas** (`~147 MB`) | 9.131 ms | 3.444 ms | 1.258 ms | 332 ms | 255 ms | 4.166 ms | 652 ms | **624 ms (0,62 s)** |",
        "| **100M linhas** (`~1,47 GB`) | 113,05 s *(OOM)* | OOM / DNF | 22,40 s | 7,35 s | 2,65 s | 15,34 s | 2,11 s *(méd: 2,25 s)* | **2,18 s (2.181 ms)** 🏆 |",
        "| **1B linhas** (`~14,71 GB`) | OOM / Crash | OOM / Crash | OOM / DNF | OOM / DNF | **19,42 s** | OOM / DNF | 31,55 s *(méd: 32,09 s)* | **31,12 s (31.124 ms)** |",
        "",
        "---",
        "",
        "## 3. Diagnóstico de Hardware (PMU), Cache L1d/LLC e Memória",
        "",
        "- **Contadores Fixos de Hardware (`PMC0` = Ciclos, `PMC1` = Instruções):** Coletados por iteração tanto no JMH (`HardwareCountersProfiler` via FFM `proc_pid_rusage(RUSAGE_INFO_V4)`) quanto no Polars (`ctypes` `proc_pid_rusage`), permitindo cálculo exato de **`IPC`**, **`Ciclos/Linha (cyc/row)`** e **`Instruções/Linha (ins/row)`** sem exigir privilégios de superusuário.",
        "- **Contadores Configuráveis de Cache L1d e Branch (`PMC2..PMC9`):**",
        "  - **Linux (`perf_event_open`):** `L1-dcache-load-misses`, `LLC-load-misses` e `branch-misses` são coletados via `./benchmarks/scripts/run_perf_stat.sh` quando `kernel.perf_event_paranoid <= 1`.",
        "  - **macOS Apple Silicon (`kperf.framework` / `kperfdata.framework`):** O kernel XNU bloqueia a programação dos registradores `PMC2..PMC9` (`L1D_CACHE_MISS_LD_NONSPEC`, `L1D_CACHE_MISS_ST_NONSPEC`, `BRANCH_MISPRED_NONSPEC`) para processos comuns (`kpc_force_all_ctrs_set` retorna `EPERM`). Para habilitar a coleta de L1d Miss (MPKI) e Branch Miss (%) no Apple Silicon, execute com `sudo`:",
        "    ```bash",
        "    sudo ./benchmarks/scripts/run_perf_stat.sh 1brc benchmarks/results/perf-stat.txt 100000",
        "    ```",
    ])
    if perf_info.get("pmu_note"):
        lines.append(f"- **Status PMU Atual:** {perf_info['pmu_note']}")
    lines.append("")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="Generate comparative Markdown report for CupOfTea vs. Polars")
    parser.add_argument("--jmh-json", type=str, default="benchmarks/results/jmh-results.json")
    parser.add_argument("--polars-json", type=str, default="benchmarks/results/polars-results.json")
    parser.add_argument("--perf-log", type=str, default="benchmarks/results/perf-stat.txt")
    parser.add_argument("--kperf-json", type=str, default="benchmarks/results/kperf-pmu.json")
    parser.add_argument("--dataset-dir", type=str, default="benchmarks/results/datasets")
    parser.add_argument("--output", type=str, default="benchmarks/results/comparison-report.md")
    args = parser.parse_args()

    jmh_entries = load_jmh_results(Path(args.jmh_json))
    polars_map = load_polars_results(Path(args.polars_json))
    perf_info = parse_perf_log(Path(args.perf_log), Path(args.kperf_json))
    dataset_dir = Path(args.dataset_dir) if args.dataset_dir else None

    md = generate_markdown(jmh_entries, polars_map, perf_info, dataset_dir)
    out_path = Path(args.output)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(md, encoding="utf-8")
    print(f"Generated comparative benchmark report at: {out_path}")


if __name__ == "__main__":
    main()
