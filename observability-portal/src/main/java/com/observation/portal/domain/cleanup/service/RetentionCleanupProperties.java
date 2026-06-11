package com.observation.portal.domain.cleanup.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Dashboard snapshot과 accepted metric bucket cleanup의 운영 설정을 보관한다.
 *
 * <p>`retentionDays`는 기존 dashboard snapshot read horizon property를 그대로 주입받아 snapshot과 metric evidence의
 * 사용자-facing 보관 horizon이 갈라지지 않게 한다. enabled/dry-run은 rollout 제어일 뿐 cutoff 계산 의미를 바꾸지 않는다.</p>
 */
@Component
public class RetentionCleanupProperties {

    private final int retentionDays;
    private final boolean enabled;
    private final boolean dryRun;

    /**
     * cleanup retention horizon과 scheduler rollout flag를 생성한다.
     */
    public RetentionCleanupProperties(
            @Value("${portal.dashboard-snapshots.retention-days:14}") int retentionDays,
            @Value("${portal.retention.cleanup.enabled:false}") boolean enabled,
            @Value("${portal.retention.cleanup.dry-run:false}") boolean dryRun) {
        if (retentionDays <= 0) {
            throw new IllegalArgumentException("retentionDays must be positive");
        }
        this.retentionDays = retentionDays;
        this.enabled = enabled;
        this.dryRun = dryRun;
    }

    /**
     * dashboard snapshot과 accepted metric bucket이 공유하는 기본 보관 일수다.
     */
    public int retentionDays() {
        return retentionDays;
    }

    /**
     * daily scheduler가 cleanup service를 호출할지 결정하는 운영 flag다.
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * cutoff 계산과 result는 유지하되 물리 삭제를 건너뛰는 운영 검증 flag다.
     */
    public boolean dryRun() {
        return dryRun;
    }
}
