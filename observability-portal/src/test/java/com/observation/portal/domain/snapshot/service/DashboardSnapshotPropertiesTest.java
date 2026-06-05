package com.observation.portal.domain.snapshot.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardSnapshotPropertiesTest {

    @Test
    void exposesDelayGraceAndComputedFallbackDefaults() {
        DashboardSnapshotProperties properties = new DashboardSnapshotProperties();

        assertThat(properties.getCaptureDelay()).isEqualTo(Duration.ofSeconds(120));
        assertThat(properties.getFallbackGrace()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.fallbackStalenessThreshold()).isEqualTo(Duration.ofMinutes(67));
        assertThat(properties.snapshotCutoffAt(OffsetDateTime.parse("2026-05-27T13:00:00Z")))
                .isEqualTo(OffsetDateTime.parse("2026-05-27T13:02:00Z"));
    }

    @Test
    void rejectsNonPositiveDurations() {
        DashboardSnapshotProperties properties = new DashboardSnapshotProperties();

        assertThatThrownBy(() -> properties.setCaptureDelay(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("captureDelay");
        assertThatThrownBy(() -> properties.setFallbackGrace(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fallbackGrace");
    }
}
