package com.observation.portal.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProdRequiredPropertiesGuardTest {

    /**
     * prod profile은 임시 signing key나 빈 datasource secret으로 기동되지 않아야 한다.
     */
    @Test
    void prodProfileFailsClosedWhenRequiredExternalConfigurationIsMissing() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("prod");
            context.register(ProdRequiredPropertiesGuard.class);

            assertThatThrownBy(context::refresh)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("prod profile requires non-empty external configuration")
                    .hasMessageContaining("spring.datasource.password")
                    .hasMessageContaining("PORTAL_AUTH_SERVICE_TOKEN_SIGNING_KEY")
                    .hasMessageNotContaining("secret-value");
        }
    }

    /**
     * prod 필수값이 모두 있으면 guard는 이후 datasource/Flyway 검증 단계로 제어를 넘긴다.
     */
    @Test
    void prodProfileAllowsStartupWhenRequiredExternalConfigurationIsPresent() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("prod");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", configuredValues()));
            context.register(ProdRequiredPropertiesGuard.class);

            context.refresh();
        }
    }

    /**
     * local profile은 기존 `.private` fallback을 유지하므로 prod 전용 guard가 개입하지 않는다.
     */
    @Test
    void localProfileDoesNotRequireProdExternalConfiguration() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("local");
            context.register(ProdRequiredPropertiesGuard.class);

            context.refresh();
        }
    }

    private static Map<String, Object> configuredValues() {
        return Map.of(
                "spring.datasource.url", "jdbc:postgresql://127.0.0.1:5432/observation",
                "spring.datasource.username", "observation",
                "spring.datasource.password", "secret-value",
                "portal.auth.github.client-id", "github-client-id",
                "portal.auth.github.client-secret", "github-client-secret",
                "portal.auth.github.redirect-uri", "https://example.com/api/auth/github/callback",
                "portal.auth.github.homepage-url", "https://example.com/dashboard/",
                "portal.auth.service-token.signing-key", "service-token-signing-key",
                "portal.auth.oauth-state.signing-key", "oauth-state-signing-key");
    }
}
