package com.observation.portal.domain.bucket.repository;

import com.observation.portal.domain.bucket.model.AcceptedMetricBucketReceipt;
import com.observation.portal.domain.bucket.model.AcceptedMetricBucketWriteCommand;
import com.observation.portal.domain.catalog.entity.ProjectEntity;
import com.observation.portal.domain.catalog.repository.ApplicationInstanceRepository;
import com.observation.portal.domain.catalog.repository.ApplicationRepository;
import com.observation.portal.domain.catalog.repository.ProjectRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
class MetricBucketRepositoryIntegrationTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000003201");
    private static final OffsetDateTime FIXED_TIME = OffsetDateTime.parse("2026-05-08T01:00:31Z");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private MetricBucketRepository metricBucketRepository;

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
    void migrateSchema() {
        cleanAndMigrate();
        projectRepository.saveAndFlush(new ProjectEntity(
                PROJECT_ID,
                "checkout",
                "pk_repo",
                "$2a$10$repositoryhashrepositoryhashrepositoryhashrepositoryhash12",
                "active",
                FIXED_TIME,
                FIXED_TIME));
    }

    @Test
    void insertsAcceptedBucketAndCreatesCatalogRows() throws SQLException {
        AcceptedMetricBucketWriteCommand command = command(
                "project-123:orders-api:prod:pod-a:20260508T010000Z",
                "hash-1",
                "2026-05-08T01:00:00Z",
                FIXED_TIME);

        AcceptedMetricBucketReceipt receipt = metricBucketRepository.insert(command);

        assertThat(receipt.bucketId()).isNotNull();
        assertThat(receipt.acceptedAt()).isEqualTo(FIXED_TIME);
        assertThat(metricBucketRepository.findByProjectIdAndIdempotencyKey(PROJECT_ID, command.idempotencyKey()))
                .hasValue(receipt);

        var application = applicationRepository.findByProjectIdAndNameAndEnvironment(PROJECT_ID, "orders-api", "prod")
                .orElseThrow();
        var instance = applicationInstanceRepository.findByApplicationIdAndInstanceName(application.id(), "pod-a")
                .orElseThrow();
        assertThat(application.firstSeenAt()).isEqualTo(FIXED_TIME);
        assertThat(application.lastSeenAt()).isEqualTo(FIXED_TIME);
        assertThat(instance.firstSeenAt()).isEqualTo(FIXED_TIME);
        assertThat(instance.lastSeenAt()).isEqualTo(FIXED_TIME);

        assertPersistedBucketRow(receipt.bucketId(), command);
    }

    @Test
    void updatesCatalogLastSeenWhenExistingApplicationAndInstanceReceiveAnotherBucket() {
        AcceptedMetricBucketWriteCommand first = command(
                "project-123:orders-api:prod:pod-a:20260508T010000Z",
                "hash-1",
                "2026-05-08T01:00:00Z",
                FIXED_TIME);
        OffsetDateTime later = OffsetDateTime.parse("2026-05-08T01:01:01Z");
        AcceptedMetricBucketWriteCommand second = command(
                "project-123:orders-api:prod:pod-a:20260508T010030Z",
                "hash-2",
                "2026-05-08T01:00:30Z",
                later);

        metricBucketRepository.insert(first);
        metricBucketRepository.insert(second);

        var application = applicationRepository.findByProjectIdAndNameAndEnvironment(PROJECT_ID, "orders-api", "prod")
                .orElseThrow();
        var instance = applicationInstanceRepository.findByApplicationIdAndInstanceName(application.id(), "pod-a")
                .orElseThrow();
        assertThat(application.firstSeenAt()).isEqualTo(FIXED_TIME);
        assertThat(application.lastSeenAt()).isEqualTo(later);
        assertThat(instance.firstSeenAt()).isEqualTo(FIXED_TIME);
        assertThat(instance.lastSeenAt()).isEqualTo(later);
    }

    @Test
    void databaseEnforcesIdempotencyAndInstanceBucketUniqueness() throws SQLException {
        AcceptedMetricBucketWriteCommand first = command(
                "project-123:orders-api:prod:pod-a:20260508T010000Z",
                "hash-1",
                "2026-05-08T01:00:00Z",
                FIXED_TIME);
        AcceptedMetricBucketReceipt receipt = metricBucketRepository.insert(first);

        assertThatThrownBy(() -> metricBucketRepository.insert(command(
                "project-123:orders-api:prod:pod-a:20260508T010000Z",
                "hash-2",
                "2026-05-08T01:00:30Z",
                OffsetDateTime.parse("2026-05-08T01:01:01Z"))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> metricBucketRepository.insert(command(
                "project-123:orders-api:prod:pod-a:20260508T010030Z",
                "hash-3",
                "2026-05-08T01:00:00Z",
                OffsetDateTime.parse("2026-05-08T01:01:02Z"))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(countAcceptedBuckets()).isEqualTo(1);
        assertPersistedBucketRow(receipt.bucketId(), first);
        assertCatalogSeenAt(FIXED_TIME);
    }

    private static AcceptedMetricBucketWriteCommand command(
            String idempotencyKey,
            String payloadHash,
            String bucketStartUtc,
            OffsetDateTime acceptedAt) {
        OffsetDateTime start = OffsetDateTime.parse(bucketStartUtc);
        return new AcceptedMetricBucketWriteCommand(
                PROJECT_ID,
                "checkout",
                "orders-api",
                "prod",
                "pod-a",
                "1.0",
                idempotencyKey,
                payloadHash,
                start,
                start.plusSeconds(30),
                30,
                acceptedAt,
                3L,
                1L,
                List.of(
                        new AcceptedMetricBucketWriteCommand.DurationBucket(50L, 1L),
                        new AcceptedMetricBucketWriteCommand.DurationBucket(100L, 2L),
                        new AcceptedMetricBucketWriteCommand.DurationBucket(250L, 3L)),
                0.64d,
                0.71d,
                0.82d,
                new AcceptedMetricBucketWriteCommand.LocalPercentiles(
                        "instance_bucket",
                        "starter_local",
                        start.toString(),
                        start.plusSeconds(30).toString(),
                        3L,
                        250L,
                        1000L,
                        false),
                List.of(new AcceptedMetricBucketWriteCommand.EndpointBucket(
                        "GET",
                        "/orders/{orderId}",
                        2L,
                        0L,
                        List.of(new AcceptedMetricBucketWriteCommand.DurationBucket(50L, 1L)))));
    }

    private static void assertPersistedBucketRow(
            UUID bucketId,
            AcceptedMetricBucketWriteCommand command) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
             PreparedStatement statement = connection.prepareStatement(
                     """
                     select schema_version, idempotency_key, payload_hash,
                            bucket_start_utc, bucket_end_utc, request_count, error_count,
                            duration_buckets_json::text, endpoints_json::text,
                            cpu_usage_ratio, heap_used_ratio, datasource_pool_usage_ratio,
                            local_percentiles_json::text, accepted_at
                     from accepted_metric_buckets
                     where id = ?
                     """)) {
            statement.setObject(1, bucketId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString(1)).isEqualTo(command.schemaVersion());
                assertThat(resultSet.getString(2)).isEqualTo(command.idempotencyKey());
                assertThat(resultSet.getString(3)).isEqualTo(command.payloadHash());
                assertThat(resultSet.getObject(4, OffsetDateTime.class)).isEqualTo(command.bucketStartUtc());
                assertThat(resultSet.getObject(5, OffsetDateTime.class)).isEqualTo(command.bucketEndUtc());
                assertThat(resultSet.getLong(6)).isEqualTo(command.requestCount());
                assertThat(resultSet.getLong(7)).isEqualTo(command.errorCount());
                assertThat(resultSet.getString(8)).contains("\"leMs\": 50", "\"count\": 1");
                assertThat(resultSet.getString(9)).contains("\"method\": \"GET\"", "\"route\": \"/orders/{orderId}\"");
                assertThat(resultSet.getBigDecimal(10)).isEqualByComparingTo("0.64000");
                assertThat(resultSet.getBigDecimal(11)).isEqualByComparingTo("0.71000");
                assertThat(resultSet.getBigDecimal(12)).isEqualByComparingTo("0.82000");
                assertThat(resultSet.getString(13)).contains(
                        "\"scope\": \"instance_bucket\"",
                        "\"source\": \"starter_local\"",
                        "\"p95Ms\": 250",
                        "\"p99Ms\": 1000",
                        "\"mergeable\": false");
                assertThat(resultSet.getObject(14, OffsetDateTime.class)).isEqualTo(command.acceptedAt());
            }
        }
    }

    private void assertCatalogSeenAt(OffsetDateTime expectedSeenAt) {
        var application = applicationRepository.findByProjectIdAndNameAndEnvironment(PROJECT_ID, "orders-api", "prod")
                .orElseThrow();
        var instance = applicationInstanceRepository.findByApplicationIdAndInstanceName(application.id(), "pod-a")
                .orElseThrow();
        assertThat(application.firstSeenAt()).isEqualTo(expectedSeenAt);
        assertThat(application.lastSeenAt()).isEqualTo(expectedSeenAt);
        assertThat(instance.firstSeenAt()).isEqualTo(expectedSeenAt);
        assertThat(instance.lastSeenAt()).isEqualTo(expectedSeenAt);
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

    private static long countAcceptedBuckets() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
             PreparedStatement statement = connection.prepareStatement("select count(*) from accepted_metric_buckets");
             ResultSet resultSet = statement.executeQuery()) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }
}
