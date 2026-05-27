package com.observation.portal.domain.snapshot.service;

import com.observation.portal.domain.catalog.entity.ApplicationEntity;
import com.observation.portal.domain.catalog.repository.ApplicationRepository;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotCaptureReason;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotCaptureRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
        when(applicationRepository.findActiveApplicationsWithAcceptedBucketSince(
                OffsetDateTime.parse("2026-05-13T13:05:21Z"),
                OffsetDateTime.parse("2026-05-27T13:00:00Z")))
                .thenReturn(List.of(application));

        DashboardSnapshotScheduler scheduler = new DashboardSnapshotScheduler(
                applicationRepository,
                captureService,
                clock,
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
        assertThat(request.requestedAt()).isEqualTo(OffsetDateTime.parse("2026-05-27T13:05:21Z"));
        assertThat(request.triggerSource()).isEqualTo("utc_hourly_scheduler");
    }
}
