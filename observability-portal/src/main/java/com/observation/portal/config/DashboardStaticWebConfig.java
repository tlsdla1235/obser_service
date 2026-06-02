package com.observation.portal.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * static dashboard entry route를 product homepage URL과 맞춰 등록한다.
 *
 * <p>GitHub OAuth App homepage와 runbook이 `/dashboard/`를 기준으로 삼으므로, directory URL이 실제
 * `index.html`을 열도록 MVC forward를 둔다.</p>
 */
@Configuration
public class DashboardStaticWebConfig implements WebMvcConfigurer {

    /**
     * `/dashboard`와 `/dashboard/`가 static dashboard 첫 화면으로 수렴하도록 한다.
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/dashboard", "/dashboard/");
        registry.addViewController("/dashboard/").setViewName("forward:/dashboard/index.html");
    }
}
