package com.cupoftea.plot.vega;

import com.cupoftea.plot.Plot;
import com.cupoftea.plot.spi.PlotProvider;
import com.cupoftea.table.interfaces.IDataFrame;

/**
 * Service provider implementation producing Vega-Lite plotting engines.
 */
public class VegaLitePlotProvider implements PlotProvider {

    @Override
    public String name() {
        return "vegalite";
    }

    @Override
    public Plot createPlot(IDataFrame dataFrame) {
        return new VegaLitePlot(dataFrame);
    }
}
