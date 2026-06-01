package com.observation.starter.config;

import com.observation.starter.service.ObservationSampleCollector;
import com.observation.starter.spring.observation.MicrometerHttpServerObservationBinder;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Micrometer HTTP server observation handler를 starter collector boundary에 연결하는 auto-configuration이다.
 *
 * <p>host/smoke application이 별도 custom bean을 만들지 않아도 Spring Boot의 ObservationRegistry가
 * {@link ObservationHandler} bean을 자동 등록할 수 있게 한다.</p>
 */
@AutoConfiguration(after = MetricDrainAutoConfiguration.class)
public class ObservationBindingAutoConfiguration {

    /**
     * starter local collector가 있을 때만 HTTP server observation handler를 노출한다.
     *
     * <p>handler는 request path에서 local sample record만 수행하고, portal network call이나 flush를 직접
     * 실행하지 않는다.</p>
     */
    @Bean
    @ConditionalOnMissingBean(MicrometerHttpServerObservationBinder.class)
    @ConditionalOnBean(ObservationSampleCollector.class)
    public ObservationHandler<Observation.Context> micrometerHttpServerObservationBinder(
            ObservationSampleCollector collector) {
        return new MicrometerHttpServerObservationBinder(collector);
    }
}
