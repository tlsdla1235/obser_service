package com.observation.portal.domain.catalog.controller;

import com.observation.portal.domain.account.controller.BearerResourceApiInterceptor;
import com.observation.portal.domain.catalog.model.ProjectApplicationNavigationReadModel;
import com.observation.portal.domain.catalog.model.ProjectNavigationReadModel;
import com.observation.portal.domain.catalog.service.ProjectApplicationNavigationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

/**
 * Project Entry와 Application List navigation API를 HTTP read-only endpoint로 노출한다.
 *
 * <p>path 변환과 404 매핑만 담당하고 read model 생성은 catalog service에 위임한다.</p>
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectNavigationController {

    private final ProjectApplicationNavigationService navigationService;

    /**
     * navigation read model 생성을 담당하는 service를 주입한다.
     */
    public ProjectNavigationController(ProjectApplicationNavigationService navigationService) {
        this.navigationService = Objects.requireNonNull(navigationService, "navigationService must not be null");
    }

    /**
     * Project Entry에서 사용할 account-scoped read-only project 목록을 반환한다.
     */
    @GetMapping
    public ProjectNavigationReadModel listProjects(HttpServletRequest request) {
        return navigationService.listProjects(BearerResourceApiInterceptor.requiredAccountId(request));
    }

    /**
     * 특정 project의 Application List navigation read model을 반환하고, project가 없으면 404로 매핑한다.
     */
    @GetMapping("/{projectId}/applications")
    public ResponseEntity<ProjectApplicationNavigationReadModel> listApplications(@PathVariable UUID projectId) {
        return navigationService.listApplications(projectId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
