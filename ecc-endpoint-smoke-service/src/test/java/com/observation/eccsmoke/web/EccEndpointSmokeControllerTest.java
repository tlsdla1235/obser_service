package com.observation.eccsmoke.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EccEndpointSmokeControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new EccEndpointSmokeController())
            .build();

    @Test
    void routeCatalogExposesPollingCandidatesWithoutSecrets() throws Exception {
        mockMvc.perform(get("/api/ecc-smoke/routes"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"routeCount\":93")))
                .andExpect(content().string(containsString("\"path\":\"/api/auth/login\"")))
                .andExpect(content().string(containsString("\"path\":\"/api/review/me/{reviewId}/test\"")))
                .andExpect(content().string(not(containsString("ECC_ENDPOINT_SMOKE_PROJECT_KEY"))))
                .andExpect(content().string(not(containsString("Authorization"))));
    }

    @Test
    void eccGetEndpointReturnsBoundedStubResponse() throws Exception {
        mockMvc.perform(get("/api/auth/signup/check-id").param("studentId", "20201234"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"method\":\"GET\"")))
                .andExpect(content().string(containsString("\"route\":\"/api/auth/signup/check-id\"")))
                .andExpect(content().string(containsString("\"dbAccess\":false")))
                .andExpect(content().string(containsString("\"businessLogic\":false")));
    }

    @Test
    void eccPostEndpointReturnsBoundedStubResponse() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"method\":\"POST\"")))
                .andExpect(content().string(containsString("\"route\":\"/api/auth/login\"")))
                .andExpect(content().string(not(containsString("accessToken"))))
                .andExpect(content().string(not(containsString("refreshToken"))));
    }

    @Test
    void eccPatchPutAndDeleteEndpointsReturnBoundedStubResponses() throws Exception {
        mockMvc.perform(patch("/api/users/me/password").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"method\":\"PATCH\"")))
                .andExpect(content().string(containsString("\"route\":\"/api/users/me/password\"")));

        mockMvc.perform(put("/api/study/study-1/general").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"method\":\"PUT\"")))
                .andExpect(content().string(containsString("\"route\":\"/api/study/{studyId}/general\"")));

        mockMvc.perform(delete("/api/admin/content/topics/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"method\":\"DELETE\"")))
                .andExpect(content().string(containsString("\"route\":\"/api/admin/content/topics/{topicId}\"")));
    }

    @Test
    void intentionalErrorEndpointReturnsServerErrorWithoutLeakingSecrets() throws Exception {
        mockMvc.perform(get("/api/ecc-smoke/error-500"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString("\"success\":false")))
                .andExpect(content().string(containsString("\"route\":\"/api/ecc-smoke/error-500\"")))
                .andExpect(content().string(not(containsString("ECC_ENDPOINT_SMOKE_PROJECT_KEY"))))
                .andExpect(content().string(not(containsString("Authorization"))));
    }
}
