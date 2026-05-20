package com.observation.starter.model.metric;

import com.observation.starter.model.route.NormalizedRoute;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * endpoint rollup의 유일한 식별자다.
 *
 * <p>endpoint key는 HTTP method와 정규화된 route만으로 구성한다. 상태 코드, 오류 유형, 사용자
 * 식별자, session/trace 값, 임의 label은 key에 포함하지 않는다.</p>
 */
public record EndpointKey(String method, NormalizedRoute normalizedRoute) {

    private static final String UNKNOWN_METHOD = "UNKNOWN";
    private static final Set<String> PORTAL_ACCEPTED_METHODS = Set.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE", UNKNOWN_METHOD);

    /**
     * method와 normalized route만 남기도록 endpoint key 입력을 정리한다.
     */
    public EndpointKey {
        method = normalizeMethod(method);
        normalizedRoute = Objects.requireNonNull(normalizedRoute, "normalizedRoute must not be null");
    }

    /**
     * rollup map 등에서 사용할 수 있는 단일 문자열 key를 반환한다.
     */
    public String value() {
        return method + " " + normalizedRoute.value();
    }

    /**
     * HTTP method 후보를 bounded uppercase 값으로 정규화한다.
     */
    public static String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            return UNKNOWN_METHOD;
        }

        String candidate = method.trim().toUpperCase(Locale.ROOT);
        if (!candidate.matches("[A-Z]+")) {
            return UNKNOWN_METHOD;
        }
        if (!PORTAL_ACCEPTED_METHODS.contains(candidate)) {
            return UNKNOWN_METHOD;
        }
        return candidate;
    }
}
