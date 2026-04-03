package io.github.hectorvent.floci.services.cloudwatch.logs;

import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.RegionResolver;
import io.github.hectorvent.floci.core.storage.InMemoryStorage;
import io.github.hectorvent.floci.services.cloudwatch.logs.model.LogEvent;
import io.github.hectorvent.floci.services.cloudwatch.logs.model.LogGroup;
import io.github.hectorvent.floci.services.cloudwatch.logs.model.LogStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CloudWatchLogsServiceTest {

    private static final String REGION = "us-east-1";

    private CloudWatchLogsService service;

    @BeforeEach
    void setUp() {
        service = new CloudWatchLogsService(
                new InMemoryStorage<>(),
                new InMemoryStorage<>(),
                new InMemoryStorage<>(),
                10000,
                new RegionResolver("us-east-1", "000000000000")
        );
    }

    // ──────────────────────────── Log Groups ────────────────────────────

    @Test
    void createLogGroup() {
        service.createLogGroup("/app/logs", null, null, REGION);

        List<LogGroup> groups = service.describeLogGroups(null, REGION);
        assertEquals(1, groups.size());
        assertEquals("/app/logs", groups.getFirst().getLogGroupName());
    }

    @Test
    void createLogGroupDuplicateThrows() {
        service.createLogGroup("/app/logs", null, null, REGION);
        assertThrows(AwsException.class, () ->
                service.createLogGroup("/app/logs", null, null, REGION));
    }

    @Test
    void createLogGroupBlankNameThrows() {
        assertThrows(AwsException.class, () ->
                service.createLogGroup("", null, null, REGION));
    }

    @Test
    void deleteLogGroup() {
        service.createLogGroup("/app/logs", null, null, REGION);
        service.deleteLogGroup("/app/logs", REGION);

        assertTrue(service.describeLogGroups(null, REGION).isEmpty());
    }

    @Test
    void deleteLogGroupNotFoundThrows() {
        assertThrows(AwsException.class, () ->
                service.deleteLogGroup("/missing", REGION));
    }

    @Test
    void describeLogGroupsWithPrefix() {
        service.createLogGroup("/app/alpha", null, null, REGION);
        service.createLogGroup("/app/beta", null, null, REGION);
        service.createLogGroup("/other/logs", null, null, REGION);

        List<LogGroup> result = service.describeLogGroups("/app", REGION);
        assertEquals(2, result.size());
    }

    @Test
    void putAndDeleteRetentionPolicy() {
        service.createLogGroup("/app/logs", null, null, REGION);
        service.putRetentionPolicy("/app/logs", 30, REGION);

        LogGroup group = service.describeLogGroups("/app/logs", REGION).getFirst();
        assertEquals(30, group.getRetentionInDays());

        service.deleteRetentionPolicy("/app/logs", REGION);
        group = service.describeLogGroups("/app/logs", REGION).getFirst();
        assertNull(group.getRetentionInDays());
    }

    @Test
    void tagAndUntagLogGroup() {
        service.createLogGroup("/app/logs", null, Map.of("env", "prod"), REGION);
        service.tagLogGroup("/app/logs", Map.of("team", "platform"), REGION);

        Map<String, String> tags = service.listTagsLogGroup("/app/logs", REGION);
        assertEquals("prod", tags.get("env"));
        assertEquals("platform", tags.get("team"));

        service.untagLogGroup("/app/logs", List.of("env"), REGION);
        tags = service.listTagsLogGroup("/app/logs", REGION);
        assertFalse(tags.containsKey("env"));
    }

    // ──────────────────────────── Log Streams ────────────────────────────

    @Test
    void createLogStream() {
        service.createLogGroup("/app/logs", null, null, REGION);
        service.createLogStream("/app/logs", "stream-1", REGION);

        List<LogStream> streams = service.describeLogStreams("/app/logs", null, REGION);
        assertEquals(1, streams.size());
        assertEquals("stream-1", streams.getFirst().getLogStreamName());
    }

    @Test
    void createLogStreamForNonExistentGroupThrows() {
        assertThrows(AwsException.class, () ->
                service.createLogStream("/missing", "stream-1", REGION));
    }

    @Test
    void createLogStreamDuplicateThrows() {
        service.createLogGroup("/app/logs", null, null, REGION);
        service.createLogStream("/app/logs", "stream-1", REGION);
        assertThrows(AwsException.class, () ->
                service.createLogStream("/app/logs", "stream-1", REGION));
    }

    @Test
    void deleteLogStream() {
        service.createLogGroup("/app/logs", null, null, REGION);
        service.createLogStream("/app/logs", "stream-1", REGION);
        service.deleteLogStream("/app/logs", "stream-1", REGION);

        assertTrue(service.describeLogStreams("/app/logs", null, REGION).isEmpty());
    }

    @Test
    void deleteLogGroupCascadesStreamsAndEvents() {
        service.createLogGroup("/app/logs", null, null, REGION);
        service.createLogStream("/app/logs", "stream-1", REGION);
        service.putLogEvents("/app/logs", "stream-1",
                List.of(Map.of("timestamp", System.currentTimeMillis(), "message", "hello")), REGION);

        service.deleteLogGroup("/app/logs", REGION);
        assertTrue(service.describeLogGroups(null, REGION).isEmpty());
    }

    // ──────────────────────────── Log Events ────────────────────────────

    @Test
    void putAndGetLogEvents() {
        service.createLogGroup("/app/logs", null, null, REGION);
        service.createLogStream("/app/logs", "stream-1", REGION);

        long now = System.currentTimeMillis();
        service.putLogEvents("/app/logs", "stream-1", List.of(
                Map.of("timestamp", now, "message", "first"),
                Map.of("timestamp", now + 1, "message", "second")
        ), REGION);

        CloudWatchLogsService.LogEventsResult result = service.getLogEvents(
                "/app/logs", "stream-1", null, null, 100, true, null, REGION);
        assertEquals(2, result.events().size());
        assertEquals("first", result.events().get(0).getMessage());
        assertEquals("second", result.events().get(1).getMessage());
    }

    @Test
    void getLogEventsWithTimeRange() {
        service.createLogGroup("/app/logs", null, null, REGION);
        service.createLogStream("/app/logs", "stream-1", REGION);

        long base = System.currentTimeMillis();
        service.putLogEvents("/app/logs", "stream-1", List.of(
                Map.of("timestamp", base, "message", "old"),
                Map.of("timestamp", base + 10000, "message", "new")
        ), REGION);

        CloudWatchLogsService.LogEventsResult result = service.getLogEvents(
                "/app/logs", "stream-1", base + 5000, null, 100, true, null, REGION);
        assertEquals(1, result.events().size());
        assertEquals("new", result.events().getFirst().getMessage());
    }

    @Test
    void filterLogEvents() {
        service.createLogGroup("/app/logs", null, null, REGION);
        service.createLogStream("/app/logs", "stream-1", REGION);

        long now = System.currentTimeMillis();
        service.putLogEvents("/app/logs", "stream-1", List.of(
                Map.of("timestamp", now, "message", "ERROR: something failed"),
                Map.of("timestamp", now + 1, "message", "INFO: all good"),
                Map.of("timestamp", now + 2, "message", "ERROR: another failure")
        ), REGION);

        CloudWatchLogsService.FilteredLogEventsResult result = service.filterLogEvents(
                "/app/logs", null, null, null, "ERROR", 100, REGION);
        assertEquals(2, result.events().size());
        assertTrue(result.events().stream().allMatch(e -> e.getMessage().contains("ERROR")));
    }

    @Test
    void filterLogEventsNoPattern() {
        service.createLogGroup("/app/logs", null, null, REGION);
        service.createLogStream("/app/logs", "stream-1", REGION);

        long now = System.currentTimeMillis();
        service.putLogEvents("/app/logs", "stream-1", List.of(
                Map.of("timestamp", now, "message", "msg1"),
                Map.of("timestamp", now + 1, "message", "msg2")
        ), REGION);

        CloudWatchLogsService.FilteredLogEventsResult result = service.filterLogEvents(
                "/app/logs", null, null, null, null, 100, REGION);
        assertEquals(2, result.events().size());
    }

    @Test
    void putLogEventsUpdatesStreamMetadata() {
        service.createLogGroup("/app/logs", null, null, REGION);
        service.createLogStream("/app/logs", "stream-1", REGION);

        long now = System.currentTimeMillis();
        service.putLogEvents("/app/logs", "stream-1",
                List.of(Map.of("timestamp", now, "message", "test")), REGION);

        List<LogStream> streams = service.describeLogStreams("/app/logs", null, REGION);
        LogStream stream = streams.getFirst();
        assertEquals(now, stream.getFirstEventTimestamp());
        assertEquals(now, stream.getLastEventTimestamp());
        assertNotNull(stream.getLastIngestionTime());
    }

    @Test
    void maxEventsPerQueryIsRespected() {
        CloudWatchLogsService limitedService = new CloudWatchLogsService(
                new InMemoryStorage<>(),
                new InMemoryStorage<>(),
                new InMemoryStorage<>(),
                2,
                new RegionResolver("us-east-1", "000000000000")
        );

        limitedService.createLogGroup("/app/logs", null, null, REGION);
        limitedService.createLogStream("/app/logs", "stream-1", REGION);

        long now = System.currentTimeMillis();
        limitedService.putLogEvents("/app/logs", "stream-1", List.of(
                Map.of("timestamp", now, "message", "a"),
                Map.of("timestamp", now + 1, "message", "b"),
                Map.of("timestamp", now + 2, "message", "c")
        ), REGION);

        CloudWatchLogsService.LogEventsResult result = limitedService.getLogEvents(
                "/app/logs", "stream-1", null, null, 100, true, null, REGION);
        assertEquals(2, result.events().size());
    }

    // ──────────────────────────── GetLogEvents pagination (issue #90) ────────────────────────────

    private void putEvents(String group, String stream, long baseTs, int count) {
        List<Map<String, Object>> events = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            events.add(Map.of("timestamp", baseTs + i, "message", "msg-" + i));
        }
        service.putLogEvents(group, stream, events, REGION);
    }

    @Test
    void getLogEventsInitialTokensEncodePosition() {
        service.createLogGroup("/app/logs", null, null, REGION);
        service.createLogStream("/app/logs", "stream-1", REGION);
        putEvents("/app/logs", "stream-1", System.currentTimeMillis(), 5);

        CloudWatchLogsService.LogEventsResult result =
                service.getLogEvents("/app/logs", "stream-1", null, null, 100, true, null, REGION);

        assertEquals(5, result.events().size());
        assertEquals("f/5", result.nextForwardToken());
        assertEquals("b/0", result.nextBackwardToken());
    }

    @Test
    void getLogEventsForwardPaginationContinues() {
        service.createLogGroup("/app/logs", null, null, REGION);
        service.createLogStream("/app/logs", "stream-1", REGION);
        long base = System.currentTimeMillis();
        putEvents("/app/logs", "stream-1", base, 5);

        CloudWatchLogsService.LogEventsResult page1 =
                service.getLogEvents("/app/logs", "stream-1", null, null, 3, true, null, REGION);
        assertEquals(3, page1.events().size());
        assertEquals("msg-0", page1.events().get(0).getMessage());
        assertEquals("f/3", page1.nextForwardToken());

        CloudWatchLogsService.LogEventsResult page2 =
                service.getLogEvents("/app/logs", "stream-1", null, null, 3, true, page1.nextForwardToken(), REGION);
        assertEquals(2, page2.events().size());
        assertEquals("msg-3", page2.events().get(0).getMessage());
        assertEquals("f/5", page2.nextForwardToken());
    }

    @Test
    void getLogEventsAtEndOfStreamEchosToken() {
        service.createLogGroup("/app/logs", null, null, REGION);
        service.createLogStream("/app/logs", "stream-1", REGION);
        putEvents("/app/logs", "stream-1", System.currentTimeMillis(), 3);

        // Simulate the SDK sending back the last returned forward token
        CloudWatchLogsService.LogEventsResult atEnd =
                service.getLogEvents("/app/logs", "stream-1", null, null, 10, true, "f/3", REGION);

        assertEquals(0, atEnd.events().size());
        assertEquals("f/3", atEnd.nextForwardToken(), "token must echo back to signal end of stream");
    }

    @Test
    void getLogEventsStartFromTailWithNoToken() {
        service.createLogGroup("/app/logs", null, null, REGION);
        service.createLogStream("/app/logs", "stream-1", REGION);
        putEvents("/app/logs", "stream-1", System.currentTimeMillis(), 5);

        CloudWatchLogsService.LogEventsResult result =
                service.getLogEvents("/app/logs", "stream-1", null, null, 3, false, null, REGION);

        assertEquals(3, result.events().size());
        assertEquals("msg-2", result.events().get(0).getMessage());
        assertEquals("msg-4", result.events().get(2).getMessage());
        assertEquals("b/2", result.nextBackwardToken());
        assertEquals("f/5", result.nextForwardToken());
    }

    @Test
    void getLogEventsBackwardPaginationEchosTokenAtStart() {
        service.createLogGroup("/app/logs", null, null, REGION);
        service.createLogStream("/app/logs", "stream-1", REGION);
        putEvents("/app/logs", "stream-1", System.currentTimeMillis(), 3);

        // b/0 means we are already at the start — echoed back
        CloudWatchLogsService.LogEventsResult atStart =
                service.getLogEvents("/app/logs", "stream-1", null, null, 10, true, "b/0", REGION);

        assertEquals(0, atStart.events().size());
        assertEquals("b/0", atStart.nextBackwardToken(), "token must echo back to signal start of stream");
    }
}