package com.observation.portal.domain.catalog.repository;

import com.observation.portal.domain.catalog.entity.ApplicationEntity;
import com.observation.portal.domain.catalog.entity.ApplicationInstanceEntity;
import com.observation.portal.domain.bucket.model.AcceptedMetricBucketWriteCommand;
import com.observation.portal.domain.catalog.model.ApplicationCatalogEntry;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * accepted ingest 시 application과 instance catalog row를 찾거나 생성하는 repository layer 구성요소다.
 *
 * <p>public onboarding API가 아니라 persistence path의 identity maintenance 용도로만 사용한다.</p>
 */
@Repository
public class ApplicationCatalogRepository {

    private static final String ACTIVE_STATUS = "active";

    private final ApplicationRepository applicationRepository;
    private final ApplicationInstanceRepository applicationInstanceRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * catalog identity 조회에 필요한 Spring Data repository들을 주입한다.
     */
    public ApplicationCatalogRepository(
            ApplicationRepository applicationRepository,
            ApplicationInstanceRepository applicationInstanceRepository,
            NamedParameterJdbcTemplate jdbcTemplate) {
        this.applicationRepository = Objects.requireNonNull(
                applicationRepository,
                "applicationRepository must not be null");
        this.applicationInstanceRepository = Objects.requireNonNull(
                applicationInstanceRepository,
                "applicationInstanceRepository must not be null");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    /**
     * application/environment와 instance name을 기준으로 catalog row를 찾거나 만들고 last-seen 시각을 갱신한다.
     */
    public ApplicationCatalogEntry getOrCreate(
            UUID projectId,
            String applicationName,
            String environment,
            String instanceName,
            OffsetDateTime seenAt) {
        UUID requiredProjectId = Objects.requireNonNull(projectId, "projectId must not be null");
        OffsetDateTime requiredSeenAt = Objects.requireNonNull(seenAt, "seenAt must not be null");

        ApplicationEntity application = applicationRepository
                .findByProjectIdAndNameAndEnvironment(requiredProjectId, applicationName, environment)
                .orElseGet(() -> new ApplicationEntity(
                        UUID.randomUUID(),
                        requiredProjectId,
                        applicationName,
                        environment,
                        ACTIVE_STATUS,
                        requiredSeenAt,
                        requiredSeenAt,
                        requiredSeenAt,
                        requiredSeenAt));
        application.markSeenAt(requiredSeenAt);
        ApplicationEntity savedApplication = applicationRepository.save(application);

        ApplicationInstanceEntity instance = applicationInstanceRepository
                .findByApplicationIdAndInstanceName(savedApplication.id(), instanceName)
                .orElseGet(() -> new ApplicationInstanceEntity(
                        UUID.randomUUID(),
                        savedApplication.id(),
                        instanceName,
                        requiredSeenAt,
                        requiredSeenAt,
                        requiredSeenAt,
                        requiredSeenAt));
        instance.markSeenAt(requiredSeenAt);
        ApplicationInstanceEntity savedInstance = applicationInstanceRepository.save(instance);

        return new ApplicationCatalogEntry(savedApplication.id(), savedInstance.id());
    }

    /**
     * batch writer가 command들을 application/instance identity별로 묶어 catalog get-or-create 횟수를 줄이도록 한다.
     *
     * <p>반환 목록은 입력 command 순서를 유지하므로 accepted bucket batch insert가 source message와 결과를 다시 매핑할 수 있다.</p>
     */
    public List<ApplicationCatalogEntry> getOrCreateBatch(List<AcceptedMetricBucketWriteCommand> commands) {
        List<AcceptedMetricBucketWriteCommand> requiredCommands = List.copyOf(
                Objects.requireNonNull(commands, "commands must not be null"));
        if (requiredCommands.isEmpty()) {
            return List.of();
        }

        Map<ApplicationGroupKey, SeenRange> applicationGroups = new LinkedHashMap<>();
        for (AcceptedMetricBucketWriteCommand command : requiredCommands) {
            applicationGroups.computeIfAbsent(ApplicationGroupKey.from(command), ignored -> SeenRange.empty())
                    .include(command.acceptedAt());
        }

        Map<ApplicationGroupKey, UUID> applications = new LinkedHashMap<>();
        for (Map.Entry<ApplicationGroupKey, SeenRange> entry : applicationGroups.entrySet()) {
            applications.put(entry.getKey(), upsertApplication(entry.getKey(), entry.getValue()));
        }

        Map<InstanceGroupKey, SeenRange> instanceGroups = new LinkedHashMap<>();
        for (AcceptedMetricBucketWriteCommand command : requiredCommands) {
            UUID applicationId = applications.get(ApplicationGroupKey.from(command));
            instanceGroups.computeIfAbsent(
                            new InstanceGroupKey(applicationId, command.instanceName()),
                            ignored -> SeenRange.empty())
                    .include(command.acceptedAt());
        }

        Map<InstanceGroupKey, UUID> instances = new LinkedHashMap<>();
        for (Map.Entry<InstanceGroupKey, SeenRange> entry : instanceGroups.entrySet()) {
            instances.put(entry.getKey(), upsertInstance(entry.getKey(), entry.getValue()));
        }

        List<ApplicationCatalogEntry> entries = new ArrayList<>(requiredCommands.size());
        for (AcceptedMetricBucketWriteCommand command : requiredCommands) {
            UUID applicationId = applications.get(ApplicationGroupKey.from(command));
            UUID instanceId = instances.get(new InstanceGroupKey(
                    applicationId,
                    command.instanceName()));
            entries.add(new ApplicationCatalogEntry(applicationId, instanceId));
        }
        return List.copyOf(entries);
    }

    private UUID upsertApplication(ApplicationGroupKey key, SeenRange seenRange) {
        String sql = """
                insert into applications (
                  id, project_id, name, environment, status, first_seen_at, last_seen_at, created_at, updated_at
                )
                values (
                  :id, :projectId, :name, :environment, :status, :firstSeenAt, :lastSeenAt, :createdAt, :updatedAt
                )
                on conflict (project_id, name, environment) do update
                set first_seen_at = least(
                      coalesce(applications.first_seen_at, excluded.first_seen_at),
                      excluded.first_seen_at
                    ),
                    last_seen_at = greatest(
                      coalesce(applications.last_seen_at, excluded.last_seen_at),
                      excluded.last_seen_at
                    ),
                    updated_at = greatest(applications.updated_at, excluded.updated_at)
                returning id
                """;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("projectId", key.projectId())
                .addValue("name", key.applicationName())
                .addValue("environment", key.environment())
                .addValue("status", ACTIVE_STATUS)
                .addValue("firstSeenAt", seenRange.firstSeenAt())
                .addValue("lastSeenAt", seenRange.lastSeenAt())
                .addValue("createdAt", seenRange.firstSeenAt())
                .addValue("updatedAt", seenRange.lastSeenAt());
        return jdbcTemplate.queryForObject(sql, parameters, UUID.class);
    }

    private UUID upsertInstance(InstanceGroupKey key, SeenRange seenRange) {
        String sql = """
                insert into application_instances (
                  id, application_id, instance_name, first_seen_at, last_seen_at, created_at, updated_at
                )
                values (
                  :id, :applicationId, :instanceName, :firstSeenAt, :lastSeenAt, :createdAt, :updatedAt
                )
                on conflict (application_id, instance_name) do update
                set first_seen_at = least(application_instances.first_seen_at, excluded.first_seen_at),
                    last_seen_at = greatest(application_instances.last_seen_at, excluded.last_seen_at),
                    updated_at = greatest(application_instances.updated_at, excluded.updated_at)
                returning id
                """;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("applicationId", key.applicationId())
                .addValue("instanceName", key.instanceName())
                .addValue("firstSeenAt", seenRange.firstSeenAt())
                .addValue("lastSeenAt", seenRange.lastSeenAt())
                .addValue("createdAt", seenRange.firstSeenAt())
                .addValue("updatedAt", seenRange.lastSeenAt());
        return jdbcTemplate.queryForObject(sql, parameters, UUID.class);
    }

    private record ApplicationGroupKey(UUID projectId, String applicationName, String environment) {

        private ApplicationGroupKey {
            Objects.requireNonNull(projectId, "projectId must not be null");
            applicationName = requireText(applicationName, "applicationName");
            environment = requireText(environment, "environment");
        }

        private static ApplicationGroupKey from(AcceptedMetricBucketWriteCommand command) {
            return new ApplicationGroupKey(command.projectId(), command.applicationName(), command.environment());
        }
    }

    private record InstanceGroupKey(UUID applicationId, String instanceName) {

        private InstanceGroupKey {
            Objects.requireNonNull(applicationId, "applicationId must not be null");
            instanceName = requireText(instanceName, "instanceName");
        }
    }

    private static final class SeenRange {

        private OffsetDateTime firstSeenAt;
        private OffsetDateTime lastSeenAt;

        private static SeenRange empty() {
            return new SeenRange();
        }

        private void include(OffsetDateTime seenAt) {
            OffsetDateTime requiredSeenAt = Objects.requireNonNull(seenAt, "seenAt must not be null");
            if (firstSeenAt == null || requiredSeenAt.isBefore(firstSeenAt)) {
                firstSeenAt = requiredSeenAt;
            }
            if (lastSeenAt == null || requiredSeenAt.isAfter(lastSeenAt)) {
                lastSeenAt = requiredSeenAt;
            }
        }

        private OffsetDateTime firstSeenAt() {
            return firstSeenAt;
        }

        private OffsetDateTime lastSeenAt() {
            return lastSeenAt;
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
