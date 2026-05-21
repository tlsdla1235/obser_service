---
artifactType: contract
name: state-semantics
architectureStyle: Traditional MVC
status: mvc-version-generated
date: 2026-05-09
---

# Contract - State Semantics MVC Version

## 1. 목적

이 계약은 첫 화면의 상태 언어를 고정한다.

UI, controller, repository는 서로 다른 state 의미를 만들지 않는다. 단일 판단은 portal service layer에서 수행하고 dashboard read model로 전달한다.

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
| `active` | `degraded` | freshness/sample guard와 degraded enter hysteresis를 통과한 concern candidate가 1개 이상 |
| `degraded` | `active` | degraded resolve hysteresis를 통과하고 zero-insight reason이 `no_action_needed` |

### 4.1 Degraded Hysteresis

Spike가 튀었다가 사라지는 구간에서 dashboard current state와 operational history가 과도하게 흔들리지 않도록 degraded 진입 기준과 해소 기준은 다르게 둔다.

Degraded enter 기준은 아래 조건을 모두 만족해야 한다.

1. rule별 freshness, sample, baseline, absolute threshold guard 통과
2. concern confidence `>= 0.75`
3. 최근 5개 30초 bucket 중 3개 이상 bad 상태

30초 단발 blip은 `degraded` state로 만들지 않는다.

Degraded resolve 기준은 아래 조건 중 하나가 `5 consecutive buckets` 동안 유지될 때 통과한다.

1. concern absence
2. concern confidence `< 0.60`
3. rule별 recovery/threshold 하회

Resolve 기준은 enter보다 보수적으로 두어 flapping을 줄인다. 같은 `application + endpointKey + ruleId` concern을 operational history event로 승격할 때는 `60분` suppression window를 적용한다.

이 기준은 `LifecycleStateService`와 operational history service의 계약이며, UI가 state transition이나 recovery를 직접 계산하지 않는다.

## 5. Operational Event Candidate Boundary

State transition은 current state를 대체하지 않지만 operational event 후보가 될 수 있다.

예를 들어 `active -> degraded`, `any -> stale`, `any -> down`, `stale/down -> unknown`, `degraded -> active` 같은 변화는 bounded history surface에서 최근 운영 흐름으로 노출할 수 있다.

이 후보는 `operational-event-history.md` contract에 따라 dashboard snapshot/read model 결과에서 service layer가 파생한다. UI는 transition table을 복제하거나 현재 상태를 재판정하지 않는다.

## 6. Recovery

stale/down 이후 새 bucket이 수용되면 즉시 active/degraded로 단정하지 않는다.

최소 sample 조건을 만족하기 전까지는 `unknown`과 read model의 `recovery` field로 표현한다. MVP UI state enum에는 별도 최상위 state를 늘리지 않고, recovery 안내 문구로 처리한다.

Recovery field는 아래 입력을 제공해야 한다.

- `isRecovering`
- `lastHealthyAt`
- `retryAfterSeconds`
- `recommendedAction`

## 7. MVC Boundary

- `LifecycleStateService`가 state를 결정한다.
- `DashboardReadModelService`가 state 결과를 read model에 담는다.
- UI는 state를 재판정하지 않는다.
- repository는 state를 저장할 수 있지만 계산하지 않는다.
- Epic 5/6의 operational history service 후보는 저장된 state 결과를 event 후보로 읽을 수 있지만 current state를 다시 계산하지 않는다.
