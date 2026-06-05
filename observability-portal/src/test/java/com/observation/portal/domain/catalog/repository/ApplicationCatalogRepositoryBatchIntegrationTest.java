package com.observation.portal.domain.catalog.repository;

import com.observation.portal.domain.bucket.model.AcceptedMetricBucketWriteCommand;
import com.observation.portal.domain.catalog.entity.ProjectEntity;
import com.observation.portal.domain.catalog.model.ApplicationCatalogEntry;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
class ApplicationCatalogRepositoryBatchIntegrationTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000003201");
    private static final OffsetDateTime FIXED_TIME = OffsetDateTime.parse("2026-05-08T01:00:31Z");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private ApplicationCatalogRepository applicationCatalogRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ApplicationInstanceRepository applicationInstanceRepository;

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
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
        projectRepository.saveAndFlush(new ProjectEntity(
                PROJECT_ID,
                "checkout",
                "pk_catalog_batch",
                "$2a$10$repositoryhashrepositoryhashrepositoryhashrepositoryhash12",
                "active",
                FIXED_TIME,
                FIXED_TIME));
    }

    @Test
    void batchGetOrCreateGroupsApplicationAndInstancesAndUsesMaxSeenAtWithoutDowngrade() {
        OffsetDateTime early = OffsetDateTime.parse("2026-05-08T01:00:31Z");
        OffsetDateTime late = OffsetDateTime.parse("2026-05-08T01:02:31Z");
        List<ApplicationCatalogEntry> entries = applicationCatalogRepository.getOrCreateBatch(List.of(
                command("pod-a", "key-a", early),
                command("pod-b", "key-b", late),
                command("pod-c", "key-c", early.plusSeconds(30))));

        assertThat(entries).hasSize(3);
        assertThat(entries.stream().map(ApplicationCatalogEntry::applicationId).distinct()).hasSize(1);
        assertThat(entries.stream().map(ApplicationCatalogEntry::applicationInstanceId).distinct()).hasSize(3);
        var application = applicationRepository.findByProjectIdAndNameAndEnvironment(PROJECT_ID, "orders-api", "prod")
                .orElseThrow();
        assertThat(application.firstSeenAt()).isEqualTo(early);
        assertThat(application.lastSeenAt()).isEqualTo(late);

        applicationCatalogRepository.getOrCreateBatch(List.of(command("pod-a", "older-key", early.minusMinutes(5))));

        var afterOlderBatch = applicationRepository.findByProjectIdAndNameAndEnvironment(PROJECT_ID, "orders-api", "prod")
                .orElseThrow();
        var podA = applicationInstanceRepository.findByApplicationIdAndInstanceName(application.id(), "pod-a")
                .orElseThrow();
        assertThat(afterOlderBatch.lastSeenAt()).isEqualTo(late);
        assertThat(podA.lastSeenAt()).isEqualTo(early);
        assertThat(applicationRepository.count()).isEqualTo(1);
        assertThat(applicationInstanceRepository.count()).isEqualTo(3);
    }

    @Test
    void concurrentBatchGetOrCreateConvergesWithoutDuplicateCatalogRows() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<List<ApplicationCatalogEntry>> task = () -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            return applicationCatalogRepository.getOrCreateBatch(List.of(command(
                    "pod-race",
                    "key-race",
                    FIXED_TIME.plusMinutes(1))));
        };

        var first = executor.submit(task);
        var second = executor.submit(task);
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<ApplicationCatalogEntry> firstEntries = first.get(5, TimeUnit.SECONDS);
        List<ApplicationCatalogEntry> secondEntries = second.get(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(firstEntries).hasSize(1);
        assertThat(secondEntries).hasSize(1);
        assertThat(secondEntries.get(0).applicationId()).isEqualTo(firstEntries.get(0).applicationId());
        assertThat(secondEntries.get(0).applicationInstanceId()).isEqualTo(firstEntries.get(0).applicationInstanceId());
        assertThat(applicationRepository.count()).isEqualTo(1);
        assertThat(applicationInstanceRepository.count()).isEqualTo(1);
    }

    private static AcceptedMetricBucketWriteCommand command(String instanceName, String keySuffix, OffsetDateTime acceptedAt) {
        OffsetDateTime start = OffsetDateTime.parse("2026-05-08T01:00:00Z");
        return new AcceptedMetricBucketWriteCommand(
                PROJECT_ID,
                "checkout",
                "orders-api",
                "prod",
                instanceName,
                "1.0",
                "project-123:orders-api:prod:%s:%s".formatted(instanceName, keySuffix),
                "hash-" + keySuffix,
                start,
                start.plusSeconds(30),
                30,
                acceptedAt,
                3L,
                1L,
                List.of(new AcceptedMetricBucketWriteCommand.DurationBucket(100L, 3L)),
                null,
                null,
                null,
                null,
                List.of());
    }
}
