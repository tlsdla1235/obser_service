package com.observation.starter.model.route;

import java.util.regex.Pattern;

/**
 * portal ingest boundary가 허용하는 normalized route template 규칙이다.
 *
 * <p>starter가 만든 route가 portal에서 거절되지 않도록 framework route와 allowlist route가 같은
 * shape 검증을 공유한다.</p>
 */
public final class RouteTemplateContract {

    private static final Pattern UUID_SEGMENT = Pattern.compile(
            "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern LONG_HEX_SEGMENT = Pattern.compile("(?i)[0-9a-f]{8,}");
    private static final Pattern LITERAL_ROUTE_SEGMENT = Pattern.compile("[A-Za-z0-9._~-]+");

    private RouteTemplateContract() {
    }

    /**
     * route template을 portal ingest contract가 받을 수 있는 형태로 검증하고 trim한 값을 반환한다.
     */
    public static String normalizeTemplate(String template) {
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("route template must not be blank");
        }

        String candidate = template.trim();
        if ("UNKNOWN".equalsIgnoreCase(candidate)) {
            throw new IllegalArgumentException("route template must not be UNKNOWN");
        }
        if (candidate.contains("?")) {
            throw new IllegalArgumentException("route template must not contain query string");
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

        for (String segment : candidate.split("/")) {
            if (segment.isBlank()) {
                continue;
            }
            if (isTemplateVariable(segment)) {
                continue;
            }
            if (segment.contains("{") || segment.contains("}") || segment.contains("*")
                    || !LITERAL_ROUTE_SEGMENT.matcher(segment).matches()) {
                throw new IllegalArgumentException("route template must be normalized");
            }
            if (looksLikeConcreteIdentifier(segment)) {
                throw new IllegalArgumentException("route template must not contain concrete identifiers");
            }
        }
        return candidate;
    }

    /**
     * 예외를 노출하지 않고 route template shape만 확인한다.
     */
    public static boolean isValidTemplate(String template) {
        try {
            normalizeTemplate(template);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
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
