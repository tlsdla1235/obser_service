package com.observation.smoke;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * repo-local smoke 전용 Spring Boot host application entrypoint다.
 *
 * <p>production portal runtime behavior를 포함하지 않고, starter dependency가 실제 host app에서
 * auto-configuration되는지 검증하는 local operator runtime으로만 사용한다.</p>
 */
@SpringBootApplication
public class SmokeServiceApplication {

    /**
     * local smoke service를 Spring Boot application으로 실행한다.
     */
    public static void main(String[] args) {
        SpringApplication.run(SmokeServiceApplication.class, args);
    }
}
