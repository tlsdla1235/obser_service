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

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    void appliesMigrationsToCleanDatabase() throws SQLException {
        MigrateResult result = cleanAndMigrate();

        assertThat(result.migrationsExecuted).isEqualTo(2);
        assertThat(tableExists("projects")).isTrue();
        assertThat(tableExists("applications")).isTrue();
        assertThat(tableExists("application_instances")).isTrue();
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
                        "updated_at"));

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
