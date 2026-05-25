package com.observation.portal.domain.dashboard.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Application Dashboard current API가 반환하는 read-model-contract skeleton이다.
 *
 * <p>Story 5.2에서는 source-scoped percentile, triage, endpoint priority, snapshot을 계산하지 않고 contract-safe
 * placeholder만 포함한다.</p>
 */
public record ApplicationDashboardReadModel(
        OffsetDateTime generatedAt,
        Application application,
        State state,
        StarterConnection starterConnection,
        ZeroInsight zeroInsight,
        Recovery recovery,
        Metrics metrics,
        SourceScopedPercentiles sourceScopedPercentiles,
        List<Object> triageCards,
        List<Object> endpointPriority,
        Object snapshot
) {

    /**
     * top-level dashboard field가 항상 존재하도록 필수 field와 placeholder collection을 검증한다.
     */
    public ApplicationDashboardReadModel {
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        Objects.requireNonNull(application, "application must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(starterConnection, "starterConnection must not be null");
        Objects.requireNonNull(zeroInsight, "zeroInsight must not be null");
        Objects.requireNonNull(recovery, "recovery must not be null");
        Objects.requireNonNull(metrics, "metrics must not be null");
        Objects.requireNonNull(sourceScopedPercentiles, "sourceScopedPercentiles must not be null");
        triageCards = List.copyOf(Objects.requireNonNull(triageCards, "triageCards must not be null"));
        endpointPriority = List.copyOf(Objects.requireNonNull(endpointPriority, "endpointPriority must not be null"));
    }

    /**
     * dashboard가 속한 application/environment row identity와 metric data window/freshness를 담는다.
     */
    public record Application(
            UUID projectId,
            UUID applicationId,
            String name,
            String environment,
            OffsetDateTime lastAcceptedBucketAt,
            OffsetDateTime lastHealthyAt,
            SourceWindow sourceWindow,
            Freshness freshness
    ) {

        /**
         * application identity와 source axis 정보를 검증한다.
         */
        public Application {
            Objects.requireNonNull(projectId, "projectId must not be null");
            Objects.requireNonNull(applicationId, "applicationId must not be null");
            name = requireText(name, "name");
            environment = requireText(environment, "environment");
            Objects.requireNonNull(sourceWindow, "sourceWindow must not be null");
            Objects.requireNonNull(freshness, "freshness must not be null");
        }
    }

    /**
     * query evaluationAt 기준 current 15분 window와 직전 baseline 15분 window를 담는다.
     */
    public record SourceWindow(Window current, Window baseline) {

        /**
         * current와 baseline window가 모두 존재하도록 검증한다.
         */
        public SourceWindow {
            Objects.requireNonNull(current, "current must not be null");
            Objects.requireNonNull(baseline, "baseline must not be null");
        }
    }

    /**
     * dashboard response에 노출되는 UTC window boundary다.
     */
    public record Window(OffsetDateTime startUtc, OffsetDateTime endUtc) {

        /**
         * 시작/종료 boundary가 유효한 순서를 갖도록 검증한다.
         */
        public Window {
            Objects.requireNonNull(startUtc, "startUtc must not be null");
            Objects.requireNonNull(endUtc, "endUtc must not be null");
            if (!endUtc.isAfter(startUtc)) {
                throw new IllegalArgumentException("endUtc must be after startUtc");
            }
        }
    }

    /**
     * accepted bucket endUtc 기반 freshness source와 threshold 시각을 담는다.
     */
    public record Freshness(
            OffsetDateTime lastObservedAt,
            OffsetDateTime staleAt,
            OffsetDateTime downAt
    ) {
    }

    /**
     * LifecycleStateService가 결정한 metric data-plane state를 API copy로 옮긴 값이다.
     */
    public record State(
            String code,
            String label,
            String rationale,
            String recommendedAction,
            String scope
    ) {

        /**
         * state code/copy/scope가 응답에서 비어 있지 않도록 검증한다.
         */
        public State {
            code = requireText(code, "code");
            label = requireText(label, "label");
            rationale = requireText(rationale, "rationale");
            recommendedAction = requireText(recommendedAction, "recommendedAction");
            scope = requireText(scope, "scope");
        }
    }

    /**
     * starter heartbeat control-plane source만 사용한 connection summary다.
     */
    public record StarterConnection(
            String statusSource,
            OffsetDateTime lastHeartbeatAt,
            String lastHeartbeatStatus,
            String connectionMeaning,
            String stateImpact
    ) {

        /**
         * starter connection source와 metric state 영향 여부를 명시적으로 보존한다.
         */
        public StarterConnection {
            statusSource = requireText(statusSource, "statusSource");
            lastHeartbeatStatus = requireText(lastHeartbeatStatus, "lastHeartbeatStatus");
            connectionMeaning = requireText(connectionMeaning, "connectionMeaning");
            stateImpact = requireText(stateImpact, "stateImpact");
        }
    }

    /**
     * triageCards가 비어 있을 때 UI가 표시할 server-computed 이유와 권장 행동이다.
     */
    public record ZeroInsight(
            String reasonCode,
            String message,
            String recommendedAction
    ) {

        /**
         * zero insight reason과 copy가 항상 존재하도록 검증한다.
         */
        public ZeroInsight {
            reasonCode = requireText(reasonCode, "reasonCode");
            message = requireText(message, "message");
            recommendedAction = requireText(recommendedAction, "recommendedAction");
        }
    }

    /**
     * stale/down 이후 회복 관찰 안내를 top-level state와 분리해 담는다.
     */
    public record Recovery(
            boolean isRecovering,
            OffsetDateTime lastHealthyAt,
            Integer retryAfterSeconds,
            String recommendedAction
    ) {
    }

    /**
     * current 15분 window의 request/error scalar만 담는다.
     */
    public record Metrics(
            long requestCount,
            long errorCount,
            BigDecimal errorRate
    ) {

        /**
         * request/error count가 음수가 되지 않도록 검증한다.
         */
        public Metrics {
            if (requestCount < 0) {
                throw new IllegalArgumentException("requestCount must not be negative");
            }
            if (errorCount < 0) {
                throw new IllegalArgumentException("errorCount must not be negative");
            }
        }
    }

    /**
     * Story 5.2에서 percentile 계산을 하지 않음을 명시하는 source-scoped placeholder다.
     */
    public record SourceScopedPercentiles(
            String source,
            String scope,
            String displayPolicy,
            String aggregatePolicy,
            List<Object> items,
            String applicationScopeFallback
    ) {

        /**
         * 후속 percentile story가 채울 위치와 현재 empty items contract를 보존한다.
         */
        public SourceScopedPercentiles {
            source = requireText(source, "source");
            scope = requireText(scope, "scope");
            displayPolicy = requireText(displayPolicy, "displayPolicy");
            aggregatePolicy = requireText(aggregatePolicy, "aggregatePolicy");
            items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
            applicationScopeFallback = requireText(applicationScopeFallback, "applicationScopeFallback");
        }

        /**
         * Story 5.2에서 고정된 빈 source-scoped percentile placeholder를 만든다.
         */
        public static SourceScopedPercentiles empty() {
            return new SourceScopedPercentiles(
                    "starter_canonical_percentile",
                    "instance_bucket",
                    "source_scoped_points",
                    "no_average_no_max_no_merge_no_histogram_recalculation",
                    List.of(),
                    "bucket_distribution_only_when_multiple_sources");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
