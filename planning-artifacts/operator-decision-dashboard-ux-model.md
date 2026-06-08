---
artifactType: ux-decision-model
projectName: Observation Portal
status: proposed
date: 2026-06-07
relatedDesignNote: planning-artifacts/operator-decision-dashboard-redesign.md
relatedMockup: planning-artifacts/mockups/operator-decision-dashboard-mockup.html
---

# Observation Portal 운영 판단 UX 모델

## 목적

이 문서는 화면 목업 디자인이 아니라, 영속화된 관측 데이터를 바탕으로 운영자가 읽는 UI 모델을 어떻게 만들지 정의한다.

핵심 질문은 다음이다.

- 어떤 데이터를 판단의 원천으로 삼는가?
- 어떤 문제 후보를 만든다?
- 어떤 기준으로 우선순위를 정한다?
- RED, USE, golden signals 같은 내부 개념을 사용자 언어로 어떻게 번역한다?
- 저활동, 초기 수집, 테스트 데이터처럼 판단이 어려운 상태를 어떻게 보여준다?
- snapshot을 flight recorder처럼 보이게 하려면 어떤 증거 묶음이 필요한가?

## 한 줄 모델

영속화 데이터는 metric 목록으로 직접 노출하지 않고, `운영 판단 요약 -> 먼저 볼 후보 -> 후보별 증거 -> 판단 신뢰도 -> 다음 행동`으로 변환해 보여준다.

## 현재 영속화 데이터와 UX 역할

| 영속화 / read source | 주요 데이터 | UX에서의 역할 |
|---|---|---|
| `applications` | application name, environment, status, first/last seen | 사용자가 보는 service scope, application rail 항목 |
| `application_instances` | instance name, first/last seen | instance spread, 특정 instance 편중 판단 |
| `accepted_metric_buckets` | 30초 bucket start/end, accepted_at | current/baseline window, timeline, freshness 판단 |
| `accepted_metric_buckets.request_count` | application 전체 요청 수 | traffic, sample size, impact weight |
| `accepted_metric_buckets.error_count` | application 전체 오류 수 | failure signal, error rate, degraded 후보 |
| `accepted_metric_buckets.duration_buckets_json` | application duration histogram | latency signal, slow share, baseline comparison |
| `accepted_metric_buckets.endpoints_json` | endpoint별 request/error/duration bucket | endpoint priority, affected endpoint evidence |
| `accepted_metric_buckets.local_percentiles_json` | starter local p95/p99 | instance 단위 source-scoped p95/p99 표시 |
| `accepted_metric_buckets.cpu_usage_ratio` | CPU 사용률 | USE 관점 resource pressure hint |
| `accepted_metric_buckets.heap_used_ratio` | heap 사용률 | memory pressure hint |
| `accepted_metric_buckets.datasource_pool_usage_ratio` | datasource pool 사용률 | DB connection pool saturation hint |
| `starter_heartbeat_telemetry` | heartbeat, starter status | control-plane 연결 상태. metric state와 분리 |
| `dashboard_snapshots` | stored read_model_json, state_code, capture_reason | flight recorder, operational event history |
| `dashboard_snapshots.primary_rule_id` | 대표 rule | snapshot/event list의 빠른 요약 |
| `dashboard_snapshots.primary_endpoint_key` | 대표 endpoint | snapshot/detail 진입 anchor |
| `dashboard_snapshots.max_confidence` | 저장 당시 최대 confidence | event history 우선순위와 회고 신뢰도 |

## UX 변환 파이프라인

UI는 DB row를 그대로 보여주지 않는다. 다음 순서로 판단 모델을 만든다.

```text
영속화 원천
  -> window 정렬
  -> data quality 판단
  -> signal 추출
  -> 문제 후보 생성
  -> impact / confidence 계산
  -> Look First queue
  -> operator-facing copy
  -> detail / snapshot evidence bundle
```

### 1. Window 정렬

모든 현재 판단은 동일한 시간 기준으로 맞춘다.

- current window: 최근 15분
- baseline window: 직전 15분
- bucket unit: 30초
- snapshot detail: 저장 당시 current/baseline window를 그대로 사용

UI는 "지금 값"만 보이지 않고 "평소 대비 어떻게 달라졌는가"를 먼저 보여준다.

### 2. Data Quality 판단

우선순위 계산보다 먼저 data quality를 판단한다. 데이터가 부족하면 문제 없음처럼 보이면 안 된다.

권장 상태:

| State | 조건 예시 | UI 표현 |
|---|---|---|
| `sufficient` | current window 요청 수와 bucket 수가 충분함 | 판단 가능 |
| `initializing` | 첫 데이터 또는 baseline 부족 | 기준선 수집 중 |
| `low_traffic` | 요청 수가 threshold 미만 | 표본 부족, 판단 신뢰 낮음 |
| `metric_idle` | starter는 연결됐지만 accepted bucket이 없거나 오래됨 | 트래픽 없음 또는 bucket 대기 |
| `telemetry_unreachable` | heartbeat와 metric bucket 모두 오래됨 | starter/portal 연결 확인 |
| `stale_or_down` | accepted bucket freshness가 stale/down | 수집 지연 또는 끊김 |
| `test_or_synthetic` | metadata나 heuristic상 테스트성 traffic | 운영 영향 산정 제외 또는 감산 |

Data quality는 UI에서 별도 badge 또는 panel로 보여준다. `정상`, `장애`, `판단 보류`를 섞지 않는 것이 중요하다.

### 3. Signal 추출

내부 계산은 RED/USE/golden signals를 사용해도, 화면에서는 다음 언어로 번역한다.

| 내부 개념 | UI 언어 | 계산 원천 |
|---|---|---|
| Rate | 요청량 | request_count |
| Errors | 실패 | error_count / request_count |
| Duration | 느려짐 | duration histogram slow share, local p95/p99 |
| Utilization | 사용률 | CPU, heap, datasource pool ratio |
| Saturation | 자원 압박 | threshold 이상 resource ratio + latency 동시 발생 |
| Golden signals | 서비스 생존 신호 | 요청량, 실패, 느려짐, 자원 압박 |

## 문제 후보 생성 모델

첫 화면의 `Look First`는 endpoint 목록만이 아니라 운영자가 실제 확인할 후보 queue다.

### 후보 타입

| Candidate Type | 생성 원천 | 예시 |
|---|---|---|
| `application_state` | lifecycle state, triage cards | application degraded |
| `endpoint` | endpointPriority, endpoints_json | POST /orders error + latency |
| `resource` | runtime ratio + companion signal | datasource pool high with latency |
| `instance` | instance summary, instance evidence refs | 특정 instance에 evidence 집중 |
| `snapshot_event` | dashboard_snapshots helper columns | high confidence concern snapshot |
| `data_quality` | freshness, sample readiness | low traffic / initializing |

### 후보 생성 규칙

1. Application-level triage candidate
   - `triageCards`가 있으면 application-level concern을 만든다.
   - 예: `global_error_spike`, `global_latency_spike`, `db_pool_high_with_latency`

2. Endpoint candidate
   - `endpointPriority` 상위 item을 후보로 만든다.
   - 현재 구현의 reason을 유지한다.
     - `error_spike`
     - `latency_spike`
     - `error_and_latency`
     - `comparative_regression`
     - `recent_error`

3. Resource candidate
   - resource ratio 단독으로는 장애 후보가 아니라 hint다.
   - latency 또는 error signal과 같은 window에서 같이 발생하면 `resource` 후보로 승격한다.
   - 예: `datasource_pool_usage_ratio >= 0.85` + latency spike

4. Instance candidate
   - 특정 instance에 endpoint evidence ref가 몰리거나, p95/p99 tail latency가 높고 request sample이 충분하면 후보로 만든다.
   - 단, instance 단위 상태를 확정하지 않고 "확인 지점"으로 표시한다.

5. Data quality candidate
   - 데이터가 부족하면 문제 후보보다 data quality 후보가 먼저 보일 수 있다.
   - 예: `low_traffic`, `initializing`, `telemetry_unreachable`

## 우선순위 모델

우선순위는 "가장 나쁜 숫자"가 아니라 "먼저 볼 가치"다.

### 기본 정렬 원칙

```text
dataQuality gate
  -> impactScore desc
  -> confidence desc
  -> recency desc
  -> businessCriticality desc
  -> deterministic key asc
```

### Impact Score 후보 공식

정확한 수치는 구현 단계에서 조정하되, UX 모델은 다음 구성으로 둔다.

```text
impactScore =
  trafficWeight
  + failureWeight
  + latencyWeight
  + regressionWeight
  + saturationCooccurrenceWeight
  + spreadWeight
  + businessCriticalityWeight
  - dataQualityPenalty
```

각 항목의 의미:

| Component | 의미 | 원천 |
|---|---|---|
| `trafficWeight` | 영향을 받은 요청 규모 | endpoint/application request count, request share |
| `failureWeight` | 오류율과 오류 증가량 | error rate, error rate delta |
| `latencyWeight` | slow share와 지연 증가량 | duration histogram current/baseline |
| `regressionWeight` | 평소 대비 악화 정도 | current vs baseline comparison |
| `saturationCooccurrenceWeight` | 자원 압박과 증상 동시 발생 | runtime ratio + latency/error |
| `spreadWeight` | 영향이 특정 instance인지 전체 확산인지 | instance evidence, instance count |
| `businessCriticalityWeight` | 핵심 여정/중요 endpoint 여부 | endpoint/application metadata |
| `dataQualityPenalty` | 표본 부족, 테스트성 traffic, stale data | data quality state |

### Confidence 후보 공식

Confidence는 impact와 분리한다.

```text
confidence =
  evidenceCompleteness
  + baselineAvailability
  + ruleStrength
  + cooccurrenceStrength
  + sampleReadiness
  - missingEvidencePenalty
```

예시:

- 요청 수가 충분하고 current/baseline 모두 있으면 confidence 상승
- error와 latency가 동시에 rule threshold를 넘으면 confidence 상승
- datasource pool 포화가 같은 window에 있으면 원인 후보 confidence 상승
- low traffic이면 confidence 하락
- histogram boundary mismatch면 latency confidence 하락

## Top N 표시 모델

### Project/Application Rail

Rail은 목록이 아니라 triage entry다.

표시 항목:

- application name / environment
- lifecycle state badge
- data quality badge
- primary finding text
- impact score
- journey or owner metadata

정렬:

1. degraded/down/stale 등 actionable state
2. impact score 높은 application
3. confidence 높은 application
4. 최근 snapshot/event가 있는 application
5. normal/quiet/low traffic application

### First Screen

첫 화면은 다음 순서로 읽힌다.

1. `OperatorSummary`
   - 한 문장 결론
   - impact score
   - confidence
   - primary rule
   - primary suspect
2. `LookFirst`
   - endpoint, resource, instance 후보를 같은 queue로 표시
   - Top 3은 펼쳐서 보여주고, Top 5는 리스트로 유지
3. `SignalSummary`
   - 요청량, 실패, 느려짐, 자원 압박
4. `EvidenceTimeline`
   - current/baseline window 변화
   - bad bucket 흐름
   - snapshot marker
5. `DataQuality`
   - 판단 가능/보류/테스트성/수집 지연

### Problem Detail

문제 상세 화면은 후보 하나를 선택했을 때 그 후보의 판단 근거를 보여준다.

구성:

- 후보 제목
- 왜 우선순위가 높았는가
- impact breakdown
- confidence breakdown
- current vs baseline evidence
- related endpoint evidence
- related resource hints
- related instance refs
- recommended next action
- related snapshot / flight recorder link

## Endpoint Top N 기준

Endpoint Top N은 느린 순서나 error rate 순서가 아니다. `현재 악화 기여도` 순서다.

권장 순서:

1. `error_and_latency`
2. `error_spike`
3. `latency_spike`
4. `comparative_regression`
5. `recent_error`

같은 reason 안에서는 다음 기준을 적용한다.

```text
request share desc
-> error rate delta desc
-> slow share delta desc
-> confidence desc
-> business criticality desc
-> endpoint key asc
```

주의:

- traffic이 거의 없는 endpoint의 100% error를 무조건 위로 올리지 않는다.
- endpoint p95/p99 rollup은 현재 원천에서 안전하게 만들지 않는다.
- latency는 duration histogram의 slow share로 표현한다.
- endpoint별 local p95/p99가 없으면 "source 없음"으로 둔다.

## Resource Hint 표시 기준

Resource는 단독 원인으로 확정하지 않는다. 항상 companion signal과 함께 보여준다.

예시:

| Resource | 단독 표시 | 승격 조건 | UI 문구 |
|---|---|---|---|
| datasource pool | latest sample | latency spike와 동시 발생 | DB connection wait 가능성 |
| CPU | latest sample | latency spike와 동시 발생 | CPU saturation 가능성 |
| heap | latest sample | latency 또는 error와 동시 발생 | GC/memory pressure 가능성 |

UI 문구는 "원인"이 아니라 "확인 후보"로 쓴다.

좋은 문구:

- "DB connection pool 대기 가능성을 먼저 확인하세요."
- "CPU saturation 가능성을 확인하세요."

피해야 할 문구:

- "DB pool이 원인입니다."
- "CPU 때문에 장애입니다."

## Instance 표시 기준

Instance는 다음 질문에 답해야 한다.

- 특정 instance만 문제인가?
- 여러 instance에 확산됐는가?
- endpoint evidence와 연결되는 instance가 있는가?

표시 항목:

- instance name
- last seen
- freshness
- request count
- starter local p95/p99
- resource hints
- endpoint evidence refs

Instance는 Top-level 원인 후보가 될 수 있지만, MVP에서는 "먼저 확인할 instance"로 표현한다.

## Snapshot / Flight Recorder UX 모델

Snapshot은 current dashboard를 다시 계산하지 않고 저장 당시 판단 근거를 보여준다.

필수 묶음:

- snapshot metadata
  - capturedAt
  - generatedAt
  - current/baseline window
  - captureReason
  - stateCode
- primary finding
  - primaryRuleId
  - primaryEndpointKey
  - primarySuspectType
  - primarySuspectKey
  - impactScore
  - maxConfidence
- evidence bundle
  - endpoint evidence
  - instance summary
  - resource hints
  - data quality
  - missing evidence notes
- version
  - scoringVersion
  - evidenceVersion
  - metadataVersion
- review summary
  - 당시 판단 문장
  - 다음 확인 action
  - 회고용 요약

## 제안 Read Model Shape

기존 dashboard read model을 깨기보다, 운영 판단용 top-level block을 추가하는 방식이 좋다.

```json
{
  "operatorSummary": {
    "primaryFindingText": "POST /orders 지연 증가와 datasource pool 포화가 함께 발생 중입니다.",
    "recommendedFirstLook": "POST /orders 오류 로그와 DB connection pool wait를 먼저 확인하세요.",
    "primarySuspectType": "endpoint",
    "primarySuspectKey": "POST /orders",
    "impactScore": 84,
    "confidence": 0.82,
    "confidenceReason": "error_and_latency_with_datasource_pool_pressure",
    "dataQualityState": "sufficient"
  },
  "lookFirst": [
    {
      "rank": 1,
      "type": "endpoint",
      "key": "POST /orders",
      "label": "주문 생성",
      "reason": "error_and_latency",
      "impactScore": 84,
      "confidence": 0.82,
      "recommendedAction": "최근 배포, 5xx 로그, DB pool wait를 확인하세요.",
      "evidenceRefs": ["endpoint-evidence-1", "instance-summary-1"]
    }
  ],
  "signalSummary": {
    "request": {},
    "failure": {},
    "latency": {},
    "resourcePressure": {}
  },
  "dataQuality": {
    "state": "sufficient",
    "sampleBucketCount": 30,
    "missingBucketCount": 0,
    "lowTraffic": false,
    "testOrSynthetic": false
  }
}
```

## MVP 단계

### MVP 1. Presentation-only 재구성

목표:

- 기존 `triageCards`, `endpointPriority`, `metrics`, `histogramDistribution`, `sourceScopedPercentiles`를 재배열한다.
- visual redesign보다 정보 순서를 바꾼다.
- schema 변경 없음.

산출:

- Operator summary copy
- Look First Top 3
- RED/USE 번역 label
- Data quality placeholder

### MVP 2. Server-computed operator model

목표:

- 서버가 impact/confidence/data quality를 계산한다.
- UI가 점수를 재계산하지 않는다.

추가:

- `operatorSummary`
- `lookFirst`
- `impactScore`
- `confidenceBreakdown`
- `signalSummary`
- `dataQuality`

### MVP 3. Metadata 반영

목표:

- 기술 신호에 비즈니스 중요도를 반영한다.

추가:

- endpoint metadata
- business criticality
- journey name
- owner team
- runbook URL
- test/synthetic flag

### MVP 4. Flight Recorder 강화

목표:

- snapshot detail을 회고 가능한 사건 보고서로 만든다.

추가:

- scoring version
- evidence version
- data completeness
- review summary
- shareable report

## 최종 원칙

- UI는 metric explorer가 아니라 decision surface다.
- persistence는 관측 원천이고, read model은 운영 판단 언어로 번역된 결과다.
- impact와 confidence는 분리한다.
- data quality는 문제 없음과 구분한다.
- endpoint p95/p99를 억지로 만들지 않는다.
- resource signal은 원인 확정이 아니라 가설과 다음 확인 지점으로 표현한다.
- snapshot은 과거 화면이 아니라 저장된 판단 증거 번들이다.

