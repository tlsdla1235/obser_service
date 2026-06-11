package com.observation.portal.domain.snapshot.repository;

import com.observation.portal.domain.snapshot.entity.DashboardSnapshotEntity;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailRow;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotLatestRow;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotSourceRow;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotTrendRow;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * `dashboard_snapshots` table의 Spring Data JPA read-side 조회를 담당한다.
 */
@Repository
interface DashboardSnapshotJpaRepository extends JpaRepository<DashboardSnapshotEntity, UUID> {

    /**
     * instance trend projection 후보 snapshot을 newest-first로 조회한다.
     *
     * <p>projection은 row metadata와 저장 JSON만 전달하며 lifecycle state, rule, endpoint priority, marker, p95/p99를
     * 계산하지 않는다. `currentWindowEndUntil`은 service가 계산한 slot horizon 상한이며 future slot snapshot이 limit를
     * 차지하지 않게 한다.</p>
     */
    @Query("select new com.observation.portal.domain.snapshot.model.DashboardSnapshotTrendRow("
            + "snapshot.id, "
            + "snapshot.generatedAt, "
            + "snapshot.currentWindowEndUtc, "
            + "snapshot.stateCode, "
            + "snapshot.captureReason, "
            + "snapshot.readModelJson) "
            + "from DashboardSnapshotEntity snapshot "
            + "where snapshot.projectId = :projectId "
            + "and snapshot.applicationId = :applicationId "
            + "and snapshot.currentWindowEndUtc >= :currentWindowEndSince "
            + "and snapshot.currentWindowEndUtc <= :currentWindowEndUntil "
            + "order by snapshot.currentWindowEndUtc desc, snapshot.generatedAt desc, snapshot.id asc")
    List<DashboardSnapshotTrendRow> findTrendRowsNewestFirst(
            @Param("projectId") UUID projectId,
            @Param("applicationId") UUID applicationId,
            @Param("currentWindowEndSince") OffsetDateTime currentWindowEndSince,
            @Param("currentWindowEndUntil") OffsetDateTime currentWindowEndUntil,
            Pageable pageable);

    /**
     * project/application/snapshot catalog path 정합성이 모두 맞는 detail source row를 조회한다.
     */
    @Query("select new com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailRow("
            + "snapshot.id, "
            + "snapshot.projectId, "
            + "snapshot.applicationId, "
            + "snapshot.generatedAt, "
            + "snapshot.currentWindowStartUtc, "
            + "snapshot.currentWindowEndUtc, "
            + "snapshot.baselineWindowStartUtc, "
            + "snapshot.baselineWindowEndUtc, "
            + "snapshot.stateCode, "
            + "snapshot.captureReason, "
            + "snapshot.primaryRuleId, "
            + "snapshot.primaryEndpointKey, "
            + "snapshot.maxConfidence, "
            + "snapshot.readModelJson) "
            + "from DashboardSnapshotEntity snapshot "
            + "where snapshot.projectId = :projectId "
            + "and snapshot.applicationId = :applicationId "
            + "and snapshot.id = :snapshotId")
    Optional<DashboardSnapshotDetailRow> findDetailRow(
            @Param("projectId") UUID projectId,
            @Param("applicationId") UUID applicationId,
            @Param("snapshotId") UUID snapshotId);

    /**
     * marker horizon 안의 stored snapshot row를 slot ASC, capturedAt ASC, snapshot id ASC 후보 순서로 bounded 조회한다.
     */
    @Query("select new com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailRow("
            + "snapshot.id, "
            + "snapshot.projectId, "
            + "snapshot.applicationId, "
            + "snapshot.generatedAt, "
            + "snapshot.currentWindowStartUtc, "
            + "snapshot.currentWindowEndUtc, "
            + "snapshot.baselineWindowStartUtc, "
            + "snapshot.baselineWindowEndUtc, "
            + "snapshot.stateCode, "
            + "snapshot.captureReason, "
            + "snapshot.primaryRuleId, "
            + "snapshot.primaryEndpointKey, "
            + "snapshot.maxConfidence, "
            + "snapshot.readModelJson) "
            + "from DashboardSnapshotEntity snapshot "
            + "where snapshot.projectId = :projectId "
            + "and snapshot.applicationId = :applicationId "
            + "and snapshot.currentWindowEndUtc >= :currentWindowEndSince "
            + "and snapshot.currentWindowEndUtc <= :currentWindowEndUntil "
            + "order by snapshot.currentWindowEndUtc asc, snapshot.generatedAt asc, snapshot.id asc")
    List<DashboardSnapshotDetailRow> findMarkerRows(
            @Param("projectId") UUID projectId,
            @Param("applicationId") UUID applicationId,
            @Param("currentWindowEndSince") OffsetDateTime currentWindowEndSince,
            @Param("currentWindowEndUntil") OffsetDateTime currentWindowEndUntil,
            Pageable pageable);

    /**
     * operational event history source boundary용 stored snapshot row를 newest-first로 조회한다.
     *
     * <p>projection은 row metadata/helper columns/stored JSON만 반환하며 event promotion, dedup, suppression, p95/p99를
     * 계산하지 않는다.</p>
     */
    @Query("select new com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailRow("
            + "snapshot.id, "
            + "snapshot.projectId, "
            + "snapshot.applicationId, "
            + "snapshot.generatedAt, "
            + "snapshot.currentWindowStartUtc, "
            + "snapshot.currentWindowEndUtc, "
            + "snapshot.baselineWindowStartUtc, "
            + "snapshot.baselineWindowEndUtc, "
            + "snapshot.stateCode, "
            + "snapshot.captureReason, "
            + "snapshot.primaryRuleId, "
            + "snapshot.primaryEndpointKey, "
            + "snapshot.maxConfidence, "
            + "snapshot.readModelJson) "
            + "from DashboardSnapshotEntity snapshot "
            + "where snapshot.projectId = :projectId "
            + "and snapshot.applicationId = :applicationId "
            + "and snapshot.currentWindowEndUtc >= :currentWindowEndSince "
            + "and snapshot.currentWindowEndUtc <= :currentWindowEndUntil "
            + "order by snapshot.currentWindowEndUtc desc, snapshot.generatedAt desc, snapshot.id asc")
    List<DashboardSnapshotDetailRow> findOperationalHistoryRows(
            @Param("projectId") UUID projectId,
            @Param("applicationId") UUID applicationId,
            @Param("currentWindowEndSince") OffsetDateTime currentWindowEndSince,
            @Param("currentWindowEndUntil") OffsetDateTime currentWindowEndUntil,
            Pageable pageable);

    /**
     * 같은 application의 strictly earlier current window snapshot 중 previous state 후보를 조회한다.
     */
    @Query("select new com.observation.portal.domain.snapshot.model.DashboardSnapshotSourceRow("
            + "snapshot.id, "
            + "snapshot.generatedAt, "
            + "snapshot.currentWindowEndUtc, "
            + "snapshot.stateCode) "
            + "from DashboardSnapshotEntity snapshot "
            + "where snapshot.applicationId = :applicationId "
            + "and snapshot.currentWindowEndUtc < :currentWindowEndUtc "
            + "and snapshot.currentWindowEndUtc >= :currentWindowEndSince "
            + "order by snapshot.currentWindowEndUtc desc, snapshot.generatedAt desc, snapshot.id asc")
    List<DashboardSnapshotSourceRow> findPreviousRows(
            @Param("applicationId") UUID applicationId,
            @Param("currentWindowEndUtc") OffsetDateTime currentWindowEndUtc,
            @Param("currentWindowEndSince") OffsetDateTime currentWindowEndSince,
            Pageable pageable);

    /**
     * 같은 application의 previous active snapshot 중 lastHealthyAt source 후보를 조회한다.
     */
    @Query("select new com.observation.portal.domain.snapshot.model.DashboardSnapshotSourceRow("
            + "snapshot.id, "
            + "snapshot.generatedAt, "
            + "snapshot.currentWindowEndUtc, "
            + "snapshot.stateCode) "
            + "from DashboardSnapshotEntity snapshot "
            + "where snapshot.applicationId = :applicationId "
            + "and snapshot.currentWindowEndUtc < :currentWindowEndUtc "
            + "and snapshot.currentWindowEndUtc >= :currentWindowEndSince "
            + "and snapshot.stateCode = 'active' "
            + "order by snapshot.currentWindowEndUtc desc, snapshot.generatedAt desc, snapshot.id asc")
    List<DashboardSnapshotSourceRow> findPreviousActiveRows(
            @Param("applicationId") UUID applicationId,
            @Param("currentWindowEndUtc") OffsetDateTime currentWindowEndUtc,
            @Param("currentWindowEndSince") OffsetDateTime currentWindowEndSince,
            Pageable pageable);

    /**
     * cleanup cutoff보다 오래된 dashboard snapshot row를 current window end 기준으로 물리 삭제한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from DashboardSnapshotEntity snapshot "
            + "where snapshot.currentWindowEndUtc < :snapshotCutoffUtc")
    int deleteDashboardSnapshotsWindowEndedBefore(
            @Param("snapshotCutoffUtc") OffsetDateTime snapshotCutoffUtc);

    /**
     * writer upsert identity에 해당하는 row를 transaction 안에서 잠그고 조회한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DashboardSnapshotEntity> findByApplicationIdAndCurrentWindowEndUtc(
            UUID applicationId,
            OffsetDateTime currentWindowEndUtc);

    /**
     * dashboard query fallback threshold 판단을 위해 최신 snapshot metadata만 조회한다.
     */
    @Query("select new com.observation.portal.domain.snapshot.model.DashboardSnapshotLatestRow("
            + "snapshot.id, "
            + "snapshot.generatedAt, "
            + "snapshot.currentWindowEndUtc, "
            + "snapshot.stateCode, "
            + "snapshot.captureReason) "
            + "from DashboardSnapshotEntity snapshot "
            + "where snapshot.applicationId = :applicationId "
            + "order by snapshot.generatedAt desc, snapshot.id desc")
    List<DashboardSnapshotLatestRow> findLatestRowsByApplicationId(
            @Param("applicationId") UUID applicationId,
            Pageable pageable);
}
