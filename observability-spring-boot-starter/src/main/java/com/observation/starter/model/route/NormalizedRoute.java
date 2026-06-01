package com.observation.starter.model.route;

/**
 * starter 내부에서 endpoint 식별자로 사용할 수 있는 정규화된 route 값이다.
 *
 * <p>이 값 타입은 query string과 URL 형태 값을 보관하지 않는다. route를 안전하게 정규화하지
 * 못한 경우에는 원본 path 대신 {@link #unknown()}을 사용해 이후 rollup과 envelope 경계가
 * raw path를 받을 수 없게 한다.</p>
 */
public record NormalizedRoute(String value) {

    private static final String UNKNOWN_VALUE = "UNKNOWN";

    /**
     * route 값을 query key/value 없는 bounded route 문자열로 정리한다.
     *
     * <p>{@code ?...}는 raw query가 아니라 route omission marker이므로 보존하고, 그 외
     * {@code ?key=value} suffix는 route 값에 남기지 않는다.</p>
     */
    public NormalizedRoute {
        value = normalize(value);
    }

    /**
     * 안전한 route를 결정하지 못했을 때 사용하는 고정 fallback route다.
     */
    public static NormalizedRoute unknown() {
        return new NormalizedRoute(UNKNOWN_VALUE);
    }

    /**
     * 외부 후보 문자열을 정규화된 route 값으로 감싼다.
     */
    public static NormalizedRoute of(String value) {
        return new NormalizedRoute(value);
    }

    /**
     * 이 route가 unknown fallback인지 확인한다.
     */
    public boolean isUnknown() {
        return UNKNOWN_VALUE.equals(value);
    }

    private static String normalize(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return UNKNOWN_VALUE;
        }

        String candidate = stripQueryString(rawValue.trim());
        if (candidate.isBlank()
                || UNKNOWN_VALUE.equalsIgnoreCase(candidate)) {
            return UNKNOWN_VALUE;
        }
        try {
            return RouteTemplateContract.normalizeTemplate(candidate);
        } catch (IllegalArgumentException ignored) {
            return UNKNOWN_VALUE;
        }
    }

    private static String stripQueryString(String value) {
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
}
