package com.observation.portal.common.time;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AcceptedBucketFreshnessTest {

    private static final Instant QUERY_AT = Instant.parse("2026-05-08T01:10:00Z");

    @Test
    void reportsWaitingFirstDataWhenNoAcceptedBucketExists() {
        AcceptedBucketFreshnessEvaluator evaluator = new AcceptedBucketFreshnessEvaluator(
                Clock.fixed(QUERY_AT, ZoneOffset.UTC));

        AcceptedBucketFreshness freshness = evaluator.evaluate(null);

        assertThat(freshness.status()).isEqualTo(AcceptedBucketFreshnessStatus.WAITING_FIRST_DATA);
        assertThat(freshness.lastAcceptedBucketEndUtc()).isEmpty();
        assertThat(freshness.age()).isEmpty();
    }

    @Test
    void usesLastAcceptedBucketEndUtcForFreshnessThresholds() {
        AcceptedBucketFreshnessEvaluator evaluator = new AcceptedBucketFreshnessEvaluator(
                Clock.fixed(QUERY_AT, ZoneOffset.UTC));

        assertThat(evaluator.evaluate(QUERY_AT.minusMillis(89_999)).status())
                .isEqualTo(AcceptedBucketFreshnessStatus.CURRENT);
        assertThat(evaluator.evaluate(QUERY_AT.minusSeconds(90)).status())
                .isEqualTo(AcceptedBucketFreshnessStatus.STALE_CANDIDATE);
        assertThat(evaluator.evaluate(QUERY_AT.minusMillis(179_999)).status())
                .isEqualTo(AcceptedBucketFreshnessStatus.STALE_CANDIDATE);
        assertThat(evaluator.evaluate(QUERY_AT.minusSeconds(180)).status())
                .isEqualTo(AcceptedBucketFreshnessStatus.DOWN_CANDIDATE);
    }

    @Test
    void exposesFreshnessAgeWithoutLifecycleStateDecision() {
        AcceptedBucketFreshnessEvaluator evaluator = new AcceptedBucketFreshnessEvaluator(
                Clock.fixed(QUERY_AT, ZoneOffset.UTC));

        AcceptedBucketFreshness freshness = evaluator.evaluate(Instant.parse("2026-05-08T01:08:00Z"));

        assertThat(freshness.evaluatedAtUtc()).isEqualTo(QUERY_AT);
        assertThat(freshness.lastAcceptedBucketEndUtc()).contains(Instant.parse("2026-05-08T01:08:00Z"));
        assertThat(freshness.age()).contains(Duration.ofSeconds(120));
        assertThat(freshness.status()).isEqualTo(AcceptedBucketFreshnessStatus.STALE_CANDIDATE);
    }
}
