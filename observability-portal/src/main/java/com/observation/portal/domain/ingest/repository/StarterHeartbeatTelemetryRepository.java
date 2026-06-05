package com.observation.portal.domain.ingest.repository;

import com.observation.portal.domain.ingest.model.StarterHeartbeatTelemetryCommand;
import com.observation.portal.domain.ingest.model.StarterHeartbeatTelemetryRecord;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * starter heartbeat latest telemetry 저장과 조회를 담당하는 repository facade다.
 *
 * <p>accepted metric bucket이나 application catalog를 만들지 않고 heartbeat 전용 table만 갱신한다.</p>
 */
@Repository
public class StarterHeartbeatTelemetryRepository {

    private final StarterHeartbeatTelemetryJpaRepository jpaRepository;

    /**
     * heartbeat telemetry JPA repository를 감싼다.
     */
    public StarterHeartbeatTelemetryRepository(StarterHeartbeatTelemetryJpaRepository jpaRepository) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository, "jpaRepository must not be null");
    }

    /**
     * 같은 project/application/environment/instance identity의 latest heartbeat row를 insert/update한다.
     */
    @Transactional
    public StarterHeartbeatTelemetryRecord upsertLatest(StarterHeartbeatTelemetryCommand command) {
        StarterHeartbeatTelemetryCommand requiredCommand = Objects.requireNonNull(
                command,
                "command must not be null");
        jpaRepository.upsertLatest(
                UUID.randomUUID(),
                requiredCommand.projectId(),
                requiredCommand.applicationName(),
                requiredCommand.environment(),
                requiredCommand.instanceName(),
                requiredCommand.starterVersion(),
                requiredCommand.lastSentAtUtc(),
                requiredCommand.lastReceivedAtUtc(),
                requiredCommand.lastSequence(),
                requiredCommand.intervalSeconds(),
                requiredCommand.metadataStatus(),
                requiredCommand.heartbeatStatus(),
                requiredCommand.lastReceivedAtUtc());
        return findByIdentity(
                requiredCommand.projectId(),
                requiredCommand.applicationName(),
                requiredCommand.environment(),
                requiredCommand.instanceName())
                .orElseThrow(() -> new IllegalStateException("starter heartbeat telemetry upsert failed"));
    }

    /**
     * heartbeat identity별 latest row를 조회한다.
     */
    @Transactional(readOnly = true)
    public Optional<StarterHeartbeatTelemetryRecord> findByIdentity(
            UUID projectId,
            String applicationName,
            String environment,
            String instanceName) {
        return jpaRepository.findByProjectIdAndApplicationNameAndEnvironmentAndInstanceName(
                        Objects.requireNonNull(projectId, "projectId must not be null"),
                        requireText(applicationName, "applicationName"),
                        requireText(environment, "environment"),
                        requireText(instanceName, "instanceName"))
                .map(entity -> entity.toRecord());
    }

    /**
     * snapshot 저장 시점의 target window 이후 heartbeat를 제외하고 identity별 latest row를 조회한다.
     */
    @Transactional(readOnly = true)
    public Optional<StarterHeartbeatTelemetryRecord> findByIdentityAtOrBeforeReceivedAt(
            UUID projectId,
            String applicationName,
            String environment,
            String instanceName,
            OffsetDateTime receivedAtOrBeforeUtc) {
        return jpaRepository
                .findByProjectIdAndApplicationNameAndEnvironmentAndInstanceNameAndLastReceivedAtUtcLessThanEqual(
                        Objects.requireNonNull(projectId, "projectId must not be null"),
                        requireText(applicationName, "applicationName"),
                        requireText(environment, "environment"),
                        requireText(instanceName, "instanceName"),
                        toUtc(Objects.requireNonNull(
                                receivedAtOrBeforeUtc,
                                "receivedAtOrBeforeUtc must not be null")))
                .map(entity -> entity.toRecord());
    }

    /**
     * project scope에서 가장 최근에 수신된 starter heartbeat를 조회한다.
     */
    @Transactional(readOnly = true)
    public Optional<StarterHeartbeatTelemetryRecord> findLatestByProjectId(UUID projectId) {
        return jpaRepository.findTopByProjectIdOrderByLastReceivedAtUtcDesc(
                        Objects.requireNonNull(projectId, "projectId must not be null"))
                .map(entity -> entity.toRecord());
    }

    /**
     * Application List navigation에서 사용할 application/environment scope latest heartbeat row를 조회한다.
     *
     * <p>project-wide latest가 아니라 같은 project/application/environment에 속한 instance row 중 최신 수신 시각만 반환한다.</p>
     */
    @Transactional(readOnly = true)
    public Optional<StarterHeartbeatTelemetryRecord> findLatestByApplicationScope(
            UUID projectId,
            String applicationName,
            String environment) {
        return jpaRepository
                .findTopByProjectIdAndApplicationNameAndEnvironmentOrderByLastReceivedAtUtcDesc(
                        Objects.requireNonNull(projectId, "projectId must not be null"),
                        requireText(applicationName, "applicationName"),
                        requireText(environment, "environment"))
                .map(entity -> entity.toRecord());
    }

    /**
     * snapshot application read model에서 target window 이후 heartbeat를 제외하고 application scope latest row를 조회한다.
     */
    @Transactional(readOnly = true)
    public Optional<StarterHeartbeatTelemetryRecord> findLatestByApplicationScopeAtOrBeforeReceivedAt(
            UUID projectId,
            String applicationName,
            String environment,
            OffsetDateTime receivedAtOrBeforeUtc) {
        return jpaRepository
                .findTopByProjectIdAndApplicationNameAndEnvironmentAndLastReceivedAtUtcLessThanEqualOrderByLastReceivedAtUtcDesc(
                        Objects.requireNonNull(projectId, "projectId must not be null"),
                        requireText(applicationName, "applicationName"),
                        requireText(environment, "environment"),
                        toUtc(Objects.requireNonNull(
                                receivedAtOrBeforeUtc,
                                "receivedAtOrBeforeUtc must not be null")))
                .map(entity -> entity.toRecord());
    }

    private static OffsetDateTime toUtc(OffsetDateTime value) {
        return value.withOffsetSameInstant(ZoneOffset.UTC);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
