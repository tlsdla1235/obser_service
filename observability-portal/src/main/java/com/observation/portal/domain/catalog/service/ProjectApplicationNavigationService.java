package com.observation.portal.domain.catalog.service;

import com.observation.portal.common.time.AcceptedBucketFreshness;
import com.observation.portal.common.time.AcceptedBucketFreshnessEvaluator;
import com.observation.portal.common.time.AcceptedBucketFreshnessStatus;
import com.observation.portal.domain.bucket.repository.MetricBucketRepository;
import com.observation.portal.domain.catalog.entity.ApplicationEntity;
import com.observation.portal.domain.catalog.entity.ProjectEntity;
import com.observation.portal.domain.catalog.model.ProjectApplicationNavigationReadModel;
import com.observation.portal.domain.catalog.model.ProjectKeyCandidate;
import com.observation.portal.domain.catalog.model.ProjectNavigationReadModel;
import com.observation.portal.domain.catalog.repository.ApplicationRepository;
import com.observation.portal.domain.catalog.repository.ProjectRepository;
import com.observation.portal.domain.ingest.model.StarterHeartbeatTelemetryRecord;
import com.observation.portal.domain.ingest.repository.StarterHeartbeatTelemetryRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Project Entry와 Application List가 사용하는 read-only navigation read model을 만든다.
 *
 * <p>catalog, accepted bucket freshness, starter heartbeat latest row를 조합하되 dashboard state/triage 판단은 생성하지 않는다.</p>
 */
@Service
public class ProjectApplicationNavigationService {

    private static final Sort PROJECT_SORT = Sort.by(Sort.Direction.ASC, "name");
    private static final Duration STARTER_HEARTBEAT_RECENT_WINDOW = Duration.ofSeconds(90);
    private static final String ACCEPTED_BUCKET_SOURCE = "accepted_bucket";
    private static final String STARTER_HEARTBEAT_SOURCE = "starter_heartbeat";
    private static final String LIGHT_BADGE_SOURCE = "server_light_navigation_read_model";
    private static final String STATE_IMPACT_NONE = "none";

    private final ProjectRepository projectRepository;
    private final ApplicationRepository applicationRepository;
    private final MetricBucketRepository metricBucketRepository;
    private final StarterHeartbeatTelemetryRepository heartbeatTelemetryRepository;
    private final AcceptedBucketFreshnessEvaluator freshnessEvaluator;
    private final Clock clock;

    /**
     * navigation read model 생성에 필요한 read-only repository와 시간 평가 component를 주입한다.
     */
    public ProjectApplicationNavigationService(
            ProjectRepository projectRepository,
            ApplicationRepository applicationRepository,
            MetricBucketRepository metricBucketRepository,
            StarterHeartbeatTelemetryRepository heartbeatTelemetryRepository,
            AcceptedBucketFreshnessEvaluator freshnessEvaluator,
            Clock clock) {
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository must not be null");
        this.applicationRepository = Objects.requireNonNull(
                applicationRepository,
                "applicationRepository must not be null");
        this.metricBucketRepository = Objects.requireNonNull(
                metricBucketRepository,
                "metricBucketRepository must not be null");
        this.heartbeatTelemetryRepository = Objects.requireNonNull(
                heartbeatTelemetryRepository,
                "heartbeatTelemetryRepository must not be null");
        this.freshnessEvaluator = Objects.requireNonNull(freshnessEvaluator, "freshnessEvaluator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null").withZone(ZoneOffset.UTC);
    }

    /**
     * Project Entry용 project 목록 read model을 반환한다.
     *
     * <p>recentConcern은 Story 5.4 triage source가 아직 없으므로 null로 유지한다.</p>
     */
    @Transactional(readOnly = true)
    public ProjectNavigationReadModel listProjects() {
        OffsetDateTime generatedAt = nowUtc();
        List<ProjectNavigationReadModel.ProjectItem> projects = projectRepository.findAll(PROJECT_SORT).stream()
                .map(ProjectEntity::toCandidate)
                .map(this::toProjectItem)
                .toList();
        return new ProjectNavigationReadModel(generatedAt, projects);
    }

    /**
     * Application List용 project-scoped application navigation read model을 반환한다.
     *
     * <p>project가 없으면 controller가 404로 매핑할 수 있도록 empty를 반환한다.</p>
     */
    @Transactional(readOnly = true)
    public Optional<ProjectApplicationNavigationReadModel> listApplications(UUID projectId) {
        UUID requiredProjectId = Objects.requireNonNull(projectId, "projectId must not be null");
        return projectRepository.findById(requiredProjectId)
                .map(ProjectEntity::toCandidate)
                .map(project -> {
                    List<ProjectApplicationNavigationReadModel.ApplicationItem> applications =
                            applicationRepository.findByProjectIdOrderByNameAscEnvironmentAsc(project.projectId())
                                    .stream()
                                    .map(this::toApplicationItem)
                                    .toList();
                    return new ProjectApplicationNavigationReadModel(
                            nowUtc(),
                            new ProjectApplicationNavigationReadModel.ProjectSummary(
                                    project.projectId(),
                                    project.projectName()),
                            applications);
                });
    }

    private ProjectNavigationReadModel.ProjectItem toProjectItem(ProjectKeyCandidate project) {
        List<ApplicationEntity> applications = applicationRepository
                .findByProjectIdOrderByNameAscEnvironmentAsc(project.projectId());
        int issueCandidateCount = Math.toIntExact(applications.stream()
                .map(this::navigationSignals)
                .filter(NavigationSignals::hasSetupConnectionIssueCandidate)
                .count());
        return new ProjectNavigationReadModel.ProjectItem(
                project.projectId(),
                project.projectName(),
                applications.size(),
                issueCandidateCount,
                null,
                new ProjectNavigationReadModel.ProjectLinks("/api/projects/%s/applications".formatted(
                        project.projectId())));
    }

    private ProjectApplicationNavigationReadModel.ApplicationItem toApplicationItem(ApplicationEntity application) {
        NavigationSignals signals = navigationSignals(application);
        return new ProjectApplicationNavigationReadModel.ApplicationItem(
                application.id(),
                application.name(),
                application.environment(),
                signals.metricData(),
                signals.starterConnection(),
                unknownLifecycleBadge(),
                null,
                new ProjectApplicationNavigationReadModel.ApplicationLinks(
                        "/api/projects/%s/applications/%s/dashboard".formatted(
                                application.projectId(),
                                application.id())));
    }

    private NavigationSignals navigationSignals(ApplicationEntity application) {
        ProjectApplicationNavigationReadModel.MetricDataSummary metricData = metricData(application.id());
        ProjectApplicationNavigationReadModel.StarterConnectionSummary starterConnection =
                starterConnection(application.projectId(), application.name(), application.environment());
        boolean hasIssueCandidate = !metricData.freshnessLabel().equals("current")
                || !starterConnection.freshnessLabel().equals("recent");
        return new NavigationSignals(metricData, starterConnection, hasIssueCandidate);
    }

    private ProjectApplicationNavigationReadModel.MetricDataSummary metricData(UUID applicationId) {
        Optional<OffsetDateTime> latestBucketEndUtc = metricBucketRepository
                .findLatestBucketEndUtcByApplicationId(applicationId);
        AcceptedBucketFreshness freshness = freshnessEvaluator.evaluate(
                latestBucketEndUtc.map(OffsetDateTime::toInstant).orElse(null));
        OffsetDateTime lastAcceptedBucketAt = freshness.lastAcceptedBucketEndUtc()
                .map(ProjectApplicationNavigationService::toUtcOffsetDateTime)
                .orElse(null);
        return new ProjectApplicationNavigationReadModel.MetricDataSummary(
                ACCEPTED_BUCKET_SOURCE,
                lastAcceptedBucketAt,
                freshnessLabel(freshness.status()));
    }

    private ProjectApplicationNavigationReadModel.StarterConnectionSummary starterConnection(
            UUID projectId,
            String applicationName,
            String environment) {
        Optional<StarterHeartbeatTelemetryRecord> latestHeartbeat = heartbeatTelemetryRepository
                .findLatestByApplicationScope(projectId, applicationName, environment);
        if (latestHeartbeat.isEmpty()) {
            return new ProjectApplicationNavigationReadModel.StarterConnectionSummary(
                    STARTER_HEARTBEAT_SOURCE,
                    null,
                    "missing",
                    "unknown",
                    "starter_telemetry_missing",
                    STATE_IMPACT_NONE);
        }

        OffsetDateTime lastHeartbeatAt = toUtcOffsetDateTime(latestHeartbeat.orElseThrow()
                .lastReceivedAtUtc()
                .toInstant());
        boolean recent = Duration.between(lastHeartbeatAt.toInstant(), clock.instant())
                .compareTo(STARTER_HEARTBEAT_RECENT_WINDOW) <= 0;
        if (recent) {
            return new ProjectApplicationNavigationReadModel.StarterConnectionSummary(
                    STARTER_HEARTBEAT_SOURCE,
                    lastHeartbeatAt,
                    "received",
                    "recent",
                    "starter_connected",
                    STATE_IMPACT_NONE);
        }
        return new ProjectApplicationNavigationReadModel.StarterConnectionSummary(
                STARTER_HEARTBEAT_SOURCE,
                lastHeartbeatAt,
                "received",
                "stale",
                "starter_telemetry_stale",
                STATE_IMPACT_NONE);
    }

    private static ProjectApplicationNavigationReadModel.LifecycleBadge unknownLifecycleBadge() {
        return new ProjectApplicationNavigationReadModel.LifecycleBadge(
                LIGHT_BADGE_SOURCE,
                "unknown",
                "Metric data unknown");
    }

    private static String freshnessLabel(AcceptedBucketFreshnessStatus status) {
        return switch (status) {
            case WAITING_FIRST_DATA -> "waiting_first_data";
            case CURRENT -> "current";
            case STALE_CANDIDATE -> "stale_candidate";
            case DOWN_CANDIDATE -> "down_candidate";
        };
    }

    private OffsetDateTime nowUtc() {
        return toUtcOffsetDateTime(clock.instant());
    }

    private static OffsetDateTime toUtcOffsetDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /**
     * Project와 Application navigation 양쪽에서 재사용하는 source별 light signal 묶음이다.
     */
    private record NavigationSignals(
            ProjectApplicationNavigationReadModel.MetricDataSummary metricData,
            ProjectApplicationNavigationReadModel.StarterConnectionSummary starterConnection,
            boolean hasSetupConnectionIssueCandidate
    ) {
    }
}
