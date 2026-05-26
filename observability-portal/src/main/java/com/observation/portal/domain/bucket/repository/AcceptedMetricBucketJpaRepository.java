package com.observation.portal.domain.bucket.repository;

import com.observation.portal.domain.bucket.entity.AcceptedMetricBucketEntity;
import com.observation.portal.domain.bucket.model.AcceptedBucketBoundaryEvidenceRow;
import com.observation.portal.domain.bucket.model.AcceptedBucketGapEvidenceRow;
import com.observation.portal.domain.bucket.model.EndpointEvidenceRow;
import com.observation.portal.domain.bucket.model.HistogramBucketEvidenceRow;
import com.observation.portal.domain.bucket.model.LocalPercentileEvidenceRow;
import com.observation.portal.domain.bucket.model.RecentBucketEvidenceRow;
import com.observation.portal.domain.bucket.model.RuntimeRatioEvidenceRow;
import com.observation.portal.domain.bucket.model.WindowBucketAggregate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
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

    /**
     * application scope freshness 입력으로 사용할 마지막 accepted bucket endUtc timestamp만 조회한다.
     */
    @Query("select max(bucket.bucketEndUtc) from AcceptedMetricBucketEntity bucket "
            + "where bucket.applicationId = :applicationId")
    Optional<OffsetDateTime> findLatestBucketEndUtcByApplicationId(@Param("applicationId") UUID applicationId);

    /**
     * dashboard freshness 입력으로 evaluationAt 이후 future bucket을 제외한 마지막 endUtc만 조회한다.
     */
    @Query("select max(bucket.bucketEndUtc) from AcceptedMetricBucketEntity bucket "
            + "where bucket.applicationId = :applicationId "
            + "and bucket.bucketEndUtc <= :evaluationAtUtc")
    Optional<OffsetDateTime> findLatestBucketEndUtcByApplicationIdAtOrBefore(
            @Param("applicationId") UUID applicationId,
            @Param("evaluationAtUtc") OffsetDateTime evaluationAtUtc);

    /**
     * dashboard current window에 포함되는 bucket request/error count 합계를 한 query snapshot으로 조회한다.
     */
    @Query("select new com.observation.portal.domain.bucket.model.WindowBucketAggregate("
            + "coalesce(sum(bucket.requestCount), 0L), "
            + "coalesce(sum(bucket.errorCount), 0L)) "
            + "from AcceptedMetricBucketEntity bucket "
            + "where bucket.applicationId = :applicationId "
            + "and bucket.bucketEndUtc > :windowStartUtc "
            + "and bucket.bucketEndUtc <= :windowEndUtc")
    WindowBucketAggregate sumWindowRequestAndErrorCountsByApplicationId(
            @Param("applicationId") UUID applicationId,
            @Param("windowStartUtc") OffsetDateTime windowStartUtc,
            @Param("windowEndUtc") OffsetDateTime windowEndUtc);

    /**
     * source-scoped percentile read model을 위한 raw local percentile JSON과 instance identity를 조회한다.
     */
    @Query("select new com.observation.portal.domain.bucket.model.LocalPercentileEvidenceRow("
            + "bucket.applicationId, "
            + "bucket.applicationInstanceId, "
            + "instance.instanceName, "
            + "bucket.bucketStartUtc, "
            + "bucket.bucketEndUtc, "
            + "bucket.localPercentilesJson) "
            + "from AcceptedMetricBucketEntity bucket, ApplicationInstanceEntity instance "
            + "where instance.id = bucket.applicationInstanceId "
            + "and bucket.applicationId = :applicationId "
            + "and bucket.bucketEndUtc > :windowStartUtc "
            + "and bucket.bucketEndUtc <= :windowEndUtc "
            + "and bucket.localPercentilesJson is not null "
            + "order by instance.instanceName asc, bucket.bucketEndUtc asc")
    List<LocalPercentileEvidenceRow> findLocalPercentileEvidenceRowsByApplicationId(
            @Param("applicationId") UUID applicationId,
            @Param("windowStartUtc") OffsetDateTime windowStartUtc,
            @Param("windowEndUtc") OffsetDateTime windowEndUtc);

    /**
     * histogram distribution read model을 위한 application summary duration bucket JSON row를 조회한다.
     */
    @Query("select new com.observation.portal.domain.bucket.model.HistogramBucketEvidenceRow("
            + "bucket.applicationId, "
            + "bucket.bucketStartUtc, "
            + "bucket.bucketEndUtc, "
            + "bucket.durationBucketsJson) "
            + "from AcceptedMetricBucketEntity bucket "
            + "where bucket.applicationId = :applicationId "
            + "and bucket.bucketEndUtc > :windowStartUtc "
            + "and bucket.bucketEndUtc <= :windowEndUtc "
            + "and bucket.durationBucketsJson is not null "
            + "order by bucket.bucketEndUtc asc")
    List<HistogramBucketEvidenceRow> findSummaryDurationBucketEvidenceRowsByApplicationId(
            @Param("applicationId") UUID applicationId,
            @Param("windowStartUtc") OffsetDateTime windowStartUtc,
            @Param("windowEndUtc") OffsetDateTime windowEndUtc);

    /**
     * endpoint priority read model을 위한 accepted bucket endpoints_json row를 조회한다.
     *
     * <p>projection은 bucket boundary와 raw JSON source만 전달하며, rule/rank/confidence/recommended action은
     * service layer에서 계산한다. endpoint p95/p99나 endpoint percentile rollup은 repository/service 어디에서도
     * 계산하지 않는다.</p>
     */
    @Query("select new com.observation.portal.domain.bucket.model.EndpointEvidenceRow("
            + "bucket.applicationId, "
            + "bucket.bucketStartUtc, "
            + "bucket.bucketEndUtc, "
            + "bucket.endpointsJson) "
            + "from AcceptedMetricBucketEntity bucket "
            + "where bucket.applicationId = :applicationId "
            + "and bucket.bucketEndUtc > :windowStartUtc "
            + "and bucket.bucketEndUtc <= :windowEndUtc "
            + "and bucket.endpointsJson is not null "
            + "order by bucket.bucketEndUtc asc")
    List<EndpointEvidenceRow> findEndpointEvidenceRowsByApplicationId(
            @Param("applicationId") UUID applicationId,
            @Param("windowStartUtc") OffsetDateTime windowStartUtc,
            @Param("windowEndUtc") OffsetDateTime windowEndUtc);

    /**
     * accepted bucket gap 판단용 최신 distinct bucket boundary row를 최신순으로 조회한다.
     */
    @Query("select distinct new com.observation.portal.domain.bucket.model.AcceptedBucketGapEvidenceRow("
            + "bucket.applicationId, "
            + "bucket.bucketStartUtc, "
            + "bucket.bucketEndUtc) "
            + "from AcceptedMetricBucketEntity bucket "
            + "where bucket.applicationId = :applicationId "
            + "and bucket.bucketEndUtc <= :evaluationAtUtc "
            + "order by bucket.bucketEndUtc desc")
    List<AcceptedBucketGapEvidenceRow> findAcceptedBucketGapEvidenceRowsByApplicationIdAtOrBefore(
            @Param("applicationId") UUID applicationId,
            @Param("evaluationAtUtc") OffsetDateTime evaluationAtUtc,
            Pageable pageable);

    /**
     * recent evidence 범위를 정하기 위해 distinct application-level 30초 bucket boundary를 최신순으로 조회한다.
     */
    @Query("select distinct new com.observation.portal.domain.bucket.model.AcceptedBucketBoundaryEvidenceRow("
            + "bucket.applicationId, "
            + "bucket.bucketStartUtc, "
            + "bucket.bucketEndUtc) "
            + "from AcceptedMetricBucketEntity bucket "
            + "where bucket.applicationId = :applicationId "
            + "and bucket.bucketEndUtc <= :evaluationAtUtc "
            + "order by bucket.bucketEndUtc desc")
    List<AcceptedBucketBoundaryEvidenceRow> findRecentBucketBoundaryEvidenceRowsByApplicationIdAtOrBefore(
            @Param("applicationId") UUID applicationId,
            @Param("evaluationAtUtc") OffsetDateTime evaluationAtUtc,
            Pageable pageable);

    /**
     * selected bucket boundary에 속한 bounded bucket evidence row를 최신순으로 조회한다.
     */
    @Query("select new com.observation.portal.domain.bucket.model.RecentBucketEvidenceRow("
            + "bucket.applicationId, "
            + "bucket.bucketStartUtc, "
            + "bucket.bucketEndUtc, "
            + "bucket.requestCount, "
            + "bucket.errorCount, "
            + "bucket.durationBucketsJson) "
            + "from AcceptedMetricBucketEntity bucket "
            + "where bucket.applicationId = :applicationId "
            + "and bucket.bucketEndUtc in :bucketEndUtcValues "
            + "order by bucket.bucketEndUtc desc")
    List<RecentBucketEvidenceRow> findRecentBucketEvidenceRowsByApplicationIdAndBucketEndUtcIn(
            @Param("applicationId") UUID applicationId,
            @Param("bucketEndUtcValues") List<OffsetDateTime> bucketEndUtcValues);

    /**
     * saturation hint에 사용할 current window latest runtime ratio row를 최신순으로 조회한다.
     */
    @Query("select new com.observation.portal.domain.bucket.model.RuntimeRatioEvidenceRow("
            + "bucket.applicationId, "
            + "bucket.bucketStartUtc, "
            + "bucket.bucketEndUtc, "
            + "bucket.cpuUsageRatio, "
            + "bucket.heapUsedRatio, "
            + "bucket.datasourcePoolUsageRatio) "
            + "from AcceptedMetricBucketEntity bucket "
            + "where bucket.applicationId = :applicationId "
            + "and bucket.bucketEndUtc > :windowStartUtc "
            + "and bucket.bucketEndUtc <= :windowEndUtc "
            + "and (bucket.cpuUsageRatio is not null "
            + "or bucket.heapUsedRatio is not null "
            + "or bucket.datasourcePoolUsageRatio is not null) "
            + "order by bucket.bucketEndUtc desc")
    List<RuntimeRatioEvidenceRow> findRuntimeRatioEvidenceRowsByApplicationId(
            @Param("applicationId") UUID applicationId,
            @Param("windowStartUtc") OffsetDateTime windowStartUtc,
            @Param("windowEndUtc") OffsetDateTime windowEndUtc,
            Pageable pageable);
}
