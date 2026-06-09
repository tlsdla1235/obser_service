package com.observation.portal.domain.snapshot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.portal.domain.catalog.entity.ApplicationEntity;
import com.observation.portal.domain.catalog.entity.ApplicationInstanceEntity;
import com.observation.portal.domain.catalog.entity.ProjectEntity;
import com.observation.portal.domain.catalog.repository.ApplicationInstanceRepository;
import com.observation.portal.domain.catalog.repository.ApplicationRepository;
import com.observation.portal.domain.catalog.repository.ProjectRepository;
import com.observation.portal.domain.dashboard.model.ApplicationDashboardReadModel;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotCaptureReason;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotWriteCommand;
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

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
class DashboardSnapshotWriterServiceIntegrationTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000005801");
    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000005811");
    private static final UUID INSTANCE_ID = UUID.fromString("00000000-0000-0000-0000-000000005821");
    private static final OffsetDateTime WINDOW_END = OffsetDateTime.parse("2026-05-26T08:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private DashboardSnapshotWriterService writerService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ApplicationInstanceRepository applicationInstanceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void migrateAndSeedCatalog() throws SQLException {
        cleanAndMigrate();
        OffsetDateTime now = OffsetDateTime.parse("2026-05-26T07:59:30Z");
        projectRepository.saveAndFlush(new ProjectEntity(
                PROJECT_ID,
                "snapshot-writer-project",
                "writer",
                "$2a$10$writerhashwriterhashwriterhashwriterhash12",
                "active",
                now,
                now));
        applicationRepository.saveAndFlush(new ApplicationEntity(
                APPLICATION_ID,
                PROJECT_ID,
                "orders-api",
                "prod",
                "active",
                now.minusHours(1),
                now,
                now.minusHours(1),
                now));
        applicationInstanceRepository.saveAndFlush(new ApplicationInstanceEntity(
                INSTANCE_ID,
                APPLICATION_ID,
                "pod-a",
                now.minusHours(1),
                now,
                now.minusHours(1),
                now));
        insertAcceptedBucket();
    }

    @Test
    void upsertsSameIdentityByReasonPriorityAndDoesNotDowngradeHelperColumns() throws Exception {
        writerService.write(command(
                DashboardSnapshotCaptureReason.HOURLY_SCHEDULED,
                "active",
                OffsetDateTime.parse("2026-05-26T08:00:00Z"),
                List.of(),
                List.of(endpointPriority(1, "GET", "/health", "endpoint_error_spike", 0.70d))));

        SnapshotRow inserted = findOnlySnapshot();

        writerService.write(command(
                DashboardSnapshotCaptureReason.STATE_CHANGE,
                "degraded",
                OffsetDateTime.parse("2026-05-26T08:01:00Z"),
                List.of(triageCard("global_error_spike", "POST /orders", 0.84d)),
                List.of(endpointPriority(1, "POST", "/orders", "endpoint_error_spike", 0.86d))));

        SnapshotRow upgraded = findOnlySnapshot();

        writerService.write(command(
                DashboardSnapshotCaptureReason.HOURLY_SCHEDULED,
                "active",
                OffsetDateTime.parse("2026-05-26T08:02:00Z"),
                List.of(),
                List.of(endpointPriority(1, "GET", "/health", "endpoint_error_spike", 0.70d))));

        SnapshotRow finalRow = findOnlySnapshot();

        assertThat(upgraded.snapshotId()).isEqualTo(inserted.snapshotId());
        assertThat(upgraded.createdAt()).isEqualTo(inserted.createdAt());
        assertThat(upgraded.captureReason()).isEqualTo("state_change");
        assertThat(upgraded.stateCode()).isEqualTo("degraded");
        assertThat(upgraded.generatedAt()).isEqualTo(OffsetDateTime.parse("2026-05-26T08:01:00Z"));
        assertThat(upgraded.primaryRuleId()).isEqualTo("global_error_spike");
        assertThat(upgraded.primaryEndpointKey()).isEqualTo("POST /orders");
        assertThat(upgraded.maxConfidence()).isEqualByComparingTo("0.860");

        assertThat(finalRow).usingRecursiveComparison().isEqualTo(upgraded);
    }

    @Test
    void storesBoundedEndpointEvidenceAndStory57CompatibleInstanceSummary() throws Exception {
        List<ApplicationDashboardReadModel.EndpointPriorityItem> endpointPriority = new ArrayList<>();
        for (int index = 1; index <= 12; index++) {
            endpointPriority.add(endpointPriority(index, "POST", "/orders/" + index, "endpoint_error_spike", 0.90d));
        }

        writerService.write(command(
                DashboardSnapshotCaptureReason.HIGH_CONFIDENCE_CONCERN,
                "degraded",
                OffsetDateTime.parse("2026-05-26T08:03:00Z"),
                List.of(triageCard("global_error_spike", "POST /orders", 0.84d)),
                endpointPriority));

        JsonNode readModelJson = objectMapper.readTree(findOnlySnapshot().readModelJson());

        JsonNode endpointEvidence = readModelJson.path(DashboardSnapshotReadModelEnricher.SNAPSHOT_ENDPOINT_EVIDENCE_FIELD);
        assertThat(endpointEvidence.path("items")).hasSize(10);
        assertThat(endpointEvidence.path("items").get(0).path("rank").asInt()).isEqualTo(1);
        assertThat(endpointEvidence.path("items").get(0).has("rawPath")).isFalse();
        assertThat(endpointEvidence.path("items").get(0).has("endpointP95Ms")).isFalse();
        assertThat(endpointEvidence.path("items").get(0).has("errorCount")).isFalse();

        JsonNode instanceSummary = readModelJson.path("instanceSummary");
        assertThat(instanceSummary.path("schemaVersion").asText()).isEqualTo("1.0");
        assertThat(instanceSummary.path("source").asText()).isEqualTo("bounded_instance_summary");
        assertThat(instanceSummary.path("maxItems").asInt()).isEqualTo(50);
        assertThat(instanceSummary.path("selectionPolicy").asText())
                .isEqualTo("triage_contributors_then_freshness_attention_then_high_request_count");
        assertThat(instanceSummary.path("items")).hasSize(1);
        JsonNode item = instanceSummary.path("items").get(0);
        assertThat(item.path("instanceId").asText()).isEqualTo(INSTANCE_ID.toString());
        assertThat(item.path("metricData").path("statusSource").asText()).isEqualTo("accepted_bucket");
        assertThat(item.path("starterConnection").path("statusSource").asText()).isEqualTo("starter_heartbeat");
        assertThat(item.path("starterConnection").path("stateImpact").asText()).isEqualTo("none");
        assertThat(item.has("starterPercentilePoint")).isTrue();
        assertThat(item.has("resourceHints")).isTrue();
        assertThat(item.has("applicationTriageContribution")).isTrue();
        assertThat(item.has("endpointEvidenceRefs")).isTrue();
        assertThat(item.path("endpointEvidenceRefs").findValues("requestCount")).isEmpty();
        assertThat(item.has("snapshotDetailAnchor")).isFalse();
    }

    @Test
    void buildsInstanceSummaryFromFullCatalogCandidatesBeforeApplyingFiftyItemCap() throws Exception {
        for (int index = 2; index <= 55; index++) {
            UUID instanceId = instanceId(index);
            seedInstance(instanceId, "pod-%02d".formatted(index), WINDOW_END.minusSeconds(index));
            insertAcceptedBucket(
                    bucketId(index),
                    instanceId,
                    "bulk-%02d".formatted(index),
                    WINDOW_END.minusSeconds(30),
                    1L,
                    0L,
                    "[]");
        }

        writerService.write(command(
                DashboardSnapshotCaptureReason.HOURLY_SCHEDULED,
                "active",
                OffsetDateTime.parse("2026-05-26T08:04:00Z"),
                List.of(),
                List.of()));

        JsonNode items = objectMapper.readTree(findOnlySnapshot().readModelJson())
                .path("instanceSummary")
                .path("items");
        List<String> instanceIds = new ArrayList<>();
        items.forEach(item -> instanceIds.add(item.path("instanceId").asText()));

        assertThat(items).hasSize(50);
        assertThat(instanceIds).contains(instanceId(2).toString());
        assertThat(instanceIds).doesNotContain(instanceId(55).toString());
    }

    @Test
    void ordersInstanceSummaryByTriageFreshnessRequestCountAndDeterministicTieBreakers() throws Exception {
        UUID freshnessAttentionId = instanceId(2);
        UUID highRequestId = instanceId(3);
        UUID lowRequestId = instanceId(4);
        seedInstance(freshnessAttentionId, "pod-freshness", WINDOW_END.minusMinutes(10));
        seedInstance(highRequestId, "pod-high-request", WINDOW_END.minusSeconds(20));
        seedInstance(lowRequestId, "pod-low-request", WINDOW_END.minusSeconds(10));
        insertAcceptedBucket(
                bucketId(2),
                freshnessAttentionId,
                "freshness",
                WINDOW_END.minusMinutes(10),
                1L,
                0L,
                "[]");
        insertAcceptedBucket(
                bucketId(3),
                highRequestId,
                "high-request",
                WINDOW_END.minusSeconds(30),
                250L,
                0L,
                "[]");
        insertAcceptedBucket(
                bucketId(4),
                lowRequestId,
                "low-request",
                WINDOW_END.minusSeconds(30),
                25L,
                0L,
                "[]");

        writerService.write(command(
                DashboardSnapshotCaptureReason.HIGH_CONFIDENCE_CONCERN,
                "degraded",
                OffsetDateTime.parse("2026-05-26T08:05:00Z"),
                List.of(triageCard("global_error_spike", "POST /orders", 0.84d)),
                List.of(endpointPriority(1, "POST", "/orders", "endpoint_error_spike", 0.86d))));

        JsonNode items = objectMapper.readTree(findOnlySnapshot().readModelJson())
                .path("instanceSummary")
                .path("items");

        assertThat(items.get(0).path("instanceId").asText()).isEqualTo(INSTANCE_ID.toString());
        assertThat(items.get(1).path("instanceId").asText()).isEqualTo(freshnessAttentionId.toString());
        assertThat(items.get(2).path("instanceId").asText()).isEqualTo(highRequestId.toString());
        assertThat(items.get(3).path("instanceId").asText()).isEqualTo(lowRequestId.toString());
    }

    @Test
    void persistsShortStrongSpikeWhenRecentBadBucketsAreIndependentEvidence() throws Exception {
        insertAcceptedBucket(
                bucketId(2),
                INSTANCE_ID,
                "second-bad-bucket",
                WINDOW_END.minusSeconds(90),
                100L,
                8L,
                "[]");

        writerService.write(command(
                DashboardSnapshotCaptureReason.HOURLY_SCHEDULED,
                "active",
                OffsetDateTime.parse("2026-05-26T08:06:00Z"),
                List.of(),
                List.of(endpointPriority(1, "POST", "/orders", "endpoint_error_spike", 0.91d))));

        assertThat(findOnlySnapshot().captureReason()).isEqualTo("short_strong_spike");
    }

    @Test
    void persistsHighConfidenceConcernAheadOfShortStrongSpikeWhenBothAreEligible() throws Exception {
        insertAcceptedBucket(
                bucketId(2),
                INSTANCE_ID,
                "second-bad-bucket",
                WINDOW_END.minusSeconds(90),
                100L,
                8L,
                "[]");

        writerService.write(command(
                DashboardSnapshotCaptureReason.HOURLY_SCHEDULED,
                "degraded",
                OffsetDateTime.parse("2026-05-26T08:07:00Z"),
                List.of(triageCard("global_error_spike", "POST /orders", 0.95d)),
                List.of(endpointPriority(1, "POST", "/orders", "endpoint_error_spike", 0.95d))));

        assertThat(findOnlySnapshot().captureReason()).isEqualTo("high_confidence_concern");
    }

    private DashboardSnapshotWriteCommand command(
            DashboardSnapshotCaptureReason reason,
            String stateCode,
            OffsetDateTime generatedAt,
            List<ApplicationDashboardReadModel.TriageCard> triageCards,
            List<ApplicationDashboardReadModel.EndpointPriorityItem> endpointPriority) {
        return new DashboardSnapshotWriteCommand(
                PROJECT_ID,
                APPLICATION_ID,
                readModel(stateCode, generatedAt, triageCards, endpointPriority),
                reason,
                WINDOW_END,
                generatedAt,
                generatedAt,
                "test");
    }

    private ApplicationDashboardReadModel readModel(
            String stateCode,
            OffsetDateTime generatedAt,
            List<ApplicationDashboardReadModel.TriageCard> triageCards,
            List<ApplicationDashboardReadModel.EndpointPriorityItem> endpointPriority) {
        boolean hasTriage = !triageCards.isEmpty();
        return new ApplicationDashboardReadModel(
                generatedAt,
                new ApplicationDashboardReadModel.Application(
                        PROJECT_ID,
                        APPLICATION_ID,
                        "orders-api",
                        "prod",
                        WINDOW_END.minusSeconds(30),
                        null,
                        new ApplicationDashboardReadModel.SourceWindow(
                                new ApplicationDashboardReadModel.Window(WINDOW_END.minusMinutes(15), WINDOW_END),
                                new ApplicationDashboardReadModel.Window(
                                        WINDOW_END.minusMinutes(30),
                                        WINDOW_END.minusMinutes(15))),
                        new ApplicationDashboardReadModel.Freshness(
                                WINDOW_END.minusSeconds(30),
                                WINDOW_END.plusSeconds(60),
                                WINDOW_END.plusSeconds(150))),
                new ApplicationDashboardReadModel.State(
                        stateCode,
                        "상태",
                        "테스트 상태입니다.",
                        "테스트 액션을 확인하세요.",
                        "application"),
                new ApplicationDashboardReadModel.StarterConnection(
                        "starter_heartbeat",
                        WINDOW_END.minusSeconds(20),
                        "received",
                        "starter_connected",
                        "none"),
                hasTriage ? null : new ApplicationDashboardReadModel.ZeroInsight(
                        "no_action_needed",
                        "현재 우선 조치가 필요한 신호는 없습니다.",
                        "트래픽이 유지되는지 관찰하세요."),
                new ApplicationDashboardReadModel.Recovery(false, null, null, null),
                new ApplicationDashboardReadModel.Metrics(100L, 5L, new BigDecimal("0.05")),
                ApplicationDashboardReadModel.SourceScopedPercentiles.empty(),
                ApplicationDashboardReadModel.HistogramDistribution.empty(),
                triageCards,
                endpointPriority,
                List.of(new ApplicationDashboardReadModel.InstanceEntry(
                        INSTANCE_ID,
                        "pod-a",
                        WINDOW_END.minusSeconds(30),
                        new ApplicationDashboardReadModel.InstanceEntryLinks(
                                "/api/projects/%s/applications/%s/instances/%s/evidence".formatted(
                                        PROJECT_ID,
                                        APPLICATION_ID,
                                        INSTANCE_ID)))),
                null);
    }

    private static ApplicationDashboardReadModel.TriageCard triageCard(
            String ruleId,
            String affectedEndpoint,
            double confidence) {
        return new ApplicationDashboardReadModel.TriageCard(
                ruleId,
                ApplicationDashboardReadModel.TriageSeverity.WARNING,
                "오류율 증가",
                "오류율이 증가했습니다.",
                "관련 endpoint를 먼저 확인하세요.",
                confidence,
                86,
                affectedEndpoint,
                new ApplicationDashboardReadModel.TriageEvidence(
                        100L,
                        5L,
                        new BigDecimal("0.05"),
                        100L,
                        0L,
                        BigDecimal.ZERO,
                        new BigDecimal("0.05"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        "current",
                        null));
    }

    private static ApplicationDashboardReadModel.EndpointPriorityItem endpointPriority(
            int rank,
            String method,
            String route,
            String ruleId,
            double confidence) {
        String endpointKey = method + " " + route;
        return new ApplicationDashboardReadModel.EndpointPriorityItem(
                rank,
                method,
                route,
                endpointKey,
                ApplicationDashboardReadModel.EndpointPriorityReason.ERROR_SPIKE,
                List.of(ruleId),
                confidence,
                88,
                new ApplicationDashboardReadModel.EndpointPriorityFreshness(
                        "current",
                        WINDOW_END.minusSeconds(30),
                        "current",
                        null),
                new ApplicationDashboardReadModel.EndpointPriorityEvidence(
                        100L,
                        5L,
                        new BigDecimal("0.05"),
                        100L,
                        0L,
                        BigDecimal.ZERO,
                        new BigDecimal("0.05"),
                        List.of(new ApplicationDashboardReadModel.HistogramBucket(500L, 100L)),
                        List.of(new ApplicationDashboardReadModel.HistogramBucket(500L, 100L)),
                        null,
                        null,
                        null,
                        "accepted_bucket",
                        ApplicationDashboardReadModel.EndpointEvidenceStatus.AVAILABLE,
                        ApplicationDashboardReadModel.EndpointEvidenceStatus.AVAILABLE),
                "이 endpoint를 먼저 확인하세요.");
    }

    private SnapshotRow findOnlySnapshot() throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                     select id, capture_reason, state_code, generated_at, created_at,
                            primary_rule_id, primary_endpoint_key, max_confidence, read_model_json::text
                     from dashboard_snapshots
                     where application_id = ?
                     """)) {
            statement.setObject(1, APPLICATION_ID);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                SnapshotRow row = new SnapshotRow(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("capture_reason"),
                        resultSet.getString("state_code"),
                        resultSet.getObject("generated_at", OffsetDateTime.class),
                        resultSet.getObject("created_at", OffsetDateTime.class),
                        resultSet.getString("primary_rule_id"),
                        resultSet.getString("primary_endpoint_key"),
                        resultSet.getBigDecimal("max_confidence"),
                        resultSet.getString("read_model_json"));
                assertThat(resultSet.next()).isFalse();
                return row;
            }
        }
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

    private static void insertAcceptedBucket() throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                     insert into accepted_metric_buckets (
                       id, project_id, application_id, application_instance_id, schema_version,
                       idempotency_key, payload_hash, bucket_start_utc, bucket_end_utc, duration_seconds,
                       accepted_at, request_count, error_count, duration_buckets_json,
                       cpu_usage_ratio, heap_used_ratio, datasource_pool_usage_ratio,
                       local_percentiles_json, endpoints_json, created_at
                     )
                     values (
                       ?, ?, ?, ?, '1.0',
                       ?, 'hash-1', ?, ?, 30,
                       ?, 100, 5, '[{"leMs":500,"count":100}]'::jsonb,
                       0.41000, 0.62000, 0.37000,
                       ?::jsonb, ?::jsonb, ?
                     )
                     """)) {
            OffsetDateTime bucketStart = OffsetDateTime.parse("2026-05-26T07:59:00Z");
            OffsetDateTime bucketEnd = OffsetDateTime.parse("2026-05-26T07:59:30Z");
            statement.setObject(1, UUID.fromString("00000000-0000-0000-0000-000000005831"));
            statement.setObject(2, PROJECT_ID);
            statement.setObject(3, APPLICATION_ID);
            statement.setObject(4, INSTANCE_ID);
            statement.setString(5, "writer:orders-api:prod:pod-a:20260526T075900Z");
            statement.setObject(6, bucketStart);
            statement.setObject(7, bucketEnd);
            statement.setObject(8, OffsetDateTime.parse("2026-05-26T07:59:31Z"));
            statement.setString(9, """
                    {
                      "scope": "instance_bucket",
                      "source": "starter_local",
                      "bucketStartUtc": "2026-05-26T07:59:00Z",
                      "bucketEndUtc": "2026-05-26T07:59:30Z",
                      "requestCount": 100,
                      "p95Ms": 120,
                      "p99Ms": 220,
                      "mergeable": false
                    }
                    """);
            statement.setString(10, """
                    [
                      {
                        "method": "POST",
                        "route": "/orders",
                        "requestCount": 100,
                        "errorCount": 5,
                        "durationBuckets": [{"leMs":500,"count":100}]
                      }
                    ]
                    """);
            statement.setObject(11, OffsetDateTime.parse("2026-05-26T07:59:31Z"));
            statement.executeUpdate();
        }
    }

    private void seedInstance(UUID instanceId, String instanceName, OffsetDateTime lastSeenAt) {
        applicationInstanceRepository.saveAndFlush(new ApplicationInstanceEntity(
                instanceId,
                APPLICATION_ID,
                instanceName,
                lastSeenAt.minusHours(1),
                lastSeenAt,
                lastSeenAt.minusHours(1),
                lastSeenAt));
    }

    private static void insertAcceptedBucket(
            UUID bucketId,
            UUID instanceId,
            String keySuffix,
            OffsetDateTime bucketEnd,
            long requestCount,
            long errorCount,
            String endpointsJson) throws SQLException {
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
                       ?, ?, ?, ?, 30,
                       ?, ?, ?, '[{"leMs":500,"count":90},{"leMs":1000,"count":100}]'::jsonb,
                       ?::jsonb, ?
                     )
                     """)) {
            statement.setObject(1, bucketId);
            statement.setObject(2, PROJECT_ID);
            statement.setObject(3, APPLICATION_ID);
            statement.setObject(4, instanceId);
            statement.setString(5, "writer:orders-api:prod:%s:%s".formatted(keySuffix, bucketEnd));
            statement.setString(6, "hash-" + keySuffix);
            statement.setObject(7, bucketEnd.minusSeconds(30));
            statement.setObject(8, bucketEnd);
            statement.setObject(9, bucketEnd.plusSeconds(1));
            statement.setLong(10, requestCount);
            statement.setLong(11, errorCount);
            statement.setString(12, endpointsJson);
            statement.setObject(13, bucketEnd.plusSeconds(1));
            statement.executeUpdate();
        }
    }

    private static UUID instanceId(int index) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(6000 + index));
    }

    private static UUID bucketId(int index) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(6100 + index));
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private record SnapshotRow(
            UUID snapshotId,
            String captureReason,
            String stateCode,
            OffsetDateTime generatedAt,
            OffsetDateTime createdAt,
            String primaryRuleId,
            String primaryEndpointKey,
            BigDecimal maxConfidence,
            String readModelJson
    ) {
    }
}
