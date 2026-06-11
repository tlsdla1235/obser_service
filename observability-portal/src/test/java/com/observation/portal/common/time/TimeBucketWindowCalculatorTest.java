package com.observation.portal.common.time;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TimeBucketWindowCalculatorTest {

    private static final Instant QUERY_AT = Instant.parse("2026-05-08T01:07:17Z");

    @Test
    void calculatesRecentThirtyMinuteWindowFromInjectedClock() {
        TimeBucketWindowCalculator calculator = new TimeBucketWindowCalculator(
                Clock.fixed(QUERY_AT, ZoneOffset.UTC));

        DashboardTimeWindow window = calculator.dashboardWindowAtCurrentTime();

        assertThat(window.queryAtUtc()).isEqualTo(QUERY_AT);
        assertThat(window.current()).isEqualTo(new UtcTimeInterval(
                Instant.parse("2026-05-08T00:37:17Z"),
                QUERY_AT));
        assertThat(window.recent30Minutes()).isEqualTo(window.current());
        assertThat(window.baseline()).isNull();
        assertThat(window.current().duration()).isEqualTo(Duration.ofMinutes(30));
        assertThat(window.bucketDuration()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void recentThirtyMinuteRepositoryBoundaryIsStartExclusiveAndEndInclusive() {
        TimeBucketWindowCalculator calculator = new TimeBucketWindowCalculator(
                Clock.fixed(QUERY_AT, ZoneOffset.UTC));

        DashboardTimeWindow window = calculator.dashboardWindowEndingAt(Instant.parse("2026-05-08T01:10:30Z"));
        UtcTimeInterval recent = window.recent30Minutes();

        assertThat(recent.startUtc()).isEqualTo(Instant.parse("2026-05-08T00:40:30Z"));
        assertThat(recent.endUtc()).isEqualTo(Instant.parse("2026-05-08T01:10:30Z"));
        assertThat(bucketEndMatchesRepositoryBoundary(recent, recent.startUtc())).isFalse();
        assertThat(bucketEndMatchesRepositoryBoundary(recent, recent.startUtc().plusMillis(1))).isTrue();
        assertThat(bucketEndMatchesRepositoryBoundary(recent, recent.endUtc())).isTrue();
        assertThat(bucketEndMatchesRepositoryBoundary(recent, recent.endUtc().plusMillis(1))).isFalse();
    }

    @Test
    void intervalUsesStartInclusiveAndEndExclusiveSemantics() {
        UtcTimeInterval interval = new UtcTimeInterval(
                Instant.parse("2026-05-08T01:00:00Z"),
                Instant.parse("2026-05-08T01:00:30Z"));

        assertThat(interval.contains(Instant.parse("2026-05-08T01:00:00Z"))).isTrue();
        assertThat(interval.contains(Instant.parse("2026-05-08T01:00:29.999Z"))).isTrue();
        assertThat(interval.contains(Instant.parse("2026-05-08T01:00:30Z"))).isFalse();
    }

    @Test
    void calculatesUtcThirtySecondBucketBoundaries() {
        TimeBucketWindowCalculator calculator = new TimeBucketWindowCalculator(
                Clock.fixed(QUERY_AT, ZoneOffset.UTC));

        assertThat(calculator.bucketContaining(Instant.parse("2026-05-08T01:00:29.999Z")))
                .isEqualTo(new UtcTimeInterval(
                        Instant.parse("2026-05-08T01:00:00Z"),
                        Instant.parse("2026-05-08T01:00:30Z")));
        assertThat(calculator.bucketContaining(Instant.parse("2026-05-08T01:00:30Z")))
                .isEqualTo(new UtcTimeInterval(
                        Instant.parse("2026-05-08T01:00:30Z"),
                        Instant.parse("2026-05-08T01:01:00Z")));
        assertThat(calculator.isBucketBoundary(Instant.parse("2026-05-08T01:00:30Z"))).isTrue();
        assertThat(calculator.isBucketBoundary(Instant.parse("2026-05-08T01:00:30.001Z"))).isFalse();
    }

    /**
     * repository의 accepted bucket 조회는 interval helper와 달리 `(start, end]` bucket_end boundary를 쓴다.
     */
    private static boolean bucketEndMatchesRepositoryBoundary(UtcTimeInterval window, Instant bucketEndUtc) {
        return bucketEndUtc.isAfter(window.startUtc()) && !bucketEndUtc.isAfter(window.endUtc());
    }
}
