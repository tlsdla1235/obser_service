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

    private final BearerResourceApiInterceptor interceptor;

    /**
     * resource API Bearer 검증 interceptor를 주입한다.
     */
    public BearerResourceApiWebConfig(BearerResourceApiInterceptor interceptor) {
        this.interceptor = Objects.requireNonNull(interceptor, "interceptor must not be null");
    }

    /**
     * Project Entry와 후속 dashboard resource API를 `/api/projects` 경계로 보호한다.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/api/projects", "/api/projects/**");
    }
}
