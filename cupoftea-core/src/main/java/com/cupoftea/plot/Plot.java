package com.cupoftea.plot;

/**
 * Declarative plot builder API for DataFrame visualization.
 * Entrypoint obtained via {@code df.plot()}.
 */
public interface Plot {

    /**
     * Creates a line chart mapping {@code x} and {@code y} columns.
     *
     * @param x column name for X axis
     * @param y column name for Y axis
     * @return a {@link Chart} instance
     */
    Chart line(String x, String y);

    /**
     * Creates a line chart mapping {@code x} and {@code y} columns, grouped by {@code colorCol}.
     *
     * @param x column name for X axis
     * @param y column name for Y axis
     * @param colorCol category column for multi-line grouping
     * @return a {@link Chart} instance
     */
    Chart line(String x, String y, String colorCol);

    /**
     * Creates a bar chart mapping {@code x} and {@code y} columns.
     *
     * @param x column name for X axis (categories or values)
     * @param y column name for Y axis (values)
     * @return a {@link Chart} instance
     */
    Chart bar(String x, String y);

    /**
     * Creates a bar chart mapping {@code x} and {@code y} columns, grouped by {@code colorCol}.
     *
     * @param x column name for X axis
     * @param y column name for Y axis
     * @param colorCol category column for bar grouping
     * @return a {@link Chart} instance
     */
    Chart bar(String x, String y, String colorCol);

    /**
     * Creates a 2D scatter plot mapping {@code x} and {@code y} columns.
     *
     * @param x column name for X axis
     * @param y column name for Y axis
     * @return a {@link Chart} instance
     */
    Chart scatter(String x, String y);

    /**
     * Creates a 2D scatter plot mapping {@code x} and {@code y} columns with a color encoding.
     *
     * @param x column name for X axis
     * @param y column name for Y axis
     * @param colorCol column name for point color encoding
     * @return a {@link Chart} instance
     */
    Chart scatter(String x, String y, String colorCol);

    /**
     * Creates a multi-dimensional scatter plot mapping {@code x}, {@code y},
     * {@code colorCol}, and {@code sizeCol}.
     *
     * @param x column name for X axis
     * @param y column name for Y axis
     * @param colorCol column name for point color encoding
     * @param sizeCol column name for point size encoding
     * @return a {@link Chart} instance
     */
    Chart scatter(String x, String y, String colorCol, String sizeCol);

    /**
     * Creates a histogram of the specified column with default binning.
     *
     * @param column column name to bin
     * @return a {@link Chart} instance
     */
    Chart hist(String column);

    /**
     * Creates a histogram of the specified column with a maximum number of bins.
     *
     * @param column column name to bin
     * @param bins maximum number of bins
     * @return a {@link Chart} instance
     */
    Chart hist(String column, int bins);

    /**
     * Creates a single-column box plot showing distribution and outliers.
     *
     * @param valueCol numerical column name
     * @return a {@link Chart} instance
     */
    Chart box(String valueCol);

    /**
     * Creates a categorized box plot comparing distributions across categories.
     *
     * @param categoryCol categorical column name
     * @param valueCol numerical value column name
     * @return a {@link Chart} instance
     */
    Chart box(String categoryCol, String valueCol);

    /**
     * Creates an area chart mapping {@code x} and {@code y} columns.
     *
     * @param x column name for X axis
     * @param y column name for Y axis
     * @return a {@link Chart} instance
     */
    Chart area(String x, String y);

    /**
     * Creates an area chart mapping {@code x} and {@code y} columns, grouped by {@code colorCol}.
     *
     * @param x column name for X axis
     * @param y column name for Y axis
     * @param colorCol category column for area grouping
     * @return a {@link Chart} instance
     */
    Chart area(String x, String y, String colorCol);

    /**
     * Creates a 2D heatmap matrix mapping {@code x}, {@code y}, and numerical cell {@code values}.
     *
     * @param x column name for X axis
     * @param y column name for Y axis
     * @param values column name for color magnitude
     * @return a {@link Chart} instance
     */
    Chart heatmap(String x, String y, String values);
}
