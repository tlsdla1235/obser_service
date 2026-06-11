package com.observation.portal.domain.snapshot.service;

import com.observation.portal.domain.catalog.repository.ApplicationRepository;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.PreviousState;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailRow;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotMarkerItem;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotMarkerReadModel;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotMarkerReadModel.EmptyState;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotMarkerReadModel.Horizon;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotStoredReadModelProjection;
import com.observation.portal.domain.snapshot.repository.DashboardSnapshotRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Stored dashboard snapshot rows를 marker timeline read model로 projection하는 service다.
 *
 * <p>horizon/default/clamp/order와 marker classifier orchestration만 수행하며 current dashboard, accepted bucket,
 * heartbeat, operational event promotion/dedup/suppression source를 join하지 않는다.</p>
 */
@Service
public class DashboardSnapshotMarkerService {

    private static final int HOURS_24 = 24;
    private static final int DAYS_7 = 7;
    private static final int DAYS_14 = 14;

    private final ApplicationRepository applicationRepository;
    private final DashboardSnapshotRepository dashboardSnapshotRepository;
    private final DashboardSnapshotDetailProjectionParser projectionParser;
    private final DashboardSnapshotMarkerClassifier markerClassifier;
    private final Clock clock;
    private final int retentionDays;

    /**
     * catalog path 정합성 lookup, stored snapshot repository, stored JSON parser/classifier와 retention clamp 설정을 주입한다.
     */
    public DashboardSnapshotMarkerService(
            ApplicationRepository applicationRepository,
            DashboardSnapshotRepository dashboardSnapshotRepository,
            DashboardSnapshotDetailProjectionParser projectionParser,
            DashboardSnapshotMarkerClassifier markerClassifier,
            Clock clock,
            @Value("${portal.dashboard-snapshots.retention-days:14}") int retentionDays) {
        this.applicationRepository = Objects.requireNonNull(
                applicationRepository,
                "applicationRepository must not be null");
        this.dashboardSnapshotRepository = Objects.requireNonNull(
                dashboardSnapshotRepository,
                "dashboardSnapshotRepository must not be null");
        this.projectionParser = Objects.requireNonNull(projectionParser, "projectionParser must not be null");
        this.markerClassifier = Objects.requireNonNull(markerClassifier, "markerClassifier must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null").withZone(ZoneOffset.UTC);
        if (retentionDays <= 0) {
            throw new IllegalArgumentException("retentionDays must be positive");
        }
        this.retentionDays = retentionDays;
    }

    /**
     * project/application catalog path 정합성이 맞으면 marker list를 반환하고, mismatch는 empty로 수렴한다.
     */
    @Transactional(readOnly = true)
    public Optional<DashboardSnapshotMarkerReadModel> getMarkers(
            UUID projectId,
            UUID applicationId,
            String since,
            String limit) {
        UUID requiredProjectId = Objects.requireNonNull(projectId, "projectId must not be null");
        UUID requiredApplicationId = Objects.requireNonNull(applicationId, "applicationId must not be null");
        EffectiveQuery effectiveQuery = effectiveQuery(since, limit);
        if (applicationRepository.findByIdAndProjectId(requiredApplicationId, requiredProjectId).isEmpty()) {
            return Optional.empty();
        }
        List<DashboardSnapshotDetailRow> rows = dashboardSnapshotRepository.findMarkerRows(
                requiredProjectId,
                requiredApplicationId,
                effectiveQuery.since(),
                effectiveQuery.until(),
                effectiveQuery.limit());
        OffsetDateTime snapshotCutoffUtc = snapshotCutoffUtc();
        List<DashboardSnapshotMarkerItem> markers = rows.stream()
                .filter(row -> rowInHorizon(row, effectiveQuery.since(), effectiveQuery.until()))
                .map(row -> marker(requiredProjectId, requiredApplicationId, row, snapshotCutoffUtc))
                .sorted(Comparator.comparing(DashboardSnapshotMarkerItem::currentWindowEndUtc)
                        .thenComparing(DashboardSnapshotMarkerItem::capturedAt)
                        .thenComparing(DashboardSnapshotMarkerItem::snapshotId))
                .toList();
        return Optional.of(new DashboardSnapshotMarkerReadModel(
                effectiveQuery.until(),
                requiredApplicationId,
                DashboardSnapshotMarkerReadModel.SOURCE,
                new Horizon(
                        effectiveQuery.since(),
                        effectiveQuery.until(),
                        effectiveQuery.requestedSince(),
                        DashboardSnapshotMarkerReadModel.DEFAULT_SINCE,
                        DashboardSnapshotMarkerReadModel.MAX_SINCE,
                        effectiveQuery.limit(),
                        DashboardSnapshotMarkerReadModel.MAX_LIMIT,
                        DashboardSnapshotMarkerReadModel.ORDER),
                markers.isEmpty() ? EmptyState.noSnapshotsInRetention() : null,
                markers));
    }

    private static boolean rowInHorizon(
            DashboardSnapshotDetailRow row,
            OffsetDateTime currentWindowEndSince,
            OffsetDateTime currentWindowEndUntil) {
        OffsetDateTime currentWindowEndUtc = row.currentWindowEndUtc().withOffsetSameInstant(ZoneOffset.UTC);
        return !currentWindowEndUtc.isBefore(currentWindowEndSince)
                && !currentWindowEndUtc.isAfter(currentWindowEndUntil);
    }

    private DashboardSnapshotMarkerItem marker(
            UUID projectId,
            UUID applicationId,
            DashboardSnapshotDetailRow row,
            OffsetDateTime snapshotCutoffUtc) {
        DashboardSnapshotStoredReadModelProjection projection = projectionParser.project(row.readModelJson());
        PreviousState previousState = dashboardSnapshotRepository.findPreviousSnapshot(
                        row.applicationId(),
                        row.currentWindowEndUtc(),
                        snapshotCutoffUtc)
                .map(DashboardSnapshotMarkerService::previousState)
                .orElseGet(PreviousState::none);
        return markerClassifier.marker(
                row,
                previousState,
                projection,
                DashboardSnapshotDetailService.snapshotLink(projectId, applicationId, row.snapshotId()));
    }

    private OffsetDateTime snapshotCutoffUtc() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
                .minusDays(retentionDays);
    }

    private static PreviousState previousState(com.observation.portal.domain.snapshot.model.DashboardSnapshotSourceRow row) {
        return new PreviousState(
                row.stateCode(),
                DashboardSnapshotDetailReadModel.SOURCE,
                row.snapshotId(),
                row.generatedAt());
    }

    private EffectiveQuery effectiveQuery(String since, String limit) {
        String requestedSince = requestedSince(since);
        Duration requestedDuration = switch (requestedSince) {
            case "24h" -> Duration.ofHours(HOURS_24);
            case "7d" -> Duration.ofDays(DAYS_7);
            case "14d" -> Duration.ofDays(DAYS_14);
            default -> throw new InvalidSnapshotMarkerQueryException("Unsupported since token: " + requestedSince);
        };
        Duration retentionDuration = Duration.ofDays(Math.max(1, Math.min(DAYS_14, retentionDays)));
        Duration effectiveDuration = requestedDuration.compareTo(retentionDuration) <= 0
                ? requestedDuration
                : retentionDuration;
        OffsetDateTime until = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return new EffectiveQuery(
                requestedSince,
                until.minus(effectiveDuration),
                until,
                effectiveLimit(limit));
    }

    private static String requestedSince(String since) {
        if (since == null) {
            return DashboardSnapshotMarkerReadModel.DEFAULT_SINCE;
        }
        String normalized = since.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new InvalidSnapshotMarkerQueryException("since must not be blank");
        }
        if (!"24h".equals(normalized) && !"7d".equals(normalized) && !"14d".equals(normalized)) {
            throw new InvalidSnapshotMarkerQueryException("since must be 24h, 7d, or 14d");
        }
        return normalized;
    }

    private static int effectiveLimit(String limit) {
        if (limit == null) {
            return DashboardSnapshotMarkerReadModel.DEFAULT_LIMIT;
        }
        String normalized = limit.trim();
        if (normalized.isEmpty()) {
            throw new InvalidSnapshotMarkerQueryException("limit must not be blank");
        }
        int parsedLimit;
        try {
            parsedLimit = Integer.parseInt(normalized);
        } catch (NumberFormatException exception) {
            throw new InvalidSnapshotMarkerQueryException("limit must be an integer");
        }
        if (parsedLimit <= 0) {
            throw new InvalidSnapshotMarkerQueryException("limit must be positive");
        }
        return Math.min(parsedLimit, DashboardSnapshotMarkerReadModel.MAX_LIMIT);
    }

    private record EffectiveQuery(
            String requestedSince,
            OffsetDateTime since,
            OffsetDateTime until,
            int limit
    ) {
    }
}
