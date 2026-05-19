---
artifactType: api-surface
projectName: Spring Boot 운영 첫 화면 포털
architectureStyle: Traditional MVC
status: mvc-version-generated
date: 2026-05-09
---

# API Surface - MVC Version

## 1. 목적과 경계

MVP API surface는 first-screen delivery에 필요한 것만 둔다.

필수 API:

- portal 사용자 account signup/login API: GitHub OAuth only
- starter -> portal direct ingest API
- dashboard UI -> portal read model query API

Bootstrap surface:

- project key와 application metadata를 만들 수 있는 경로는 필요하다.
- 다만 MVP 제품 surface로 public onboarding API를 열 필요는 없다.
- 권장 시작점은 local/dev seed 또는 internal admin API다.
- 사용자 계정 생성은 public project creation API와 별개이며, MVP에서는 GitHub OAuth 성공을 통해서만 내부 account를 만들거나 연결한다.

MVC 기준에서 controller는 모두 `controller` package에 속한다. Controller는 request/response 변환과 HTTP status mapping만 수행하고 service에 위임한다.

## 2. Ingest API

### 2.1 Endpoint

```http
POST /api/ingest/v1/buckets
X-OBS-Project-Key: <project-key>
Idempotency-Key: <project-id>:<application>:<environment>:<instance>:<bucket-start-utc-basic>
Content-Type: application/json
```

`Idempotency-Key` component는 delimiter `:`와 제어문자를 포함하지 않는다. identity component는
`A-Z`, `a-z`, `0-9`, `.`, `_`, `-`만 허용하고, `bucket-start-utc-basic`은
`yyyyMMdd'T'HHmmss'Z'` 형식의 UTC bucket start다.

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

### 2.4 MVC Boundary

- `IngestController`는 header/body를 request DTO로 변환한다.
- `IngestAcceptanceService`가 project key 검증, payload validation, idempotency 판단, catalog upsert, bucket 저장을 orchestration한다.
- `MetricBucketRepository`와 `ApplicationRepository`는 repository layer다.
- Controller, repository, DB는 lifecycle state, p95, insight rule을 계산하지 않는다.

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
2. dashboard query 시 최신 snapshot이 없거나 stale하면 `DashboardReadModelService`가 service layer 안에서 재생성한다.

둘 중 어느 쪽이든 `DashboardReadModelService`가 read model source of truth다. DB view나 frontend는 대체 source가 아니다.

Dashboard current response에는 큰 history 배열을 넣지 않는다. Snapshot identifier와 detail link가 필요하면 `read-model-contract.md`의 `snapshot` 경계만 사용한다.

### 3.4 MVC Boundary

- `DashboardController`는 path variable을 query DTO로 변환한다.
- `DashboardReadModelService`가 current/baseline window, freshness, state, p95, triage cards, endpoint priority를 구성한다.
- UI는 이 응답을 소비할 뿐 state/rule/p95/p99/endpoint priority를 재계산하지 않는다.

## 4. Epic 5/6 Candidate - Operational Event History API

이 section은 Epic 5/6 착수 전 구현 기준 후보를 문서화한다. Epic 2와 Epic 3의 현재 구현 대상이 아니다.

### 4.1 Operational Events Endpoint

```http
GET /api/projects/{projectId}/applications/{applicationId}/operational-events?since=24h&limit=50
Accept: application/json
```

이 API는 raw bucket explorer, raw snapshot explorer, arbitrary time-series query가 아니다. `operational-event-history.md` contract에 따라 state transition, stale/down/recovery, high-confidence concern만 bounded event로 반환한다.

Rough response:

```json
{
  "generatedAt": "2026-05-14T08:30:00Z",
  "applicationId": "5c942671-e251-4f7f-b610-18ae6ca4ef65",
  "horizon": {
    "since": "2026-05-13T08:30:00Z",
    "limit": 50
  },
  "events": []
}
```

### 4.2 Snapshot Detail Endpoint

```http
GET /api/projects/{projectId}/applications/{applicationId}/dashboard/snapshots/{snapshotId}
Accept: application/json
```

Snapshot detail은 해당 snapshot에 저장된 read model을 반환하는 후보 API다. Current state를 재판정하지 않고, history event를 UI나 controller에서 새로 계산하지 않는다.

대표 status mapping:

| Status | 조건 |
|---|---|
| `200` | 저장된 snapshot read model 반환 |
| `404` | snapshot이 없거나 retention으로 삭제됨 |
| `500` | snapshot 조회 실패 |

### 4.3 Candidate MVC Boundary

- `OperationalEventHistoryService` 후보가 `DashboardSnapshotRepository`를 재사용해 bounded event read model을 만든다.
- 별도 `OperationalEventRepository`와 `operational_events` table은 MVP에서 만들지 않는다.
- UI는 event 목록과 snapshot deep link를 표시할 뿐 state/rule/p95/p99/endpoint priority를 재계산하지 않는다.
- Alert delivery log와 operational event history를 같은 API로 섞지 않는다.

## 5. Project/Application Bootstrap Surface

### 5.1 필요 여부 판단

MVP ingest에는 project key가 필요하므로 bootstrap 경로는 필요하다. 하지만 사용자-facing 제품 API로 먼저 만들 필요는 없다.

권장 순서:

1. local/dev seed로 `projects`와 project key hash를 만든다.
2. application과 instance는 첫 accepted ingest에서 `ApplicationCatalogService`가 upsert한다.
3. dashboard가 application 선택 목록이 필요해지면 read-only application list API를 추가한다.
4. public project creation/onboarding API는 MVP 이후로 미룬다.

### 5.2 Internal Admin API 후보

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

### 5.3 Application List API 후보

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

- 이 API는 catalog 조회 controller + service + repository 경로다.
- state, rule, p95, endpoint priority를 포함하지 않는다.
- first-screen 판단은 dashboard query API에서만 가져온다.

## 6. Static Dashboard Surface

MVP portal은 dashboard static asset을 같은 runtime에서 제공한다.

후보 route:

```http
GET /dashboard
GET /dashboard/*
```

Static asset serving은 state/rule 판단을 하지 않는다. dashboard 화면은 API read model을 그대로 표시한다.

## 7. Product Account Auth Surface

이 surface는 portal 사용자 account signup/login 기준이다. Starter ingest의 `X-OBS-Project-Key` 인증과 섞지 않는다.

### 7.1 Policy

- Account signup은 GitHub OAuth only다.
- Login도 MVP에서는 GitHub OAuth로 생성되었거나 연결된 account에만 허용한다.
- GitHub OAuth 성공 후 내부 `user/account` row를 생성하거나 기존 GitHub identity와 연결한다.
- GitHub user id 또는 provider subject를 외부 identity의 stable key로 사용한다.
- email/password signup, local account registration, local password, password reset, email verification required for signup, magic link, multiple OAuth providers, Google/Kakao/Naver OAuth, anonymous user flow는 MVP에서 지원하지 않는다.

### 7.2 Candidate Endpoints

```http
GET /api/auth/github/authorize
GET /api/auth/github/callback?code=<code>&state=<state>
POST /api/auth/token/refresh
Authorization: Bearer <refresh-token>
POST /api/auth/logout
Authorization: Bearer <access-token>
```

`/authorize`는 GitHub OAuth 시작점이고, `/callback`은 GitHub OAuth 성공 결과를 내부 account와 연결하는 boundary다. OAuth 실패나 취소는 계정을 생성하지 않는다.

`/token/refresh`는 Refresh Token을 Bearer token으로 받아 rotation, 만료, revoke, reuse detection을 적용한다. Refresh Token 저장소는 `token store` 추상으로 두며, 초기 구현 후보는 RDBMS에 hashed refresh token 또는 token family metadata를 저장하는 방식이다. Redis는 고성능 revoke list, distributed token state, reuse detection 최적화가 필요해질 때 후속 선택지로 둔다.

### 7.3 Response / Log Boundary

- MVP 인증은 cookie 기반 server session을 사용하지 않는다.
- API 요청 인증은 `Authorization: Bearer <access_token>` header를 사용한다.
- Access Token은 stateless하게 검증 가능한 짧은 만료 JWT다.
- GitHub OAuth token과 우리 서비스 access token/refresh token을 구분한다.
- MVP에서 GitHub API 호출이 필요 없다면 GitHub OAuth token을 저장하지 않는다.
- Controller/API response, log, error에는 GitHub OAuth token, provider raw payload, secret을 노출하지 않는다.
- 일반 resource API response, log, error에는 access token, refresh token도 노출하지 않는다.
- Token issuance/refresh response에서 우리 서비스 access token/refresh token을 어떤 channel로 전달할지는 구현 story에서 별도 승인 기준으로 닫는다.
- Signup/login 실패 메시지는 provider 내부 오류, raw payload, token 상태를 과도하게 드러내지 않는 일반화된 메시지로 둔다.

상세 acceptance는 `planning-artifacts/contracts/account-auth-policy.md`를 따른다.
