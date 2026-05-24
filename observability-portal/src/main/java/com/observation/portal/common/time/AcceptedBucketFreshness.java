package com.observation.portal.common.time;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * accepted bucket 기반 freshness 계산 결과다.
 *
 * <p>starter heartbeat나 accepted_at이 아니라 마지막 accepted bucket의 endUtc와 query 시각만 보존한다.</p>
 */
public record AcceptedBucketFreshness(
        Instant evaluatedAtUtc,
        Optional<Instant> lastAcceptedBucketEndUtc,
        Optional<Duration> age,
        AcceptedBucketFreshnessStatus status
) {

    /**
     * freshness 결과의 필수 값과 Optional container를 null 없이 고정한다.
     */
    public AcceptedBucketFreshness {
        Objects.requireNonNull(evaluatedAtUtc, "evaluatedAtUtc must not be null");
        lastAcceptedBucketEndUtc = Objects.requireNonNull(
                lastAcceptedBucketEndUtc,
                "lastAcceptedBucketEndUtc must not be null");
        age = Objects.requireNonNull(age, "age must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    /**
     * 아직 accepted bucket이 없는 application의 freshness 후보를 만든다.
     */
    public static AcceptedBucketFreshness waitingFirstData(Instant evaluatedAtUtc) {
        return new AcceptedBucketFreshness(
                evaluatedAtUtc,
                Optional.empty(),
                Optional.empty(),
                AcceptedBucketFreshnessStatus.WAITING_FIRST_DATA);
    }

    /**
     * 마지막 accepted bucket endUtc가 있는 application의 freshness 후보를 만든다.
     */
    public static AcceptedBucketFreshness observed(
            Instant evaluatedAtUtc,
            Instant lastAcceptedBucketEndUtc,
            Duration age,
            AcceptedBucketFreshnessStatus status) {
        return new AcceptedBucketFreshness(
                evaluatedAtUtc,
                Optional.of(Objects.requireNonNull(lastAcceptedBucketEndUtc, "lastAcceptedBucketEndUtc must not be null")),
                Optional.of(Objects.requireNonNull(age, "age must not be null")),
                status);
    }
}
