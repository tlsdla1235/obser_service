package com.observation.portal.domain.bucket.entity;

import com.observation.portal.domain.bucket.model.AcceptedMetricBucketReceipt;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Flyway가 생성한 `accepted_metric_buckets` table에 매핑되는 JPA entity다.
 *
 * <p>validated 30초 bucket의 persistence model이며 controller DTO나 service result로 직접 노출하지 않는다.</p>
 */
@Entity
@Table(name = "accepted_metric_buckets")
public class AcceptedMetricBucketEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "application_instance_id", nullable = false)
    private UUID applicationInstanceId;

    @Column(name = "schema_version", nullable = false, length = 16)
    private String schemaVersion;

    @Column(name = "idempotency_key", nullable = false, length = 300)
    private String idempotencyKey;

    @Column(name = "payload_hash", nullable = false, length = 128)
    private String payloadHash;

    @Column(name = "bucket_start_utc", nullable = false)
    private OffsetDateTime bucketStartUtc;

    @Column(name = "bucket_end_utc", nullable = false)
    private OffsetDateTime bucketEndUtc;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(name = "accepted_at", nullable = false)
    private OffsetDateTime acceptedAt;

    @Column(name = "request_count", nullable = false)
    private long requestCount;

    @Column(name = "error_count", nullable = false)
    private long errorCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "duration_buckets_json", nullable = false, columnDefinition = "jsonb")
    private String durationBucketsJson;

    @Column(name = "cpu_usage_ratio", precision = 6, scale = 5)
    private BigDecimal cpuUsageRatio;

    @Column(name = "heap_used_ratio", precision = 6, scale = 5)
    private BigDecimal heapUsedRatio;

    @Column(name = "datasource_pool_usage_ratio", precision = 6, scale = 5)
    private BigDecimal datasourcePoolUsageRatio;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "endpoints_json", nullable = false, columnDefinition = "jsonb")
    private String endpointsJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /**
     * Hibernate가 entity를 materialize할 때 사용하는 기본 생성자다.
     */
    protected AcceptedMetricBucketEntity() {
    }

    /**
     * accepted bucket insert path에서 application-generated UUID와 validated 값을 사용해 entity를 만든다.
     */
    public AcceptedMetricBucketEntity(
            UUID id,
            UUID projectId,
            UUID applicationId,
            UUID applicationInstanceId,
            String schemaVersion,
            String idempotencyKey,
            String payloadHash,
            OffsetDateTime bucketStartUtc,
            OffsetDateTime bucketEndUtc,
            int durationSeconds,
            OffsetDateTime acceptedAt,
            long requestCount,
            long errorCount,
            String durationBucketsJson,
            BigDecimal cpuUsageRatio,
            BigDecimal heapUsedRatio,
            BigDecimal datasourcePoolUsageRatio,
            String endpointsJson,
            OffsetDateTime createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.projectId = Objects.requireNonNull(projectId, "projectId must not be null");
        this.applicationId = Objects.requireNonNull(applicationId, "applicationId must not be null");
        this.applicationInstanceId = Objects.requireNonNull(
                applicationInstanceId,
                "applicationInstanceId must not be null");
        this.schemaVersion = requireText(schemaVersion, "schemaVersion");
        this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        this.payloadHash = requireText(payloadHash, "payloadHash");
        this.bucketStartUtc = Objects.requireNonNull(bucketStartUtc, "bucketStartUtc must not be null");
        this.bucketEndUtc = Objects.requireNonNull(bucketEndUtc, "bucketEndUtc must not be null");
        this.durationSeconds = durationSeconds;
        this.acceptedAt = Objects.requireNonNull(acceptedAt, "acceptedAt must not be null");
        this.requestCount = requestCount;
        this.errorCount = errorCount;
        this.durationBucketsJson = requireText(durationBucketsJson, "durationBucketsJson");
        this.cpuUsageRatio = cpuUsageRatio;
        this.heapUsedRatio = heapUsedRatio;
        this.datasourcePoolUsageRatio = datasourcePoolUsageRatio;
        this.endpointsJson = requireText(endpointsJson, "endpointsJson");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    /**
     * service/controller 경계로 전달 가능한 저장 receipt로 변환한다.
     */
    public AcceptedMetricBucketReceipt toReceipt() {
        return new AcceptedMetricBucketReceipt(id, acceptedAt);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
