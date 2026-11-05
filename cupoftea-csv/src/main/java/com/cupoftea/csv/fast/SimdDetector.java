package com.cupoftea.csv.fast;

/**
 * Runtime hardware and JVM capability detector for the Java Vector API ({@code jdk.incubator.vector}).
 *
 * <p>Safely checks whether the {@code jdk.incubator.vector} incubator module is resolved in the
 * boot module layer and whether the host CPU architecture provides hardware SIMD registers of
 * at least 128 bits (16 bytes: ARM NEON / SSE4.2, 32 bytes: AVX2, 64 bytes: AVX-512).
 * If the module is absent or vector instructions are unsupported, {@link #isSupported()} returns
 * {@code false} without throwing {@link NoClassDefFoundError} or {@link LinkageError}.</p>
 */
public final class SimdDetector {

    /**
     * System property name to explicitly enable or disable the SIMD CSV structural scanner
     * ({@code -Dcupoftea.simd.enabled=true|false}).
     */
    public static final String SIMD_ENABLED_PROPERTY = "cupoftea.simd.enabled";

    /**
     * Minimum vector register width in bytes (16 bytes = 128 bits for ARM NEON / x86 SSE,
     * 32 bytes = 256 bits for AVX2, 64 bytes = 512 bits for AVX-512).
     */
    public static final int MIN_VECTOR_BYTE_LENGTH = 16;

    private static final boolean HARDWARE_SUPPORTED;
    private static final int VECTOR_BYTE_LENGTH;
    private static final String SPECIES_DESCRIPTION;

    private static volatile boolean forceDisabled = false;

    static {
        boolean supported = false;
        int byteLen = 0;
        String desc = "Scalar Fallback (jdk.incubator.vector unavailable)";

        try {
            boolean modulePresent = ModuleLayer.boot()
                    .findModule("jdk.incubator.vector")
                    .isPresent();
            if (modulePresent) {
                byteLen = VectorProbe.probeByteLength();
                desc = VectorProbe.probeDescription();
                supported = byteLen >= MIN_VECTOR_BYTE_LENGTH;
            }
        } catch (Throwable ignored) {
            supported = false;
            byteLen = 0;
        }

        HARDWARE_SUPPORTED = supported;
        VECTOR_BYTE_LENGTH = byteLen;
        SPECIES_DESCRIPTION = desc;
    }

    private SimdDetector() {
    }

    /**
     * Returns {@code true} if the Java Vector API ({@code jdk.incubator.vector}) is available,
     * hardware SIMD vector width is at least {@link #MIN_VECTOR_BYTE_LENGTH} bytes, and SIMD
     * acceleration has not been disabled via system property or {@link #setForceDisabled(boolean)}.
     *
     * @return {@code true} if SIMD scanning is supported and enabled
     */
    public static boolean isSupported() {
        if (forceDisabled || !HARDWARE_SUPPORTED) {
            return false;
        }
        String prop = System.getProperty(SIMD_ENABLED_PROPERTY);
        return prop == null || !"false".equalsIgnoreCase(prop.trim());
    }

    /**
     * Returns {@code true} if the underlying JVM and CPU hardware support the Vector API,
     * regardless of runtime toggle flags.
     *
     * @return {@code true} if hardware and module support are present
     */
    public static boolean isHardwareSupported() {
        return HARDWARE_SUPPORTED;
    }

    /**
     * Returns the preferred SIMD {@code ByteVector} register length in bytes
     * (e.g., 16 for ARM NEON, 32 for AVX2, 64 for AVX-512), or {@code 0} if unavailable.
     *
     * @return vector byte length
     */
    public static int getVectorByteLength() {
        return VECTOR_BYTE_LENGTH;
    }

    /**
     * Returns the preferred SIMD {@code ByteVector} register width in bits
     * (e.g., 128, 256, or 512), or {@code 0} if unavailable.
     *
     * @return vector bit size
     */
    public static int getVectorBitSize() {
        return VECTOR_BYTE_LENGTH * Byte.SIZE;
    }

    /**
     * Returns a human-readable description of the detected vector species or scalar fallback reason.
     *
     * @return species description string
     */
    public static String getSpeciesDescription() {
        return SPECIES_DESCRIPTION;
    }

    /**
     * Programmatically forces the SIMD scanner to be disabled (falling back to scalar scanning)
     * or restores automatic hardware detection. Primarily intended for testing and benchmarking.
     *
     * @param disabled {@code true} to force scalar fallback, {@code false} to use hardware detection
     */
    public static void setForceDisabled(boolean disabled) {
        forceDisabled = disabled;
    }

    /**
     * Returns whether SIMD scanning has been programmatically force-disabled.
     *
     * @return {@code true} if force-disabled
     */
    public static boolean isForceDisabled() {
        return forceDisabled;
    }

    /**
     * Isolated holder class so that {@code jdk.incubator.vector} classes are only linked
     * if the module is confirmed present in {@link ModuleLayer#boot()}.
     */
    private static final class VectorProbe {
        static int probeByteLength() {
            return jdk.incubator.vector.ByteVector.SPECIES_PREFERRED.length();
        }

        static String probeDescription() {
            return jdk.incubator.vector.ByteVector.SPECIES_PREFERRED.toString();
        }
    }
}
