package com.cupoftea.table;

import java.io.Serializable;
import java.util.Objects;

/**
 * Logical type marker and value representation for dictionary-encoded / categorical string columns.
 * <p>
 * Can be used in {@link Schema} definitions (e.g., {@code Schema.of("station", Schema.CATEGORICAL)}
 * or {@code Schema.of("station", Categorical.class)}) and column type conversions
 * ({@code df.alterColumnType("station", Categorical.class)}) to store repeated string columns
 * using a compact dictionary lookup array and primitive integer codes.
 */
public final class Categorical implements Serializable, Comparable<Categorical>, CharSequence {

    private static final long serialVersionUID = 1L;

    private final String value;

    public Categorical(String value) {
        this.value = Objects.requireNonNull(value, "Categorical value cannot be null");
    }

    public static Categorical of(String value) {
        return value != null ? new Categorical(value) : null;
    }

    public String getValue() {
        return value;
    }

    @Override
    public int length() {
        return value.length();
    }

    @Override
    public char charAt(int index) {
        return value.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return value.subSequence(start, end);
    }

    @Override
    public int compareTo(Categorical other) {
        if (other == null) {
            return -1;
        }
        return this.value.compareTo(other.value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof Categorical that) {
            return Objects.equals(this.value, that.value);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
