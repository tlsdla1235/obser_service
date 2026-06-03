package com.observation.eccsmoke.config;

import io.micrometer.observation.ObservationRegistry;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ServerHttpObservationFilter;

import java.util.EnumSet;

/**
 * ECC endpoint smoke 전용 MVC observation runtime wiring이다.
 *
 * <p>starter 자체에는 Web dependency를 강제하지 않고, 이 host app에서만 실제 HTTP request가
 * Micrometer observation으로 발행되도록 servlet filter를 등록한다.</p>
 */
@Configuration(proxyBeanMethods = false)
public class EccSmokeMvcObservationRuntimeConfiguration {

    /**
     * Spring MVC 요청을 {@link ObservationRegistry}로 전달하는 smoke-only servlet filter를 등록한다.
     */
    @Bean
    @ConditionalOnMissingBean(ServerHttpObservationFilter.class)
    public ServerHttpObservationFilter serverHttpObservationFilter(ObservationRegistry observationRegistry) {
        return new ServerHttpObservationFilter(observationRegistry);
    }

    /**
     * smoke servlet container에 observation filter를 명시적으로 등록해 실제 HTTP request를 관측한다.
     */
    @Bean
    @ConditionalOnMissingBean(name = "serverHttpObservationFilterRegistration")
    public FilterRegistrationBean<ServerHttpObservationFilter> serverHttpObservationFilterRegistration(
            ServerHttpObservationFilter serverHttpObservationFilter) {
        FilterRegistrationBean<ServerHttpObservationFilter> registration = new FilterRegistrationBean<>(
                serverHttpObservationFilter);
        registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
