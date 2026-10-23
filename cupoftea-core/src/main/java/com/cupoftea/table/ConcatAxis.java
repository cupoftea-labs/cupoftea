package com.cupoftea.table;

/**
 * Axis along which DataFrames are concatenated.
 */
public enum ConcatAxis {
    /**
     * Concatenate along rows (vertical concatenation / axis 0).
     */
    ROWS(0),

    /**
     * Concatenate along columns (horizontal concatenation / axis 1).
     */
    COLUMNS(1);

    private final int value;

    ConcatAxis(int value) {
        this.value = value;
    }

    /**
     * Returns the integer representation of the axis (0 for ROWS, 1 for COLUMNS).
     *
     * @return 0 for ROWS, 1 for COLUMNS
     */
    public int getValue() {
        return value;
    }

    /**
     * Parses an integer value into a {@link ConcatAxis}.
     *
     * @param axis 0 for ROWS, 1 for COLUMNS
     * @return corresponding {@link ConcatAxis}
     * @throws IllegalArgumentException if axis is neither 0 nor 1
     */
    public static ConcatAxis fromValue(int axis) {
        return switch (axis) {
            case 0 -> ROWS;
            case 1 -> COLUMNS;
            default -> throw new IllegalArgumentException(
                "Unknown axis: " + axis + ". Supported axis values are 0 (ROWS) and 1 (COLUMNS).");
        };
    }

    /**
     * Parses a string representation of an axis (case-insensitive).
     *
     * @param axis string such as "rows", "0", "vertical", "columns", "1", "horizontal"
     * @return corresponding {@link ConcatAxis}
     * @throws IllegalArgumentException if axis string is unrecognized
     */
    public static ConcatAxis fromString(String axis) {
        if (axis == null) {
            return ROWS;
        }
        return switch (axis.trim().toLowerCase()) {
            case "rows", "row", "0", "vertical", "v", "index" -> ROWS;
            case "columns", "column", "col", "cols", "1", "horizontal", "h" -> COLUMNS;
            default -> throw new IllegalArgumentException(
                "Unknown axis: '" + axis + "'. Supported values are 'rows' (0) and 'columns' (1).");
        };
    }
}
