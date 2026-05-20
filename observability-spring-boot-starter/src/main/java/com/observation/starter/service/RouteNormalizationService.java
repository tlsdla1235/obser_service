package com.observation.starter.service;

import com.observation.starter.config.RouteAttributionProperties;
import com.observation.starter.model.route.NormalizedRoute;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * framework route template 우선, configured allowlist fallback 순서로 route를 정규화한다.
 *
 * <p>raw request path는 {@code http.route}가 없을 때 allowlist template과 매칭하는 동안만
 * 일시적으로 사용한다. 반환값은 framework template, allowlist template, {@code UNKNOWN}뿐이다.</p>
 */
public final class RouteNormalizationService {

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
     * <p>framework template이 있으면 가장 먼저 사용한다. framework template이 없을 때만 raw path
     * 후보의 query를 폐기한 뒤 allowlist exact-one matching에 사용한다. miss, ambiguous match,
     * invalid path, absolute URL, decoding failure는 모두 {@code UNKNOWN}으로 수렴한다.</p>
     */
    public NormalizedRoute normalize(Optional<String> frameworkRouteTemplate, Optional<String> rawPathCandidate) {
        try {
            Optional<String> frameworkCandidate = frameworkCandidate(frameworkRouteTemplate);
            if (frameworkCandidate.isPresent()) {
                return normalizeFrameworkRoute(frameworkCandidate).orElseGet(NormalizedRoute::unknown);
            }
            return normalizeAllowlistMatch(rawPathCandidate).orElseGet(NormalizedRoute::unknown);
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
    // /orders/{orderId}?debug=true는 query를 제거해서 /orders/{orderId}가 된다
    private Optional<NormalizedRoute> normalizeFrameworkRoute(Optional<String> frameworkCandidate) {
        return frameworkCandidate
                .map(RouteNormalizationService::stripQueryString)
                .map(String::trim)
                .flatMap(RouteNormalizationService::normalizeRouteTemplate);
    }
    // http.route가 없을 때 raw path 후보를 allowlist에 매칭합니다.
    private Optional<NormalizedRoute> normalizeAllowlistMatch(Optional<String> rawPathCandidate) {
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
        //매칭 결과가 정확히 하나여야한다.
        if (matches.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(matches.get(0));
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
                && !containsControlCharacter(value)
                && hasValidPercentEncoding(value);
    }

    private static boolean isValidRawPathCandidate(String value) {
        if (!isRouteShaped(value)) {
            return false;
        }
        return !hasTemplateMarker(value);
    }

    private static Optional<NormalizedRoute> normalizeRouteTemplate(String candidate) {
        try {
            return Optional.of(NormalizedRoute.of(RouteAttributionProperties.normalizeAllowlistTemplate(candidate)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static boolean hasTemplateMarker(String value) {
        return value.contains("{") && value.contains("}")
                || value.contains("*");
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
