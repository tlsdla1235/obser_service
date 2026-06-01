package com.observation.portal.domain.admin.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * `portal.smoke.seed.enabled=true`일 때만 local smoke seed를 한 번 실행하는 Spring Boot runner다.
 *
 * <p>성공 출력은 project id/name과 다음 검증 명령만 포함하고, access token이나 raw project key는 출력하지 않는다.</p>
 */
@Component
public class SmokeProjectSeedRunner implements ApplicationRunner {

    private final Environment environment;
    private final SmokeProjectSeedService seedService;

    /**
     * seed 설정을 읽을 Environment와 실제 row 생성을 담당하는 service를 주입한다.
     */
    public SmokeProjectSeedRunner(Environment environment, SmokeProjectSeedService seedService) {
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
        this.seedService = Objects.requireNonNull(seedService, "seedService must not be null");
    }

    /**
     * seed가 비활성화된 일반 실행에서는 아무 row도 쓰지 않고 즉시 반환한다.
     */
    @Override
    public void run(ApplicationArguments args) {
        SmokeProjectSeedProperties properties = SmokeProjectSeedProperties.from(environment);
        if (!properties.enabled()) {
            return;
        }
        SmokeProjectSeedResult result = seedService.seed(properties);
        System.out.printf(
                "Smoke seed complete: projectId=%s projectName=%s projectCreated=%s membershipStatus=%s%n",
                result.projectId(),
                result.projectName(),
                result.projectCreated(),
                result.membershipStatus());
        System.out.printf("Next verification: %s%n", result.verificationCommand());
    }
}
