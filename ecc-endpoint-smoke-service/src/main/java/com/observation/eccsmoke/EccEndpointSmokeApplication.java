package com.observation.eccsmoke;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ECC-back과 유사한 HTTP surface를 만드는 local smoke 전용 Spring Boot entrypoint다.
 *
 * <p>비즈니스 로직과 DB 접근 없이 controller mapping과 starter 관측 경로만 검증하는 host app으로 사용한다.</p>
 */
@SpringBootApplication
public class EccEndpointSmokeApplication {

    /**
     * ECC endpoint smoke service를 Spring Boot application으로 실행한다.
     */
    public static void main(String[] args) {
        SpringApplication.run(EccEndpointSmokeApplication.class, args);
    }
}
