package com.observation.smoke.startertosnapshot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Story 6.0 preflight에서 실제 사용자 Spring Boot 서비스 역할을 하는 최소 smoke app이다.
 *
 * <p>starter dependency와 setup guide 속성만으로 auto-configuration이 붙는지 확인하기 위해
 * starter runtime bridge bean을 사용자 코드에서 직접 등록하지 않는다.</p>
 */
@SpringBootApplication
public class StarterToSnapshotSmokeApplication {

    /**
     * 수동 preflight 실행 시 smoke app을 기동한다.
     */
    public static void main(String[] args) {
        SpringApplication.run(StarterToSnapshotSmokeApplication.class, args);
    }
}
