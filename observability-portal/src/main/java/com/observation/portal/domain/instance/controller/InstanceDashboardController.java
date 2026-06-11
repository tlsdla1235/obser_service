package com.observation.portal.domain.instance.controller;

import com.observation.portal.domain.instance.model.InstanceDashboardReadModel;
import com.observation.portal.domain.instance.service.InstanceDashboardReadModelService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

/**
 * Instance Dashboard live mode와 selected Application Snapshot 기반 snapshot mode를 HTTP endpoint로 노출한다.
 *
 * <p>controller는 route mapping과 404 변환만 담당하고, catalog path 검증과 live/snapshot source semantics 조립은
 * service 계층에 위임한다.</p>
 */
@RestController
public class InstanceDashboardController {

    private final InstanceDashboardReadModelService service;

    /**
     * Instance Dashboard read model service를 주입한다.
     */
    public InstanceDashboardController(InstanceDashboardReadModelService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    /**
     * selected instance live dashboard를 recent 30분 accepted bucket evidence 기준으로 반환한다.
     */
    @GetMapping("/api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/dashboard")
    public ResponseEntity<InstanceDashboardReadModel> getLiveDashboard(
            @PathVariable UUID projectId,
            @PathVariable UUID applicationId,
            @PathVariable UUID instanceId) {
        return service.getLiveDashboard(projectId, applicationId, instanceId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * selected Application Snapshot row window 기준으로 selected instance evidence를 cutoff 없이 재구성해 반환한다.
     */
    @GetMapping(
            "/api/projects/{projectId}/applications/{applicationId}/snapshots/{snapshotId}/instances/{instanceId}/dashboard")
    public ResponseEntity<InstanceDashboardReadModel> getSnapshotDashboard(
            @PathVariable UUID projectId,
            @PathVariable UUID applicationId,
            @PathVariable UUID snapshotId,
            @PathVariable UUID instanceId) {
        return service.getSnapshotDashboard(projectId, applicationId, snapshotId, instanceId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
