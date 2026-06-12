package com.observation.starter.service;

import com.observation.starter.model.metric.DatasourcePoolMetricSample;
import com.observation.starter.model.metric.JvmMetricSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * background tick에서 JVM/datasource resource 샘플을 읽어 starter local ingest 경계에 기록한다.
 *
 * <p>개별 resource 수집 실패는 다른 resource와 host request path로 전파하지 않고 해당 tick에서만 생략한다.</p>
 */
public final class StarterResourceMetricSampler {

    private static final Logger log = LoggerFactory.getLogger(StarterResourceMetricSampler.class);

    private final Supplier<Optional<JvmMetricSample>> jvmSampleSupplier;
    private final Supplier<Optional<DatasourcePoolMetricSample>> datasourceSampleSupplier;
    private final Consumer<JvmMetricSample> jvmSampleRecorder;
    private final Consumer<DatasourcePoolMetricSample> datasourceSampleRecorder;

    /**
     * 기본 JVM/datasource sampler와 starter collector를 연결한다.
     */
    public StarterResourceMetricSampler(
            ObservationSampleCollector collector,
            JdkJvmMetricSampler jvmMetricSampler,
            HikariDatasourcePoolMetricSampler datasourcePoolMetricSampler) {
        this(Objects.requireNonNull(jvmMetricSampler, "jvmMetricSampler must not be null")::sample,
                Objects.requireNonNull(
                        datasourcePoolMetricSampler,
                        "datasourcePoolMetricSampler must not be null")::sample,
                Objects.requireNonNull(collector, "collector must not be null")::recordJvmMetricSample,
                collector::recordDatasourcePoolMetricSample);
    }

    StarterResourceMetricSampler(
            Supplier<Optional<JvmMetricSample>> jvmSampleSupplier,
            Supplier<Optional<DatasourcePoolMetricSample>> datasourceSampleSupplier,
            Consumer<JvmMetricSample> jvmSampleRecorder,
            Consumer<DatasourcePoolMetricSample> datasourceSampleRecorder) {
        this.jvmSampleSupplier = Objects.requireNonNull(jvmSampleSupplier, "jvmSampleSupplier must not be null");
        this.datasourceSampleSupplier = Objects.requireNonNull(
                datasourceSampleSupplier,
                "datasourceSampleSupplier must not be null");
        this.jvmSampleRecorder = Objects.requireNonNull(jvmSampleRecorder, "jvmSampleRecorder must not be null");
        this.datasourceSampleRecorder = Objects.requireNonNull(
                datasourceSampleRecorder,
                "datasourceSampleRecorder must not be null");
    }

    /**
     * 현재 tick에서 관측 가능한 resource sample을 local rollup에 기록한다.
     */
    public void sampleAndRecord() {
        sampleAndRecordJvm();
        sampleAndRecordDatasource();
    }

    private void sampleAndRecordJvm() {
        try {
            jvmSampleSupplier.get().ifPresent(jvmSampleRecorder);
        } catch (RuntimeException exception) {
            log.debug("starter jvm resource metric sample skipped", exception);
        }
    }

    private void sampleAndRecordDatasource() {
        try {
            datasourceSampleSupplier.get().ifPresent(datasourceSampleRecorder);
        } catch (RuntimeException exception) {
            log.debug("starter datasource resource metric sample skipped", exception);
        }
    }
}
