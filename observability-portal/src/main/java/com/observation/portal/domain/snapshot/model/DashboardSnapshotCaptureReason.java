package com.observation.portal.domain.snapshot.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * 5.8-a writer가 새 snapshot row에 저장할 수 있는 닫힌 capture reason token이다.
 *
 * <p>DB column은 legacy/null 값을 허용하지만, writer 입력은 이 enum으로 제한해 `scheduled` 같은 legacy token이
 * 새 write에 섞이지 않게 한다.</p>
 */
public enum DashboardSnapshotCaptureReason {
    STATE_CHANGE("state_change", 5),
    HIGH_CONFIDENCE_CONCERN("high_confidence_concern", 4),
    SHORT_STRONG_SPIKE("short_strong_spike", 3),
    QUERY_FALLBACK("query_fallback", 2),
    HOURLY_SCHEDULED("hourly_scheduled", 1);

    private final String token;
    private final int priority;

    DashboardSnapshotCaptureReason(String token, int priority) {
        this.token = token;
        this.priority = priority;
    }

    /**
     * `dashboard_snapshots.capture_reason`에 저장할 canonical token을 반환한다.
     */
    public String token() {
        return token;
    }

    /**
     * 같은 window의 대표 row를 고를 때 사용하는 고정 priority 값이다.
     *
     * <p>confidence 숫자가 아니라 이 값만으로 downgrade/update 여부를 판단한다.</p>
     */
    public int priority() {
        return priority;
    }

    /**
     * persisted token을 writer priority 비교에 사용할 수 있으면 enum으로 복원한다.
     *
     * <p>null, blank, unknown, legacy token은 empty로 두어 read-side opaque metadata로 유지한다.</p>
     */
    public static Optional<DashboardSnapshotCaptureReason> fromPersistedToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(reason -> reason.token.equals(normalized))
                .findFirst();
    }

    /**
     * persisted token보다 이 reason이 더 높은 대표 priority인지 판단한다.
     *
     * <p>기존 row의 token이 null/unknown/legacy이면 모든 canonical incoming reason이 upgrade로 취급된다.</p>
     */
    public boolean outranksPersistedToken(String persistedToken) {
        return fromPersistedToken(persistedToken)
                .map(existing -> priority > existing.priority)
                .orElse(true);
    }
}
