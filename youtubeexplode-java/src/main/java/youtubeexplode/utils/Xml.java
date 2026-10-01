package youtubeexplode.utils;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/** Namespace-stripping XML helper: lookups match on local names only. */
public final class Xml {
    private Xml() {}

    public static Element parse(String source) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(source))).getDocumentElement();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid XML.", e);
        }
    }

    private static String localName(Node n) {
        return n.getLocalName() != null ? n.getLocalName() : n.getNodeName();
    }

    /** Attribute value by local name, or null. */
    public static String attr(Element element, String name) {
        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node a = attrs.item(i);
            if (localName(a).equals(name)) return a.getNodeValue();
        }
        return null;
    }

    /** Direct child elements with the local name. */
    public static List<Element> children(Element element, String name) {
        List<Element> result = new ArrayList<>();
        for (Node n = element.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && localName(e).equals(name)) result.add(e);
        }
        return result;
    }

    public static Element child(Element element, String name) {
        List<Element> all = children(element, name);
        return all.isEmpty() ? null : all.get(0);
    }

    /** All descendant elements with the local name, in document order. */
    public static List<Element> descendants(Element element, String name) {
        List<Element> result = new ArrayList<>();
        collect(element, name, result);
        return result;
    }

    private static void collect(Element element, String name, List<Element> out) {
        for (Node n = element.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e) {
                if (localName(e).equals(name)) out.add(e);
                collect(e, name, out);
            }
        }
    }

    public static String text(Element element) {
        return element.getTextContent();
    }
}
