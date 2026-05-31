package com.observation.portal.domain.account.repository;

import com.observation.portal.domain.bucket.model.AcceptedMetricBucketReceipt;
import com.observation.portal.domain.bucket.model.AcceptedMetricBucketWriteCommand;
import com.observation.portal.domain.bucket.repository.MetricBucketRepository;
import com.observation.portal.domain.catalog.entity.ApplicationEntity;
import com.observation.portal.domain.catalog.entity.ApplicationInstanceEntity;
import com.observation.portal.domain.catalog.entity.ProjectEntity;
import com.observation.portal.domain.catalog.repository.ApplicationInstanceRepository;
import com.observation.portal.domain.catalog.repository.ApplicationRepository;
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
import java.util.List;
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
    private static final OffsetDateTime DEMO_BUCKET_START = OffsetDateTime.parse("2026-05-31T11:59:30Z");
    private static final OffsetDateTime DEMO_BUCKET_END = OffsetDateTime.parse("2026-05-31T12:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private AccountJpaRepository accountRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ApplicationInstanceRepository applicationInstanceRepository;

    @Autowired
    private MetricBucketRepository metricBucketRepository;

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
    void demoGreenPathFixtureLinksAccountProjectMembershipCatalogInstanceAndAcceptedBucket() {
        membershipRepository.saveAndFlush(membership(
                UUID.fromString("00000000-0000-0000-0000-000000006521"),
                ACCOUNT_A_ID,
                ACTIVE_PROJECT_ID,
                "active"));

        AcceptedMetricBucketReceipt receipt = metricBucketRepository.insert(demoAcceptedBucket());

        assertThat(receipt.acceptedAt()).isEqualTo(NOW);
        assertThat(membershipRepository.findActiveMembershipProjectsByAccountId(ACCOUNT_A_ID))
                .extracting(project -> project.toCandidate().projectId())
                .containsExactly(ACTIVE_PROJECT_ID);

        ApplicationEntity application = applicationRepository
                .findByProjectIdAndNameAndEnvironment(ACTIVE_PROJECT_ID, "orders-api", "prod")
                .orElseThrow();
        ApplicationInstanceEntity instance = applicationInstanceRepository
                .findByApplicationIdAndInstanceName(application.id(), "pod-a")
                .orElseThrow();

        assertThat(application.projectId()).isEqualTo(ACTIVE_PROJECT_ID);
        assertThat(instance.applicationId()).isEqualTo(application.id());
        assertThat(metricBucketRepository.findLatestBucketEndUtcByApplicationId(application.id()))
                .contains(DEMO_BUCKET_END);
        assertThat(metricBucketRepository.findLatestBucketEndUtcByApplicationInstanceIdAtOrBefore(
                instance.id(),
                NOW.toInstant()))
                .contains(DEMO_BUCKET_END);
    }

    @Test
    void greenPathMembershipGuardExcludesDisabledMembershipAndDisabledProject() {
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
                "prefix-" + name.replace("-", "_"),
                "hash-only-" + name,
                status,
                NOW,
                NOW);
    }

    /**
     * Story 6.8 demo fixture가 raw project key 없이 accepted bucket으로 catalog row를 생성하는 저장 command다.
     */
    private static AcceptedMetricBucketWriteCommand demoAcceptedBucket() {
        return new AcceptedMetricBucketWriteCommand(
                ACTIVE_PROJECT_ID,
                "alpha-project",
                "orders-api",
                "prod",
                "pod-a",
                "1.0",
                "demo-green-path-2026-05-31T120000Z",
                "sha256-demo-green-path-fixture-hash",
                DEMO_BUCKET_START,
                DEMO_BUCKET_END,
                30,
                NOW,
                40L,
                0L,
                List.of(
                        new AcceptedMetricBucketWriteCommand.DurationBucket(100L, 20L),
                        new AcceptedMetricBucketWriteCommand.DurationBucket(500L, 40L)),
                0.20d,
                0.35d,
                0.25d,
                new AcceptedMetricBucketWriteCommand.LocalPercentiles(
                        "instance_bucket",
                        "starter_local",
                        DEMO_BUCKET_START.toString(),
                        DEMO_BUCKET_END.toString(),
                        40L,
                        120L,
                        240L,
                        false),
                List.of(new AcceptedMetricBucketWriteCommand.EndpointBucket(
                        "GET",
                        "/orders",
                        40L,
                        0L,
                        List.of(
                                new AcceptedMetricBucketWriteCommand.DurationBucket(100L, 20L),
                                new AcceptedMetricBucketWriteCommand.DurationBucket(500L, 40L)))));
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
