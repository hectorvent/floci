package io.github.hectorvent.floci.services.detective;

import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.RegionResolver;
import io.github.hectorvent.floci.core.storage.AccountAwareStorageBackend;
import io.github.hectorvent.floci.services.detective.model.DetectiveState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DetectiveServiceTest {
    private static final String REGION = "us-east-1";
    private static final String MANAGEMENT_ACCOUNT = "222222222222";
    private static final String ADMIN_ACCOUNT = MANAGEMENT_ACCOUNT;
    private static final String MEMBER_ACCOUNT = "333333333333";

    private RegionResolver regionResolver;
    private DetectiveService service;

    @BeforeEach
    void setUp() {
        regionResolver = mock(RegionResolver.class);
        when(regionResolver.getAccountId()).thenReturn(MANAGEMENT_ACCOUNT);
        service = new DetectiveService(AccountAwareStorageBackend.inMemory(MANAGEMENT_ACCOUNT), regionResolver);
    }

    @Test
    void delegationCreatesGraphForAdministratorAccount() {
        service.enableAdmin(REGION, ADMIN_ACCOUNT);

        when(regionResolver.getAccountId()).thenReturn(ADMIN_ACCOUNT);
        assertTrue(service.requireGraph(REGION).isGraph());
        assertEquals(ADMIN_ACCOUNT, service.requireGraph(REGION).getAdminAccountId());
    }

    @Test
    void organizationMemberDoesNotRequireEmailAddress() {
        service.enableAdmin(REGION, ADMIN_ACCOUNT);
        when(regionResolver.getAccountId()).thenReturn(ADMIN_ACCOUNT);
        String graphArn = service.graphArn(REGION);

        var member = service.createMember(REGION, graphArn, MEMBER_ACCOUNT, null);

        assertEquals(MEMBER_ACCOUNT, member.getAccountId());
        assertNull(member.getEmailAddress());
        assertEquals("ACCEPTED_BUT_DISABLED", member.getStatus());
    }

    @Test
    void omittedAutoEnableLeavesConfigurationUnchanged() {
        service.enableAdmin(REGION, ADMIN_ACCOUNT);
        when(regionResolver.getAccountId()).thenReturn(ADMIN_ACCOUNT);
        String graphArn = service.graphArn(REGION);
        service.updateOrganizationConfiguration(REGION, graphArn, true);

        service.updateOrganizationConfiguration(REGION, graphArn, null);

        assertTrue(service.requireGraph(REGION).isAutoEnable());
    }

    @Test
    void startMonitoringRequiresAcceptedButDisabledMember() {
        service.enableAdmin(REGION, ADMIN_ACCOUNT);
        when(regionResolver.getAccountId()).thenReturn(ADMIN_ACCOUNT);
        String graphArn = service.graphArn(REGION);
        service.createMember(REGION, graphArn, MEMBER_ACCOUNT, null);

        assertEquals("ENABLED", service.startMonitoring(REGION, MEMBER_ACCOUNT, graphArn).getStatus());
        AwsException error = assertThrows(AwsException.class,
                () -> service.startMonitoring(REGION, MEMBER_ACCOUNT, graphArn));
        assertEquals("ConflictException", error.getErrorCode());
    }

    @Test
    void clearRemovesAllDetectiveState() {
        service.enableAdmin(REGION, ADMIN_ACCOUNT);
        assertEquals(ADMIN_ACCOUNT, service.state(REGION).getAdminAccountId());

        service.clear();

        assertNull(service.state(REGION).getAdminAccountId());
        assertFalse(service.state(REGION).isGraph());
    }
}
