package com.cupoftea.spi;

import com.cupoftea.table.interfaces.IDataFrame;

/**
 * Service Provider Interface (SPI) for reading tabular data formats.
 * Discovered at runtime via {@link java.util.ServiceLoader}.
 */
public interface FormatReader {

    /**
     * Unique format identifier handled by this reader (e.g., "csv", "json", "parquet", "excel", "html", "yaml", "xml").
     * Matching is case-insensitive.
     *
     * @return lowercase format name
     */
    String format();

    /**
     * Reads tabular data matching the given read options and returns a populated {@link IDataFrame}.
     *
     * @param options input options containing source file, path, text, URL, schema, or configuration
     * @return populated IDataFrame
     */
    IDataFrame read(ReadOptions options);
}
