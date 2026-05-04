package io.github.hectorvent.floci.services.scheduler;

import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.TagHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

/**
 * {@link TagHandler} implementation for EventBridge Scheduler.
 *
 * <p>ARN format: {@code arn:aws:scheduler:<region>:<account>:schedule-group/<name>}.
 * AWS only permits tags on schedule groups; ARNs pointing at individual schedules
 * ({@code schedule/<group>/<name>}) are rejected with {@code ValidationException} to
 * mirror AWS behavior.
 */
@ApplicationScoped
public class SchedulerTagHandler implements TagHandler {

    private final SchedulerService service;

    @Inject
    public SchedulerTagHandler(SchedulerService service) {
        this.service = service;
    }

    @Override
    public String serviceKey() {
        return "scheduler";
    }

    @Override
    public String tagsBodyKey() {
        return "Tags";
    }

    @Override
    public boolean tagsBodyIsList() {
        return true;
    }

    @Override
    public String tagKeysQueryName() {
        return "TagKeys";
    }

    @Override
    public boolean strictTagValidation() {
        return true;
    }

    @Override
    public Map<String, String> listTags(String region, String arn) {
        return service.getScheduleGroupTags(groupNameFromArn(arn), region);
    }

    @Override
    public void tagResource(String region, String arn, Map<String, String> tags) {
        service.tagScheduleGroup(groupNameFromArn(arn), region, tags);
    }

    @Override
    public void untagResource(String region, String arn, List<String> tagKeys) {
        service.untagScheduleGroup(groupNameFromArn(arn), region, tagKeys);
    }

    private static String groupNameFromArn(String arn) {
        String[] parts = arn.split(":", 6);
        if (parts.length < 6) {
            throw new AwsException("ValidationException", "Invalid resource ARN: " + arn, 400);
        }
        String resource = parts[5];
        String prefix = "schedule-group/";
        if (!resource.startsWith(prefix)) {
            throw new AwsException("ValidationException",
                    "Tags are only supported on schedule groups: " + arn, 400);
        }
        String name = resource.substring(prefix.length());
        if (name.isBlank() || name.contains("/")) {
            throw new AwsException("ValidationException", "Invalid resource ARN: " + arn, 400);
        }
        return name;
    }
}
