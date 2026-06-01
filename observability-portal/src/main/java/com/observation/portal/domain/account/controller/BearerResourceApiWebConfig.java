package com.observation.portal.domain.account.controller;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Objects;

/**
 * service Bearer token이 필요한 resource API와 공개 auth API의 MVC 인증 경계를 등록한다.
 *
 * <p>`/api/auth/github/authorize`, `/api/auth/github/callback`, `/api/auth/token/refresh`,
 * `/api/auth/logout`는 공개 endpoint로 유지하고, Project resource API만 Bearer 검증 대상에 둔다.</p>
 */
@Configuration
public class BearerResourceApiWebConfig implements WebMvcConfigurer {

    private final BearerResourceApiInterceptor bearerInterceptor;
    private final AccountProjectMembershipResourceApiInterceptor membershipInterceptor;

    /**
     * resource API Bearer 검증과 project-scoped membership 검증 interceptor를 주입한다.
     */
    public BearerResourceApiWebConfig(
            BearerResourceApiInterceptor bearerInterceptor,
            AccountProjectMembershipResourceApiInterceptor membershipInterceptor) {
        this.bearerInterceptor = Objects.requireNonNull(
                bearerInterceptor,
                "bearerInterceptor must not be null");
        this.membershipInterceptor = Objects.requireNonNull(
                membershipInterceptor,
                "membershipInterceptor must not be null");
    }

    /**
     * Project Entry는 Bearer로 보호하고, project-scoped resource API는 추가로 account-project membership을 확인한다.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(bearerInterceptor)
                .addPathPatterns("/api/projects", "/api/projects/**");
        registry.addInterceptor(membershipInterceptor)
                .addPathPatterns(
                        "/api/projects/*/applications",
                        "/api/projects/*/applications/**",
                        "/api/projects/*/starter-credential",
                        "/api/projects/*/starter-credential/**");
    }
}
