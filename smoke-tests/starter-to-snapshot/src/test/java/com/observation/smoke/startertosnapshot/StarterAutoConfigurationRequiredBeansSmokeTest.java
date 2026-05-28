package com.observation.smoke.startertosnapshot;

import com.observation.starter.client.PortalMetricBucketClient;
import com.observation.starter.spring.observation.MicrometerHttpServerObservationBinder;
import io.micrometer.observation.ObservationHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 6.0 Checkpoint 2의 starter auto-configuration production wiring regression test다.
 *
 * <p>smoke app은 setup guide 핵심 속성과 starter dependency만 가진 사용자 앱으로 두고,
 * 필요한 runtime bean을 사용자 custom bean으로 덮지 않는다.</p>
 */
@SpringBootTest(
        classes = StarterToSnapshotSmokeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "observation.heartbeat.portal-base-url=http://127.0.0.1:1",
                "observation.heartbeat.project-key=issued-project-key",
                "observation.metric-flush.environment=preflight",
                "observation.heartbeat.timeout-millis=10",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
        })
class StarterAutoConfigurationRequiredBeansSmokeTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void starterOnlyConfigurationShouldCreatePortalMetricBucketClientFromSetupGuideProperties() {
        assertThat(applicationContext.getBeansOfType(PortalMetricBucketClient.class))
                .as("""
                        Story 6.1 setup guide 속성만 둔 사용자 앱에서 starter가 PortalMetricBucketClient를 제공해야 한다.
                        smoke app은 이 gap을 덮기 위한 custom PortalMetricBucketClient @Bean을 선언하지 않는다.
                        """)
                .hasSize(1);
    }

    @Test
    void starterOnlyConfigurationShouldRegisterHttpObservationBinderAsRuntimeHandler() {
        assertThat(applicationContext.getBeansOfType(MicrometerHttpServerObservationBinder.class))
                .as("""
                        HTTP endpoint 호출이 starter bucket rollup으로 흘러가려면
                        MicrometerHttpServerObservationBinder가 starter auto-configuration으로 등록되어야 한다.
                        """)
                .hasSize(1);

        assertThat(applicationContext.getBeansOfType(ObservationHandler.class).values())
                .as("runtime ObservationHandler 목록에 MicrometerHttpServerObservationBinder가 포함되어야 한다.")
                .anySatisfy(handler -> assertThat(handler)
                        .isInstanceOf(MicrometerHttpServerObservationBinder.class));
    }
}
