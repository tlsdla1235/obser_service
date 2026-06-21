package com.observation.portal.domain.health.controller;

import com.observation.portal.domain.health.service.ReadinessCheckResult;
import com.observation.portal.domain.health.service.ReadinessProbeService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalHealthControllerTest {

    private final ReadinessProbeService readinessProbeService = mock(ReadinessProbeService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new InternalHealthController(readinessProbeService))
            .build();

    @Test
    void liveReturnsHttpOnlyLivenessWithoutRuntimeDetails() throws Exception {
        MvcResult result = mockMvc.perform(get("/internal/health/live"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.checks.http").value("UP"))
                .andReturn();

        assertSecretFree(result);
    }

    @Test
    void readyReturnsOkWhenDatabaseAndFlywayAreReady() throws Exception {
        when(readinessProbeService.check()).thenReturn(ReadinessCheckResult.readyResult());

        MvcResult result = mockMvc.perform(get("/internal/health/ready"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.checks.database").value("UP"))
                .andExpect(jsonPath("$.checks.flyway").value("UP"))
                .andReturn();

        assertSecretFree(result);
    }

    @Test
    void readyReturnsServiceUnavailableWithSanitizedBodyWhenDatabaseIsDown() throws Exception {
        when(readinessProbeService.check()).thenReturn(ReadinessCheckResult.databaseDown());

        MvcResult result = mockMvc.perform(get("/internal/health/ready"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.checks.database").value("DOWN"))
                .andExpect(jsonPath("$.checks.flyway").value("UNKNOWN"))
                .andReturn();

        assertSecretFree(result);
    }

    private static void assertSecretFree(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .doesNotContain("jdbc:")
                .doesNotContain("password")
                .doesNotContain("secret")
                .doesNotContain("/observation/prod")
                .doesNotContain("ssm")
                .doesNotContain("queueUrl")
                .doesNotContain("amazonaws.com");
    }
}
