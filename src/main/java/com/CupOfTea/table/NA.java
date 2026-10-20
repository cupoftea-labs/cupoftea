package com.CupOfTea.table;

import java.io.Serializable;
import java.util.Objects;

/**
 * First-class missing-value sentinel, representing an absent / missing value (similar to pandas NA).
 * <p>
 * Missing values (NA or {@code null}) are distinct from empty strings ({@code ""}).
 * An empty string is a valid string of length zero, whereas NA represents absent data.
 */
public final class NA implements Comparable<Object>, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Singleton instance of NA.
     */
    public static final NA NA = new NA();

    /**
     * Alias for {@link #NA}.
     */
    public static final NA INSTANCE = NA;

    private NA() {
    }

    /**
     * Checks if a value is NA or missing.
     * <p>
     * A value is considered NA / missing if:
     * <ul>
     *     <li>It is {@code null}</li>
     *     <li>It is an instance of {@link NA}</li>
     *     <li>It is a floating-point {@code Double.NaN} or {@code Float.NaN}</li>
     * </ul>
     * Note that an empty string {@code ""} is <b>not</b> considered NA.
     *
     * @param value the value to test
     * @return true if the value represents a missing value
     */
    public static boolean isNA(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof NA) {
            return true;
        }
        if (value instanceof Double d && Double.isNaN(d)) {
            return true;
        }
        if (value instanceof Float f && Float.isNaN(f)) {
            return true;
        }
        return false;
    }

    /**
     * Alias for {@link #isNA(Object)}.
     */
    public static boolean isMissing(Object value) {
        return isNA(value);
    }

    /**
     * Checks if a value is a present, non-missing value.
     */
    public static boolean isValue(Object value) {
        return !isNA(value);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof NA;
    }

    @Override
    public int hashCode() {
        return Objects.hash("NA");
    }

    @Override
    public String toString() {
        return "NA";
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public int compareTo(Object o) {
        if (isNA(o)) {
            return 0;
        }
        // NA sorts last (nulls-last)
        return 1;
    }
}
