package com.CupOfTea.importers;


import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.CupOfTea.CupOfTea;
import com.CupOfTea.importers.interfaces.IHTMLImporter;
import com.CupOfTea.table.Column;

/**
 * Importer for HTML tables into a {@link CupOfTea} data frame.
 * <p>
 * <b>Security warning:</b> Fetching arbitrary or user-supplied URLs via {@link #importFromHtml(URL)}
 * or {@link #importFromHtml(URL, int, int)} is unsafe when running on a server (SSRF and slowloris risks).
 * For untrusted or server environments, fetch the HTML using an HTTP client with appropriate network
 * policy controls and pass the already-fetched {@link Document} or {@link String} to
 * {@link #importFromHtml(Document)} or {@link #importFromHtml(String)}.
 */
public class HTMLImporter implements IHTMLImporter {

    /**
     * Default connect/read timeout in milliseconds (10 seconds).
     */
    public static final int DEFAULT_TIMEOUT_MILLIS = 10_000;

    /**
     * Default maximum response body size in bytes (2 MiB).
     */
    public static final int DEFAULT_MAX_BODY_SIZE_BYTES = 2 * 1024 * 1024;

    private final CupOfTea dataFrame;
    private final int timeoutMillis;
    private final int maxBodySizeBytes;

    public HTMLImporter(final CupOfTea dataFrame) {
        this(dataFrame, DEFAULT_TIMEOUT_MILLIS, DEFAULT_MAX_BODY_SIZE_BYTES);
    }

    public HTMLImporter(final CupOfTea dataFrame, final int timeoutMillis, final int maxBodySizeBytes) {
        validateLimits(timeoutMillis, maxBodySizeBytes);
        this.dataFrame = dataFrame;
        this.timeoutMillis = timeoutMillis;
        this.maxBodySizeBytes = maxBodySizeBytes;
    }

    /**
     * Dumps the Data from a HTML table into the CupOfTea data frame.
     * 
     * @param file HTML file with a table
     */
    @Override
    public void importFromHtml(final File file) throws Exception {
        if (file == null) {
            throw new IllegalArgumentException("HTML file cannot be null");
        }
        Document doc = Jsoup.parse(file);
        this.importFromHtml(doc);
    }
    
    /**
     * Dumps the Data from a html table into the CupOfTea data frame using the configured
     * timeout ({@link #DEFAULT_TIMEOUT_MILLIS} by default) and max body size
     * ({@link #DEFAULT_MAX_BODY_SIZE_BYTES} by default).
     * <p>
     * <b>Security warning:</b> Fetching arbitrary URLs on a server is unsafe (SSRF / slowloris risk).
     * Prefer {@link #importFromHtml(Document)} or {@link #importFromHtml(String)} with an
     * already-fetched document in untrusted environments.
     * 
     * @param url URL to get the HTML data from
     * @throws Exception if fetching or parsing fails
     */
    @Override
    public void importFromHtml(final URL url) throws Exception {
        this.importFromHtml(url, this.timeoutMillis, this.maxBodySizeBytes);
    }

    /**
     * Dumps the Data from a html table into the CupOfTea data frame using explicit
     * connect/read timeout and maximum response body size limits.
     * <p>
     * <b>Security warning:</b> Fetching arbitrary URLs on a server is unsafe (SSRF / slowloris risk).
     * Prefer {@link #importFromHtml(Document)} or {@link #importFromHtml(String)} with an
     * already-fetched document in untrusted environments.
     *
     * @param url URL to get the HTML data from (must use {@code http} or {@code https})
     * @param timeoutMillis connect and read timeout in milliseconds (must be &gt; 0)
     * @param maxBodySizeBytes maximum response body size in bytes (must be &gt; 0)
     * @throws Exception if fetching or parsing fails
     */
    @Override
    public void importFromHtml(final URL url, final int timeoutMillis, final int maxBodySizeBytes) throws Exception {
        if (url == null) {
            throw new IllegalArgumentException("HTML URL cannot be null");
        }
        validateLimits(timeoutMillis, maxBodySizeBytes);
        String protocol = url.getProtocol() != null ? url.getProtocol().toLowerCase(Locale.ROOT) : "";
        if (!"http".equals(protocol) && !"https".equals(protocol)) {
            throw new IllegalArgumentException("Only http and https URLs are supported: " + url.getProtocol());
        }

        Document doc = Jsoup.connect(url.toString())
                .timeout(timeoutMillis)
                .maxBodySize(maxBodySizeBytes)
                .get();
        this.importFromHtml(doc);
    }

    /**
     * Dumps the table data from an already-fetched HTML string into the CupOfTea data frame.
     * Preferred over {@link #importFromHtml(URL)} in untrusted or server environments.
     *
     * @param html HTML string containing a table
     * @throws Exception if parsing or importing fails
     */
    @Override
    public void importFromHtml(final String html) throws Exception {
        if (html == null) {
            throw new IllegalArgumentException("HTML string cannot be null");
        }
        Document doc = Jsoup.parse(html);
        this.importFromHtml(doc);
    }

    /**
     * Dumps the table data from an already-parsed Jsoup {@link Document} into the CupOfTea data frame.
     * Preferred over {@link #importFromHtml(URL)} in untrusted or server environments.
     *
     * @param doc Jsoup document containing a table
     * @throws Exception if importing fails
     */
    @Override
    public void importFromHtml(final Document doc) throws Exception {
        if (doc == null) {
            throw new IllegalArgumentException("HTML document cannot be null");
        }
        this.importToDataFrame(doc);
    }

    private static void validateLimits(final int timeoutMillis, final int maxBodySizeBytes) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("Timeout must be greater than 0");
        }
        if (maxBodySizeBytes <= 0) {
            throw new IllegalArgumentException("Max body size must be greater than 0");
        }
    }

    /**
     * Dumps the table data from a JSOUP document into the CupOfTea data frame.
     * 
     * @param doc
     * @throws Exception
     */
    private void importToDataFrame(final Document doc) throws Exception {
        boolean firstRow = true;
        Elements rows = doc.select("tr");
        for(Element row : rows) {   
            int i = 0;
            HashMap<String, Object> rowDF = new HashMap<>();
            Elements columns;
            if(firstRow) {
                columns = row.select("th"); // Todo entender como funciona um objecto table no html (entender as tags e seus significados)
            } else {
                columns = row.select("td");
            }
            for (Element column : columns) {
                if(firstRow) this.dataFrame.getHeaders().add(new Column(column.text(), null));
                if(!firstRow) {
                    rowDF.put(this.dataFrame.getHeaders().get(i).columnName(),  column.text());
                    i++;
                }
            }
            if(!firstRow) this.dataFrame.addRow(rowDF);
            firstRow = false;
        }
    }
}
