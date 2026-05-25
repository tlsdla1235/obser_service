package com.observation.portal.domain.bucket.model;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * accepted bucket gap projection을 만들기 위해 최신순으로 읽는 bucket boundary row다.
 *
 * <p>이 row는 bucket boundary만 담으며 state, recovery, rule, percentile, endpoint priority 계산을 하지 않는다.</p>
 */
public record AcceptedBucketGapEvidenceRow(
        UUID applicationId,
        OffsetDateTime bucketStartUtc,
        OffsetDateTime bucketEndUtc
) {

    /**
     * projection row의 application scope와 bucket boundary가 비어 있지 않도록 검증한다.
     */
    public AcceptedBucketGapEvidenceRow {
        Objects.requireNonNull(applicationId, "applicationId must not be null");
        Objects.requireNonNull(bucketStartUtc, "bucketStartUtc must not be null");
        Objects.requireNonNull(bucketEndUtc, "bucketEndUtc must not be null");
    }
}
