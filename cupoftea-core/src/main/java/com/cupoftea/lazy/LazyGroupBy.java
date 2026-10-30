package com.cupoftea.lazy;

import java.util.Arrays;

import com.cupoftea.CupOfTea;
import com.cupoftea.table.Aggregate;
import com.cupoftea.table.Aggregation;
import com.cupoftea.table.Aggregations;

/**
 * Lazy GroupBy operation on a {@link LazyCupOfTea} execution plan.
 * Encapsulates grouping column(s) and prepares streaming aggregation.
 */
public class LazyGroupBy {

    private final LazyCupOfTea parent;
    private final String[] columns;
    private Aggregate[] aggregates;

    public LazyGroupBy(LazyCupOfTea parent, String... columns) {
        if (parent == null) {
            throw new IllegalArgumentException("Parent LazyCupOfTea cannot be null");
        }
        if (columns == null || columns.length == 0) {
            throw new IllegalArgumentException("At least one grouping column must be specified");
        }
        this.parent = parent;
        this.columns = columns.clone();
    }

    public String[] getColumns() {
        return columns.clone();
    }

    public Aggregate[] getAggregates() {
        return aggregates != null ? aggregates.clone() : null;
    }

    /**
     * Configures the aggregates to compute over the streaming groups and returns the aggregated {@link LazyCupOfTea}.
     */
    public LazyCupOfTea agg(Aggregate... aggregates) {
        if (aggregates == null || aggregates.length == 0) {
            throw new IllegalArgumentException("At least one aggregate must be specified");
        }
        this.aggregates = aggregates.clone();
        return parent.withGroupBy(this);
    }

    /**
     * Configures high-level {@link Aggregation}s to compute over the streaming groups.
     */
    public LazyCupOfTea agg(Aggregation... aggregations) {
        if (aggregations == null || aggregations.length == 0) {
            throw new IllegalArgumentException("At least one aggregation must be specified");
        }
        Aggregate[] aggs = new Aggregate[aggregations.length];
        for (int i = 0; i < aggregations.length; i++) {
            aggs[i] = aggregations[i].of(columns[0]);
        }
        return agg(aggs);
    }

    public LazyCupOfTea sum(String column) {
        return agg(Aggregations.sum(column));
    }

    public LazyCupOfTea mean(String column) {
        return agg(Aggregations.mean(column));
    }

    public LazyCupOfTea avg(String column) {
        return agg(Aggregations.avg(column));
    }

    public LazyCupOfTea min(String column) {
        return agg(Aggregations.min(column));
    }

    public LazyCupOfTea max(String column) {
        return agg(Aggregations.max(column));
    }

    public LazyCupOfTea count() {
        return agg(Aggregations.count());
    }

    public LazyCupOfTea count(String column) {
        return agg(Aggregations.count(column));
    }

    public LazyCupOfTea first(String column) {
        return agg(Aggregations.first(column));
    }

    public LazyCupOfTea last(String column) {
        return agg(Aggregations.last(column));
    }

    public LazyCupOfTea std(String column) {
        return agg(Aggregations.std(column));
    }

    public LazyCupOfTea var(String column) {
        return agg(Aggregations.var(column));
    }

    /**
     * Finalizes and collects the grouped result DataFrame.
     */
    public CupOfTea collect() {
        if (this.aggregates == null || this.aggregates.length == 0) {
            return count().collect();
        }
        return parent.withGroupBy(this).collect();
    }
}
