package com.observation.smoke.web;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SmokeTrafficControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new SmokeTrafficController())
            .build();

    @Test
    void okEndpointReturnsBoundedGreenTrafficResponse() throws Exception {
        mockMvc.perform(get("/smoke/ok"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"ok\"")))
                .andExpect(content().string(not(containsString("OBSERVATION_SMOKE_PROJECT_KEY"))))
                .andExpect(content().string(not(containsString("accessToken"))))
                .andExpect(content().string(not(containsString("refreshToken"))));
    }

    @Test
    void slowEndpointReturnsAfterBoundedDelay() throws Exception {
        mockMvc.perform(get("/smoke/slow"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"slow\"")))
                .andExpect(content().string(not(containsString("OBSERVATION_SMOKE_PROJECT_KEY"))))
                .andExpect(content().string(not(containsString("accessToken"))))
                .andExpect(content().string(not(containsString("refreshToken"))));
    }

    @Test
    void errorCandidateEndpointReturnsIntentionalServerErrorCandidate() throws Exception {
        mockMvc.perform(get("/smoke/error-candidate"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString("\"status\":\"error_candidate\"")))
                .andExpect(content().string(not(containsString("OBSERVATION_SMOKE_PROJECT_KEY"))))
                .andExpect(content().string(not(containsString("accessToken"))))
                .andExpect(content().string(not(containsString("refreshToken"))));
    }
}
