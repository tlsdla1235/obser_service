package com.observation.starter.spring;

import com.observation.starter.service.StarterMetricIngestService;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;

/**
 * starter runtime에서 idle 상태의 due bucket을 queue로 넘기기 위한 30초 drain tick 어댑터다.
 *
 * <p>이 클래스는 Spring scheduling boundary만 담당하며 portal client, HTTP transport, envelope
 * serialization, idempotency 생성은 알지 않는다.</p>
 */
public final class StarterMetricDrainScheduler {

    public static final long FLUSH_CADENCE_MILLIS = 30_000L;

    private final StarterMetricIngestService ingestService;

    /**
     * scheduled tick이 호출할 local ingest service 경계를 주입받는다.
     */
    public StarterMetricDrainScheduler(StarterMetricIngestService ingestService) {
        this.ingestService = Objects.requireNonNull(ingestService, "ingestService must not be null");
    }

    /**
     * starter flush cadence에 맞춰 새 샘플이 없어도 due bucket drain을 시도한다.
     */
    @Scheduled(fixedDelay = FLUSH_CADENCE_MILLIS, initialDelay = FLUSH_CADENCE_MILLIS)
    public void drainDueBucketsOnTick() {
        ingestService.drainDueBuckets();
    }
}
