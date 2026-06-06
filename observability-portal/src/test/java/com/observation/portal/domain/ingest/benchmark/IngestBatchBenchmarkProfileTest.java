package com.observation.portal.domain.ingest.benchmark;

import com.observation.portal.domain.ingest.queue.IngestBufferMode;
import com.observation.portal.domain.ingest.queue.IngestBufferProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IngestBatchBenchmarkProfileTest {

    @Test
    void benchmarkPropertiesAreOptInAndPrimaryFixtureUsesOneApplicationAndThirtyInstances() {
        IngestBenchmarkProperties properties = new IngestBenchmarkProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getFixture().getApplicationCount()).isEqualTo(1);
        assertThat(properties.getFixture().getInstanceCount()).isEqualTo(30);

        IngestBufferProperties defaultBufferProperties = new IngestBufferProperties();
        assertThat(defaultBufferProperties.getMode()).isEqualTo(IngestBufferMode.DIRECT);
        assertThat(defaultBufferProperties.getWorker().getMaxBatchSize()).isEqualTo(10);
    }

    @Test
    void manifestRecordsRdsReferenceSpecAndLocalFallbackWithoutQueueUrl() {
        IngestBenchmarkManifest manifest = IngestBenchmarkManifest.localFallback(
                "run-20260605",
                "abc123-dirty",
                "postgres:16-alpine isolated container on developer workstation");

        assertThat(manifest.fixture().applicationCount()).isEqualTo(1);
        assertThat(manifest.fixture().instanceCount()).isEqualTo(30);
        assertThat(manifest.database().referenceInstanceClass()).isEqualTo("db.t4g.micro");
        assertThat(manifest.database().referenceStorage()).contains("gp3 20 GiB", "3,000 IOPS", "125 MiB/s");
        assertThat(manifest.database().fallbackReason()).contains("postgres:16-alpine");
        assertThat(manifest.queue().description()).doesNotContain("https://sqs.");
    }

    @Test
    void reportTemplateKeepsPhaseOneAndPhaseTwoEvidenceSeparatedAndAvoidsProductionClaims() {
        String report = IngestBenchmarkReportTemplate.renderSkeleton(IngestBenchmarkManifest.localFallback(
                "run-20260605",
                "abc123",
                "local isolated PostgreSQL fallback"));

        assertThat(report).contains("## Phase 1 Request Latency Evidence");
        assertThat(report).contains("## Phase 2 DB Batch Throughput Evidence");
        assertThat(report).contains("portfolio evidence");
        assertThat(report).doesNotContain("production에서 동일 비율");
        assertThat(report).doesNotContain("autoscaling");
        assertThat(report).doesNotContain("cost model");
    }

    @Test
    void redactionScannerRejectsSecretsQueueUrlAndRawPayloadMarkers() {
        IngestBenchmarkRedactionScanner scanner = new IngestBenchmarkRedactionScanner();

        assertThat(scanner.findViolations("aggregate count only").violations()).isEmpty();
        assertThatThrownBy(() -> scanner.assertSafe("""
                queueUrl : http://localhost:4566/000000000000/source
                projectKey=raw-project-key
                starterCredential=starter-secret
                Authorization: Bearer token
                "token" : "secret"
                AWS_SESSION_TOKEN=session
                "payload" : {"schemaVersion" : "1.0"}
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("queue_url")
                .hasMessageContaining("raw_project_key")
                .hasMessageContaining("starter_credential")
                .hasMessageContaining("authorization_token")
                .hasMessageContaining("token")
                .hasMessageContaining("aws_credential")
                .hasMessageContaining("raw_payload");
    }
}
