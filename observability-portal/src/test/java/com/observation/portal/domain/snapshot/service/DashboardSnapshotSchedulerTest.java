package com.observation.portal.domain.snapshot.service;

import com.observation.portal.domain.catalog.entity.ApplicationEntity;
import com.observation.portal.domain.catalog.repository.ApplicationRepository;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotCaptureReason;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotCaptureRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DashboardSnapshotSchedulerTest {

    @Test
    void dispatchesHourlyScheduledCaptureAtStableUtcHourBoundary() {
        ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
        DashboardSnapshotCaptureService captureService = mock(DashboardSnapshotCaptureService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-05-27T13:05:21Z"), ZoneOffset.UTC);
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000005801");
        UUID applicationId = UUID.fromString("00000000-0000-0000-0000-000000005811");
        ApplicationEntity application = new ApplicationEntity(
                applicationId,
                projectId,
                "orders-api",
                "prod",
                "active",
                OffsetDateTime.parse("2026-05-27T12:00:00Z"),
                OffsetDateTime.parse("2026-05-27T13:00:00Z"),
                OffsetDateTime.parse("2026-05-27T12:00:00Z"),
                OffsetDateTime.parse("2026-05-27T13:00:00Z"));
        when(applicationRepository.findActiveApplicationsEligibleForScheduledSnapshot(
                OffsetDateTime.parse("2026-05-13T13:05:21Z"),
                OffsetDateTime.parse("2026-05-27T13:00:00Z"),
                OffsetDateTime.parse("2026-05-27T13:02:00Z"),
                OffsetDateTime.parse("2026-05-27T13:05:21Z")))
                .thenReturn(List.of(application));

        DashboardSnapshotScheduler scheduler = new DashboardSnapshotScheduler(
                applicationRepository,
                captureService,
                clock,
                properties(),
                14);

        scheduler.dispatchHourlyScheduledCaptures();

        ArgumentCaptor<DashboardSnapshotCaptureRequest> captor =
                ArgumentCaptor.forClass(DashboardSnapshotCaptureRequest.class);
        verify(captureService).capture(captor.capture());
        DashboardSnapshotCaptureRequest request = captor.getValue();
        assertThat(request.projectId()).isEqualTo(projectId);
        assertThat(request.applicationId()).isEqualTo(applicationId);
        assertThat(request.captureReason()).isEqualTo(DashboardSnapshotCaptureReason.HOURLY_SCHEDULED);
        assertThat(request.currentWindowEndUtc()).isEqualTo(OffsetDateTime.parse("2026-05-27T13:00:00Z"));
        assertThat(request.snapshotCutoffAt()).isEqualTo(OffsetDateTime.parse("2026-05-27T13:02:00Z"));
        assertThat(request.requestedAt()).isEqualTo(OffsetDateTime.parse("2026-05-27T13:05:21Z"));
        assertThat(request.triggerSource()).isEqualTo("utc_hourly_scheduler");
    }

    @Test
    void skipsBeforeCutoffAndDispatchesSameHourlyTargetOnlyOnce() {
        ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
        DashboardSnapshotCaptureService captureService = mock(DashboardSnapshotCaptureService.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-05-27T13:01:59Z"));
        ApplicationEntity application = new ApplicationEntity(
                UUID.fromString("00000000-0000-0000-0000-000000005811"),
                UUID.fromString("00000000-0000-0000-0000-000000005801"),
                "orders-api",
                "prod",
                "active",
                OffsetDateTime.parse("2026-05-27T12:00:00Z"),
                OffsetDateTime.parse("2026-05-27T13:00:00Z"),
                OffsetDateTime.parse("2026-05-27T12:00:00Z"),
                OffsetDateTime.parse("2026-05-27T13:00:00Z"));
        when(applicationRepository.findActiveApplicationsEligibleForScheduledSnapshot(
                OffsetDateTime.parse("2026-05-13T13:02:00Z"),
                OffsetDateTime.parse("2026-05-27T13:00:00Z"),
                OffsetDateTime.parse("2026-05-27T13:02:00Z"),
                OffsetDateTime.parse("2026-05-27T13:02:00Z")))
                .thenReturn(List.of(application));
        DashboardSnapshotScheduler scheduler = new DashboardSnapshotScheduler(
                applicationRepository,
                captureService,
                clock,
                properties(),
                14);

        scheduler.dispatchHourlyScheduledCaptures();
        verifyNoInteractions(applicationRepository);
        verify(captureService, never()).capture(org.mockito.ArgumentMatchers.any());

        clock.setInstant(Instant.parse("2026-05-27T13:02:00Z"));
        scheduler.dispatchHourlyScheduledCaptures();

        clock.setInstant(Instant.parse("2026-05-27T13:03:00Z"));
        scheduler.dispatchHourlyScheduledCaptures();

        verify(applicationRepository).findActiveApplicationsEligibleForScheduledSnapshot(
                OffsetDateTime.parse("2026-05-13T13:02:00Z"),
                OffsetDateTime.parse("2026-05-27T13:00:00Z"),
                OffsetDateTime.parse("2026-05-27T13:02:00Z"),
                OffsetDateTime.parse("2026-05-27T13:02:00Z"));
        verify(captureService).capture(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void supportsCaptureDelayThatCrossesHourlyBoundary() {
        ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
        DashboardSnapshotCaptureService captureService = mock(DashboardSnapshotCaptureService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-05-27T13:30:00Z"), ZoneOffset.UTC);
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000005801");
        UUID applicationId = UUID.fromString("00000000-0000-0000-0000-000000005811");
        ApplicationEntity application = new ApplicationEntity(
                applicationId,
                projectId,
                "orders-api",
                "prod",
                "active",
                OffsetDateTime.parse("2026-05-27T12:00:00Z"),
                OffsetDateTime.parse("2026-05-27T13:00:00Z"),
                OffsetDateTime.parse("2026-05-27T12:00:00Z"),
                OffsetDateTime.parse("2026-05-27T13:00:00Z"));
        when(applicationRepository.findActiveApplicationsEligibleForScheduledSnapshot(
                OffsetDateTime.parse("2026-05-13T13:30:00Z"),
                OffsetDateTime.parse("2026-05-27T12:00:00Z"),
                OffsetDateTime.parse("2026-05-27T13:30:00Z"),
                OffsetDateTime.parse("2026-05-27T13:30:00Z")))
                .thenReturn(List.of(application));

        DashboardSnapshotScheduler scheduler = new DashboardSnapshotScheduler(
                applicationRepository,
                captureService,
                clock,
                properties(Duration.ofMinutes(90)),
                14);

        scheduler.dispatchHourlyScheduledCaptures();

        ArgumentCaptor<DashboardSnapshotCaptureRequest> captor =
                ArgumentCaptor.forClass(DashboardSnapshotCaptureRequest.class);
        verify(captureService).capture(captor.capture());
        assertThat(captor.getValue().currentWindowEndUtc()).isEqualTo(OffsetDateTime.parse("2026-05-27T12:00:00Z"));
        assertThat(captor.getValue().snapshotCutoffAt()).isEqualTo(OffsetDateTime.parse("2026-05-27T13:30:00Z"));
    }

    private static DashboardSnapshotProperties properties() {
        return properties(Duration.ofSeconds(120));
    }

    private static DashboardSnapshotProperties properties(Duration captureDelay) {
        DashboardSnapshotProperties properties = new DashboardSnapshotProperties();
        properties.setCaptureDelay(captureDelay);
        return properties;
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
