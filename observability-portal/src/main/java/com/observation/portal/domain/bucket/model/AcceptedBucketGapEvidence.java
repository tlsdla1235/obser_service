package com.observation.portal.domain.bucket.model;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * 최신 accepted bucket과 직전 bucket 사이의 metric data 공백을 판단하기 위한 중립 projection이다.
 *
 * <p>repository는 이 값으로 state, recovery, rule, p95/p99, endpoint priority를 계산하지 않고, service가
 * lightweight previous context를 만들 수 있는 timestamp 근거만 전달한다.</p>
 */
public record AcceptedBucketGapEvidence(
        OffsetDateTime latestBucketEndUtc,
        Optional<OffsetDateTime> previousBucketEndUtc
) {

    /**
     * 최신 bucket endUtc는 필수이고, 직전 bucket이 없으면 recovery 후보 근거도 없도록 Optional로 보존한다.
     */
    public AcceptedBucketGapEvidence {
        Objects.requireNonNull(latestBucketEndUtc, "latestBucketEndUtc must not be null");
        previousBucketEndUtc = Objects.requireNonNull(
                previousBucketEndUtc,
                "previousBucketEndUtc must not be null");
    }
}
