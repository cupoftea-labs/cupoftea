package com.cupoftea.spi;

import com.cupoftea.table.interfaces.IDataFrame;

/**
 * Service Provider Interface (SPI) for writing tabular data formats.
 * Discovered at runtime via {@link java.util.ServiceLoader}.
 */
public interface FormatWriter {

    /**
     * Unique format identifier handled by this writer (e.g., "csv", "json", "parquet", "yaml").
     * Matching is case-insensitive.
     *
     * @return lowercase format name
     */
    String format();

    /**
     * Writes the given {@link IDataFrame} according to the write options.
     *
     * @param dataFrame dataframe to write
     * @param options output options containing target file, path, or configuration
     */
    void write(IDataFrame dataFrame, WriteOptions options);
}
