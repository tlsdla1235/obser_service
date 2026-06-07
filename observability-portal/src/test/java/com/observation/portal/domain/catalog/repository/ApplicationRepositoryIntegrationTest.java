package com.observation.portal.domain.catalog.repository;

import com.observation.portal.domain.catalog.entity.ApplicationEntity;
import com.observation.portal.domain.catalog.entity.ApplicationInstanceEntity;
import com.observation.portal.domain.catalog.entity.ProjectEntity;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
class ApplicationRepositoryIntegrationTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000005851");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-27T13:05:00Z");
    private static final OffsetDateTime RETENTION_CUTOFF = OffsetDateTime.parse("2026-05-13T13:05:00Z");

    private static final UUID ACTIVE_RECENT_APP_ID = UUID.fromString("00000000-0000-0000-0000-000000005861");
    private static final UUID NO_BUCKET_APP_ID = UUID.fromString("00000000-0000-0000-0000-000000005862");
    private static final UUID HEARTBEAT_ONLY_APP_ID = UUID.fromString("00000000-0000-0000-0000-000000005863");
    private static final UUID DISABLED_APP_ID = UUID.fromString("00000000-0000-0000-0000-000000005864");
    private static final UUID OLD_BUCKET_APP_ID = UUID.fromString("00000000-0000-0000-0000-000000005865");
    private static final UUID FUTURE_ONLY_APP_ID = UUID.fromString("00000000-0000-0000-0000-000000005866");
    private static final UUID LATE_ACCEPTED_APP_ID = UUID.fromString("00000000-0000-0000-0000-000000005867");
    private static final UUID ACTIVE_LONG_INTERVAL_APP_ID = UUID.fromString("00000000-0000-0000-0000-000000005868");
    private static final UUID NO_HEARTBEAT_APP_ID = UUID.fromString("00000000-0000-0000-0000-000000005869");
    private static final UUID STALE_HEARTBEAT_APP_ID = UUID.fromString("00000000-0000-0000-0000-000000005870");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ApplicationInstanceRepository applicationInstanceRepository;

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void migrateAndSeedProject() {
        cleanAndMigrate();
        projectRepository.saveAndFlush(new ProjectEntity(
                PROJECT_ID,
                "scheduler-eligibility-project",
                "scheduler",
                "$2a$10$schedulerhashschedulerhashschedulerhash12",
                "active",
                NOW,
                NOW));
    }

    @Test
    void findsOnlyActiveApplicationsWithAcceptedBucketAndRecentHeartbeatInsideRetentionHorizon() throws SQLException {
        seedApplication(ACTIVE_RECENT_APP_ID, "active-recent", "active");
        seedApplication(ACTIVE_LONG_INTERVAL_APP_ID, "active-long-interval", "active");
        seedApplication(NO_HEARTBEAT_APP_ID, "active-no-heartbeat", "active");
        seedApplication(STALE_HEARTBEAT_APP_ID, "stale-heartbeat", "active");
        seedApplication(NO_BUCKET_APP_ID, "active-no-bucket", "active");
        seedApplication(HEARTBEAT_ONLY_APP_ID, "heartbeat-only", "active");
        seedApplication(DISABLED_APP_ID, "disabled-recent", "disabled");
        seedApplication(OLD_BUCKET_APP_ID, "active-old-bucket", "active");
        seedApplication(FUTURE_ONLY_APP_ID, "active-future-only", "active");
        seedApplication(LATE_ACCEPTED_APP_ID, "late-accepted", "active");

        insertAcceptedBucket(ACTIVE_RECENT_APP_ID, "2026-05-27T12:59:30Z");
        insertHeartbeat("active-recent", "pod-a", "2026-05-27T13:03:30Z");
        insertAcceptedBucket(ACTIVE_LONG_INTERVAL_APP_ID, "2026-05-27T12:59:30Z");
        insertHeartbeat("active-long-interval", "pod-a", "2026-05-27T13:02:01Z", 60);
        insertAcceptedBucket(NO_HEARTBEAT_APP_ID, "2026-05-27T12:59:30Z");
        insertAcceptedBucket(STALE_HEARTBEAT_APP_ID, "2026-05-27T12:59:30Z");
        insertHeartbeat("stale-heartbeat", "pod-a", "2026-05-27T13:03:29Z");
        insertAcceptedBucket(DISABLED_APP_ID, "2026-05-27T12:59:30Z");
        insertHeartbeat("disabled-recent", "pod-a", "2026-05-27T13:04:45Z");
        insertAcceptedBucket(OLD_BUCKET_APP_ID, "2026-05-01T12:59:30Z");
        insertHeartbeat("active-old-bucket", "pod-a", "2026-05-27T13:04:45Z");
        insertAcceptedBucket(FUTURE_ONLY_APP_ID, "2026-05-27T13:00:30Z");
        insertHeartbeat("active-future-only", "pod-a", "2026-05-27T13:04:45Z");
        insertAcceptedBucket(
                LATE_ACCEPTED_APP_ID,
                "2026-05-27T12:59:30Z",
                OffsetDateTime.parse("2026-05-27T13:02:01Z"));
        insertHeartbeat("late-accepted", "pod-a", "2026-05-27T13:04:45Z");
        insertHeartbeat("heartbeat-only", "pod-a", "2026-05-27T13:04:45Z");

        List<ApplicationEntity> applications =
                applicationRepository.findActiveApplicationsEligibleForScheduledSnapshot(
                        RETENTION_CUTOFF,
                        OffsetDateTime.parse("2026-05-27T13:00:00Z"),
                        OffsetDateTime.parse("2026-05-27T13:02:00Z"),
                        NOW);

        assertThat(applications)
                .extracting(ApplicationEntity::id)
                .containsExactly(ACTIVE_RECENT_APP_ID, ACTIVE_LONG_INTERVAL_APP_ID);
    }

    private void seedApplication(UUID applicationId, String applicationName, String status) {
        applicationRepository.saveAndFlush(new ApplicationEntity(
                applicationId,
                PROJECT_ID,
                applicationName,
                "prod",
                status,
                NOW.minusDays(1),
                NOW,
                NOW.minusDays(1),
                NOW));
        applicationInstanceRepository.saveAndFlush(new ApplicationInstanceEntity(
                instanceId(applicationId),
                applicationId,
                "pod-a",
                NOW.minusDays(1),
                NOW,
                NOW.minusDays(1),
                NOW));
    }

    private static void insertAcceptedBucket(UUID applicationId, String bucketEndText) throws SQLException {
        OffsetDateTime bucketEnd = OffsetDateTime.parse(bucketEndText);
        insertAcceptedBucket(applicationId, bucketEndText, bucketEnd.plusSeconds(1));
    }

    private static void insertAcceptedBucket(
            UUID applicationId,
            String bucketEndText,
            OffsetDateTime acceptedAt) throws SQLException {
        OffsetDateTime bucketEnd = OffsetDateTime.parse(bucketEndText);
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                     insert into accepted_metric_buckets (
                       id, project_id, application_id, application_instance_id, schema_version,
                       idempotency_key, payload_hash, bucket_start_utc, bucket_end_utc, duration_seconds,
                       accepted_at, request_count, error_count, duration_buckets_json,
                       endpoints_json, created_at
                     )
                     values (
                       ?, ?, ?, ?, '1.0',
                       ?, 'hash', ?, ?, 30,
                       ?, 100, 0, '[{"leMs":500,"count":100}]'::jsonb,
                       '[]'::jsonb, ?
                     )
                     """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, PROJECT_ID);
            statement.setObject(3, applicationId);
            statement.setObject(4, instanceId(applicationId));
            statement.setString(5, "scheduler:%s:%s".formatted(applicationId, bucketEndText));
            statement.setObject(6, bucketEnd.minusSeconds(30));
            statement.setObject(7, bucketEnd);
            statement.setObject(8, acceptedAt);
            statement.setObject(9, acceptedAt);
            statement.executeUpdate();
        }
    }

    private static void insertHeartbeat(
            String applicationName,
            String instanceName,
            String receivedAtText) throws SQLException {
        insertHeartbeat(applicationName, instanceName, receivedAtText, 30);
    }

    private static void insertHeartbeat(
            String applicationName,
            String instanceName,
            String receivedAtText,
            int intervalSeconds) throws SQLException {
        OffsetDateTime receivedAt = OffsetDateTime.parse(receivedAtText);
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                     insert into starter_heartbeat_telemetry (
                       id, project_id, application_name, environment, instance_name, starter_version,
                       last_sent_at_utc, last_received_at_utc, last_sequence, interval_seconds,
                       metadata_status, heartbeat_status, created_at, updated_at
                     )
                     values (?, ?, ?, 'prod', ?, '1.0.0', ?, ?, 1, ?, 'valid', 'received', ?, ?)
                     """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, PROJECT_ID);
            statement.setString(3, applicationName);
            statement.setString(4, instanceName);
            statement.setObject(5, receivedAt.minusSeconds(1));
            statement.setObject(6, receivedAt);
            statement.setInt(7, intervalSeconds);
            statement.setObject(8, receivedAt);
            statement.setObject(9, receivedAt);
            statement.executeUpdate();
        }
    }

    private static UUID instanceId(UUID applicationId) {
        String suffix = applicationId.toString().substring(24);
        return UUID.fromString("10000000-0000-0000-0000-" + suffix);
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void cleanAndMigrate() {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();

        flyway.clean();
        flyway.migrate();
    }
}
