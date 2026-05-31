package com.observation.portal.domain.ingest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.observation.portal.domain.bucket.model.AcceptedMetricBucketReceipt;
import com.observation.portal.domain.bucket.model.AcceptedMetricBucketWriteCommand;
import com.observation.portal.domain.bucket.repository.MetricBucketRepository;
import com.observation.portal.domain.ingest.dto.IngestErrorResponse;
import com.observation.portal.domain.ingest.model.IngestEnvelopeRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestAcceptanceServiceTest {

    private static final AcceptedMetricBucketReceipt ACCEPTED_RECEIPT = new AcceptedMetricBucketReceipt(
            UUID.fromString("00000000-0000-0000-0000-00000000a331"),
            OffsetDateTime.parse("2026-05-08T01:00:31Z"));

    @Test
    void acceptsStory25GoldenEnvelopeAndIdempotencyKeyAfterProjectVerification() throws Exception {
        ProjectKeyVerificationService projectKeyVerificationService = verifiedProjectKeyService();
        MetricBucketRepository metricBucketRepository = acceptingRepository();
        IngestAcceptanceService service = newService(projectKeyVerificationService, metricBucketRepository);

        IngestAcceptanceResult result = service.accept(
                PortalIngestValidationFixture.PROJECT_KEY_HEADER,
                PortalIngestValidationFixture.IDEMPOTENCY_KEY,
                PortalIngestValidationFixture.goldenRequest());

        assertThat(result.isAccepted()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.acceptedCandidate()).hasValueSatisfying(candidate -> {
            assertThat(candidate.verifiedProject()).isEqualTo(PortalIngestValidationFixture.VERIFIED_PROJECT);
            assertThat(candidate.idempotencyKey()).isEqualTo(PortalIngestValidationFixture.IDEMPOTENCY_KEY);
            assertThat(candidate.payload().application().name()).isEqualTo("orders-api");
        });
        assertThat(result.acceptedReceipt()).hasValue(ACCEPTED_RECEIPT);
        verify(projectKeyVerificationService).verify(PortalIngestValidationFixture.PROJECT_KEY_HEADER);

        ArgumentCaptor<AcceptedMetricBucketWriteCommand> commandCaptor =
                ArgumentCaptor.forClass(AcceptedMetricBucketWriteCommand.class);
        verify(metricBucketRepository).insert(commandCaptor.capture());
        assertThat(commandCaptor.getValue()).satisfies(command -> {
            assertThat(command.projectId()).isEqualTo(PortalIngestValidationFixture.VERIFIED_PROJECT.projectId());
            assertThat(command.payloadHash()).hasSize(64);
            assertThat(command.acceptedAt()).isNotNull();
        });
    }

    @Test
    void rejectsExistingProjectIdempotencyKeyWithoutHashingOrInsert() throws Exception {
        ProjectKeyVerificationService projectKeyVerificationService = verifiedProjectKeyService();
        MetricBucketRepository metricBucketRepository = mock(MetricBucketRepository.class);
        IngestPayloadHasher payloadHasher = mock(IngestPayloadHasher.class);
        when(metricBucketRepository.findByProjectIdAndIdempotencyKey(
                PortalIngestValidationFixture.VERIFIED_PROJECT.projectId(),
                PortalIngestValidationFixture.IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(ACCEPTED_RECEIPT));
        IngestAcceptanceService service = newService(projectKeyVerificationService, metricBucketRepository, payloadHasher);

        IngestAcceptanceResult result = service.accept(
                PortalIngestValidationFixture.PROJECT_KEY_HEADER,
                PortalIngestValidationFixture.IDEMPOTENCY_KEY,
                PortalIngestValidationFixture.goldenRequest());

        assertDuplicateIdempotencyKey(result);
        verify(metricBucketRepository).findByProjectIdAndIdempotencyKey(
                PortalIngestValidationFixture.VERIFIED_PROJECT.projectId(),
                PortalIngestValidationFixture.IDEMPOTENCY_KEY);
        verifyNoInteractions(payloadHasher);
        verify(metricBucketRepository, never()).insert(any(AcceptedMetricBucketWriteCommand.class));
    }

    @Test
    void rejectsSameKeyDifferentPayloadAsDuplicateKeyWithoutPayloadComparison() throws Exception {
        ProjectKeyVerificationService projectKeyVerificationService = verifiedProjectKeyService();
        MetricBucketRepository metricBucketRepository = mock(MetricBucketRepository.class);
        IngestPayloadHasher payloadHasher = mock(IngestPayloadHasher.class);
        when(metricBucketRepository.findByProjectIdAndIdempotencyKey(
                PortalIngestValidationFixture.VERIFIED_PROJECT.projectId(),
                PortalIngestValidationFixture.IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(ACCEPTED_RECEIPT));
        IngestAcceptanceService service = newService(projectKeyVerificationService, metricBucketRepository, payloadHasher);

        IngestAcceptanceResult result = service.accept(
                PortalIngestValidationFixture.PROJECT_KEY_HEADER,
                PortalIngestValidationFixture.IDEMPOTENCY_KEY,
                PortalIngestValidationFixture.requestWith(
                        root -> ((ObjectNode) root.get("summary")).put("requestCount", 4)));

        assertDuplicateIdempotencyKey(result);
        verifyNoInteractions(payloadHasher);
        verify(metricBucketRepository, never()).insert(any(AcceptedMetricBucketWriteCommand.class));
    }

    @Test
    void duplicateIngestGuardDoesNotCreateRecoveryOrDashboardRefreshMeaning() throws Exception {
        ProjectKeyVerificationService projectKeyVerificationService = verifiedProjectKeyService();
        MetricBucketRepository metricBucketRepository = mock(MetricBucketRepository.class);
        IngestPayloadHasher payloadHasher = mock(IngestPayloadHasher.class);
        when(metricBucketRepository.findByProjectIdAndIdempotencyKey(
                PortalIngestValidationFixture.VERIFIED_PROJECT.projectId(),
                PortalIngestValidationFixture.IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(ACCEPTED_RECEIPT));
        IngestAcceptanceService service = newService(projectKeyVerificationService, metricBucketRepository, payloadHasher);

        IngestAcceptanceResult result = service.accept(
                PortalIngestValidationFixture.PROJECT_KEY_HEADER,
                PortalIngestValidationFixture.IDEMPOTENCY_KEY,
                PortalIngestValidationFixture.goldenRequest());
        IngestErrorResponse response = IngestErrorResponse.duplicateIdempotencyKey();

        assertDuplicateIdempotencyKey(result);
        assertThat(response.error()).isEqualTo("duplicate_idempotency_key");
        assertThat(response.message()).doesNotContain(
                "dashboard",
                "snapshot",
                "refresh",
                "recovery",
                "recovered",
                "data loss",
                "복구",
                "장애 해결");
        verifyNoInteractions(payloadHasher);
        verify(metricBucketRepository, never()).insert(any(AcceptedMetricBucketWriteCommand.class));
    }

    @Test
    void mapsInsertUniqueViolationRaceToDuplicateIdempotencyKeyReject() throws Exception {
        ProjectKeyVerificationService projectKeyVerificationService = verifiedProjectKeyService();
        MetricBucketRepository metricBucketRepository = mock(MetricBucketRepository.class);
        when(metricBucketRepository.findByProjectIdAndIdempotencyKey(
                PortalIngestValidationFixture.VERIFIED_PROJECT.projectId(),
                PortalIngestValidationFixture.IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
        when(metricBucketRepository.insert(any(AcceptedMetricBucketWriteCommand.class)))
                .thenThrow(new DataIntegrityViolationException("uk_buckets_project_idempotency_key"));
        IngestAcceptanceService service = newService(projectKeyVerificationService, metricBucketRepository);

        IngestAcceptanceResult result = service.accept(
                PortalIngestValidationFixture.PROJECT_KEY_HEADER,
                PortalIngestValidationFixture.IDEMPOTENCY_KEY,
                PortalIngestValidationFixture.goldenRequest());

        assertDuplicateIdempotencyKey(result);
        verify(metricBucketRepository).insert(any(AcceptedMetricBucketWriteCommand.class));
    }

    @Test
    void mapsInvalidProjectKeyToUnauthorizedAndSkipsPayloadValidation() {
        ProjectKeyVerificationService projectKeyVerificationService = mock(ProjectKeyVerificationService.class);
        when(projectKeyVerificationService.verify(PortalIngestValidationFixture.PROJECT_KEY_HEADER))
                .thenReturn(ProjectKeyVerificationResult.unauthorized());
        MetricBucketRepository metricBucketRepository = acceptingRepository();
        IngestAcceptanceService service = newService(projectKeyVerificationService, metricBucketRepository);

        IngestAcceptanceResult result = service.accept(
                PortalIngestValidationFixture.PROJECT_KEY_HEADER,
                "not-a-valid-idempotency-key",
                null);

        assertThat(result.isUnauthorized()).isTrue();
        assertThat(result.acceptedCandidate()).isEmpty();
        assertThat(result.acceptedReceipt()).isEmpty();
        assertThat(result.errors()).isEmpty();
        verify(projectKeyVerificationService).verify(PortalIngestValidationFixture.PROJECT_KEY_HEADER);
        verifyNoInteractions(metricBucketRepository);
    }

    @Test
    void rejectsUnsupportedSchemaVersion() throws Exception {
        IngestAcceptanceResult result = accept(root -> root.put("schemaVersion", "1.1"));

        assertInvalid(result, "schemaVersion", "unsupported_schema_version");
    }

    @Test
    void rejectsInvalidBucketBoundaryAndDuration() throws Exception {
        assertInvalid(
                accept(root -> ((ObjectNode) root.get("bucket")).put("startUtc", "2026-05-08T01:00:01Z")),
                "bucket.startUtc",
                "invalid_bucket_boundary");
        assertInvalid(
                accept(root -> ((ObjectNode) root.get("bucket")).put("startUtc", "2026-05-08T10:00:00+09:00")),
                "bucket.startUtc",
                "invalid_bucket_timestamp");
        assertInvalid(
                accept(root -> ((ObjectNode) root.get("bucket")).put("endUtc", "2026-05-08T01:00:31Z")),
                "bucket",
                "invalid_bucket_interval");
        assertInvalid(
                accept(root -> ((ObjectNode) root.get("bucket")).put("durationSeconds", 60)),
                "bucket.durationSeconds",
                "invalid_bucket_duration");
    }

    @Test
    void rejectsBlankApplicationIdentityValues() throws Exception {
        IngestAcceptanceResult result = accept(root -> {
            ObjectNode application = (ObjectNode) root.get("application");
            application.put("name", " ");
            application.put("environment", "");
            application.put("instance", "  ");
        });

        assertThat(result.errors())
                .extracting(IngestValidationError::field)
                .contains("application.name", "application.environment", "application.instance");
    }

    @Test
    void rejectsApplicationIdentityRawWhitespaceOrControlCharacters() throws Exception {
        assertInvalid(
                accept(root -> ((ObjectNode) root.get("application")).put("name", "orders-api ")),
                "application.name",
                "invalid_identity");
        assertInvalid(
                accept(root -> ((ObjectNode) root.get("application")).put("environment", "prod\n")),
                "application.environment",
                "invalid_identity");
        assertInvalid(
                accept(root -> ((ObjectNode) root.get("application")).put("instance", "\torders-api-7f9c9c8c9d-x2p4k")),
                "application.instance",
                "invalid_identity");
    }

    @Test
    void rejectsBucketTimestampRawWhitespaceOrControlCharacters() throws Exception {
        assertInvalid(
                accept(root -> ((ObjectNode) root.get("bucket")).put("startUtc", "2026-05-08T01:00:00Z\n")),
                "bucket.startUtc",
                "invalid_bucket_timestamp");
        assertInvalid(
                accept(root -> ((ObjectNode) root.get("bucket")).put("endUtc", " 2026-05-08T01:00:30Z")),
                "bucket.endUtc",
                "invalid_bucket_timestamp");
    }

    @Test
    void rejectsInvalidCountsHistogramsAndRuntimeRatios() throws Exception {
        assertInvalid(
                accept(root -> ((ObjectNode) root.get("summary")).put("requestCount", -1)),
                "summary.requestCount",
                "invalid_count");
        assertInvalid(
                accept(root -> ((ObjectNode) root.get("summary")).put("errorCount", 4)),
                "summary.errorCount",
                "invalid_count");
        assertInvalid(
                accept(root -> ((ArrayNode) root.get("summary").get("httpServerDurationBuckets")).removeAll()),
                "summary.httpServerDurationBuckets",
                "invalid_histogram");
        assertInvalid(
                accept(root -> {
                    ArrayNode buckets = (ArrayNode) root.get("endpoints").get(0).get("durationBuckets");
                    ((ObjectNode) buckets.get(2)).put("count", 1);
                }),
                "endpoints[0].durationBuckets[2].count",
                "invalid_histogram");
        assertInvalid(
                accept(root -> ((ArrayNode) root.get("endpoints").get(0).get("durationBuckets")).removeAll()),
                "endpoints[0].durationBuckets",
                "invalid_histogram");
        assertInvalid(
                accept(root -> ((ObjectNode) root.get("summary").get("jvm")).put("cpuUsage", 1.5d)),
                "summary.jvm.cpuUsage",
                "invalid_ratio");
        assertInvalid(
                accept(root -> ((ObjectNode) root.get("summary").get("datasource")).put("poolUsageRatio", -0.1d)),
                "summary.datasource.poolUsageRatio",
                "invalid_ratio");
    }

    @Test
    void rejectsEndpointMethodAndNonNormalizedRoutes() throws Exception {
        assertInvalid(
                accept(root -> ((ObjectNode) root.get("endpoints").get(0)).put("method", "get")),
                "endpoints[0].method",
                "invalid_endpoint_method");
        assertInvalid(
                accept(root -> ((ObjectNode) root.get("endpoints").get(0)).put("method", " GET ")),
                "endpoints[0].method",
                "invalid_endpoint_method");
        assertInvalid(
                accept(root -> ((ObjectNode) root.get("endpoints").get(0)).put("method", "GET\n")),
                "endpoints[0].method",
                "invalid_endpoint_method");
        assertInvalid(
                accept(root -> ((ObjectNode) root.get("endpoints").get(0)).put("route", "/orders/12345")),
                "endpoints[0].route",
                "invalid_endpoint_route");
        assertInvalid(
                accept(root -> ((ObjectNode) root.get("endpoints").get(0)).put(
                        "route",
                        "/orders/550e8400-e29b-41d4-a716-446655440000")),
                "endpoints[0].route",
                "invalid_endpoint_route");
        assertInvalid(
                accept(root -> ((ObjectNode) root.get("endpoints").get(0)).put("route", "/assets/deadbeef")),
                "endpoints[0].route",
                "invalid_endpoint_route");
        assertInvalid(
                accept(root -> ((ObjectNode) root.get("endpoints").get(0)).put("route", "/orders/{orderId}?debug=true")),
                "endpoints[0].route",
                "invalid_endpoint_route");
        assertInvalid(
                accept(root -> ((ObjectNode) root.get("endpoints").get(0)).put(
                        "route",
                        "https://example.test/orders/{orderId}")),
                "endpoints[0].route",
                "invalid_endpoint_route");
        assertInvalid(
                accept(root -> ((ObjectNode) root.get("endpoints").get(0)).put("route", "/orders/")),
                "endpoints[0].route",
                "invalid_endpoint_route");
        assertInvalid(
                accept(root -> ((ObjectNode) root.get("endpoints").get(0)).put("route", "/orders//{orderId}")),
                "endpoints[0].route",
                "invalid_endpoint_route");
        assertInvalid(
                accept(root -> ((ObjectNode) root.get("endpoints").get(0)).put("route", "/orders/{order-id}")),
                "endpoints[0].route",
                "invalid_endpoint_route");
        assertInvalid(
                accept(root -> ((ObjectNode) root.get("endpoints").get(0)).put("route", " /orders/{orderId}")),
                "endpoints[0].route",
                "invalid_endpoint_route");
        assertInvalid(
                accept(root -> ((ObjectNode) root.get("endpoints").get(0)).put("route", "/orders/{orderId}\n")),
                "endpoints[0].route",
                "invalid_endpoint_route");
    }

    @Test
    void acceptsCanonicalEndpointMethodAndRouteWithoutRewriting() throws Exception {
        IngestAcceptanceResult result = accept(root -> {
            ObjectNode endpoint = (ObjectNode) root.get("endpoints").get(0);
            endpoint.put("method", "GET");
            endpoint.put("route", "/orders/{orderId}");
        });

        assertThat(result.isAccepted()).isTrue();
        assertThat(result.acceptedCandidate()).hasValueSatisfying(candidate -> {
            IngestEnvelopeRequest.Endpoint endpoint = candidate.payload().endpoints().get(0);
            assertThat(endpoint.method()).isEqualTo("GET");
            assertThat(endpoint.route()).isEqualTo("/orders/{orderId}");
        });
    }

    @Test
    void acceptsUnknownRouteFallbackWhenEverythingElseIsValid() throws Exception {
        IngestAcceptanceResult result = accept(root -> ((ObjectNode) root.get("endpoints").get(0)).put("route", "UNKNOWN"));

        assertThat(result.isAccepted()).isTrue();
    }

    @Test
    void acceptsAndPreservesStarterCanonicalLocalPercentiles() throws Exception {
        ProjectKeyVerificationService projectKeyVerificationService = verifiedProjectKeyService();
        MetricBucketRepository metricBucketRepository = acceptingRepository();
        IngestAcceptanceService service = newService(projectKeyVerificationService, metricBucketRepository);
        IngestEnvelopeRequest request = PortalIngestValidationFixture.requestWithLocalPercentiles();

        IngestAcceptanceResult result = service.accept(
                PortalIngestValidationFixture.PROJECT_KEY_HEADER,
                PortalIngestValidationFixture.IDEMPOTENCY_KEY,
                request);

        assertThat(result.isAccepted()).isTrue();
        assertThat(result.acceptedCandidate()).hasValueSatisfying(candidate -> {
            IngestEnvelopeRequest.LocalPercentiles localPercentiles =
                    candidate.payload().summary().localPercentiles();
            assertThat(localPercentiles.scope()).isEqualTo("instance_bucket");
            assertThat(localPercentiles.source()).isEqualTo("starter_local");
            assertThat(localPercentiles.mergeable()).isFalse();
        });

        ArgumentCaptor<AcceptedMetricBucketWriteCommand> commandCaptor =
                ArgumentCaptor.forClass(AcceptedMetricBucketWriteCommand.class);
        verify(metricBucketRepository).insert(commandCaptor.capture());
        assertThat(commandCaptor.getValue().localPercentiles()).satisfies(localPercentiles -> {
            assertThat(localPercentiles.scope()).isEqualTo("instance_bucket");
            assertThat(localPercentiles.source()).isEqualTo("starter_local");
            assertThat(localPercentiles.p95Ms()).isEqualTo(250L);
            assertThat(localPercentiles.p99Ms()).isEqualTo(1000L);
            assertThat(localPercentiles.mergeable()).isFalse();
        });
    }

    @Test
    void rejectsInvalidLocalPercentilesWithoutReopeningExistingEnvelopeFields() throws Exception {
        IngestAcceptanceResult result = accept(root -> {
            ObjectNode localPercentiles = PortalIngestValidationFixture.addValidLocalPercentiles(root);
            localPercentiles.put("scope", "application_window");
            localPercentiles.put("source", "histogram_bucket_distribution");
            localPercentiles.put("bucketStartUtc", "2026-05-08T01:00:30Z");
            localPercentiles.put("requestCount", 2);
            localPercentiles.remove("p95Ms");
            localPercentiles.put("p99Ms", 100L);
            localPercentiles.put("mergeable", true);
        });

        assertThat(result.isInvalidRequest()).isTrue();
        assertThat(result.errors())
                .extracting(IngestValidationError::field)
                .contains(
                        "summary.localPercentiles.scope",
                        "summary.localPercentiles.source",
                        "summary.localPercentiles.bucketStartUtc",
                        "summary.localPercentiles.requestCount",
                        "summary.localPercentiles.p95Ms",
                        "summary.localPercentiles.mergeable");
    }

    @Test
    void rejectsMalformedOrPayloadMismatchedIdempotencyKey() throws Exception {
        IngestEnvelopeRequest request = PortalIngestValidationFixture.goldenRequest();

        assertInvalid(accept("project-123:orders-api:prod:orders-api-7f9c9c8c9d-x2p4k", request),
                "Idempotency-Key", "invalid_idempotency_key");
        assertInvalid(accept("project-123:orders-api:prod:orders-api-7f9c9c8c9d-x2p4k:20260508T010000Z\u0001", request),
                "Idempotency-Key", "invalid_idempotency_key");
        assertInvalid(accept(" project-123:orders-api:prod:orders-api-7f9c9c8c9d-x2p4k:20260508T010000Z", request),
                "Idempotency-Key", "invalid_idempotency_key");
        assertInvalid(accept("project-123:orders-api:prod:orders-api-7f9c9c8c9d-x2p4k:20260508T010000Z ", request),
                "Idempotency-Key", "invalid_idempotency_key");
        assertInvalid(accept("project-123:orders-api:stage:orders-api-7f9c9c8c9d-x2p4k:20260508T010000Z", request),
                "Idempotency-Key.environment", "idempotency_payload_mismatch");
        assertInvalid(accept("project-123:orders-api:prod:orders-api-7f9c9c8c9d-x2p4k:20260508T010030Z", request),
                "Idempotency-Key.bucketStartUtc", "idempotency_payload_mismatch");
        assertInvalid(accept("project-123:orders:api:prod:orders-api-7f9c9c8c9d-x2p4k:20260508T010000Z", request),
                "Idempotency-Key", "invalid_idempotency_key");
    }

    @Test
    void invalidResultsDoNotRetainRawRouteOrProjectKeyValues() throws Exception {
        IngestAcceptanceResult result = accept(root -> ((ObjectNode) root.get("endpoints").get(0)).put(
                "route",
                "/orders/12345?token=secret"));

        assertThat(result.isInvalidRequest()).isTrue();
        assertThat(result.toString()).doesNotContain("/orders/12345", "token=secret", PortalIngestValidationFixture.PROJECT_KEY_HEADER);
        assertThat(result.errors().toString()).doesNotContain("/orders/12345", "token=secret", PortalIngestValidationFixture.PROJECT_KEY_HEADER);
    }

    private static IngestAcceptanceResult accept(Consumer<ObjectNode> mutation) throws Exception {
        return accept(PortalIngestValidationFixture.IDEMPOTENCY_KEY, PortalIngestValidationFixture.requestWith(mutation));
    }

    private static IngestAcceptanceResult accept(String idempotencyKey, IngestEnvelopeRequest request) {
        IngestAcceptanceService service = newService(verifiedProjectKeyService(), acceptingRepository());
        return service.accept(PortalIngestValidationFixture.PROJECT_KEY_HEADER, idempotencyKey, request);
    }

    private static IngestAcceptanceService newService(
            ProjectKeyVerificationService projectKeyVerificationService,
            MetricBucketRepository metricBucketRepository) {
        return newService(
                projectKeyVerificationService,
                metricBucketRepository,
                new IngestPayloadHasher(new ObjectMapper()));
    }

    private static IngestAcceptanceService newService(
            ProjectKeyVerificationService projectKeyVerificationService,
            MetricBucketRepository metricBucketRepository,
            IngestPayloadHasher payloadHasher) {
        return new IngestAcceptanceService(
                projectKeyVerificationService,
                metricBucketRepository,
                payloadHasher);
    }

    private static MetricBucketRepository acceptingRepository() {
        MetricBucketRepository metricBucketRepository = mock(MetricBucketRepository.class);
        when(metricBucketRepository.findByProjectIdAndIdempotencyKey(any(UUID.class), any(String.class)))
                .thenReturn(Optional.empty());
        when(metricBucketRepository.insert(any(AcceptedMetricBucketWriteCommand.class))).thenReturn(ACCEPTED_RECEIPT);
        return metricBucketRepository;
    }

    private static ProjectKeyVerificationService verifiedProjectKeyService() {
        ProjectKeyVerificationService projectKeyVerificationService = mock(ProjectKeyVerificationService.class);
        when(projectKeyVerificationService.verify(PortalIngestValidationFixture.PROJECT_KEY_HEADER))
                .thenReturn(ProjectKeyVerificationResult.verified(PortalIngestValidationFixture.VERIFIED_PROJECT));
        return projectKeyVerificationService;
    }

    private static void assertInvalid(IngestAcceptanceResult result, String field, String code) {
        assertThat(result.isInvalidRequest()).isTrue();
        assertThat(result.acceptedCandidate()).isEmpty();
        assertThat(result.acceptedReceipt()).isEmpty();
        assertThat(result.errors())
                .anySatisfy(error -> {
                    assertThat(error.field()).isEqualTo(field);
                    assertThat(error.code()).isEqualTo(code);
                });
    }

    private static void assertDuplicateIdempotencyKey(IngestAcceptanceResult result) {
        assertThat(result.isDuplicateIdempotencyKey()).isTrue();
        assertThat(result.isAccepted()).isFalse();
        assertThat(result.acceptedCandidate()).isEmpty();
        assertThat(result.acceptedReceipt()).isEmpty();
        assertThat(result.errors()).isEmpty();
    }
}
