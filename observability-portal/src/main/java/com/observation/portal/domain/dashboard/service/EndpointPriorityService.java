package com.observation.portal.domain.dashboard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.portal.common.time.AcceptedBucketFreshnessStatus;
import com.observation.portal.domain.bucket.model.EndpointEvidenceRow;
import com.observation.portal.domain.dashboard.model.ApplicationDashboardReadModel;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * accepted bucket endpoint evidence에서 Dashboard endpoint priority read model을 계산한다.
 *
 * <p>이 service가 endpoint JSON parsing, merge, rule, confidence, score, ranking, recommended action을 담당한다.
 * repository/controller/UI는 endpoint priority를 계산하지 않는다.</p>
 */
@Service
public class EndpointPriorityService {

    private static final long COMMON_MINIMUM_REQUEST_COUNT = 30L;
    private static final long COMPARATIVE_MINIMUM_REQUEST_COUNT = 100L;
    private static final BigDecimal ERROR_RATE_THRESHOLD = BigDecimal.valueOf(0.05d);
    private static final BigDecimal ERROR_RATE_DELTA_THRESHOLD = BigDecimal.valueOf(0.03d);
    private static final BigDecimal ERROR_RATE_RELATIVE_THRESHOLD = BigDecimal.valueOf(2.0d);
    private static final long LATENCY_SLOW_BUCKET_LE_MS = 500L;
    private static final BigDecimal LATENCY_SLOW_SHARE_THRESHOLD = BigDecimal.valueOf(0.20d);
    private static final BigDecimal LATENCY_SLOW_SHARE_DELTA_THRESHOLD = BigDecimal.valueOf(0.10d);
    private static final BigDecimal COMPARATIVE_ERROR_DELTA_THRESHOLD = BigDecimal.valueOf(0.02d);
    private static final BigDecimal COMPARATIVE_SLOW_SHARE_DELTA_THRESHOLD = BigDecimal.valueOf(0.08d);
    private static final double COMPARATIVE_CONFIDENCE_CAP = 0.64d;
    private static final int MAX_ENDPOINT_PRIORITY_ITEMS = 5;
    private static final Pattern UUID_SEGMENT = Pattern.compile(
            "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern ULID_SEGMENT = Pattern.compile("(?i)[0-7][0-9a-hjkmnp-tv-z]{25}");
    private static final Pattern LONG_HEX_SEGMENT = Pattern.compile("(?i)[0-9a-f]{8,}");
    private static final Pattern VERSION_SEGMENT = Pattern.compile("(?i)v[0-9]{1,2}");
    private static final Pattern LITERAL_ROUTE_SEGMENT = Pattern.compile("[A-Za-z0-9._~-]+");
    private static final Set<String> ALLOWED_METHODS = Set.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE", "UNKNOWN");

    private final ObjectMapper objectMapper;

    /**
     * persisted endpoints_json을 lenient하게 읽기 위해 ObjectMapper를 주입받는다.
     */
    public EndpointPriorityService(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * current freshness일 때만 current/baseline endpoint evidence를 ranking item으로 변환한다.
     */
    public List<ApplicationDashboardReadModel.EndpointPriorityItem> endpointPriority(EndpointPriorityInput input) {
        EndpointPriorityInput requiredInput = Objects.requireNonNull(input, "input must not be null");
        if (requiredInput.freshnessStatus() != AcceptedBucketFreshnessStatus.CURRENT) {
            return List.of();
        }

        WindowEndpointEvidence currentEvidence = mergeWindow(requiredInput.currentRows());
        WindowEndpointEvidence baselineEvidence = mergeWindow(requiredInput.baselineRows());

        OffsetDateTime lastObservedAt = latestObservedAt(requiredInput.currentRows())
                .or(() -> requiredInput.lastObservedAt())
                .orElse(null);
        if (lastObservedAt == null) {
            return List.of();
        }

        List<EndpointPriorityCandidate> candidates = currentEvidence.endpoints().values().stream()
                .filter(endpoint -> !isUnknownRoute(endpoint.route()))
                .flatMap(endpoint -> candidate(endpoint, baselineEvidence.endpoints().get(endpoint.endpointKey()),
                        lastObservedAt).stream())
                .sorted(EndpointPriorityService::compareCandidates)
                .limit(MAX_ENDPOINT_PRIORITY_ITEMS)
                .toList();

        List<ApplicationDashboardReadModel.EndpointPriorityItem> items = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            EndpointPriorityCandidate candidate = candidates.get(index);
            items.add(new ApplicationDashboardReadModel.EndpointPriorityItem(
                    index + 1,
                    candidate.method(),
                    candidate.route(),
                    candidate.endpointKey(),
                    candidate.reason(),
                    candidate.ruleIds(),
                    candidate.confidence(),
                    candidate.score(),
                    new ApplicationDashboardReadModel.EndpointPriorityFreshness(
                            "current",
                            lastObservedAt,
                            "current",
                            null),
                    candidate.evidence(),
                    candidate.recommendedAction()));
        }
        return items;
    }

    private Optional<EndpointPriorityCandidate> candidate(
            EndpointAggregate current,
            EndpointAggregate baseline,
            OffsetDateTime lastObservedAt) {
        if (baseline == null
                || current.requestCount() < COMMON_MINIMUM_REQUEST_COUNT
                || baseline.requestCount() < COMMON_MINIMUM_REQUEST_COUNT) {
            return Optional.empty();
        }

        BigDecimal currentErrorRate = decimal(current.errorCount(), current.requestCount());
        BigDecimal baselineErrorRate = decimal(baseline.errorCount(), baseline.requestCount());
        BigDecimal errorRateDelta = currentErrorRate.subtract(baselineErrorRate);
        boolean errorSpike = currentErrorRate.compareTo(ERROR_RATE_THRESHOLD) >= 0
                && errorRateDelta.compareTo(ERROR_RATE_DELTA_THRESHOLD) >= 0
                && currentErrorRate.compareTo(baselineErrorRate.multiply(ERROR_RATE_RELATIVE_THRESHOLD)) >= 0;

        Optional<SlowSharePair> slowSharePair = slowSharePair(current, baseline);
        boolean latencyAvailable = slowSharePair.isPresent();
        BigDecimal slowShare = latencyAvailable ? slowSharePair.orElseThrow().currentShare() : null;
        BigDecimal baselineSlowShare = latencyAvailable ? slowSharePair.orElseThrow().baselineShare() : null;
        BigDecimal slowShareDelta = latencyAvailable ? slowShare.subtract(baselineSlowShare) : null;
        boolean latencySpike = latencyAvailable
                && slowShare.compareTo(LATENCY_SLOW_SHARE_THRESHOLD) >= 0
                && slowShareDelta.compareTo(LATENCY_SLOW_SHARE_DELTA_THRESHOLD) >= 0;
        boolean comparativeRegression = comparativeRegression(
                current,
                baseline,
                errorRateDelta,
                latencyAvailable,
                slowShareDelta);

        ApplicationDashboardReadModel.EndpointPriorityReason reason;
        List<String> ruleIds;
        double confidence;
        if (errorSpike && latencySpike) {
            reason = ApplicationDashboardReadModel.EndpointPriorityReason.ERROR_AND_LATENCY;
            ruleIds = List.of("endpoint_error_spike", "endpoint_latency_spike");
            confidence = clamp(Math.max(
                    confidenceFromDelta(
                            currentErrorRate,
                            ERROR_RATE_THRESHOLD,
                            errorRateDelta,
                            ERROR_RATE_DELTA_THRESHOLD),
                    confidenceFromDelta(
                            slowShare,
                            LATENCY_SLOW_SHARE_THRESHOLD,
                            slowShareDelta,
                            LATENCY_SLOW_SHARE_DELTA_THRESHOLD)) + 0.05d);
        } else if (errorSpike) {
            reason = ApplicationDashboardReadModel.EndpointPriorityReason.ERROR_SPIKE;
            ruleIds = List.of("endpoint_error_spike");
            confidence = confidenceFromDelta(
                    currentErrorRate,
                    ERROR_RATE_THRESHOLD,
                    errorRateDelta,
                    ERROR_RATE_DELTA_THRESHOLD);
        } else if (latencySpike) {
            reason = ApplicationDashboardReadModel.EndpointPriorityReason.LATENCY_SPIKE;
            ruleIds = List.of("endpoint_latency_spike");
            confidence = confidenceFromDelta(
                    slowShare,
                    LATENCY_SLOW_SHARE_THRESHOLD,
                    slowShareDelta,
                    LATENCY_SLOW_SHARE_DELTA_THRESHOLD);
        } else if (comparativeRegression) {
            reason = ApplicationDashboardReadModel.EndpointPriorityReason.COMPARATIVE_REGRESSION;
            ruleIds = List.of("endpoint_comparative_regression");
            confidence = comparativeConfidence(errorRateDelta, slowShareDelta);
        } else {
            return Optional.empty();
        }

        ApplicationDashboardReadModel.EndpointPriorityEvidence evidence =
                new ApplicationDashboardReadModel.EndpointPriorityEvidence(
                        current.requestCount(),
                        current.errorCount(),
                        currentErrorRate,
                        baseline.requestCount(),
                        baseline.errorCount(),
                        baselineErrorRate,
                        errorRateDelta,
                        latencyAvailable ? current.durationBuckets() : null,
                        latencyAvailable ? baseline.durationBuckets() : null,
                        slowShare,
                        baselineSlowShare,
                        slowShareDelta,
                        "histogram_bucket_distribution",
                        ApplicationDashboardReadModel.EndpointEvidenceStatus.AVAILABLE,
                        latencyAvailable
                                ? ApplicationDashboardReadModel.EndpointEvidenceStatus.AVAILABLE
                                : ApplicationDashboardReadModel.EndpointEvidenceStatus.UNAVAILABLE);
        int score = score(confidence);
        return Optional.of(new EndpointPriorityCandidate(
                current.method(),
                current.route(),
                current.endpointKey(),
                reason,
                ruleIds,
                confidence,
                score,
                current.requestCount(),
                evidence,
                recommendedAction(reason)));
    }

    private static boolean comparativeRegression(
            EndpointAggregate current,
            EndpointAggregate baseline,
            BigDecimal errorRateDelta,
            boolean latencyAvailable,
            BigDecimal slowShareDelta) {
        if (current.requestCount() < COMPARATIVE_MINIMUM_REQUEST_COUNT
                || baseline.requestCount() < COMPARATIVE_MINIMUM_REQUEST_COUNT) {
            return false;
        }
        boolean errorRegression = errorRateDelta.compareTo(COMPARATIVE_ERROR_DELTA_THRESHOLD) >= 0;
        boolean latencyRegression = latencyAvailable
                && slowShareDelta.compareTo(COMPARATIVE_SLOW_SHARE_DELTA_THRESHOLD) >= 0;
        return errorRegression || latencyRegression;
    }

    private WindowEndpointEvidence mergeWindow(List<EndpointEvidenceRow> rows) {
        List<EndpointEvidenceRow> evidenceRows = List.copyOf(Objects.requireNonNullElse(rows, List.of()));
        Map<String, EndpointAggregateBuilder> builders = new LinkedHashMap<>();
        for (EndpointEvidenceRow row : evidenceRows) {
            for (ParsedEndpoint endpoint : parseEndpoints(row.endpointsJson())) {
                EndpointAggregateBuilder builder = builders.computeIfAbsent(
                        endpoint.endpointKey(),
                        key -> new EndpointAggregateBuilder(endpoint.method(), endpoint.route(), endpoint.endpointKey()));
                builder.add(endpoint);
            }
        }

        Map<String, EndpointAggregate> endpoints = new LinkedHashMap<>();
        builders.forEach((key, builder) -> endpoints.put(key, builder.build()));
        return new WindowEndpointEvidence(endpoints);
    }

    private List<ParsedEndpoint> parseEndpoints(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isArray()) {
                return List.of();
            }
            List<ParsedEndpoint> endpoints = new ArrayList<>();
            for (JsonNode item : root) {
                parseEndpoint(item).ifPresent(endpoints::add);
            }
            return endpoints;
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private Optional<ParsedEndpoint> parseEndpoint(JsonNode item) {
        String method = textValue(item, "method")
                .map(value -> value.toUpperCase(Locale.ROOT))
                .orElse(null);
        String route = textValue(item, "route").orElse(null);
        Long requestCount = longValue(item, "requestCount").orElse(null);
        Long errorCount = longValue(item, "errorCount").orElse(null);
        if (method == null || !isAllowedMethod(method) || route == null || !isSafeRoute(route)
                || requestCount == null || errorCount == null
                || requestCount < 0L || errorCount < 0L || errorCount > requestCount) {
            return Optional.empty();
        }
        ParsedDurationBuckets durationBuckets = parseDurationBuckets(item.get("durationBuckets"), requestCount);
        if (durationBuckets.malformed()) {
            return Optional.empty();
        }
        return Optional.of(new ParsedEndpoint(
                method,
                route,
                method + " " + route,
                requestCount,
                errorCount,
                durationBuckets.buckets()));
    }

    private static Optional<String> textValue(JsonNode root, String fieldName) {
        JsonNode value = root == null ? null : root.get(fieldName);
        if (value == null || !value.isTextual()) {
            return Optional.empty();
        }
        String text = value.asText();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    private static Optional<Long> longValue(JsonNode root, String fieldName) {
        JsonNode value = root == null ? null : root.get(fieldName);
        return value != null && value.isIntegralNumber() && value.canConvertToLong()
                ? Optional.of(value.asLong())
                : Optional.empty();
    }

    private ParsedDurationBuckets parseDurationBuckets(JsonNode root, long requestCount) {
        if (root == null || !root.isArray()) {
            return ParsedDurationBuckets.unavailable();
        }
        List<ApplicationDashboardReadModel.HistogramBucket> buckets = new ArrayList<>();
        for (JsonNode item : root) {
            if (hasNonIntegralNumeric(item, "leMs") || hasNonIntegralNumeric(item, "count")) {
                return ParsedDurationBuckets.malformedItem();
            }
            Optional<Long> leMs = longValue(item, "leMs");
            Optional<Long> count = longValue(item, "count");
            if (leMs.isEmpty() || count.isEmpty() || leMs.orElseThrow() < 0L || count.orElseThrow() < 0L) {
                return ParsedDurationBuckets.unavailable();
            }
            buckets.add(new ApplicationDashboardReadModel.HistogramBucket(leMs.orElseThrow(), count.orElseThrow()));
        }
        List<ApplicationDashboardReadModel.HistogramBucket> sortedBuckets =
                validCumulativeBuckets(buckets, requestCount);
        return sortedBuckets.isEmpty()
                ? ParsedDurationBuckets.unavailable()
                : ParsedDurationBuckets.available(sortedBuckets);
    }

    private static boolean hasNonIntegralNumeric(JsonNode root, String fieldName) {
        JsonNode value = root == null ? null : root.get(fieldName);
        return value != null && value.isNumber() && !value.isIntegralNumber();
    }

    private static List<ApplicationDashboardReadModel.HistogramBucket> validCumulativeBuckets(
            List<ApplicationDashboardReadModel.HistogramBucket> buckets,
            long requestCount) {
        List<ApplicationDashboardReadModel.HistogramBucket> sortedBuckets = buckets.stream()
                .sorted(Comparator.comparingLong(ApplicationDashboardReadModel.HistogramBucket::leMs))
                .toList();
        Long previousBoundary = null;
        Long previousCount = null;
        for (ApplicationDashboardReadModel.HistogramBucket bucket : sortedBuckets) {
            if (Objects.equals(previousBoundary, bucket.leMs())) {
                return List.of();
            }
            if (previousCount != null && bucket.count() < previousCount) {
                return List.of();
            }
            if (bucket.count() > requestCount) {
                return List.of();
            }
            previousBoundary = bucket.leMs();
            previousCount = bucket.count();
        }
        return sortedBuckets;
    }

    private static Optional<SlowSharePair> slowSharePair(EndpointAggregate current, EndpointAggregate baseline) {
        if (current.durationBuckets() == null
                || baseline.durationBuckets() == null
                || !sameBoundarySet(current.durationBuckets(), baseline.durationBuckets())) {
            return Optional.empty();
        }
        Optional<BigDecimal> currentShare = slowShare(current.durationBuckets());
        Optional<BigDecimal> baselineShare = slowShare(baseline.durationBuckets());
        if (currentShare.isEmpty() || baselineShare.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SlowSharePair(currentShare.orElseThrow(), baselineShare.orElseThrow()));
    }

    private static Optional<BigDecimal> slowShare(List<ApplicationDashboardReadModel.HistogramBucket> buckets) {
        if (buckets.isEmpty()) {
            return Optional.empty();
        }
        long totalCount = buckets.get(buckets.size() - 1).count();
        if (totalCount <= 0L) {
            return Optional.empty();
        }
        Optional<ApplicationDashboardReadModel.HistogramBucket> thresholdBucket = buckets.stream()
                .filter(bucket -> bucket.leMs() == LATENCY_SLOW_BUCKET_LE_MS)
                .findFirst();
        if (thresholdBucket.isEmpty()) {
            return Optional.empty();
        }
        long slowCount = Math.max(0L, totalCount - thresholdBucket.orElseThrow().count());
        return Optional.of(decimal(slowCount, totalCount));
    }

    private static boolean sameBoundarySet(
            List<ApplicationDashboardReadModel.HistogramBucket> current,
            List<ApplicationDashboardReadModel.HistogramBucket> baseline) {
        return current.stream().map(ApplicationDashboardReadModel.HistogramBucket::leMs).toList()
                .equals(baseline.stream().map(ApplicationDashboardReadModel.HistogramBucket::leMs).toList());
    }

    private static Optional<OffsetDateTime> latestObservedAt(List<EndpointEvidenceRow> rows) {
        return List.copyOf(Objects.requireNonNullElse(rows, List.of())).stream()
                .map(EndpointEvidenceRow::bucketEndUtc)
                .max(Comparator.naturalOrder());
    }

    private static boolean isUnknownRoute(String route) {
        return "UNKNOWN".equalsIgnoreCase(route == null ? "" : route.trim());
    }

    /**
     * persisted endpoint method를 API endpointKey에 올리기 전 bounded HTTP method인지 다시 확인한다.
     */
    private static boolean isAllowedMethod(String method) {
        return ALLOWED_METHODS.contains(method);
    }

    /**
     * read-side에서도 route attribution 최종 payload 계약을 재검증해 raw path/query/detail 노출을 차단한다.
     */
    private static boolean isSafeRoute(String route) {
        if (route == null || route.isBlank()) {
            return false;
        }
        if (hasLeadingOrTrailingWhitespace(route) || containsControlCharacter(route)) {
            return false;
        }
        if ("UNKNOWN".equals(route)) {
            return true;
        }
        String lowerCaseRoute = route.toLowerCase(Locale.ROOT);
        if (route.contains("?") || lowerCaseRoute.startsWith("http://") || lowerCaseRoute.startsWith("https://")) {
            return false;
        }
        if (!route.startsWith("/") || route.startsWith("//") || route.contains("//")) {
            return false;
        }
        if (route.length() > 1 && route.endsWith("/")) {
            return false;
        }
        if (!hasValidPercentEncoding(route)) {
            return false;
        }

        for (String segment : route.split("/")) {
            if (segment.isBlank()) {
                continue;
            }
            if (isTemplateVariable(segment)) {
                continue;
            }
            if (segment.contains("{") || segment.contains("}") || segment.contains("*")
                    || !LITERAL_ROUTE_SEGMENT.matcher(segment).matches()) {
                return false;
            }
            if (looksLikeConcreteIdentifier(segment)) {
                return false;
            }
        }
        return true;
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
                || ULID_SEGMENT.matcher(segment).matches()
                || LONG_HEX_SEGMENT.matcher(segment).matches()
                || segment.contains(".")
                || segment.contains("@")
                || (!VERSION_SEGMENT.matcher(segment).matches()
                && hasLetterAndDigit(segment)
                && (isLongAlphaNumericToken(segment) || isDigitLetterSlug(segment)));
    }

    private static boolean isLongAlphaNumericToken(String segment) {
        return segment.length() >= 12 && segment.matches("[A-Za-z0-9]+");
    }

    private static boolean isDigitLetterSlug(String segment) {
        boolean hasSeparator = segment.indexOf('-') >= 0 || segment.indexOf('_') >= 0 || segment.indexOf('~') >= 0;
        long digitCount = segment.chars().filter(Character::isDigit).count();
        return (hasSeparator && digitCount > 0L)
                || (segment.length() >= 8 && digitCount >= 2L && segment.matches("[A-Za-z0-9]+"));
    }

    private static boolean hasLetterAndDigit(String value) {
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            hasLetter = hasLetter || Character.isLetter(character);
            hasDigit = hasDigit || Character.isDigit(character);
        }
        return hasLetter && hasDigit;
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

    private static BigDecimal decimal(long numerator, long denominator) {
        if (denominator <= 0L) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }

    private static double confidenceFromDelta(
            BigDecimal current,
            BigDecimal currentThreshold,
            BigDecimal delta,
            BigDecimal deltaThreshold) {
        BigDecimal confidence = BigDecimal.valueOf(0.65d)
                .add(current.subtract(currentThreshold).multiply(BigDecimal.valueOf(2.0d)))
                .add(delta.subtract(deltaThreshold).multiply(BigDecimal.valueOf(3.0d)));
        return clamp(confidence.doubleValue());
    }

    private static double comparativeConfidence(BigDecimal errorRateDelta, BigDecimal slowShareDelta) {
        BigDecimal errorContribution = errorRateDelta.max(BigDecimal.ZERO).multiply(BigDecimal.valueOf(3.0d));
        BigDecimal slowContribution = slowShareDelta == null
                ? BigDecimal.ZERO
                : slowShareDelta.max(BigDecimal.ZERO).multiply(BigDecimal.valueOf(2.0d));
        double confidence = BigDecimal.valueOf(0.50d)
                .add(errorContribution.max(slowContribution))
                .doubleValue();
        return Math.min(COMPARATIVE_CONFIDENCE_CAP, clamp(confidence));
    }

    private static double clamp(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private static int score(double confidence) {
        return Math.max(0, Math.min(100, (int) Math.round(confidence * 100.0d)));
    }

    private static String recommendedAction(ApplicationDashboardReadModel.EndpointPriorityReason reason) {
        return switch (reason) {
            case ERROR_AND_LATENCY -> "이 endpoint의 오류 로그와 외부 의존성 지연 가능성을 먼저 확인해보세요.";
            case ERROR_SPIKE -> "이 endpoint의 최근 오류 로그와 배포 변경 가능성을 먼저 확인해보세요.";
            case LATENCY_SPIKE -> "이 endpoint의 느린 요청 구간과 외부 호출, DB query 대기 가능성을 먼저 확인해보세요.";
            case COMPARATIVE_REGRESSION -> "이 endpoint가 baseline보다 악화된 신호를 보이므로 최근 변경과 traffic 패턴을 먼저 확인해보세요.";
        };
    }

    private static int compareCandidates(EndpointPriorityCandidate first, EndpointPriorityCandidate second) {
        int reasonPriority = Integer.compare(reasonPriority(first.reason()), reasonPriority(second.reason()));
        if (reasonPriority != 0) {
            return reasonPriority;
        }
        int confidence = Double.compare(second.confidence(), first.confidence());
        if (confidence != 0) {
            return confidence;
        }
        int score = Integer.compare(second.score(), first.score());
        if (score != 0) {
            return score;
        }
        int requestCount = Long.compare(second.requestCount(), first.requestCount());
        if (requestCount != 0) {
            return requestCount;
        }
        return first.endpointKey().compareTo(second.endpointKey());
    }

    private static int reasonPriority(ApplicationDashboardReadModel.EndpointPriorityReason reason) {
        return switch (reason) {
            case ERROR_AND_LATENCY -> 1;
            case ERROR_SPIKE -> 2;
            case LATENCY_SPIKE -> 3;
            case COMPARATIVE_REGRESSION -> 4;
        };
    }

    /**
     * endpoint priority service에 전달되는 bounded orchestration input이다.
     */
    public record EndpointPriorityInput(
            AcceptedBucketFreshnessStatus freshnessStatus,
            List<EndpointEvidenceRow> currentRows,
            List<EndpointEvidenceRow> baselineRows,
            Optional<OffsetDateTime> lastObservedAt
    ) {

        /**
         * freshness status와 repository projection collection이 null 없이 전달되도록 검증한다.
         */
        public EndpointPriorityInput {
            Objects.requireNonNull(freshnessStatus, "freshnessStatus must not be null");
            currentRows = List.copyOf(Objects.requireNonNull(currentRows, "currentRows must not be null"));
            baselineRows = List.copyOf(Objects.requireNonNull(baselineRows, "baselineRows must not be null"));
            lastObservedAt = Objects.requireNonNull(lastObservedAt, "lastObservedAt must not be null");
        }

        /**
         * nullable timestamp 호출부를 Optional 기반 input으로 변환한다.
         */
        public EndpointPriorityInput(
                AcceptedBucketFreshnessStatus freshnessStatus,
                List<EndpointEvidenceRow> currentRows,
                List<EndpointEvidenceRow> baselineRows,
                OffsetDateTime lastObservedAt) {
            this(freshnessStatus, currentRows, baselineRows, Optional.ofNullable(lastObservedAt));
        }
    }

    private static final class EndpointAggregateBuilder {

        private final String method;
        private final String route;
        private final String endpointKey;
        private long requestCount;
        private long errorCount;
        private List<Long> expectedBoundarySet;
        private final Map<Long, Long> mergedDurationCounts = new LinkedHashMap<>();
        private boolean latencyUnavailable;

        private EndpointAggregateBuilder(String method, String route, String endpointKey) {
            this.method = method;
            this.route = route;
            this.endpointKey = endpointKey;
        }

        private void add(ParsedEndpoint endpoint) {
            requestCount += endpoint.requestCount();
            errorCount += endpoint.errorCount();
            if (endpoint.durationBuckets().isEmpty()) {
                latencyUnavailable = true;
                return;
            }
            List<ApplicationDashboardReadModel.HistogramBucket> buckets = endpoint.durationBuckets().orElseThrow();
            List<Long> boundarySet = buckets.stream()
                    .map(ApplicationDashboardReadModel.HistogramBucket::leMs)
                    .toList();
            if (expectedBoundarySet == null) {
                expectedBoundarySet = boundarySet;
                boundarySet.forEach(boundary -> mergedDurationCounts.put(boundary, 0L));
            } else if (!expectedBoundarySet.equals(boundarySet)) {
                latencyUnavailable = true;
                return;
            }
            if (!latencyUnavailable) {
                for (ApplicationDashboardReadModel.HistogramBucket bucket : buckets) {
                    mergedDurationCounts.compute(bucket.leMs(), (boundary, count) -> count + bucket.count());
                }
            }
        }

        private EndpointAggregate build() {
            List<ApplicationDashboardReadModel.HistogramBucket> durationBuckets = null;
            if (!latencyUnavailable && !mergedDurationCounts.isEmpty()) {
                durationBuckets = mergedDurationCounts.entrySet().stream()
                        .map(entry -> new ApplicationDashboardReadModel.HistogramBucket(
                                entry.getKey(),
                                entry.getValue()))
                        .toList();
            }
            return new EndpointAggregate(method, route, endpointKey, requestCount, errorCount, durationBuckets);
        }
    }

    private record WindowEndpointEvidence(Map<String, EndpointAggregate> endpoints) {
    }

    private record EndpointAggregate(
            String method,
            String route,
            String endpointKey,
            long requestCount,
            long errorCount,
            List<ApplicationDashboardReadModel.HistogramBucket> durationBuckets
    ) {
    }

    private record EndpointPriorityCandidate(
            String method,
            String route,
            String endpointKey,
            ApplicationDashboardReadModel.EndpointPriorityReason reason,
            List<String> ruleIds,
            double confidence,
            int score,
            long requestCount,
            ApplicationDashboardReadModel.EndpointPriorityEvidence evidence,
            String recommendedAction
    ) {
    }

    private record ParsedEndpoint(
            String method,
            String route,
            String endpointKey,
            long requestCount,
            long errorCount,
            Optional<List<ApplicationDashboardReadModel.HistogramBucket>> durationBuckets
    ) {
    }

    private record ParsedDurationBuckets(
            Optional<List<ApplicationDashboardReadModel.HistogramBucket>> buckets,
            boolean malformed
    ) {

        private static ParsedDurationBuckets available(List<ApplicationDashboardReadModel.HistogramBucket> buckets) {
            return new ParsedDurationBuckets(Optional.of(List.copyOf(buckets)), false);
        }

        private static ParsedDurationBuckets unavailable() {
            return new ParsedDurationBuckets(Optional.empty(), false);
        }

        private static ParsedDurationBuckets malformedItem() {
            return new ParsedDurationBuckets(Optional.empty(), true);
        }
    }

    private record SlowSharePair(BigDecimal currentShare, BigDecimal baselineShare) {
    }
}
