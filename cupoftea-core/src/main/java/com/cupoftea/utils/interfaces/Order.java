package com.cupoftea.utils.interfaces;

public enum Order {
    ASCENDING(1),
    DESCENDING(-1);

    public int value;

    Order(int i) {
        this.value = i;
    }
}