package com.cupoftea.csv.lazy;

import java.lang.invoke.MethodHandle;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.MappedByteBuffer;
import java.util.Optional;

/**
 * Engine-level memory adaptation helpers for large streaming scans.
 *
 * <p>Provides (1) detection of free physical memory to adapt the mapped segment size and
 * (2) best-effort {@code madvise} hints ({@code MADV_WILLNEED} / {@code MADV_DONTNEED}) so the
 * kernel can read ahead and evict consumed pages. {@code madvise} is reached through the
 * Foreign Function &amp; Memory API via reflection (the module targets Java 17). Every operation
 * degrades gracefully to a no-op when unsupported (e.g. Windows, older JVMs, restricted access).</p>
 */
public final class MemoryAdvisor {

    /** Segment size used when the dataset exceeds free physical memory. */
    public static final int LOW_MEMORY_SEGMENT_SIZE = 2 * 1024 * 1024;

    private static final int MADV_WILLNEED = 3;
    private static final int MADV_DONTNEED = 4;
    /** Conservative alignment, valid for both 4 KB and 16 KB page kernels. */
    private static final long ALIGN = 16384L;

    private static final MethodHandle MADVISE;
    private static final Method OF_BUFFER;
    private static final Method ADDRESS;

    static {
        MethodHandle handle = null;
        Method ofBuffer = null;
        Method address = null;
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("linux") || os.contains("mac")) {
                Class<?> linkerC = Class.forName("java.lang.foreign.Linker");
                Class<?> segC = Class.forName("java.lang.foreign.MemorySegment");
                Class<?> descC = Class.forName("java.lang.foreign.FunctionDescriptor");
                Class<?> layoutC = Class.forName("java.lang.foreign.MemoryLayout");
                Class<?> valueC = Class.forName("java.lang.foreign.ValueLayout");
                Class<?> optC = Class.forName("java.lang.foreign.Linker$Option");
                Class<?> lookupC = Class.forName("java.lang.foreign.SymbolLookup");

                Object linker = linkerC.getMethod("nativeLinker").invoke(null);
                Object lookup = linkerC.getMethod("defaultLookup").invoke(linker);
                Optional<?> sym = (Optional<?>) lookupC.getMethod("find", String.class).invoke(lookup, "madvise");
                if (sym.isPresent()) {
                    Object jInt = valueC.getField("JAVA_INT").get(null);
                    Object jLong = valueC.getField("JAVA_LONG").get(null);
                    Object layouts3 = Array.newInstance(layoutC, 3);
                    Array.set(layouts3, 0, jLong);
                    Array.set(layouts3, 1, jLong);
                    Array.set(layouts3, 2, jInt);
                    Object desc = descC.getMethod("of", layoutC, layouts3.getClass()).invoke(null, jInt, layouts3);
                    Object noOpts = Array.newInstance(optC, 0);
                    handle = (MethodHandle) linkerC
                            .getMethod("downcallHandle", segC, descC, noOpts.getClass())
                            .invoke(linker, sym.get(), desc, noOpts);
                    ofBuffer = segC.getMethod("ofBuffer", java.nio.Buffer.class);
                    address = segC.getMethod("address");
                }
            }
        } catch (Throwable t) {
            handle = null;
        }
        MADVISE = handle;
        OF_BUFFER = ofBuffer;
        ADDRESS = address;
    }

    private MemoryAdvisor() {
    }

    /** @return {@code true} if native {@code madvise} is usable on this platform. */
    public static boolean isMadviseAvailable() {
        return MADVISE != null;
    }

    /**
     * @return free physical memory in bytes, or {@link Long#MAX_VALUE} if it cannot be determined.
     */
    public static long freePhysicalMemory() {
        try {
            java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
                long free = sun.getFreeMemorySize();
                if (free > 0) {
                    return free;
                }
            }
        } catch (Throwable ignored) {
            // fall through
        }
        return Long.MAX_VALUE;
    }

    /**
     * Chooses a segment size: shrinks to {@link #LOW_MEMORY_SEGMENT_SIZE} when the data does not
     * fit in free physical memory, otherwise returns {@code defaultSize}.
     */
    public static int adaptSegmentSize(long dataLen, long freeMemory, int defaultSize) {
        if (dataLen > freeMemory) {
            return Math.min(defaultSize, LOW_MEMORY_SEGMENT_SIZE);
        }
        return defaultSize;
    }

    /** Hints the kernel to asynchronously read ahead the mapped region. */
    public static void willNeed(MappedByteBuffer buf) {
        advise(buf, MADV_WILLNEED);
    }

    /** Hints the kernel that the (already consumed) mapped region can be evicted. */
    public static void dontNeed(MappedByteBuffer buf) {
        advise(buf, MADV_DONTNEED);
    }

    private static void advise(MappedByteBuffer buf, int advice) {
        if (MADVISE == null || buf == null) {
            return;
        }
        try {
            Object seg = OF_BUFFER.invoke(null, buf);
            long start = (Long) ADDRESS.invoke(seg);
            long end = start + buf.capacity();
            long aStart = (start + ALIGN - 1) & -ALIGN;
            long aEnd = end & -ALIGN;
            if (aEnd > aStart) {
                MADVISE.invokeWithArguments(aStart, aEnd - aStart, advice);
            }
        } catch (Throwable ignored) {
            // best-effort hint only
        }
    }
}
