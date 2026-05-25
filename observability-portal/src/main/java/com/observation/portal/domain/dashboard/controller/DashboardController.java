package com.observation.portal.domain.dashboard.controller;

import com.observation.portal.domain.dashboard.model.ApplicationDashboardReadModel;
import com.observation.portal.domain.dashboard.service.DashboardReadModelService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

/**
 * Application Dashboard current read model API를 HTTP endpoint로 노출한다.
 *
 * <p>path variable 변환과 404 status mapping만 담당하고 dashboard 조립과 state 판단은 service 계층에 위임한다.</p>
 */
@RestController
@RequestMapping("/api/projects/{projectId}/applications/{applicationId}/dashboard")
public class DashboardController {

    private final DashboardReadModelService dashboardReadModelService;

    /**
     * dashboard read model 생성을 담당하는 service를 주입한다.
     */
    public DashboardController(DashboardReadModelService dashboardReadModelService) {
        this.dashboardReadModelService = Objects.requireNonNull(
                dashboardReadModelService,
                "dashboardReadModelService must not be null");
    }

    /**
     * project/application scope의 current dashboard read model을 반환하고, scope가 맞지 않으면 404로 매핑한다.
     */
    @GetMapping("")
    public ResponseEntity<ApplicationDashboardReadModel> getDashboard(
            @PathVariable UUID projectId,
            @PathVariable UUID applicationId) {
        return dashboardReadModelService.getDashboard(projectId, applicationId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
