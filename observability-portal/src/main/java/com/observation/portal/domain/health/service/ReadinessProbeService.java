package com.observation.portal.domain.health.service;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfoService;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 운영 ready endpoint가 사용할 DB/Flyway readiness probe를 수행한다.
 *
 * <p>예외 메시지나 connection 문자열은 반환하지 않고 component별 UP/DOWN/UNKNOWN 상태로만 축약한다.</p>
 */
@Service
public class ReadinessProbeService {

    private final JdbcOperations jdbcOperations;
    private final Flyway flyway;

    /**
     * DB ping과 Flyway migration 상태 확인에 필요한 runtime collaborator를 주입한다.
     */
    public ReadinessProbeService(JdbcOperations jdbcOperations, Flyway flyway) {
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
        try {
            MigrationInfoService info = flyway.info();
            return info != null && info.pending().length == 0;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
