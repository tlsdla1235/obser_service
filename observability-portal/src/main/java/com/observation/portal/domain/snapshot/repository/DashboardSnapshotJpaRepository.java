package com.observation.portal.domain.snapshot.repository;

import com.observation.portal.domain.snapshot.entity.DashboardSnapshotEntity;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotTrendRow;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
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
     * 계산하지 않는다. `generatedAtUntil`은 service가 계산한 horizon 상한이며 future-dated snapshot이 limit를 차지하지 않게 한다.</p>
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
            + "and snapshot.generatedAt >= :generatedAtSince "
            + "and snapshot.generatedAt <= :generatedAtUntil "
            + "order by snapshot.generatedAt desc, snapshot.id desc")
    List<DashboardSnapshotTrendRow> findTrendRowsNewestFirst(
            @Param("projectId") UUID projectId,
            @Param("applicationId") UUID applicationId,
            @Param("generatedAtSince") OffsetDateTime generatedAtSince,
            @Param("generatedAtUntil") OffsetDateTime generatedAtUntil,
            Pageable pageable);
}
