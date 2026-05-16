package com.observation.starter.model.metric;

/**
 * duration histogram의 cumulative bucket 한 칸이다.
 *
 * <p>{@code leMs}는 "less than or equal" millisecond upper bound이고,
 * {@code count}는 해당 upper bound 이하로 관측된 누적 요청 수다.</p>
 */
public record HistogramBucket(long leMs, long count) {

    /**
     * histogram bucket upper bound와 count의 기본 제약을 검증한다.
     */
    public HistogramBucket {
        if (leMs <= 0) {
            throw new IllegalArgumentException("leMs must be positive");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
    }
}
