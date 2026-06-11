package com.observation.portal.domain.cleanup.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 매일 01:15 KST에 retention cleanup service를 호출하는 scheduler다.
 *
 * <p>KST는 운영자가 예측하는 wall-clock trigger 기준으로만 사용하고, cutoff 계산과 DB timestamp 비교는 service의 UTC
 * 계산에 위임한다.</p>
 */
@Service
public class RetentionCleanupScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetentionCleanupScheduler.class);

    private final RetentionCleanupProperties properties;
    private final RetentionCleanupService cleanupService;

    /**
     * scheduler rollout 설정과 cleanup use case를 주입한다.
     */
    public RetentionCleanupScheduler(
            RetentionCleanupProperties properties,
            RetentionCleanupService cleanupService) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.cleanupService = Objects.requireNonNull(cleanupService, "cleanupService must not be null");
    }

    /**
     * daily cleanup trigger다. cron/zone은 retention horizon이 아니라 실행 cadence만 정의한다.
     */
    @Scheduled(cron = "0 15 1 * * *", zone = "Asia/Seoul")
    public void runDailyCleanup() {
        if (!properties.enabled()) {
            LOGGER.info("retention_cleanup_skipped enabled=false retentionDays={}", properties.retentionDays());
            return;
        }
        RetentionCleanupResult result = cleanupService.cleanup();
        LOGGER.info(
                "retention_cleanup_completed runAtUtc={} snapshotCutoffUtc={} metricEvidenceCutoffUtc={} "
                        + "retentionDays={} dryRun={} deletedDashboardSnapshots={} deletedAcceptedMetricBuckets={}",
                result.runAtUtc(),
                result.snapshotCutoffUtc(),
                result.metricEvidenceCutoffUtc(),
                result.retentionDays(),
                result.dryRun(),
                result.deletedDashboardSnapshots(),
                result.deletedAcceptedMetricBuckets());
    }
}
