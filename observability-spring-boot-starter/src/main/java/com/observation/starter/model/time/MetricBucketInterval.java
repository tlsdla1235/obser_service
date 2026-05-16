package com.observation.starter.model.time;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * starter가 로컬 메트릭을 집계할 때 사용하는 UTC 30초 bucket interval이다.
 *
 * <p>interval은 {@code [startUtc, endUtc)} 의미를 따른다. 시작 시각은 UTC epoch 기준
 * 30초 boundary에 정렬되어야 하며 종료 시각은 항상 시작 시각에서 30초 뒤다.</p>
 */
public record MetricBucketInterval(Instant startUtc, Instant endUtc) implements Comparable<MetricBucketInterval> {

    public static final Duration DURATION = Duration.ofSeconds(30);
    private static final long DURATION_SECONDS = DURATION.toSeconds();

    /**
     * bucket interval의 boundary 정렬과 고정 duration을 검증한다.
     */
    public MetricBucketInterval {
        startUtc = Objects.requireNonNull(startUtc, "startUtc must not be null");
        endUtc = Objects.requireNonNull(endUtc, "endUtc must not be null");
        if (!isBoundaryAligned(startUtc)) {
            throw new IllegalArgumentException("startUtc must be aligned to a UTC 30 second boundary");
        }
        if (!endUtc.equals(startUtc.plus(DURATION))) {
            throw new IllegalArgumentException("endUtc must be exactly 30 seconds after startUtc");
        }
    }

    /**
     * 관측 시각을 포함하는 UTC 30초 bucket interval을 반환한다.
     * observedAt = 12:34:56 이라면
     * bucket     = 12:34:30 ~ 12:35:00
     */
    public static MetricBucketInterval containing(Instant observedAt) {
        Instant requiredObservedAt = Objects.requireNonNull(observedAt, "observedAt must not be null");
        long bucketStartEpochSecond = Math.floorDiv(requiredObservedAt.getEpochSecond(), DURATION_SECONDS)
                * DURATION_SECONDS;
        Instant start = Instant.ofEpochSecond(bucketStartEpochSecond);
        return new MetricBucketInterval(start, start.plus(DURATION));
    }

    /**
     * 입력 시각이 이 interval의 {@code [startUtc, endUtc)} 범위에 속하는지 확인한다.
     */
    public boolean contains(Instant instant) {
        Instant requiredInstant = Objects.requireNonNull(instant, "instant must not be null");
        return !requiredInstant.isBefore(startUtc) && requiredInstant.isBefore(endUtc);
    }

    @Override
    public int compareTo(MetricBucketInterval other) {
        return startUtc.compareTo(other.startUtc);
    }

    private static boolean isBoundaryAligned(Instant instant) {
        return instant.getNano() == 0 && Math.floorMod(instant.getEpochSecond(), DURATION_SECONDS) == 0;
    }
}
