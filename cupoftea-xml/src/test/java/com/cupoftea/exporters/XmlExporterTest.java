package com.cupoftea.exporters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exporters.configurations.XmlExporterConfiguration;

public class XmlExporterTest {

    @TempDir
    File tempDir;

    private CupOfTea createSampleDataFrame() {
        CupOfTea df = new CupOfTea();
        df.addColumn("id", Integer.class);
        df.addColumn("name", String.class);
        df.addColumn("score", Double.class);

        Map<String, Object> r1 = new HashMap<>();
        r1.put("id", 1);
        r1.put("name", "Alice");
        r1.put("score", 95.5);
        df.addRow(r1);

        Map<String, Object> r2 = new HashMap<>();
        r2.put("id", 2);
        r2.put("name", "Bob");
        r2.put("score", 82.0);
        df.addRow(r2);

        Map<String, Object> r3 = new HashMap<>();
        r3.put("id", 3);
        r3.put("name", null);
        r3.put("score", null);
        df.addRow(r3);

        return df;
    }

    @Test
    @DisplayName("Should export DataFrame to XML file and read back")
    void testExportToXmlFile() throws Exception {
        CupOfTea df = createSampleDataFrame();
        File xmlFile = new File(tempDir, "test_output.xml");

        XmlExporter exporter = new XmlExporter(df);
        exporter.exportToXml(xmlFile);

        assertThat(xmlFile).exists();
        String content = Files.readString(xmlFile.toPath());
        assertThat(content).contains("<records>");
        assertThat(content).contains("<record>");
        assertThat(content).contains("<id>1</id>");
        assertThat(content).contains("<name>Alice</name>");
        assertThat(content).contains("<score>95.5</score>");

        // Roundtrip check
        CupOfTea reloaded = CupOfTea.read().xml(xmlFile.getPath());
        assertThat(reloaded.size()).isEqualTo(3);
        assertThat(reloaded.getRow(0).get("id").toString()).isEqualTo("1");
        assertThat(reloaded.getRow(0).get("name")).isEqualTo("Alice");
    }

    @Test
    @DisplayName("Should export DataFrame to String and Writer")
    void testExportToStringAndWriter() {
        CupOfTea df = createSampleDataFrame();
        XmlExporter exporter = new XmlExporter(df);

        String xml = exporter.exportToXml();
        assertThat(xml).contains("<records>");
        assertThat(xml).contains("<name>Alice</name>");

        StringWriter sw = new StringWriter();
        exporter.exportToXml(sw);
        assertThat(sw.toString()).isEqualTo(xml);
    }

    @Test
    @DisplayName("Should export DataFrame to OutputStream")
    void testExportToOutputStream() {
        CupOfTea df = createSampleDataFrame();
        XmlExporter exporter = new XmlExporter(df);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exporter.exportToXml(baos);

        String xml = baos.toString(StandardCharsets.UTF_8);
        assertThat(xml).contains("<records>");
        assertThat(xml).contains("<name>Alice</name>");
    }

    @Test
    @DisplayName("Should respect XmlExporterConfiguration custom tags")
    void testCustomTagsConfiguration() {
        CupOfTea df = createSampleDataFrame();
        XmlExporter exporter = new XmlExporter(df);

        XmlExporterConfiguration config = XmlExporterConfiguration.builder()
                .rootElement("items")
                .rowElement("item")
                .formatted(false);

        String xml = exporter.exportToXml(config);
        assertThat(xml).contains("<items>");
        assertThat(xml).contains("<item>");
        assertThat(xml).contains("</items>");
    }

    @Test
    @DisplayName("Should throw ExportException on null targets")
    void testNullTargetsValidation() {
        CupOfTea df = createSampleDataFrame();
        XmlExporter exporter = new XmlExporter(df);

        assertThatThrownBy(() -> exporter.exportToXml((File) null))
                .isInstanceOf(ExportException.class);

        assertThatThrownBy(() -> exporter.exportToXml((java.io.OutputStream) null))
                .isInstanceOf(ExportException.class);

        assertThatThrownBy(() -> exporter.exportToXml((java.io.Writer) null))
                .isInstanceOf(ExportException.class);
    }

    @Test
    @DisplayName("Should export via CupOfTea fluent exportToXml and write().xml()")
    void testFluentExport() throws Exception {
        CupOfTea df = createSampleDataFrame();
        File xml1 = new File(tempDir, "fluent1.xml");
        File xml2 = new File(tempDir, "fluent2.xml");

        df.exportToXml(xml1);
        assertThat(xml1).exists();

        df.write().xml(xml2);
        assertThat(xml2).exists();
    }
}
