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

상태 판단은 두 축을 분리해서 다룬다.

1. **Metric data axis:** accepted bucket은 metric data freshness, current/baseline read model, state metric input의 source-of-truth다.
2. **Starter connection axis:** starter heartbeat는 starter/application process liveness, portal reachability, project key validity, metadata validity를 확인하는 control-plane source다.

Heartbeat 성공 또는 미수신은 metric bucket, dashboard snapshot, operational event, p95/p99, rule result, endpoint priority를 직접 만들지 않는다. 다만 `LifecycleStateService`와 `DashboardReadModelService`는 heartbeat telemetry가 구현된 뒤 이 값을 accepted bucket freshness와 별도 입력으로 받아 `starterConnection` 또는 diagnosis copy를 만들 수 있다.

요청이 없어 bucket이 오지 않는 상황은 host application down을 의미하지 않는다. 마지막 accepted bucket이 오래됐다는 사실은 "최근 metric data가 없다"는 data-plane freshness 신호이며, heartbeat 축이 살아 있으면 "starter connected but no accepted bucket", "waiting for traffic", "metric data idle" 계열로 표현한다.

## 2. States

| State | 의미 | UI 책임 |
|---|---|---|
| `waiting_first_data` | project/application은 등록됐지만 아직 accepted bucket이 없다 | 설치 직후 대기, starter 연결 여부, 트래픽 대기 상태를 분리해 보여준다 |
| `unknown` | 데이터가 충분하지 않아 상태를 단정할 수 없다 | 단정하지 않고 필요한 데이터 조건을 설명한다 |
| `idle` | 요청 수가 낮아 anomaly 판단을 하지 않는다 | 정상/장애 단정 대신 traffic 부족을 설명한다 |
| `active` | freshness와 sample이 충분하고 치명 신호가 없다 | 핵심 숫자와 0-insight 이유를 보여준다 |
| `stale` | 최근 accepted bucket이 90초 이상 없다 | accepted bucket 수집 경로 확인을 우선 제안한다 |
| `down` | 최근 accepted bucket이 180초 이상 없는 data-plane outage 후보 | host application process down으로 단정하지 않고 accepted bucket 수집 경로, starter 연결 상태, 최근 트래픽 여부를 함께 설명한다 |
| `degraded` | freshness는 충분하지만 error/latency/saturation concern이 있다 | 상위 0~3개 triage card를 보여준다 |

## 3. Evaluation Order

1. accepted bucket 존재 여부
2. freshness
3. 최소 sample 수
4. availability 상태
5. error/latency/saturation concern
6. 0-insight 또는 degraded summary

freshness가 부족하면 stale/down 계열 metric-data 평가가 우선하고, 일반 비교형 rule은 억제한다.

heartbeat telemetry가 존재하더라도 accepted bucket freshness age 계산에는 들어가지 않는다. 대신 metric-data state가 정해진 뒤 starter connection 축을 함께 읽어 rationale, zero-insight reason, recommended action, `starterConnection` copy를 보강할 수 있다. UI가 heartbeat를 함께 표시할 때도 starter 연결 상태와 accepted bucket 기반 application state는 별도 축으로 유지한다.

## 4. Transition Criteria

| From | To | 조건 |
|---|---|---|
| none | `waiting_first_data` | project/application 등록 후 accepted bucket이 0개 |
| `waiting_first_data` | `unknown` | 첫 accepted bucket은 있으나 current window sample이 최소 기준 미달 |
| `waiting_first_data` | `idle` | accepted bucket은 있으나 traffic이 idle 기준 이하 |
| `waiting_first_data` | `active` | freshness와 sample이 충분하고 concern candidate가 0개 |
| any | `stale` | 마지막 accepted bucket `endUtc`가 query 시점보다 90초 이상 과거 |
| any | `down` | 마지막 accepted bucket `endUtc`가 query 시점보다 180초 이상 과거. 이름은 data-plane 기준 후보이며 host process down 확정이 아니다 |
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

### 4.2 Two-Axis Interpretation Matrix

Story 4.2 이후 구현은 accepted bucket freshness와 heartbeat liveness를 아래처럼 분리해 해석한다.

| Accepted bucket axis | Heartbeat axis | Expected meaning / copy 후보 |
|---|---|---|
| 최근 accepted bucket 있음 | 최근 heartbeat 있음 | metric data current, starter connected |
| accepted bucket 없음 | 최근 heartbeat 있음 | `waiting_first_data`, `waiting for traffic`, `starter connected but no accepted bucket` |
| accepted bucket 오래됨 | 최근 heartbeat 있음 | `no recent traffic`, `metric data idle`, `starter connected but no accepted bucket`; host application down으로 단정하지 않음 |
| accepted bucket 없음 또는 오래됨 | heartbeat도 오래됨/없음 | `starter disconnected`, `telemetry unreachable`, `unknown`; host application down 원인은 미확정 |
| accepted bucket 최근 있음 | heartbeat 오래됨/없음 | metric data는 최근 수용됐지만 starter connection telemetry는 stale/unknown; state와 connection warning을 분리 |

`down` enum을 계속 사용할 경우 UI label과 rationale은 "앱이 내려감"이 아니라 "metric data-plane disconnected/unreachable" 계열이어야 한다. 이 이름이 계속 host application process down으로 오해되면 후속 story에서 `telemetry_unreachable`, `data_plane_down`, `metric_data_disconnected` 같은 rename을 검토한다.

## 5. Operational Event Candidate Boundary

State transition은 current state를 대체하지 않지만 operational event 후보가 될 수 있다.

예를 들어 `active -> degraded`, `any -> stale`, `any -> down`, `stale/down -> unknown`, `degraded -> active` 같은 변화는 bounded history surface에서 최근 운영 흐름으로 노출할 수 있다.

이 후보는 `operational-event-history.md` contract에 따라 dashboard snapshot/read model 결과에서 service layer가 파생한다. UI는 transition table을 복제하거나 현재 상태를 재판정하지 않는다.

Heartbeat success/failure/missing 자체는 이 state transition 후보가 아니다. heartbeat telemetry는 별도 starter connection surface에서 다루며 operational event history를 생성하지 않는다. 다만 같은 read model 안에서 accepted bucket state와 함께 보여주는 diagnosis field가 될 수 있다.

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
- heartbeat service나 repository 후보는 accepted bucket freshness나 metric application state를 계산하거나 갱신하지 않는다.
- `LifecycleStateService`는 accepted bucket freshness와 starter connection summary를 별도 typed input으로 받을 수 있다. 출력도 application metric state와 starter connection/liveness status를 섞지 않고 구분한다.
