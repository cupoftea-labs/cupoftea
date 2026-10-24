package com.cupoftea.table;

/**
 * Retention strategy for deduplication operations (e.g., dropDuplicates and distinct).
 */
public enum Keep {
    /**
     * Keep the first occurrence of each set of duplicate rows, dropping subsequent ones.
     */
    FIRST,

    /**
     * Keep the last occurrence of each set of duplicate rows, dropping preceding ones.
     */
    LAST,

    /**
     * Drop all occurrences of duplicated rows (if a row appears more than once, all copies are dropped).
     */
    NONE
}
