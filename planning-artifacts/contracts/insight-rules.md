---
artifactType: contract
name: insight-rules
architectureStyle: Traditional MVC
status: mvc-version-generated
date: 2026-05-09
---

# Contract - Insight Rules MVC Version

## 1. 역할

Insight rule은 원인 확정 엔진이 아니라 첫 확인 지점을 제안하는 triage engine이다.

## 2. 공통 Guard

모든 비교형 rule은 아래 guard를 먼저 통과해야 한다.

- freshness sufficient
- minimum request count
- absolute threshold
- baseline sufficiency
- baseline 대비 변화율

guard를 통과하지 못한 rule은 candidate를 만들지 않거나 confidence를 낮춘다.

Route attribution이 `UNKNOWN`인 endpoint-level rule은 actionability를 낮게 보거나 candidate 생성을 제한할 수 있다. `route-attribution-policy.md`를 통과한 normalized route는 endpoint evidence로 사용할 수 있으나, raw path/query detail은 evidence에 포함하지 않는다.

## 3. Candidate Shape

```json
{
  "ruleId": "endpoint_latency_spike",
  "severity": "warning",
  "title": "GET /products 응답 지연 증가",
  "summary": "current window의 duration bucket 분포가 baseline보다 느린 구간에 몰렸습니다.",
  "recommendation": "이 endpoint의 bucket 분포와 DB query, 외부 호출을 먼저 확인해보세요.",
  "affectedEndpoint": "GET /products",
  "score": 72,
  "confidence": 0.78,
  "evidence": {
    "durationBuckets": [
      { "leMs": 250, "count": 900 },
      { "leMs": 500, "count": 1500 },
      { "leMs": 1000, "count": 1800 }
    ],
    "baselineDurationBuckets": [
      { "leMs": 250, "count": 1500 },
      { "leMs": 500, "count": 1750 },
      { "leMs": 1000, "count": 1800 }
    ],
    "requestCount": 1800
  }
}
```

## 4. Ranking

후보 정렬은 아래 순서를 따른다.

1. severity
2. score
3. confidence
4. actionability

Dashboard triage card 기본 노출 기준은 confidence `>= 0.65`다. 노출은 최대 3개다. 이 기준은 operational history event 승격 기준과 다르며, dashboard에 보이는 warning candidate가 곧 history event가 된다는 뜻이 아니다.

## 5. Operational Event Promotion

Operational event로 승격되는 concern은 high-confidence candidate로 제한한다.

Standalone operational history `high_confidence_concern` 승격 기준은 confidence `>= 0.82`다. low-confidence candidate, minimum sample guard를 통과하지 못한 candidate, `60분` suppression window 안에서 반복된 같은 `application + endpointKey + ruleId` concern은 history event로 만들지 않는다. Event 승격은 `operational-event-history.md` contract에 따라 service layer에서 수행한다.

Rule이 p95/p99를 참조해야 한다면 source는 starter canonical percentile이어야 한다. 같은 scope에 starter-reported p95/p99와 histogram-derived p95/p99를 함께 두지 않는다.

Endpoint-level rule은 endpoint별 p95/p99를 계산하지 않는다. Endpoint latency rule은 duration bucket distribution, request count, error rate, freshness 같은 bounded evidence를 사용하며 endpoint percentile rollup, endpoint percentile judgment, endpoint p99 alert 기준을 만들지 않는다.

짧지만 강한 spike 실험값은 confidence `>= 0.90`이고 최근 5개 30초 bucket 중 2개 이상 bad이면 state 변화가 없어도 capture 후보가 될 수 있다. 이 실험값에도 minimum sample guard와 suppression window는 항상 적용한다.

Spike 구간 색인은 추가 raw metric 영속화가 아니라 저장된 dashboard read model의 rule result/evidence를 기반으로 한다. 필요한 bounded index column을 `dashboard_snapshots`에 추가할 수는 있지만, endpoint timeseries table이나 `operational_events` table을 만들지는 않는다.

## 6. MVP Rule Set

- availability
  - `service_down`
  - `service_stale`
  - `service_idle`
- error
  - `global_error_spike`
  - `endpoint_error_spike`
- latency
  - `global_latency_spike`
  - `endpoint_latency_spike`
- saturation hint
  - `db_pool_high_with_latency`
  - `cpu_high_with_latency`
  - `heap_high_hint`
- endpoint priority
  - bucket distribution/error/comparative evidence ranking

## 7. Post-MVP Runtime Aggregate Evidence

MVP saturation hint는 JVM/datasource runtime ratio의 latest sample과 HTTP latency/error evidence를 함께 사용한다. Post-MVP runtime aggregate가 도입되면 rule evidence는 아래 의미를 구분해야 한다.

- `latest`: 사용자가 지금 확인할 현재 상태에 가까운 값이다.
- `max`: bucket 안 순간 피크다. 짧은 DB pool 포화, CPU spike, heap pressure를 놓치지 않는 보조 evidence로 사용한다.
- `avg`: bucket 안 지속 압력이다. 단발성 spike인지 window 전체 압박인지 confidence 계산에 사용한다.

`max` 하나만으로 degraded/down을 단정하지 않는다. Saturation hint는 starter canonical percentile, bucket distribution, error, freshness, minimum sample guard와 함께 있을 때만 high-confidence concern으로 승격할 수 있다. `avg` 병합은 instance별 sample count 기반 weighted average를 사용하며, average-of-averages로 계산하지 않는다.

## 8. Copy Rules

문구는 진단 확정이 아니라 확인 제안이어야 한다.

- 금지: "DB pool 고갈로 장애가 발생했습니다."
- 허용: "DB pool 사용률이 높고 응답 지연도 함께 증가했습니다. DB 연결 대기 가능성을 먼저 확인해보세요."

## 9. MVC Boundary

- rule evaluation은 `TriageSummaryService`와 `EndpointPriorityService` 안에 둔다.
- UI, controller, repository는 rule을 평가하지 않는다.
- 새로운 rule 추가는 service test와 read model contract update를 동반한다.
- Operational event history는 rule을 다시 평가하지 않고 저장된 read model/rule result를 bounded event 후보로 요약한다.
