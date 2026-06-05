package com.observation.portal.domain.bucket.repository;

import com.observation.portal.domain.bucket.model.AcceptedMetricBucketBatchItemResult;
import com.observation.portal.domain.bucket.model.AcceptedMetricBucketBatchItemStatus;
import com.observation.portal.domain.bucket.model.AcceptedMetricBucketBatchWriteResult;
import com.observation.portal.domain.bucket.model.AcceptedMetricBucketWriteCommand;
import com.observation.portal.domain.catalog.entity.ProjectEntity;
import com.observation.portal.domain.catalog.repository.ProjectRepository;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
class MetricBucketRepositoryBatchIntegrationTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000003201");
    private static final OffsetDateTime FIXED_TIME = OffsetDateTime.parse("2026-05-08T01:00:31Z");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private MetricBucketRepository metricBucketRepository;

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
                "checkout",
                "pk_batch",
                "$2a$10$repositoryhashrepositoryhashrepositoryhashrepositoryhash12",
                "active",
                FIXED_TIME,
                FIXED_TIME));
    }

    @Test
    void batchInsertPersistsNewRowsAndClassifiesPreReadDuplicatesAndConflicts() throws SQLException {
        AcceptedMetricBucketWriteCommand first = command("pod-a", "key-a", "hash-a", "2026-05-08T01:00:00Z", FIXED_TIME);
        AcceptedMetricBucketWriteCommand second = command("pod-b", "key-b", "hash-b", "2026-05-08T01:00:00Z", FIXED_TIME);
        AcceptedMetricBucketBatchWriteResult initial = metricBucketRepository.insertBatch(List.of(first, second));

        assertThat(initial.items()).extracting(AcceptedMetricBucketBatchItemResult::status)
                .containsExactly(
                        AcceptedMetricBucketBatchItemStatus.INSERTED,
                        AcceptedMetricBucketBatchItemStatus.INSERTED);
        assertThat(initial.bucketStatementCount()).isLessThanOrEqualTo(4);

        AcceptedMetricBucketWriteCommand duplicate = command("pod-a", "key-a", "hash-a", "2026-05-08T01:00:00Z", FIXED_TIME);
        AcceptedMetricBucketWriteCommand idempotencyConflict =
                command("pod-a", "key-a", "hash-changed", "2026-05-08T01:00:00Z", FIXED_TIME);
        AcceptedMetricBucketWriteCommand instanceConflict =
                command("pod-b", "key-b-other", "hash-other", "2026-05-08T01:00:00Z", FIXED_TIME);
        AcceptedMetricBucketWriteCommand newRow =
                command("pod-c", "key-c", "hash-c", "2026-05-08T01:00:30Z", FIXED_TIME.plusSeconds(30));

        AcceptedMetricBucketBatchWriteResult classified = metricBucketRepository.insertBatch(List.of(
                duplicate,
                idempotencyConflict,
                instanceConflict,
                newRow));

        assertThat(classified.items()).extracting(AcceptedMetricBucketBatchItemResult::status)
                .containsExactly(
                        AcceptedMetricBucketBatchItemStatus.DUPLICATE_NOOP,
                        AcceptedMetricBucketBatchItemStatus.IDEMPOTENCY_PAYLOAD_CONFLICT,
                        AcceptedMetricBucketBatchItemStatus.INSTANCE_BUCKET_IDENTITY_CONFLICT,
                        AcceptedMetricBucketBatchItemStatus.INSERTED);
        assertThat(countAcceptedBuckets()).isEqualTo(3);
    }

    @Test
    void onConflictReturningPostReadClassifiesRowsSkippedInsideSameBatch() throws SQLException {
        AcceptedMetricBucketWriteCommand winner =
                command("pod-a", "key-a", "hash-a", "2026-05-08T01:00:00Z", FIXED_TIME);
        AcceptedMetricBucketWriteCommand duplicate =
                command("pod-a", "key-a", "hash-a", "2026-05-08T01:00:00Z", FIXED_TIME.plusSeconds(1));
        AcceptedMetricBucketWriteCommand instanceConflict =
                command("pod-a", "key-a-other", "hash-other", "2026-05-08T01:00:00Z", FIXED_TIME.plusSeconds(2));

        AcceptedMetricBucketBatchWriteResult result = metricBucketRepository.insertBatch(List.of(
                winner,
                duplicate,
                instanceConflict));

        assertThat(result.items()).extracting(AcceptedMetricBucketBatchItemResult::status)
                .containsExactly(
                        AcceptedMetricBucketBatchItemStatus.INSERTED,
                        AcceptedMetricBucketBatchItemStatus.DUPLICATE_NOOP,
                        AcceptedMetricBucketBatchItemStatus.INSTANCE_BUCKET_IDENTITY_CONFLICT);
        assertThat(countAcceptedBuckets()).isEqualTo(1);
    }

    private static AcceptedMetricBucketWriteCommand command(
            String instanceName,
            String keySuffix,
            String payloadHash,
            String bucketStartUtc,
            OffsetDateTime acceptedAt) {
        OffsetDateTime start = OffsetDateTime.parse(bucketStartUtc);
        return new AcceptedMetricBucketWriteCommand(
                PROJECT_ID,
                "checkout",
                "orders-api",
                "prod",
                instanceName,
                "1.0",
                "project-123:orders-api:prod:%s:%s".formatted(instanceName, keySuffix),
                payloadHash,
                start,
                start.plusSeconds(30),
                30,
                acceptedAt,
                3L,
                1L,
                List.of(
                        new AcceptedMetricBucketWriteCommand.DurationBucket(50L, 1L),
                        new AcceptedMetricBucketWriteCommand.DurationBucket(100L, 3L)),
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
                        3L,
                        1L,
                        List.of(new AcceptedMetricBucketWriteCommand.DurationBucket(100L, 3L)))));
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
