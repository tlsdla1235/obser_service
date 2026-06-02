package com.observation.portal.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Vite SPA의 client route를 Spring MVC에서 정적 root index로 연결한다.
 *
 * <p>알려진 route만 allow-list로 forward해 API, Vite asset, 확장자 resource 요청이 SPA HTML에 가려지지 않게 한다.</p>
 */
@Configuration
public class DashboardStaticWebConfig implements WebMvcConfigurer {

    /**
     * 직접 진입과 새로고침이 가능한 client route만 새 SPA index로 보낸다.
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/dashboard").setViewName("forward:/index.html");
        registry.addViewController("/dashboard/").setViewName("forward:/index.html");
        registry.addViewController("/docs").setViewName("forward:/index.html");
        registry.addViewController("/docs/").setViewName("forward:/index.html");
    }
}
