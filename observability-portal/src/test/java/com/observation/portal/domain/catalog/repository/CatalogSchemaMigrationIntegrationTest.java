package com.observation.portal.domain.catalog.repository;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
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
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class CatalogSchemaMigrationIntegrationTest {

    private static final Pattern KOREAN_TEXT = Pattern.compile(".*[가-힣].*");
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final String CHECK_VIOLATION = "23514";

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    void appliesMigrationsToCleanDatabase() throws SQLException {
        MigrateResult result = cleanAndMigrate();

        assertThat(result.migrationsExecuted).isEqualTo(7);
        assertThat(tableExists("projects")).isTrue();
        assertThat(tableExists("applications")).isTrue();
        assertThat(tableExists("application_instances")).isTrue();
        assertThat(tableExists("accepted_metric_buckets")).isTrue();
        assertThat(tableExists("starter_heartbeat_telemetry")).isTrue();
        assertThat(tableExists("dashboard_snapshots")).isTrue();
        assertThat(tableExists("operational_events")).isFalse();
        assertThat(tableExists("endpoint_timeseries")).isFalse();
        assertThat(tableExists("raw_snapshots")).isFalse();
        assertThat(tableExists("raw_metric_buckets")).isFalse();
    }

    @Test
    void enforcesCatalogUniqueConstraints() throws SQLException {
        cleanAndMigrate();

        UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID otherProjectId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        UUID applicationId = UUID.fromString("00000000-0000-0000-0000-000000000201");

        insertProject(projectId, "checkout", "chk", "hash-checkout");

        assertSqlState(UNIQUE_VIOLATION,
                () -> insertProject(otherProjectId, "billing", "chk", "hash-billing"));
        assertSqlState(UNIQUE_VIOLATION,
                () -> insertProject(otherProjectId, "billing", "bill", "hash-checkout"));

        insertApplication(applicationId, projectId, "portal-api", "prod");
        assertSqlState(UNIQUE_VIOLATION,
                () -> insertApplication(UUID.fromString("00000000-0000-0000-0000-000000000202"),
                        projectId,
                        "portal-api",
                        "prod"));

        insertApplicationInstance(
                UUID.fromString("00000000-0000-0000-0000-000000000301"),
                applicationId,
                "pod-a");
        assertSqlState(UNIQUE_VIOLATION,
                () -> insertApplicationInstance(
                        UUID.fromString("00000000-0000-0000-0000-000000000302"),
                        applicationId,
                        "pod-a"));
    }

    @Test
    void enforcesCatalogForeignKeys() throws SQLException {
        cleanAndMigrate();

        UUID missingProjectId = UUID.fromString("00000000-0000-0000-0000-000000000401");
        UUID missingApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000402");

        assertSqlState(FOREIGN_KEY_VIOLATION,
                () -> insertApplication(
                        UUID.fromString("00000000-0000-0000-0000-000000000501"),
                        missingProjectId,
                        "portal-api",
                        "prod"));
        assertSqlState(FOREIGN_KEY_VIOLATION,
                () -> insertApplicationInstance(
                        UUID.fromString("00000000-0000-0000-0000-000000000601"),
                        missingApplicationId,
                        "pod-a"));
    }

    @Test
    void enforcesAcceptedBucketConstraintsAndIndexes() throws SQLException {
        cleanAndMigrate();

        UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000000701");
        UUID applicationId = UUID.fromString("00000000-0000-0000-0000-000000000702");
        UUID instanceId = UUID.fromString("00000000-0000-0000-0000-000000000703");
        insertProject(projectId, "bucket-project", "bucket", "hash-bucket");
        insertApplication(applicationId, projectId, "orders-api", "prod");
        insertApplicationInstance(instanceId, applicationId, "pod-a");

        assertThat(constraintExists("accepted_metric_buckets", "uk_buckets_project_idempotency_key")).isTrue();
        assertThat(constraintExists("accepted_metric_buckets", "uk_buckets_instance_bucket_start")).isTrue();
        assertThat(constraintExists("accepted_metric_buckets", "ck_buckets_local_percentiles_object")).isTrue();
        assertThat(indexExists("idx_buckets_app_window")).isTrue();
        assertThat(indexExists("idx_buckets_app_last_end")).isTrue();
        assertThat(indexExists("idx_buckets_instance_window")).isTrue();
        assertThat(indexExists("idx_buckets_accepted_at")).isTrue();

        insertAcceptedBucket(
                UUID.fromString("00000000-0000-0000-0000-000000000704"),
                projectId,
                applicationId,
                instanceId,
                "bucket-project:orders-api:prod:pod-a:20260508T010000Z",
                "hash-1",
                "2026-05-08T01:00:00Z");

        assertSqlState(UNIQUE_VIOLATION,
                () -> insertAcceptedBucket(
                        UUID.fromString("00000000-0000-0000-0000-000000000705"),
                        projectId,
                        applicationId,
                        instanceId,
                        "bucket-project:orders-api:prod:pod-a:20260508T010000Z",
                        "hash-2",
                        "2026-05-08T01:00:30Z"));
        assertSqlState(UNIQUE_VIOLATION,
                () -> insertAcceptedBucket(
                        UUID.fromString("00000000-0000-0000-0000-000000000706"),
                        projectId,
                        applicationId,
                        instanceId,
                        "bucket-project:orders-api:prod:pod-a:20260508T010030Z",
                        "hash-3",
                        "2026-05-08T01:00:00Z"));
        assertSqlState(CHECK_VIOLATION,
                () -> insertAcceptedBucketWithDuration(
                        UUID.fromString("00000000-0000-0000-0000-000000000707"),
                        projectId,
                        applicationId,
                        instanceId,
                        "bucket-project:orders-api:prod:pod-a:20260508T010100Z",
                        "hash-4",
                        "2026-05-08T01:01:00Z",
                        60));
    }

    @Test
    void enforcesStarterHeartbeatTelemetryConstraintsAndIndexes() throws SQLException {
        cleanAndMigrate();

        UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000000801");
        insertProject(projectId, "heartbeat-project", "heartbeat", "hash-heartbeat");

        assertThat(constraintExists("starter_heartbeat_telemetry", "uk_starter_heartbeat_identity")).isTrue();
        assertThat(constraintExists(
                "starter_heartbeat_telemetry",
                "ck_starter_heartbeat_sequence_non_negative")).isTrue();
        assertThat(constraintExists(
                "starter_heartbeat_telemetry",
                "ck_starter_heartbeat_interval_positive")).isTrue();
        assertThat(indexExists("idx_starter_heartbeat_project_received")).isTrue();

        insertStarterHeartbeat(
                UUID.fromString("00000000-0000-0000-0000-000000000802"),
                projectId,
                "orders-api",
                "prod",
                "pod-a",
                1L,
                30);

        assertSqlState(UNIQUE_VIOLATION,
                () -> insertStarterHeartbeat(
                        UUID.fromString("00000000-0000-0000-0000-000000000803"),
                        projectId,
                        "orders-api",
                        "prod",
                        "pod-a",
                        2L,
                        30));
        assertSqlState(CHECK_VIOLATION,
                () -> insertStarterHeartbeat(
                        UUID.fromString("00000000-0000-0000-0000-000000000804"),
                        projectId,
                        "orders-api",
                        "prod",
                        "pod-b",
                        -1L,
                        30));
        assertSqlState(CHECK_VIOLATION,
                () -> insertStarterHeartbeat(
                        UUID.fromString("00000000-0000-0000-0000-000000000805"),
                        projectId,
                        "orders-api",
                        "prod",
                        "pod-c",
                        3L,
                0));
    }

    @Test
    void enforcesDashboardSnapshotConstraintsAndIndexes() throws SQLException {
        cleanAndMigrate();

        UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000000901");
        UUID applicationId = UUID.fromString("00000000-0000-0000-0000-000000000902");
        insertProject(projectId, "snapshot-project", "snapshot", "hash-snapshot");
        insertApplication(applicationId, projectId, "orders-api", "prod");

        assertThat(constraintExists("dashboard_snapshots", "pk_dashboard_snapshots")).isTrue();
        assertThat(constraintExists("dashboard_snapshots", "fk_dashboard_snapshots_project_id")).isTrue();
        assertThat(constraintExists("dashboard_snapshots", "fk_dashboard_snapshots_application_id")).isTrue();
        assertThat(constraintExists("dashboard_snapshots", "ck_dashboard_snapshots_read_model_object")).isTrue();
        assertThat(constraintExists("dashboard_snapshots", "ck_dashboard_snapshots_state_code")).isTrue();
        assertThat(constraintExists("dashboard_snapshots", "ck_dashboard_snapshots_capture_reason")).isFalse();
        assertThat(constraintExists(
                "dashboard_snapshots",
                "uk_dashboard_snapshots_application_current_window_end")).isTrue();
        assertThat(indexExists("idx_dashboard_snapshots_project_app_generated_desc")).isTrue();
        assertThat(indexExists("idx_dashboard_snapshots_created_at")).isTrue();
        assertThat(indexExists("idx_dashboard_snapshots_app_capture_generated")).isTrue();
        assertThat(indexExists("idx_dashboard_snapshots_app_rule_generated")).isFalse();
        assertThat(indexExists("idx_dashboard_snapshots_app_endpoint_generated")).isFalse();
        assertThat(indexExists("idx_dashboard_snapshots_app_confidence_generated")).isFalse();

        insertDashboardSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000000903"),
                projectId,
                applicationId,
                "active",
                "hourly_scheduled",
                "{\"instanceSummary\":{\"schemaVersion\":\"1.0\",\"items\":[]}}");
        insertDashboardSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000000904"),
                projectId,
                applicationId,
                "2026-05-26T09:00:00Z",
                "degraded",
                null,
                "{\"instanceSummary\":{\"schemaVersion\":\"1.0\",\"items\":[]}}");
        insertDashboardSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000000905"),
                projectId,
                applicationId,
                "2026-05-26T10:00:00Z",
                "active",
                "unknown_future_reason",
                "{\"instanceSummary\":{\"schemaVersion\":\"1.0\",\"items\":[]}}");
        assertSqlState(UNIQUE_VIOLATION,
                () -> insertDashboardSnapshot(
                        UUID.fromString("00000000-0000-0000-0000-000000000908"),
                        projectId,
                        applicationId,
                        "active",
                        "query_fallback",
                        "{\"instanceSummary\":{\"schemaVersion\":\"1.0\",\"items\":[]}}"));

        assertSqlState(CHECK_VIOLATION,
                () -> insertDashboardSnapshot(
                        UUID.fromString("00000000-0000-0000-0000-000000000906"),
                        projectId,
                        applicationId,
                        "active",
                        null,
                        "[]"));
        assertSqlState(CHECK_VIOLATION,
                () -> insertDashboardSnapshot(
                        UUID.fromString("00000000-0000-0000-0000-000000000907"),
                        projectId,
                        applicationId,
                        "healthy",
                        null,
                "{}"));
    }

    @Test
    void v007CleansDuplicateDashboardSnapshotsDeterministicallyBeforeAddingUniqueConstraint() throws SQLException {
        cleanAndMigrateTo("6");

        UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000000911");
        UUID applicationId = UUID.fromString("00000000-0000-0000-0000-000000000912");
        UUID stateChangeId = UUID.fromString("00000000-0000-0000-0000-000000000913");
        insertProject(projectId, "snapshot-duplicate-project", "snapdup", "hash-snapshot-duplicate");
        insertApplication(applicationId, projectId, "orders-api", "prod");
        insertDashboardSnapshotBeforeV007(
                UUID.fromString("00000000-0000-0000-0000-000000000914"),
                projectId,
                applicationId,
                "2026-05-26T08:08:00Z",
                "2026-05-26T08:00:00Z",
                "active",
                "hourly_scheduled",
                "2026-05-26T08:01:00Z");
        insertDashboardSnapshotBeforeV007(
                UUID.fromString("00000000-0000-0000-0000-000000000915"),
                projectId,
                applicationId,
                "2026-05-26T08:04:00Z",
                "2026-05-26T08:00:00Z",
                "degraded",
                "high_confidence_concern",
                "2026-05-26T08:02:00Z");
        insertDashboardSnapshotBeforeV007(
                UUID.fromString("00000000-0000-0000-0000-000000000916"),
                projectId,
                applicationId,
                "2026-05-26T08:06:00Z",
                "2026-05-26T08:00:00Z",
                "degraded",
                "short_strong_spike",
                "2026-05-26T08:03:00Z");
        insertDashboardSnapshotBeforeV007(
                UUID.fromString("00000000-0000-0000-0000-000000000917"),
                projectId,
                applicationId,
                "2026-05-26T08:07:00Z",
                "2026-05-26T08:00:00Z",
                "active",
                "query_fallback",
                "2026-05-26T08:04:00Z");
        insertDashboardSnapshotBeforeV007(
                UUID.fromString("00000000-0000-0000-0000-000000000918"),
                projectId,
                applicationId,
                "2026-05-26T08:09:00Z",
                "2026-05-26T08:00:00Z",
                "active",
                "unknown_future_reason",
                "2026-05-26T08:05:00Z");
        insertDashboardSnapshotBeforeV007(
                UUID.fromString("00000000-0000-0000-0000-000000000919"),
                projectId,
                applicationId,
                "2026-05-26T08:10:00Z",
                "2026-05-26T08:00:00Z",
                "active",
                null,
                "2026-05-26T08:06:00Z");
        insertDashboardSnapshotBeforeV007(
                stateChangeId,
                projectId,
                applicationId,
                "2026-05-26T08:01:00Z",
                "2026-05-26T08:00:00Z",
                "degraded",
                "state_change",
                "2026-05-26T08:07:00Z");

        MigrateResult result = migrateRemaining();

        assertThat(result.migrationsExecuted).isEqualTo(1);
        assertThat(dashboardSnapshotCount(applicationId)).isEqualTo(1);
        assertThat(dashboardSnapshotExists(stateChangeId)).isTrue();
        assertThat(constraintExists(
                "dashboard_snapshots",
                "uk_dashboard_snapshots_application_current_window_end")).isTrue();
    }

    @Test
    void keepsKoreanCommentsForEveryCatalogTableAndColumn() throws SQLException {
        cleanAndMigrate();

        Map<String, List<String>> expectedColumnsByTable = Map.of(
                "projects",
                List.of("id", "name", "key_prefix", "project_key_hash", "status", "created_at", "updated_at"),
                "applications",
                List.of(
                        "id",
                        "project_id",
                        "name",
                        "environment",
                        "status",
                        "first_seen_at",
                        "last_seen_at",
                        "created_at",
                        "updated_at"),
                "application_instances",
                List.of(
                        "id",
                        "application_id",
                        "instance_name",
                        "first_seen_at",
                        "last_seen_at",
                        "created_at",
                        "updated_at"),
                "accepted_metric_buckets",
                List.of(
                        "id",
                        "project_id",
                        "application_id",
                        "application_instance_id",
                        "schema_version",
                        "idempotency_key",
                        "payload_hash",
                        "bucket_start_utc",
                        "bucket_end_utc",
                        "duration_seconds",
                        "accepted_at",
                        "request_count",
                        "error_count",
                        "duration_buckets_json",
                        "cpu_usage_ratio",
                        "heap_used_ratio",
                        "datasource_pool_usage_ratio",
                        "local_percentiles_json",
                        "endpoints_json",
                        "created_at"),
                "starter_heartbeat_telemetry",
                List.of(
                        "id",
                        "project_id",
                        "application_name",
                        "environment",
                        "instance_name",
                        "starter_version",
                        "last_sent_at_utc",
                        "last_received_at_utc",
                        "last_sequence",
                        "interval_seconds",
                        "metadata_status",
                        "heartbeat_status",
                        "created_at",
                        "updated_at"),
                "dashboard_snapshots",
                List.of(
                        "id",
                        "project_id",
                        "application_id",
                        "generated_at",
                        "current_window_start_utc",
                        "current_window_end_utc",
                        "baseline_window_start_utc",
                        "baseline_window_end_utc",
                        "last_accepted_ingest_at",
                        "last_observed_at",
                        "state_code",
                        "capture_reason",
                        "primary_rule_id",
                        "primary_endpoint_key",
                        "max_confidence",
                        "read_model_json",
                        "created_at"));

        for (Map.Entry<String, List<String>> table : expectedColumnsByTable.entrySet()) {
            assertKoreanComment(tableComment(table.getKey()), table.getKey() + " table comment");
            for (String column : table.getValue()) {
                assertKoreanComment(columnComment(table.getKey(), column), table.getKey() + "." + column + " comment");
            }
        }
    }

    private static MigrateResult cleanAndMigrate() {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();

        flyway.clean();
        return flyway.migrate();
    }

    private static MigrateResult cleanAndMigrateTo(String target) {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(target)
                .cleanDisabled(false)
                .load();

        flyway.clean();
        return flyway.migrate();
    }

    private static MigrateResult migrateRemaining() {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load()
                .migrate();
    }

    private static boolean tableExists(String tableName) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("select to_regclass(?) is not null")) {
            statement.setString(1, "public." + tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getBoolean(1);
            }
        }
    }

    private static String tableComment(String tableName) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                     select obj_description(c.oid, 'pg_class')
                     from pg_class c
                     join pg_namespace n on n.oid = c.relnamespace
                     where n.nspname = 'public'
                       and c.relname = ?
                     """)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString(1);
            }
        }
    }

    private static String columnComment(String tableName, String columnName) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                     select d.description
                     from pg_class c
                     join pg_namespace n on n.oid = c.relnamespace
                     join pg_attribute a on a.attrelid = c.oid
                     left join pg_description d on d.objoid = c.oid and d.objsubid = a.attnum
                     where n.nspname = 'public'
                       and c.relname = ?
                       and a.attname = ?
                       and a.attnum > 0
                       and not a.attisdropped
                     """)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString(1);
            }
        }
    }

    private static boolean constraintExists(String tableName, String constraintName) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                     select exists (
                       select 1
                       from information_schema.table_constraints
                       where table_schema = 'public'
                         and table_name = ?
                         and constraint_name = ?
                     )
                     """)) {
            statement.setString(1, tableName);
            statement.setString(2, constraintName);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getBoolean(1);
            }
        }
    }

    private static boolean indexExists(String indexName) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("select to_regclass(?) is not null")) {
            statement.setString(1, "public." + indexName);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getBoolean(1);
            }
        }
    }

    private static void insertProject(UUID id, String name, String keyPrefix, String projectKeyHash) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                     insert into projects (
                       id, name, key_prefix, project_key_hash, status, created_at, updated_at
                     )
                     values (?, ?, ?, ?, 'active', ?, ?)
                     """)) {
            statement.setObject(1, id);
            statement.setString(2, name);
            statement.setString(3, keyPrefix);
            statement.setString(4, projectKeyHash);
            statement.setObject(5, OffsetDateTime.parse("2026-05-10T00:00:00Z"));
            statement.setObject(6, OffsetDateTime.parse("2026-05-10T00:00:00Z"));
            statement.executeUpdate();
        }
    }

    private static void insertApplication(UUID id, UUID projectId, String name, String environment) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                     insert into applications (
                       id, project_id, name, environment, status, created_at, updated_at
                     )
                     values (?, ?, ?, ?, 'active', ?, ?)
                     """)) {
            statement.setObject(1, id);
            statement.setObject(2, projectId);
            statement.setString(3, name);
            statement.setString(4, environment);
            statement.setObject(5, OffsetDateTime.parse("2026-05-10T00:00:00Z"));
            statement.setObject(6, OffsetDateTime.parse("2026-05-10T00:00:00Z"));
            statement.executeUpdate();
        }
    }

    private static void insertApplicationInstance(UUID id, UUID applicationId, String instanceName) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                     insert into application_instances (
                       id, application_id, instance_name, first_seen_at, last_seen_at, created_at, updated_at
                     )
                     values (?, ?, ?, ?, ?, ?, ?)
                     """)) {
            statement.setObject(1, id);
            statement.setObject(2, applicationId);
            statement.setString(3, instanceName);
            statement.setObject(4, OffsetDateTime.parse("2026-05-10T00:00:00Z"));
            statement.setObject(5, OffsetDateTime.parse("2026-05-10T00:00:00Z"));
            statement.setObject(6, OffsetDateTime.parse("2026-05-10T00:00:00Z"));
            statement.setObject(7, OffsetDateTime.parse("2026-05-10T00:00:00Z"));
            statement.executeUpdate();
        }
    }

    private static void insertAcceptedBucket(
            UUID id,
            UUID projectId,
            UUID applicationId,
            UUID applicationInstanceId,
            String idempotencyKey,
            String payloadHash,
            String bucketStartUtc) throws SQLException {
        insertAcceptedBucketWithDuration(
                id,
                projectId,
                applicationId,
                applicationInstanceId,
                idempotencyKey,
                payloadHash,
                bucketStartUtc,
                30);
    }

    private static void insertAcceptedBucketWithDuration(
            UUID id,
            UUID projectId,
            UUID applicationId,
            UUID applicationInstanceId,
            String idempotencyKey,
            String payloadHash,
            String bucketStartUtc,
            int durationSeconds) throws SQLException {
        OffsetDateTime start = OffsetDateTime.parse(bucketStartUtc);
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                     insert into accepted_metric_buckets (
                       id, project_id, application_id, application_instance_id, schema_version,
                       idempotency_key, payload_hash, bucket_start_utc, bucket_end_utc, duration_seconds,
                       accepted_at, request_count, error_count, duration_buckets_json,
                       cpu_usage_ratio, heap_used_ratio, datasource_pool_usage_ratio, endpoints_json, created_at
                     )
                     values (
                       ?, ?, ?, ?, '1.0',
                       ?, ?, ?, ?, ?,
                       ?, 3, 1, '[{"leMs":50,"count":1}]'::jsonb,
                       0.64000, 0.71000, 0.82000, '[{"method":"GET","route":"/orders/{orderId}","requestCount":2,"errorCount":0,"durationBuckets":[{"leMs":50,"count":1}]}]'::jsonb, ?
                     )
                     """)) {
            statement.setObject(1, id);
            statement.setObject(2, projectId);
            statement.setObject(3, applicationId);
            statement.setObject(4, applicationInstanceId);
            statement.setString(5, idempotencyKey);
            statement.setString(6, payloadHash);
            statement.setObject(7, start);
            statement.setObject(8, start.plusSeconds(30));
            statement.setInt(9, durationSeconds);
            statement.setObject(10, OffsetDateTime.parse("2026-05-08T01:00:31Z"));
            statement.setObject(11, OffsetDateTime.parse("2026-05-08T01:00:31Z"));
            statement.executeUpdate();
        }
    }

    private static void insertStarterHeartbeat(
            UUID id,
            UUID projectId,
            String applicationName,
            String environment,
            String instanceName,
            long sequence,
            int intervalSeconds) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                     insert into starter_heartbeat_telemetry (
                       id, project_id, application_name, environment, instance_name,
                       starter_version, last_sent_at_utc, last_received_at_utc, last_sequence,
                       interval_seconds, metadata_status, heartbeat_status, created_at, updated_at
                     )
                     values (?, ?, ?, ?, ?, '0.1.0', ?, ?, ?, ?, 'valid', 'received', ?, ?)
                     """)) {
            OffsetDateTime now = OffsetDateTime.parse("2026-05-24T08:31:00Z");
            statement.setObject(1, id);
            statement.setObject(2, projectId);
            statement.setString(3, applicationName);
            statement.setString(4, environment);
            statement.setString(5, instanceName);
            statement.setObject(6, OffsetDateTime.parse("2026-05-24T08:30:00Z"));
            statement.setObject(7, now);
            statement.setLong(8, sequence);
            statement.setInt(9, intervalSeconds);
            statement.setObject(10, now);
            statement.setObject(11, now);
            statement.executeUpdate();
        }
    }

    private static void insertDashboardSnapshot(
            UUID id,
            UUID projectId,
            UUID applicationId,
            String stateCode,
            String captureReason,
            String readModelJson) throws SQLException {
        insertDashboardSnapshot(
                id,
                projectId,
                applicationId,
                "2026-05-26T08:00:00Z",
                stateCode,
                captureReason,
                readModelJson);
    }

    private static void insertDashboardSnapshot(
            UUID id,
            UUID projectId,
            UUID applicationId,
            String generatedAtText,
            String stateCode,
            String captureReason,
            String readModelJson) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                     insert into dashboard_snapshots (
                       id, project_id, application_id, generated_at,
                       current_window_start_utc, current_window_end_utc,
                       baseline_window_start_utc, baseline_window_end_utc,
                       last_accepted_ingest_at, last_observed_at,
                       state_code, capture_reason, read_model_json, created_at
                     )
                     values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                     """)) {
            OffsetDateTime generatedAt = OffsetDateTime.parse(generatedAtText);
            statement.setObject(1, id);
            statement.setObject(2, projectId);
            statement.setObject(3, applicationId);
            statement.setObject(4, generatedAt);
            statement.setObject(5, generatedAt.minusMinutes(15));
            statement.setObject(6, generatedAt);
            statement.setObject(7, generatedAt.minusMinutes(30));
            statement.setObject(8, generatedAt.minusMinutes(15));
            statement.setObject(9, generatedAt.minusSeconds(30));
            statement.setObject(10, generatedAt.minusSeconds(30));
            statement.setString(11, stateCode);
            statement.setString(12, captureReason);
            statement.setString(13, readModelJson);
            statement.setObject(14, generatedAt);
            statement.executeUpdate();
        }
    }

    private static void insertDashboardSnapshotBeforeV007(
            UUID id,
            UUID projectId,
            UUID applicationId,
            String generatedAtText,
            String currentWindowEndText,
            String stateCode,
            String captureReason,
            String createdAtText) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                     insert into dashboard_snapshots (
                       id, project_id, application_id, generated_at,
                       current_window_start_utc, current_window_end_utc,
                       baseline_window_start_utc, baseline_window_end_utc,
                       last_accepted_ingest_at, last_observed_at,
                       state_code, capture_reason, read_model_json, created_at
                     )
                     values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                     """)) {
            OffsetDateTime generatedAt = OffsetDateTime.parse(generatedAtText);
            OffsetDateTime currentWindowEnd = OffsetDateTime.parse(currentWindowEndText);
            statement.setObject(1, id);
            statement.setObject(2, projectId);
            statement.setObject(3, applicationId);
            statement.setObject(4, generatedAt);
            statement.setObject(5, currentWindowEnd.minusMinutes(15));
            statement.setObject(6, currentWindowEnd);
            statement.setObject(7, currentWindowEnd.minusMinutes(30));
            statement.setObject(8, currentWindowEnd.minusMinutes(15));
            statement.setObject(9, generatedAt.minusSeconds(30));
            statement.setObject(10, generatedAt.minusSeconds(30));
            statement.setString(11, stateCode);
            statement.setString(12, captureReason);
            statement.setString(13, "{\"instanceSummary\":{\"schemaVersion\":\"1.0\",\"items\":[]}}");
            statement.setObject(14, OffsetDateTime.parse(createdAtText));
            statement.executeUpdate();
        }
    }

    private static int dashboardSnapshotCount(UUID applicationId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "select count(*) from dashboard_snapshots where application_id = ?")) {
            statement.setObject(1, applicationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getInt(1);
            }
        }
    }

    private static boolean dashboardSnapshotExists(UUID snapshotId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "select exists (select 1 from dashboard_snapshots where id = ?)")) {
            statement.setObject(1, snapshotId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getBoolean(1);
            }
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void assertSqlState(String expectedSqlState, ThrowingCallable operation) {
        assertThatThrownBy(operation)
                .isInstanceOf(SQLException.class)
                .satisfies(throwable -> assertThat(((SQLException) throwable).getSQLState()).isEqualTo(expectedSqlState));
    }

    private static void assertKoreanComment(String comment, String description) {
        assertThat(comment)
                .as(description)
                .isNotBlank()
                .matches(KOREAN_TEXT);
    }
}
