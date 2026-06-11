package com.observation.portal.domain.bucket.repository;

import com.observation.portal.domain.bucket.entity.AcceptedMetricBucketEntity;
import com.observation.portal.domain.bucket.model.AcceptedBucketBoundaryEvidenceRow;
import com.observation.portal.domain.bucket.model.AcceptedBucketGapEvidenceRow;
import com.observation.portal.domain.bucket.model.AcceptedMetricBucketIdentity;
import com.observation.portal.domain.bucket.model.EndpointEvidenceRow;
import com.observation.portal.domain.bucket.model.HistogramBucketEvidenceRow;
import com.observation.portal.domain.bucket.model.LocalPercentileEvidenceRow;
import com.observation.portal.domain.bucket.model.RecentBucketEvidenceRow;
import com.observation.portal.domain.bucket.model.RuntimeRatioEvidenceRow;
import com.observation.portal.domain.bucket.model.WindowBucketAggregate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
     * worker duplicate/no-op 분류에 필요한 저장 identity만 project/idempotency key로 조회한다.
     */
    @Query("select new com.observation.portal.domain.bucket.model.AcceptedMetricBucketIdentity("
            + "bucket.id, "
            + "bucket.projectId, "
            + "bucket.applicationId, "
            + "bucket.applicationInstanceId, "
            + "bucket.idempotencyKey, "
            + "bucket.payloadHash, "
            + "bucket.bucketStartUtc, "
            + "bucket.bucketEndUtc, "
            + "bucket.acceptedAt) "
            + "from AcceptedMetricBucketEntity bucket "
            + "where bucket.projectId = :projectId "
            + "and bucket.idempotencyKey = :idempotencyKey")
    Optional<AcceptedMetricBucketIdentity> findIdentityByProjectIdAndIdempotencyKey(
            @Param("projectId") UUID projectId,
            @Param("idempotencyKey") String idempotencyKey);

    /**
     * application identity와 30초 bucket start 기준 unique constraint에 대응하는 저장 identity를 조회한다.
     */
    @Query("select new com.observation.portal.domain.bucket.model.AcceptedMetricBucketIdentity("
            + "bucket.id, "
            + "bucket.projectId, "
            + "bucket.applicationId, "
            + "bucket.applicationInstanceId, "
            + "bucket.idempotencyKey, "
            + "bucket.payloadHash, "
            + "bucket.bucketStartUtc, "
            + "bucket.bucketEndUtc, "
            + "bucket.acceptedAt) "
            + "from AcceptedMetricBucketEntity bucket, ApplicationEntity application, ApplicationInstanceEntity instance "
            + "where application.id = bucket.applicationId "
            + "and instance.id = bucket.applicationInstanceId "
            + "and application.projectId = :projectId "
            + "and application.name = :applicationName "
            + "and application.environment = :environment "
            + "and instance.instanceName = :instanceName "
            + "and bucket.bucketStartUtc = :bucketStartUtc")
    Optional<AcceptedMetricBucketIdentity> findIdentityByProjectApplicationInstanceAndBucketStartUtc(
            @Param("projectId") UUID projectId,
            @Param("applicationName") String applicationName,
            @Param("environment") String environment,
            @Param("instanceName") String instanceName,
            @Param("bucketStartUtc") OffsetDateTime bucketStartUtc);

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
     * snapshot freshness 입력으로 bucket_end와 accepted_at cutoff를 모두 만족하는 마지막 endUtc를 조회한다.
     */
    @Query("select max(bucket.bucketEndUtc) from AcceptedMetricBucketEntity bucket "
            + "where bucket.applicationId = :applicationId "
            + "and bucket.bucketEndUtc <= :evaluationAtUtc "
            + "and bucket.acceptedAt <= :acceptedAtCutoffUtc")
    Optional<OffsetDateTime> findLatestBucketEndUtcByApplicationIdAtOrBeforeAcceptedAt(
            @Param("applicationId") UUID applicationId,
            @Param("evaluationAtUtc") OffsetDateTime evaluationAtUtc,
            @Param("acceptedAtCutoffUtc") OffsetDateTime acceptedAtCutoffUtc);

    /**
     * instance evidence freshness 입력으로 evaluationAt 이후 future bucket을 제외한 selected instance 마지막 endUtc를 조회한다.
     */
    @Query("select max(bucket.bucketEndUtc) from AcceptedMetricBucketEntity bucket "
            + "where bucket.applicationInstanceId = :applicationInstanceId "
            + "and bucket.bucketEndUtc <= :evaluationAtUtc")
    Optional<OffsetDateTime> findLatestBucketEndUtcByApplicationInstanceIdAtOrBefore(
            @Param("applicationInstanceId") UUID applicationInstanceId,
            @Param("evaluationAtUtc") OffsetDateTime evaluationAtUtc);

    /**
     * snapshot instance freshness 입력으로 bucket_end와 accepted_at cutoff를 모두 만족하는 마지막 endUtc를 조회한다.
     */
    @Query("select max(bucket.bucketEndUtc) from AcceptedMetricBucketEntity bucket "
            + "where bucket.applicationInstanceId = :applicationInstanceId "
            + "and bucket.bucketEndUtc <= :evaluationAtUtc "
            + "and bucket.acceptedAt <= :acceptedAtCutoffUtc")
    Optional<OffsetDateTime> findLatestBucketEndUtcByApplicationInstanceIdAtOrBeforeAcceptedAt(
            @Param("applicationInstanceId") UUID applicationInstanceId,
            @Param("evaluationAtUtc") OffsetDateTime evaluationAtUtc,
            @Param("acceptedAtCutoffUtc") OffsetDateTime acceptedAtCutoffUtc);

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
     * snapshot application window count 합계를 accepted_at cutoff 이전 row만으로 계산한다.
     */
    @Query("select new com.observation.portal.domain.bucket.model.WindowBucketAggregate("
            + "coalesce(sum(bucket.requestCount), 0L), "
            + "coalesce(sum(bucket.errorCount), 0L)) "
            + "from AcceptedMetricBucketEntity bucket "
            + "where bucket.applicationId = :applicationId "
            + "and bucket.bucketEndUtc > :windowStartUtc "
            + "and bucket.bucketEndUtc <= :windowEndUtc "
            + "and bucket.acceptedAt <= :acceptedAtCutoffUtc")
    WindowBucketAggregate sumWindowRequestAndErrorCountsByApplicationIdAcceptedAtOrBefore(
            @Param("applicationId") UUID applicationId,
            @Param("windowStartUtc") OffsetDateTime windowStartUtc,
            @Param("windowEndUtc") OffsetDateTime windowEndUtc,
            @Param("acceptedAtCutoffUtc") OffsetDateTime acceptedAtCutoffUtc);

    /**
     * instance evidence current window에 포함되는 selected instance bucket request/error count 합계만 조회한다.
     */
    @Query("select new com.observation.portal.domain.bucket.model.WindowBucketAggregate("
            + "coalesce(sum(bucket.requestCount), 0L), "
            + "coalesce(sum(bucket.errorCount), 0L)) "
            + "from AcceptedMetricBucketEntity bucket "
            + "where bucket.applicationInstanceId = :applicationInstanceId "
            + "and bucket.bucketEndUtc > :windowStartUtc "
            + "and bucket.bucketEndUtc <= :windowEndUtc")
    WindowBucketAggregate sumWindowRequestAndErrorCountsByApplicationInstanceId(
            @Param("applicationInstanceId") UUID applicationInstanceId,
            @Param("windowStartUtc") OffsetDateTime windowStartUtc,
            @Param("windowEndUtc") OffsetDateTime windowEndUtc);

    /**
     * snapshot instance window count 합계를 accepted_at cutoff 이전 row만으로 계산한다.
     */
    @Query("select new com.observation.portal.domain.bucket.model.WindowBucketAggregate("
            + "coalesce(sum(bucket.requestCount), 0L), "
            + "coalesce(sum(bucket.errorCount), 0L)) "
            + "from AcceptedMetricBucketEntity bucket "
            + "where bucket.applicationInstanceId = :applicationInstanceId "
            + "and bucket.bucketEndUtc > :windowStartUtc "
            + "and bucket.bucketEndUtc <= :windowEndUtc "
            + "and bucket.acceptedAt <= :acceptedAtCutoffUtc")
    WindowBucketAggregate sumWindowRequestAndErrorCountsByApplicationInstanceIdAcceptedAtOrBefore(
            @Param("applicationInstanceId") UUID applicationInstanceId,
            @Param("windowStartUtc") OffsetDateTime windowStartUtc,
            @Param("windowEndUtc") OffsetDateTime windowEndUtc,
            @Param("acceptedAtCutoffUtc") OffsetDateTime acceptedAtCutoffUtc);

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
     * snapshot source-scoped percentile evidence를 accepted_at cutoff 이전 application row만으로 조회한다.
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
            + "and bucket.acceptedAt <= :acceptedAtCutoffUtc "
            + "and bucket.localPercentilesJson is not null "
            + "order by instance.instanceName asc, bucket.bucketEndUtc asc")
    List<LocalPercentileEvidenceRow> findLocalPercentileEvidenceRowsByApplicationIdAcceptedAtOrBefore(
            @Param("applicationId") UUID applicationId,
            @Param("windowStartUtc") OffsetDateTime windowStartUtc,
            @Param("windowEndUtc") OffsetDateTime windowEndUtc,
            @Param("acceptedAtCutoffUtc") OffsetDateTime acceptedAtCutoffUtc);

    /**
     * instance evidence percentile series를 위한 selected instance local percentile JSON과 boundary를 조회한다.
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
            + "and bucket.applicationInstanceId = :applicationInstanceId "
            + "and bucket.bucketEndUtc > :windowStartUtc "
            + "and bucket.bucketEndUtc <= :windowEndUtc "
            + "and bucket.localPercentilesJson is not null "
            + "order by bucket.bucketEndUtc asc")
    List<LocalPercentileEvidenceRow> findLocalPercentileEvidenceRowsByApplicationInstanceId(
            @Param("applicationInstanceId") UUID applicationInstanceId,
            @Param("windowStartUtc") OffsetDateTime windowStartUtc,
            @Param("windowEndUtc") OffsetDateTime windowEndUtc);

    /**
     * snapshot instance percentile evidence를 accepted_at cutoff 이전 selected instance row만으로 조회한다.
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
            + "and bucket.applicationInstanceId = :applicationInstanceId "
            + "and bucket.bucketEndUtc > :windowStartUtc "
            + "and bucket.bucketEndUtc <= :windowEndUtc "
            + "and bucket.acceptedAt <= :acceptedAtCutoffUtc "
            + "and bucket.localPercentilesJson is not null "
            + "order by bucket.bucketEndUtc asc")
    List<LocalPercentileEvidenceRow> findLocalPercentileEvidenceRowsByApplicationInstanceIdAcceptedAtOrBefore(
            @Param("applicationInstanceId") UUID applicationInstanceId,
            @Param("windowStartUtc") OffsetDateTime windowStartUtc,
            @Param("windowEndUtc") OffsetDateTime windowEndUtc,
            @Param("acceptedAtCutoffUtc") OffsetDateTime acceptedAtCutoffUtc);

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
     * snapshot histogram evidence를 accepted_at cutoff 이전 application row만으로 조회한다.
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
            + "and bucket.acceptedAt <= :acceptedAtCutoffUtc "
            + "and bucket.durationBucketsJson is not null "
            + "order by bucket.bucketEndUtc asc")
    List<HistogramBucketEvidenceRow> findSummaryDurationBucketEvidenceRowsByApplicationIdAcceptedAtOrBefore(
            @Param("applicationId") UUID applicationId,
            @Param("windowStartUtc") OffsetDateTime windowStartUtc,
            @Param("windowEndUtc") OffsetDateTime windowEndUtc,
            @Param("acceptedAtCutoffUtc") OffsetDateTime acceptedAtCutoffUtc);

    /**
     * instance evidence histogram distribution을 위한 selected instance summary duration bucket JSON row를 조회한다.
     */
    @Query("select new com.observation.portal.domain.bucket.model.HistogramBucketEvidenceRow("
            + "bucket.applicationId, "
            + "bucket.bucketStartUtc, "
            + "bucket.bucketEndUtc, "
            + "bucket.durationBucketsJson) "
            + "from AcceptedMetricBucketEntity bucket "
            + "where bucket.applicationInstanceId = :applicationInstanceId "
            + "and bucket.bucketEndUtc > :windowStartUtc "
            + "and bucket.bucketEndUtc <= :windowEndUtc "
            + "and bucket.durationBucketsJson is not null "
            + "order by bucket.bucketEndUtc asc")
    List<HistogramBucketEvidenceRow> findSummaryDurationBucketEvidenceRowsByApplicationInstanceId(
            @Param("applicationInstanceId") UUID applicationInstanceId,
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
     * snapshot endpoint priority evidence를 accepted_at cutoff 이전 application row만으로 조회한다.
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
            + "and bucket.acceptedAt <= :acceptedAtCutoffUtc "
            + "and bucket.endpointsJson is not null "
            + "order by bucket.bucketEndUtc asc")
    List<EndpointEvidenceRow> findEndpointEvidenceRowsByApplicationIdAcceptedAtOrBefore(
            @Param("applicationId") UUID applicationId,
            @Param("windowStartUtc") OffsetDateTime windowStartUtc,
            @Param("windowEndUtc") OffsetDateTime windowEndUtc,
            @Param("acceptedAtCutoffUtc") OffsetDateTime acceptedAtCutoffUtc);

    /**
     * instance evidence endpoint subset을 위한 selected instance endpoints_json row를 조회한다.
     *
     * <p>projection은 bucket boundary와 JSON source만 전달하고, endpoint presence/share/display order나
     * rule/rank/confidence/recommended action, endpoint p95/p99는 계산하지 않는다.</p>
     */
    @Query("select new com.observation.portal.domain.bucket.model.EndpointEvidenceRow("
            + "bucket.applicationId, "
            + "bucket.bucketStartUtc, "
            + "bucket.bucketEndUtc, "
            + "bucket.endpointsJson) "
            + "from AcceptedMetricBucketEntity bucket "
            + "where bucket.applicationInstanceId = :applicationInstanceId "
            + "and bucket.bucketEndUtc > :windowStartUtc "
            + "and bucket.bucketEndUtc <= :windowEndUtc "
            + "and bucket.endpointsJson is not null "
            + "order by bucket.bucketEndUtc asc")
    List<EndpointEvidenceRow> findEndpointEvidenceRowsByApplicationInstanceId(
            @Param("applicationInstanceId") UUID applicationInstanceId,
            @Param("windowStartUtc") OffsetDateTime windowStartUtc,
            @Param("windowEndUtc") OffsetDateTime windowEndUtc);

    /**
     * snapshot instance endpoint refs를 accepted_at cutoff 이전 selected instance row만으로 조회한다.
     */
    @Query("select new com.observation.portal.domain.bucket.model.EndpointEvidenceRow("
            + "bucket.applicationId, "
            + "bucket.bucketStartUtc, "
            + "bucket.bucketEndUtc, "
            + "bucket.endpointsJson) "
            + "from AcceptedMetricBucketEntity bucket "
            + "where bucket.applicationInstanceId = :applicationInstanceId "
            + "and bucket.bucketEndUtc > :windowStartUtc "
            + "and bucket.bucketEndUtc <= :windowEndUtc "
            + "and bucket.acceptedAt <= :acceptedAtCutoffUtc "
            + "and bucket.endpointsJson is not null "
            + "order by bucket.bucketEndUtc asc")
    List<EndpointEvidenceRow> findEndpointEvidenceRowsByApplicationInstanceIdAcceptedAtOrBefore(
            @Param("applicationInstanceId") UUID applicationInstanceId,
            @Param("windowStartUtc") OffsetDateTime windowStartUtc,
            @Param("windowEndUtc") OffsetDateTime windowEndUtc,
            @Param("acceptedAtCutoffUtc") OffsetDateTime acceptedAtCutoffUtc);

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
     * snapshot gap evidence boundary를 accepted_at cutoff 이전 row만으로 조회한다.
     */
    @Query("select distinct new com.observation.portal.domain.bucket.model.AcceptedBucketGapEvidenceRow("
            + "bucket.applicationId, "
            + "bucket.bucketStartUtc, "
            + "bucket.bucketEndUtc) "
            + "from AcceptedMetricBucketEntity bucket "
            + "where bucket.applicationId = :applicationId "
            + "and bucket.bucketEndUtc <= :evaluationAtUtc "
            + "and bucket.acceptedAt <= :acceptedAtCutoffUtc "
            + "order by bucket.bucketEndUtc desc")
    List<AcceptedBucketGapEvidenceRow> findAcceptedBucketGapEvidenceRowsByApplicationIdAtOrBeforeAcceptedAt(
            @Param("applicationId") UUID applicationId,
            @Param("evaluationAtUtc") OffsetDateTime evaluationAtUtc,
            @Param("acceptedAtCutoffUtc") OffsetDateTime acceptedAtCutoffUtc,
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
     * snapshot recent evidence boundary를 accepted_at cutoff 이전 row만으로 조회한다.
     */
    @Query("select distinct new com.observation.portal.domain.bucket.model.AcceptedBucketBoundaryEvidenceRow("
            + "bucket.applicationId, "
            + "bucket.bucketStartUtc, "
            + "bucket.bucketEndUtc) "
            + "from AcceptedMetricBucketEntity bucket "
            + "where bucket.applicationId = :applicationId "
            + "and bucket.bucketEndUtc <= :evaluationAtUtc "
            + "and bucket.acceptedAt <= :acceptedAtCutoffUtc "
            + "order by bucket.bucketEndUtc desc")
    List<AcceptedBucketBoundaryEvidenceRow> findRecentBucketBoundaryEvidenceRowsByApplicationIdAtOrBeforeAcceptedAt(
            @Param("applicationId") UUID applicationId,
            @Param("evaluationAtUtc") OffsetDateTime evaluationAtUtc,
            @Param("acceptedAtCutoffUtc") OffsetDateTime acceptedAtCutoffUtc,
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
     * snapshot recent evidence row 재조회도 accepted_at cutoff 이전 row만 포함한다.
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
            + "and bucket.acceptedAt <= :acceptedAtCutoffUtc "
            + "order by bucket.bucketEndUtc desc")
    List<RecentBucketEvidenceRow> findRecentBucketEvidenceRowsByApplicationIdAndBucketEndUtcInAcceptedAtOrBefore(
            @Param("applicationId") UUID applicationId,
            @Param("bucketEndUtcValues") List<OffsetDateTime> bucketEndUtcValues,
            @Param("acceptedAtCutoffUtc") OffsetDateTime acceptedAtCutoffUtc);

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

    /**
     * snapshot runtime ratio hint를 accepted_at cutoff 이전 application row만으로 조회한다.
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
            + "and bucket.acceptedAt <= :acceptedAtCutoffUtc "
            + "and (bucket.cpuUsageRatio is not null "
            + "or bucket.heapUsedRatio is not null "
            + "or bucket.datasourcePoolUsageRatio is not null) "
            + "order by bucket.bucketEndUtc desc")
    List<RuntimeRatioEvidenceRow> findRuntimeRatioEvidenceRowsByApplicationIdAcceptedAtOrBefore(
            @Param("applicationId") UUID applicationId,
            @Param("windowStartUtc") OffsetDateTime windowStartUtc,
            @Param("windowEndUtc") OffsetDateTime windowEndUtc,
            @Param("acceptedAtCutoffUtc") OffsetDateTime acceptedAtCutoffUtc,
            Pageable pageable);

    /**
     * instance evidence resource hints에 사용할 selected instance current window latest runtime ratio row를 최신순 조회한다.
     */
    @Query("select new com.observation.portal.domain.bucket.model.RuntimeRatioEvidenceRow("
            + "bucket.applicationId, "
            + "bucket.bucketStartUtc, "
            + "bucket.bucketEndUtc, "
            + "bucket.cpuUsageRatio, "
            + "bucket.heapUsedRatio, "
            + "bucket.datasourcePoolUsageRatio) "
            + "from AcceptedMetricBucketEntity bucket "
            + "where bucket.applicationInstanceId = :applicationInstanceId "
            + "and bucket.bucketEndUtc > :windowStartUtc "
            + "and bucket.bucketEndUtc <= :windowEndUtc "
            + "and (bucket.cpuUsageRatio is not null "
            + "or bucket.heapUsedRatio is not null "
            + "or bucket.datasourcePoolUsageRatio is not null) "
            + "order by bucket.bucketEndUtc desc")
    List<RuntimeRatioEvidenceRow> findRuntimeRatioEvidenceRowsByApplicationInstanceId(
            @Param("applicationInstanceId") UUID applicationInstanceId,
            @Param("windowStartUtc") OffsetDateTime windowStartUtc,
            @Param("windowEndUtc") OffsetDateTime windowEndUtc,
            Pageable pageable);

    /**
     * snapshot instance runtime ratio hint를 accepted_at cutoff 이전 selected instance row만으로 조회한다.
     */
    @Query("select new com.observation.portal.domain.bucket.model.RuntimeRatioEvidenceRow("
            + "bucket.applicationId, "
            + "bucket.bucketStartUtc, "
            + "bucket.bucketEndUtc, "
            + "bucket.cpuUsageRatio, "
            + "bucket.heapUsedRatio, "
            + "bucket.datasourcePoolUsageRatio) "
            + "from AcceptedMetricBucketEntity bucket "
            + "where bucket.applicationInstanceId = :applicationInstanceId "
            + "and bucket.bucketEndUtc > :windowStartUtc "
            + "and bucket.bucketEndUtc <= :windowEndUtc "
            + "and bucket.acceptedAt <= :acceptedAtCutoffUtc "
            + "and (bucket.cpuUsageRatio is not null "
            + "or bucket.heapUsedRatio is not null "
            + "or bucket.datasourcePoolUsageRatio is not null) "
            + "order by bucket.bucketEndUtc desc")
    List<RuntimeRatioEvidenceRow> findRuntimeRatioEvidenceRowsByApplicationInstanceIdAcceptedAtOrBefore(
            @Param("applicationInstanceId") UUID applicationInstanceId,
            @Param("windowStartUtc") OffsetDateTime windowStartUtc,
            @Param("windowEndUtc") OffsetDateTime windowEndUtc,
            @Param("acceptedAtCutoffUtc") OffsetDateTime acceptedAtCutoffUtc,
            Pageable pageable);

    /**
     * cleanup cutoff보다 오래된 accepted bucket row를 bucket end 기준으로 물리 삭제한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AcceptedMetricBucketEntity bucket "
            + "where bucket.bucketEndUtc < :metricEvidenceCutoffUtc")
    int deleteAcceptedMetricBucketsEndedBefore(
            @Param("metricEvidenceCutoffUtc") OffsetDateTime metricEvidenceCutoffUtc);
}
