package com.observation.portal.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPA route allow-list가 API와 정적 resource path를 HTML fallback으로 가리지 않는지 검증한다.
 */
@SpringJUnitConfig
@WebAppConfiguration
@ContextConfiguration(classes = {
        DashboardStaticWebConfig.class,
        DashboardStaticWebConfigTest.WebMvcTestConfig.class
})
class DashboardStaticWebConfigTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/dashboard", "/dashboard/", "/docs", "/docs/"})
    void spaClientRoutesForwardToRootIndex(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/projects",
            "/assets/index-demo.js",
            "/favicon.ico",
            "/dashboard/app.js",
            "/dashboard/styles.css"
    })
    void apiAssetAndExtensionRoutesAreNotForwardedToSpa(String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(result.getResponse().getForwardedUrl()).isNull();
    }

    @Test
    void rootPathIsNotRegisteredAsMvcFallback() throws Exception {
        MvcResult result = mockMvc.perform(get("/"))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(result.getResponse().getForwardedUrl()).isNull();
    }

    @Configuration
    @EnableWebMvc
    static class WebMvcTestConfig {
    }
}
