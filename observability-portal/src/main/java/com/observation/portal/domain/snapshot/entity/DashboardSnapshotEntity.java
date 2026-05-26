package com.observation.portal.domain.snapshot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Flyway가 생성한 `dashboard_snapshots` table에 매핑되는 JPA persistence model이다.
 *
 * <p>저장된 dashboard read model JSON과 row metadata를 read-side projection으로 조회하기 위한 entity이며,
 * controller response나 service 외부 반환 모델로 직접 노출하지 않는다.</p>
 */
@Entity
@Table(name = "dashboard_snapshots")
public class DashboardSnapshotEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    @Column(name = "current_window_start_utc", nullable = false)
    private OffsetDateTime currentWindowStartUtc;

    @Column(name = "current_window_end_utc", nullable = false)
    private OffsetDateTime currentWindowEndUtc;

    @Column(name = "baseline_window_start_utc", nullable = false)
    private OffsetDateTime baselineWindowStartUtc;

    @Column(name = "baseline_window_end_utc", nullable = false)
    private OffsetDateTime baselineWindowEndUtc;

    @Column(name = "last_accepted_ingest_at")
    private OffsetDateTime lastAcceptedIngestAt;

    @Column(name = "last_observed_at")
    private OffsetDateTime lastObservedAt;

    @Column(name = "state_code", nullable = false, length = 40)
    private String stateCode;

    @Column(name = "capture_reason", length = 64)
    private String captureReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "read_model_json", nullable = false, columnDefinition = "jsonb")
    private String readModelJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /**
     * Hibernate가 entity를 materialize할 때 사용하는 기본 생성자다.
     */
    protected DashboardSnapshotEntity() {
    }
}
