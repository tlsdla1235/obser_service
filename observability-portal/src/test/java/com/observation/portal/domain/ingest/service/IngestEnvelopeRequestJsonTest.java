package com.observation.portal.domain.ingest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.observation.portal.domain.bucket.model.AcceptedMetricBucketReceipt;
import com.observation.portal.domain.bucket.model.AcceptedMetricBucketWriteCommand;
import com.observation.portal.domain.bucket.repository.MetricBucketRepository;
import com.observation.portal.domain.ingest.model.IngestEnvelopeRequest;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IngestEnvelopeRequestJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void jsonBoundaryIgnoresUnknownFieldsAndAcceptedRequestStillCounts() throws Exception {
        String json = PortalIngestValidationFixture.jsonWith(root -> {
            root.putObject("customMetrics").put("ignored", 1);
            root.putArray("rawTimeseries").add(42);
            root.putObject("futureUnsupportedField").put("ignored", true);
            ((ObjectNode) root.get("summary")).putObject("tags").put("tenant", "checkout");
            ((ObjectNode) root.get("endpoints").get(0)).putObject("tags").put("userId", "u-123");
        });
        IngestEnvelopeRequest request = objectMapper.readValue(json, IngestEnvelopeRequest.class);
        IngestAcceptanceService service = new IngestAcceptanceService(
                verifiedProjectKeyService(),
                acceptingRepository(),
                new IngestPayloadHasher(objectMapper));

        IngestAcceptanceResult result = service.accept(
                PortalIngestValidationFixture.PROJECT_KEY_HEADER,
                PortalIngestValidationFixture.IDEMPOTENCY_KEY,
                request);

        assertThat(result.isAccepted()).isTrue();
        assertThat(result.acceptedCandidate()).hasValueSatisfying(candidate -> {
            IngestEnvelopeRequest payload = candidate.payload();
            assertThat(payload.summary().requestCount()).isEqualTo(3);
            assertThat(payload.summary().errorCount()).isEqualTo(1);
            assertThat(payload.endpoints()).hasSize(2);
            assertThat(payload.endpoints().get(0).method()).isEqualTo("GET");
            assertThat(payload.endpoints().get(0).route()).isEqualTo("/orders/{orderId}");
        });
    }

    @Test
    void jsonBoundaryParsesLocalPercentilesAsSupportedField() throws Exception {
        String json = PortalIngestValidationFixture.jsonWith(PortalIngestValidationFixture::addValidLocalPercentiles);

        IngestEnvelopeRequest request = objectMapper.readValue(json, IngestEnvelopeRequest.class);

        assertThat(request.summary().localPercentiles()).satisfies(localPercentiles -> {
            assertThat(localPercentiles.scope()).isEqualTo("instance_bucket");
            assertThat(localPercentiles.source()).isEqualTo("starter_local");
            assertThat(localPercentiles.p95Ms()).isEqualTo(250L);
            assertThat(localPercentiles.p99Ms()).isEqualTo(1000L);
            assertThat(localPercentiles.mergeable()).isFalse();
        });
    }

    private static ProjectKeyVerificationService verifiedProjectKeyService() {
        ProjectKeyVerificationService projectKeyVerificationService = mock(ProjectKeyVerificationService.class);
        when(projectKeyVerificationService.verify(PortalIngestValidationFixture.PROJECT_KEY_HEADER))
                .thenReturn(ProjectKeyVerificationResult.verified(PortalIngestValidationFixture.VERIFIED_PROJECT));
        return projectKeyVerificationService;
    }

    private static MetricBucketRepository acceptingRepository() {
        MetricBucketRepository metricBucketRepository = mock(MetricBucketRepository.class);
        when(metricBucketRepository.insert(any(AcceptedMetricBucketWriteCommand.class)))
                .thenReturn(new AcceptedMetricBucketReceipt(
                        UUID.fromString("00000000-0000-0000-0000-00000000a332"),
                        OffsetDateTime.parse("2026-05-08T01:00:31Z")));
        return metricBucketRepository;
    }
}
