package io.github.hectorvent.floci.services.cloudwatch.metricstreams;

import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.RegionResolver;
import io.github.hectorvent.floci.core.storage.InMemoryStorage;
import io.github.hectorvent.floci.services.cloudwatch.metricstreams.model.MetricStream;
import io.github.hectorvent.floci.services.cloudwatch.metricstreams.model.MetricStreamFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudWatchMetricStreamsServiceTest {

    private static final String REGION = "us-east-1";

    private CloudWatchMetricStreamsService service;

    @BeforeEach
    void setUp() {
        service = new CloudWatchMetricStreamsService(
                new InMemoryStorage<>(), new RegionResolver(REGION, "000000000000"));
    }

    private static MetricStream stream(String name) {
        MetricStream stream = new MetricStream();
        stream.setName(name);
        stream.setFirehoseArn("arn:aws:firehose:us-east-1:000000000000:deliverystream/metrics");
        stream.setRoleArn("arn:aws:iam::000000000000:role/metric-stream-role");
        stream.setOutputFormat("json");
        return stream;
    }

    @Test
    void putStoresTheDefinitionRunningWithAnArn() {
        MetricStream stored = service.putMetricStream(stream("ops"), REGION);

        assertEquals("arn:aws:cloudwatch:us-east-1:000000000000:metric-stream/ops", stored.getArn());
        assertEquals(MetricStream.STATE_RUNNING, stored.getState());
        assertTrue(stored.getCreationDate() > 0);
        assertEquals(stored.getCreationDate(), stored.getLastUpdateDate());
        assertEquals("json", service.getMetricStream("ops", REGION).getOutputFormat());
    }

    @Test
    void putOnExistingNameUpdatesTheDefinitionAndKeepsCreationStateAndTags() {
        MetricStream first = stream("ops");
        first.setTags(new LinkedHashMap<>(Map.of("env", "dev")));
        long createdAt = service.putMetricStream(first, REGION).getCreationDate();
        service.stopMetricStreams(List.of("ops"), REGION);

        MetricStream update = stream("ops");
        update.setOutputFormat("opentelemetry1.0");
        update.setTags(new LinkedHashMap<>(Map.of("env", "prod")));
        service.putMetricStream(update, REGION);

        MetricStream stored = service.getMetricStream("ops", REGION);
        assertEquals(createdAt, stored.getCreationDate());
        assertEquals("opentelemetry1.0", stored.getOutputFormat());
        assertEquals(MetricStream.STATE_STOPPED, stored.getState());
        assertEquals("dev", stored.getTags().get("env"));
        assertEquals(1, service.listMetricStreams(REGION).size());
    }

    @Test
    void putRequiresTheDocumentedParameters() {
        MetricStream missingRole = stream("ops");
        missingRole.setRoleArn(null);
        AwsException e = assertThrows(AwsException.class, () -> service.putMetricStream(missingRole, REGION));
        assertEquals("MissingParameter", e.getErrorCode());
        assertEquals(400, e.getHttpStatus());

        MetricStream badFormat = stream("ops");
        badFormat.setOutputFormat("csv");
        assertEquals("InvalidParameterValue",
                assertThrows(AwsException.class, () -> service.putMetricStream(badFormat, REGION)).getErrorCode());
    }

    @Test
    void putRejectsIncludeAndExcludeFiltersTogether() {
        MetricStream both = stream("ops");
        both.setIncludeFilters(List.of(new MetricStreamFilter("AWS/EC2", List.of())));
        both.setExcludeFilters(List.of(new MetricStreamFilter("AWS/S3", List.of())));

        AwsException e = assertThrows(AwsException.class, () -> service.putMetricStream(both, REGION));
        assertEquals("InvalidParameterCombination", e.getErrorCode());
    }

    @Test
    void getMissingStreamThrowsResourceNotFoundException() {
        AwsException e = assertThrows(AwsException.class, () -> service.getMetricStream("nope", REGION));
        assertEquals("ResourceNotFoundException", e.getErrorCode());
        assertEquals(404, e.getHttpStatus());
    }

    @Test
    void listIsScopedToTheRegionAndSortedByName() {
        service.putMetricStream(stream("zeta"), REGION);
        service.putMetricStream(stream("alpha"), REGION);
        service.putMetricStream(stream("other-region"), "eu-west-1");

        List<MetricStream> streams = service.listMetricStreams(REGION);
        assertEquals(List.of("alpha", "zeta"), streams.stream().map(MetricStream::getName).toList());
    }

    @Test
    void stopAndStartToggleTheState() {
        service.putMetricStream(stream("ops"), REGION);

        service.stopMetricStreams(List.of("ops"), REGION);
        assertEquals(MetricStream.STATE_STOPPED, service.getMetricStream("ops", REGION).getState());

        service.startMetricStreams(List.of("ops"), REGION);
        assertEquals(MetricStream.STATE_RUNNING, service.getMetricStream("ops", REGION).getState());
    }

    @Test
    void stopRequiresNamesAndReportsAMissingStream() {
        assertEquals("MissingParameter",
                assertThrows(AwsException.class, () -> service.stopMetricStreams(List.of(), REGION)).getErrorCode());
        assertEquals("ResourceNotFoundException",
                assertThrows(AwsException.class, () -> service.stopMetricStreams(List.of("nope"), REGION)).getErrorCode());
    }

    @Test
    void deleteRemovesTheStreamAndToleratesAMissingOne() {
        service.putMetricStream(stream("ops"), REGION);

        service.deleteMetricStream("ops", REGION);
        service.deleteMetricStream("ops", REGION);

        assertTrue(service.listMetricStreams(REGION).isEmpty());
    }

    @Test
    void tagsRoundTripByArn() {
        MetricStream tagged = stream("ops");
        tagged.setTags(new LinkedHashMap<>(Map.of("team", "platform")));
        String arn = service.putMetricStream(tagged, REGION).getArn();

        assertTrue(CloudWatchMetricStreamsService.isMetricStreamArn(arn));
        assertFalse(CloudWatchMetricStreamsService.isMetricStreamArn(
                "arn:aws:cloudwatch:us-east-1:000000000000:alarm:ops"));
        assertEquals("platform", service.listTagsForResource(arn, REGION).get("team"));

        service.tagResource(arn, Map.of("owner", "ops"), REGION);
        assertEquals("ops", service.listTagsForResource(arn, REGION).get("owner"));

        service.untagResource(arn, List.of("team"), REGION);
        assertFalse(service.listTagsForResource(arn, REGION).containsKey("team"));
    }
}
