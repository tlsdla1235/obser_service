package com.observation.portal.domain.bucket.repository;

import com.observation.portal.domain.bucket.entity.AcceptedMetricBucketEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * `accepted_metric_buckets` table의 Spring Data JPA persistence 작업을 담당한다.
 */
@Repository
interface AcceptedMetricBucketJpaRepository extends JpaRepository<AcceptedMetricBucketEntity, UUID> {

    /**
     * MVP duplicate key reject에서 사용할 project scope idempotency lookup이다.
     */
    Optional<AcceptedMetricBucketEntity> findByProjectIdAndIdempotencyKey(UUID projectId, String idempotencyKey);

    /**
     * instance와 bucket start 기준 identity unique constraint에 대응하는 lookup이다.
     */
    Optional<AcceptedMetricBucketEntity> findByApplicationInstanceIdAndBucketStartUtc(
            UUID applicationInstanceId,
            OffsetDateTime bucketStartUtc);
}
