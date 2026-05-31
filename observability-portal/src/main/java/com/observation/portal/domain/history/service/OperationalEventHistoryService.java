package com.observation.portal.domain.history.service;

import com.observation.portal.domain.catalog.repository.ApplicationRepository;
import com.observation.portal.domain.history.model.OperationalEventHistoryReadModel;
import com.observation.portal.domain.history.model.OperationalEventHistoryReadModel.Horizon;
import com.observation.portal.domain.history.model.OperationalEventItem;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailRow;
import com.observation.portal.domain.snapshot.repository.DashboardSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Operational event history response를 stored `dashboard_snapshots` source boundary에서 조립하는 service다.
 *
 * <p>current dashboard, accepted bucket, heartbeat, lifecycle, triage, endpoint priority, p95/p99 source를 조회하지 않고
 * catalog path 정합성 확인 후 snapshot repository row만 읽는다.</p>
 */
@Service
public class OperationalEventHistoryService {

    private static final int MIN_SOURCE_FETCH_LIMIT = 336;
    private static final int SOURCE_FETCH_LIMIT_MULTIPLIER = 4;
    private static final int MAX_SOURCE_FETCH_LIMIT = 500;

    private final ApplicationRepository applicationRepository;
    private final DashboardSnapshotRepository dashboardSnapshotRepository;
    private final OperationalEventHistoryProjector projector;
    private final Clock clock;

    /**
     * catalog path 정합성 repository, stored snapshot repository, 5.9-b projector extension point, UTC clock을 주입한다.
     */
    public OperationalEventHistoryService(
            ApplicationRepository applicationRepository,
            DashboardSnapshotRepository dashboardSnapshotRepository,
            OperationalEventHistoryProjector projector,
            Clock clock) {
        this.applicationRepository = Objects.requireNonNull(
                applicationRepository,
                "applicationRepository must not be null");
        this.dashboardSnapshotRepository = Objects.requireNonNull(
                dashboardSnapshotRepository,
                "dashboardSnapshotRepository must not be null");
        this.projector = Objects.requireNonNull(projector, "projector must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null").withZone(ZoneOffset.UTC);
    }

    /**
     * project/application catalog path 정합성이 유효하면 compact history response를 반환하고, mismatch는 empty로 둔다.
     */
    @Transactional(readOnly = true)
    public Optional<OperationalEventHistoryReadModel> getHistory(
            UUID projectId,
            UUID applicationId,
            String since,
            String limit) {
        UUID requiredProjectId = Objects.requireNonNull(projectId, "projectId must not be null");
        UUID requiredApplicationId = Objects.requireNonNull(applicationId, "applicationId must not be null");
        OperationalEventHistoryQuery query = OperationalEventHistoryQuery.from(since, limit, clock);
        if (applicationRepository.findByIdAndProjectId(requiredApplicationId, requiredProjectId).isEmpty()) {
            return Optional.empty();
        }
        try {
            List<DashboardSnapshotDetailRow> sourceRows = dashboardSnapshotRepository.findOperationalHistoryRows(
                    requiredProjectId,
                    requiredApplicationId,
                    query.since(),
                    query.until(),
                    sourceFetchLimit(query.limit()));
            List<OperationalEventItem> events = projector.project(requiredProjectId, requiredApplicationId, sourceRows);
            return Optional.of(new OperationalEventHistoryReadModel(
                    query.until(),
                    requiredApplicationId,
                    OperationalEventHistoryReadModel.SOURCE,
                    new Horizon(
                            query.since(),
                            query.until(),
                            query.requestedSince(),
                            OperationalEventHistoryReadModel.DEFAULT_SINCE,
                            OperationalEventHistoryReadModel.MAX_SINCE,
                            query.limit(),
                            OperationalEventHistoryReadModel.MAX_LIMIT,
                            OperationalEventHistoryReadModel.ORDER),
                    events));
        } catch (OperationalEventHistoryProjectionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new OperationalEventHistoryProjectionException(
                    "stored operational event history source projection failed",
                    exception);
        }
    }

    /**
     * response event cap과 source row cap을 분리해 suppression/period folding이 작은 응답 limit에 잘리지 않게 한다.
     */
    private static int sourceFetchLimit(int responseLimit) {
        return Math.min(
                MAX_SOURCE_FETCH_LIMIT,
                Math.max(MIN_SOURCE_FETCH_LIMIT, responseLimit * SOURCE_FETCH_LIMIT_MULTIPLIER));
    }
}
