package com.observation.portal.domain.snapshot.repository;

import com.observation.portal.domain.snapshot.model.DashboardSnapshotTrendRow;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Stored dashboard snapshot을 instance trend projection용 read-only row로 조회하는 repository facade다.
 *
 * <p>이 repository는 `dashboard_snapshots.read_model_json`을 해석하지 않고 row metadata와 JSON source만 반환한다.
 * state/rule/priority/p95/p99/marker/recovery 의미 계산은 수행하지 않는다.</p>
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
     * project/application scope와 horizon closed range에 맞는 snapshot row를 최신순으로 bounded 조회한다.
     *
     * <p>`generatedAtUntil`은 조회 시점 이후 snapshot이 응답과 limit에 섞이지 않도록 하는 상한이다. caller는 이 결과를
     * 필요한 만큼 선택한 뒤 `capturedAt ASC`, `snapshotId ASC`로 다시 정렬할 수 있다.</p>
     */
    @Transactional(readOnly = true)
    public List<DashboardSnapshotTrendRow> findTrendRowsNewestFirst(
            UUID projectId,
            UUID applicationId,
            OffsetDateTime generatedAtSince,
            OffsetDateTime generatedAtUntil,
            int limit) {
        UUID requiredProjectId = Objects.requireNonNull(projectId, "projectId must not be null");
        UUID requiredApplicationId = Objects.requireNonNull(applicationId, "applicationId must not be null");
        OffsetDateTime requiredGeneratedAtSince = Objects.requireNonNull(
                generatedAtSince,
                "generatedAtSince must not be null");
        OffsetDateTime requiredGeneratedAtUntil = Objects.requireNonNull(
                generatedAtUntil,
                "generatedAtUntil must not be null");
        if (requiredGeneratedAtUntil.isBefore(requiredGeneratedAtSince)) {
            throw new IllegalArgumentException("generatedAtUntil must not be before generatedAtSince");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return jpaRepository.findTrendRowsNewestFirst(
                requiredProjectId,
                requiredApplicationId,
                requiredGeneratedAtSince,
                requiredGeneratedAtUntil,
                PageRequest.of(0, limit));
    }
}
