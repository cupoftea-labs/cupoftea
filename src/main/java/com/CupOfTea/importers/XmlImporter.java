package com.CupOfTea.importers;

import java.io.File;
import java.io.StringReader;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.CupOfTea.CupOfTea;
import com.CupOfTea.importers.interfaces.IXmlImporter;
import com.CupOfTea.table.Column;

public class XmlImporter implements IXmlImporter {

    private final CupOfTea dataFrame;

    public XmlImporter(final CupOfTea dataFrame){

        this.dataFrame = dataFrame;

    }

    /**
     * Dumps the Data from a XML File into the CupOfTea data frame.
     * 
     * @param file XML File.
     */
    @Override
    public void importFromXml(final File file) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(file);
        
        this.importFromXml(doc);

    }

    /**
     * Dumps the Data from a XML String into the CupOfTea data frame.
     * 
     * @param xml XML String.
     */
    @Override
    public void importFromXml(final String xml) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        InputSource is = new InputSource(new StringReader(xml));
        Document doc = dBuilder.parse(is);
        this.importFromXml(doc);
    }

    /**
     * Dumps the Data from a XML Document into the CupOfTea data frame.
     * 
     * @param doc XML Document.
     */
    @Override
    public void importFromXml(final Document doc) throws Exception {
        doc.getDocumentElement().normalize();
        
        NodeList outNodes = doc.getChildNodes();
        for (int indexOutNodes = 0; indexOutNodes < outNodes.getLength(); indexOutNodes++) {
            // Cada OutNode Equivale a Um CupOfTea, deveria retornar uma lista de CupOfTeas?
            Node innerNode = outNodes.item(indexOutNodes);
            boolean firstRow = true;

            if (innerNode.getNodeType() == Node.ELEMENT_NODE) {
                Element eElement = (Element) innerNode;
                
                NodeList rows = eElement.getChildNodes();
                for (int j = 0; j < rows.getLength(); ++j)
                {
                    HashMap<String, Object> rowObj = new HashMap<>();
                    Node row = rows.item(j);
                    if (row.getNodeType() == Node.ELEMENT_NODE) {
                        Element element = (Element) row;
                        NamedNodeMap rowAttributes = element.getAttributes();
                        for (int i = 0; i < rowAttributes.getLength(); ++i)
                        {
                            Node attr = rowAttributes.item(i);
                            String columnName = element.getTagName() + '.' + attr.getNodeName();
                            if (firstRow) this.dataFrame.getHeaders().add(new Column(columnName, null));
                            rowObj.put(columnName, attr.getNodeValue());
                        }
                        
                        NodeList rowColumns = element.getChildNodes();
                        for (int i = 0; i < rowColumns.getLength(); ++i)
                        {
                            if (rowColumns.item(i) instanceof Element rowColumn) {
                                String columnName = rowColumn.getTagName();
                                if (firstRow) this.dataFrame.getHeaders().add(new Column(columnName, null));
                                rowObj.put(columnName, rowColumn.getTextContent());
                            }
                        }
                        this.dataFrame.addRow(rowObj);
                        firstRow = false;
                    }
                }
            }
        }
    }

}
