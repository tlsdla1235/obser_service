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

Operational Event History는 raw snapshot explorer가 아니다. 현재 상태 판단을 대체하지 않으며, `DashboardReadModelService`가 만든 state/rule/evidence 결과를 최근 운영 이력으로 요약한다.

## 2. 용어

| 용어 | 의미 |
|---|---|
| current dashboard read model | query 시점 기준 current/baseline 15분 window로 만든 현재 상태 source of truth |
| dashboard snapshot | `dashboard_snapshots`에 저장된 특정 시점의 read model 결과 |
| operational event | snapshot/read model 결과에서 service layer가 파생한 bounded 운영 사건 |
| recent operational history | 사용자-facing 표현인 최근 운영 이력 |
| alert delivery log | Discord 등 알림 전송 성공/실패 기록. operational event history와 섞지 않는다 |

## 3. Event Candidate Source

Operational event 후보는 아래 source에서만 나온다.

- `dashboard_snapshots.generated_at`, `state_code`, `read_model_json`
- state transition 또는 notable state period
- high-confidence degraded concern 또는 alertable concern
- stale/down 진입과 recovery 관찰
- 충분한 sample guard를 통과한 auxiliary tail latency evidence

후보 생성은 UI, controller, repository가 아니라 portal service layer에서 수행한다.

## 4. Event Types

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

## 5. Bounded Query Rules

history 조회 horizon은 current/baseline 판단 window와 다르다.

- current 15분과 baseline 15분은 현재 상태 판단 전용이다.
- recent history는 최근 24시간 또는 `limit` 기반으로 bounded 조회한다.
- 기본 후보는 `since=24h`, `limit=50`이다.
- service는 과도한 `since`와 `limit`을 서버 정책으로 clamp한다.
- retention으로 snapshot이 삭제된 구간은 history가 비어 있을 수 있다.

이 API는 arbitrary time-series query, raw bucket query, raw snapshot list를 제공하지 않는다.

## 6. Deduplication and Suppression Rules

Operational event는 노이즈를 줄이기 위해 아래 규칙을 따른다.

- 연속된 같은 state period는 하나의 event period로 접는다.
- 같은 endpoint와 같은 rule의 반복 concern은 suppression window 안에서 중복 event로 만들지 않는다.
- low-confidence candidate는 event로 승격하지 않는다.
- minimum sample guard를 통과하지 못한 candidate는 event로 승격하지 않는다.
- 같은 snapshot에서 여러 concern이 있으면 사용자에게 가장 행동 가능한 concern을 우선한다.

## 7. Event Shape

응답 item은 구현 시 아래 의미를 포함해야 한다.

```json
{
  "eventId": "snapshot:018f:degraded:endpoint_latency_spike",
  "type": "degraded_entered",
  "severity": "warning",
  "title": "POST /orders 응답 지연 증가",
  "summary": "오류율과 p95가 함께 증가했습니다.",
  "occurredAt": "2026-05-14T07:42:30Z",
  "resolvedAt": "2026-05-14T08:05:00Z",
  "stateCode": "degraded",
  "confidence": 0.84,
  "snapshotId": "018f6b9a-2e1a-7d2b-9b2f-4db69d92c241",
  "evidence": {
    "ruleId": "endpoint_latency_spike",
    "affectedEndpoint": "POST /orders",
    "requestCount": 12000,
    "p95Ms": 720,
    "p99Ms": 1400,
    "tailLatencyEvidence": "auxiliary"
  },
  "links": {
    "snapshot": "/api/projects/{projectId}/applications/{applicationId}/dashboard/snapshots/{snapshotId}"
  }
}
```

`eventId`는 MVP에서 snapshot 기반 derived id일 수 있다. event별 durable external id, acknowledgement, owner assignment가 필요해지기 전에는 별도 event table을 만들지 않는다.

## 8. Snapshot and Deep Link

각 operational event는 가능한 경우 snapshot/read model detail deep link를 가진다.

Snapshot detail은 저장된 read model을 보여주는 경계다. detail 조회가 current state를 재판정하거나 history event를 새로 계산하는 경로가 되어서는 안 된다.

Retention으로 snapshot이 삭제된 경우 detail endpoint는 `404`를 반환할 수 있다. UI는 이 상황을 event 자체의 오류로 해석하지 않는다.

## 9. p99 and Tail Latency Evidence Rules

p95는 latency primary judgment다.

p99 또는 tail latency는 같은 cumulative histogram merge 결과에서 service layer가 계산할 수 있는 auxiliary evidence다. p99는 단독으로 degraded/down 판단을 만들지 않으며, p95/error/saturation evidence와 함께 있을 때 confidence를 보조하는 근거로만 쓴다.

p99 노출은 충분한 request count guard를 통과해야 한다. guard를 통과하지 못하면 p99를 숨기거나 `tailLatencyEvidence = insufficient_sample`로 표시한다.

p99를 위해 starter metric payload를 확장하지 않는다.

## 10. MVC Boundary

Epic 5/6 후보 구현 위치는 아래 중 하나로 둔다.

- `domain.dashboard.service.OperationalEventHistoryService`
- `domain.history.service.OperationalEventHistoryService`

MVP에서는 `DashboardSnapshotRepository`를 재사용한다. 별도 `OperationalEventRepository`나 `operational_events` table은 만들지 않는다.

Controller는 history read model을 serialize하고 HTTP status를 mapping한다. UI는 event를 표시하고 snapshot deep link를 열 뿐 state/rule/p95/p99/endpoint priority를 재계산하지 않는다.

## 11. Non-Goals

- Epic 2 또는 Epic 3에서 구현하기
- 별도 `operational_events` table 생성
- raw snapshot explorer
- arbitrary time-series query
- raw bucket explorer
- UI-side state/rule/p95/p99/endpoint priority recomputation
- alert delivery log와 operational event history 병합
- p99 단독 degraded/down 판단
- per-request latency sample, trace id, raw percentile payload 추가
