package com.observation.portal.domain.ingest.controller;

import com.observation.portal.domain.ingest.dto.IngestErrorResponse;
import com.observation.portal.domain.ingest.dto.IngestHeartbeatResponse;
import com.observation.portal.domain.ingest.model.IngestHeartbeatRequest;
import com.observation.portal.domain.ingest.service.IngestHeartbeatReceipt;
import com.observation.portal.domain.ingest.service.IngestHeartbeatResult;
import com.observation.portal.domain.ingest.service.IngestHeartbeatService;
import com.observation.portal.domain.ingest.service.IngestValidationError;
import com.observation.portal.domain.ingest.service.PortalIngestValidationFixture;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestHeartbeatControllerTest {

    @Test
    void mapsReceivedHeartbeatTo200Ok() {
        IngestHeartbeatService service = mock(IngestHeartbeatService.class);
        IngestHeartbeatController controller = new IngestHeartbeatController(service);
        IngestHeartbeatRequest request = validRequest();
        IngestHeartbeatResponse heartbeatResponse = response();
        IngestHeartbeatReceipt receipt = receipt();
        when(service.receive(PortalIngestValidationFixture.PROJECT_KEY_HEADER, request))
                .thenReturn(IngestHeartbeatResult.received(receipt));

        ResponseEntity<?> response = controller.receiveHeartbeat(
                PortalIngestValidationFixture.PROJECT_KEY_HEADER,
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(heartbeatResponse);
        verify(service).receive(PortalIngestValidationFixture.PROJECT_KEY_HEADER, request);
    }

    @Test
    void mapsInvalidHeartbeatTo400BadRequest() {
        IngestHeartbeatService service = mock(IngestHeartbeatService.class);
        IngestHeartbeatController controller = new IngestHeartbeatController(service);
        IngestHeartbeatRequest request = validRequest();
        List<IngestValidationError> errors = List.of(IngestValidationError.of(
                "unsupported_schema_version",
                "schemaVersion",
                "schemaVersion must be 1.0"));
        when(service.receive(PortalIngestValidationFixture.PROJECT_KEY_HEADER, request))
                .thenReturn(IngestHeartbeatResult.invalid(errors));

        ResponseEntity<?> response = controller.receiveHeartbeat(
                PortalIngestValidationFixture.PROJECT_KEY_HEADER,
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(IngestErrorResponse.invalidHeartbeatRequest(errors));
    }

    @Test
    void mapsInvalidProjectKeyTo401Unauthorized() {
        IngestHeartbeatService service = mock(IngestHeartbeatService.class);
        IngestHeartbeatController controller = new IngestHeartbeatController(service);
        IngestHeartbeatRequest request = validRequest();
        when(service.receive(PortalIngestValidationFixture.PROJECT_KEY_HEADER, request))
                .thenReturn(IngestHeartbeatResult.unauthorized());

        ResponseEntity<?> response = controller.receiveHeartbeat(
                PortalIngestValidationFixture.PROJECT_KEY_HEADER,
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo(IngestErrorResponse.unauthorized());
    }

    private static IngestHeartbeatRequest validRequest() {
        return new IngestHeartbeatRequest(
                "1.0",
                "0.1.0-test",
                new IngestHeartbeatRequest.Heartbeat("2026-05-24T08:30:00Z", 1L, 30),
                new IngestHeartbeatRequest.Application("orders-api", "prod", "instance-1"));
    }

    private static IngestHeartbeatResponse response() {
        return new IngestHeartbeatResponse(
                "received",
                PortalIngestValidationFixture.VERIFIED_PROJECT.projectId(),
                OffsetDateTime.parse("2026-05-24T08:31:00Z"),
                List.of("1.0"),
                "valid",
                "received",
                new IngestHeartbeatResponse.IngestBoundary(
                        OffsetDateTime.parse("2026-05-24T08:30:30Z"),
                        "accepted_bucket"),
                "Starter heartbeat was received. Accepted bucket freshness is reported separately.");
    }

    private static IngestHeartbeatReceipt receipt() {
        return new IngestHeartbeatReceipt(
                "received",
                PortalIngestValidationFixture.VERIFIED_PROJECT.projectId(),
                OffsetDateTime.parse("2026-05-24T08:31:00Z"),
                List.of("1.0"),
                "valid",
                "received",
                new IngestHeartbeatReceipt.IngestBoundary(
                        OffsetDateTime.parse("2026-05-24T08:30:30Z"),
                        "accepted_bucket"),
                "Starter heartbeat was received. Accepted bucket freshness is reported separately.");
    }
}
