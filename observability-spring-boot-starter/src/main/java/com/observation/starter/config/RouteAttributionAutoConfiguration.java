package com.observation.starter.config;

import com.observation.starter.service.LowCardinalityHttpObservationGuard;
import com.observation.starter.service.RouteNormalizationService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Route Attribution B안에 필요한 allowlist 설정과 guard service를 연결한다.
 *
 * <p>host application은 {@code observation.route-attribution.allowlist}에 route template만
 * 선언할 수 있으며, 이 값은 {@code http.route} 부재 또는 실패 시 raw path candidate matching에만 사용된다.</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(RouteAttributionProperties.class)
public class RouteAttributionAutoConfiguration {

    /**
     * starter 설정 allowlist를 사용하는 route normalization service를 등록한다.
     */
    @Bean
    @ConditionalOnMissingBean
    public RouteNormalizationService routeNormalizationService(RouteAttributionProperties properties) {
        return new RouteNormalizationService(properties.getAllowlist());
    }

    /**
     * route/tag guard가 설정 기반 route normalization service를 사용하도록 연결한다.
     */
    @Bean
    @ConditionalOnMissingBean
    public LowCardinalityHttpObservationGuard lowCardinalityHttpObservationGuard(
            RouteNormalizationService routeNormalizationService) {
        return new LowCardinalityHttpObservationGuard(routeNormalizationService);
    }
}
