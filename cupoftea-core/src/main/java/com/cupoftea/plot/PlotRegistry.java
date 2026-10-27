package com.cupoftea.plot;

import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

import com.cupoftea.exceptions.CupOfTeaException;
import com.cupoftea.plot.spi.PlotProvider;
import com.cupoftea.table.interfaces.IDataFrame;

/**
 * Registry and runtime discovery hub for plotting providers using {@link ServiceLoader}.
 */
public final class PlotRegistry {

    private static final Map<String, PlotProvider> PROVIDERS = new ConcurrentHashMap<>();
    private static volatile boolean loaded = false;
    private static volatile String defaultProviderName = "vegalite";

    private PlotRegistry() {
    }

    private static void ensureLoaded() {
        if (!loaded) {
            synchronized (PlotRegistry.class) {
                if (!loaded) {
                    reload();
                    loaded = true;
                }
            }
        }
    }

    /**
     * Reloads plot providers from the classpath via {@link ServiceLoader}.
     */
    public static synchronized void reload() {
        PROVIDERS.clear();
        for (PlotProvider provider : ServiceLoader.load(PlotProvider.class)) {
            PROVIDERS.put(provider.name().toLowerCase(), provider);
        }
        if (!PROVIDERS.isEmpty() && !PROVIDERS.containsKey(defaultProviderName)) {
            defaultProviderName = PROVIDERS.keySet().iterator().next();
        }
    }

    public static Optional<PlotProvider> getProvider(String name) {
        if (name == null) {
            return Optional.empty();
        }
        ensureLoaded();
        return Optional.ofNullable(PROVIDERS.get(name.toLowerCase()));
    }

    public static PlotProvider requireProvider() {
        return requireProvider(defaultProviderName);
    }

    public static PlotProvider requireProvider(String name) {
        ensureLoaded();
        if (PROVIDERS.isEmpty()) {
            throw new CupOfTeaException(
                    "No plot provider found on the classpath. " +
                    "Ensure the visualization module 'cupoftea-plot' (or 'cupoftea-all') is included in your dependencies.");
        }
        PlotProvider provider = PROVIDERS.get(name != null ? name.toLowerCase() : defaultProviderName);
        if (provider == null) {
            // fallback to first available
            provider = PROVIDERS.values().iterator().next();
        }
        return provider;
    }

    public static void registerProvider(PlotProvider provider) {
        if (provider != null && provider.name() != null) {
            PROVIDERS.put(provider.name().toLowerCase(), provider);
        }
    }

    public static void setDefaultProviderName(String name) {
        if (name != null) {
            defaultProviderName = name.toLowerCase();
        }
    }

    public static Plot createPlot(IDataFrame dataFrame) {
        return requireProvider().createPlot(dataFrame);
    }

    public static Plot createPlot(IDataFrame dataFrame, String engine) {
        return requireProvider(engine).createPlot(dataFrame);
    }
}
