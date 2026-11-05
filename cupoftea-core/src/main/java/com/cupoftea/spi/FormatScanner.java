package com.cupoftea.spi;

import com.cupoftea.lazy.LazySource;

/**
 * Service Provider Interface (SPI) for scanning and streaming tabular data formats lazily.
 * Discovered at runtime via {@link java.util.ServiceLoader}.
 */
public interface FormatScanner {

    /**
     * Unique format identifier handled by this scanner (e.g., "csv", "json", "parquet").
     * Matching is case-insensitive.
     *
     * @return lowercase format name
     */
    String format();

    /**
     * Creates a {@link LazySource} configured with the given scan options.
     *
     * @param options scan options containing source file, path, schema, delimiter, etc.
     * @return configured LazySource
     */
    LazySource scan(ScanOptions options);
}
