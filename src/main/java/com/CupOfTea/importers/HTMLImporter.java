package com.CupOfTea.importers;


import java.io.File;
import java.net.URL;
import java.util.HashMap;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.CupOfTea.CupOfTea;
import com.CupOfTea.importers.interfaces.IHTMLImporter;
import com.CupOfTea.table.Column;

public class HTMLImporter implements IHTMLImporter{

    private final CupOfTea dataFrame;

    public HTMLImporter(final CupOfTea dataFrame){
        this.dataFrame = dataFrame;
    }

    /**
     * Dumps the Data from a HTML table into the CupOfTea data frame.
     * 
     * @param file HTML file with a table
     */
    @Override
    public void importFromHtml(final File file) throws Exception {
        Document doc = Jsoup.parse(file);
        this.importToDataFrame(doc);
    }
    
    /**
     * Dumps the Data from a html table into the CupOfTea data frame.
     * 
     * @param url URL to get the HTML data from
     * @throws Exception
     */
    @Override
    public void importFromHtml(final URL url) throws Exception {
        Document doc = Jsoup.connect(url.toString()).get();
        this.importToDataFrame(doc);
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
