package com.cupoftea.plot;

import com.cupoftea.CupOfTea;
import com.cupoftea.table.interfaces.IDataFrame;

/**
 * Convenient static factory methods for creating plots and charts.
 */
public final class Plots {

    private Plots() {
    }

    /**
     * Creates a {@link Plot} builder for the given {@link CupOfTea} DataFrame.
     *
     * @param df DataFrame wrapper
     * @return a {@link Plot} instance
     */
    public static Plot of(CupOfTea df) {
        if (df == null) {
            throw new IllegalArgumentException("CupOfTea DataFrame cannot be null");
        }
        return df.plot();
    }

    /**
     * Creates a {@link Plot} builder for the given {@link IDataFrame}.
     *
     * @param df DataFrame instance
     * @return a {@link Plot} instance
     */
    public static Plot of(IDataFrame df) {
        if (df == null) {
            throw new IllegalArgumentException("DataFrame cannot be null");
        }
        return df.plot();
    }

    public static Chart line(CupOfTea df, String x, String y) {
        return of(df).line(x, y);
    }

    public static Chart bar(CupOfTea df, String x, String y) {
        return of(df).bar(x, y);
    }

    public static Chart scatter(CupOfTea df, String x, String y) {
        return of(df).scatter(x, y);
    }

    public static Chart hist(CupOfTea df, String column) {
        return of(df).hist(column);
    }

    public static Chart hist(CupOfTea df, String column, int bins) {
        return of(df).hist(column, bins);
    }

    public static Chart box(CupOfTea df, String categoryCol, String valueCol) {
        return of(df).box(categoryCol, valueCol);
    }

    public static Chart area(CupOfTea df, String x, String y) {
        return of(df).area(x, y);
    }

    public static Chart heatmap(CupOfTea df, String x, String y, String values) {
        return of(df).heatmap(x, y, values);
    }
}
