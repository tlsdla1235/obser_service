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
    void calculatesCurrentAndBaselineWindowsFromInjectedClock() {
        TimeBucketWindowCalculator calculator = new TimeBucketWindowCalculator(
                Clock.fixed(QUERY_AT, ZoneOffset.UTC));

        DashboardTimeWindow window = calculator.dashboardWindowAtCurrentTime();

        assertThat(window.queryAtUtc()).isEqualTo(QUERY_AT);
        assertThat(window.current()).isEqualTo(new UtcTimeInterval(
                Instant.parse("2026-05-08T00:52:17Z"),
                QUERY_AT));
        assertThat(window.baseline()).isEqualTo(new UtcTimeInterval(
                Instant.parse("2026-05-08T00:37:17Z"),
                Instant.parse("2026-05-08T00:52:17Z")));
        assertThat(window.bucketDuration()).isEqualTo(Duration.ofSeconds(30));
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
}
