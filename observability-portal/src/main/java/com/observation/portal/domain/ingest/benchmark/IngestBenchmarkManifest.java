package com.observation.portal.domain.ingest.benchmark;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * ingest benchmark evidence와 함께 저장할 sanitized environment manifest다.
 *
 * <p>queue URL, raw payload, credential은 포함하지 않고 fixture cardinality와 RDS reference/fallback 사유만 기록한다.</p>
 */
public record IngestBenchmarkManifest(
        String runId,
        String gitRevision,
        Fixture fixture,
        Runtime runtime,
        Database database,
        Queue queue,
        String timing,
        String redaction
) {

    public IngestBenchmarkManifest {
        runId = requireText(runId, "runId");
        gitRevision = requireText(gitRevision, "gitRevision");
        Objects.requireNonNull(fixture, "fixture must not be null");
        Objects.requireNonNull(runtime, "runtime must not be null");
        Objects.requireNonNull(database, "database must not be null");
        Objects.requireNonNull(queue, "queue must not be null");
        timing = requireText(timing, "timing");
        redaction = requireText(redaction, "redaction");
    }

    public static IngestBenchmarkManifest localFallback(
            String runId,
            String gitRevision,
            String fallbackReason) {
        return new IngestBenchmarkManifest(
                runId,
                gitRevision,
                new Fixture(1, 30, "same fixture and idempotency distribution for direct and SQS buffered paths"),
                new Runtime("benchmark-sqs-ingest opt-in profile, worker max-batch-size/age recorded per run"),
                new Database(
                        "Amazon RDS for PostgreSQL",
                        "db.t4g.micro",
                        "2 vCPU / 1 GiB memory",
                        "gp3 20 GiB, 3,000 IOPS / 125 MiB/s baseline",
                        requireText(fallbackReason, "fallbackReason")),
                new Queue("fake/SQS/LocalStack type and region only; queue URL redacted"),
                "warmup and measurement duration recorded by runner at " + OffsetDateTime.now(),
                "redaction scan required before publishing");
    }

    public record Fixture(int applicationCount, int instanceCount, String distribution) {

        public Fixture {
            if (applicationCount != 1) {
                throw new IllegalArgumentException("applicationCount must be 1");
            }
            if (instanceCount != 30) {
                throw new IllegalArgumentException("instanceCount must be 30");
            }
            distribution = requireText(distribution, "distribution");
        }
    }

    public record Runtime(String description) {

        public Runtime {
            description = requireText(description, "description");
        }
    }

    public record Database(
            String engine,
            String referenceInstanceClass,
            String referenceCompute,
            String referenceStorage,
            String fallbackReason
    ) {

        public Database {
            engine = requireText(engine, "engine");
            referenceInstanceClass = requireText(referenceInstanceClass, "referenceInstanceClass");
            referenceCompute = requireText(referenceCompute, "referenceCompute");
            referenceStorage = requireText(referenceStorage, "referenceStorage");
            fallbackReason = requireText(fallbackReason, "fallbackReason");
        }
    }

    public record Queue(String description) {

        public Queue {
            description = requireText(description, "description");
        }
    }

    private static String requireText(String value, String fieldName) {
        String requiredValue = Objects.requireNonNull(value, fieldName + " must not be null").trim();
        if (requiredValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return requiredValue;
    }
}
