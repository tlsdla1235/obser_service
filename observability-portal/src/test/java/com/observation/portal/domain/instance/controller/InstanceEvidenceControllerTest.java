package com.observation.portal.domain.instance.controller;

import com.observation.portal.domain.instance.model.InstanceEvidenceReadModel;
import com.observation.portal.domain.instance.service.InstanceEvidenceReadModelService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InstanceEvidenceControllerTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000005201");
    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000005211");
    private static final UUID INSTANCE_ID = UUID.fromString("00000000-0000-0000-0000-000000005221");

    private final InstanceEvidenceReadModelService service = mock(InstanceEvidenceReadModelService.class);
    private final InstanceEvidenceController controller = new InstanceEvidenceController(service);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    @Test
    void getEvidenceSerializesFoundationShapeAndDelegatesToService() throws Exception {
        when(service.getEvidence(PROJECT_ID, APPLICATION_ID, INSTANCE_ID)).thenReturn(Optional.of(readModel()));

        mockMvc.perform(get(
                        "/api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/evidence",
                        PROJECT_ID,
                        APPLICATION_ID,
                        INSTANCE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedAt").value("2026-05-26T06:10:35Z"))
                .andExpect(jsonPath("$.application.projectId").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.application.applicationId").value(APPLICATION_ID.toString()))
                .andExpect(jsonPath("$.application.name").value("orders-api"))
                .andExpect(jsonPath("$.application.environment").value("prod"))
                .andExpect(jsonPath("$.application.links.dashboard")
                        .value("/api/projects/%s/applications/%s/dashboard"
                                .formatted(PROJECT_ID, APPLICATION_ID)))
                .andExpect(jsonPath("$.instance.instanceId").value(INSTANCE_ID.toString()))
                .andExpect(jsonPath("$.instance.instanceName").value("pod-a"))
                .andExpect(jsonPath("$.metricData.statusSource").value("accepted_bucket"))
                .andExpect(jsonPath("$.metricData.window.name").value("current_15m"))
                .andExpect(jsonPath("$.metricData.window.bucketDurationSeconds").value(30))
                .andExpect(jsonPath("$.metricData.errorRate").value(nullValue()))
                .andExpect(jsonPath("$.starterConnection.statusSource").value("starter_heartbeat"))
                .andExpect(jsonPath("$.starterConnection.stateImpact").value("none"))
                .andExpect(jsonPath("$.starterPercentiles.source").value("starter_canonical_percentile"))
                .andExpect(jsonPath("$.starterPercentiles.points").isArray())
                .andExpect(jsonPath("$.histogramDistribution.source").value("histogram_bucket_distribution"))
                .andExpect(jsonPath("$.histogramDistribution.buckets").isArray())
                .andExpect(jsonPath("$.resourceHints.source").value("accepted_bucket_latest_sample"))
                .andExpect(jsonPath("$.applicationTriageContribution.relatedRuleIds").isArray())
                .andExpect(jsonPath("$.endpointEvidence.source").value("accepted_metric_buckets.endpoints_json"))
                .andExpect(jsonPath("$.endpointEvidence.reason").value(nullValue()))
                .andExpect(jsonPath("$.endpointEvidence.items").isArray())
                .andExpect(jsonPath("$.links.self")
                        .value("/api/projects/%s/applications/%s/instances/%s/evidence"
                                .formatted(PROJECT_ID, APPLICATION_ID, INSTANCE_ID)))
                .andExpect(jsonPath("$.links.dashboard")
                        .value("/api/projects/%s/applications/%s/dashboard"
                                .formatted(PROJECT_ID, APPLICATION_ID)))
                .andExpect(jsonPath("$.links.snapshotTrend").value(nullValue()));
        verify(service).getEvidence(PROJECT_ID, APPLICATION_ID, INSTANCE_ID);
    }

    @Test
    void getEvidenceMapsMissingOrMismatchedMembershipTo404() throws Exception {
        when(service.getEvidence(PROJECT_ID, APPLICATION_ID, INSTANCE_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get(
                        "/api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/evidence",
                        PROJECT_ID,
                        APPLICATION_ID,
                        INSTANCE_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void getEvidenceDoesNotExposeForbiddenRawExplorerFields() throws Exception {
        when(service.getEvidence(PROJECT_ID, APPLICATION_ID, INSTANCE_ID)).thenReturn(Optional.of(readModel()));

        mockMvc.perform(get(
                        "/api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/evidence",
                        PROJECT_ID,
                        APPLICATION_ID,
                        INSTANCE_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("rawBucketJson"))))
                .andExpect(content().string(not(containsString("rawJson"))))
                .andExpect(content().string(not(containsString("endpointsJson"))))
                .andExpect(content().string(not(containsString("rawPath"))))
                .andExpect(content().string(not(containsString("queryString"))))
                .andExpect(content().string(not(containsString("traceId"))))
                .andExpect(jsonPath("$.state").doesNotExist())
                .andExpect(content().string(not(containsString("healthScore"))))
                .andExpect(content().string(not(containsString("availabilityScore"))))
                .andExpect(content().string(not(containsString("hostStatus"))))
                .andExpect(content().string(not(containsString("connectedAndHealthy"))))
                .andExpect(content().string(not(containsString("endpointP95Ms"))))
                .andExpect(content().string(not(containsString("endpointP99Ms"))))
                .andExpect(content().string(not(containsString("recommendedAction"))));
    }

    @Test
    void evidenceRouteUsesOnlyProjectApplicationAndInstancePathVariables() {
        Method method = evidenceMethod();

        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly("");
        assertThat(method.getParameters())
                .extracting(Parameter::getName)
                .containsExactly("projectId", "applicationId", "instanceId");
        assertThat(method.getParameters())
                .noneMatch(parameter -> parameter.isAnnotationPresent(RequestParam.class));
    }

    private static Method evidenceMethod() {
        return List.of(InstanceEvidenceController.class.getDeclaredMethods()).stream()
                .filter(method -> method.isAnnotationPresent(GetMapping.class))
                .findFirst()
                .orElseThrow();
    }

    private static InstanceEvidenceReadModel readModel() {
        String dashboard = "/api/projects/%s/applications/%s/dashboard".formatted(PROJECT_ID, APPLICATION_ID);
        String self = "/api/projects/%s/applications/%s/instances/%s/evidence"
                .formatted(PROJECT_ID, APPLICATION_ID, INSTANCE_ID);
        return new InstanceEvidenceReadModel(
                OffsetDateTime.parse("2026-05-26T06:10:35Z"),
                new InstanceEvidenceReadModel.Application(
                        PROJECT_ID,
                        APPLICATION_ID,
                        "orders-api",
                        "prod",
                        new InstanceEvidenceReadModel.ApplicationLinks(dashboard)),
                new InstanceEvidenceReadModel.Instance(
                        INSTANCE_ID,
                        "pod-a",
                        OffsetDateTime.parse("2026-05-26T05:00:05Z"),
                        OffsetDateTime.parse("2026-05-26T06:10:05Z")),
                InstanceEvidenceReadModel.MetricData.missing(new InstanceEvidenceReadModel.MetricWindow(
                        "current_15m",
                        OffsetDateTime.parse("2026-05-26T05:55:30Z"),
                        OffsetDateTime.parse("2026-05-26T06:10:30Z"),
                        30)),
                InstanceEvidenceReadModel.StarterConnection.missing(),
                InstanceEvidenceReadModel.StarterPercentiles.missing(),
                InstanceEvidenceReadModel.HistogramDistribution.missing(),
                InstanceEvidenceReadModel.ResourceHints.missing(),
                InstanceEvidenceReadModel.ApplicationTriageContribution.missing(),
                InstanceEvidenceReadModel.EndpointEvidence.missing(),
                new InstanceEvidenceReadModel.Links(self, dashboard, null));
    }
}
