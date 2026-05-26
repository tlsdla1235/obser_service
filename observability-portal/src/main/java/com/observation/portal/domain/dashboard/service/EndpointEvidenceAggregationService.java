package com.observation.portal.domain.dashboard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.portal.domain.bucket.model.EndpointEvidenceRow;
import com.observation.portal.domain.dashboard.model.ApplicationDashboardReadModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
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
 * accepted bucket endpoint JSON을 endpointKey 단위 aggregate로 변환하는 공용 helper service다.
 *
 * <p>Story 5.5 endpoint priority와 Story 5.6 instance endpoint evidence가 같은 route safety, count validation,
 * histogram boundary merge 규칙을 쓰도록 parsing/merge 책임만 분리한다. rank, confidence, recommended action은 계산하지
 * 않는다.</p>
 */
@Service
public class EndpointEvidenceAggregationService {

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
     * persisted `endpoints_json`을 lenient하게 읽기 위한 ObjectMapper를 주입받는다.
     */
    public EndpointEvidenceAggregationService(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * 한 window의 endpoint JSON row들을 endpointKey 기준으로 합산한다.
     *
     * <p>raw JSON/path/query는 반환하지 않고, service caller가 insufficient 여부를 판단할 수 있도록 malformed flag만 함께
     * 제공한다.</p>
     */
    public WindowEndpointEvidence mergeWindow(List<EndpointEvidenceRow> rows) {
        List<EndpointEvidenceRow> evidenceRows = List.copyOf(Objects.requireNonNullElse(rows, List.of()));
        Map<String, EndpointAggregateBuilder> builders = new LinkedHashMap<>();
        boolean malformedEvidence = false;
        for (EndpointEvidenceRow row : evidenceRows) {
            EndpointParseBatch batch = parseEndpoints(row.endpointsJson());
            malformedEvidence = malformedEvidence || batch.malformedEvidence();
            for (ParsedEndpoint endpoint : batch.endpoints()) {
                EndpointAggregateBuilder builder = builders.computeIfAbsent(
                        endpoint.endpointKey(),
                        key -> new EndpointAggregateBuilder(endpoint.method(), endpoint.route(), endpoint.endpointKey()));
                builder.add(endpoint);
            }
        }

        Map<String, EndpointAggregate> endpoints = new LinkedHashMap<>();
        builders.forEach((key, builder) -> endpoints.put(key, builder.build()));
        return new WindowEndpointEvidence(endpoints, malformedEvidence);
    }

    private EndpointParseBatch parseEndpoints(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isArray()) {
                return EndpointParseBatch.malformed();
            }
            List<ParsedEndpoint> endpoints = new ArrayList<>();
            boolean malformedEvidence = false;
            for (JsonNode item : root) {
                ParsedEndpointResult result = parseEndpoint(item);
                result.endpoint().ifPresent(endpoints::add);
                malformedEvidence = malformedEvidence || result.malformedEvidence();
            }
            return new EndpointParseBatch(endpoints, malformedEvidence);
        } catch (JsonProcessingException exception) {
            return EndpointParseBatch.malformed();
        }
    }

    private ParsedEndpointResult parseEndpoint(JsonNode item) {
        String method = textValue(item, "method")
                .map(value -> value.toUpperCase(Locale.ROOT))
                .orElse(null);
        String route = textValue(item, "route").orElse(null);
        Long requestCount = longValue(item, "requestCount").orElse(null);
        Long errorCount = longValue(item, "errorCount").orElse(null);
        if (method == null || !isAllowedMethod(method) || route == null || !isSafeRoute(route)
                || requestCount == null || errorCount == null
                || requestCount < 0L || errorCount < 0L || errorCount > requestCount) {
            return ParsedEndpointResult.malformed();
        }
        ParsedDurationBuckets durationBuckets = parseDurationBuckets(item.get("durationBuckets"), requestCount);
        if (durationBuckets.skipEndpoint()) {
            return ParsedEndpointResult.malformed();
        }
        return ParsedEndpointResult.parsed(new ParsedEndpoint(
                method,
                route,
                method + " " + route,
                requestCount,
                errorCount,
                durationBuckets.buckets(),
                durationBuckets.malformedEvidence()), durationBuckets.malformedEvidence());
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
            return ParsedDurationBuckets.unavailable(false);
        }
        List<ApplicationDashboardReadModel.HistogramBucket> buckets = new ArrayList<>();
        for (JsonNode item : root) {
            if (hasNonIntegralNumeric(item, "leMs") || hasNonIntegralNumeric(item, "count")) {
                return ParsedDurationBuckets.malformedItem();
            }
            Optional<Long> leMs = longValue(item, "leMs");
            Optional<Long> count = longValue(item, "count");
            if (leMs.isEmpty() || count.isEmpty() || leMs.orElseThrow() < 0L || count.orElseThrow() < 0L) {
                return ParsedDurationBuckets.unavailable(true);
            }
            buckets.add(new ApplicationDashboardReadModel.HistogramBucket(leMs.orElseThrow(), count.orElseThrow()));
        }
        List<ApplicationDashboardReadModel.HistogramBucket> sortedBuckets =
                validCumulativeBuckets(buckets, requestCount);
        return sortedBuckets.isEmpty()
                ? ParsedDurationBuckets.unavailable(true)
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

    /**
     * 한 window 안에서 endpointKey별로 합산된 aggregate와 parsing/validation 실패 여부를 담는다.
     */
    public record WindowEndpointEvidence(
            Map<String, EndpointAggregate> endpoints,
            boolean malformedEvidence
    ) {

        /**
         * caller가 aggregate map을 변경하지 못하도록 방어적으로 복사한다.
         */
        public WindowEndpointEvidence {
            endpoints = Collections.unmodifiableMap(new LinkedHashMap<>(
                    Objects.requireNonNull(endpoints, "endpoints must not be null")));
        }
    }

    /**
     * endpointKey 하나에 대해 request/error count와 표시 가능한 duration bucket merge 결과를 담는다.
     */
    public record EndpointAggregate(
            String method,
            String route,
            String endpointKey,
            long requestCount,
            long errorCount,
            List<ApplicationDashboardReadModel.HistogramBucket> durationBuckets,
            boolean durationBoundaryMismatch
    ) {

        /**
         * aggregate는 bounded endpoint identity와 count만 담고 raw JSON/path/query를 보존하지 않는다.
         */
        public EndpointAggregate {
            method = requireText(method, "method");
            route = requireText(route, "route");
            endpointKey = requireText(endpointKey, "endpointKey");
            if (!endpointKey.equals(method + " " + route)) {
                throw new IllegalArgumentException("endpointKey must match method + ' ' + route");
            }
            if (requestCount < 0L || errorCount < 0L || errorCount > requestCount) {
                throw new IllegalArgumentException("invalid endpoint counts");
            }
            durationBuckets = durationBuckets == null ? null : List.copyOf(durationBuckets);
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
        private boolean durationBoundaryMismatch;

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
                durationBoundaryMismatch = true;
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
            return new EndpointAggregate(
                    method,
                    route,
                    endpointKey,
                    requestCount,
                    errorCount,
                    durationBuckets,
                    durationBoundaryMismatch);
        }
    }

    private record EndpointParseBatch(List<ParsedEndpoint> endpoints, boolean malformedEvidence) {

        private EndpointParseBatch {
            endpoints = List.copyOf(Objects.requireNonNull(endpoints, "endpoints must not be null"));
        }

        private static EndpointParseBatch malformed() {
            return new EndpointParseBatch(List.of(), true);
        }
    }

    private record ParsedEndpointResult(Optional<ParsedEndpoint> endpoint, boolean malformedEvidence) {

        private static ParsedEndpointResult parsed(ParsedEndpoint endpoint, boolean malformedEvidence) {
            return new ParsedEndpointResult(Optional.of(endpoint), malformedEvidence);
        }

        private static ParsedEndpointResult malformed() {
            return new ParsedEndpointResult(Optional.empty(), true);
        }
    }

    private record ParsedEndpoint(
            String method,
            String route,
            String endpointKey,
            long requestCount,
            long errorCount,
            Optional<List<ApplicationDashboardReadModel.HistogramBucket>> durationBuckets,
            boolean durationEvidenceMalformed
    ) {
    }

    private record ParsedDurationBuckets(
            Optional<List<ApplicationDashboardReadModel.HistogramBucket>> buckets,
            boolean malformedEvidence,
            boolean skipEndpoint
    ) {

        private static ParsedDurationBuckets available(List<ApplicationDashboardReadModel.HistogramBucket> buckets) {
            return new ParsedDurationBuckets(Optional.of(List.copyOf(buckets)), false, false);
        }

        private static ParsedDurationBuckets unavailable(boolean malformedEvidence) {
            return new ParsedDurationBuckets(Optional.empty(), malformedEvidence, false);
        }

        private static ParsedDurationBuckets malformedItem() {
            return new ParsedDurationBuckets(Optional.empty(), true, true);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
