package com.observation.portal.domain.cleanup.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RetentionCleanupSchedulerTest {

    @Test
    @DisplayName("cleanup scheduler는 매일 01:15 KST cron으로 등록된다")
    void scheduledAnnotationUsesDailyOneFifteenKst() throws NoSuchMethodException {
        Method method = RetentionCleanupScheduler.class.getMethod("runDailyCleanup");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("0 15 1 * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }

    @Test
    @DisplayName("enabled scheduler는 cleanup service에 cutoff와 삭제 orchestration을 위임한다")
    void enabledSchedulerDelegatesToCleanupService() {
        RetentionCleanupService service = mock(RetentionCleanupService.class);
        RetentionCleanupResult result = result(false);
        when(service.cleanup()).thenReturn(result);
        RetentionCleanupScheduler scheduler = new RetentionCleanupScheduler(
                new RetentionCleanupProperties(14, true, false),
                service);

        scheduler.runDailyCleanup();

        verify(service).cleanup();
    }

    @Test
    @DisplayName("disabled scheduler는 daily cadence 의미를 바꾸지 않고 실행만 멈춘다")
    void disabledSchedulerDoesNotInvokeCleanupService() {
        RetentionCleanupService service = mock(RetentionCleanupService.class);
        RetentionCleanupScheduler scheduler = new RetentionCleanupScheduler(
                new RetentionCleanupProperties(14, false, false),
                service);

        scheduler.runDailyCleanup();

        verifyNoInteractions(service);
    }

    private static RetentionCleanupResult result(boolean dryRun) {
        return new RetentionCleanupResult(
                OffsetDateTime.parse("2026-06-10T16:15:00Z"),
                OffsetDateTime.parse("2026-05-27T16:15:00Z"),
                OffsetDateTime.parse("2026-05-27T15:45:00Z"),
                14,
                1L,
                2L,
                true,
                dryRun);
    }
}
