---
artifactType: contract
name: ingest-envelope
architectureStyle: Traditional MVC
status: mvc-version-generated
date: 2026-05-09
---

# Contract - Ingest Envelope MVC Version

## 1. 역할

`ingest-envelope`는 starter와 portal 사이의 source of truth다.

이 계약은 pull-based scrape/query 방식을 MVP 필수 경로에서 제외하고, portal service layer가 lifecycle state, p95, triage summary, endpoint priority를 계산할 수 있는 최소 bounded payload만 허용한다.

## 2. API

```http
POST /api/ingest/v1/buckets
X-OBS-Project-Key: <project-key>
Idempotency-Key: <project-id>:<application>:<environment>:<instance>:<bucket-start-utc>
Content-Type: application/json
```

## 3. Payload Shape

```json
{
  "schemaVersion": "1.0",
  "application": {
    "name": "orders-api",
    "environment": "prod",
    "instance": "orders-api-7f9c9c8c9d-x2p4k"
  },
  "bucket": {
    "startUtc": "2026-05-08T01:00:00Z",
    "endUtc": "2026-05-08T01:00:30Z",
    "durationSeconds": 30
  },
  "summary": {
    "requestCount": 1200,
    "errorCount": 18,
    "httpServerDurationBuckets": [
      { "leMs": 50, "count": 500 },
      { "leMs": 100, "count": 850 },
      { "leMs": 250, "count": 1120 },
      { "leMs": 500, "count": 1190 },
      { "leMs": 1000, "count": 1200 }
    ],
    "jvm": {
      "cpuUsage": 0.64,
      "heapUsedRatio": 0.71
    },
    "datasource": {
      "poolUsageRatio": 0.82
    }
  },
  "endpoints": [
    {
      "method": "POST",
      "route": "/orders",
      "requestCount": 300,
      "errorCount": 12,
      "durationBuckets": [
        { "leMs": 50, "count": 50 },
        { "leMs": 100, "count": 120 },
        { "leMs": 250, "count": 220 },
        { "leMs": 500, "count": 285 },
        { "leMs": 1000, "count": 300 }
      ]
    }
  ]
}
```

## 4. Validation Rules

- `schemaVersion`은 MVP에서 `1.0`만 허용한다.
- `bucket.durationSeconds`는 `30`만 허용한다.
- `bucket.startUtc`와 `bucket.endUtc`는 UTC이고 30초 boundary에 맞아야 한다.
- `application.name`, `environment`, `instance`는 비어 있으면 안 된다.
- endpoint `route`는 framework route template, configured allowlist template, 또는 `UNKNOWN`이어야 한다.
- raw path candidate, query string, query key/value, high-cardinality tag, attribution source raw detail은 payload shape에 존재할 수 없다.
- endpoint 항목은 이미 정규화된 route 기준의 bounded top-N 또는 허용 route set 안에 있어야 한다. 이 제한은 출력 cardinality cap이며 route attribution fallback으로 사용하지 않는다.
- histogram bucket은 cumulative count를 사용한다.
- 자유 tag map, arbitrary custom metric map, raw timeseries 배열은 허용하지 않는다.

## 5. Idempotency

동일 project/application/environment/instance/bucket start에 대해 같은 payload가 재전송되면 portal은 중복 저장하지 않고 성공으로 응답한다.

동일 idempotency key에 다른 payload hash가 들어오면 `409 Conflict`로 처리한다.

## 6. MVC Boundary

- `IngestController`는 header/body를 request DTO로 변환하고 `IngestAcceptanceService`에 위임한다.
- `IngestAcceptanceService`가 검증과 저장 orchestration을 수행한다.
- `MetricBucketRepository`가 idempotency와 persistence를 담당한다.
- PostgreSQL repository 구현은 저장/조회만 담당한다.
- Controller와 repository는 lifecycle state, p95, insight rule을 계산하지 않는다.

## 7. Non-Goals

- pull exposition format 지원
- arbitrary query를 위한 raw metric 저장
- allowlist matching의 임시 입력으로 사용된 raw path/query 저장
- high-cardinality label 검색
- user-defined custom metric ingestion
