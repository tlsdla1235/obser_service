package com.observation.portal.domain.ingest.service;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
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
    void rejectsUnknownTopLevelAndNestedFields() {
        assertJsonRejectedWithUnknownField(root -> root.putObject("customMetrics"));
        assertJsonRejectedWithUnknownField(root -> ((ObjectNode) root.get("summary")).putObject("tags"));
        assertJsonRejectedWithUnknownField(root -> root.putArray("rawTimeseries").add(1));
        assertJsonRejectedWithUnknownField(root -> ((ObjectNode) root.get("endpoints").get(0)).putObject("rawPath"));
    }

    @Test
    void rejectsPostMvpRuntimeAggregateShapeBeforeServiceValidation() {
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

    private static void assertJsonRejectedWithUnknownField(JsonMutation mutation) {
        assertThatThrownBy(() -> PortalIngestValidationFixture.requestWith(mutation::mutate))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }

    @FunctionalInterface
    private interface JsonMutation {

        /**
         * golden JSON tree를 contract 위반 형태로 변형한다.
         */
        void mutate(ObjectNode root);
    }
}
