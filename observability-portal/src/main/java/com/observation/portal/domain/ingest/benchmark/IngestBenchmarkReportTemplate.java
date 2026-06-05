package com.observation.portal.domain.ingest.benchmark;

import java.util.Objects;

/**
 * benchmark report의 claim boundary를 고정하는 간단한 Markdown template renderer다.
 */
public final class IngestBenchmarkReportTemplate {

    private IngestBenchmarkReportTemplate() {
    }

    /**
     * Phase 1 request latency와 Phase 2 DB batch throughput evidence를 별도 section으로 렌더링한다.
     */
    public static String renderSkeleton(IngestBenchmarkManifest manifest) {
        IngestBenchmarkManifest requiredManifest = Objects.requireNonNull(manifest, "manifest must not be null");
        return """
                # SQS Buffered Ingest Benchmark Evidence

                This report is portfolio evidence and relative trend context from an opt-in local/isolated benchmark profile.
                It is not a production load test, scaling proof, cloud benchmark suite, or financial planning artifact.

                ## Manifest

                - runId: %s
                - gitRevision: %s
                - fixture: applicationCount=%d, instanceCount=%d
                - database reference: %s, %s, %s
                - fallback: %s

                ## Phase 1 Request Latency Evidence

                Direct insert path and SQS enqueue path request latency metrics are recorded here only.
                DB persistence completion and batch throughput are not claimed in this table.

                ## Worker MVP Correctness/Lag Baseline

                Worker MVP evidence records enqueue-to-persist lag and message-level processing baseline.
                It does not claim DB batch throughput improvement.

                ## Phase 2 DB Batch Throughput Evidence

                Batch writer path metrics record batch size, inserted/no-op/conflict counts, bucket statement count,
                batch persist duration, and persisted buckets/sec.

                ## Deployment Smoke Evidence Boundary

                Deployment evidence remains SQS queue, worker receive/delete, malformed/conflict DLQ, rollback smoke,
                and snapshot delay/queue lag semantics regression.
                """.formatted(
                requiredManifest.runId(),
                requiredManifest.gitRevision(),
                requiredManifest.fixture().applicationCount(),
                requiredManifest.fixture().instanceCount(),
                requiredManifest.database().referenceInstanceClass(),
                requiredManifest.database().referenceCompute(),
                requiredManifest.database().referenceStorage(),
                requiredManifest.database().fallbackReason());
    }
}
