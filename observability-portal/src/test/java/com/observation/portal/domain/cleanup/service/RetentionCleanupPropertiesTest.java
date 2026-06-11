package com.observation.portal.domain.cleanup.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetentionCleanupPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RetentionCleanupProperties.class);

    @Test
    @DisplayName("cleanup retention 기본 horizon은 dashboard snapshot retention과 같은 14일이다")
    void defaultRetentionHorizonUsesFourteenDaysAndOperationalFlagsStaySeparate() {
        RetentionCleanupProperties properties = new RetentionCleanupProperties(14, true, false);

        assertThat(properties.retentionDays()).isEqualTo(14);
        assertThat(properties.enabled()).isTrue();
        assertThat(properties.dryRun()).isFalse();
    }

    @Test
    @DisplayName("cleanup property fallback 기본값은 physical delete를 자동으로 켜지 않는다")
    void propertyFallbackStartsDisabledUntilOperatorOptsIn() {
        contextRunner.run(context -> {
            RetentionCleanupProperties properties = context.getBean(RetentionCleanupProperties.class);

            assertThat(properties.retentionDays()).isEqualTo(14);
            assertThat(properties.enabled()).isFalse();
            assertThat(properties.dryRun()).isFalse();
        });
    }

    @Test
    @DisplayName("packaged application.properties도 cleanup physical delete를 기본 비활성화한다")
    void packagedApplicationPropertiesKeepsPhysicalCleanupDisabledByDefault() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            assertThat(input).isNotNull();
            properties.load(input);
        }

        assertThat(properties.getProperty("portal.retention.cleanup.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("portal.retention.cleanup.dry-run")).isEqualTo("false");
    }

    @Test
    @DisplayName("cleanup retention-days가 0 이하이면 빠르게 실패한다")
    void rejectsNonPositiveRetentionDays() {
        assertThatThrownBy(() -> new RetentionCleanupProperties(0, true, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("retentionDays must be positive");
        assertThatThrownBy(() -> new RetentionCleanupProperties(-1, true, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("retentionDays must be positive");
    }
}
