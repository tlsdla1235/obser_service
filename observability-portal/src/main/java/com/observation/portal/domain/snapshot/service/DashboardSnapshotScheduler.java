package com.observation.portal.domain.snapshot.service;

import com.observation.portal.domain.catalog.entity.ApplicationEntity;
import com.observation.portal.domain.catalog.repository.ApplicationRepository;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotCaptureReason;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotCaptureRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/**
 * UTC 30분 dashboard slot 기준 scheduled snapshot capture trigger/dispatcher다.
 *
 * <p>이 class는 eligible application과 target 30분 boundary만 결정하고, read model 생성과 snapshot 저장은
 * `DashboardSnapshotCaptureService`에 위임한다. 저장 token은 legacy compatibility 때문에 `hourly_scheduled`를 유지한다.</p>
 */
@Service
public class DashboardSnapshotScheduler {

    private static final Duration SCHEDULED_SLOT_SPAN = Duration.ofMinutes(30);

    private final ApplicationRepository applicationRepository;
    private final DashboardSnapshotCaptureService captureService;
    private final Clock clock;
    private final DashboardSnapshotProperties snapshotProperties;
    private final int retentionDays;
    private OffsetDateTime lastDispatchedWindowEndUtc;

    /**
     * scheduler 대상 조회 repository, capture use case, UTC clock과 retention horizon을 주입한다.
     */
    public DashboardSnapshotScheduler(
            ApplicationRepository applicationRepository,
            DashboardSnapshotCaptureService captureService,
            Clock clock,
            DashboardSnapshotProperties snapshotProperties,
            @Value("${portal.dashboard-snapshots.retention-days:14}") int retentionDays) {
        this.applicationRepository = Objects.requireNonNull(
                applicationRepository,
                "applicationRepository must not be null");
        this.captureService = Objects.requireNonNull(captureService, "captureService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null").withZone(ZoneOffset.UTC);
        this.snapshotProperties = Objects.requireNonNull(snapshotProperties, "snapshotProperties must not be null");
        if (retentionDays <= 0) {
            throw new IllegalArgumentException("retentionDays must be positive");
        }
        this.retentionDays = retentionDays;
    }

    /**
     * UTC minute tick마다 cutoff가 지난 첫 tick에서만 30분 target slot capture를 dispatch한다.
     */
    @Scheduled(cron = "0 * * * * *", zone = "UTC")
    public synchronized void dispatchThirtyMinuteScheduledCaptures() {
        Instant requestedAt = clock.instant();
        Instant targetWindowEnd = targetWindowEnd(requestedAt);
        OffsetDateTime requestedAtUtc = OffsetDateTime.ofInstant(requestedAt, ZoneOffset.UTC);
        OffsetDateTime targetWindowEndUtc = OffsetDateTime.ofInstant(targetWindowEnd, ZoneOffset.UTC);
        OffsetDateTime snapshotCutoffAt = snapshotProperties.snapshotCutoffAt(targetWindowEndUtc);
        if (requestedAtUtc.isBefore(snapshotCutoffAt) || targetWindowEndUtc.equals(lastDispatchedWindowEndUtc)) {
            return;
        }
        OffsetDateTime retentionCutoffUtc = requestedAtUtc.minusDays(retentionDays);
        List<ApplicationEntity> applications =
                applicationRepository.findActiveApplicationsEligibleForScheduledSnapshot(
                        retentionCutoffUtc,
                        targetWindowEndUtc,
                        snapshotCutoffAt,
                        requestedAtUtc);
        lastDispatchedWindowEndUtc = targetWindowEndUtc;
        for (ApplicationEntity application : applications) {
            captureService.capture(new DashboardSnapshotCaptureRequest(
                    application.projectId(),
                    application.id(),
                    DashboardSnapshotCaptureReason.HOURLY_SCHEDULED,
                    targetWindowEndUtc,
                    snapshotCutoffAt,
                    requestedAtUtc,
                    "utc_30_minute_scheduler"));
        }
    }

    private Instant targetWindowEnd(Instant requestedAt) {
        if (snapshotProperties.getCaptureDelay().compareTo(SCHEDULED_SLOT_SPAN) < 0) {
            return floorToScheduledSlotBoundary(requestedAt);
        }
        return floorToScheduledSlotBoundary(requestedAt.minus(snapshotProperties.getCaptureDelay()));
    }

    private static Instant floorToScheduledSlotBoundary(Instant instant) {
        OffsetDateTime utc = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
        int boundaryMinute = utc.getMinute() >= 30 ? 30 : 0;
        return utc.withMinute(boundaryMinute)
                .withSecond(0)
                .withNano(0)
                .toInstant();
    }
}
