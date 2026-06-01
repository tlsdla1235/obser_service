package com.observation.portal.domain.catalog.controller;

import com.observation.portal.domain.catalog.model.ProjectApplicationNavigationReadModel;
import com.observation.portal.domain.catalog.model.ProjectNavigationReadModel;
import com.observation.portal.domain.catalog.service.ProjectApplicationNavigationService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectNavigationControllerTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000005101");
    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000006101");
    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000005111");
    private static final OffsetDateTime GENERATED_AT = OffsetDateTime.parse("2026-05-25T10:00:00Z");

    private final ProjectApplicationNavigationService service = mock(ProjectApplicationNavigationService.class);
    private final ProjectNavigationController controller = new ProjectNavigationController(service);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    @Test
    void getProjectsReturnsSerializedProjectNavigationShape() throws Exception {
        ProjectNavigationReadModel readModel = new ProjectNavigationReadModel(
                GENERATED_AT,
                List.of(new ProjectNavigationReadModel.ProjectItem(
                        PROJECT_ID,
                        "local-demo",
                        2,
                        1,
                        null,
                        new ProjectNavigationReadModel.ProjectLinks(
                                "/api/projects/" + PROJECT_ID + "/applications"))));
        when(service.listProjects(ACCOUNT_ID)).thenReturn(readModel);

        mockMvc.perform(get("/api/projects")
                        .requestAttr("observation.portal.accountId", ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedAt").value("2026-05-25T10:00:00Z"))
                .andExpect(jsonPath("$.projects[0].projectId").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.projects[0].name").value("local-demo"))
                .andExpect(jsonPath("$.projects[0].applicationCount").value(2))
                .andExpect(jsonPath("$.projects[0].setupConnectionIssueCount").value(1))
                .andExpect(jsonPath("$.projects[0].recentConcern").value(nullValue()))
                .andExpect(jsonPath("$.projects[0].links.applications")
                        .value("/api/projects/" + PROJECT_ID + "/applications"));
        verify(service).listProjects(ACCOUNT_ID);
    }

    @Test
    void getApplicationsConvertsProjectIdAndKeepsSourceFieldsSeparate() throws Exception {
        ProjectApplicationNavigationReadModel readModel = new ProjectApplicationNavigationReadModel(
                GENERATED_AT,
                new ProjectApplicationNavigationReadModel.ProjectSummary(PROJECT_ID, "local-demo"),
                List.of(new ProjectApplicationNavigationReadModel.ApplicationItem(
                        APPLICATION_ID,
                        "orders-api",
                        "prod",
                        new ProjectApplicationNavigationReadModel.MetricDataSummary(
                                "accepted_bucket",
                                null,
                                "waiting_first_data"),
                        new ProjectApplicationNavigationReadModel.StarterConnectionSummary(
                                "starter_heartbeat",
                                GENERATED_AT.minusSeconds(20),
                                "received",
                                "recent",
                                "starter_connected",
                                "none"),
                        new ProjectApplicationNavigationReadModel.LifecycleBadge(
                                "server_light_navigation_read_model",
                                "unknown",
                                "Metric data unknown"),
                        null,
                        new ProjectApplicationNavigationReadModel.ApplicationLinks(
                                "/api/projects/" + PROJECT_ID + "/applications/" + APPLICATION_ID + "/dashboard"))));
        when(service.listApplications(PROJECT_ID)).thenReturn(Optional.of(readModel));

        mockMvc.perform(get("/api/projects/{projectId}/applications", PROJECT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedAt").value("2026-05-25T10:00:00Z"))
                .andExpect(jsonPath("$.project.projectId").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.project.name").value("local-demo"))
                .andExpect(jsonPath("$.applications[0].applicationId").value(APPLICATION_ID.toString()))
                .andExpect(jsonPath("$.applications[0].name").value("orders-api"))
                .andExpect(jsonPath("$.applications[0].environment").value("prod"))
                .andExpect(jsonPath("$.applications[0].metricData.statusSource").value("accepted_bucket"))
                .andExpect(jsonPath("$.applications[0].metricData.lastAcceptedBucketAt").value(nullValue()))
                .andExpect(jsonPath("$.applications[0].metricData.freshnessLabel").value("waiting_first_data"))
                .andExpect(jsonPath("$.applications[0].starterConnection.statusSource").value("starter_heartbeat"))
                .andExpect(jsonPath("$.applications[0].starterConnection.lastHeartbeatAt")
                        .value("2026-05-25T09:59:40Z"))
                .andExpect(jsonPath("$.applications[0].starterConnection.heartbeatStatus").value("received"))
                .andExpect(jsonPath("$.applications[0].starterConnection.freshnessLabel").value("recent"))
                .andExpect(jsonPath("$.applications[0].starterConnection.connectionMeaning").value("starter_connected"))
                .andExpect(jsonPath("$.applications[0].starterConnection.stateImpact").value("none"))
                .andExpect(jsonPath("$.applications[0].lifecycleBadge.code").value("unknown"))
                .andExpect(jsonPath("$.applications[0].topConcern").value(nullValue()))
                .andExpect(jsonPath("$.applications[0].links.dashboard")
                        .value("/api/projects/" + PROJECT_ID + "/applications/" + APPLICATION_ID + "/dashboard"));
        verify(service).listApplications(PROJECT_ID);
    }

    @Test
    void getApplicationsMapsMissingProjectTo404OverHttp() throws Exception {
        when(service.listApplications(PROJECT_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/projects/{projectId}/applications", PROJECT_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void projectCollectionPostRouteIsOwnedByRegistrationController() throws Exception {
        mockMvc.perform(post("/api/projects"))
                .andExpect(status().isMethodNotAllowed());
        assertThat(publicProjectCreationRoutes())
                .as("Story 9.2 opens public project registration through the dedicated controller")
                .containsExactly("com.observation.portal.domain.catalog.controller.ProjectRegistrationController#registerProject /api/projects");
    }

    @Test
    void projectRoutesAreReadOnlyGetMappings() {
        assertThat(requestMappingPaths(ProjectNavigationController.class.getAnnotation(RequestMapping.class)))
                .containsExactly("/api/projects");
        assertThat(ProjectNavigationController.class.getDeclaredMethods())
                .filteredOn(method -> method.isAnnotationPresent(GetMapping.class))
                .hasSize(2);
        assertThat(ProjectNavigationController.class.getDeclaredMethods())
                .filteredOn(method -> method.isAnnotationPresent(PostMapping.class))
                .isEmpty();
    }

    private static List<String> publicProjectCreationRoutes() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<String> routes = new ArrayList<>();
        for (var beanDefinition : scanner.findCandidateComponents("com.observation.portal.domain")) {
            Class<?> controllerType = Class.forName(beanDefinition.getBeanClassName());
            List<String> basePaths = requestMappingPaths(controllerType.getAnnotation(RequestMapping.class));
            for (var method : controllerType.getDeclaredMethods()) {
                for (String postPath : postMappingPaths(method.getAnnotation(PostMapping.class))) {
                    addMatchingProjectRoute(routes, controllerType, method.getName(), basePaths, postPath);
                }
                RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
                if (requestMapping != null && List.of(requestMapping.method()).contains(RequestMethod.POST)) {
                    for (String postPath : requestMappingPaths(requestMapping)) {
                        addMatchingProjectRoute(routes, controllerType, method.getName(), basePaths, postPath);
                    }
                }
            }
        }
        return routes;
    }

    private static void addMatchingProjectRoute(
            List<String> routes,
            Class<?> controllerType,
            String methodName,
            List<String> basePaths,
            String postPath) {
        for (String basePath : basePaths) {
            String fullPath = normalizeRoute(basePath, postPath);
            if (fullPath.equals("/api/projects")) {
                routes.add(controllerType.getName() + "#" + methodName + " " + fullPath);
            }
        }
    }

    private static List<String> requestMappingPaths(RequestMapping requestMapping) {
        if (requestMapping == null) {
            return List.of("");
        }
        if (requestMapping.path().length > 0) {
            return List.of(requestMapping.path());
        }
        if (requestMapping.value().length > 0) {
            return List.of(requestMapping.value());
        }
        return List.of("");
    }

    private static List<String> postMappingPaths(PostMapping postMapping) {
        if (postMapping == null) {
            return List.of();
        }
        if (postMapping.path().length > 0) {
            return List.of(postMapping.path());
        }
        if (postMapping.value().length > 0) {
            return List.of(postMapping.value());
        }
        return List.of("");
    }

    private static String normalizeRoute(String basePath, String methodPath) {
        String normalizedBase = basePath == null || basePath.isBlank() ? "" : basePath.trim();
        String normalizedMethod = methodPath == null || methodPath.isBlank() ? "" : methodPath.trim();
        String joined = (normalizedBase + "/" + normalizedMethod)
                .replaceAll("/{2,}", "/");
        if (joined.length() > 1 && joined.endsWith("/")) {
            return joined.substring(0, joined.length() - 1);
        }
        return joined.isBlank() ? "/" : joined;
    }
}
