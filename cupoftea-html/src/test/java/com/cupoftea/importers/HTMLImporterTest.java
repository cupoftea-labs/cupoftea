package com.cupoftea.importers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ImportException;
import com.sun.net.httpserver.HttpServer;

public class HTMLImporterTest {

    private static final String SAMPLE_TABLE_HTML =
            "<html><body><table>" +
            "<tr><th>Name</th><th>Role</th></tr>" +
            "<tr><td>Alice</td><td>Engineer</td></tr>" +
            "<tr><td>Bob</td><td>Designer</td></tr>" +
            "</table></body></html>";

    private HttpServer tryCreateServer() {
        try {
            return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (Throwable t) {
            Assumptions.assumeTrue(false, "Loopback HTTP server could not be created in this environment: " + t.getMessage());
            return null;
        }
    }

    
    private File getSampleHtml() {
        File[] candidates = new File[]{
            new File("src/test/resources/testFiles/html_sample.html"),
            new File("cupoftea-html/src/test/resources/testFiles/html_sample.html"),
            new File("src/test/java/com/cupoftea/testFiles/html_sample.html"),
            new File("src/test/java/com/CupOfTea/testFiles/html_sample.html")
        };
        for (File candidate : candidates) {
            if (candidate.exists()) return candidate;
        }
        return candidates[0];
    }

    @Test
    @DisplayName("Should import DataFrame from HTML file")
    void test_GivenAHTMLFile_WhenImporting_ShouldReturnACupOfTeaDataFrame() {
        int expectedColumnsNumber = 11;
        int expectedRowsNumber = 19;

        File testFile = getSampleHtml();
        CupOfTea cupOfTea = new CupOfTea();

        cupOfTea.importFromHtml(testFile);

        assertThat(cupOfTea.getHeaders()).hasSize(expectedColumnsNumber);
        assertThat(cupOfTea.getRows()).hasSize(expectedRowsNumber);
    }

    @Test
    @DisplayName("Should import DataFrame from HTML string")
    void test_GivenAHTMLString_WhenImporting_ShouldReturnACupOfTeaDataFrame() {
        CupOfTea cupOfTea = new CupOfTea();

        cupOfTea.importFromHtml(SAMPLE_TABLE_HTML);

        assertThat(cupOfTea.getHeaders()).hasSize(2);
        assertThat(cupOfTea.getHeaders().get(0).columnName()).isEqualTo("Name");
        assertThat(cupOfTea.getHeaders().get(1).columnName()).isEqualTo("Role");
        assertThat(cupOfTea.getRows()).hasSize(2);
        assertThat(cupOfTea.getRow(0).get("Name")).isEqualTo("Alice");
        assertThat(cupOfTea.getRow(0).get("Role")).isEqualTo("Engineer");
        assertThat(cupOfTea.getRow(1).get("Name")).isEqualTo("Bob");
        assertThat(cupOfTea.getRow(1).get("Role")).isEqualTo("Designer");
    }

    @Test
    @DisplayName("Should import DataFrame from Jsoup Document")
    void test_GivenAJsoupDocument_WhenImporting_ShouldReturnACupOfTeaDataFrame() {
        Document doc = Jsoup.parse(SAMPLE_TABLE_HTML);
        CupOfTea cupOfTea = new CupOfTea();

        cupOfTea.importFromHtml(doc);

        assertThat(cupOfTea.getHeaders()).hasSize(2);
        assertThat(cupOfTea.getRows()).hasSize(2);
        assertThat(cupOfTea.getRow(0).get("Name")).isEqualTo("Alice");
        assertThat(cupOfTea.getRow(1).get("Role")).isEqualTo("Designer");
    }

    @Test
    @DisplayName("Should import DataFrame from valid HTTP URL")
    void test_GivenAValidURL_WhenImporting_ShouldReturnACupOfTeaDataFrame() throws Exception {
        HttpServer server = tryCreateServer();
        try {
            byte[] responseBytes = SAMPLE_TABLE_HTML.getBytes(StandardCharsets.UTF_8);
            server.createContext("/table", exchange -> {
                exchange.sendResponseHeaders(200, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            });
            server.start();

            URL url = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/table").toURL();
            CupOfTea cupOfTea = new CupOfTea();
            cupOfTea.importFromHtml(url);

            assertThat(cupOfTea.getHeaders()).hasSize(2);
            assertThat(cupOfTea.getRows()).hasSize(2);
            assertThat(cupOfTea.getRow(0).get("Name")).isEqualTo("Alice");
            assertThat(cupOfTea.getRow(1).get("Name")).isEqualTo("Bob");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Should throw ImportException when URL response exceeds timeout")
    void test_GivenASlowURL_WhenTimeoutIsExceeded_ShouldThrowSocketTimeoutException() throws Exception {
        HttpServer server = tryCreateServer();
        try {
            byte[] responseBytes = SAMPLE_TABLE_HTML.getBytes(StandardCharsets.UTF_8);
            server.createContext("/slow", exchange -> {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                exchange.sendResponseHeaders(200, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            });
            server.start();

            URL url = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/slow").toURL();
            CupOfTea cupOfTea = new CupOfTea();

            assertThatThrownBy(() -> cupOfTea.importFromHtml(url, 50, HTMLImporter.DEFAULT_MAX_BODY_SIZE_BYTES))
                .isInstanceOf(ImportException.class)
                .satisfies(e -> {
                    Throwable cause = e.getCause();
                    assertThat(cause).satisfiesAnyOf(
                        c -> assertThat(c).isInstanceOf(SocketTimeoutException.class),
                        c -> assertThat(c).isInstanceOf(HttpTimeoutException.class)
                    );
                });
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Should limit bytes read when maxBodySize is set")
    void test_GivenALargeURLResponse_WhenMaxBodySizeIsSet_ShouldLimitBytesRead() throws Exception {
        HttpServer server = tryCreateServer();
        StringBuilder largeTable = new StringBuilder("<html><body><table><tr><th>Name</th><th>Role</th></tr>");
        for (int i = 0; i < 200; i++) {
            largeTable.append("<tr><td>User").append(i).append("</td><td>Engineer</td></tr>");
        }
        largeTable.append("</table></body></html>");

        try {
            byte[] responseBytes = largeTable.toString().getBytes(StandardCharsets.UTF_8);
            server.createContext("/large", exchange -> {
                exchange.sendResponseHeaders(200, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            });
            server.start();

            URL url = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/large").toURL();
            CupOfTea cupOfTea = new CupOfTea();
            cupOfTea.importFromHtml(url, HTMLImporter.DEFAULT_TIMEOUT_MILLIS, 256);

            assertThat(cupOfTea.getRows().size()).isLessThan(200);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for non-http URL")
    void test_GivenNonHttpURL_WhenImporting_ShouldThrowIllegalArgumentException() throws Exception {
        File testFile = getSampleHtml();
        URL fileUrl = testFile.toURI().toURL();
        CupOfTea cupOfTea = new CupOfTea();

        assertThatThrownBy(() -> cupOfTea.importFromHtml(fileUrl))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for null inputs or invalid limits")
    void test_GivenNullInputsOrInvalidLimits_WhenImporting_ShouldThrowIllegalArgumentException() {
        CupOfTea cupOfTea = new CupOfTea();
        HTMLImporter importer = new HTMLImporter(cupOfTea);

        assertThatThrownBy(() -> importer.importFromHtml((File) null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> importer.importFromHtml((URL) null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> importer.importFromHtml((String) null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> importer.importFromHtml((Document) null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HTMLImporter(cupOfTea, 0, 1024))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HTMLImporter(cupOfTea, 1000, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
