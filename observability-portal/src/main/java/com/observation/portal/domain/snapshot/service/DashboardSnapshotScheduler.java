package com.observation.portal.domain.snapshot.service;

import com.observation.portal.domain.catalog.entity.ApplicationEntity;
import com.observation.portal.domain.catalog.repository.ApplicationRepository;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotCaptureReason;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotCaptureRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * UTC hourly scheduled snapshot capture trigger/dispatcher다.
 *
 * <p>이 class는 eligible application과 target hourly boundary만 결정하고, read model 생성과 snapshot 저장은
 * `DashboardSnapshotCaptureService`에 위임한다.</p>
 */
@Service
public class DashboardSnapshotScheduler {

    private final ApplicationRepository applicationRepository;
    private final DashboardSnapshotCaptureService captureService;
    private final Clock clock;
    private final int retentionDays;

    /**
     * scheduler 대상 조회 repository, capture use case, UTC clock과 retention horizon을 주입한다.
     */
    public DashboardSnapshotScheduler(
            ApplicationRepository applicationRepository,
            DashboardSnapshotCaptureService captureService,
            Clock clock,
            @Value("${portal.dashboard-snapshots.retention-days:14}") int retentionDays) {
        this.applicationRepository = Objects.requireNonNull(
                applicationRepository,
                "applicationRepository must not be null");
        this.captureService = Objects.requireNonNull(captureService, "captureService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null").withZone(ZoneOffset.UTC);
        if (retentionDays <= 0) {
            throw new IllegalArgumentException("retentionDays must be positive");
        }
        this.retentionDays = retentionDays;
    }

    /**
     * UTC 정시마다 active application 중 retention horizon 안 accepted bucket이 있는 application으로 capture를 dispatch한다.
     */
    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    public void dispatchHourlyScheduledCaptures() {
        Instant requestedAt = clock.instant();
        Instant targetWindowEnd = requestedAt.truncatedTo(ChronoUnit.HOURS);
        OffsetDateTime requestedAtUtc = OffsetDateTime.ofInstant(requestedAt, ZoneOffset.UTC);
        OffsetDateTime targetWindowEndUtc = OffsetDateTime.ofInstant(targetWindowEnd, ZoneOffset.UTC);
        OffsetDateTime retentionCutoffUtc = requestedAtUtc.minusDays(retentionDays);
        List<ApplicationEntity> applications =
                applicationRepository.findActiveApplicationsWithAcceptedBucketSince(
                        retentionCutoffUtc,
                        targetWindowEndUtc);
        for (ApplicationEntity application : applications) {
            captureService.capture(new DashboardSnapshotCaptureRequest(
                    application.projectId(),
                    application.id(),
                    DashboardSnapshotCaptureReason.HOURLY_SCHEDULED,
                    targetWindowEndUtc,
                    requestedAtUtc,
                    "utc_hourly_scheduler"));
        }
    }
}
