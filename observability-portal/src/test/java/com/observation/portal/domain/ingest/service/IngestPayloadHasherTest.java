package com.observation.portal.domain.ingest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.portal.domain.ingest.model.IngestEnvelopeRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IngestPayloadHasherTest {

    @Test
    void hashesValidatedRequestDeterministicallyAfterUnknownFieldsAreIgnored() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        IngestPayloadHasher hasher = new IngestPayloadHasher(objectMapper);
        IngestEnvelopeRequest original = PortalIngestValidationFixture.goldenRequest();
        IngestEnvelopeRequest withUnknownFields = objectMapper.readValue(
                PortalIngestValidationFixture.jsonWith(root -> root.putObject("customMetrics").put("ignored", 1)),
                IngestEnvelopeRequest.class);

        String first = hasher.sha256(original);
        String second = hasher.sha256(withUnknownFields);

        assertThat(first).hasSize(64).isEqualTo(second);
    }
}
