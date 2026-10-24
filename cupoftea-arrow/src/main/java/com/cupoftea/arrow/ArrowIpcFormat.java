package com.cupoftea.arrow;

/**
 * Supported Arrow IPC serialization formats.
 */
public enum ArrowIpcFormat {
    /**
     * Arrow IPC File format (random-access, includes footer dictionary/record batches, Feather v2).
     */
    FILE,

    /**
     * Arrow IPC Stream format (streaming record batches terminated by end-of-stream indicator).
     */
    STREAM
}
