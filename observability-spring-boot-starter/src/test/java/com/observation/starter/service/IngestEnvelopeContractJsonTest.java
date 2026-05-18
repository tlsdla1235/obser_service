package com.observation.starter.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.starter.model.ingest.IngestEnvelopeCandidate;
import com.observation.starter.model.ingest.IngestEnvelopeIdentity;
import com.observation.starter.model.metric.AppMetricRollup;
import com.observation.starter.model.metric.ClosedMetricBucket;
import com.observation.starter.model.metric.DatasourcePoolMetricSample;
import com.observation.starter.model.metric.EndpointKey;
import com.observation.starter.model.metric.EndpointMetricRollup;
import com.observation.starter.model.metric.HistogramBucket;
import com.observation.starter.model.metric.JvmMetricSample;
import com.observation.starter.model.route.NormalizedRoute;
import com.observation.starter.model.time.MetricBucketInterval;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class IngestEnvelopeContractJsonTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setDefaultPropertyInclusion(JsonInclude.Value.construct(
                    JsonInclude.Include.NON_NULL,
                    JsonInclude.Include.NON_NULL));

    @Test
    void serializesRepresentativeEnvelopeToGoldenContractShape() throws Exception {
        IngestEnvelopeBuilderService builder = new IngestEnvelopeBuilderService(new IngestEnvelopeIdentity(
                "project-123",
                "orders-api",
                "prod",
                "orders-api-7f9c9c8c9d-x2p4k"));

        IngestEnvelopeCandidate candidate = builder.build(contractBucket());
        String json = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(candidate.payload());

        assertEquals("""
                {
                  "schemaVersion" : "1.0",
                  "application" : {
                    "name" : "orders-api",
                    "environment" : "prod",
                    "instance" : "orders-api-7f9c9c8c9d-x2p4k"
                  },
                  "bucket" : {
                    "startUtc" : "2026-05-08T01:00:00Z",
                    "endUtc" : "2026-05-08T01:00:30Z",
                    "durationSeconds" : 30
                  },
                  "summary" : {
                    "requestCount" : 3,
                    "errorCount" : 1,
                    "httpServerDurationBuckets" : [ {
                      "leMs" : 50,
                      "count" : 1
                    }, {
                      "leMs" : 100,
                      "count" : 2
                    }, {
                      "leMs" : 250,
                      "count" : 3
                    }, {
                      "leMs" : 500,
                      "count" : 3
                    }, {
                      "leMs" : 1000,
                      "count" : 3
                    } ],
                    "jvm" : {
                      "cpuUsage" : 0.64,
                      "heapUsedRatio" : 0.71
                    },
                    "datasource" : {
                      "poolUsageRatio" : 0.82
                    }
                  },
                  "endpoints" : [ {
                    "method" : "GET",
                    "route" : "/orders/{orderId}",
                    "requestCount" : 2,
                    "errorCount" : 0,
                    "durationBuckets" : [ {
                      "leMs" : 50,
                      "count" : 1
                    }, {
                      "leMs" : 100,
                      "count" : 2
                    }, {
                      "leMs" : 250,
                      "count" : 2
                    }, {
                      "leMs" : 500,
                      "count" : 2
                    }, {
                      "leMs" : 1000,
                      "count" : 2
                    } ]
                  }, {
                    "method" : "POST",
                    "route" : "/orders",
                    "requestCount" : 1,
                    "errorCount" : 1,
                    "durationBuckets" : [ {
                      "leMs" : 50,
                      "count" : 0
                    }, {
                      "leMs" : 100,
                      "count" : 0
                    }, {
                      "leMs" : 250,
                      "count" : 1
                    }, {
                      "leMs" : 500,
                      "count" : 1
                    }, {
                      "leMs" : 1000,
                      "count" : 1
                    } ]
                  } ]
                }""", json);
        assertEquals(
                "project-123:orders-api:prod:orders-api-7f9c9c8c9d-x2p4k:2026-05-08T01:00:00Z",
                candidate.idempotencyKey());
        assertFalse(json.contains("raw"));
        assertFalse(json.contains("query"));
        assertFalse(json.contains("tags"));
        assertFalse(json.contains("custom"));
        assertFalse(json.contains("p95"));
        assertFalse(json.contains("state"));
        assertFalse(json.contains("priority"));
    }

    private static ClosedMetricBucket contractBucket() {
        Instant start = Instant.parse("2026-05-08T01:00:00Z");
        return new ClosedMetricBucket(
                new MetricBucketInterval(start, start.plusSeconds(30)),
                new AppMetricRollup(
                        3,
                        1,
                        List.of(
                                new HistogramBucket(250, 3),
                                new HistogramBucket(50, 1),
                                new HistogramBucket(100, 2),
                                new HistogramBucket(500, 3),
                                new HistogramBucket(1000, 3)),
                        Optional.of(new JvmMetricSample(Instant.parse("2026-05-08T01:00:20Z"), 0.64d, 0.71d)),
                        Optional.of(new DatasourcePoolMetricSample(
                                Instant.parse("2026-05-08T01:00:25Z"),
                                0.82d))),
                List.of(
                        new EndpointMetricRollup(
                                new EndpointKey("POST", NormalizedRoute.of("/orders")),
                                1,
                                1,
                                List.of(
                                        new HistogramBucket(250, 1),
                                        new HistogramBucket(50, 0),
                                        new HistogramBucket(100, 0),
                                        new HistogramBucket(500, 1),
                                        new HistogramBucket(1000, 1))),
                        new EndpointMetricRollup(
                                new EndpointKey("GET", NormalizedRoute.of("/orders/{orderId}")),
                                2,
                                0,
                                List.of(
                                        new HistogramBucket(250, 2),
                                        new HistogramBucket(50, 1),
                                        new HistogramBucket(100, 2),
                                        new HistogramBucket(500, 2),
                                        new HistogramBucket(1000, 2)))));
    }
}
