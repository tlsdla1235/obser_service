package com.observation.portal.domain.snapshot.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardSnapshotCaptureReasonTest {

    @Test
    void keepsHourlyScheduledAsLegacyTokenForThirtyMinuteScheduledSnapshots() {
        assertThat(DashboardSnapshotCaptureReason.HOURLY_SCHEDULED.token()).isEqualTo("hourly_scheduled");
        assertThat(DashboardSnapshotCaptureReason.fromPersistedToken("scheduled")).isEmpty();
        assertThat(DashboardSnapshotCaptureReason.fromPersistedToken("thirty_minute_scheduled")).isEmpty();
        assertThat(DashboardSnapshotCaptureReason.values())
                .extracting(DashboardSnapshotCaptureReason::token)
                .doesNotContain("thirty_minute_scheduled");
    }

    @Test
    void comparesFixedPriorityInsteadOfConfidenceLikeOrdering() {
        assertThat(DashboardSnapshotCaptureReason.STATE_CHANGE.priority())
                .isGreaterThan(DashboardSnapshotCaptureReason.HIGH_CONFIDENCE_CONCERN.priority());
        assertThat(DashboardSnapshotCaptureReason.HIGH_CONFIDENCE_CONCERN.priority())
                .isGreaterThan(DashboardSnapshotCaptureReason.SHORT_STRONG_SPIKE.priority());
        assertThat(DashboardSnapshotCaptureReason.SHORT_STRONG_SPIKE.priority())
                .isGreaterThan(DashboardSnapshotCaptureReason.QUERY_FALLBACK.priority());
        assertThat(DashboardSnapshotCaptureReason.QUERY_FALLBACK.priority())
                .isGreaterThan(DashboardSnapshotCaptureReason.HOURLY_SCHEDULED.priority());

        assertThat(DashboardSnapshotCaptureReason.SHORT_STRONG_SPIKE
                .outranksPersistedToken("high_confidence_concern")).isFalse();
        assertThat(DashboardSnapshotCaptureReason.STATE_CHANGE
                .outranksPersistedToken("hourly_scheduled")).isTrue();
        assertThat(DashboardSnapshotCaptureReason.HOURLY_SCHEDULED
                .outranksPersistedToken("legacy_unknown")).isTrue();
    }
}
