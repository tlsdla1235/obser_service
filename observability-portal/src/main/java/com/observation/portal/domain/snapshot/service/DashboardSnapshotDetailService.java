package com.observation.portal.domain.snapshot.service;

import com.observation.portal.domain.catalog.repository.ApplicationRepository;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.LastHealthyAt;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.PreviousState;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.SnapshotLinks;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.SnapshotMetadata;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.SnapshotReadSemantics;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.Window;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailRow;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotMarkerItem;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotSourceRow;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotStoredReadModelProjection;
import com.observation.portal.domain.snapshot.repository.DashboardSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Stored dashboard snapshot detail을 row metadata와 stored `read_model_json`에서만 조립하는 service다.
 *
 * <p>current dashboard generation, accepted bucket lookup, heartbeat lookup, lifecycle/rule/endpoint priority 계산을
 * 호출하지 않고, retention/detail miss는 empty로 수렴시켜 controller가 404로 매핑하게 한다.</p>
 */
@Service
public class DashboardSnapshotDetailService {

    private final ApplicationRepository applicationRepository;
    private final DashboardSnapshotRepository dashboardSnapshotRepository;
    private final DashboardSnapshotDetailProjectionParser projectionParser;
    private final DashboardSnapshotMarkerClassifier markerClassifier;

    /**
     * catalog path 정합성 lookup, stored snapshot repository, stored JSON parser/classifier만 주입한다.
     */
    public DashboardSnapshotDetailService(
            ApplicationRepository applicationRepository,
            DashboardSnapshotRepository dashboardSnapshotRepository,
            DashboardSnapshotDetailProjectionParser projectionParser,
            DashboardSnapshotMarkerClassifier markerClassifier) {
        this.applicationRepository = Objects.requireNonNull(
                applicationRepository,
                "applicationRepository must not be null");
        this.dashboardSnapshotRepository = Objects.requireNonNull(
                dashboardSnapshotRepository,
                "dashboardSnapshotRepository must not be null");
        this.projectionParser = Objects.requireNonNull(projectionParser, "projectionParser must not be null");
        this.markerClassifier = Objects.requireNonNull(markerClassifier, "markerClassifier must not be null");
    }

    /**
     * project/application/snapshot catalog path 정합성이 맞는 stored snapshot detail을 반환하고, mismatch는 empty로 둔다.
     */
    @Transactional(readOnly = true)
    public Optional<DashboardSnapshotDetailReadModel> getDetail(
            UUID projectId,
            UUID applicationId,
            UUID snapshotId) {
        UUID requiredProjectId = Objects.requireNonNull(projectId, "projectId must not be null");
        UUID requiredApplicationId = Objects.requireNonNull(applicationId, "applicationId must not be null");
        UUID requiredSnapshotId = Objects.requireNonNull(snapshotId, "snapshotId must not be null");
        if (applicationRepository.findByIdAndProjectId(requiredApplicationId, requiredProjectId).isEmpty()) {
            return Optional.empty();
        }
        return dashboardSnapshotRepository.findDetailRow(
                        requiredProjectId,
                        requiredApplicationId,
                        requiredSnapshotId)
                .map(row -> toReadModel(requiredProjectId, requiredApplicationId, row));
    }

    private DashboardSnapshotDetailReadModel toReadModel(
            UUID projectId,
            UUID applicationId,
            DashboardSnapshotDetailRow row) {
        DashboardSnapshotStoredReadModelProjection projection = projectionParser.project(row.readModelJson());
        PreviousState previousState = previousState(row);
        LastHealthyAt lastHealthyAt = lastHealthyAt(row);
        String snapshotLink = snapshotLink(projectId, applicationId, row.snapshotId());
        DashboardSnapshotMarkerItem marker = markerClassifier.marker(row, previousState, projection, snapshotLink);
        return new DashboardSnapshotDetailReadModel(
                row.generatedAt(),
                DashboardSnapshotDetailReadModel.SOURCE,
                SnapshotReadSemantics.storedSnapshotDetail(),
                snapshot(row),
                marker,
                previousState,
                lastHealthyAt,
                markerClassifier.recoveryMarker(marker, previousState, lastHealthyAt),
                projection.readModel(),
                projection.snapshotEndpointEvidence(),
                projection.instanceSummary(),
                new SnapshotLinks(snapshotLink, markerListLink(projectId, applicationId)));
    }

    private PreviousState previousState(DashboardSnapshotDetailRow row) {
        return dashboardSnapshotRepository.findPreviousSnapshot(row.applicationId(), row.currentWindowEndUtc())
                .map(DashboardSnapshotDetailService::previousState)
                .orElseGet(PreviousState::none);
    }

    private static PreviousState previousState(DashboardSnapshotSourceRow row) {
        String source = knownState(row.stateCode())
                ? "previous_dashboard_snapshot"
                : "previous_dashboard_snapshot_unknown_state";
        return new PreviousState(
                row.stateCode(),
                source,
                row.snapshotId(),
                row.generatedAt());
    }

    private LastHealthyAt lastHealthyAt(DashboardSnapshotDetailRow row) {
        return dashboardSnapshotRepository.findPreviousActiveSnapshot(row.applicationId(), row.currentWindowEndUtc())
                .map(sourceRow -> new LastHealthyAt(
                        sourceRow.generatedAt(),
                        "previous_active_dashboard_snapshot",
                        sourceRow.snapshotId()))
                .orElseGet(LastHealthyAt::none);
    }

    private static SnapshotMetadata snapshot(DashboardSnapshotDetailRow row) {
        return new SnapshotMetadata(
                row.snapshotId(),
                row.generatedAt(),
                row.generatedAt(),
                new Window(row.currentWindowStartUtc(), row.currentWindowEndUtc()),
                new Window(row.baselineWindowStartUtc(), row.baselineWindowEndUtc()),
                row.captureReason(),
                row.stateCode(),
                row.primaryRuleId(),
                row.primaryEndpointKey(),
                row.maxConfidence());
    }

    /**
     * Snapshot detail path를 marker/detail response가 공유하도록 UUID path로 만든다.
     */
    public static String snapshotLink(UUID projectId, UUID applicationId, UUID snapshotId) {
        return "/api/projects/%s/applications/%s/dashboard/snapshots/%s".formatted(
                projectId,
                applicationId,
                snapshotId);
    }

    /**
     * Marker list 기본 query link를 detail response에서 제공한다.
     */
    public static String markerListLink(UUID projectId, UUID applicationId) {
        return "/api/projects/%s/applications/%s/dashboard/snapshot-markers?since=24h".formatted(
                projectId,
                applicationId);
    }

    static boolean knownState(String stateCode) {
        if (stateCode == null || stateCode.isBlank()) {
            return false;
        }
        return switch (stateCode.trim().toLowerCase(Locale.ROOT)) {
            case "active", "idle", "waiting_first_data", "degraded", "stale", "down", "unknown" -> true;
            default -> false;
        };
    }
}
