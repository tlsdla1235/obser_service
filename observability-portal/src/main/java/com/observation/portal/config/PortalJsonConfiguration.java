package com.observation.portal.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * portal 내부 JSON 직렬화 구성이다.
 *
 * <p>accepted bucket JSON persistence와 payload hash 계산이 같은 기본 ObjectMapper를 공유하도록 한다.</p>
 */
@Configuration
public class PortalJsonConfiguration {

    /**
     * Spring Web/Jackson 자동 구성 bean이 없을 때 사용할 기본 ObjectMapper를 제공한다.
     */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
