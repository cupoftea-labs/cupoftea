package com.cupoftea.plot.spi;

import com.cupoftea.plot.Plot;
import com.cupoftea.table.interfaces.IDataFrame;

/**
 * Service Provider Interface (SPI) for pluggable visualization engines.
 * Discovered at runtime via {@link java.util.ServiceLoader}.
 */
public interface PlotProvider {

    /**
     * Unique identifier for this plotting engine (e.g., "vegalite", "plotly").
     *
     * @return engine name
     */
    String name();

    /**
     * Creates a {@link Plot} builder bound to the given {@link IDataFrame}.
     *
     * @param dataFrame the source DataFrame
     * @return a {@link Plot} instance
     */
    Plot createPlot(IDataFrame dataFrame);
}
