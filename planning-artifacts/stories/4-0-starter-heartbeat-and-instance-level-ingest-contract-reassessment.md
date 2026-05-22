---
artifactType: story
storyId: "4.0"
epic: "Epic 4. State Semantics and Time Windows"
title: "Starter Heartbeat and Instance-Level Ingest Contract Gate"
architectureStyle: Traditional MVC
status: draft
date: 2026-05-22
---

# Story 4.0 - Starter Heartbeat and Instance-Level Ingest Contract Gate

## Story Goal

Epic 4 state/read-model implementation에 들어가기 전에 starter heartbeat와 instance-level percentile 계약을 한 번 잠그는 **문서 정렬 gate**다.

이 story는 starter가 주기적으로 portal 도달 가능성, project key 유효성, `application/environment/instance` metadata shape, starter liveness를 알리는 heartbeat 계약을 고정한다. 또한 이후 project -> application -> instance 탐색에서 p95/p99의 canonical source를 starter가 보낸 `localPercentiles`로 고정하고, histogram bucket은 distribution 표시와 진단 원자료로 유지하는 계약을 고정한다.

이 story 자체는 heartbeat endpoint, instance detail API, snapshot persistence, UI를 구현하지 않는다. 후속 구현 story가 흔들리지 않도록 용어, source-of-truth, side effect 금지, 테스트 guard를 먼저 잠그는 것이 목적이다.

## User Story

Spring Boot 앱 운영자로서, starter가 실행 중인 동안 portal 연결과 project key 설정이 계속 유효한지 주기적으로 확인하고 싶다.

그래야 첫 dashboard가 비어 있거나 수집이 끊겼을 때 “starter는 살아 있지만 metric bucket이 아직 없는 것인지”, “project key가 틀린 것인지”, “portal로 보낼 수 없는 것인지”, “starter 자체가 더 이상 heartbeat를 보내지 않는 것인지”를 구분할 수 있다.

## Scope

포함:

- starter -> portal heartbeat API 계약 초안
- `POST /api/ingest/v1/buckets`와 heartbeat endpoint의 의미 분리
- project key 검증 재사용 경계
- `application.name`, `environment`, `instance` metadata shape validation
- starter-side heartbeat sender 후보 수준의 계약 경계
- fail-open 기본 정책, bounded timeout/logging, interval/jitter/backoff 경계
- instance-level p95/p99 source-of-truth 재평가
- `localPercentiles` ingest payload 후보와 starter canonical percentile 의미 정의
- histogram bucket을 distribution visualization, endpoint bucket display, diagnostic raw bucket으로 사용하는 경계
- 같은 scope에서 starter-reported p95/p99와 histogram-derived p95/p99를 병렬 표시하지 않는 dashboard 정책
- 여러 starter instance의 p95/p99가 다를 때 임의 평균/병합하지 않는 상위 scope 표시 정책
- endpoint별 p95/p99 계산 금지와 endpoint bucket display only 정책
- snapshot persistence에서 p99를 단일 long-window 대표값으로 만들지 않는 후속 계약 원칙
- 관련 계약/architecture/API/UX 문서 정렬 계획

## Non-goals

- project/application/instance navigation UI 구현
- instance dashboard/detail API 구현
- dashboard snapshot persistence 구현
- snapshot marker UI 구현
- operational event history API 구현
- 여러 starter-local p95/p99 값을 평균/병합해서 app/project rollup p95/p99를 새로 만드는 것
- 30초 bucket p95/p99 값을 source/scope 없이 canonical 15분 p95/p99 또는 application/project p95/p99라고 부르는 것
- raw path, query string, high-cardinality tag, trace/per-request sample 추가
- Prometheus scrape/query UI
- endpoint timeseries table, materialized view, Redis/outbox 도입
- UI-side state/rule/p95/p99/endpoint priority 재계산

## Proposed Heartbeat API Boundary

권장 endpoint:

```http
POST /api/ingest/v1/heartbeat
X-OBS-Project-Key: <project-key>
Content-Type: application/json
```

요청 후보:

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

응답 후보:

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

Boundary:

- heartbeat는 metric ingest가 아니라 starter liveness/control-plane 신호다.
- heartbeat 성공은 accepted bucket 존재나 host application health를 의미하지 않는다.
- heartbeat는 `accepted_metric_buckets`, `dashboard_snapshots`, operational event, state/read-model calculation을 생성하지 않는다.
- heartbeat는 application/instance catalog upsert source가 아니다. 첫 accepted bucket이 계속 catalog upsert source다.
- heartbeat는 별도 lightweight telemetry로 `lastHeartbeatAt`, `lastHeartbeatStatus`, failure category를 저장할 수 있지만, 이 값은 dashboard operational state source-of-truth가 아니다.
- heartbeat는 UI state를 직접 바꾸지 않는다. `waiting_first_data`, `stale`, `down`은 accepted bucket 기준으로 유지된다. UI가 heartbeat를 보여줄 경우 “starter 연결/heartbeat 상태”와 “수집 bucket freshness”를 분리해 표시한다.
- heartbeat 응답에서 accepted bucket 참고 시각을 함께 제공하더라도 `ingestBoundary.lastAcceptedBucketAt`처럼 별도 namespace에 두며, heartbeat 성공/failure 문구와 같은 상태축으로 표현하지 않는다.
- starter는 기본적으로 host startup/request path를 막지 않는 background schedule로 heartbeat를 보낸다. 권장 interval은 config로 열되, MVP 기본값은 30초 후보이며 jitter/backoff/rate-limited logging을 적용한다.

## Instance-Local Percentile Trend Payload Candidate

개별 instance에 대해서는 starter가 각 30초 bucket에서 직접 계측한 p95/p99를 보낸다. 이 값은 해당 `instance_bucket` scope의 **starter canonical percentile**이다.

이 story의 정렬 기준은 `localPercentiles`를 제거하거나 숨기는 것이 아니다. 필드명은 호환성 때문에 유지하지만 문서와 UI label은 `starter-reported percentile` 또는 `starter canonical percentile`로 설명한다. 강조되는 값의 의미는 source와 scope가 붙은 starter-reported 30초 bucket p95/p99이며, 여러 값의 평균/병합으로 새 p95/p99를 만들지 않는다.

후보 shape:

```json
{
  "localPercentiles": {
    "scope": "instance_bucket",
    "source": "starter_local",
    "bucketStartUtc": "2026-05-22T10:12:00Z",
    "bucketEndUtc": "2026-05-22T10:12:30Z",
    "requestCount": 820,
    "p95Ms": 640,
    "p99Ms": 1800,
    "mergeable": false
  }
}
```

Instance detail headline 후보:

```json
{
  "starterPercentiles": {
    "scope": "instance",
    "source": "starter_canonical_percentile",
    "window": "current_15m",
    "bucketDurationSeconds": 30,
    "bucketCount": 30,
    "displayPolicy": "source_scoped_series",
    "points": [
      {
        "bucketStartUtc": "2026-05-22T10:12:00Z",
        "bucketEndUtc": "2026-05-22T10:12:30Z",
        "requestCount": 820,
        "p95Ms": 640,
        "p99Ms": 1800
      }
    ]
  }
}
```

Boundary:

- `localPercentiles.p95Ms`와 `localPercentiles.p99Ms`는 해당 instance의 해당 30초 bucket에 대한 starter canonical value다.
- `mergeable=false`는 여러 p95/p99 숫자끼리 평균/병합해 새로운 p95/p99를 만들지 않는다는 뜻이며, 해당 `instance_bucket` scope에서는 starter canonical 값이라는 뜻을 함께 가진다.
- current, baseline, snapshot window에 여러 point를 표시할 수 있지만 `avgMs` 같은 평균 percentile field를 canonical p95/p99처럼 만들지 않는다.
- app/project/window에서 여러 starter 값이 존재하면 instance/source 단위로 노출하거나 상위 scope에는 percentile 대신 bucket distribution을 표시한다.
- UI label은 “starter-reported p95/p99”, “instance 30초 bucket p99”, “source-scoped p95/p99 series”처럼 의미를 드러내야 한다.
- 피해야 할 라벨은 source/scope 없이 쓰는 “15분 p99”, “current p99”, “application p99”, “서비스 p99”다.
- Endpoint detail에서는 percentile 값을 만들지 않고 histogram bucket을 그대로 표시한다.

## Snapshot Tail Summary Follow-up Boundary

이 story는 dashboard snapshot persistence와 `tailLatencySummary` schema를 구현하거나 확정하지 않는다. 다만 후속 snapshot story가 p99를 오도하지 않도록 아래 표현 경계만 gate 기준으로 남긴다.

Boundary:

- 긴 시간 snapshot 또는 history surface는 `hourP99`, `dayP99`, `14dP99` 같은 단일 대표 p99를 만들지 않는다.
- 후속 snapshot story는 저장 당시 read model, starter canonical percentile, bucket distribution evidence를 분리해서 정의한다.
- 후속 `trendSlices`는 p95/p99 후보를 만들지 않고 해당 subwindow의 histogram bucket distribution만 표시한다.
- 후속 `worstBuckets`는 전체 raw bucket list가 아니라 top-N representative bucket이어야 한다.
- 후속 `badBucketCount`는 rule/sample guard를 통과한 bounded count여야 한다.
- snapshot detail은 저장 당시 read model과 bounded tail evidence를 보여줄 뿐 current state를 재판정하지 않는다.

## Acceptance Criteria

1. `handshake`와 `health check` 의도는 폐기되고, 용어는 `starter heartbeat`로 문서화된다.
2. 별도 heartbeat endpoint 계약이 `POST /api/ingest/v1/buckets`와 분리되어 문서화된다.
3. 유효한 `X-OBS-Project-Key`와 유효한 metadata shape가 들어오면 `200 OK`와 actionable response를 반환한다.
4. project key 누락 또는 검증 실패는 `401`로 반환하며 raw key material을 response/log/error에 노출하지 않는다.
5. disabled project처럼 인증은 됐지만 ingest를 허용할 수 없는 상태는 `403` 후보로 문서화한다.
6. `application.name`, `environment`, `instance`, `schemaVersion` shape가 잘못되면 `400`으로 반환한다.
7. heartbeat 성공은 accepted metric bucket, dashboard snapshot, operational event, state/read-model calculation을 생성하거나 암시하지 않는다.
8. heartbeat 응답이 accepted bucket 참고 시각을 제공하더라도 `ingestBoundary.lastAcceptedBucketAt`처럼 분리하며, heartbeat status와 freshness status를 같은 상태로 표현하지 않는다.
9. heartbeat는 기본적으로 application/instance catalog row를 upsert하지 않으며, 첫 accepted bucket이 catalog upsert source라는 기존 경계를 유지한다.
10. starter heartbeat는 기본 fail-open 정책을 따른다. portal 장애나 timeout은 host app startup/request path를 실패시키지 않는다.
11. starter heartbeat 실패는 bounded timeout, jitter/backoff, rate-limited warning, actionable message로 표현된다.
12. ingest envelope는 `localPercentiles` 후보를 instance-local 30초 bucket의 starter canonical percentile field로 문서화하며, `scope=instance_bucket`, `source=starter_local`, `mergeable=false` 의미를 고정한다.
13. instance detail read model은 starter-reported p95/p99 point series를 source/scope와 함께 표시할 수 있다.
14. app/project/window p95/p99는 histogram bucket merge로 계산하지 않는다.
15. 여러 starter 값이 존재하는 상위 scope는 임의 평균/병합 p95/p99를 만들지 않고 source별 노출 또는 bucket distribution 표시 중 하나를 선택한다.
16. endpoint별 p95/p99 계산, endpoint percentile rollup, endpoint percentile judgment, endpoint p99 alert 기준을 만들지 않는다.
17. UI는 heartbeat result, state, p95/p99, endpoint priority를 직접 계산하지 않고 server response/read model을 표시한다.
18. Story 4-0은 contract gate이며 project -> application -> instance navigation, heartbeat endpoint 구현, instance detail API 구현, snapshot marker 구현은 후속 story로 분리한다.
19. 후속 snapshot contract는 단일 long-window p99를 만들지 않으며, starter canonical percentile과 bucket distribution evidence의 세부 shape는 별도 story에서 확정한다.
20. heartbeat 미수신은 host application down 판정 근거가 아니며, starter connection 상태와 accepted bucket 기반 operational state를 분리해 표현한다.

## Dev Notes

- Active baseline은 Traditional MVC + Service/Repository Layering이다.
- 이 story는 구현 story가 아니라 문서 정렬 gate다. 후속 구현 story가 이 gate의 용어와 source-of-truth 경계를 따른다.
- Controller는 request/response 변환과 HTTP status mapping만 맡는다.
- Portal service는 `ProjectKeyVerificationService`와 ingest metadata validation vocabulary를 재사용한다.
- Repository는 lookup/persistence만 맡고 state/rule/p95/p99/endpoint priority를 계산하지 않는다.
- Starter heartbeat schedule은 bean 생성이나 host startup을 기본으로 막지 않는다.
- `starter-failure-semantics.md`의 fail-open, bounded timeout, warning-once/rate-limited logging 원칙을 따른다.
- `ingest-envelope.md`의 `localPercentiles.p95Ms`/`p99Ms`가 starter canonical percentile source-of-truth다.
- 개별 instance의 30초 p95/p99는 starter-reported percentile로 받을 수 있다.
- `localPercentileSummary.avgMs` 같은 평균 percentile field는 새 p95/p99로 만들지 않는다.
- 사용자 제시 옵션 기준 권장안은 histogram bucket 유지 + starter canonical percentile 표시다.
- histogram bucket은 app/project/time-window p95/p99 산출 근거가 아니라 distribution display와 진단 원자료다.
- p95/p99 값만으로 app/project rollup을 만들지 않는다.
- 후속 snapshot story에서 canonical p99를 저장할 때는 값끼리 평균/최댓값 조합으로 만들지 않는다.
- 후속 snapshot story는 starter canonical percentile과 bucket distribution evidence의 shape를 별도로 확정한다.
- 후속 `tailLatencySummary`는 raw bucket explorer가 아니어야 한다. top-N worst buckets/slices와 count/marker 같은 bounded evidence로 제한한다.

## Test Notes

권장 test:

- `IngestHeartbeatControllerTest`
  - valid project key + valid metadata + valid heartbeat -> `200 OK`
  - missing/invalid project key -> `401`
  - disabled project candidate -> `403`
  - invalid schema version or metadata -> `400`
  - response에서 accepted bucket 참고 시각은 `ingestBoundary.lastAcceptedBucketAt`처럼 heartbeat status와 분리됨
  - response/log/error에 raw project key가 노출되지 않음

- `IngestHeartbeatServiceTest`
  - `ProjectKeyVerificationService` 재사용
  - metadata validation 재사용 또는 같은 validation vocabulary 적용
  - heartbeat가 bucket/snapshot/event/read-model 계산을 호출하지 않음
  - heartbeat가 application/instance catalog upsert를 호출하지 않음
  - heartbeat telemetry를 저장하더라도 accepted bucket freshness/state 계산에 반영하지 않음
  - heartbeat 미수신 fixture가 host application down 판정으로 변환되지 않음

- `StarterHeartbeatClientTest`
  - bounded timeout 설정 적용
  - portal down/timeout이 startup/request path를 막지 않음
  - 실패 메시지가 endpoint, failure category, next action을 포함
  - interval/jitter/backoff와 반복 실패 log spam 방지

- `HistogramMergeService` 후속 test guard
  - single instance p95
  - instance scoped p95/p99
  - app/project/window p95/p99를 histogram count merge로 계산하지 않음
  - `localPercentiles` payload가 schema validation에서 거절되지 않음
  - `localPercentiles` payload가 해당 `instance_bucket` scope의 starter canonical percentile로 쓰임
  - `localPercentiles` 숫자끼리 app/project/window rollup p95/p99로 평균/병합되지 않음
  - endpoint별 p95/p99가 생성되지 않음
  - snapshot `trendSlices` 후보는 subwindow bucket distribution으로 표시
  - snapshot `worstBuckets` 후보는 bounded top-N만 반환
  - long-window `dayP99` 또는 `14dP99` 같은 단일 representative p99를 만들지 않음

## Affected Docs

- `planning-artifacts/contracts/ingest-envelope.md`
- `planning-artifacts/contracts/histogram-merge.md`
- `planning-artifacts/contracts/starter-failure-semantics.md`
- `planning-artifacts/contracts/state-semantics.md`
- `planning-artifacts/contracts/read-model-contract.md`
- `planning-artifacts/contracts/operational-event-history.md`
- `planning-artifacts/architecture.md`
- `planning-artifacts/api-surface.md`
- `bmad-restart-context-pack/ux-design-specification.md`
- `bmad-restart-context-pack/observability_toy_spec_v0.8.md`
- `planning-artifacts/epics.md`
- `implementation-artifacts/sprint-status.yaml`

## Suggested Follow-Up Stories

1. Starter heartbeat endpoint and lightweight telemetry
2. Instance-local percentile ingest contract and validation
3. Project/Application selector read model
4. Application instance list with freshness/state hints
5. Instance detail evidence read model and local percentile headline
6. Snapshot marker and bounded tail summary contract
7. Snapshot detail read-model endpoint
8. UX copy guardrails for heartbeat, freshness, p99, and non-mergeable metrics

## Developer Guardrails

- Hexagonal 구조로 되돌리지 않는다.
- UI는 state/rule/p95/p99/endpoint priority를 직접 재계산하지 않는다.
- raw path, query string, high-cardinality tag, trace/per-request sample을 MVP read model에 넣지 않는다.
- p95/p99 값만으로 app/project rollup을 만들지 않는다.
- `localPercentileSummary.avgMs` 같은 평균 percentile field를 만들거나 canonical 15분 p95/p99라고 부르지 않는다.
- endpoint detail은 histogram bucket display only이며 endpoint별 p95/p99를 계산하지 않는다.
- snapshot에서 `hourP99`, `dayP99`, `14dP99` 같은 단일 long-window p99 대표값을 만들지 않는다.
- heartbeat 성공을 accepted ingest로 해석하지 않는다.
- heartbeat를 host application health/down 판정으로 연결하지 않는다.
- heartbeat 미수신을 host application down 판정으로 연결하지 않는다.
- bucket ingest와 heartbeat의 retry/logging/idempotency 의미를 섞지 않는다.

## Status

draft
