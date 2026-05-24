package com.observation.portal.domain.bucket.model;

import com.observation.portal.domain.ingest.service.PortalIngestValidationFixture;
import com.observation.portal.domain.ingest.service.ValidatedIngestCandidate;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AcceptedMetricBucketWriteCommandTest {

    @Test
    void copiesValidatedEnvelopeIntoPersistenceCommandWithoutRawProjectKey() throws Exception {
        ValidatedIngestCandidate candidate = new ValidatedIngestCandidate(
                PortalIngestValidationFixture.VERIFIED_PROJECT,
                PortalIngestValidationFixture.IDEMPOTENCY_KEY,
                PortalIngestValidationFixture.requestWithLocalPercentiles());
        OffsetDateTime acceptedAt = OffsetDateTime.parse("2026-05-08T01:00:31Z");

        AcceptedMetricBucketWriteCommand command = AcceptedMetricBucketWriteCommand.from(
                candidate,
                "7f7f37f9e5a25d880fd095226fc46df04e990b8b6e2e327a22323d800ffdde46",
                acceptedAt);

        assertThat(command.projectId()).isEqualTo(PortalIngestValidationFixture.VERIFIED_PROJECT.projectId());
        assertThat(command.projectName()).isEqualTo("checkout");
        assertThat(command.applicationName()).isEqualTo("orders-api");
        assertThat(command.environment()).isEqualTo("prod");
        assertThat(command.instanceName()).isEqualTo("orders-api-7f9c9c8c9d-x2p4k");
        assertThat(command.bucketStartUtc()).isEqualTo(OffsetDateTime.parse("2026-05-08T01:00:00Z"));
        assertThat(command.bucketEndUtc()).isEqualTo(OffsetDateTime.parse("2026-05-08T01:00:30Z"));
        assertThat(command.requestCount()).isEqualTo(3L);
        assertThat(command.errorCount()).isEqualTo(1L);
        assertThat(command.durationBuckets()).first()
                .isEqualTo(new AcceptedMetricBucketWriteCommand.DurationBucket(50L, 1L));
        assertThat(command.localPercentiles()).satisfies(localPercentiles -> {
            assertThat(localPercentiles.scope()).isEqualTo("instance_bucket");
            assertThat(localPercentiles.source()).isEqualTo("starter_local");
            assertThat(localPercentiles.p95Ms()).isEqualTo(250L);
            assertThat(localPercentiles.p99Ms()).isEqualTo(1000L);
            assertThat(localPercentiles.mergeable()).isFalse();
        });
        assertThat(command.endpoints()).hasSize(2);
        assertThat(command.endpoints()).first().satisfies(endpoint -> {
            assertThat(endpoint.method()).isEqualTo("GET");
            assertThat(endpoint.route()).isEqualTo("/orders/{orderId}");
            assertThat(endpoint.durationBuckets()).hasSize(5);
        });
        assertThat(command.toString()).doesNotContain(PortalIngestValidationFixture.PROJECT_KEY_HEADER);
    }

    @Test
    void rejectsInvalidPersistenceCommandShape() {
        OffsetDateTime acceptedAt = OffsetDateTime.parse("2026-05-08T01:00:31Z");

        assertThatThrownBy(() -> new AcceptedMetricBucketWriteCommand(
                PortalIngestValidationFixture.VERIFIED_PROJECT.projectId(),
                "checkout",
                "orders-api",
                "prod",
                "pod-a",
                "1.0",
                "idempotency-key",
                "hash",
                OffsetDateTime.parse("2026-05-08T01:00:00Z"),
                OffsetDateTime.parse("2026-05-08T01:00:30Z"),
                30,
                acceptedAt,
                1L,
                2L,
                java.util.List.of(new AcceptedMetricBucketWriteCommand.DurationBucket(50L, 1L)),
                0.1d,
                0.2d,
                0.3d,
                null,
                java.util.List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("errorCount");
    }
}
