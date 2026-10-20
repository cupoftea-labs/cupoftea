package com.CupOfTea.exceptions;

import static org.junit.Assert.assertThrows;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.CupOfTea.CupOfTea;
import com.CupOfTea.exporters.CSVExporter;
import com.CupOfTea.exporters.JsonExporter;
import com.CupOfTea.exporters.ParquetExporter;
import com.CupOfTea.exporters.YamlExporter;
import com.CupOfTea.exporters.interfaces.IJsonExporter;
import com.CupOfTea.exporters.interfaces.IParquetExporter;
import com.CupOfTea.exporters.interfaces.IYamlExporter;
import com.CupOfTea.importers.CSVImporter;
import com.CupOfTea.importers.ExcelImporter;
import com.CupOfTea.importers.HTMLImporter;
import com.CupOfTea.importers.JsonImporter;
import com.CupOfTea.importers.ParquetImporter;
import com.CupOfTea.importers.XmlImporter;
import com.CupOfTea.importers.YamlImporter;
import com.CupOfTea.importers.interfaces.ICSVImporter;
import com.CupOfTea.importers.interfaces.IExcelImporter;
import com.CupOfTea.importers.interfaces.IHTMLImporter;
import com.CupOfTea.importers.interfaces.IJsonImporter;
import com.CupOfTea.importers.interfaces.IParquetImporter;
import com.CupOfTea.importers.interfaces.IXmlImporter;
import com.CupOfTea.importers.interfaces.IYamlImporter;
import com.CupOfTea.table.DataFrame;
import com.CupOfTea.table.Header;
import com.CupOfTea.table.converters.BooleanConverter;
import com.CupOfTea.table.converters.DateConverter;
import com.CupOfTea.table.converters.DoubleConverter;
import com.CupOfTea.table.converters.FloatConverter;
import com.CupOfTea.table.converters.IntegerConverter;
import com.CupOfTea.table.interfaces.IDataFrame;
import com.CupOfTea.table.interfaces.IDataTypeConverter;

import junit.framework.TestCase;

public class ExceptionHierarchyTest extends TestCase {

    public void testExceptionHierarchyInheritance() {
        assertTrue(RuntimeException.class.isAssignableFrom(CupOfTeaException.class));
        assertTrue(CupOfTeaException.class.isAssignableFrom(SchemaMismatchException.class));
        assertTrue(CupOfTeaException.class.isAssignableFrom(ConversionException.class));
        assertTrue(CupOfTeaException.class.isAssignableFrom(ImportException.class));
        assertTrue(ImportException.class.isAssignableFrom(CsvParseException.class));
        assertTrue(CupOfTeaException.class.isAssignableFrom(ExportException.class));

        // Test constructors
        CupOfTeaException base = new CupOfTeaException("base error");
        assertEquals("base error", base.getMessage());

        Throwable cause = new IllegalArgumentException("cause");
        CupOfTeaException baseWithCause = new CupOfTeaException("base error", cause);
        assertEquals(cause, baseWithCause.getCause());

        CupOfTeaException baseCauseOnly = new CupOfTeaException(cause);
        assertEquals(cause, baseCauseOnly.getCause());

        SchemaMismatchException sme = new SchemaMismatchException("schema error", cause);
        assertEquals("schema error", sme.getMessage());
        assertEquals(cause, sme.getCause());

        ConversionException ce = new ConversionException("conv error", cause);
        assertEquals("conv error", ce.getMessage());
        assertEquals(cause, ce.getCause());

        ImportException ie = new ImportException("import error", cause);
        assertEquals("import error", ie.getMessage());
        assertEquals(cause, ie.getCause());

        CsvParseException cpe = new CsvParseException("csv error", cause);
        assertEquals("csv error", cpe.getMessage());
        assertEquals(cause, cpe.getCause());

        ExportException ee = new ExportException("export error", cause);
        assertEquals("export error", ee.getMessage());
        assertEquals(cause, ee.getCause());
    }

    public void testPublicApiDoesNotDeclareThrowsException() {
        Class<?>[] publicApiClasses = new Class<?>[]{
            CupOfTea.class,
            IDataFrame.class,
            DataFrame.class,
            Header.class,
            ICSVImporter.class,
            CSVImporter.class,
            IExcelImporter.class,
            ExcelImporter.class,
            IHTMLImporter.class,
            HTMLImporter.class,
            IJsonImporter.class,
            JsonImporter.class,
            IParquetImporter.class,
            ParquetImporter.class,
            IXmlImporter.class,
            XmlImporter.class,
            IYamlImporter.class,
            YamlImporter.class,
            CSVExporter.class,
            IJsonExporter.class,
            JsonExporter.class,
            IParquetExporter.class,
            ParquetExporter.class,
            IYamlExporter.class,
            YamlExporter.class,
            IDataTypeConverter.class,
            BooleanConverter.class,
            IntegerConverter.class,
            DoubleConverter.class,
            FloatConverter.class,
            DateConverter.class
        };

        for (Class<?> clazz : publicApiClasses) {
            for (Method method : clazz.getDeclaredMethods()) {
                for (Class<?> exceptionType : method.getExceptionTypes()) {
                    assertFalse(
                        "Method " + clazz.getSimpleName() + "." + method.getName()
                                + " should not declare throws java.lang.Exception",
                        exceptionType.equals(Exception.class)
                    );
                    assertFalse(
                        "Method " + clazz.getSimpleName() + "." + method.getName()
                                + " should not declare throws java.lang.Throwable",
                        exceptionType.equals(Throwable.class)
                    );
                }
            }
        }
    }

    public void testSchemaMismatchOnAddColumnSizeMismatch() {
        DataFrame df = new DataFrame();
        df.addColumn("col1");
        HashMap<String, Object> row = new HashMap<>();
        row.put("col1", "val1");
        df.addRow(row);

        ArrayList<Object> invalidData = new ArrayList<>();
        invalidData.add("item1");
        invalidData.add("item2"); // Size is 2, but DataFrame has 1 row

        SchemaMismatchException ex = assertThrows(
            SchemaMismatchException.class,
            () -> df.addColumn(1, "col2", invalidData)
        );
        assertTrue(ex.getMessage().contains("does not match DataFrame size"));
    }

    public void testSchemaMismatchOnInvalidHeaderTypes() {
        CupOfTea df = new CupOfTea();
        ArrayList<Object> invalidHeaders = new ArrayList<>();
        invalidHeaders.add(12345); // Not String or Column

        SchemaMismatchException ex = assertThrows(
            SchemaMismatchException.class,
            () -> df.setHeaders(invalidHeaders)
        );
        assertTrue(ex.getMessage().contains("Unsupported header array type"));
    }

    public void testSchemaMismatchOnAggregationWithoutGrouping() {
        CupOfTea df = new CupOfTea();
        df.addColumn("price");
        HashMap<String, Object> row = new HashMap<>();
        row.put("price", 100);
        df.addRow(row);

        SchemaMismatchException sumEx = assertThrows(
            SchemaMismatchException.class,
            () -> df.sum("price")
        );
        assertTrue(sumEx.getMessage().contains("is not a grouped column"));

        SchemaMismatchException avgEx = assertThrows(
            SchemaMismatchException.class,
            () -> df.average("price")
        );
        assertTrue(avgEx.getMessage().contains("is not a grouped column"));

        SchemaMismatchException maxEx = assertThrows(
            SchemaMismatchException.class,
            () -> df.maximum("price")
        );
        assertTrue(maxEx.getMessage().contains("is not a grouped column"));

        SchemaMismatchException minEx = assertThrows(
            SchemaMismatchException.class,
            () -> df.minimum("price")
        );
        assertTrue(minEx.getMessage().contains("is not a grouped column"));

        SchemaMismatchException countEx = assertThrows(
            SchemaMismatchException.class,
            () -> df.count("price")
        );
        assertTrue(countEx.getMessage().contains("is not a grouped column"));

        SchemaMismatchException aggEx = assertThrows(
            SchemaMismatchException.class,
            () -> df.aggregate("price", ",")
        );
        assertTrue(aggEx.getMessage().contains("is not a grouped column"));
    }

    public void testSchemaMismatchOnNonExistentColumnAggregation() {
        CupOfTea df = new CupOfTea();
        assertThrows(SchemaMismatchException.class, () -> df.sum("non_existent"));
        assertThrows(SchemaMismatchException.class, () -> df.average("non_existent"));
        assertThrows(SchemaMismatchException.class, () -> df.maximum("non_existent"));
        assertThrows(SchemaMismatchException.class, () -> df.minimum("non_existent"));
        assertThrows(SchemaMismatchException.class, () -> df.count("non_existent"));
        assertThrows(SchemaMismatchException.class, () -> df.aggregate("non_existent", ","));
    }

    public void testSchemaMismatchOnGroupByMissingColumn() {
        CupOfTea df = new CupOfTea();
        df.addColumn("a");
        assertThrows(SchemaMismatchException.class, () -> df.groupBy("non_existent"));
    }

    public void testConversionExceptionsOnConverters() {
        // BooleanConverter
        BooleanConverter bc = new BooleanConverter();
        ConversionException bEx = assertThrows(
            ConversionException.class,
            () -> bc.convert("not_a_boolean", String.class)
        );
        assertTrue(bEx.getMessage().contains("Value cannot be converted to Boolean"));

        ConversionException bIntEx = assertThrows(
            ConversionException.class,
            () -> bc.convert(99, Integer.class)
        );
        assertTrue(bIntEx.getMessage().contains("Value cannot be converted to Boolean"));

        ConversionException bTypeEx = assertThrows(
            ConversionException.class,
            () -> bc.convert(12.34, Double.class)
        );
        assertTrue(bTypeEx.getMessage().contains("Column type not supported for Boolean conversion"));

        // IntegerConverter
        IntegerConverter ic = new IntegerConverter();
        ConversionException iEx = assertThrows(
            ConversionException.class,
            () -> ic.convert("abc", String.class)
        );
        assertTrue(iEx.getMessage().contains("Cannot convert string 'abc' to Integer"));

        ConversionException iTypeEx = assertThrows(
            ConversionException.class,
            () -> ic.convert(true, Boolean.class)
        );
        assertTrue(iTypeEx.getMessage().contains("Column type not supported for Integer conversion"));

        // DoubleConverter
        DoubleConverter dc = new DoubleConverter();
        ConversionException dEx = assertThrows(
            ConversionException.class,
            () -> dc.convert("xyz", String.class)
        );
        assertTrue(dEx.getMessage().contains("Cannot convert string 'xyz' to Double"));

        ConversionException dTypeEx = assertThrows(
            ConversionException.class,
            () -> dc.convert(true, Boolean.class)
        );
        assertTrue(dTypeEx.getMessage().contains("Column type not supported for Double conversion"));

        // FloatConverter
        FloatConverter fc = new FloatConverter();
        ConversionException fEx = assertThrows(
            ConversionException.class,
            () -> fc.convert("invalid_float", String.class)
        );
        assertTrue(fEx.getMessage().contains("Cannot convert string 'invalid_float' to Float"));

        ConversionException fTypeEx = assertThrows(
            ConversionException.class,
            () -> fc.convert(true, Boolean.class)
        );
        assertTrue(fTypeEx.getMessage().contains("Column type not supported for Float conversion"));

        // DateConverter
        DateConverter dateC = new DateConverter();
        ConversionException dateEx = assertThrows(
            ConversionException.class,
            () -> dateC.convert("not-a-date", String.class)
        );
        assertTrue(dateEx.getMessage().contains("Cannot convert date string"));

        ConversionException dateTypeEx = assertThrows(
            ConversionException.class,
            () -> dateC.convert(1234, Integer.class)
        );
        assertTrue(dateTypeEx.getMessage().contains("Column type not supported for Date conversion"));
    }

    public void testAlterColumnTypeExceptions() {
        CupOfTea df = new CupOfTea();
        df.addColumn("col1");

        // Non-existent column
        assertThrows(
            SchemaMismatchException.class,
            () -> df.alterColumnType("non_existent", Integer.class)
        );

        // Missing converter for type (e.g. List.class)
        assertThrows(
            ConversionException.class,
            () -> df.alterColumnType("col1", List.class)
        );
    }

    public void testImportExceptions() {
        CupOfTea df = new CupOfTea();

        // Null checks
        assertThrows(ImportException.class, () -> df.importFromCsv(null, true));
        assertThrows(ImportException.class, () -> df.importFromExcel(null, true));
        assertThrows(ImportException.class, () -> df.importFromJson((String) null));
        assertThrows(ImportException.class, () -> df.importFromJson((File) null));
        assertThrows(ImportException.class, () -> df.importFromXml((File) null));
        assertThrows(ImportException.class, () -> df.importFromYaml((File) null));

        // Non-existent CSV file throws CsvParseException
        File missingFile = new File("non_existent_file.csv");
        assertThrows(CsvParseException.class, () -> df.importFromCsv(missingFile, true));

        // Invalid JSON
        assertThrows(ImportException.class, () -> df.importFromJson("{ invalid json }"));

        // Invalid XML
        assertThrows(ImportException.class, () -> df.importFromXml("<unclosed>"));

        // Non-excel file extension
        assertThrows(ImportException.class, () -> df.importFromExcel(new File("test.txt"), false));
    }

    public void testExportExceptions() {
        CupOfTea df = new CupOfTea();

        // Null file checks
        assertThrows(ExportException.class, () -> df.exportToCsv(null));
        assertThrows(ExportException.class, () -> df.exportToJson((File) null));
        assertThrows(ExportException.class, () -> df.exportToParquet(null));

        // Yaml export not supported
        ExportException yamlEx = assertThrows(
            ExportException.class,
            () -> new YamlExporter().exportToYaml(new File("out.yaml"))
        );
        assertTrue(yamlEx.getMessage().contains("YAML export is not supported yet"));
    }
}
