package io.github.hectorvent.floci.services.scheduler;

import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.RegionResolver;
import io.github.hectorvent.floci.core.storage.StorageBackend;
import io.github.hectorvent.floci.core.storage.StorageFactory;
import io.github.hectorvent.floci.services.scheduler.model.ScheduleGroup;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Map;
import java.util.regex.Pattern;

@ApplicationScoped
public class SchedulerService {

    private static final Logger LOG = Logger.getLogger(SchedulerService.class);

    // AWS EventBridge Scheduler name constraints: [0-9a-zA-Z-_.]+, 1-64 chars.
    private static final Pattern NAME_PATTERN = Pattern.compile("[0-9a-zA-Z\\-_.]{1,64}");
    private static final String DEFAULT_GROUP = "default";

    private final StorageBackend<String, ScheduleGroup> groupStore;
    private final RegionResolver regionResolver;

    @Inject
    public SchedulerService(StorageFactory storageFactory, RegionResolver regionResolver) {
        this(
                storageFactory.create("scheduler", "scheduler-groups.json",
                        new TypeReference<Map<String, ScheduleGroup>>() {}),
                regionResolver
        );
    }

    SchedulerService(StorageBackend<String, ScheduleGroup> groupStore, RegionResolver regionResolver) {
        this.groupStore = groupStore;
        this.regionResolver = regionResolver;
    }

    // ──────────────────────────── Schedule Groups ────────────────────────────

    public ScheduleGroup getOrCreateDefaultGroup(String region) {
        String key = groupKey(region, DEFAULT_GROUP);
        return groupStore.get(key).orElseGet(() -> {
            Instant now = Instant.now();
            ScheduleGroup group = new ScheduleGroup(
                    DEFAULT_GROUP,
                    buildGroupArn(region, DEFAULT_GROUP),
                    "ACTIVE",
                    now,
                    now
            );
            groupStore.put(key, group);
            return group;
        });
    }

    public ScheduleGroup createScheduleGroup(String name, Map<String, String> tags, String region) {
        validateName(name);
        if (DEFAULT_GROUP.equals(name)) {
            throw new AwsException("ConflictException",
                    "ScheduleGroup already exists: " + name, 409);
        }
        String key = groupKey(region, name);
        if (groupStore.get(key).isPresent()) {
            throw new AwsException("ConflictException",
                    "ScheduleGroup already exists: " + name, 409);
        }
        Instant now = Instant.now();
        ScheduleGroup group = new ScheduleGroup(
                name,
                buildGroupArn(region, name),
                "ACTIVE",
                now,
                now
        );
        if (tags != null) {
            group.getTags().putAll(tags);
        }
        groupStore.put(key, group);
        LOG.infov("Created schedule group: {0} in region {1}", name, region);
        return group;
    }

    public ScheduleGroup getScheduleGroup(String name, String region) {
        String effectiveName = (name == null || name.isBlank()) ? DEFAULT_GROUP : name;
        if (DEFAULT_GROUP.equals(effectiveName)) {
            return getOrCreateDefaultGroup(region);
        }
        return groupStore.get(groupKey(region, effectiveName))
                .orElseThrow(() -> new AwsException("ResourceNotFoundException",
                        "ScheduleGroup not found: " + effectiveName, 404));
    }

    public void deleteScheduleGroup(String name, String region) {
        if (name == null || name.isBlank()) {
            throw new AwsException("ValidationException", "Name is required.", 400);
        }
        if (DEFAULT_GROUP.equals(name)) {
            throw new AwsException("ValidationException",
                    "Cannot delete the default schedule group.", 400);
        }
        String key = groupKey(region, name);
        groupStore.get(key)
                .orElseThrow(() -> new AwsException("ResourceNotFoundException",
                        "ScheduleGroup not found: " + name, 404));
        groupStore.delete(key);
        LOG.infov("Deleted schedule group: {0}", name);
    }

    public java.util.List<ScheduleGroup> listScheduleGroups(String namePrefix, String region) {
        getOrCreateDefaultGroup(region);
        String storagePrefix = "group:" + region + ":";
        return groupStore.scan(k -> {
            if (!k.startsWith(storagePrefix)) {
                return false;
            }
            if (namePrefix == null || namePrefix.isBlank()) {
                return true;
            }
            String groupName = k.substring(storagePrefix.length());
            return groupName.startsWith(namePrefix);
        });
    }

    // ──────────────────────────── Helpers ────────────────────────────

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new AwsException("ValidationException", "Name is required.", 400);
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new AwsException("ValidationException",
                    "Name must match pattern [0-9a-zA-Z-_.]{1,64}: " + name, 400);
        }
    }

    private String buildGroupArn(String region, String name) {
        return regionResolver.buildArn("scheduler", region, "schedule-group/" + name);
    }

    private static String groupKey(String region, String name) {
        return "group:" + region + ":" + name;
    }
}
