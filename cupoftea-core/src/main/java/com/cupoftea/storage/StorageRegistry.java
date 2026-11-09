package com.cupoftea.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;

import com.cupoftea.exceptions.ImportException;

/**
 * Global registry and resolver for {@link StorageFileSystem} providers.
 */
public final class StorageRegistry {

    private static final List<StorageFileSystem> FILESYSTEMS = new CopyOnWriteArrayList<>();
    private static volatile boolean loaded = false;

    private StorageRegistry() {
    }

    private static void ensureLoaded() {
        if (!loaded) {
            synchronized (StorageRegistry.class) {
                if (!loaded) {
                    reload();
                    loaded = true;
                }
            }
        }
    }

    /**
     * Reloads all {@link StorageFileSystem} implementations discovered via {@link ServiceLoader}.
     */
    public static synchronized void reload() {
        FILESYSTEMS.clear();
        for (StorageFileSystem fs : ServiceLoader.load(StorageFileSystem.class)) {
            FILESYSTEMS.add(fs);
        }
    }

    /**
     * Registers a custom {@link StorageFileSystem} programmatically.
     *
     * @param fs filesystem provider.
     */
    public static void register(StorageFileSystem fs) {
        if (fs != null) {
            ensureLoaded();
            FILESYSTEMS.add(0, fs); // prioritize manually registered ones
        }
    }

    /**
     * Resolves the {@link StorageFileSystem} capable of handling the specified URI scheme.
     *
     * @param uri URI to check.
     * @return optional containing the matching filesystem, or empty if none matches.
     */
    public static Optional<StorageFileSystem> getFileSystem(URI uri) {
        if (uri == null || uri.getScheme() == null) {
            return Optional.empty();
        }
        ensureLoaded();
        String scheme = uri.getScheme().toLowerCase();
        for (StorageFileSystem fs : FILESYSTEMS) {
            if (fs.supports(scheme)) {
                return Optional.of(fs);
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves the {@link StorageFileSystem} or throws {@link ImportException} if not found.
     *
     * @param uri URI to check.
     * @return matching filesystem.
     */
    public static StorageFileSystem requireFileSystem(URI uri) {
        return getFileSystem(uri).orElseThrow(() -> new ImportException(
                "No StorageFileSystem found for scheme '" + (uri != null ? uri.getScheme() : "null") +
                "'. Make sure 'cupoftea-storage' or the appropriate cloud module is in the classpath."));
    }

    /**
     * Checks if a given string represents a remote URI with a registered storage scheme.
     *
     * @param pathOrUri path or URI string.
     * @return {@code true} if supported by a registered {@link StorageFileSystem}.
     */
    public static boolean isStorageUri(String pathOrUri) {
        if (pathOrUri == null) {
            return false;
        }
        try {
            int colonIdx = pathOrUri.indexOf("://");
            if (colonIdx > 0) {
                URI uri = URI.create(pathOrUri);
                return getFileSystem(uri).isPresent();
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Opens a seekable byte channel for the given URI string or throws if unsupported.
     */
    public static SeekableByteChannel openSeekable(String uriStr) throws IOException {
        URI uri = URI.create(uriStr);
        return requireFileSystem(uri).openSeekable(uri);
    }

    /**
     * Opens an input stream for the given URI string or throws if unsupported.
     */
    public static InputStream openInputStream(String uriStr) throws IOException {
        URI uri = URI.create(uriStr);
        return requireFileSystem(uri).openInputStream(uri);
    }

    /**
     * Opens an output stream for the given URI string or throws if unsupported.
     */
    public static OutputStream openOutputStream(String uriStr) throws IOException {
        URI uri = URI.create(uriStr);
        return requireFileSystem(uri).openOutputStream(uri);
    }
}
