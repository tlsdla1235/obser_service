package com.observation.starter.service;

import com.observation.starter.model.metric.DatasourcePoolMetricSample;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HikariDatasourcePoolMetricSamplerTest {

    @Test
    void returnsEmptyWhenNoDatasourceExists() {
        HikariDatasourcePoolMetricSampler sampler = new HikariDatasourcePoolMetricSampler(
                () -> Instant.parse("2026-05-08T01:00:10Z"),
                List::of);

        Optional<DatasourcePoolMetricSample> sample = sampler.sample();

        assertTrue(sample.isEmpty());
    }

    @Test
    void samplesHighestObservedHikariPoolUsageRatio() {
        Instant observedAt = Instant.parse("2026-05-08T01:00:10Z");
        HikariDatasourcePoolMetricSampler sampler = new HikariDatasourcePoolMetricSampler(
                () -> observedAt,
                () -> List.of(
                        new HikariLikeDataSource(new HikariLikePoolMxBean(2, 4)),
                        new HikariLikeDataSource(new HikariLikePoolMxBean(8, 10))));

        DatasourcePoolMetricSample sample = sampler.sample().orElseThrow();

        assertEquals(observedAt, sample.observedAt());
        assertEquals(0.8d, sample.poolUsageRatio());
    }

    @Test
    void skipsDatasourceWithoutPoolMxBeanOrTotalConnections() {
        HikariDatasourcePoolMetricSampler sampler = new HikariDatasourcePoolMetricSampler(
                () -> Instant.parse("2026-05-08T01:00:10Z"),
                () -> List.of(
                        new PlainDataSource(),
                        new HikariLikeDataSource(new HikariLikePoolMxBean(1, 0))));

        Optional<DatasourcePoolMetricSample> sample = sampler.sample();

        assertTrue(sample.isEmpty());
    }

    static final class HikariLikeDataSource extends PlainDataSource {

        private final HikariLikePoolMxBean hikariPoolMxBean;

        private HikariLikeDataSource(HikariLikePoolMxBean hikariPoolMxBean) {
            this.hikariPoolMxBean = hikariPoolMxBean;
        }

        public HikariLikePoolMxBean getHikariPoolMXBean() {
            return hikariPoolMxBean;
        }
    }

    static final class HikariLikePoolMxBean {

        private final int activeConnections;
        private final int totalConnections;

        private HikariLikePoolMxBean(int activeConnections, int totalConnections) {
            this.activeConnections = activeConnections;
            this.totalConnections = totalConnections;
        }

        public int getActiveConnections() {
            return activeConnections;
        }

        public int getTotalConnections() {
            return totalConnections;
        }
    }

    static class PlainDataSource implements DataSource {

        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
