package com.cupoftea.table.window;

@FunctionalInterface
public interface WindowFunction<T, R> {
    R apply(T window);
}
