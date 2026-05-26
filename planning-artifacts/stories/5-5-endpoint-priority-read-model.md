---
artifactType: story
storyId: "5.5"
storyKey: "5-5-endpoint-priority-read-model"
epic: "Epic 5. Dashboard Read Model and API"
title: "Endpoint Priority Read Model"
architectureStyle: Traditional MVC
status: done
date: 2026-05-26
---

# Story 5.5 - Endpoint Priority Read Model

## User Story

portal 구현자로서, Application Dashboard read model의 `endpointPriority`가 server-computed typed list로 채워지길 원한다.

그래야 UI가 어떤 endpoint를 먼저 확인할지 표시하면서도, endpoint ranking, rule, confidence, recommended action, p95/p99 판단을 UI/controller/repository에서 다시 계산하지 않는다.

## Source of Truth

`implementation-artifacts/spec-story-5-5-endpoint-priority-contract-decisions.md`가 Story 5.5의 최상위 결정 문서다.

이 story는 해당 문서의 8개 계약을 닫힌 계약으로 취급한다. 구현자는 아래 결정을 재논의하지 않는다.

1. Ranking reason and ordering
2. Freshness suppression and snapshot handoff
3. Endpoint threshold and confidence seed
4. Endpoint evidence source and merge
5. `UNKNOWN` route actionability
6. Endpoint priority item shape
7. Triage affected endpoint relationship
8. Snapshot and history handoff

## Scope / Out of Scope

포함:

- `GET /api/projects/{projectId}/applications/{applicationId}/dashboard` read model의 `endpointPriority` 확장
- `ApplicationDashboardReadModel.endpointPriority`를 `List<Object>` placeholder에서 typed list로 변경
- `EndpointPriorityItem` typed record와 bounded evidence/freshness/reason/action shape 추가
- accepted bucket `endpoints_json`을 current 15분 window와 baseline 15분 window에서 조회하는 bounded repository projection 추가
- window membership을 bucket `bucketEndUtc` 기준 `(start, end]`로 적용
- endpoint group key를 `method + route`로 두고 API `endpointKey`를 `method + " " + route`로 생성
- current freshness에서만 endpoint ranking 계산
- `stale`, `down`, `unknown`, insufficient sample에서는 current response의 `endpointPriority=[]` 유지
- `UNKNOWN` route 또는 `<method> UNKNOWN` endpoint를 endpoint priority 후보에서 제외
- endpoint error/latency/comparative rule, confidence, score, reason, `ruleIds`, recommended action 계산
- 최대 5개 endpoint priority item 반환
- Story 5.8/5.9 snapshot/history handoff에 저장 가능한 bounded item/evidence shape 유지
- focused unit/service/controller/repository tests와 MVC boundary regression

제외:

- stale/down 직전 endpoint evidence를 current `endpointPriority`에 재사용하는 것
- stale/down 직전 endpoint evidence snapshot/history 노출 구현. 이는 Story 5.8/5.9 handoff다.
- snapshot persistence, `dashboard_snapshots` migration, snapshot lookup runtime dependency
- operational event promotion, operational event history API, endpoint timeseries table
- route attribution diagnostic surface, route attribution 설정 UI/API
- raw endpoint explorer, raw accepted bucket JSON, raw `endpoints_json` string 노출
- raw path, query string, query key/value, trace id, per-request sample 노출
- endpoint p95/p99, endpoint percentile rollup, endpoint percentile judgment, endpoint p99 alert 기준
- histogram bucket에서 percentile scalar, avg/max latency scalar, regression scalar 생성
- UI/static asset 변경, starter ingest/heartbeat endpoint 동작 변경
- `application`, `port`, `adapter` package 추가

## Acceptance Criteria

1. Dashboard `200` response는 Story 5.4 top-level fields를 유지하고, `endpointPriority`를 typed list로 반환한다.
2. `ApplicationDashboardReadModel.endpointPriority`는 `List<EndpointPriorityItem>` 또는 동등 typed record list이며 `List<Object>` placeholder가 아니다.
3. `EndpointPriorityItem`은 `rank`, `method`, `route`, `endpointKey`, `reason`, `ruleIds`, `confidence`, `score`, `freshness`, `evidence`, `recommendedAction`을 포함한다.
4. `rank`는 1부터 시작하는 응답 내 순서이며, 반환 item 수는 최대 5개다.
5. `method`, `route`, `endpointKey`는 blank가 아니어야 하며 `endpointKey`는 `method + " " + route` 형식이다.
6. `reason`은 `error_spike`, `latency_spike`, `error_and_latency`, `comparative_regression` 중 하나로 제한한다.
7. `ruleIds`는 item과 관련된 endpoint rule id 목록이며 raw path/query/detail을 포함하지 않는다.
8. `confidence`는 `0.0 <= confidence <= 1.0`, `score`는 bounded numeric value로 검증한다.
9. `freshness.status`는 필수이며 Story 5.5 current ranking item에서는 `current`로 둔다.
10. `freshness.lastObservedAt`은 필수이며 endpoint evidence를 만든 current window의 최신 observed bucket timestamp를 사용한다.
11. `freshness.sourceWindow`는 필수이며 Story 5.5 current ranking item에서는 `current`로 둔다.
12. `freshness.reason`은 nullable이다.
13. `evidence`는 항상 bounded object이며 `requestCount`, `errorCount`, `errorRate`, `bucketDistributionSource`, `errorEvidenceStatus`, `latencyEvidenceStatus`를 포함한다.
14. `evidence`는 status에 따라 `baselineRequestCount`, `baselineErrorCount`, `baselineErrorRate`, `errorRateDelta`, `durationBuckets`, `baselineDurationBuckets`, `slowShare`, `baselineSlowShare`, `slowShareDelta`를 nullable로 포함할 수 있다.
15. `bucketDistributionSource`는 `histogram_bucket_distribution`이다.
16. evidence에는 raw JSON string, unbounded map, raw path, query string, query key/value, trace id, per-request sample을 포함하지 않는다.
17. evidence에는 endpoint p95/p99, endpoint percentile rollup, endpoint p99 alert 기준, histogram-derived percentile scalar를 포함하지 않는다.
18. `recommendedAction`은 server-computed copy이며 root cause 확정 표현을 사용하지 않는다.
19. application metric freshness가 `current`일 때만 endpoint ranking을 계산한다.
20. application metric freshness가 `stale`, `down`, `unknown` 계열이면 response의 current `endpointPriority=[]`다.
21. current sample 또는 endpoint evidence가 threshold 판단에 부족하면 response의 current `endpointPriority=[]`다.
22. stale/down 직전 endpoint evidence는 Story 5.5 current ranking에 복사하지 않고 Story 5.8/5.9 snapshot/history handoff로 남긴다.
23. Story 5.5 구현은 snapshot repository, snapshot persistence, history marker API를 runtime dependency로 만들지 않는다.
24. repository endpoint evidence 조회는 `accepted_metric_buckets.endpoints_json`을 source로 사용한다.
25. repository는 current 15분 window와 baseline 15분 window를 각각 조회할 수 있는 bounded projection만 제공한다.
26. endpoint evidence window membership은 bucket `bucketEndUtc > windowStart` 그리고 `bucketEndUtc <= windowEnd` 조건을 따른다.
27. repository projection은 application id, bucket boundary, endpoint JSON 또는 parsed endpoint row에 필요한 bounded source field만 전달하고 endpoint rule, confidence, rank, recommended action을 계산하지 않는다.
28. service layer가 endpoint JSON parsing, merge, status, rule, confidence, score, ranking, recommended action, read model 변환을 담당한다.
29. 같은 `endpointKey` 안에서 current `requestCount`와 `errorCount`를 합산한다.
30. 같은 `endpointKey` 안에서 baseline `requestCount`와 `errorCount`를 합산한다.
31. `durationBuckets`는 같은 `leMs` boundary set일 때만 cumulative count를 boundary별로 합산한다.
32. current 또는 baseline 한쪽 window에서 endpoint 내부 histogram boundary mismatch가 있으면 해당 window의 latency evidence는 `unavailable`로 둔다.
33. latency evidence가 `unavailable`이어도 request/error count가 유효하면 error evidence는 계속 사용할 수 있다.
34. current endpoint row가 없으면 해당 endpoint evidence status는 `missing`으로 취급하고 priority 후보로 만들지 않는다.
35. baseline endpoint row가 없으면 error/latency comparison status는 `insufficient_baseline`으로 취급한다.
36. endpoint bucket JSON이 invalid하면 해당 endpoint evidence status는 `insufficient`로 취급하고 raw JSON을 노출하지 않는다.
37. `route = UNKNOWN` 또는 `endpointKey = <method> UNKNOWN`인 endpoint는 current `endpointPriority` 후보에서 제외한다.
38. 모든 endpoint가 `UNKNOWN`이면 error/latency spike가 명확해도 `endpointPriority=[]`다.
39. `UNKNOWN` 보완을 위해 raw path, query string, query key/value, trace id, per-request sample을 노출하지 않는다.
40. endpoint common guard는 application metric freshness `current`, current endpoint `requestCount >= 30`, baseline endpoint `requestCount >= 30`이다.
41. endpoint histogram latency 판단은 current/baseline bucket boundary set이 일치할 때만 수행한다.
42. `error_spike`는 current endpoint error rate `>= 0.05`, error rate absolute delta `>= 0.03`, current endpoint error rate `>= baseline endpoint error rate * 2`를 모두 만족해야 한다.
43. `latency_spike`는 endpoint duration bucket distribution에서 `> 500ms` slow share를 사용한다.
44. `latency_spike`는 current endpoint slow share `>= 0.20`, slow share delta `>= 0.10`, latency evidence available 조건을 만족해야 한다.
45. `error_and_latency`는 같은 endpoint가 `error_spike`와 `latency_spike` 조건을 모두 만족할 때 사용한다.
46. `comparative_regression`은 current/baseline endpoint `requestCount >= 100`, error rate delta `>= 0.02` 또는 slow share delta `>= 0.08` 조건에서만 낮은 confidence 후보로 만든다.
47. `comparative_regression` confidence는 최대 `0.64`로 제한한다.
48. ranking reason priority는 `error_and_latency`, `error_spike`, `latency_spike`, `comparative_regression` 순서다.
49. 같은 reason priority 안에서는 `confidence desc`, `score desc`, `requestCount desc`, `endpointKey asc` 순서로 deterministic tie-breaker를 적용한다.
50. `triageCards[].affectedEndpoint`는 계속 optional hint이며 endpoint rank source가 아니다.
51. UI가 endpoint 순위를 표시할 때 canonical source는 `endpointPriority`다.
52. `triageCards[].affectedEndpoint`와 `endpointPriority[0].endpointKey`가 달라도 response는 valid다.
53. `EndpointPriorityItem.ruleIds`는 triage/snapshot/history 추적성을 위한 field이며 triage card와 1:1 동기화를 강제하지 않는다.
54. `DashboardReadModelService`는 기존 current/baseline window, source-scoped percentile, histogram distribution, triage/zeroInsight/recovery orchestration을 보존한다.
55. `TriageSummaryService`는 app-level triage card 계산 책임을 유지하며 endpoint priority ranking source로 승격되지 않는다.
56. UI/controller/repository는 endpoint priority, rule, confidence, recommended action을 계산하지 않는다.
57. Controller는 path/status mapping과 service 위임만 담당하고 repository, JSON parsing, endpoint merge, rule/ranking 계산을 직접 수행하지 않는다.
58. JPA entity는 API response, controller response DTO, service external return model로 직접 노출하지 않는다.
59. 새 공개 클래스, 공개 메서드, 핵심 helper는 AGENTS.md 지침에 따라 한국어 Javadoc/docstring으로 역할과 사용 맥락을 설명한다.
60. Focused tests가 model shape, service ranking/threshold/freshness suppression, `UNKNOWN` 제외, endpoint evidence merge, controller serialization, MVC boundary regression을 검증한다.
61. `./gradlew :observability-portal:test`와 `git diff --check`가 통과한다.

## Tasks/Subtasks

- [x] Typed endpoint priority read model shape 구현 (AC: 1~18, 50~53, 58~59)
  - [x] `ApplicationDashboardReadModel.endpointPriority`를 `List<EndpointPriorityItem>`로 변경한다.
  - [x] `EndpointPriorityItem`, `EndpointPriorityReason` 또는 동등 enum, `EndpointPriorityFreshness`, `EndpointPriorityEvidence` typed record를 추가한다.
  - [x] `reason` enum은 `error_spike`, `latency_spike`, `error_and_latency`, `comparative_regression`만 JSON으로 노출한다.
  - [x] item constructor에서 `rank`, `method`, `route`, `endpointKey`, `confidence`, `score`, `ruleIds`, `freshness`, `evidence`, `recommendedAction`을 검증한다.
  - [x] evidence constructor에서 bounded field만 허용하고 bucket list를 defensive copy한다.
  - [x] Story 5.4의 `triageCards`, nullable `zeroInsight`, `histogramDistribution`, `sourceScopedPercentiles` shape를 보존한다.

- [x] Endpoint evidence repository projection 추가 (AC: 24~28, 56~59)
  - [x] `MetricBucketRepository`에 application/window scope endpoint evidence 조회 method를 추가한다.
  - [x] 조회 조건은 `application_id`, `bucket_end_utc > windowStart`, `bucket_end_utc <= windowEnd`, `endpoints_json is not null`이다.
  - [x] current window와 baseline window에서 같은 method를 재사용할 수 있게 한다.
  - [x] projection은 bucket boundary와 `endpoints_json` 또는 endpoint row parsing에 필요한 bounded source만 반환한다.
  - [x] repository/JPA method Javadoc에 rule, confidence, rank, recommended action, endpoint p95/p99를 계산하지 않는다고 명시한다.
  - [x] schema migration, DB view, materialized view, endpoint timeseries table은 추가하지 않는다.

- [x] Endpoint evidence merge helper/service 구현 (AC: 24~39, 41, 56~59)
  - [x] `EndpointPriorityService` 또는 `domain.dashboard.service` package의 동등 service/helper를 추가한다.
  - [x] accepted bucket `endpoints_json`을 lenient하게 parse하되 invalid JSON은 `insufficient` status로 수렴하고 raw JSON을 response에 노출하지 않는다.
  - [x] endpoint group key는 `method + route`로 두고 API `endpointKey`는 `method + " " + route`로 만든다.
  - [x] current/baseline window별로 `requestCount`, `errorCount`를 endpointKey 단위 합산한다.
  - [x] `durationBuckets`는 endpointKey/window 내부 boundary set이 모두 같을 때만 cumulative count를 boundary별 합산한다.
  - [x] boundary mismatch window는 latency evidence를 `unavailable`로 두되 error evidence는 유지한다.
  - [x] baseline endpoint가 없으면 comparison rule은 `insufficient_baseline`으로 처리한다.
  - [x] `route=UNKNOWN`과 `<method> UNKNOWN`을 후보에서 제외한다.
  - [x] 모든 candidate가 제외되거나 insufficient이면 빈 list를 반환한다.

- [x] Endpoint priority rule/ranking 계산 구현 (AC: 19~23, 40~49, 56~59)
  - [x] application freshness가 `current`가 아니면 repository evidence가 있어도 `endpointPriority=[]`를 반환한다.
  - [x] current/baseline endpoint request count `>= 30` common guard를 적용한다.
  - [x] `error_spike` threshold를 current error rate `>= 0.05`, delta `>= 0.03`, relative `>= 2x`로 구현한다.
  - [x] `latency_spike` threshold를 `> 500ms` slow share, current `>= 0.20`, delta `>= 0.10`으로 구현한다.
  - [x] 같은 endpoint가 error/latency threshold를 모두 통과하면 reason을 `error_and_latency`로 정한다.
  - [x] `comparative_regression`은 request count `>= 100`, error delta `>= 0.02` 또는 slow share delta `>= 0.08`에서만 만들고 confidence를 `0.64` 이하로 cap한다.
  - [x] reason priority와 tie-breaker 순서대로 deterministic sort를 적용한다.
  - [x] 정렬 후 rank를 1부터 부여하고 최대 5개만 반환한다.
  - [x] recommended action copy는 reason별 next-check 제안으로 작성하고 원인 확정 표현을 피한다.
  - [x] stale/down 직전 endpoint evidence snapshot/history 노출은 Story 5.8/5.9 follow-up으로 남긴다.

- [x] Dashboard orchestration 연결 (AC: 19~23, 50~57)
  - [x] `DashboardReadModelService.buildDashboard()`의 기존 Story 5.4 흐름을 보존한다.
  - [x] current/baseline window 계산은 query 시점 `evaluationAt` 기준을 유지하고 latest bucket end로 바꾸지 않는다.
  - [x] current/baseline endpoint evidence projection을 조회해 endpoint priority service에 전달한다.
  - [x] endpoint priority service에는 application freshness status, current/baseline endpoint rows, latest observed timestamp, window metadata를 bounded input으로 전달한다.
  - [x] `triageCards[].affectedEndpoint`는 endpoint ranking source로 사용하지 않는다.
  - [x] state, zeroInsight, recovery, triage card 계산을 endpoint priority 결과로 다시 계산하지 않는다.
  - [x] snapshot persistence나 history marker dependency를 추가하지 않는다.

- [x] Controller/API serialization 유지 (AC: 1~18, 56~58)
  - [x] `DashboardController` route와 404 mapping을 유지한다.
  - [x] controller test는 service stub 결과의 typed `endpointPriority` serialization과 status mapping만 검증한다.
  - [x] controller에서 repository, ObjectMapper endpoint parsing, rule/ranking 계산을 호출하지 않는다.

- [x] Focused tests와 regression 실행 (AC: 60~61)
  - [x] model shape test에서 `endpointPriority`가 typed list이고 필수 field/nullability/enum/score-confidence validation을 검증한다.
  - [x] `EndpointPriorityServiceTest` 또는 동등 focused test에서 ranking priority, tie-breaker, max 5 cap을 검증한다.
  - [x] service threshold test에서 `error_spike`, `latency_spike`, `error_and_latency`, `comparative_regression` seed threshold와 confidence cap을 검증한다.
  - [x] freshness suppression test에서 `current`가 아니면 endpoint evidence가 있어도 `endpointPriority=[]`임을 검증한다.
  - [x] insufficient/current-baseline request count guard test에서 부족한 sample은 current `endpointPriority=[]` 또는 해당 candidate 제외로 수렴함을 검증한다.
  - [x] `UNKNOWN` route 제외 test에서 모든 endpoint가 `UNKNOWN`이면 빈 list이고 raw path/query fallback이 없음을 검증한다.
  - [x] endpoint evidence merge test에서 current/baseline `(start, end]` window, request/error count 합산, boundary set 일치 merge, boundary mismatch latency unavailable, error evidence 유지, invalid JSON insufficient를 검증한다.
  - [x] `DashboardReadModelServiceTest`에 endpoint priority orchestration, stale/down suppression, triage affected endpoint와 priority 불일치 허용 scenario를 추가한다.
  - [x] `DashboardControllerTest`에 typed endpoint priority serialization, `ruleIds`, freshness/evidence/recommendedAction field, no raw JSON/no endpoint p95/p99 assertion을 추가한다.
  - [x] repository projection이 추가되면 `MetricBucketRepositoryIntegrationTest` 또는 전용 integration test에서 endpoint JSON read projection과 `(start, end]` scope만 검증한다.
  - [x] `MvcLayerBoundaryTest`가 UI/controller/repository 계산 금지와 no `application`/`port`/`adapter` package guard를 계속 통과하는지 확인한다.
  - [x] `./gradlew :observability-portal:test --tests '*EndpointPriority*'`를 실행한다.
  - [x] `./gradlew :observability-portal:test --tests '*DashboardReadModel*'`를 실행한다.
  - [x] `./gradlew :observability-portal:test --tests '*DashboardController*'`를 실행한다.
  - [x] `./gradlew :observability-portal:test --tests '*MetricBucketRepository*'`를 실행한다.
  - [x] `./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest`를 실행한다.
  - [x] `./gradlew :observability-portal:test`를 실행한다.
  - [x] `git diff --check`를 실행한다.

## Dev Notes

### Contract Priority

- 최우선 source는 `implementation-artifacts/spec-story-5-5-endpoint-priority-contract-decisions.md`다.
- 이 story와 기존 `read-model-contract.md`, `insight-rules.md`, `histogram-merge.md`, `ingest-envelope.md`, `route-attribution-policy.md`, `state-semantics.md`, `time-buckets.md` 예시가 충돌하면 Story 5.5 결정 문서를 우선한다.
- 닫힌 8개 계약을 재논의하지 않는다. 구현 중 충돌처럼 보이는 지점은 source 문서를 기준으로 story scope 안에서 가장 보수적인 변경으로 해소한다.

### Suggested Response Shape

```json
{
  "endpointPriority": [
    {
      "rank": 1,
      "method": "POST",
      "route": "/orders",
      "endpointKey": "POST /orders",
      "reason": "error_and_latency",
      "ruleIds": ["endpoint_error_spike", "endpoint_latency_spike"],
      "confidence": 0.84,
      "score": 84,
      "freshness": {
        "status": "current",
        "lastObservedAt": "2026-05-26T01:10:30Z",
        "sourceWindow": "current",
        "reason": null
      },
      "evidence": {
        "requestCount": 1200,
        "errorCount": 96,
        "errorRate": 0.08,
        "baselineRequestCount": 1300,
        "baselineErrorCount": 13,
        "baselineErrorRate": 0.01,
        "errorRateDelta": 0.07,
        "durationBuckets": [
          { "leMs": 250, "count": 320 },
          { "leMs": 500, "count": 820 },
          { "leMs": 1000, "count": 1200 }
        ],
        "baselineDurationBuckets": [
          { "leMs": 250, "count": 900 },
          { "leMs": 500, "count": 1210 },
          { "leMs": 1000, "count": 1300 }
        ],
        "slowShare": 0.316667,
        "baselineSlowShare": 0.069231,
        "slowShareDelta": 0.247436,
        "bucketDistributionSource": "histogram_bucket_distribution",
        "errorEvidenceStatus": "available",
        "latencyEvidenceStatus": "available"
      },
      "recommendedAction": "이 endpoint의 오류 로그와 외부 의존성 지연 가능성을 먼저 확인해보세요."
    }
  ]
}
```

Notes:

- `endpointPriority=[]`는 stale/down 또는 endpoint 문제 없음 확정이 아니다. current response에서 지금 확인할 endpoint ranking을 만들 수 없다는 뜻이다.
- `comparative_regression`은 낮은 confidence 후보이며 high-confidence warning이나 degraded source처럼 표현하지 않는다.
- endpoint priority는 root cause 확정 순위가 아니라 next-check surface다.

### Current Code State

- `ApplicationDashboardReadModel`은 Story 5.4 기준으로 `triageCards` typed list와 `endpointPriority` `List<Object>` placeholder를 갖고 있다.
- `ApplicationDashboardReadModel` compact constructor는 top-level field와 `triageCards`를 검증하고, `endpointPriority`는 non-null list로만 copy한다. Story 5.5는 이 field를 typed list와 typed item validation으로 바꿔야 한다.
- `ApplicationDashboardReadModel`에는 이미 `HistogramBucket`, `HistogramDistribution`, `HistogramWindow`, `TriageCard`, `TriageEvidence` typed record가 있다. endpoint evidence bucket list는 기존 `HistogramBucket`을 재사용하거나 endpoint 전용 bounded bucket record를 만들 수 있다.
- `DashboardReadModelService.buildDashboard()`는 current/baseline window, latest bucket freshness, current/baseline aggregate, percentile rows, histogram rows, accepted bucket gap, recent buckets, runtime ratio, heartbeat를 조회한다.
- `DashboardReadModelService.buildDashboard()`는 `TriageSummaryService` 결과와 `LifecycleStateService` 결과를 조립하고 마지막에 `endpointPriority`로 `List.of()`를 넣는다. Story 5.5는 이 지점에 endpoint priority service 결과를 연결한다.
- `DashboardReadModelService`의 current/baseline window end는 `clock.instant()`를 UTC 30초 boundary로 floor한 `evaluationAt`이다. latest accepted bucket `endUtc`로 바꾸지 않는다.
- `TriageSummaryService`는 app-level `global_error_spike`, `global_latency_spike`, saturation hint, degraded input을 계산한다. endpoint ranking source가 아니며 Story 5.5에서도 책임을 섞지 않는다.
- `MetricBucketRepository`는 request/error aggregate, source-scoped percentile rows, summary histogram rows, accepted bucket gap, recent five bucket evidence, runtime ratio latest sample projection을 제공한다.
- `MetricBucketRepository`와 `AcceptedMetricBucketJpaRepository`의 existing read path는 `(start, end]` bucket `bucketEndUtc` semantics와 neutral projection 패턴을 따른다. endpoint evidence projection도 이 패턴을 따른다.
- `AcceptedMetricBucketEntity`는 `endpointsJson`을 JSONB string field로 매핑하고 insert path에서 validated endpoint payload를 저장한다. entity를 API response로 직접 반환하지 않는다.
- 현재 test에는 `DashboardReadModelServiceTest`와 `DashboardControllerTest`가 `endpointPriority` empty placeholder를 검증하는 지점이 있다. Story 5.5는 이 fixture를 typed list와 suppression/positive scenario로 갱신해야 한다.
- `MvcLayerBoundaryTest`는 `EndpointPriority` 같은 계산 class가 service/model package 밖으로 나가지 않도록 이름 기반 guard를 이미 포함한다.

### Endpoint Rule Seed Thresholds

| 항목 | 값 |
|---|---|
| Current endpoint minimum request count | current 15m endpoint `>= 30` |
| Baseline endpoint sufficiency | baseline 15m endpoint `>= 30` |
| Error spike current error rate | `>= 0.05` |
| Error rate absolute delta | `>= 0.03` |
| Error rate relative delta | current `>= baseline * 2` |
| Latency slow bucket | duration bucket `> 500ms` |
| Current slow share | `>= 0.20` |
| Slow share delta | `>= 0.10` |
| Comparative current/baseline request count | each `>= 100` |
| Comparative error delta | `>= 0.02` |
| Comparative slow share delta | `>= 0.08` |
| Comparative confidence cap | `<= 0.64` |
| Max returned item count | `5` |

### Ranking Contract

Reason priority:

1. `error_and_latency`
2. `error_spike`
3. `latency_spike`
4. `comparative_regression`

Tie-breaker:

1. `confidence` descending
2. `score` descending
3. `requestCount` descending
4. `endpointKey` ascending

### Evidence Boundary

- Endpoint evidence source는 `accepted_metric_buckets.endpoints_json`이다.
- `method + route`가 endpoint grouping key다.
- `endpointKey`는 API 표시용 `method + " " + route`다.
- request/error evidence와 latency evidence status를 분리한다.
- latency evidence가 `unavailable`이어도 error evidence는 사용할 수 있다.
- duration bucket은 distribution display/evidence source다. percentile scalar로 변환하지 않는다.
- raw path/query/trace/per-request sample과 raw JSON string은 response evidence에 넣지 않는다.

### Freshness and Snapshot Handoff Boundary

- current endpoint priority는 application metric freshness가 `current`일 때만 계산한다.
- `stale`, `down`, `unknown`, insufficient sample에서는 current `endpointPriority=[]`다.
- stale/down 직전 spike나 high-confidence endpoint concern은 current priority가 아니라 Story 5.8/5.9의 snapshot/history surface에서 "last recorded endpoint evidence" 또는 snapshot detail/history marker 후보로 다룬다.
- Story 5.5는 snapshot persistence, snapshot lookup, history marker, operational event promotion, endpoint timeseries table을 만들지 않는다.

### Triage Relationship

- `triageCards[].affectedEndpoint`는 optional hint이며 endpoint ranking source가 아니다.
- endpoint 순위의 canonical source는 `endpointPriority`다.
- `triageCards[].affectedEndpoint`와 `endpointPriority[0].endpointKey`가 다를 수 있다.
- `EndpointPriorityItem.ruleIds`는 추적성 field이며 triage card와 강한 1:1 동기화를 만들지 않는다.

## Existing Code / Documents To Reuse

- `_bmad/custom/project-context.md`
  - Traditional MVC + Service/Repository Layering, feature-first MVC, Spring Data JPA/Flyway 기준을 따른다.
- `implementation-artifacts/spec-story-5-5-endpoint-priority-contract-decisions.md`
  - Story 5.5 source of truth다.
- `planning-artifacts/epics.md`
  - Epic 5는 server-computed dashboard read model/API epic이며 UI 재계산 금지를 고정한다.
- `planning-artifacts/contracts/read-model-contract.md`
  - first-screen UI source-of-truth, current response와 snapshot storage boundary, endpoint evidence bounded field 후보를 따른다.
- `planning-artifacts/contracts/insight-rules.md`
  - endpoint-level rule은 p95/p99가 아니라 duration bucket distribution, request count, error rate, freshness evidence를 사용한다.
- `planning-artifacts/contracts/histogram-merge.md`
  - endpoint bucket display는 p95/p99 계산 없이 bucket distribution만 사용한다.
- `planning-artifacts/contracts/ingest-envelope.md`
  - `endpoints` payload shape, route normalization boundary, raw path/query 금지, endpoint duration bucket 의미를 따른다.
- `planning-artifacts/contracts/route-attribution-policy.md`
  - 안전한 route를 결정하지 못하면 raw path 대신 `UNKNOWN`을 사용하고, Story 5.5는 `UNKNOWN`을 priority에서 제외한다.
- `planning-artifacts/contracts/state-semantics.md`
  - freshness 부족 시 일반 비교형 rule은 억제하며 heartbeat와 accepted bucket axis를 섞지 않는다.
- `planning-artifacts/contracts/time-buckets.md`
  - current 15분, baseline 15분, UTC, `(start, end]` read window, stale 90초, down 180초 기준을 따른다.
- `planning-artifacts/api-surface.md`
  - dashboard API path, applicationId path scope, controller boundary와 status mapping을 유지한다.
- `planning-artifacts/stories/5-3-source-scoped-percentile-and-histogram-distribution-read-model.md`
  - source-scoped percentile, histogram distribution, no percentile recomputation, repository neutral projection 패턴을 재사용한다.
- `planning-artifacts/stories/5-4-triage-summary-and-zero-insight-recovery-mapping.md`
  - typed triage shape, zeroInsight/recovery precedence, service/repository/controller boundary, endpoint priority handoff를 재사용한다.
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/model/ApplicationDashboardReadModel.java`
  - endpoint priority typed list와 item/evidence/freshness shape 확장 대상이다.
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/DashboardReadModelService.java`
  - endpoint priority orchestration 연결 지점이다.
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/TriageSummaryService.java`
  - app-level triage service로 유지하고 endpoint ranking 책임을 넘기지 않는다.
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java`
  - endpoint evidence neutral projection facade 추가 후보이다.
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/AcceptedMetricBucketJpaRepository.java`
  - endpoint evidence JPQL/native projection 추가 후보이다.
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/entity/AcceptedMetricBucketEntity.java`
  - `endpointsJson` JSONB persistence field를 재사용한다.
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/service/DashboardReadModelServiceTest.java`
  - dashboard orchestration, freshness suppression, typed endpoint priority scenario를 추가한다.
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/controller/DashboardControllerTest.java`
  - typed endpoint priority serialization fixture를 갱신한다.
- `observability-portal/src/test/java/com/observation/portal/domain/bucket/repository/MetricBucketRepositoryIntegrationTest.java`
  - PostgreSQL JSONB/Flyway/Testcontainers repository projection test 패턴을 재사용한다.
- `observability-portal/src/test/java/com/observation/portal/architecture/MvcLayerBoundaryTest.java`
  - controller/repository/dto dependency guard와 no hexagonal package guard를 유지한다.

## Previous Story Intelligence

- Story 5.3은 `sourceScopedPercentiles`와 `histogramDistribution`을 evidence-only로 추가했다. endpoint priority도 histogram bucket을 percentile로 변환하지 않는다.
- Story 5.3은 repository가 raw-ish bounded projection만 반환하고 service/model helper가 status/reason/merge를 계산하는 패턴을 확정했다.
- Story 5.4는 `triageCards`를 typed list로 만들고 `endpointPriority=[]` placeholder를 유지했다. Story 5.5는 endpoint priority typed list를 처음 채운다.
- Story 5.4는 `TriageSummaryService`가 card exposure와 degraded input을 분리하는 패턴을 만들었다. Story 5.5도 endpoint priority 노출과 lifecycle state/triage card를 섞지 않는다.
- Story 5.4는 stale/down 이후 recovery source를 accepted bucket gap fallback으로 두고 Story 5.8 snapshot handoff를 남겼다. Story 5.5도 stale/down 직전 endpoint evidence를 current priority로 되살리지 않는다.
- 최근 git history는 Story 5.4 triage summary/recovery mapping merge가 마지막 주요 변경이다. Story 5.5는 dashboard read model/service/bucket read path를 확장하되 catalog/navigation, ingest/heartbeat, snapshot/history를 재작성하지 않는다.

## Architecture Constraints

- Active baseline은 Traditional MVC + Service/Repository Layering이다.
- Portal package는 feature-first MVC 구조를 따른다.
- 이 프로젝트에서 `domain`은 business feature grouping namespace이며 DDD pure domain layer가 아니다.
- 구현 위치는 기존 `domain.dashboard`, `domain.bucket`, `domain.state` feature packages를 우선 재사용한다.
- `application`, `port`, `adapter`, `adapter.in`, `adapter.out` package를 새로 만들지 않는다.
- Controller는 service를 호출하고 repository를 직접 호출하지 않는다.
- Service는 빠른 MVC 구현을 위해 필요하면 Spring Data repository와 JPA entity를 직접 사용할 수 있지만, JPA entity를 external return model로 노출하지 않는다.
- Repository는 Spring Data JPA/Jakarta Persistence + Hibernate 기반 read-only 조회만 담당한다.
- Flyway SQL migration이 schema source of truth다. Story 5.5는 새 migration을 기대하지 않는다.
- DB view, materialized view, trigger, stored procedure에 endpoint rule, confidence, rank, recommended action을 숨기지 않는다.
- raw project key, access token, refresh token, GitHub OAuth token, provider raw payload, secret은 response/log/error에 노출하지 않는다.
- 새 공개 클래스/메서드/핵심 helper의 Javadoc/comment는 프로젝트 지침에 따라 한국어로 작성한다.

## Developer Guardrails

- UI는 lifecycle state, starter connection diagnosis, zeroInsight, recovery, rule, p95/p99, endpoint priority를 재계산하지 않는다.
- Controller는 lifecycle state, starter connection diagnosis, zeroInsight, recovery, rule, p95/p99, endpoint priority를 재계산하지 않는다.
- Repository는 lifecycle state, starter connection diagnosis, zeroInsight, recovery, rule, confidence, p95/p99, endpoint priority를 계산하지 않는다.
- heartbeat를 accepted bucket freshness, host application health, degraded 판단, recovery source, endpoint priority source로 합치지 않는다.
- accepted bucket은 metric data freshness/state/read-model source-of-truth다.
- latest accepted bucket `endUtc`는 freshness source일 뿐 dashboard current window end가 아니다.
- current/baseline endpoint evidence는 query 시점 15분 window를 따른다.
- p95/p99를 평균, 최댓값, 합산, 병합하지 않는다.
- histogram bucket으로 p95/p99, avg/max latency, endpoint percentile을 계산하지 않는다.
- endpoint p95/p99, endpoint percentile rollup, endpoint p99 alert 기준을 만들지 않는다.
- `metrics` field에 p95/p99, avg/max latency, health score, availability score를 추가하지 않는다.
- `triageCards[].affectedEndpoint`는 optional hint이며 rank가 아니다.
- `UNKNOWN` route는 endpoint priority에서 제외하고 raw route detail로 보완하지 않는다.
- raw path, query string, query key/value, trace id, per-request sample, raw accepted bucket JSON, raw endpoint JSON, raw histogram JSON string을 response evidence에 노출하지 않는다.
- dashboard UI, snapshot persistence, operational event API, endpoint timeseries table, raw instance timeseries table, Redis/outbox를 추가하지 않는다.
- unrelated refactor, package 재배치, 기존 ingest/heartbeat behavior 변경을 피한다.

## Test Expectations

Focused test 대상 후보:

- `EndpointPriorityServiceTest`
- `DashboardReadModelServiceTest`
- `DashboardControllerTest`
- `MetricBucketRepositoryIntegrationTest` 또는 Story 5.5 전용 repository projection integration test
- `MvcLayerBoundaryTest`

필수 scenario:

- `endpointPriority`는 typed `EndpointPriorityItem` list로 serialize된다.
- `endpointPriority` item은 `rank`, `method`, `route`, `endpointKey`, `reason`, `ruleIds`, `confidence`, `score`, `freshness`, `evidence`, `recommendedAction`을 포함한다.
- `endpointPriority`에는 `rawPath`, `query`, `traceId`, raw JSON, endpoint p95/p99, endpoint percentile field가 없다.
- application freshness가 `current`가 아니면 endpoint evidence가 있어도 `endpointPriority=[]`다.
- current/baseline endpoint request count가 30 미만이면 candidate를 만들지 않는다.
- `error_spike`는 threshold 세 조건을 모두 만족할 때만 만들어진다.
- `latency_spike`는 `>500ms` slow share와 boundary match 조건을 만족할 때만 만들어진다.
- `error_and_latency`는 같은 endpoint가 error와 latency 조건을 모두 만족할 때 reason으로 선택된다.
- `comparative_regression`은 request count 100 guard와 delta 조건을 만족할 때만 만들어지고 confidence가 0.64를 넘지 않는다.
- ranking은 reason priority와 tie-breaker 순서를 따른다.
- 같은 입력은 항상 같은 rank order를 반환한다.
- 반환 item은 최대 5개다.
- `UNKNOWN` route와 `<method> UNKNOWN` endpoint는 후보에서 제외된다.
- 모든 endpoint가 `UNKNOWN`이면 `endpointPriority=[]`다.
- current/baseline endpoint merge는 request/error count를 endpointKey 단위로 합산한다.
- endpoint histogram boundary set이 일치하면 cumulative count를 boundary별로 합산한다.
- endpoint histogram boundary mismatch는 latency evidence `unavailable`이지만 error evidence는 유지된다.
- invalid endpoint JSON은 raw JSON 노출 없이 `insufficient`로 수렴한다.
- baseline endpoint missing은 `insufficient_baseline`으로 수렴한다.
- `triageCards[].affectedEndpoint`와 top endpoint priority가 달라도 response는 valid다.
- Controller test는 service stub 결과 serialization과 404 mapping만 검증한다.
- Repository integration test는 application scope, `(start, end]` window, endpoint JSON projection만 검증하고 rule/rank/confidence를 검증하지 않는다.
- no `application`, `port`, `adapter` package가 추가되지 않았음을 `MvcLayerBoundaryTest`로 확인한다.

Suggested commands:

```bash
./gradlew :observability-portal:test --tests '*EndpointPriority*'
./gradlew :observability-portal:test --tests '*DashboardReadModel*'
./gradlew :observability-portal:test --tests '*DashboardController*'
./gradlew :observability-portal:test --tests '*MetricBucketRepository*'
./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest
./gradlew :observability-portal:test
git diff --check
```

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- 2026-05-26: bmad-create-story workflow 설정, project-context, sprint status, Epic 5, read model/insight/histogram/ingest/route/state/time/api contracts, Story 5.3/5.4 산출물, Story 5.5 결정 문서, 현재 dashboard/bucket code를 확인했다.
- 2026-05-26: Story 5.5 create-story 산출물을 `planning-artifacts/stories/5-5-endpoint-priority-read-model.md`에 생성했다.
- 2026-05-26: bmad-dev-story 실행으로 Story 5.5 전체와 닫힌 계약 문서, 선행 Story 5.3/5.4, dashboard/bucket/service/test 구조를 재확인하고 sprint status를 `in-progress`로 전환했다.
- 2026-05-26: endpoint priority typed read model, endpoint evidence projection, `EndpointPriorityService`, dashboard orchestration, controller/repository/service focused tests를 구현했다.
- 2026-05-26: required focused tests, MVC boundary, 전체 `./gradlew :observability-portal:test`, `git diff --check`를 통과했다.

### Completion Notes List

- `ApplicationDashboardReadModel.endpointPriority`를 `List<EndpointPriorityItem>` typed list로 변경하고 reason/freshness/evidence/status enum과 bounded validation을 추가했다.
- `MetricBucketRepository`/JPA에 `endpoints_json` application/window `(start, end]` projection을 추가하되 rule, rank, confidence, action, endpoint p95/p99 계산은 service에 남겼다.
- `EndpointPriorityService`가 current freshness에서만 endpoint JSON merge, UNKNOWN 제외, threshold/rule/confidence/ranking/recommended action을 계산하도록 구현했다.
- stale/down/unknown 또는 insufficient evidence에서는 current `endpointPriority=[]`로 수렴하며, stale/down 직전 evidence를 current response에 복사하지 않는다.
- controller serialization, dashboard orchestration, repository projection, MVC boundary와 전체 regression이 통과했다.

### File List

- `implementation-artifacts/sprint-status.yaml`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/model/EndpointEvidenceRow.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/AcceptedMetricBucketJpaRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/model/ApplicationDashboardReadModel.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/DashboardReadModelService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/EndpointPriorityService.java`
- `observability-portal/src/test/java/com/observation/portal/domain/bucket/repository/MetricBucketRepositoryIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/controller/DashboardControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/model/EndpointPriorityReadModelShapeTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/service/DashboardReadModelServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/service/EndpointPriorityServiceTest.java`
- `planning-artifacts/stories/5-5-endpoint-priority-read-model.md`

### Change Log

- 2026-05-26: Endpoint priority typed read model, bounded endpoint evidence projection, service ranking/rule calculation, dashboard/controller serialization, focused regression tests를 구현하고 Story 5.5를 review 상태로 전환했다.

## Status

review
