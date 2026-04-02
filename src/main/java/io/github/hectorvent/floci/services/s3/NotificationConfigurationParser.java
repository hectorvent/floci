package io.github.hectorvent.floci.services.s3;

import io.github.hectorvent.floci.services.s3.model.FilterRule;
import io.github.hectorvent.floci.services.s3.model.NotificationConfiguration;
import io.github.hectorvent.floci.services.s3.model.QueueNotification;
import io.github.hectorvent.floci.services.s3.model.TopicNotification;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Single-pass StAX parser for S3 {@code NotificationConfiguration} XML bodies.
 *
 * <p>Handles the full schema including nested {@code <Filter><S3Key><FilterRule>}
 * elements that the generic {@link io.github.hectorvent.floci.core.common.XmlParser}
 * cannot process (its {@code getElementText()} throws on container elements).
 */
public final class NotificationConfigurationParser {

    private static final XMLInputFactory FACTORY;

    static {
        FACTORY = XMLInputFactory.newInstance();
        FACTORY.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        FACTORY.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        FACTORY.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    }

    private NotificationConfigurationParser() {}

    /**
     * Parses a {@code PutBucketNotificationConfiguration} XML body into a
     * {@link NotificationConfiguration} with full filter support.
     */
    public static NotificationConfiguration parse(String xml) {
        NotificationConfiguration config = new NotificationConfiguration();
        if (xml == null || xml.isEmpty()) {
            return config;
        }
        try {
            XMLStreamReader r = FACTORY.createXMLStreamReader(new StringReader(xml));
            while (r.hasNext()) {
                int event = r.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = r.getLocalName();
                    if ("QueueConfiguration".equals(name)) {
                        QueueNotification qn = parseQueueConfiguration(r);
                        if (qn != null) {
                            config.getQueueConfigurations().add(qn);
                        }
                    } else if ("TopicConfiguration".equals(name)) {
                        TopicNotification tn = parseTopicConfiguration(r);
                        if (tn != null) {
                            config.getTopicConfigurations().add(tn);
                        }
                    }
                }
            }
            r.close();
        } catch (XMLStreamException ignored) {}
        return config;
    }

    private static QueueNotification parseQueueConfiguration(XMLStreamReader r) throws XMLStreamException {
        String id = "";
        String queueArn = null;
        List<String> events = new ArrayList<>();
        List<FilterRule> filterRules = new ArrayList<>();

        while (r.hasNext()) {
            int event = r.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = r.getLocalName();
                switch (name) {
                    case "Id" -> id = r.getElementText();
                    case "Queue" -> queueArn = r.getElementText();
                    case "Event" -> events.add(r.getElementText());
                    case "Filter" -> filterRules.addAll(parseFilter(r));
                    default -> skipElement(r);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT
                    && "QueueConfiguration".equals(r.getLocalName())) {
                break;
            }
        }

        if (queueArn != null && !events.isEmpty()) {
            return new QueueNotification(id, queueArn, events, filterRules);
        }
        return null;
    }

    private static TopicNotification parseTopicConfiguration(XMLStreamReader r) throws XMLStreamException {
        String id = "";
        String topicArn = null;
        List<String> events = new ArrayList<>();
        List<FilterRule> filterRules = new ArrayList<>();

        while (r.hasNext()) {
            int event = r.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = r.getLocalName();
                switch (name) {
                    case "Id" -> id = r.getElementText();
                    case "Topic" -> topicArn = r.getElementText();
                    case "Event" -> events.add(r.getElementText());
                    case "Filter" -> filterRules.addAll(parseFilter(r));
                    default -> skipElement(r);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT
                    && "TopicConfiguration".equals(r.getLocalName())) {
                break;
            }
        }

        if (topicArn != null && !events.isEmpty()) {
            return new TopicNotification(id, topicArn, events, filterRules);
        }
        return null;
    }

    /**
     * Parses {@code <Filter><S3Key><FilterRule><Name>...</Name><Value>...</Value>
     * </FilterRule></S3Key></Filter>} and returns the list of filter rules.
     */
    private static List<FilterRule> parseFilter(XMLStreamReader r) throws XMLStreamException {
        List<FilterRule> rules = new ArrayList<>();
        while (r.hasNext()) {
            int event = r.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = r.getLocalName();
                if ("FilterRule".equals(name)) {
                    FilterRule rule = parseFilterRule(r);
                    if (rule != null) {
                        rules.add(rule);
                    }
                }
                // S3Key and other wrappers are traversed implicitly
            } else if (event == XMLStreamConstants.END_ELEMENT
                    && "Filter".equals(r.getLocalName())) {
                break;
            }
        }
        return rules;
    }

    private static FilterRule parseFilterRule(XMLStreamReader r) throws XMLStreamException {
        String ruleName = null;
        String ruleValue = null;
        while (r.hasNext()) {
            int event = r.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = r.getLocalName();
                switch (name) {
                    case "Name" -> ruleName = r.getElementText();
                    case "Value" -> ruleValue = r.getElementText();
                    default -> skipElement(r);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT
                    && "FilterRule".equals(r.getLocalName())) {
                break;
            }
        }
        if (ruleName != null && ruleValue != null) {
            return new FilterRule(ruleName, ruleValue);
        }
        return null;
    }

    /**
     * Skips the current element and its entire subtree.
     * Call when positioned on a START_ELEMENT you want to ignore.
     */
    private static void skipElement(XMLStreamReader r) throws XMLStreamException {
        int depth = 1;
        while (r.hasNext() && depth > 0) {
            int event = r.next();
            if (event == XMLStreamConstants.START_ELEMENT) depth++;
            else if (event == XMLStreamConstants.END_ELEMENT) depth--;
        }
    }
}
