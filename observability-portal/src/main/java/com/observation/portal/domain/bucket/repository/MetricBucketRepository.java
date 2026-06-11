package com.observation.portal.domain.bucket.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.portal.domain.bucket.entity.AcceptedMetricBucketEntity;
import com.observation.portal.domain.bucket.model.AcceptedMetricBucketBatchItemResult;
import com.observation.portal.domain.bucket.model.AcceptedMetricBucketBatchWriteResult;
import com.observation.portal.domain.bucket.model.AcceptedBucketBoundaryEvidenceRow;
import com.observation.portal.domain.bucket.model.AcceptedBucketGapEvidence;
import com.observation.portal.domain.bucket.model.AcceptedBucketGapEvidenceRow;
import com.observation.portal.domain.bucket.model.AcceptedMetricBucketIdentity;
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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * bucket 저장과 catalog get-or-create에 필요한 repository 구현체를 주입한다.
     */
    public MetricBucketRepository(
            AcceptedMetricBucketJpaRepository acceptedMetricBucketJpaRepository,
            ApplicationCatalogRepository applicationCatalogRepository,
            ObjectMapper objectMapper,
            NamedParameterJdbcTemplate jdbcTemplate) {
        this.acceptedMetricBucketJpaRepository = Objects.requireNonNull(
                acceptedMetricBucketJpaRepository,
                "acceptedMetricBucketJpaRepository must not be null");
        this.applicationCatalogRepository = Objects.requireNonNull(
                applicationCatalogRepository,
                "applicationCatalogRepository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
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
     * bounded worker batch를 PostgreSQL multi-row insert로 저장하고 command별 duplicate/conflict 결과를 반환한다.
     *
     * <p>기본 전략은 bulk pre-read, catalog grouping, `ON CONFLICT DO NOTHING RETURNING`, post-read classification이다.
     * JPA `saveAll`이나 plain JDBC batch를 쓰지 않는 이유는 conflict row가 전체 batch를 실패시키지 않게 하고, 반환되지 않은
     * row를 deterministic duplicate/conflict로 다시 분류하기 위해서다.</p>
     */
    @Transactional
    public AcceptedMetricBucketBatchWriteResult insertBatch(List<AcceptedMetricBucketWriteCommand> commands) {
        List<AcceptedMetricBucketWriteCommand> requiredCommands = List.copyOf(
                Objects.requireNonNull(commands, "commands must not be null"));
        if (requiredCommands.isEmpty()) {
            return AcceptedMetricBucketBatchWriteResult.empty();
        }

        int statementCount = 0;
        List<AcceptedMetricBucketBatchItemResult> results =
                new ArrayList<>(Collections.nCopies(requiredCommands.size(), null));

        Map<CommandIdempotencyKey, AcceptedMetricBucketIdentity> identitiesByKey =
                findIdentitiesByIdempotencyKeys(requiredCommands);
        statementCount++;
        Map<CommandInstanceKey, AcceptedMetricBucketIdentity> identitiesByInstance =
                findIdentitiesByInstanceBoundaries(requiredCommands);
        statementCount++;

        Set<CommandIdempotencyKey> electedIdempotencyKeys = new java.util.LinkedHashSet<>();
        Set<CommandInstanceKey> electedInstanceKeys = new java.util.LinkedHashSet<>();
        List<IndexedCommand> insertCandidates = new ArrayList<>();
        List<IndexedCommand> postReadCandidates = new ArrayList<>();

        for (int index = 0; index < requiredCommands.size(); index++) {
            AcceptedMetricBucketWriteCommand command = requiredCommands.get(index);
            Optional<AcceptedMetricBucketBatchItemResult> existing =
                    classifyWithIdentities(command, identitiesByKey, identitiesByInstance);
            if (existing.isPresent()) {
                results.set(index, existing.orElseThrow());
                continue;
            }

            CommandIdempotencyKey idempotencyKey = CommandIdempotencyKey.from(command);
            CommandInstanceKey instanceKey = CommandInstanceKey.from(command);
            boolean elected = electedIdempotencyKeys.add(idempotencyKey) && electedInstanceKeys.add(instanceKey);
            if (elected) {
                insertCandidates.add(new IndexedCommand(index, command));
            } else {
                postReadCandidates.add(new IndexedCommand(index, command));
            }
        }

        if (!insertCandidates.isEmpty()) {
            List<AcceptedMetricBucketWriteCommand> insertCommands = insertCandidates.stream()
                    .map(IndexedCommand::command)
                    .toList();
            List<ApplicationCatalogEntry> catalogEntries = applicationCatalogRepository.getOrCreateBatch(insertCommands);
            Map<CommandIdempotencyKey, AcceptedMetricBucketIdentity> inserted =
                    insertRowsReturningIdentities(insertCandidates, catalogEntries);
            statementCount++;
            for (IndexedCommand candidate : insertCandidates) {
                AcceptedMetricBucketIdentity identity = inserted.get(CommandIdempotencyKey.from(candidate.command()));
                if (identity == null) {
                    postReadCandidates.add(candidate);
                } else {
                    results.set(candidate.index(), AcceptedMetricBucketBatchItemResult.inserted(
                            candidate.command(),
                            new AcceptedMetricBucketReceipt(identity.bucketId(), identity.acceptedAt())));
                }
            }
        }

        if (!postReadCandidates.isEmpty()) {
            List<AcceptedMetricBucketWriteCommand> postReadCommands = postReadCandidates.stream()
                    .map(IndexedCommand::command)
                    .toList();
            Map<CommandIdempotencyKey, AcceptedMetricBucketIdentity> postReadByKey =
                    findIdentitiesByIdempotencyKeys(postReadCommands);
            statementCount++;
            Map<CommandInstanceKey, AcceptedMetricBucketIdentity> postReadByInstance =
                    findIdentitiesByInstanceBoundaries(postReadCommands);
            statementCount++;
            for (IndexedCommand candidate : postReadCandidates) {
                results.set(candidate.index(), classifyWithIdentities(
                                candidate.command(),
                                postReadByKey,
                                postReadByInstance)
                        .orElseGet(() -> AcceptedMetricBucketBatchItemResult.transientFailure(
                                candidate.command(),
                                "database_integrity_reread_missing")));
            }
        }

        for (int index = 0; index < results.size(); index++) {
            if (results.get(index) == null) {
                results.set(index, AcceptedMetricBucketBatchItemResult.transientFailure(
                        requiredCommands.get(index),
                        "database_batch_result_missing"));
            }
        }
        return new AcceptedMetricBucketBatchWriteResult(results, statementCount);
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
     * queue worker가 같은 idempotency key의 stored payload hash를 비교할 수 있게 identity projection을 조회한다.
     */
    @Transactional(readOnly = true)
    public Optional<AcceptedMetricBucketIdentity> findIdentityByProjectIdAndIdempotencyKey(
            UUID projectId,
            String idempotencyKey) {
        return acceptedMetricBucketJpaRepository.findIdentityByProjectIdAndIdempotencyKey(
                Objects.requireNonNull(projectId, "projectId must not be null"),
                requireText(idempotencyKey, "idempotencyKey"));
    }

    /**
     * queue worker가 같은 application instance와 bucket start의 기존 row를 deterministic conflict로 분류할 수 있게 조회한다.
     */
    @Transactional(readOnly = true)
    public Optional<AcceptedMetricBucketIdentity> findIdentityByProjectApplicationInstanceAndBucketStartUtc(
            UUID projectId,
            String applicationName,
            String environment,
            String instanceName,
            OffsetDateTime bucketStartUtc) {
        return acceptedMetricBucketJpaRepository.findIdentityByProjectApplicationInstanceAndBucketStartUtc(
                Objects.requireNonNull(projectId, "projectId must not be null"),
                requireText(applicationName, "applicationName"),
                requireText(environment, "environment"),
                requireText(instanceName, "instanceName"),
                Objects.requireNonNull(bucketStartUtc, "bucketStartUtc must not be null"));
    }

    /**
     * retention cleanup이 `bucket_end_utc < metricEvidenceCutoffUtc`인 accepted bucket row만 물리 삭제하도록 위임한다.
     *
     * <p>`accepted_at`이나 row 생성 시각은 cleanup predicate로 사용하지 않는다. snapshot retention의 가장 오래된 30분
     * window evidence를 보존하기 위한 grace cutoff는 caller가 계산해서 전달한다.</p>
     */
    @Transactional
    public long deleteAcceptedMetricBucketsEndedBefore(OffsetDateTime metricEvidenceCutoffUtc) {
        return acceptedMetricBucketJpaRepository.deleteAcceptedMetricBucketsEndedBefore(
                toUtcOffsetDateTime(metricEvidenceCutoffUtc));
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
     * snapshot read model freshness source를 bucket_end와 accepted_at cutoff가 모두 지난 row로 제한한다.
     */
    @Transactional(readOnly = true)
    public Optional<OffsetDateTime> findLatestBucketEndUtcByApplicationIdAtOrBeforeAcceptedAt(
            UUID applicationId,
            Instant evaluationAtUtc,
            OffsetDateTime acceptedAtCutoffUtc) {
        return acceptedMetricBucketJpaRepository.findLatestBucketEndUtcByApplicationIdAtOrBeforeAcceptedAt(
                Objects.requireNonNull(applicationId, "applicationId must not be null"),
                toUtcOffsetDateTime(Objects.requireNonNull(evaluationAtUtc, "evaluationAtUtc must not be null")),
                toUtcOffsetDateTime(acceptedAtCutoffUtc));
    }

    /**
     * selected application instance scope의 마지막 accepted bucket endUtc timestamp만 조회한다.
     *
     * <p>이 값은 freshness source로만 사용하며, current window end boundary를 대체하지 않는다.</p>
     */
    @Transactional(readOnly = true)
    public Optional<OffsetDateTime> findLatestBucketEndUtcByApplicationInstanceIdAtOrBefore(
            UUID applicationInstanceId,
            Instant evaluationAtUtc) {
        return acceptedMetricBucketJpaRepository.findLatestBucketEndUtcByApplicationInstanceIdAtOrBefore(
                Objects.requireNonNull(applicationInstanceId, "applicationInstanceId must not be null"),
                toUtcOffsetDateTime(Objects.requireNonNull(evaluationAtUtc, "evaluationAtUtc must not be null")));
    }

    /**
     * snapshot instance summary freshness source를 accepted_at cutoff 이전 persisted bucket으로 제한한다.
     */
    @Transactional(readOnly = true)
    public Optional<OffsetDateTime> findLatestBucketEndUtcByApplicationInstanceIdAtOrBeforeAcceptedAt(
            UUID applicationInstanceId,
            Instant evaluationAtUtc,
            OffsetDateTime acceptedAtCutoffUtc) {
        return acceptedMetricBucketJpaRepository.findLatestBucketEndUtcByApplicationInstanceIdAtOrBeforeAcceptedAt(
                Objects.requireNonNull(applicationInstanceId, "applicationInstanceId must not be null"),
                toUtcOffsetDateTime(Objects.requireNonNull(evaluationAtUtc, "evaluationAtUtc must not be null")),
                toUtcOffsetDateTime(acceptedAtCutoffUtc));
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
     * snapshot application window aggregate를 accepted_at cutoff 이전 row만으로 합산한다.
     */
    @Transactional(readOnly = true)
    public WindowBucketAggregate findWindowAggregateByApplicationIdAcceptedAtOrBefore(
            UUID applicationId,
            Instant windowStartUtc,
            Instant windowEndUtc,
            OffsetDateTime acceptedAtCutoffUtc) {
        UUID requiredApplicationId = Objects.requireNonNull(applicationId, "applicationId must not be null");
        Instant requiredWindowStartUtc = Objects.requireNonNull(windowStartUtc, "windowStartUtc must not be null");
        Instant requiredWindowEndUtc = Objects.requireNonNull(windowEndUtc, "windowEndUtc must not be null");
        if (!requiredWindowEndUtc.isAfter(requiredWindowStartUtc)) {
            throw new IllegalArgumentException("windowEndUtc must be after windowStartUtc");
        }
        OffsetDateTime windowStart = toUtcOffsetDateTime(requiredWindowStartUtc);
        OffsetDateTime windowEnd = toUtcOffsetDateTime(requiredWindowEndUtc);
        WindowBucketAggregate aggregate = acceptedMetricBucketJpaRepository
                .sumWindowRequestAndErrorCountsByApplicationIdAcceptedAtOrBefore(
                        requiredApplicationId,
                        windowStart,
                        windowEnd,
                        toUtcOffsetDateTime(acceptedAtCutoffUtc));
        return Objects.requireNonNullElse(aggregate, WindowBucketAggregate.zero());
    }

    /**
     * selected application instance current window의 request/error count 합계만 조회한다.
     *
     * <p>repository는 sample readiness, lifecycle state, health score, p95/p99, rule을 계산하지 않고 `(start, end]`
     * bucket window 포함 여부와 count 합계만 service에 전달한다.</p>
     */
    @Transactional(readOnly = true)
    public WindowBucketAggregate findWindowAggregateByApplicationInstanceId(
            UUID applicationInstanceId,
            Instant windowStartUtc,
            Instant windowEndUtc) {
        UUID requiredApplicationInstanceId = Objects.requireNonNull(
                applicationInstanceId,
                "applicationInstanceId must not be null");
        Instant requiredWindowStartUtc = Objects.requireNonNull(windowStartUtc, "windowStartUtc must not be null");
        Instant requiredWindowEndUtc = Objects.requireNonNull(windowEndUtc, "windowEndUtc must not be null");
        if (!requiredWindowEndUtc.isAfter(requiredWindowStartUtc)) {
            throw new IllegalArgumentException("windowEndUtc must be after windowStartUtc");
        }
        OffsetDateTime windowStart = toUtcOffsetDateTime(requiredWindowStartUtc);
        OffsetDateTime windowEnd = toUtcOffsetDateTime(requiredWindowEndUtc);
        WindowBucketAggregate aggregate = acceptedMetricBucketJpaRepository
                .sumWindowRequestAndErrorCountsByApplicationInstanceId(
                        requiredApplicationInstanceId,
                        windowStart,
                        windowEnd);
        return Objects.requireNonNullElse(aggregate, WindowBucketAggregate.zero());
    }

    /**
     * snapshot instance summary aggregate를 accepted_at cutoff 이전 row만으로 합산한다.
     */
    @Transactional(readOnly = true)
    public WindowBucketAggregate findWindowAggregateByApplicationInstanceIdAcceptedAtOrBefore(
            UUID applicationInstanceId,
            Instant windowStartUtc,
            Instant windowEndUtc,
            OffsetDateTime acceptedAtCutoffUtc) {
        UUID requiredApplicationInstanceId = Objects.requireNonNull(
                applicationInstanceId,
                "applicationInstanceId must not be null");
        Instant requiredWindowStartUtc = Objects.requireNonNull(windowStartUtc, "windowStartUtc must not be null");
        Instant requiredWindowEndUtc = Objects.requireNonNull(windowEndUtc, "windowEndUtc must not be null");
        if (!requiredWindowEndUtc.isAfter(requiredWindowStartUtc)) {
            throw new IllegalArgumentException("windowEndUtc must be after windowStartUtc");
        }
        WindowBucketAggregate aggregate = acceptedMetricBucketJpaRepository
                .sumWindowRequestAndErrorCountsByApplicationInstanceIdAcceptedAtOrBefore(
                        requiredApplicationInstanceId,
                        toUtcOffsetDateTime(requiredWindowStartUtc),
                        toUtcOffsetDateTime(requiredWindowEndUtc),
                        toUtcOffsetDateTime(acceptedAtCutoffUtc));
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
     * snapshot source-scoped percentile evidence를 accepted_at cutoff 이전 row만으로 조회한다.
     */
    @Transactional(readOnly = true)
    public List<LocalPercentileEvidenceRow> findLocalPercentileEvidenceRowsByApplicationIdAcceptedAtOrBefore(
            UUID applicationId,
            Instant windowStartUtc,
            Instant windowEndUtc,
            OffsetDateTime acceptedAtCutoffUtc) {
        UUID requiredApplicationId = Objects.requireNonNull(applicationId, "applicationId must not be null");
        OffsetDateTime windowStart = toUtcOffsetDateTime(
                Objects.requireNonNull(windowStartUtc, "windowStartUtc must not be null"));
        OffsetDateTime windowEnd = toUtcOffsetDateTime(
                Objects.requireNonNull(windowEndUtc, "windowEndUtc must not be null"));
        validateWindow(windowStart, windowEnd);
        return acceptedMetricBucketJpaRepository.findLocalPercentileEvidenceRowsByApplicationIdAcceptedAtOrBefore(
                requiredApplicationId,
                windowStart,
                windowEnd,
                toUtcOffsetDateTime(acceptedAtCutoffUtc));
    }

    /**
     * selected application instance current window의 local percentile JSON evidence row를 조회한다.
     *
     * <p>repository는 persisted JSON과 bucket boundary만 전달하며, source/scope 검증과 p95/p99 노출 여부는 service가
     * 판단한다. p95/p99 평균, 병합, histogram 재계산은 하지 않는다.</p>
     */
    @Transactional(readOnly = true)
    public List<LocalPercentileEvidenceRow> findLocalPercentileEvidenceRowsByApplicationInstanceId(
            UUID applicationInstanceId,
            Instant windowStartUtc,
            Instant windowEndUtc) {
        UUID requiredApplicationInstanceId = Objects.requireNonNull(
                applicationInstanceId,
                "applicationInstanceId must not be null");
        OffsetDateTime windowStart = toUtcOffsetDateTime(
                Objects.requireNonNull(windowStartUtc, "windowStartUtc must not be null"));
        OffsetDateTime windowEnd = toUtcOffsetDateTime(
                Objects.requireNonNull(windowEndUtc, "windowEndUtc must not be null"));
        validateWindow(windowStart, windowEnd);
        return acceptedMetricBucketJpaRepository.findLocalPercentileEvidenceRowsByApplicationInstanceId(
                requiredApplicationInstanceId,
                windowStart,
                windowEnd);
    }

    /**
     * snapshot instance summary percentile point를 accepted_at cutoff 이전 row만으로 조회한다.
     */
    @Transactional(readOnly = true)
    public List<LocalPercentileEvidenceRow> findLocalPercentileEvidenceRowsByApplicationInstanceIdAcceptedAtOrBefore(
            UUID applicationInstanceId,
            Instant windowStartUtc,
            Instant windowEndUtc,
            OffsetDateTime acceptedAtCutoffUtc) {
        UUID requiredApplicationInstanceId = Objects.requireNonNull(
                applicationInstanceId,
                "applicationInstanceId must not be null");
        OffsetDateTime windowStart = toUtcOffsetDateTime(
                Objects.requireNonNull(windowStartUtc, "windowStartUtc must not be null"));
        OffsetDateTime windowEnd = toUtcOffsetDateTime(
                Objects.requireNonNull(windowEndUtc, "windowEndUtc must not be null"));
        validateWindow(windowStart, windowEnd);
        return acceptedMetricBucketJpaRepository
                .findLocalPercentileEvidenceRowsByApplicationInstanceIdAcceptedAtOrBefore(
                        requiredApplicationInstanceId,
                        windowStart,
                        windowEnd,
                        toUtcOffsetDateTime(acceptedAtCutoffUtc));
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
     * snapshot histogram evidence를 accepted_at cutoff 이전 application row만으로 조회한다.
     */
    @Transactional(readOnly = true)
    public List<HistogramBucketEvidenceRow> findSummaryDurationBucketEvidenceRowsByApplicationIdAcceptedAtOrBefore(
            UUID applicationId,
            Instant windowStartUtc,
            Instant windowEndUtc,
            OffsetDateTime acceptedAtCutoffUtc) {
        UUID requiredApplicationId = Objects.requireNonNull(applicationId, "applicationId must not be null");
        OffsetDateTime windowStart = toUtcOffsetDateTime(
                Objects.requireNonNull(windowStartUtc, "windowStartUtc must not be null"));
        OffsetDateTime windowEnd = toUtcOffsetDateTime(
                Objects.requireNonNull(windowEndUtc, "windowEndUtc must not be null"));
        validateWindow(windowStart, windowEnd);
        return acceptedMetricBucketJpaRepository
                .findSummaryDurationBucketEvidenceRowsByApplicationIdAcceptedAtOrBefore(
                        requiredApplicationId,
                        windowStart,
                        windowEnd,
                        toUtcOffsetDateTime(acceptedAtCutoffUtc));
    }

    /**
     * selected application instance current window의 summary duration bucket JSON evidence row를 조회한다.
     *
     * <p>repository는 duration bucket JSON과 boundary만 전달하며, boundary mismatch, count validation, p95/p99 금지는
     * service read model 조립 단계에서 처리한다.</p>
     */
    @Transactional(readOnly = true)
    public List<HistogramBucketEvidenceRow> findSummaryDurationBucketEvidenceRowsByApplicationInstanceId(
            UUID applicationInstanceId,
            Instant windowStartUtc,
            Instant windowEndUtc) {
        UUID requiredApplicationInstanceId = Objects.requireNonNull(
                applicationInstanceId,
                "applicationInstanceId must not be null");
        OffsetDateTime windowStart = toUtcOffsetDateTime(
                Objects.requireNonNull(windowStartUtc, "windowStartUtc must not be null"));
        OffsetDateTime windowEnd = toUtcOffsetDateTime(
                Objects.requireNonNull(windowEndUtc, "windowEndUtc must not be null"));
        validateWindow(windowStart, windowEnd);
        return acceptedMetricBucketJpaRepository.findSummaryDurationBucketEvidenceRowsByApplicationInstanceId(
                requiredApplicationInstanceId,
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
     * snapshot endpoint priority evidence를 accepted_at cutoff 이전 application row만으로 조회한다.
     */
    @Transactional(readOnly = true)
    public List<EndpointEvidenceRow> findEndpointEvidenceRowsByApplicationIdAcceptedAtOrBefore(
            UUID applicationId,
            Instant windowStartUtc,
            Instant windowEndUtc,
            OffsetDateTime acceptedAtCutoffUtc) {
        UUID requiredApplicationId = Objects.requireNonNull(applicationId, "applicationId must not be null");
        OffsetDateTime windowStart = toUtcOffsetDateTime(
                Objects.requireNonNull(windowStartUtc, "windowStartUtc must not be null"));
        OffsetDateTime windowEnd = toUtcOffsetDateTime(
                Objects.requireNonNull(windowEndUtc, "windowEndUtc must not be null"));
        validateWindow(windowStart, windowEnd);
        return acceptedMetricBucketJpaRepository.findEndpointEvidenceRowsByApplicationIdAcceptedAtOrBefore(
                requiredApplicationId,
                windowStart,
                windowEnd,
                toUtcOffsetDateTime(acceptedAtCutoffUtc));
    }

    /**
     * instance evidence read model용 selected instance endpoint JSON evidence row를 조회한다.
     *
     * <p>조회는 selected `application_instance_id`와 `(start, end]` bucket end boundary, `endpoints_json is not null`
     * 조건만 적용한다. repository는 presence, share, display order, rule/rank/confidence/action, endpoint p95/p99를
     * 계산하지 않는다.</p>
     */
    @Transactional(readOnly = true)
    public List<EndpointEvidenceRow> findEndpointEvidenceRowsByApplicationInstanceId(
            UUID applicationInstanceId,
            Instant windowStartUtc,
            Instant windowEndUtc) {
        UUID requiredApplicationInstanceId = Objects.requireNonNull(
                applicationInstanceId,
                "applicationInstanceId must not be null");
        OffsetDateTime windowStart = toUtcOffsetDateTime(
                Objects.requireNonNull(windowStartUtc, "windowStartUtc must not be null"));
        OffsetDateTime windowEnd = toUtcOffsetDateTime(
                Objects.requireNonNull(windowEndUtc, "windowEndUtc must not be null"));
        validateWindow(windowStart, windowEnd);
        return acceptedMetricBucketJpaRepository.findEndpointEvidenceRowsByApplicationInstanceId(
                requiredApplicationInstanceId,
                windowStart,
                windowEnd);
    }

    /**
     * snapshot instance endpoint evidence ref를 accepted_at cutoff 이전 selected instance row만으로 조회한다.
     */
    @Transactional(readOnly = true)
    public List<EndpointEvidenceRow> findEndpointEvidenceRowsByApplicationInstanceIdAcceptedAtOrBefore(
            UUID applicationInstanceId,
            Instant windowStartUtc,
            Instant windowEndUtc,
            OffsetDateTime acceptedAtCutoffUtc) {
        UUID requiredApplicationInstanceId = Objects.requireNonNull(
                applicationInstanceId,
                "applicationInstanceId must not be null");
        OffsetDateTime windowStart = toUtcOffsetDateTime(
                Objects.requireNonNull(windowStartUtc, "windowStartUtc must not be null"));
        OffsetDateTime windowEnd = toUtcOffsetDateTime(
                Objects.requireNonNull(windowEndUtc, "windowEndUtc must not be null"));
        validateWindow(windowStart, windowEnd);
        return acceptedMetricBucketJpaRepository.findEndpointEvidenceRowsByApplicationInstanceIdAcceptedAtOrBefore(
                requiredApplicationInstanceId,
                windowStart,
                windowEnd,
                toUtcOffsetDateTime(acceptedAtCutoffUtc));
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
     * snapshot previous-state/gap 근거를 bucket_end와 accepted_at cutoff가 모두 지난 boundary로 제한한다.
     */
    @Transactional(readOnly = true)
    public Optional<AcceptedBucketGapEvidence> findAcceptedBucketGapEvidenceByApplicationIdAtOrBeforeAcceptedAt(
            UUID applicationId,
            Instant evaluationAtUtc,
            OffsetDateTime acceptedAtCutoffUtc) {
        UUID requiredApplicationId = Objects.requireNonNull(applicationId, "applicationId must not be null");
        OffsetDateTime evaluationAt = toUtcOffsetDateTime(
                Objects.requireNonNull(evaluationAtUtc, "evaluationAtUtc must not be null"));
        List<AcceptedBucketGapEvidenceRow> rows =
                acceptedMetricBucketJpaRepository
                        .findAcceptedBucketGapEvidenceRowsByApplicationIdAtOrBeforeAcceptedAt(
                                requiredApplicationId,
                                evaluationAt,
                                toUtcOffsetDateTime(acceptedAtCutoffUtc),
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
     * snapshot degraded/spike recent bucket evidence를 accepted_at cutoff 이전 row만으로 조회한다.
     */
    @Transactional(readOnly = true)
    public List<RecentBucketEvidenceRow> findRecentFiveBucketEvidenceRowsByApplicationIdAtOrBeforeAcceptedAt(
            UUID applicationId,
            Instant evaluationAtUtc,
            OffsetDateTime acceptedAtCutoffUtc) {
        UUID requiredApplicationId = Objects.requireNonNull(applicationId, "applicationId must not be null");
        OffsetDateTime evaluationAt = toUtcOffsetDateTime(
                Objects.requireNonNull(evaluationAtUtc, "evaluationAtUtc must not be null"));
        OffsetDateTime acceptedAtCutoff = toUtcOffsetDateTime(acceptedAtCutoffUtc);
        List<AcceptedBucketBoundaryEvidenceRow> boundaries =
                acceptedMetricBucketJpaRepository
                        .findRecentBucketBoundaryEvidenceRowsByApplicationIdAtOrBeforeAcceptedAt(
                                requiredApplicationId,
                                evaluationAt,
                                acceptedAtCutoff,
                                PageRequest.of(0, RECENT_BUCKET_EVIDENCE_LIMIT));
        if (boundaries.isEmpty()) {
            return List.of();
        }
        List<OffsetDateTime> bucketEndUtcValues = boundaries.stream()
                .map(AcceptedBucketBoundaryEvidenceRow::bucketEndUtc)
                .toList();
        List<RecentBucketEvidenceRow> rows =
                acceptedMetricBucketJpaRepository
                        .findRecentBucketEvidenceRowsByApplicationIdAndBucketEndUtcInAcceptedAtOrBefore(
                                requiredApplicationId,
                                bucketEndUtcValues,
                                acceptedAtCutoff);
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

    /**
     * snapshot saturation hint를 accepted_at cutoff 이전 application row만으로 조회한다.
     */
    @Transactional(readOnly = true)
    public Optional<RuntimeRatioEvidenceRow> findLatestRuntimeRatioEvidenceRowByApplicationIdAcceptedAtOrBefore(
            UUID applicationId,
            Instant windowStartUtc,
            Instant windowEndUtc,
            OffsetDateTime acceptedAtCutoffUtc) {
        UUID requiredApplicationId = Objects.requireNonNull(applicationId, "applicationId must not be null");
        OffsetDateTime windowStart = toUtcOffsetDateTime(
                Objects.requireNonNull(windowStartUtc, "windowStartUtc must not be null"));
        OffsetDateTime windowEnd = toUtcOffsetDateTime(
                Objects.requireNonNull(windowEndUtc, "windowEndUtc must not be null"));
        validateWindow(windowStart, windowEnd);
        return acceptedMetricBucketJpaRepository.findRuntimeRatioEvidenceRowsByApplicationIdAcceptedAtOrBefore(
                        requiredApplicationId,
                        windowStart,
                        windowEnd,
                        toUtcOffsetDateTime(acceptedAtCutoffUtc),
                        PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }

    /**
     * selected application instance current window 안의 최신 runtime ratio sample 한 건만 조회한다.
     *
     * <p>latest sample은 instance evidence의 hint이며 repository는 max/avg/sustained pressure, degraded/down, root cause를
     * 계산하지 않는다.</p>
     */
    @Transactional(readOnly = true)
    public Optional<RuntimeRatioEvidenceRow> findLatestRuntimeRatioEvidenceRowByApplicationInstanceId(
            UUID applicationInstanceId,
            Instant windowStartUtc,
            Instant windowEndUtc) {
        UUID requiredApplicationInstanceId = Objects.requireNonNull(
                applicationInstanceId,
                "applicationInstanceId must not be null");
        OffsetDateTime windowStart = toUtcOffsetDateTime(
                Objects.requireNonNull(windowStartUtc, "windowStartUtc must not be null"));
        OffsetDateTime windowEnd = toUtcOffsetDateTime(
                Objects.requireNonNull(windowEndUtc, "windowEndUtc must not be null"));
        validateWindow(windowStart, windowEnd);
        return acceptedMetricBucketJpaRepository.findRuntimeRatioEvidenceRowsByApplicationInstanceId(
                        requiredApplicationInstanceId,
                        windowStart,
                        windowEnd,
                        PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }

    /**
     * snapshot instance resource hint를 accepted_at cutoff 이전 selected instance row만으로 조회한다.
     */
    @Transactional(readOnly = true)
    public Optional<RuntimeRatioEvidenceRow> findLatestRuntimeRatioEvidenceRowByApplicationInstanceIdAcceptedAtOrBefore(
            UUID applicationInstanceId,
            Instant windowStartUtc,
            Instant windowEndUtc,
            OffsetDateTime acceptedAtCutoffUtc) {
        UUID requiredApplicationInstanceId = Objects.requireNonNull(
                applicationInstanceId,
                "applicationInstanceId must not be null");
        OffsetDateTime windowStart = toUtcOffsetDateTime(
                Objects.requireNonNull(windowStartUtc, "windowStartUtc must not be null"));
        OffsetDateTime windowEnd = toUtcOffsetDateTime(
                Objects.requireNonNull(windowEndUtc, "windowEndUtc must not be null"));
        validateWindow(windowStart, windowEnd);
        return acceptedMetricBucketJpaRepository
                .findRuntimeRatioEvidenceRowsByApplicationInstanceIdAcceptedAtOrBefore(
                        requiredApplicationInstanceId,
                        windowStart,
                        windowEnd,
                        toUtcOffsetDateTime(acceptedAtCutoffUtc),
                        PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }

    private Optional<AcceptedMetricBucketBatchItemResult> classifyWithIdentities(
            AcceptedMetricBucketWriteCommand command,
            Map<CommandIdempotencyKey, AcceptedMetricBucketIdentity> identitiesByKey,
            Map<CommandInstanceKey, AcceptedMetricBucketIdentity> identitiesByInstance) {
        AcceptedMetricBucketIdentity byKey = identitiesByKey.get(CommandIdempotencyKey.from(command));
        if (byKey != null) {
            if (byKey.payloadHash().equals(command.payloadHash())) {
                return Optional.of(AcceptedMetricBucketBatchItemResult.duplicateNoop(command));
            }
            return Optional.of(AcceptedMetricBucketBatchItemResult.conflict(
                    command,
                    "idempotency_payload_conflict",
                    byKey));
        }

        AcceptedMetricBucketIdentity byInstance = identitiesByInstance.get(CommandInstanceKey.from(command));
        if (byInstance != null && !byInstance.idempotencyKey().equals(command.idempotencyKey())) {
            return Optional.of(AcceptedMetricBucketBatchItemResult.conflict(
                    command,
                    "instance_bucket_identity_conflict",
                    byInstance));
        }
        if (byInstance != null && byInstance.payloadHash().equals(command.payloadHash())) {
            return Optional.of(AcceptedMetricBucketBatchItemResult.duplicateNoop(command));
        }
        return Optional.empty();
    }

    private Map<CommandIdempotencyKey, AcceptedMetricBucketIdentity> findIdentitiesByIdempotencyKeys(
            List<AcceptedMetricBucketWriteCommand> commands) {
        List<CommandIdempotencyKey> keys = commands.stream()
                .map(CommandIdempotencyKey::from)
                .distinct()
                .toList();
        if (keys.isEmpty()) {
            return Map.of();
        }

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        StringBuilder tuples = new StringBuilder();
        for (int index = 0; index < keys.size(); index++) {
            if (index > 0) {
                tuples.append(", ");
            }
            tuples.append("(:projectId").append(index).append(", :idempotencyKey").append(index).append(")");
            parameters.addValue("projectId" + index, keys.get(index).projectId());
            parameters.addValue("idempotencyKey" + index, keys.get(index).idempotencyKey());
        }

        String sql = """
                select id as bucket_id,
                       project_id,
                       application_id,
                       application_instance_id,
                       idempotency_key,
                       payload_hash,
                       bucket_start_utc,
                       bucket_end_utc,
                       accepted_at
                from accepted_metric_buckets
                where (project_id, idempotency_key) in (%s)
                """.formatted(tuples);
        Map<CommandIdempotencyKey, AcceptedMetricBucketIdentity> identities = new LinkedHashMap<>();
        jdbcTemplate.query(sql, parameters, (resultSet) -> {
            AcceptedMetricBucketIdentity identity = identityFrom(resultSet);
            identities.put(new CommandIdempotencyKey(identity.projectId(), identity.idempotencyKey()), identity);
        });
        return identities;
    }

    private Map<CommandInstanceKey, AcceptedMetricBucketIdentity> findIdentitiesByInstanceBoundaries(
            List<AcceptedMetricBucketWriteCommand> commands) {
        List<CommandInstanceKey> keys = commands.stream()
                .map(CommandInstanceKey::from)
                .distinct()
                .toList();
        if (keys.isEmpty()) {
            return Map.of();
        }

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        StringBuilder tuples = new StringBuilder();
        for (int index = 0; index < keys.size(); index++) {
            if (index > 0) {
                tuples.append(", ");
            }
            tuples.append("(:projectId").append(index)
                    .append(", :applicationName").append(index)
                    .append(", :environment").append(index)
                    .append(", :instanceName").append(index)
                    .append(", :bucketStartUtc").append(index)
                    .append(")");
            CommandInstanceKey key = keys.get(index);
            parameters.addValue("projectId" + index, key.projectId());
            parameters.addValue("applicationName" + index, key.applicationName());
            parameters.addValue("environment" + index, key.environment());
            parameters.addValue("instanceName" + index, key.instanceName());
            parameters.addValue("bucketStartUtc" + index, key.bucketStartUtc());
        }

        String sql = """
                select bucket.id as bucket_id,
                       bucket.project_id,
                       bucket.application_id,
                       bucket.application_instance_id,
                       bucket.idempotency_key,
                       bucket.payload_hash,
                       bucket.bucket_start_utc,
                       bucket.bucket_end_utc,
                       bucket.accepted_at,
                       application.name as application_name,
                       application.environment as environment,
                       instance.instance_name as instance_name
                from accepted_metric_buckets bucket
                join applications application on application.id = bucket.application_id
                join application_instances instance on instance.id = bucket.application_instance_id
                where (application.project_id,
                       application.name,
                       application.environment,
                       instance.instance_name,
                       bucket.bucket_start_utc) in (%s)
                """.formatted(tuples);
        Map<CommandInstanceKey, AcceptedMetricBucketIdentity> identities = new LinkedHashMap<>();
        jdbcTemplate.query(sql, parameters, (resultSet) -> {
            AcceptedMetricBucketIdentity identity = identityFrom(resultSet);
            identities.put(new CommandInstanceKey(
                    identity.projectId(),
                    resultSet.getString("application_name"),
                    resultSet.getString("environment"),
                    resultSet.getString("instance_name"),
                    identity.bucketStartUtc()), identity);
        });
        return identities;
    }

    private Map<CommandIdempotencyKey, AcceptedMetricBucketIdentity> insertRowsReturningIdentities(
            List<IndexedCommand> insertCandidates,
            List<ApplicationCatalogEntry> catalogEntries) {
        if (insertCandidates.size() != catalogEntries.size()) {
            throw new IllegalStateException("catalog entry count must match insert candidate count");
        }
        if (insertCandidates.isEmpty()) {
            return Map.of();
        }

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        StringBuilder values = new StringBuilder();
        for (int index = 0; index < insertCandidates.size(); index++) {
            if (index > 0) {
                values.append(", ");
            }
            IndexedCommand indexed = insertCandidates.get(index);
            AcceptedMetricBucketWriteCommand command = indexed.command();
            ApplicationCatalogEntry catalogEntry = catalogEntries.get(index);
            values.append("""
                    (:id%s, :projectId%s, :applicationId%s, :applicationInstanceId%s,
                     :schemaVersion%s, :idempotencyKey%s, :payloadHash%s,
                     :bucketStartUtc%s, :bucketEndUtc%s, :durationSeconds%s,
                     :acceptedAt%s, :requestCount%s, :errorCount%s,
                     CAST(:durationBucketsJson%s AS jsonb), :cpuUsageRatio%s, :heapUsedRatio%s,
                     :datasourcePoolUsageRatio%s, CAST(:localPercentilesJson%s AS jsonb),
                     CAST(:endpointsJson%s AS jsonb), :createdAt%s)
                    """.formatted(
                    index, index, index, index, index, index, index, index, index, index,
                    index, index, index, index, index, index, index, index, index, index));
            parameters.addValue("id" + index, UUID.randomUUID());
            parameters.addValue("projectId" + index, command.projectId());
            parameters.addValue("applicationId" + index, catalogEntry.applicationId());
            parameters.addValue("applicationInstanceId" + index, catalogEntry.applicationInstanceId());
            parameters.addValue("schemaVersion" + index, command.schemaVersion());
            parameters.addValue("idempotencyKey" + index, command.idempotencyKey());
            parameters.addValue("payloadHash" + index, command.payloadHash());
            parameters.addValue("bucketStartUtc" + index, command.bucketStartUtc());
            parameters.addValue("bucketEndUtc" + index, command.bucketEndUtc());
            parameters.addValue("durationSeconds" + index, command.durationSeconds());
            parameters.addValue("acceptedAt" + index, command.acceptedAt());
            parameters.addValue("requestCount" + index, command.requestCount());
            parameters.addValue("errorCount" + index, command.errorCount());
            parameters.addValue("durationBucketsJson" + index, writeJson(command.durationBuckets()));
            parameters.addValue("cpuUsageRatio" + index, toBigDecimal(command.cpuUsageRatio()));
            parameters.addValue("heapUsedRatio" + index, toBigDecimal(command.heapUsedRatio()));
            parameters.addValue("datasourcePoolUsageRatio" + index, toBigDecimal(command.datasourcePoolUsageRatio()));
            parameters.addValue("localPercentilesJson" + index, writeNullableJson(command.localPercentiles()));
            parameters.addValue("endpointsJson" + index, writeJson(command.endpoints()));
            parameters.addValue("createdAt" + index, command.acceptedAt());
        }

        String sql = """
                insert into accepted_metric_buckets (
                  id, project_id, application_id, application_instance_id,
                  schema_version, idempotency_key, payload_hash,
                  bucket_start_utc, bucket_end_utc, duration_seconds,
                  accepted_at, request_count, error_count,
                  duration_buckets_json, cpu_usage_ratio, heap_used_ratio,
                  datasource_pool_usage_ratio, local_percentiles_json,
                  endpoints_json, created_at
                )
                values %s
                on conflict do nothing
                returning id as bucket_id,
                          project_id,
                          application_id,
                          application_instance_id,
                          idempotency_key,
                          payload_hash,
                          bucket_start_utc,
                          bucket_end_utc,
                          accepted_at
                """.formatted(values);

        Map<CommandIdempotencyKey, AcceptedMetricBucketIdentity> identities = new LinkedHashMap<>();
        jdbcTemplate.query(sql, parameters, (resultSet) -> {
            AcceptedMetricBucketIdentity identity = identityFrom(resultSet);
            identities.put(CommandIdempotencyKey.from(identity), identity);
        });
        return identities;
    }

    private static AcceptedMetricBucketIdentity identityFrom(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new AcceptedMetricBucketIdentity(
                resultSet.getObject("bucket_id", UUID.class),
                resultSet.getObject("project_id", UUID.class),
                resultSet.getObject("application_id", UUID.class),
                resultSet.getObject("application_instance_id", UUID.class),
                resultSet.getString("idempotency_key"),
                resultSet.getString("payload_hash"),
                resultSet.getObject("bucket_start_utc", OffsetDateTime.class),
                resultSet.getObject("bucket_end_utc", OffsetDateTime.class),
                resultSet.getObject("accepted_at", OffsetDateTime.class));
    }

    private record IndexedCommand(int index, AcceptedMetricBucketWriteCommand command) {

        private IndexedCommand {
            Objects.requireNonNull(command, "command must not be null");
        }
    }

    private record CommandIdempotencyKey(UUID projectId, String idempotencyKey) {

        private CommandIdempotencyKey {
            Objects.requireNonNull(projectId, "projectId must not be null");
            idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        }

        private static CommandIdempotencyKey from(AcceptedMetricBucketWriteCommand command) {
            return new CommandIdempotencyKey(command.projectId(), command.idempotencyKey());
        }

        private static CommandIdempotencyKey from(AcceptedMetricBucketIdentity identity) {
            return new CommandIdempotencyKey(identity.projectId(), identity.idempotencyKey());
        }
    }

    private record CommandInstanceKey(
            UUID projectId,
            String applicationName,
            String environment,
            String instanceName,
            OffsetDateTime bucketStartUtc
    ) {

        private CommandInstanceKey {
            Objects.requireNonNull(projectId, "projectId must not be null");
            applicationName = requireText(applicationName, "applicationName");
            environment = requireText(environment, "environment");
            instanceName = requireText(instanceName, "instanceName");
            Objects.requireNonNull(bucketStartUtc, "bucketStartUtc must not be null");
        }

        private static CommandInstanceKey from(AcceptedMetricBucketWriteCommand command) {
            return new CommandInstanceKey(
                    command.projectId(),
                    command.applicationName(),
                    command.environment(),
                    command.instanceName(),
                    command.bucketStartUtc());
        }
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

    private static OffsetDateTime toUtcOffsetDateTime(OffsetDateTime offsetDateTime) {
        return Objects.requireNonNull(offsetDateTime, "offsetDateTime must not be null")
                .withOffsetSameInstant(ZoneOffset.UTC);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
