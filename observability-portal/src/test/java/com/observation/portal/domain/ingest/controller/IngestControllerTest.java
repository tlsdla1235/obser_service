package com.observation.portal.domain.ingest.controller;

import com.observation.portal.domain.bucket.model.AcceptedMetricBucketReceipt;
import com.observation.portal.domain.ingest.dto.IngestAcceptedResponse;
import com.observation.portal.domain.ingest.dto.IngestErrorResponse;
import com.observation.portal.domain.ingest.service.IngestAcceptanceResult;
import com.observation.portal.domain.ingest.service.IngestAcceptanceService;
import com.observation.portal.domain.ingest.service.IngestValidationError;
import com.observation.portal.domain.ingest.service.PortalIngestValidationFixture;
import com.observation.portal.domain.ingest.service.ValidatedIngestCandidate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestControllerTest {

    @Test
    void mapsFirstSuccessfulIngestTo201CreatedWithBucketReceipt() throws Exception {
        IngestAcceptanceService service = mock(IngestAcceptanceService.class);
        IngestController controller = new IngestController(service);
        AcceptedMetricBucketReceipt receipt = new AcceptedMetricBucketReceipt(
                UUID.fromString("00000000-0000-0000-0000-00000000a333"),
                OffsetDateTime.parse("2026-05-08T01:00:31Z"));
        var request = PortalIngestValidationFixture.goldenRequest();
        when(service.accept(
                PortalIngestValidationFixture.PROJECT_KEY_HEADER,
                PortalIngestValidationFixture.IDEMPOTENCY_KEY,
                request))
                .thenReturn(IngestAcceptanceResult.accepted(new ValidatedIngestCandidate(
                        PortalIngestValidationFixture.VERIFIED_PROJECT,
                        PortalIngestValidationFixture.IDEMPOTENCY_KEY,
                        request), receipt));

        ResponseEntity<?> response = controller.acceptBucket(
                PortalIngestValidationFixture.PROJECT_KEY_HEADER,
                PortalIngestValidationFixture.IDEMPOTENCY_KEY,
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation())
                .isEqualTo(URI.create("/api/ingest/v1/buckets/" + receipt.bucketId()));
        assertThat(response.getBody()).isEqualTo(new IngestAcceptedResponse(
                "accepted",
                false,
                receipt.bucketId(),
                receipt.acceptedAt()));
        verify(service).accept(
                PortalIngestValidationFixture.PROJECT_KEY_HEADER,
                PortalIngestValidationFixture.IDEMPOTENCY_KEY,
                request);
    }

    @Test
    void mapsInvalidPayloadTo400BadRequest() throws Exception {
        IngestAcceptanceService service = mock(IngestAcceptanceService.class);
        IngestController controller = new IngestController(service);
        var request = PortalIngestValidationFixture.goldenRequest();
        List<IngestValidationError> errors = List.of(IngestValidationError.of(
                "unsupported_schema_version",
                "schemaVersion",
                "schemaVersion must be 1.0"));
        when(service.accept(
                PortalIngestValidationFixture.PROJECT_KEY_HEADER,
                PortalIngestValidationFixture.IDEMPOTENCY_KEY,
                request))
                .thenReturn(IngestAcceptanceResult.invalid(errors));

        ResponseEntity<?> response = controller.acceptBucket(
                PortalIngestValidationFixture.PROJECT_KEY_HEADER,
                PortalIngestValidationFixture.IDEMPOTENCY_KEY,
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(IngestErrorResponse.invalidRequest(errors));
    }

    @Test
    void mapsInvalidProjectKeyTo401Unauthorized() throws Exception {
        IngestAcceptanceService service = mock(IngestAcceptanceService.class);
        IngestController controller = new IngestController(service);
        var request = PortalIngestValidationFixture.goldenRequest();
        when(service.accept(
                PortalIngestValidationFixture.PROJECT_KEY_HEADER,
                PortalIngestValidationFixture.IDEMPOTENCY_KEY,
                request))
                .thenReturn(IngestAcceptanceResult.unauthorized());

        ResponseEntity<?> response = controller.acceptBucket(
                PortalIngestValidationFixture.PROJECT_KEY_HEADER,
                PortalIngestValidationFixture.IDEMPOTENCY_KEY,
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo(IngestErrorResponse.unauthorized());
    }

    @Test
    void mapsRevokedOrRotatedOldCredentialVerificationFailureTo401Unauthorized() throws Exception {
        IngestAcceptanceService service = mock(IngestAcceptanceService.class);
        IngestController controller = new IngestController(service);
        var request = PortalIngestValidationFixture.goldenRequest();
        String oldCredentialHeader = "obs_live_old.<shown-once-old>";
        when(service.accept(
                oldCredentialHeader,
                PortalIngestValidationFixture.IDEMPOTENCY_KEY,
                request))
                .thenReturn(IngestAcceptanceResult.unauthorized());

        ResponseEntity<?> response = controller.acceptBucket(
                oldCredentialHeader,
                PortalIngestValidationFixture.IDEMPOTENCY_KEY,
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo(IngestErrorResponse.unauthorized());
    }

    @Test
    void mapsDuplicateIdempotencyKeyRejectTo409Conflict() throws Exception {
        IngestAcceptanceService service = mock(IngestAcceptanceService.class);
        IngestController controller = new IngestController(service);
        var request = PortalIngestValidationFixture.goldenRequest();
        when(service.accept(
                PortalIngestValidationFixture.PROJECT_KEY_HEADER,
                PortalIngestValidationFixture.IDEMPOTENCY_KEY,
                request))
                .thenReturn(IngestAcceptanceResult.duplicateIdempotencyKey());

        ResponseEntity<?> response = controller.acceptBucket(
                PortalIngestValidationFixture.PROJECT_KEY_HEADER,
                PortalIngestValidationFixture.IDEMPOTENCY_KEY,
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(IngestErrorResponse.duplicateIdempotencyKey());
        verify(service).accept(
                PortalIngestValidationFixture.PROJECT_KEY_HEADER,
                PortalIngestValidationFixture.IDEMPOTENCY_KEY,
                request);
    }
}
