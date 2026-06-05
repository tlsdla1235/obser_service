package com.observation.portal.domain.ingest.benchmark;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * SQS ingest benchmark harness를 명시 opt-in으로만 동작시키기 위한 전용 설정이다.
 *
 * <p>기본 local/dev/test/smoke/CI profile은 이 값을 읽더라도 benchmark 실행이나 resource 축소를 시작하지 않는다.</p>
 */
@Component
@ConfigurationProperties(prefix = "portal.ingest.benchmark")
public class IngestBenchmarkProperties {

    private boolean enabled = false;
    private String outputDir = "build/reports/ingest-benchmark";
    private final Fixture fixture = new Fixture();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = requireText(outputDir, "outputDir");
    }

    /**
     * direct/SQS 비교에 공통으로 사용할 synthetic fixture 설정이다.
     */
    public Fixture getFixture() {
        return fixture;
    }

    /**
     * Story 12.5 primary fixture는 application 1개와 synthetic instance 30개를 기준으로 한다.
     */
    public static class Fixture {

        private int applicationCount = 1;
        private int instanceCount = 30;
        private double duplicateRatio = 0.10d;
        private double conflictRatio = 0.0d;
        private String cadence = "30s bucket payload replay";

        public int getApplicationCount() {
            return applicationCount;
        }

        public void setApplicationCount(int applicationCount) {
            if (applicationCount != 1) {
                throw new IllegalArgumentException("applicationCount must remain 1 for primary benchmark fixture");
            }
            this.applicationCount = applicationCount;
        }

        public int getInstanceCount() {
            return instanceCount;
        }

        public void setInstanceCount(int instanceCount) {
            if (instanceCount != 30) {
                throw new IllegalArgumentException("instanceCount must remain 30 for primary benchmark fixture");
            }
            this.instanceCount = instanceCount;
        }

        public double getDuplicateRatio() {
            return duplicateRatio;
        }

        public void setDuplicateRatio(double duplicateRatio) {
            this.duplicateRatio = ratio(duplicateRatio, "duplicateRatio");
        }

        public double getConflictRatio() {
            return conflictRatio;
        }

        public void setConflictRatio(double conflictRatio) {
            this.conflictRatio = ratio(conflictRatio, "conflictRatio");
        }

        public String getCadence() {
            return cadence;
        }

        public void setCadence(String cadence) {
            this.cadence = requireText(cadence, "cadence");
        }
    }

    private static double ratio(double value, String fieldName) {
        if (Double.isNaN(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
        return value;
    }

    private static String requireText(String value, String fieldName) {
        String requiredValue = Objects.requireNonNull(value, fieldName + " must not be null").trim();
        if (requiredValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return requiredValue;
    }
}
