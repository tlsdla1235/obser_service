---
artifactType: story
storyId: "5.4"
storyKey: "5-4-triage-summary-and-zero-insight-recovery-mapping"
epic: "Epic 5. Dashboard Read Model and API"
title: "Triage Summary and Zero-Insight/Recovery Mapping"
architectureStyle: Traditional MVC
status: done
date: 2026-05-25
---

# Story 5.4 - Triage Summary and Zero-Insight/Recovery Mapping

## User Story

portal 구현자로서, Application Dashboard read model이 앱 단위 triage card와 zeroInsight/recovery mapping을 server-computed contract로 제공하길 원한다.

그래야 UI가 빈 화면 대신 "지금 우선 확인할 신호" 또는 "왜 아직 조치가 없는지"를 표시하면서도, state, rule, recovery, p95/p99, endpoint priority를 controller나 frontend에서 다시 계산하지 않는다.

## Source of Truth

`implementation-artifacts/spec-story-5-4-triage-zero-insight-recovery-contract-decisions.md`가 Story 5.4의 최상위 결정 문서다.

이 story는 해당 문서의 8개 계약을 닫힌 계약으로 취급한다. 구현자는 recovery source, rule scope, typed triage card shape, zeroInsight precedence, degraded/triage 분리, seed threshold, saturation hint 조건, Story 5.5 endpoint boundary를 재논의하지 않는다.

## Scope / Out of Scope

포함:

- `GET /api/projects/{projectId}/applications/{applicationId}/dashboard` read model의 Story 5.3 shape 확장
- `triageCards`를 `List<Object>` placeholder에서 typed read model list로 변경
- application-level `global_error_spike`, `global_latency_spike`, saturation hint 계열 triage candidate 계산
- confidence `>= 0.65` card exposure, 최대 0~3개 triage card ranking
- `triageCards=[]`일 때 zeroInsight reason precedence 고정
- stale/down 이후 새 accepted bucket이 들어왔지만 current sample이 insufficient인 recovery observation mapping
- lightweight previous context: 최신/current accepted bucket 직전 bucket gap으로 previous `stale`/`down` 후보 판단
- `TriageSummaryService` 또는 동등 service/helper가 triage card 결과와 `DegradedHysteresisInput` 대표 입력을 분리해서 반환
- degraded enter는 rule guard 통과, confidence `>= 0.75`, 최근 5개 30초 bucket 중 3개 이상 bad 조건으로 제한
- `endpointPriority=[]` placeholder 유지
- `triageCards[].affectedEndpoint` optional hint 0~1개 허용
- bounded evidence object만 응답에 포함
- focused unit/service/controller/repository tests와 MVC boundary regression

제외:

- endpoint ranking/list 구현
- top-level `endpointPriority` typed list 채우기
- endpoint-specific freshness, endpoint evidence list, endpoint recommended action
- endpoint p95/p99 계산, endpoint percentile rollup, endpoint p99 alert 기준
- p95/p99 평균, 최댓값 선택, 합산, 병합, histogram-derived percentile
- raw path, query string, query key/value, trace id, per-request sample 노출
- raw accepted bucket JSON, raw `endpoints_json`, raw histogram JSON string 노출
- resource latest sample만으로 degraded state 또는 원인 확정 card 생성
- heartbeat를 accepted bucket freshness, host application health, degraded 판단 source로 합치기
- dashboard snapshot persistence, previous read model persistence, recovery marker source 정교화
- snapshot detail, operational event history, endpoint timeseries, raw bucket explorer
- dashboard UI/static asset 구현
- starter ingest/heartbeat endpoint 동작 변경
- `application`, `port`, `adapter` package 추가

## Acceptance Criteria

1. Dashboard `200` response는 Story 5.3 top-level fields를 유지하고, `triageCards`를 typed list로 반환한다.
2. `ApplicationDashboardReadModel.triageCards`는 `List<TriageCard>` 또는 동등 typed record list이며 `List<Object>` placeholder가 아니다.
3. `TriageCard`는 `ruleId`, `severity`, `title`, `summary`, `recommendation`, `confidence`, `score`, `affectedEndpoint`, `evidence`를 포함한다.
4. `severity`는 MVP에서 제한된 값(`info`, `warning`, `critical` 또는 enum)만 허용한다.
5. `confidence`는 `0.0 <= confidence <= 1.0`, `score`는 정렬용 bounded numeric value로 검증한다.
6. `evidence`는 rule별 bounded object만 허용하고 raw persistence JSON string이나 unbounded map을 그대로 노출하지 않는다.
7. evidence에는 request count, current/baseline error rate/count, histogram distribution window summary, source-scoped starter percentile point summary, runtime ratio latest sample, freshness/status reason 같은 bounded field만 포함할 수 있다.
8. evidence에는 raw path, query string, query key/value, trace id, per-request sample, endpoint p95/p99, endpoint percentile rollup, histogram-derived percentile을 포함하지 않는다.
9. Story 5.4 rule scope는 application-level triage와 zeroInsight/recovery mapping이다.
10. `global_error_spike`, `global_latency_spike`, `db_pool_high_with_latency`, `cpu_high_with_latency`, `heap_high_hint` candidate를 계산할 수 있다.
11. Availability 계열 copy는 state/zeroInsight/recovery와 중복되지 않게 first-screen explanation에만 제한하고, host application down을 확정하지 않는다.
12. endpoint ranking/list를 만들지 않는다.
13. `affectedEndpoint`는 optional hint이며 endpoint priority item이나 endpoint rank가 아니다.
14. `endpointPriority`는 Story 5.4에서 계속 `[]`로 반환한다.
15. `triageCards`는 confidence `>= 0.65` candidate 중 ranking 기준으로 최대 3개만 노출한다.
16. `triageCards`가 하나라도 있으면 `zeroInsight`는 "zero insight 없음" 상태로 둔다. Story 5.4 구현 결정은 기존 response 호환성과 `read-model-contract` 예시를 함께 고려해, field는 유지하되 value는 `null`을 허용하는 방향을 우선한다.
17. `triageCards=[]`이면 `zeroInsight`는 반드시 non-null이다.
18. `triageCards=[]`일 때 `zeroInsight.reasonCode`는 아래 우선순위로만 결정한다: `observing_recovery`, `telemetry_unreachable`, `waiting_first_data`, `insufficient_sample`, `metric_data_idle`, `no_action_needed`.
19. recovery trigger가 성립하고 `triageCards=[]`이면 `observing_recovery`가 `insufficient_sample`보다 우선한다.
20. `observing_recovery` copy는 "복구 완료"가 아니라 "복구 관찰 중"으로 표현한다.
21. `no_recent_traffic` zeroInsight reason code를 새로 추가하지 않고 `metric_data_idle`로 수렴한다.
22. Recovery source는 accepted bucket gap 기반 lightweight previous context를 사용한다.
23. 최신 accepted bucket freshness가 `current`이고 직전 accepted bucket과의 metric data 공백이 stale/down threshold 이상이면 previous state 후보를 `stale` 또는 `down`으로 본다.
24. previous state 후보가 `stale` 또는 `down`이고 current freshness가 `current`이며 current sample이 `insufficient`이면 `state.code=unknown`, `recovery.isRecovering=true`, `zeroInsight.reasonCode=observing_recovery`를 반환한다.
25. `lastHealthyAt`은 현재 요청의 accepted bucket만으로 추론하지 않는다. snapshot 또는 이전 read model source가 없으면 `recovery.lastHealthyAt=null`이다.
26. latest/current bucket만 있고 직전 bucket gap 근거가 없으면 recovery previous state를 만들지 않는다.
27. Story 5.8에서 snapshot 기반 `previousState`, `lastHealthyAt`, recovery marker source를 재정교화할 수 있도록 구현 주석/test/story follow-up에 이 결정 문서를 참조한다.
28. triage card 노출과 degraded state 진입은 분리한다.
29. confidence `0.65~0.74` candidate는 card로 노출될 수 있지만 `state.code=active`일 수 있다.
30. confidence `>= 0.75` candidate라도 최근 5개 30초 bucket 중 3개 이상 bad 조건을 통과하지 못하면 `state.code=active`일 수 있다.
31. `state.code=degraded`는 rule guard 통과, confidence `>= 0.75`, 최근 5개 30초 bucket 중 3개 이상 bad 조건을 모두 만족한 대표 concern이 있을 때만 가능하다.
32. `TriageSummaryService`는 card exposure 결과와 degraded hysteresis 대표 입력을 분리한다.
33. `DashboardReadModelService`는 triage summary의 degraded input을 `MetricLifecycleInput`에 전달하고, 최종 metric state 판단은 `LifecycleStateService`에 위임한다.
34. UI는 triage card 존재 여부로 degraded state를 다시 계산하지 않는다.
35. Current minimum request count는 current 15분 window `>= 30` requests다.
36. Baseline sufficiency는 baseline 15분 window `>= 30` requests다.
37. `global_error_spike`는 current error rate `>= 0.05`, absolute delta `>= 0.03`, relative delta `>= 2x`를 모두 통과해야 한다.
38. baseline error rate가 0인 경우에도 division-by-zero 없이 absolute threshold와 absolute delta를 통과한 뒤 `current >= baseline * 2` 의미로만 처리한다.
39. `global_latency_spike`는 histogram distribution에서 `> 500ms` slow bucket share를 사용한다.
40. latency rule은 current slow share `>= 0.20`, slow share delta `>= 0.10`, request/baseline sufficiency를 통과해야 한다.
41. latency rule은 histogram bucket distribution을 사용하되 percentile scalar를 계산하지 않는다.
42. Degraded bad bucket count는 최근 5개 30초 bucket 중 `>= 3` bad로 고정한다.
43. Degraded resolve는 기존 `DegradedHysteresisInput` 기준인 confidence `< 0.60` 또는 rule recovery condition 5 consecutive buckets를 유지한다.
44. saturation hint는 동반 신호가 있을 때만 card 후보로 쓴다.
45. datasource pool ratio `>= 0.85`는 latency spike와 함께 있을 때만 `db_pool_high_with_latency` candidate가 될 수 있다.
46. CPU usage ratio `>= 0.85`는 latency spike와 함께 있을 때만 `cpu_high_with_latency` candidate가 될 수 있다.
47. heap used ratio `>= 0.90`은 latency 또는 error signal과 함께 있을 때만 `heap_high_hint` candidate가 될 수 있다.
48. resource latest sample 단독으로 degraded state를 만들지 않는다.
49. resource latest sample 단독으로 high-confidence 원인 확정 card를 만들지 않는다.
50. saturation hint copy는 "가능성을 먼저 확인"하는 문구를 사용하고 원인을 확정하지 않는다.
51. heartbeat는 accepted bucket freshness, host application health, rule guard, degraded 판단, recovery source로 사용하지 않는다.
52. Controller는 path/status mapping과 service 위임만 담당하고 state, rule, zeroInsight, recovery, p95/p99, endpoint priority를 계산하지 않는다.
53. Repository는 bounded read projection만 반환하고 state, rule, zeroInsight, recovery, p95/p99, endpoint priority를 계산하지 않는다.
54. JPA entity는 API response, controller response DTO, service external return model로 직접 노출하지 않는다.
55. 새 공개 클래스, 공개 메서드, 핵심 helper는 AGENTS.md 지침에 따라 한국어 Javadoc/docstring으로 역할과 사용 맥락을 설명한다.
56. Focused tests가 zeroInsight precedence, recovery mapping, typed triage card shape, rule thresholds, card-vs-degraded 분리, saturation hint coupling, endpoint boundary, MVC recomputation guard를 검증한다.
57. `./gradlew :observability-portal:test`와 `git diff --check`가 통과한다.

## Tasks/Subtasks

- [x] Typed triage read model shape 구현 (AC: 1~8, 15~17, 54~55)
  - [x] `ApplicationDashboardReadModel`의 `triageCards`를 `List<TriageCard>`로 변경한다.
  - [x] `TriageCard`와 bounded `TriageEvidence` record 또는 rule-specific evidence record를 추가한다.
  - [x] `confidence`, `score`, `severity`, nullable `affectedEndpoint`, bounded evidence validation을 model constructor에 둔다.
  - [x] `zeroInsight`는 `triageCards=[]`일 때 non-null, `triageCards`가 있을 때 field value `null` 허용으로 조정한다.
- [x] Triage summary service/helper 추가 (AC: 9~15, 28~33, 35~50)
  - [x] `domain.dashboard.service.TriageSummaryService` 또는 동등 package-local helper를 추가한다.
  - [x] current/baseline aggregate, histogram distribution, source-scoped starter percentile point summary, runtime ratio latest sample, freshness/status reason을 bounded input으로 받는다.
  - [x] `global_error_spike` guard와 confidence/score/ranking을 구현한다.
  - [x] `global_latency_spike` guard를 histogram slow share `>500ms` 기준으로 구현하되 percentile을 계산하지 않는다.
  - [x] saturation hint는 latency/error 동반 신호가 있을 때만 candidate로 만든다.
  - [x] card exposure result와 degraded `DegradedHysteresisInput` 대표 concern을 분리해서 반환한다.
- [x] Repository neutral read path 추가 (AC: 22~27, 35~50, 53~54)
  - [x] latest/current bucket 직전 bucket gap을 판단할 수 있는 neutral projection을 추가한다.
  - [x] recent 5 bucket bad count 계산에 필요한 30초 bucket bounded projection을 추가한다.
  - [x] runtime ratio latest sample 또는 representative current bucket ratio를 읽는 projection을 추가한다.
  - [x] repository method는 bucket boundary, count, error count, summary duration buckets, runtime ratios 같은 raw-ish bounded field만 반환한다.
  - [x] repository method 이름과 projection Javadoc에 state/rule/recovery/p95/p99/endpoint priority 계산을 하지 않는다고 명시한다.
- [x] Dashboard orchestration 확장 (AC: 16~34, 51~53)
  - [x] 기존 Story 5.3 window/freshness/source-scoped percentile/histogram 조립을 유지한다.
  - [x] baseline aggregate를 rule guard에 필요한 request/error count source로 조회한다.
  - [x] previous bucket gap으로 previous `stale`/`down` 후보를 만든다.
  - [x] snapshot/previous read model source가 없으면 previous healthy source는 `Optional.empty()`로 전달한다.
  - [x] triage summary 결과의 degraded input과 previous state 후보를 `MetricLifecycleInput`에 전달한다.
  - [x] `LifecycleStateService` 최종 state와 recovery guidance를 response에 옮긴다.
  - [x] `triageCards=[]`일 때 zeroInsight precedence helper를 적용한다.
  - [x] `triageCards`가 있으면 zeroInsight reason을 만들지 않거나 null로 둔다.
- [x] ZeroInsight/recovery precedence 고정 (AC: 18~27)
  - [x] `observing_recovery`를 `telemetry_unreachable`보다 먼저 평가하되 recovery trigger 자체가 current accepted bucket과 previous stale/down gap을 필요로 하게 한다.
  - [x] missing/stale heartbeat + missing/stale accepted bucket은 `telemetry_unreachable`로 유지한다.
  - [x] accepted bucket 없음 + recent heartbeat는 `waiting_first_data`로 유지한다.
  - [x] current accepted bucket + insufficient sample + recovery trigger 없음은 `insufficient_sample`로 유지한다.
  - [x] traffic idle 또는 recent heartbeat + 오래된/없는 accepted bucket traffic 부재 조합은 `metric_data_idle`로 수렴한다.
  - [x] sufficient active sample + no triage card는 `no_action_needed`로 유지한다.
- [x] Controller/API serialization 유지 (AC: 1~8, 16~17, 52)
  - [x] `DashboardController` route와 404 mapping을 유지한다.
  - [x] controller test는 service 결과 serialization/status mapping만 검증한다.
  - [x] controller에서 repository, JSON parsing, rule evaluation, state/recovery/zeroInsight 계산을 호출하지 않는다.
- [x] Focused tests와 regression 실행 (AC: 56~57)
  - [x] `DashboardReadModelServiceTest`에 5.4 scenarios를 추가한다.
  - [x] `TriageSummaryServiceTest` 또는 동등 focused unit test를 추가한다.
  - [x] repository projection이 추가되면 PostgreSQL Testcontainers integration test로 query scope와 neutral projection만 검증한다.
  - [x] `DashboardControllerTest` serialization fixture를 typed triage/nullable zeroInsight shape로 갱신한다.
  - [x] `MvcLayerBoundaryTest`가 계속 통과하는지 확인한다.
  - [x] `./gradlew :observability-portal:test --tests '*DashboardReadModel*'`, `./gradlew :observability-portal:test --tests '*TriageSummary*'`, `./gradlew :observability-portal:test`, `git diff --check`를 실행한다.

## Dev Notes

### Contract Priority

- 최우선 source는 `implementation-artifacts/spec-story-5-4-triage-zero-insight-recovery-contract-decisions.md`다.
- 이 story와 기존 `read-model-contract.md`, `state-semantics.md`, `insight-rules.md` 예시가 충돌하면 Story 5.4 결정 문서를 우선한다.
- 닫힌 계약을 재논의하지 않는다. 구현 중 충돌처럼 보이는 지점은 source 문서를 기준으로 story scope 안에서 가장 보수적인 변경으로 해소한다.

### Current Code State

- `ApplicationDashboardReadModel`은 현재 `triageCards`와 `endpointPriority`를 `List<Object>` placeholder로 둔다. Story 5.4는 `triageCards`만 typed list로 바꾸고 `endpointPriority`는 `[]` placeholder로 유지한다.
- `ApplicationDashboardReadModel` compact constructor는 현재 `zeroInsight` non-null을 강제한다. Story 5.4는 `triageCards=[]`일 때만 zeroInsight non-null을 강제하고, triage card가 있으면 response field value가 null일 수 있도록 모델 검증을 조정해야 한다.
- `DashboardReadModelService.buildDashboard()`는 current/baseline window, latest bucket, current request/error aggregate, percentile rows, histogram rows, scoped heartbeat를 조회한다.
- `DashboardReadModelService.metricLifecycleInput()`은 현재 `DegradedHysteresisInput.noConcern()`, `previousState=Optional.empty()`, `previousHealthyAt=Optional.empty()`를 전달한다. Story 5.4는 triage summary degraded input과 accepted bucket gap 기반 previous state 후보를 여기에 연결한다.
- `DashboardReadModelService.zeroInsightReasonCode()`는 현재 Story 5.2 precedence이며 `observing_recovery`를 반환하지 않는다. Story 5.4는 5.4 precedence로 교체하거나 별도 helper로 분리한다.
- `LifecycleStateService`는 이미 `MetricLifecycleInput.previousState()`가 `STALE`/`DOWN`, freshness `CURRENT`, sample `INSUFFICIENT`일 때 `RecoveryGuidance.recovering()`을 반환한다.
- `DegradedHysteresisInput`은 이미 enter confidence `0.75`, bad bucket threshold `3/5`, resolve confidence `<0.60`, resolve consecutive `5`를 상수로 가진다.
- `MetricBucketRepository`는 latest bucket end, current aggregate, source-scoped percentile evidence, summary histogram evidence를 제공한다. Story 5.4에는 previous bucket gap, baseline aggregate, recent 5 bucket bad count, runtime ratio latest sample을 위한 neutral read path가 추가로 필요할 수 있다.
- `DashboardReadModelServiceTest`는 현재 `observing_recovery`가 반환되지 않는다고 검증한다. Story 5.4는 이 회귀 기대를 새 recovery scenario와 non-recovery scenario로 갱신해야 한다.

### Recovery Source Contract

- Recovery는 accepted bucket gap 기반 lightweight previous context로만 감지한다.
- 최신 accepted bucket이 `current` freshness이고 그 직전 metric data 공백이 `AcceptedBucketFreshnessEvaluator.STALE_AFTER` 이상이면 previous state 후보는 `stale`이다.
- 같은 gap이 `AcceptedBucketFreshnessEvaluator.DOWN_AFTER` 이상이면 previous state 후보는 `down`이다.
- current sample이 insufficient일 때만 recovery를 켠다.
- `lastHealthyAt`은 현재 bucket으로 만들지 않는다. Story 5.8 snapshot/previous read model source가 생기기 전까지는 null이 정상이다.
- heartbeat는 recovery source가 아니다.
- Story 5.8 follow-up: snapshot 기반 previous dashboard read model 또는 marker source가 생기면 gap fallback과 snapshot source 우선순위를 재정의한다.

### Rule Guard Seed Thresholds

| 항목 | 값 |
|---|---|
| Current minimum request count | current 15m `>= 30` |
| Baseline sufficiency | baseline 15m `>= 30` |
| Global error current error rate | `>= 0.05` |
| Error absolute delta | `>= 0.03` |
| Error relative delta | current `>= baseline * 2` |
| Latency slow bucket | duration bucket `> 500ms` |
| Current slow share | `>= 0.20` |
| Slow share delta | `>= 0.10` |
| Triage card exposure | confidence `>= 0.65` |
| Degraded enter | rule guard passed + confidence `>= 0.75` + recent 5 buckets 중 `>= 3` bad |
| Degraded resolve | confidence `< 0.60` 또는 rule recovery condition 5 consecutive buckets |

### Triage Evidence Boundary

- Evidence는 "왜 card가 나왔는지"를 설명하는 bounded object다.
- Histogram evidence는 bucket distribution과 slow share summary만 허용한다. percentile scalar로 변환하지 않는다.
- Source-scoped starter percentile point는 source/scope가 드러나는 요약 evidence로만 허용한다. 여러 point를 평균/최댓값/병합하지 않는다.
- Endpoint hint가 필요하면 `affectedEndpoint` 하나만 둔다. 이는 endpoint ranking이 아니다.
- raw path/query/trace/per-request sample은 어떤 evidence에도 포함하지 않는다.

### Story 5.5 Boundary

- Story 5.4는 `affectedEndpoint` optional hint만 남긴다.
- Story 5.4는 `endpointPriority=[]`를 유지한다.
- Story 5.5가 `endpointPriority` typed list를 처음 채운다.
- Story 5.5의 "확실한 정보"는 root cause 확정이 아니다. 이는 `rank`, `reason`, `confidence`, `freshness`, `evidence`, `recommendedAction` 기반 next-check surface를 뜻한다.
- Story 5.5도 raw path/query string/query key/value/trace id/per-request sample, endpoint p95/p99, endpoint percentile rollup, endpoint p99 alert 기준을 만들지 않는다.

### Story 5.8 Boundary

- Story 5.4는 dashboard snapshot persistence를 만들지 않는다.
- Story 5.4는 previous read model repository나 recovery marker source를 만들지 않는다.
- Story 5.4의 lightweight previous context는 accepted bucket gap fallback이다.
- Story 5.8은 snapshot 기반 `previousState`, `lastHealthyAt`, recovery marker source와 fallback 우선순위를 닫는다.
- current accepted bucket만으로 `lastHealthyAt`을 추론하지 않는 원칙은 Story 5.8 이후에도 유지한다.

## Existing Code / Documents To Reuse

- `_bmad/custom/project-context.md`
  - Traditional MVC + Service/Repository Layering, feature-first MVC, Spring Data JPA/Flyway 기준을 따른다.
- `implementation-artifacts/spec-story-5-4-triage-zero-insight-recovery-contract-decisions.md`
  - Story 5.4 source of truth다.
- `planning-artifacts/contracts/read-model-contract.md`
  - first-screen UI source-of-truth, zeroInsight/recovery, heartbeat/accepted bucket 분리, MVC boundary를 따른다.
- `planning-artifacts/contracts/state-semantics.md`
  - accepted bucket axis와 starter connection axis 분리, degraded hysteresis, recovery trigger를 따른다.
- `planning-artifacts/contracts/insight-rules.md`
  - insight rule은 원인 확정이 아니라 first-check triage engine이라는 원칙을 따른다.
- `planning-artifacts/contracts/time-buckets.md`
  - 30초 bucket, current 15분, baseline 15분, stale 90초, down 180초 기준을 따른다.
- `planning-artifacts/contracts/histogram-merge.md`
  - histogram bucket은 distribution display/evidence source이며 p95/p99 계산 source가 아니다.
- `planning-artifacts/contracts/metric-taxonomy.md`
  - 허용 metric, low-cardinality route, raw tag/query 금지, runtime ratio latest-only 의미를 따른다.
- `planning-artifacts/contracts/ingest-envelope.md`
  - accepted bucket과 heartbeat 분리, starter canonical percentile source, endpoint bucket boundary를 따른다.
- `planning-artifacts/api-surface.md`
  - dashboard API path, applicationId path scope, 404 mapping, controller boundary를 유지한다.
- `planning-artifacts/stories/5-2-application-dashboard-read-model-skeleton.md`
  - dashboard skeleton, zeroInsight baseline mapping, source axis separation, controller/service/repository boundary를 재사용한다.
- `planning-artifacts/stories/5-3-source-scoped-percentile-and-histogram-distribution-read-model.md`
  - source-scoped percentile and histogram distribution evidence shape와 no percentile recomputation guard를 재사용한다.
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/model/ApplicationDashboardReadModel.java`
  - typed triage card 추가 및 zeroInsight nullability 조정 대상이다.
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/DashboardReadModelService.java`
  - Triage summary orchestration, recovery/zeroInsight precedence, degraded input 연결 대상이다.
- `observability-portal/src/main/java/com/observation/portal/domain/state/service/LifecycleStateService.java`
  - 최종 state와 recovery guidance 판단 source다. UI/controller/repository로 state 판단을 옮기지 않는다.
- `observability-portal/src/main/java/com/observation/portal/domain/state/model/DegradedHysteresisInput.java`
  - degraded enter/resolve threshold source다.
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java`
  - neutral bucket read path 확장 후보이다.
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/service/DashboardReadModelServiceTest.java`
  - Story 5.4 service-level regression scenarios를 추가할 주요 테스트다.

## Previous Story Intelligence

- Story 5.2는 Application Dashboard endpoint, current/baseline window, accepted bucket freshness, starter connection axis, zeroInsight baseline mapping을 구현했다.
- Story 5.2는 `observing_recovery`를 의도적으로 제외했다. Story 5.4가 이 gap을 닫는다.
- Story 5.3은 `sourceScopedPercentiles`와 `histogramDistribution`을 evidence-only로 추가했다. Story 5.4는 이 evidence를 rule input으로 사용할 수 있지만 p95/p99나 histogram percentile을 새로 만들 수 없다.
- Story 5.3은 `sourceScopedPercentiles.source=starter_local`을 persisted source 그대로 노출하도록 고정했다.
- Story 4.2/4.3은 recovery를 top-level `recovering` state가 아니라 `state=unknown` + `recovery.isRecovering=true` 조합으로 고정했다.
- Story 4.4는 heartbeat가 metric state를 current/active로 만들지 못하고 host down을 단정하지 않도록 회귀 기준을 고정했다.
- 최근 git history는 Story 5.3 dashboard evidence read model merge가 마지막 주요 변경이다. Story 5.4는 dashboard read model/service/bucket read path를 확장하되 catalog/navigation을 재작성하지 않는다.

## Architecture Constraints

- Active baseline은 Traditional MVC + Service/Repository Layering이다.
- Portal package는 feature-first MVC 구조를 따른다.
- 이 프로젝트에서 `domain`은 business feature grouping namespace이며 DDD pure domain layer가 아니다.
- 구현 위치는 기존 `domain.dashboard`, `domain.bucket`, `domain.state` feature packages를 우선 재사용한다.
- `application`, `port`, `adapter`, `adapter.in`, `adapter.out` package를 새로 만들지 않는다.
- Controller는 service를 호출하고 repository를 직접 호출하지 않는다.
- Service는 빠른 MVC 구현을 위해 필요하면 Spring Data repository와 JPA entity를 직접 사용할 수 있지만, JPA entity를 external return model로 노출하지 않는다.
- Repository는 Spring Data JPA/Jakarta Persistence + Hibernate 기반 read-only 조회만 담당한다.
- Flyway SQL migration이 schema source of truth다. Story 5.4는 새 migration을 기대하지 않는다.
- DB view, materialized view, trigger, stored procedure에 lifecycle state, starter connection diagnosis, zeroInsight, recovery, rule confidence, p95/p99, endpoint priority 계산을 숨기지 않는다.
- raw project key, access token, refresh token, GitHub OAuth token, provider raw payload, secret은 response/log/error에 노출하지 않는다.
- 새 공개 클래스/메서드/핵심 helper의 Javadoc/comment는 프로젝트 지침에 따라 한국어로 작성한다.

## Developer Guardrails

- UI는 lifecycle state, starter connection diagnosis, zeroInsight, recovery, rule, p95/p99, endpoint priority를 재계산하지 않는다.
- Controller는 lifecycle state, starter connection diagnosis, zeroInsight, recovery, rule, p95/p99, endpoint priority를 재계산하지 않는다.
- Repository는 lifecycle state, starter connection diagnosis, zeroInsight, recovery, rule, confidence, p95/p99, endpoint priority를 계산하지 않는다.
- heartbeat를 accepted bucket freshness, host application health, degraded 판단, recovery source로 합치지 않는다.
- heartbeat 성공만으로 accepted bucket, dashboard snapshot, operational event, p95/p99, rule/read-model calculation을 만들지 않는다. 최근 heartbeat는 snapshot 저장 eligibility gate로만 사용할 수 있다.
- accepted bucket은 metric data freshness/state/read-model source-of-truth다.
- latest accepted bucket `endUtc`는 freshness source일 뿐 dashboard current window end가 아니다.
- recovery previous state 후보는 accepted bucket gap 기반 lightweight context이며, 현재 bucket 하나만으로 만들지 않는다.
- `lastHealthyAt`은 현재 bucket으로 추론하지 않는다.
- `state=down` copy도 host application process down을 확정하지 않는다.
- p95/p99를 평균, 최댓값, 합산, 병합하지 않는다.
- histogram bucket으로 p95/p99, avg/max latency, endpoint percentile을 계산하지 않는다.
- endpoint p95/p99, endpoint percentile rollup, endpoint p99 alert 기준을 만들지 않는다.
- `metrics` field에 p95/p99, avg/max latency, health score, availability score를 추가하지 않는다.
- `global_latency_spike`는 histogram slow share evidence를 사용하고 percentile scalar를 만들지 않는다.
- saturation ratio latest sample 단독으로 degraded state나 원인 확정 card를 만들지 않는다.
- `affectedEndpoint`는 optional hint이며 rank가 아니다.
- `endpointPriority`는 Story 5.4에서 항상 empty placeholder다.
- raw path, query string, query key/value, trace id, per-request sample, raw accepted bucket JSON, raw endpoint JSON, raw histogram JSON string을 response evidence에 노출하지 않는다.
- dashboard UI, snapshot persistence, operational event API, endpoint timeseries table, raw instance timeseries table, Redis/outbox를 추가하지 않는다.
- unrelated refactor, package 재배치, 기존 ingest/heartbeat behavior 변경을 피한다.

## Test Expectations

Focused test 대상 후보:

- `TriageSummaryServiceTest`
- `DashboardReadModelServiceTest`
- `DashboardControllerTest`
- `MetricBucketRepositoryIntegrationTest` 또는 Story 5.4 전용 repository projection integration test
- `MvcLayerBoundaryTest`

필수 scenario:

- `triageCards`는 typed `TriageCard` list로 serialize된다.
- `triageCards=[]`이면 `zeroInsight`는 non-null이다.
- triage card가 있으면 response에서 `zeroInsight`는 null 또는 명시적 no-zero-insight marker 중 story 구현 결정과 일관된다. 우선 구현 방향은 null 허용이다.
- zeroInsight precedence는 `observing_recovery`, `telemetry_unreachable`, `waiting_first_data`, `insufficient_sample`, `metric_data_idle`, `no_action_needed` 순서다.
- previous bucket gap stale/down + latest current bucket + current sample insufficient는 `state.code=unknown`, `recovery.isRecovering=true`, `zeroInsight.reasonCode=observing_recovery`다.
- recovery scenario에서 `recovery.lastHealthyAt=null`이다.
- current bucket만 있고 previous gap 근거가 없으면 `observing_recovery`가 아니라 `insufficient_sample`이다.
- accepted bucket 없음 + recent heartbeat는 `waiting_first_data`다.
- accepted bucket 없음 + missing/stale heartbeat는 `telemetry_unreachable`다.
- stale/down accepted bucket + recent heartbeat는 `metric_data_idle`이며 host down copy를 반환하지 않는다.
- current bucket + insufficient sample + no recovery trigger는 `insufficient_sample`이다.
- current bucket + idle traffic은 `metric_data_idle`이다.
- sufficient active sample + no triage card는 `no_action_needed`다.
- `global_error_spike`는 current request `>=30`, baseline request `>=30`, current error rate `>=0.05`, delta `>=0.03`, relative `>=2x`를 모두 통과해야 card가 된다.
- guard 하나라도 실패하면 error spike card가 노출되지 않거나 confidence가 `0.65` 미만이다.
- `global_latency_spike`는 current/baseline histogram evidence와 slow share `>500ms` 기준을 사용하고 p95/p99 field를 만들지 않는다.
- histogram boundary mismatch 또는 missing baseline이면 latency comparison card를 만들지 않는다.
- confidence `0.64` candidate는 card로 노출되지 않는다.
- confidence `0.65` candidate는 card로 노출될 수 있다.
- confidence `0.70` card가 있어도 degraded input은 enter하지 않아 `state.code=active`일 수 있다.
- confidence `0.82` candidate라도 recent 5 bucket bad count가 2이면 `state.code=active`일 수 있다.
- confidence `0.82` + guard pass + recent 5 bucket bad count 3이면 `state.code=degraded`가 될 수 있다.
- datasource ratio `0.86`만 있고 latency spike가 없으면 saturation card를 만들지 않는다.
- datasource ratio `0.86` + latency spike는 `db_pool_high_with_latency` card 후보가 될 수 있다.
- CPU ratio `0.86` + latency spike는 `cpu_high_with_latency` card 후보가 될 수 있다.
- heap ratio `0.91` + latency 또는 error signal은 `heap_high_hint` card 후보가 될 수 있다.
- saturation hint card copy는 원인을 확정하지 않는다.
- `affectedEndpoint`가 있어도 `endpointPriority=[]`다.
- response evidence에 raw path, query, trace, per-request sample, endpoint p95/p99가 없다.
- response 어디에도 application/window p95/p99 scalar, histogram-derived percentile, endpoint percentile rollup이 없다.
- Controller test는 service stub 결과 serialization과 404 mapping만 검증한다.
- Repository integration test는 application scope, `(start, end]` window, previous bucket gap projection, recent 5 bucket projection, runtime ratio projection만 검증하고 rule/state를 검증하지 않는다.
- no `application`, `port`, `adapter` package가 추가되지 않았음을 `MvcLayerBoundaryTest`로 확인한다.

Suggested commands:

```bash
./gradlew :observability-portal:test --tests '*TriageSummary*'
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

- 2026-05-25: bmad-create-story workflow 설정, project-context, sprint status, Epic 5, read model/state/insight/time/histogram/metric/ingest/api contracts, Story 5.2/5.3 산출물, Story 5.4 결정 문서, 현재 dashboard/state/bucket code를 확인했다.
- 2026-05-25: Story 5.4 create-story 산출물을 `planning-artifacts/stories/5-4-triage-summary-and-zero-insight-recovery-mapping.md`에 생성했다.
- 2026-05-25: bmad-dev-story 실행으로 Story 5.4 전체와 닫힌 계약 문서를 재확인하고 sprint status를 `in-progress`로 전환했다.
- 2026-05-25: typed triage card read model, triage summary service, accepted bucket gap/recent/runtime neutral projection, dashboard orchestration을 구현했다.
- 2026-05-25: focused tests, repository integration, MVC boundary, 전체 `./gradlew :observability-portal:test`를 통과했다.

### Completion Notes List

- `triageCards`를 typed `TriageCard` list로 변경하고 bounded `TriageEvidence`, severity/confidence/score validation, nullable `zeroInsight` contract를 구현했다.
- `TriageSummaryService`가 `global_error_spike`, histogram slow share 기반 `global_latency_spike`, latency/error 동반 saturation hint를 계산하고 card exposure와 degraded input을 분리하도록 구현했다.
- accepted bucket gap 기반 lightweight previous context로 recovery trigger를 연결했으며, snapshot/previous source가 없으므로 `lastHealthyAt=null`을 유지했다.
- `endpointPriority=[]`를 유지하고 endpoint ranking/list, endpoint p95/p99, histogram-derived percentile, heartbeat 기반 freshness/recovery/degraded 판단은 추가하지 않았다.
- focused tests와 전체 regression이 통과했다.

### File List

- `implementation-artifacts/spec-story-5-4-triage-zero-insight-recovery-contract-decisions.md`
- `implementation-artifacts/sprint-status.yaml`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/model/AcceptedBucketGapEvidence.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/model/AcceptedBucketGapEvidenceRow.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/model/RecentBucketEvidenceRow.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/model/RuntimeRatioEvidenceRow.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/AcceptedMetricBucketJpaRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/model/ApplicationDashboardReadModel.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/DashboardReadModelService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/TriageSummaryService.java`
- `observability-portal/src/test/java/com/observation/portal/domain/bucket/repository/MetricBucketRepositoryIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/controller/DashboardControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/service/DashboardReadModelServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/service/TriageSummaryServiceTest.java`
- `planning-artifacts/stories/5-4-triage-summary-and-zero-insight-recovery-mapping.md`

### Change Log

- 2026-05-25: Story 5.4 create-story 산출물을 생성하고 sprint status를 ready-for-dev로 전환했다.
- 2026-05-25: Triage summary와 zeroInsight/recovery mapping을 구현하고 Story 5.4를 review 상태로 전환했다.

## Status

done
