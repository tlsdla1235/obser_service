---
artifactType: api-surface
projectName: Spring Boot 운영 첫 화면 포털
architectureStyle: Lightweight Hexagonal
status: draft-for-ir
date: 2026-05-08
---

# API Surface

## 1. 목적과 경계

MVP API surface는 first-screen delivery에 필요한 것만 둔다.

필수 API:

- starter -> portal direct ingest API
- dashboard UI -> portal read model query API

Bootstrap surface:

- project key와 application metadata를 만들 수 있는 경로는 필요하다.
- 다만 MVP 제품 surface로 public onboarding API를 열 필요는 없다.
- 권장 시작점은 local/dev seed 또는 internal admin API다.

Controller는 모두 `adapter.in.web`에 속한다. Controller는 request/response 변환과 HTTP status mapping만 수행하고, application use case에 위임한다.

## 2. Ingest API

### 2.1 Endpoint

```http
POST /api/ingest/v1/buckets
X-OBS-Project-Key: <project-key>
Idempotency-Key: <project-id>:<application>:<environment>:<instance>:<bucket-start-utc>
Content-Type: application/json
```

### 2.2 Request Rough Shape

`planning-artifacts/contracts/ingest-envelope.md`의 payload shape를 따른다.

요약:

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
      { "leMs": 50, "count": 500 }
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
        { "leMs": 50, "count": 50 }
      ]
    }
  ]
}
```

### 2.3 Response Rough Shape

새 bucket 수용:

```http
201 Created
Content-Type: application/json
```

```json
{
  "status": "accepted",
  "duplicate": false,
  "bucketId": "0cbb0d8c-5e33-44e0-90a8-77a9648a8e9e",
  "acceptedAt": "2026-05-08T01:00:31Z"
}
```

동일 idempotency key와 동일 payload hash 재전송:

```http
200 OK
```

```json
{
  "status": "accepted",
  "duplicate": true,
  "bucketId": "0cbb0d8c-5e33-44e0-90a8-77a9648a8e9e",
  "acceptedAt": "2026-05-08T01:00:31Z"
}
```

동일 idempotency key와 다른 payload hash:

```http
409 Conflict
```

```json
{
  "error": "idempotency_conflict",
  "message": "Same idempotency key was already accepted with a different payload."
}
```

대표 status mapping:

| Status | 조건 |
|---|---|
| `201` | 새 bucket 수용 |
| `200` | 동일 payload 중복 수용 |
| `400` | schema version, bucket boundary, metric taxonomy, histogram validation 실패 |
| `401` | project key 누락 또는 검증 실패 |
| `409` | 동일 idempotency key에 다른 payload |
| `500` | persistence 등 portal 내부 실패 |

### 2.4 Hexagonal Boundary

- `IngestController`는 header/body를 `AcceptIngestEnvelopeCommand`로 변환한다.
- `AcceptIngestEnvelopeUseCase`가 project key 검증, payload validation, idempotency 판단, catalog upsert, bucket 저장을 orchestration한다.
- `MetricBucketStorePort`와 `ApplicationCatalogPort`는 outbound port다.
- Controller, persistence adapter, DB는 lifecycle state, p95, insight rule을 계산하지 않는다.

## 3. Dashboard Query API

### 3.1 Endpoint

```http
GET /api/projects/{projectId}/applications/{applicationId}/dashboard
Accept: application/json
```

MVP에서는 `applicationId`가 environment를 포함한 application row를 가리킨다. 별도 `environment` query parameter를 추가하지 않는다.

### 3.2 Response Rough Shape

`planning-artifacts/contracts/read-model-contract.md`의 response shape를 그대로 반환한다.

요약:

```json
{
  "generatedAt": "2026-05-08T01:10:35Z",
  "application": {
    "name": "orders-api",
    "environment": "prod",
    "lastAcceptedIngestAt": "2026-05-08T01:10:30Z",
    "lastHealthyAt": "2026-05-08T01:08:30Z",
    "sourceWindow": {
      "current": {
        "startUtc": "2026-05-08T00:55:30Z",
        "endUtc": "2026-05-08T01:10:30Z"
      },
      "baseline": {
        "startUtc": "2026-05-08T00:40:30Z",
        "endUtc": "2026-05-08T00:55:30Z"
      }
    },
    "freshness": {
      "lastObservedAt": "2026-05-08T01:10:30Z",
      "staleAt": "2026-05-08T01:12:00Z",
      "downAt": "2026-05-08T01:13:30Z"
    }
  },
  "state": {
    "code": "degraded",
    "label": "확인이 필요합니다",
    "rationale": "오류율과 p95가 함께 증가했습니다.",
    "recommendedAction": "먼저 POST /orders endpoint의 최근 변경과 외부 의존성을 확인하세요.",
    "scope": "application"
  },
  "zeroInsight": null,
  "recovery": {
    "isRecovering": false,
    "lastRecoveredAt": null,
    "retryAfterSeconds": null,
    "recommendedAction": null
  },
  "metrics": {
    "requestCount": 42000,
    "errorRate": 0.031,
    "p95Ms": 480,
    "p95Source": "server_histogram_merge"
  },
  "triageCards": [],
  "endpointPriority": []
}
```

대표 status mapping:

| Status | 조건 |
|---|---|
| `200` | read model 반환 |
| `404` | project 또는 application 없음 |
| `500` | snapshot 생성/조회 실패 |

### 3.3 Snapshot 생성 정책

MVP에서는 둘 중 하나로 시작한다.

1. ingest 수용 직후 current window snapshot을 동기 또는 commit 후 in-process로 refresh한다.
2. dashboard query 시 최신 snapshot이 없거나 stale하면 `QueryDashboardSnapshotUseCase`가 application 안에서 재생성한다.

둘 중 어느 쪽이든 `QueryDashboardSnapshotUseCase`가 read model source of truth다. DB view나 frontend는 대체 source가 아니다.

### 3.4 Hexagonal Boundary

- `DashboardQueryController`는 path variable을 `QueryDashboardSnapshotQuery`로 변환한다.
- `QueryDashboardSnapshotUseCase`가 current/baseline window, freshness, state, p95, triage cards, endpoint priority를 구성한다.
- UI는 이 응답을 소비할 뿐 state/rule/p95/endpoint priority를 재계산하지 않는다.

## 4. Project/Application Bootstrap Surface

### 4.1 필요 여부 판단

MVP ingest에는 project key가 필요하므로 bootstrap 경로는 필요하다. 하지만 사용자-facing 제품 API로 먼저 만들 필요는 없다.

권장 순서:

1. local/dev seed로 `projects`와 project key hash를 만든다.
2. application과 instance는 첫 accepted ingest에서 `ApplicationCatalogPort`가 upsert한다.
3. dashboard가 application 선택 목록이 필요해지면 read-only application list API를 추가한다.
4. public project creation/onboarding API는 MVP 이후로 미룬다.

### 4.2 Internal Admin API 후보

seed만으로 demo가 불편하면 internal/admin profile에서만 아래 API를 둘 수 있다.

```http
POST /api/admin/projects
Content-Type: application/json
```

```json
{
  "name": "local-demo"
}
```

```json
{
  "projectId": "0fcf1d62-c5f2-43bd-85f7-2dd6c9e458b1",
  "projectKey": "obs_live_xxx",
  "keyPrefix": "obs_live"
}
```

주의:

- raw `projectKey`는 생성 응답에서만 보여주고 DB에는 hash만 저장한다.
- 이 API는 MVP public product scope가 아니다.
- 인증/권한 체계를 크게 만들지 않는다. local/internal profile 또는 운영자-only 경로로 제한한다.

### 4.3 Application List API 후보

dashboard 첫 화면 진입을 위해 application 선택이 필요하면 read-only API 하나만 추가한다.

```http
GET /api/projects/{projectId}/applications
Accept: application/json
```

```json
{
  "applications": [
    {
      "applicationId": "5c942671-e251-4f7f-b610-18ae6ca4ef65",
      "name": "orders-api",
      "environment": "prod",
      "lastSeenAt": "2026-05-08T01:10:31Z",
      "status": "active"
    }
  ]
}
```

Boundary:

- 이 API는 catalog 조회 adapter다.
- state, rule, p95, endpoint priority를 포함하지 않는다.
- first-screen 판단은 dashboard query API에서만 가져온다.

## 5. Static Dashboard Surface

MVP portal은 dashboard static asset을 같은 runtime에서 제공한다.

후보 route:

```http
GET /dashboard
GET /dashboard/*
```

원칙:

- static route는 API가 아니다.
- dashboard UI는 `GET /api/projects/{projectId}/applications/{applicationId}/dashboard` 응답을 그대로 렌더링한다.
- polling refresh는 허용한다.
- WebSocket/SSE는 MVP 필수로 만들지 않는다.

## 6. API Non-Goals

- Prometheus scrape endpoint 또는 PromQL proxy
- arbitrary metric query API
- custom metric registration API
- logs/traces query API
- dashboard builder API
- multi-tenant admin/billing API
- endpoint explorer/search API
- frontend-only state/rule/p95 계산 API 우회

## 7. IR 전 확인할 API 결정

- ingest duplicate response를 `200`으로 할지 `202`로 할지 최종 선택한다. 이 문서는 `200`을 권장한다.
- project bootstrap을 seed-only로 둘지 internal admin API를 둘지 결정한다.
- dashboard snapshot refresh를 ingest 후 즉시 할지 query 시점 lazy refresh로 둘지 결정한다.
- application list API가 첫 demo에 필요한지 확인한다.

