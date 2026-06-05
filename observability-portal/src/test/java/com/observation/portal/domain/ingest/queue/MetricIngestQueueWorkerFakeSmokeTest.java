package com.observation.portal.domain.ingest.queue;

import com.observation.portal.domain.catalog.entity.ProjectEntity;
import com.observation.portal.domain.catalog.repository.ProjectRepository;
import com.observation.portal.domain.ingest.service.IngestAcceptanceResult;
import com.observation.portal.domain.ingest.service.IngestAcceptanceService;
import com.observation.portal.domain.ingest.service.PortalIngestValidationFixture;
import org.flywaydb.core.Flyway;
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

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "portal.ingest.buffer.mode=fake"
})
class MetricIngestQueueWorkerFakeSmokeTest {

    private static final OffsetDateTime FIXED_TIME = OffsetDateTime.parse("2026-05-08T01:00:31Z");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private IngestAcceptanceService acceptanceService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private com.observation.portal.domain.bucket.repository.MetricBucketRepository metricBucketRepository;

    @Autowired
    private IngestBufferProperties properties;

    @Autowired
    private FakeMetricIngestQueuePublisher fakeQueue;

    @Autowired
    private MetricIngestQueueProcessor processor;

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
        fakeQueue.clear();
        String rawProjectKey = PortalIngestValidationFixture.PROJECT_KEY_HEADER;
        String keyPrefix = rawProjectKey.substring(0, rawProjectKey.indexOf('.'));
        projectRepository.saveAndFlush(new ProjectEntity(
                PortalIngestValidationFixture.VERIFIED_PROJECT.projectId(),
                "checkout",
                keyPrefix,
                BCrypt.hashpw(rawProjectKey, BCrypt.gensalt()),
                "active",
                FIXED_TIME,
                FIXED_TIME));
    }

    @Test
    void fakeQueueEnqueueToWorkerPersistAndDeleteSmoke() throws Exception {
        IngestAcceptanceResult queued = acceptanceService.accept(
                PortalIngestValidationFixture.PROJECT_KEY_HEADER,
                PortalIngestValidationFixture.IDEMPOTENCY_KEY,
                PortalIngestValidationFixture.goldenRequest());
        MetricIngestQueueWorker worker = new MetricIngestQueueWorker(properties, fakeQueue, fakeQueue, processor);

        worker.pollOnce();

        assertThat(queued.isQueued()).isTrue();
        assertThat(metricBucketRepository.findIdentityByProjectIdAndIdempotencyKey(
                PortalIngestValidationFixture.VERIFIED_PROJECT.projectId(),
                PortalIngestValidationFixture.IDEMPOTENCY_KEY))
                .isPresent();
        assertThat(fakeQueue.deletedMessages()).hasSize(1);
        assertThat(fakeQueue.visibleMessages()).isEmpty();
        assertThat(fakeQueue.dlqEnvelopes()).isEmpty();
    }
}
