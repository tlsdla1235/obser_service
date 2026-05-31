package com.observation.portal.domain.account.repository;

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

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
class AccountProjectMembershipRepositoryIntegrationTest {

    private static final UUID ACCOUNT_A_ID = UUID.fromString("00000000-0000-0000-0000-000000006301");
    private static final UUID ACCOUNT_B_ID = UUID.fromString("00000000-0000-0000-0000-000000006302");
    private static final UUID ACTIVE_PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000006401");
    private static final UUID OTHER_PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000006402");
    private static final UUID DISABLED_PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000006403");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-31T12:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private AccountJpaRepository accountRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private AccountProjectMembershipRepository membershipRepository;

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void migrateAndSeedCatalog() {
        cleanAndMigrate();
        accountRepository.saveAndFlush(AccountEntity.active(ACCOUNT_A_ID, NOW));
        accountRepository.saveAndFlush(AccountEntity.active(ACCOUNT_B_ID, NOW));
        projectRepository.saveAndFlush(project(ACTIVE_PROJECT_ID, "alpha-project", "active"));
        projectRepository.saveAndFlush(project(OTHER_PROJECT_ID, "beta-project", "active"));
        projectRepository.saveAndFlush(project(DISABLED_PROJECT_ID, "disabled-project", "disabled"));
    }

    @Test
    void findsOnlyActiveMembershipProjectsForAccount() {
        membershipRepository.saveAndFlush(membership(
                UUID.fromString("00000000-0000-0000-0000-000000006501"),
                ACCOUNT_A_ID,
                ACTIVE_PROJECT_ID,
                "active"));
        membershipRepository.saveAndFlush(membership(
                UUID.fromString("00000000-0000-0000-0000-000000006502"),
                ACCOUNT_A_ID,
                OTHER_PROJECT_ID,
                "disabled"));
        membershipRepository.saveAndFlush(membership(
                UUID.fromString("00000000-0000-0000-0000-000000006503"),
                ACCOUNT_A_ID,
                DISABLED_PROJECT_ID,
                "active"));
        membershipRepository.saveAndFlush(membership(
                UUID.fromString("00000000-0000-0000-0000-000000006504"),
                ACCOUNT_B_ID,
                OTHER_PROJECT_ID,
                "active"));

        assertThat(membershipRepository.findActiveMembershipProjectsByAccountId(ACCOUNT_A_ID))
                .extracting(ProjectEntity::toCandidate)
                .extracting(candidate -> candidate.projectId())
                .containsExactly(ACTIVE_PROJECT_ID);
    }

    @Test
    void checksActiveMembershipByAccountAndProjectWithoutCrossAccountLeak() {
        membershipRepository.saveAndFlush(membership(
                UUID.fromString("00000000-0000-0000-0000-000000006511"),
                ACCOUNT_A_ID,
                ACTIVE_PROJECT_ID,
                "active"));
        membershipRepository.saveAndFlush(membership(
                UUID.fromString("00000000-0000-0000-0000-000000006512"),
                ACCOUNT_B_ID,
                OTHER_PROJECT_ID,
                "active"));

        assertThat(membershipRepository.existsActiveMembership(ACCOUNT_A_ID, ACTIVE_PROJECT_ID)).isTrue();
        assertThat(membershipRepository.existsActiveMembership(ACCOUNT_B_ID, ACTIVE_PROJECT_ID)).isFalse();
        assertThat(membershipRepository.existsActiveMembership(ACCOUNT_A_ID, OTHER_PROJECT_ID)).isFalse();
    }

    private static ProjectEntity project(UUID projectId, String name, String status) {
        return new ProjectEntity(
                projectId,
                name,
                "pk_" + name.replace("-", "_"),
                "hash-" + name,
                status,
                NOW,
                NOW);
    }

    private static AccountProjectMembershipEntity membership(
            UUID membershipId,
            UUID accountId,
            UUID projectId,
            String status) {
        return new AccountProjectMembershipEntity(
                membershipId,
                accountId,
                projectId,
                "member",
                status,
                NOW,
                NOW);
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
