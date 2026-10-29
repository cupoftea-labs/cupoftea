package com.cupoftea.utils.configurations;

/**
 * Configuration options for rendering DataFrames into rich, responsive HTML tables.
 */
public class TableHtmlConfiguration {

    private int maxRows = 1000;
    private int pageSize = 25;
    private boolean stickyHeader = true;
    private boolean showDataTypes = true;
    private boolean showIndex = true;
    private boolean scrollable = true;
    private String maxHeight = "450px";
    private boolean interactivePagination = true;
    private boolean fullDocument = false;
    private boolean showSummary = true;
    private String tableId = null;
    private String title = null;
    private String nullPlaceholder = "null";
    private String naPlaceholder = "NA";

    public TableHtmlConfiguration() {
    }

    public String getTableId() {
        return tableId;
    }

    public int getMaxRows() {
        return maxRows;
    }

    public int getPageSize() {
        return pageSize;
    }

    public boolean isStickyHeader() {
        return stickyHeader;
    }

    public boolean isShowDataTypes() {
        return showDataTypes;
    }

    public boolean isShowIndex() {
        return showIndex;
    }

    public boolean isScrollable() {
        return scrollable;
    }

    public String getMaxHeight() {
        return maxHeight;
    }

    public boolean isInteractivePagination() {
        return interactivePagination;
    }

    public boolean isFullDocument() {
        return fullDocument;
    }

    public boolean isShowSummary() {
        return showSummary;
    }

    public String getTitle() {
        return title;
    }

    public String getNullPlaceholder() {
        return nullPlaceholder;
    }

    public String getNaPlaceholder() {
        return naPlaceholder;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final TableHtmlConfiguration config = new TableHtmlConfiguration();

        public Builder maxRows(int maxRows) {
            config.maxRows = maxRows;
            return this;
        }

        public Builder pageSize(int pageSize) {
            config.pageSize = Math.max(1, pageSize);
            return this;
        }

        public Builder stickyHeader(boolean stickyHeader) {
            config.stickyHeader = stickyHeader;
            return this;
        }

        public Builder showDataTypes(boolean showDataTypes) {
            config.showDataTypes = showDataTypes;
            return this;
        }

        public Builder showIndex(boolean showIndex) {
            config.showIndex = showIndex;
            return this;
        }

        public Builder scrollable(boolean scrollable) {
            config.scrollable = scrollable;
            return this;
        }

        public Builder maxHeight(String maxHeight) {
            config.maxHeight = maxHeight != null ? maxHeight : "450px";
            return this;
        }

        public Builder interactivePagination(boolean interactivePagination) {
            config.interactivePagination = interactivePagination;
            return this;
        }

        public Builder fullDocument(boolean fullDocument) {
            config.fullDocument = fullDocument;
            return this;
        }

        public Builder showSummary(boolean showSummary) {
            config.showSummary = showSummary;
            return this;
        }

        public Builder title(String title) {
            config.title = title;
            return this;
        }

        public Builder tableId(String tableId) {
            config.tableId = tableId;
            return this;
        }

        public Builder nullPlaceholder(String nullPlaceholder) {
            config.nullPlaceholder = nullPlaceholder != null ? nullPlaceholder : "null";
            return this;
        }

        public Builder naPlaceholder(String naPlaceholder) {
            config.naPlaceholder = naPlaceholder != null ? naPlaceholder : "NA";
            return this;
        }

        public TableHtmlConfiguration build() {
            return config;
        }
    }
}
