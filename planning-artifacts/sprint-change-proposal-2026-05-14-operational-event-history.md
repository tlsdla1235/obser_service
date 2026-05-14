---
artifactType: sprint-change-proposal
projectName: Spring Boot 운영 첫 화면 포털
architectureStyle: Traditional MVC
status: proposed
date: 2026-05-14
changeTrigger: Operational Event History / Recent State History 도입 검토
relatedProposal: 별도 Route Attribution B안 proposal과 병합하지 않음
---

# Sprint Change Proposal - Operational Event History 도입

## 1. Issue Summary

현재 dashboard first-screen 판단 모델은 query 시점 기준 `최근 15분 current window`와 그 직전 `15분 baseline window`를 비교해 현재 상태를 판단한다. 이 모델은 유지한다.

문제는 운영 UX에서 사용자가 Discord 알림이나 외부 신호를 받고 30분에서 1시간 뒤에 대시보드에 들어오는 경우가 많다는 점이다. 그 시점에는 current 15분 window만 보면 이미 회복된 뒤일 수 있어, 사용자는 "아까 무슨 일이 있었는지"를 first-screen에서 이해하기 어렵다.

또 실제 운영 문제는 서버 완전 down보다 아래와 같은 degraded 상태로 더 자주 나타날 가능성이 높다.

- 서버가 무거워짐
- latency 증가
- p95/p99 또는 tail latency 악화
- 특정 endpoint 지연
- DB pool 사용률 상승
- CPU 상승
- heap pressure
- error rate와 latency가 함께 증가

따라서 새로 필요한 것은 raw snapshot explorer가 아니라, 사용자가 늦게 들어와도 최근 운영 흐름을 이해할 수 있는 bounded recent operational event history다.

## 2. Constraints and Non-Negotiables

- 현재 상태 판단 모델은 유지한다.
- current window는 최근 15분이다.
- baseline window는 current 직전 15분이다.
- 현재 상태 판단은 계속 `DashboardReadModelService`가 담당한다.
- UI는 state, rule, p95/p99, endpoint priority를 재계산하지 않는다.
- Epic 2에는 p95, p99, state, rule, read model, history 구현을 끌어오지 않는다.
- 기존 Route Attribution B안 proposal과 병합하지 않는다.
- active 구현 기준은 `planning-artifacts/`와 `implementation-artifacts/`다.
- `bmad-restart-context-pack/`는 제품 문제와 UX intent 참고용으로만 사용한다.
- Traditional MVC + Service/Repository Layering을 유지한다.
- Hexagonal, port, adapter, application package 구조로 되돌리지 않는다.

## 3. Impact Analysis

### Checklist Status

| ID | Status | Findings |
|---|---|---|
| 1.1 | [x] Done | 트리거는 운영자가 알림 이후 늦게 들어왔을 때 현재 15분 상태만으로 과거 degraded/down/recovery 맥락을 이해하기 어렵다는 UX gap이다. |
| 1.2 | [x] Done | 문제 유형은 새 요구사항과 UX gap 보정이다. 기존 current/baseline 판단 모델 실패가 아니라 history surface 부재다. |
| 1.3 | [x] Done | 근거는 30분~1시간 지연 진입 시나리오, degraded 상태 빈도, Discord deep link 필요성이다. |
| 2.1 | [x] Done | 현재 Epic 2는 계속 완료 가능하다. 이 변경을 Epic 2로 당기면 안 된다. |
| 2.2 | [x] Done | 새 epic을 만들기보다는 Epic 4/5/6 문서와 contract 보정으로 처리하는 편이 적절하다. |
| 2.3 | [x] Done | Epic 4는 state transition event 기준, Epic 5는 snapshot/read model/history API, Epic 6은 UI와 alert deep link에 영향을 받는다. |
| 2.4 | [x] Done | 기존 Epic을 무효화하지 않는다. |
| 2.5 | [x] Done | Epic 순서 변경은 필요 없다. Epic 5 착수 전 문서 보정이 필요하다. |
| 3.1 | [!] Action-needed | 활성 PRD 파일은 발견되지 않았다. 활성 Epics/Architecture/Contracts와 UX intent를 PRD 대체 기준으로 검토했다. |
| 3.2 | [x] Done | Architecture, API, DB schema, read model, state, insight, histogram contract 보정이 필요하다. |
| 3.3 | [x] Done | UX는 recent history strip/list, event deep link, alert surface 연결이 필요하다. |
| 3.4 | [x] Done | acceptance traceability와 sprint plan exclusion guard 보정이 필요하다. |
| 4.1 | [x] Viable | Direct Adjustment가 가능하다. 노력 Medium, 위험 Medium. |
| 4.2 | [x] Not viable | rollback은 필요 없다. |
| 4.3 | [x] Not viable | MVP 축소가 아니라 current model을 유지한 보조 이력 surface 추가다. |
| 4.4 | [x] Done | 권장 경로는 proposal 우선 작성 후 Epic 5 전 문서 반영이다. |
| 5.1-5.5 | [x] Done | 이 문서에 issue, impact, recommended approach, action plan, handoff를 포함한다. |
| 6.1-6.5 | [!] Action-needed | 사용자 승인 후 contracts/api/schema/epics 등 실제 구현 기준 문서 수정 여부를 결정한다. |

### Epic Impact

#### Epic 2. Starter Direct Ingest Producer

영향 없음.

Epic 2는 starter가 low-cardinality metric을 30초 bucket으로 만들고 비동기로 전송하는 범위다. Operational Event History는 portal service/read model/API/UI 영역이므로 Epic 2에 포함하지 않는다.

Epic 2 제외 범위에는 아래 항목을 유지하거나 더 명확히 추가한다.

- p95/p99 계산
- lifecycle state 판단
- insight rule 평가
- dashboard read model 생성
- dashboard snapshot 저장/조회
- operational event history 생성/조회
- alert deep link 생성

#### Epic 3. Portal Ingest Acceptance

직접 영향은 작다.

`accepted_metric_buckets`는 계속 source bucket 저장소 역할을 한다. Operational Event History는 accepted bucket을 직접 raw explorer로 노출하지 않고, Epic 5의 dashboard snapshot/read model 결과에서 파생하는 편이 안전하다.

Epic 3에서는 operational event를 저장하거나 계산하지 않는다.

#### Epic 4. State Semantics and Time Windows

중간 영향이 있다.

Epic 4는 `waiting_first_data`, `unknown`, `idle`, `active`, `stale`, `down`, `degraded`, recovery guidance를 정의한다. Operational Event History는 이 state 결과의 transition 또는 notable state period를 event 후보로 사용한다.

필요한 보정:

- current/baseline window는 현재 상태 판단 전용임을 명시한다.
- event history window는 별도 query horizon이다. 예: 최근 24시간 또는 최근 N개.
- state transition, stale/down 진입, recovery 관찰, degraded 진입/해소가 event 후보가 될 수 있음을 명시한다.
- event 생성은 UI가 아니라 portal service layer에서 수행한다.

#### Epic 5. Triage Summary and Endpoint Priority

가장 큰 영향이 있다.

Epic 5는 histogram merge, insight rule, triage summary, endpoint priority, dashboard query API를 구현한다. Operational Event History는 이 계산 결과를 raw snapshot이 아니라 bounded event로 다시 요약하는 read model surface다.

필요한 보정:

- `DashboardReadModelService`는 현재 상태 read model source of truth로 유지한다.
- `OperationalEventHistoryService` 후보를 추가한다.
- history service는 `dashboard_snapshots` 또는 read model 결과를 읽어 event 목록을 파생한다.
- event item은 해당 시점 snapshot/read model detail deep link를 가져야 한다.
- p99/tail latency는 primary judgment가 아니라 auxiliary evidence로만 포함한다.

#### Epic 6. First-Screen Delivery and Demo Hardening

중간 영향이 있다.

Epic 6 UI는 first-screen에 recent operational history surface를 추가할 수 있다. 다만 first-screen의 주인공은 계속 current state strip과 top triage다. history는 "아까 무슨 일이 있었는지"를 확인하는 보조 surface다.

필요한 보정:

- 첫 화면에는 최근 24시간 또는 최근 N개 event만 보여준다.
- raw snapshot 목록을 전체 노출하지 않는다.
- event 클릭 시 snapshot/read model detail로 이동한다.
- Discord 알림은 event 또는 snapshot deep link를 포함할 수 있다.

## 4. Artifact Impact and Edit Proposals

### `planning-artifacts/contracts/time-buckets.md`

수정 필요 여부: 필요

수정 이유:

현재 문서는 current/baseline window 의미를 잘 고정하고 있다. 여기에 history window가 current/baseline 판단 모델을 대체하지 않는다는 경계를 추가해야 한다.

추가/변경해야 할 핵심 문장:

```markdown
Operational event history의 조회 horizon은 current/baseline 판단 window와 다르다. current 15분과 baseline 15분은 현재 상태 판단 전용이며, recent history는 최근 24시간 또는 limit 기반으로 이미 생성된 snapshot/read model 결과를 bounded event로 요약한다.
```

변경 규모: 소

추천: 기존 문서에 짧은 경계 문장 추가

### `planning-artifacts/contracts/state-semantics.md`

수정 필요 여부: 필요

수정 이유:

Operational Event History의 주요 event type은 state transition과 recovery 흐름에서 나온다. state semantics가 event 후보가 되는 조건을 명시해야 한다.

추가/변경해야 할 핵심 문장:

```markdown
State transition은 current state를 대체하지 않지만 operational event 후보가 될 수 있다. `active -> degraded`, `any -> stale`, `any -> down`, `stale/down -> unknown recovery`, `degraded -> active` 같은 변화는 bounded history surface에서 최근 운영 흐름으로 노출할 수 있다.
```

변경 규모: 중

추천: 기존 문서 보정 + 새 `operational-event-history.md` contract 참조

### `planning-artifacts/contracts/insight-rules.md`

수정 필요 여부: 필요

수정 이유:

모든 insight candidate를 history event로 만들면 alert fatigue와 noise가 커진다. 고신뢰 concern만 event 승격 대상이라는 기준이 필요하다.

추가/변경해야 할 핵심 문장:

```markdown
Operational event로 승격되는 concern은 고신뢰 candidate로 제한한다. low-confidence candidate, minimum sample guard를 통과하지 못한 candidate, 중복된 endpoint concern은 history event로 만들지 않는다.

p99/tail latency는 primary rule judgment가 아니라 auxiliary evidence다. p99 단독으로 장애를 단정하지 않으며, 충분한 request count와 p95/error/saturation evidence가 함께 있을 때 confidence를 높이는 근거로만 사용한다.
```

변경 규모: 중

추천: 기존 문서 보정 + 새 contract 참조

### `planning-artifacts/contracts/read-model-contract.md`

수정 필요 여부: 필요

수정 이유:

현재 read model은 first-screen current state의 source of truth다. history 목록을 이 응답에 직접 크게 넣으면 책임이 흐려질 수 있다. 대신 snapshot/detail 식별자와 deep link 경계를 추가하는 편이 안전하다.

추가/변경해야 할 핵심 문장:

```markdown
Dashboard read model은 현재 상태 source of truth다. Operational event history는 이 응답 안에서 재계산하지 않으며, 별도 history read model/API가 dashboard snapshot 또는 read model 결과를 기반으로 bounded event 목록을 제공한다.

각 dashboard snapshot/read model은 deep link 대상이 될 수 있는 stable identifier를 가져야 한다.
```

변경 규모: 중

추천: 기존 문서 보정, history response shape는 새 contract에 둠

### `planning-artifacts/contracts/histogram-merge.md`

수정 필요 여부: 필요

수정 이유:

현재 문서는 p95 source of truth로 작성되어 있다. p99를 auxiliary evidence로 쓰려면 starter 수집 변경 없이 같은 cumulative histogram bucket을 재사용해 quantile을 계산할 수 있음을 명시해야 한다.

추가/변경해야 할 핵심 문장:

```markdown
MVP primary judgment quantile은 p95다. 동일 cumulative histogram merge algorithm은 p99 같은 auxiliary quantile 계산에도 사용할 수 있지만, p99는 충분한 sample guard를 통과한 경우에만 evidence로 노출한다. p99는 단독으로 degraded/down 판단을 만들지 않는다.
```

변경 규모: 중

추천: "p95 전용"에서 "server-side quantile merge, p95 primary"로 확장

### `planning-artifacts/contracts/metric-taxonomy.md`

수정 필요 여부: 필요

수정 이유:

p99와 tail latency를 위해 starter metric taxonomy를 넓히면 Epic 2와 ingest payload가 흔들린다. 기존 HTTP server duration cumulative histogram bucket을 재사용한다는 경계가 필요하다.

추가/변경해야 할 핵심 문장:

```markdown
Tail latency evidence는 별도 starter metric 추가 없이 HTTP server duration cumulative histogram bucket에서 portal service layer가 계산한다. p99를 위해 raw percentile, per-request sample, trace id, arbitrary latency distribution payload를 추가하지 않는다.
```

변경 규모: 소

추천: 기존 문서 보정

### `planning-artifacts/api-surface.md`

수정 필요 여부: 필요

수정 이유:

history는 current dashboard API와 다른 조회 목적을 가진다. API surface를 작게 추가해야 한다.

추가/변경해야 할 핵심 문장:

```http
GET /api/projects/{projectId}/applications/{applicationId}/operational-events?since=24h&limit=50
Accept: application/json
```

```http
GET /api/projects/{projectId}/applications/{applicationId}/dashboard/snapshots/{snapshotId}
Accept: application/json
```

```markdown
Operational events API는 raw bucket explorer가 아니다. state transition, degraded/down/stale/recovery, 고신뢰 concern만 bounded event로 반환한다.
```

변경 규모: 중

추천: 기존 문서 보정 + 새 contract response shape 참조

### `planning-artifacts/database-schema.md`

수정 필요 여부: 필요

수정 이유:

핵심 결정은 지금 새 `operational_events` 테이블을 만들지 않는 것이다. 이 판단을 schema 문서에 명시해 scope creep을 막아야 한다.

추가/변경해야 할 핵심 문장:

```markdown
MVP operational event history는 별도 `operational_events` 테이블 없이 `dashboard_snapshots`의 `generated_at`, `state_code`, `read_model_json`을 기반으로 service layer에서 파생한다. 별도 테이블은 alert acknowledgement, durable event id, 장기 event retention, event delivery dedupe가 필요해질 때 후속 확장으로 검토한다.
```

변경 규모: 중

추천: 기존 문서 보정, 신규 테이블은 보류

### `planning-artifacts/architecture.md`

수정 필요 여부: 필요

수정 이유:

새 service 책임을 MVC 구조 안에 넣어야 한다. 단일 source of truth와 service/repository 경계는 유지한다.

추가/변경해야 할 핵심 문장:

```markdown
`OperationalEventHistoryService`는 dashboard snapshot/read model 결과를 기반으로 bounded recent operational event history를 구성한다. 이 service는 current state를 재판정하지 않고, `DashboardReadModelService`가 만든 state/rule/evidence 결과를 event surface로 요약한다.
```

변경 규모: 중

추천: 기존 문서 보정

### `planning-artifacts/architecture-implementation-supplement.md`

수정 필요 여부: 필요

수정 이유:

feature-first MVC package와 service/repository 배치 기준에 history service/repository 후보를 반영해야 한다.

추가/변경해야 할 핵심 문장:

```markdown
Operational event history는 `domain.dashboard.service` 또는 별도 `domain.history.service`에 둘 수 있다. MVP에서는 snapshot repository를 재사용하고, 별도 event repository/table은 만들지 않는다.
```

변경 규모: 중

추천: 기존 문서 보정

### `planning-artifacts/epics.md`

수정 필요 여부: 필요

수정 이유:

Epic 4/5/6에 영향이 있다. Epic 2는 제외로 고정해야 한다.

추가/변경해야 할 핵심 문장:

```markdown
Operational event history는 Epic 5/6에서 dashboard snapshot/read model 기반으로 추가한다. Epic 2와 Epic 3에서는 history 계산이나 history API를 구현하지 않는다.
```

변경 규모: 중

추천: 기존 문서 보정

### `planning-artifacts/sprint-plan.md`

수정 필요 여부: 필요

수정 이유:

현재 Epic 2 Sprint Plan은 p95/state/rule/read model을 제외하고 있다. 여기에 history와 p99 판단을 추가해 Epic 2 scope creep을 막아야 한다.

추가/변경해야 할 핵심 문장:

```markdown
Operational event history, p99/tail latency judgment, dashboard snapshot history, alert deep link는 Epic 2 범위가 아니다.
```

변경 규모: 소

추천: 기존 문서 보정

### `planning-artifacts/acceptance-traceability.md`

수정 필요 여부: 필요

수정 이유:

history가 제품 약속으로 들어오면 test/AC traceability가 필요하다.

추가/변경해야 할 핵심 문장:

```markdown
Operational event history는 raw snapshot explorer가 아니라 bounded event surface다. UI는 event를 표시할 뿐 state/rule/p95/p99/endpoint priority를 재계산하지 않는다.
```

변경 규모: 중

추천: 기존 문서 보정

### `bmad-restart-context-pack/ux-design-specification.md`

수정 필요 여부: 없음

수정 이유:

이 문서는 active 구현 기준이 아니라 UX intent 참고용이다. Alert Surface, Alert Delivery Context, Discord deep link 의도는 proposal 근거로 활용하되 직접 수정하지 않는다.

변경 규모: 없음

추천: active 문서에만 반영

## 5. New Contract Recommendation

새 contract 문서를 만드는 것을 추천한다.

권장 파일:

```text
planning-artifacts/contracts/operational-event-history.md
```

추천 이유:

- 기존 문서에는 event history 전용 source of truth가 없다.
- `read-model-contract.md`에 history response까지 넣으면 first-screen current read model 책임이 커진다.
- `state-semantics.md`에 history 규칙을 모두 넣으면 state 판단과 event projection이 섞인다.
- `api-surface.md`에 shape를 직접 길게 두면 event selection/dedupe/confidence 기준이 흩어진다.

새 contract가 가져야 할 최소 목차:

1. 목적과 경계
2. 용어
3. Event Type
4. Event Candidate Source
5. Bounded Query Rules
6. Deduplication and Suppression Rules
7. Event Shape
8. Snapshot/Read Model Deep Link
9. p99/Tail Latency Evidence Rules
10. MVC Boundary
11. Non-Goals

## 6. Recommended Terminology

### 후보 비교

| 후보 | 장점 | 단점 | 판단 |
|---|---|---|---|
| `snapshot history` | 기존 `dashboard_snapshots`와 연결이 쉽다 | raw snapshot explorer로 오해하기 쉽다 | 비추천 |
| `recent state history` | 사용자가 이해하기 쉽다 | latency/error/saturation concern까지 담기에는 좁다 | 사용자-facing 보조 용어로 적합 |
| `operational event history` | state change와 degraded concern을 함께 담기 좋다 | 사용자에게는 약간 기술적으로 들릴 수 있다 | 내부/contract 용어로 추천 |
| `alert/event history` | Discord alert surface와 연결된다 | alert가 없는 event와 alert delivery log가 섞인다 | 비추천 |

### 결정

사용자-facing 용어:

- 한국어: `최근 운영 이력`
- 보조 표현: `최근 상태 이력`

내부/contract 용어:

- `Operational Event History`
- `OperationalEventHistoryService`
- `OperationalEventHistoryReadModel`

주의:

- `snapshot history`는 내부 구현 설명에서만 제한적으로 사용한다.
- `alert history`는 Discord delivery log에만 사용하고 operational event history와 분리한다.

## 7. Minimal-Invasive Design

### DB Schema 변경 여부

MVP에서는 DB schema 변경을 권장하지 않는다.

이유:

- `dashboard_snapshots`는 이미 `generated_at`, `state_code`, `read_model_json`을 가진다.
- snapshot은 service layer가 계산한 state/rule/evidence 결과를 보존한다.
- 최근 24시간 또는 최근 N개 history는 snapshot 기반 파생으로 충분하다.
- 별도 event table은 event lifecycle 기능이 필요해질 때 의미가 있다.

### 기존 `dashboard_snapshots`로 충분한가

MVP에서는 충분하다.

가능한 파생 방식:

1. application의 최근 `dashboard_snapshots`를 `generated_at desc` 또는 24시간 horizon으로 조회한다.
2. 각 snapshot의 `state_code`와 `read_model_json`에서 event candidate를 만든다.
3. 같은 state/concern이 연속될 경우 하나의 event period로 dedupe한다.
4. `active -> degraded`, `degraded -> active`, `any -> stale/down`, recovery observation, high-confidence concern만 반환한다.
5. 각 event는 `snapshotId` 또는 snapshot detail URL을 가진다.

주의:

- 이 방식은 current status를 재계산하지 않는다.
- DB view/materialized view/stored procedure에 event 계산을 숨기지 않는다.
- UI는 snapshot JSON을 분석해 event를 만들지 않는다.

### 별도 `operational_events` 테이블 필요 여부

지금은 필요 없다.

후속 확장 조건:

- event acknowledgement가 필요하다.
- event별 stable external id가 필요하다.
- alert delivery dedupe 또는 suppression window를 durable하게 저장해야 한다.
- event retention이 snapshot retention과 달라져야 한다.
- event annotation, owner assignment, resolution note가 필요하다.

그 전까지는 별도 테이블을 만들지 않는다.

### API Surface 최소 추가

권장 API:

```http
GET /api/projects/{projectId}/applications/{applicationId}/operational-events?since=24h&limit=50
Accept: application/json
```

응답 예시:

```json
{
  "generatedAt": "2026-05-14T08:30:00Z",
  "applicationId": "5c942671-e251-4f7f-b610-18ae6ca4ef65",
  "horizon": {
    "since": "2026-05-13T08:30:00Z",
    "limit": 50
  },
  "events": [
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
  ]
}
```

Snapshot detail 후보:

```http
GET /api/projects/{projectId}/applications/{applicationId}/dashboard/snapshots/{snapshotId}
Accept: application/json
```

이 endpoint는 해당 snapshot의 stored read model을 반환한다. 없거나 retention으로 삭제된 경우 `404`를 반환한다.

### Read Model Contract 확장

권장:

- current dashboard response에는 큰 history 배열을 넣지 않는다.
- current response에는 `snapshotId` 또는 `links.selfSnapshot` 정도만 추가한다.
- history 목록은 별도 API/read model에서 제공한다.

핵심 문장:

```markdown
Dashboard read model은 현재 상태를 표현한다. Operational event history는 별도 read model이며, current read model의 state/rule/evidence 결과를 재사용하되 UI나 controller에서 재계산하지 않는다.
```

## 8. p99 / Tail Latency Policy

### MVP 포함 여부

MVP primary judgment에는 포함하지 않는다.

단, Epic 5에서 `HistogramMergeService`를 구현할 때 p99 계산이 같은 cumulative histogram merge 알고리즘으로 자연스럽게 가능하다면 auxiliary evidence로 열 수 있다.

### p95와의 관계

- p95는 primary latency judgment다.
- p99는 tail latency auxiliary evidence다.
- p99는 단독으로 degraded/down을 만들지 않는다.
- p99는 p95, error rate, DB pool, CPU, heap pressure와 결합될 때 confidence를 높이는 보조 근거다.

### Minimum Sample Guard

초안:

- p99 계산/노출은 current window request count가 `tailLatencyMinRequestCount` 이상일 때만 허용한다.
- 비교형 p99 evidence는 current와 baseline 모두 minimum sample guard를 통과해야 한다.
- guard를 통과하지 못하면 p99를 숨기거나 `tailLatencyEvidence = insufficient_sample`로 둔다.
- 초기 기본값은 Epic 5 구현 전 조정한다. proposal 기준으로는 15분 window에서 1000 requests 이상을 후보값으로 둔다.

주의:

- low traffic endpoint의 p99는 매우 흔들릴 수 있다.
- p99 단독 alert/event 승격은 금지한다.
- p99는 endpoint priority ranking을 보조할 수 있지만 단독 rank reason이 되면 안 된다.

### Histogram Merge Contract 방향

권장:

- `histogram-merge.md`를 "p95 전용"에서 "server-side quantile merge"로 확장한다.
- 문서 제목은 유지해도 된다.
- 본문에서 `p95 primary`, `p99 auxiliary`를 명확히 분리한다.
- starter 수집 단계는 변경하지 않는다.

## 9. Detailed Change Proposals

### New Contract: `operational-event-history.md`

Proposed content outline:

```markdown
# Contract - Operational Event History MVC Version

## 1. 역할

Operational Event History는 raw snapshot explorer가 아니라, 최근 운영 흐름을 bounded event로 요약하는 read model contract다.

## 2. Event Sources

- dashboard snapshot state transition
- stale/down/recovery transition
- high-confidence degraded concern
- high-confidence alertable concern

## 3. Event Types

- `state_changed`
- `degraded_entered`
- `degraded_resolved`
- `stale_entered`
- `down_entered`
- `recovery_observed`
- `high_confidence_concern`
- `alert_sent`

## 4. Bounding

첫 화면은 최근 24시간 또는 최근 N개 event만 조회한다.

## 5. Non-Goals

- raw bucket explorer
- arbitrary time-series query
- UI-side state/rule/p95/p99 recomputation
- alert delivery log와 event history의 무분별한 병합
```

### API Surface

Add:

```markdown
## Operational Event History API

`GET /api/projects/{projectId}/applications/{applicationId}/operational-events`

이 API는 raw snapshot 목록이 아니라 bounded operational event read model을 반환한다.
```

### Database Schema

Add:

```markdown
MVP에서는 별도 `operational_events` 테이블을 만들지 않는다. Operational event history는 `dashboard_snapshots`에서 service layer가 파생한다.
```

### Epics

Add:

```markdown
Epic 5에 operational event history read model/API 보정을 추가한다.
Epic 6에 recent operational history UI surface와 snapshot deep link 보정을 추가한다.
Epic 2에는 포함하지 않는다.
```

### Acceptance Traceability

Add:

```markdown
| Bounded operational event history | Epic 5, 6 | `operational-event-history.md` | `OperationalEventHistoryService` | `OperationalEventHistoryReadModelTest` |
```

## 10. Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| raw snapshot explorer로 번짐 | MVP scope와 UX clarity 악화 | event type, 24h/limit, no arbitrary query로 제한 |
| current state와 history 혼동 | 사용자가 현재 상태를 오해 | current dashboard와 recent history copy/section 분리 |
| p99 noise | false positive 증가 | minimum sample guard, auxiliary-only, no p99-only event |
| DB schema scope creep | Epic 5 전 migration 부담 증가 | MVP에서는 `dashboard_snapshots` 기반 파생, event table 보류 |
| alert delivery log와 event history 혼합 | 용어/UX 혼란 | alert는 delivery surface, operational event는 state/concern surface로 분리 |
| UI 재계산 회귀 | source of truth 붕괴 | service layer read model만 사용, acceptance traceability 추가 |
| Epic 2 scope creep | 현재 sprint 안정성 저하 | sprint plan과 story guardrail에 history/p99 제외 명시 |

## 11. Implementation Handoff

### Scope Classification

Change scope: Moderate

MVP 목표와 architecture style은 바뀌지 않는다. 다만 Epic 5/6 contract, API, read model, UI surface에 영향을 주므로 Developer 단독 즉시 구현보다 Product Owner / Developer / Architect 문서 보정 흐름이 적절하다.

### Handoff Recipients

Product Owner / Developer:

- 새 contract 생성 여부 승인
- Epic 5/6 story 보정 승인
- Epic 2 exclusion guard 보정 승인

Architect:

- Traditional MVC + Service/Repository Layering 유지 확인
- `OperationalEventHistoryService` 위치 결정
- `dashboard_snapshots` 기반 파생 vs 별도 event table 보류 결정 확인

Developer:

- Epic 5에서 snapshot repository 기반 history read model 구현
- Epic 6에서 recent history UI surface와 deep link 구현
- p99 auxiliary evidence guard 구현 여부 결정 후 테스트 작성

### Suggested Implementation Order

1. 이 Sprint Change Proposal 승인
2. `operational-event-history.md` contract 생성
3. `time-buckets`, `state-semantics`, `insight-rules`, `read-model-contract`, `histogram-merge`, `metric-taxonomy` 보정
4. `api-surface`, `database-schema`, `architecture`, `architecture-implementation-supplement` 보정
5. `epics`, `sprint-plan`, `acceptance-traceability` 보정
6. Epic 5 착수 전 story split 또는 story AC 업데이트
7. Epic 6 UI/alert deep link 보정

## 12. Success Criteria

- current/baseline 15분 판단 모델은 그대로 유지된다.
- current state source of truth는 계속 `DashboardReadModelService`다.
- UI는 state/rule/p95/p99/endpoint priority를 재계산하지 않는다.
- operational history는 raw snapshot explorer가 아니다.
- first-screen history는 최근 24시간 또는 최근 N개로 bounded된다.
- 각 event는 snapshot/read model detail deep link를 가진다.
- Epic 2에는 history/p99/read model 구현이 들어가지 않는다.
- p99는 sufficient sample guard를 통과한 auxiliary evidence로만 쓰인다.
- p99 단독으로 장애나 degraded를 단정하지 않는다.
- MVP에서는 별도 `operational_events` 테이블을 만들지 않는다.
- Traditional MVC + Service/Repository Layering을 유지한다.

## 13. Final Recommendation

권장 결론은 **B. sprint-change-proposal만 만들고 구현 문서는 다음 Epic 전에 반영**이다.

이 변경은 제품 UX와 Epic 5/6 완성도에 중요하지만, 현재 Epic 2 구현 흐름을 중단할 만큼 긴급한 구현 변경은 아니다. 지금은 이 proposal로 방향과 경계를 고정하고, Epic 5 착수 전에 contract/API/schema/epic 문서를 반영하는 것이 가장 안전하다.

다음 단계 추천:

1. 사용자가 이 proposal을 승인한다.
2. 별도 다음 작업으로 active 문서 보정을 수행한다.
3. 문서 보정 시 새 `planning-artifacts/contracts/operational-event-history.md`를 생성한다.
4. 구현은 Epic 5/6 범위에서 수행한다.
