package com.observation.portal.domain.ingest.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.observation.portal.domain.bucket.model.AcceptedMetricBucketBatchItemStatus;
import com.observation.portal.domain.bucket.model.AcceptedMetricBucketBatchWriteResult;
import com.observation.portal.domain.bucket.model.AcceptedMetricBucketWriteCommand;
import com.observation.portal.domain.bucket.repository.MetricBucketRepository;
import com.observation.portal.domain.catalog.entity.ProjectEntity;
import com.observation.portal.domain.catalog.model.ProjectStatus;
import com.observation.portal.domain.catalog.repository.ProjectRepository;
import com.observation.portal.domain.ingest.model.IngestEnvelopeRequest;
import com.observation.portal.domain.ingest.queue.FakeMetricIngestQueuePublisher;
import com.observation.portal.domain.ingest.queue.IngestBufferMode;
import com.observation.portal.domain.ingest.queue.IngestBufferProperties;
import com.observation.portal.domain.ingest.queue.MetricIngestEnqueueReceipt;
import com.observation.portal.domain.ingest.queue.MetricIngestQueueMessage;
import com.observation.portal.domain.ingest.queue.MetricIngestQueueMessageFactory;
import com.observation.portal.domain.ingest.queue.MetricIngestQueueProcessResult;
import com.observation.portal.domain.ingest.queue.MetricIngestQueueProcessStatus;
import com.observation.portal.domain.ingest.queue.MetricIngestQueueProcessor;
import com.observation.portal.domain.ingest.queue.MetricIngestQueuePublisher;
import com.observation.portal.domain.ingest.queue.MetricIngestReceivedMessage;
import com.observation.portal.domain.ingest.service.IngestAcceptanceResult;
import com.observation.portal.domain.ingest.service.IngestAcceptanceService;
import com.observation.portal.domain.ingest.service.IngestPayloadHasher;
import com.observation.portal.domain.ingest.service.PortalIngestValidationFixture;
import com.observation.portal.domain.ingest.service.ProjectKeyVerificationResult;
import com.observation.portal.domain.ingest.service.ProjectKeyVerificationService;
import com.observation.portal.domain.ingest.service.ValidatedIngestCandidate;
import com.observation.portal.domain.ingest.service.VerifiedProject;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Story 12.6 benchmark evidence를 명시 opt-in으로만 생성하는 local/isolated scenario runner다.
 *
 * <p>일반 test task에서는 환경변수 guard로 skip되고, `scripts/benchmark/run-sqs-ingest-benchmark.py --opt-in`에서만
 * Testcontainers PostgreSQL을 사용해 sanitized 수치 artifact를 쓴다.</p>
 */
@EnabledIfEnvironmentVariable(named = "PORTAL_INGEST_BENCHMARK_OPT_IN", matches = "true")
@Testcontainers
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "portal.ingest.buffer.worker.enabled=false",
        "portal.ingest.benchmark.enabled=true"
})
class IngestBenchmarkScenarioRunTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000003201");
    private static final VerifiedProject VERIFIED_PROJECT = new VerifiedProject(
            PROJECT_ID,
            "checkout",
            ProjectStatus.ACTIVE);
    private static final String PROJECT_KEY_HEADER = "bench_project_key.sanitized";
    private static final String APPLICATION_NAME = "orders-api";
    private static final String ENVIRONMENT = "prod";
    private static final int APPLICATION_COUNT = 1;
    private static final int INSTANCE_COUNT = 30;
    private static final int DEFAULT_MEASUREMENT_COUNT = 90;
    private static final int DEFAULT_WARMUP_COUNT = 30;
    private static final int DEFAULT_BATCH_SIZE = 30;
    private static final OffsetDateTime BASE_BUCKET_START = OffsetDateTime.parse("2026-05-08T01:00:00Z");
    private static final DateTimeFormatter IDEMPOTENCY_BUCKET_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

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
    void writesSanitizedBenchmarkEvidenceArtifacts() throws Exception {
        int measurementCount = positiveIntEnv("PORTAL_INGEST_BENCHMARK_MEASUREMENT_COUNT", DEFAULT_MEASUREMENT_COUNT);
        int warmupCount = positiveIntEnv("PORTAL_INGEST_BENCHMARK_WARMUP_COUNT", DEFAULT_WARMUP_COUNT);
        int batchSize = positiveIntEnv("PORTAL_INGEST_BENCHMARK_BATCH_SIZE", DEFAULT_BATCH_SIZE);
        Path outputDirectory = outputDirectory();
        Files.createDirectories(outputDirectory);

        List<FixtureItem> phaseOneFixture = fixtureItems(measurementCount, 0);
        List<FixtureItem> warmupFixture = fixtureItems(warmupCount, 10_000);

        PhaseOneEvidence phaseOne = runPhaseOne(warmupFixture, phaseOneFixture);
        PhaseTwoEvidence phaseTwo = runPhaseTwo(fixtureItems(measurementCount, 20_000), batchSize);
        DuplicateSmokeEvidence duplicateSmoke = runDuplicateNoopSmoke(fixtureItems(1, 30_000).get(0));

        Map<String, Object> manifest = manifest(
                measurementCount,
                warmupCount,
                batchSize,
                phaseOne,
                phaseTwo,
                duplicateSmoke,
                "pending");
        writeJson(outputDirectory.resolve("manifest.json"), manifest);
        writeJson(outputDirectory.resolve("phase-1-request-latency.json"), phaseOne.toArtifact());
        writeJson(outputDirectory.resolve("phase-2-db-throughput.json"), phaseTwo.toArtifact(duplicateSmoke));
        Files.writeString(outputDirectory.resolve("report.md"), report(manifest, phaseOne, phaseTwo, duplicateSmoke));

        IngestBenchmarkRedactionScanner scanner = new IngestBenchmarkRedactionScanner();
        IngestBenchmarkRedactionScanner.ScanResult scanResult = scanner.scanDirectory(outputDirectory);
        assertThat(scanResult.violations()).isEmpty();

        manifest.put("redaction", Map.of(
                "scanStatus", "passed",
                "scanner", "IngestBenchmarkRedactionScanner",
                "artifactPublishGate", "passed"));
        writeJson(outputDirectory.resolve("manifest.json"), manifest);
        assertThat(scanner.scanDirectory(outputDirectory).violations()).isEmpty();
    }

    private PhaseOneEvidence runPhaseOne(List<FixtureItem> warmupFixture, List<FixtureItem> measurementFixture)
            throws Exception {
        resetDatabase();
        IngestAcceptanceService directService = service(IngestBufferMode.DIRECT, null);
        for (FixtureItem item : warmupFixture) {
            directService.accept(PROJECT_KEY_HEADER, item.idempotencyKey(), item.request());
        }

        resetDatabase();
        ScenarioTiming directTiming = measureRequests(directService, measurementFixture, IngestResultExpectation.ACCEPTED);
        long directRows = countAcceptedBuckets();

        resetDatabase();
        MeasuringPublisher measuringPublisher = new MeasuringPublisher();
        IngestAcceptanceService enqueueService = service(IngestBufferMode.FAKE, measuringPublisher);
        for (FixtureItem item : warmupFixture) {
            enqueueService.accept(PROJECT_KEY_HEADER, item.idempotencyKey(), item.request());
        }

        resetDatabase();
        measuringPublisher.clear();
        ScenarioTiming enqueueTiming = measureRequests(enqueueService, measurementFixture, IngestResultExpectation.QUEUED);
        long rowsAfterEnqueue = countAcceptedBuckets();

        return new PhaseOneEvidence(
                new RequestLatencyScenario(
                        "direct-insert-request-path",
                        "direct",
                        "저장 완료 후 응답하는 service request boundary",
                        directTiming.summary(),
                        directTiming.failureCount(),
                        directRows,
                        directRows),
                new RequestLatencyScenario(
                        "fake-queue-enqueue-request-path",
                        "fake",
                        "SQS mode enqueue semantics with in-memory fake queue; real SQS network latency evidence가 아님",
                        enqueueTiming.summary(),
                        enqueueTiming.failureCount(),
                        rowsAfterEnqueue,
                        0,
                        measuringPublisher.enqueueSummary(),
                        measuringPublisher.enqueuedCount(),
                        rowsAfterEnqueue == 0));
    }

    private PhaseTwoEvidence runPhaseTwo(List<FixtureItem> measurementFixture, int batchSize) throws Exception {
        List<AcceptedMetricBucketWriteCommand> commands = commands(measurementFixture);

        resetDatabase();
        List<Long> mvpDurations = new ArrayList<>();
        int inserted = 0;
        int transientFailure = 0;
        long mvpStartedAt = System.nanoTime();
        for (AcceptedMetricBucketWriteCommand command : commands) {
            long startedAt = System.nanoTime();
            try {
                metricBucketRepository.insert(command);
                inserted++;
            } catch (RuntimeException exception) {
                transientFailure++;
            }
            mvpDurations.add(System.nanoTime() - startedAt);
        }
        long mvpDurationNanos = System.nanoTime() - mvpStartedAt;
        ThroughputScenario workerMvp = new ThroughputScenario(
                "worker-mvp-message-by-message-persistence",
                commands.size(),
                1,
                inserted,
                0,
                0,
                transientFailure,
                commands.size(),
                commands.size(),
                nanosToMillis(mvpDurationNanos),
                bucketsPerSecond(inserted, mvpDurationNanos),
                PercentileSummary.fromNanos(mvpDurations),
                "message별 repository insert baseline이며 DB batch throughput improvement claim이 아님");

        resetDatabase();
        List<AcceptedMetricBucketBatchWriteResult> batchResults = new ArrayList<>();
        long batchStartedAt = System.nanoTime();
        for (List<AcceptedMetricBucketWriteCommand> chunk : chunks(commands, batchSize)) {
            batchResults.add(metricBucketRepository.insertBatch(chunk));
        }
        long batchDurationNanos = System.nanoTime() - batchStartedAt;
        int batchInserted = batchResults.stream().mapToInt(result -> (int) result.insertedCount()).sum();
        int duplicateNoop = batchResults.stream().mapToInt(result -> (int) result.duplicateNoopCount()).sum();
        int conflict = batchResults.stream().mapToInt(result -> (int) result.conflictCount()).sum();
        int batchTransient = batchResults.stream()
                .mapToInt(result -> (int) result.items().stream()
                        .filter(item -> item.status() == AcceptedMetricBucketBatchItemStatus.TRANSIENT_FAILURE)
                        .count())
                .sum();
        int statementCount = batchResults.stream()
                .mapToInt(AcceptedMetricBucketBatchWriteResult::bucketStatementCount)
                .sum();
        ThroughputScenario batchWriter = new ThroughputScenario(
                "batch-writer-persistence",
                commands.size(),
                batchSize,
                batchInserted,
                duplicateNoop,
                conflict,
                batchTransient,
                statementCount,
                batchResults.size(),
                nanosToMillis(batchDurationNanos),
                bucketsPerSecond(batchInserted, batchDurationNanos),
                PercentileSummary.fromNanos(List.of(batchDurationNanos)),
                "Story 12.5 batch writer DB persistence evidence");
        return new PhaseTwoEvidence(workerMvp, batchWriter);
    }

    private DuplicateSmokeEvidence runDuplicateNoopSmoke(FixtureItem item) throws Exception {
        resetDatabase();
        MetricIngestQueueProcessor processor = new MetricIngestQueueProcessor(
                objectMapper,
                payloadHasher,
                metricBucketRepository,
                Clock.systemUTC());
        MetricIngestReceivedMessage first = receivedMessage(item, "duplicate-smoke-first");
        MetricIngestReceivedMessage duplicate = receivedMessage(item, "duplicate-smoke-second");

        MetricIngestQueueProcessResult firstResult = processor.process(first);
        MetricIngestQueueProcessResult duplicateResult = processor.process(duplicate);
        return new DuplicateSmokeEvidence(
                firstResult.status().name(),
                duplicateResult.status().name(),
                duplicateResult.status() == MetricIngestQueueProcessStatus.DUPLICATE_NOOP);
    }

    private ScenarioTiming measureRequests(
            IngestAcceptanceService service,
            List<FixtureItem> fixture,
            IngestResultExpectation expectation) {
        List<Long> durations = new ArrayList<>();
        int failures = 0;
        for (FixtureItem item : fixture) {
            long startedAt = System.nanoTime();
            IngestAcceptanceResult result = service.accept(PROJECT_KEY_HEADER, item.idempotencyKey(), item.request());
            durations.add(System.nanoTime() - startedAt);
            if (!expectation.matches(result)) {
                failures++;
            }
        }
        return new ScenarioTiming(PercentileSummary.fromNanos(durations), failures);
    }

    private IngestAcceptanceService service(IngestBufferMode mode, MetricIngestQueuePublisher publisher) {
        IngestBufferProperties properties = new IngestBufferProperties();
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

    private ProjectKeyVerificationService verifiedProjectService() {
        ProjectKeyVerificationService service = mock(ProjectKeyVerificationService.class);
        when(service.verify(anyString())).thenReturn(ProjectKeyVerificationResult.verified(VERIFIED_PROJECT));
        return service;
    }

    private List<AcceptedMetricBucketWriteCommand> commands(List<FixtureItem> fixture) {
        return fixture.stream()
                .map(item -> AcceptedMetricBucketWriteCommand.from(
                        new ValidatedIngestCandidate(VERIFIED_PROJECT, item.idempotencyKey(), item.request()),
                        payloadHasher.sha256(item.request()),
                        OffsetDateTime.now(Clock.systemUTC())))
                .toList();
    }

    private MetricIngestReceivedMessage receivedMessage(FixtureItem item, String messageId) {
        MetricIngestQueueMessage message = queueMessageFactory.build(
                new ValidatedIngestCandidate(VERIFIED_PROJECT, item.idempotencyKey(), item.request()),
                OffsetDateTime.now(Clock.systemUTC()),
                OffsetDateTime.now(Clock.systemUTC()));
        return MetricIngestReceivedMessage.fromBodyJson(
                messageId,
                "receipt-" + messageId,
                message.bodyJson(),
                MetricIngestReceivedMessage.attributesFrom(message.attributes()),
                1);
    }

    private List<FixtureItem> fixtureItems(int count, int bucketOffset) throws Exception {
        List<FixtureItem> items = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            int instanceNumber = (index % INSTANCE_COUNT) + 1;
            OffsetDateTime bucketStart = BASE_BUCKET_START.plusSeconds(30L * (bucketOffset + (index / INSTANCE_COUNT)));
            String instanceName = "orders-api-bench-%03d".formatted(instanceNumber);
            String idempotencyKey = "project-123:%s:%s:%s:%s".formatted(
                    APPLICATION_NAME,
                    ENVIRONMENT,
                    instanceName,
                    IDEMPOTENCY_BUCKET_FORMAT.format(bucketStart.toInstant()));
            IngestEnvelopeRequest request = request(instanceName, bucketStart);
            items.add(new FixtureItem(instanceName, bucketStart, idempotencyKey, request));
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

    private void resetDatabase() {
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
    }

    private long countAcceptedBuckets() {
        Long count = jdbcTemplate.queryForObject("select count(*) from accepted_metric_buckets", Long.class);
        return count == null ? 0L : count;
    }

    private String postgresVersion() {
        String version = jdbcTemplate.queryForObject("select version()", String.class);
        return version == null ? "unknown" : version;
    }

    private Map<String, Object> manifest(
            int measurementCount,
            int warmupCount,
            int batchSize,
            PhaseOneEvidence phaseOne,
            PhaseTwoEvidence phaseTwo,
            DuplicateSmokeEvidence duplicateSmoke,
            String redactionStatus) {
        Map<String, Object> manifest = orderedMap();
        manifest.put("runId", runId());
        manifest.put("generatedAtUtc", OffsetDateTime.now(ZoneOffset.UTC).toString());
        manifest.put("gitRevision", gitRevision());
        manifest.put("gitDirty", gitDirty());
        manifest.put("fixture", orderedMap(
                "applicationCount", APPLICATION_COUNT,
                "instanceCount", INSTANCE_COUNT,
                "syntheticInstanceMeaning", "30 synthetic instance names inside one local benchmark fixture; not EC2 hosts",
                "phase1PayloadCount", measurementCount,
                "phase1DuplicateRatio", 0.0d,
                "phase2PayloadCount", measurementCount,
                "phase2DuplicateRatio", 0.0d,
                "conflictRatio", 0.0d,
                "cadence", "30s bucket replay with deterministic synthetic identities"));
        manifest.put("runtime", orderedMap(
                "javaVersion", System.getProperty("java.version"),
                "springProfile", "benchmark-sqs-ingest opt-in test runner",
                "workerBatchSize", batchSize,
                "workerMaxBatchAge", "2s default; DB scenario flushes bounded chunks"));
        manifest.put("host", orderedMap(
                "os", "%s %s %s".formatted(
                        System.getProperty("os.name"),
                        System.getProperty("os.version"),
                        System.getProperty("os.arch")),
                "availableProcessors", java.lang.Runtime.getRuntime().availableProcessors(),
                "jvmMaxMemoryBytes", java.lang.Runtime.getRuntime().maxMemory()));
        manifest.put("database", orderedMap(
                "engine", "PostgreSQL isolated Testcontainers run",
                "postgresVersion", postgresVersion(),
                "referenceInstanceClass", "db.t4g.micro",
                "referenceCompute", "2 vCPU / 1 GiB memory",
                "referenceStorage", "gp3 20 GiB, 3,000 IOPS / 125 MiB/s baseline",
                "fallbackReason", fallbackReason()));
        manifest.put("queue", orderedMap(
                "phase1Mode", phaseOne.enqueue().queueMode(),
                "queueType", "in-memory fake queue",
                "queueEndpointStored", false,
                "realSqsLatencyEvidence", false));
        manifest.put("timing", orderedMap(
                "warmupCount", warmupCount,
                "measurementCount", measurementCount,
                "clockSource", "System.nanoTime for durations, UTC wall clock for run metadata"));
        manifest.put("scenarios", List.of(
                phaseOne.direct().name(),
                phaseOne.enqueue().name(),
                phaseTwo.workerMvp().name(),
                phaseTwo.batchWriter().name()));
        manifest.put("duplicateNoopSmoke", duplicateSmoke.toArtifact());
        manifest.put("redaction", orderedMap(
                "scanStatus", redactionStatus,
                "forbiddenContentStored", false));
        return manifest;
    }

    private String report(
            Map<String, Object> manifest,
            PhaseOneEvidence phaseOne,
            PhaseTwoEvidence phaseTwo,
            DuplicateSmokeEvidence duplicateSmoke) {
        return """
                # SQS Buffered Ingest Benchmark Evidence

                This is local/isolated benchmark evidence from the explicit `benchmark-sqs-ingest` opt-in runner.
                The fixture uses one synthetic application and 30 synthetic instance names in one local run; it is not
                evidence for a distributed host fleet, scaling behavior, financial planning, or dashboard UI behavior.

                ## Manifest Summary

                - runId: %s
                - gitRevision: %s
                - gitDirty: %s
                - fixture: applicationCount=1, instanceCount=30, synthetic local identities
                - database fallback: %s
                - queue mode for Phase 1 enqueue: fake queue; real SQS network latency is not claimed

                ## Phase 1 Request Latency Evidence

                Direct insert request latency measures the request path that persists before responding.
                Enqueue request latency measures the request path that returns after fake queue enqueue succeeds.
                These rows are request-boundary evidence only and are not a shared end-to-end persistence latency table.

                | Scenario | Count | p50 ms | p95 ms | p99 ms | Max ms | Failure count | Request-thread accepted bucket rows |
                | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
                | Direct insert | %d | %.3f | %.3f | %.3f | %.3f | %d | %d |
                | Fake queue enqueue | %d | %.3f | %.3f | %.3f | %.3f | %d | %d |

                Fake enqueue duration summary: count=%d, p50=%.3f ms, p95=%.3f ms, p99=%.3f ms.
                Request-thread DB write absence evidence for enqueue path: acceptedBucketRowsAfterScenario=%d.

                ## Worker MVP Correctness/Lag Baseline

                Worker MVP baseline uses the message-by-message repository persistence shape from Story 12.3.
                It is a correctness and latency baseline, not a DB batch throughput improvement claim.

                | Scenario | Count | Inserted | No-op | Conflict | Transient | Bucket statement count | Persist duration ms | Persisted buckets/sec |
                | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
                | Worker MVP message-by-message | %d | %d | %d | %d | %d | %d | %.3f | %.3f |

                Same key/same hash smoke: first=%s, duplicate=%s, no-op-success=%s.

                ## Phase 2 DB Batch Throughput Evidence

                Batch writer evidence uses the Story 12.5 PostgreSQL batch writer path and is separate from request latency.

                | Scenario | Count | Batch size | Inserted | No-op | Conflict | Transient | Bucket statement count | Batch chunks | Persist duration ms | Persisted buckets/sec |
                | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
                | Batch writer | %d | %d | %d | %d | %d | %d | %d | %d | %.3f | %.3f |

                ## Deployment Smoke Boundary

                Deployment evidence remains separate: source queue smoke, worker receive/delete smoke, malformed/conflict
                DLQ smoke, direct rollback config smoke, and snapshot delay/queue lag semantics regression.
                """.formatted(
                manifest.get("runId"),
                manifest.get("gitRevision"),
                manifest.get("gitDirty"),
                fallbackReason(),
                phaseOne.direct().latency().count(),
                phaseOne.direct().latency().p50Ms(),
                phaseOne.direct().latency().p95Ms(),
                phaseOne.direct().latency().p99Ms(),
                phaseOne.direct().latency().maxMs(),
                phaseOne.direct().failureCount(),
                phaseOne.direct().requestThreadAcceptedBucketRows(),
                phaseOne.enqueue().latency().count(),
                phaseOne.enqueue().latency().p50Ms(),
                phaseOne.enqueue().latency().p95Ms(),
                phaseOne.enqueue().latency().p99Ms(),
                phaseOne.enqueue().latency().maxMs(),
                phaseOne.enqueue().failureCount(),
                phaseOne.enqueue().requestThreadAcceptedBucketRows(),
                phaseOne.enqueue().enqueueLatency().count(),
                phaseOne.enqueue().enqueueLatency().p50Ms(),
                phaseOne.enqueue().enqueueLatency().p95Ms(),
                phaseOne.enqueue().enqueueLatency().p99Ms(),
                phaseOne.enqueue().acceptedBucketRowsAfterScenario(),
                phaseTwo.workerMvp().count(),
                phaseTwo.workerMvp().insertedCount(),
                phaseTwo.workerMvp().duplicateNoopCount(),
                phaseTwo.workerMvp().conflictCount(),
                phaseTwo.workerMvp().transientCount(),
                phaseTwo.workerMvp().bucketStatementCount(),
                phaseTwo.workerMvp().persistDurationMs(),
                phaseTwo.workerMvp().persistedBucketsPerSecond(),
                duplicateSmoke.firstStatus(),
                duplicateSmoke.duplicateStatus(),
                duplicateSmoke.noopSuccess(),
                phaseTwo.batchWriter().count(),
                phaseTwo.batchWriter().batchSize(),
                phaseTwo.batchWriter().insertedCount(),
                phaseTwo.batchWriter().duplicateNoopCount(),
                phaseTwo.batchWriter().conflictCount(),
                phaseTwo.batchWriter().transientCount(),
                phaseTwo.batchWriter().bucketStatementCount(),
                phaseTwo.batchWriter().roundTripCount(),
                phaseTwo.batchWriter().persistDurationMs(),
                phaseTwo.batchWriter().persistedBucketsPerSecond());
    }

    private void writeJson(Path path, Object value) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
    }

    private static List<List<AcceptedMetricBucketWriteCommand>> chunks(
            List<AcceptedMetricBucketWriteCommand> commands,
            int batchSize) {
        List<List<AcceptedMetricBucketWriteCommand>> chunks = new ArrayList<>();
        for (int index = 0; index < commands.size(); index += batchSize) {
            chunks.add(commands.subList(index, Math.min(index + batchSize, commands.size())));
        }
        return chunks;
    }

    private static double bucketsPerSecond(long inserted, long durationNanos) {
        if (durationNanos <= 0) {
            return 0.0d;
        }
        return inserted / (durationNanos / 1_000_000_000.0d);
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0d;
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
        String configured = System.getenv("PORTAL_INGEST_BENCHMARK_OUTPUT_DIR");
        if (configured == null || configured.isBlank()) {
            return Path.of("build/reports/ingest-benchmark");
        }
        return Path.of(configured);
    }

    private static String fallbackReason() {
        String configured = System.getenv("PORTAL_INGEST_BENCHMARK_FALLBACK_REASON");
        if (configured == null || configured.isBlank()) {
            return "postgres:16-alpine isolated Testcontainers PostgreSQL on local workstation";
        }
        return configured.trim();
    }

    private static String runId() {
        return DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now()) + "-story-12-6";
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
            boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
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
        Map<String, Object> values = orderedMap();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            values.put((String) keysAndValues[index], keysAndValues[index + 1]);
        }
        return values;
    }

    private static Map<String, Object> orderedMap() {
        return new LinkedHashMap<>();
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

    private record FixtureItem(
            String instanceName,
            OffsetDateTime bucketStart,
            String idempotencyKey,
            IngestEnvelopeRequest request
    ) {
    }

    private record ScenarioTiming(PercentileSummary summary, int failureCount) {
    }

    private record PhaseOneEvidence(RequestLatencyScenario direct, RequestLatencyScenario enqueue) {

        private Map<String, Object> toArtifact() {
            return orderedMap(
                    "phase", "phase-1-request-latency",
                    "claimBoundary", "request latency only; direct persist response and fake enqueue response are separate meanings",
                    "direct", direct.toArtifact(),
                    "enqueue", enqueue.toArtifact());
        }
    }

    private record RequestLatencyScenario(
            String name,
            String queueMode,
            String measurementMeaning,
            PercentileSummary latency,
            int failureCount,
            long acceptedBucketRowsAfterScenario,
            long requestThreadAcceptedBucketRows,
            PercentileSummary enqueueLatency,
            int queuedCount,
            boolean requestThreadDbWriteAbsent
    ) {

        private RequestLatencyScenario(
                String name,
                String queueMode,
                String measurementMeaning,
                PercentileSummary latency,
                int failureCount,
                long acceptedBucketRowsAfterScenario,
                long requestThreadAcceptedBucketRows) {
            this(
                    name,
                    queueMode,
                    measurementMeaning,
                    latency,
                    failureCount,
                    acceptedBucketRowsAfterScenario,
                    requestThreadAcceptedBucketRows,
                    PercentileSummary.empty(),
                    0,
                    false);
        }

        private Map<String, Object> toArtifact() {
            return orderedMap(
                    "name", name,
                    "queueMode", queueMode,
                    "measurementMeaning", measurementMeaning,
                    "latencyMs", latency.toArtifact(),
                    "failureCount", failureCount,
                    "acceptedBucketRowsAfterScenario", acceptedBucketRowsAfterScenario,
                    "requestThreadAcceptedBucketRows", requestThreadAcceptedBucketRows,
                    "enqueueLatencyMs", enqueueLatency.toArtifact(),
                    "queuedCount", queuedCount,
                    "requestThreadDbWriteAbsent", requestThreadDbWriteAbsent);
        }
    }

    private record PhaseTwoEvidence(ThroughputScenario workerMvp, ThroughputScenario batchWriter) {

        private Map<String, Object> toArtifact(DuplicateSmokeEvidence duplicateSmoke) {
            return orderedMap(
                    "phase", "phase-2-db-throughput",
                    "claimBoundary", "DB persistence throughput only; request latency is reported in Phase 1",
                    "workerMvp", workerMvp.toArtifact(),
                    "batchWriter", batchWriter.toArtifact(),
                    "duplicateNoopSmoke", duplicateSmoke.toArtifact());
        }
    }

    private record ThroughputScenario(
            String name,
            int count,
            int batchSize,
            int insertedCount,
            int duplicateNoopCount,
            int conflictCount,
            int transientCount,
            int bucketStatementCount,
            int roundTripCount,
            double persistDurationMs,
            double persistedBucketsPerSecond,
            PercentileSummary itemDurationMs,
            String measurementMeaning
    ) {

        private Map<String, Object> toArtifact() {
            return orderedMap(
                    "name", name,
                    "count", count,
                    "batchSize", batchSize,
                    "insertedCount", insertedCount,
                    "duplicateNoopCount", duplicateNoopCount,
                    "conflictCount", conflictCount,
                    "transientCount", transientCount,
                    "bucketStatementCount", bucketStatementCount,
                    "roundTripCount", roundTripCount,
                    "persistDurationMs", persistDurationMs,
                    "persistedBucketsPerSecond", persistedBucketsPerSecond,
                    "itemDurationMs", itemDurationMs.toArtifact(),
                    "measurementMeaning", measurementMeaning);
        }
    }

    private record DuplicateSmokeEvidence(String firstStatus, String duplicateStatus, boolean noopSuccess) {

        private Map<String, Object> toArtifact() {
            return orderedMap(
                    "scenario", "same-key-same-hash-duplicate-smoke",
                    "firstStatus", firstStatus,
                    "duplicateStatus", duplicateStatus,
                    "noopSuccess", noopSuccess);
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

        private static PercentileSummary empty() {
            return new PercentileSummary(0, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d);
        }

        private static PercentileSummary fromNanos(List<Long> values) {
            if (values.isEmpty()) {
                return empty();
            }
            List<Long> sorted = values.stream()
                    .filter(Objects::nonNull)
                    .sorted(Comparator.naturalOrder())
                    .toList();
            if (sorted.isEmpty()) {
                return empty();
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
    }

    private final class MeasuringPublisher implements MetricIngestQueuePublisher {

        private final FakeMetricIngestQueuePublisher delegate = new FakeMetricIngestQueuePublisher();
        private final List<Long> enqueueDurations = Collections.synchronizedList(new ArrayList<>());

        @Override
        public MetricIngestEnqueueReceipt enqueue(MetricIngestQueueMessage message) {
            long startedAt = System.nanoTime();
            try {
                return delegate.enqueue(message);
            } finally {
                enqueueDurations.add(System.nanoTime() - startedAt);
            }
        }

        private PercentileSummary enqueueSummary() {
            return PercentileSummary.fromNanos(enqueueDurations);
        }

        private int enqueuedCount() {
            return delegate.enqueuedMessages().size();
        }

        private void clear() {
            delegate.clear();
            enqueueDurations.clear();
        }
    }
}
