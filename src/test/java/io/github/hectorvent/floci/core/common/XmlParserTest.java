package io.github.hectorvent.floci.core.common;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class XmlParserTest {

    @Test
    void extractGroupsMultiParsesFilterRules() {
        String xml = """
                <NotificationConfiguration>
                  <QueueConfiguration>
                    <Id>notif1</Id>
                    <Queue>arn:aws:sqs:us-east-1:000000000000:my-queue</Queue>
                    <Event>s3:ObjectCreated:*</Event>
                    <Filter>
                      <S3Key>
                        <FilterRule>
                          <Name>suffix</Name>
                          <Value>.jpg</Value>
                        </FilterRule>
                        <FilterRule>
                          <Name>prefix</Name>
                          <Value>images/</Value>
                        </FilterRule>
                      </S3Key>
                    </Filter>
                  </QueueConfiguration>
                </NotificationConfiguration>
                """;

        List<Map<String, List<String>>> groups =
                XmlParser.extractGroupsMulti(xml, "QueueConfiguration");

        assertEquals(1, groups.size());
        Map<String, List<String>> group = groups.get(0);
        assertEquals(List.of("notif1"), group.get("Id"));
        assertEquals(List.of("arn:aws:sqs:us-east-1:000000000000:my-queue"), group.get("Queue"));
        assertEquals(List.of("s3:ObjectCreated:*"), group.get("Event"));
        assertEquals(List.of(".jpg"), group.get("suffix"));
        assertEquals(List.of("images/"), group.get("prefix"));
    }

    @Test
    void extractGroupsParsesFilterRules() {
        String xml = """
                <NotificationConfiguration>
                  <QueueConfiguration>
                    <Queue>arn:aws:sqs:us-east-1:000000000000:my-queue</Queue>
                    <Event>s3:ObjectCreated:*</Event>
                    <Filter>
                      <S3Key>
                        <FilterRule>
                          <Name>prefix</Name>
                          <Value>logs/</Value>
                        </FilterRule>
                      </S3Key>
                    </Filter>
                  </QueueConfiguration>
                </NotificationConfiguration>
                """;

        List<Map<String, String>> groups =
                XmlParser.extractGroups(xml, "QueueConfiguration");

        assertEquals(1, groups.size());
        Map<String, String> group = groups.get(0);
        assertEquals("arn:aws:sqs:us-east-1:000000000000:my-queue", group.get("Queue"));
        assertEquals("s3:ObjectCreated:*", group.get("Event"));
        assertEquals("logs/", group.get("prefix"));
    }

    @Test
    void extractGroupsMultiWithMultipleEventsAndNoFilter() {
        String xml = """
                <NotificationConfiguration>
                  <QueueConfiguration>
                    <Queue>arn:aws:sqs:us-east-1:000000000000:q1</Queue>
                    <Event>s3:ObjectCreated:*</Event>
                    <Event>s3:ObjectRemoved:*</Event>
                  </QueueConfiguration>
                </NotificationConfiguration>
                """;

        List<Map<String, List<String>>> groups =
                XmlParser.extractGroupsMulti(xml, "QueueConfiguration");

        assertEquals(1, groups.size());
        assertEquals(List.of("s3:ObjectCreated:*", "s3:ObjectRemoved:*"),
                groups.get(0).get("Event"));
        assertNull(groups.get(0).get("prefix"));
        assertNull(groups.get(0).get("suffix"));
    }

    @Test
    void extractGroupsMultiWithFilterBetweenTextElements() {
        String xml = """
                <NotificationConfiguration>
                  <QueueConfiguration>
                    <Queue>arn:aws:sqs:us-east-1:000000000000:q1</Queue>
                    <Filter>
                      <S3Key>
                        <FilterRule><Name>suffix</Name><Value>.png</Value></FilterRule>
                      </S3Key>
                    </Filter>
                    <Event>s3:ObjectCreated:Put</Event>
                  </QueueConfiguration>
                </NotificationConfiguration>
                """;

        List<Map<String, List<String>>> groups =
                XmlParser.extractGroupsMulti(xml, "QueueConfiguration");

        assertEquals(1, groups.size());
        Map<String, List<String>> group = groups.get(0);
        assertEquals(List.of("arn:aws:sqs:us-east-1:000000000000:q1"), group.get("Queue"));
        assertEquals(List.of("s3:ObjectCreated:Put"), group.get("Event"));
        assertEquals(List.of(".png"), group.get("suffix"));
    }

    @Test
    void extractGroupsMultiMultipleConfigsWithDifferentFilters() {
        String xml = """
                <NotificationConfiguration>
                  <QueueConfiguration>
                    <Queue>arn:aws:sqs:us-east-1:000000000000:q1</Queue>
                    <Event>s3:ObjectCreated:*</Event>
                    <Filter>
                      <S3Key>
                        <FilterRule><Name>prefix</Name><Value>images/</Value></FilterRule>
                      </S3Key>
                    </Filter>
                  </QueueConfiguration>
                  <QueueConfiguration>
                    <Queue>arn:aws:sqs:us-east-1:000000000000:q2</Queue>
                    <Event>s3:ObjectRemoved:*</Event>
                    <Filter>
                      <S3Key>
                        <FilterRule><Name>suffix</Name><Value>.log</Value></FilterRule>
                      </S3Key>
                    </Filter>
                  </QueueConfiguration>
                </NotificationConfiguration>
                """;

        List<Map<String, List<String>>> groups =
                XmlParser.extractGroupsMulti(xml, "QueueConfiguration");

        assertEquals(2, groups.size());
        assertEquals(List.of("images/"), groups.get(0).get("prefix"));
        assertNull(groups.get(0).get("suffix"));
        assertNull(groups.get(1).get("prefix"));
        assertEquals(List.of(".log"), groups.get(1).get("suffix"));
    }
}
