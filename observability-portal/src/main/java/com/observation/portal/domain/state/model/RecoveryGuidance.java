package com.observation.portal.domain.state.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * stale/down 이후 새 accepted bucket은 들어왔지만 sample이 부족한 회복 관찰 구간 안내를 담는다.
 *
 * <p>이 모델은 metric state를 새 top-level recovering state로 바꾸지 않고, read model이 별도 recovery surface를 만들 수
 * 있도록 필요한 copy와 다음 판단 대기 힌트만 전달한다.</p>
 */
public record RecoveryGuidance(
        boolean isRecovering,
        Optional<Instant> lastHealthyAt,
        Optional<Integer> retryAfterSeconds,
        Optional<String> recommendedAction
) {

    public static final int RECOVERY_RETRY_AFTER_SECONDS = 30;
    public static final String RECOVERY_RECOMMENDED_ACTION =
            "다음 판단까지 약 30초 기다린 뒤 accepted bucket 수용과 sample 증가를 확인하세요.";

    /**
     * 이전 read model 또는 snapshot에서 받은 healthy 시각과 nullable copy field를 null 없이 보존한다.
     */
    public RecoveryGuidance {
        lastHealthyAt = Objects.requireNonNull(lastHealthyAt, "lastHealthyAt must not be null");
        retryAfterSeconds = Objects.requireNonNull(retryAfterSeconds, "retryAfterSeconds must not be null");
        recommendedAction = Objects.requireNonNull(recommendedAction, "recommendedAction must not be null");
        if (isRecovering) {
            if (retryAfterSeconds.isEmpty() || retryAfterSeconds.get() != RECOVERY_RETRY_AFTER_SECONDS) {
                throw new IllegalArgumentException("recovering guidance must retry after 30 seconds");
            }
            if (recommendedAction.filter(action -> !action.isBlank()).isEmpty()) {
                throw new IllegalArgumentException("recovering guidance must include recommendedAction");
            }
        } else if (retryAfterSeconds.isPresent() || recommendedAction.isPresent()) {
            throw new IllegalArgumentException("non-recovery guidance must not include retryAfterSeconds or recommendedAction");
        }
    }

    /**
     * recovery trigger가 성립한 경우 30초 bucket cadence에 맞춘 관찰 안내를 만든다.
     */
    public static RecoveryGuidance recovering(Optional<Instant> lastHealthyAt) {
        return new RecoveryGuidance(
                true,
                Objects.requireNonNull(lastHealthyAt, "lastHealthyAt must not be null"),
                Optional.of(RECOVERY_RETRY_AFTER_SECONDS),
                Optional.of(RECOVERY_RECOMMENDED_ACTION));
    }

    /**
     * recovery가 아닌 경우 retry/action은 비워 두고, 전달받은 이전 healthy source만 보존한다.
     */
    public static RecoveryGuidance notRecovering(Optional<Instant> lastHealthyAt) {
        return new RecoveryGuidance(
                false,
                Objects.requireNonNull(lastHealthyAt, "lastHealthyAt must not be null"),
                Optional.empty(),
                Optional.empty());
    }
}
