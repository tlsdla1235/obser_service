package com.observation.starter.service;

import com.observation.starter.config.RouteAttributionProperties;
import com.observation.starter.model.route.NormalizedRoute;
import com.observation.starter.model.route.RouteTemplateContract;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * framework route template 우선, configured allowlist와 safe prefix collapse fallback 순서로 route를 정규화한다.
 *
 * <p>raw request path는 low-cardinality {@code uri}/{@code path} 후보에서만 일시적으로 사용한다.
 * 반환값은 safe template, allowlist template, safe prefix collapse marker, {@code UNKNOWN}뿐이다.</p>
 */
public final class RouteNormalizationService {

    private static final Pattern UUID_SEGMENT = Pattern.compile(
            "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern LONG_HEX_SEGMENT = Pattern.compile("(?i)[0-9a-f]{8,}");
    private static final Pattern LITERAL_ROUTE_SEGMENT = Pattern.compile("[A-Za-z0-9._~-]+");

    private final List<AllowedRoute> allowlist;

    /**
     * allowlist 없이 framework route template과 {@code UNKNOWN} fallback만 사용하는 서비스를 만든다.
     */
    public RouteNormalizationService() {
        this(List.of());
    }

    /**
     * 지정된 route template allowlist와 {@code UNKNOWN} fallback을 사용하는 서비스를 만든다.
     */
    public RouteNormalizationService(Collection<String> allowlistTemplates) {
        this.allowlist = Optional.ofNullable(allowlistTemplates).orElseGet(List::of).stream()
                .map(RouteAttributionProperties::normalizeAllowlistTemplate)
                .map(AllowedRoute::new)
                .toList();
    }

    /**
     * framework route template만 있는 관측값을 정규화한다.
     */
    public NormalizedRoute normalize(Optional<String> frameworkRouteTemplate) {
        return normalize(frameworkRouteTemplate, Optional.empty());
    }

    /**
     * route source precedence에 따라 정규화된 route를 반환한다.
     *
     * <p>framework template이 있으면 가장 먼저 사용하되, 처리 결과가 {@code UNKNOWN}이면 low-cardinality
     * raw 후보의 allowlist exact-one matching과 safe prefix collapse fallback을 이어서 시도한다.
     * absolute URL, malformed marker, invalid percent encoding 같은 실패는 host 요청 경로로 예외를
     * 전파하지 않고 bounded route 또는 {@code UNKNOWN}으로 수렴한다.</p>
     */
    public NormalizedRoute normalize(Optional<String> frameworkRouteTemplate, Optional<String> rawPathCandidate) {
        try {
            Optional<String> frameworkCandidate = frameworkCandidate(frameworkRouteTemplate);
            if (frameworkCandidate.isPresent()) {
                NormalizedRoute frameworkRoute = normalizeFrameworkRoute(frameworkCandidate.get());
                if (!frameworkRoute.isUnknown()) {
                    return frameworkRoute;
                }
            }
            return normalizeRawCandidate(rawPathCandidate).orElseGet(NormalizedRoute::unknown);
        } catch (RuntimeException ignored) {
            return NormalizedRoute.unknown();
        }
    }

    private Optional<String> frameworkCandidate(Optional<String> frameworkRouteTemplate) {
        return safeOptional(frameworkRouteTemplate)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .filter(value -> !value.equalsIgnoreCase("UNKNOWN"));
    }

    /**
     * {@code http.route} 후보를 safe template, omission marker template, safe prefix collapse 순서로 평가한다.
     */
    private NormalizedRoute normalizeFrameworkRoute(String frameworkCandidate) {
        String candidate = stripActualQueryString(frameworkCandidate).trim();
        if (!isRouteShaped(candidate)) {
            return NormalizedRoute.unknown();
        }

        Optional<NormalizedRoute> routeTemplate = normalizeRouteTemplate(candidate);
        if (routeTemplate.isPresent()) {
            return routeTemplate.get();
        }
        if (hasTemplateSyntax(candidate)) {
            return NormalizedRoute.unknown();
        }
        return safePrefixCollapse(candidate).orElseGet(NormalizedRoute::unknown);
    }

    /**
     * low-cardinality raw 후보를 query 없는 path로 줄인 뒤 allowlist exact-one, prefix collapse 순서로 평가한다.
     */
    private Optional<NormalizedRoute> normalizeRawCandidate(Optional<String> rawPathCandidate) {
        String rawPath = safeOptional(rawPathCandidate)
                .map(RouteNormalizationService::stripQueryString)
                .map(String::trim)
                .filter(RouteNormalizationService::isValidRawPathCandidate)
                .orElse(null);
        if (rawPath == null) {
            return Optional.empty();
        }

        List<NormalizedRoute> matches = allowlist.stream()
                .filter(allowedRoute -> allowedRoute.matches(rawPath))
                .map(AllowedRoute::normalizedRoute)
                .toList();
        if (matches.size() == 1) {
            return Optional.of(matches.get(0));
        }
        if (matches.size() > 1) {
            return Optional.of(NormalizedRoute.unknown());
        }
        return safePrefixCollapse(rawPath);
    }

    private static Optional<String> safeOptional(Optional<String> value) {
        return value == null ? Optional.empty() : value;
    }

    private static boolean isRouteShaped(String value) {
        return value != null
                && !value.isBlank()
                && !value.equalsIgnoreCase("UNKNOWN")
                && value.startsWith("/")
                && !value.startsWith("//")
                && !value.startsWith("http://")
                && !value.startsWith("https://")
                && !value.contains("//")
                && (value.length() == 1 || !value.endsWith("/"))
                && !containsControlCharacter(value)
                && hasValidPercentEncoding(value);
    }

    private static boolean isValidRawPathCandidate(String value) {
        if (!isRouteShaped(value)) {
            return false;
        }
        return !hasTemplateSyntax(value);
    }

    private static Optional<NormalizedRoute> normalizeRouteTemplate(String candidate) {
        try {
            return Optional.of(NormalizedRoute.of(RouteTemplateContract.normalizeTemplate(candidate)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<NormalizedRoute> safePrefixCollapse(String candidate) {
        if (!isRouteShaped(candidate) || hasTemplateSyntax(candidate)) {
            return Optional.empty();
        }

        List<String> safePrefix = new ArrayList<>();
        for (String segment : candidate.split("/", -1)) {
            if (segment.isBlank()) {
                continue;
            }
            if (isSafeLiteralSegment(segment)) {
                safePrefix.add(segment);
                continue;
            }
            if (safePrefix.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(NormalizedRoute.of("/" + String.join("/", safePrefix) + "/..."));
        }
        return Optional.empty();
    }

    private static boolean hasTemplateSyntax(String value) {
        return value.contains("{")
                || value.contains("}")
                || value.contains("*")
                || value.contains(":")
                || value.contains("[")
                || value.contains("]")
                || value.contains("(")
                || value.contains(")");
    }

    private static boolean isSafeLiteralSegment(String segment) {
        return LITERAL_ROUTE_SEGMENT.matcher(segment).matches()
                && !looksLikeConcreteIdentifier(segment)
                && !"...".equals(segment);
    }

    private static boolean looksLikeConcreteIdentifier(String segment) {
        return segment.matches("[0-9]+")
                || UUID_SEGMENT.matcher(segment).matches()
                || LONG_HEX_SEGMENT.matcher(segment).matches();
    }

    private static String stripQueryString(String value) {
        if (value == null) {
            return "";
        }
        int queryStart = value.indexOf('?');
        if (queryStart < 0) {
            return value;
        }
        return value.substring(0, queryStart);
    }

    private static String stripActualQueryString(String value) {
        if (value == null) {
            return "";
        }
        int queryStart = actualQueryStart(value);
        if (queryStart < 0) {
            return value;
        }
        return value.substring(0, queryStart);
    }

    private static int actualQueryStart(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) != '?') {
                continue;
            }
            if (value.startsWith("...", index + 1)) {
                index += 3;
                continue;
            }
            return index;
        }
        return -1;
    }

    private static boolean containsControlCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return true;
            }
        }
        return false;
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

    private record AllowedRoute(String template, Pattern pattern, NormalizedRoute normalizedRoute) {

        private AllowedRoute(String template) {
            this(template, Pattern.compile(toPathRegex(template)), NormalizedRoute.of(template));
        }

        private boolean matches(String rawPath) {
            return pattern.matcher(rawPath).matches();
        }

        private static String toPathRegex(String template) {
            if ("/".equals(template)) {
                return "^/$";
            }

            StringBuilder regex = new StringBuilder("^");
            for (String segment : template.split("/", -1)) {
                if (segment.isEmpty()) {
                    continue;
                }
                regex.append("/");
                if (isPathVariable(segment)) {
                    regex.append("[^/]+");
                } else {
                    regex.append(Pattern.quote(segment));
                }
            }
            regex.append("$");
            return regex.toString();
        }

        private static boolean isPathVariable(String segment) {
            return segment.startsWith("{") && segment.endsWith("}") && segment.length() > 2;
        }
    }
}
