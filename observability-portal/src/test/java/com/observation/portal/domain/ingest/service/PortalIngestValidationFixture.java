package com.observation.portal.domain.ingest.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.observation.portal.domain.catalog.model.ProjectStatus;
import com.observation.portal.domain.ingest.model.IngestEnvelopeRequest;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Story 2.5 starter golden envelope를 portal validation success fixture로 재사용하는 test helper다.
 */
public final class PortalIngestValidationFixture {

    public static final String PROJECT_KEY_HEADER = "pk_live_checkout.<test-placeholder>";
    public static final VerifiedProject VERIFIED_PROJECT = new VerifiedProject(
            UUID.fromString("00000000-0000-0000-0000-000000003201"),
            "checkout",
            ProjectStatus.ACTIVE);
    public static final String IDEMPOTENCY_KEY =
            "project-123:orders-api:prod:orders-api-7f9c9c8c9d-x2p4k:20260508T010000Z";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String GOLDEN_JSON = """
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
            }""";

    private PortalIngestValidationFixture() {
    }

    /**
     * Story 2.5 golden JSON 문자열을 반환한다.
     */
    public static String goldenJson() {
        return GOLDEN_JSON;
    }

    /**
     * Story 2.5 golden JSON을 portal request model로 deserialize한다.
     */
    public static IngestEnvelopeRequest goldenRequest() throws JsonProcessingException {
        return OBJECT_MAPPER.readValue(GOLDEN_JSON, IngestEnvelopeRequest.class);
    }

    /**
     * Story 4.0의 starter canonical percentile 후보를 포함한 request model을 만든다.
     */
    public static IngestEnvelopeRequest requestWithLocalPercentiles() throws JsonProcessingException {
        return requestWith(PortalIngestValidationFixture::addValidLocalPercentiles);
    }

    /**
     * golden JSON을 일부 변경한 뒤 portal request model로 deserialize한다.
     */
    public static IngestEnvelopeRequest requestWith(Consumer<ObjectNode> mutation) throws JsonProcessingException {
        return OBJECT_MAPPER.readValue(jsonWith(mutation), IngestEnvelopeRequest.class);
    }

    /**
     * JSON boundary test에서 사용할 변형 JSON을 만든다.
     */
    public static String jsonWith(Consumer<ObjectNode> mutation) throws JsonProcessingException {
        ObjectNode root = (ObjectNode) OBJECT_MAPPER.readTree(GOLDEN_JSON);
        mutation.accept(root);
        return OBJECT_MAPPER.writeValueAsString(root);
    }

    /**
     * localPercentiles 후보를 valid instance_bucket scope starter canonical point로 추가한다.
     */
    public static ObjectNode addValidLocalPercentiles(ObjectNode root) {
        ObjectNode localPercentiles = ((ObjectNode) root.get("summary")).putObject("localPercentiles");
        localPercentiles.put("scope", "instance_bucket");
        localPercentiles.put("source", "starter_local");
        localPercentiles.put("bucketStartUtc", "2026-05-08T01:00:00Z");
        localPercentiles.put("bucketEndUtc", "2026-05-08T01:00:30Z");
        localPercentiles.put("requestCount", 3);
        localPercentiles.put("p95Ms", 250);
        localPercentiles.put("p99Ms", 1000);
        localPercentiles.put("mergeable", false);
        return localPercentiles;
    }
}
