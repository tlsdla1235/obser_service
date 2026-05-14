package com.observation.starter.model.metric;

import java.util.Arrays;
import java.util.Optional;

/**
 * MVP ingest path에서 허용하는 저카디널리티 식별자 이름을 코드로 고정한다.
 *
 * <p>이 enum은 임의 tag map을 열기 위한 확장 지점이 아니라, user/tenant/session/trace 같은
 * high-cardinality key가 starter payload 후보에 남지 않게 검증하는 정책이다.</p>
 */
public enum LowCardinalityTagKey {
    APPLICATION("application"),
    ENVIRONMENT("environment"),
    INSTANCE("instance"),
    METHOD("method"),
    NORMALIZED_ROUTE("normalizedRoute");

    private final String externalKey;

    LowCardinalityTagKey(String externalKey) {
        this.externalKey = externalKey;
    }

    /**
     * ingest contract와 metric taxonomy에서 사용하는 외부 key 이름이다.
     */
    public String externalKey() {
        return externalKey;
    }

    /**
     * 외부 tag key가 MVP allowlist에 포함되는지 확인한다.
     */
    public static boolean isAllowedExternalKey(String key) {
        return fromExternalKey(key).isPresent();
    }

    /**
     * 외부 tag key를 허용 enum 값으로 변환한다.
     */
    public static Optional<LowCardinalityTagKey> fromExternalKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String candidate = key.trim();
        return Arrays.stream(values())
                .filter(value -> value.externalKey.equals(candidate))
                .findFirst();
    }
}
