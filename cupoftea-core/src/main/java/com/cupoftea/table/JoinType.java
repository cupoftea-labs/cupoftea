package com.cupoftea.table;

/**
 * Types of join operations supported between DataFrames.
 */
public enum JoinType {
    /**
     * Inner join: returns rows where join keys match in both DataFrames.
     */
    INNER,

    /**
     * Left outer join: returns all rows from the left DataFrame, and matched rows from the right DataFrame (null for unmatched).
     */
    LEFT,

    /**
     * Right outer join: returns all rows from the right DataFrame, and matched rows from the left DataFrame (null for unmatched).
     */
    RIGHT,

    /**
     * Full outer join: returns all rows from both DataFrames, padding missing values with null when no match exists.
     */
    OUTER,

    /**
     * Cross join: Cartesian product of all rows from both DataFrames.
     */
    CROSS;

    /**
     * Parses a string representation of a join type (case-insensitive).
     *
     * @param how name of join type: "inner", "left", "left_outer", "right", "right_outer", "outer", "full", "full_outer", "cross"
     * @return corresponding {@link JoinType}
     */
    public static JoinType fromString(String how) {
        if (how == null) {
            return INNER;
        }
        return switch (how.trim().toLowerCase()) {
            case "inner" -> INNER;
            case "left", "left_outer" -> LEFT;
            case "right", "right_outer" -> RIGHT;
            case "outer", "full", "full_outer" -> OUTER;
            case "cross" -> CROSS;
            default -> throw new IllegalArgumentException(
                "Unknown join type: '" + how + "'. Supported types: 'inner', 'left', 'right', 'outer', 'cross'");
        };
    }
}
