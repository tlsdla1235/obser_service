package com.observation.portal.domain.state.service;

import com.observation.portal.common.time.AcceptedBucketFreshness;
import com.observation.portal.common.time.AcceptedBucketFreshnessEvaluator;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LifecycleStateServiceTest {

    private static final Instant QUERY_AT = Instant.parse("2026-05-08T01:10:00Z");
    private static final Instant RECENT_HEARTBEAT_AT = Instant.parse("2026-05-08T01:09:50Z");
    private static final Instant STALE_HEARTBEAT_AT = Instant.parse("2026-05-08T01:04:00Z");
    private static final Instant LAST_HEALTHY_AT = Instant.parse("2026-05-08T01:08:30Z");

    private final AcceptedBucketFreshnessEvaluator freshnessEvaluator = new AcceptedBucketFreshnessEvaluator(
            Clock.fixed(QUERY_AT, ZoneOffset.UTC));
    private final LifecycleStateService service = new LifecycleStateService();

    @Test
    void keepsWaitingFirstDataWhenStarterIsConnectedButNoAcceptedBucketExists() {
        LifecycleStateDecision decision = service.decide(
                metricInput(freshnessEvaluator.evaluate(null)),
                StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT));

        assertThat(decision.metricState().code()).isEqualTo(LifecycleStateCode.WAITING_FIRST_DATA);
        assertThat(decision.starterConnection().meaning()).isEqualTo(StarterConnectionMeaning.STARTER_CONNECTED);
        assertThat(decision.starterConnection().diagnosis())
                .isEqualTo(StarterConnectionDiagnosis.STARTER_CONNECTED_BUT_NO_ACCEPTED_BUCKET);
        assertThat(decision.starterConnection().stateImpact()).isEqualTo(StarterStateImpact.NONE);
        assertDoesNotDeclareHostDown(decision);
    }

    @Test
    void reportsTelemetryUnreachableWhenNoAcceptedBucketAndNoHeartbeatExist() {
        LifecycleStateDecision decision = service.decide(
                metricInput(freshnessEvaluator.evaluate(null)),
                StarterConnectionInput.unknown());

        assertThat(decision.metricState().code()).isEqualTo(LifecycleStateCode.WAITING_FIRST_DATA);
        assertThat(decision.starterConnection().meaning()).isEqualTo(StarterConnectionMeaning.TELEMETRY_UNREACHABLE);
        assertThat(decision.starterConnection().diagnosis()).isEqualTo(StarterConnectionDiagnosis.TELEMETRY_UNREACHABLE);
        assertDoesNotDeclareHostDown(decision);
    }

    @Test
    void promotesFreshnessCandidatesToStaleAndDownWithoutUsingHeartbeatAsFreshness() {
        LifecycleStateDecision staleDecision = service.decide(
                metricInput(freshnessEvaluator.evaluate(QUERY_AT.minusSeconds(90))),
                StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT));
        LifecycleStateDecision downDecision = service.decide(
                metricInput(freshnessEvaluator.evaluate(QUERY_AT.minusSeconds(180))),
                StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT));

        assertThat(staleDecision.metricState().code()).isEqualTo(LifecycleStateCode.STALE);
        assertThat(staleDecision.starterConnection().diagnosis())
                .isEqualTo(StarterConnectionDiagnosis.NO_RECENT_TRAFFIC);
        assertThat(downDecision.metricState().code()).isEqualTo(LifecycleStateCode.DOWN);
        assertThat(downDecision.starterConnection().meaning()).isEqualTo(StarterConnectionMeaning.STARTER_CONNECTED);
        assertDoesNotDeclareHostDown(staleDecision);
        assertDoesNotDeclareHostDown(downDecision);
    }

    @Test
    void freshnessCandidatesSuppressSampleAndDegradedEvaluation() {
        LifecycleStateDecision staleDecision = service.decide(
                new MetricLifecycleInput(
                        freshnessEvaluator.evaluate(QUERY_AT.minusSeconds(90)),
                        MetricSampleReadiness.INSUFFICIENT,
                        MetricTrafficActivity.IDLE,
                        DegradedHysteresisInput.of(true, true, 0.95, 5, false, 0),
                        Optional.empty()),
                StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT));
        LifecycleStateDecision downDecision = service.decide(
                new MetricLifecycleInput(
                        freshnessEvaluator.evaluate(QUERY_AT.minusSeconds(180)),
                        MetricSampleReadiness.SUFFICIENT,
                        MetricTrafficActivity.ACTIVE,
                        DegradedHysteresisInput.of(true, true, 0.95, 5, false, 0),
                        Optional.empty()),
                StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT));

        assertThat(staleDecision.metricState().code()).isEqualTo(LifecycleStateCode.STALE);
        assertThat(downDecision.metricState().code()).isEqualTo(LifecycleStateCode.DOWN);
    }

    @Test
    void staleHeartbeatDoesNotOverwriteCurrentMetricState() {
        LifecycleStateDecision decision = service.decide(
                metricInput(freshnessEvaluator.evaluate(QUERY_AT.minusSeconds(30))),
                StarterConnectionInput.staleHeartbeat(STALE_HEARTBEAT_AT));

        assertThat(decision.metricState().code()).isEqualTo(LifecycleStateCode.ACTIVE);
        assertThat(decision.starterConnection().meaning()).isEqualTo(StarterConnectionMeaning.STARTER_DISCONNECTED);
        assertThat(decision.starterConnection().diagnosis()).isEqualTo(StarterConnectionDiagnosis.STARTER_CONNECTION_STALE);
        assertThat(decision.starterConnection().stateImpact()).isEqualTo(StarterStateImpact.NONE);
    }

    @Test
    void evaluatesSampleIdleAndActiveOnlyWhenFreshnessIsCurrent() {
        AcceptedBucketFreshness currentFreshness = freshnessEvaluator.evaluate(QUERY_AT.minusSeconds(30));

        assertThat(service.decide(
                        metricInput(currentFreshness, MetricSampleReadiness.INSUFFICIENT, MetricTrafficActivity.ACTIVE),
                        StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT))
                .metricState().code()).isEqualTo(LifecycleStateCode.UNKNOWN);
        assertThat(service.decide(
                        metricInput(currentFreshness, MetricSampleReadiness.SUFFICIENT, MetricTrafficActivity.IDLE),
                        StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT))
                .metricState().code()).isEqualTo(LifecycleStateCode.IDLE);
        assertThat(service.decide(
                        metricInput(currentFreshness, MetricSampleReadiness.SUFFICIENT, MetricTrafficActivity.ACTIVE),
                        StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT))
                .metricState().code()).isEqualTo(LifecycleStateCode.ACTIVE);
    }

    @Test
    void marksRecoveryOnlyWhenPreviousStaleOrDownCurrentFreshnessAndInsufficientSample() {
        AcceptedBucketFreshness currentFreshness = freshnessEvaluator.evaluate(QUERY_AT.minusSeconds(30));

        LifecycleStateDecision staleRecovery = service.decide(
                metricInput(
                        currentFreshness,
                        MetricSampleReadiness.INSUFFICIENT,
                        MetricTrafficActivity.ACTIVE,
                        LifecycleStateCode.STALE,
                        LAST_HEALTHY_AT),
                StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT));
        LifecycleStateDecision downRecovery = service.decide(
                metricInput(
                        currentFreshness,
                        MetricSampleReadiness.INSUFFICIENT,
                        MetricTrafficActivity.ACTIVE,
                        LifecycleStateCode.DOWN,
                        LAST_HEALTHY_AT),
                StarterConnectionInput.unknown());

        assertThat(staleRecovery.metricState().code()).isEqualTo(LifecycleStateCode.UNKNOWN);
        assertThat(staleRecovery.recovery().isRecovering()).isTrue();
        assertThat(staleRecovery.recovery().lastHealthyAt()).contains(LAST_HEALTHY_AT);
        assertThat(staleRecovery.recovery().retryAfterSeconds()).contains(30);
        assertThat(staleRecovery.recovery().recommendedAction())
                .contains("다음 판단까지 약 30초 기다린 뒤 accepted bucket 수용과 sample 증가를 확인하세요.");
        assertThat(downRecovery.metricState().code()).isEqualTo(LifecycleStateCode.UNKNOWN);
        assertThat(downRecovery.recovery().isRecovering()).isTrue();
        assertDoesNotDeclareHostDown(staleRecovery);
        assertDoesNotDeclareHostDown(downRecovery);
    }

    @Test
    void allowsMissingPreviousHealthyTimestampDuringRecovery() {
        AcceptedBucketFreshness currentFreshness = freshnessEvaluator.evaluate(QUERY_AT.minusSeconds(30));

        LifecycleStateDecision decision = service.decide(
                metricInput(
                        currentFreshness,
                        MetricSampleReadiness.INSUFFICIENT,
                        MetricTrafficActivity.ACTIVE,
                        LifecycleStateCode.STALE,
                        null),
                StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT));

        assertThat(decision.recovery().isRecovering()).isTrue();
        assertThat(decision.recovery().lastHealthyAt()).isEmpty();
    }

    @Test
    void doesNotTreatFirstDataOrNonStalePreviousStatesAsRecovery() {
        AcceptedBucketFreshness currentFreshness = freshnessEvaluator.evaluate(QUERY_AT.minusSeconds(30));

        assertThat(service.decide(
                        metricInput(currentFreshness, MetricSampleReadiness.INSUFFICIENT, MetricTrafficActivity.ACTIVE),
                        StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT))
                .recovery().isRecovering()).isFalse();
        assertThat(service.decide(
                        metricInput(
                                currentFreshness,
                                MetricSampleReadiness.INSUFFICIENT,
                                MetricTrafficActivity.ACTIVE,
                                LifecycleStateCode.WAITING_FIRST_DATA,
                                LAST_HEALTHY_AT),
                        StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT))
                .recovery().isRecovering()).isFalse();
        assertThat(service.decide(
                        metricInput(
                                currentFreshness,
                                MetricSampleReadiness.INSUFFICIENT,
                                MetricTrafficActivity.ACTIVE,
                                LifecycleStateCode.ACTIVE,
                                LAST_HEALTHY_AT),
                        StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT))
                .recovery().isRecovering()).isFalse();
        assertThat(service.decide(
                        metricInput(
                                currentFreshness,
                                MetricSampleReadiness.INSUFFICIENT,
                                MetricTrafficActivity.ACTIVE,
                                LifecycleStateCode.IDLE,
                                LAST_HEALTHY_AT),
                        StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT))
                .recovery().isRecovering()).isFalse();
        assertThat(service.decide(
                        metricInput(
                                currentFreshness,
                                MetricSampleReadiness.INSUFFICIENT,
                                MetricTrafficActivity.ACTIVE,
                                LifecycleStateCode.UNKNOWN,
                                LAST_HEALTHY_AT),
                        StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT))
                .recovery().isRecovering()).isFalse();
    }

    @Test
    void endsRecoveryWhenSampleIsSufficientOrFreshnessIsNotCurrent() {
        LifecycleStateDecision sufficientSample = service.decide(
                metricInput(
                        freshnessEvaluator.evaluate(QUERY_AT.minusSeconds(30)),
                        MetricSampleReadiness.SUFFICIENT,
                        MetricTrafficActivity.ACTIVE,
                        LifecycleStateCode.STALE,
                        LAST_HEALTHY_AT),
                StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT));
        LifecycleStateDecision downSufficientSample = service.decide(
                metricInput(
                        freshnessEvaluator.evaluate(QUERY_AT.minusSeconds(30)),
                        MetricSampleReadiness.SUFFICIENT,
                        MetricTrafficActivity.ACTIVE,
                        LifecycleStateCode.DOWN,
                        LAST_HEALTHY_AT),
                StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT));
        LifecycleStateDecision staleCandidate = service.decide(
                metricInput(
                        freshnessEvaluator.evaluate(QUERY_AT.minusSeconds(90)),
                        MetricSampleReadiness.INSUFFICIENT,
                        MetricTrafficActivity.ACTIVE,
                        LifecycleStateCode.DOWN,
                        LAST_HEALTHY_AT),
                StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT));
        LifecycleStateDecision downCandidate = service.decide(
                metricInput(
                        freshnessEvaluator.evaluate(QUERY_AT.minusSeconds(180)),
                        MetricSampleReadiness.INSUFFICIENT,
                        MetricTrafficActivity.ACTIVE,
                        LifecycleStateCode.STALE,
                        LAST_HEALTHY_AT),
                StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT));
        LifecycleStateDecision waitingFirstData = service.decide(
                metricInput(
                        freshnessEvaluator.evaluate(null),
                        MetricSampleReadiness.INSUFFICIENT,
                        MetricTrafficActivity.ACTIVE,
                        LifecycleStateCode.STALE,
                        LAST_HEALTHY_AT),
                StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT));

        assertThat(sufficientSample.metricState().code()).isEqualTo(LifecycleStateCode.ACTIVE);
        assertNonRecoveryGuidance(sufficientSample);
        assertThat(downSufficientSample.metricState().code()).isEqualTo(LifecycleStateCode.ACTIVE);
        assertNonRecoveryGuidance(downSufficientSample);
        assertThat(staleCandidate.metricState().code()).isEqualTo(LifecycleStateCode.STALE);
        assertNonRecoveryGuidance(staleCandidate);
        assertThat(downCandidate.metricState().code()).isEqualTo(LifecycleStateCode.DOWN);
        assertNonRecoveryGuidance(downCandidate);
        assertThat(waitingFirstData.metricState().code()).isEqualTo(LifecycleStateCode.WAITING_FIRST_DATA);
        assertNonRecoveryGuidance(waitingFirstData);
    }

    @Test
    void keepsDegradedHysteresisSeparateFromRecoveryGuidance() {
        AcceptedBucketFreshness currentFreshness = freshnessEvaluator.evaluate(QUERY_AT.minusSeconds(30));

        LifecycleStateDecision unresolvedDegraded = service.decide(
                metricInput(
                        currentFreshness,
                        DegradedHysteresisInput.of(false, true, 0.90, 0, false, 4),
                        LifecycleStateCode.DEGRADED),
                StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT));
        LifecycleStateDecision resolvedDegraded = service.decide(
                metricInput(
                        currentFreshness,
                        DegradedHysteresisInput.of(false, true, 0.90, 0, false, 5),
                        LifecycleStateCode.DEGRADED),
                StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT));

        assertThat(unresolvedDegraded.metricState().code()).isEqualTo(LifecycleStateCode.DEGRADED);
        assertNonRecoveryGuidance(unresolvedDegraded);
        assertThat(resolvedDegraded.metricState().code()).isEqualTo(LifecycleStateCode.ACTIVE);
        assertNonRecoveryGuidance(resolvedDegraded);
    }

    @Test
    void starterConnectionInputDoesNotChangeRecoveryTrigger() {
        AcceptedBucketFreshness currentFreshness = freshnessEvaluator.evaluate(QUERY_AT.minusSeconds(30));
        MetricLifecycleInput recoveringInput = metricInput(
                currentFreshness,
                MetricSampleReadiness.INSUFFICIENT,
                MetricTrafficActivity.ACTIVE,
                LifecycleStateCode.STALE,
                LAST_HEALTHY_AT);

        LifecycleStateDecision connectedDecision = service.decide(
                recoveringInput,
                StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT));
        LifecycleStateDecision staleHeartbeatDecision = service.decide(
                recoveringInput,
                StarterConnectionInput.staleHeartbeat(STALE_HEARTBEAT_AT));
        LifecycleStateDecision unknownHeartbeatDecision = service.decide(
                recoveringInput,
                StarterConnectionInput.unknown());

        assertThat(connectedDecision.recovery().isRecovering()).isTrue();
        assertThat(staleHeartbeatDecision.recovery().isRecovering()).isTrue();
        assertThat(unknownHeartbeatDecision.recovery().isRecovering()).isTrue();
        assertThat(connectedDecision.metricState().code()).isEqualTo(LifecycleStateCode.UNKNOWN);
        assertThat(staleHeartbeatDecision.metricState().code()).isEqualTo(LifecycleStateCode.UNKNOWN);
        assertThat(unknownHeartbeatDecision.metricState().code()).isEqualTo(LifecycleStateCode.UNKNOWN);
    }

    @Test
    void recoveryGuidanceShapeDoesNotExposeRecoveredTimestampOrTopLevelRecoveringState() {
        assertThat(RecoveryGuidance.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("isRecovering", "lastHealthyAt", "retryAfterSeconds", "recommendedAction");
        assertThat(Arrays.stream(LifecycleStateCode.values()).map(Enum::name))
                .doesNotContain("RECOVERING");
    }

    @Test
    void recoveryGuidanceConstructorRejectsInvalidRetryAndActionCombinations() {
        assertThatThrownBy(() -> new RecoveryGuidance(
                true,
                Optional.of(LAST_HEALTHY_AT),
                Optional.empty(),
                Optional.of(RecoveryGuidance.RECOVERY_RECOMMENDED_ACTION)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("30 seconds");
        assertThatThrownBy(() -> new RecoveryGuidance(
                true,
                Optional.of(LAST_HEALTHY_AT),
                Optional.of(RecoveryGuidance.RECOVERY_RETRY_AFTER_SECONDS),
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recommendedAction");
        assertThatThrownBy(() -> new RecoveryGuidance(
                false,
                Optional.of(LAST_HEALTHY_AT),
                Optional.of(RecoveryGuidance.RECOVERY_RETRY_AFTER_SECONDS),
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-recovery");
        assertThatThrownBy(() -> new RecoveryGuidance(
                false,
                Optional.of(LAST_HEALTHY_AT),
                Optional.empty(),
                Optional.of(RecoveryGuidance.RECOVERY_RECOMMENDED_ACTION)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-recovery");
    }

    @Test
    void entersDegradedOnlyWhenTypedHysteresisInputPassesAllEnterGuards() {
        AcceptedBucketFreshness currentFreshness = freshnessEvaluator.evaluate(QUERY_AT.minusSeconds(30));

        assertThat(service.decide(
                        metricInput(currentFreshness, DegradedHysteresisInput.of(true, true, 0.75, 3, false, 0)),
                        StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT))
                .metricState().code()).isEqualTo(LifecycleStateCode.DEGRADED);
        assertThat(service.decide(
                        metricInput(currentFreshness, DegradedHysteresisInput.of(true, false, 0.95, 5, false, 0)),
                        StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT))
                .metricState().code()).isEqualTo(LifecycleStateCode.ACTIVE);
        assertThat(service.decide(
                        metricInput(currentFreshness, DegradedHysteresisInput.of(true, true, 0.74, 5, false, 0)),
                        StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT))
                .metricState().code()).isEqualTo(LifecycleStateCode.ACTIVE);
        assertThat(service.decide(
                        metricInput(currentFreshness, DegradedHysteresisInput.of(true, true, 0.95, 2, false, 0)),
                        StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT))
                .metricState().code()).isEqualTo(LifecycleStateCode.ACTIVE);
        assertThat(service.decide(
                        metricInput(currentFreshness, DegradedHysteresisInput.of(true, true, 0.95, 1, false, 0)),
                        StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT))
                .metricState().code()).isEqualTo(LifecycleStateCode.ACTIVE);
    }

    @Test
    void resolvesExistingDegradedStateOnlyAfterFiveConsecutiveRecoveryBuckets() {
        AcceptedBucketFreshness currentFreshness = freshnessEvaluator.evaluate(QUERY_AT.minusSeconds(30));

        assertThat(service.decide(
                        metricInput(
                                currentFreshness,
                                DegradedHysteresisInput.of(false, true, 0.90, 0, false, 4),
                                LifecycleStateCode.DEGRADED),
                        StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT))
                .metricState().code()).isEqualTo(LifecycleStateCode.DEGRADED);
        assertThat(service.decide(
                        metricInput(
                                currentFreshness,
                                DegradedHysteresisInput.of(false, true, 0.90, 0, false, 5),
                                LifecycleStateCode.DEGRADED),
                        StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT))
                .metricState().code()).isEqualTo(LifecycleStateCode.ACTIVE);
        assertThat(service.decide(
                        metricInput(
                                currentFreshness,
                                DegradedHysteresisInput.of(true, true, 0.59, 0, false, 5),
                                LifecycleStateCode.DEGRADED),
                        StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT))
                .metricState().code()).isEqualTo(LifecycleStateCode.ACTIVE);
        assertThat(service.decide(
                        metricInput(
                                currentFreshness,
                                DegradedHysteresisInput.of(true, true, 0.90, 0, true, 5),
                                LifecycleStateCode.DEGRADED),
                        StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT))
                .metricState().code()).isEqualTo(LifecycleStateCode.ACTIVE);
    }

    @Test
    void changingHeartbeatInputDoesNotChangeAcceptedBucketFreshnessOrMetricDecision() {
        AcceptedBucketFreshness freshness = freshnessEvaluator.evaluate(QUERY_AT.minusSeconds(30));

        LifecycleStateDecision connectedDecision = service.decide(
                metricInput(freshness),
                StarterConnectionInput.recentHeartbeat(RECENT_HEARTBEAT_AT));
        LifecycleStateDecision staleHeartbeatDecision = service.decide(
                metricInput(freshness),
                StarterConnectionInput.staleHeartbeat(STALE_HEARTBEAT_AT));

        assertThat(freshness.lastAcceptedBucketEndUtc()).contains(QUERY_AT.minusSeconds(30));
        assertThat(freshness.age()).contains(java.time.Duration.ofSeconds(30));
        assertThat(connectedDecision.metricState().code()).isEqualTo(LifecycleStateCode.ACTIVE);
        assertThat(staleHeartbeatDecision.metricState().code()).isEqualTo(LifecycleStateCode.ACTIVE);
    }

    private static MetricLifecycleInput metricInput(AcceptedBucketFreshness freshness) {
        return metricInput(freshness, MetricSampleReadiness.SUFFICIENT, MetricTrafficActivity.ACTIVE);
    }

    private static MetricLifecycleInput metricInput(
            AcceptedBucketFreshness freshness,
            MetricSampleReadiness sampleReadiness,
            MetricTrafficActivity trafficActivity) {
        return metricInput(freshness, sampleReadiness, trafficActivity, null, null);
    }

    private static MetricLifecycleInput metricInput(
            AcceptedBucketFreshness freshness,
            MetricSampleReadiness sampleReadiness,
            MetricTrafficActivity trafficActivity,
            LifecycleStateCode previousState,
            Instant previousHealthyAt) {
        return new MetricLifecycleInput(
                freshness,
                sampleReadiness,
                trafficActivity,
                DegradedHysteresisInput.noConcern(),
                Optional.ofNullable(previousState),
                Optional.ofNullable(previousHealthyAt));
    }

    private static MetricLifecycleInput metricInput(
            AcceptedBucketFreshness freshness,
            DegradedHysteresisInput degradedHysteresis) {
        return metricInput(freshness, degradedHysteresis, null);
    }

    private static MetricLifecycleInput metricInput(
            AcceptedBucketFreshness freshness,
            DegradedHysteresisInput degradedHysteresis,
            LifecycleStateCode previousState) {
        return new MetricLifecycleInput(
                freshness,
                MetricSampleReadiness.SUFFICIENT,
                MetricTrafficActivity.ACTIVE,
                degradedHysteresis,
                Optional.ofNullable(previousState),
                Optional.empty());
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
                .doesNotContain("host application down", "host process down", "앱이 내려", "프로세스 down");
    }
}
