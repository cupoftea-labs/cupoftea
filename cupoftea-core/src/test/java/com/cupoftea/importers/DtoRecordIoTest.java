package com.cupoftea.importers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.MutationMode;
import com.cupoftea.exporters.DtoExporter;
import com.cupoftea.exporters.RecordExporter;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.table.Row;

public class DtoRecordIoTest {

    // Record sample
    public record PersonRecord(String name, int age, double salary) {}

    // Record with nullable / wrapper fields
    public record ProductRecord(long id, String sku, Double price, Boolean inStock) {}

    // POJO / JavaBean sample
    public static class EmployeeDto {
        private String name;
        private int age;
        private double salary;

        public EmployeeDto() {}

        public EmployeeDto(String name, int age, double salary) {
            this.name = name;
            this.age = age;
            this.salary = salary;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public double getSalary() {
            return salary;
        }

        public void setSalary(double salary) {
            this.salary = salary;
        }
    }

    @Test
    @DisplayName("Should import and export Java Records using CupOfTea instance methods (#81, #78)")
    void testRecordImportAndExport() {
        List<PersonRecord> initial = List.of(
                new PersonRecord("Alice", 30, 75000.5),
                new PersonRecord("Bob", 25, 52000.0)
        );

        CupOfTea df = new CupOfTea();
        df.importFromRecords(initial);

        assertThat(df.getDataFrame().size()).isEqualTo(2);
        assertThat(df.getDataFrame().getHeaders()).extracting("columnName")
                .containsExactly("name", "age", "salary");

        Row row0 = df.getDataFrame().getRow(0);
        assertThat(row0.getString("name")).isEqualTo("Alice");
        assertThat(row0.getInt("age")).isEqualTo(30);
        assertThat(row0.getDouble("salary")).isEqualTo(75000.5);

        List<PersonRecord> exported = df.exportToRecords(PersonRecord.class);
        assertThat(exported).containsExactlyElementsOf(initial);
    }

    @Test
    @DisplayName("Should import and export POJO DTOs using CupOfTea instance methods (#42, #43)")
    void testPojoDtoImportAndExport() {
        List<EmployeeDto> initial = List.of(
                new EmployeeDto("Charlie", 40, 95000.0),
                new EmployeeDto("Diana", 35, 88000.0)
        );

        CupOfTea df = new CupOfTea();
        df.importFromDtos(initial);

        assertThat(df.getDataFrame().size()).isEqualTo(2);
        assertThat(df.getDataFrame().getHeaders()).extracting("columnName")
                .contains("name", "age", "salary");

        List<EmployeeDto> exported = df.exportToDtos(EmployeeDto.class);
        assertThat(exported).hasSize(2);
        assertThat(exported.get(0).getName()).isEqualTo("Charlie");
        assertThat(exported.get(0).getAge()).isEqualTo(40);
        assertThat(exported.get(0).getSalary()).isEqualTo(95000.0);
        assertThat(exported.get(1).getName()).isEqualTo("Diana");
        assertThat(exported.get(1).getAge()).isEqualTo(35);
        assertThat(exported.get(1).getSalary()).isEqualTo(88000.0);
    }

    @Test
    @DisplayName("Should support fluent read and write API for records and DTOs")
    void testFluentReadWriteApi() {
        List<ProductRecord> products = List.of(
                new ProductRecord(1L, "PROD-A", 19.99, true),
                new ProductRecord(2L, "PROD-B", null, false)
        );

        // Fluent read
        CupOfTea df = CupOfTea.read().records(products);
        assertThat(df.getDataFrame().size()).isEqualTo(2);

        // Fluent write
        List<ProductRecord> outRecords = df.write().records(ProductRecord.class);
        assertThat(outRecords).containsExactlyElementsOf(products);

        // Fluent read & write via dtos
        CupOfTea dfDtos = CupOfTea.read().dtos(products);
        List<ProductRecord> outDtos = dfDtos.write().dtos(ProductRecord.class);
        assertThat(outDtos).containsExactlyElementsOf(products);
    }

    @Test
    @DisplayName("Should respect MutationMode COPY_ON_WRITE when importing DTOs/records")
    void testMutationModeSupport() {
        List<PersonRecord> initial = List.of(new PersonRecord("Alice", 30, 75000.0));
        CupOfTea original = new CupOfTea().asCopyOnWrite();

        CupOfTea mutated = original.importFromRecords(initial);
        assertThat(original.getDataFrame().size()).isEqualTo(0);
        assertThat(mutated.getDataFrame().size()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle null and empty collections gracefully")
    void testNullAndEmptyCollections() {
        CupOfTea df = new CupOfTea();

        assertThatThrownBy(() -> df.importFromDtos(null))
                .isInstanceOf(ImportException.class);

        assertThatThrownBy(() -> df.importFromRecords(null))
                .isInstanceOf(ImportException.class);

        assertThatThrownBy(() -> df.exportToDtos(null))
                .isInstanceOf(ExportException.class);

        assertThatThrownBy(() -> df.exportToRecords(null))
                .isInstanceOf(ExportException.class);

        // Empty list should result in empty DataFrame without error
        CupOfTea emptyDf = new CupOfTea();
        emptyDf.importFromDtos(Collections.emptyList());
        assertThat(emptyDf.getDataFrame().size()).isEqualTo(0);

        List<PersonRecord> emptyExport = emptyDf.exportToRecords(PersonRecord.class);
        assertThat(emptyExport).isEmpty();
    }

    @Test
    @DisplayName("Should throw ExportException when exportToRecords is called with a non-record class")
    void testExportToRecordsWithNonRecordThrows() {
        CupOfTea df = new CupOfTea();
        assertThatThrownBy(() -> df.exportToRecords((Class) EmployeeDto.class))
                .isInstanceOf(ExportException.class)
                .hasMessageContaining("not a Java Record");
    }

    @Test
    @DisplayName("Should convert types flexibly during DTO export")
    void testTypeConversionDuringExport() {
        CupOfTea df = new CupOfTea();
        df.addColumn("name", String.class)
          .addColumn("age", String.class)  // stored as String
          .addColumn("salary", Double.class);

        df.addRow(java.util.Map.of("name", "Eve", "age", "28", "salary", 64000.0));

        List<PersonRecord> exported = df.exportToRecords(PersonRecord.class);
        assertThat(exported).hasSize(1);
        assertThat(exported.get(0).age()).isEqualTo(28);
    }

    @Test
    @DisplayName("Should test dedicated RecordImporter and RecordExporter classes directly")
    void testDirectImporterExporterClasses() {
        CupOfTea df = new CupOfTea();
        RecordImporter importer = new RecordImporter(df);
        List<PersonRecord> list = List.of(new PersonRecord("Frank", 45, 82000.0));
        importer.importFromRecords(list);

        RecordExporter exporter = new RecordExporter(df);
        List<PersonRecord> out = exporter.exportToRecords(PersonRecord.class);
        assertThat(out).containsExactlyElementsOf(list);
    }
}
