package com.observation.portal.domain.ingest.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 포털이 starter ingest envelope를 다시 검증하기 위해 사용하는 schemaVersion 1.0 요청 모델이다.
 *
 * <p>허용된 contract field만 표현하며 Jackson unknown field 거부를 통해 free tag, custom metric,
 * raw timeseries, Post-MVP runtime aggregate 후보가 service boundary로 들어오지 못하게 한다.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record IngestEnvelopeRequest(
        String schemaVersion,
        Application application,
        Bucket bucket,
        Summary summary,
        List<Endpoint> endpoints
) {

    /**
     * mutable list가 service 내부로 들어오지 않도록 top-level endpoint 목록을 고정한다.
     */
    public IngestEnvelopeRequest {
        endpoints = endpoints == null ? null : List.copyOf(endpoints);
    }

    /**
     * application/environment/instance identity block이다.
     */
    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Application(String name, String environment, String instance) {
    }

    /**
     * UTC 30초 bucket interval metadata block이다.
     */
    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Bucket(String startUtc, String endUtc, Integer durationSeconds) {
    }

    /**
     * application-level request/error count, cumulative histogram, latest runtime ratio block이다.
     */
    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Summary(
            Long requestCount,
            Long errorCount,
            List<DurationBucket> httpServerDurationBuckets,
            Jvm jvm,
            Datasource datasource
    ) {

        /**
         * histogram bucket list를 service validation 전에도 immutable하게 유지한다.
         */
        public Summary {
            httpServerDurationBuckets = httpServerDurationBuckets == null
                    ? null
                    : List.copyOf(httpServerDurationBuckets);
        }
    }

    /**
     * schemaVersion 1.0에서 허용하는 JVM latest ratio block이다.
     */
    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Jvm(Double cpuUsage, Double heapUsedRatio) {
    }

    /**
     * schemaVersion 1.0에서 허용하는 datasource latest ratio block이다.
     */
    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Datasource(Double poolUsageRatio) {
    }

    /**
     * method + normalized route 기준 endpoint metric block이다.
     */
    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Endpoint(
            String method,
            String route,
            Long requestCount,
            Long errorCount,
            List<DurationBucket> durationBuckets
    ) {

        /**
         * endpoint histogram bucket list를 immutable하게 유지한다.
         */
        public Endpoint {
            durationBuckets = durationBuckets == null ? null : List.copyOf(durationBuckets);
        }
    }

    /**
     * cumulative HTTP duration histogram bucket 한 칸이다.
     */
    @JsonIgnoreProperties(ignoreUnknown = false)
    public record DurationBucket(Long leMs, Long count) {
    }
}
