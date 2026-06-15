---
artifactType: api-surface
projectName: Spring Boot 운영 첫 화면 포털
architectureStyle: Traditional MVC
status: account-project-membership-updated
date: 2026-05-31
---

# API Surface - MVC Version

## 1. 목적과 경계

MVP API surface는 first-screen delivery에 필요한 것만 둔다.

필수 API:

- portal 사용자 account signup/login API: GitHub OAuth only
- starter -> portal direct ingest API
- starter -> portal heartbeat API
- dashboard UI -> portal read model query API

Epic 5/6 dashboard flow 기준으로 dashboard UI query surface는 Project Entry, Application List, Application Dashboard, Instance Detail, Instance Snapshot Trend, Snapshot/History 순서로 사용된다. Project/Application navigation은 scope 선택과 light summary를 위한 surface이며, Application Dashboard가 primary first-screen이다.

Project resource API는 `Authorization: Bearer <access_token>`으로 인증된 account id를 기준으로 동작한다.
`GET /api/projects` collection read는 path project id가 없으므로 account-scoped active membership project 목록만 반환하고,
public onboarding `POST /api/projects`는 membership을 새로 만드는 생성 경로다. `/api/projects/{projectId}/...` project-scoped
resource API는 인증된 account id와 `account_project_memberships.status='active'` membership을 함께 확인한다.
no-token/invalid-token/expired-token은 `401 Unauthorized`와 `WWW-Authenticate: Bearer`로 처리하고, membership mismatch는
project 존재 여부를 드러내지 않도록 `404 Not Found`로 fail-closed한다.

Bootstrap surface:

- project key와 application metadata를 만들 수 있는 경로는 필요하다.
- Story 9.2 Stage 1부터 public onboarding project registration API를 연다.
- local/dev seed 또는 internal admin API는 operator smoke/bootstrap 경로로 유지하고 public onboarding API와 섞지 않는다.
- 사용자 계정 생성은 public project creation API와 별개이며, MVP에서는 GitHub OAuth 성공을 통해서만 내부 account를 만들거나 연결한다.
- Resource API 인증은 `Authorization: Bearer <access_token>` service token 기준이고, starter ingest 인증은 `X-OBS-Project-Key` starter credential 기준이다.

MVC 기준에서 controller는 모두 `controller` package에 속한다. Controller는 request/response 변환과 HTTP status mapping만 수행하고 service에 위임한다.

## 2. Ingest API

Ingest bucket API는 accepted bucket data plane이다. Starter heartbeat는 이 API와 분리된 control-plane/liveness signal이며, bucket 수용이나 metric dashboard state를 뜻하지 않는다. Dashboard/read model은 두 축을 함께 보여줄 수 있지만 source-of-truth를 섞지 않는다.

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
- Controller, repository, DB는 lifecycle state, p95/p99, insight rule을 계산하지 않는다.

### 2.5 Starter Heartbeat Endpoint

```http
POST /api/ingest/v1/heartbeat
X-OBS-Project-Key: <project-key>
Content-Type: application/json
```

Heartbeat는 starter가 주기적으로 portal 도달성, project key 유효성, schema version, `application/environment/instance` metadata shape, starter/application process liveness를 알리는 control-plane 신호다.

Request 후보:

```json
{
  "schemaVersion": "1.0",
  "starterVersion": "0.1.0",
  "heartbeat": {
    "sentAtUtc": "2026-05-22T08:30:00Z",
    "sequence": 42,
    "intervalSeconds": 30
  },
  "application": {
    "name": "orders-api",
    "environment": "prod",
    "instance": "orders-api-7f9c9c8c9d-x2p4k"
  }
}
```

Response 후보:

```json
{
  "status": "received",
  "projectId": "0fcf1d62-c5f2-43bd-85f7-2dd6c9e458b1",
  "serverTimeUtc": "2026-05-22T08:30:00Z",
  "supportedIngestSchemaVersions": ["1.0"],
  "metadataStatus": "valid",
  "heartbeatStatus": "recorded",
  "ingestBoundary": {
    "lastAcceptedBucketAt": null,
    "statusSource": "accepted_bucket"
  },
  "message": "Starter heartbeat was received. No metric bucket has been accepted yet."
}
```

대표 status mapping:

| Status | 조건 |
|---|---|
| `200` | heartbeat 수신, project key 검증, metadata shape 검증 성공 |
| `400` | schema version 또는 `application/environment/instance` shape 실패 |
| `401` | project key 누락 또는 검증 실패 |
| `403` | 인증은 됐지만 ingest/heartbeat를 허용할 수 없는 project 상태 후보 |
| `500` | portal 내부 실패 |

Boundary:

- heartbeat 성공만으로 accepted bucket, host business health, dashboard snapshot, operational event, p95/p99, rule/read-model calculation을 생성하거나 암시하지 않는다. 최근 heartbeat는 snapshot 저장 eligibility gate로만 사용할 수 있다.
- heartbeat 미수신은 host application down 판정이 아니다.
- heartbeat는 application/instance catalog upsert source가 아니다. 첫 accepted bucket이 catalog upsert source다.
- heartbeat telemetry를 저장하더라도 `lastHeartbeatAt`, `lastHeartbeatStatus`, failure category 같은 lightweight connection field로 제한한다.
- UI에 표시할 경우 starter heartbeat/connection status와 accepted bucket freshness/application state를 분리한다.
- heartbeat retry/logging/idempotency 의미는 bucket ingest retry/logging/idempotency 의미와 섞지 않는다.

Expected read semantics:

| Heartbeat | Accepted bucket | API/read-model 표현 후보 |
|---|---|---|
| 최근 수신 | 없음/오래됨 | `starterConnection.connectionMeaning=starter_connected`, `zeroInsight.reasonCode=waiting_first_data` 또는 `metric_data_idle`, message 후보 `Starter connected; waiting for traffic.` |
| 없음/오래됨 | 없음/오래됨 | `starterConnection.connectionMeaning=starter_disconnected` 또는 `unknown`, message 후보 `Telemetry unreachable; application status not confirmed.` |
| 없음/오래됨 | 최근 있음 | metric freshness는 current로 유지하되 starter connection warning을 별도 field로 표현 |

## 3. Dashboard Query API

### 3.1 Endpoint

```http
GET /api/projects/{projectId}/applications/{applicationId}/dashboard
Authorization: Bearer <access_token>
Accept: application/json
```

MVP에서는 `applicationId`가 environment를 포함한 application row를 가리킨다. 별도 `environment` query parameter를 추가하지 않는다.

### 3.2 Response Rough Shape

`planning-artifacts/contracts/read-model-contract.md`의 response shape를 그대로 반환한다.

Dashboard triage card는 기본적으로 confidence `>= 0.65` candidate만 노출한다. 이 기준은 operational history event 승격 기준과 다르며, UI는 confidence나 rule을 다시 계산하지 않는다.

요약:

```json
{
  "generatedAt": "2026-05-08T01:10:35Z",
  "application": {
    "name": "orders-api",
    "environment": "prod",
    "lastAcceptedBucketAt": "2026-05-08T01:10:30Z",
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
    "rationale": "오류율과 source-scoped starter-reported p95가 함께 증가했습니다.",
    "recommendedAction": "먼저 POST /orders endpoint의 최근 변경과 외부 의존성을 확인하세요.",
    "scope": "application"
  },
  "zeroInsight": null,
  "recovery": {
    "isRecovering": false,
    "lastHealthyAt": null,
    "retryAfterSeconds": null,
    "recommendedAction": null
  },
  "starterConnection": {
    "statusSource": "starter_heartbeat",
    "lastHeartbeatAt": "2026-05-08T01:10:20Z",
    "lastHeartbeatStatus": "received",
    "connectionMeaning": "starter_connected",
    "stateImpact": "none"
  },
  "metrics": {
    "requestCount": 42000,
    "errorRate": 0.031
  },
  "sourceScopedPercentiles": {
    "source": "starter_canonical_percentile",
    "scope": "instance_bucket",
    "displayPolicy": "source_scoped_points",
    "aggregatePolicy": "no_average_no_max_no_merge_no_histogram_recalculation",
    "items": [
      {
        "application": "orders-api",
        "environment": "prod",
        "instance": "orders-api-7f9c9c8c9d-x2p4k",
        "bucketStartUtc": "2026-05-08T01:10:00Z",
        "bucketEndUtc": "2026-05-08T01:10:30Z",
        "requestCount": 1200,
        "p95Ms": 480,
        "p99Ms": 960
      }
    ],
    "applicationScopeFallback": "bucket_distribution_only_when_multiple_sources"
  },
  "triageCards": [],
  "endpointPriority": []
}
```

대표 status mapping:

| Status | 조건 |
|---|---|
| `200` | read model 반환 |
| `401` | Bearer access token 없음/invalid/expired |
| `404` | active account-project membership 없음, project 또는 application 없음 |
| `500` | snapshot 생성/조회 실패 |

### 3.3 Snapshot 생성 정책

MVP snapshot 정책은 아래로 고정한다.

1. application별 `1시간 scheduled snapshot`이 `DashboardReadModelService`의 전체 read model 결과를 `dashboard_snapshots`에 저장한다.
2. 의미 있는 `state_code` 변화, confidence `>= 0.82` high-confidence concern, 짧지만 강한 spike 실험값(confidence `>= 0.90` + 최근 5개 30초 bucket 중 2개 이상 bad), dashboard query fallback regeneration 조건에서만 추가 snapshot row를 남긴다.
3. dashboard query 시 최신 snapshot이 없거나 current response로 쓰기에 명백히 오래된 경우 `DashboardReadModelService`가 service layer 안에서 fallback으로 현재 read model을 재생성하고, 필요하면 snapshot으로 저장할 수 있다.
4. 중요한 state 변화와 high-confidence concern 구간은 `dashboard_snapshots.state_code`, `generated_at`, `current_window_end_utc`, `read_model_json` 또는 필요한 bounded index/search helper column을 기반으로 history surface에서 파생/검색한다.

Ingest 수용 직후에는 `accepted_metric_buckets`와 catalog/idempotency state만 저장한다. 30초 bucket마다 dashboard snapshot을 동기 refresh하거나 commit 후 in-process refresh하지 않는다.

`dashboard_snapshots` 기본 retention은 `14일`이며 config로 조정 가능해야 한다. Snapshot detail은 retention 안에 저장된 read model을 반환하며, raw bucket retention과 같은 의미가 아니다.

`DashboardReadModelService`가 read model source of truth다. DB view나 frontend는 대체 source가 아니다. Starter heartbeat가 존재하더라도 dashboard metric state/freshness는 accepted bucket 기반으로 계산한다. Heartbeat telemetry가 구현되어 있으면 `starterConnection` 같은 별도 control-plane field의 source로 사용할 수 있다.

p95/p99 값의 canonical source는 ingest envelope의 starter-reported percentile이다. Read model 예시는 top-level `metrics.p95Ms`/`p99Ms` scalar를 약속하지 않고 `sourceScopedPercentiles`처럼 source/instance/bucket scope가 드러나는 shape를 사용한다. 같은 scope에서 starter-reported p95/p99와 histogram-derived p95/p99를 병렬 표시하지 않는다. 여러 starter instance 값이 같은 app/project/window에 존재하면 평균/최댓값/병합/히스토그램 재계산으로 단일 percentile scalar를 만들지 않고, instance/source 단위로 노출하거나 상위 scope에는 percentile 대신 bucket distribution을 표시한다.

Dashboard current response에는 큰 history 배열을 넣지 않는다. Snapshot identifier와 detail link가 필요하면 `read-model-contract.md`의 `snapshot` 경계만 사용한다.

### 3.4 MVC Boundary

- `DashboardController`는 path variable을 query DTO로 변환한다.
- `DashboardReadModelService`가 current/baseline window, freshness, state, source-scoped starter percentile, triage cards, endpoint priority를 구성한다.
- UI는 이 응답을 소비할 뿐 state/rule/p95/p99/endpoint priority를 재계산하지 않는다.

## 4. Epic 5/6 Candidate - Operational Event History API

이 section은 Epic 5/6 착수 전 구현 기준 후보를 문서화한다. Epic 2와 Epic 3의 현재 구현 대상이 아니다.

### 4.1 Operational Events Endpoint

```http
GET /api/projects/{projectId}/applications/{applicationId}/operational-events?since=24h&limit=50
Authorization: Bearer <access_token>
Accept: application/json
```

이 API는 raw bucket explorer, raw snapshot explorer, arbitrary time-series query가 아니다. `operational-event-history.md` contract에 따라 state transition, stale/down/recovery, high-confidence concern만 bounded event로 반환한다.

Operational history는 별도 `operational_events` table에서 읽지 않는다. 1시간 scheduled dashboard snapshot과 정책상 저장된 capture/fallback read model 결과를 기반으로 service layer가 suppression, hysteresis, dedup을 적용해 파생한다.

API surface에 노출되는 판정 경계:

- 같은 `application + endpointKey + ruleId` concern은 `60분` 안에 중복 event로 승격하지 않는다.
- degraded enter는 rule guard 통과, confidence `>= 0.75`, 최근 5개 30초 bucket 중 3개 이상 bad일 때만 가능하다.
- degraded resolve는 concern absence, confidence `< 0.60`, rule별 recovery/threshold 하회 중 하나가 `5 consecutive buckets` 동안 유지될 때만 가능하다.
- standalone `high_confidence_concern` event 승격 기준은 confidence `>= 0.82`다.
- 짧지만 강한 spike는 실험값으로 confidence `>= 0.90` + 최근 5개 bucket 중 2개 이상 bad일 때 capture 후보가 될 수 있다.

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
Authorization: Bearer <access_token>
Accept: application/json
```

Snapshot detail은 해당 snapshot에 저장된 read model을 반환하는 후보 API다. Current state를 재판정하지 않고, history event를 UI나 controller에서 새로 계산하지 않는다.

Snapshot detail의 endpoint evidence는 최대 10개까지 남는다. API는 `method`, `route`, `endpointKey`, `rank`, `reason`, `ruleIds`, `confidence`, `requestCount`, `errorRate`, `durationBuckets`, `baselineDurationBuckets`, `bucketDistributionSource`, `freshness`, `recommendedAction` 같은 bounded field만 제공하고 raw path, query string, query key/value, trace id, per-request sample, endpoint p95/p99는 제공하지 않는다.

Snapshot marker와 bounded endpoint/instance summary의 세부 shape는 Epic 5 Story 5.8에서 확정한다. 이 API section은 저장 당시 read model 반환, current state 재판정 금지, raw explorer 금지, endpoint percentile 계산 금지 원칙만 고정한다.

대표 status mapping:

| Status | 조건 |
|---|---|
| `200` | 저장된 snapshot read model 반환 |
| `401` | Bearer access token 없음/invalid/expired |
| `404` | active account-project membership 없음, snapshot이 없거나 retention으로 삭제됨 |
| `500` | snapshot 조회 실패 |

### 4.2.1 Snapshot Read Model (snapshot-mode restore) Endpoint

```http
GET /api/projects/{projectId}/applications/{applicationId}/dashboard/snapshots/{snapshotId}/read-model
Authorization: Bearer <access_token>
Accept: application/json
```

Snapshot/History에서 slot을 클릭하면 live dashboard surface를 **동일한 컴포넌트로** 복원하는(snapshot-mode) API다. 4.2 detail의 bounded projection과 달리, capture 당시 enricher가 저장한 **full `read_model_json`(application-dashboard-read-model shape, `mode=snapshot`)을 그대로** 반환한다. 따라서 응답 shape는 4.1 Dashboard current read model과 동일하되 `mode`/`readSemantics.source`만 snapshot 값이다.

- current state를 재판정하거나 metric을 재계산하지 않는다. 저장된 read model을 표시만 한다.
- 저장된 JSON을 record로 역직렬화/재직렬화하지 않고 **문자열 그대로** 내려준다(다중 생성자 record round-trip 시 일부 필드 유실 방지).
- 저장 당시 schema를 따른다. `InstanceEntry.summary`(D5) 이전 snapshot은 `instances[]`에 `summary`가 없을 수 있고, 이를 backfill하지 않는다. 소비 측 guard/렌더는 snapshot-mode에서 이를 허용한다.

| Status | 조건 |
|---|---|
| `200` | 저장된 full read model JSON 반환(`mode=snapshot`) |
| `400` | snapshotId가 UUID 형식이 아님 |
| `401` | Bearer access token 없음/invalid/expired |
| `404` | active account-project membership 없음, snapshot이 없거나 retention으로 삭제됨 |
| `500` | 저장된 read model JSON 조회/파싱 실패 |

### 4.3 Instance Snapshot Trend Endpoint

```http
GET /api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/snapshot-trend?since=7d&limit=168
Authorization: Bearer <access_token>
Accept: application/json
```

이 API는 특정 instance의 최근 관찰 흐름을 보여주는 bounded read model projection이다. 문제 상황에 한정하지 않고, stored dashboard snapshot에 남은 bounded instance summary를 시간 순서로 반환한다.

기본값과 clamp:

| Parameter | 기본값 | 최대값 | 의미 |
|---|---:|---:|---|
| `since` | `7d` | `14d` | `dashboard_snapshots` retention 안에서만 조회 |
| `limit` | `168` | `336` | hourly snapshot 기준 point cap |

Rough response:

```json
{
  "generatedAt": "2026-05-25T12:05:35Z",
  "applicationId": "5c942671-e251-4f7f-b610-18ae6ca4ef65",
  "instanceId": "orders-api-7f9c9c8c9d-x2p4k",
  "source": "dashboard_snapshots.read_model_json.instanceSummary",
  "horizon": {
    "since": "2026-05-18T12:05:35Z",
    "until": "2026-05-25T12:05:35Z",
    "limit": 168,
    "order": "capturedAt_asc"
  },
  "points": [
    {
      "snapshotId": "018f6b9a-2e1a-7d2b-9b2f-4db69d92c241",
      "capturedAt": "2026-05-25T12:00:00Z",
      "captureReason": "hourly_scheduled",
      "storedApplicationStateCode": "active",
      "metricData": {
        "lastAcceptedBucketAt": "2026-05-25T11:59:30Z",
        "freshnessLabel": "current"
      },
      "starterConnection": {
        "lastHeartbeatAt": "2026-05-25T11:59:45Z",
        "connectionMeaning": "starter_connected",
        "stateImpact": "none"
      },
      "starterPercentilePoint": {
        "source": "starter_canonical_percentile",
        "scope": "instance_bucket",
        "bucketStartUtc": "2026-05-25T11:59:00Z",
        "bucketEndUtc": "2026-05-25T11:59:30Z",
        "requestCount": 820,
        "p95Ms": 210,
        "p99Ms": 360
      },
      "resourceHints": {
        "cpuUsage": 0.41,
        "heapUsedRatio": 0.62,
        "datasourcePoolUsage": 0.37
      },
      "applicationTriageContribution": {
        "contributed": false,
        "ruleIds": [],
        "reason": "no_action_needed"
      },
      "endpointEvidenceRefs": []
    }
  ]
}
```

Boundary:

- source는 stored dashboard snapshot/read model이다.
- API는 accepted bucket, heartbeat telemetry, resource sample을 live join해서 current state를 만들지 않는다.
- UI/API는 point를 조합해 lifecycle state, health score, insight rule, p95/p99, endpoint priority, operational event를 재계산하지 않는다.
- `starterPercentilePoint`는 source-scoped starter canonical latest point만 담고, 없으면 `null`이다.
- `endpointEvidenceRefs`는 stored snapshot detail의 bounded endpoint evidence 참조만 담는다.
- raw 30초 bucket list, raw snapshot JSON list, endpoint timeseries, arbitrary query parameter는 제공하지 않는다.

대표 status mapping:

| Status | 조건 |
|---|---|
| `200` | bounded instance snapshot trend 반환. snapshot이 없으면 `points=[]` |
| `401` | Bearer access token 없음/invalid/expired |
| `404` | active account-project membership 없음, project, application, instance 식별 불가 |
| `500` | snapshot 조회/projection 실패 |

### 4.4 Candidate MVC Boundary

- `OperationalEventHistoryService` 후보가 `DashboardSnapshotRepository`를 재사용해 bounded event read model을 만든다.
- `InstanceSnapshotTrendService` 후보가 `DashboardSnapshotRepository`를 재사용해 특정 instance summary projection을 만든다.
- 별도 `OperationalEventRepository`와 `operational_events` table은 MVP에서 만들지 않는다.
- 별도 endpoint timeseries table, materialized view, Redis queue, PostgreSQL outbox는 MVP history source로 만들지 않는다.
- UI는 event 목록과 snapshot deep link를 표시할 뿐 state/rule/p95/p99/endpoint priority를 재계산하지 않는다.
- Alert delivery log와 operational event history를 같은 API로 섞지 않는다.

## 5. Project/Application Navigation and Bootstrap Surface

### 5.1 필요 여부 판단

MVP ingest에는 project key가 필요하므로 bootstrap 경로는 필요하다. Story 9.2 Stage 1에서는 GitHub OAuth로 생성된 service access token을 가진 account가 public onboarding API로 project를 등록하고 starter credential을 1회 표시로 발급받을 수 있다.

권장 순서:

1. public onboarding은 `POST /api/projects`로 active project, active member membership, starter credential hash/prefix metadata를 만든다.
2. local/dev seed는 operator smoke path로 유지하며 public onboarding API의 우회 경로가 아니다.
3. application과 instance는 첫 accepted bucket ingest에서 `ApplicationCatalogService`가 upsert한다.
4. dashboard는 read-only project list와 application list API로 scope를 선택한다.

Heartbeat는 bootstrap catalog 생성 경로가 아니다. project key와 metadata shape 검증은 할 수 있지만 application/instance row 생성은 첫 accepted bucket 기준으로 유지한다.

### 5.2 Internal Admin API 후보

Story 9.2 이후 일반 사용자의 project 생성은 public onboarding `POST /api/projects`가 담당한다. 아래 internal/admin API는
MVP public product scope가 아니며, local/internal profile에서 운영자가 별도 bootstrap이 필요하다고 결정할 때만 후보로 둔다.

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
  "starterCredential": {
    "displayValue": "<shown-once>",
    "keyPrefix": "obs_live_xxxxx",
    "visibleOnce": true
  }
}
```

주의:

- raw value는 public API와 동일하게 `starterCredential.displayValue` 1회 표시 field로만 보여주고 DB에는 hash/prefix metadata만 저장한다.
- secret-bearing response에는 `Cache-Control: no-store`를 둔다.
- 이 API는 MVP public product scope가 아니다.
- 인증/권한 체계를 크게 만들지 않는다. local/internal profile 또는 운영자-only 경로로 제한한다.

### 5.3 Application List API 후보

dashboard 첫 화면 진입을 위해 application 선택이 필요하면 read-only API 하나만 추가한다.

```http
GET /api/projects/{projectId}/applications
Authorization: Bearer <access_token>
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
- active account-project membership을 통과한 project에서만 catalog 조회를 수행한다.
- membership mismatch는 `403`이 아니라 body 없는 `404`로 fail-closed한다.
- Application Dashboard 판단을 대체하는 상세 state, rule, p95, endpoint priority를 포함하지 않는다.
- 필요하면 lifecycle state badge, last accepted bucket, starter connection summary, top concern 0~1개 수준의 light summary만 server-computed field로 둔다.
- heartbeat summary와 accepted bucket freshness는 별도 field/source로 표현한다.
- first-screen 판단은 dashboard query API에서만 가져온다.

### 5.4 Project List API 후보

Project Entry가 필요하면 read-only project list API를 둔다.

```http
GET /api/projects
Authorization: Bearer <access_token>
Accept: application/json
```

```json
{
  "projects": [
    {
      "projectId": "0fcf1d62-c5f2-43bd-85f7-2dd6c9e458b1",
      "name": "local-demo",
      "applicationCount": 3,
      "setupIssueCount": 0,
      "recentConcernCount": 1
    }
  ]
}
```

Boundary:

- Project 화면은 운영 판단 화면이 아니라 scope 선택 화면이다.
- 이 목록은 authenticated account의 active membership project만 포함한다.
- membership이 없거나 active project가 없으면 `200 OK`와 `projects=[]`를 반환한다.
- UI는 server response를 렌더링하고 hidden project를 추론하거나 probing하지 않는다.
- detailed triage, endpoint priority, p95/p99, snapshot/history marker는 포함하지 않는다.
- Story 9.2부터 Project 생성 API는 public onboarding `POST /api/projects`로 열린다. Story 6.10은 public create flow를 만들지 않았고,
  local/internal seed는 operator smoke/bootstrap 경로로만 남는다.

### 5.5 Public Project Registration API

```http
POST /api/projects
Authorization: Bearer <access_token>
Content-Type: application/json
```

Request는 project name 같은 non-secret 입력만 허용한다.

```json
{
  "name": "orders-prod"
}
```

성공 response는 secret-bearing response이므로 `Cache-Control: no-store`를 포함한다. Raw starter credential은 `starterCredential.displayValue` 1회 표시 field에만 나타난다.

```json
{
  "project": {
    "projectId": "0fcf1d62-c5f2-43bd-85f7-2dd6c9e458b1",
    "name": "orders-prod",
    "links": {
      "applications": "/api/projects/0fcf1d62-c5f2-43bd-85f7-2dd6c9e458b1/applications"
    }
  },
  "starterCredential": {
    "displayValue": "<shown-once>",
    "keyPrefix": "obs_live_xxxxx",
    "visibleOnce": true,
    "issuedAt": "2026-06-01T00:00:00Z"
  }
}
```

Boundary:

- No-token/invalid-token/expired-token은 `401 Unauthorized`와 `WWW-Authenticate: Bearer`다.
- Client-supplied account id, role/status, key prefix, hash, raw credential은 반영하지 않는다.
- Normalized project name 중복 또는 invalid name은 sanitized `400`/`409`로 fail-closed한다.
- 성공 시 새 `projects.status='active'` row와 현재 account의 `account_project_memberships.role='member', status='active'` row를 함께 만든다.
- DB에는 `project_key_hash`, `key_prefix`, `starter_credential_status`, `starter_credential_issued_at`, `starter_credential_rotated_at`, `starter_credential_revoked_at` 같은 non-secret metadata만 저장한다.
- `GET /api/projects`, Application List, Dashboard, Snapshot/History response에는 raw starter credential이나 hash를 포함하지 않는다.
- `domain.admin.service.SmokeProjectSeedService`, `portal.smoke.seed.*`, `.private/smoke-*`는 public registration API dependency가 아니다.

### 5.6 Starter Credential Lifecycle API

Credential metadata는 raw value/hash 없이 prefix/status/timestamps만 반환한다.

```http
GET /api/projects/{projectId}/starter-credential
Authorization: Bearer <access_token>
Accept: application/json
```

```json
{
  "projectId": "0fcf1d62-c5f2-43bd-85f7-2dd6c9e458b1",
  "starterCredential": {
    "keyPrefix": "obs_live_xxxxx",
    "status": "active",
    "issuedAt": "2026-06-01T00:00:00Z",
    "rotatedAt": null,
    "revokedAt": null
  }
}
```

Rotation은 기존 starter credential을 즉시 폐기하고 새 raw value를 1회 표시 response로만 반환한다. Secret-bearing response이므로 `Cache-Control: no-store`를 포함한다.

```http
POST /api/projects/{projectId}/starter-credential/rotations
Authorization: Bearer <access_token>
```

```json
{
  "projectId": "0fcf1d62-c5f2-43bd-85f7-2dd6c9e458b1",
  "starterCredential": {
    "displayValue": "<shown-once>",
    "keyPrefix": "obs_live_yyyyy",
    "status": "active",
    "visibleOnce": true,
    "issuedAt": "2026-06-01T00:05:00Z",
    "rotatedAt": "2026-06-01T00:05:00Z"
  }
}
```

Revocation은 project visibility를 숨기지 않고 starter ingest credential만 `revoked`로 바꾼다. Raw value는 반환하지 않는다.

```http
POST /api/projects/{projectId}/starter-credential/revocations
Authorization: Bearer <access_token>
```

Boundary:

- No-token/invalid-token/expired-token은 `401 Unauthorized`와 `WWW-Authenticate: Bearer`다.
- active account-project membership이 없는 account는 metadata/rotation/revocation 모두 body 없는 `404 Not Found`로 fail-closed한다.
- Membership mismatch response는 project name, application count, credential prefix/status, snapshot/history 존재 여부를 드러내지 않는다.
- Rotation 후 old credential은 prefix/hash 후보에서 사라지거나 active credential 상태를 통과하지 못해 bucket ingest와 heartbeat에서 `401`이다.
- Revocation 후 project는 `GET /api/projects`에 남을 수 있지만 bucket ingest와 heartbeat project key verification은 `401`이다.
- Metadata/revocation response는 raw value/hash를 포함하지 않는다. Raw value는 create/rotation success response의 `starterCredential.displayValue`에서만 허용한다.
- Public lifecycle API는 `domain.admin.service.SmokeProjectSeedService`, `portal.smoke.seed.*`, `.private/smoke-*`를 사용하지 않는다.

### 5.7 Instance Evidence API 후보

Instance Detail이 dashboard response에 포함된 summary보다 깊은 bounded evidence를 필요로 하면 read-only API를 둔다.

```http
GET /api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/evidence
Authorization: Bearer <access_token>
Accept: application/json
```

Boundary:

- Instance evidence는 application dashboard 판단을 대체하지 않는 drill-down이다.
- active account-project membership을 통과한 뒤 project/application/instance catalog path 정합성을 확인한다.
- accepted bucket freshness와 starter heartbeat/connection axis를 분리한다.
- source-scoped starter p95/p99 series와 histogram distribution은 표시 evidence일 뿐, API/UI가 새 application p95/p99를 계산하지 않는다.
- lifecycle state, rule, endpoint priority, operational event를 instance API에서 재계산하지 않는다.

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
POST /api/auth/github/callback/tokens
GET /api/auth/github/callback/token?code=<code>&state=<state>
POST /api/auth/token/refresh
Authorization: Bearer <refresh-token>
POST /api/auth/logout
Authorization: Bearer <access-token>
```

`/authorize`는 GitHub OAuth 시작점이고, `/callback`은 browser-facing GitHub OAuth 성공 결과를 내부 account와 연결하는 boundary다.
기본 `/callback`은 token pair JSON을 화면에 렌더링하지 않고 no-store HTML relay page를 반환한다.
Dashboard는 popup opener를 유지한 상태에서 `/callback/tokens`를 한 번 호출해 service access token만 in-memory auth state로 세팅한다.
이 relay는 token을 URL query/hash/fragment, cookie, `localStorage`, `sessionStorage`에 저장하지 않기 위한 짧은 수명 server-memory handoff다.
Refresh token은 기본 static dashboard flow에 전달하지 않는다.

`/callback/token`은 테스트/도구용 explicit JSON callback endpoint다. GitHub OAuth App redirect URI를 이 endpoint로 별도 설정한 자동화는 token pair JSON을 받을 수 있지만,
사용자 browser-facing 기본 callback URL과 분리한다. OAuth 실패나 취소는 계정을 생성하지 않는다.

`/token/refresh`는 Refresh Token을 Bearer token으로 받아 rotation, 만료, revoke, reuse detection을 적용한다. Refresh Token 저장소는 `token store` 추상으로 두며, 초기 구현 후보는 RDBMS에 hashed refresh token 또는 token family metadata를 저장하는 방식이다. Redis는 고성능 revoke list, distributed token state, reuse detection 최적화가 필요해질 때 후속 선택지로 둔다.

### 7.3 Response / Log Boundary

- MVP 인증은 cookie 기반 server session을 사용하지 않는다.
- API 요청 인증은 `Authorization: Bearer <access_token>` header를 사용한다.
- Project resource API authorization은 Bearer account id 기준이며, project-scoped endpoint는 active account-project membership도 함께 확인한다.
- `GET /api/projects` collection endpoint는 path project id가 없으므로 account id만으로 scoped project list를 만든다.
- `POST /api/projects` public onboarding endpoint는 active account를 인증한 뒤 새 project와 membership을 함께 만든다.
- `/api/projects/{projectId}/applications/**` endpoint는 account id와 path project id의 active membership을 먼저 확인한 뒤 catalog path 정합성을 확인한다.
- `/api/projects/{projectId}/starter-credential/**` lifecycle endpoint도 resource lookup 전에 active membership을 확인하고 mismatch는 body 없는 `404`로 닫는다.
- Access Token은 stateless하게 검증 가능한 짧은 만료 JWT다.
- GitHub OAuth token과 우리 서비스 access token/refresh token을 구분한다.
- MVP에서 GitHub API 호출이 필요 없다면 GitHub OAuth token을 저장하지 않는다.
- Controller/API response, log, error에는 GitHub OAuth token, provider raw payload, secret을 노출하지 않는다.
- 일반 resource API response, log, error에는 access token, refresh token도 노출하지 않는다.
- Browser-facing callback은 service token pair JSON을 화면에 렌더링하지 않는다. Token pair JSON이 필요한 도구는 `/api/auth/github/callback/token`처럼 browser flow와 분리된 explicit API endpoint를 사용한다.
- `/token/refresh` response는 우리 서비스 access token/refresh token JSON body를 반환하지만, cookie server session, URL token 전달, browser storage 저장 정책은 열지 않는다.
- Signup/login 실패 메시지는 provider 내부 오류, raw payload, token 상태를 과도하게 드러내지 않는 일반화된 메시지로 둔다.

상세 acceptance는 `planning-artifacts/contracts/account-auth-policy.md`를 따른다.
