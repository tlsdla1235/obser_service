package com.observation.starter.model.route;

import java.util.Objects;

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
     * route 값을 query string 없는 bounded route 문자열로 정리한다.
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
                || UNKNOWN_VALUE.equalsIgnoreCase(candidate)
                || candidate.startsWith("http://")
                || candidate.startsWith("https://")
                || !candidate.equals(UNKNOWN_VALUE) && !candidate.startsWith("/")) {
            return UNKNOWN_VALUE;
        }
        return Objects.requireNonNull(candidate);
    }

    private static String stripQueryString(String value) {
        int queryStart = value.indexOf('?');
        if (queryStart < 0) {
            return value;
        }
        return value.substring(0, queryStart);
    }
}
