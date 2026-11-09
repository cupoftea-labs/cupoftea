package com.cupoftea.storage;

import com.cupoftea.CupOfTea;
import com.cupoftea.storage.cloud.CloudStorageFileSystem;
import com.cupoftea.storage.http.HttpRangeSeekableByteChannel;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StorageModuleTest {

    public record User(int id, String name) {}

    private static HttpServer server;
    private static int serverPort;
    private static final byte[] SERVER_DATA = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8);

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        serverPort = server.getAddress().getPort();
        server.createContext("/data.bin", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String rangeHeader = exchange.getRequestHeaders().getFirst("Range");
                if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                    String[] parts = rangeHeader.substring(6).split("-");
                    int start = Integer.parseInt(parts[0]);
                    int end = parts.length > 1 && !parts[1].isEmpty() ? Integer.parseInt(parts[1]) : SERVER_DATA.length - 1;
                    int length = end - start + 1;
                    exchange.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + SERVER_DATA.length);
                    exchange.getResponseHeaders().set("Content-Length", String.valueOf(length));
                    exchange.sendResponseHeaders(206, length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(SERVER_DATA, start, length);
                    }
                } else if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.getResponseHeaders().set("Content-Length", String.valueOf(SERVER_DATA.length));
                    exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
                    exchange.sendResponseHeaders(200, -1);
                } else {
                    exchange.getResponseHeaders().set("Content-Length", String.valueOf(SERVER_DATA.length));
                    exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
                    exchange.sendResponseHeaders(200, SERVER_DATA.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(SERVER_DATA);
                    }
                }
            }
        });
        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void testStorageRegistryDiscovery() {
        assertThat(StorageRegistry.getFileSystem(URI.create("s3://bucket/test.csv"))).isPresent();
        assertThat(StorageRegistry.getFileSystem(URI.create("gs://bucket/test.csv"))).isPresent();
        assertThat(StorageRegistry.getFileSystem(URI.create("abfss://bucket/test.csv"))).isPresent();
        assertThat(StorageRegistry.getFileSystem(URI.create("http://localhost/test.csv"))).isPresent();
        assertThat(StorageRegistry.getFileSystem(URI.create("https://localhost/test.csv"))).isPresent();
        assertThat(StorageRegistry.getFileSystem(URI.create("unsupported://bucket/test.csv"))).isEmpty();
    }

    @Test
    void testHttpRangeSeekableByteChannel() throws Exception {
        URI uri = URI.create("http://localhost:" + serverPort + "/data.bin");
        try (SeekableByteChannel channel = new HttpRangeSeekableByteChannel(uri, SERVER_DATA.length)) {
            assertThat(channel.size()).isEqualTo(SERVER_DATA.length);
            assertThat(channel.position()).isEqualTo(0);

            ByteBuffer buf = ByteBuffer.allocate(10);
            int read = channel.read(buf);
            assertThat(read).isEqualTo(10);
            assertThat(new String(buf.array(), 0, read, StandardCharsets.UTF_8)).isEqualTo("0123456789");
            assertThat(channel.position()).isEqualTo(10);

            // Seek to index 10 ("A")
            channel.position(10);
            buf.clear();
            read = channel.read(buf);
            assertThat(read).isEqualTo(10);
            assertThat(new String(buf.array(), 0, read, StandardCharsets.UTF_8)).isEqualTo("ABCDEFGHIJ");

            // Seek backwards
            channel.position(5);
            buf.clear();
            channel.read(buf);
            assertThat(new String(buf.array(), 0, 5, StandardCharsets.UTF_8)).isEqualTo("56789");
        }
    }

    @Test
    void testCloudStorageFileSystemCsvReadWrite() throws Exception {
        CloudStorageFileSystem.clearMockObjects();

        CupOfTea df = CupOfTea.read().records(List.of(
                new User(1, "Alice"),
                new User(2, "Bob"),
                new User(3, "Charlie")
        ));

        URI s3Uri = URI.create("s3://my-bucket/data/users.csv");
        df.write().csv(s3Uri.toString());

        StorageFileSystem s3Fs = StorageRegistry.requireFileSystem(s3Uri);
        assertThat(s3Fs.exists(s3Uri)).isTrue();
        assertThat(s3Fs.size(s3Uri)).isGreaterThan(0);

        // Read back using DataFrameReader URI detection
        CupOfTea readDf = CupOfTea.read().csv(s3Uri.toString());
        assertThat(readDf.getDataFrame().size()).isEqualTo(3);
        assertThat(readDf.getRow(0).getString("name")).isEqualTo("Alice");
        assertThat(readDf.getRow(1).getString("name")).isEqualTo("Bob");
        assertThat(readDf.getRow(2).getString("name")).isEqualTo("Charlie");
    }

    @Test
    void testCloudStorageFileSystemParquetReadWrite() throws Exception {
        CloudStorageFileSystem.clearMockObjects();

        CupOfTea df = CupOfTea.read().records(List.of(
                new User(100, "X"),
                new User(200, "Y"),
                new User(300, "Z")
        ));

        URI gsUri = URI.create("gs://my-gcs-bucket/analytics/data.parquet");

        Path tempParquet = Files.createTempFile("test_parquet", ".parquet");
        Files.deleteIfExists(tempParquet);
        try {
            df.write().parquet(tempParquet.toString());
            byte[] parquetBytes = Files.readAllBytes(tempParquet);

            StorageFileSystem gsFs = StorageRegistry.requireFileSystem(gsUri);
            try (OutputStream os = gsFs.openOutputStream(gsUri)) {
                os.write(parquetBytes);
            }

            assertThat(gsFs.exists(gsUri)).isTrue();
            assertThat(gsFs.size(gsUri)).isGreaterThan(0);

            // Read parquet selectively via SeekableByteChannel
            CupOfTea parquetDf = CupOfTea.read().parquet(gsUri.toString());
            assertThat(parquetDf.getDataFrame().size()).isEqualTo(3);
            assertThat(parquetDf.getRow(0).getString("name")).isEqualTo("X");
            assertThat(parquetDf.getRow(1).getString("name")).isEqualTo("Y");
            assertThat(parquetDf.getRow(2).getString("name")).isEqualTo("Z");
        } finally {
            Files.deleteIfExists(tempParquet);
        }
    }

    @Test
    void testCloudStorageSchemes() {
        CloudStorageFileSystem cloudFs = new CloudStorageFileSystem();
        for (String scheme : List.of("s3", "s3a", "gs", "gcs", "abfs", "abfss")) {
            assertThat(cloudFs.supports(scheme)).isTrue();
        }
        assertThat(cloudFs.supports("file")).isFalse();
    }
}
