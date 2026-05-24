package com.observation.portal.domain.state.service;

import com.observation.portal.common.time.AcceptedBucketFreshness;
import com.observation.portal.common.time.AcceptedBucketFreshnessEvaluator;
import com.observation.portal.domain.state.model.DegradedHysteresisInput;
import com.observation.portal.domain.state.model.LifecycleStateCode;
import com.observation.portal.domain.state.model.LifecycleStateDecision;
import com.observation.portal.domain.state.model.MetricLifecycleInput;
import com.observation.portal.domain.state.model.MetricSampleReadiness;
import com.observation.portal.domain.state.model.MetricTrafficActivity;
import com.observation.portal.domain.state.model.StarterConnectionDiagnosis;
import com.observation.portal.domain.state.model.StarterConnectionInput;
import com.observation.portal.domain.state.model.StarterConnectionMeaning;
import com.observation.portal.domain.state.model.StarterStateImpact;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LifecycleStateServiceTest {

    private static final Instant QUERY_AT = Instant.parse("2026-05-08T01:10:00Z");
    private static final Instant RECENT_HEARTBEAT_AT = Instant.parse("2026-05-08T01:09:50Z");
    private static final Instant STALE_HEARTBEAT_AT = Instant.parse("2026-05-08T01:04:00Z");

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
        return new MetricLifecycleInput(
                freshness,
                sampleReadiness,
                trafficActivity,
                DegradedHysteresisInput.noConcern(),
                Optional.empty());
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
                Optional.ofNullable(previousState));
    }

    private static void assertDoesNotDeclareHostDown(LifecycleStateDecision decision) {
        assertThat(decision.metricState().label()
                + " " + decision.metricState().rationale()
                + " " + decision.metricState().recommendedAction()
                + " " + decision.starterConnection().label()
                + " " + decision.starterConnection().rationale()
                + " " + decision.starterConnection().recommendedAction())
                .doesNotContain("host application down", "host process down", "앱이 내려", "프로세스 down");
    }
}
