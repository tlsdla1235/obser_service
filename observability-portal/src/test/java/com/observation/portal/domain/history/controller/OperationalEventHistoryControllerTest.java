package com.observation.portal.domain.history.controller;

import com.observation.portal.domain.history.model.OperationalEventHistoryReadModel;
import com.observation.portal.domain.history.service.InvalidOperationalEventHistoryQueryException;
import com.observation.portal.domain.history.service.OperationalEventHistoryProjectionException;
import com.observation.portal.domain.history.service.OperationalEventHistoryService;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OperationalEventHistoryControllerTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000005901");
    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000005911");

    private final OperationalEventHistoryService service = mock(OperationalEventHistoryService.class);
    private final OperationalEventHistoryController controller = new OperationalEventHistoryController(service);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    @Test
    void getOperationalEventsReturnsDefaultEmptyResponseWithoutHealthOrRecoveryCompletionCopy() throws Exception {
        when(service.getHistory(PROJECT_ID, APPLICATION_ID, null, null))
                .thenReturn(Optional.of(emptyHistory()));

        mockMvc.perform(get(
                        "/api/projects/{projectId}/applications/{applicationId}/operational-events",
                        PROJECT_ID,
                        APPLICATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("dashboard_snapshots"))
                .andExpect(jsonPath("$.applicationId").value(APPLICATION_ID.toString()))
                .andExpect(jsonPath("$.horizon.requestedSince").value("24h"))
                .andExpect(jsonPath("$.horizon.defaultSince").value("24h"))
                .andExpect(jsonPath("$.horizon.maxSince").value("14d"))
                .andExpect(jsonPath("$.horizon.limit").value(50))
                .andExpect(jsonPath("$.horizon.maxLimit").value(100))
                .andExpect(jsonPath("$.horizon.order").value("occurredAt_desc"))
                .andExpect(jsonPath("$.events").isEmpty())
                .andExpect(content().string(not(containsString("현재 문제 없음"))))
                .andExpect(content().string(not(containsString("복구 완료"))))
                .andExpect(content().string(not(containsString("장애 해결 완료"))));
        verify(service).getHistory(PROJECT_ID, APPLICATION_ID, null, null);
    }

    @Test
    void statusMappingUses400ForInvalidQuery404ForMembershipAnd500ForProjectionFailure() throws Exception {
        when(service.getHistory(PROJECT_ID, APPLICATION_ID, "bad", "50"))
                .thenThrow(new InvalidOperationalEventHistoryQueryException("bad since"));
        when(service.getHistory(PROJECT_ID, APPLICATION_ID, "24h", "50"))
                .thenReturn(Optional.empty());
        when(service.getHistory(PROJECT_ID, APPLICATION_ID, "7d", "50"))
                .thenThrow(new OperationalEventHistoryProjectionException("projection failed"));

        mockMvc.perform(get(
                        "/api/projects/{projectId}/applications/{applicationId}/operational-events",
                        PROJECT_ID,
                        APPLICATION_ID)
                        .param("since", "bad")
                        .param("limit", "50"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_operational_event_history_query"));
        mockMvc.perform(get(
                        "/api/projects/{projectId}/applications/{applicationId}/operational-events",
                        PROJECT_ID,
                        APPLICATION_ID)
                        .param("since", "24h")
                        .param("limit", "50"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("operational_event_history_not_found"));
        mockMvc.perform(get(
                        "/api/projects/{projectId}/applications/{applicationId}/operational-events",
                        PROJECT_ID,
                        APPLICATION_ID)
                        .param("since", "7d")
                        .param("limit", "50"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("operational_event_history_projection_failed"));
    }

    @Test
    void routeUsesProjectApplicationOperationalEventsPathAndOptionalQueryParameters() {
        Method method = List.of(OperationalEventHistoryController.class.getDeclaredMethods()).stream()
                .filter(candidate -> candidate.isAnnotationPresent(GetMapping.class))
                .filter(candidate -> candidate.getName().equals("getOperationalEvents"))
                .findFirst()
                .orElseThrow();

        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly("/operational-events");
        assertThat(method.getParameters())
                .extracting(Parameter::getName)
                .containsExactly("projectId", "applicationId", "since", "limit");
        assertThat(method.getParameters()[2].getAnnotation(RequestParam.class).required()).isFalse();
        assertThat(method.getParameters()[3].getAnnotation(RequestParam.class).required()).isFalse();
    }

    private static OperationalEventHistoryReadModel emptyHistory() {
        return new OperationalEventHistoryReadModel(
                offset("2026-05-27T13:10:35Z"),
                APPLICATION_ID,
                OperationalEventHistoryReadModel.SOURCE,
                new OperationalEventHistoryReadModel.Horizon(
                        offset("2026-05-26T13:10:35Z"),
                        offset("2026-05-27T13:10:35Z"),
                        "24h",
                        OperationalEventHistoryReadModel.DEFAULT_SINCE,
                        OperationalEventHistoryReadModel.MAX_SINCE,
                        50,
                        OperationalEventHistoryReadModel.MAX_LIMIT,
                        OperationalEventHistoryReadModel.ORDER),
                List.of());
    }

    private static OffsetDateTime offset(String instant) {
        return OffsetDateTime.parse(instant);
    }
}
