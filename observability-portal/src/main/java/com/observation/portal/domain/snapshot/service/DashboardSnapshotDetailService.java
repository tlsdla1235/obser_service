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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    private final Clock clock;
    private final int retentionDays;

    /**
     * catalog path 정합성 lookup, stored snapshot repository, stored JSON parser/classifier만 주입한다.
     */
    public DashboardSnapshotDetailService(
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
        OffsetDateTime snapshotCutoffUtc = snapshotCutoffUtc();
        return dashboardSnapshotRepository.findDetailRow(
                        requiredProjectId,
                        requiredApplicationId,
                        requiredSnapshotId)
                .filter(row -> snapshotInRetention(row, snapshotCutoffUtc))
                .map(row -> toReadModel(requiredProjectId, requiredApplicationId, row, snapshotCutoffUtc));
    }

    /**
     * snapshot mode가 live dashboard surface를 같은 컴포넌트로 복원하도록 저장된 full read model JSON을 그대로 반환한다.
     *
     * <p>catalog path 정합성과 retention은 {@link #getDetail}과 동일하게 검증한다. capture 시 enricher가 mode=snapshot,
     * readSemantics=snapshot으로 덮어쓴 full read model을 저장하므로, 추가 가공/역직렬화 없이 stored `read_model_json`
     * 문자열을 그대로 내려준다. (record 다중 생성자 round-trip은 일부 필드를 잃을 수 있어 raw passthrough가 충실하다.)</p>
     */
    @Transactional(readOnly = true)
    public Optional<String> getStoredReadModelJson(
            UUID projectId,
            UUID applicationId,
            UUID snapshotId) {
        UUID requiredProjectId = Objects.requireNonNull(projectId, "projectId must not be null");
        UUID requiredApplicationId = Objects.requireNonNull(applicationId, "applicationId must not be null");
        UUID requiredSnapshotId = Objects.requireNonNull(snapshotId, "snapshotId must not be null");
        if (applicationRepository.findByIdAndProjectId(requiredApplicationId, requiredProjectId).isEmpty()) {
            return Optional.empty();
        }
        OffsetDateTime snapshotCutoffUtc = snapshotCutoffUtc();
        return dashboardSnapshotRepository.findDetailRow(
                        requiredProjectId,
                        requiredApplicationId,
                        requiredSnapshotId)
                .filter(row -> snapshotInRetention(row, snapshotCutoffUtc))
                .map(DashboardSnapshotDetailRow::readModelJson);
    }

    private OffsetDateTime snapshotCutoffUtc() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
                .minusDays(retentionDays);
    }

    private static boolean snapshotInRetention(DashboardSnapshotDetailRow row, OffsetDateTime snapshotCutoffUtc) {
        return !row.currentWindowEndUtc()
                .withOffsetSameInstant(ZoneOffset.UTC)
                .isBefore(snapshotCutoffUtc);
    }

    private DashboardSnapshotDetailReadModel toReadModel(
            UUID projectId,
            UUID applicationId,
            DashboardSnapshotDetailRow row,
            OffsetDateTime snapshotCutoffUtc) {
        DashboardSnapshotStoredReadModelProjection projection = projectionParser.project(row.readModelJson());
        PreviousState previousState = previousState(row, snapshotCutoffUtc);
        LastHealthyAt lastHealthyAt = lastHealthyAt(row, snapshotCutoffUtc);
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

    private PreviousState previousState(DashboardSnapshotDetailRow row, OffsetDateTime snapshotCutoffUtc) {
        return dashboardSnapshotRepository.findPreviousSnapshot(
                        row.applicationId(),
                        row.currentWindowEndUtc(),
                        snapshotCutoffUtc)
                .map(DashboardSnapshotDetailService::previousState)
                .orElseGet(PreviousState::none);
    }

    private static PreviousState previousState(DashboardSnapshotSourceRow row) {
        return new PreviousState(
                row.stateCode(),
                DashboardSnapshotDetailReadModel.SOURCE,
                row.snapshotId(),
                row.generatedAt());
    }

    private LastHealthyAt lastHealthyAt(DashboardSnapshotDetailRow row, OffsetDateTime snapshotCutoffUtc) {
        return dashboardSnapshotRepository.findPreviousActiveSnapshot(
                        row.applicationId(),
                        row.currentWindowEndUtc(),
                        snapshotCutoffUtc)
                .map(sourceRow -> new LastHealthyAt(
                        sourceRow.generatedAt(),
                        DashboardSnapshotDetailReadModel.SOURCE,
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

}
