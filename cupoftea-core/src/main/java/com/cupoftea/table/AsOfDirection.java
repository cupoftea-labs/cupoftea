package com.cupoftea.table;

/**
 * Direction for As-Of Join lookups when matching ordered/temporal keys.
 */
public enum AsOfDirection {
    /**
     * Finds the nearest prior observation where right key &lt;= left key.
     * This is the standard default for financial and event time-series joins.
     */
    BACKWARD,

    /**
     * Finds the nearest subsequent observation where right key &gt;= left key.
     */
    FORWARD,

    /**
     * Finds the absolute closest observation in either direction.
     * In case of exact equidistant ties, the later (forward) value is preferred.
     */
    NEAREST;

    /**
     * Parses a string representation of an as-of direction (case-insensitive).
     *
     * @param direction name of direction: "backward", "forward", "nearest"
     * @return corresponding {@link AsOfDirection}
     */
    public static AsOfDirection fromString(String direction) {
        if (direction == null) {
            return BACKWARD;
        }
        return switch (direction.trim().toLowerCase()) {
            case "backward", "back" -> BACKWARD;
            case "forward", "forw" -> FORWARD;
            case "nearest", "near" -> NEAREST;
            default -> throw new IllegalArgumentException(
                "Unknown AsOfDirection: '" + direction + "'. Supported directions: 'backward', 'forward', 'nearest'");
        };
    }
}
