package com.observation.portal.domain.account.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * production MVC interceptor path wiring이 project-scoped authorization 경계를 포함하는지 검증한다.
 */
class BearerResourceApiWebConfigTest {

    @Test
    void productionMembershipInterceptorIncludesStarterCredentialLifecyclePaths() throws Exception {
        BearerResourceApiInterceptor bearerInterceptor = mock(BearerResourceApiInterceptor.class);
        AccountProjectMembershipResourceApiInterceptor membershipInterceptor =
                mock(AccountProjectMembershipResourceApiInterceptor.class);
        InterceptorRegistry registry = new InterceptorRegistry();

        new BearerResourceApiWebConfig(bearerInterceptor, membershipInterceptor).addInterceptors(registry);

        List<InterceptorRegistration> registrations = registrationsFrom(registry);
        assertThat(registrations).hasSize(2);
        assertThat(includePatterns(registrations.get(1))).containsExactly(
                "/api/projects/*/applications",
                "/api/projects/*/applications/**",
                "/api/projects/*/starter-credential",
                "/api/projects/*/starter-credential/**");
    }

    @SuppressWarnings("unchecked")
    private static List<InterceptorRegistration> registrationsFrom(InterceptorRegistry registry) throws Exception {
        Field registrations = InterceptorRegistry.class.getDeclaredField("registrations");
        registrations.setAccessible(true);
        return (List<InterceptorRegistration>) registrations.get(registry);
    }

    @SuppressWarnings("unchecked")
    private static List<String> includePatterns(InterceptorRegistration registration) throws Exception {
        Field includePatterns = InterceptorRegistration.class.getDeclaredField("includePatterns");
        includePatterns.setAccessible(true);
        return (List<String>) includePatterns.get(registration);
    }
}
