package io.github.hectorvent.floci.services.cloudwatch.metricstreams;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.RegionResolver;
import io.github.hectorvent.floci.core.storage.StorageBackend;
import io.github.hectorvent.floci.core.storage.StorageFactory;
import io.github.hectorvent.floci.services.cloudwatch.metricstreams.model.MetricStream;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * CloudWatch metric streams. A stream is pure metadata here: Floci stores the definition and
 * its running or stopped state and never delivers metrics to the Firehose delivery stream it
 * names, so there is no backing behaviour behind the record.
 */
@ApplicationScoped
public class CloudWatchMetricStreamsService {

    private static final Logger LOG = Logger.getLogger(CloudWatchMetricStreamsService.class);

    /** The OutputFormat values PutMetricStream documents. */
    private static final Set<String> OUTPUT_FORMATS = Set.of("json", "opentelemetry0.7", "opentelemetry1.0");

    private final StorageBackend<String, MetricStream> streamStore;
    private final RegionResolver regionResolver;

    @Inject
    public CloudWatchMetricStreamsService(StorageFactory storageFactory, RegionResolver regionResolver) {
        this.streamStore = storageFactory.create("cloudwatchmetrics", "cwmetricstreams.json",
                new TypeReference<Map<String, MetricStream>>() {});
        this.regionResolver = regionResolver;
    }

    // Public rather than package-private so handler tests in the metrics package can build
    // the service over an InMemoryStorage without standing up Quarkus.
    public CloudWatchMetricStreamsService(StorageBackend<String, MetricStream> streamStore,
                                          RegionResolver regionResolver) {
        this.streamStore = streamStore;
        this.regionResolver = regionResolver;
    }

    /**
     * Creates the stream, or updates the definition when the name is already taken.
     *
     * <p>An update keeps the creation date, the state and the tags of the existing stream. AWS
     * documents Tags on PutMetricStream as applying to a new stream only, so a Put that updates
     * an existing one ignores the tags on the request.
     */
    public MetricStream putMetricStream(MetricStream stream, String region) {
        requireParameter(stream.getName(), "Name");
        requireParameter(stream.getFirehoseArn(), "FirehoseArn");
        requireParameter(stream.getRoleArn(), "RoleArn");
        requireParameter(stream.getOutputFormat(), "OutputFormat");
        if (!OUTPUT_FORMATS.contains(stream.getOutputFormat())) {
            throw new AwsException("InvalidParameterValue",
                    "OutputFormat must be one of json, opentelemetry0.7 or opentelemetry1.0.", 400);
        }
        if (!stream.getIncludeFilters().isEmpty() && !stream.getExcludeFilters().isEmpty()) {
            throw new AwsException("InvalidParameterCombination",
                    "IncludeFilters and ExcludeFilters cannot both be specified.", 400);
        }

        long now = Instant.now().getEpochSecond();
        MetricStream existing = streamStore.get(key(region, stream.getName())).orElse(null);
        if (existing != null) {
            stream.setCreationDate(existing.getCreationDate());
            stream.setState(existing.getState());
            stream.setTags(new LinkedHashMap<>(existing.getTags()));
        } else {
            stream.setCreationDate(now);
            stream.setState(MetricStream.STATE_RUNNING);
        }
        stream.setLastUpdateDate(now);
        stream.setArn(regionResolver.buildArn("cloudwatch", region, "metric-stream/" + stream.getName()));
        streamStore.put(key(region, stream.getName()), stream);
        LOG.debugv("PutMetricStream: {0} in {1}", stream.getName(), region);
        return stream;
    }

    public MetricStream getMetricStream(String name, String region) {
        requireParameter(name, "Name");
        return streamStore.get(key(region, name)).orElseThrow(() -> notFound(name));
    }

    /** Deleting a stream that is not there is a no-op, which is how AWS answers it. */
    public void deleteMetricStream(String name, String region) {
        requireParameter(name, "Name");
        streamStore.delete(key(region, name));
        LOG.debugv("DeleteMetricStream: {0} in {1}", name, region);
    }

    public List<MetricStream> listMetricStreams(String region) {
        return streamStore.scan(k -> k.startsWith(region + "::")).stream()
                .sorted(Comparator.comparing(MetricStream::getName))
                .toList();
    }

    public void startMetricStreams(List<String> names, String region) {
        setState(names, MetricStream.STATE_RUNNING, region);
    }

    public void stopMetricStreams(List<String> names, String region) {
        setState(names, MetricStream.STATE_STOPPED, region);
    }

    private void setState(List<String> names, String state, String region) {
        if (names == null || names.isEmpty()) {
            throw new AwsException("MissingParameter", "The parameter Names is required.", 400);
        }
        long now = Instant.now().getEpochSecond();
        for (String name : names) {
            MetricStream stream = getMetricStream(name, region);
            stream.setState(state);
            stream.setLastUpdateDate(now);
            streamStore.put(key(region, name), stream);
        }
        LOG.debugv("Metric streams {0} in {1} are now {2}", names, region, state);
    }

    /**
     * Whether an ARN names a metric stream. CloudWatch's tag operations take one ARN for every
     * taggable resource it owns, so the handler needs to know which service should answer.
     */
    public static boolean isMetricStreamArn(String resourceArn) {
        return resourceArn != null && resourceArn.contains(":metric-stream/");
    }

    public Map<String, String> listTagsForResource(String resourceArn, String region) {
        return findByArn(resourceArn, region).map(MetricStream::getTags).orElse(Map.of());
    }

    public void tagResource(String resourceArn, Map<String, String> tags, String region) {
        findByArn(resourceArn, region).ifPresent(stream -> {
            stream.getTags().putAll(tags);
            streamStore.put(key(region, stream.getName()), stream);
        });
    }

    public void untagResource(String resourceArn, List<String> tagKeys, String region) {
        findByArn(resourceArn, region).ifPresent(stream -> {
            tagKeys.forEach(stream.getTags()::remove);
            streamStore.put(key(region, stream.getName()), stream);
        });
    }

    private Optional<MetricStream> findByArn(String resourceArn, String region) {
        return streamStore.scan(k -> k.startsWith(region + "::")).stream()
                .filter(s -> resourceArn.equals(s.getArn()))
                .findFirst();
    }

    private static void requireParameter(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new AwsException("MissingParameter", "The parameter " + name + " is required.", 400);
        }
    }

    private static String key(String region, String name) {
        return region + "::" + name;
    }

    private static AwsException notFound(String name) {
        return new AwsException("ResourceNotFoundException",
                "Metric stream " + name + " does not exist.", 404);
    }
}
