package com.observation.starter.model.route;

import java.util.regex.Pattern;

/**
 * portal ingest boundary가 허용하는 normalized route template 규칙이다.
 *
 * <p>starter가 만든 route가 portal에서 거절되지 않도록 framework route와 allowlist route가 같은
 * shape 검증을 공유한다. normalized route에는 bounded marker인 {@code ?...}와 {@code /...}를
 * 허용하지만, allowlist 설정은 path template 전용 검증을 별도로 사용한다.</p>
 */
public final class RouteTemplateContract {

    private static final String OMISSION_MARKER = "?...";
    private static final String COLLAPSE_MARKER = "...";
    private static final Pattern UUID_SEGMENT = Pattern.compile(
            "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern LONG_HEX_SEGMENT = Pattern.compile("(?i)[0-9a-f]{8,}");
    private static final Pattern LITERAL_ROUTE_SEGMENT = Pattern.compile("[A-Za-z0-9._~-]+");

    private RouteTemplateContract() {
    }

    /**
     * normalized route 값을 portal ingest contract가 받을 수 있는 형태로 검증하고 trim한 값을 반환한다.
     *
     * <p>{@code ?...}는 raw query 값이 아니라 bounded omission marker일 때만 허용하고,
     * {@code /...}는 안전한 prefix 뒤에 붙은 collapse marker일 때만 허용한다.</p>
     */
    public static String normalizeTemplate(String template) {
        String candidate = normalizeCommonShape(template);
        if (hasCollapseMarker(candidate)) {
            return normalizeCollapseTemplate(candidate);
        }
        return normalizeRouteSegments(removeOmissionMarker(candidate), candidate);
    }

    /**
     * allowlist 설정에 들어갈 수 있는 path template만 검증하고 trim한 값을 반환한다.
     *
     * <p>allowlist는 raw path를 declared template으로 귀속시키는 설정이므로 query key/value,
     * {@code ?...} omission marker, {@code /...} collapse marker를 허용하지 않는다.</p>
     */
    public static String normalizePathTemplate(String template) {
        String candidate = normalizeCommonShape(template);
        if (candidate.contains("?")) {
            throw new IllegalArgumentException("route template must not contain query string");
        }
        if (hasCollapseMarker(candidate)) {
            throw new IllegalArgumentException("allowlist route template must not contain collapse marker");
        }
        return normalizeRouteSegments(candidate, candidate);
    }

    /**
     * 예외를 노출하지 않고 normalized route template shape만 확인한다.
     */
    public static boolean isValidTemplate(String template) {
        try {
            normalizeTemplate(template);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    /**
     * 예외를 노출하지 않고 allowlist path template shape만 확인한다.
     */
    public static boolean isValidPathTemplate(String template) {
        try {
            normalizePathTemplate(template);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static String normalizeCommonShape(String template) {
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("route template must not be blank");
        }

        String candidate = template.trim();
        if ("UNKNOWN".equalsIgnoreCase(candidate)) {
            throw new IllegalArgumentException("route template must not be UNKNOWN");
        }
        if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
            throw new IllegalArgumentException("route template must not be an absolute URL");
        }
        if (!candidate.startsWith("/") || candidate.startsWith("//") || candidate.contains("//")) {
            throw new IllegalArgumentException("route template must start with one slash");
        }
        if (candidate.length() > 1 && candidate.endsWith("/")) {
            throw new IllegalArgumentException("route template must not end with slash");
        }
        if (containsControlCharacter(candidate) || !hasValidPercentEncoding(candidate)) {
            throw new IllegalArgumentException("route template contains unsupported characters");
        }
        return candidate;
    }

    private static String normalizeCollapseTemplate(String candidate) {
        if (candidate.contains("?")) {
            throw new IllegalArgumentException("collapse route template must not contain query string");
        }
        if (!candidate.endsWith("/" + COLLAPSE_MARKER) || collapseMarkerCount(candidate) != 1) {
            throw new IllegalArgumentException("collapse marker must be the final route segment");
        }

        String prefix = candidate.substring(0, candidate.length() - (COLLAPSE_MARKER.length() + 1));
        if (prefix.isBlank()) {
            throw new IllegalArgumentException("collapse marker requires a safe route prefix");
        }
        normalizePathTemplate(prefix);
        return candidate;
    }

    private static String normalizeRouteSegments(String structuralCandidate, String returnedCandidate) {
        for (String segment : structuralCandidate.split("/", -1)) {
            if (segment.isBlank()) {
                continue;
            }
            if (isTemplateVariable(segment)) {
                continue;
            }
            if (segment.contains("{") || segment.contains("}") || segment.contains("*")
                    || segment.contains("?") || COLLAPSE_MARKER.equals(segment)
                    || !LITERAL_ROUTE_SEGMENT.matcher(segment).matches()) {
                throw new IllegalArgumentException("route template must be normalized");
            }
            if (looksLikeConcreteIdentifier(segment)) {
                throw new IllegalArgumentException("route template must not contain concrete identifiers");
            }
        }
        return returnedCandidate;
    }

    private static String removeOmissionMarker(String candidate) {
        int markerIndex = candidate.indexOf(OMISSION_MARKER);
        if (markerIndex < 0) {
            if (candidate.contains("?")) {
                throw new IllegalArgumentException("route template must not contain query string");
            }
            return candidate;
        }
        if (candidate.indexOf(OMISSION_MARKER, markerIndex + OMISSION_MARKER.length()) >= 0
                || candidate.indexOf('?') != markerIndex) {
            throw new IllegalArgumentException("route template must contain at most one omission marker");
        }
        if (markerIndex == 0 || candidate.charAt(markerIndex - 1) == '/') {
            throw new IllegalArgumentException("omission marker must follow a route segment");
        }
        int afterMarker = markerIndex + OMISSION_MARKER.length();
        if (afterMarker < candidate.length() && candidate.charAt(afterMarker) != '/') {
            throw new IllegalArgumentException("omission marker must be followed by slash or route end");
        }

        String structuralCandidate = candidate.substring(0, markerIndex) + candidate.substring(afterMarker);
        if (!structuralCandidate.startsWith("/")
                || structuralCandidate.startsWith("//")
                || structuralCandidate.contains("//")
                || structuralCandidate.length() > 1 && structuralCandidate.endsWith("/")) {
            throw new IllegalArgumentException("omission marker must leave a valid route template");
        }
        return structuralCandidate;
    }

    private static boolean hasCollapseMarker(String candidate) {
        return collapseMarkerCount(candidate) > 0;
    }

    private static int collapseMarkerCount(String candidate) {
        int count = 0;
        for (String segment : candidate.split("/", -1)) {
            if (COLLAPSE_MARKER.equals(segment)) {
                count++;
            }
        }
        return count;
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
}
