---
artifactType: source-of-truth
projectName: Observation Portal
status: active
date: 2026-06-09
dependsOn:
  - planning-artifacts/source-of-truth/dashboard-snapshot-mvp-source-of-truth.md
supersedesForMvp:
  - planning-artifacts/operator-decision-dashboard-redesign.md
  - planning-artifacts/operator-decision-dashboard-ux-model.md
  - planning-artifacts/operator-decision-dashboard-ux-pitch.md
---

# Application Dashboard Read Model MVP Source of Truth

## 1. 문서 목적

이 문서는 Observation Portal MVP에서 Application Dashboard read model과 API 계약이 무엇을 반환해야 하는지 고정한다.

`planning-artifacts/source-of-truth/dashboard-snapshot-mvp-source-of-truth.md`는 dashboard와 snapshot의 관계를 닫는 상위 source of truth다. 이 문서는 그 결정을 바꾸지 않고, live application dashboard와 application snapshot detail이 공통으로 복원해야 하는 read model shape, 판단 rule, evidence 정책, persistence 재사용 가능성과 한계를 정의한다.

기존 `operator-decision-dashboard-redesign.md`, `operator-decision-dashboard-ux-model.md`, `operator-decision-dashboard-ux-pitch.md`는 참고 자료다. 해당 문서의 baseline, impact score, Look First, incident/flight-recorder 확장 제안은 이 문서에서 명시적으로 채택한 항목만 MVP 계약으로 본다.

## 2. 한 문장 정의

Observation Portal MVP application dashboard read model은 현재 시각 기준 특정 application의 최근 30분 accepted metric bucket을 단순한 절대 기준으로 해석해, 운영자에게 "이 application에서 지금 무엇이 문제인가, 어디부터 봐야 하는가, 왜 그렇게 판단했는가"를 알려주는 저장 가능한 Application Dashboard API 계약이다.

## 3. Product Promise

MVP application dashboard는 예측, root cause 확정, incident 관리, baseline 분석 제품이 아니다.

MVP application dashboard가 약속하는 것은 아래 세 가지다.

1. 최근 30분 기준으로 application metric state를 일관되게 판단한다.
2. state를 바꾸는 근거와 state를 바꾸지는 않지만 확인해야 하는 evidence를 분리한다.
3. 동일 read model을 snapshot에 저장해 나중에 재계산 없이 같은 dashboard로 복원한다.

운영자가 첫 화면에서 읽어야 하는 순서는 아래로 고정한다.

1. 지금 이 application이 판단 가능한 상태인가?
2. 판단 가능하다면 lifecycle state는 무엇인가?
3. state를 만든 직접 근거는 무엇인가?
4. state와 별개로 확인해야 하는 endpoint/resource evidence가 있는가?
5. 데이터 품질상 무엇을 과신하면 안 되는가?

## 4. Scope

MVP read model은 application dashboard 한 화면을 기준으로 한다.

- Project/Application navigation은 application dashboard 판단을 대체하지 않는다.
- Live dashboard source는 `accepted_metric_buckets`다.
- Snapshot detail source는 `dashboard_snapshots.read_model_json`이다.
- Snapshot marker/history는 timeline 탐색 색인일 뿐 read model source가 아니다.
- UI는 lifecycle state, endpoint priority, resource concern, data quality를 재계산하지 않는다.

## 5. Hard Contract

아래 항목은 MVP에서 흔들지 않는다.

1. Dashboard 판단 window는 현재 시각 기준 최근 30분이다.
2. Snapshot은 dashboard read model 저장본이다.
3. Snapshot detail은 저장된 read model을 현재 metric으로 재계산하지 않는다.
4. Lifecycle state는 기존 7개만 사용한다.
5. Marker bucket은 timeline 색인이고 lifecycle state가 아니다.
6. Starter heartbeat는 metric lifecycle state를 직접 바꾸지 않는다.
7. `requestCount < 30`이면 error rate만으로 `degraded`를 만들지 않는다.
8. Resource signal은 단독 root cause로 확정하지 않는다.
9. 5xx/500번대 서버 에러 evidence는 lifecycle state 승격과 별개로 attention evidence로 보존한다.
10. MVP는 baseline 비교, 전일/전주 비교, adaptive threshold, incident folding, business criticality를 사용하지 않는다.

## 6. Terminology

| Term | MVP 의미 |
|---|---|
| `state` | application-level lifecycle state. 저장 당시 dashboard가 보여준 실제 상태다. |
| `markerBucket` | snapshot timeline 탐색용 문제 정도 색인. state/evidence source가 아니다. |
| `stateReason` | lifecycle state를 만들거나 바꿀 수 있는 직접 근거다. |
| `attentionEvidence` | state를 바꾸지는 않지만 운영자가 확인해야 하는 증거다. |
| `firstLookCandidate` | 화면에서 먼저 볼 후보. endpoint, resource, data quality를 같은 queue로 묶되 bounded cap을 둔다. |
| `dataQuality` | 판단 가능성, sample 부족, freshness, malformed/missing evidence를 설명하는 별도 축이다. |
| `starterConnection` | starter heartbeat/control-plane 연결 정보다. metric state와 분리한다. |
| `endpoint errorCount` | MVP에서는 endpoint의 5xx/500번대 서버 에러 count로 해석한다. 개별 status code 수집/표시는 요구하지 않는다. |

## 7. MVP Threshold

MVP는 절대 기준만 사용한다.

| Signal | 기준 |
|---|---:|
| 최소 요청 수 | `requestCount >= 30` |
| 오류율 | `errorRate >= 0.05` |
| 느린 요청 비율 | `slowShareOver500ms >= 0.20` |
| datasource pool usage | `datasourcePoolUsage >= 0.85` |
| CPU usage | `cpuUsage >= 0.85` |
| heap usage | `heapUsage >= 0.90` |

Threshold는 read model에 포함한다. 그래야 snapshot detail이 저장 당시 어떤 기준으로 해석되었는지 재현할 수 있다.

## 8. Simple MVP Rule Model

MVP rule은 일부러 단순하게 둔다. scoring engine, impact formula, adaptive threshold를 만들지 않는다.

### 8.1 Evaluation Order

아래 순서로 판단한다.

```text
source 선택
  -> window 결정
  -> freshness/lifecycle guard
  -> sample readiness
  -> RED signal 계산
  -> USE/resource signal 계산
  -> stateReason 생성
  -> attentionEvidence 생성
  -> firstLookCandidates 정렬
  -> read model 저장/반환
```

### 8.2 Source 선택

| Mode | Source | 계산 여부 |
|---|---|---|
| `live` | `accepted_metric_buckets` | 현재 query 시각 기준으로 계산 |
| `snapshot` | `dashboard_snapshots.read_model_json` | 재계산 금지 |

Snapshot detail은 저장된 read model을 반환한다. 현재 bucket, 현재 threshold, 현재 route metadata, 현재 starter 상태를 다시 조인하지 않는다.

### 8.3 Window

Live dashboard는 `evaluatedAt` 기준 최근 30분 bucket을 사용한다.

```text
windowStartUtc = evaluatedAt - 30 minutes
windowEndUtc = evaluatedAt
source bucket = bucket_end_utc > windowStartUtc and bucket_end_utc <= windowEndUtc
```

실제 구현에서 clock rounding을 쓰더라도 read model에는 저장 당시 `window.startUtc`, `window.endUtc`, `evaluatedAt`을 명시한다.

### 8.4 Freshness and Lifecycle Guard

Freshness guard는 signal rule보다 먼저 적용한다.

| 조건 | state |
|---|---|
| accepted bucket이 한 번도 없음 | `waiting_first_data` |
| 마지막 accepted bucket이 down boundary를 넘음 | `down` |
| 마지막 accepted bucket이 stale boundary를 넘음 | `stale` |
| current freshness지만 `requestCount = 0` | `idle` |
| current freshness지만 핵심 metric이 누락/malformed라 판단 불가 | `unknown` |

`down`, `stale`, `waiting_first_data`, `idle`, `unknown`에서는 RED/USE 숫자를 보여줄 수는 있어도, 정상적인 degraded rule을 억지로 적용하지 않는다. 이 경우 data quality와 state rationale이 우선한다.

### 8.5 Sample Readiness

`requestCount >= 30`일 때만 application-level error/latency degraded 판단을 허용한다.

| requestCount | 의미 |
|---:|---|
| `0` | 트래픽 없음. freshness가 current이면 `idle` 후보 |
| `1..29` | 표본 부족. error rate가 높아도 `degraded` 금지 |
| `>= 30` | error/latency degraded rule 적용 가능 |

표본 부족 상태에서도 5xx/500번대 서버 에러는 숨기지 않는다. 다만 copy는 "운영 기준 초과"가 아니라 "최근 서버 오류 관찰" 또는 "표본 부족으로 상태 격상 보류"로 쓴다.

### 8.6 RED Signal

RED는 사용자 요청 증상으로 번역한다.

| 내부 개념 | 사용자 언어 | Field |
|---|---|---|
| Rate | 요청량 | `signals.red.requestCount` |
| Errors | 오류 | `signals.red.errorCount`, `signals.red.errorRate` |
| Duration | 지연 | `signals.red.slowCountOver500ms`, `signals.red.slowShareOver500ms` |

MVP에서는 endpoint p95/p99를 만들지 않는다. Application-level duration histogram과 endpoint duration histogram에서 `500ms 초과 비율`만 계산한다. Histogram boundary가 맞지 않거나 `500ms` boundary가 없으면 latency evidence는 `unavailable`로 표시하고, latency degraded rule은 적용하지 않는다.

### 8.7 USE Signal

USE는 공유 자원 압박으로 번역한다.

| 내부 개념 | 사용자 언어 | Field |
|---|---|---|
| CPU utilization | CPU 사용률 | `signals.use.cpuUsage` |
| Heap utilization | Heap 사용률 | `signals.use.heapUsage` |
| Datasource pool utilization/saturation | DB pool 사용률/포화 | `signals.use.datasourcePoolUsage` |

Resource ratio는 root cause 확정이 아니라 evidence다. MVP에서는 "CPU 때문에 느리다"처럼 단정하지 않는다.

권장 계산은 window 내 관측 가능한 ratio의 `max`와 `latestObservedAt`을 함께 보존하는 것이다. 평균만 쓰면 짧은 포화가 묻히고, latest만 쓰면 window 중 발생한 압박을 놓칠 수 있다. 단, MVP read model은 우선 `maxUsage`, `threshold`, `status`, `observedAt`만 반환하고 복잡한 timeseries는 반환하지 않는다.

## 9. State Decision Rules

Freshness guard를 통과하고 sample이 충분할 때 아래 rule로 `degraded`를 판단한다.

### 9.1 Application Error Rule

```text
if requestCount >= 30
and errorRate >= 0.05
then stateReason = application_error_rate_high
```

문구:

```text
최근 30분 오류율이 5% 이상입니다.
```

### 9.2 Application Latency Rule

```text
if requestCount >= 30
and slowShareOver500ms is available
and slowShareOver500ms >= 0.20
then stateReason = application_slow_share_high
```

문구:

```text
최근 30분 500ms 초과 요청 비율이 20% 이상입니다.
```

### 9.3 Resource With Symptom Rule

Resource threshold 초과만으로 `degraded`를 확정하지 않는다. Resource는 오류/지연 증상과 함께 있을 때 "공유 자원 압박 확인 후보"로 승격한다.

```text
if resourceUsage >= resourceThreshold
and requestCount >= 30
and (
  application error/latency rule hit
  or at least 2 endpoint symptom candidates exist
)
then stateReason = shared_resource_pressure_with_request_symptom
```

Endpoint symptom candidate는 아래 중 하나다.

- endpoint `requestCount >= 30` and `errorRate >= 0.05`
- endpoint `requestCount >= 30` and `slowShareOver500ms >= 0.20`
- endpoint error evidence가 있고 같은 window에 application latency rule이 hit

문구:

```text
공유 자원 사용률이 높고 여러 요청 증상이 같은 window에서 관찰됩니다.
```

### 9.4 Resource Alone Rule

```text
if resourceUsage >= resourceThreshold
and no request symptom exists
then attentionEvidence = resource_pressure_without_request_symptom
```

이 경우 lifecycle state를 resource만으로 올리지 않는다.

문구:

```text
공유 자원 사용률이 기준 이상이지만 오류/지연 증상이 함께 관찰되지는 않았습니다.
```

### 9.5 Active Rule

Freshness가 current이고 sample이 충분하며 stateReason이 없으면 `active`다.

`active`는 evidence가 하나도 없다는 뜻이 아니다. 예를 들어 state는 `active`여도 endpoint 5xx/500번대 서버 에러 attention evidence가 있을 수 있다.

## 10. First Look Candidate Policy

`firstLookCandidates`는 "느린 endpoint 목록"이 아니다. 운영자가 먼저 확인할 후보 queue다.

MVP에서는 최대 3개만 반환한다.

후보 타입은 아래로 제한한다.

| Type | 생성 조건 |
|---|---|
| `application_error` | application error rule hit |
| `application_latency` | application latency rule hit |
| `resource_pressure` | resource with symptom rule hit 또는 resource alone attention |
| `endpoint` | endpoint degraded candidate 또는 endpoint error attention |
| `data_quality` | state가 `waiting_first_data`, `unknown`, `stale`, `down`, sample limited |

정렬은 점수가 아니라 deterministic priority reason으로 한다.

```text
state-changing reason
  -> resource_with_multiple_endpoint_symptoms
  -> endpoint error+latency
  -> endpoint error
  -> endpoint latency
  -> resource alone attention
  -> 5xx/server_error attention under low sample
  -> data quality
  -> deterministic key asc
```

MVP에서 `impactScore`는 public field로 두지 않는다. `confidence`도 사용자에게 숫자로 보여주지 않는다. 필요하면 `confidence`는 snapshot helper column 또는 내부 정렬용으로만 보존한다.

## 11. Endpoint Evidence Policy

Endpoint evidence는 상태 원천과 확인 후보를 분리한다.

### 11.1 노출 조건

Endpoint는 아래 중 하나를 만족하면 evidence 후보가 된다.

1. `requestCount >= 30` and `errorRate >= 0.05`
2. `requestCount >= 30` and `slowShareOver500ms >= 0.20`
3. current window에서 endpoint `errorCount > 0`인 5xx/500번대 서버 에러가 관찰됨

Endpoint 목록은 bounded cap을 둔다.

- `firstLookCandidates`: 최대 3개 전체 후보 중 일부
- `endpointEvidence`: 최대 5개
- snapshot 저장용 endpoint evidence도 같은 cap을 기본값으로 둔다

### 11.2 숨기거나 낮은 우선순위로 둘 조건

아래 endpoint는 낮은 우선순위 또는 detail-only evidence로 둔다.

- `requestCount = 0`
- `UNKNOWN` route
- route safety 검증 실패
- duration bucket malformed
- error/latency/resource와 연결되지 않은 단순 slow bucket
- requestCount가 매우 작고 error가 없는 endpoint

### 11.3 5xx/500번대 Server Error Contract

제품 계약상 "500 발생 endpoint"는 정확히 HTTP status code `500` 하나를 뜻하지 않는다. MVP에서는 `5xx` 전체를 500번대 서버 에러로 표현한다.

따라서 개별 status code, status family breakdown, raw error code를 수집하거나 표시할 필요가 없다. Canonical read model은 기존 endpoint `errorCount`를 사용하되, 그 의미를 5xx/500번대 서버 에러 count로 고정한다.

```json
{
  "errorCount": 1,
  "errorSemantic": "server_error_5xx"
}
```

운영자 copy는 "정확한 HTTP 500"이 아니라 "500번대 서버 오류" 또는 "서버 오류"라고 표현한다.

```json
{
  "errorCount": 1,
  "operatorText": "최근 endpoint에서 500번대 서버 오류가 관찰되었습니다."
}
```

MVP에서 필요한 것은 status code breakdown이 아니라 `errorCount` semantic의 명확화다.

- Starter/ingest contract에서 endpoint `errorCount`는 5xx/500번대 서버 에러 count로 정의한다.
- Portal read model은 endpoint `errorCount > 0`을 5xx/500번대 서버 에러 attention evidence로 노출한다.
- 정확한 status code별 count가 필요한 use case는 MVP 이후 raw diagnostics 또는 log/trace 연계 범위로 둔다.

## 12. Resource Evidence Policy

Resource evidence는 endpoint evidence와 의미를 섞지 않는다.

| Resource | Evidence 의미 | Operator copy |
|---|---|---|
| `datasource_pool` | DB connection pool 사용률이 높아 공유 병목 가능성이 있음 | DB pool 사용률이 기준 이상입니다. 여러 endpoint 지연과 함께 보면 connection wait 가능성을 확인하세요. |
| `cpu` | instance CPU 사용률이 높아 처리 여유가 낮을 수 있음 | CPU 사용률이 기준 이상입니다. 같은 window의 지연/오류와 함께 확인하세요. |
| `heap` | heap 사용률이 높아 GC/memory pressure 가능성이 있음 | Heap 사용률이 기준 이상입니다. 오류나 지연이 함께 있으면 memory pressure를 확인하세요. |

Resource evidence field는 아래를 포함한다.

- `resourceKey`
- `scope`
- `usage`
- `threshold`
- `status`
- `observedAt`
- `stateContribution`
- `operatorText`

`stateContribution` 값은 아래로 제한한다.

| 값 | 의미 |
|---|---|
| `none` | state에 기여하지 않음 |
| `attention_only` | 확인 evidence지만 state 승격 근거는 아님 |
| `degraded_with_request_symptom` | 요청 오류/지연과 함께 있어 degraded stateReason으로 사용됨 |

## 13. Shared Resource vs Endpoint-Only Rule

MVP는 원인을 확정하지 않고 패턴만 말한다.

### 13.1 공유 자원 압박 패턴

```text
resource threshold hit
and request symptom exists
and affected endpoint count >= 2 or application-level slow/error rule hit
```

문구:

```text
공유 자원 압박과 여러 요청 증상이 함께 관찰됩니다.
```

추천 first look:

1. resource evidence
2. affected endpoint evidence 최대 2개
3. instance summary

### 13.2 특정 Endpoint 문제 패턴

```text
no resource threshold hit
and one endpoint has error/latency threshold hit
```

문구:

```text
공유 자원 압박 없이 특정 endpoint의 오류/지연 증상이 두드러집니다.
```

추천 first look:

1. endpoint evidence
2. endpoint histogram/error evidence
3. 관련 instance summary가 있으면 detail link

### 13.3 낮은 표본의 Error 패턴

```text
endpoint errorCount > 0
and endpoint requestCount < 30
```

문구:

```text
endpoint 서버 오류가 관찰되었지만 표본 수가 낮아 상태를 degraded로 격상하지 않았습니다.
```

이 패턴은 `attentionEvidence`이며 stateReason이 아니다.

## 14. Data Quality and Starter Connection

Data quality는 lifecycle state와 별도의 보조 축이다.

권장 상태는 아래로 제한한다.

| State | 의미 |
|---|---|
| `sufficient` | freshness와 sample이 판단 가능 |
| `sample_limited` | requestCount가 최소 기준보다 낮음 |
| `waiting_first_data` | 아직 accepted metric bucket이 없음 |
| `stale` | 최근 metric이 stale boundary를 넘음 |
| `down` | 최근 metric 공백이 down boundary를 넘음 |
| `partial` | 일부 metric/histogram/resource evidence가 누락됨 |
| `malformed` | endpoint JSON 또는 histogram evidence가 읽기 불가능 |

Starter connection은 `dataQuality` 안에 넣지 않고 top-level `starterConnection`으로 분리한다. UI는 두 블록을 가까이 보여줄 수 있지만, API 의미는 분리한다.

`starterConnection.stateImpact`는 항상 아래 의미 중 하나여야 한다.

| 값 | 의미 |
|---|---|
| `does_not_change_metric_state` | heartbeat는 metric state를 직접 바꾸지 않음 |
| `control_plane_only` | starter 연결 참고 정보로만 사용 |

## 15. Instance Evidence Scope

MVP 첫 화면에서 instance evidence는 summary다. Instance별 독립 state machine을 만들지 않는다.

2026-06-11 UI MVP 결정: Instance Summary 행은 현재 list read model이 제공하는 얇은 summary와 detail link를 표시한다. `status`, `heartbeat`, `requests`, `slow`, `contribution`처럼 현 `InstanceEntry` 목록 read model에 없는 값은 프론트에서 재계산하거나 합성해 행에 직접 표시하지 않는다. SoT mockup의 더 풍부한 행 표현과의 차이는 sanctioned gap으로 추적하며, 현재 화면 진입점은 단일 wide modal의 `Open modal`뿐이다.

첫 화면 field:

- `observedInstanceCount`
- `affectedInstanceCount`
- `resourcePressureInstanceCount`
- `items[]` 최대 10개

Snapshot/detail 복원용 field:

- `items[]` 최대 50개까지 허용 가능
- `instanceId`
- `instanceName`
- `lastSeenAt`
- `resourceEvidenceRefs`
- `endpointEvidenceRefs`
- `links.evidence`

MVP에서는 endpoint와 instance의 causality를 단정하지 않는다. `endpointEvidenceRefs`는 "같은 저장 시점에 함께 관찰된 anchor"이지 원인 관계가 아니다.

## 16. Read Model Shape Draft

아래 shape는 MVP 계약의 기준이다. 실제 Java record naming은 기존 camelCase API 스타일을 따른다.

```json
{
  "schemaVersion": "dashboard_read_model.v1",
  "mode": "live",
  "generatedAt": "2026-06-08T10:30:00Z",
  "application": {
    "projectId": "00000000-0000-0000-0000-000000000000",
    "applicationId": "00000000-0000-0000-0000-000000000000",
    "name": "orders-api",
    "environment": "prod"
  },
  "window": {
    "type": "recent_30_minutes",
    "startUtc": "2026-06-08T10:00:00Z",
    "endUtc": "2026-06-08T10:30:00Z"
  },
  "thresholds": {
    "minimumRequestCount": 30,
    "errorRate": 0.05,
    "slowShareOver500ms": 0.20,
    "datasourcePoolUsage": 0.85,
    "cpuUsage": 0.85,
    "heapUsage": 0.90
  },
  "state": {
    "code": "active",
    "label": "정상 범위",
    "rationale": "최근 30분 기준 state를 변경할 운영 기준 초과 신호가 없습니다.",
    "recommendedAction": "주의 evidence가 있으면 해당 endpoint 또는 resource를 확인하세요.",
    "scope": "application"
  },
  "operatorSummary": {
    "headline": "최근 30분 서비스 요청 처리는 정상 범위입니다.",
    "primaryProblemCode": null,
    "firstLookText": "즉시 우선 확인할 degraded 후보는 없습니다."
  },
  "dataQuality": {
    "state": "sufficient",
    "requestCount": 128,
    "minimumRequestCount": 30,
    "lastObservedAt": "2026-06-08T10:29:30Z",
    "limitations": []
  },
  "starterConnection": {
    "statusSource": "starter_heartbeat",
    "lastHeartbeatAt": "2026-06-08T10:29:45Z",
    "lastHeartbeatStatus": "received",
    "connectionMeaning": "starter 연결 참고 정보입니다.",
    "stateImpact": "does_not_change_metric_state"
  },
  "signals": {
    "red": {
      "requestCount": 128,
      "errorCount": 0,
      "errorRate": 0.0,
      "slowCountOver500ms": 15,
      "slowShareOver500ms": 0.117,
      "latencyEvidenceStatus": "available"
    },
    "use": {
      "datasourcePoolUsage": {
        "max": 0.42,
        "threshold": 0.85,
        "status": "normal",
        "observedAt": "2026-06-08T10:29:30Z"
      },
      "cpuUsage": {
        "max": 0.51,
        "threshold": 0.85,
        "status": "normal",
        "observedAt": "2026-06-08T10:29:30Z"
      },
      "heapUsage": {
        "max": 0.63,
        "threshold": 0.90,
        "status": "normal",
        "observedAt": "2026-06-08T10:29:30Z"
      }
    }
  },
  "stateReasons": [],
  "attentionEvidence": [
    {
      "type": "endpoint_server_error",
      "severity": "attention",
      "scope": "endpoint",
      "target": "POST /orders",
      "reasonCode": "endpoint_server_error_observed",
      "affectsLifecycleState": false,
      "operatorText": "최근 endpoint에서 500번대 서버 오류가 관찰되었습니다. 전체 상태를 변경하지는 않지만 확인 후보입니다."
    }
  ],
  "firstLookCandidates": [],
  "endpointEvidence": [
    {
      "rank": 1,
      "method": "POST",
      "route": "/orders",
      "endpointKey": "POST /orders",
      "policy": "attention_only",
      "requestCount": 18,
      "errorCount": 1,
      "errorSemantic": "server_error_5xx",
      "errorRate": 0.0556,
      "slowShareOver500ms": null,
      "latencyEvidenceStatus": "unavailable",
      "reasonCodes": [
        "endpoint_server_error_observed",
        "sample_limited"
      ],
      "affectsLifecycleState": false,
      "operatorText": "표본 수가 낮아 상태를 degraded로 격상하지 않았습니다."
    }
  ],
  "resourceEvidence": [],
  "instanceSummary": {
    "observedInstanceCount": 2,
    "affectedInstanceCount": 0,
    "resourcePressureInstanceCount": 0,
    "items": []
  },
  "snapshot": null,
  "readSemantics": {
    "source": "accepted_metric_buckets",
    "snapshotDetailRecalculates": false,
    "markerIsStateSource": false
  },
  "excludedCapabilities": [
    "time_series_baseline",
    "previous_day_or_week_comparison",
    "adaptive_threshold",
    "incident_folding",
    "impact_score_public_ui",
    "endpoint_business_criticality",
    "marker_as_state_source",
    "heartbeat_as_metric_state"
  ]
}
```

Snapshot detail은 같은 shape를 사용하되 `mode=snapshot`, `snapshot` metadata, `readSemantics.source=dashboard_snapshots.read_model_json`을 넣는다.

```json
{
  "mode": "snapshot",
  "snapshot": {
    "snapshotId": "00000000-0000-0000-0000-000000000000",
    "capturedAt": "2026-06-08T10:30:05Z",
    "captureReason": "hourly_scheduled",
    "stateCode": "active",
    "markerBucket": "normal"
  },
  "readSemantics": {
    "source": "dashboard_snapshots.read_model_json",
    "snapshotDetailRecalculates": false,
    "markerIsStateSource": false
  }
}
```

## 17. Snapshot Storage Contract

Snapshot 저장 시 반드시 read model에 포함되어야 하는 field는 아래다.

- `schemaVersion`
- `mode`
- `generatedAt`
- `application`
- `window`
- `thresholds`
- `state`
- `operatorSummary`
- `dataQuality`
- `starterConnection`
- `signals`
- `stateReasons`
- `attentionEvidence`
- `firstLookCandidates`
- `endpointEvidence`
- `resourceEvidence`
- `instanceSummary`
- `readSemantics`
- `excludedCapabilities`

Snapshot row helper column은 read model의 색인용 복사본이다.

| Helper column | Source |
|---|---|
| `state_code` | `state.code` |
| `primary_rule_id` | 첫 번째 stateReason 또는 firstLookCandidate reason |
| `primary_endpoint_key` | 첫 번째 endpoint firstLookCandidate |
| `max_confidence` | 내부 confidence-bearing evidence 중 최댓값. 없으면 null |

Helper column은 snapshot detail source가 아니다. Detail은 `read_model_json`을 복원한다.

## 18. Persistence Reuse Assessment

현재 persistence는 MVP dashboard read model을 만들기에 대체로 충분하다. 하지만 그대로 장기 확장까지 끌고 가면 발목을 잡을 수 있는 지점이 있다.

### 18.1 재사용하기 좋은 부분

| Persistence | MVP 적합성 |
|---|---|
| `accepted_metric_buckets` 30초 bucket | 최근 30분 window 계산에 적합 |
| `request_count`, `error_count` | application-level RED error/rate 계산에 적합 |
| `duration_buckets_json` | slow share over 500ms 계산에 적합 |
| `cpu_usage_ratio`, `heap_used_ratio`, `datasource_pool_usage_ratio` | resource pressure hint에 적합 |
| `endpoints_json` | bounded endpoint evidence와 priority 후보에 적합 |
| `dashboard_snapshots.read_model_json` | snapshot detail 복원 source로 적합 |
| snapshot helper columns | marker/history 색인에 적합 |

MVP에서는 새 operational event table, endpoint timeseries table, raw explorer table을 만들지 않는다.

### 18.2 발목을 잡을 수 있는 부분

#### 18.2.1 Endpoint errorCount 의미 고정 필요

현재 ingest/request model과 endpoint aggregation은 endpoint별 `errorCount`를 저장하고, status code별 count나 status family breakdown은 저장하지 않는다.

이는 MVP 발목이 아니다. MVP에서 필요한 것은 정확한 HTTP status code가 아니라 "500번대 서버 에러가 있었는가"이기 때문이다. 다만 `errorCount`가 5xx/500번대 서버 에러 count라는 semantic은 starter/ingest/read model 계약에서 명확히 고정해야 한다.

권장 결정:

- MVP에서는 endpoint `errorCount`를 5xx/500번대 서버 에러 count로 정의한다.
- Read model은 status code를 표시하지 않고 "500번대 서버 오류" 또는 "서버 오류"라고 표현한다.
- 정확한 500/502/503/504 breakdown은 MVP 이후 diagnostics 확장으로 둔다.
- 만약 starter가 현재 `errorCount`를 4xx까지 포함한 모든 non-2xx로 세고 있다면, persistence 변경보다 먼저 starter/ingest semantic을 바로잡아야 한다.

#### 18.2.2 Endpoint JSON array의 장기 한계

`endpoints_json`은 MVP bounded endpoint evidence에는 충분하다. 하지만 endpoint별 장기 history, endpoint baseline, endpoint adaptive threshold, endpoint owner/runbook까지 넣기 시작하면 JSON array scan이 병목이 된다.

권장 결정:

- MVP에서는 그대로 사용한다.
- 후속에서 endpoint별 baseline/adaptive threshold가 필요해질 때 `endpoint_metric_bucket_projection` 또는 유사한 read-side projection table을 검토한다.
- 지금 projection table을 먼저 만들지는 않는다.

#### 18.2.3 Baseline column의 의미 혼동

`dashboard_snapshots`에는 `baseline_window_start_utc`, `baseline_window_end_utc`가 not null이다. 그러나 이 문서의 MVP 판단은 baseline을 사용하지 않는다.

권장 결정:

- MVP read model에는 baseline comparison을 넣지 않는다.
- 기존 baseline column은 compatibility metadata로만 둔다.
- Snapshot detail copy와 readSemantics에 "baseline not used for MVP decision"을 명시한다.
- 후속 migration에서 baseline column nullable/semantic rename이 필요한지는 별도 검토한다.

#### 18.2.4 Resource ratio만으로 saturation을 확정할 수 없음

현재 resource field는 CPU/heap/datasource pool usage ratio다. Queue length, DB wait time, GC pause, pool acquire latency 같은 saturation evidence는 없다.

권장 결정:

- MVP에서는 resource를 root cause로 확정하지 않는다.
- Resource threshold 단독 hit는 attention evidence다.
- Resource threshold + request symptom이 함께 있을 때만 shared resource pressure pattern으로 표현한다.

#### 18.2.5 Local percentile의 오해 가능성

`local_percentiles_json`은 instance bucket scope의 starter-local p95/p99 point다. Application 또는 endpoint percentile로 merge할 수 없다.

권장 결정:

- MVP dashboard state와 endpoint priority는 p95/p99를 사용하지 않는다.
- 필요하면 instance detail에서 source-scoped 참고값으로만 표시한다.
- 첫 화면은 histogram 기반 slow share를 사용한다.

#### 18.2.6 Snapshot helper column은 source of truth가 아님

`primary_rule_id`, `primary_endpoint_key`, `max_confidence`는 marker/history 색인을 빠르게 하기 위한 helper다. Detail state/evidence를 대체하면 안 된다.

권장 결정:

- Marker/history list는 helper column을 사용할 수 있다.
- Snapshot detail은 `read_model_json`을 source of truth로 사용한다.
- Helper와 JSON이 충돌하면 JSON detail을 우선하고, helper mismatch는 data integrity issue로 다룬다.

#### 18.2.7 Schema version 고정

`accepted_metric_buckets.schema_version`은 `1.0`으로 고정되어 있다. Future metric field를 추가하려면 ingest schema와 validation/migration 정책이 필요하다.

권장 결정:

- MVP read model 자체에는 `schemaVersion=dashboard_read_model.v1`을 둔다.
- Metric ingest schema extension은 별도 버전으로 다룬다.
- Dashboard read model v1은 source metric schema가 부족한 field를 `evidenceStatus=unavailable`로 표현할 수 있어야 한다.

## 19. MVP Exclusions

아래는 MVP에서 절대 제외한다.

- 시계열 baseline 분석
- 직전 window 비교를 primary 판단 기준으로 사용하는 UX
- 전일 같은 시간대 비교
- 전주 같은 요일/시간대 비교
- adaptive threshold
- endpoint p95/p99 rollup
- endpoint business criticality
- owner/team/runbook metadata
- impact score public UI
- confidence number public UI
- operational event 생성/folding
- incident id, resolvedAt, acknowledgement
- marker 기반 state 재판정
- heartbeat 기반 metric state 변경
- snapshot detail 재계산
- raw metric explorer
- endpoint long-term timeseries projection
- Instance Snapshot Trend / Stored trend / projection trend UI surface
- InstanceTrendView, narrow Sheet, `openTrend` / `openLiveDashboard`, `snapshotTrend` surface
- list read model에 없는 instance row metrics를 프론트에서 재계산하거나 합성해 표시하는 동작

## 20. Test Guard Candidates

구현 시 아래 guard test를 둔다.

- `requestCount=29`, `errorRate>=0.05`는 `degraded`가 아니다.
- `requestCount=30`, `errorRate=0.05`는 error stateReason 후보가 된다.
- `slowShareOver500ms=0.1999`는 latency stateReason이 아니다.
- `slowShareOver500ms=0.20`은 latency stateReason 후보가 된다.
- `datasourcePoolUsage=0.85` 단독은 attention evidence이며 stateReason이 아니다.
- `datasourcePoolUsage=0.85`와 application latency rule hit가 함께 있으면 shared resource pressure stateReason 후보가 된다.
- endpoint error under low sample은 attentionEvidence이며 stateReason이 아니다.
- endpoint `errorCount > 0`은 5xx/500번대 서버 에러 attention evidence로 노출한다.
- heartbeat received는 state를 `active`로 만들지 않는다.
- stale/down freshness는 RED/USE degraded rule보다 우선한다.
- snapshot detail은 stored read model을 반환하고 현재 bucket으로 재계산하지 않는다.
- active snapshot에 endpoint attention evidence가 있어도 marker는 source-of-truth marker rule에 따라 `normal`일 수 있다.

## 21. Final Decisions

1. MVP dashboard read model은 application-scoped current 30-minute decision surface다.
2. Read model은 live API response이자 snapshot storage payload다.
3. MVP 판단은 절대 threshold와 sample guard만 사용한다.
4. State-changing reasons와 attention evidence를 분리한다.
5. Resource threshold 단독 hit는 degraded root cause가 아니다.
6. Shared resource pressure는 resource threshold와 request symptom이 함께 있을 때만 말한다.
7. Endpoint evidence는 느린 API 전체 목록이 아니라 확인 후보다.
8. 500 endpoint는 정확한 HTTP 500 status code가 아니라 5xx/500번대 서버 에러 endpoint로 정의한다.
9. 기존 persistence는 MVP에는 충분하다. 단, endpoint 장기 분석에는 `endpoints_json` array가 후속 한계가 될 수 있다.
10. Persistence 확장은 지금 과하게 하지 않는다. 다만 starter/ingest contract에서 `errorCount=server_error_5xx_count` 의미는 명확히 고정해야 한다.
