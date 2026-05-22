package com.observation.portal.domain.ingest.service;

import com.observation.portal.domain.bucket.model.AcceptedMetricBucketReceipt;
import com.observation.portal.domain.bucket.model.AcceptedMetricBucketWriteCommand;
import com.observation.portal.domain.bucket.repository.MetricBucketRepository;
import com.observation.portal.domain.ingest.model.IngestEnvelopeRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * starter ingest envelope를 포털 계약 기준으로 다시 검증하는 acceptance service다.
 *
 * <p>project key 검증을 먼저 수행한 뒤 schemaVersion, UTC 30초 bucket, metric taxonomy,
 * normalized route, Idempotency-Key 일관성을 검증하고 첫 successful ingest를 persistence path에 연결한다.</p>
 */
@Service
public class IngestAcceptanceService {

    private static final String SUPPORTED_SCHEMA_VERSION = "1.0";
    private static final int BUCKET_DURATION_SECONDS = 30;
    private static final Pattern HEADER_SAFE_COMPONENT = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Pattern IDEMPOTENCY_BUCKET_START = Pattern.compile("[0-9]{8}T[0-9]{6}Z");
    private static final Pattern UUID_SEGMENT = Pattern.compile(
            "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern LONG_HEX_SEGMENT = Pattern.compile("(?i)[0-9a-f]{8,}");
    private static final Pattern LITERAL_ROUTE_SEGMENT = Pattern.compile("[A-Za-z0-9._~-]+");
    private static final String IDEMPOTENCY_UNIQUE_CONSTRAINT = "uk_buckets_project_idempotency_key";
    private static final DateTimeFormatter IDEMPOTENCY_BUCKET_START_FORMAT =
            DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'")
                    .withResolverStyle(ResolverStyle.STRICT);
    private static final Set<String> ALLOWED_METHODS = Set.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE", "UNKNOWN");

    private final ProjectKeyVerificationService projectKeyVerificationService;
    private final MetricBucketRepository metricBucketRepository;
    private final IngestPayloadHasher payloadHasher;

    /**
     * project key 검증, payload hash, bucket repository를 연결해 ingest acceptance service를 구성한다.
     */
    public IngestAcceptanceService(
            ProjectKeyVerificationService projectKeyVerificationService,
            MetricBucketRepository metricBucketRepository,
            IngestPayloadHasher payloadHasher) {
        this.projectKeyVerificationService = Objects.requireNonNull(
                projectKeyVerificationService,
                "projectKeyVerificationService must not be null");
        this.metricBucketRepository = Objects.requireNonNull(
                metricBucketRepository,
                "metricBucketRepository must not be null");
        this.payloadHasher = Objects.requireNonNull(payloadHasher, "payloadHasher must not be null");
    }

    /**
     * project key, Idempotency-Key, envelope payload를 검증하고 accepted/400/401/409 후보 결과를 반환한다.
     */
    public IngestAcceptanceResult accept(
            String projectKeyHeader,
            String idempotencyKeyHeader,
            IngestEnvelopeRequest request) {
        ProjectKeyVerificationResult projectKeyResult = projectKeyVerificationService.verify(projectKeyHeader);
        if (!projectKeyResult.isVerified()) {
            return IngestAcceptanceResult.unauthorized();
        }

        EnvelopeValidator validator = new EnvelopeValidator(request, idempotencyKeyHeader);
        List<IngestValidationError> errors = validator.validate();
        if (!errors.isEmpty()) {
            return IngestAcceptanceResult.invalid(errors);
        }

        ValidatedIngestCandidate candidate = new ValidatedIngestCandidate(
                projectKeyResult.verifiedProject().orElseThrow(),
                idempotencyKeyHeader,
                request);
        if (metricBucketRepository.findByProjectIdAndIdempotencyKey(
                candidate.verifiedProject().projectId(),
                candidate.idempotencyKey()).isPresent()) {
            return IngestAcceptanceResult.duplicateIdempotencyKey();
        }

        String payloadHash = payloadHasher.sha256(request);
        AcceptedMetricBucketWriteCommand command = AcceptedMetricBucketWriteCommand.from(
                candidate,
                payloadHash,
                OffsetDateTime.now(ZoneOffset.UTC));
        AcceptedMetricBucketReceipt receipt;
        try {
            receipt = metricBucketRepository.insert(command);
        } catch (DataIntegrityViolationException exception) {
            if (isIdempotencyUniqueViolation(exception)) {
                // MVP에서는 insert race 후 re-read/hash 비교 없이 명시적인 duplicate key reject로 수렴시킨다.
                return IngestAcceptanceResult.duplicateIdempotencyKey();
            }
            throw exception;
        }

        return IngestAcceptanceResult.accepted(candidate, receipt);
    }

    /**
     * Spring exception chain에서 idempotency unique constraint 이름을 찾아 insert race 여부를 좁혀 판단한다.
     */
    private static boolean isIdempotencyUniqueViolation(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            String message = cursor.getMessage();
            if (message != null && message.contains(IDEMPOTENCY_UNIQUE_CONSTRAINT)) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static final class EnvelopeValidator {

        private final IngestEnvelopeRequest request;
        private final String idempotencyKeyHeader;
        private final List<IngestValidationError> errors = new ArrayList<>();
        private Instant bucketStartUtc;

        private EnvelopeValidator(IngestEnvelopeRequest request, String idempotencyKeyHeader) {
            this.request = request;
            this.idempotencyKeyHeader = idempotencyKeyHeader;
        }

        private List<IngestValidationError> validate() {
            if (request == null) {
                add("required", "request", "request body is required");
                validateIdempotencyKey(null);
                return List.copyOf(errors);
            }

            validateSchemaVersion();
            validateApplication();
            validateBucket();
            validateSummary();
            validateEndpoints();
            validateIdempotencyKey(request.application());
            return List.copyOf(errors);
        }

        private void validateSchemaVersion() {
            if (!SUPPORTED_SCHEMA_VERSION.equals(request.schemaVersion())) {
                add("unsupported_schema_version", "schemaVersion", "schemaVersion must be 1.0");
            }
        }

        private void validateApplication() {
            IngestEnvelopeRequest.Application application = request.application();
            if (application == null) {
                add("required", "application", "application is required");
                return;
            }
            requireText(application.name(), "application.name");
            requireText(application.environment(), "application.environment");
            requireText(application.instance(), "application.instance");
        }

        private void validateBucket() {
            IngestEnvelopeRequest.Bucket bucket = request.bucket();
            if (bucket == null) {
                add("required", "bucket", "bucket is required");
                return;
            }

            Optional<Instant> startUtc = parseUtcInstant(bucket.startUtc(), "bucket.startUtc");
            Optional<Instant> endUtc = parseUtcInstant(bucket.endUtc(), "bucket.endUtc");
            startUtc.ifPresent(start -> bucketStartUtc = start);
            startUtc.ifPresent(start -> validateBoundary(start, "bucket.startUtc"));
            endUtc.ifPresent(end -> validateBoundary(end, "bucket.endUtc"));

            if (bucket.durationSeconds() == null || bucket.durationSeconds() != BUCKET_DURATION_SECONDS) {
                add("invalid_bucket_duration", "bucket.durationSeconds", "bucket duration must be 30 seconds");
            }

            if (startUtc.isPresent() && endUtc.isPresent()) {
                Duration interval = Duration.between(startUtc.orElseThrow(), endUtc.orElseThrow());
                if (!Duration.ofSeconds(BUCKET_DURATION_SECONDS).equals(interval)) {
                    add("invalid_bucket_interval", "bucket", "bucket interval must be exactly 30 seconds");
                }
            }
        }

        private void validateSummary() {
            IngestEnvelopeRequest.Summary summary = request.summary();
            if (summary == null) {
                add("required", "summary", "summary is required");
                return;
            }
            validateCounts("summary", summary.requestCount(), summary.errorCount());
            validateHistogram(
                    "summary.httpServerDurationBuckets",
                    summary.httpServerDurationBuckets(),
                    summary.requestCount());
            validateJvm(summary.jvm());
            validateDatasource(summary.datasource());
        }

        private void validateJvm(IngestEnvelopeRequest.Jvm jvm) {
            if (jvm == null) {
                return;
            }
            validateRatio("summary.jvm.cpuUsage", jvm.cpuUsage());
            validateRatio("summary.jvm.heapUsedRatio", jvm.heapUsedRatio());
        }

        private void validateDatasource(IngestEnvelopeRequest.Datasource datasource) {
            if (datasource == null) {
                return;
            }
            validateRatio("summary.datasource.poolUsageRatio", datasource.poolUsageRatio());
        }

        private void validateEndpoints() {
            List<IngestEnvelopeRequest.Endpoint> endpoints = request.endpoints();
            if (endpoints == null) {
                add("required", "endpoints", "endpoints array is required");
                return;
            }

            for (int index = 0; index < endpoints.size(); index++) {
                String prefix = "endpoints[" + index + "]";
                IngestEnvelopeRequest.Endpoint endpoint = endpoints.get(index);
                if (endpoint == null) {
                    add("required", prefix, "endpoint is required");
                    continue;
                }
                validateMethod(prefix + ".method", endpoint.method());
                validateRoute(prefix + ".route", endpoint.route());
                validateCounts(prefix, endpoint.requestCount(), endpoint.errorCount());
                validateHistogram(prefix + ".durationBuckets", endpoint.durationBuckets(), endpoint.requestCount());
            }
        }

        private void validateIdempotencyKey(IngestEnvelopeRequest.Application application) {
            if (!hasText(idempotencyKeyHeader)) {
                add("invalid_idempotency_key", "Idempotency-Key", "Idempotency-Key is required");
                return;
            }
            if (containsControlCharacter(idempotencyKeyHeader)) {
                add("invalid_idempotency_key", "Idempotency-Key", "Idempotency-Key contains unsupported characters");
                return;
            }
            if (hasLeadingOrTrailingWhitespace(idempotencyKeyHeader)) {
                add("invalid_idempotency_key", "Idempotency-Key", "Idempotency-Key contains unsupported characters");
                return;
            }
            String idempotencyKey = idempotencyKeyHeader;

            String[] components = idempotencyKey.split(":", -1);
            if (components.length != 5) {
                add("invalid_idempotency_key", "Idempotency-Key", "Idempotency-Key must have five components");
                return;
            }

            validateHeaderSafeComponent(components[0], "Idempotency-Key.project");
            validateHeaderSafeComponent(components[1], "Idempotency-Key.application");
            validateHeaderSafeComponent(components[2], "Idempotency-Key.environment");
            validateHeaderSafeComponent(components[3], "Idempotency-Key.instance");

            Optional<Instant> idempotencyBucketStart =
                    parseIdempotencyBucketStart(components[4], "Idempotency-Key.bucketStartUtc");

            if (application != null) {
                requireMatchingComponent(components[1], application.name(), "Idempotency-Key.application");
                requireMatchingComponent(components[2], application.environment(), "Idempotency-Key.environment");
                requireMatchingComponent(components[3], application.instance(), "Idempotency-Key.instance");
            }

            if (bucketStartUtc != null && idempotencyBucketStart.isPresent()
                    && !bucketStartUtc.equals(idempotencyBucketStart.orElseThrow())) {
                add("idempotency_payload_mismatch",
                        "Idempotency-Key.bucketStartUtc",
                        "Idempotency-Key bucket start must match payload");
            }
        }

        private void validateCounts(String field, Long requestCount, Long errorCount) {
            if (requestCount == null) {
                add("required", field + ".requestCount", "requestCount is required");
            } else if (requestCount < 0) {
                add("invalid_count", field + ".requestCount", "requestCount must not be negative");
            }

            if (errorCount == null) {
                add("required", field + ".errorCount", "errorCount is required");
            } else if (errorCount < 0) {
                add("invalid_count", field + ".errorCount", "errorCount must not be negative");
            }

            if (requestCount != null && errorCount != null
                    && requestCount >= 0 && errorCount >= 0 && errorCount > requestCount) {
                add("invalid_count", field + ".errorCount", "errorCount must not exceed requestCount");
            }
        }

        private void validateHistogram(
                String field,
                List<IngestEnvelopeRequest.DurationBucket> buckets,
                Long requestCount) {
            if (buckets == null || buckets.isEmpty()) {
                add("invalid_histogram", field, "cumulative histogram must not be empty");
                return;
            }

            long previousLeMs = Long.MIN_VALUE;
            long previousCount = Long.MIN_VALUE;
            for (int index = 0; index < buckets.size(); index++) {
                String bucketField = field + "[" + index + "]";
                IngestEnvelopeRequest.DurationBucket bucket = buckets.get(index);
                if (bucket == null) {
                    add("invalid_histogram", bucketField, "histogram bucket is required");
                    continue;
                }
                Long leMs = bucket.leMs();
                Long count = bucket.count();
                if (leMs == null || leMs <= 0) {
                    add("invalid_histogram", bucketField + ".leMs", "histogram upper bound must be positive");
                } else if (leMs <= previousLeMs) {
                    add("invalid_histogram", bucketField + ".leMs", "histogram upper bounds must increase");
                } else {
                    previousLeMs = leMs;
                }

                if (count == null || count < 0) {
                    add("invalid_histogram", bucketField + ".count", "histogram count must not be negative");
                } else {
                    if (count < previousCount) {
                        add("invalid_histogram", bucketField + ".count", "histogram count must be cumulative");
                    }
                    if (requestCount != null && requestCount >= 0 && count > requestCount) {
                        add("invalid_histogram", bucketField + ".count", "histogram count must not exceed requestCount");
                    }
                    previousCount = count;
                }
            }
        }

        private void validateRatio(String field, Double value) {
            if (value == null || value.isNaN() || value < 0.0d || value > 1.0d) {
                add("invalid_ratio", field, "ratio must be between 0.0 and 1.0");
            }
        }

        private void validateMethod(String field, String method) {
            if (!hasText(method)
                    || hasLeadingOrTrailingWhitespace(method)
                    || containsControlCharacter(method)
                    || !ALLOWED_METHODS.contains(method)) {
                add("invalid_endpoint_method", field, "endpoint method must be a bounded uppercase HTTP method");
            }
        }

        private void validateRoute(String field, String route) {
            if (!hasText(route)) {
                add("invalid_endpoint_route", field, "endpoint route is required");
                return;
            }
            if (hasLeadingOrTrailingWhitespace(route) || containsControlCharacter(route)) {
                add("invalid_endpoint_route", field, "endpoint route contains unsupported characters");
                return;
            }
            String candidate = route;
            if ("UNKNOWN".equals(candidate)) {
                return;
            }
            if (candidate.contains("?")) {
                add("invalid_endpoint_route", field, "endpoint route must not contain query string");
                return;
            }
            if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
                add("invalid_endpoint_route", field, "endpoint route must not be an absolute URL");
                return;
            }
            if (!candidate.startsWith("/") || candidate.startsWith("//") || candidate.contains("//")) {
                add("invalid_endpoint_route", field, "endpoint route must start with one slash");
                return;
            }
            if (candidate.length() > 1 && candidate.endsWith("/")) {
                add("invalid_endpoint_route", field, "endpoint route must not end with slash");
                return;
            }
            if (containsControlCharacter(candidate) || !hasValidPercentEncoding(candidate)) {
                add("invalid_endpoint_route", field, "endpoint route contains unsupported characters");
                return;
            }

            for (String segment : candidate.split("/")) {
                if (segment.isBlank()) {
                    continue;
                }
                if (isTemplateVariable(segment)) {
                    continue;
                }
                if (segment.contains("{") || segment.contains("}") || segment.contains("*")
                        || !LITERAL_ROUTE_SEGMENT.matcher(segment).matches()) {
                    add("invalid_endpoint_route", field, "endpoint route must be normalized");
                    return;
                }
                if (looksLikeConcreteIdentifier(segment)) {
                    add("invalid_endpoint_route", field, "endpoint route must not contain concrete identifiers");
                    return;
                }
            }
        }

        private void validateHeaderSafeComponent(String component, String field) {
            if (!hasText(component) || !HEADER_SAFE_COMPONENT.matcher(component).matches()) {
                add("invalid_idempotency_key", field, "Idempotency-Key component has unsupported characters");
            }
        }

        private Optional<Instant> parseUtcInstant(String value, String field) {
            if (!hasText(value)) {
                add("required", field, "UTC instant is required");
                return Optional.empty();
            }
            if (hasLeadingOrTrailingWhitespace(value) || containsControlCharacter(value)) {
                add("invalid_bucket_timestamp", field, "timestamp contains unsupported characters");
                return Optional.empty();
            }
            String candidate = value;
            if (!candidate.endsWith("Z")) {
                add("invalid_bucket_timestamp", field, "timestamp must be UTC");
                return Optional.empty();
            }
            try {
                return Optional.of(Instant.parse(candidate));
            } catch (RuntimeException ignored) {
                add("invalid_bucket_timestamp", field, "timestamp must be an ISO-8601 UTC instant");
                return Optional.empty();
            }
        }

        private Optional<Instant> parseIdempotencyBucketStart(String value, String field) {
            if (!hasText(value) || !IDEMPOTENCY_BUCKET_START.matcher(value).matches()) {
                add("invalid_idempotency_key", field, "bucket start component must be UTC basic format");
                return Optional.empty();
            }
            try {
                LocalDateTime localDateTime = LocalDateTime.parse(value, IDEMPOTENCY_BUCKET_START_FORMAT);
                return Optional.of(localDateTime.toInstant(ZoneOffset.UTC));
            } catch (RuntimeException ignored) {
                add("invalid_idempotency_key", field, "bucket start component must be UTC basic format");
                return Optional.empty();
            }
        }

        private void validateBoundary(Instant instant, String field) {
            if (instant.getNano() != 0 || Math.floorMod(instant.getEpochSecond(), BUCKET_DURATION_SECONDS) != 0) {
                add("invalid_bucket_boundary", field, "timestamp must align to a 30 second UTC boundary");
            }
        }

        private void requireText(String value, String field) {
            if (!hasText(value)) {
                add("required", field, field + " is required");
            } else if (hasLeadingOrTrailingWhitespace(value) || containsControlCharacter(value)) {
                add("invalid_identity", field, field + " contains unsupported characters");
            }
        }

        private void requireMatchingComponent(String component, String payloadValue, String field) {
            if (hasText(payloadValue) && !component.equals(payloadValue)) {
                add("idempotency_payload_mismatch", field, "Idempotency-Key component must match payload");
            }
        }

        private void add(String code, String field, String message) {
            errors.add(IngestValidationError.of(code, field, message));
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }

        private static boolean containsControlCharacter(String value) {
            for (int index = 0; index < value.length(); index++) {
                if (Character.isISOControl(value.charAt(index))) {
                    return true;
                }
            }
            return false;
        }

        private static boolean hasLeadingOrTrailingWhitespace(String value) {
            return !value.isEmpty()
                    && (isWhitespace(value.charAt(0)) || isWhitespace(value.charAt(value.length() - 1)));
        }

        private static boolean isWhitespace(char value) {
            return Character.isWhitespace(value) || Character.isSpaceChar(value);
        }

        private static boolean isTemplateVariable(String segment) {
            return segment.startsWith("{")
                    && segment.endsWith("}")
                    && segment.length() > 2
                    && segment.substring(1, segment.length() - 1).matches("[A-Za-z][A-Za-z0-9_]*");
        }

        private static boolean looksLikeConcreteIdentifier(String segment) {
            return segment.matches("[0-9]+")
                    || UUID_SEGMENT.matcher(segment).matches()
                    || LONG_HEX_SEGMENT.matcher(segment).matches();
        }

        private static boolean hasValidPercentEncoding(String value) {
            for (int index = 0; index < value.length(); index++) {
                if (value.charAt(index) != '%') {
                    continue;
                }
                if (index + 2 >= value.length()
                        || !isHex(value.charAt(index + 1))
                        || !isHex(value.charAt(index + 2))) {
                    return false;
                }
                index += 2;
            }
            return true;
        }

        private static boolean isHex(char value) {
            return value >= '0' && value <= '9'
                    || value >= 'a' && value <= 'f'
                    || value >= 'A' && value <= 'F';
        }
    }
}
