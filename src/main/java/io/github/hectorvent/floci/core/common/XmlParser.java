package io.github.hectorvent.floci.core.common;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight StAX-based helpers for parsing XML request bodies.
 *
 * <p>Uses {@code javax.xml.stream} (part of the JDK — no extra dependency).
 * Namespace prefixes are ignored so that both plain {@code <Key>} and
 * namespace-qualified {@code <s3:Key>} elements match by local name.
 * Handles whitespace variations and CDATA sections correctly.
 *
 * <p>All methods silently return empty collections on malformed input so that
 * callers receive the same result they would have from a non-matching regex.
 */
public final class XmlParser {

    private static final XMLInputFactory FACTORY;

    static {
        FACTORY = XMLInputFactory.newInstance();
        FACTORY.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        FACTORY.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        FACTORY.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    }

    private XmlParser() {}

    /**
     * Extracts the text content of every element whose local name matches {@code elementName}.
     *
     * <pre>{@code
     * List<String> keys = XmlParser.extractAll(body, "Key");
     * }</pre>
     */
    public static List<String> extractAll(String xml, String elementName) {
        List<String> result = new ArrayList<>();
        if (xml == null || xml.isEmpty()) {
            return result;
        }
        try {
            XMLStreamReader r = FACTORY.createXMLStreamReader(new StringReader(xml));
            while (r.hasNext()) {
                int event = r.next();
                if (event == XMLStreamConstants.START_ELEMENT
                        && elementName.equals(r.getLocalName())) {
                    result.add(r.getElementText());
                }
            }
            r.close();
        } catch (XMLStreamException ignored) {}
        return result;
    }

    /**
     * Extracts the text content of the first element matching {@code elementName},
     * or {@code defaultValue} if no such element exists.
     *
     * <pre>{@code
     * String mode = XmlParser.extractFirst(body, "Mode", null);
     * }</pre>
     */
    public static String extractFirst(String xml, String elementName, String defaultValue) {
        List<String> all = extractAll(xml, elementName);
        return all.isEmpty() ? defaultValue : all.get(0);
    }

    /**
     * Returns {@code true} if the document contains at least one element with the given
     * local name whose text is equal to {@code value} (case-sensitive).
     *
     * <pre>{@code
     * boolean quiet = XmlParser.containsValue(body, "Quiet", "true");
     * }</pre>
     */
    public static boolean containsValue(String xml, String elementName, String value) {
        return extractAll(xml, elementName).stream().anyMatch(value::equals);
    }

    /**
     * Extracts sibling key/value pairs from every {@code parentElement} block.
     *
     * <p>Example — parses {@code <Tag><Key>env</Key><Value>prod</Value></Tag>}:
     * <pre>{@code
     * Map<String,String> tags = XmlParser.extractPairs(body, "Tag", "Key", "Value");
     * }</pre>
     *
     * Insertion order is preserved (backed by {@link LinkedHashMap}).
     */
    public static Map<String, String> extractPairs(String xml, String parentElement,
                                                    String keyElement, String valueElement) {
        Map<String, String> result = new LinkedHashMap<>();
        if (xml == null || xml.isEmpty()) {
            return result;
        }
        try {
            XMLStreamReader r = FACTORY.createXMLStreamReader(new StringReader(xml));
            String pendingKey = null;
            boolean inParent = false;
            while (r.hasNext()) {
                int event = r.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String local = r.getLocalName();
                    if (parentElement.equals(local)) {
                        inParent = true;
                        pendingKey = null;
                    } else if (inParent && keyElement.equals(local)) {
                        pendingKey = r.getElementText();
                    } else if (inParent && valueElement.equals(local) && pendingKey != null) {
                        result.put(pendingKey, r.getElementText());
                        pendingKey = null;
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if (parentElement.equals(r.getLocalName())) {
                        inParent = false;
                    }
                }
            }
            r.close();
        } catch (XMLStreamException ignored) {}
        return result;
    }

    /**
     * Extracts every group of elements nested inside a repeating {@code parentElement},
     * returning each group as a {@code Map<localName, List<text>>}.
     *
     * <p>Allows for repeated child elements with the same name (e.g. multiple {@code <Event>}
     * tags inside a single {@code <QueueConfiguration>}).
     */
    public static List<Map<String, List<String>>> extractGroupsMulti(String xml, String parentElement) {
        List<Map<String, List<String>>> result = new ArrayList<>();
        if (xml == null || xml.isEmpty()) {
            return result;
        }
        try {
            XMLStreamReader r = FACTORY.createXMLStreamReader(new StringReader(xml));
            Map<String, List<String>> current = null;
            int depth = 0;
            while (r.hasNext()) {
                int event = r.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String local = r.getLocalName();
                    if (parentElement.equals(local)) {
                        current = new LinkedHashMap<>();
                        depth = 1;
                    } else if (current != null && depth == 1) {
                        readElementIntoMultiMap(r, local, current);
                    } else if (current != null) {
                        depth++;
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if (current != null && parentElement.equals(r.getLocalName())) {
                        result.add(current);
                        current = null;
                        depth = 0;
                    } else if (current != null) {
                        depth--;
                    }
                }
            }
            r.close();
        } catch (XMLStreamException ignored) {}
        return result;
    }

    /**
     * Extracts every group of elements nested inside a repeating {@code parentElement},
     * returning each group as a {@code Map<localName, text>}.
     *
     * <p>Useful for notification-configuration blocks that contain multiple fields:
     * <pre>{@code
     * List<Map<String,String>> configs =
     *         XmlParser.extractGroups(body, "QueueConfiguration");
     * // configs.get(0).get("QueueArn") → "arn:aws:sqs:..."
     * }</pre>
     */
    public static List<Map<String, String>> extractGroups(String xml, String parentElement) {
        List<Map<String, String>> result = new ArrayList<>();
        if (xml == null || xml.isEmpty()) {
            return result;
        }
        try {
            XMLStreamReader r = FACTORY.createXMLStreamReader(new StringReader(xml));
            Map<String, String> current = null;
            int depth = 0;
            while (r.hasNext()) {
                int event = r.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String local = r.getLocalName();
                    if (parentElement.equals(local)) {
                        current = new LinkedHashMap<>();
                        depth = 1;
                    } else if (current != null && depth == 1) {
                        readElementIntoMap(r, local, current);
                    } else if (current != null) {
                        depth++;
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if (current != null && parentElement.equals(r.getLocalName())) {
                        result.add(current);
                        current = null;
                        depth = 0;
                    } else if (current != null) {
                        depth--;
                    }
                }
            }
            r.close();
        } catch (XMLStreamException ignored) {}
        return result;
    }

    /**
     * Reads a depth-1 child element and stores its content into a multi-value map.
     * Text-only elements are stored as {@code elementName → [text]}.
     * Nested elements (e.g. {@code <Filter>}) are traversed and any leaf
     * {@code <Name>}/{@code <Value>} pairs found inside are stored directly
     * into the map (e.g. {@code prefix → [images/]}).
     */
    private static void readElementIntoMultiMap(XMLStreamReader r, String elementName,
                                                 Map<String, List<String>> map) throws XMLStreamException {
        StringBuilder text = new StringBuilder();
        boolean textOnly = true;
        String pendingName = null;
        int nested = 1;
        while (r.hasNext()) {
            int ev = r.next();
            if (ev == XMLStreamConstants.START_ELEMENT) {
                if (nested == 1) textOnly = false;
                nested++;
                String local = r.getLocalName();
                if ("Name".equals(local)) {
                    pendingName = r.getElementText();
                    nested--;
                } else if ("Value".equals(local) && pendingName != null) {
                    String value = r.getElementText();
                    nested--;
                    map.computeIfAbsent(pendingName, k -> new ArrayList<>()).add(value);
                    pendingName = null;
                }
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                nested--;
                if (nested == 0) break;
            } else if (textOnly && (ev == XMLStreamConstants.CHARACTERS || ev == XMLStreamConstants.CDATA)) {
                text.append(r.getText());
            }
        }
        if (textOnly) {
            map.computeIfAbsent(elementName, k -> new ArrayList<>()).add(text.toString());
        }
    }

    /**
     * Reads a depth-1 child element and stores its content into a single-value map.
     * Text-only elements are stored as {@code elementName → text}.
     * Nested elements are traversed and any leaf {@code <Name>}/{@code <Value>}
     * pairs are stored directly (e.g. {@code prefix → images/}).
     */
    private static void readElementIntoMap(XMLStreamReader r, String elementName,
                                            Map<String, String> map) throws XMLStreamException {
        StringBuilder text = new StringBuilder();
        boolean textOnly = true;
        String pendingName = null;
        int nested = 1;
        while (r.hasNext()) {
            int ev = r.next();
            if (ev == XMLStreamConstants.START_ELEMENT) {
                if (nested == 1) textOnly = false;
                nested++;
                String local = r.getLocalName();
                if ("Name".equals(local)) {
                    pendingName = r.getElementText();
                    nested--;
                } else if ("Value".equals(local) && pendingName != null) {
                    String value = r.getElementText();
                    nested--;
                    map.put(pendingName, value);
                    pendingName = null;
                }
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                nested--;
                if (nested == 0) break;
            } else if (textOnly && (ev == XMLStreamConstants.CHARACTERS || ev == XMLStreamConstants.CDATA)) {
                text.append(r.getText());
            }
        }
        if (textOnly) {
            map.put(elementName, text.toString());
        }
    }
}
