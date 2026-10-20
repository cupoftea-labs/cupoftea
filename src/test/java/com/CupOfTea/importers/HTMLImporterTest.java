package com.CupOfTea.importers;

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

import com.CupOfTea.CupOfTea;
import com.sun.net.httpserver.HttpServer;

import junit.framework.TestCase;

public class HTMLImporterTest extends TestCase {

    private static final String SAMPLE_TABLE_HTML =
            "<html><body><table>" +
            "<tr><th>Name</th><th>Role</th></tr>" +
            "<tr><td>Alice</td><td>Engineer</td></tr>" +
            "<tr><td>Bob</td><td>Designer</td></tr>" +
            "</table></body></html>";

    public void test_GivenAHTMLFile_WhenImporting_ShouldReturnACupOfTeaDataFrame() throws Exception {

        int expectedColumnsNumber = 11;
        int expectedRowsNumber = 19;

        File testFile = new File("src/test/java/com/CupOfTea/testFiles/html_sample.html");

        CupOfTea cupOfTea = new CupOfTea();

        cupOfTea.importFromHtml(testFile);

        assertEquals(expectedColumnsNumber, cupOfTea.getHeaders().size());
        assertEquals(expectedRowsNumber, cupOfTea.getRows().size());

    }

    public void test_GivenAHTMLString_WhenImporting_ShouldReturnACupOfTeaDataFrame() throws Exception {
        CupOfTea cupOfTea = new CupOfTea();

        cupOfTea.importFromHtml(SAMPLE_TABLE_HTML);

        assertEquals(2, cupOfTea.getHeaders().size());
        assertEquals("Name", cupOfTea.getHeaders().get(0).columnName());
        assertEquals("Role", cupOfTea.getHeaders().get(1).columnName());
        assertEquals(2, cupOfTea.getRows().size());
        assertEquals("Alice", cupOfTea.getRow(0).get("Name"));
        assertEquals("Engineer", cupOfTea.getRow(0).get("Role"));
        assertEquals("Bob", cupOfTea.getRow(1).get("Name"));
        assertEquals("Designer", cupOfTea.getRow(1).get("Role"));
    }

    public void test_GivenAJsoupDocument_WhenImporting_ShouldReturnACupOfTeaDataFrame() throws Exception {
        Document doc = Jsoup.parse(SAMPLE_TABLE_HTML);
        CupOfTea cupOfTea = new CupOfTea();

        cupOfTea.importFromHtml(doc);

        assertEquals(2, cupOfTea.getHeaders().size());
        assertEquals(2, cupOfTea.getRows().size());
        assertEquals("Alice", cupOfTea.getRow(0).get("Name"));
        assertEquals("Designer", cupOfTea.getRow(1).get("Role"));
    }

    public void test_GivenAValidURL_WhenImporting_ShouldReturnACupOfTeaDataFrame() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
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

            assertEquals(2, cupOfTea.getHeaders().size());
            assertEquals(2, cupOfTea.getRows().size());
            assertEquals("Alice", cupOfTea.getRow(0).get("Name"));
            assertEquals("Bob", cupOfTea.getRow(1).get("Name"));
        } finally {
            server.stop(0);
        }
    }

    public void test_GivenASlowURL_WhenTimeoutIsExceeded_ShouldThrowSocketTimeoutException() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
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
            try {
                cupOfTea.importFromHtml(url, 50, HTMLImporter.DEFAULT_MAX_BODY_SIZE_BYTES);
                fail("Expected SocketTimeoutException or HttpTimeoutException when URL response exceeds timeout");
            } catch (SocketTimeoutException | HttpTimeoutException expected) {
                // expected
            }
        } finally {
            server.stop(0);
        }
    }

    public void test_GivenALargeURLResponse_WhenMaxBodySizeIsSet_ShouldLimitBytesRead() throws Exception {
        StringBuilder largeTable = new StringBuilder("<html><body><table><tr><th>Name</th><th>Role</th></tr>");
        for (int i = 0; i < 200; i++) {
            largeTable.append("<tr><td>User").append(i).append("</td><td>Engineer</td></tr>");
        }
        largeTable.append("</table></body></html>");

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
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

            assertTrue("Expected rows to be truncated by maxBodySizeBytes", cupOfTea.getRows().size() < 200);
        } finally {
            server.stop(0);
        }
    }

    public void test_GivenNonHttpURL_WhenImporting_ShouldThrowIllegalArgumentException() throws Exception {
        File testFile = new File("src/test/java/com/CupOfTea/testFiles/html_sample.html");
        URL fileUrl = testFile.toURI().toURL();
        CupOfTea cupOfTea = new CupOfTea();

        try {
            cupOfTea.importFromHtml(fileUrl);
            fail("Expected IllegalArgumentException for file:// URL");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    public void test_GivenNullInputsOrInvalidLimits_WhenImporting_ShouldThrowIllegalArgumentException() throws Exception {
        CupOfTea cupOfTea = new CupOfTea();
        HTMLImporter importer = new HTMLImporter(cupOfTea);

        try {
            importer.importFromHtml((File) null);
            fail("Expected IllegalArgumentException for null File");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        try {
            importer.importFromHtml((URL) null);
            fail("Expected IllegalArgumentException for null URL");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        try {
            importer.importFromHtml((String) null);
            fail("Expected IllegalArgumentException for null String");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        try {
            importer.importFromHtml((Document) null);
            fail("Expected IllegalArgumentException for null Document");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        try {
            new HTMLImporter(cupOfTea, 0, 1024);
            fail("Expected IllegalArgumentException for non-positive timeout");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        try {
            new HTMLImporter(cupOfTea, 1000, 0);
            fail("Expected IllegalArgumentException for non-positive maxBodySize");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

}
