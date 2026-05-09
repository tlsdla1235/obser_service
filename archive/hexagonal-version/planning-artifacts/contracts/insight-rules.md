---
artifactType: contract
name: insight-rules
architectureStyle: Lightweight Hexagonal
status: party-mode-fixes-applied
date: 2026-05-08
---

# Contract - Insight Rules

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

## 3. Candidate Shape

```json
{
  "ruleId": "endpoint_latency_spike",
  "severity": "warning",
  "title": "GET /products 응답 지연 증가",
  "summary": "current window p95가 baseline보다 높습니다.",
  "recommendation": "이 endpoint의 DB query와 외부 호출을 먼저 확인해보세요.",
  "affectedEndpoint": "GET /products",
  "score": 72,
  "confidence": 0.78,
  "evidence": {
    "currentP95Ms": 620,
    "baselineP95Ms": 210,
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

노출은 최대 3개다.

## 5. MVP Rule Set

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
  - slow/error/comparative evidence ranking

## 6. Copy Rules

문구는 진단 확정이 아니라 확인 제안이어야 한다.

- 금지: "DB pool 고갈로 장애가 발생했습니다."
- 허용: "DB pool 사용률이 높고 응답 지연도 함께 증가했습니다. DB 연결 대기 가능성을 먼저 확인해보세요."

## 7. Hexagonal Boundary

- rule evaluation은 domain/application 안에 둔다.
- UI와 persistence adapter는 rule을 평가하지 않는다.
- 새로운 rule 추가는 domain test와 read model contract update를 동반한다.
