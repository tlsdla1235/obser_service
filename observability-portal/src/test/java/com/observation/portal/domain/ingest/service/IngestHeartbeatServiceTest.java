package com.observation.portal.domain.ingest.service;

import com.observation.portal.domain.bucket.model.AcceptedMetricBucketWriteCommand;
import com.observation.portal.domain.bucket.repository.MetricBucketRepository;
import com.observation.portal.domain.catalog.entity.ApplicationEntity;
import com.observation.portal.domain.catalog.repository.ApplicationRepository;
import com.observation.portal.domain.ingest.model.IngestHeartbeatRequest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IngestHeartbeatServiceTest {

    private static final String PROJECT_KEY = "pk_live.secret";
    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-00000000a441");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-24T08:31:00Z"), ZoneOffset.UTC);

    @Test
    void receivesValidHeartbeatAndSeparatesAcceptedBucketBoundary() {
        ProjectKeyVerificationService projectKeyVerificationService = verifiedProjectKeyService();
        ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
        MetricBucketRepository metricBucketRepository = mock(MetricBucketRepository.class);
        when(applicationRepository.findByProjectIdAndNameAndEnvironment(
                PortalIngestValidationFixture.VERIFIED_PROJECT.projectId(),
                "orders-api",
                "prod"))
                .thenReturn(Optional.of(application()));
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(OffsetDateTime.parse("2026-05-24T08:30:30Z")));
        IngestHeartbeatService service = newService(
                projectKeyVerificationService,
                applicationRepository,
                metricBucketRepository);

        IngestHeartbeatResult result = service.receive(PROJECT_KEY, validRequest());

        assertThat(result.isReceived()).isTrue();
        assertThat(result.receipt()).hasValueSatisfying(receipt -> {
            assertThat(receipt.status()).isEqualTo("received");
            assertThat(receipt.projectId()).isEqualTo(PortalIngestValidationFixture.VERIFIED_PROJECT.projectId());
            assertThat(receipt.serverTimeUtc()).isEqualTo(OffsetDateTime.parse("2026-05-24T08:31:00Z"));
            assertThat(receipt.supportedIngestSchemaVersions()).containsExactly("1.0");
            assertThat(receipt.metadataStatus()).isEqualTo("valid");
            assertThat(receipt.heartbeatStatus()).isEqualTo("received");
            assertThat(receipt.ingestBoundary().lastAcceptedBucketAt())
                    .isEqualTo(OffsetDateTime.parse("2026-05-24T08:30:30Z"));
            assertThat(receipt.ingestBoundary().statusSource()).isEqualTo("accepted_bucket");
        });
        verify(projectKeyVerificationService).verify(PROJECT_KEY);
        verify(metricBucketRepository, never()).insert(any(AcceptedMetricBucketWriteCommand.class));
    }

    @Test
    void validHeartbeatWithoutCatalogRowReturnsNullAcceptedBucketBoundary() {
        ProjectKeyVerificationService projectKeyVerificationService = verifiedProjectKeyService();
        ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
        MetricBucketRepository metricBucketRepository = mock(MetricBucketRepository.class);
        when(applicationRepository.findByProjectIdAndNameAndEnvironment(
                PortalIngestValidationFixture.VERIFIED_PROJECT.projectId(),
                "orders-api",
                "prod"))
                .thenReturn(Optional.empty());
        IngestHeartbeatService service = newService(
                projectKeyVerificationService,
                applicationRepository,
                metricBucketRepository);

        IngestHeartbeatResult result = service.receive(PROJECT_KEY, validRequest());

        assertThat(result.isReceived()).isTrue();
        assertThat(result.receipt()).hasValueSatisfying(receipt -> {
            assertThat(receipt.ingestBoundary().lastAcceptedBucketAt()).isNull();
            assertThat(receipt.ingestBoundary().statusSource()).isEqualTo("accepted_bucket");
            assertThat(receipt.message()).contains("No metric bucket has been accepted yet");
        });
        verifyNoInteractions(metricBucketRepository);
    }

    @Test
    void invalidProjectKeyReturnsUnauthorizedBeforeShapeValidationOrRepositoryLookup() {
        ProjectKeyVerificationService projectKeyVerificationService = mock(ProjectKeyVerificationService.class);
        when(projectKeyVerificationService.verify(PROJECT_KEY)).thenReturn(ProjectKeyVerificationResult.unauthorized());
        ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
        MetricBucketRepository metricBucketRepository = mock(MetricBucketRepository.class);
        IngestHeartbeatService service = newService(
                projectKeyVerificationService,
                applicationRepository,
                metricBucketRepository);

        IngestHeartbeatResult result = service.receive(PROJECT_KEY, null);

        assertThat(result.isUnauthorized()).isTrue();
        assertThat(result.receipt()).isEmpty();
        assertThat(result.errors()).isEmpty();
        assertThat(result.toString()).doesNotContain(PROJECT_KEY);
        verifyNoInteractions(applicationRepository, metricBucketRepository);
    }

    @Test
    void invalidSchemaOrApplicationMetadataReturns400AndSkipsBucketLookup() {
        ProjectKeyVerificationService projectKeyVerificationService = verifiedProjectKeyService();
        ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
        MetricBucketRepository metricBucketRepository = mock(MetricBucketRepository.class);
        IngestHeartbeatService service = newService(
                projectKeyVerificationService,
                applicationRepository,
                metricBucketRepository);
        IngestHeartbeatRequest request = new IngestHeartbeatRequest(
                "1.1",
                "0.1.0-test",
                new IngestHeartbeatRequest.Heartbeat("2026-05-24T08:30:00Z", 1L, 30),
                new IngestHeartbeatRequest.Application("orders-api ", "", "instance-1\n"));

        IngestHeartbeatResult result = service.receive(PROJECT_KEY, request);

        assertThat(result.isInvalidRequest()).isTrue();
        assertThat(result.errors())
                .extracting(IngestValidationError::field)
                .contains(
                        "schemaVersion",
                        "application.name",
                        "application.environment",
                        "application.instance");
        verifyNoInteractions(applicationRepository, metricBucketRepository);
    }

    @Test
    void invalidHeartbeatShapeReturns400() {
        IngestHeartbeatService service = newService(
                verifiedProjectKeyService(),
                mock(ApplicationRepository.class),
                mock(MetricBucketRepository.class));
        IngestHeartbeatRequest request = new IngestHeartbeatRequest(
                "1.0",
                "0.1.0-test",
                new IngestHeartbeatRequest.Heartbeat("2026-05-24T08:30:00+09:00", -1L, 0),
                new IngestHeartbeatRequest.Application("orders-api", "prod", "instance-1"));

        IngestHeartbeatResult result = service.receive(PROJECT_KEY, request);

        assertThat(result.isInvalidRequest()).isTrue();
        assertThat(result.errors())
                .extracting(IngestValidationError::field)
                .contains(
                        "heartbeat.sentAtUtc",
                        "heartbeat.sequence",
                        "heartbeat.intervalSeconds");
    }

    private static IngestHeartbeatService newService(
            ProjectKeyVerificationService projectKeyVerificationService,
            ApplicationRepository applicationRepository,
            MetricBucketRepository metricBucketRepository) {
        return new IngestHeartbeatService(
                projectKeyVerificationService,
                applicationRepository,
                metricBucketRepository,
                CLOCK);
    }

    private static ProjectKeyVerificationService verifiedProjectKeyService() {
        ProjectKeyVerificationService projectKeyVerificationService = mock(ProjectKeyVerificationService.class);
        when(projectKeyVerificationService.verify(PROJECT_KEY))
                .thenReturn(ProjectKeyVerificationResult.verified(PortalIngestValidationFixture.VERIFIED_PROJECT));
        return projectKeyVerificationService;
    }

    private static IngestHeartbeatRequest validRequest() {
        return new IngestHeartbeatRequest(
                "1.0",
                "0.1.0-test",
                new IngestHeartbeatRequest.Heartbeat("2026-05-24T08:30:00Z", 1L, 30),
                new IngestHeartbeatRequest.Application("orders-api", "prod", "instance-1"));
    }

    private static ApplicationEntity application() {
        OffsetDateTime now = OffsetDateTime.parse("2026-05-24T08:30:30Z");
        return new ApplicationEntity(
                APPLICATION_ID,
                PortalIngestValidationFixture.VERIFIED_PROJECT.projectId(),
                "orders-api",
                "prod",
                "active",
                now,
                now,
                now,
                now);
    }
}
