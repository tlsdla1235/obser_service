package com.observation.portal.domain.ingest.repository;

import com.observation.portal.domain.catalog.entity.ProjectEntity;
import com.observation.portal.domain.catalog.repository.ProjectRepository;
import com.observation.portal.domain.ingest.model.StarterHeartbeatTelemetryCommand;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * starter heartbeat telemetry repository가 latest row만 갱신하고 bucket/catalog side effect를 만들지 않는지 검증한다.
 */
@Testcontainers
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
class StarterHeartbeatTelemetryRepositoryIntegrationTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000004201");
    private static final OffsetDateTime FIXED_TIME = OffsetDateTime.parse("2026-05-24T08:31:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private StarterHeartbeatTelemetryRepository heartbeatTelemetryRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void migrateSchema() {
        cleanAndMigrate();
        projectRepository.saveAndFlush(new ProjectEntity(
                PROJECT_ID,
                "heartbeat-project",
                "pk_heartbeat",
                "$2a$10$heartbeathashheartbeathashheartbeathashheartbeathash12",
                "active",
                FIXED_TIME,
                FIXED_TIME));
    }

    @Test
    void upsertsTelemetryUsingLastReceivedHeartbeat() throws SQLException {
        var first = heartbeatTelemetryRepository.upsertLatest(command(
                "orders-api",
                "prod",
                "pod-a",
                "0.1.0",
                "2026-05-24T08:30:00Z",
                "2026-05-24T08:31:00Z",
                10L,
                30));

        var second = heartbeatTelemetryRepository.upsertLatest(command(
                "orders-api",
                "prod",
                "pod-a",
                "0.1.1",
                "2026-05-24T08:29:00Z",
                "2026-05-24T08:32:00Z",
                9L,
                45));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(countRows("starter_heartbeat_telemetry")).isEqualTo(1);
        assertThat(heartbeatTelemetryRepository.findByIdentity(PROJECT_ID, "orders-api", "prod", "pod-a"))
                .hasValueSatisfying(record -> {
                    assertThat(record.starterVersion()).isEqualTo("0.1.1");
                    assertThat(record.lastSentAtUtc()).isEqualTo(OffsetDateTime.parse("2026-05-24T08:29:00Z"));
                    assertThat(record.lastReceivedAtUtc()).isEqualTo(OffsetDateTime.parse("2026-05-24T08:32:00Z"));
                    assertThat(record.lastSequence()).isEqualTo(9L);
                    assertThat(record.intervalSeconds()).isEqualTo(45);
                    assertThat(record.metadataStatus()).isEqualTo("valid");
                    assertThat(record.heartbeatStatus()).isEqualTo("received");
                    assertThat(record.createdAt()).isEqualTo(OffsetDateTime.parse("2026-05-24T08:31:00Z"));
                    assertThat(record.updatedAt()).isEqualTo(OffsetDateTime.parse("2026-05-24T08:32:00Z"));
                });
    }

    @Test
    void storesSeparateRowsForDifferentIdentityAndFindsProjectLatest() throws SQLException {
        heartbeatTelemetryRepository.upsertLatest(command(
                "orders-api",
                "prod",
                "pod-a",
                "0.1.0",
                "2026-05-24T08:30:00Z",
                "2026-05-24T08:31:00Z",
                1L,
                30));
        heartbeatTelemetryRepository.upsertLatest(command(
                "orders-api",
                "stage",
                "pod-a",
                "0.1.0",
                "2026-05-24T08:30:10Z",
                "2026-05-24T08:31:10Z",
                2L,
                30));
        heartbeatTelemetryRepository.upsertLatest(command(
                "orders-api",
                "prod",
                "pod-b",
                "0.1.0",
                "2026-05-24T08:30:20Z",
                "2026-05-24T08:31:20Z",
                3L,
                30));

        assertThat(countRows("starter_heartbeat_telemetry")).isEqualTo(3);
        assertThat(heartbeatTelemetryRepository.findLatestByProjectId(PROJECT_ID))
                .hasValueSatisfying(record -> {
                    assertThat(record.environment()).isEqualTo("prod");
                    assertThat(record.instanceName()).isEqualTo("pod-b");
                    assertThat(record.lastReceivedAtUtc()).isEqualTo(OffsetDateTime.parse("2026-05-24T08:31:20Z"));
                });
        assertThat(heartbeatTelemetryRepository.findLatestByApplicationScope(PROJECT_ID, "orders-api", "prod"))
                .hasValueSatisfying(record -> {
                    assertThat(record.environment()).isEqualTo("prod");
                    assertThat(record.instanceName()).isEqualTo("pod-b");
                    assertThat(record.lastReceivedAtUtc()).isEqualTo(OffsetDateTime.parse("2026-05-24T08:31:20Z"));
                });
        assertThat(heartbeatTelemetryRepository.findLatestByApplicationScope(PROJECT_ID, "orders-api", "stage"))
                .hasValueSatisfying(record -> {
                    assertThat(record.environment()).isEqualTo("stage");
                    assertThat(record.instanceName()).isEqualTo("pod-a");
                    assertThat(record.lastReceivedAtUtc()).isEqualTo(OffsetDateTime.parse("2026-05-24T08:31:10Z"));
                });
    }

    @Test
    void findsLatestHeartbeatAtOrBeforeSnapshotWindowBoundary() throws SQLException {
        heartbeatTelemetryRepository.upsertLatest(command(
                "orders-api",
                "prod",
                "pod-a",
                "0.1.0",
                "2026-05-24T08:29:30Z",
                "2026-05-24T08:30:00Z",
                1L,
                30));
        heartbeatTelemetryRepository.upsertLatest(command(
                "orders-api",
                "prod",
                "pod-b",
                "0.1.0",
                "2026-05-24T08:31:30Z",
                "2026-05-24T08:32:00Z",
                2L,
                30));
        OffsetDateTime snapshotWindowEnd = OffsetDateTime.parse("2026-05-24T08:31:00Z");

        assertThat(heartbeatTelemetryRepository.findLatestByApplicationScopeAtOrBeforeReceivedAt(
                PROJECT_ID,
                "orders-api",
                "prod",
                snapshotWindowEnd))
                .hasValueSatisfying(record -> {
                    assertThat(record.instanceName()).isEqualTo("pod-a");
                    assertThat(record.lastReceivedAtUtc()).isEqualTo(OffsetDateTime.parse("2026-05-24T08:30:00Z"));
                });
        assertThat(heartbeatTelemetryRepository.findByIdentityAtOrBeforeReceivedAt(
                PROJECT_ID,
                "orders-api",
                "prod",
                "pod-b",
                snapshotWindowEnd))
                .isEmpty();
    }

    @Test
    void heartbeatTelemetryDoesNotCreateAcceptedBucketOrCatalogRows() throws SQLException {
        heartbeatTelemetryRepository.upsertLatest(command(
                "orders-api",
                "prod",
                "pod-a",
                "0.1.0",
                "2026-05-24T08:30:00Z",
                "2026-05-24T08:31:00Z",
                1L,
                30));

        assertThat(countRows("starter_heartbeat_telemetry")).isEqualTo(1);
        assertThat(countRows("accepted_metric_buckets")).isZero();
        assertThat(countRows("applications")).isZero();
        assertThat(countRows("application_instances")).isZero();
    }

    private static StarterHeartbeatTelemetryCommand command(
            String applicationName,
            String environment,
            String instanceName,
            String starterVersion,
            String sentAtUtc,
            String receivedAtUtc,
            long sequence,
            int intervalSeconds) {
        return new StarterHeartbeatTelemetryCommand(
                PROJECT_ID,
                applicationName,
                environment,
                instanceName,
                starterVersion,
                OffsetDateTime.parse(sentAtUtc),
                OffsetDateTime.parse(receivedAtUtc),
                sequence,
                intervalSeconds,
                "valid",
                "received");
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

    private static long countRows(String tableName) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
             PreparedStatement statement = connection.prepareStatement("select count(*) from " + tableName);
             ResultSet resultSet = statement.executeQuery()) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }
}
