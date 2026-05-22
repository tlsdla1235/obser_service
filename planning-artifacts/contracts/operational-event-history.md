---
artifactType: contract
name: operational-event-history
architectureStyle: Traditional MVC
status: active-planning-aligned
date: 2026-05-14
---

# Contract - Operational Event History MVC Version

## 1. 역할과 경계

Operational Event History는 사용자가 알림이나 외부 신호를 받은 뒤 늦게 대시보드에 들어와도 최근 운영 흐름을 이해할 수 있게 하는 bounded read model contract다.

이 계약은 Epic 5/6 착수 전 구현 기준이다. Epic 2와 Epic 3은 operational event 저장, 계산, API, UI를 구현하지 않는다.

Operational Event History는 raw snapshot explorer나 arbitrary time-series query가 아니다. 현재 상태 판단을 대체하지 않으며, `DashboardReadModelService`가 만든 state/rule/evidence 결과를 최근 운영 이력으로 요약한다.

Starter heartbeat는 operational event source가 아니다. Heartbeat 성공, 실패, 미수신은 accepted bucket, dashboard snapshot, operational event, state/read-model calculation을 생성하거나 암시하지 않는다.

## 2. 용어

| 용어 | 의미 |
|---|---|
| current dashboard read model | query 시점 기준 current/baseline 15분 window로 만든 현재 상태 source of truth |
| dashboard snapshot | `dashboard_snapshots`에 저장된 특정 시점의 dashboard read model. 세부 shape는 Epic 5의 snapshot marker/tail summary story에서 확정 |
| operational event | snapshot/read model 결과에서 service layer가 파생한 bounded 운영 사건 |
| recent operational history | 사용자-facing 표현인 최근 운영 이력 |
| alert delivery log | Discord 등 알림 전송 성공/실패 기록. operational event history와 섞지 않는다 |

## 3. Snapshot Source and Cadence

`dashboard_snapshots`는 spike 전용 table이 아니라 dashboard read model history 저장소다. `read_model_json`에는 해당 시점의 state, p95, triage cards, endpoint priority, bounded endpoint evidence를 포함할 수 있다.

Snapshot marker와 bounded distribution summary의 세부 schema는 Epic 5의 별도 story에서 닫는다. 이 계약은 1시간 coarse history, state/read-model 기반 event 파생, raw time-series 금지, histogram-derived percentile 금지, 단일 long-window p99 금지 원칙을 유지한다.

MVP 기본 snapshot 생성 정책은 아래로 고정한다.

1. application별 `1시간 scheduled snapshot`을 저장한다.
2. `state_code`가 hysteresis 이후 의미 있게 바뀌면 추가 snapshot row를 남긴다. 예: `active -> degraded`, `degraded -> active`, stale/down/recovery 계열 변화.
3. high-confidence concern이 confidence `>= 0.82`, rule guard, minimum sample guard를 통과하고 suppression window 밖이면 추가 snapshot row를 남긴다.
4. 짧지만 강한 spike 실험값은 confidence `>= 0.90`이고 최근 5개 30초 bucket 중 2개 이상 bad일 때 state 변화가 없어도 capture 후보가 될 수 있다. 이 경우에도 minimum sample guard와 suppression window는 항상 적용한다.
5. dashboard query 시 최신 snapshot이 없거나 current response로 쓰기에 명백히 오래된 경우 `DashboardReadModelService`가 fallback으로 현재 read model을 재생성하고, 필요하면 snapshot으로 저장할 수 있다.

Ingest commit 직후 모든 30초 bucket마다 dashboard snapshot을 refresh하지 않는다. `accepted_metric_buckets`는 짧은 retention의 30초 계산 원천이고, `dashboard_snapshots`는 UX와 DB 비용 균형을 위해 coarse-grained history로 유지한다.

이 정책은 30초 dashboard snapshot 장기 보관이 아니다. 기본 1시간 cadence만으로 raw bucket retention 이후의 모든 sub-hour spike를 완전 재구성한다고 약속하지 않으며, 중요한 state-change와 high-confidence concern만 bounded capture로 남긴다. 그래도 endpoint timeseries table, materialized view, 별도 `operational_events` table은 범위 밖이다.

`dashboard_snapshots` 기본 retention은 `14일`이다. 이 값은 config로 조정 가능해야 하며 `accepted_metric_buckets`의 짧은 raw-ish bucket retention과 의미를 섞지 않는다.

## 4. Event Candidate Source

Operational event 후보는 아래 source에서만 나온다.

- `dashboard_snapshots.generated_at`, `state_code`, `read_model_json`
- 필요한 경우 저장된 read model에서 복사한 bounded index column
- state transition 또는 notable state period
- high-confidence degraded concern 또는 alertable concern
- stale/down 진입과 recovery 관찰
- 저장된 read model의 starter canonical percentile 또는 bucket distribution evidence

후보 생성은 UI, controller, repository가 아니라 portal service layer에서 수행한다.

Heartbeat telemetry는 이 후보 source에 포함하지 않는다. Starter connection 상태를 보여주는 별도 surface가 필요하면 heartbeat read model에서 다루고 operational event history와 섞지 않는다.

Raw bucket retention 범위 안에서 fallback regeneration 또는 diagnostic detail을 보강할 수는 있지만, operational event 후보의 장기 source of truth는 snapshot/read model 계층이다.

Bounded index/search helper column 후보는 `capture_reason`, `primary_rule_id`, `primary_endpoint_key`, `max_confidence`, `state_code`, `generated_at`, `current_window_end_utc`로 고정한다. 이 값은 저장된 read model 검색 편의용 복사값이며 raw metric이나 endpoint timeseries 저장소가 아니다.

Snapshot `read_model_json`에 남기는 endpoint evidence는 최대 `10개`다. 우선순위는 top triage card에 연결된 endpoint, `endpointPriority` 상위 항목, high-confidence concern endpoint 순서다. Endpoint evidence에는 `method`, `route`, `endpointKey`, `rank`, `reason`, `ruleIds`, `confidence`, `requestCount`, `errorRate`, `durationBuckets`, `baselineDurationBuckets`, `bucketDistributionSource`, `freshness`, `recommendedAction`만 담는다. raw path, query string, query key/value, trace id, per-request sample, endpoint p95/p99는 담지 않는다.

## 5. Event Types

허용 event type 후보는 아래로 제한한다.

| Type | 의미 |
|---|---|
| `state_changed` | state code가 의미 있게 바뀐 경우 |
| `degraded_entered` | high-confidence concern 때문에 degraded로 진입한 경우 |
| `degraded_resolved` | degraded concern이 해소되고 active로 돌아온 경우 |
| `stale_entered` | freshness 부족으로 stale에 진입한 경우 |
| `down_entered` | freshness 부족이 더 길어져 down에 진입한 경우 |
| `recovery_observed` | stale/down 이후 새 bucket이 들어와 recovery 관찰 중인 경우 |
| `high_confidence_concern` | state 변화 없이도 노출할 가치가 있는 고신뢰 concern이 생긴 경우 |

`alert_sent` 같은 delivery event는 이 contract의 event type이 아니다. 알림 전송 이력은 별도 delivery log로 다룬다.

## 6. Bounded Query Rules

history 조회 horizon은 current/baseline 판단 window와 다르다.

- current 15분과 baseline 15분은 현재 상태 판단 전용이다.
- recent history는 최근 24시간 또는 `limit` 기반으로 bounded 조회한다.
- 기본 후보는 `since=24h`, `limit=50`이다.
- service는 과도한 `since`와 `limit`을 서버 정책으로 clamp한다.
- retention으로 snapshot이 삭제된 구간은 history가 비어 있을 수 있다.

이 API는 arbitrary time-series query, raw bucket query, raw snapshot list를 제공하지 않는다.

## 7. Deduplication, Hysteresis, and Suppression Rules

Operational event는 노이즈를 줄이기 위해 아래 규칙을 따른다.

- 연속된 같은 state period는 하나의 event period로 접는다.
- 같은 `application + endpointKey + ruleId` concern은 `60분` suppression window 안에서 중복 event로 승격하지 않는다.
- degraded 진입과 해소는 서로 다른 hysteresis 기준을 사용한다.
- dashboard triage card 기본 노출 기준은 confidence `>= 0.65`이고, standalone operational history `high_confidence_concern` 승격 기준은 confidence `>= 0.82`다.
- minimum sample guard를 통과하지 못한 candidate는 event로 승격하지 않는다.
- 같은 snapshot에서 여러 concern이 있으면 사용자에게 가장 행동 가능한 concern을 우선한다.

Degraded enter hysteresis는 아래 조건을 모두 만족해야 한다.

1. rule별 freshness, sample, baseline, absolute threshold guard 통과
2. concern confidence `>= 0.75`
3. 최근 5개 30초 bucket 중 3개 이상 bad 상태

30초 단발 blip은 degraded state나 `degraded_entered` event를 만들지 않는다.

Degraded resolve hysteresis는 아래 조건 중 하나가 `5 consecutive buckets` 동안 유지될 때만 통과한다.

1. concern absence
2. concern confidence `< 0.60`
3. rule별 recovery/threshold 하회

Resolve 기준은 enter보다 보수적으로 두어 flapping을 줄인다.

짧지만 강한 spike 실험값은 confidence `>= 0.90`이고 최근 5개 bucket 중 2개 이상 bad이면 state 변화가 없어도 capture 후보가 될 수 있다. 단, minimum sample guard와 `60분` suppression window는 항상 적용한다.

## 8. Event Shape

응답 item은 구현 시 아래 의미를 포함해야 한다.

```json
{
  "eventId": "snapshot:018f:degraded:endpoint_latency_spike",
  "type": "degraded_entered",
  "severity": "warning",
  "title": "POST /orders 응답 지연 증가",
  "summary": "오류율과 지연 bucket 분포가 함께 악화됐습니다.",
  "occurredAt": "2026-05-14T07:42:30Z",
  "resolvedAt": "2026-05-14T08:05:00Z",
  "stateCode": "degraded",
  "confidence": 0.84,
  "snapshotId": "018f6b9a-2e1a-7d2b-9b2f-4db69d92c241",
  "evidence": {
    "ruleId": "endpoint_latency_spike",
    "method": "POST",
    "route": "/orders",
    "endpointKey": "POST /orders",
    "requestCount": 12000,
    "durationBuckets": [
      { "leMs": 250, "count": 5200 },
      { "leMs": 500, "count": 9800 },
      { "leMs": 1000, "count": 11800 },
      { "leMs": 2000, "count": 12000 }
    ],
    "bucketDistributionSource": "histogram_bucket_distribution"
  },
  "links": {
    "snapshot": "/api/projects/{projectId}/applications/{applicationId}/dashboard/snapshots/{snapshotId}"
  }
}
```

`eventId`는 MVP에서 snapshot 기반 derived id일 수 있다. event별 durable external id, acknowledgement, owner assignment가 필요해지기 전에는 별도 event table을 만들지 않는다.

## 9. Snapshot and Deep Link

각 operational event는 가능한 경우 snapshot/read model detail deep link를 가진다.

Snapshot detail은 저장된 read model을 보여주는 경계다. detail 조회가 current state를 재판정하거나 history event를 새로 계산하는 경로가 되어서는 안 된다.

Retention으로 snapshot이 삭제된 경우 detail endpoint는 `404`를 반환할 수 있다. UI는 이 상황을 event 자체의 오류로 해석하지 않는다.

## 10. Percentile and Bucket Distribution Rules

Operational event가 p95/p99를 포함해야 할 때 source는 starter가 보낸 canonical percentile이어야 한다. 이 값은 `starter-reported percentile` 또는 `starter canonical percentile`로 표현한다.

Histogram bucket은 operational event에서 percentile scalar를 만들기 위한 입력이 아니다. Bucket은 distribution visualization, endpoint bucket display, diagnostic raw bucket으로만 쓴다.

같은 event 또는 snapshot detail의 같은 scope에 starter-reported p99와 histogram-derived p99가 동시에 존재하면 안 된다.

여러 starter instance의 p95/p99가 같은 app/project/window 안에 섞여 있으면 service는 임의로 평균/병합한 p95/p99를 event evidence로 만들지 않는다. Instance/source 단위로 노출하거나 상위 scope에는 bucket distribution만 남긴다.

Endpoint event/detail은 endpoint별 p95/p99를 계산하지 않는다. Endpoint 화면은 histogram bucket을 그대로 표시하고, endpoint percentile rollup, endpoint percentile judgment, endpoint p99 alert 기준은 만들지 않는다.

## 11. MVC Boundary

Epic 5/6 후보 구현 위치는 아래 중 하나로 둔다.

- `domain.dashboard.service.OperationalEventHistoryService`
- `domain.history.service.OperationalEventHistoryService`

MVP에서는 `DashboardSnapshotRepository`를 재사용한다. 별도 `OperationalEventRepository`나 `operational_events` table은 만들지 않는다.

Controller는 history read model을 serialize하고 HTTP status를 mapping한다. UI는 event를 표시하고 snapshot deep link를 열 뿐 state/rule/p95/p99/endpoint priority를 재계산하지 않는다.

## 12. Non-Goals

- Epic 2 또는 Epic 3에서 구현하기
- 별도 `operational_events` table 생성
- raw snapshot explorer
- arbitrary time-series query
- raw bucket explorer
- 30초 dashboard snapshot 장기 보관
- endpoint별 장기 timeseries table
- materialized view, Redis queue, PostgreSQL outbox를 MVP history source로 도입
- UI-side state/rule/p95/p99/endpoint priority recomputation
- alert delivery log와 operational event history 병합
- endpoint별 p95/p99 계산 또는 endpoint p99 alert 기준
- per-request latency sample, trace id, app/project rollup용 raw percentile payload 추가
- heartbeat success/failure/missing을 operational event로 승격
