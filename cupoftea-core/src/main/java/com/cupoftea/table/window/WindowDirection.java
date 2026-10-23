package com.cupoftea.table.window;

/**
 * Direction of observation inclusion for sliding rolling windows.
 */
public enum WindowDirection {
    /**
     * Backward-looking (trailing) window: includes observations from {@code [i - windowSize + 1, i]}.
     * This is the standard default for time-series rolling computations.
     */
    BACKWARD,

    /**
     * Centered window: places observation {@code i} at the center of the window.
     */
    CENTERED,

    /**
     * Forward-looking (leading) window: includes observations from {@code [i, i + windowSize - 1]}.
     */
    FORWARD
}
