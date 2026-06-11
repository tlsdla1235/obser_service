package com.observation.portal.domain.snapshot.repository;

import com.observation.portal.domain.snapshot.entity.DashboardSnapshotEntity;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailRow;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotLatestRow;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotSourceRow;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotTrendRow;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotWriteValues;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Stored dashboard snapshot을 slot horizon 기반 read-only row로 조회하는 repository facade다.
 *
 * <p>이 repository는 `dashboard_snapshots.read_model_json`을 해석하지 않고 row metadata와 JSON source만 반환한다.
 * state/rule/priority/p95/p99/marker/recovery 의미 계산은 수행하지 않는다. marker/history/trend inclusion horizon은
 * `generated_at`이 아니라 `current_window_end_utc`를 사용하며, `generated_at`은 capture provenance와 tie-breaker로만
 * 유지한다.</p>
 */
@Repository
public class DashboardSnapshotRepository {

    private final DashboardSnapshotJpaRepository jpaRepository;

    /**
     * dashboard snapshot JPA repository를 주입한다.
     */
    public DashboardSnapshotRepository(DashboardSnapshotJpaRepository jpaRepository) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository, "jpaRepository must not be null");
    }

    /**
     * project/application scope와 current window horizon closed range에 맞는 snapshot row를 최신 slot 순으로 bounded 조회한다.
     *
     * <p>`currentWindowEndUntil`은 조회 시점 이후 slot snapshot이 응답과 limit에 섞이지 않도록 하는 상한이다. caller는
     * 이 결과를 필요한 만큼 선택한 뒤 public response order로 다시 정렬할 수 있다.</p>
     */
    @Transactional(readOnly = true)
    public List<DashboardSnapshotTrendRow> findTrendRowsNewestFirst(
            UUID projectId,
            UUID applicationId,
            OffsetDateTime currentWindowEndSince,
            OffsetDateTime currentWindowEndUntil,
            int limit) {
        UUID requiredProjectId = Objects.requireNonNull(projectId, "projectId must not be null");
        UUID requiredApplicationId = Objects.requireNonNull(applicationId, "applicationId must not be null");
        OffsetDateTime requiredCurrentWindowEndSince = Objects.requireNonNull(
                currentWindowEndSince,
                "currentWindowEndSince must not be null");
        OffsetDateTime requiredCurrentWindowEndUntil = Objects.requireNonNull(
                currentWindowEndUntil,
                "currentWindowEndUntil must not be null");
        if (requiredCurrentWindowEndUntil.isBefore(requiredCurrentWindowEndSince)) {
            throw new IllegalArgumentException("currentWindowEndUntil must not be before currentWindowEndSince");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return jpaRepository.findTrendRowsNewestFirst(
                requiredProjectId,
                requiredApplicationId,
                requiredCurrentWindowEndSince,
                requiredCurrentWindowEndUntil,
                PageRequest.of(0, limit));
    }

    /**
     * writer duplicate identity row를 transaction 안에서 잠그고 조회한다.
     *
     * <p>service layer가 priority-aware upsert를 결정할 수 있도록 persistence entity를 반환하지만, 이 entity는
     * controller/API 반환 모델로 사용하지 않는다.</p>
     */
    @Transactional
    public Optional<DashboardSnapshotEntity> findByIdentityForUpdate(
            UUID applicationId,
            OffsetDateTime currentWindowEndUtc) {
        return jpaRepository.findByApplicationIdAndCurrentWindowEndUtc(
                Objects.requireNonNull(applicationId, "applicationId must not be null"),
                Objects.requireNonNull(currentWindowEndUtc, "currentWindowEndUtc must not be null"));
    }

    /**
     * 같은 identity row가 없을 때 새 dashboard snapshot row를 저장한다.
     */
    @Transactional
    public DashboardSnapshotEntity insert(DashboardSnapshotWriteValues values) {
        DashboardSnapshotEntity entity = new DashboardSnapshotEntity(
                Objects.requireNonNull(values, "values must not be null"));
        return jpaRepository.saveAndFlush(entity);
    }

    /**
     * higher-priority write가 대표 row를 갱신한 뒤 flush한다.
     */
    @Transactional
    public DashboardSnapshotEntity saveUpdated(DashboardSnapshotEntity entity) {
        return jpaRepository.saveAndFlush(Objects.requireNonNull(entity, "entity must not be null"));
    }

    /**
     * dashboard query fallback threshold 판단에 필요한 application scope latest snapshot metadata를 조회한다.
     */
    @Transactional(readOnly = true)
    public Optional<DashboardSnapshotLatestRow> findLatestByApplicationId(UUID applicationId) {
        List<DashboardSnapshotLatestRow> rows = jpaRepository.findLatestRowsByApplicationId(
                Objects.requireNonNull(applicationId, "applicationId must not be null"),
                PageRequest.of(0, 1));
        return rows.stream().findFirst();
    }

    /**
     * project/application/snapshot catalog path 정합성이 모두 맞는 snapshot detail source row를 조회한다.
     */
    @Transactional(readOnly = true)
    public Optional<DashboardSnapshotDetailRow> findDetailRow(
            UUID projectId,
            UUID applicationId,
            UUID snapshotId) {
        return jpaRepository.findDetailRow(
                Objects.requireNonNull(projectId, "projectId must not be null"),
                Objects.requireNonNull(applicationId, "applicationId must not be null"),
                Objects.requireNonNull(snapshotId, "snapshotId must not be null"));
    }

    /**
     * current window horizon 안의 stored snapshot rows를 bounded ascending slot order로 조회한다.
     */
    @Transactional(readOnly = true)
    public List<DashboardSnapshotDetailRow> findMarkerRows(
            UUID projectId,
            UUID applicationId,
            OffsetDateTime currentWindowEndSince,
            OffsetDateTime currentWindowEndUntil,
            int limit) {
        UUID requiredProjectId = Objects.requireNonNull(projectId, "projectId must not be null");
        UUID requiredApplicationId = Objects.requireNonNull(applicationId, "applicationId must not be null");
        OffsetDateTime requiredCurrentWindowEndSince = Objects.requireNonNull(
                currentWindowEndSince,
                "currentWindowEndSince must not be null");
        OffsetDateTime requiredCurrentWindowEndUntil = Objects.requireNonNull(
                currentWindowEndUntil,
                "currentWindowEndUntil must not be null");
        if (requiredCurrentWindowEndUntil.isBefore(requiredCurrentWindowEndSince)) {
            throw new IllegalArgumentException("currentWindowEndUntil must not be before currentWindowEndSince");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return jpaRepository.findMarkerRows(
                requiredProjectId,
                requiredApplicationId,
                requiredCurrentWindowEndSince,
                requiredCurrentWindowEndUntil,
                PageRequest.of(0, limit));
    }

    /**
     * Operational event history skeleton이 사용할 stored snapshot source rows를 newest slot first로 bounded 조회한다.
     *
     * <p>repository는 row metadata/helper columns/stored JSON만 운반하고 event type, severity, promotion, dedup,
     * suppression, p95/p99를 계산하지 않는다.</p>
     */
    @Transactional(readOnly = true)
    public List<DashboardSnapshotDetailRow> findOperationalHistoryRows(
            UUID projectId,
            UUID applicationId,
            OffsetDateTime currentWindowEndSince,
            OffsetDateTime currentWindowEndUntil,
            int limit) {
        UUID requiredProjectId = Objects.requireNonNull(projectId, "projectId must not be null");
        UUID requiredApplicationId = Objects.requireNonNull(applicationId, "applicationId must not be null");
        OffsetDateTime requiredCurrentWindowEndSince = Objects.requireNonNull(
                currentWindowEndSince,
                "currentWindowEndSince must not be null");
        OffsetDateTime requiredCurrentWindowEndUntil = Objects.requireNonNull(
                currentWindowEndUntil,
                "currentWindowEndUntil must not be null");
        if (requiredCurrentWindowEndUntil.isBefore(requiredCurrentWindowEndSince)) {
            throw new IllegalArgumentException("currentWindowEndUntil must not be before currentWindowEndSince");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return jpaRepository.findOperationalHistoryRows(
                requiredProjectId,
                requiredApplicationId,
                requiredCurrentWindowEndSince,
                requiredCurrentWindowEndUntil,
                PageRequest.of(0, limit));
    }

    /**
     * 같은 application의 retention horizon 안 strictly earlier snapshot 중 previous state source row를 하나 조회한다.
     */
    @Transactional(readOnly = true)
    public Optional<DashboardSnapshotSourceRow> findPreviousSnapshot(
            UUID applicationId,
            OffsetDateTime currentWindowEndUtc,
            OffsetDateTime currentWindowEndSince) {
        List<DashboardSnapshotSourceRow> rows = jpaRepository.findPreviousRows(
                Objects.requireNonNull(applicationId, "applicationId must not be null"),
                Objects.requireNonNull(currentWindowEndUtc, "currentWindowEndUtc must not be null"),
                Objects.requireNonNull(currentWindowEndSince, "currentWindowEndSince must not be null"),
                PageRequest.of(0, 1));
        return rows.stream().findFirst();
    }

    /**
     * 같은 application의 retention horizon 안 strictly earlier active snapshot 중 lastHealthyAt source row를 하나 조회한다.
     */
    @Transactional(readOnly = true)
    public Optional<DashboardSnapshotSourceRow> findPreviousActiveSnapshot(
            UUID applicationId,
            OffsetDateTime currentWindowEndUtc,
            OffsetDateTime currentWindowEndSince) {
        List<DashboardSnapshotSourceRow> rows = jpaRepository.findPreviousActiveRows(
                Objects.requireNonNull(applicationId, "applicationId must not be null"),
                Objects.requireNonNull(currentWindowEndUtc, "currentWindowEndUtc must not be null"),
                Objects.requireNonNull(currentWindowEndSince, "currentWindowEndSince must not be null"),
                PageRequest.of(0, 1));
        return rows.stream().findFirst();
    }

    /**
     * retention cleanup이 `current_window_end_utc < snapshotCutoffUtc`인 snapshot row만 물리 삭제하도록 위임한다.
     *
     * <p>`generated_at`, `created_at`, `accepted_at` 계열 timestamp는 cleanup predicate로 사용하지 않는다.</p>
     */
    @Transactional
    public long deleteDashboardSnapshotsWindowEndedBefore(OffsetDateTime snapshotCutoffUtc) {
        return jpaRepository.deleteDashboardSnapshotsWindowEndedBefore(
                toUtcOffsetDateTime(snapshotCutoffUtc));
    }

    private static OffsetDateTime toUtcOffsetDateTime(OffsetDateTime offsetDateTime) {
        return Objects.requireNonNull(offsetDateTime, "offsetDateTime must not be null")
                .withOffsetSameInstant(ZoneOffset.UTC);
    }
}
