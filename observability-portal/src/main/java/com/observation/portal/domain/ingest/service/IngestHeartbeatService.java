package com.observation.portal.domain.ingest.service;

import com.observation.portal.domain.bucket.repository.MetricBucketRepository;
import com.observation.portal.domain.catalog.entity.ApplicationEntity;
import com.observation.portal.domain.catalog.repository.ApplicationRepository;
import com.observation.portal.domain.ingest.model.IngestHeartbeatRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * starter heartbeat를 project key와 metadata shape 기준으로 검증하는 service다.
 *
 * <p>heartbeat는 accepted bucket 저장, catalog upsert, state 계산을 수행하지 않고 accepted bucket timestamp만 분리 조회한다.</p>
 */
@Service
public class IngestHeartbeatService {

    private static final String SUPPORTED_SCHEMA_VERSION = "1.0";
    private static final String STATUS_RECEIVED = "received";
    private static final String STATUS_VALID = "valid";
    private static final String STATUS_SOURCE_ACCEPTED_BUCKET = "accepted_bucket";
    private static final Pattern ISO_CONTROL = Pattern.compile("\\p{Cntrl}");

    private final ProjectKeyVerificationService projectKeyVerificationService;
    private final ApplicationRepository applicationRepository;
    private final MetricBucketRepository metricBucketRepository;
    private final Clock clock;

    /**
     * project key 검증, catalog read-only lookup, bucket timestamp lookup, UTC clock을 연결한다.
     */
    public IngestHeartbeatService(
            ProjectKeyVerificationService projectKeyVerificationService,
            ApplicationRepository applicationRepository,
            MetricBucketRepository metricBucketRepository,
            Clock clock) {
        this.projectKeyVerificationService = Objects.requireNonNull(
                projectKeyVerificationService,
                "projectKeyVerificationService must not be null");
        this.applicationRepository = Objects.requireNonNull(
                applicationRepository,
                "applicationRepository must not be null");
        this.metricBucketRepository = Objects.requireNonNull(
                metricBucketRepository,
                "metricBucketRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * heartbeat request를 검증하고 response contract로 변환한다.
     */
    @Transactional(readOnly = true)
    public IngestHeartbeatResult receive(String projectKeyHeader, IngestHeartbeatRequest request) {
        ProjectKeyVerificationResult projectKeyResult = projectKeyVerificationService.verify(projectKeyHeader);
        if (!projectKeyResult.isVerified()) {
            return IngestHeartbeatResult.unauthorized();
        }

        HeartbeatValidator validator = new HeartbeatValidator(request);
        List<IngestValidationError> errors = validator.validate();
        if (!errors.isEmpty()) {
            return IngestHeartbeatResult.invalid(errors);
        }

        VerifiedProject project = projectKeyResult.verifiedProject().orElseThrow();
        IngestHeartbeatRequest.Application application = request.application();
        Optional<OffsetDateTime> lastAcceptedBucketAt = findLastAcceptedBucketAt(project, application);
        return IngestHeartbeatResult.received(new IngestHeartbeatReceipt(
                STATUS_RECEIVED,
                project.projectId(),
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                List.of(SUPPORTED_SCHEMA_VERSION),
                STATUS_VALID,
                STATUS_RECEIVED,
                new IngestHeartbeatReceipt.IngestBoundary(
                        lastAcceptedBucketAt.orElse(null),
                        STATUS_SOURCE_ACCEPTED_BUCKET),
                message(lastAcceptedBucketAt)));
    }

    private Optional<OffsetDateTime> findLastAcceptedBucketAt(
            VerifiedProject project,
            IngestHeartbeatRequest.Application application) {
        return applicationRepository.findByProjectIdAndNameAndEnvironment(
                        project.projectId(),
                        application.name(),
                        application.environment())
                .map(ApplicationEntity::id)
                .flatMap(metricBucketRepository::findLatestBucketEndUtcByApplicationId);
    }

    private static String message(Optional<OffsetDateTime> lastAcceptedBucketAt) {
        if (lastAcceptedBucketAt.isEmpty()) {
            return "Starter heartbeat was received. No metric bucket has been accepted yet.";
        }
        return "Starter heartbeat was received. Accepted bucket freshness is reported separately.";
    }

    private static final class HeartbeatValidator {

        private final IngestHeartbeatRequest request;
        private final List<IngestValidationError> errors = new ArrayList<>();

        private HeartbeatValidator(IngestHeartbeatRequest request) {
            this.request = request;
        }

        private List<IngestValidationError> validate() {
            if (request == null) {
                add("required", "request", "request body is required");
                return List.copyOf(errors);
            }
            validateSchemaVersion();
            validateText(request.starterVersion(), "starterVersion");
            validateHeartbeat(request.heartbeat());
            validateApplication(request.application());
            return List.copyOf(errors);
        }

        private void validateSchemaVersion() {
            if (!SUPPORTED_SCHEMA_VERSION.equals(request.schemaVersion())) {
                add("unsupported_schema_version", "schemaVersion", "schemaVersion must be 1.0");
            }
        }

        private void validateHeartbeat(IngestHeartbeatRequest.Heartbeat heartbeat) {
            if (heartbeat == null) {
                add("required", "heartbeat", "heartbeat is required");
                return;
            }
            validateUtcInstant(heartbeat.sentAtUtc(), "heartbeat.sentAtUtc");
            if (heartbeat.sequence() == null) {
                add("required", "heartbeat.sequence", "heartbeat sequence is required");
            } else if (heartbeat.sequence() < 0) {
                add("invalid_heartbeat", "heartbeat.sequence", "heartbeat sequence must not be negative");
            }
            if (heartbeat.intervalSeconds() == null) {
                add("required", "heartbeat.intervalSeconds", "heartbeat intervalSeconds is required");
            } else if (heartbeat.intervalSeconds() <= 0) {
                add("invalid_heartbeat", "heartbeat.intervalSeconds", "heartbeat intervalSeconds must be positive");
            }
        }

        private void validateApplication(IngestHeartbeatRequest.Application application) {
            if (application == null) {
                add("required", "application", "application is required");
                return;
            }
            validateText(application.name(), "application.name");
            validateText(application.environment(), "application.environment");
            validateText(application.instance(), "application.instance");
        }

        private void validateUtcInstant(String value, String field) {
            if (!hasText(value)) {
                add("required", field, "UTC instant is required");
                return;
            }
            if (hasLeadingOrTrailingWhitespace(value) || containsControlCharacter(value)) {
                add("invalid_heartbeat", field, "timestamp contains unsupported characters");
                return;
            }
            if (!value.endsWith("Z")) {
                add("invalid_heartbeat", field, "timestamp must be UTC");
                return;
            }
            try {
                Instant.parse(value);
            } catch (RuntimeException ignored) {
                add("invalid_heartbeat", field, "timestamp must be an ISO-8601 UTC instant");
            }
        }

        private void validateText(String value, String field) {
            if (!hasText(value)) {
                add("required", field, field + " is required");
            } else if (hasLeadingOrTrailingWhitespace(value) || containsControlCharacter(value)) {
                add("invalid_metadata", field, field + " contains unsupported characters");
            }
        }

        private void add(String code, String field, String message) {
            errors.add(IngestValidationError.of(code, field, message));
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }

        private static boolean containsControlCharacter(String value) {
            return ISO_CONTROL.matcher(value).find();
        }

        private static boolean hasLeadingOrTrailingWhitespace(String value) {
            return !value.isEmpty()
                    && (isWhitespace(value.charAt(0)) || isWhitespace(value.charAt(value.length() - 1)));
        }

        private static boolean isWhitespace(char value) {
            return Character.isWhitespace(value) || Character.isSpaceChar(value);
        }
    }
}
