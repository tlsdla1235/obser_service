package com.observation.portal.domain.catalog.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Application List 화면이 사용할 read-only navigation 응답 모델이다.
 *
 * <p>accepted bucket freshness와 starter heartbeat summary를 별도 field로 내려 dashboard 판단을 복제하지 않는다.</p>
 */
public record ProjectApplicationNavigationReadModel(
        OffsetDateTime generatedAt,
        ProjectSummary project,
        List<ApplicationItem> applications
) {

    /**
     * 생성 시각, 프로젝트 identity, application 목록을 null 없이 보존한다.
     */
    public ProjectApplicationNavigationReadModel {
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        Objects.requireNonNull(project, "project must not be null");
        applications = List.copyOf(Objects.requireNonNull(applications, "applications must not be null"));
    }

    /**
     * Application List가 속한 project scope의 최소 identity다.
     */
    public record ProjectSummary(UUID projectId, String name) {

        /**
         * project id와 표시 이름이 존재하도록 검증한다.
         */
        public ProjectSummary {
            Objects.requireNonNull(projectId, "projectId must not be null");
            name = requireText(name, "name");
        }
    }

    /**
     * Application List의 단일 application/environment navigation 항목이다.
     *
     * <p>metricData와 starterConnection은 서로 다른 source summary이며 하나의 health 판단으로 합치지 않는다.</p>
     */
    public record ApplicationItem(
            UUID applicationId,
            String name,
            String environment,
            MetricDataSummary metricData,
            StarterConnectionSummary starterConnection,
            LifecycleBadge lifecycleBadge,
            ConcernSummary topConcern,
            ApplicationLinks links
    ) {

        /**
         * application identity와 분리된 source summary, dashboard link가 모두 존재하도록 검증한다.
         */
        public ApplicationItem {
            Objects.requireNonNull(applicationId, "applicationId must not be null");
            name = requireText(name, "name");
            environment = requireText(environment, "environment");
            Objects.requireNonNull(metricData, "metricData must not be null");
            Objects.requireNonNull(starterConnection, "starterConnection must not be null");
            Objects.requireNonNull(lifecycleBadge, "lifecycleBadge must not be null");
            Objects.requireNonNull(links, "links must not be null");
        }
    }

    /**
     * accepted bucket source만 사용한 metric freshness light summary다.
     */
    public record MetricDataSummary(
            String statusSource,
            OffsetDateTime lastAcceptedBucketAt,
            String freshnessLabel
    ) {

        /**
         * status source와 freshness label을 필수 값으로 검증한다.
         */
        public MetricDataSummary {
            statusSource = requireText(statusSource, "statusSource");
            freshnessLabel = requireText(freshnessLabel, "freshnessLabel");
        }
    }

    /**
     * starter heartbeat source만 사용한 connection/liveness light summary다.
     */
    public record StarterConnectionSummary(
            String statusSource,
            OffsetDateTime lastHeartbeatAt,
            String heartbeatStatus,
            String freshnessLabel,
            String connectionMeaning,
            String stateImpact
    ) {

        /**
         * heartbeat source, recency label, 의미, state 영향 값을 검증한다.
         */
        public StarterConnectionSummary {
            statusSource = requireText(statusSource, "statusSource");
            heartbeatStatus = requireText(heartbeatStatus, "heartbeatStatus");
            freshnessLabel = requireText(freshnessLabel, "freshnessLabel");
            connectionMeaning = requireText(connectionMeaning, "connectionMeaning");
            stateImpact = requireText(stateImpact, "stateImpact");
        }
    }

    /**
     * Application List용 server-computed light lifecycle badge다.
     *
     * <p>Story 5.1에서는 sample/readiness와 triage source가 부족하면 unknown으로만 표현한다.</p>
     */
    public record LifecycleBadge(String source, String code, String label) {

        /**
         * badge source, code, label이 response에 항상 존재하도록 검증한다.
         */
        public LifecycleBadge {
            source = requireText(source, "source");
            code = requireText(code, "code");
            label = requireText(label, "label");
        }
    }

    /**
     * 후속 triage source가 제공될 때만 채워질 최대 1개의 light concern summary다.
     */
    public record ConcernSummary(String code, String label, String source) {

        /**
         * concern code, label, source를 공백 없이 보존한다.
         */
        public ConcernSummary {
            code = requireText(code, "code");
            label = requireText(label, "label");
            source = requireText(source, "source");
        }
    }

    /**
     * application dashboard 진입에 사용할 navigation link 모음이다.
     */
    public record ApplicationLinks(String dashboard) {

        /**
         * dashboard link가 비어 있지 않도록 보장한다.
         */
        public ApplicationLinks {
            dashboard = requireText(dashboard, "dashboard");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
