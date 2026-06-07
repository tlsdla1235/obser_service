package com.observation.portal.common.time;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class StarterHeartbeatFreshnessPolicyTest {

    @Test
    void usesNinetySecondsAsMinimumRecentWindow() {
        OffsetDateTime referenceAt = OffsetDateTime.parse("2026-05-27T13:05:00Z");

        assertThat(StarterHeartbeatFreshnessPolicy.recentWindow(30))
                .isEqualTo(Duration.ofSeconds(90));
        assertThat(StarterHeartbeatFreshnessPolicy.isRecent(
                OffsetDateTime.parse("2026-05-27T13:03:30Z"),
                30,
                referenceAt))
                .isTrue();
        assertThat(StarterHeartbeatFreshnessPolicy.isRecent(
                OffsetDateTime.parse("2026-05-27T13:03:29Z"),
                30,
                referenceAt))
                .isFalse();
    }

    @Test
    void usesThreeTimesReportedIntervalWhenLongerThanMinimum() {
        OffsetDateTime referenceAt = OffsetDateTime.parse("2026-05-27T13:05:00Z");

        assertThat(StarterHeartbeatFreshnessPolicy.recentWindow(60))
                .isEqualTo(Duration.ofSeconds(180));
        assertThat(StarterHeartbeatFreshnessPolicy.isRecent(
                OffsetDateTime.parse("2026-05-27T13:02:01Z"),
                60,
                referenceAt))
                .isTrue();
        assertThat(StarterHeartbeatFreshnessPolicy.isRecent(
                OffsetDateTime.parse("2026-05-27T13:01:59Z"),
                60,
                referenceAt))
                .isFalse();
    }

    @Test
    void rejectsHeartbeatsReceivedAfterReferenceTime() {
        assertThat(StarterHeartbeatFreshnessPolicy.isRecent(
                OffsetDateTime.parse("2026-05-27T13:05:01Z"),
                30,
                OffsetDateTime.parse("2026-05-27T13:05:00Z")))
                .isFalse();
    }
}
