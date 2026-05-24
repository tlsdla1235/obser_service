package com.observation.portal.common.time;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * dashboard query 시점의 current/baseline window 묶음이다.
 *
 * <p>후속 DashboardReadModelService와 LifecycleStateService가 같은 15분 window 계약을 사용하도록
 * query 시각, current, baseline, bucket duration을 하나의 값으로 전달한다.</p>
 */
public record DashboardTimeWindow(
        Instant queryAtUtc,
        UtcTimeInterval current,
        UtcTimeInterval baseline,
        Duration bucketDuration
) {

    /**
     * current는 query 시각에서 끝나고 baseline은 current 직전 구간이어야 한다.
     */
    public DashboardTimeWindow {
        Objects.requireNonNull(queryAtUtc, "queryAtUtc must not be null");
        Objects.requireNonNull(current, "current must not be null");
        Objects.requireNonNull(baseline, "baseline must not be null");
        Objects.requireNonNull(bucketDuration, "bucketDuration must not be null");
        if (!current.endUtc().equals(queryAtUtc)) {
            throw new IllegalArgumentException("current window must end at queryAtUtc");
        }
        if (!baseline.endUtc().equals(current.startUtc())) {
            throw new IllegalArgumentException("baseline window must end at current startUtc");
        }
    }
}
