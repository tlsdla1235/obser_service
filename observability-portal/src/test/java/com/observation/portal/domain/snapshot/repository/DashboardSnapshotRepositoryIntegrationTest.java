package com.observation.portal.domain.snapshot.repository;

import com.observation.portal.domain.catalog.entity.ApplicationEntity;
import com.observation.portal.domain.catalog.entity.ProjectEntity;
import com.observation.portal.domain.catalog.repository.ApplicationRepository;
import com.observation.portal.domain.catalog.repository.ProjectRepository;
import com.observation.portal.domain.instance.model.InstanceSnapshotTrendReadModel;
import com.observation.portal.domain.instance.service.InstanceSnapshotTrendParser;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailRow;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotSourceRow;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotTrendRow;
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
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
class DashboardSnapshotRepositoryIntegrationTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000005701");
    private static final UUID OTHER_PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000005702");
    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000005711");
    private static final UUID OTHER_APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000005712");
    private static final UUID TARGET_INSTANCE_ID = UUID.fromString("00000000-0000-0000-0000-000000005721");
    private static final OffsetDateTime FIXED_TIME = OffsetDateTime.parse("2026-05-26T08:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private DashboardSnapshotRepository dashboardSnapshotRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private InstanceSnapshotTrendParser parser;

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
                "snapshot-project",
                "pk_snapshot",
                "$2a$10$snapshothashsnapshothashsnapshothashsnapshothash12",
                "active",
                FIXED_TIME,
                FIXED_TIME));
        projectRepository.saveAndFlush(new ProjectEntity(
                OTHER_PROJECT_ID,
                "snapshot-other-project",
                "pk_snapshot_other",
                "$2a$10$snapshototherhashsnapshototherhashsnapshotother12",
                "active",
                FIXED_TIME,
                FIXED_TIME));
        applicationRepository.saveAndFlush(new ApplicationEntity(
                APPLICATION_ID,
                PROJECT_ID,
                "orders-api",
                "prod",
                "active",
                FIXED_TIME,
                FIXED_TIME,
                FIXED_TIME,
                FIXED_TIME));
        applicationRepository.saveAndFlush(new ApplicationEntity(
                OTHER_APPLICATION_ID,
                OTHER_PROJECT_ID,
                "orders-api",
                "prod",
                "active",
                FIXED_TIME,
                FIXED_TIME,
                FIXED_TIME,
                FIXED_TIME));
    }

    @Test
    void findsApplicationSnapshotsNewestFirstWithinHorizonAndLimit() throws SQLException {
        UUID oldestSnapshotId = UUID.fromString("00000000-0000-0000-0000-000000005721");
        UUID middleSnapshotId = UUID.fromString("00000000-0000-0000-0000-000000005722");
        UUID newestSnapshotId = UUID.fromString("00000000-0000-0000-0000-000000005723");
        insertSnapshot(
                oldestSnapshotId,
                PROJECT_ID,
                APPLICATION_ID,
                "2026-05-26T05:00:00Z",
                "2026-05-26T05:00:00Z",
                "active",
                "hourly_scheduled",
                "{\"instanceSummary\":{\"schemaVersion\":\"1.0\",\"items\":[]}}");
        insertSnapshot(
                middleSnapshotId,
                PROJECT_ID,
                APPLICATION_ID,
                "2026-05-26T08:40:00Z",
                "2026-05-26T06:30:00Z",
                "degraded",
                null,
                "{\"instanceSummary\":{\"schemaVersion\":\"1.0\",\"items\":[]}}");
        insertSnapshot(
                newestSnapshotId,
                PROJECT_ID,
                APPLICATION_ID,
                "2026-05-26T07:05:00Z",
                "2026-05-26T07:00:00Z",
                "active",
                "state_changed",
                "{\"instanceSummary\":{\"schemaVersion\":\"1.0\",\"items\":[]},\"instances\":[]}");
        insertSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000005726"),
                PROJECT_ID,
                APPLICATION_ID,
                "2026-05-26T06:30:00Z",
                "2026-05-26T08:00:00Z",
                "active",
                "future_fixture",
                "{\"instanceSummary\":{\"schemaVersion\":\"1.0\",\"items\":[]}}");
        insertSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000005724"),
                OTHER_PROJECT_ID,
                OTHER_APPLICATION_ID,
                "2026-05-26T07:30:00Z",
                "active",
                "other_scope",
                "{\"instanceSummary\":{\"schemaVersion\":\"1.0\",\"items\":[]}}");

        List<DashboardSnapshotTrendRow> rows = dashboardSnapshotRepository.findTrendRowsNewestFirst(
                PROJECT_ID,
                APPLICATION_ID,
                OffsetDateTime.parse("2026-05-26T05:30:00Z"),
                OffsetDateTime.parse("2026-05-26T07:30:00Z"),
                2);

        assertThat(rows)
                .extracting(DashboardSnapshotTrendRow::snapshotId)
                .containsExactly(newestSnapshotId, middleSnapshotId);
        assertThat(rows.get(0)).satisfies(row -> {
            assertThat(row.generatedAt()).isEqualTo(OffsetDateTime.parse("2026-05-26T07:05:00Z"));
            assertThat(row.currentWindowEndUtc()).isEqualTo(OffsetDateTime.parse("2026-05-26T07:00:00Z"));
            assertThat(row.stateCode()).isEqualTo("active");
            assertThat(row.captureReason()).isEqualTo("state_changed");
            assertThat(row.readModelJson()).contains("\"instanceSummary\"");
        });
        assertThat(rows.get(1).generatedAt()).isEqualTo(OffsetDateTime.parse("2026-05-26T08:40:00Z"));
        assertThat(rows.get(1).currentWindowEndUtc()).isEqualTo(OffsetDateTime.parse("2026-05-26T06:30:00Z"));
        assertThat(rows.get(1).captureReason()).isNull();
    }

    @Test
    void seedsJsonbInstanceSummaryAndProjectsTargetInstanceTrendPoint() throws SQLException {
        UUID snapshotId = UUID.fromString("00000000-0000-0000-0000-000000005725");
        insertSnapshot(
                snapshotId,
                PROJECT_ID,
                APPLICATION_ID,
                "2026-05-26T08:00:00Z",
                "active",
                "hourly_scheduled",
                """
                {
                  "instanceSummary": {
                    "schemaVersion": "1.0",
                    "items": [
                      {
                        "instanceId": "%s",
                        "instanceName": "pod-a",
                        "observationStatus": "observed",
                        "metricData": {
                          "statusSource": "accepted_bucket",
                          "lastAcceptedBucketAt": "2026-05-26T07:59:30Z",
                          "freshnessLabel": "current"
                        },
                        "starterConnection": {
                          "statusSource": "starter_heartbeat",
                          "lastHeartbeatAt": "2026-05-26T07:59:45Z",
                          "lastHeartbeatStatus": "received",
                          "connectionMeaning": "starter_connected",
                          "stateImpact": "none"
                        },
                        "applicationTriageContribution": {
                          "status": "available",
                          "contributed": false,
                          "relatedRuleIds": [],
                          "reason": "no_action_needed"
                        },
                        "endpointEvidenceRefs": []
                      }
                    ]
                  }
                }
                """.formatted(TARGET_INSTANCE_ID));

        List<DashboardSnapshotTrendRow> rows = dashboardSnapshotRepository.findTrendRowsNewestFirst(
                PROJECT_ID,
                APPLICATION_ID,
                OffsetDateTime.parse("2026-05-25T08:00:00Z"),
                OffsetDateTime.parse("2026-05-26T08:10:00Z"),
                1);

        InstanceSnapshotTrendReadModel.Point point = parser.projectPoint(rows.get(0), TARGET_INSTANCE_ID)
                .orElseThrow();

        assertThat(point.snapshotId()).isEqualTo(snapshotId);
        assertThat(point.capturedAt()).isEqualTo(OffsetDateTime.parse("2026-05-26T08:00:00Z"));
        assertThat(point.currentWindowEndUtc()).isEqualTo(OffsetDateTime.parse("2026-05-26T08:00:00Z"));
        assertThat(point.storedApplicationStateCode()).isEqualTo("active");
        assertThat(point.captureReason()).isEqualTo("hourly_scheduled");
        assertThat(point.metricData().statusSource()).isEqualTo("accepted_bucket");
        assertThat(point.starterConnection().stateImpact()).isEqualTo("none");
    }

    @Test
    void findsDetailMarkerPreviousStateAndPreviousActiveRowsFromStoredSnapshotsOnly() throws SQLException {
        UUID activeSnapshotId = UUID.fromString("00000000-0000-0000-0000-000000005731");
        UUID degradedSnapshotId = UUID.fromString("00000000-0000-0000-0000-000000005732");
        UUID currentSnapshotId = UUID.fromString("00000000-0000-0000-0000-000000005733");
        insertSnapshot(
                activeSnapshotId,
                PROJECT_ID,
                APPLICATION_ID,
                "2026-05-26T08:40:00Z",
                "2026-05-26T05:00:00Z",
                "active",
                "hourly_scheduled",
                "{\"triageCards\":[]}");
        insertSnapshot(
                degradedSnapshotId,
                PROJECT_ID,
                APPLICATION_ID,
                "2026-05-26T06:02:00Z",
                "2026-05-26T06:00:00Z",
                "degraded",
                "state_change",
                "{\"triageCards\":[]}");
        insertSnapshot(
                currentSnapshotId,
                PROJECT_ID,
                APPLICATION_ID,
                "2026-05-26T07:04:00Z",
                "2026-05-26T07:00:00Z",
                "unknown",
                "query_fallback",
                "{\"triageCards\":[]}");
        insertSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000005735"),
                PROJECT_ID,
                APPLICATION_ID,
                "2026-05-26T06:30:00Z",
                "2026-05-26T08:00:00Z",
                "active",
                "outside_slot_horizon",
                "{\"triageCards\":[]}");
        insertSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000005734"),
                OTHER_PROJECT_ID,
                OTHER_APPLICATION_ID,
                "2026-05-26T06:30:00Z",
                "active",
                "hourly_scheduled",
                "{\"triageCards\":[]}");

        DashboardSnapshotDetailRow detail = dashboardSnapshotRepository.findDetailRow(
                        PROJECT_ID,
                        APPLICATION_ID,
                        currentSnapshotId)
                .orElseThrow();
        List<DashboardSnapshotDetailRow> markers = dashboardSnapshotRepository.findMarkerRows(
                PROJECT_ID,
                APPLICATION_ID,
                OffsetDateTime.parse("2026-05-26T04:30:00Z"),
                OffsetDateTime.parse("2026-05-26T07:30:00Z"),
                10);
        DashboardSnapshotSourceRow previous = dashboardSnapshotRepository.findPreviousSnapshot(
                        APPLICATION_ID,
                        OffsetDateTime.parse("2026-05-26T07:00:00Z"))
                .orElseThrow();
        DashboardSnapshotSourceRow previousActive = dashboardSnapshotRepository.findPreviousActiveSnapshot(
                        APPLICATION_ID,
                        OffsetDateTime.parse("2026-05-26T07:00:00Z"))
                .orElseThrow();

        assertThat(detail.snapshotId()).isEqualTo(currentSnapshotId);
        assertThat(detail.captureReason()).isEqualTo("query_fallback");
        assertThat(markers)
                .extracting(DashboardSnapshotDetailRow::snapshotId)
                .containsExactly(activeSnapshotId, degradedSnapshotId, currentSnapshotId);
        assertThat(previous.snapshotId()).isEqualTo(degradedSnapshotId);
        assertThat(previous.stateCode()).isEqualTo("degraded");
        assertThat(previousActive.snapshotId()).isEqualTo(activeSnapshotId);
        assertThat(previousActive.generatedAt()).isEqualTo(OffsetDateTime.parse("2026-05-26T08:40:00Z"));
        assertThat(dashboardSnapshotRepository.findDetailRow(OTHER_PROJECT_ID, APPLICATION_ID, currentSnapshotId))
                .isEmpty();
    }

    @Test
    void findsOperationalHistorySourceRowsNewestFirstWithinHorizonAndKeepsHelperColumns() throws SQLException {
        UUID olderSnapshotId = UUID.fromString("00000000-0000-0000-0000-000000005741");
        UUID middleSnapshotId = UUID.fromString("00000000-0000-0000-0000-000000005742");
        UUID newestSnapshotId = UUID.fromString("00000000-0000-0000-0000-000000005743");
        insertSnapshot(
                olderSnapshotId,
                PROJECT_ID,
                APPLICATION_ID,
                "2026-05-26T08:40:00Z",
                "2026-05-26T05:00:00Z",
                "active",
                "hourly_scheduled",
                "{\"triageCards\":[]}",
                null,
                null,
                null);
        insertSnapshot(
                middleSnapshotId,
                PROJECT_ID,
                APPLICATION_ID,
                "2026-05-26T06:03:00Z",
                "2026-05-26T06:00:00Z",
                "degraded",
                "state_change",
                "{\"snapshotEndpointEvidence\":{\"items\":[]},\"triageCards\":[]}",
                "endpoint_latency_spike",
                "POST /orders",
                new BigDecimal("0.840"));
        insertSnapshot(
                newestSnapshotId,
                PROJECT_ID,
                APPLICATION_ID,
                "2026-05-26T07:04:00Z",
                "2026-05-26T07:00:00Z",
                "degraded",
                "high_confidence_concern",
                "{\"snapshotEndpointEvidence\":{\"items\":[]},\"triageCards\":[]}",
                "endpoint_error_spike",
                "GET /checkout",
                new BigDecimal("0.910"));
        insertSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000005744"),
                PROJECT_ID,
                APPLICATION_ID,
                "2026-05-26T06:30:00Z",
                "2026-05-26T08:00:00Z",
                "active",
                "future_fixture",
                "{\"triageCards\":[]}");
        insertSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000005745"),
                OTHER_PROJECT_ID,
                OTHER_APPLICATION_ID,
                "2026-05-26T07:30:00Z",
                "degraded",
                "other_scope",
                "{\"triageCards\":[]}");

        List<DashboardSnapshotDetailRow> rows = dashboardSnapshotRepository.findOperationalHistoryRows(
                PROJECT_ID,
                APPLICATION_ID,
                OffsetDateTime.parse("2026-05-26T04:30:00Z"),
                OffsetDateTime.parse("2026-05-26T07:30:00Z"),
                3);

        assertThat(rows)
                .extracting(DashboardSnapshotDetailRow::snapshotId)
                .containsExactly(newestSnapshotId, middleSnapshotId, olderSnapshotId);
        assertThat(rows.get(0).captureReason()).isEqualTo("high_confidence_concern");
        assertThat(rows.get(0).primaryRuleId()).isEqualTo("endpoint_error_spike");
        assertThat(rows.get(0).primaryEndpointKey()).isEqualTo("GET /checkout");
        assertThat(rows.get(0).maxConfidence()).isEqualByComparingTo(new BigDecimal("0.910"));
        assertThat(rows.get(0).readModelJson()).contains("snapshotEndpointEvidence");
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

    private static void insertSnapshot(
            UUID id,
            UUID projectId,
            UUID applicationId,
            String generatedAt,
            String stateCode,
            String captureReason,
            String readModelJson) throws SQLException {
        insertSnapshot(
                id,
                projectId,
                applicationId,
                generatedAt,
                generatedAt,
                stateCode,
                captureReason,
                readModelJson,
                null,
                null,
                null);
    }

    private static void insertSnapshot(
            UUID id,
            UUID projectId,
            UUID applicationId,
            String generatedAt,
            String currentWindowEndUtc,
            String stateCode,
            String captureReason,
            String readModelJson) throws SQLException {
        insertSnapshot(
                id,
                projectId,
                applicationId,
                generatedAt,
                currentWindowEndUtc,
                stateCode,
                captureReason,
                readModelJson,
                null,
                null,
                null);
    }

    private static void insertSnapshot(
            UUID id,
            UUID projectId,
            UUID applicationId,
            String generatedAt,
            String currentWindowEndUtc,
            String stateCode,
            String captureReason,
            String readModelJson,
            String primaryRuleId,
            String primaryEndpointKey,
            BigDecimal maxConfidence) throws SQLException {
        OffsetDateTime generated = OffsetDateTime.parse(generatedAt);
        OffsetDateTime currentWindowEnd = OffsetDateTime.parse(currentWindowEndUtc);
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
             PreparedStatement statement = connection.prepareStatement(
                     """
                     insert into dashboard_snapshots (
                       id, project_id, application_id, generated_at,
                       current_window_start_utc, current_window_end_utc,
                       baseline_window_start_utc, baseline_window_end_utc,
                       last_accepted_ingest_at, last_observed_at,
                       state_code, capture_reason, primary_rule_id, primary_endpoint_key, max_confidence,
                       read_model_json, created_at
                     )
                     values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                     """)) {
            statement.setObject(1, id);
            statement.setObject(2, projectId);
            statement.setObject(3, applicationId);
            statement.setObject(4, generated);
            statement.setObject(5, currentWindowEnd.minusMinutes(30));
            statement.setObject(6, currentWindowEnd);
            statement.setObject(7, currentWindowEnd.minusMinutes(60));
            statement.setObject(8, currentWindowEnd.minusMinutes(30));
            statement.setObject(9, currentWindowEnd.minusSeconds(30));
            statement.setObject(10, currentWindowEnd.minusSeconds(30));
            statement.setString(11, stateCode);
            statement.setString(12, captureReason);
            statement.setString(13, primaryRuleId);
            statement.setString(14, primaryEndpointKey);
            statement.setBigDecimal(15, maxConfidence);
            statement.setString(16, readModelJson);
            statement.setObject(17, generated);
            statement.executeUpdate();
        }
    }
}
