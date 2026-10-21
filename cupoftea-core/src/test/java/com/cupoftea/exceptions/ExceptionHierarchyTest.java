package com.cupoftea.exceptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.io.DataFrameReader;
import com.cupoftea.io.DataFrameWriter;
import com.cupoftea.table.Column;
import com.cupoftea.table.DataFrame;
import com.cupoftea.table.Header;
import com.cupoftea.table.converters.BooleanConverter;
import com.cupoftea.table.converters.DateConverter;
import com.cupoftea.table.converters.DoubleConverter;
import com.cupoftea.table.converters.FloatConverter;
import com.cupoftea.table.converters.IntegerConverter;
import com.cupoftea.table.converters.LocalDateConverter;
import com.cupoftea.table.converters.LocalDateTimeConverter;
import com.cupoftea.table.converters.LongConverter;
import com.cupoftea.table.interfaces.IDataFrame;
import com.cupoftea.table.interfaces.IDataTypeConverter;

public class ExceptionHierarchyTest {

    @Test
    @DisplayName("Should verify exception inheritance and constructor messages")
    void testExceptionHierarchyInheritance() {
        assertThat(RuntimeException.class).isAssignableFrom(CupOfTeaException.class);
        assertThat(CupOfTeaException.class).isAssignableFrom(SchemaMismatchException.class);
        assertThat(CupOfTeaException.class).isAssignableFrom(ConversionException.class);
        assertThat(CupOfTeaException.class).isAssignableFrom(ImportException.class);
        assertThat(ImportException.class).isAssignableFrom(CsvParseException.class);
        assertThat(CupOfTeaException.class).isAssignableFrom(ExportException.class);

        // Test constructors
        CupOfTeaException base = new CupOfTeaException("base error");
        assertThat(base.getMessage()).isEqualTo("base error");

        Throwable cause = new IllegalArgumentException("cause");
        CupOfTeaException baseWithCause = new CupOfTeaException("base error", cause);
        assertThat(baseWithCause.getMessage()).isEqualTo("base error");
        assertThat(baseWithCause.getCause()).isSameAs(cause);

        CupOfTeaException baseCauseOnly = new CupOfTeaException(cause);
        assertThat(baseCauseOnly.getCause()).isSameAs(cause);

        SchemaMismatchException sme = new SchemaMismatchException("schema error", cause);
        assertThat(sme.getMessage()).isEqualTo("schema error");
        assertThat(sme.getCause()).isSameAs(cause);

        ConversionException ce = new ConversionException("conv error", cause);
        assertThat(ce.getMessage()).isEqualTo("conv error");
        assertThat(ce.getCause()).isSameAs(cause);

        ImportException ie = new ImportException("import error", cause);
        assertThat(ie.getMessage()).isEqualTo("import error");
        assertThat(ie.getCause()).isSameAs(cause);

        CsvParseException cpe = new CsvParseException("csv error", cause);
        assertThat(cpe.getMessage()).isEqualTo("csv error");
        assertThat(cpe.getCause()).isSameAs(cause);

        ExportException ee = new ExportException("export error", cause);
        assertThat(ee.getMessage()).isEqualTo("export error");
        assertThat(ee.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("Should ensure public API methods do not declare throws Exception or Throwable")
    void testPublicApiDoesNotDeclareThrowsException() {
        Class<?>[] publicApiClasses = new Class<?>[]{
            CupOfTea.class,
            IDataFrame.class,
            DataFrame.class,
            Header.class,
            DataFrameReader.class,
            DataFrameWriter.class,
            IDataTypeConverter.class,
            BooleanConverter.class,
            IntegerConverter.class,
            DoubleConverter.class,
            FloatConverter.class,
            DateConverter.class,
            LongConverter.class,
            LocalDateConverter.class,
            LocalDateTimeConverter.class
        };

        for (Class<?> clazz : publicApiClasses) {
            for (Method method : clazz.getDeclaredMethods()) {
                for (Class<?> exceptionType : method.getExceptionTypes()) {
                    assertThat(exceptionType)
                        .withFailMessage("Method %s.%s should not declare throws java.lang.Exception",
                            clazz.getSimpleName(), method.getName())
                        .isNotEqualTo(Exception.class);
                    assertThat(exceptionType)
                        .withFailMessage("Method %s.%s should not declare throws java.lang.Throwable",
                            clazz.getSimpleName(), method.getName())
                        .isNotEqualTo(Throwable.class);
                }
            }
        }
    }

    @Test
    @DisplayName("Should throw SchemaMismatchException on addColumn size mismatch")
    void testSchemaMismatchOnAddColumnSizeMismatch() {
        DataFrame df = new DataFrame();
        df.addColumn("col1");
        HashMap<String, Object> row = new HashMap<>();
        row.put("col1", "val1");
        df.addRow(row);

        ArrayList<Object> invalidData = new ArrayList<>();
        invalidData.add("item1");
        invalidData.add("item2"); // Size is 2, but DataFrame has 1 row

        assertThatThrownBy(() -> df.addColumn(1, "col2", invalidData))
            .isInstanceOf(SchemaMismatchException.class)
            .hasMessageContaining("does not match DataFrame size");
    }

    @Test
    @DisplayName("Should throw SchemaMismatchException on invalid header types")
    void testSchemaMismatchOnInvalidHeaderTypes() {
        CupOfTea df = new CupOfTea();
        ArrayList<Object> invalidHeaders = new ArrayList<>();
        invalidHeaders.add(12345); // Not String or Column

        assertThatThrownBy(() -> df.setHeaders(invalidHeaders))
            .isInstanceOf(SchemaMismatchException.class)
            .hasMessageContaining("Unsupported header array type");
    }

    @Test
    @DisplayName("Should throw SchemaMismatchException on aggregations without grouping")
    void testSchemaMismatchOnAggregationWithoutGrouping() {
        CupOfTea df = new CupOfTea();
        df.addColumn("price");
        HashMap<String, Object> row = new HashMap<>();
        row.put("price", 100);
        df.addRow(row);

        assertThatThrownBy(() -> df.sum("price"))
            .isInstanceOf(SchemaMismatchException.class)
            .hasMessageContaining("is not a grouped column");

        assertThatThrownBy(() -> df.average("price"))
            .isInstanceOf(SchemaMismatchException.class)
            .hasMessageContaining("is not a grouped column");

        assertThatThrownBy(() -> df.maximum("price"))
            .isInstanceOf(SchemaMismatchException.class)
            .hasMessageContaining("is not a grouped column");

        assertThatThrownBy(() -> df.minimum("price"))
            .isInstanceOf(SchemaMismatchException.class)
            .hasMessageContaining("is not a grouped column");

        assertThatThrownBy(() -> df.count("price"))
            .isInstanceOf(SchemaMismatchException.class)
            .hasMessageContaining("is not a grouped column");

        assertThatThrownBy(() -> df.aggregate("price", ","))
            .isInstanceOf(SchemaMismatchException.class)
            .hasMessageContaining("is not a grouped column");
    }

    @Test
    @DisplayName("Should throw SchemaMismatchException on non-existent column aggregations")
    void testSchemaMismatchOnNonExistentColumnAggregation() {
        CupOfTea df = new CupOfTea();
        assertThatThrownBy(() -> df.sum("non_existent")).isInstanceOf(SchemaMismatchException.class);
        assertThatThrownBy(() -> df.average("non_existent")).isInstanceOf(SchemaMismatchException.class);
        assertThatThrownBy(() -> df.maximum("non_existent")).isInstanceOf(SchemaMismatchException.class);
        assertThatThrownBy(() -> df.minimum("non_existent")).isInstanceOf(SchemaMismatchException.class);
        assertThatThrownBy(() -> df.count("non_existent")).isInstanceOf(SchemaMismatchException.class);
        assertThatThrownBy(() -> df.aggregate("non_existent", ",")).isInstanceOf(SchemaMismatchException.class);
    }

    @Test
    @DisplayName("Should throw SchemaMismatchException on groupBy with missing column")
    void testSchemaMismatchOnGroupByMissingColumn() {
        CupOfTea df = new CupOfTea();
        df.addColumn("a");
        assertThatThrownBy(() -> df.groupBy("non_existent")).isInstanceOf(SchemaMismatchException.class);
    }

    @Test
    @DisplayName("Should throw ConversionException on converter parse failures and unsupported types")
    void testConversionExceptionsOnConverters() {
        // BooleanConverter
        BooleanConverter bc = new BooleanConverter();
        assertThatThrownBy(() -> bc.convert("not_a_boolean", String.class))
            .isInstanceOf(ConversionException.class)
            .hasMessageContaining("Value cannot be converted to Boolean");

        assertThatThrownBy(() -> bc.convert(99, Integer.class))
            .isInstanceOf(ConversionException.class)
            .hasMessageContaining("Value cannot be converted to Boolean");

        assertThatThrownBy(() -> bc.convert(12.34, Double.class))
            .isInstanceOf(ConversionException.class)
            .hasMessageContaining("Column type not supported for Boolean conversion");

        // IntegerConverter
        IntegerConverter ic = new IntegerConverter();
        assertThatThrownBy(() -> ic.convert("abc", String.class))
            .isInstanceOf(ConversionException.class)
            .hasMessageContaining("Cannot convert string 'abc' to Integer");

        assertThatThrownBy(() -> ic.convert(true, Boolean.class))
            .isInstanceOf(ConversionException.class)
            .hasMessageContaining("Column type not supported for Integer conversion");

        // DoubleConverter
        DoubleConverter dc = new DoubleConverter();
        assertThatThrownBy(() -> dc.convert("xyz", String.class))
            .isInstanceOf(ConversionException.class)
            .hasMessageContaining("Cannot convert string 'xyz' to Double");

        assertThatThrownBy(() -> dc.convert(true, Boolean.class))
            .isInstanceOf(ConversionException.class)
            .hasMessageContaining("Column type not supported for Double conversion");

        // FloatConverter
        FloatConverter fc = new FloatConverter();
        assertThatThrownBy(() -> fc.convert("invalid_float", String.class))
            .isInstanceOf(ConversionException.class)
            .hasMessageContaining("Cannot convert string 'invalid_float' to Float");

        assertThatThrownBy(() -> fc.convert(true, Boolean.class))
            .isInstanceOf(ConversionException.class)
            .hasMessageContaining("Column type not supported for Float conversion");

        // DateConverter
        DateConverter dateC = new DateConverter();
        assertThatThrownBy(() -> dateC.convert("not-a-date", String.class))
            .isInstanceOf(ConversionException.class)
            .hasMessageContaining("Cannot convert date string");

        assertThatThrownBy(() -> dateC.convert(1234, Integer.class))
            .isInstanceOf(ConversionException.class)
            .hasMessageContaining("Column type not supported for Date conversion");
    }

    @Test
    @DisplayName("Should throw appropriate exceptions on alterColumnType")
    void testAlterColumnTypeExceptions() {
        CupOfTea df = new CupOfTea().addColumn("col1");

        // Non-existent column
        assertThatThrownBy(() -> df.alterColumnType("non_existent", Integer.class))
            .isInstanceOf(SchemaMismatchException.class);

        // Missing converter for type (e.g. List.class)
        assertThatThrownBy(() -> df.alterColumnType("col1", List.class))
            .isInstanceOf(ConversionException.class);
    }

    @Test
    @DisplayName("Should throw ImportException on null import inputs")
    void testImportExceptions() {
        CupOfTea df = new CupOfTea();

        // Null checks
        assertThatThrownBy(() -> df.importFromCsv(null, true)).isInstanceOf(ImportException.class);
        assertThatThrownBy(() -> df.importFromExcel(null, true)).isInstanceOf(ImportException.class);
        assertThatThrownBy(() -> df.importFromJson((String) null)).isInstanceOf(ImportException.class);
        assertThatThrownBy(() -> df.importFromJson((File) null)).isInstanceOf(ImportException.class);
        assertThatThrownBy(() -> df.importFromXml((File) null)).isInstanceOf(ImportException.class);
        assertThatThrownBy(() -> df.importFromYaml((File) null)).isInstanceOf(ImportException.class);
        assertThatThrownBy(() -> df.importFromHtml((File) null)).isInstanceOf(ImportException.class);
        assertThatThrownBy(() -> df.importFromParquet(null)).isInstanceOf(ImportException.class);
    }

    @Test
    @DisplayName("Should throw ExportException on null export inputs")
    void testExportExceptions() {
        CupOfTea df = new CupOfTea();

        // Null file checks
        assertThatThrownBy(() -> df.exportToCsv(null)).isInstanceOf(ExportException.class);
        assertThatThrownBy(() -> df.exportToJson((File) null)).isInstanceOf(ExportException.class);
        assertThatThrownBy(() -> df.exportToParquet(null)).isInstanceOf(ExportException.class);
        assertThatThrownBy(() -> df.exportToYaml(null)).isInstanceOf(ExportException.class);
    }
}
