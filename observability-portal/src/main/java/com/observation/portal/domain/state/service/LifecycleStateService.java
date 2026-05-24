package com.observation.portal.domain.state.service;

import com.observation.portal.common.time.AcceptedBucketFreshnessStatus;
import com.observation.portal.domain.state.model.LifecycleStateCode;
import com.observation.portal.domain.state.model.LifecycleStateDecision;
import com.observation.portal.domain.state.model.MetricLifecycleInput;
import com.observation.portal.domain.state.model.MetricLifecycleState;
import com.observation.portal.domain.state.model.MetricSampleReadiness;
import com.observation.portal.domain.state.model.MetricTrafficActivity;
import com.observation.portal.domain.state.model.StarterConnectionDiagnosis;
import com.observation.portal.domain.state.model.StarterConnectionFreshness;
import com.observation.portal.domain.state.model.StarterConnectionInput;
import com.observation.portal.domain.state.model.StarterConnectionMeaning;
import com.observation.portal.domain.state.model.StarterConnectionSummary;
import com.observation.portal.domain.state.model.StarterStateImpact;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * accepted bucket metric axis와 starter heartbeat connection axis를 분리해 lifecycle state를 판단한다.
 *
 * <p>이 service는 repository를 조회하지 않고 typed input만 소비한다. heartbeat는 metric freshness age 계산에 사용하지 않는다.</p>
 */
@Service
public class LifecycleStateService {

    /**
     * application metric state와 starter connection summary를 별도 field로 반환한다.
     */
    public LifecycleStateDecision decide(
            MetricLifecycleInput metricInput,
            StarterConnectionInput starterConnectionInput) {
        MetricLifecycleInput requiredMetricInput = Objects.requireNonNull(
                metricInput,
                "metricInput must not be null");
        StarterConnectionInput requiredStarterInput = Objects.requireNonNull(
                starterConnectionInput,
                "starterConnectionInput must not be null");

        MetricLifecycleState metricState = decideMetricState(requiredMetricInput);
        StarterConnectionSummary starterConnection = decideStarterConnection(
                requiredMetricInput.freshness().status(),
                metricState.code(),
                requiredStarterInput);
        return new LifecycleStateDecision(metricState, starterConnection);
    }

    private MetricLifecycleState decideMetricState(MetricLifecycleInput input) {
        AcceptedBucketFreshnessStatus freshnessStatus = input.freshness().status();
        return switch (freshnessStatus) {
            case WAITING_FIRST_DATA -> metricState(
                    LifecycleStateCode.WAITING_FIRST_DATA,
                    "Metric data waiting",
                    "Accepted bucket이 아직 없어 metric data state를 waiting_first_data로 둔다.",
                    "트래픽 발생 후 accepted bucket 수용 여부를 확인하세요.");
            case STALE_CANDIDATE -> metricState(
                    LifecycleStateCode.STALE,
                    "Metric data stale",
                    "마지막 accepted bucket endUtc 기준 freshness가 stale 후보입니다.",
                    "Accepted bucket 수용 경로와 최근 트래픽 여부를 확인하세요.");
            case DOWN_CANDIDATE -> metricState(
                    LifecycleStateCode.DOWN,
                    "Metric data-plane unreachable",
                    "마지막 accepted bucket endUtc 기준 data-plane freshness 공백이 down 후보에 도달했습니다.",
                    "Starter 연결 상태, accepted bucket 수용 경로, 최근 트래픽 여부를 함께 확인하세요.");
            case CURRENT -> decideCurrentMetricState(input);
        };
    }

    private MetricLifecycleState decideCurrentMetricState(MetricLifecycleInput input) {
        if (input.sampleReadiness() == MetricSampleReadiness.INSUFFICIENT) {
            return metricState(
                    LifecycleStateCode.UNKNOWN,
                    "Metric data unknown",
                    "Freshness는 current지만 lifecycle 판단에 필요한 sample이 부족합니다.",
                    "다음 bucket까지 더 많은 요청 sample을 관찰하세요.");
        }
        if (input.trafficActivity() == MetricTrafficActivity.IDLE) {
            return metricState(
                    LifecycleStateCode.IDLE,
                    "Metric data idle",
                    "Freshness는 current지만 traffic이 idle 상태라 anomaly 판단을 보류합니다.",
                    "요청이 들어온 뒤 metric state를 다시 평가하세요.");
        }
        if (input.previousState().filter(LifecycleStateCode.DEGRADED::equals).isPresent()) {
            if (input.degradedHysteresis().canResolveDegraded()) {
                return activeState();
            }
            return degradedState("기존 degraded 상태가 recovery hysteresis를 아직 통과하지 않았습니다.");
        }
        if (input.degradedHysteresis().canEnterDegraded()) {
            return degradedState("Concern signal이 guard, confidence, 5-bucket bad count 기준을 모두 통과했습니다.");
        }
        return activeState();
    }

    private StarterConnectionSummary decideStarterConnection(
            AcceptedBucketFreshnessStatus freshnessStatus,
            LifecycleStateCode metricStateCode,
            StarterConnectionInput input) {
        if (input.freshness() == StarterConnectionFreshness.RECENT) {
            return recentStarterConnection(freshnessStatus, metricStateCode, input);
        }
        if (input.freshness() == StarterConnectionFreshness.STALE) {
            return staleStarterConnection(freshnessStatus, input);
        }
        return unknownStarterConnection(freshnessStatus, input);
    }

    private StarterConnectionSummary recentStarterConnection(
            AcceptedBucketFreshnessStatus freshnessStatus,
            LifecycleStateCode metricStateCode,
            StarterConnectionInput input) {
        if (freshnessStatus == AcceptedBucketFreshnessStatus.WAITING_FIRST_DATA) {
            return starterSummary(
                    input,
                    StarterConnectionMeaning.STARTER_CONNECTED,
                    StarterConnectionDiagnosis.STARTER_CONNECTED_BUT_NO_ACCEPTED_BUCKET,
                    "Starter connected",
                    "Starter heartbeat는 최근 수신됐지만 accepted bucket은 아직 없습니다.",
                    "요청 traffic을 발생시키고 metric bucket 수용 여부를 확인하세요.");
        }
        if (metricStateCode == LifecycleStateCode.STALE || metricStateCode == LifecycleStateCode.DOWN) {
            return starterSummary(
                    input,
                    StarterConnectionMeaning.STARTER_CONNECTED,
                    StarterConnectionDiagnosis.NO_RECENT_TRAFFIC,
                    "Starter connected",
                    "Starter heartbeat는 최근 수신됐지만 metric data-plane accepted bucket freshness가 오래됐습니다.",
                    "최근 traffic 여부와 bucket ingest 경로를 우선 확인하세요.");
        }
        if (metricStateCode == LifecycleStateCode.IDLE) {
            return starterSummary(
                    input,
                    StarterConnectionMeaning.STARTER_CONNECTED,
                    StarterConnectionDiagnosis.METRIC_DATA_IDLE,
                    "Starter connected",
                    "Starter heartbeat와 accepted bucket freshness는 유지되지만 traffic이 idle 상태입니다.",
                    "요청 volume이 회복되는지 관찰하세요.");
        }
        return starterSummary(
                input,
                StarterConnectionMeaning.STARTER_CONNECTED,
                StarterConnectionDiagnosis.STARTER_CONNECTED,
                "Starter connected",
                "Starter heartbeat와 metric data freshness가 각각 최근 상태입니다.",
                "현재 starter connection 관련 추가 조치는 없습니다.");
    }

    private StarterConnectionSummary staleStarterConnection(
            AcceptedBucketFreshnessStatus freshnessStatus,
            StarterConnectionInput input) {
        if (hasNoRecentAcceptedBucket(freshnessStatus)) {
            return starterSummary(
                    input,
                    StarterConnectionMeaning.STARTER_DISCONNECTED,
                    StarterConnectionDiagnosis.TELEMETRY_UNREACHABLE,
                    "Starter telemetry stale",
                    "Starter heartbeat와 accepted bucket freshness가 모두 최근 상태가 아닙니다.",
                    "Starter 설정, portal 연결, network 경로를 확인하되 metric data 공백 원인은 단정하지 마세요.");
        }
        return starterSummary(
                input,
                StarterConnectionMeaning.STARTER_DISCONNECTED,
                StarterConnectionDiagnosis.STARTER_CONNECTION_STALE,
                "Starter telemetry stale",
                "Accepted bucket은 최근 수용됐지만 starter heartbeat freshness는 오래됐습니다.",
                "Metric state는 유지하고 starter heartbeat 송신 경로만 별도로 확인하세요.");
    }

    private StarterConnectionSummary unknownStarterConnection(
            AcceptedBucketFreshnessStatus freshnessStatus,
            StarterConnectionInput input) {
        if (hasNoRecentAcceptedBucket(freshnessStatus)) {
            return starterSummary(
                    input,
                    StarterConnectionMeaning.TELEMETRY_UNREACHABLE,
                    StarterConnectionDiagnosis.TELEMETRY_UNREACHABLE,
                    "Starter telemetry unknown",
                    "Starter heartbeat input이 없고 accepted bucket freshness도 최근 상태가 아닙니다.",
                    "Starter 설치, project key, portal reachability를 확인하되 원인은 추가 evidence로 좁히세요.");
        }
        return starterSummary(
                input,
                StarterConnectionMeaning.UNKNOWN,
                StarterConnectionDiagnosis.UNKNOWN,
                "Starter telemetry unknown",
                "Accepted bucket은 최근 수용됐지만 starter heartbeat input은 아직 판단할 수 없습니다.",
                "Metric state는 유지하고 heartbeat telemetry adapter 입력을 확인하세요.");
    }

    private static boolean hasNoRecentAcceptedBucket(AcceptedBucketFreshnessStatus freshnessStatus) {
        return freshnessStatus == AcceptedBucketFreshnessStatus.WAITING_FIRST_DATA
                || freshnessStatus == AcceptedBucketFreshnessStatus.STALE_CANDIDATE
                || freshnessStatus == AcceptedBucketFreshnessStatus.DOWN_CANDIDATE;
    }

    private static MetricLifecycleState activeState() {
        return metricState(
                LifecycleStateCode.ACTIVE,
                "Metric data active",
                "Freshness와 sample이 충분하고 degraded concern이 hysteresis 기준을 통과하지 않았습니다.",
                "현재 metric data state 관련 우선 조치는 없습니다.");
    }

    private static MetricLifecycleState degradedState(String rationale) {
        return metricState(
                LifecycleStateCode.DEGRADED,
                "Metric data degraded",
                rationale,
                "Typed concern 입력이 가리키는 rule과 endpoint evidence를 확인하세요.");
    }

    private static MetricLifecycleState metricState(
            LifecycleStateCode code,
            String label,
            String rationale,
            String recommendedAction) {
        return new MetricLifecycleState(code, label, rationale, recommendedAction);
    }

    private static StarterConnectionSummary starterSummary(
            StarterConnectionInput input,
            StarterConnectionMeaning meaning,
            StarterConnectionDiagnosis diagnosis,
            String label,
            String rationale,
            String recommendedAction) {
        return new StarterConnectionSummary(
                input.statusSource(),
                input.lastHeartbeatAt(),
                input.lastHeartbeatStatus(),
                input.freshness(),
                meaning,
                diagnosis,
                StarterStateImpact.NONE,
                label,
                rationale,
                recommendedAction);
    }
}
