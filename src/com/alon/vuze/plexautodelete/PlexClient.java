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

    public PlexClient(String hostname) throws ParserConfigurationException {
        this(hostname, 32400);
    }

    public PlexClient(String hostname, int port) throws ParserConfigurationException {
        this.hostname = hostname;
        this.port = port;

        builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        xPath = XPathFactory.newInstance().newXPath();
    }

    public List<Episode> getWatchedEpisodes(Directory section)
            throws IOException, SAXException, XPathExpressionException {

        final ArrayList<Directory> shows = getDirectories(
                "/library/sections/" + section.getKey() + "/all");

        final List<Episode> episodes = new ArrayList<Episode>();
        for (Directory show : shows) {
            final ArrayList<Directory> seasons = getDirectories(show.getKey());
            for (Directory season : seasons) {
                final NodeList elements = getNodes(
                        season.getKey(), "/MediaContainer/Video");
                for (int iElement = 0, numElements = elements.getLength(); iElement < numElements; iElement++) {
                    final Element element = (Element) elements.item(iElement);
                    final String key = element.getAttribute("key");
                    final Document document = getDocument(key);
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
                        final Element deepElement = (Element) xPath.evaluate(
                                "/MediaContainer/Video", document, XPathConstants.NODE);
                        final String s = deepElement.getAttribute("lastViewedAt");
                        lastViewedAt = Long.parseLong(s) * 1000;
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

    public Collection<Directory> getShowSections()
            throws IOException, SAXException, XPathExpressionException {
        return getDirectories("/library/sections");
    }

    private ArrayList<Directory> getDirectories(String uri)
            throws IOException, SAXException, XPathExpressionException {
        final NodeList elements = getNodes(uri, "/MediaContainer/Directory");

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
}
