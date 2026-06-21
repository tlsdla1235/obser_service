package com.observation.portal.domain.health.service;

import com.observation.portal.domain.health.model.HealthCheckState;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReadinessProbeServiceTest {

    private final JdbcOperations jdbcOperations = mock(JdbcOperations.class);
    private final Flyway flyway = mock(Flyway.class);
    private final MigrationInfoService migrationInfoService = mock(MigrationInfoService.class);
    private final ReadinessProbeService service = new ReadinessProbeService(jdbcOperations, flyway);

    @Test
    void checkReturnsReadyWhenDatabasePingAndFlywayPendingCheckPass() {
        when(jdbcOperations.queryForObject("select 1", Integer.class)).thenReturn(1);
        when(flyway.info()).thenReturn(migrationInfoService);
        when(migrationInfoService.pending()).thenReturn(new MigrationInfo[0]);

        ReadinessCheckResult result = service.check();

        assertThat(result.ready()).isTrue();
        assertThat(result.database()).isEqualTo(HealthCheckState.UP);
        assertThat(result.flyway()).isEqualTo(HealthCheckState.UP);
    }

    @Test
    void checkMarksDatabaseDownWithoutExposingExceptionDetails() {
        when(jdbcOperations.queryForObject("select 1", Integer.class))
                .thenThrow(new DataAccessResourceFailureException("jdbc:postgresql://internal/password"));

        ReadinessCheckResult result = service.check();

        assertThat(result.ready()).isFalse();
        assertThat(result.database()).isEqualTo(HealthCheckState.DOWN);
        assertThat(result.flyway()).isEqualTo(HealthCheckState.UNKNOWN);
    }

    @Test
    void checkMarksFlywayDownWhenPendingMigrationExists() {
        when(jdbcOperations.queryForObject("select 1", Integer.class)).thenReturn(1);
        when(flyway.info()).thenReturn(migrationInfoService);
        when(migrationInfoService.pending()).thenReturn(new MigrationInfo[] { mock(MigrationInfo.class) });

        ReadinessCheckResult result = service.check();

        assertThat(result.ready()).isFalse();
        assertThat(result.database()).isEqualTo(HealthCheckState.UP);
        assertThat(result.flyway()).isEqualTo(HealthCheckState.DOWN);
    }
}
