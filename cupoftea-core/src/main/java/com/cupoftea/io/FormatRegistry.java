package com.cupoftea.io;

import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.spi.FormatReader;
import com.cupoftea.spi.FormatScanner;
import com.cupoftea.spi.FormatWriter;

/**
 * Registry and runtime discovery hub for format readers, writers, and scanners using {@link ServiceLoader}.
 */
public final class FormatRegistry {

    private static final Map<String, FormatReader> READERS = new ConcurrentHashMap<>();
    private static final Map<String, FormatWriter> WRITERS = new ConcurrentHashMap<>();
    private static final Map<String, FormatScanner> SCANNERS = new ConcurrentHashMap<>();
    private static volatile boolean loaded = false;

    private FormatRegistry() {
    }

    private static void ensureLoaded() {
        if (!loaded) {
            synchronized (FormatRegistry.class) {
                if (!loaded) {
                    reload();
                    loaded = true;
                }
            }
        }
    }

    /**
     * Discovers providers via {@link ServiceLoader}.
     */
    public static synchronized void reload() {
        READERS.clear();
        WRITERS.clear();
        SCANNERS.clear();
        for (FormatReader reader : ServiceLoader.load(FormatReader.class)) {
            READERS.put(reader.format().toLowerCase(), reader);
        }
        for (FormatWriter writer : ServiceLoader.load(FormatWriter.class)) {
            WRITERS.put(writer.format().toLowerCase(), writer);
        }
        for (FormatScanner scanner : ServiceLoader.load(FormatScanner.class)) {
            SCANNERS.put(scanner.format().toLowerCase(), scanner);
        }
    }

    public static Optional<FormatReader> getReader(String format) {
        if (format == null) {
            return Optional.empty();
        }
        ensureLoaded();
        return Optional.ofNullable(READERS.get(format.toLowerCase()));
    }

    public static FormatReader requireReader(String format) {
        return getReader(format).orElseThrow(() -> new ImportException(
                "No format reader found for format '" + format +
                "'. Ensure the optional module 'cupoftea-" + format + "' is included in your dependencies."));
    }

    public static Optional<FormatWriter> getWriter(String format) {
        if (format == null) {
            return Optional.empty();
        }
        ensureLoaded();
        return Optional.ofNullable(WRITERS.get(format.toLowerCase()));
    }

    public static FormatWriter requireWriter(String format) {
        return getWriter(format).orElseThrow(() -> new ExportException(
                "No format writer found for format '" + format +
                "'. Ensure the optional module 'cupoftea-" + format + "' is included in your dependencies."));
    }

    public static void registerReader(FormatReader reader) {
        if (reader != null && reader.format() != null) {
            READERS.put(reader.format().toLowerCase(), reader);
        }
    }

    public static void registerWriter(FormatWriter writer) {
        if (writer != null && writer.format() != null) {
            WRITERS.put(writer.format().toLowerCase(), writer);
        }
    }

    public static Optional<FormatScanner> getScanner(String format) {
        if (format == null) {
            return Optional.empty();
        }
        ensureLoaded();
        return Optional.ofNullable(SCANNERS.get(format.toLowerCase()));
    }

    public static FormatScanner requireScanner(String format) {
        return getScanner(format).orElseThrow(() -> new ImportException(
                "No format scanner found for format '" + format +
                "'. Ensure the optional module 'cupoftea-" + format + "' is included in your dependencies."));
    }

    public static void registerScanner(FormatScanner scanner) {
        if (scanner != null && scanner.format() != null) {
            SCANNERS.put(scanner.format().toLowerCase(), scanner);
        }
    }
}
