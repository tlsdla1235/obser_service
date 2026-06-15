package com.observation.portal.domain.state.model;

/**
 * degraded 진입/해소 hysteresis에 필요한 rule 계산 결과만 담는 typed input이다.
 *
 * <p>이 record는 p95/p99, endpoint priority, rule 자체를 계산하지 않고 이미 계산된 guard/confidence/bucket count만 전달한다.</p>
 */
public record DegradedHysteresisInput(
        boolean concernPresent,
        boolean ruleGuardPassed,
        double confidence,
        int badBucketsInRecentFive,
        boolean recoveryConditionMet,
        int recoveryConsecutiveBuckets
) {

    public static final double ENTER_CONFIDENCE_THRESHOLD = 0.75;
    public static final double ATTENTION_CONFIDENCE_THRESHOLD = 0.65;
    public static final double RESOLVE_CONFIDENCE_THRESHOLD = 0.60;
    public static final int ENTER_BAD_BUCKET_THRESHOLD = 3;
    public static final int RECENT_BUCKET_WINDOW = 5;
    public static final int RESOLVE_CONSECUTIVE_BUCKETS = 5;

    /**
     * confidence와 bucket count가 service threshold 비교에 안전한 범위인지 검증한다.
     */
    public DegradedHysteresisInput {
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        if (badBucketsInRecentFive < 0 || badBucketsInRecentFive > RECENT_BUCKET_WINDOW) {
            throw new IllegalArgumentException("badBucketsInRecentFive must be between 0 and 5");
        }
        if (recoveryConsecutiveBuckets < 0) {
            throw new IllegalArgumentException("recoveryConsecutiveBuckets must be greater than or equal to 0");
        }
    }

    /**
     * 테스트와 adapter code에서 명시적으로 hysteresis 입력을 만들 때 사용하는 factory다.
     */
    public static DegradedHysteresisInput of(
            boolean concernPresent,
            boolean ruleGuardPassed,
            double confidence,
            int badBucketsInRecentFive,
            boolean recoveryConditionMet,
            int recoveryConsecutiveBuckets) {
        return new DegradedHysteresisInput(
                concernPresent,
                ruleGuardPassed,
                confidence,
                badBucketsInRecentFive,
                recoveryConditionMet,
                recoveryConsecutiveBuckets);
    }

    /**
     * concern 후보가 없는 기본 입력을 만든다.
     */
    public static DegradedHysteresisInput noConcern() {
        return new DegradedHysteresisInput(false, true, 0.0, 0, false, 0);
    }

    /**
     * degraded로 새로 진입할 수 있는지 Story 4.2 threshold만 적용해 판단한다.
     */
    public boolean canEnterDegraded() {
        return concernPresent
                && ruleGuardPassed
                && confidence >= ENTER_CONFIDENCE_THRESHOLD
                && badBucketsInRecentFive >= ENTER_BAD_BUCKET_THRESHOLD;
    }

    /**
     * degraded bad bucket threshold에는 못 미치지만 RED concern을 주의 상태로 표시할 수 있는지 판단한다.
     */
    public boolean canEnterAttention() {
        return concernPresent
                && ruleGuardPassed
                && confidence >= ATTENTION_CONFIDENCE_THRESHOLD;
    }

    /**
     * 기존 degraded 상태를 해소할 만큼 recovery 조건이 연속 유지됐는지 판단한다.
     */
    public boolean canResolveDegraded() {
        return recoveryConsecutiveBuckets >= RESOLVE_CONSECUTIVE_BUCKETS
                && (!concernPresent || confidence < RESOLVE_CONFIDENCE_THRESHOLD || recoveryConditionMet);
    }
}
