package com.CupOfTea.importers.interfaces;

import java.io.File;
import java.net.URL;

import org.jsoup.nodes.Document;

public interface IHTMLImporter {

    public void importFromHtml(File file);

    /**
     * Dumps the data from an HTML table at the given URL into the CupOfTea data frame
     * using default connect/read timeout and maximum response body size limits.
     * <p>
     * <b>Security warning:</b> Fetching arbitrary or user-supplied URLs on a server is unsafe
     * (SSRF / slowloris risk). In untrusted or server environments, fetch and validate the
     * content externally and prefer {@link #importFromHtml(Document)} or {@link #importFromHtml(String)}.
     *
     * @param url URL to fetch the HTML data from (only {@code http} and {@code https} are supported)
     */
    public void importFromHtml(URL url);

    /**
     * Dumps the data from an HTML table at the given URL into the CupOfTea data frame
     * using the specified timeout and maximum response body size limits.
     * <p>
     * <b>Security warning:</b> Fetching arbitrary or user-supplied URLs on a server is unsafe
     * (SSRF / slowloris risk). In untrusted or server environments, fetch and validate the
     * content externally and prefer {@link #importFromHtml(Document)} or {@link #importFromHtml(String)}.
     *
     * @param url URL to fetch the HTML data from (only {@code http} and {@code https} are supported)
     * @param timeoutMillis connect and read timeout in milliseconds (must be &gt; 0)
     * @param maxBodySizeBytes maximum response body size in bytes (must be &gt; 0)
     */
    public void importFromHtml(URL url, int timeoutMillis, int maxBodySizeBytes);

    /**
     * Dumps the data from an HTML string containing a table into the CupOfTea data frame.
     * Preferred over {@link #importFromHtml(URL)} in untrusted or server environments.
     *
     * @param html HTML markup containing a table
     */
    public void importFromHtml(String html);

    /**
     * Dumps the data from an already-parsed Jsoup {@link Document} into the CupOfTea data frame.
     * Preferred over {@link #importFromHtml(URL)} in untrusted or server environments.
     *
     * @param doc Jsoup HTML document containing a table
     */
    public void importFromHtml(Document doc);

}
