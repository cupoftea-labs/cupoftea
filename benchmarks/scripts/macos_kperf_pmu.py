#!/usr/bin/env python3
"""
macOS Apple Silicon Hardware PMU Counter Profiler (Issue #251).

Uses Apple's private kperf.framework and kperfdata.framework (/usr/share/kpep/*.plist)
to program the Apple Silicon PMU configurable counters (PMC2..PMC9) in addition to
FIXED_CYCLES (PMC0) and FIXED_INSTRUCTIONS (PMC1):
  - FIXED_CYCLES
  - FIXED_INSTRUCTIONS
  - L1D_CACHE_MISS_LD_NONSPEC (or L1D_CACHE_MISS_LD)
  - L1D_CACHE_MISS_ST_NONSPEC (or L1D_CACHE_MISS_ST)
  - INST_BRANCH
  - BRANCH_MISPRED_NONSPEC

Note on macOS Darwin/XNU permissions:
  - PMC0 (FIXED_CYCLES) and PMC1 (FIXED_INSTRUCTIONS) are always enabled by the XNU
    kernel and readable without privileges via proc_pid_rusage(RUSAGE_INFO_V4).
  - Programming PMC2..PMC9 (L1D cache misses, branch mispredictions) via kpc_set_config
    requires root privileges (sudo) or Xcode Instruments (xctrace).
"""

import ctypes
import json
import os
import subprocess
import sys
import time

KPC_CLASS_FIXED = 0
KPC_CLASS_CONFIGURABLE = 1
KPC_CLASS_FIXED_MASK = 1 << KPC_CLASS_FIXED
KPC_CLASS_CONFIGURABLE_MASK = 1 << KPC_CLASS_CONFIGURABLE
KPC_MAX_COUNTERS = 32


class KpepEvent(ctypes.Structure):
    pass


class KpepDb(ctypes.Structure):
    pass


class KpepConfig(ctypes.Structure):
    pass


def collect_with_kperf(cmd: list[str]) -> dict:
    result = {
        "backend": "macos_kperf",
        "privileged": os.geteuid() == 0,
        "status": "unprivileged",
        "events": {},
        "metrics": {},
    }

    if sys.platform != "darwin":
        result["status"] = "unsupported_os"
        return result

    try:
        kperf = ctypes.CDLL("/System/Library/PrivateFrameworks/kperf.framework/kperf")
        kperfdata = ctypes.CDLL("/System/Library/PrivateFrameworks/kperfdata.framework/kperfdata")
    except OSError as exc:
        result["status"] = f"framework_load_error: {exc}"
        return result

    # Check if we have root permissions to program configurable PMCs
    if kperf.kpc_force_all_ctrs_set(1) != 0:
        result["status"] = "requires_sudo_for_l1d_and_branch_pmcs"
        return result

    try:
        kperfdata.kpep_db_create.argtypes = [ctypes.c_char_p, ctypes.POINTER(ctypes.POINTER(KpepDb))]
        kperfdata.kpep_db_create.restype = ctypes.c_int
        kperfdata.kpep_config_create.argtypes = [
            ctypes.POINTER(KpepDb),
            ctypes.POINTER(ctypes.POINTER(KpepConfig)),
        ]
        kperfdata.kpep_config_create.restype = ctypes.c_int
        kperfdata.kpep_config_force_counters.argtypes = [ctypes.POINTER(KpepConfig)]
        kperfdata.kpep_config_force_counters.restype = ctypes.c_int
        kperfdata.kpep_db_event.argtypes = [
            ctypes.POINTER(KpepDb),
            ctypes.c_char_p,
            ctypes.POINTER(ctypes.POINTER(KpepEvent)),
        ]
        kperfdata.kpep_db_event.restype = ctypes.c_int
        kperfdata.kpep_config_add_event.argtypes = [
            ctypes.POINTER(KpepConfig),
            ctypes.POINTER(ctypes.POINTER(KpepEvent)),
            ctypes.c_uint32,
            ctypes.POINTER(ctypes.c_uint32),
        ]
        kperfdata.kpep_config_add_event.restype = ctypes.c_int
        kperfdata.kpep_config_kpc_classes.argtypes = [
            ctypes.POINTER(KpepConfig),
            ctypes.POINTER(ctypes.c_uint32),
        ]
        kperfdata.kpep_config_kpc_classes.restype = ctypes.c_int
        kperfdata.kpep_config_kpc_count.argtypes = [
            ctypes.POINTER(KpepConfig),
            ctypes.POINTER(ctypes.c_size_t),
        ]
        kperfdata.kpep_config_kpc_count.restype = ctypes.c_int
        kperfdata.kpep_config_kpc.argtypes = [
            ctypes.POINTER(KpepConfig),
            ctypes.POINTER(ctypes.c_uint64),
            ctypes.c_size_t,
        ]
        kperfdata.kpep_config_kpc.restype = ctypes.c_int
        kperfdata.kpep_config_kpc_map.argtypes = [
            ctypes.POINTER(KpepConfig),
            ctypes.POINTER(ctypes.c_size_t),
            ctypes.c_size_t,
        ]
        kperfdata.kpep_config_kpc_map.restype = ctypes.c_int

        db = ctypes.POINTER(KpepDb)()
        if kperfdata.kpep_db_create(None, ctypes.byref(db)) != 0:
            result["status"] = "kpep_db_create_failed"
            return result

        cfg = ctypes.POINTER(KpepConfig)()
        if kperfdata.kpep_config_create(db, ctypes.byref(cfg)) != 0:
            result["status"] = "kpep_config_create_failed"
            return result
        kperfdata.kpep_config_force_counters(cfg)

        desired_events = [
            ("cycles", [b"FIXED_CYCLES"]),
            ("instructions", [b"FIXED_INSTRUCTIONS"]),
            ("l1d_miss_ld", [b"L1D_CACHE_MISS_LD_NONSPEC", b"L1D_CACHE_MISS_LD"]),
            ("l1d_miss_st", [b"L1D_CACHE_MISS_ST_NONSPEC", b"L1D_CACHE_MISS_ST"]),
            ("inst_branch", [b"INST_BRANCH"]),
            ("branch_mispred", [b"BRANCH_MISPRED_NONSPEC"]),
        ]

        added_keys = []
        for key, candidates in desired_events:
            ev = ctypes.POINTER(KpepEvent)()
            found = False
            for name in candidates:
                if kperfdata.kpep_db_event(db, name, ctypes.byref(ev)) == 0 and bool(ev):
                    found = True
                    break
            if found:
                if kperfdata.kpep_config_add_event(cfg, ctypes.byref(ev), 0, None) == 0:
                    added_keys.append(key)

        classes = ctypes.c_uint32(0)
        reg_count = ctypes.c_size_t(0)
        kperfdata.kpep_config_kpc_classes(cfg, ctypes.byref(classes))
        kperfdata.kpep_config_kpc_count(cfg, ctypes.byref(reg_count))

        regs = (ctypes.c_uint64 * KPC_MAX_COUNTERS)()
        counter_map = (ctypes.c_size_t * KPC_MAX_COUNTERS)()
        kperfdata.kpep_config_kpc(cfg, regs, ctypes.sizeof(regs))
        kperfdata.kpep_config_kpc_map(cfg, counter_map, ctypes.sizeof(counter_map))

        if reg_count.value > 0:
            kperf.kpc_set_config(classes.value, regs)

        kperf.kpc_set_counting(classes.value)
        kperf.kpc_set_thread_counting(classes.value)

        before = (ctypes.c_uint64 * KPC_MAX_COUNTERS)()
        after = (ctypes.c_uint64 * KPC_MAX_COUNTERS)()
        kperf.kpc_get_thread_counters(0, KPC_MAX_COUNTERS, before)

        t0 = time.perf_counter()
        proc = subprocess.run(cmd, check=False)
        elapsed_s = time.perf_counter() - t0

        kperf.kpc_get_thread_counters(0, KPC_MAX_COUNTERS, after)
        kperf.kpc_set_counting(0)
        kperf.kpc_set_thread_counting(0)
        kperf.kpc_force_all_ctrs_set(0)

        for idx, key in enumerate(added_keys):
            pmc_idx = counter_map[idx]
            delta = max(0, int(after[pmc_idx]) - int(before[pmc_idx]))
            result["events"][key] = delta

        cycles = result["events"].get("cycles", 0)
        insns = result["events"].get("instructions", 0)
        l1d_ld = result["events"].get("l1d_miss_ld", 0)
        l1d_st = result["events"].get("l1d_miss_st", 0)
        branches = result["events"].get("inst_branch", 0)
        br_miss = result["events"].get("branch_mispred", 0)

        if cycles > 0:
            result["metrics"]["ipc"] = round(insns / cycles, 3)
        if insns > 0:
            result["metrics"]["l1d_miss_per_1k_inst"] = round(((l1d_ld + l1d_st) * 1000.0) / insns, 3)
        if branches > 0:
            result["metrics"]["branch_miss_pct"] = f"{(br_miss * 100.0) / branches:.2f}%"

        result["status"] = "ok"
        result["exitCode"] = proc.returncode
        result["elapsedSec"] = round(elapsed_s, 4)
        return result
    except Exception as exc:
        kperf.kpc_force_all_ctrs_set(0)
        result["status"] = f"error: {exc}"
        return result


def main():
    if len(sys.argv) < 3:
        print("Usage: macos_kperf_pmu.py <output-json> <command...>", file=sys.stderr)
        sys.exit(1)

    out_path = sys.argv[1]
    cmd = sys.argv[2:]
    res = collect_with_kperf(cmd)
    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(res, f, indent=2)
    print(f"kperf PMU status: {res['status']} -> {out_path}")


if __name__ == "__main__":
    main()
