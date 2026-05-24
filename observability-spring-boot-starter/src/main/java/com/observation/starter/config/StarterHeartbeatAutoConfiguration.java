package com.observation.starter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.starter.client.JdkPortalHeartbeatClient;
import com.observation.starter.client.NoopPortalHeartbeatClient;
import com.observation.starter.client.PortalHeartbeatClient;
import com.observation.starter.service.StarterHeartbeatSender;
import com.observation.starter.service.StarterHeartbeatService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.time.Instant;

/**
 * starter heartbeat control-plane sender를 Spring runtime에 연결하는 auto-configuration이다.
 */
@AutoConfiguration(after = MetricDrainAutoConfiguration.class)
@EnableConfigurationProperties({MetricDrainProperties.class, HeartbeatProperties.class})
public class StarterHeartbeatAutoConfiguration {

    /**
     * portal 연결 설정이 있으면 JDK HTTP client를 만들고, 없으면 startup-safe no-op client를 등록한다.
     */
    @Bean
    @ConditionalOnMissingBean
    public PortalHeartbeatClient portalHeartbeatClient(HeartbeatProperties properties) {
        if (!properties.hasPortalConnectionSettings()) {
            return new NoopPortalHeartbeatClient();
        }
        return new JdkPortalHeartbeatClient(
                properties.heartbeatUri(),
                properties.getProjectKey(),
                properties.timeout(),
                new ObjectMapper());
    }

    /**
     * metric bucket ingest와 같은 application identity를 사용해 heartbeat payload builder를 구성한다.
     */
    @Bean
    @ConditionalOnMissingBean
    public StarterHeartbeatService starterHeartbeatService(
            PortalHeartbeatClient client,
            MetricDrainProperties metricDrainProperties,
            HeartbeatProperties heartbeatProperties,
            Environment environment) {
        return new StarterHeartbeatService(
                client,
                metricDrainProperties.ingestEnvelopeIdentity(environment),
                heartbeatProperties,
                Instant::now);
    }

    /**
     * heartbeat service를 host request path와 분리된 daemon sender로 실행한다.
     */
    @Bean(initMethod = "start", destroyMethod = "close")
    @ConditionalOnMissingBean
    public StarterHeartbeatSender starterHeartbeatSender(
            StarterHeartbeatService heartbeatService,
            HeartbeatProperties properties) {
        return new StarterHeartbeatSender(heartbeatService, properties.interval());
    }
}
