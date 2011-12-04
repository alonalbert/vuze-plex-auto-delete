package com.alon.vuze.plexautodelete;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

public class PlexClient {
    final private String hostname;

    final private int port;

    final private DocumentBuilder builder;

    final private XPath xPath;

    public PlexClient(String hostname, int port) throws ParserConfigurationException {
        this.hostname = hostname;
        this.port = port;

        builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        xPath = XPathFactory.newInstance().newXPath();
    }

    public List<Episode> getEpisodes(Directory section)
            throws IOException, SAXException, XPathExpressionException {

        final ArrayList<Directory> shows = getDirectories(
                "/library/sections/" + section.getKey() + "/all", null);

        final List<Episode> episodes = new ArrayList<Episode>();
        for (Directory show : shows) {
            final ArrayList<Directory> seasons = getDirectories(show.getKey(), null);
            for (Directory season : seasons) {
                final NodeList elements = getNodes(
                        season.getKey(), "/MediaContainer/Video");
                for (int iElement = 0, numElements = elements.getLength(); iElement < numElements; iElement++) {
                    final Element element = (Element) elements.item(iElement);
                    final String key = element.getAttribute("key");
                    final NodeList attributes = (NodeList) xPath.evaluate("Media/Part/@file", element, XPathConstants.NODESET);
                    final List<String> files = new ArrayList<String>();
                    for (int iAttribute = 0, numAttibutes = attributes.getLength(); iAttribute < numAttibutes; iAttribute++) {
                        final Attr attr = (Attr) attributes.item(iAttribute);
                        files.add(attr.getValue());
                    }

                    final int viewCount;
                    final long lastViewedAt;
                    if (element.hasAttribute("viewCount")) {
                        viewCount = Integer.parseInt(element.getAttribute("viewCount"));
                        if (viewCount > 0) {
                            lastViewedAt = getLastViewedAt(key);
                        } else {
                            lastViewedAt = Long.MAX_VALUE;
                        }
                    } else {
                        viewCount = 0;
                        lastViewedAt = Long.MAX_VALUE;
                    }
                    final Episode episode = new Episode(key, viewCount, lastViewedAt, files);

                    episodes.add(episode);
                }
            }
        }

        return episodes;
    }

    private long getLastViewedAt(String key) throws IOException, SAXException, XPathExpressionException {
        Document document = getDocument(key);
        Element element = (Element) xPath.evaluate("/MediaContainer/Video", document, XPathConstants.NODE);
        if (!element.hasAttribute("lastViewedAt")) {
            document = getDocument(key);
            element = (Element) xPath.evaluate("/MediaContainer/Video", document, XPathConstants.NODE);
        }
        try {
            return Long.parseLong(element.getAttribute("lastViewedAt")) * 1000;
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }

    public Collection<Directory> getShowSections()
            throws IOException, SAXException, XPathExpressionException {
        return getDirectories("/library/sections", "[@type='show']");
    }

    private ArrayList<Directory> getDirectories(String uri, String filter)
            throws IOException, SAXException, XPathExpressionException {
        if (filter == null) {
            filter = "";
        }
        final NodeList elements = getNodes(uri, "/MediaContainer/Directory" + filter);

        final ArrayList<Directory> directories = new ArrayList<Directory>();
        for (int i = 0, len = elements.getLength(); i < len; i++) {
            final Element element = (Element) elements.item(i);
            final String title = element.getAttribute("title");
            final String type = element.getAttribute("type");
            final String key = element.getAttribute("key");
            directories.add(new Directory(title, type, key));
        }
        return directories;
    }

    private NodeList getNodes(String uri, String expression)
            throws IOException, SAXException, XPathExpressionException {
        final Document document = getDocument(uri);
        return (NodeList) xPath.evaluate(expression, document, XPathConstants.NODESET);
    }

    private Document getDocument(String uri) throws IOException, SAXException {
        final URL url = new URL("http", hostname, port, uri);
        final InputStream stream = url.openStream();
        try {
            return builder.parse(stream);
        } finally {
            stream.close();
        }
      }

    @Override
    public String toString() {
        return hostname + ":" + port;
    }
}