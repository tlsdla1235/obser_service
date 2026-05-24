package com.observation.portal.common.time;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * 마지막 accepted bucket endUtc만 사용해 freshness 후보를 계산하는 component다.
 *
 * <p>Story 4.1 범위에서는 stale/down 후보 threshold까지만 제공하고 최종 lifecycle state 판정은 하지 않는다.</p>
 */
@Component
public class AcceptedBucketFreshnessEvaluator {

    public static final Duration STALE_AFTER = Duration.ofSeconds(90);
    public static final Duration DOWN_AFTER = Duration.ofSeconds(180);

    private final Clock clock;

    /**
     * query 시점 테스트를 위해 Clock을 주입받고 UTC instant 기준으로 평가한다.
     */
    public AcceptedBucketFreshnessEvaluator(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null").withZone(ZoneOffset.UTC);
    }

    /**
     * 주입된 clock의 현재 시각과 마지막 accepted bucket endUtc로 freshness 후보를 계산한다.
     */
    public AcceptedBucketFreshness evaluate(Instant lastAcceptedBucketEndUtc) {
        return evaluateAt(clock.instant(), lastAcceptedBucketEndUtc);
    }

    /**
     * 명시적인 query 시각으로 freshness 후보를 계산한다. null이면 첫 bucket 대기 상태로 표현한다.
     */
    public AcceptedBucketFreshness evaluateAt(Instant evaluatedAtUtc, Instant lastAcceptedBucketEndUtc) {
        Instant requiredEvaluatedAtUtc = Objects.requireNonNull(evaluatedAtUtc, "evaluatedAtUtc must not be null");
        if (lastAcceptedBucketEndUtc == null) {
            return AcceptedBucketFreshness.waitingFirstData(requiredEvaluatedAtUtc);
        }

        Duration age = Duration.between(lastAcceptedBucketEndUtc, requiredEvaluatedAtUtc);
        AcceptedBucketFreshnessStatus status = statusFor(age);
        return AcceptedBucketFreshness.observed(
                requiredEvaluatedAtUtc,
                lastAcceptedBucketEndUtc,
                age,
                status);
    }

    private static AcceptedBucketFreshnessStatus statusFor(Duration age) {
        if (age.compareTo(DOWN_AFTER) >= 0) {
            return AcceptedBucketFreshnessStatus.DOWN_CANDIDATE;
        }
        if (age.compareTo(STALE_AFTER) >= 0) {
            return AcceptedBucketFreshnessStatus.STALE_CANDIDATE;
        }
        return AcceptedBucketFreshnessStatus.CURRENT;
    }
}
