package com.cupoftea.plot;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Declarative chart interface produced by {@link Plot}.
 * Supports fluent configuration, HTML export, desktop preview,
 * and Jupyter / IJava notebook rich MIME rendering.
 */
public interface Chart {

    /**
     * Sets the chart title.
     *
     * @param title the chart title
     * @return this chart
     */
    Chart title(String title);

    /**
     * Sets the chart display width in pixels.
     *
     * @param width the width in pixels
     * @return this chart
     */
    Chart width(int width);

    /**
     * Sets the chart display height in pixels.
     *
     * @param height the height in pixels
     * @return this chart
     */
    Chart height(int height);

    /**
     * Sets a custom label for the X axis.
     *
     * @param label axis label
     * @return this chart
     */
    Chart xLabel(String label);

    /**
     * Sets a custom label for the Y axis.
     *
     * @param label axis label
     * @return this chart
     */
    Chart yLabel(String label);

    /**
     * Sets a primary/accent color for marks (e.g. hex code, rgb, or color name).
     *
     * @param color the color definition
     * @return this chart
     */
    Chart color(String color);

    /**
     * Sets a custom color palette/scale for categorical encoding.
     *
     * @param colors list of color hex codes or names
     * @return this chart
     */
    Chart colors(List<String> colors);

    /**
     * Sets a visual theme (e.g. "default", "dark", "quartz", "vox", "fivethirtyeight").
     *
     * @param theme the theme name
     * @return this chart
     */
    Chart theme(String theme);

    /**
     * Enables or disables interactive tooltips.
     *
     * @param tooltip whether tooltips are enabled
     * @return this chart
     */
    Chart tooltip(boolean tooltip);

    /**
     * Enables or disables interactive pan/zoom navigation.
     *
     * @param interactive whether interactivity is enabled
     * @return this chart
     */
    Chart interactive(boolean interactive);

    /**
     * Opens an interactive HTML preview of the chart in the local default web browser.
     * In headless environments, this method completes safely without throwing exceptions.
     *
     * @return this chart
     */
    Chart show();

    /**
     * Saves the chart as a standalone interactive HTML file.
     *
     * @param file target file
     * @return this chart
     */
    Chart saveHtml(File file);

    /**
     * Saves the chart as a standalone interactive HTML file.
     *
     * @param path target file path
     * @return this chart
     */
    default Chart saveHtml(String path) {
        if (path == null) {
            throw new IllegalArgumentException("Path cannot be null");
        }
        return saveHtml(new File(path));
    }

    /**
     * Saves the chart as a standalone interactive HTML file.
     *
     * @param path target file path
     * @return this chart
     */
    default Chart saveHtml(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("Path cannot be null");
        }
        return saveHtml(path.toFile());
    }

    /**
     * Returns an embeddable interactive HTML representation of the chart.
     *
     * @return HTML string
     */
    String toHtml();

    /**
     * Returns an interactive HTML representation of the chart.
     *
     * @param standalone if {@code true}, generates a complete HTML document (&lt;!DOCTYPE html&gt;);
     *                   if {@code false}, returns an embeddable HTML fragment.
     * @return HTML string
     */
    String toHtml(boolean standalone);

    /**
     * Returns the underlying Vega-Lite specification as a JSON string.
     *
     * @return Vega-Lite JSON string
     */
    String toVegaLiteJson();

    /**
     * Returns the underlying chart specification as a nested map.
     *
     * @return specification map
     */
    Map<String, Object> toSpecMap();

    /**
     * Jupyter / IJava notebook MIME contract method.
     * Evaluated automatically when the chart is the cell result in Jupyter/IJava notebooks.
     *
     * @return interactive HTML snippet
     */
    default String renderHtml() {
        return toHtml();
    }

    /**
     * Jupyter / Polyglot notebook MIME representation bundle.
     *
     * @return map mapping MIME type ("text/html") to HTML content
     */
    default Map<String, String> render() {
        return Collections.singletonMap("text/html", toHtml());
    }
}
