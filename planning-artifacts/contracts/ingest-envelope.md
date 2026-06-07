---
artifactType: contract
name: ingest-envelope
architectureStyle: Traditional MVC
status: mvc-version-generated
date: 2026-05-09
---

# Contract - Ingest Envelope MVC Version

## 1. 역할

`ingest-envelope`는 starter와 portal 사이의 metric data accepted bucket 및 starter canonical percentile source of truth다.

이 계약은 pull-based scrape/query 방식을 MVP 필수 경로에서 제외하고, portal service layer가 lifecycle state, triage summary, endpoint priority, distribution display를 구성할 수 있는 최소 bounded payload만 허용한다. p95/p99 값 자체는 `summary.localPercentiles.p95Ms`/`p99Ms`로 starter가 보고한 값을 canonical source로 삼는다.

Starter heartbeat는 이 계약의 metric ingest가 아니다. 주기적 starter heartbeat는 `POST /api/ingest/v1/heartbeat`의 별도 control-plane/liveness 신호로 다루며, starter/application process liveness, portal reachability, project key validity, metadata validity를 표현한다. Heartbeat 성공만으로 accepted bucket, host business health, dashboard snapshot, operational event, p95/p99, rule/read-model calculation을 생성하거나 암시하지 않는다. 최근 heartbeat는 snapshot 저장 eligibility gate로만 사용할 수 있다.

accepted bucket이 없거나 오래된 것은 "최근 metric data가 없다"는 뜻이다. 사용자 서버에 요청이 없어 bucket이 안 온 상황을 host application down으로 해석하지 않는다. Heartbeat가 최근 수신됐다면 후속 read model은 이 조합을 `starter connected but no accepted bucket`, `waiting for traffic`, `metric data idle` 계열로 표현할 수 있다.

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
    },
    "localPercentiles": {
      "scope": "instance_bucket",
      "source": "starter_local",
      "bucketStartUtc": "2026-05-08T01:00:00Z",
      "bucketEndUtc": "2026-05-08T01:00:30Z",
      "requestCount": 1200,
      "p95Ms": 640,
      "p99Ms": 1800,
      "mergeable": false
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

### 3.1 Starter Canonical Percentile

`summary.localPercentiles`는 해당 instance의 해당 30초 bucket에서 starter가 직접 산출해 보고한 canonical p95/p99 payload다. 필드명은 기존 ingest 후보와의 호환성 때문에 `localPercentiles`로 유지하지만, 의미는 단순 참고값이 아니라 `starter-reported percentile` 또는 `starter canonical percentile`이다.

- `scope`는 `instance_bucket`으로 고정한다.
- `source`는 `starter_local`로 고정한다.
- `bucketStartUtc`와 `bucketEndUtc`는 같은 envelope의 30초 bucket boundary와 일치해야 한다.
- `requestCount`는 해당 starter canonical percentile point를 만든 요청 수다.
- `p95Ms`와 `p99Ms`는 해당 30초 bucket 안의 starter-reported canonical value다.
- `mergeable=false`는 여러 starter instance나 여러 bucket의 p95/p99 숫자를 평균/병합해 새로운 상위 scope p95/p99를 만들지 않는다는 뜻이다. 해당 `instance_bucket` scope에서는 그대로 canonical 값이다.

Portal은 이 값을 `accepted_metric_buckets.local_percentiles_json`에 보존하고 동일 scope의 p95/p99 source로 노출할 수 있다. 같은 화면/같은 scope에서 starter-reported p95/p99를 표시하면 histogram-derived p95/p99를 병렬 표시하지 않는다.

상위 scope에서 여러 starter instance의 p95/p99가 서로 다르면 portal은 임의 평균, 최댓값 선택, histogram-derived 재계산으로 새로운 p95/p99를 만들지 않는다. 이 경우 read model은 instance/source 단위로 starter-reported percentile을 분리해 보여주거나, 상위 scope에는 percentile scalar 대신 histogram bucket distribution을 표시한다.

Endpoint payload의 `durationBuckets`는 endpoint 상세 bucket distribution 표시와 진단 원자료다. Endpoint별 p95/p99 계산, endpoint percentile rollup, endpoint percentile judgment, endpoint p99 alert 기준은 만들지 않는다.

### 3.2 Post-MVP Runtime Aggregate Payload Candidate

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
- endpoint `route`는 `route-attribution-policy.md`가 허용한 safe route template, safe prefix collapse 결과, configured allowlist template, 또는 `UNKNOWN`이어야 한다.
- raw path candidate, 실제 query string/key/value, high-cardinality tag, attribution source raw detail은 지원 payload shape에 존재할 수 없다.
- endpoint 항목은 이미 정규화된 route 기준의 bounded top-N 또는 허용 route set 안에 있어야 한다. 이 제한은 출력 cardinality cap이며 route attribution fallback으로 사용하지 않는다.
- histogram bucket은 cumulative count를 사용한다.
- `localPercentiles`가 존재하면 `scope=instance_bucket`, `source=starter_local`, `mergeable=false`, envelope와 동일한 30초 boundary를 만족해야 한다.
- `localPercentiles.p95Ms`/`p99Ms`는 해당 `instance_bucket` scope의 starter canonical p95/p99 field다.
- `localPercentiles.p95Ms`/`p99Ms` 숫자끼리 평균/병합해 app/project/window p95/p99를 만들지 않는다.
- 자유 tag map, arbitrary custom metric map, raw timeseries 배열은 지원 payload field로 정의하지 않는다.

### 4.1 Unsupported Unknown Field Handling

Portal ingest는 forward compatibility를 위해 JSON unknown field를 거부하지 않고 무시한다. 예를 들어 `customMetrics`, `tags`, `rawTimeseries`, 또는 future unsupported field가 포함되어도 지원 envelope field가 유효하면 request는 counting/acceptance 대상으로 남는다.

무시된 field는 metric taxonomy 확장, aggregation 입력, route attribution, idempotency payload identity, persisted accepted metric 후보에 반영하지 않는다. 즉, unknown field를 받는 것은 semantic acceptance가 아니라 "지원하지 않는 입력을 읽지 않고 버린다"는 boundary 정책이다. 지원 field 자체가 잘못된 경우, 예를 들어 `schemaVersion`이 `1.0`이 아니거나 endpoint `route`에 실제 query key/value, unsupported query suffix, raw identifier가 남아 있는 경우에는 기존 validation rule대로 reject한다. `?...`는 route attribution policy가 허용한 bounded omission marker일 때만 query string으로 보지 않는다.

`localPercentiles`는 unknown field가 아니라 지원 후보 field다. 이 field가 유효하면 request는 accepted bucket 후보로 남고, field 자체가 잘못됐을 때만 payload validation 실패로 다룬다.

Post-MVP runtime aggregate schema를 열 경우에는 각 ratio aggregate에 대해 `0 <= latest <= max <= 1`, `0 <= avg <= max`, `sampleCount > 0`을 검증한다. 특정 metric aggregate가 없을 수는 있지만, 한 metric을 보낼 때는 `latest`, `max`, `avg`, `sampleCount`가 함께 있어야 한다. 이 검증은 starter local builder와 portal `IngestAcceptanceService` 양쪽에 둔다.

### 4.2 Story 4.0 Validation Review Scope

Story 4.0 이후 리뷰/검토에서 portal ingest validation을 다시 열 때도, 기존 ingest envelope field들에 대해서는 추가 안정성 검증을 새로 요구하지 않는다.

기존 field란 Story 3.x에서 이미 구현/수용된 `schemaVersion`, `application`, `bucket`, `summary.requestCount/errorCount`, `summary.httpServerDurationBuckets`, `summary.jvm`, `summary.datasource`, `endpoints`, `idempotencyKey` 계열을 말한다. 이 field들은 현재 MVP acceptance 수준을 baseline으로 삼고, edge-case review가 추가 hardening을 발견하더라도 Story 4.0 또는 다음 sprint planning의 blocker로 삼지 않는다.

검증 검토 대상은 Story 4.0으로 의미가 바뀌거나 새로 추가되는 field/contract에 한정한다. 예시는 `summary.localPercentiles`, heartbeat request/response 후보, source/scope/display policy enum, read model shape다.

수정/추가 field 검증도 저장 가능성, source/scope 의미 보존, 보안/권한, null/필수값, 기존 read model 오해 방지 수준으로 제한한다. 이 검증을 이유로 기존 histogram, endpoint, route, count, idempotency, runtime ratio에 대한 추가 검증을 재개하지 않는다.

기존 ingest field의 더 강한 안정성 검증이 필요하다고 판단되면 Story 4.0 범위 밖의 deferred hardening item으로 기록하고, MVP 진행을 막지 않는다.

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
- Controller와 repository는 lifecycle state, p95/p99, insight rule을 계산하지 않는다.
- Heartbeat endpoint가 추가되더라도 bucket ingest controller/service와 의미를 섞지 않는다.

## 7. Non-Goals

- pull exposition format 지원
- arbitrary query를 위한 raw metric 저장
- route attribution raw 후보로 사용된 `uri`/`path`나 query 저장
- ignored unknown field 저장 또는 metric taxonomy 반영
- high-cardinality label 검색
- user-defined custom metric ingestion
- heartbeat를 metric bucket으로 취급하는 것
- heartbeat 성공만으로 accepted bucket, dashboard snapshot, operational event, p95/p99, rule/read model 계산을 만드는 것
- heartbeat를 accepted bucket freshness source나 host business health/degraded 판단 근거로 사용하는 것
- `localPercentiles` 값으로 app/project/window p95/p99 rollup을 만드는 것
- endpoint별 p95/p99 계산, endpoint percentile rollup, endpoint p99 alert 기준을 만드는 것
