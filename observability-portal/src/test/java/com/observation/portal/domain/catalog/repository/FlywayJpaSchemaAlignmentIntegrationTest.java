package com.observation.portal.domain.catalog.repository;

import com.observation.portal.PortalApplication;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway가 만든 PostgreSQL schema와 현재 JPA entity mapping이 함께 부팅 가능한지 검증한다.
 *
 * <p>Hibernate DDL 자동 생성/수정 대신 validation만 켜서, 새 entity가 migration 없이 추가되면 context
 * bootstrap 단계에서 실패하도록 고정한다.</p>
 */
@Testcontainers
class FlywayJpaSchemaAlignmentIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    void validatesJpaMappingsAgainstFlywayMigratedSchema() {
        cleanAndMigrate();

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(PortalApplication.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "spring.datasource.username=" + POSTGRES.getUsername(),
                        "spring.datasource.password=" + POSTGRES.getPassword(),
                        "spring.flyway.enabled=false",
                        "spring.jpa.hibernate.ddl-auto=validate",
                        "portal.auth.service-token.signing-key=flyway-jpa-schema-alignment-service-token-key",
                        "portal.auth.oauth-state.signing-key=flyway-jpa-schema-alignment-oauth-state-key")
                .run()) {
            EntityManagerFactory entityManagerFactory = context.getBean(EntityManagerFactory.class);
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);

            assertThat(entityManagerFactory.isOpen()).isTrue();
            assertThat(currentApplicationTables(jdbcTemplate))
                    .containsExactlyInAnyOrder(
                            "accepted_metric_buckets",
                            "account_project_memberships",
                            "accounts",
                            "application_instances",
                            "applications",
                            "dashboard_snapshots",
                            "external_identities",
                            "flyway_schema_history",
                            "oauth_state_nonces",
                            "projects",
                            "refresh_token_families",
                            "refresh_tokens",
                            "starter_heartbeat_telemetry");
        }
    }

    private void cleanAndMigrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load()
                .clean();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private List<String> currentApplicationTables(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.queryForList(
                """
                select tablename
                from pg_tables
                where schemaname = 'public'
                order by tablename
                """,
                String.class);
    }
}
