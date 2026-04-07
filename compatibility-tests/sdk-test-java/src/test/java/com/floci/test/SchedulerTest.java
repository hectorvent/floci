package com.floci.test;

import org.junit.jupiter.api.*;
import software.amazon.awssdk.services.scheduler.SchedulerClient;
import software.amazon.awssdk.services.scheduler.model.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("EventBridge Scheduler ScheduleGroup")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SchedulerTest {

    private static SchedulerClient scheduler;
    private static final String GROUP_NAME = "test-schedule-group";

    @BeforeAll
    static void setup() {
        scheduler = TestFixtures.schedulerClient();
    }

    @AfterAll
    static void cleanup() {
        if (scheduler != null) {
            try {
                scheduler.deleteScheduleGroup(DeleteScheduleGroupRequest.builder()
                        .name(GROUP_NAME).build());
            } catch (Exception ignored) {}
            scheduler.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("CreateScheduleGroup - create group")
    void createScheduleGroup() {
        CreateScheduleGroupResponse resp = scheduler.createScheduleGroup(
                CreateScheduleGroupRequest.builder()
                        .name(GROUP_NAME)
                        .build());

        assertThat(resp.scheduleGroupArn()).isNotNull().contains(GROUP_NAME);
    }

    @Test
    @Order(2)
    @DisplayName("GetScheduleGroup - get created group")
    void getScheduleGroup() {
        GetScheduleGroupResponse resp = scheduler.getScheduleGroup(
                GetScheduleGroupRequest.builder()
                        .name(GROUP_NAME)
                        .build());

        assertThat(resp.name()).isEqualTo(GROUP_NAME);
        assertThat(resp.arn()).isNotNull().contains(GROUP_NAME);
        assertThat(resp.state()).isEqualTo(ScheduleGroupState.ACTIVE);
        assertThat(resp.creationDate()).isNotNull();
    }

    @Test
    @Order(3)
    @DisplayName("ListScheduleGroups - created group is present")
    void listScheduleGroupsContainsGroup() {
        ListScheduleGroupsResponse resp = scheduler.listScheduleGroups(
                ListScheduleGroupsRequest.builder().build());

        boolean found = resp.scheduleGroups().stream()
                .anyMatch(g -> GROUP_NAME.equals(g.name()));
        assertThat(found).isTrue();
    }

    @Test
    @Order(4)
    @DisplayName("ListScheduleGroups - namePrefix filter works")
    void listScheduleGroupsNamePrefixFilter() {
        ListScheduleGroupsResponse resp = scheduler.listScheduleGroups(
                ListScheduleGroupsRequest.builder()
                        .namePrefix("test-schedule")
                        .build());

        assertThat(resp.scheduleGroups()).isNotEmpty();
        assertThat(resp.scheduleGroups()).allMatch(g -> g.name().startsWith("test-schedule"));
    }

    @Test
    @Order(5)
    @DisplayName("CreateScheduleGroup - duplicate returns ConflictException")
    void createScheduleGroupDuplicate() {
        assertThatThrownBy(() -> scheduler.createScheduleGroup(
                CreateScheduleGroupRequest.builder()
                        .name(GROUP_NAME)
                        .build()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @Order(6)
    @DisplayName("GetScheduleGroup - non-existent returns ResourceNotFoundException")
    void getScheduleGroupNotFound() {
        assertThatThrownBy(() -> scheduler.getScheduleGroup(
                GetScheduleGroupRequest.builder()
                        .name("does-not-exist-group")
                        .build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @Order(7)
    @DisplayName("DeleteScheduleGroup - delete group")
    void deleteScheduleGroup() {
        scheduler.deleteScheduleGroup(DeleteScheduleGroupRequest.builder()
                .name(GROUP_NAME)
                .build());
    }

    @Test
    @Order(8)
    @DisplayName("GetScheduleGroup - after delete returns NotFound or DELETING")
    void getScheduleGroupAfterDelete() {
        try {
            GetScheduleGroupResponse resp = scheduler.getScheduleGroup(
                    GetScheduleGroupRequest.builder()
                            .name(GROUP_NAME)
                            .build());
            // AWS may briefly return the group with state=DELETING before it disappears
            assertThat(resp.state()).isEqualTo(ScheduleGroupState.DELETING);
        } catch (ResourceNotFoundException e) {
            // Expected — group is already gone
        }
    }
}
