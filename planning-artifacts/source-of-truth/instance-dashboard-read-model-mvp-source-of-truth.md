---
artifactType: source-of-truth
projectName: Observation Portal
status: active
date: 2026-06-09
dependsOn:
  - planning-artifacts/source-of-truth/current-product-source-of-truth.md
  - planning-artifacts/source-of-truth/dashboard-snapshot-mvp-source-of-truth.md
  - planning-artifacts/source-of-truth/application-dashboard-read-model-mvp-source-of-truth.md
supersedesForMvp:
  - planning-artifacts/party-context-instance-snapshot-trend.md
---

# Instance Dashboard Read Model MVP Source of Truth

## 1. 문서 목적

이 문서는 Observation Portal MVP에서 Instance Dashboard read model과 API 계약이 무엇을 반환해야 하는지 고정한다.

상위 결정은 아래 문서를 따른다.

- `planning-artifacts/source-of-truth/current-product-source-of-truth.md`
- `planning-artifacts/source-of-truth/dashboard-snapshot-mvp-source-of-truth.md`
- `planning-artifacts/source-of-truth/application-dashboard-read-model-mvp-source-of-truth.md`

이 문서는 Application Dashboard와 Application Snapshot의 기존 결정을 바꾸지 않는다. Instance Dashboard는 Application Dashboard 판단을 대체하지 않고, 특정 application 안의 특정 instance에 대해 같은 최근 30분 window를 instance-level evidence로 설명하는 하위 read model이다.

## 2. 한 문장 정의

Observation Portal MVP Instance Dashboard read model은 Application Dashboard의 최근 30분 판단을 특정 application instance의 요청, endpoint, resource, data quality 증거로 확대해 설명하는 저장 가능한 evidence read model이다.

## 3. Product Promise

MVP Instance Dashboard는 독립적인 instance health product, root cause analyzer, host monitoring console이 아니다.

MVP Instance Dashboard가 약속하는 것은 아래 세 가지다.

1. Application Dashboard와 같은 최근 30분 window에서 selected instance의 관측 증거를 보여준다.
2. Application-level state 판단과 instance-level evidence/status summary를 분리한다.
3. Snapshot mode에서는 선택한 Application Snapshot의 window를 기준으로 현재 저장소에 남아 있는 selected instance metric evidence를 재구성한다.

운영자가 Instance Dashboard 첫 화면에서 읽어야 하는 순서는 아래로 고정한다.

1. 이 화면이 live인지 snapshot 기준 window인지 확인한다.
2. 이 instance가 같은 30분 window에서 관측되었는지, evidence 품질은 충분한지 확인한다.
3. Application 판단과 어떤 방식으로 연결되는지 확인한다.
4. selected instance의 request/error/slow 증상을 확인한다.
5. endpoint evidence가 selected instance에서 관찰되었는지 확인한다.
6. resource pressure hint가 request symptom과 함께 있었는지 확인한다.
7. starter heartbeat는 metric state와 별도 control-plane 정보로만 확인한다.

## 4. Scope

MVP Instance Dashboard는 application-scoped drill-down이다.

- Live source는 `accepted_metric_buckets`다.
- Snapshot mode는 `dashboard_snapshots` row에서 application snapshot window와 참조 metadata를 얻고, selected instance evidence는 `accepted_metric_buckets`에서 재구성한다.
- Application Dashboard timeline, window, snapshot cadence를 따른다.
- Instance Dashboard는 application lifecycle state를 다시 계산하지 않는다.
- Instance Dashboard는 instance별 독립 lifecycle state machine을 만들지 않는다.
- UI는 endpoint evidence, resource evidence, data quality, application contribution을 재계산하지 않는다.

## 5. Hard Contract

아래 항목은 MVP에서 흔들지 않는다.

1. Instance Dashboard 판단 window는 Application Dashboard와 같은 최근 30분이다.
2. Instance Dashboard는 Application Dashboard의 evidence detail이며 application 판단을 대체하지 않는다.
3. Snapshot mode는 선택한 Application Snapshot의 window를 사용하되, selected instance metric evidence는 현재 저장소의 `accepted_metric_buckets`를 다시 읽어 재구성한다.
4. Instance에는 application lifecycle state와 같은 `state`를 두지 않는다.
5. Instance에는 `observationStatus`와 `applicationContribution`만 둔다.
6. `errorCount`는 5xx/500번대 서버 에러 count 의미로 고정한다.
7. `requestCount < 30`이면 instance-level error rate만으로 강한 문제 판단 copy를 쓰지 않는다.
8. Resource signal은 단독 root cause로 확정하지 않는다.
9. Resource threshold와 request symptom이 함께 있을 때만 shared resource pressure pattern으로 표현한다.
10. Starter heartbeat는 metric state, observation status, application state를 직접 바꾸지 않는다.
11. Endpoint `not_observed`는 "정상"이 아니라 "selected instance에서는 해당 endpoint evidence가 관찰되지 않음"이다.
12. Snapshot mode의 instance evidence는 accepted-at cutoff를 적용하지 않으므로 late-arriving metric이 포함될 수 있고, 저장 당시 Application Snapshot 판단과 일부 다를 수 있다.
13. Snapshot mode는 Application Snapshot의 stored state/evidence를 대체하거나 검증하지 않는다.
14. MVP는 baseline 비교, 전일/전주 비교, adaptive threshold, incident folding, impact score public UI, endpoint business criticality, status code breakdown을 사용하지 않는다.

## 6. Terminology

| Term | MVP 의미 |
|---|---|
| `applicationStateRef` | 저장 당시 또는 live 계산 결과의 application-level state 참조. Instance state가 아니다. |
| `observationStatus` | selected instance evidence가 최근 30분 window에서 관측 가능한지 설명하는 상태 요약. lifecycle state가 아니다. |
| `applicationContribution` | selected instance evidence가 application 판단을 설명하는 정도. causality나 root cause가 아니다. |
| `requestEvidence` 또는 `signals.red` | selected instance의 요청량, 5xx 오류, 500ms 초과 요청 비율 증거다. |
| `resourceEvidence` 또는 `signals.use` | selected instance의 CPU/heap/datasource pool 사용률 hint다. |
| `endpointEvidence` | application endpoint evidence와 selected instance 관측 여부를 연결한 bounded evidence다. |
| `starterConnection` | starter heartbeat/control-plane 연결 정보다. metric state와 분리한다. |
| `snapshot` | selected Application Snapshot row와 그 window를 가리키는 metadata다. Instance evidence source가 아니다. |
| `readSemantics` | live/snapshot mode의 source, window source, cutoff 적용 여부, late metric 포함 가능성을 설명하는 계약 block이다. |

## 7. Application Dashboard와의 관계

Instance Dashboard는 아래 관계를 가진다.

```text
Application Dashboard
  = application-level 최근 30분 운영 판단

Instance Dashboard
  = 같은 application, 같은 최근 30분 window, selected instance evidence detail

Application Snapshot
  = application dashboard read model 저장본

Instance Snapshot Detail
  = 선택한 application snapshot window 기준 selected instance metric evidence 재구성
```

Application Dashboard가 lifecycle state의 owner다. Instance Dashboard는 application state를 참조할 수 있지만, instance state를 새로 만들거나 application state를 override하지 않는다.

## 8. Live and Snapshot Mode

| Mode | Source | 계산 여부 | 의미 |
|---|---|---|---|
| `live` | `accepted_metric_buckets` | 현재 query 시각 기준 최근 30분 instance evidence 계산 | 지금 selected instance evidence |
| `snapshot` | `dashboard_snapshots` row metadata + `accepted_metric_buckets` | selected application snapshot window 기준 instance evidence 재구성 | 선택한 snapshot 구간의 selected instance evidence |

권장 API shape:

```http
GET /api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/dashboard
GET /api/projects/{projectId}/applications/{applicationId}/snapshots/{snapshotId}/instances/{instanceId}/dashboard
```

Snapshot mode는 `dashboard_snapshots` row에서 아래 metadata만 참조한다.

- `snapshotId`
- `generatedAt`
- `currentWindowStartUtc`
- `currentWindowEndUtc`
- `captureReason`
- `stateCode`
- marker/detail link에 필요한 row metadata

Selected instance의 request, endpoint, histogram, resource metric evidence는 `currentWindowStartUtc < bucket_end_utc <= currentWindowEndUtc` 조건으로 현재 저장소의 `accepted_metric_buckets`를 다시 읽어 구성한다. 이때 `accepted_at <= snapshotCutoffAt` cutoff는 적용하지 않는다.

따라서 Instance Dashboard snapshot mode는 늦게 수용된 metric을 포함할 수 있다. 이 차이는 의도된 계약이며, read model에는 `mayDifferFromStoredApplicationSnapshot=true`를 명시한다.

Snapshot mode가 하지 않는 일:

- Application Snapshot의 stored state/evidence 재계산
- marker bucket 기반 state/evidence 재판정
- selected instance lifecycle state 생성
- late metric 때문에 Application Snapshot state를 수정하거나 override

## 9. Window와 Cadence

Instance Dashboard window는 Application Dashboard와 같은 최근 30분이다.

```text
windowStartUtc = evaluatedAt - 30 minutes
windowEndUtc = evaluatedAt
source bucket = bucket_end_utc > windowStartUtc and bucket_end_utc <= windowEndUtc
```

기존 Instance Evidence API의 `current_15m` 계약은 Instance Dashboard MVP 계약이 아니다. Instance Dashboard MVP에서는 `recent_30_minutes` 또는 동등한 30분 window 이름을 사용한다.

Snapshot cadence는 Application Snapshot timeline을 따른다.

- Application scheduled snapshot cadence: 30분
- Instance Dashboard snapshot mode: 해당 application snapshot의 30분 window를 기준으로 selected instance metric evidence 재구성
- 별도 instance snapshot cadence: MVP에서 만들지 않음
- 별도 instance timeline marker: MVP에서 만들지 않음

## 10. Instance State 결정

MVP에서는 instance별 독립 lifecycle state machine을 만들지 않는다.

금지 field/name:

- `state`
- `stateCode`
- `health`
- `healthScore`
- `degraded`
- `down`
- `rootCause`
- `cause`
- `rootCauseCandidate`

허용 field:

| Field | 허용 값 | 의미 |
|---|---|---|
| `observationStatus.code` | `observed` | selected instance evidence가 window 안에서 관측됨 |
| `observationStatus.code` | `not_observed_in_window` | selected instance bucket이 window 안에 없음 |
| `observationStatus.code` | `metric_stale` | 마지막 accepted bucket이 freshness 기준상 오래됨 |
| `observationStatus.code` | `metric_missing` | metric evidence가 없음 |
| `observationStatus.code` | `insufficient_evidence` | sample 또는 필수 evidence가 부족함 |
| `observationStatus.code` | `malformed_evidence` | endpoint/resource/histogram evidence가 해석 불가능함 |

`observationStatus`는 "좋음/나쁨" 판단이 아니라 evidence availability와 freshness 설명이다.

`applicationContribution.level`은 아래로 제한한다.

| Level | 의미 |
|---|---|
| `none` | application 판단과 연결된 instance evidence가 없음 |
| `attention` | state를 바꾸지는 않지만 확인할 evidence가 있음 |
| `supporting` | application attention/state reason을 설명하는 보조 증거가 있음 |
| `contributing` | application stateReason 또는 firstLookCandidate와 명시적으로 연결된 증거가 있음 |
| `insufficient` | 연결 여부를 판단할 evidence가 부족함 |

## 11. First Screen Information Hierarchy

Instance Dashboard 첫 화면은 아래 순서를 따른다.

1. Context bar
   - application name/environment
   - instance name/id
   - mode: `live` 또는 `snapshot`
   - window start/end
   - snapshot capturedAt/captureReason when snapshot mode
2. Application state reference
   - application `state.code`
   - stateReason refs
   - attentionEvidence refs
   - copy: "Application 판단 참조"
3. Observation and data quality
   - `observationStatus`
   - bucket coverage
   - sample readiness
   - missing/malformed limitations
4. Request evidence
   - request count
   - 5xx error count/rate
   - 500ms 초과 요청 비율
5. Endpoint evidence
   - application endpoint evidence와 selected instance 관찰 여부
   - 최대 5개
6. Resource evidence
   - CPU/heap/datasource pool threshold hint
   - request symptom과 함께 있는지 여부
7. Starter connection
   - heartbeat source, lastHeartbeatAt, status
   - metric state에 영향 없음 표시

## 12. RED 사용자 언어 매핑

RED는 request symptom으로 표현한다.

| 내부 개념 | 사용자 언어 | Field |
|---|---|---|
| Rate | 요청량 | `signals.red.requestCount` |
| Errors | 500번대 서버 오류 | `signals.red.errorCount`, `signals.red.errorRate` |
| Duration | 느린 요청 | `signals.red.slowCountOver500ms`, `signals.red.slowShareOver500ms` |

Instance Dashboard도 Application Dashboard와 같은 threshold를 사용한다.

| Signal | 기준 |
|---|---:|
| 최소 요청 수 | `requestCount >= 30` |
| 오류율 | `errorRate >= 0.05` |
| 느린 요청 비율 | `slowShareOver500ms >= 0.20` |

`requestCount < 30`일 때 error가 있어도 copy는 "운영 기준 초과"가 아니라 "최근 500번대 서버 오류 관찰" 또는 "표본 부족으로 강한 판단 보류"로 쓴다.

## 13. USE 사용자 언어 매핑

USE는 resource pressure hint로 표현한다.

| 내부 개념 | 사용자 언어 | Field |
|---|---|---|
| CPU utilization | CPU 사용률 | `signals.use.cpuUsage` |
| Heap utilization | Heap 사용률 | `signals.use.heapUsage` |
| Datasource pool utilization/saturation | DB pool 사용률/포화 | `signals.use.datasourcePoolUsage` |

Resource evidence field는 아래를 포함한다.

- `resourceKey`
- `scope=instance`
- `usage.max`
- `threshold`
- `status`
- `observedAt`
- `requestSymptomPresent`
- `patternContribution`
- `operatorText`

`patternContribution` 값은 아래로 제한한다.

| 값 | 의미 |
|---|---|
| `none` | threshold hit가 없거나 pattern 판단에 쓰이지 않음 |
| `attention_only` | resource threshold hit는 있으나 request symptom이 없음 |
| `shared_resource_pressure_pattern` | resource threshold와 request symptom이 같은 window에서 관찰됨 |

## 14. Endpoint Evidence Policy

Endpoint evidence는 Application Dashboard endpoint evidence와 selected instance의 관측 여부를 연결한다.

### 14.1 노출 조건

Endpoint는 아래 중 하나를 만족하면 evidence 후보가 된다.

1. Application Dashboard endpoint evidence에 포함된 endpoint가 selected instance에서 `observed`, `not_observed`, `insufficient` 중 하나로 확인 필요
2. selected instance endpoint `errorCount > 0`
3. selected instance endpoint `requestCount >= 30` and `errorRate >= 0.05`
4. selected instance endpoint `requestCount >= 30` and `slowShareOver500ms >= 0.20`

### 14.2 Display Policy

- `endpointEvidence.items`는 최대 5개다.
- `source`는 `accepted_metric_buckets.endpoints_json`이다.
- `scope`는 `instance_recent_30_minutes`다.
- `presenceOnSelectedInstance`는 `observed`, `not_observed`, `insufficient`만 허용한다.
- `not_observed`는 "문제 없음"이 아니라 "selected instance에서는 해당 endpoint evidence가 관찰되지 않음"이다.
- `relatedApplicationEndpointEvidenceRef`는 causality가 아니라 같은 저장 시점의 anchor다.

### 14.3 5xx/500번대 Server Error Contract

`errorCount`는 정확한 HTTP `500` 하나가 아니라 5xx/500번대 서버 에러 count다.

MVP에서는 개별 status code breakdown을 수집하거나 표시하지 않는다.

```json
{
  "errorCount": 1,
  "errorSemantic": "server_error_5xx"
}
```

권장 copy:

```text
이 인스턴스의 endpoint에서 500번대 서버 오류가 관찰되었습니다.
```

## 15. Resource Evidence Policy

Resource evidence는 root cause claim이 아니다.

| 상황 | 표현 |
|---|---|
| resource threshold hit 없음 | "resource 사용률은 기준 이하입니다." |
| resource threshold 단독 hit | "resource 사용률이 기준 이상이지만 요청 오류/지연 증상이 함께 관찰되지는 않았습니다." |
| resource threshold + request symptom | "요청 증상과 resource 압박이 같은 window에서 관찰됩니다." |

금지 copy:

- "CPU가 원인입니다."
- "DB pool 때문에 장애입니다."
- "이 instance가 문제를 발생시켰습니다."

권장 copy:

- "CPU 사용률이 기준 이상입니다. 같은 window의 요청 지연/오류와 함께 확인하세요."
- "DB pool 사용률이 기준 이상입니다. 여러 endpoint 지연과 함께 connection wait 가능성을 확인하세요."
- "요청 증상과 resource 압박 패턴이 같은 구간에 나타났습니다."

## 16. Data Quality and Starter Connection

Data quality는 metric evidence 품질이다. Starter connection은 control-plane 정보다. 둘은 분리한다.

권장 `dataQuality.state`:

| State | 의미 |
|---|---|
| `sufficient` | freshness와 sample이 판단 가능 |
| `sample_limited` | requestCount가 최소 기준보다 낮음 |
| `not_observed_in_window` | selected instance bucket이 window 안에 없음 |
| `stale` | 최근 metric이 stale boundary를 넘음 |
| `down` | 최근 metric 공백이 down boundary를 넘음. application state가 아니라 metric availability 설명이다. |
| `partial` | 일부 metric/histogram/resource evidence가 누락됨 |
| `malformed` | endpoint JSON 또는 histogram evidence가 읽기 불가능 |

`starterConnection.stateImpact`는 항상 아래 의미 중 하나여야 한다.

| 값 | 의미 |
|---|---|
| `does_not_change_metric_state` | heartbeat는 metric state를 직접 바꾸지 않음 |
| `control_plane_only` | starter 연결 참고 정보로만 사용 |

Heartbeat가 최근이라고 해서 instance metric evidence가 충분한 것은 아니다. Heartbeat가 없다고 host application down을 확정할 수도 없다.

## 17. Snapshot Mode Reconstruction Contract

MVP에서는 별도 `instance_dashboard_snapshots` table을 만들지 않는다.

Instance Dashboard snapshot mode는 Application Snapshot row를 선택 기준으로 사용한다. 그러나 selected instance evidence의 source of truth는 snapshot JSON 안의 instance summary 조각이 아니라 현재 저장소에 남아 있는 `accepted_metric_buckets`다.

```text
Application Snapshot row
  -> snapshotId, generatedAt, captureReason, stateCode
  -> currentWindowStartUtc, currentWindowEndUtc

Instance Dashboard snapshot mode
  -> 위 window 안의 selected instance accepted bucket을 cutoff 없이 다시 읽음
  -> request/endpoint/histogram/resource evidence 재구성
```

Application Snapshot의 stored read model은 그대로 유지한다.

- Application Snapshot Detail의 state/evidence source of truth는 `dashboard_snapshots.read_model_json`이다.
- Instance Dashboard snapshot mode는 Application Snapshot Detail의 state/evidence를 대체하지 않는다.
- Instance Dashboard snapshot mode는 selected instance evidence를 보여주는 하위 detail이다.
- 늦게 도착한 metric이 포함될 수 있으므로 Application Snapshot 저장 당시 판단과 수치가 일부 다를 수 있다.

`instanceSummary.items[]`는 여전히 Application Snapshot과 Instance Snapshot Trend의 bounded summary source로 유지한다. 다만 Instance Dashboard snapshot mode의 필수 source는 아니다.

2026-06-11 UI MVP 결정: 이 보존은 read-model/backward compatibility 계약이다. 현재 MVP UI는 Instance Snapshot Trend, Stored trend, projection trend, InstanceTrendView, narrow Sheet, `openTrend`/`openLiveDashboard`, `snapshotTrend` surface를 열지 않는다. 과거 instance evidence는 Snapshot/History에서 snapshot을 선택한 뒤 snapshot-mode wide modal로 본다.

기존 `instanceSummary.items[]` minimum shape는 유지한다. 기존 trend parser가 의존하는 field 이름과 의미를 바꾸지 않는다.

Snapshot instance detail은 아래 동작을 금지한다.

- Application Snapshot stored read model 재계산
- Application Snapshot state override
- endpoint priority 재계산
- resource root cause 재판정
- marker bucket 기반 state/evidence 재판정
- `accepted_at` cutoff 적용으로 late metric을 의도적으로 숨기기

## 18. Application Snapshot Timeline과의 관계

Instance Snapshot Detail은 Application Snapshot timeline의 selected point를 따라가되, selected instance evidence는 해당 snapshot window의 현재 저장 metric 기준으로 재구성한다.

```text
Application timeline marker 선택
  -> dashboard_snapshots row 선택
  -> row의 currentWindowStartUtc/currentWindowEndUtc 확인
  -> selected instance의 accepted_metric_buckets를 cutoff 없이 조회
  -> Instance Dashboard evidence 구성
```

Instance marker, instance incident period, instance resolvedAt은 MVP에서 만들지 않는다.

후속에서 Instance trend API가 다시 필요하면 기존처럼 stored `instanceSummary.items[]` projection으로 제한한다. Trend는 current state나 health score를 재계산하지 않는다. 현재 MVP UI에는 이 read surface를 노출하지 않으며, Instance Dashboard snapshot mode와 Instance Snapshot Trend는 서로 다른 read surface다.

## 19. Persistence Reuse Assessment

현재 persistence는 MVP Instance Dashboard read model을 만들기에 대체로 충분하다.

### 19.1 재사용하기 좋은 부분

| Persistence | MVP 적합성 |
|---|---|
| `accepted_metric_buckets.application_instance_id` | selected instance 최근 30분 window 조회에 적합 |
| `accepted_metric_buckets.request_count`, `error_count` | instance-level RED count/rate 계산에 적합 |
| `accepted_metric_buckets.duration_buckets_json` | instance-level slow share over 500ms 계산에 적합 |
| `accepted_metric_buckets.endpoints_json` | bounded endpoint evidence에 적합 |
| `accepted_metric_buckets.cpu_usage_ratio`, `heap_used_ratio`, `datasource_pool_usage_ratio` | resource hint에 적합 |
| `accepted_metric_buckets.local_percentiles_json` | source-scoped starter percentile point 표시에는 적합 |
| `application_instances` | instance identity, first/last seen 표시와 membership 검증에 적합 |
| `dashboard_snapshots` row metadata | snapshot mode의 window, capture metadata, application state reference source로 적합 |
| `dashboard_snapshots.read_model_json.instanceSummary.items[]` | backend/post-MVP instance trend와 snapshot summary projection에는 적합. 현재 MVP Instance Dashboard detail UI의 필수 source는 아님 |

### 19.2 발목을 잡을 수 있는 부분

#### 19.2.1 기존 Instance Evidence의 15분 window

기존 Instance Evidence 계약은 `current_15m`이다. Instance Dashboard MVP는 Application Dashboard와 맞춘 최근 30분이다.

권장 결정:

- 기존 `current_15m` evidence API를 Instance Dashboard 계약으로 재사용하지 않는다.
- Instance Dashboard는 `recent_30_minutes` window를 명시한다.
- 기존 API를 유지한다면 이름과 목적을 "current short evidence detail"로 분리한다.

#### 19.2.2 `instanceSummary.items[]` cap

Application Snapshot에는 bounded instance summary만 저장한다. 그러나 Instance Dashboard snapshot mode는 `instanceSummary.items[]`를 필수 source로 쓰지 않고 selected instance metric을 window 기준으로 다시 읽는다.

권장 결정:

- `items[]` 최대 50개를 유지한다.
- `instanceSummary.items[]` cap은 보존된 Instance Snapshot Trend read-model contract와 snapshot summary 노출 범위의 제한으로만 본다.
- Instance Dashboard snapshot mode에서는 target instance가 summary cap 밖이어도 `application_instances` membership이 유효하고 metric retention 안에 bucket이 있으면 evidence를 재구성할 수 있다.
- metric retention 밖이면 `observationStatus=metric_missing` 또는 `not_observed_in_window`와 data quality limitation으로 표현한다.

#### 19.2.3 Late-arriving metric에 따른 정합성 차이

Instance Dashboard snapshot mode는 `accepted_at` cutoff를 적용하지 않는다. 따라서 snapshot 저장 이후 늦게 수용된 metric이 selected instance evidence에 포함될 수 있다.

권장 결정:

- 이 차이를 오류로 보지 않는다.
- read model에 `includesLateAcceptedMetrics=true`, `mayDifferFromStoredApplicationSnapshot=true`를 명시한다.
- UI copy는 "Application Snapshot 판단과 일부 다를 수 있음"을 짧게 안내한다.
- Application Snapshot Detail의 stored state/evidence는 여전히 `dashboard_snapshots.read_model_json`을 source of truth로 둔다.

#### 19.2.4 Endpoint JSON array의 장기 한계

`endpoints_json`은 MVP bounded evidence에는 충분하지만 endpoint별 장기 trend, baseline, adaptive threshold에는 한계가 있다.

권장 결정:

- MVP에서는 그대로 사용한다.
- 후속에서 endpoint long-term projection table을 검토한다.

#### 19.2.5 Resource ratio의 의미 한계

현재 resource field는 latest valid sample 성격의 ratio다. Queue length, DB wait time, GC pause, pool acquire latency는 없다.

권장 결정:

- MVP에서는 resource를 root cause로 확정하지 않는다.
- Resource threshold 단독 hit는 attention evidence다.
- Resource threshold + request symptom이 함께 있을 때만 shared resource pressure pattern으로 표현한다.

#### 19.2.6 Local percentile의 오해 가능성

`local_percentiles_json`은 starter canonical point다. 여러 bucket 또는 여러 instance를 평균/merge해서 application 또는 endpoint percentile로 만들 수 없다.

권장 결정:

- Instance Dashboard에서 source-scoped 참고 series 또는 latest point로만 표시한다.
- State 판단이나 endpoint priority 계산에는 사용하지 않는다.
- Application/endpoint p95/p99 rollup은 MVP에서 만들지 않는다.

## 20. MVP Exclusions

아래는 MVP에서 절대 제외한다.

- instance lifecycle state machine
- instance health score
- instance root cause candidate
- instance incident period/resolvedAt
- instance marker timeline
- instance snapshot 별도 table
- heartbeat 기반 metric state 변경
- resource-only root cause claim
- endpoint status code breakdown
- endpoint business criticality
- owner/team/runbook metadata
- impact score public UI
- confidence number public UI
- baseline comparison
- previous day/week comparison
- adaptive threshold
- incident folding
- raw metric explorer
- endpoint long-term timeseries projection
- Stored trend / projection trend UI surface
- InstanceTrendView, narrow Sheet, `openTrend` / `openLiveDashboard`, `snapshotTrend` surface
- Application Snapshot detail 재계산
- Instance Dashboard snapshot mode에서 application state/evidence 재판정

## 21. Read Model Shape Draft

아래 shape는 MVP 계약의 기준이다. 실제 Java record naming은 기존 camelCase API 스타일을 따른다.

```json
{
  "schemaVersion": "instance_dashboard_read_model.v1",
  "mode": "live",
  "generatedAt": "2026-06-09T00:30:00Z",
  "application": {
    "projectId": "00000000-0000-0000-0000-000000000000",
    "applicationId": "00000000-0000-0000-0000-000000000000",
    "name": "orders-api",
    "environment": "prod"
  },
  "instance": {
    "instanceId": "00000000-0000-0000-0000-000000000000",
    "instanceName": "orders-api-7f9c",
    "firstSeenAt": "2026-06-08T10:00:00Z",
    "lastSeenAt": "2026-06-09T00:29:30Z"
  },
  "window": {
    "type": "recent_30_minutes",
    "startUtc": "2026-06-09T00:00:00Z",
    "endUtc": "2026-06-09T00:30:00Z",
    "bucketDurationSeconds": 30
  },
  "thresholds": {
    "minimumRequestCount": 30,
    "errorRate": 0.05,
    "slowShareOver500ms": 0.20,
    "datasourcePoolUsage": 0.85,
    "cpuUsage": 0.85,
    "heapUsage": 0.90
  },
  "applicationStateRef": {
    "stateCode": "degraded",
    "stateLabel": "확인 필요",
    "stateReasonRefs": [
      "application_error_rate_high"
    ],
    "attentionEvidenceRefs": [
      "endpoint-evidence-1"
    ],
    "lifecycleOwner": "application"
  },
  "observationStatus": {
    "code": "observed",
    "operatorText": "이 인스턴스에서 최근 30분 metric evidence가 관찰되었습니다."
  },
  "applicationContribution": {
    "level": "contributing",
    "reasonCodes": [
      "selected_instance_endpoint_server_error_observed"
    ],
    "operatorText": "Application 판단을 설명하는 instance-level evidence가 있습니다.",
    "causalityClaim": false
  },
  "dataQuality": {
    "state": "sufficient",
    "expectedBucketCount": 60,
    "observedBucketCount": 60,
    "requestCount": 128,
    "minimumRequestCount": 30,
    "lastObservedAt": "2026-06-09T00:29:30Z",
    "limitations": []
  },
  "starterConnection": {
    "statusSource": "starter_heartbeat",
    "lastHeartbeatAt": "2026-06-09T00:29:45Z",
    "lastHeartbeatStatus": "received",
    "connectionMeaning": "starter 연결 참고 정보입니다.",
    "stateImpact": "does_not_change_metric_state"
  },
  "signals": {
    "red": {
      "requestCount": 128,
      "errorCount": 7,
      "errorSemantic": "server_error_5xx",
      "errorRate": 0.0547,
      "slowCountOver500ms": 31,
      "slowShareOver500ms": 0.242,
      "latencyEvidenceStatus": "available",
      "sampleReady": true
    },
    "use": {
      "cpuUsage": {
        "max": 0.82,
        "threshold": 0.85,
        "status": "normal",
        "observedAt": "2026-06-09T00:29:30Z"
      },
      "heapUsage": {
        "max": 0.91,
        "threshold": 0.90,
        "status": "threshold_hit",
        "observedAt": "2026-06-09T00:29:30Z"
      },
      "datasourcePoolUsage": {
        "max": 0.88,
        "threshold": 0.85,
        "status": "threshold_hit",
        "observedAt": "2026-06-09T00:29:30Z"
      }
    }
  },
  "endpointEvidence": {
    "source": "accepted_metric_buckets.endpoints_json",
    "scope": "instance_recent_30_minutes",
    "maxItems": 5,
    "selectionPolicy": "application_evidence_presence_then_instance_symptom",
    "items": [
      {
        "method": "POST",
        "route": "/orders",
        "endpointKey": "POST /orders",
        "presenceOnSelectedInstance": "observed",
        "instanceRequestCount": 42,
        "instanceErrorCount": 4,
        "errorSemantic": "server_error_5xx",
        "instanceErrorRate": 0.0952,
        "instanceSlowShareOver500ms": 0.31,
        "relatedApplicationEndpointEvidenceRef": "endpoint-evidence-1",
        "affectsApplicationLifecycleState": false,
        "causalityClaim": false,
        "operatorText": "이 인스턴스에서 같은 endpoint의 500번대 서버 오류가 관찰되었습니다."
      }
    ]
  },
  "resourceEvidence": [
    {
      "resourceKey": "datasource_pool",
      "scope": "instance",
      "usage": 0.88,
      "threshold": 0.85,
      "status": "threshold_hit",
      "observedAt": "2026-06-09T00:29:30Z",
      "requestSymptomPresent": true,
      "patternContribution": "shared_resource_pressure_pattern",
      "operatorText": "DB pool 사용률과 요청 증상이 같은 window에서 관찰됩니다."
    }
  ],
  "patterns": [
    {
      "code": "shared_resource_pressure_pattern",
      "operatorText": "요청 증상과 resource 압박이 같은 window에서 관찰되었습니다.",
      "rootCauseClaim": false
    }
  ],
  "snapshot": null,
  "readSemantics": {
    "source": "accepted_metric_buckets",
    "windowSource": "current_query_time",
    "acceptedAtCutoffApplied": false,
    "includesLateAcceptedMetrics": false,
    "mayDifferFromStoredApplicationSnapshot": false,
    "applicationSnapshotRecalculated": false,
    "instanceEvidenceReconstructedFromMetrics": false,
    "markerIsStateSource": false,
    "instanceLifecycleStateMachine": false
  },
  "links": {
    "applicationDashboard": "/api/projects/{projectId}/applications/{applicationId}/dashboard",
    "snapshotDetail": null
  },
  "excludedCapabilities": [
    "instance_lifecycle_state_machine",
    "instance_health_score",
    "instance_root_cause",
    "instance_snapshot_table",
    "time_series_baseline",
    "adaptive_threshold",
    "incident_folding",
    "heartbeat_as_metric_state"
  ]
}
```

Snapshot mode는 같은 shape를 사용하되 `mode=snapshot`, `snapshot` metadata, `windowSource=selected_application_snapshot`, `readSemantics.source=accepted_metric_buckets`를 넣는다.

```json
{
  "mode": "snapshot",
  "snapshot": {
    "snapshotId": "00000000-0000-0000-0000-000000000000",
    "capturedAt": "2026-06-09T00:30:05Z",
    "captureReason": "hourly_scheduled",
    "windowStartUtc": "2026-06-09T00:00:00Z",
    "windowEndUtc": "2026-06-09T00:30:00Z",
    "applicationStateCode": "degraded",
    "markerBucket": "attention"
  },
  "readSemantics": {
    "source": "accepted_metric_buckets",
    "windowSource": "selected_application_snapshot",
    "snapshotRowSource": "dashboard_snapshots",
    "acceptedAtCutoffApplied": false,
    "includesLateAcceptedMetrics": true,
    "mayDifferFromStoredApplicationSnapshot": true,
    "applicationSnapshotRecalculated": false,
    "instanceEvidenceReconstructedFromMetrics": true,
    "markerIsStateSource": false,
    "instanceLifecycleStateMachine": false
  }
}
```

## 22. Snapshot Mode Response Required Fields

Instance Dashboard snapshot mode response에는 아래 field가 포함되어야 한다.

- `schemaVersion`
- `mode`
- `generatedAt`
- `application`
- `instance`
- `window`
- `thresholds`
- `applicationStateRef`
- `observationStatus`
- `applicationContribution`
- `dataQuality`
- `starterConnection`
- `signals`
- `endpointEvidence`
- `resourceEvidence`
- `patterns`
- `snapshot`
- `readSemantics`
- `links`
- `excludedCapabilities`

Snapshot mode에서 `snapshot` block은 selected Application Snapshot row metadata를 담는다. `signals`, `endpointEvidence`, `resourceEvidence`, `dataQuality`는 selected snapshot window 안의 현재 저장 metric 기준으로 재구성된 field다.

Helper column이나 marker는 instance detail state/evidence를 대체하지 않는다. Application Snapshot stored state/evidence의 source of truth는 계속 `dashboard_snapshots.read_model_json`이다.

## 23. Test Guard Candidates

구현 시 아래 guard test를 둔다.

- Instance Dashboard response에는 top-level `state` field가 없다.
- `applicationStateRef.lifecycleOwner`는 항상 `application`이다.
- `observationStatus`는 allowed enum만 사용한다.
- `requestCount=29`, `errorCount>0`이어도 instance degraded/health/rootCause field가 생기지 않는다.
- `resource threshold hit` 단독은 `patternContribution=attention_only`이며 root cause copy를 만들지 않는다.
- `resource threshold hit + request symptom`은 `shared_resource_pressure_pattern` evidence만 만든다.
- heartbeat received는 `observationStatus=observed`나 application state를 만들지 않는다.
- heartbeat missing은 host down 또는 instance down copy를 만들지 않는다.
- endpoint `not_observed`는 "정상" copy로 렌더되지 않는다.
- endpoint `errorCount > 0`은 `server_error_5xx` semantic을 가진다.
- snapshot mode는 selected application snapshot row의 current window를 사용한다.
- snapshot mode는 selected instance metric evidence 조회 시 `accepted_at` cutoff를 적용하지 않는다.
- snapshot mode는 `includesLateAcceptedMetrics=true`, `mayDifferFromStoredApplicationSnapshot=true`를 반환한다.
- snapshot mode는 Application Snapshot stored state/evidence를 재계산하거나 override하지 않는다.
- snapshot mode는 `instanceSummary.items[]` target item 존재 여부에 의존하지 않는다.
- metric retention 밖이거나 bucket이 없으면 `metric_missing` 또는 `not_observed_in_window` data quality로 표현한다.
- malformed endpoint/resource item은 suppress 또는 limitation으로 표현하고 전체 response를 실패시키지 않는다.

## 24. Final Decisions

1. Instance Dashboard MVP는 application-scoped instance evidence read model이다.
2. Instance Dashboard는 Application Dashboard 판단을 대체하지 않는다.
3. Live mode는 selected instance의 최근 30분 accepted bucket에서 계산한다.
4. Snapshot mode는 selected Application Snapshot row의 window를 기준으로 현재 저장소의 selected instance accepted bucket을 cutoff 없이 다시 읽어 evidence를 재구성한다.
5. 판단 window와 snapshot cadence는 Application Dashboard/Snapshot과 같은 30분을 따른다.
6. Instance별 lifecycle state machine은 MVP에서 만들지 않는다.
7. Instance에는 `observationStatus`와 `applicationContribution`만 둔다.
8. Endpoint evidence는 application evidence와 selected instance 관측 여부를 연결하되 causality를 단정하지 않는다.
9. Resource evidence는 request symptom과 함께 있을 때만 shared resource pressure pattern으로 표현한다.
10. Starter heartbeat는 metric state와 분리된 control-plane 정보다.
11. 별도 instance snapshot table은 MVP에서 만들지 않는다.
12. 기존 `instanceSummary.items[]` minimum shape는 보존된 Instance Snapshot Trend read-model contract와 snapshot summary를 위해 유지하되, 현재 MVP UI에 trend surface를 노출하거나 Instance Dashboard snapshot mode의 필수 source로 만들지 않는다.
13. Snapshot mode의 instance evidence는 late-arriving metric 때문에 저장 당시 Application Snapshot 판단과 일부 다를 수 있다.
14. Application Snapshot stored state/evidence의 source of truth는 계속 `dashboard_snapshots.read_model_json`이다.
15. 기존 persistence는 MVP에는 충분하지만, 15분 instance evidence 계약과 30분 dashboard 계약은 분리해야 한다.
16. 후속 baseline, adaptive threshold, incident folding, endpoint projection은 이 MVP 계약 위에서 별도 논의한다.
