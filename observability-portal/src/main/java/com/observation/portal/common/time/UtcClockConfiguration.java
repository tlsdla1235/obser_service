package com.observation.portal.common.time;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * portal service에서 공유하는 UTC system clock bean을 제공한다.
 *
 * <p>시간 의존 계산은 이 Clock을 주입받아 테스트에서 고정 시각으로 대체할 수 있게 한다.</p>
 */
@Configuration
public class UtcClockConfiguration {

    /**
     * production 기본 clock은 UTC 기준 system clock이다.
     */
    @Bean
    public Clock utcClock() {
        return Clock.systemUTC();
    }
}
