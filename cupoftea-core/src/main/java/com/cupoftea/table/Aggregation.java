package com.cupoftea.table;

import java.util.Objects;

/**
 * Standard aggregation operations supported across CupOfTea DataFrames, GroupBy, and pivot operations.
 */
public enum Aggregation {
    SUM,
    MEAN,
    AVERAGE,
    AVG,
    COUNT,
    MIN,
    MINIMUM,
    MAX,
    MAXIMUM,
    FIRST,
    LAST,
    MEDIAN,
    STD,
    STANDARD_DEVIATION,
    VAR,
    VARIANCE,
    QUANTILE,
    SKEWNESS,
    KURTOSIS;

    /**
     * Creates an {@link Aggregate} for the given column using this aggregation type.
     *
     * @param column the target column name
     * @return an {@link Aggregate} definition
     */
    public Aggregate of(String column) {
        Objects.requireNonNull(column, "Column name cannot be null");
        return switch (this) {
            case SUM -> Aggregations.sum(column);
            case MEAN -> Aggregations.mean(column);
            case AVERAGE -> Aggregations.average(column);
            case AVG -> Aggregations.avg(column);
            case COUNT -> Aggregations.count(column);
            case MIN -> Aggregations.min(column);
            case MINIMUM -> Aggregations.minimum(column);
            case MAX -> Aggregations.max(column);
            case MAXIMUM -> Aggregations.maximum(column);
            case FIRST -> Aggregations.first(column);
            case LAST -> Aggregations.last(column);
            case MEDIAN -> Aggregations.median(column);
            case STD, STANDARD_DEVIATION -> Aggregations.std(column);
            case VAR, VARIANCE -> Aggregations.var(column);
            case QUANTILE -> Aggregations.quantile(column, 0.5);
            case SKEWNESS -> Aggregations.skewness(column);
            case KURTOSIS -> Aggregations.kurtosis(column);
        };
    }

    /**
     * Converts this enum aggregation into an {@link Aggregate} configured for the specified column.
     *
     * @param column the target column name
     * @return the corresponding {@link Aggregate} instance
     */
    public Aggregate toAggregate(String column) {
        return of(column);
    }

    /**
     * Creates an {@link Aggregate} with an explicit output column alias.
     *
     * @param column the target column name
     * @param alias the output column alias
     * @return an {@link Aggregate} definition
     */
    public Aggregate of(String column, String alias) {
        return of(column).as(alias);
    }

    /**
     * Creates a quantile aggregate with an arbitrary percentile fraction q &isin; [0, 1].
     *
     * @param column the target column name
     * @param quantile the quantile fraction (e.g. 0.25, 0.75, 0.95)
     * @return an {@link Aggregate} definition
     */
    public Aggregate of(String column, double quantile) {
        if (this == QUANTILE) {
            return Aggregations.quantile(column, quantile);
        }
        return of(column);
    }

    /**
     * Creates a quantile aggregate with an arbitrary percentile fraction and output alias.
     *
     * @param column the target column name
     * @param quantile the quantile fraction
     * @param alias the output column alias
     * @return an {@link Aggregate} definition
     */
    public Aggregate of(String column, double quantile, String alias) {
        return of(column, quantile).as(alias);
    }
}
