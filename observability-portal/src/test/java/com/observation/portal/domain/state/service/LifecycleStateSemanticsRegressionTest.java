package com.observation.portal.domain.state.service;

import com.observation.portal.common.time.AcceptedBucketFreshness;
import com.observation.portal.common.time.AcceptedBucketFreshnessEvaluator;
import com.observation.portal.common.time.AcceptedBucketFreshnessStatus;
import com.observation.portal.domain.state.model.DegradedHysteresisInput;
import com.observation.portal.domain.state.model.LifecycleStateCode;
import com.observation.portal.domain.state.model.LifecycleStateDecision;
import com.observation.portal.domain.state.model.MetricLifecycleInput;
import com.observation.portal.domain.state.model.MetricSampleReadiness;
import com.observation.portal.domain.state.model.MetricTrafficActivity;
import com.observation.portal.domain.state.model.RecoveryGuidance;
import com.observation.portal.domain.state.model.StarterConnectionDiagnosis;
import com.observation.portal.domain.state.model.StarterConnectionInput;
import com.observation.portal.domain.state.model.StarterConnectionMeaning;
import com.observation.portal.domain.state.model.StarterStateImpact;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 4.4의 state semantics 회귀 테스트다.
 *
 * <p>accepted bucket metric axis와 starter heartbeat connection axis를 의도적으로 분리한 fixture로 후속 read model/API/UI
 * 구현자가 두 의미를 섞지 않도록 계약을 고정한다.</p>
 */
class LifecycleStateSemanticsRegressionTest {

    private static final Instant QUERY_AT = Instant.parse("2026-05-08T01:10:00Z");
    private static final Instant RECENT_HEARTBEAT_AT = Instant.parse("2026-05-08T01:09:50Z");
    private static final Instant STALE_HEARTBEAT_AT = Instant.parse("2026-05-08T01:04:00Z");
    private static final Instant LAST_HEALTHY_AT = Instant.parse("2026-05-08T01:08:30Z");

    private final AcceptedBucketFreshnessEvaluator freshnessEvaluator = new AcceptedBucketFreshnessEvaluator(
            Clock.fixed(QUERY_AT, ZoneOffset.UTC));
    private final MetricAxisFixture metricAxis = new MetricAxisFixture(freshnessEvaluator);
    private final StarterAxisFixture starterAxis = new StarterAxisFixture();
    private final LifecycleStateService service = new LifecycleStateService();

    @Test
    void acceptedBucketEndUtcAloneDrivesFreshnessThresholds() {
        assertThat(metricAxis.noAcceptedBucket().freshness().status())
                .isEqualTo(AcceptedBucketFreshnessStatus.WAITING_FIRST_DATA);
        assertThat(metricAxis.currentBucket(Duration.ofMillis(89_999)).freshness().status())
                .isEqualTo(AcceptedBucketFreshnessStatus.CURRENT);
        assertThat(metricAxis.currentBucket(Duration.ofSeconds(90)).freshness().status())
                .isEqualTo(AcceptedBucketFreshnessStatus.STALE_CANDIDATE);
        assertThat(metricAxis.currentBucket(Duration.ofMillis(179_999)).freshness().status())
                .isEqualTo(AcceptedBucketFreshnessStatus.STALE_CANDIDATE);
        assertThat(metricAxis.currentBucket(Duration.ofSeconds(180)).freshness().status())
                .isEqualTo(AcceptedBucketFreshnessStatus.DOWN_CANDIDATE);

        AcceptedBucketFreshness current = metricAxis.currentBucket(Duration.ofMillis(89_999)).freshness();
        assertThat(current.lastAcceptedBucketEndUtc()).contains(QUERY_AT.minusMillis(89_999));
        assertThat(current.age()).contains(Duration.ofMillis(89_999));
    }

    @Test
    void staleAndDownFreshnessSuppressSampleIdleAndDegradedBranches() {
        DegradedHysteresisInput strongConcern = DegradedHysteresisInput.of(true, true, 0.95, 5, false, 0);

        LifecycleStateDecision staleDecision = decide(metricAxis.staleCandidate()
                .withSample(MetricSampleReadiness.INSUFFICIENT)
                .withTraffic(MetricTrafficActivity.IDLE)
                .withDegradedHysteresis(strongConcern), starterAxis.connected());
        LifecycleStateDecision downDecision = decide(metricAxis.downCandidate()
                .withSample(MetricSampleReadiness.SUFFICIENT)
                .withTraffic(MetricTrafficActivity.ACTIVE)
                .withDegradedHysteresis(strongConcern), starterAxis.connected());

        assertThat(staleDecision.metricState().code()).isEqualTo(LifecycleStateCode.STALE);
        assertThat(downDecision.metricState().code()).isEqualTo(LifecycleStateCode.DOWN);
        assertNonRecoveryGuidance(staleDecision);
        assertNonRecoveryGuidance(downDecision);
        assertDoesNotDeclareHostDown(staleDecision);
        assertDoesNotDeclareHostDown(downDecision);
    }

    @Test
    void currentFreshnessWithInsufficientSampleStaysUnknownEvenWithStrongConcern() {
        LifecycleStateDecision decision = decide(metricAxis.currentBucket()
                .withSample(MetricSampleReadiness.INSUFFICIENT)
                .withTraffic(MetricTrafficActivity.ACTIVE)
                .withDegradedHysteresis(DegradedHysteresisInput.of(true, true, 0.95, 5, false, 0)),
                starterAxis.connected());

        assertThat(decision.metricState().code()).isEqualTo(LifecycleStateCode.UNKNOWN);
        assertNonRecoveryGuidance(decision);
    }

    @Test
    void currentFreshnessWithEnoughSampleKeepsActiveAndIdleMeanings() {
        LifecycleStateDecision activeDecision = decide(metricAxis.currentBucket()
                .withSample(MetricSampleReadiness.SUFFICIENT)
                .withTraffic(MetricTrafficActivity.ACTIVE)
                .withDegradedHysteresis(DegradedHysteresisInput.noConcern()), starterAxis.connected());
        LifecycleStateDecision idleDecision = decide(metricAxis.currentBucket()
                .withSample(MetricSampleReadiness.SUFFICIENT)
                .withTraffic(MetricTrafficActivity.IDLE)
                .withDegradedHysteresis(DegradedHysteresisInput.of(true, true, 0.95, 5, false, 0)),
                starterAxis.connected());

        assertThat(activeDecision.metricState().code()).isEqualTo(LifecycleStateCode.ACTIVE);
        assertThat(idleDecision.metricState().code()).isEqualTo(LifecycleStateCode.IDLE);
    }

    @Test
    void baselineInsufficientGuardFalsePreventsDegradedDespiteHighConfidenceAndBadBuckets() {
        LifecycleStateDecision decision = decide(metricAxis.currentBucket()
                .withDegradedHysteresis(baselineInsufficientConcernFixture()), starterAxis.connected());

        assertThat(decision.metricState().code()).isEqualTo(LifecycleStateCode.ACTIVE);
        assertNonRecoveryGuidance(decision);
    }

    @Test
    void degradedEnterRequiresGuardConfidenceAndThreeOfFiveBadBuckets() {
        assertThat(decide(metricAxis.currentBucket()
                        .withDegradedHysteresis(DegradedHysteresisInput.of(true, true, 0.75, 3, false, 0)),
                starterAxis.connected()).metricState().code()).isEqualTo(LifecycleStateCode.DEGRADED);
        assertThat(decide(metricAxis.currentBucket()
                        .withDegradedHysteresis(DegradedHysteresisInput.of(true, true, 0.74, 5, false, 0)),
                starterAxis.connected()).metricState().code()).isEqualTo(LifecycleStateCode.ACTIVE);
        assertThat(decide(metricAxis.currentBucket()
                        .withDegradedHysteresis(DegradedHysteresisInput.of(true, true, 0.95, 2, false, 0)),
                starterAxis.connected()).metricState().code()).isEqualTo(LifecycleStateCode.ACTIVE);
        assertThat(decide(metricAxis.currentBucket()
                        .withDegradedHysteresis(DegradedHysteresisInput.of(true, true, 0.95, 1, false, 0)),
                starterAxis.connected()).metricState().code()).isEqualTo(LifecycleStateCode.ACTIVE);
    }

    @Test
    void degradedResolveRequiresFiveConsecutiveRecoveryBuckets() {
        assertThat(decide(metricAxis.currentBucket()
                        .withPreviousState(LifecycleStateCode.DEGRADED)
                        .withDegradedHysteresis(DegradedHysteresisInput.of(false, true, 0.90, 0, false, 4)),
                starterAxis.connected()).metricState().code()).isEqualTo(LifecycleStateCode.DEGRADED);
        assertThat(decide(metricAxis.currentBucket()
                        .withPreviousState(LifecycleStateCode.DEGRADED)
                        .withDegradedHysteresis(DegradedHysteresisInput.of(false, true, 0.90, 0, false, 5)),
                starterAxis.connected()).metricState().code()).isEqualTo(LifecycleStateCode.ACTIVE);
        assertThat(decide(metricAxis.currentBucket()
                        .withPreviousState(LifecycleStateCode.DEGRADED)
                        .withDegradedHysteresis(DegradedHysteresisInput.of(true, true, 0.59, 0, false, 5)),
                starterAxis.connected()).metricState().code()).isEqualTo(LifecycleStateCode.ACTIVE);
        assertThat(decide(metricAxis.currentBucket()
                        .withPreviousState(LifecycleStateCode.DEGRADED)
                        .withDegradedHysteresis(DegradedHysteresisInput.of(true, true, 0.90, 0, true, 5)),
                starterAxis.connected()).metricState().code()).isEqualTo(LifecycleStateCode.ACTIVE);
    }

    @Test
    void recentHeartbeatDoesNotPromoteMissingAcceptedBucketToCurrentOrActive() {
        LifecycleStateDecision decision = decide(metricAxis.noAcceptedBucket(), starterAxis.connected());

        assertThat(decision.metricState().code()).isEqualTo(LifecycleStateCode.WAITING_FIRST_DATA);
        assertThat(decision.metricState().code()).isNotEqualTo(LifecycleStateCode.ACTIVE);
        assertThat(decision.starterConnection().meaning()).isEqualTo(StarterConnectionMeaning.STARTER_CONNECTED);
        assertThat(decision.starterConnection().diagnosis())
                .isEqualTo(StarterConnectionDiagnosis.STARTER_CONNECTED_BUT_NO_ACCEPTED_BUCKET);
        assertThat(decision.starterConnection().stateImpact()).isEqualTo(StarterStateImpact.NONE);
        assertNonRecoveryGuidance(decision);
        assertDoesNotDeclareHostDown(decision);
    }

    @Test
    void recentHeartbeatStaysSeparateFromStaleAndDownAcceptedBucketCandidates() {
        LifecycleStateDecision staleDecision = decide(metricAxis.staleCandidate(), starterAxis.connected());
        LifecycleStateDecision downDecision = decide(metricAxis.downCandidate(), starterAxis.connected());

        assertThat(staleDecision.metricState().code()).isEqualTo(LifecycleStateCode.STALE);
        assertThat(staleDecision.starterConnection().meaning()).isEqualTo(StarterConnectionMeaning.STARTER_CONNECTED);
        assertThat(staleDecision.starterConnection().diagnosis()).isEqualTo(StarterConnectionDiagnosis.NO_RECENT_TRAFFIC);
        assertThat(downDecision.metricState().code()).isEqualTo(LifecycleStateCode.DOWN);
        assertThat(downDecision.starterConnection().meaning()).isEqualTo(StarterConnectionMeaning.STARTER_CONNECTED);
        assertThat(downDecision.starterConnection().diagnosis()).isEqualTo(StarterConnectionDiagnosis.NO_RECENT_TRAFFIC);
        assertDoesNotDeclareHostDown(staleDecision);
        assertDoesNotDeclareHostDown(downDecision);
    }

    @Test
    void staleOrUnknownHeartbeatOnlyAddsStarterWarningWhenAcceptedBucketIsCurrent() {
        LifecycleStateDecision staleHeartbeatDecision = decide(metricAxis.currentBucket(), starterAxis.stale());
        LifecycleStateDecision unknownHeartbeatDecision = decide(metricAxis.currentBucket(), starterAxis.unknown());

        assertThat(staleHeartbeatDecision.metricState().code()).isEqualTo(LifecycleStateCode.ACTIVE);
        assertThat(staleHeartbeatDecision.starterConnection().meaning())
                .isEqualTo(StarterConnectionMeaning.STARTER_DISCONNECTED);
        assertThat(staleHeartbeatDecision.starterConnection().diagnosis())
                .isEqualTo(StarterConnectionDiagnosis.STARTER_CONNECTION_STALE);
        assertThat(unknownHeartbeatDecision.metricState().code()).isEqualTo(LifecycleStateCode.ACTIVE);
        assertThat(unknownHeartbeatDecision.starterConnection().meaning()).isEqualTo(StarterConnectionMeaning.UNKNOWN);
        assertThat(unknownHeartbeatDecision.starterConnection().diagnosis()).isEqualTo(StarterConnectionDiagnosis.UNKNOWN);
        assertThat(staleHeartbeatDecision.starterConnection().stateImpact()).isEqualTo(StarterStateImpact.NONE);
        assertThat(unknownHeartbeatDecision.starterConnection().stateImpact()).isEqualTo(StarterStateImpact.NONE);
    }

    @Test
    void staleOrMissingHeartbeatWithMissingOrOldAcceptedBucketIsTelemetryUnreachableNotHostDown() {
        LifecycleStateDecision staleHeartbeatAndNoBucket = decide(metricAxis.noAcceptedBucket(), starterAxis.stale());
        LifecycleStateDecision missingHeartbeatAndDownCandidate = decide(metricAxis.downCandidate(), starterAxis.missing());

        assertThat(staleHeartbeatAndNoBucket.metricState().code()).isEqualTo(LifecycleStateCode.WAITING_FIRST_DATA);
        assertThat(staleHeartbeatAndNoBucket.starterConnection().diagnosis())
                .isEqualTo(StarterConnectionDiagnosis.TELEMETRY_UNREACHABLE);
        assertThat(missingHeartbeatAndDownCandidate.metricState().code()).isEqualTo(LifecycleStateCode.DOWN);
        assertThat(missingHeartbeatAndDownCandidate.starterConnection().diagnosis())
                .isEqualTo(StarterConnectionDiagnosis.TELEMETRY_UNREACHABLE);
        assertDoesNotDeclareHostDown(staleHeartbeatAndNoBucket);
        assertDoesNotDeclareHostDown(missingHeartbeatAndDownCandidate);
    }

    @Test
    void downCopyStaysLimitedToDataPlaneFreshnessCandidate() {
        LifecycleStateDecision decision = decide(metricAxis.downCandidate(), starterAxis.connected());

        assertThat(decision.metricState().code()).isEqualTo(LifecycleStateCode.DOWN);
        assertThat(decision.metricState().label()).contains("data-plane");
        assertThat(decision.metricState().rationale()).contains("data-plane freshness");
        assertDoesNotDeclareHostDown(decision);
    }

    @Test
    void recoveryGuidanceOnlyTriggersForPreviousStaleOrDownCurrentFreshnessAndInsufficientSample() {
        LifecycleStateDecision staleRecovery = decide(metricAxis.currentBucket()
                .withSample(MetricSampleReadiness.INSUFFICIENT)
                .withPreviousState(LifecycleStateCode.STALE)
                .withPreviousHealthyAt(LAST_HEALTHY_AT), starterAxis.connected());
        LifecycleStateDecision downRecoveryWithoutSource = decide(metricAxis.currentBucket()
                .withSample(MetricSampleReadiness.INSUFFICIENT)
                .withPreviousState(LifecycleStateCode.DOWN), starterAxis.unknown());

        assertThat(staleRecovery.metricState().code()).isEqualTo(LifecycleStateCode.UNKNOWN);
        assertThat(staleRecovery.recovery().isRecovering()).isTrue();
        assertThat(staleRecovery.recovery().lastHealthyAt()).contains(LAST_HEALTHY_AT);
        assertThat(staleRecovery.recovery().retryAfterSeconds())
                .contains(RecoveryGuidance.RECOVERY_RETRY_AFTER_SECONDS);
        assertThat(staleRecovery.recovery().recommendedAction()).contains(RecoveryGuidance.RECOVERY_RECOMMENDED_ACTION);
        assertThat(downRecoveryWithoutSource.metricState().code()).isEqualTo(LifecycleStateCode.UNKNOWN);
        assertThat(downRecoveryWithoutSource.recovery().isRecovering()).isTrue();
        assertThat(downRecoveryWithoutSource.recovery().lastHealthyAt()).isEmpty();
        assertDoesNotDeclareHostDown(staleRecovery);
        assertDoesNotDeclareHostDown(downRecoveryWithoutSource);
    }

    @Test
    void waitingFirstDataToUnknownAndOtherPreviousStatesAreNotRecovery() {
        assertFirstAcceptedBucketAfterPreviousStateIsNotRecovery(null);
        assertFirstAcceptedBucketAfterPreviousStateIsNotRecovery(LifecycleStateCode.WAITING_FIRST_DATA);
        assertFirstAcceptedBucketAfterPreviousStateIsNotRecovery(LifecycleStateCode.ACTIVE);
        assertFirstAcceptedBucketAfterPreviousStateIsNotRecovery(LifecycleStateCode.IDLE);
        assertFirstAcceptedBucketAfterPreviousStateIsNotRecovery(LifecycleStateCode.UNKNOWN);
        assertFirstAcceptedBucketAfterPreviousStateIsNotRecovery(LifecycleStateCode.DEGRADED);
    }

    @Test
    void recoveryEndsWhenSampleIsSufficientOrFreshnessBecomesStaleOrDownAgain() {
        LifecycleStateDecision sufficientSample = decide(metricAxis.currentBucket()
                .withSample(MetricSampleReadiness.SUFFICIENT)
                .withPreviousState(LifecycleStateCode.STALE)
                .withPreviousHealthyAt(LAST_HEALTHY_AT), starterAxis.connected());
        LifecycleStateDecision staleCandidate = decide(metricAxis.staleCandidate()
                .withSample(MetricSampleReadiness.INSUFFICIENT)
                .withPreviousState(LifecycleStateCode.DOWN)
                .withPreviousHealthyAt(LAST_HEALTHY_AT), starterAxis.connected());
        LifecycleStateDecision downCandidate = decide(metricAxis.downCandidate()
                .withSample(MetricSampleReadiness.INSUFFICIENT)
                .withPreviousState(LifecycleStateCode.STALE)
                .withPreviousHealthyAt(LAST_HEALTHY_AT), starterAxis.connected());

        assertThat(sufficientSample.metricState().code()).isEqualTo(LifecycleStateCode.ACTIVE);
        assertNonRecoveryGuidance(sufficientSample);
        assertThat(staleCandidate.metricState().code()).isEqualTo(LifecycleStateCode.STALE);
        assertNonRecoveryGuidance(staleCandidate);
        assertThat(downCandidate.metricState().code()).isEqualTo(LifecycleStateCode.DOWN);
        assertNonRecoveryGuidance(downCandidate);
    }

    @Test
    void degradedHysteresisRecoveryConditionDoesNotTriggerStoryRecoveryGuidance() {
        LifecycleStateDecision degradedResolve = decide(metricAxis.currentBucket()
                .withPreviousState(LifecycleStateCode.DEGRADED)
                .withDegradedHysteresis(DegradedHysteresisInput.of(true, true, 0.90, 0, true, 5)),
                starterAxis.connected());
        LifecycleStateDecision insufficientSampleWithDegradedPrevious = decide(metricAxis.currentBucket()
                .withSample(MetricSampleReadiness.INSUFFICIENT)
                .withPreviousState(LifecycleStateCode.DEGRADED)
                .withDegradedHysteresis(DegradedHysteresisInput.of(true, true, 0.90, 0, true, 5)),
                starterAxis.connected());

        assertThat(degradedResolve.metricState().code()).isEqualTo(LifecycleStateCode.ACTIVE);
        assertNonRecoveryGuidance(degradedResolve);
        assertThat(insufficientSampleWithDegradedPrevious.metricState().code()).isEqualTo(LifecycleStateCode.UNKNOWN);
        assertNonRecoveryGuidance(insufficientSampleWithDegradedPrevious);
    }

    @Test
    void recoveryFieldShapeAndLifecycleStateEnumDoNotExposeRecoveringCompletionState() {
        assertThat(RecoveryGuidance.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("isRecovering", "lastHealthyAt", "retryAfterSeconds", "recommendedAction");
        assertThat(Arrays.stream(LifecycleStateCode.values()).map(Enum::name))
                .doesNotContain("RECOVERING");
    }

    private LifecycleStateDecision decide(MetricLifecycleInput metricInput, StarterConnectionInput starterConnectionInput) {
        return service.decide(metricInput, starterConnectionInput);
    }

    private LifecycleStateDecision decide(MetricAxisInput metricInput, StarterConnectionInput starterConnectionInput) {
        return service.decide(metricInput.toLifecycleInput(), starterConnectionInput);
    }

    private void assertFirstAcceptedBucketAfterPreviousStateIsNotRecovery(LifecycleStateCode previousState) {
        MetricAxisInput input = metricAxis.currentBucket()
                .withSample(MetricSampleReadiness.INSUFFICIENT)
                .withPreviousHealthyAt(LAST_HEALTHY_AT);
        if (previousState != null) {
            input = input.withPreviousState(previousState);
        }

        LifecycleStateDecision decision = decide(input, starterAxis.connected());

        assertThat(decision.metricState().code()).isEqualTo(LifecycleStateCode.UNKNOWN);
        assertNonRecoveryGuidance(decision);
    }

    private static DegradedHysteresisInput baselineInsufficientConcernFixture() {
        return DegradedHysteresisInput.of(true, false, 0.95, 5, false, 0);
    }

    private static void assertNonRecoveryGuidance(LifecycleStateDecision decision) {
        assertThat(decision.recovery().isRecovering()).isFalse();
        assertThat(decision.recovery().retryAfterSeconds()).isEmpty();
        assertThat(decision.recovery().recommendedAction()).isEmpty();
    }

    private static void assertDoesNotDeclareHostDown(LifecycleStateDecision decision) {
        assertThat(decision.metricState().label()
                + " " + decision.metricState().rationale()
                + " " + decision.metricState().recommendedAction()
                + " " + decision.starterConnection().label()
                + " " + decision.starterConnection().rationale()
                + " " + decision.starterConnection().recommendedAction()
                + " " + decision.recovery().recommendedAction().orElse(""))
                .doesNotContain(
                        "host application down",
                        "host process down",
                        "앱이 내려",
                        "프로세스 down",
                        "애플리케이션 다운");
    }

    private record MetricAxisFixture(AcceptedBucketFreshnessEvaluator evaluator) {

        MetricAxisInput noAcceptedBucket() {
            return input(evaluator.evaluate(null));
        }

        MetricAxisInput currentBucket() {
            return currentBucket(Duration.ofSeconds(30));
        }

        MetricAxisInput currentBucket(Duration age) {
            return input(evaluator.evaluate(QUERY_AT.minus(age)));
        }

        MetricAxisInput staleCandidate() {
            return currentBucket(Duration.ofSeconds(90));
        }

        MetricAxisInput downCandidate() {
            return currentBucket(Duration.ofSeconds(180));
        }

        private static MetricAxisInput input(AcceptedBucketFreshness freshness) {
            return new MetricAxisInput(
                    freshness,
                    MetricSampleReadiness.SUFFICIENT,
                    MetricTrafficActivity.ACTIVE,
                    DegradedHysteresisInput.noConcern(),
                    Optional.empty(),
                    Optional.empty());
        }
    }

    private record MetricAxisInput(
            AcceptedBucketFreshness freshness,
            MetricSampleReadiness sampleReadiness,
            MetricTrafficActivity trafficActivity,
            DegradedHysteresisInput degradedHysteresis,
            Optional<LifecycleStateCode> previousState,
            Optional<Instant> previousHealthyAt
    ) {

        MetricAxisInput withSample(MetricSampleReadiness sampleReadiness) {
            return new MetricAxisInput(
                    freshness,
                    sampleReadiness,
                    trafficActivity,
                    degradedHysteresis,
                    previousState,
                    previousHealthyAt);
        }

        MetricAxisInput withTraffic(MetricTrafficActivity trafficActivity) {
            return new MetricAxisInput(
                    freshness,
                    sampleReadiness,
                    trafficActivity,
                    degradedHysteresis,
                    previousState,
                    previousHealthyAt);
        }

        MetricAxisInput withDegradedHysteresis(DegradedHysteresisInput degradedHysteresis) {
            return new MetricAxisInput(
                    freshness,
                    sampleReadiness,
                    trafficActivity,
                    degradedHysteresis,
                    previousState,
                    previousHealthyAt);
        }

        MetricAxisInput withPreviousState(LifecycleStateCode previousState) {
            return new MetricAxisInput(
                    freshness,
                    sampleReadiness,
                    trafficActivity,
                    degradedHysteresis,
                    Optional.of(previousState),
                    previousHealthyAt);
        }

        MetricAxisInput withPreviousHealthyAt(Instant previousHealthyAt) {
            return new MetricAxisInput(
                    freshness,
                    sampleReadiness,
                    trafficActivity,
                    degradedHysteresis,
                    previousState,
                    Optional.of(previousHealthyAt));
        }

        MetricLifecycleInput toLifecycleInput() {
            return new MetricLifecycleInput(
                    freshness,
                    sampleReadiness,
                    trafficActivity,
                    degradedHysteresis,
                    previousState,
                    previousHealthyAt);
        }
    }

    private record StarterAxisFixture() {

        StarterConnectionInput connected() {
            return StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT);
        }

        StarterConnectionInput stale() {
            return StarterConnectionInput.staleHeartbeat(STALE_HEARTBEAT_AT);
        }

        StarterConnectionInput unknown() {
            return StarterConnectionInput.unknown();
        }

        StarterConnectionInput missing() {
            return StarterConnectionInput.missing();
        }
    }

}
