package com.observation.portal.domain.health.service;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfoService;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * 운영 ready endpoint가 사용할 DB/Flyway readiness probe를 수행한다.
 *
 * <p>예외 메시지나 connection 문자열은 반환하지 않고 component별 UP/DOWN/UNKNOWN 상태로만 축약한다.</p>
 */
@Service
public class ReadinessProbeService {

    private final JdbcOperations jdbcOperations;
    private final Optional<Flyway> flyway;

    /**
     * DB ping과 Flyway migration 상태 확인에 필요한 runtime collaborator를 주입한다.
     *
     * <p>일부 schema alignment 테스트처럼 Flyway를 수동으로 실행하고 Spring bean은 끄는 컨텍스트가 있으므로,
     * Flyway bean이 없더라도 애플리케이션 부팅 자체는 허용하고 ready 판정에서만 실패로 축약한다.</p>
     */
    public ReadinessProbeService(JdbcOperations jdbcOperations, Optional<Flyway> flyway) {
        this.jdbcOperations = Objects.requireNonNull(jdbcOperations, "jdbcOperations must not be null");
        this.flyway = Objects.requireNonNull(flyway, "flyway must not be null");
    }

    /**
     * DB 연결과 Flyway pending migration 여부를 확인해 운영 요청 수용 가능 상태를 판정한다.
     */
    public ReadinessCheckResult check() {
        if (!databaseResponds()) {
            return ReadinessCheckResult.databaseDown();
        }
        if (!flywayHasNoPendingMigrations()) {
            return ReadinessCheckResult.flywayDown();
        }
        return ReadinessCheckResult.readyResult();
    }

    private boolean databaseResponds() {
        try {
            Integer result = jdbcOperations.queryForObject("select 1", Integer.class);
            return Integer.valueOf(1).equals(result);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean flywayHasNoPendingMigrations() {
        if (flyway.isEmpty()) {
            return false;
        }
        try {
            MigrationInfoService info = flyway.get().info();
            return info != null && info.pending().length == 0;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
