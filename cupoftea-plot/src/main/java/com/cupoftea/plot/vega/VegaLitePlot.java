package com.cupoftea.plot.vega;

import java.util.Objects;

import com.cupoftea.plot.Chart;
import com.cupoftea.plot.Plot;
import com.cupoftea.table.interfaces.IDataFrame;

/**
 * Concrete implementation of {@link Plot} producing interactive {@link VegaLiteChart} instances.
 */
public class VegaLitePlot implements Plot {

    private final IDataFrame dataFrame;

    public VegaLitePlot(IDataFrame dataFrame) {
        this.dataFrame = Objects.requireNonNull(dataFrame, "dataFrame cannot be null");
    }

    @Override
    public Chart line(String x, String y) {
        return line(x, y, null);
    }

    @Override
    public Chart line(String x, String y, String colorCol) {
        return new VegaLiteChart(dataFrame, ChartType.LINE, x, y, colorCol, null, null, 0);
    }

    @Override
    public Chart bar(String x, String y) {
        return bar(x, y, null);
    }

    @Override
    public Chart bar(String x, String y, String colorCol) {
        return new VegaLiteChart(dataFrame, ChartType.BAR, x, y, colorCol, null, null, 0);
    }

    @Override
    public Chart scatter(String x, String y) {
        return scatter(x, y, null, null);
    }

    @Override
    public Chart scatter(String x, String y, String colorCol) {
        return scatter(x, y, colorCol, null);
    }

    @Override
    public Chart scatter(String x, String y, String colorCol, String sizeCol) {
        return new VegaLiteChart(dataFrame, ChartType.SCATTER, x, y, colorCol, sizeCol, null, 0);
    }

    @Override
    public Chart hist(String column) {
        return hist(column, 0);
    }

    @Override
    public Chart hist(String column, int bins) {
        return new VegaLiteChart(dataFrame, ChartType.HIST, column, null, null, null, null, bins);
    }

    @Override
    public Chart box(String valueCol) {
        return box(null, valueCol);
    }

    @Override
    public Chart box(String categoryCol, String valueCol) {
        return new VegaLiteChart(dataFrame, ChartType.BOX, categoryCol, valueCol, null, null, null, 0);
    }

    @Override
    public Chart area(String x, String y) {
        return area(x, y, null);
    }

    @Override
    public Chart area(String x, String y, String colorCol) {
        return new VegaLiteChart(dataFrame, ChartType.AREA, x, y, colorCol, null, null, 0);
    }

    @Override
    public Chart heatmap(String x, String y, String values) {
        return new VegaLiteChart(dataFrame, ChartType.HEATMAP, x, y, null, null, values, 0);
    }
}
