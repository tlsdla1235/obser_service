package com.observation.portal.domain.ingest.repository;

import com.observation.portal.domain.ingest.entity.StarterHeartbeatTelemetryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * `starter_heartbeat_telemetry` table의 Spring Data JPA persistence 작업을 담당한다.
 */
@Repository
interface StarterHeartbeatTelemetryJpaRepository extends JpaRepository<StarterHeartbeatTelemetryEntity, UUID> {

    /**
     * heartbeat identity별 latest row를 조회한다.
     */
    Optional<StarterHeartbeatTelemetryEntity> findByProjectIdAndApplicationNameAndEnvironmentAndInstanceName(
            UUID projectId,
            String applicationName,
            String environment,
            String instanceName);

    /**
     * snapshot read model에서 target window 이후 heartbeat가 섞이지 않도록 identity별 bounded latest row를 조회한다.
     */
    Optional<StarterHeartbeatTelemetryEntity>
    findByProjectIdAndApplicationNameAndEnvironmentAndInstanceNameAndLastReceivedAtUtcLessThanEqual(
            UUID projectId,
            String applicationName,
            String environment,
            String instanceName,
            OffsetDateTime receivedAtOrBeforeUtc);

    /**
     * project scope에서 가장 최근에 수신된 heartbeat row를 조회한다.
     */
    Optional<StarterHeartbeatTelemetryEntity> findTopByProjectIdOrderByLastReceivedAtUtcDesc(UUID projectId);

    /**
     * project/application/environment scope에서 instance heartbeat 중 가장 최근 row를 조회한다.
     */
    Optional<StarterHeartbeatTelemetryEntity> findTopByProjectIdAndApplicationNameAndEnvironmentOrderByLastReceivedAtUtcDesc(
            UUID projectId,
            String applicationName,
            String environment);

    /**
     * snapshot read model에서 application scope latest heartbeat를 target window 이하로 제한해 조회한다.
     */
    Optional<StarterHeartbeatTelemetryEntity>
    findTopByProjectIdAndApplicationNameAndEnvironmentAndLastReceivedAtUtcLessThanEqualOrderByLastReceivedAtUtcDesc(
            UUID projectId,
            String applicationName,
            String environment,
            OffsetDateTime receivedAtOrBeforeUtc);

    /**
     * PostgreSQL unique key를 기준으로 latest heartbeat row를 원자적으로 insert/update한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            insert into starter_heartbeat_telemetry (
              id, project_id, application_name, environment, instance_name,
              starter_version, last_sent_at_utc, last_received_at_utc, last_sequence,
              interval_seconds, metadata_status, heartbeat_status, created_at, updated_at
            )
            values (
              :id, :projectId, :applicationName, :environment, :instanceName,
              :starterVersion, :lastSentAtUtc, :lastReceivedAtUtc, :lastSequence,
              :intervalSeconds, :metadataStatus, :heartbeatStatus, :nowUtc, :nowUtc
            )
            on conflict on constraint uk_starter_heartbeat_identity
            do update set
              starter_version = excluded.starter_version,
              last_sent_at_utc = excluded.last_sent_at_utc,
              last_received_at_utc = excluded.last_received_at_utc,
              last_sequence = excluded.last_sequence,
              interval_seconds = excluded.interval_seconds,
              metadata_status = excluded.metadata_status,
              heartbeat_status = excluded.heartbeat_status,
              updated_at = excluded.updated_at
            """, nativeQuery = true)
    void upsertLatest(
            @Param("id") UUID id,
            @Param("projectId") UUID projectId,
            @Param("applicationName") String applicationName,
            @Param("environment") String environment,
            @Param("instanceName") String instanceName,
            @Param("starterVersion") String starterVersion,
            @Param("lastSentAtUtc") OffsetDateTime lastSentAtUtc,
            @Param("lastReceivedAtUtc") OffsetDateTime lastReceivedAtUtc,
            @Param("lastSequence") long lastSequence,
            @Param("intervalSeconds") int intervalSeconds,
            @Param("metadataStatus") String metadataStatus,
            @Param("heartbeatStatus") String heartbeatStatus,
            @Param("nowUtc") OffsetDateTime nowUtc);
}
