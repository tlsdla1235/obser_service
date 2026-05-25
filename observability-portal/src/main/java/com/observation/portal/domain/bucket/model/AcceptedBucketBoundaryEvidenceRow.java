package com.observation.portal.domain.bucket.model;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * application-level 30초 accepted bucket boundary를 distinct projection으로 옮기는 중립 row다.
 *
 * <p>이 projection은 recent evidence 조회 범위를 정하기 위한 boundary만 담으며 state, rule, recovery, p95/p99,
 * endpoint priority를 계산하지 않는다.</p>
 */
public record AcceptedBucketBoundaryEvidenceRow(
        UUID applicationId,
        OffsetDateTime bucketStartUtc,
        OffsetDateTime bucketEndUtc
) {

    /**
     * application scope와 30초 bucket boundary가 비어 있지 않은 projection인지 검증한다.
     */
    public AcceptedBucketBoundaryEvidenceRow {
        Objects.requireNonNull(applicationId, "applicationId must not be null");
        Objects.requireNonNull(bucketStartUtc, "bucketStartUtc must not be null");
        Objects.requireNonNull(bucketEndUtc, "bucketEndUtc must not be null");
    }
}
