package com.observation.portal.domain.snapshot.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * dashboard snapshot capture 지연과 fallback grace 설정을 한 곳에서 계산하는 properties다.
 *
 * <p>scheduler, fallback capture, writer enrichment가 같은 delay/grace 의미를 공유하도록 threshold와 cutoff helper를
 * 제공한다.</p>
 */
@Component
@ConfigurationProperties(prefix = "portal.dashboard-snapshots")
public class DashboardSnapshotProperties {

    private static final Duration BASE_FALLBACK_STALENESS = Duration.ofMinutes(30);

    private Duration captureDelay = Duration.ofSeconds(120);
    private Duration fallbackGrace = Duration.ofMinutes(5);

    /**
     * 30분 scheduled slot boundary 이후 accepted bucket을 기다릴 capture delay다.
     */
    public Duration getCaptureDelay() {
        return captureDelay;
    }

    /**
     * capture delay는 snapshot cutoff 계산에 직접 쓰이므로 0보다 큰 값만 허용한다.
     */
    public void setCaptureDelay(Duration captureDelay) {
        this.captureDelay = requirePositive(captureDelay, "captureDelay");
    }

    /**
     * query fallback staleness 판단에 더하는 운영 grace다.
     */
    public Duration getFallbackGrace() {
        return fallbackGrace;
    }

    /**
     * fallback grace는 capture delay 이후 여유 시간이라 0보다 큰 값만 허용한다.
     */
    public void setFallbackGrace(Duration fallbackGrace) {
        this.fallbackGrace = requirePositive(fallbackGrace, "fallbackGrace");
    }

    /**
     * query fallback이 30분 scheduled cadence보다 충분히 오래된 snapshot으로 보는 기준값이다.
     */
    public Duration fallbackStalenessThreshold() {
        return BASE_FALLBACK_STALENESS
                .plus(captureDelay)
                .plus(fallbackGrace);
    }

    /**
     * 30분 target boundary에 capture delay를 더해 accepted_at cutoff timestamp를 만든다.
     */
    public OffsetDateTime snapshotCutoffAt(OffsetDateTime currentWindowEndUtc) {
        return Objects.requireNonNull(currentWindowEndUtc, "currentWindowEndUtc must not be null")
                .withOffsetSameInstant(ZoneOffset.UTC)
                .plus(captureDelay);
    }

    private static Duration requirePositive(Duration value, String fieldName) {
        Duration required = Objects.requireNonNull(value, fieldName + " must not be null");
        if (required.isZero() || required.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return required;
    }
}
