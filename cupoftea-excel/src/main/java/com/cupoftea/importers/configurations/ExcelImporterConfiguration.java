package com.cupoftea.importers.configurations;

import java.util.HashSet;
import java.util.Set;

import com.cupoftea.table.Schema;

public class ExcelImporterConfiguration {

    private boolean hasHeaders = true;
    private boolean inferTypes = true;
    private Schema schema = null;
    private int sheetIndex = 0;
    private String sheetName = null;
    private Set<String> naStrings = new HashSet<>(Set.of("NA", "N/A", "null", "NULL", "#N/A"));
    private boolean preserveEmptyStrings = true;

    public boolean isHasHeaders() {
        return this.hasHeaders;
    }

    public boolean isInferTypes() {
        return this.inferTypes;
    }

    public Schema getSchema() {
        return this.schema;
    }

    public int getSheetIndex() {
        return this.sheetIndex;
    }

    public String getSheetName() {
        return this.sheetName;
    }

    public Set<String> getNaStrings() {
        return this.naStrings;
    }

    public boolean isPreserveEmptyStrings() {
        return this.preserveEmptyStrings;
    }

    public ExcelImporterConfiguration setHasHeaders(boolean hasHeaders) {
        this.hasHeaders = hasHeaders;
        return this;
    }

    public ExcelImporterConfiguration setInferTypes(boolean inferTypes) {
        this.inferTypes = inferTypes;
        return this;
    }

    public ExcelImporterConfiguration setSchema(Schema schema) {
        this.schema = schema;
        return this;
    }

    public ExcelImporterConfiguration setSheetIndex(int sheetIndex) {
        this.sheetIndex = sheetIndex;
        this.sheetName = null;
        return this;
    }

    public ExcelImporterConfiguration setSheetName(String sheetName) {
        this.sheetName = sheetName;
        return this;
    }

    public ExcelImporterConfiguration setNaStrings(Set<String> naStrings) {
        this.naStrings = naStrings != null ? new HashSet<>(naStrings) : new HashSet<>();
        return this;
    }

    public ExcelImporterConfiguration setPreserveEmptyStrings(boolean preserveEmptyStrings) {
        this.preserveEmptyStrings = preserveEmptyStrings;
        return this;
    }
}
