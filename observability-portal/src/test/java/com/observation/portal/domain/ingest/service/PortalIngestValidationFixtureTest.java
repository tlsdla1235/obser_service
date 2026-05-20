package com.observation.portal.domain.ingest.service;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortalIngestValidationFixtureTest {

    @Test
    void story25GoldenJsonDeserializesAsPortalSuccessFixture() throws Exception {
        IngestEnvelopeRequest request = PortalIngestValidationFixture.goldenRequest();

        assertThat(request.schemaVersion()).isEqualTo("1.0");
        assertThat(request.application().name()).isEqualTo("orders-api");
        assertThat(request.bucket().durationSeconds()).isEqualTo(30);
        assertThat(request.summary().httpServerDurationBuckets()).hasSize(5);
        assertThat(request.endpoints()).hasSize(2);
        assertThat(PortalIngestValidationFixture.IDEMPOTENCY_KEY)
                .isEqualTo("project-123:orders-api:prod:orders-api-7f9c9c8c9d-x2p4k:20260508T010000Z");
    }

    @Test
    void ignoresUnknownTopLevelAndNestedFields() throws Exception {
        IngestEnvelopeRequest request = PortalIngestValidationFixture.requestWith(root -> {
            root.putObject("customMetrics").put("ignored", 1);
            ((ObjectNode) root.get("summary")).putObject("tags").put("tenant", "checkout");
            root.putArray("rawTimeseries").add(1);
            ((ObjectNode) root.get("endpoints").get(0)).putObject("rawPath").put("value", "/orders/12345");
        });

        assertThat(request.summary().requestCount()).isEqualTo(3);
        assertThat(request.endpoints().get(0).method()).isEqualTo("GET");
        assertThat(request.endpoints().get(0).route()).isEqualTo("/orders/{orderId}");
    }

    @Test
    void rejectsUnsupportedRuntimeAggregateShapeOnSupportedRatioField() {
        assertThatThrownBy(() -> PortalIngestValidationFixture.requestWith(root -> {
            root.put("schemaVersion", "1.1");
            ObjectNode cpuUsage = ((ObjectNode) root.get("summary").get("jvm")).putObject("cpuUsage");
            cpuUsage.put("latest", 0.64d);
            cpuUsage.put("max", 0.91d);
            cpuUsage.put("avg", 0.70d);
            cpuUsage.put("sampleCount", 6);
        }))
                .isInstanceOf(MismatchedInputException.class);
    }
}
