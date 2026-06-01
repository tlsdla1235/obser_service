package com.observation.portal.domain.catalog.repository;

import com.observation.portal.domain.catalog.entity.ProjectEntity;
import com.observation.portal.domain.catalog.model.ProjectKeyCandidate;
import com.observation.portal.domain.catalog.model.ProjectStatus;
import com.observation.portal.domain.catalog.model.StarterCredentialStatus;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
class ProjectRepositoryIntegrationTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000003201");
    private static final String KEY_PREFIX = "pk_repo_lookup";
    private static final String RAW_PROJECT_KEY = KEY_PREFIX + ".<repository-placeholder>";
    private static final OffsetDateTime FIXED_TIME = OffsetDateTime.parse("2026-05-19T00:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private ProjectRepository repository;

    private MigrateResult migrateResult;

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void migrateSchema() {
        migrateResult = cleanAndMigrate();
    }

    @Test
    void findsProjectCandidateByKeyPrefixAfterFlywayMigration() {
        assertThat(migrateResult.migrations)
                .extracting(migration -> migration.filepath)
                .anySatisfy(filepath -> assertThat(filepath).endsWith("V001__create_projects.sql"))
                .anySatisfy(filepath -> assertThat(filepath).endsWith("V002__create_applications_and_instances.sql"));
        String hash = BCrypt.hashpw(RAW_PROJECT_KEY, BCrypt.gensalt());
        repository.saveAndFlush(projectEntity(PROJECT_ID, "repository-project", KEY_PREFIX, hash, "active"));

        Optional<ProjectKeyCandidate> candidate = repository.findByKeyPrefix(KEY_PREFIX)
                .map(ProjectEntity::toCandidate);

        assertThat(candidate)
                .hasValue(new ProjectKeyCandidate(
                        PROJECT_ID,
                        "repository-project",
                        KEY_PREFIX,
                        hash,
                        ProjectStatus.ACTIVE,
                        StarterCredentialStatus.ACTIVE,
                        FIXED_TIME,
                        null,
                        null));
        assertThat(candidate.orElseThrow().toString()).doesNotContain(RAW_PROJECT_KEY);
    }

    @Test
    void returnsEmptyWhenPrefixIsUnknownOrBlank() {
        assertThat(repository.findByKeyPrefix("missing-prefix")).isEmpty();
        assertThat(repository.findByKeyPrefix(" ")).isEmpty();
    }

    private static ProjectEntity projectEntity(
            UUID id,
            String name,
            String keyPrefix,
            String projectKeyHash,
            String status) {
        return new ProjectEntity(id, name, keyPrefix, projectKeyHash, status, FIXED_TIME, FIXED_TIME);
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
}
