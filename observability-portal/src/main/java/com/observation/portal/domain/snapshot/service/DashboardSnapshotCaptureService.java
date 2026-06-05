package com.observation.portal.domain.snapshot.service;

import com.observation.portal.domain.dashboard.model.ApplicationDashboardReadModel;
import com.observation.portal.domain.dashboard.service.DashboardReadModelService;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotCaptureRequest;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotWriteCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 내부 capture request를 dashboard read model generation과 writer upsert로 orchestration하는 portal use case다.
 *
 * <p>scheduler나 Post-MVP external trigger는 이 service에 요청만 전달하고, read model 계산과 snapshot 저장은 계속
 * portal service layer가 소유한다.</p>
 */
@Service
public class DashboardSnapshotCaptureService {

    private static final Logger log = LoggerFactory.getLogger(DashboardSnapshotCaptureService.class);

    private final DashboardReadModelService dashboardReadModelService;
    private final DashboardSnapshotWriterService writerService;

    /**
     * target window 기준 read model generator와 snapshot writer를 주입한다.
     */
    public DashboardSnapshotCaptureService(
            DashboardReadModelService dashboardReadModelService,
            DashboardSnapshotWriterService writerService) {
        this.dashboardReadModelService = Objects.requireNonNull(
                dashboardReadModelService,
                "dashboardReadModelService must not be null");
        this.writerService = Objects.requireNonNull(writerService, "writerService must not be null");
    }

    /**
     * capture request의 target current window end 기준으로 read model을 한 번 생성해 writer에 전달한다.
     *
     * <p>application catalog path 정합성이 사라졌거나 save가 실패하면 exception을 삼키고 caller가 다음 application/run으로
     * 진행할 수 있게 한다.</p>
     */
    public void capture(DashboardSnapshotCaptureRequest request) {
        DashboardSnapshotCaptureRequest requiredRequest = Objects.requireNonNull(
                request,
                "request must not be null");
        try {
            dashboardReadModelService.getDashboardForSnapshot(
                            requiredRequest.projectId(),
                            requiredRequest.applicationId(),
                            requiredRequest.currentWindowEndUtc(),
                            requiredRequest.snapshotCutoffAt())
                    .ifPresentOrElse(
                            readModel -> write(requiredRequest, readModel),
                            () -> log.warn(
                                    "dashboard_snapshot_capture_skipped captureReason={} applicationId={} reason=application_not_found",
                                    requiredRequest.captureReason().token(),
                                    requiredRequest.applicationId()));
        } catch (RuntimeException exception) {
            log.warn(
                    "dashboard_snapshot_capture_failed captureReason={} applicationId={} triggerSource={}",
                    requiredRequest.captureReason().token(),
                    requiredRequest.applicationId(),
                    requiredRequest.triggerSource(),
                    exception);
        }
    }

    private void write(
            DashboardSnapshotCaptureRequest request,
            ApplicationDashboardReadModel readModel) {
        writerService.write(new DashboardSnapshotWriteCommand(
                request.projectId(),
                request.applicationId(),
                readModel,
                request.captureReason(),
                request.currentWindowEndUtc(),
                request.snapshotCutoffAt(),
                request.requestedAt(),
                request.triggerSource()));
    }
}
