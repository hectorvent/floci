package io.github.hectorvent.floci.services.scheduler;

import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.RegionResolver;
import io.github.hectorvent.floci.core.storage.InMemoryStorage;
import io.github.hectorvent.floci.services.scheduler.model.ScheduleGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SchedulerServiceTest {

    private static final String REGION = "us-east-1";

    private SchedulerService service;

    @BeforeEach
    void setUp() {
        service = new SchedulerService(
                new InMemoryStorage<>(),
                new RegionResolver("us-east-1", "000000000000")
        );
    }

    @Test
    void getOrCreateDefaultGroup() {
        ScheduleGroup group = service.getOrCreateDefaultGroup(REGION);
        assertEquals("default", group.getName());
        assertEquals("ACTIVE", group.getState());
        assertTrue(group.getArn().contains("schedule-group/default"));
        assertTrue(group.getArn().contains(":scheduler:"));
    }

    @Test
    void getOrCreateDefaultGroupIsIdempotent() {
        ScheduleGroup first = service.getOrCreateDefaultGroup(REGION);
        ScheduleGroup second = service.getOrCreateDefaultGroup(REGION);
        assertEquals(first.getArn(), second.getArn());
        assertEquals(first.getCreationDate(), second.getCreationDate());
    }

    @Test
    void createScheduleGroup() {
        ScheduleGroup group = service.createScheduleGroup("my-group", null, REGION);
        assertEquals("my-group", group.getName());
        assertEquals("ACTIVE", group.getState());
        assertTrue(group.getArn().contains("schedule-group/my-group"));
    }

    @Test
    void createScheduleGroupWithTags() {
        ScheduleGroup group = service.createScheduleGroup(
                "tagged", Map.of("env", "test"), REGION);
        assertEquals("test", group.getTags().get("env"));
    }

    @Test
    void createScheduleGroupDuplicateThrows() {
        service.createScheduleGroup("dup", null, REGION);
        AwsException e = assertThrows(AwsException.class, () ->
                service.createScheduleGroup("dup", null, REGION));
        assertEquals("ConflictException", e.getErrorCode());
        assertEquals(409, e.getHttpStatus());
    }

    @Test
    void createScheduleGroupReservedDefaultNameThrows() {
        AwsException e = assertThrows(AwsException.class, () ->
                service.createScheduleGroup("default", null, REGION));
        assertEquals("ConflictException", e.getErrorCode());
    }

    @Test
    void createScheduleGroupBlankNameThrows() {
        AwsException e = assertThrows(AwsException.class, () ->
                service.createScheduleGroup("", null, REGION));
        assertEquals("ValidationException", e.getErrorCode());
    }

    @Test
    void createScheduleGroupInvalidCharactersThrows() {
        AwsException e = assertThrows(AwsException.class, () ->
                service.createScheduleGroup("bad name!", null, REGION));
        assertEquals("ValidationException", e.getErrorCode());
    }

    @Test
    void getScheduleGroup() {
        service.createScheduleGroup("find-me", null, REGION);
        ScheduleGroup group = service.getScheduleGroup("find-me", REGION);
        assertEquals("find-me", group.getName());
    }

    @Test
    void getScheduleGroupNotFoundThrows() {
        AwsException e = assertThrows(AwsException.class, () ->
                service.getScheduleGroup("missing", REGION));
        assertEquals("ResourceNotFoundException", e.getErrorCode());
        assertEquals(404, e.getHttpStatus());
    }

    @Test
    void getScheduleGroupBlankReturnsDefault() {
        ScheduleGroup group = service.getScheduleGroup("", REGION);
        assertEquals("default", group.getName());
    }

    @Test
    void deleteScheduleGroup() {
        service.createScheduleGroup("to-delete", null, REGION);
        service.deleteScheduleGroup("to-delete", REGION);
        assertThrows(AwsException.class, () ->
                service.getScheduleGroup("to-delete", REGION));
    }

    @Test
    void deleteDefaultGroupThrows() {
        AwsException e = assertThrows(AwsException.class, () ->
                service.deleteScheduleGroup("default", REGION));
        assertEquals("ValidationException", e.getErrorCode());
    }

    @Test
    void deleteScheduleGroupNotFoundThrows() {
        AwsException e = assertThrows(AwsException.class, () ->
                service.deleteScheduleGroup("missing", REGION));
        assertEquals("ResourceNotFoundException", e.getErrorCode());
    }

    @Test
    void listScheduleGroupsIncludesDefault() {
        List<ScheduleGroup> groups = service.listScheduleGroups(null, REGION);
        assertTrue(groups.stream().anyMatch(g -> "default".equals(g.getName())));
    }

    @Test
    void listScheduleGroupsWithPrefix() {
        service.createScheduleGroup("alpha-1", null, REGION);
        service.createScheduleGroup("alpha-2", null, REGION);
        service.createScheduleGroup("beta-1", null, REGION);
        List<ScheduleGroup> result = service.listScheduleGroups("alpha", REGION);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(g -> g.getName().startsWith("alpha")));
    }

    @Test
    void scheduleGroupsAreRegionScoped() {
        service.createScheduleGroup("shared", null, "us-east-1");
        assertThrows(AwsException.class, () ->
                service.getScheduleGroup("shared", "us-west-2"));
    }
}
