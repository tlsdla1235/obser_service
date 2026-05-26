package com.observation.portal.domain.instance.service;

import com.observation.portal.domain.catalog.entity.ApplicationEntity;
import com.observation.portal.domain.catalog.entity.ApplicationInstanceEntity;
import com.observation.portal.domain.catalog.repository.ApplicationInstanceRepository;
import com.observation.portal.domain.catalog.repository.ApplicationRepository;
import com.observation.portal.domain.instance.model.InstanceSnapshotTrendReadModel;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotTrendRow;
import com.observation.portal.domain.snapshot.repository.DashboardSnapshotRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Stored dashboard snapshot에서 selected instance의 bounded trend read model을 projection하는 service다.
 *
 * <p>이 service는 project/application/instance membership repository, `DashboardSnapshotRepository`, 그리고
 * `instanceSummary.items[]` parser만 사용한다. Snapshot absence는 live accepted bucket, heartbeat, current dashboard,
 * current evidence, lifecycle/rule/endpoint priority 재계산으로 이어지지 않는다.</p>
 */
@Service
public class InstanceSnapshotTrendService {

    private static final int DAYS_7 = 7;
    private static final int DAYS_14 = 14;

    private final ApplicationRepository applicationRepository;
    private final ApplicationInstanceRepository applicationInstanceRepository;
    private final DashboardSnapshotRepository dashboardSnapshotRepository;
    private final InstanceSnapshotTrendParser parser;
    private final Clock clock;
    private final int retentionDays;

    /**
     * membership lookup repository와 stored snapshot projection dependency를 주입한다.
     *
     * <p>`retentionDays`는 dashboard snapshot retention clamp에만 사용하며, 별도 설정이 없으면 14일로 동작한다.</p>
     */
    public InstanceSnapshotTrendService(
            ApplicationRepository applicationRepository,
            ApplicationInstanceRepository applicationInstanceRepository,
            DashboardSnapshotRepository dashboardSnapshotRepository,
            InstanceSnapshotTrendParser parser,
            Clock clock,
            @Value("${portal.dashboard-snapshots.retention-days:14}") int retentionDays) {
        this.applicationRepository = Objects.requireNonNull(
                applicationRepository,
                "applicationRepository must not be null");
        this.applicationInstanceRepository = Objects.requireNonNull(
                applicationInstanceRepository,
                "applicationInstanceRepository must not be null");
        this.dashboardSnapshotRepository = Objects.requireNonNull(
                dashboardSnapshotRepository,
                "dashboardSnapshotRepository must not be null");
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null").withZone(ZoneOffset.UTC);
        if (retentionDays <= 0) {
            throw new IllegalArgumentException("retentionDays must be positive");
        }
        this.retentionDays = retentionDays;
    }

    /**
     * UUID path membership이 맞으면 selected instance snapshot trend를 반환하고, mismatch는 empty로 수렴한다.
     *
     * <p>`since` 생략은 `7d`, 지원 token은 `7d`/`14d`이며 retention과 최대 14일로 clamp한다. `limit` 생략은 168,
     * 최대 336으로 clamp한다.</p>
     */
    @Transactional(readOnly = true)
    public Optional<InstanceSnapshotTrendReadModel> getTrend(
            UUID projectId,
            UUID applicationId,
            UUID instanceId,
            String since,
            String limit) {
        UUID requiredProjectId = Objects.requireNonNull(projectId, "projectId must not be null");
        UUID requiredApplicationId = Objects.requireNonNull(applicationId, "applicationId must not be null");
        UUID requiredInstanceId = Objects.requireNonNull(instanceId, "instanceId must not be null");
        EffectiveQuery effectiveQuery = effectiveQuery(since, limit);

        Optional<ApplicationEntity> application = applicationRepository.findByIdAndProjectId(
                requiredApplicationId,
                requiredProjectId);
        if (application.isEmpty()) {
            return Optional.empty();
        }
        Optional<ApplicationInstanceEntity> instance = applicationInstanceRepository.findByIdAndApplicationId(
                requiredInstanceId,
                requiredApplicationId);
        if (instance.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(buildReadModel(
                application.orElseThrow(),
                instance.orElseThrow(),
                requiredInstanceId,
                effectiveQuery));
    }

    private InstanceSnapshotTrendReadModel buildReadModel(
            ApplicationEntity application,
            ApplicationInstanceEntity instance,
            UUID targetInstanceId,
            EffectiveQuery effectiveQuery) {
        List<DashboardSnapshotTrendRow> rows = dashboardSnapshotRepository.findTrendRowsNewestFirst(
                application.projectId(),
                application.id(),
                effectiveQuery.since(),
                effectiveQuery.until(),
                effectiveQuery.limit());
        List<InstanceSnapshotTrendReadModel.Point> points = rows.stream()
                .flatMap(row -> parser.projectPoint(row, targetInstanceId).stream())
                .sorted(Comparator.comparing(InstanceSnapshotTrendReadModel.Point::capturedAt)
                        .thenComparing(InstanceSnapshotTrendReadModel.Point::snapshotId))
                .toList();

        String dashboardLink = dashboardLink(application.projectId(), application.id());
        String evidenceLink = evidenceLink(application.projectId(), application.id(), instance.id());
        return new InstanceSnapshotTrendReadModel(
                effectiveQuery.until(),
                new InstanceSnapshotTrendReadModel.Application(
                        application.projectId(),
                        application.id(),
                        application.name(),
                        application.environment(),
                        new InstanceSnapshotTrendReadModel.ApplicationLinks(dashboardLink)),
                new InstanceSnapshotTrendReadModel.Instance(
                        instance.id(),
                        instance.instanceName(),
                        instance.firstSeenAt(),
                        instance.lastSeenAt(),
                        new InstanceSnapshotTrendReadModel.InstanceLinks(evidenceLink)),
                InstanceSnapshotTrendReadModel.SOURCE,
                new InstanceSnapshotTrendReadModel.Horizon(
                        effectiveQuery.since(),
                        effectiveQuery.until(),
                        effectiveQuery.requestedSince(),
                        InstanceSnapshotTrendReadModel.DEFAULT_SINCE,
                        InstanceSnapshotTrendReadModel.MAX_SINCE,
                        effectiveQuery.limit(),
                        InstanceSnapshotTrendReadModel.MAX_LIMIT,
                        InstanceSnapshotTrendReadModel.ORDER),
                points);
    }

    private EffectiveQuery effectiveQuery(String since, String limit) {
        String requestedSince = requestedSince(since);
        int requestedDays = switch (requestedSince) {
            case "7d" -> DAYS_7;
            case "14d" -> DAYS_14;
            default -> throw new InvalidSnapshotTrendQueryException("Unsupported since token: " + requestedSince);
        };
        int maxRetentionDays = Math.max(1, Math.min(DAYS_14, retentionDays));
        int effectiveDays = Math.min(requestedDays, maxRetentionDays);
        int effectiveLimit = effectiveLimit(limit);
        OffsetDateTime until = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return new EffectiveQuery(
                requestedSince,
                until.minusDays(effectiveDays),
                until,
                effectiveLimit);
    }

    private static String requestedSince(String since) {
        if (since == null) {
            return InstanceSnapshotTrendReadModel.DEFAULT_SINCE;
        }
        String normalized = since.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new InvalidSnapshotTrendQueryException("since must not be blank");
        }
        if (!"7d".equals(normalized) && !"14d".equals(normalized)) {
            throw new InvalidSnapshotTrendQueryException("since must be 7d or 14d");
        }
        return normalized;
    }

    private static int effectiveLimit(String limit) {
        if (limit == null) {
            return InstanceSnapshotTrendReadModel.DEFAULT_LIMIT;
        }
        String normalized = limit.trim();
        if (normalized.isEmpty()) {
            throw new InvalidSnapshotTrendQueryException("limit must not be blank");
        }
        int parsedLimit;
        try {
            parsedLimit = Integer.parseInt(normalized);
        } catch (NumberFormatException exception) {
            throw new InvalidSnapshotTrendQueryException("limit must be an integer");
        }
        if (parsedLimit <= 0) {
            throw new InvalidSnapshotTrendQueryException("limit must be positive");
        }
        return Math.min(parsedLimit, InstanceSnapshotTrendReadModel.MAX_LIMIT);
    }

    /**
     * snapshot trend response와 dashboard response가 같은 application dashboard path를 공유하도록 link를 만든다.
     */
    public static String dashboardLink(UUID projectId, UUID applicationId) {
        return "/api/projects/%s/applications/%s/dashboard".formatted(projectId, applicationId);
    }

    /**
     * snapshot trend response가 selected instance evidence path를 UUID 기반으로 가리키도록 link를 만든다.
     */
    public static String evidenceLink(UUID projectId, UUID applicationId, UUID instanceId) {
        return "/api/projects/%s/applications/%s/instances/%s/evidence".formatted(
                projectId,
                applicationId,
                instanceId);
    }

    /**
     * Story 5.7 endpoint path helper다. Story 5.6 evidence link 보강에서도 같은 UUID path를 사용한다.
     */
    public static String snapshotTrendLink(UUID projectId, UUID applicationId, UUID instanceId) {
        return "/api/projects/%s/applications/%s/instances/%s/snapshot-trend".formatted(
                projectId,
                applicationId,
                instanceId);
    }

    private record EffectiveQuery(
            String requestedSince,
            OffsetDateTime since,
            OffsetDateTime until,
            int limit
    ) {
    }
}
