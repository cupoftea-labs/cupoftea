package com.cupoftea.exporters.configurations;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Configuration options for XML exporting.
 */
public class XmlExporterConfiguration {

    private String rootElement = "records";
    private String rowElement = "record";
    private Charset charset = StandardCharsets.UTF_8;
    private boolean formatted = true;
    private String indent = "  ";
    private String nullValue = "";
    private int bufferSize = 64 * 1024; // 64 KB default buffer

    public XmlExporterConfiguration() {
    }

    public static XmlExporterConfiguration builder() {
        return new XmlExporterConfiguration();
    }

    public String getRootElement() {
        return rootElement;
    }

    public XmlExporterConfiguration setRootElement(String rootElement) {
        this.rootElement = (rootElement != null && !rootElement.isBlank()) ? rootElement.trim() : "records";
        return this;
    }

    public XmlExporterConfiguration rootElement(String rootElement) {
        return setRootElement(rootElement);
    }

    public String getRowElement() {
        return rowElement;
    }

    public XmlExporterConfiguration setRowElement(String rowElement) {
        this.rowElement = (rowElement != null && !rowElement.isBlank()) ? rowElement.trim() : "record";
        return this;
    }

    public XmlExporterConfiguration rowElement(String rowElement) {
        return setRowElement(rowElement);
    }

    public Charset getCharset() {
        return charset;
    }

    public XmlExporterConfiguration setCharset(Charset charset) {
        this.charset = charset != null ? charset : StandardCharsets.UTF_8;
        return this;
    }

    public XmlExporterConfiguration charset(Charset charset) {
        return setCharset(charset);
    }

    public boolean isFormatted() {
        return formatted;
    }

    public XmlExporterConfiguration setFormatted(boolean formatted) {
        this.formatted = formatted;
        return this;
    }

    public XmlExporterConfiguration formatted(boolean formatted) {
        return setFormatted(formatted);
    }

    public String getIndent() {
        return indent;
    }

    public XmlExporterConfiguration setIndent(String indent) {
        this.indent = indent != null ? indent : "  ";
        return this;
    }

    public XmlExporterConfiguration indent(String indent) {
        return setIndent(indent);
    }

    public String getNullValue() {
        return nullValue;
    }

    public XmlExporterConfiguration setNullValue(String nullValue) {
        this.nullValue = nullValue != null ? nullValue : "";
        return this;
    }

    public XmlExporterConfiguration nullValue(String nullValue) {
        return setNullValue(nullValue);
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public XmlExporterConfiguration setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize > 0 ? bufferSize : 64 * 1024;
        return this;
    }

    public XmlExporterConfiguration bufferSize(int bufferSize) {
        return setBufferSize(bufferSize);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XmlExporterConfiguration that = (XmlExporterConfiguration) o;
        return formatted == that.formatted &&
                bufferSize == that.bufferSize &&
                Objects.equals(rootElement, that.rootElement) &&
                Objects.equals(rowElement, that.rowElement) &&
                Objects.equals(charset, that.charset) &&
                Objects.equals(indent, that.indent) &&
                Objects.equals(nullValue, that.nullValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rootElement, rowElement, charset, formatted, indent, nullValue, bufferSize);
    }
}
