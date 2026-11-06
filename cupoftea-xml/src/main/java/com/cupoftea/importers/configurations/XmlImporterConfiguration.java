package com.cupoftea.importers.configurations;

import java.util.Objects;

import com.cupoftea.table.Schema;

/**
 * Configuration options for XML import and lazy scanning.
 */
public class XmlImporterConfiguration {

    private Schema schema = null;
    private String rowElement = null; // null means auto-detect from first repeating child
    private int bufferSize = 64 * 1024;
    private int batchSize = 65_536;

    public XmlImporterConfiguration() {
    }

    public static XmlImporterConfiguration builder() {
        return new XmlImporterConfiguration();
    }

    public Schema getSchema() {
        return schema;
    }

    public XmlImporterConfiguration setSchema(Schema schema) {
        this.schema = schema;
        return this;
    }

    public XmlImporterConfiguration schema(Schema schema) {
        return setSchema(schema);
    }

    public String getRowElement() {
        return rowElement;
    }

    public XmlImporterConfiguration setRowElement(String rowElement) {
        this.rowElement = (rowElement != null && !rowElement.isBlank()) ? rowElement.trim() : null;
        return this;
    }

    public XmlImporterConfiguration rowElement(String rowElement) {
        return setRowElement(rowElement);
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public XmlImporterConfiguration setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize > 0 ? bufferSize : 64 * 1024;
        return this;
    }

    public XmlImporterConfiguration bufferSize(int bufferSize) {
        return setBufferSize(bufferSize);
    }

    public int getBatchSize() {
        return batchSize;
    }

    public XmlImporterConfiguration setBatchSize(int batchSize) {
        this.batchSize = batchSize > 0 ? batchSize : 65_536;
        return this;
    }

    public XmlImporterConfiguration batchSize(int batchSize) {
        return setBatchSize(batchSize);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XmlImporterConfiguration that = (XmlImporterConfiguration) o;
        return bufferSize == that.bufferSize &&
                batchSize == that.batchSize &&
                Objects.equals(schema, that.schema) &&
                Objects.equals(rowElement, that.rowElement);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schema, rowElement, bufferSize, batchSize);
    }
}
