package io.github.hectorvent.floci.services.scheduler;

import io.github.hectorvent.floci.config.EmulatorConfig;
import io.github.hectorvent.floci.services.scheduler.model.Schedule;
import io.github.hectorvent.floci.services.scheduler.model.Target;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScheduleDispatcherTest {

    private static final String ARN_PREFIX = "arn:aws:scheduler:eu-central-1:000000000000:schedule/default/";
    private static final String SQS_TARGET_ARN = "arn:aws:sqs:eu-central-1:000000000000:test-queue";

    private SchedulerService schedulerService;
    private ScheduleInvoker invoker;
    private ScheduleDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        schedulerService = mock(SchedulerService.class);
        invoker = mock(ScheduleInvoker.class);

        EmulatorConfig.SchedulerServiceConfig schedulerCfg = mock(EmulatorConfig.SchedulerServiceConfig.class);
        when(schedulerCfg.enabled()).thenReturn(true);
        when(schedulerCfg.invocationEnabled()).thenReturn(true);
        when(schedulerCfg.tickIntervalSeconds()).thenReturn(10L);
        EmulatorConfig.ServicesConfig servicesCfg = mock(EmulatorConfig.ServicesConfig.class);
        when(servicesCfg.scheduler()).thenReturn(schedulerCfg);
        EmulatorConfig config = mock(EmulatorConfig.class);
        when(config.services()).thenReturn(servicesCfg);

        dispatcher = new ScheduleDispatcher(schedulerService, invoker, config);
    }

    private Schedule newSchedule(String name, String expression, String state) {
        Schedule s = new Schedule();
        s.setName(name);
        s.setGroupName("default");
        s.setArn(ARN_PREFIX + name);
        s.setState(state);
        s.setScheduleExpression(expression);
        Target target = new Target();
        target.setArn(SQS_TARGET_ARN);
        target.setRoleArn("arn:aws:iam::000000000000:role/test");
        target.setInput("{\"hello\":\"world\"}");
        s.setTarget(target);
        s.setCreationDate(Instant.parse("2026-04-21T09:00:00Z"));
        return s;
    }

    @Test
    void firesAtScheduleWhenDue() {
        Schedule s = newSchedule("at1", "at(2026-04-21T09:17:54)", "ENABLED");
        when(schedulerService.listAllSchedules()).thenReturn(List.of(s));

        dispatcher.tick(Instant.parse("2026-04-21T09:18:00Z"));

        verify(invoker, times(1)).invoke(s.getTarget(), "eu-central-1");
    }

    @Test
    void skipsAtScheduleBeforeFireTime() {
        Schedule s = newSchedule("at1", "at(2026-04-21T09:17:54)", "ENABLED");
        when(schedulerService.listAllSchedules()).thenReturn(List.of(s));

        dispatcher.tick(Instant.parse("2026-04-21T09:17:00Z"));

        verify(invoker, never()).invoke(any(), anyString());
    }

    @Test
    void firesAtOnlyOncePerSchedule() {
        Schedule s = newSchedule("at1", "at(2026-04-21T09:17:54)", "ENABLED");
        when(schedulerService.listAllSchedules()).thenReturn(List.of(s));

        dispatcher.tick(Instant.parse("2026-04-21T09:18:00Z"));
        dispatcher.tick(Instant.parse("2026-04-21T09:19:00Z"));
        dispatcher.tick(Instant.parse("2026-04-21T09:20:00Z"));

        verify(invoker, times(1)).invoke(eq(s.getTarget()), anyString());
    }

    @Test
    void deletesAtScheduleWhenActionAfterCompletionIsDelete() {
        Schedule s = newSchedule("at1", "at(2026-04-21T09:17:54)", "ENABLED");
        s.setActionAfterCompletion("DELETE");
        when(schedulerService.listAllSchedules()).thenReturn(List.of(s));

        dispatcher.tick(Instant.parse("2026-04-21T09:18:00Z"));

        verify(schedulerService, times(1)).deleteSchedule("at1", "default", "eu-central-1");
    }

    @Test
    void leavesAtScheduleInPlaceWhenActionAfterCompletionIsNotDelete() {
        Schedule s = newSchedule("at1", "at(2026-04-21T09:17:54)", "ENABLED");
        s.setActionAfterCompletion("NONE");
        when(schedulerService.listAllSchedules()).thenReturn(List.of(s));

        dispatcher.tick(Instant.parse("2026-04-21T09:18:00Z"));

        verify(schedulerService, never()).deleteSchedule(anyString(), anyString(), anyString());
    }

    @Test
    void skipsDisabledSchedules() {
        Schedule s = newSchedule("at1", "at(2026-04-21T09:17:54)", "DISABLED");
        when(schedulerService.listAllSchedules()).thenReturn(List.of(s));

        dispatcher.tick(Instant.parse("2026-04-21T09:18:00Z"));

        verify(invoker, never()).invoke(any(), anyString());
    }

    @Test
    void skipsBeforeStartDate() {
        Schedule s = newSchedule("at1", "at(2026-04-21T09:17:54)", "ENABLED");
        s.setStartDate(Instant.parse("2026-04-22T00:00:00Z"));
        when(schedulerService.listAllSchedules()).thenReturn(List.of(s));

        dispatcher.tick(Instant.parse("2026-04-21T09:18:00Z"));

        verify(invoker, never()).invoke(any(), anyString());
    }

    @Test
    void skipsAfterEndDate() {
        Schedule s = newSchedule("at1", "at(2026-04-21T09:17:54)", "ENABLED");
        s.setEndDate(Instant.parse("2026-04-21T09:17:00Z"));
        when(schedulerService.listAllSchedules()).thenReturn(List.of(s));

        dispatcher.tick(Instant.parse("2026-04-21T09:18:00Z"));

        verify(invoker, never()).invoke(any(), anyString());
    }

    @Test
    void ratesFireOnceIntervalHasPassed() {
        Schedule s = newSchedule("rate1", "rate(5 minutes)", "ENABLED");
        s.setCreationDate(Instant.parse("2026-04-21T09:00:00Z"));
        when(schedulerService.listAllSchedules()).thenReturn(List.of(s));

        dispatcher.tick(Instant.parse("2026-04-21T09:04:00Z"));
        verify(invoker, never()).invoke(any(), anyString());

        dispatcher.tick(Instant.parse("2026-04-21T09:06:00Z"));
        verify(invoker, times(1)).invoke(eq(s.getTarget()), anyString());

        dispatcher.tick(Instant.parse("2026-04-21T09:11:01Z"));
        verify(invoker, times(2)).invoke(eq(s.getTarget()), anyString());
    }

    @Test
    void unsupportedExpressionIsSkippedNotThrown() {
        Schedule s = newSchedule("weird", "every 5 minutes", "ENABLED");
        when(schedulerService.listAllSchedules()).thenReturn(List.of(s));

        assertDoesNotThrow(() -> dispatcher.tick(Instant.parse("2026-04-21T09:18:00Z")));
        verify(invoker, never()).invoke(any(), anyString());
    }

    @Test
    void missingTargetIsSkipped() {
        Schedule s = newSchedule("at1", "at(2026-04-21T09:17:54)", "ENABLED");
        s.setTarget(null);
        when(schedulerService.listAllSchedules()).thenReturn(List.of(s));

        dispatcher.tick(Instant.parse("2026-04-21T09:18:00Z"));

        verify(invoker, never()).invoke(any(), anyString());
    }
}
