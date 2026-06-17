package com.observation.portal.domain.ingest.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.observation.portal.domain.bucket.repository.MetricBucketRepository;
import com.observation.portal.domain.catalog.entity.ProjectEntity;
import com.observation.portal.domain.catalog.model.ProjectStatus;
import com.observation.portal.domain.catalog.repository.ProjectRepository;
import com.observation.portal.domain.ingest.model.IngestEnvelopeRequest;
import com.observation.portal.domain.ingest.queue.IngestBufferMode;
import com.observation.portal.domain.ingest.queue.IngestBufferProperties;
import com.observation.portal.domain.ingest.queue.MetricIngestDlqPublisher;
import com.observation.portal.domain.ingest.queue.MetricIngestQueueConsumer;
import com.observation.portal.domain.ingest.queue.MetricIngestQueueMessageFactory;
import com.observation.portal.domain.ingest.queue.MetricIngestQueueProcessor;
import com.observation.portal.domain.ingest.queue.MetricIngestQueuePublisher;
import com.observation.portal.domain.ingest.queue.MetricIngestQueueWorker;
import com.observation.portal.domain.ingest.queue.SqsMetricIngestDlqPublisher;
import com.observation.portal.domain.ingest.queue.SqsMetricIngestQueueConsumer;
import com.observation.portal.domain.ingest.queue.SqsMetricIngestQueuePublisher;
import com.observation.portal.domain.ingest.service.IngestAcceptanceResult;
import com.observation.portal.domain.ingest.service.IngestAcceptanceService;
import com.observation.portal.domain.ingest.service.IngestPayloadHasher;
import com.observation.portal.domain.ingest.service.PortalIngestValidationFixture;
import com.observation.portal.domain.ingest.service.ProjectKeyVerificationResult;
import com.observation.portal.domain.ingest.service.ProjectKeyVerificationService;
import com.observation.portal.domain.ingest.service.VerifiedProject;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * LocalStack SQS를 사용해 direct DB ingest와 buffered ingest의 request-path 차이를 수치화하는 opt-in evidence runner다.
 *
 * <p>이 테스트는 공개 포트폴리오에서 사용할 LocalStack SQS 기반 sanitized artifact를 생성한다.
 * 일반 test/CI에서는 환경변수 guard로 실행되지 않는다.</p>
 */
@EnabledIfEnvironmentVariable(named = "PORTAL_LOCALSTACK_SQS_EVIDENCE_OPT_IN", matches = "true")
@Testcontainers
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "portal.ingest.buffer.worker.enabled=false"
})
class LocalStackSqsIngestEvidenceRunTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000003201");
    private static final VerifiedProject VERIFIED_PROJECT = new VerifiedProject(
            PROJECT_ID,
            "checkout",
            ProjectStatus.ACTIVE);
    private static final String PROJECT_KEY_HEADER = "bench_project_key.sanitized";
    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-00000000a301");
    private static final String APPLICATION_NAME = "orders-api";
    private static final String ENVIRONMENT = "prod";
    private static final int DEFAULT_INSTANCE_COUNT = 30;
    private static final int DEFAULT_MEASUREMENT_COUNT = 3_000;
    private static final int DEFAULT_WARMUP_COUNT = 300;
    private static final int DEFAULT_CONCURRENCY = 30;
    private static final int DEFAULT_WORKER_BATCH_SIZE = 10;
    private static final int DEFAULT_DRAIN_TIMEOUT_SECONDS = 30;
    private static final OffsetDateTime BASE_BUCKET_START = OffsetDateTime.parse("2026-05-08T01:00:00Z");
    private static final DateTimeFormatter IDEMPOTENCY_BUCKET_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    private static final GenericContainer<?> LOCALSTACK = new GenericContainer<>(
            DockerImageName.parse("localstack/localstack:4.8.1"))
            .withExposedPorts(4566)
            .withEnv("SERVICES", "sqs")
            .withEnv("AWS_DEFAULT_REGION", "us-east-1")
            .withEnv("AWS_ACCESS_KEY_ID", "test")
            .withEnv("AWS_SECRET_ACCESS_KEY", "test");

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MetricBucketRepository metricBucketRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private IngestPayloadHasher payloadHasher;

    @Autowired
    private MetricIngestQueueMessageFactory queueMessageFactory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void writesLocalStackSqsEvidenceArtifacts() throws Exception {
        int measurementCount = positiveIntEnv("PORTAL_LOCALSTACK_SQS_EVIDENCE_MEASUREMENT_COUNT", DEFAULT_MEASUREMENT_COUNT);
        int warmupCount = positiveIntEnv("PORTAL_LOCALSTACK_SQS_EVIDENCE_WARMUP_COUNT", DEFAULT_WARMUP_COUNT);
        int instanceCount = positiveIntEnv("PORTAL_LOCALSTACK_SQS_EVIDENCE_INSTANCE_COUNT", DEFAULT_INSTANCE_COUNT);
        int concurrency = positiveIntEnv("PORTAL_LOCALSTACK_SQS_EVIDENCE_CONCURRENCY", DEFAULT_CONCURRENCY);
        int workerBatchSize = positiveIntEnv("PORTAL_LOCALSTACK_SQS_EVIDENCE_WORKER_BATCH_SIZE", DEFAULT_WORKER_BATCH_SIZE);
        int drainTimeoutSeconds = positiveIntEnv(
                "PORTAL_LOCALSTACK_SQS_EVIDENCE_DRAIN_TIMEOUT_SECONDS",
                DEFAULT_DRAIN_TIMEOUT_SECONDS);
        Path outputDirectory = outputDirectory();
        Files.createDirectories(outputDirectory);

        List<FixtureItem> warmupFixture = fixtureItems(warmupCount, 10_000, instanceCount);
        List<FixtureItem> measurementFixture = fixtureItems(measurementCount, 0, instanceCount);

        DirectEvidence directEvidence = runDirect(warmupFixture, measurementFixture, concurrency, instanceCount);
        LocalStackEvidence localStackEvidence = runLocalStackSqs(
                warmupFixture,
                measurementFixture,
                concurrency,
                workerBatchSize,
                drainTimeoutSeconds,
                instanceCount);

        Map<String, Object> manifest = manifest(
                instanceCount,
                measurementCount,
                warmupCount,
                concurrency,
                workerBatchSize,
                drainTimeoutSeconds);
        writeJson(outputDirectory.resolve("manifest.json"), manifest);
        writeJson(outputDirectory.resolve("direct-db.json"), directEvidence.toArtifact());
        writeJson(outputDirectory.resolve("localstack-sqs.json"), localStackEvidence.toArtifact());
        Files.writeString(outputDirectory.resolve("summary.md"), summary(manifest, directEvidence, localStackEvidence));

        assertThat(localStackEvidence.eventualPersistedRatio()).isEqualTo(1.0d);
        assertThat(localStackEvidence.persistedUniqueBucketCount()).isEqualTo(localStackEvidence.acceptedUniqueBucketCount());
        assertThat(localStackEvidence.errorRate()).isZero();
        assertThat(localStackEvidence.duplicateSuppressedCount()).isGreaterThan(0);
    }

    private DirectEvidence runDirect(
            List<FixtureItem> warmupFixture,
            List<FixtureItem> measurementFixture,
            int concurrency,
            int instanceCount) {
        resetDatabase(instanceCount);
        IngestAcceptanceService warmupService = service(IngestBufferMode.DIRECT, null);
        measureConcurrent(warmupService, warmupFixture, IngestResultExpectation.ACCEPTED, concurrency);

        resetDatabase(instanceCount);
        IngestAcceptanceService service = service(IngestBufferMode.DIRECT, null);
        ScenarioTiming timing = measureConcurrent(service, measurementFixture, IngestResultExpectation.ACCEPTED, concurrency);
        long persisted = countAcceptedBuckets();
        return new DirectEvidence(
                measurementFixture.size(),
                timing.latency(),
                timing.failureCount(),
                persisted,
                persisted);
    }

    private LocalStackEvidence runLocalStackSqs(
            List<FixtureItem> warmupFixture,
            List<FixtureItem> measurementFixture,
            int concurrency,
            int workerBatchSize,
            int drainTimeoutSeconds,
            int instanceCount) throws Exception {
        resetDatabase(instanceCount);
        try (SqsClient sqsClient = localStackSqsClient()) {
            String sourceQueueUrl = createQueue(sqsClient, "localstack-source-%s".formatted(runSuffix()));
            String dlqUrl = createQueue(sqsClient, "localstack-dlq-%s".formatted(runSuffix()));
            IngestBufferProperties properties = sqsProperties(sourceQueueUrl, dlqUrl, workerBatchSize);
            MetricIngestQueuePublisher publisher = new SqsMetricIngestQueuePublisher(sqsClient, properties);
            IngestAcceptanceService warmupService = service(IngestBufferMode.SQS, publisher, properties);
            measureConcurrent(warmupService, warmupFixture, IngestResultExpectation.QUEUED, concurrency);
            drainQueue(sqsClient, properties, warmupFixture.size(), drainTimeoutSeconds);
        }

        resetDatabase(instanceCount);
        try (SqsClient sqsClient = localStackSqsClient()) {
            String sourceQueueUrl = createQueue(sqsClient, "localstack-source-%s".formatted(runSuffix()));
            String dlqUrl = createQueue(sqsClient, "localstack-dlq-%s".formatted(runSuffix()));
            IngestBufferProperties properties = sqsProperties(sourceQueueUrl, dlqUrl, workerBatchSize);
            MetricIngestQueuePublisher publisher = new SqsMetricIngestQueuePublisher(sqsClient, properties);
            IngestAcceptanceService service = service(IngestBufferMode.SQS, publisher, properties);
            ScenarioTiming timing = measureConcurrent(service, measurementFixture, IngestResultExpectation.QUEUED, concurrency);
            long rowsAfterEnqueue = countAcceptedBuckets();
            DrainEvidence drain = drainQueue(sqsClient, properties, measurementFixture.size(), drainTimeoutSeconds);
            long persisted = countAcceptedBuckets();
            double eventualRatio = measurementFixture.isEmpty() ? 1.0d : persisted / (double) measurementFixture.size();
            DuplicateEvidence duplicate = runDuplicateSmoke(sqsClient, drainTimeoutSeconds, instanceCount);
            return new LocalStackEvidence(
                    measurementFixture.size(),
                    timing.latency(),
                    timing.failureCount(),
                    measurementFixture.size(),
                    rowsAfterEnqueue,
                    measurementFixture.size(),
                    persisted,
                    eventualRatio,
                    drain.drainSeconds(),
                    drain.pollCount(),
                    duplicate.duplicateSentCount(),
                    duplicate.duplicateSuppressedCount(),
                    duplicate.persistedRowsAfterDuplicate());
        }
    }

    private DuplicateEvidence runDuplicateSmoke(SqsClient sqsClient, int drainTimeoutSeconds, int instanceCount) throws Exception {
        FixtureItem item = fixtureItems(1, 50_000, instanceCount).get(0);
        long before = countAcceptedBuckets();
        String sourceQueueUrl = createQueue(sqsClient, "localstack-dup-source-%s".formatted(runSuffix()));
        String dlqUrl = createQueue(sqsClient, "localstack-dup-dlq-%s".formatted(runSuffix()));
        IngestBufferProperties properties = sqsProperties(sourceQueueUrl, dlqUrl, 10);
        MetricIngestQueuePublisher publisher = new SqsMetricIngestQueuePublisher(sqsClient, properties);
        IngestAcceptanceService service = service(IngestBufferMode.SQS, publisher, properties);

        service.accept(PROJECT_KEY_HEADER, item.idempotencyKey(), item.request());
        service.accept(PROJECT_KEY_HEADER, item.idempotencyKey(), item.request());
        drainQueue(sqsClient, properties, (int) before + 1, drainTimeoutSeconds);

        long after = countAcceptedBuckets();
        long insertedRows = after - before;
        long duplicateSuppressed = 2L - insertedRows;
        return new DuplicateEvidence(2, Math.max(0, duplicateSuppressed), after);
    }

    private DrainEvidence drainQueue(
            SqsClient sqsClient,
            IngestBufferProperties properties,
            int expectedRows,
            int drainTimeoutSeconds) throws InterruptedException {
        MetricIngestQueueConsumer consumer = new SqsMetricIngestQueueConsumer(sqsClient, properties);
        MetricIngestDlqPublisher dlqPublisher = new SqsMetricIngestDlqPublisher(sqsClient, properties, objectMapper);
        MetricIngestQueueProcessor processor = new MetricIngestQueueProcessor(
                objectMapper,
                payloadHasher,
                metricBucketRepository,
                Clock.systemUTC());
        MetricIngestQueueWorker worker = new MetricIngestQueueWorker(properties, consumer, dlqPublisher, processor);
        long startedAt = System.nanoTime();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(drainTimeoutSeconds);
        int polls = 0;
        while (countAcceptedBuckets() < expectedRows && System.nanoTime() < deadline) {
            worker.pollOnce();
            polls++;
            Thread.sleep(10L);
        }
        double seconds = (System.nanoTime() - startedAt) / 1_000_000_000.0d;
        assertThat(countAcceptedBuckets()).isGreaterThanOrEqualTo((long) expectedRows);
        return new DrainEvidence(seconds, polls);
    }

    private ScenarioTiming measureConcurrent(
            IngestAcceptanceService service,
            List<FixtureItem> fixture,
            IngestResultExpectation expectation,
            int concurrency) {
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<RequestResult>> futures = new ArrayList<>();
            for (FixtureItem item : fixture) {
                futures.add(executor.submit(requestTask(service, item, expectation, start)));
            }
            start.countDown();
            List<Long> durations = new ArrayList<>();
            int failures = 0;
            for (Future<RequestResult> future : futures) {
                RequestResult result = future.get(60, TimeUnit.SECONDS);
                durations.add(result.durationNanos());
                if (!result.success()) {
                    failures++;
                }
            }
            return new ScenarioTiming(PercentileSummary.fromNanos(durations), failures);
        } catch (Exception exception) {
            throw new IllegalStateException("concurrent benchmark failed", exception);
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<RequestResult> requestTask(
            IngestAcceptanceService service,
            FixtureItem item,
            IngestResultExpectation expectation,
            CountDownLatch start) {
        return () -> {
            start.await();
            long startedAt = System.nanoTime();
            IngestAcceptanceResult result = service.accept(PROJECT_KEY_HEADER, item.idempotencyKey(), item.request());
            return new RequestResult(System.nanoTime() - startedAt, expectation.matches(result));
        };
    }

    private IngestAcceptanceService service(IngestBufferMode mode, MetricIngestQueuePublisher publisher) {
        return service(mode, publisher, properties(mode));
    }

    private IngestAcceptanceService service(
            IngestBufferMode mode,
            MetricIngestQueuePublisher publisher,
            IngestBufferProperties properties) {
        properties.setMode(mode);
        return new IngestAcceptanceService(
                verifiedProjectService(),
                metricBucketRepository,
                payloadHasher,
                properties,
                queueMessageFactory,
                publisher,
                Clock.systemUTC());
    }

    private static IngestBufferProperties properties(IngestBufferMode mode) {
        IngestBufferProperties properties = new IngestBufferProperties();
        properties.setMode(mode);
        return properties;
    }

    private IngestBufferProperties sqsProperties(String sourceQueueUrl, String dlqUrl, int workerBatchSize) {
        IngestBufferProperties properties = new IngestBufferProperties();
        properties.setMode(IngestBufferMode.SQS);
        properties.getSqs().setQueueUrl(sourceQueueUrl);
        properties.getSqs().setEndpointOverride(localStackEndpoint().toString());
        properties.getWorker().setEnabled(true);
        properties.getWorker().setDlqUrl(dlqUrl);
        properties.getWorker().setLongPollSeconds(0);
        properties.getWorker().setMaxMessagesPerPoll(10);
        properties.getWorker().setMaxBatchSize(workerBatchSize);
        properties.getWorker().setMaxBatchAge(Duration.ofSeconds(1));
        return properties;
    }

    private ProjectKeyVerificationService verifiedProjectService() {
        ProjectKeyVerificationService service = mock(ProjectKeyVerificationService.class);
        when(service.verify(anyString())).thenReturn(ProjectKeyVerificationResult.verified(VERIFIED_PROJECT));
        return service;
    }

    private List<FixtureItem> fixtureItems(int count, int bucketOffset, int instanceCount) throws Exception {
        List<FixtureItem> items = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            int instanceNumber = (index % instanceCount) + 1;
            OffsetDateTime bucketStart = BASE_BUCKET_START.plusSeconds(30L * (bucketOffset + (index / instanceCount)));
            String instanceName = "orders-api-bench-%03d".formatted(instanceNumber);
            String idempotencyKey = "project-123:%s:%s:%s:%s".formatted(
                    APPLICATION_NAME,
                    ENVIRONMENT,
                    instanceName,
                    IDEMPOTENCY_BUCKET_FORMAT.format(bucketStart.toInstant()));
            items.add(new FixtureItem(idempotencyKey, request(instanceName, bucketStart)));
        }
        return List.copyOf(items);
    }

    private IngestEnvelopeRequest request(String instanceName, OffsetDateTime bucketStart) throws Exception {
        String json = PortalIngestValidationFixture.jsonWith(root -> mutateFixture(root, instanceName, bucketStart));
        return objectMapper.readValue(json, IngestEnvelopeRequest.class);
    }

    private static void mutateFixture(ObjectNode root, String instanceName, OffsetDateTime bucketStart) {
        ((ObjectNode) root.get("application")).put("name", APPLICATION_NAME);
        ((ObjectNode) root.get("application")).put("environment", ENVIRONMENT);
        ((ObjectNode) root.get("application")).put("instance", instanceName);
        ObjectNode bucket = (ObjectNode) root.get("bucket");
        bucket.put("startUtc", DateTimeFormatter.ISO_INSTANT.format(bucketStart.toInstant()));
        bucket.put("endUtc", DateTimeFormatter.ISO_INSTANT.format(bucketStart.plusSeconds(30).toInstant()));
        bucket.put("durationSeconds", 30);
    }

    private void resetDatabase(int instanceCount) {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
        OffsetDateTime now = OffsetDateTime.now(Clock.systemUTC());
        projectRepository.saveAndFlush(new ProjectEntity(
                PROJECT_ID,
                "checkout",
                "pk_benchmark",
                "$2a$10$benchmarkhashbenchmarkhashbenchmarkhashbenchmarkhash12",
                "active",
                now,
                now));
        seedCatalog(now, instanceCount);
    }

    private void seedCatalog(OffsetDateTime now, int instanceCount) {
        jdbcTemplate.update("""
                        insert into applications (
                          id, project_id, name, environment, status, first_seen_at, last_seen_at, created_at, updated_at
                        ) values (?, ?, ?, ?, 'active', ?, ?, ?, ?)
                        """,
                APPLICATION_ID,
                PROJECT_ID,
                APPLICATION_NAME,
                ENVIRONMENT,
                now,
                now,
                now,
                now);
        for (int index = 1; index <= instanceCount; index++) {
            String instanceName = "orders-api-bench-%03d".formatted(index);
            jdbcTemplate.update("""
                            insert into application_instances (
                              id, application_id, instance_name, first_seen_at, last_seen_at, created_at, updated_at
                            ) values (?, ?, ?, ?, ?, ?, ?)
                            """,
                    deterministicUuid("instance-" + instanceName),
                    APPLICATION_ID,
                    instanceName,
                    now,
                    now,
                    now,
                    now);
        }
    }

    private long countAcceptedBuckets() {
        Long count = jdbcTemplate.queryForObject("select count(*) from accepted_metric_buckets", Long.class);
        return count == null ? 0L : count;
    }

    private SqsClient localStackSqsClient() {
        return SqsClient.builder()
                .endpointOverride(localStackEndpoint())
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .region(Region.US_EAST_1)
                .build();
    }

    private static URI localStackEndpoint() {
        return URI.create("http://%s:%d".formatted(LOCALSTACK.getHost(), LOCALSTACK.getMappedPort(4566)));
    }

    private static String createQueue(SqsClient sqsClient, String queueName) {
        return sqsClient.createQueue(CreateQueueRequest.builder()
                        .queueName(queueName)
                        .build())
                .queueUrl();
    }

    private Map<String, Object> manifest(
            int instanceCount,
            int measurementCount,
            int warmupCount,
            int concurrency,
            int workerBatchSize,
            int drainTimeoutSeconds) {
        return orderedMap(
                "runId", runId(),
                "generatedAtUtc", OffsetDateTime.now(ZoneOffset.UTC).toString(),
                "gitRevision", gitRevision(),
                "gitDirty", gitDirty(),
                "purpose", "document LocalStack SQS buffered ingest local evidence",
                "environment", orderedMap(
                        "postgres", "Testcontainers PostgreSQL",
                        "queue", "LocalStack SQS Standard queue",
                        "awsProductionClaim", false),
                "load", orderedMap(
                        "instances", instanceCount,
                        "measurementCount", measurementCount,
                        "warmupCount", warmupCount,
                        "concurrency", concurrency,
                        "duplicateSmokeMessages", 2),
                "worker", orderedMap(
                        "maxMessagesPerPoll", 10,
                        "maxBatchSize", workerBatchSize,
                        "drainTimeoutSeconds", drainTimeoutSeconds),
                "claimBoundary", List.of(
                        "request-path latency only",
                        "eventual DB persistence correctness",
                        "local benchmark with LocalStack SQS; not AWS production latency"));
    }

    private String summary(Map<String, Object> manifest, DirectEvidence direct, LocalStackEvidence localStack) {
        double p95Improvement = improvementPercent(direct.latency().p95Ms(), localStack.latency().p95Ms());
        double p99Improvement = improvementPercent(direct.latency().p99Ms(), localStack.latency().p99Ms());
        int instanceCount = ((Number) ((Map<?, ?>) manifest.get("load")).get("instances")).intValue();
        return """
                # LocalStack SQS Buffered Ingest Evidence

                이 결과는 LocalStack SQS buffered ingest의 request-path와 final persistence 경계를 확인하기 위한 로컬 evidence다.
                AWS 운영 환경 latency, autoscaling, 비용, dashboard UI 성능을 보장하지 않는다.

                ## Summary

                | Case | Instances | Requests | Error %% | p50 ms | p95 ms | p99 ms | Persisted %% | Drain sec |
                | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
                | Direct DB | %d | %d | %.2f | %.3f | %.3f | %.3f | 100.00 | 0.000 |
                | LocalStack SQS | %d | %d | %.2f | %.3f | %.3f | %.3f | %.2f | %.3f |

                ## Portfolio Claim Boundary

                - Request p95 delta vs Direct DB (positive is faster): %.2f%%
                - Request p99 delta vs Direct DB (positive is faster): %.2f%%
                - LocalStack SQS eventual persisted ratio: %.2f%%
                - Duplicate suppressed count: %d

                LocalStack SQS buffered mode는 DB insert를 request path 밖으로 분리해 request latency를 낮추는지 확인하기 위한 비교다.
                Worker drain 이후 최종 저장률과 duplicate no-op 성격을 함께 확인해, request-path 개선과 persistence correctness를 분리해서 기록한다.

                ## Manifest

                - runId: %s
                - gitRevision: %s
                - gitDirty: %s
                """.formatted(
                instanceCount,
                direct.requestCount(),
                direct.errorRate() * 100.0d,
                direct.latency().p50Ms(),
                direct.latency().p95Ms(),
                direct.latency().p99Ms(),
                instanceCount,
                localStack.requestCount(),
                localStack.errorRate() * 100.0d,
                localStack.latency().p50Ms(),
                localStack.latency().p95Ms(),
                localStack.latency().p99Ms(),
                localStack.eventualPersistedRatio() * 100.0d,
                localStack.workerDrainSeconds(),
                p95Improvement,
                p99Improvement,
                localStack.eventualPersistedRatio() * 100.0d,
                localStack.duplicateSuppressedCount(),
                manifest.get("runId"),
                manifest.get("gitRevision"),
                manifest.get("gitDirty"));
    }

    private void writeJson(Path path, Object value) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
    }

    private static int positiveIntEnv(String name, int defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        int parsed = Integer.parseInt(value);
        if (parsed < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return parsed;
    }

    private static Path outputDirectory() {
        String configured = System.getenv("PORTAL_LOCALSTACK_SQS_EVIDENCE_OUTPUT_DIR");
        if (configured == null || configured.isBlank()) {
            return Path.of("build/reports/localstack-sqs-ingest-evidence");
        }
        return Path.of(configured);
    }

    private static String runId() {
        return DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now()) + "-localstack-sqs-evidence";
    }

    private static String runSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static UUID deterministicUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static double improvementPercent(double baseline, double candidate) {
        if (baseline <= 0.0d) {
            return 0.0d;
        }
        return ((baseline - candidate) / baseline) * 100.0d;
    }

    private static String gitRevision() {
        String revision = commandOutput("git", "rev-parse", "--short", "HEAD");
        return revision.isBlank() ? "unknown" : revision;
    }

    private static boolean gitDirty() {
        return !commandOutput("git", "status", "--short").isBlank();
    }

    private static String commandOutput(String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            byte[] bytes = process.getInputStream().readAllBytes();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished || process.exitValue() != 0) {
                return "";
            }
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8).trim();
        } catch (IOException exception) {
            return "";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    private static Map<String, Object> orderedMap(Object... keysAndValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            values.put((String) keysAndValues[index], keysAndValues[index + 1]);
        }
        return values;
    }

    private enum IngestResultExpectation {
        ACCEPTED {
            @Override
            boolean matches(IngestAcceptanceResult result) {
                return result.isAccepted();
            }
        },
        QUEUED {
            @Override
            boolean matches(IngestAcceptanceResult result) {
                return result.isQueued();
            }
        };

        abstract boolean matches(IngestAcceptanceResult result);
    }

    private record FixtureItem(String idempotencyKey, IngestEnvelopeRequest request) {
    }

    private record RequestResult(long durationNanos, boolean success) {
    }

    private record ScenarioTiming(PercentileSummary latency, int failureCount) {
    }

    private record DrainEvidence(double drainSeconds, int pollCount) {
    }

    private record DuplicateEvidence(int duplicateSentCount, long duplicateSuppressedCount, long persistedRowsAfterDuplicate) {
    }

    private record DirectEvidence(
            int requestCount,
            PercentileSummary latency,
            int failureCount,
            long requestThreadDbWriteCount,
            long persistedUniqueBucketCount
    ) {
        private double errorRate() {
            return requestCount == 0 ? 0.0d : failureCount / (double) requestCount;
        }

        private Map<String, Object> toArtifact() {
            return orderedMap(
                    "case", "DIRECT_DB",
                    "requestLatencyMs", latency.toArtifact(),
                    "requestCount", requestCount,
                    "failureCount", failureCount,
                    "errorRate", errorRate(),
                    "requestThreadDbWriteCount", requestThreadDbWriteCount,
                    "persistedUniqueBucketCount", persistedUniqueBucketCount);
        }
    }

    private record LocalStackEvidence(
            int requestCount,
            PercentileSummary latency,
            int failureCount,
            int queuedCount,
            long requestThreadDbWriteCount,
            long acceptedUniqueBucketCount,
            long persistedUniqueBucketCount,
            double eventualPersistedRatio,
            double workerDrainSeconds,
            int workerPollCount,
            int duplicateSentCount,
            long duplicateSuppressedCount,
            long persistedRowsAfterDuplicate
    ) {
        private double errorRate() {
            return requestCount == 0 ? 0.0d : failureCount / (double) requestCount;
        }

        private Map<String, Object> toArtifact() {
            return orderedMap(
                    "case", "LOCALSTACK_SQS",
                    "requestLatencyMs", latency.toArtifact(),
                    "requestCount", requestCount,
                    "failureCount", failureCount,
                    "errorRate", errorRate(),
                    "queuedCount", queuedCount,
                    "requestThreadDbWriteCount", requestThreadDbWriteCount,
                    "acceptedUniqueBucketCount", acceptedUniqueBucketCount,
                    "persistedUniqueBucketCount", persistedUniqueBucketCount,
                    "eventualPersistedRatio", eventualPersistedRatio,
                    "workerDrainSeconds", workerDrainSeconds,
                    "workerPollCount", workerPollCount,
                    "duplicateSentCount", duplicateSentCount,
                    "duplicateSuppressedCount", duplicateSuppressedCount,
                    "persistedRowsAfterDuplicate", persistedRowsAfterDuplicate);
        }
    }

    private record PercentileSummary(
            int count,
            double minMs,
            double p50Ms,
            double p95Ms,
            double p99Ms,
            double maxMs
    ) {
        private static PercentileSummary fromNanos(List<Long> values) {
            List<Long> sorted = values.stream()
                    .filter(Objects::nonNull)
                    .sorted(Comparator.naturalOrder())
                    .toList();
            if (sorted.isEmpty()) {
                return new PercentileSummary(0, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d);
            }
            return new PercentileSummary(
                    sorted.size(),
                    nanosToMillis(sorted.get(0)),
                    percentile(sorted, 50),
                    percentile(sorted, 95),
                    percentile(sorted, 99),
                    nanosToMillis(sorted.get(sorted.size() - 1)));
        }

        private static double percentile(List<Long> sorted, int percentile) {
            int index = (int) Math.ceil((percentile / 100.0d) * sorted.size()) - 1;
            int boundedIndex = Math.max(0, Math.min(index, sorted.size() - 1));
            return nanosToMillis(sorted.get(boundedIndex));
        }

        private Map<String, Object> toArtifact() {
            return orderedMap(
                    "count", count,
                    "min", minMs,
                    "p50", p50Ms,
                    "p95", p95Ms,
                    "p99", p99Ms,
                    "max", maxMs);
        }

        private static double nanosToMillis(long nanos) {
            return nanos / 1_000_000.0d;
        }
    }
}
