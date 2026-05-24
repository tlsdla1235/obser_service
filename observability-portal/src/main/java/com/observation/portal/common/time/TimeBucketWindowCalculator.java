package com.observation.portal.common.time;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * 잠긴 time-buckets contract의 UTC bucket/window 계산을 제공하는 공유 component다.
 *
 * <p>repository, controller, UI가 current/baseline 의미를 다시 만들지 않도록 service/model 계층에서
 * 주입받아 사용한다.</p>
 */
@Component
public class TimeBucketWindowCalculator {

    public static final Duration BUCKET_DURATION = Duration.ofSeconds(30);
    public static final Duration DASHBOARD_WINDOW_DURATION = Duration.ofMinutes(15);

    private final Clock clock;

    /**
     * 테스트 가능한 query 시각을 위해 Clock을 주입받고 UTC zone으로 고정한다.
     */
    public TimeBucketWindowCalculator(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null").withZone(ZoneOffset.UTC);
    }

    /**
     * 주입된 clock의 현재 시각을 기준으로 dashboard current/baseline window를 계산한다.
     */
    public DashboardTimeWindow dashboardWindowAtCurrentTime() {
        return dashboardWindowEndingAt(clock.instant());
    }

    /**
     * query 시각 기준 최근 15분 current와 그 직전 15분 baseline을 반환한다.
     */
    public DashboardTimeWindow dashboardWindowEndingAt(Instant queryAtUtc) {
        Instant requiredQueryAtUtc = Objects.requireNonNull(queryAtUtc, "queryAtUtc must not be null");
        Instant currentStartUtc = requiredQueryAtUtc.minus(DASHBOARD_WINDOW_DURATION);
        Instant baselineStartUtc = currentStartUtc.minus(DASHBOARD_WINDOW_DURATION);
        return new DashboardTimeWindow(
                requiredQueryAtUtc,
                new UtcTimeInterval(currentStartUtc, requiredQueryAtUtc),
                new UtcTimeInterval(baselineStartUtc, currentStartUtc),
                BUCKET_DURATION);
    }

    /**
     * 주어진 시각이 속한 UTC 30초 bucket interval을 epoch boundary 기준으로 계산한다.
     */
    public UtcTimeInterval bucketContaining(Instant instant) {
        Instant requiredInstant = Objects.requireNonNull(instant, "instant must not be null");
        long bucketStartSecond = Math.floorDiv(requiredInstant.getEpochSecond(), BUCKET_DURATION.toSeconds())
                * BUCKET_DURATION.toSeconds();
        Instant bucketStartUtc = Instant.ofEpochSecond(bucketStartSecond);
        return new UtcTimeInterval(bucketStartUtc, bucketStartUtc.plus(BUCKET_DURATION));
    }

    /**
     * ingest validation과 service 계산에서 사용할 UTC 30초 boundary 여부를 확인한다.
     */
    public boolean isBucketBoundary(Instant instant) {
        Instant requiredInstant = Objects.requireNonNull(instant, "instant must not be null");
        return requiredInstant.getNano() == 0
                && Math.floorMod(requiredInstant.getEpochSecond(), BUCKET_DURATION.toSeconds()) == 0;
    }
}
