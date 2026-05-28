package com.observation.starter.config;

import com.observation.starter.service.ObservationSampleCollector;
import com.observation.starter.spring.web.StarterHttpServerObservationFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * 최소 Spring MVC servlet app의 HTTP request를 starter metric rollup으로 전달하는 auto-configuration이다.
 *
 * <p>Actuator나 host custom ObservationRegistry 없이도 starter dependency만으로 request sample을 만들되,
 * portal 전송은 여전히 background bucket flush worker에 맡긴다.</p>
 */
@AutoConfiguration(after = MetricDrainAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "jakarta.servlet.Filter")
public class HttpServerObservationAutoConfiguration {

    /**
     * servlet request 완료 시점에 low-cardinality HTTP sample을 collector boundary로 넘기는 filter를 등록한다.
     */
    @Bean
    @ConditionalOnMissingBean(name = "starterHttpServerObservationFilterRegistration")
    @ConditionalOnBean(ObservationSampleCollector.class)
    public FilterRegistrationBean<StarterHttpServerObservationFilter> starterHttpServerObservationFilterRegistration(
            ObservationSampleCollector collector) {
        FilterRegistrationBean<StarterHttpServerObservationFilter> registration =
                new FilterRegistrationBean<>(new StarterHttpServerObservationFilter(collector));
        registration.setName("starterHttpServerObservationFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
