package com.cupoftea.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.cupoftea.table.Column;
import com.cupoftea.table.DataType;
import com.cupoftea.table.NA;
import com.cupoftea.table.Row;
import com.cupoftea.table.interfaces.IDataFrame;
import com.cupoftea.utils.configurations.TableHtmlConfiguration;

/**
 * Renders DataFrames into clean, responsive HTML tables with sticky headers,
 * data type badges, scrolling, and interactive pagination.
 */
public final class TableHtmlRenderer {

    private TableHtmlRenderer() {
    }

    public static String render(IDataFrame dataFrame, TableHtmlConfiguration configuration) {
        TableHtmlConfiguration config = configuration != null ? configuration : new TableHtmlConfiguration();
        String tableId = (config.getTableId() != null && !config.getTableId().isEmpty())
                ? config.getTableId()
                : ("cot-tbl-" + UUID.randomUUID().toString().substring(0, 8));

        ArrayList<Column> headers = dataFrame != null && dataFrame.getHeaders() != null
                ? dataFrame.getHeaders() : new ArrayList<>();
        int totalRows = dataFrame != null && dataFrame.size() != null ? dataFrame.size() : 0;
        int maxRows = config.getMaxRows() > 0 ? Math.min(config.getMaxRows(), totalRows) : totalRows;
        int pageSize = config.getPageSize() > 0 ? config.getPageSize() : 25;

        StringBuilder sb = new StringBuilder();

        if (config.isFullDocument()) {
            sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
              .append("<meta charset=\"UTF-8\">\n")
              .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
              .append("<title>").append(config.getTitle() != null ? escapeHtml(config.getTitle()) : "CupOfTea DataFrame").append("</title>\n")
              .append("<style>\n")
              .append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; margin: 24px; background: #fafafa; }\n")
              .append("</style>\n")
              .append("</head>\n<body>\n");
        }

        // Table container wrapper
        sb.append("<div id=\"").append(tableId).append("\" class=\"cot-container\">\n");

        // Scoped Stylesheet
        sb.append("<style>\n")
          .append("#").append(tableId).append(" {\n")
          .append("  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;\n")
          .append("  font-size: 13px;\n")
          .append("  line-height: 1.5;\n")
          .append("  color: #24292f;\n")
          .append("  margin: 12px 0;\n")
          .append("  border: 1px solid #d0d7de;\n")
          .append("  border-radius: 6px;\n")
          .append("  background: #ffffff;\n")
          .append("  overflow: hidden;\n")
          .append("  box-shadow: 0 1px 3px rgba(0,0,0,0.06);\n")
          .append("}\n")
          .append("#").append(tableId).append(" .cot-header-bar {\n")
          .append("  padding: 10px 14px;\n")
          .append("  background: #f6f8fa;\n")
          .append("  border-bottom: 1px solid #d0d7de;\n")
          .append("  font-weight: 600;\n")
          .append("  font-size: 14px;\n")
          .append("  color: #1f2328;\n")
          .append("}\n")
          .append("#").append(tableId).append(" .cot-scroll-box {\n")
          .append(config.isScrollable() ? "  max-height: " + config.getMaxHeight() + ";\n" : "")
          .append("  overflow-x: auto;\n")
          .append("  overflow-y: auto;\n")
          .append("}\n")
          .append("#").append(tableId).append(" table.cot-table {\n")
          .append("  width: 100%;\n")
          .append("  border-collapse: collapse;\n")
          .append("  text-align: left;\n")
          .append("  border-spacing: 0;\n")
          .append("}\n")
          .append("#").append(tableId).append(" th.cot-th {\n")
          .append(config.isStickyHeader() ? "  position: sticky; top: 0; z-index: 2;\n" : "")
          .append("  background-color: #f6f8fa;\n")
          .append("  border-bottom: 2px solid #d0d7de;\n")
          .append("  padding: 8px 12px;\n")
          .append("  font-weight: 600;\n")
          .append("  white-space: nowrap;\n")
          .append("  vertical-align: bottom;\n")
          .append("  box-shadow: inset 0 -1px 0 #d0d7de;\n")
          .append("}\n")
          .append("#").append(tableId).append(" .cot-th-name {\n")
          .append("  display: block;\n")
          .append("  color: #1f2328;\n")
          .append("}\n")
          .append("#").append(tableId).append(" .cot-th-type {\n")
          .append("  display: inline-block;\n")
          .append("  font-size: 10px;\n")
          .append("  font-weight: 500;\n")
          .append("  color: #57606a;\n")
          .append("  background: #eaeef2;\n")
          .append("  padding: 1px 5px;\n")
          .append("  border-radius: 4px;\n")
          .append("  margin-top: 3px;\n")
          .append("  font-family: ui-monospace, SFMono-Regular, 'SF Mono', Menlo, Consolas, monospace;\n")
          .append("}\n")
          .append("#").append(tableId).append(" td.cot-td {\n")
          .append("  padding: 6px 12px;\n")
          .append("  border-bottom: 1px solid #eaeef2;\n")
          .append("  white-space: nowrap;\n")
          .append("  max-width: 320px;\n")
          .append("  overflow: hidden;\n")
          .append("  text-overflow: ellipsis;\n")
          .append("}\n")
          .append("#").append(tableId).append(" tr.cot-row:nth-child(even) {\n")
          .append("  background-color: #fafbfc;\n")
          .append("}\n")
          .append("#").append(tableId).append(" tr.cot-row:hover {\n")
          .append("  background-color: #f2f5f8;\n")
          .append("}\n")
          .append("#").append(tableId).append(" td.cot-idx, #").append(tableId).append(" th.cot-idx {\n")
          .append("  font-family: ui-monospace, SFMono-Regular, 'SF Mono', Menlo, Consolas, monospace;\n")
          .append("  font-size: 11px;\n")
          .append("  color: #8c959f;\n")
          .append("  text-align: right;\n")
          .append("  background-color: #fbfcfd;\n")
          .append("  border-right: 1px solid #e1e4e8;\n")
          .append("  width: 48px;\n")
          .append("  user-select: none;\n")
          .append("}\n")
          .append("#").append(tableId).append(" .cot-num {\n")
          .append("  text-align: right;\n")
          .append("  font-variant-numeric: tabular-nums;\n")
          .append("}\n")
          .append("#").append(tableId).append(" .cot-na {\n")
          .append("  color: #cf222e;\n")
          .append("  font-style: italic;\n")
          .append("  font-weight: 500;\n")
          .append("  opacity: 0.85;\n")
          .append("}\n")
          .append("#").append(tableId).append(" .cot-null {\n")
          .append("  color: #8c959f;\n")
          .append("  font-style: italic;\n")
          .append("}\n")
          .append("#").append(tableId).append(" .cot-footer {\n")
          .append("  display: flex;\n")
          .append("  justify-content: space-between;\n")
          .append("  align-items: center;\n")
          .append("  padding: 8px 12px;\n")
          .append("  background: #f6f8fa;\n")
          .append("  border-top: 1px solid #d0d7de;\n")
          .append("  font-size: 12px;\n")
          .append("  color: #57606a;\n")
          .append("}\n")
          .append("#").append(tableId).append(" .cot-btn {\n")
          .append("  background: #ffffff;\n")
          .append("  border: 1px solid #d0d7de;\n")
          .append("  border-radius: 4px;\n")
          .append("  padding: 3px 10px;\n")
          .append("  font-size: 12px;\n")
          .append("  color: #24292f;\n")
          .append("  font-weight: 500;\n")
          .append("  cursor: pointer;\n")
          .append("  transition: background 0.15s;\n")
          .append("}\n")
          .append("#").append(tableId).append(" .cot-btn:hover:not(:disabled) {\n")
          .append("  background: #f3f4f6;\n")
          .append("}\n")
          .append("#").append(tableId).append(" .cot-btn:disabled {\n")
          .append("  opacity: 0.5;\n")
          .append("  cursor: not-allowed;\n")
          .append("}\n")
          .append("</style>\n");

        // Optional title bar
        if (config.getTitle() != null && !config.getTitle().isEmpty()) {
            sb.append("<div class=\"cot-header-bar\">")
              .append(escapeHtml(config.getTitle()))
              .append("</div>\n");
        }

        // Scroll box
        sb.append("<div class=\"cot-scroll-box\">\n");
        sb.append("<table class=\"cot-table\">\n");

        // Headers
        sb.append("<thead>\n<tr>\n");
        if (config.isShowIndex()) {
            String indexColName = dataFrame != null ? dataFrame.getIndex() : null;
            sb.append("<th class=\"cot-th cot-idx\">")
              .append(indexColName != null ? escapeHtml(indexColName) : "#")
              .append("</th>\n");
        }

        for (Column col : headers) {
            String colName = col.columnName();
            String typeName = getColumnTypeName(col);
            boolean isNumeric = isNumericType(col);

            sb.append("<th class=\"cot-th").append(isNumeric ? " cot-num" : "").append("\">\n")
              .append("  <span class=\"cot-th-name\">").append(escapeHtml(colName)).append("</span>\n");
            if (config.isShowDataTypes()) {
                sb.append("  <span class=\"cot-th-type\">").append(escapeHtml(typeName)).append("</span>\n");
            }
            sb.append("</th>\n");
        }
        sb.append("</tr>\n</thead>\n");

        // Body rows
        sb.append("<tbody class=\"cot-tbody\">\n");
        if (totalRows == 0 || headers.isEmpty()) {
            int colSpan = headers.size() + (config.isShowIndex() ? 1 : 0);
            sb.append("<tr class=\"cot-row\"><td class=\"cot-td\" colspan=\"").append(Math.max(1, colSpan))
              .append("\" style=\"text-align: center; color: #8c959f; padding: 24px;\"><em>Empty DataFrame (0 rows)</em></td></tr>\n");
        } else {
            for (int r = 0; r < maxRows; r++) {
                Row row = dataFrame.getRow(r);
                boolean hiddenInitially = config.isInteractivePagination() && r >= pageSize;
                sb.append("<tr class=\"cot-row\" data-row=\"").append(r).append("\"")
                  .append(hiddenInitially ? " style=\"display:none;\"" : "")
                  .append(">\n");

                if (config.isShowIndex()) {
                    sb.append("<td class=\"cot-td cot-idx\">").append(r).append("</td>\n");
                }

                for (Column col : headers) {
                    Object val = row != null ? row.get(col.columnName()) : null;
                    boolean isNumeric = isNumericType(col);
                    sb.append("<td class=\"cot-td").append(isNumeric ? " cot-num" : "").append("\">");

                    if (val == null) {
                        sb.append("<span class=\"cot-null\">").append(escapeHtml(config.getNullPlaceholder())).append("</span>");
                    } else if (NA.isNA(val)) {
                        sb.append("<span class=\"cot-na\">").append(escapeHtml(config.getNaPlaceholder())).append("</span>");
                    } else {
                        sb.append(escapeHtml(String.valueOf(val)));
                    }

                    sb.append("</td>\n");
                }
                sb.append("</tr>\n");
            }
        }
        sb.append("</tbody>\n");
        sb.append("</table>\n");
        sb.append("</div>\n"); // end scroll box

        // Footer / Pagination Bar
        if (config.isShowSummary() || (config.isInteractivePagination() && totalRows > pageSize)) {
            sb.append("<div class=\"cot-footer\">\n");
            sb.append("  <span class=\"cot-summary-text\">")
              .append(totalRows).append(" rows &times; ").append(headers.size()).append(" columns");
            if (maxRows < totalRows) {
                sb.append(" (showing first ").append(maxRows).append(")");
            }
            sb.append("</span>\n");

            if (config.isInteractivePagination() && maxRows > pageSize) {
                sb.append("  <div class=\"cot-nav\">\n")
                  .append("    <button type=\"button\" class=\"cot-btn cot-btn-prev\" disabled>&larr; Prev</button>\n")
                  .append("    <span class=\"cot-page-info\" style=\"margin: 0 8px;\">Page 1 of ")
                  .append((int) Math.ceil((double) maxRows / pageSize))
                  .append("</span>\n")
                  .append("    <button type=\"button\" class=\"cot-btn cot-btn-next\">&rarr; Next</button>\n")
                  .append("  </div>\n");
            }
            sb.append("</div>\n");
        }

        // Interactive pagination JavaScript
        if (config.isInteractivePagination() && maxRows > pageSize) {
            sb.append("<script type=\"text/javascript\">\n")
              .append("(function() {\n")
              .append("  var root = document.getElementById('").append(tableId).append("');\n")
              .append("  if (!root) return;\n")
              .append("  var rows = root.querySelectorAll('tbody.cot-tbody tr.cot-row');\n")
              .append("  var pageSize = ").append(pageSize).append(";\n")
              .append("  var totalRows = rows.length;\n")
              .append("  var totalPages = Math.ceil(totalRows / pageSize) || 1;\n")
              .append("  var currentPage = 1;\n")
              .append("  var prevBtn = root.querySelector('.cot-btn-prev');\n")
              .append("  var nextBtn = root.querySelector('.cot-btn-next');\n")
              .append("  var pageInfo = root.querySelector('.cot-page-info');\n")
              .append("  function updatePage(page) {\n")
              .append("    currentPage = Math.max(1, Math.min(page, totalPages));\n")
              .append("    var start = (currentPage - 1) * pageSize;\n")
              .append("    var end = start + pageSize;\n")
              .append("    for (var i = 0; i < totalRows; i++) {\n")
              .append("      rows[i].style.display = (i >= start && i < end) ? '' : 'none';\n")
              .append("    }\n")
              .append("    if (pageInfo) pageInfo.textContent = 'Page ' + currentPage + ' of ' + totalPages;\n")
              .append("    if (prevBtn) prevBtn.disabled = (currentPage === 1);\n")
              .append("    if (nextBtn) nextBtn.disabled = (currentPage === totalPages);\n")
              .append("  }\n")
              .append("  if (prevBtn) prevBtn.addEventListener('click', function() { updatePage(currentPage - 1); });\n")
              .append("  if (nextBtn) nextBtn.addEventListener('click', function() { updatePage(currentPage + 1); });\n")
              .append("  updatePage(1);\n")
              .append("})();\n")
              .append("</script>\n");
        }

        sb.append("</div>\n"); // end cot-container

        if (config.isFullDocument()) {
            sb.append("</body>\n</html>\n");
        }

        return sb.toString();
    }

    private static String getColumnTypeName(Column col) {
        if (col == null) {
            return "OBJECT";
        }
        Class<?> type = col.columnType();
        DataType dt = DataType.fromJavaType(type);
        if (dt != null && dt != DataType.OBJECT) {
            return dt.name();
        }
        if (type != null) {
            return type.getSimpleName().toUpperCase();
        }
        return "OBJECT";
    }

    private static boolean isNumericType(Column col) {
        if (col == null) return false;
        Class<?> type = col.columnType();
        DataType dt = DataType.fromJavaType(type);
        if (dt == DataType.INT || dt == DataType.DOUBLE || dt == DataType.FLOAT
                || dt == DataType.LONG || dt == DataType.DECIMAL) {
            return true;
        }
        return type != null && Number.class.isAssignableFrom(type);
    }

    public static String escapeHtml(String text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder(Math.max(16, text.length()));
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&':  out.append("&amp;"); break;
                case '<':  out.append("&lt;"); break;
                case '>':  out.append("&gt;"); break;
                case '"':  out.append("&quot;"); break;
                case '\'': out.append("&#39;"); break;
                default:   out.append(c); break;
            }
        }
        return out.toString();
    }
}
