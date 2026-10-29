package com.cupoftea.plot.vega;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.cupoftea.exceptions.ExportException;
import com.cupoftea.plot.Chart;
import com.cupoftea.table.Column;
import com.cupoftea.table.DataType;
import com.cupoftea.table.NA;
import com.cupoftea.table.Row;
import com.cupoftea.table.interfaces.IDataFrame;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Vega-Lite implementation of {@link Chart}. Generates Vega-Lite v5 specs,
 * responsive standalone HTML pages, and interactive Jupyter notebook embeds.
 */
public class VegaLiteChart implements Chart {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final IDataFrame dataFrame;
    private final ChartType chartType;
    private final String xColumn;
    private final String yColumn;
    private final String colorColumn;
    private final String sizeColumn;
    private final String valueColumn;
    private final int bins;
    private final String chartId = "cot-vis-" + UUID.randomUUID().toString().substring(0, 8);

    private String title;
    private int width = 640;
    private int height = 400;
    private String xLabel;
    private String yLabel;
    private String color;
    private List<String> colors;
    private String theme;
    private boolean tooltip = true;
    private boolean interactive = true;

    public VegaLiteChart(IDataFrame dataFrame,
                         ChartType chartType,
                         String xColumn,
                         String yColumn,
                         String colorColumn,
                         String sizeColumn,
                         String valueColumn,
                         int bins) {
        this.dataFrame = dataFrame;
        this.chartType = Objects.requireNonNull(chartType, "chartType cannot be null");
        this.xColumn = xColumn;
        this.yColumn = yColumn;
        this.colorColumn = colorColumn;
        this.sizeColumn = sizeColumn;
        this.valueColumn = valueColumn;
        this.bins = bins;
    }

    @Override
    public Chart title(String title) {
        this.title = title;
        return this;
    }

    @Override
    public Chart width(int width) {
        this.width = Math.max(100, width);
        return this;
    }

    @Override
    public Chart height(int height) {
        this.height = Math.max(100, height);
        return this;
    }

    @Override
    public Chart xLabel(String label) {
        this.xLabel = label;
        return this;
    }

    @Override
    public Chart yLabel(String label) {
        this.yLabel = label;
        return this;
    }

    @Override
    public Chart color(String color) {
        this.color = color;
        return this;
    }

    @Override
    public Chart colors(List<String> colors) {
        this.colors = colors != null ? new ArrayList<>(colors) : null;
        return this;
    }

    @Override
    public Chart theme(String theme) {
        this.theme = theme;
        return this;
    }

    @Override
    public Chart tooltip(boolean tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    @Override
    public Chart interactive(boolean interactive) {
        this.interactive = interactive;
        return this;
    }

    @Override
    public Chart show() {
        try {
            File tempFile = File.createTempFile("cupoftea_chart_", ".html");
            tempFile.deleteOnExit();
            saveHtml(tempFile);

            if (!GraphicsEnvironment.isHeadless()
                    && Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(tempFile.toURI());
            }
        } catch (Throwable ignored) {
            // Headless / CI safe fallback
        }
        return this;
    }

    @Override
    public Chart saveHtml(File file) {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            Files.writeString(file.toPath(), toHtml(true), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ExportException("Failed to save chart HTML file: " + file.getPath(), e);
        }
        return this;
    }

    @Override
    public String toHtml() {
        return toHtml(false);
    }

    @Override
    public String toHtml(boolean standalone) {
        String jsonSpec = toVegaLiteJson();

        if (standalone) {
            return "<!DOCTYPE html>\n" +
                    "<html lang=\"en\">\n" +
                    "<head>\n" +
                    "  <meta charset=\"utf-8\">\n" +
                    "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                    "  <title>" + (title != null ? escapeHtml(title) : "CupOfTea Chart") + "</title>\n" +
                    "  <script src=\"https://cdn.jsdelivr.net/npm/vega@5\"></script>\n" +
                    "  <script src=\"https://cdn.jsdelivr.net/npm/vega-lite@5\"></script>\n" +
                    "  <script src=\"https://cdn.jsdelivr.net/npm/vega-embed@6\"></script>\n" +
                    "  <style>\n" +
                    "    body {\n" +
                    "      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;\n" +
                    "      margin: 24px;\n" +
                    "      background: #ffffff;\n" +
                    "      color: #24292f;\n" +
                    "    }\n" +
                    "    .cot-chart-wrap {\n" +
                    "      max-width: " + width + "px;\n" +
                    "      margin: 0 auto;\n" +
                    "    }\n" +
                    "  </style>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "  <div class=\"cot-chart-wrap\">\n" +
                    "    <div id=\"" + chartId + "\" style=\"width: 100%;\"></div>\n" +
                    "  </div>\n" +
                    "  <script type=\"text/javascript\">\n" +
                    "    var spec = " + jsonSpec + ";\n" +
                    "    vegaEmbed('#" + chartId + "', spec, {renderer: 'canvas', actions: true}).catch(console.error);\n" +
                    "  </script>\n" +
                    "</body>\n" +
                    "</html>\n";
        }

        // Embeddable fragment for notebooks and dashboards
        return "<div id=\"" + chartId + "\" class=\"cupoftea-plot\" style=\"width: 100%; max-width: " + width + "px; margin: 12px 0;\"></div>\n" +
                "<script type=\"text/javascript\">\n" +
                "(function() {\n" +
                "  var spec = " + jsonSpec + ";\n" +
                "  function renderChart() {\n" +
                "    if (typeof vegaEmbed !== 'undefined') {\n" +
                "      vegaEmbed('#" + chartId + "', spec, {renderer: 'canvas', actions: {export: true, source: false, editor: false}}).catch(console.error);\n" +
                "    }\n" +
                "  }\n" +
                "  if (typeof vegaEmbed !== 'undefined') {\n" +
                "    renderChart();\n" +
                "  } else {\n" +
                "    var scripts = [\n" +
                "      'https://cdn.jsdelivr.net/npm/vega@5',\n" +
                "      'https://cdn.jsdelivr.net/npm/vega-lite@5',\n" +
                "      'https://cdn.jsdelivr.net/npm/vega-embed@6'\n" +
                "    ];\n" +
                "    function loadNext(idx) {\n" +
                "      if (idx >= scripts.length) {\n" +
                "        renderChart();\n" +
                "        return;\n" +
                "      }\n" +
                "      var s = document.createElement('script');\n" +
                "      s.src = scripts[idx];\n" +
                "      s.async = false;\n" +
                "      s.onload = function() { loadNext(idx + 1); };\n" +
                "      document.head.appendChild(s);\n" +
                "    }\n" +
                "    loadNext(0);\n" +
                "  }\n" +
                "})();\n" +
                "</script>\n";
    }

    @Override
    public String toVegaLiteJson() {
        try {
            return MAPPER.writeValueAsString(toSpecMap());
        } catch (JsonProcessingException e) {
            throw new ExportException("Failed to serialize Vega-Lite specification to JSON", e);
        }
    }

    @Override
    public Map<String, Object> toSpecMap() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("$schema", "https://vega.github.io/schema/vega-lite/v5.json");

        if (title != null) {
            Map<String, Object> titleMap = new LinkedHashMap<>();
            titleMap.put("text", title);
            titleMap.put("anchor", "start");
            titleMap.put("fontSize", 15);
            spec.put("title", titleMap);
        }

        spec.put("width", width);
        spec.put("height", height);

        // Data values
        Map<String, Object> dataMap = new LinkedHashMap<>();
        dataMap.put("values", extractDataValues());
        spec.put("data", dataMap);

        // Mark definition
        spec.put("mark", buildMark());

        // Encoding definition
        spec.put("encoding", buildEncoding());

        // Zoom/pan interactivity if requested
        if (interactive && (chartType == ChartType.SCATTER || chartType == ChartType.LINE || chartType == ChartType.AREA)) {
            Map<String, Object> param = new LinkedHashMap<>();
            param.put("name", "grid");
            param.put("select", "interval");
            param.put("bind", "scales");
            spec.put("params", Collections.singletonList(param));
        }

        if (theme != null) {
            Map<String, Object> usermeta = new LinkedHashMap<>();
            usermeta.put("theme", theme);
            spec.put("usermeta", usermeta);
        }

        return spec;
    }

    private Object buildMark() {
        Map<String, Object> mark = new LinkedHashMap<>();
        mark.put("tooltip", tooltip);

        switch (chartType) {
            case LINE -> {
                mark.put("type", "line");
                mark.put("point", true);
            }
            case BAR -> mark.put("type", "bar");
            case SCATTER -> {
                mark.put("type", "circle");
                mark.put("size", 60);
            }
            case HIST -> mark.put("type", "bar");
            case BOX -> {
                mark.put("type", "boxplot");
                mark.put("extent", 1.5);
            }
            case AREA -> {
                mark.put("type", "area");
                mark.put("line", true);
                mark.put("point", true);
                mark.put("opacity", 0.65);
            }
            case HEATMAP -> mark.put("type", "rect");
        }

        if (color != null && colorColumn == null) {
            mark.put("color", color);
        }

        return mark;
    }

    private Map<String, Object> buildEncoding() {
        Map<String, Object> encoding = new LinkedHashMap<>();

        switch (chartType) {
            case LINE, AREA -> {
                encoding.put("x", buildFieldEncoding(xColumn, detectVegaType(xColumn), xLabel));
                encoding.put("y", buildFieldEncoding(yColumn, "quantitative", yLabel));
                if (colorColumn != null) {
                    encoding.put("color", buildFieldEncoding(colorColumn, detectVegaType(colorColumn), null));
                }
            }
            case BAR -> {
                encoding.put("x", buildFieldEncoding(xColumn, detectVegaType(xColumn), xLabel));
                encoding.put("y", buildFieldEncoding(yColumn, "quantitative", yLabel));
                if (colorColumn != null) {
                    encoding.put("color", buildFieldEncoding(colorColumn, detectVegaType(colorColumn), null));
                }
            }
            case SCATTER -> {
                encoding.put("x", buildFieldEncoding(xColumn, "quantitative", xLabel));
                encoding.put("y", buildFieldEncoding(yColumn, "quantitative", yLabel));
                if (colorColumn != null) {
                    encoding.put("color", buildFieldEncoding(colorColumn, detectVegaType(colorColumn), null));
                }
                if (sizeColumn != null) {
                    encoding.put("size", buildFieldEncoding(sizeColumn, "quantitative", null));
                }
            }
            case HIST -> {
                Map<String, Object> xEnc = new LinkedHashMap<>();
                xEnc.put("field", xColumn);
                xEnc.put("type", "quantitative");
                Map<String, Object> binMap = new LinkedHashMap<>();
                binMap.put("maxbins", bins > 0 ? bins : 15);
                xEnc.put("bin", binMap);
                if (xLabel != null) {
                    xEnc.put("title", xLabel);
                }
                encoding.put("x", xEnc);

                Map<String, Object> yEnc = new LinkedHashMap<>();
                yEnc.put("aggregate", "count");
                yEnc.put("type", "quantitative");
                yEnc.put("title", yLabel != null ? yLabel : "Count");
                encoding.put("y", yEnc);
            }
            case BOX -> {
                if (xColumn != null && !xColumn.isEmpty()) {
                    encoding.put("x", buildFieldEncoding(xColumn, detectVegaType(xColumn), xLabel));
                }
                encoding.put("y", buildFieldEncoding(yColumn, "quantitative", yLabel));
            }
            case HEATMAP -> {
                encoding.put("x", buildFieldEncoding(xColumn, detectVegaType(xColumn), xLabel));
                encoding.put("y", buildFieldEncoding(yColumn, detectVegaType(yColumn), yLabel));
                Map<String, Object> colorEnc = buildFieldEncoding(valueColumn, "quantitative", null);
                if (yLabel != null) {
                    colorEnc.put("title", yLabel);
                }
                encoding.put("color", colorEnc);
            }
        }

        if (colors != null && !colors.isEmpty() && encoding.containsKey("color")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> colorEnc = (Map<String, Object>) encoding.get("color");
            Map<String, Object> scale = new LinkedHashMap<>();
            scale.put("range", colors);
            colorEnc.put("scale", scale);
        }

        return encoding;
    }

    private Map<String, Object> buildFieldEncoding(String field, String type, String customTitle) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("field", field);
        map.put("type", type);
        if (customTitle != null && !customTitle.isEmpty()) {
            map.put("title", customTitle);
        }
        return map;
    }

    private List<Map<String, Object>> extractDataValues() {
        if (dataFrame == null || dataFrame.size() == null || dataFrame.size() == 0) {
            return Collections.emptyList();
        }

        List<Column> headers = dataFrame.getHeaders();
        int rowCount = dataFrame.size();
        List<Map<String, Object>> values = new ArrayList<>(rowCount);

        for (int i = 0; i < rowCount; i++) {
            Row row = dataFrame.getRow(i);
            Map<String, Object> map = new LinkedHashMap<>();
            for (Column col : headers) {
                String name = col.columnName();
                Object val = row != null ? row.get(name) : null;
                if (val == null || NA.isNA(val)) {
                    map.put(name, null);
                } else if (val instanceof Number || val instanceof Boolean) {
                    map.put(name, val);
                } else {
                    map.put(name, val.toString());
                }
            }
            values.add(map);
        }
        return values;
    }

    private String detectVegaType(String columnName) {
        if (columnName == null || dataFrame == null) {
            return "nominal";
        }
        Column col = dataFrame.getColumnHeader(columnName);
        if (col == null) {
            return "nominal";
        }
        Class<?> type = col.columnType();
        DataType dt = DataType.fromJavaType(type);

        if (dt == DataType.INT || dt == DataType.DOUBLE || dt == DataType.FLOAT
                || dt == DataType.LONG || dt == DataType.DECIMAL) {
            return "quantitative";
        }
        if (dt == DataType.DATE || dt == DataType.DATETIME || dt == DataType.INSTANT) {
            return "temporal";
        }
        return "nominal";
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
