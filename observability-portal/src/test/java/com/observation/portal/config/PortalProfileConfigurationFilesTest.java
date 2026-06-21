package com.observation.portal.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PortalProfileConfigurationFilesTest {

    private static final Path RESOURCES = Path.of("src/main/resources");

    /**
     * `.private` fallback은 local profile에만 남아야 운영/CI secret 주입 경로와 섞이지 않는다.
     */
    @Test
    void privateImportsAreLocalProfileOnly() throws IOException {
        String common = read("application.properties");
        String local = read("application-local.properties");
        String ci = read("application-ci.properties");
        String prod = read("application-prod.properties");

        assertThat(common).doesNotContain(".private");
        assertThat(ci).doesNotContain(".private");
        assertThat(prod).doesNotContain(".private");
        assertThat(local).contains(".private/github-oauth.properties")
                .contains(".private/smoke-seed.properties");
    }

    /**
     * 운영 profile은 repo에 실값을 두지 않고 환경변수 placeholder만 노출한다.
     */
    @Test
    void prodProfileDocumentsRequiredEnvironmentInterface() throws IOException {
        String prod = read("application-prod.properties");

        assertThat(prod)
                .contains("${SERVER_ADDRESS:127.0.0.1}")
                .contains("${SPRING_DATASOURCE_PASSWORD:}")
                .contains("${PORTAL_AUTH_GITHUB_CLIENT_SECRET:}")
                .contains("${PORTAL_AUTH_SERVICE_TOKEN_SIGNING_KEY:}")
                .contains("${PORTAL_AUTH_OAUTH_STATE_SIGNING_KEY:}")
                .doesNotContain("github-oauth.properties")
                .doesNotContain("smoke-seed.properties");
    }

    private static String read(String fileName) throws IOException {
        return Files.readString(RESOURCES.resolve(fileName));
    }
}
