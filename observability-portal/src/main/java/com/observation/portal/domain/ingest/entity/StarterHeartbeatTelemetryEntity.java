package com.observation.portal.domain.ingest.entity;

import com.observation.portal.domain.ingest.model.StarterHeartbeatTelemetryRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Flyway가 생성한 `starter_heartbeat_telemetry` table에 매핑되는 JPA entity다.
 *
 * <p>metric bucket이나 catalog row가 아니라 starter control-plane latest heartbeat만 저장한다.</p>
 */
@Entity
@Table(name = "starter_heartbeat_telemetry")
public class StarterHeartbeatTelemetryEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "application_name", nullable = false, length = 160)
    private String applicationName;

    @Column(name = "environment", nullable = false, length = 80)
    private String environment;

    @Column(name = "instance_name", nullable = false, length = 200)
    private String instanceName;

    @Column(name = "starter_version", nullable = false, length = 80)
    private String starterVersion;

    @Column(name = "last_sent_at_utc", nullable = false)
    private OffsetDateTime lastSentAtUtc;

    @Column(name = "last_received_at_utc", nullable = false)
    private OffsetDateTime lastReceivedAtUtc;

    @Column(name = "last_sequence", nullable = false)
    private long lastSequence;

    @Column(name = "interval_seconds", nullable = false)
    private int intervalSeconds;

    @Column(name = "metadata_status", nullable = false, length = 32)
    private String metadataStatus;

    @Column(name = "heartbeat_status", nullable = false, length = 32)
    private String heartbeatStatus;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Hibernate가 entity를 materialize할 때 사용하는 기본 생성자다.
     */
    protected StarterHeartbeatTelemetryEntity() {
    }

    /**
     * service/repository 경계로 전달 가능한 immutable telemetry model로 변환한다.
     */
    public StarterHeartbeatTelemetryRecord toRecord() {
        return new StarterHeartbeatTelemetryRecord(
                id,
                projectId,
                applicationName,
                environment,
                instanceName,
                starterVersion,
                lastSentAtUtc,
                lastReceivedAtUtc,
                lastSequence,
                intervalSeconds,
                metadataStatus,
                heartbeatStatus,
                createdAt,
                updatedAt);
    }
}
