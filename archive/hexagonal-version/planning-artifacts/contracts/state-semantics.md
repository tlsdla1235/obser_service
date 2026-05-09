---
artifactType: contract
name: state-semantics
architectureStyle: Lightweight Hexagonal
status: party-mode-fixes-applied
date: 2026-05-08
---

# Contract - State Semantics

## 1. 목적

이 계약은 첫 화면의 상태 언어를 고정한다.

UI, rule engine, endpoint priority는 서로 다른 state 의미를 만들지 않는다. 단일 판단은 portal application/domain에서 수행하고 dashboard read model로 전달한다.

## 2. States

| State | 의미 | UI 책임 |
|---|---|---|
| `waiting_first_data` | project/application은 등록됐지만 아직 accepted bucket이 없다 | 설치 직후 대기와 확인 행동을 보여준다 |
| `unknown` | 데이터가 충분하지 않아 상태를 단정할 수 없다 | 단정하지 않고 필요한 데이터 조건을 설명한다 |
| `idle` | 요청 수가 낮아 anomaly 판단을 하지 않는다 | 정상/장애 단정 대신 traffic 부족을 설명한다 |
| `active` | freshness와 sample이 충분하고 치명 신호가 없다 | 핵심 숫자와 0-insight 이유를 보여준다 |
| `stale` | 최근 accepted bucket이 90초 이상 없다 | ingest 경로 확인을 우선 제안한다 |
| `down` | 최근 accepted bucket이 180초 이상 없다 | 앱 실행 상태와 ingest 경로 확인을 우선 제안한다 |
| `degraded` | freshness는 충분하지만 error/latency/saturation concern이 있다 | 상위 0~3개 triage card를 보여준다 |

## 3. Evaluation Order

1. accepted bucket 존재 여부
2. freshness
3. 최소 sample 수
4. availability 상태
5. error/latency/saturation concern
6. 0-insight 또는 degraded summary

freshness가 부족하면 stale/down 계열 평가가 우선하고, 일반 비교형 rule은 억제한다.

## 4. Transition Criteria

| From | To | 조건 |
|---|---|---|
| none | `waiting_first_data` | project/application 등록 후 accepted bucket이 0개 |
| `waiting_first_data` | `unknown` | 첫 accepted bucket은 있으나 current window sample이 최소 기준 미달 |
| `waiting_first_data` | `idle` | accepted bucket은 있으나 traffic이 idle 기준 이하 |
| `waiting_first_data` | `active` | freshness와 sample이 충분하고 concern candidate가 0개 |
| any | `stale` | 마지막 accepted bucket `endUtc`가 query 시점보다 90초 이상 과거 |
| any | `down` | 마지막 accepted bucket `endUtc`가 query 시점보다 180초 이상 과거 |
| `stale` | `unknown` | 새 bucket이 수용됐지만 recovery 판단에 필요한 sample이 아직 부족 |
| `down` | `unknown` | 새 bucket이 수용됐지만 recovery 판단에 필요한 sample이 아직 부족 |
| `active` | `degraded` | freshness/sample guard를 통과한 concern candidate가 1개 이상 |
| `degraded` | `active` | concern candidate가 0개이고 zero-insight reason이 `no_action_needed` |

## 5. Recovery

stale/down 이후 새 bucket이 수용되면 즉시 active/degraded로 단정하지 않는다.

최소 sample 조건을 만족하기 전까지는 `unknown`과 read model의 `recovery` field로 표현한다. MVP UI state enum에는 별도 최상위 state를 늘리지 않고, recovery 안내 문구로 처리한다.

Recovery field는 아래 입력을 제공해야 한다.

- `isRecovering`
- `lastHealthyAt`
- `retryAfterSeconds`
- `recommendedAction`

## 6. Hexagonal Boundary

- `EvaluateLifecycleStateUseCase`가 state를 결정한다.
- UI는 state를 재판정하지 않는다.
- persistence adapter는 state를 저장할 수 있지만 계산하지 않는다.
