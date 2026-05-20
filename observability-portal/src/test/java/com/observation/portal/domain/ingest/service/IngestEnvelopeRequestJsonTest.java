package com.observation.portal.domain.ingest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
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
        IngestAcceptanceService service = new IngestAcceptanceService(verifiedProjectKeyService());

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

    private static ProjectKeyVerificationService verifiedProjectKeyService() {
        ProjectKeyVerificationService projectKeyVerificationService = mock(ProjectKeyVerificationService.class);
        when(projectKeyVerificationService.verify(PortalIngestValidationFixture.PROJECT_KEY_HEADER))
                .thenReturn(ProjectKeyVerificationResult.verified(PortalIngestValidationFixture.VERIFIED_PROJECT));
        return projectKeyVerificationService;
    }
}
