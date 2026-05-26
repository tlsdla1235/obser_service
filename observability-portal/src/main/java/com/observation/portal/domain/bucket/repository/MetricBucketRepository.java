package com.observation.portal.domain.bucket.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.portal.domain.bucket.entity.AcceptedMetricBucketEntity;
import com.observation.portal.domain.bucket.model.AcceptedBucketBoundaryEvidenceRow;
import com.observation.portal.domain.bucket.model.AcceptedBucketGapEvidence;
import com.observation.portal.domain.bucket.model.AcceptedBucketGapEvidenceRow;
import com.observation.portal.domain.bucket.model.AcceptedMetricBucketReceipt;
import com.observation.portal.domain.bucket.model.AcceptedMetricBucketWriteCommand;
import com.observation.portal.domain.bucket.model.EndpointEvidenceRow;
import com.observation.portal.domain.bucket.model.HistogramBucketEvidenceRow;
import com.observation.portal.domain.bucket.model.LocalPercentileEvidenceRow;
import com.observation.portal.domain.bucket.model.RecentBucketEvidenceRow;
import com.observation.portal.domain.bucket.model.RecentBucketEvidenceRows;
import com.observation.portal.domain.bucket.model.RuntimeRatioEvidenceRow;
import com.observation.portal.domain.bucket.model.WindowBucketAggregate;
import com.observation.portal.domain.catalog.model.ApplicationCatalogEntry;
import com.observation.portal.domain.catalog.repository.ApplicationCatalogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * validated metric bucket을 catalog row와 연결해 PostgreSQL에 저장하는 repository layer facade다.
 *
 * <p>JPA entity와 Spring Data repository를 내부 구현으로 숨기고, service에는 receipt 모델만 반환한다.</p>
 */
@Repository
public class MetricBucketRepository {

    private static final int RECENT_BUCKET_EVIDENCE_LIMIT = 5;

    private final AcceptedMetricBucketJpaRepository acceptedMetricBucketJpaRepository;
    private final ApplicationCatalogRepository applicationCatalogRepository;
    private final ObjectMapper objectMapper;

    /**
     * bucket 저장과 catalog get-or-create에 필요한 repository 구현체를 주입한다.
     */
    public MetricBucketRepository(
            AcceptedMetricBucketJpaRepository acceptedMetricBucketJpaRepository,
            ApplicationCatalogRepository applicationCatalogRepository,
            ObjectMapper objectMapper) {
        this.acceptedMetricBucketJpaRepository = Objects.requireNonNull(
                acceptedMetricBucketJpaRepository,
                "acceptedMetricBucketJpaRepository must not be null");
        this.applicationCatalogRepository = Objects.requireNonNull(
                applicationCatalogRepository,
                "applicationCatalogRepository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * application/application instance catalog row를 찾거나 만든 뒤 accepted bucket을 insert한다.
     */
    @Transactional
    public AcceptedMetricBucketReceipt insert(AcceptedMetricBucketWriteCommand command) {
        AcceptedMetricBucketWriteCommand requiredCommand = Objects.requireNonNull(
                command,
                "command must not be null");
        ApplicationCatalogEntry catalogEntry = applicationCatalogRepository.getOrCreate(
                requiredCommand.projectId(),
                requiredCommand.applicationName(),
                requiredCommand.environment(),
                requiredCommand.instanceName(),
                requiredCommand.acceptedAt());

        AcceptedMetricBucketEntity entity = new AcceptedMetricBucketEntity(
                UUID.randomUUID(),
                requiredCommand.projectId(),
                catalogEntry.applicationId(),
                catalogEntry.applicationInstanceId(),
                requiredCommand.schemaVersion(),
                requiredCommand.idempotencyKey(),
                requiredCommand.payloadHash(),
                requiredCommand.bucketStartUtc(),
                requiredCommand.bucketEndUtc(),
                requiredCommand.durationSeconds(),
                requiredCommand.acceptedAt(),
                requiredCommand.requestCount(),
                requiredCommand.errorCount(),
                writeJson(requiredCommand.durationBuckets()),
                toBigDecimal(requiredCommand.cpuUsageRatio()),
                toBigDecimal(requiredCommand.heapUsedRatio()),
                toBigDecimal(requiredCommand.datasourcePoolUsageRatio()),
                writeNullableJson(requiredCommand.localPercentiles()),
                writeJson(requiredCommand.endpoints()),
                requiredCommand.acceptedAt());

        return acceptedMetricBucketJpaRepository.saveAndFlush(entity).toReceipt();
    }

    /**
     * project scope idempotency key로 이미 저장된 bucket receipt를 조회한다.
     */
    @Transactional(readOnly = true)
    public Optional<AcceptedMetricBucketReceipt> findByProjectIdAndIdempotencyKey(
            UUID projectId,
            String idempotencyKey) {
        return acceptedMetricBucketJpaRepository.findByProjectIdAndIdempotencyKey(projectId, idempotencyKey)
                .map(AcceptedMetricBucketEntity::toReceipt);
    }

    /**
     * application scope의 마지막 accepted bucket endUtc timestamp만 조회한다.
     *
     * <p>freshness나 lifecycle state 의미 판단은 service/model 계층에서 수행한다.</p>
     */
    @Transactional(readOnly = true)
    public Optional<OffsetDateTime> findLatestBucketEndUtcByApplicationId(UUID applicationId) {
        return acceptedMetricBucketJpaRepository.findLatestBucketEndUtcByApplicationId(
                Objects.requireNonNull(applicationId, "applicationId must not be null"));
    }

    /**
     * evaluationAt 이후의 future bucket을 freshness source에서 제외하고 마지막 accepted endUtc만 조회한다.
     *
     * <p>dashboard current window boundary는 caller가 계산한 evaluationAt을 그대로 사용하며, 이 값은 freshness 입력으로만
     * 사용한다.</p>
     */
    @Transactional(readOnly = true)
    public Optional<OffsetDateTime> findLatestBucketEndUtcByApplicationIdAtOrBefore(
            UUID applicationId,
            Instant evaluationAtUtc) {
        return acceptedMetricBucketJpaRepository.findLatestBucketEndUtcByApplicationIdAtOrBefore(
                Objects.requireNonNull(applicationId, "applicationId must not be null"),
                toUtcOffsetDateTime(Objects.requireNonNull(evaluationAtUtc, "evaluationAtUtc must not be null")));
    }

    /**
     * dashboard current window의 request/error count 합계만 application scope로 조회한다.
     *
     * <p>window는 bucket endUtc 기준으로 `(start, end]`를 사용해 15분 window 끝 boundary bucket을 포함한다.</p>
     */
    @Transactional(readOnly = true)
    public WindowBucketAggregate findWindowAggregateByApplicationId(
            UUID applicationId,
            Instant windowStartUtc,
            Instant windowEndUtc) {
        UUID requiredApplicationId = Objects.requireNonNull(applicationId, "applicationId must not be null");
        Instant requiredWindowStartUtc = Objects.requireNonNull(windowStartUtc, "windowStartUtc must not be null");
        Instant requiredWindowEndUtc = Objects.requireNonNull(windowEndUtc, "windowEndUtc must not be null");
        if (!requiredWindowEndUtc.isAfter(requiredWindowStartUtc)) {
            throw new IllegalArgumentException("windowEndUtc must be after windowStartUtc");
        }
        OffsetDateTime windowStart = toUtcOffsetDateTime(requiredWindowStartUtc);
        OffsetDateTime windowEnd = toUtcOffsetDateTime(requiredWindowEndUtc);
        WindowBucketAggregate aggregate = acceptedMetricBucketJpaRepository
                .sumWindowRequestAndErrorCountsByApplicationId(requiredApplicationId, windowStart, windowEnd);
        return Objects.requireNonNullElse(aggregate, WindowBucketAggregate.zero());
    }

    /**
     * dashboard current window에서 instance bucket scope local percentile JSON evidence row를 조회한다.
     *
     * <p>조회는 application scope와 `(start, end]` bucket end boundary, `local_percentiles_json is not null` 조건만 적용하고
     * p95/p99 rollup이나 source 판단은 service에 남긴다.</p>
     */
    @Transactional(readOnly = true)
    public List<LocalPercentileEvidenceRow> findLocalPercentileEvidenceRowsByApplicationId(
            UUID applicationId,
            Instant windowStartUtc,
            Instant windowEndUtc) {
        UUID requiredApplicationId = Objects.requireNonNull(applicationId, "applicationId must not be null");
        OffsetDateTime windowStart = toUtcOffsetDateTime(
                Objects.requireNonNull(windowStartUtc, "windowStartUtc must not be null"));
        OffsetDateTime windowEnd = toUtcOffsetDateTime(
                Objects.requireNonNull(windowEndUtc, "windowEndUtc must not be null"));
        validateWindow(windowStart, windowEnd);
        return acceptedMetricBucketJpaRepository.findLocalPercentileEvidenceRowsByApplicationId(
                requiredApplicationId,
                windowStart,
                windowEnd);
    }

    /**
     * dashboard histogram distribution용 application summary duration bucket JSON evidence row를 조회한다.
     *
     * <p>`endpoints_json`은 읽지 않으며, current/baseline 판단과 boundary mismatch guard는 service에서 수행한다.</p>
     */
    @Transactional(readOnly = true)
    public List<HistogramBucketEvidenceRow> findSummaryDurationBucketEvidenceRowsByApplicationId(
            UUID applicationId,
            Instant windowStartUtc,
            Instant windowEndUtc) {
        UUID requiredApplicationId = Objects.requireNonNull(applicationId, "applicationId must not be null");
        OffsetDateTime windowStart = toUtcOffsetDateTime(
                Objects.requireNonNull(windowStartUtc, "windowStartUtc must not be null"));
        OffsetDateTime windowEnd = toUtcOffsetDateTime(
                Objects.requireNonNull(windowEndUtc, "windowEndUtc must not be null"));
        validateWindow(windowStart, windowEnd);
        return acceptedMetricBucketJpaRepository.findSummaryDurationBucketEvidenceRowsByApplicationId(
                requiredApplicationId,
                windowStart,
                windowEnd);
    }

    /**
     * endpoint priority read model용 accepted bucket endpoint JSON evidence row를 조회한다.
     *
     * <p>조회는 application scope와 `(start, end]` bucket end boundary, `endpoints_json is not null` 조건만 적용한다.
     * repository는 endpoint rule, confidence, rank, recommended action을 계산하지 않으며, endpoint p95/p99나 endpoint
     * percentile rollup은 repository/service 어디에서도 계산하지 않는다.</p>
     */
    @Transactional(readOnly = true)
    public List<EndpointEvidenceRow> findEndpointEvidenceRowsByApplicationId(
            UUID applicationId,
            Instant windowStartUtc,
            Instant windowEndUtc) {
        UUID requiredApplicationId = Objects.requireNonNull(applicationId, "applicationId must not be null");
        OffsetDateTime windowStart = toUtcOffsetDateTime(
                Objects.requireNonNull(windowStartUtc, "windowStartUtc must not be null"));
        OffsetDateTime windowEnd = toUtcOffsetDateTime(
                Objects.requireNonNull(windowEndUtc, "windowEndUtc must not be null"));
        validateWindow(windowStart, windowEnd);
        return acceptedMetricBucketJpaRepository.findEndpointEvidenceRowsByApplicationId(
                requiredApplicationId,
                windowStart,
                windowEnd);
    }

    /**
     * latest accepted bucket과 직전 bucket boundary를 최신순 projection으로 읽어 lightweight gap 근거를 만든다.
     *
     * <p>이 method는 state/recovery/p95/p99/endpoint priority를 계산하지 않고, timestamp projection만 service에
     * 전달한다.</p>
     */
    @Transactional(readOnly = true)
    public Optional<AcceptedBucketGapEvidence> findAcceptedBucketGapEvidenceByApplicationIdAtOrBefore(
            UUID applicationId,
            Instant evaluationAtUtc) {
        UUID requiredApplicationId = Objects.requireNonNull(applicationId, "applicationId must not be null");
        OffsetDateTime evaluationAt = toUtcOffsetDateTime(
                Objects.requireNonNull(evaluationAtUtc, "evaluationAtUtc must not be null"));
        List<AcceptedBucketGapEvidenceRow> rows =
                acceptedMetricBucketJpaRepository.findAcceptedBucketGapEvidenceRowsByApplicationIdAtOrBefore(
                        requiredApplicationId,
                        evaluationAt,
                        PageRequest.of(0, 2));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        AcceptedBucketGapEvidenceRow latest = rows.get(0);
        Optional<OffsetDateTime> previous = rows.size() > 1
                ? Optional.of(rows.get(1).bucketEndUtc())
                : Optional.empty();
        return Optional.of(new AcceptedBucketGapEvidence(latest.bucketEndUtc(), previous));
    }

    /**
     * degraded enter guard가 사용할 최근 5개 distinct 30초 bucket의 bounded evidence를 최신순으로 조회한다.
     *
     * <p>같은 boundary의 여러 instance row는 application-level bucket evidence로 합치되, repository는 bad bucket, rule,
     * state, confidence, endpoint priority 판단은 하지 않는다.</p>
     */
    @Transactional(readOnly = true)
    public List<RecentBucketEvidenceRow> findRecentFiveBucketEvidenceRowsByApplicationIdAtOrBefore(
            UUID applicationId,
            Instant evaluationAtUtc) {
        UUID requiredApplicationId = Objects.requireNonNull(applicationId, "applicationId must not be null");
        OffsetDateTime evaluationAt = toUtcOffsetDateTime(
                Objects.requireNonNull(evaluationAtUtc, "evaluationAtUtc must not be null"));
        List<AcceptedBucketBoundaryEvidenceRow> boundaries =
                acceptedMetricBucketJpaRepository.findRecentBucketBoundaryEvidenceRowsByApplicationIdAtOrBefore(
                        requiredApplicationId,
                        evaluationAt,
                        PageRequest.of(0, RECENT_BUCKET_EVIDENCE_LIMIT));
        if (boundaries.isEmpty()) {
            return List.of();
        }
        List<OffsetDateTime> bucketEndUtcValues = boundaries.stream()
                .map(AcceptedBucketBoundaryEvidenceRow::bucketEndUtc)
                .toList();
        List<RecentBucketEvidenceRow> rows =
                acceptedMetricBucketJpaRepository.findRecentBucketEvidenceRowsByApplicationIdAndBucketEndUtcIn(
                        requiredApplicationId,
                        bucketEndUtcValues);
        return RecentBucketEvidenceRows.applicationLevelBuckets(rows, RECENT_BUCKET_EVIDENCE_LIMIT, objectMapper);
    }

    /**
     * current window 안 latest runtime ratio sample projection을 조회한다.
     *
     * <p>ratio latest sample은 saturation hint evidence일 뿐이며 repository는 state/rule/recovery/root cause나
     * endpoint priority를 계산하지 않는다.</p>
     */
    @Transactional(readOnly = true)
    public Optional<RuntimeRatioEvidenceRow> findLatestRuntimeRatioEvidenceRowByApplicationId(
            UUID applicationId,
            Instant windowStartUtc,
            Instant windowEndUtc) {
        UUID requiredApplicationId = Objects.requireNonNull(applicationId, "applicationId must not be null");
        OffsetDateTime windowStart = toUtcOffsetDateTime(
                Objects.requireNonNull(windowStartUtc, "windowStartUtc must not be null"));
        OffsetDateTime windowEnd = toUtcOffsetDateTime(
                Objects.requireNonNull(windowEndUtc, "windowEndUtc must not be null"));
        validateWindow(windowStart, windowEnd);
        return acceptedMetricBucketJpaRepository.findRuntimeRatioEvidenceRowsByApplicationId(
                        requiredApplicationId,
                        windowStart,
                        windowEnd,
                        PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("accepted bucket JSON serialization failed", exception);
        }
    }

    private String writeNullableJson(Object value) {
        return value == null ? null : writeJson(value);
    }

    private static BigDecimal toBigDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private static void validateWindow(OffsetDateTime windowStartUtc, OffsetDateTime windowEndUtc) {
        if (!windowEndUtc.isAfter(windowStartUtc)) {
            throw new IllegalArgumentException("windowEndUtc must be after windowStartUtc");
        }
    }

    private static OffsetDateTime toUtcOffsetDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
