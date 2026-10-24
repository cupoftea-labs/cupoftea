package com.cupoftea.all;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.lang.reflect.Method;

import org.apache.hadoop.security.UserGroupInformation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.csv.CsvFormatReader;
import com.cupoftea.csv.CsvFormatWriter;
import com.cupoftea.excel.ExcelFormatReader;
import com.cupoftea.exceptions.ConversionException;
import com.cupoftea.exceptions.CsvParseException;
import com.cupoftea.exceptions.CupOfTeaException;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.exceptions.SchemaMismatchException;
import com.cupoftea.exporters.CSVExporter;
import com.cupoftea.exporters.JsonExporter;
import com.cupoftea.exporters.ParquetExporter;
import com.cupoftea.exporters.YamlExporter;
import com.cupoftea.exporters.interfaces.IJsonExporter;
import com.cupoftea.exporters.interfaces.IParquetExporter;
import com.cupoftea.exporters.interfaces.IYamlExporter;
import com.cupoftea.html.HtmlFormatReader;
import com.cupoftea.importers.CSVImporter;
import com.cupoftea.importers.ExcelImporter;
import com.cupoftea.importers.HTMLImporter;
import com.cupoftea.importers.JsonImporter;
import com.cupoftea.importers.ParquetImporter;
import com.cupoftea.importers.XmlImporter;
import com.cupoftea.importers.YamlImporter;
import com.cupoftea.importers.interfaces.ICSVImporter;
import com.cupoftea.importers.interfaces.IExcelImporter;
import com.cupoftea.importers.interfaces.IHTMLImporter;
import com.cupoftea.importers.interfaces.IJsonImporter;
import com.cupoftea.importers.interfaces.IParquetImporter;
import com.cupoftea.importers.interfaces.IXmlImporter;
import com.cupoftea.importers.interfaces.IYamlImporter;
import com.cupoftea.io.FormatRegistry;
import com.cupoftea.json.JsonFormatReader;
import com.cupoftea.json.JsonFormatWriter;
import com.cupoftea.parquet.ParquetFormatReader;
import com.cupoftea.parquet.ParquetFormatWriter;
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
import com.cupoftea.yaml.YamlFormatReader;
import com.cupoftea.yaml.YamlFormatWriter;
import com.cupoftea.xml.XmlFormatReader;

public class AllFormatsIntegrationTest {

    private File findFile(String module, String name) {
        File[] candidates = new File[]{
            new File("../" + module + "/src/test/resources/testFiles/" + name),
            new File(module + "/src/test/resources/testFiles/" + name),
            new File("src/test/resources/testFiles/" + name)
        };
        for (File candidate : candidates) {
            if (candidate.exists()) return candidate;
        }
        return candidates[0];
    }

    @Test
    @DisplayName("Should verify ServiceLoader discovers all registered format readers and writers")
    void testServiceLoaderDiscoversAllFormats() {
        FormatRegistry.reload();

        assertThat(FormatRegistry.getReader("csv")).isPresent();
        assertThat(FormatRegistry.getWriter("csv")).isPresent();

        assertThat(FormatRegistry.getReader("json")).isPresent();
        assertThat(FormatRegistry.getWriter("json")).isPresent();

        assertThat(FormatRegistry.getReader("excel")).isPresent();

        assertThat(FormatRegistry.getReader("parquet")).isPresent();
        assertThat(FormatRegistry.getWriter("parquet")).isPresent();

        assertThat(FormatRegistry.getReader("arrow")).isPresent();
        assertThat(FormatRegistry.getWriter("arrow")).isPresent();

        assertThat(FormatRegistry.getReader("feather")).isPresent();
        assertThat(FormatRegistry.getWriter("feather")).isPresent();

        assertThat(FormatRegistry.getReader("html")).isPresent();

        assertThat(FormatRegistry.getReader("yaml")).isPresent();
        assertThat(FormatRegistry.getWriter("yaml")).isPresent();

        assertThat(FormatRegistry.getReader("xml")).isPresent();
    }

    @Test
    @DisplayName("Should execute end-to-end multi-format pipeline: CSV -> JSON -> read back")
    void testEndToEndPipelineCsvToJson() throws Exception {
        File csvFile = findFile("cupoftea-csv", "csv_sample.csv");

        // 1. Read CSV via fluent API
        CupOfTea df = CupOfTea.read().csv(csvFile.getPath());
        assertThat(df.size()).isEqualTo(500);

        // 2. Filter rows
        CupOfTea filtered = df.filter(row -> "Santa".equals(row.get("cidade")));
        assertThat(filtered.size()).isGreaterThan(0);

        // 3. Write to JSON via fluent API
        File tempJson = File.createTempFile("pipeline_", ".json");
        tempJson.deleteOnExit();
        filtered.write().json(tempJson.getPath());
        assertThat(tempJson.length()).isGreaterThan(0);

        // 4. Read back JSON via Jackson-powered reader
        CupOfTea fromJson = CupOfTea.read().json(tempJson.getPath());
        assertThat(fromJson.size()).isEqualTo(filtered.size());
        assertThat(fromJson.getRow(0).get("cidade")).isEqualTo("Santa");

        // 5. Write to Arrow and Feather, read back
        File tempArrow = File.createTempFile("pipeline_", ".arrow");
        tempArrow.deleteOnExit();
        fromJson.write().arrow(tempArrow.getPath());
        assertThat(tempArrow.length()).isGreaterThan(0);

        CupOfTea fromArrow = CupOfTea.read().arrow(tempArrow.getPath());
        assertThat(fromArrow.size()).isEqualTo(filtered.size());
        assertThat(fromArrow.getRow(0).get("cidade")).isEqualTo("Santa");
    }

    @Test
    @DisplayName("Should verify public API methods in all modules do not declare throws Exception or Throwable")
    void testAllModulesPublicApiExceptions() {
        Class<?>[] allClasses = new Class<?>[]{
            CupOfTea.class,
            IDataFrame.class,
            DataFrame.class,
            Header.class,
            ICSVImporter.class,
            CSVImporter.class,
            CSVExporter.class,
            IJsonImporter.class,
            JsonImporter.class,
            IJsonExporter.class,
            JsonExporter.class,
            IExcelImporter.class,
            ExcelImporter.class,
            IParquetImporter.class,
            ParquetImporter.class,
            IParquetExporter.class,
            ParquetExporter.class,
            com.cupoftea.importers.interfaces.IArrowImporter.class,
            com.cupoftea.importers.ArrowImporter.class,
            com.cupoftea.exporters.interfaces.IArrowExporter.class,
            com.cupoftea.exporters.ArrowExporter.class,
            IHTMLImporter.class,
            HTMLImporter.class,
            IYamlImporter.class,
            YamlImporter.class,
            IYamlExporter.class,
            YamlExporter.class,
            IXmlImporter.class,
            XmlImporter.class,
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

        for (Class<?> clazz : allClasses) {
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
}
