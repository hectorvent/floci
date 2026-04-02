package io.github.hectorvent.floci.services.s3;

import io.github.hectorvent.floci.services.s3.model.FilterRule;
import io.github.hectorvent.floci.services.s3.model.NotificationConfiguration;
import io.github.hectorvent.floci.services.s3.model.QueueNotification;
import io.github.hectorvent.floci.services.s3.model.TopicNotification;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NotificationConfigurationParserTest {

    @Test
    void queueConfigWithFilter() {
        String xml = """
                <NotificationConfiguration xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
                  <QueueConfiguration>
                    <Id>my-notif</Id>
                    <Queue>arn:aws:sqs:us-east-1:000000000000:test-queue</Queue>
                    <Event>s3:ObjectCreated:*</Event>
                    <Filter>
                      <S3Key>
                        <FilterRule>
                          <Name>prefix</Name>
                          <Value>incoming/</Value>
                        </FilterRule>
                      </S3Key>
                    </Filter>
                  </QueueConfiguration>
                </NotificationConfiguration>
                """;

        NotificationConfiguration config = NotificationConfigurationParser.parse(xml);

        assertEquals(1, config.getQueueConfigurations().size());
        QueueNotification qn = config.getQueueConfigurations().get(0);
        assertEquals("my-notif", qn.id());
        assertEquals("arn:aws:sqs:us-east-1:000000000000:test-queue", qn.queueArn());
        assertEquals(List.of("s3:ObjectCreated:*"), qn.events());
        assertEquals(1, qn.filterRules().size());
        assertEquals("prefix", qn.filterRules().get(0).name());
        assertEquals("incoming/", qn.filterRules().get(0).value());
    }

    @Test
    void filterBeforeQueueElementOrder() {
        String xml = """
                <NotificationConfiguration xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
                  <QueueConfiguration>
                    <Id>filter-first</Id>
                    <Filter>
                      <S3Key>
                        <FilterRule>
                          <Name>suffix</Name>
                          <Value>.csv</Value>
                        </FilterRule>
                      </S3Key>
                    </Filter>
                    <Queue>arn:aws:sqs:us-east-1:000000000000:csv-queue</Queue>
                    <Event>s3:ObjectCreated:Put</Event>
                  </QueueConfiguration>
                </NotificationConfiguration>
                """;

        NotificationConfiguration config = NotificationConfigurationParser.parse(xml);

        assertEquals(1, config.getQueueConfigurations().size());
        QueueNotification qn = config.getQueueConfigurations().get(0);
        assertEquals("arn:aws:sqs:us-east-1:000000000000:csv-queue", qn.queueArn());
        assertEquals(List.of("s3:ObjectCreated:Put"), qn.events());
        assertEquals("suffix", qn.filterRules().get(0).name());
        assertEquals(".csv", qn.filterRules().get(0).value());
    }

    @Test
    void queueConfigWithoutFilter() {
        String xml = """
                <NotificationConfiguration xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
                  <QueueConfiguration>
                    <Id>no-filter</Id>
                    <Queue>arn:aws:sqs:us-east-1:000000000000:all-queue</Queue>
                    <Event>s3:ObjectCreated:*</Event>
                  </QueueConfiguration>
                </NotificationConfiguration>
                """;

        NotificationConfiguration config = NotificationConfigurationParser.parse(xml);

        assertEquals(1, config.getQueueConfigurations().size());
        QueueNotification qn = config.getQueueConfigurations().get(0);
        assertEquals("arn:aws:sqs:us-east-1:000000000000:all-queue", qn.queueArn());
        assertTrue(qn.filterRules().isEmpty());
        assertTrue(qn.matchesKey("anything"));
    }

    @Test
    void topicConfigWithFilter() {
        String xml = """
                <NotificationConfiguration xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
                  <TopicConfiguration>
                    <Id>topic-notif</Id>
                    <Topic>arn:aws:sns:us-east-1:000000000000:my-topic</Topic>
                    <Event>s3:ObjectRemoved:*</Event>
                    <Filter>
                      <S3Key>
                        <FilterRule>
                          <Name>prefix</Name>
                          <Value>logs/</Value>
                        </FilterRule>
                      </S3Key>
                    </Filter>
                  </TopicConfiguration>
                </NotificationConfiguration>
                """;

        NotificationConfiguration config = NotificationConfigurationParser.parse(xml);

        assertTrue(config.getQueueConfigurations().isEmpty());
        assertEquals(1, config.getTopicConfigurations().size());
        TopicNotification tn = config.getTopicConfigurations().get(0);
        assertEquals("arn:aws:sns:us-east-1:000000000000:my-topic", tn.topicArn());
        assertEquals(List.of("s3:ObjectRemoved:*"), tn.events());
        assertEquals("prefix", tn.filterRules().get(0).name());
        assertEquals("logs/", tn.filterRules().get(0).value());
    }

    @Test
    void multipleConfigurations() {
        String xml = """
                <NotificationConfiguration xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
                  <QueueConfiguration>
                    <Id>q1</Id>
                    <Queue>arn:aws:sqs:us-east-1:000000000000:queue-1</Queue>
                    <Event>s3:ObjectCreated:*</Event>
                  </QueueConfiguration>
                  <QueueConfiguration>
                    <Id>q2</Id>
                    <Queue>arn:aws:sqs:us-east-1:000000000000:queue-2</Queue>
                    <Event>s3:ObjectRemoved:*</Event>
                    <Filter>
                      <S3Key>
                        <FilterRule><Name>prefix</Name><Value>tmp/</Value></FilterRule>
                      </S3Key>
                    </Filter>
                  </QueueConfiguration>
                  <TopicConfiguration>
                    <Id>t1</Id>
                    <Topic>arn:aws:sns:us-east-1:000000000000:topic-1</Topic>
                    <Event>s3:ObjectCreated:Put</Event>
                  </TopicConfiguration>
                </NotificationConfiguration>
                """;

        NotificationConfiguration config = NotificationConfigurationParser.parse(xml);

        assertEquals(2, config.getQueueConfigurations().size());
        assertEquals(1, config.getTopicConfigurations().size());

        assertEquals("queue-1", config.getQueueConfigurations().get(0).queueArn().substring(
                config.getQueueConfigurations().get(0).queueArn().lastIndexOf(':') + 1));
        assertTrue(config.getQueueConfigurations().get(0).filterRules().isEmpty());

        assertEquals(1, config.getQueueConfigurations().get(1).filterRules().size());
        assertEquals("prefix", config.getQueueConfigurations().get(1).filterRules().get(0).name());
    }

    @Test
    void multipleFilterRules() {
        String xml = """
                <NotificationConfiguration xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
                  <QueueConfiguration>
                    <Id>multi-rule</Id>
                    <Queue>arn:aws:sqs:us-east-1:000000000000:filtered</Queue>
                    <Event>s3:ObjectCreated:*</Event>
                    <Filter>
                      <S3Key>
                        <FilterRule>
                          <Name>prefix</Name>
                          <Value>images/</Value>
                        </FilterRule>
                        <FilterRule>
                          <Name>suffix</Name>
                          <Value>.jpg</Value>
                        </FilterRule>
                      </S3Key>
                    </Filter>
                  </QueueConfiguration>
                </NotificationConfiguration>
                """;

        NotificationConfiguration config = NotificationConfigurationParser.parse(xml);

        QueueNotification qn = config.getQueueConfigurations().get(0);
        assertEquals(2, qn.filterRules().size());
        assertEquals("prefix", qn.filterRules().get(0).name());
        assertEquals("images/", qn.filterRules().get(0).value());
        assertEquals("suffix", qn.filterRules().get(1).name());
        assertEquals(".jpg", qn.filterRules().get(1).value());

        assertTrue(qn.matchesKey("images/photo.jpg"));
        assertFalse(qn.matchesKey("images/photo.png"));
        assertFalse(qn.matchesKey("docs/file.jpg"));
    }

    @Test
    void emptyAndNullXml() {
        NotificationConfiguration empty = NotificationConfigurationParser.parse("");
        assertTrue(empty.getQueueConfigurations().isEmpty());
        assertTrue(empty.getTopicConfigurations().isEmpty());

        NotificationConfiguration nullConfig = NotificationConfigurationParser.parse(null);
        assertTrue(nullConfig.getQueueConfigurations().isEmpty());
    }

    @Test
    void missingRequiredFieldsDropsConfig() {
        // No Queue element → config should be dropped
        String noQueue = """
                <NotificationConfiguration>
                  <QueueConfiguration>
                    <Id>broken</Id>
                    <Event>s3:ObjectCreated:*</Event>
                  </QueueConfiguration>
                </NotificationConfiguration>
                """;
        assertTrue(NotificationConfigurationParser.parse(noQueue).getQueueConfigurations().isEmpty());

        // No Event element → config should be dropped
        String noEvent = """
                <NotificationConfiguration>
                  <QueueConfiguration>
                    <Id>broken</Id>
                    <Queue>arn:aws:sqs:us-east-1:000000000000:q</Queue>
                  </QueueConfiguration>
                </NotificationConfiguration>
                """;
        assertTrue(NotificationConfigurationParser.parse(noEvent).getQueueConfigurations().isEmpty());
    }

    @Test
    void unknownElementsAreSkipped() {
        String xml = """
                <NotificationConfiguration xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
                  <QueueConfiguration>
                    <Id>with-unknown</Id>
                    <Queue>arn:aws:sqs:us-east-1:000000000000:q</Queue>
                    <Event>s3:ObjectCreated:*</Event>
                    <SomeFutureElement>
                      <Nested>deep</Nested>
                    </SomeFutureElement>
                    <Filter>
                      <S3Key>
                        <FilterRule>
                          <Name>prefix</Name>
                          <Value>ok/</Value>
                        </FilterRule>
                      </S3Key>
                    </Filter>
                  </QueueConfiguration>
                </NotificationConfiguration>
                """;

        NotificationConfiguration config = NotificationConfigurationParser.parse(xml);

        assertEquals(1, config.getQueueConfigurations().size());
        QueueNotification qn = config.getQueueConfigurations().get(0);
        assertEquals("arn:aws:sqs:us-east-1:000000000000:q", qn.queueArn());
        assertEquals(1, qn.filterRules().size());
        assertEquals("prefix", qn.filterRules().get(0).name());
    }

    // --- matchesKey (QueueNotification / TopicNotification) ---

    @Test
    void matchesKeyWithNullFilterRulesFromJacksonDeserialization() {
        // Jackson uses the canonical 4-arg constructor; old persisted data
        // without filterRules deserializes as null. matchesKey must not throw.
        var qn = new QueueNotification("id", "arn:aws:sqs:us-east-1:000000000000:q",
                List.of("s3:ObjectCreated:*"), null);
        assertTrue(qn.matchesKey("anything"));

        var tn = new TopicNotification("id", "arn:aws:sns:us-east-1:000000000000:t",
                List.of("s3:ObjectCreated:*"), null);
        assertTrue(tn.matchesKey("anything"));
    }

    @Test
    void matchesKeyWithEmptyFilterRulesMatchesAll() {
        var qn = new QueueNotification("id", "arn", List.of("s3:ObjectCreated:*"));
        assertTrue(qn.matchesKey("anything"));
    }

    @Test
    void matchesKeyEnforcesAllRules() {
        var qn = new QueueNotification("id", "arn", List.of("s3:ObjectCreated:*"),
                List.of(new FilterRule("prefix", "images/"), new FilterRule("suffix", ".jpg")));
        assertTrue(qn.matchesKey("images/photo.jpg"));
        assertFalse(qn.matchesKey("images/photo.png")); // suffix fails
        assertFalse(qn.matchesKey("docs/photo.jpg"));   // prefix fails
    }

    // --- FilterRule.matches ---

    @Test
    void filterRulePrefixMatch() {
        FilterRule rule = new FilterRule("prefix", "images/");
        assertTrue(rule.matches("images/photo.jpg"));
        assertFalse(rule.matches("docs/file.txt"));
        assertFalse(rule.matches(null));
    }

    @Test
    void filterRuleSuffixMatch() {
        FilterRule rule = new FilterRule("suffix", ".jpg");
        assertTrue(rule.matches("images/photo.jpg"));
        assertFalse(rule.matches("images/photo.png"));
    }

    @Test
    void filterRuleUnknownNameDoesNotMatch() {
        FilterRule rule = new FilterRule("extension", ".jpg");
        assertFalse(rule.matches("photo.jpg"));
    }

    @Test
    void filterRuleNameIsCaseInsensitive() {
        FilterRule prefix = new FilterRule("Prefix", "images/");
        assertTrue(prefix.matches("images/photo.jpg"));

        FilterRule suffix = new FilterRule("SUFFIX", ".jpg");
        assertTrue(suffix.matches("photo.jpg"));
    }
}
