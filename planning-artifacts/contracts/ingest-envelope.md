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
Idempotency-Key: <project-id>:<application>:<environment>:<instance>:<bucket-start-utc-basic>
Content-Type: application/json
```

`Idempotency-Key`의 identity component는 `A-Z`, `a-z`, `0-9`, `.`, `_`, `-`만 허용한다.
delimiter `:`와 제어문자는 component 안에 들어갈 수 없다. `bucket-start-utc-basic`은 같은 이유로
UTC bucket start를 `yyyyMMdd'T'HHmmss'Z'` 형식으로 표현한다.

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

### 3.1 Post-MVP Runtime Aggregate Payload Candidate

MVP `schemaVersion: "1.0"`은 JVM/datasource runtime ratio를 latest sample shape로 유지한다. Post-MVP에서 saturation spike를 놓치지 않기 위해 runtime aggregate를 추가할 경우에는 `schemaVersion`을 분리하고, 아래 의미의 bounded aggregate shape를 사용한다.

```json
{
  "schemaVersion": "1.1",
  "summary": {
    "jvm": {
      "cpuUsage": {
        "latest": 0.64,
        "max": 0.91,
        "avg": 0.70,
        "sampleCount": 6
      },
      "heapUsedRatio": {
        "latest": 0.71,
        "max": 0.83,
        "avg": 0.76,
        "sampleCount": 6
      }
    },
    "datasource": {
      "poolUsageRatio": {
        "latest": 0.82,
        "max": 0.97,
        "avg": 0.88,
        "sampleCount": 6
      }
    }
  }
}
```

`latest`는 bucket 안에서 가장 늦게 관측된 valid sample, `max`는 bucket 안의 valid sample 최댓값, `avg`는 valid sample 산술 평균, `sampleCount`는 평균 계산에 사용한 valid sample 수다. Portal이 여러 instance bucket을 병합할 때 `max`는 instance max의 최댓값을 사용하고, `avg`는 `sampleCount` 기반 weighted average로 계산한다.

이 후보는 raw runtime sample 배열을 보내는 기능이 아니다. Payload에는 aggregate 결과만 남기며, arbitrary custom metric map이나 high-cardinality tag를 함께 열지 않는다.

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

Post-MVP runtime aggregate schema를 열 경우에는 각 ratio aggregate에 대해 `0 <= latest <= max <= 1`, `0 <= avg <= max`, `sampleCount > 0`을 검증한다. 특정 metric aggregate가 없을 수는 있지만, 한 metric을 보낼 때는 `latest`, `max`, `avg`, `sampleCount`가 함께 있어야 한다. 이 검증은 starter local builder와 portal `IngestAcceptanceService` 양쪽에 둔다.

## 5. Idempotency

starter는 동일 application/environment/instance/`bucket.startUtc` 조합에 대해 두 번째 envelope 후보를 만들지 않는다. idempotency key는 drain/flush 시각이 아니라 `bucket.startUtc` 기준으로 deterministic하게 만든다. header delimiter ambiguity를 피하기 위해 key component 안에는 `:` 또는 제어문자를 허용하지 않는다.

동일 project/application/environment/instance/bucket start에 대해 같은 payload가 재전송되면 portal은 중복 저장하지 않고 성공으로 응답한다.

동일 idempotency key에 다른 payload hash가 들어오면 `409 Conflict`로 처리한다.

portal idempotency는 재전송/duplicate 안전망이지, starter duplicate flush 설계의 primary mechanism이 아니다. starter는 same `bucket.startUtc` duplicate flush candidate 방지를 downstream idempotency conflict나 portal duplicate handling에 의존하지 않는다.

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
