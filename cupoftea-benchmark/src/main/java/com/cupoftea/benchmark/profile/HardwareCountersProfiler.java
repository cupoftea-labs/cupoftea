package com.cupoftea.benchmark.profile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.profile.InternalProfiler;
import org.openjdk.jmh.results.AggregationPolicy;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.ScalarResult;

/**
 * Cross-platform JMH {@link InternalProfiler} that captures hardware CPU counters
 * (Instructions Retired, Cycles Elapsed, IPC) and Peak Resident Set Size (RSS)
 * per benchmark iteration.
 *
 * <ul>
 *   <li><b>macOS (Apple Silicon / x86_64):</b> Invokes {@code proc_pid_rusage(pid, RUSAGE_INFO_V4, ...)}
 *       in {@code libSystem.B.dylib} via the Java Foreign Function &amp; Memory API ({@code java.lang.foreign.Linker})
 *       to read kernel-maintained hardware PMU counters ({@code ri_instructions}, {@code ri_cycles}) and
 *       physical memory footprint ({@code ri_resident_size}, {@code ri_lifetime_max_phys_footprint}) without
 *       requiring root privileges.</li>
 *   <li><b>Linux:</b> Reads {@code /proc/self/status} ({@code VmHWM} / {@code VmRSS}) for peak memory footprint;
 *       pairs with {@code -prof perfnorm} or {@code perf stat} for PMU counters.</li>
 * </ul>
 */
public class HardwareCountersProfiler implements InternalProfiler {

    private static final int RUSAGE_INFO_V4 = 4;
    private static final long OFF_RI_RESIDENT_SIZE = 64L;
    private static final long OFF_RI_PHYS_FOOTPRINT = 72L;
    private static final long OFF_RI_MAX_PHYS_FOOTPRINT = 240L;
    private static final long OFF_RI_INSTRUCTIONS = 248L;
    private static final long OFF_RI_CYCLES = 256L;

    private static final MethodHandle PROC_PID_RUSAGE_MH;
    private static final Method ARENA_OF_AUTO;
    private static final Method ARENA_ALLOCATE;
    private static final Method SEGMENT_GET_LONG;
    private static final Object JAVA_LONG_LAYOUT;

    static {
        MethodHandle mh = null;
        Method arenaOfAuto = null;
        Method arenaAlloc = null;
        Method segGetLong = null;
        Object javaLong = null;
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac") || os.contains("darwin")) {
            try {
                Class<?> linkerClass = Class.forName("java.lang.foreign.Linker");
                Class<?> symbolLookupClass = Class.forName("java.lang.foreign.SymbolLookup");
                Class<?> segmentClass = Class.forName("java.lang.foreign.MemorySegment");
                Class<?> arenaClass = Class.forName("java.lang.foreign.Arena");
                Class<?> layoutClass = Class.forName("java.lang.foreign.MemoryLayout");
                Class<?> valueLayoutClass = Class.forName("java.lang.foreign.ValueLayout");
                Class<?> ofLongClass = Class.forName("java.lang.foreign.ValueLayout$OfLong");
                Class<?> funcDescClass = Class.forName("java.lang.foreign.FunctionDescriptor");
                Class<?> linkerOptArrayClass = Class.forName("[Ljava.lang.foreign.Linker$Option;");

                Object linker = linkerClass.getMethod("nativeLinker").invoke(null);
                Object lookup = linkerClass.getMethod("defaultLookup").invoke(linker);
                Optional<?> symOpt = (Optional<?>) symbolLookupClass.getMethod("find", String.class)
                        .invoke(lookup, "proc_pid_rusage");

                if (symOpt.isPresent()) {
                    Object symbol = symOpt.get();
                    Object javaInt = valueLayoutClass.getField("JAVA_INT").get(null);
                    Object address = valueLayoutClass.getField("ADDRESS").get(null);
                    javaLong = valueLayoutClass.getField("JAVA_LONG").get(null);

                    Object argLayouts = Array.newInstance(layoutClass, 3);
                    Array.set(argLayouts, 0, javaInt);
                    Array.set(argLayouts, 1, javaInt);
                    Array.set(argLayouts, 2, address);

                    Method ofMethod = funcDescClass.getMethod("of", layoutClass, argLayouts.getClass());
                    Object desc = ofMethod.invoke(null, javaInt, argLayouts);

                    Object emptyOpts = Array.newInstance(Class.forName("java.lang.foreign.Linker$Option"), 0);
                    Method downcall = linkerClass.getMethod("downcallHandle", segmentClass, funcDescClass, linkerOptArrayClass);
                    mh = (MethodHandle) downcall.invoke(linker, symbol, desc, emptyOpts);

                    arenaOfAuto = arenaClass.getMethod("ofAuto");
                    arenaAlloc = arenaClass.getMethod("allocate", long.class, long.class);
                    segGetLong = segmentClass.getMethod("get", ofLongClass, long.class);
                }
            } catch (Throwable ignored) {
                // Fallback gracefully if FFM is unavailable (e.g. older JDK without java.lang.foreign)
            }
        }
        PROC_PID_RUSAGE_MH = mh;
        ARENA_OF_AUTO = arenaOfAuto;
        ARENA_ALLOCATE = arenaAlloc;
        SEGMENT_GET_LONG = segGetLong;
        JAVA_LONG_LAYOUT = javaLong;
    }

    private long startInsns;
    private long startCycles;

    @Override
    public String getDescription() {
        return "CupOfTea Hardware PMU Counters (IPC, Instructions, Cycles) & Peak RSS Profiler";
    }

    @Override
    public void beforeIteration(BenchmarkParams benchmarkParams, IterationParams iterationParams) {
        long[] snap = readMacRusage();
        if (snap != null) {
            startInsns = snap[0];
            startCycles = snap[1];
        }
    }

    @Override
    public Collection<? extends Result<?>> afterIteration(
            BenchmarkParams benchmarkParams,
            IterationParams iterationParams,
            IterationResult result) {
        List<Result<?>> results = new ArrayList<>();
        long ops = (result != null && result.getMetadata() != null)
                ? Math.max(1L, result.getMetadata().getMeasuredOps())
                : 1L;

        long[] snap = readMacRusage();
        if (snap != null) {
            long deltaInsns = Math.max(0L, snap[0] - startInsns);
            long deltaCycles = Math.max(0L, snap[1] - startCycles);
            long peakBytes = Math.max(snap[2], snap[3]);

            if (deltaCycles > 0) {
                double ipc = (double) deltaInsns / (double) deltaCycles;
                results.add(new ScalarResult("hw.ipc", ipc, "insn/cycle", AggregationPolicy.AVG));
                results.add(new ScalarResult("hw.cycles.per.op", (double) deltaCycles / ops, "cycles/op", AggregationPolicy.AVG));
                results.add(new ScalarResult("hw.insns.per.op", (double) deltaInsns / ops, "insns/op", AggregationPolicy.AVG));
            }
            if (peakBytes > 0) {
                double rssMb = peakBytes / (1024.0 * 1024.0);
                results.add(new ScalarResult("hw.rss.peak.mb", rssMb, "MB", AggregationPolicy.MAX));
            }
        } else {
            double linuxRssMb = readLinuxPeakRssMb();
            if (linuxRssMb > 0) {
                results.add(new ScalarResult("hw.rss.peak.mb", linuxRssMb, "MB", AggregationPolicy.MAX));
            }
        }
        return results;
    }

    /**
     * Returns {@code [instructions, cycles, residentBytes, maxPhysFootprintBytes]} on macOS, or {@code null}.
     */
    public static long[] readMacRusage() {
        if (PROC_PID_RUSAGE_MH == null) {
            return null;
        }
        try {
            Object arena = ARENA_OF_AUTO.invoke(null);
            Object seg = ARENA_ALLOCATE.invoke(arena, 512L, 8L);
            int pid = (int) ProcessHandle.current().pid();
            int rc = (int) PROC_PID_RUSAGE_MH.invoke(pid, RUSAGE_INFO_V4, seg);
            if (rc != 0) {
                return null;
            }
            long insns = (long) SEGMENT_GET_LONG.invoke(seg, JAVA_LONG_LAYOUT, OFF_RI_INSTRUCTIONS);
            long cycles = (long) SEGMENT_GET_LONG.invoke(seg, JAVA_LONG_LAYOUT, OFF_RI_CYCLES);
            long resident = (long) SEGMENT_GET_LONG.invoke(seg, JAVA_LONG_LAYOUT, OFF_RI_RESIDENT_SIZE);
            long phys = (long) SEGMENT_GET_LONG.invoke(seg, JAVA_LONG_LAYOUT, OFF_RI_PHYS_FOOTPRINT);
            long maxPhys = (long) SEGMENT_GET_LONG.invoke(seg, JAVA_LONG_LAYOUT, OFF_RI_MAX_PHYS_FOOTPRINT);
            return new long[]{insns, cycles, Math.max(resident, phys), maxPhys};
        } catch (Throwable t) {
            return null;
        }
    }

    private static double readLinuxPeakRssMb() {
        File status = new File("/proc/self/status");
        if (!status.exists()) {
            return 0.0;
        }
        long hwmKb = 0;
        long rssKb = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(status))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("VmHWM:")) {
                    hwmKb = parseProcKb(line);
                } else if (line.startsWith("VmRSS:")) {
                    rssKb = parseProcKb(line);
                }
            }
        } catch ( Exception ignored) {
            // ignore
        }
        return Math.max(hwmKb, rssKb) / 1024.0;
    }

    private static long parseProcKb(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length >= 2) {
            try {
                return Long.parseLong(parts[1]);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }
}
