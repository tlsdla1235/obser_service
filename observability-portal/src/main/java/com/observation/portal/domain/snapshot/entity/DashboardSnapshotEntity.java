package com.observation.portal.domain.snapshot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.observation.portal.domain.snapshot.model.DashboardSnapshotWriteValues;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
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

    @Column(name = "primary_rule_id", length = 80)
    private String primaryRuleId;

    @Column(name = "primary_endpoint_key", length = 240)
    private String primaryEndpointKey;

    @Column(name = "max_confidence", precision = 4, scale = 3)
    private BigDecimal maxConfidence;

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

    /**
     * snapshot writer가 insert할 새 persistence entity를 만든다.
     */
    public DashboardSnapshotEntity(DashboardSnapshotWriteValues values) {
        applyNewValues(Objects.requireNonNull(values, "values must not be null"));
    }

    /**
     * higher-priority incoming read model을 대표 row 값으로 반영한다.
     *
     * <p>id와 createdAt은 최초 insert 값을 유지하고, generatedAt/state/capture reason/read model/helper column만 새
     * 대표 read model 기준으로 교체한다.</p>
     */
    public void updateRepresentative(DashboardSnapshotWriteValues values) {
        DashboardSnapshotWriteValues requiredValues = Objects.requireNonNull(values, "values must not be null");
        this.projectId = requiredValues.projectId();
        this.applicationId = requiredValues.applicationId();
        this.generatedAt = requiredValues.generatedAt();
        this.currentWindowStartUtc = requiredValues.currentWindowStartUtc();
        this.currentWindowEndUtc = requiredValues.currentWindowEndUtc();
        this.baselineWindowStartUtc = requiredValues.baselineWindowStartUtc();
        this.baselineWindowEndUtc = requiredValues.baselineWindowEndUtc();
        this.lastAcceptedIngestAt = requiredValues.lastAcceptedIngestAt();
        this.lastObservedAt = requiredValues.lastObservedAt();
        this.stateCode = requiredValues.stateCode();
        this.captureReason = requiredValues.captureReason();
        this.primaryRuleId = requiredValues.primaryRuleId();
        this.primaryEndpointKey = requiredValues.primaryEndpointKey();
        this.maxConfidence = requiredValues.maxConfidence();
        this.readModelJson = requiredValues.readModelJson();
    }

    private void applyNewValues(DashboardSnapshotWriteValues values) {
        this.id = values.snapshotId();
        this.createdAt = values.createdAt();
        updateRepresentative(values);
    }

    public UUID id() {
        return id;
    }

    public OffsetDateTime generatedAt() {
        return generatedAt;
    }

    public OffsetDateTime currentWindowEndUtc() {
        return currentWindowEndUtc;
    }

    public String captureReason() {
        return captureReason;
    }
}
