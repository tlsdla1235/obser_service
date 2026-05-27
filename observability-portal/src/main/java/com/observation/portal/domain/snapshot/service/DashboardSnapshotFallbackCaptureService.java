package com.observation.portal.domain.snapshot.service;

import com.observation.portal.domain.dashboard.model.ApplicationDashboardReadModel;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotCaptureReason;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotLatestRow;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotWriteCommand;
import com.observation.portal.domain.snapshot.repository.DashboardSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * dashboard current query path에서 이미 생성된 read model을 fallback snapshot으로 저장하는 보조 service다.
 *
 * <p>이 service는 `DashboardReadModelService`를 호출하지 않으며, latest snapshot 없음 또는 65분 이상 오래된 경우에만
 * fail-open 저장을 시도한다.</p>
 */
@Service
public class DashboardSnapshotFallbackCaptureService {

    private static final Logger log = LoggerFactory.getLogger(DashboardSnapshotFallbackCaptureService.class);
    static final Duration FALLBACK_STALENESS_THRESHOLD = Duration.ofMinutes(65);
    private static final ThreadLocal<Boolean> CAPTURE_IN_PROGRESS = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private final DashboardSnapshotRepository snapshotRepository;
    private final DashboardSnapshotWriterService writerService;

    /**
     * fallback threshold 조회 repository와 writer를 주입한다.
     */
    public DashboardSnapshotFallbackCaptureService(
            DashboardSnapshotRepository snapshotRepository,
            DashboardSnapshotWriterService writerService) {
        this.snapshotRepository = Objects.requireNonNull(snapshotRepository, "snapshotRepository must not be null");
        this.writerService = Objects.requireNonNull(writerService, "writerService must not be null");
    }

    /**
     * latest snapshot이 없거나 queryAt보다 65분 이상 오래된 경우에만 `query_fallback` 저장을 시도한다.
     *
     * <p>최종 저장 실패는 dashboard response를 오염시키지 않고 warn log 후 fail-open으로 종료한다.</p>
     */
    public void captureIfNeeded(ApplicationDashboardReadModel readModel, OffsetDateTime queryAt) {
        ApplicationDashboardReadModel requiredReadModel = Objects.requireNonNull(
                readModel,
                "readModel must not be null");
        OffsetDateTime requiredQueryAt = Objects.requireNonNull(queryAt, "queryAt must not be null")
                .withOffsetSameInstant(ZoneOffset.UTC);
        if (Boolean.TRUE.equals(CAPTURE_IN_PROGRESS.get())) {
            return;
        }

        CAPTURE_IN_PROGRESS.set(Boolean.TRUE);
        try {
            if (!shouldCapture(requiredReadModel, requiredQueryAt)) {
                return;
            }
            writeFallback(requiredReadModel, requiredQueryAt);
        } catch (DashboardSnapshotWriteException exception) {
            log.warn(
                    "dashboard_snapshot_fallback_failed captureReason={} applicationId={} failureType={}",
                    DashboardSnapshotCaptureReason.QUERY_FALLBACK.token(),
                    requiredReadModel.application().applicationId(),
                    exception.failureType(),
                    exception);
        } catch (RuntimeException exception) {
            log.warn(
                    "dashboard_snapshot_fallback_failed captureReason={} applicationId={} failureType=unknown",
                    DashboardSnapshotCaptureReason.QUERY_FALLBACK.token(),
                    requiredReadModel.application().applicationId(),
                    exception);
        } finally {
            CAPTURE_IN_PROGRESS.set(Boolean.FALSE);
        }
    }

    private boolean shouldCapture(ApplicationDashboardReadModel readModel, OffsetDateTime queryAt) {
        return snapshotRepository.findLatestByApplicationId(readModel.application().applicationId())
                .map(latest -> isStale(latest, queryAt))
                .orElse(true);
    }

    private static boolean isStale(DashboardSnapshotLatestRow latest, OffsetDateTime queryAt) {
        OffsetDateTime threshold = queryAt.minus(FALLBACK_STALENESS_THRESHOLD);
        return !latest.generatedAt().isAfter(threshold);
    }

    private void writeFallback(ApplicationDashboardReadModel readModel, OffsetDateTime queryAt) {
        writerService.write(new DashboardSnapshotWriteCommand(
                readModel.application().projectId(),
                readModel.application().applicationId(),
                readModel,
                DashboardSnapshotCaptureReason.QUERY_FALLBACK,
                readModel.application().sourceWindow().current().endUtc(),
                queryAt,
                "dashboard_query_fallback"));
    }
}
