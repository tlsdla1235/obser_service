package com.observation.portal.common.time;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * UTC 기준 반열림 시간 구간을 표현하는 공유 모델이다.
 *
 * <p>dashboard current/baseline window와 30초 bucket interval에서 `[startUtc, endUtc)` 의미를
 * 반복 구현하지 않도록 service/model 계층에서 함께 사용한다.</p>
 */
public record UtcTimeInterval(Instant startUtc, Instant endUtc) {

    /**
     * 시작 시각은 포함하고 종료 시각은 제외하는 유효한 UTC interval만 허용한다.
     */
    public UtcTimeInterval {
        Objects.requireNonNull(startUtc, "startUtc must not be null");
        Objects.requireNonNull(endUtc, "endUtc must not be null");
        if (!endUtc.isAfter(startUtc)) {
            throw new IllegalArgumentException("endUtc must be after startUtc");
        }
    }

    /**
     * 주어진 시각이 이 interval에 속하는지 `[startUtc, endUtc)` 기준으로 판단한다.
     */
    public boolean contains(Instant instant) {
        Instant requiredInstant = Objects.requireNonNull(instant, "instant must not be null");
        return !requiredInstant.isBefore(startUtc) && requiredInstant.isBefore(endUtc);
    }

    /**
     * interval 길이를 반환해 window 크기와 bucket duration 검증에 사용한다.
     */
    public Duration duration() {
        return Duration.between(startUtc, endUtc);
    }
}
