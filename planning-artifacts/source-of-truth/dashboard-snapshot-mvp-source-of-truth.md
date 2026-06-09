---
artifactType: source-of-truth
projectName: Observation Portal
status: active
date: 2026-06-08
supersedesForMvp:
  - planning-artifacts/operator-decision-dashboard-redesign.md
  - planning-artifacts/operator-decision-dashboard-ux-model.md
  - planning-artifacts/operator-decision-dashboard-ux-pitch.md
---

# Dashboard and Snapshot MVP Source of Truth

## 1. 문서 목적

이 문서는 Observation Portal MVP에서 dashboard와 snapshot의 관계를 먼저 닫기 위한 기준 문서다.

기존 `operator-decision-dashboard-redesign.md`, `operator-decision-dashboard-ux-model.md`, `operator-decision-dashboard-ux-pitch.md`는 폐기하지 않는다. 다만 해당 문서들은 아직 충분히 논의되지 않은 UX 확장, impact model, baseline 비교, Look First 구성, 상세 화면 구성을 먼저 제안한 참고 자료로 둔다.

MVP 구현과 후속 문서 정렬에서는 이 문서를 우선한다.

## 2. 한 줄 결정

MVP에서 dashboard는 현재 시각 기준의 최근 관측 window를 해석한 read model이고, snapshot은 특정 시점의 dashboard read model을 저장해 나중에 같은 dashboard 화면처럼 복원하는 저장본이다.

## 3. 핵심 멘탈 모델

```text
Live Dashboard
  = 지금 기준 최근 30분 상태를 계산해 보여주는 화면

Dashboard Snapshot
  = 과거 특정 시점에 계산된 dashboard read model 저장본

Snapshot Detail
  = 저장된 dashboard read model을 현재 재계산 없이 dashboard처럼 다시 렌더링한 화면
```

Snapshot은 별도의 raw metric dump, incident report, operational event table이 아니다. MVP에서는 "그때 대시보드가 이렇게 보였다"를 복원하는 것이 핵심이다.

## 4. 결정 순서

MVP 설계는 아래 순서로 정렬한다.

1. Dashboard가 어떤 상태와 증거를 보여줄지 먼저 정의한다.
2. Snapshot은 그 dashboard read model을 저장하고 복원하는 방식으로 정의한다.
3. Snapshot marker/history는 저장된 dashboard point를 시간축에 색인하는 용도로 정의한다.
4. Operational event, incident folding, time-of-day baseline, impact 고도화는 MVP 이후 확장으로 둔다.

이 순서를 뒤집지 않는다. Snapshot을 먼저 독립 사건 모델로 키우면 MVP 범위가 커지고 dashboard와 detail이 서로 다른 제품처럼 갈라질 수 있다.

## 4.1 UX 재편 원칙

MVP dashboard UX는 기존 화면 구성이나 기존 발상을 그대로 옮기는 작업이 아니다. 운영자가 실제로 묻는 순서에 맞춰 화면을 다시 설계한다.

다만 persistence와 read source는 재사용 가능하면 최대한 재사용한다.

```text
UX / information architecture
  = 운영자 판단 흐름에 맞춰 재편

Persistence / read source
  = 기존 accepted bucket, dashboard snapshot, helper column을 우선 재사용
```

따라서 "기존 dashboard에 snapshot을 조금 붙인다"가 아니라, "운영자 판단 dashboard를 먼저 새로 정의하고, snapshot은 그 dashboard read model을 저장/복원한다"가 MVP 방향이다.

## 5. Dashboard MVP 정의

MVP dashboard는 현재 시각 기준으로 최근 30분 관측 데이터를 해석한다.

- 판단 window: 최근 30분
- source unit: accepted 30초 metric bucket
- 판단 방식: MVP에서는 절대 기준 중심
- baseline 비교: MVP 필수 판단 기준이 아니라 후속 확장 후보
- starter heartbeat: metric state를 직접 만들지 않는 별도 connection/control-plane 정보

Dashboard는 아래 질문에 답한다.

- 지금 metric data가 관측되고 있는가?
- 요청 sample이 판단 가능할 만큼 충분한가?
- 최근 30분 기준 운영 기준을 넘은 오류, 지연, 자원 압박이 있는가?
- 있다면 어떤 rule, endpoint, instance, resource hint를 먼저 봐야 하는가?

## 6. MVP State 정의

기존 lifecycle state set을 유지한다.

| State | MVP 의미 |
|---|---|
| `waiting_first_data` | 아직 accepted metric bucket이 없어 첫 관측을 기다리는 상태 |
| `idle` | metric freshness는 current지만 요청량이 거의 없어 장애 여부를 판단하지 않는 상태 |
| `unknown` | 데이터는 있으나 sample 부족, metric 누락, malformed evidence 등으로 판단을 보류하는 상태 |
| `active` | 최근 30분 기준 freshness와 sample이 충분하고 운영 기준 초과 신호가 없는 상태 |
| `degraded` | 최근 30분 기준 freshness와 sample이 충분하며 하나 이상의 운영 기준 초과 신호가 있는 상태 |
| `stale` | accepted metric bucket이 stale boundary까지 들어오지 않은 상태 |
| `down` | accepted metric bucket 공백이 down boundary까지 길어진 상태 |

`active`와 `degraded`는 accepted metric bucket 기반 품질 판단이다. Heartbeat 성공, 실패, 미수신은 이 state를 직접 올리거나 내리지 않는다.

## 7. MVP 절대 판단 기준

MVP에서는 "평소 대비 이상"보다 "현재 운영 기준 초과"를 먼저 판단한다.

초기 기준값은 아래를 사용한다.

| Signal | 기준 |
|---|---:|
| 최소 요청 수 | `request_count >= 30` |
| 오류율 | `error_rate >= 5%` |
| 느린 요청 비율 | `500ms 초과 요청 비율 >= 20%` |
| datasource pool usage | `>= 85%` |
| CPU usage | `>= 85%` |
| heap usage | `>= 90%` |

요청 수가 기준보다 낮으면 error rate가 높아도 `degraded`로 단정하지 않는다. 이 경우 `idle` 또는 `unknown`과 data quality 설명으로 표현한다.

Resource signal은 단독 root cause로 확정하지 않는다. MVP에서는 오류율 또는 느린 요청 신호와 함께 관찰될 때 "확인할 resource hint"로 보여준다. 단독 resource 초과는 `degraded` 승격보다 data quality/evidence hint로 시작한다.

## 7.1 500 발생 endpoint 노출 원칙

Application 또는 endpoint error rate가 5% 미만이어도, current window에서 500 계열 오류를 던진 endpoint는 운영자가 확인할 수 있게 노출한다.

이 원칙은 `degraded` state 승격 기준과 분리한다.

```text
State 승격
  = 최근 30분 기준 운영 기준 초과 여부 판단

Endpoint 노출
  = 500/error_count가 관찰된 endpoint를 확인 후보로 표시
```

예를 들어 `POST /orders`가 최근 30분 동안 2,000건 중 3건의 500을 냈다면 error rate는 0.15%라서 application state를 `degraded`로 만들지는 않을 수 있다. 하지만 운영자는 해당 endpoint에서 500이 있었다는 사실을 알아야 하므로 endpoint evidence 또는 확인 후보로 노출한다.

MVP endpoint 노출 규칙:

- current window에서 endpoint `errorCount > 0`이면 endpoint evidence 후보로 포함할 수 있다.
- error rate가 5% 미만이면 copy는 "운영 기준 초과"가 아니라 "500 오류 관찰" 또는 "최근 오류 발생"으로 표현한다.
- 요청 수가 매우 적으면 data quality와 함께 낮은 우선순위로 표시한다.
- 500 발생 endpoint는 `degraded`를 확정하는 증거가 아니라, 운영자가 확인할 수 있는 `attention endpoint`다.
- endpoint 목록은 너무 커지지 않도록 request count, error count, recency, normalized route 기준으로 정렬하고 bounded cap을 둔다.

Snapshot은 이 endpoint 노출 결과도 저장 당시 dashboard read model의 일부로 보존한다. 따라서 나중에 snapshot을 열면 "그때 state는 active였지만, 500을 던진 endpoint가 확인 후보로 보였다"는 상황을 재현할 수 있어야 한다.

## 8. Snapshot MVP 정의

Snapshot은 저장 당시 dashboard read model의 복원본이다.

```text
13:00 snapshot
  -> 13:00 시점에 계산한 최근 30분 dashboard read model 저장
  -> 나중에 열면 그 read model을 그대로 dashboard처럼 렌더링
  -> 현재 accepted bucket으로 state, rule, endpoint priority를 재계산하지 않음
```

Snapshot detail은 live dashboard와 같은 UI component를 최대한 재사용한다. 차이는 `mode`와 copy다.

| Mode | Source | 의미 |
|---|---|---|
| `live` | 현재 accepted metric bucket과 현재 query 시각 | 지금 대시보드 |
| `snapshot` | `dashboard_snapshots.read_model_json` | 저장 당시 대시보드 복원 |

Snapshot 화면에는 반드시 "저장된 snapshot이며 현재 상태를 재계산하지 않는다"는 의미가 드러나야 한다.

## 9. Dashboard Window와 Snapshot Cadence

Dashboard 판단 window와 snapshot 저장 cadence는 다른 개념이다.

```text
Dashboard window
  = 무엇을 보고 판단할지

Snapshot cadence
  = 그 판단 결과를 얼마나 자주 저장할지
```

MVP 권장값:

- Dashboard 판단 window: 최근 30분
- Scheduled snapshot cadence: 30분

즉, 30분마다 30분 집계를 새로 만드는 것이 아니라, 30분마다 "그 시점의 최근 30분 dashboard"를 저장한다.

Snapshot cadence가 dashboard 판단 window보다 길면 복원할 수 없는 구간이 생긴다. 예를 들어 최근 30분 dashboard를 1시간마다만 저장하면 `12:00~12:30` 상태는 `12:30` snapshot이 없는 한 나중에 dashboard처럼 복원할 수 없다.

따라서 MVP에서 "과거 dashboard point를 선택해 그때의 dashboard를 복원한다"는 UX를 목표로 한다면 scheduled snapshot cadence는 dashboard 판단 window와 같은 30분으로 둔다.

예시:

```text
12:30 scheduled snapshot
  -> 12:00~12:30 dashboard read model 저장

13:00 scheduled snapshot
  -> 12:30~13:00 dashboard read model 저장

13:30 scheduled snapshot
  -> 13:00~13:30 dashboard read model 저장
```

짧은 문제를 보완하기 위해 scheduled snapshot 외에 state-change 또는 high-confidence concern snapshot을 저장할 수 있다.

1시간 scheduled snapshot은 저장량을 더 줄이는 coarse history 옵션으로만 둔다. 이 경우 "모든 30분 dashboard point를 복원한다"는 UX를 약속하지 않는다.

## 10. Snapshot Capture 정책

모든 30초 bucket마다 snapshot을 저장하지 않는다.

MVP snapshot capture reason은 아래로 제한한다.

| Capture reason | 의미 |
|---|---|
| `hourly_scheduled` | 정시 cadence로 저장한 dashboard read model. 기존 persisted token 이름은 유지하되, MVP 목표 cadence는 30분이다. |
| `state_change` | 저장된 이전 dashboard state와 현재 dashboard state가 의미 있게 바뀐 경우 |
| `high_confidence_concern` | 운영 기준 초과와 sample guard를 통과한 강한 concern |
| `short_strong_spike` | 짧지만 강한 spike가 반복 bucket 조건을 통과한 경우 |
| `query_fallback` | dashboard 조회 시 최신 snapshot이 없거나 오래되어 보완 저장한 경우 |

동일 `application_id + current_window_end_utc`에 여러 capture reason이 성립하면 기존 priority-aware upsert 계약을 따른다.

`hourly_scheduled`는 기존 구현/계약에서 온 persisted token 이름이다. 30분 scheduled snapshot으로 MVP 방향을 바꾸더라도, token rename은 migration/API compatibility를 별도로 검토한 뒤 결정한다. 이 문서에서는 token 이름보다 scheduled snapshot의 의미와 cadence를 우선한다.

## 11. Snapshot Marker와 History

MVP history는 operational event list가 아니라 저장된 dashboard point 목록으로 시작한다.

Marker는 정확한 dashboard state를 대체하지 않고, timeline 탐색을 위한 문제 정도 색인으로만 사용한다. 실제 state와 evidence의 source of truth는 snapshot detail이 복원하는 저장된 dashboard read model이다.

Marker는 아래 정보를 색인할 수 있다.

- `snapshotId`
- `currentWindowEndUtc`
- `stateCode`
- `captureReason`
- `primaryRuleId`
- `primaryEndpointKey`
- `maxConfidence`
- `markerBucket` 또는 동등한 문제 정도 색인

사용자는 marker를 보고 "이 시점을 열어볼 가치가 있는가"를 빠르게 판단하고, marker를 선택하면 저장된 dashboard read model을 복원한다.

MVP marker는 incident id, acknowledgement, owner assignment, resolvedAt을 약속하지 않는다.

### 11.1 Marker Bucket

MVP marker bucket은 dashboard state 자체가 아니라 history navigation을 위한 축약 표현이다.

| Marker bucket | UI label | 기본 매핑 | 의미 |
|---|---|---|---|
| `normal` | 정상 범위 | `active`, `idle` | 전체 dashboard 판단 기준상 즉시 조치 대상이 아님 |
| `attention` | 확인 필요 | `degraded` 또는 명시적 high-confidence concern marker | 사용자가 열어볼 가치가 있는 문제 흔적이 있음 |
| `unavailable` | 판단 불가 | `waiting_first_data`, `unknown`, `stale` | 데이터 부족, 판단 보류, 관측 지연으로 상태 해석이 제한됨 |
| `critical` | 긴급 | `down` 또는 명시적 critical marker | 우선 확인해야 하는 심각한 관측 공백 또는 장애 후보 |

`normal`은 "아무 evidence도 없었다"는 뜻이 아니다. 예를 들어 저장 당시 state가 `active`이고 error rate가 운영 기준을 넘지 않았지만, 500을 던진 endpoint가 attention evidence로 표시될 수 있다. 이 경우 marker는 `normal`일 수 있고, snapshot detail은 `active` state와 500 endpoint evidence를 함께 복원한다.

### 11.2 Marker와 State의 경계

Marker bucket은 아래 용도로만 사용한다.

- timeline 색상
- 빠른 탐색
- 목록 필터
- 정렬 보조 정보

Marker bucket은 아래 용도로 사용하지 않는다.

- 장애 확정
- SLA/availability 계산
- alert 정확도 평가
- incident id 또는 resolvedAt 계산
- snapshot detail의 state/evidence 대체

문서, API, UI에서는 marker를 `snapshot_state`처럼 표현하지 않는다. `markerBucket`, `markerSeverity`, `problemDegree`처럼 state와 다른 이름을 사용한다.

### 11.3 Copy Guard

History UI에는 아래 의미가 드러나야 한다.

```text
마커는 스냅샷 탐색용 요약입니다.
실제 상태와 근거는 상세 화면의 저장된 dashboard 기준으로 표시됩니다.
```

Snapshot detail UI에는 아래 의미가 드러나야 한다.

```text
이 화면은 당시 저장된 dashboard read model을 복원한 것입니다.
현재 상태를 재계산한 결과가 아닙니다.
```

### 11.4 Marker Hard Contract

Marker는 또 하나의 lifecycle state machine이 아니다. MVP marker는 저장된 snapshot row를 timeline에서 훑기 위한 deterministic projection으로 제한한다.

Canonical field 역할은 아래로 고정한다.

| Field | 역할 | Source of truth 여부 |
|---|---|---|
| `stateCode` | 저장 당시 dashboard가 보여준 실제 lifecycle state | yes |
| `markerBucket` | timeline 색상/필터용 문제 정도 색인 | no |
| `transitionTag` | 이전 snapshot 대비 의미 있는 전환 보조 라벨 | no |
| `captureReason` | snapshot이 저장된 이유 | no |

`stateCode`만 snapshot detail의 실제 state다. `markerBucket`, `transitionTag`, `captureReason`은 detail state를 대체하지 않는다.

Marker 계산은 MVP에서 아래 입력만 사용한다.

- current snapshot row의 `state_code`
- current snapshot row의 `capture_reason`
- current snapshot row의 `max_confidence`
- 같은 application의 strictly earlier snapshot `state_code`

MVP marker 계산은 `read_model_json.snapshotEndpointEvidence` body를 파싱하지 않는다. 따라서 500 endpoint evidence가 있더라도 application-level state가 `active`이고 high-confidence capture reason이 없다면 marker는 `normal`일 수 있다. 해당 500 endpoint evidence는 snapshot detail에서 확인한다.

### 11.5 Marker Mapping Contract

기본 `markerBucket`은 저장된 `stateCode`에서 먼저 결정한다.

| Stored `stateCode` | Base `markerBucket` |
|---|---|
| `active` | `normal` |
| `idle` | `normal` |
| `degraded` | `attention` |
| `waiting_first_data` | `unavailable` |
| `unknown` | `unavailable` |
| `stale` | `unavailable` |
| `down` | `critical` |
| unknown future state | `unavailable` |

허용되는 elevation은 하나뿐이다.

- base bucket이 `normal`이고 `captureReason in [high_confidence_concern, short_strong_spike]`이면 `markerBucket=attention`으로 올릴 수 있다.

그 외에는 endpoint evidence, resource hint, confidence 숫자만으로 marker bucket을 올리지 않는다. 이 제한은 marker가 복잡한 second judgment layer가 되는 것을 막기 위한 MVP 경계다.

### 11.6 Transition Tag Contract

`transitionTag`는 marker bucket과 별개인 보조 라벨이다. 전환을 설명할 뿐 state나 bucket을 바꾸지 않는다.

허용 값은 아래로 제한한다.

| Transition tag | 조건 | UI label |
|---|---|---|
| `recovered_to_normal` | previous `stateCode in [degraded, stale, down]` and current `stateCode=active` | 정상 범위로 돌아옴 |
| `entered_attention` | previous `stateCode in [active, idle]` and current `stateCode=degraded` | 확인 필요 진입 |
| `entered_unavailable` | previous `markerBucket != unavailable` and current base `markerBucket=unavailable` | 판단 불가 진입 |
| `entered_critical` | previous `stateCode != down` and current `stateCode=down` | 긴급 진입 |
| null | 위 조건 없음 | bucket 기본 라벨 사용 |

예시:

```text
13:00 snapshot
  stateCode=degraded
  markerBucket=attention
  transitionTag=entered_attention

13:30 snapshot
  stateCode=active
  markerBucket=normal
  transitionTag=recovered_to_normal
```

이때 13:30 snapshot detail은 `stateCode=active` dashboard를 그대로 복원한다. `recovered_to_normal`은 history timeline에서 "이 active가 degraded 이후 정상 범위로 돌아온 지점"임을 알려주는 보조 라벨일 뿐이다.

### 11.7 Marker Non-goals

MVP marker는 아래를 하지 않는다.

- incident period 생성
- resolvedAt 계산
- SLA/availability 집계
- alert delivery 또는 alert accuracy 판단
- endpoint evidence body 기반 severity 재판정
- resource hint 단독 severity 승격
- dashboard state를 숨기거나 대체
- snapshot detail read model 재계산

후속에서 operational event나 incident folding을 만들더라도, 이 MVP marker 계약을 event model로 확장하지 않는다. 별도 event model은 snapshot marker에서 파생될 수는 있지만 marker 자체를 event로 재해석하지 않는다.

### 11.8 Guard Test 후보

구현 시 아래 guard를 둔다.

- 모든 known `stateCode`가 정확히 하나의 `markerBucket`으로 매핑된다.
- unknown future state는 `unavailable`로 수렴한다.
- `active + endpoint 500 evidence`는 high-confidence capture reason이 없으면 `markerBucket=normal`을 유지한다.
- `degraded -> active` 전환은 `transitionTag=recovered_to_normal`을 만든다.
- marker index 조회는 snapshot detail처럼 `read_model_json` evidence body를 재판정하지 않는다.
- snapshot detail은 marker와 무관하게 저장된 dashboard state/evidence를 그대로 복원한다.

## 12. Persistence 재사용 원칙

MVP에서는 기존 persistence를 우선 재사용한다.

- `accepted_metric_buckets`: live dashboard 계산 원천
- `dashboard_snapshots`: 저장된 dashboard read model 원천
- `dashboard_snapshots.read_model_json`: snapshot detail 복원 원천
- `state_code`, `capture_reason`, `primary_rule_id`, `primary_endpoint_key`, `max_confidence`: marker/history 색인 helper

새 operational event table, endpoint timeseries table, raw snapshot explorer는 MVP 범위가 아니다.

## 13. 기존 3개 문서의 위치

아래 문서들은 참고 자료다.

- `planning-artifacts/operator-decision-dashboard-redesign.md`
- `planning-artifacts/operator-decision-dashboard-ux-model.md`
- `planning-artifacts/operator-decision-dashboard-ux-pitch.md`

이 문서들에 있는 다음 내용은 후속 논의 전까지 source of truth로 보지 않는다.

- baseline comparison 중심 UX
- Look First Top 3의 최종 정보 구조
- impact score 공식
- endpoint metadata table
- journey/criticality/owner/runbook 모델
- snapshot을 별도 사건 봉투처럼 확장하는 상세 UX
- operational event와 incident history 고도화

해당 아이디어들은 폐기하지 않는다. 다만 MVP 정렬은 "dashboard를 먼저 정의하고 snapshot은 그 dashboard를 복원한다"는 본 문서의 방향을 우선한다.

## 14. MVP 이후 확장 후보

MVP 이후 아래를 순서대로 다시 논의할 수 있다.

1. 전일 같은 시간대, 지난주 같은 요일/시간대 baseline
2. endpoint별 평소 패턴과 adaptive threshold
3. impact score와 business criticality metadata
4. operational event projection, suppression, folding
5. snapshot detail의 회고/공유용 evidence summary
6. 장기 retention 또는 projection table

직전 window baseline은 MVP 기본 판단 기준으로 두지 않는다. 후속 baseline을 만든다면 운영자가 실제로 "평소"라고 느끼는 시간대 기준을 우선 검토한다.
