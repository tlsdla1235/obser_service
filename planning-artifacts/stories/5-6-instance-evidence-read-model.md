---
artifactType: story
storyId: "5.6"
storyKey: "5-6-instance-evidence-read-model"
epic: "Epic 5. Dashboard Read Model and API"
title: "Instance Evidence Read Model"
architectureStyle: Traditional MVC
status: done
date: 2026-05-26
---

# Story 5.6 - Instance Evidence Read Model

## User Story

portal 구현자로서, Instance Detail 화면이 사용할 bounded instance evidence read model API를 service layer에서 제공하길 원한다.

그래야 UI가 Application Dashboard 판단을 대체하지 않고, 선택된 instance의 accepted bucket metric data, starter heartbeat connection, starter canonical percentile series, histogram/resource/endpoint evidence를 같은 current 15분 맥락에서 drill-down으로 확인할 수 있다.

## Source of Truth

`implementation-artifacts/spec-story-5-6-instance-evidence-contract-decisions.md`가 Story 5.6의 최상위 결정 문서다.

이 story와 기존 `read-model-contract.md`, `api-surface.md`, Epic 5 문서, 선행 Story 5.5 문서/코드가 충돌하면 Story 5.6 결정 문서를 우선한다. 구현자는 아래 결정을 재논의하지 않는다.

1. Instance Evidence API는 portal-generated `application_instances.id` UUID를 path identity로 사용한다.
2. Starter ingest/heartbeat payload는 UUID를 보내지 않고 `application.name`, `application.environment`, `application.instance` 문자열 identity만 보낸다.
3. Response는 raw explorer가 아니라 bounded evidence bundle이다.
4. Story 5.6은 current 15분 instance evidence만 구현한다.
5. `starterPercentiles`는 selected instance의 current 15분 30초 bucket series다.
6. `metricData` accepted bucket axis와 `starterConnection` heartbeat axis는 절대 합치지 않는다.
7. `endpointEvidence`는 dashboard `endpointPriority`를 보조하는 instance evidence이며 새 endpoint priority/rank/confidence/recommendedAction을 만들지 않는다.
8. `endpointEvidence.localDisplayOrder`는 표시 순서일 뿐 priority 판단이 아니다.
9. `dashboard_snapshots`, snapshot trend, operational history, 7d/14d trend는 Story 5.6 범위가 아니다.
10. raw accepted bucket JSON, raw `endpoints_json`, raw path/query/trace/per-request sample, endpoint p95/p99는 금지한다.

## Scope / Out of Scope

포함:

- `GET /api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/evidence` read-only API
- `{instanceId}`를 `application_instances.id` UUID로 해석하는 project/application/instance membership 검증
- application identity, instance identity, metric data axis, starter connection axis, starter percentile series, histogram distribution, resource hints, application triage contribution, endpoint evidence, links를 포함하는 bounded evidence bundle
- query 시점 기준 UTC 30초 boundary로 floor한 `evaluationAt`과 current 15분 `(start, end]` window
- selected `applicationInstanceId` scope의 latest accepted bucket, current window request/error aggregate, local percentile rows, summary histogram rows, latest runtime ratio sample, endpoint rows 조회
- application scope의 current/baseline aggregate, histogram, recent bucket, runtime, endpoint rows를 필요한 만큼 조회해 dashboard triage/endpoint priority seed를 재사용
- starter heartbeat lookup은 catalog instance의 `instanceName`과 application name/environment 문자열 identity로만 수행
- Story 5.5 endpoint priority의 route safety, endpoint JSON parsing, boundary merge, bounded evidence 규칙 재사용 또는 공유 helper 추출
- Application Dashboard가 Instance Detail로 진입할 수 있도록 bounded `instanceSummary` 또는 동등 entry surface에 `instanceId`, `instanceName`, `links.evidence`를 제공하는 최소 read model 보강
- focused unit/service/controller/repository tests와 MVC boundary regression

제외:

- Starter properties, ingest envelope, heartbeat payload에 `applicationInstanceId` UUID field 추가
- "Add instance" registration flow, user-entered portal UUID, heartbeat-only catalog row 생성
- instance-level lifecycle state, health score, availability score, host status, process down 확정 field
- `metricData`와 `starterConnection`을 합친 `instanceHealth`, `connectedAndHealthy`, `hostStatus` 같은 단일 판단
- endpoint priority ranking, endpoint `rank`, `confidence`, `score`, `recommendedAction` 신규 계산
- `localDisplayOrder`를 root cause 순위나 action priority로 표현하는 것
- raw accepted bucket JSON, raw histogram JSON string, raw `endpoints_json`, unbounded map, raw path, query string, query key/value, trace id, per-request sample 노출
- endpoint p95/p99, endpoint percentile rollup, endpoint p99 alert 기준, histogram-derived percentile scalar
- dashboard snapshot persistence/query, `dashboard_snapshots` repository runtime dependency, operational event history, snapshot detail, instance snapshot trend
- 24h/7d/14d trend, raw instance timeseries table, endpoint timeseries table, materialized view, Redis/outbox
- dashboard UI/static asset, Epic 6 Instance Detail UI 구현
- unrelated package 재배치, ingest/heartbeat acceptance behavior 변경, new migration/table/schema 변경

## Acceptance Criteria

1. `GET /api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/evidence` endpoint를 제공한다.
2. `{projectId}`, `{applicationId}`, `{instanceId}`는 모두 UUID path variable이다. `instanceId`는 `application_instances.id`다.
3. service는 `projectId + applicationId + instanceId` membership을 검증한다.
4. project 없음, application 없음, project/application mismatch, instance 없음, instance/application mismatch는 모두 `404`로 매핑한다.
5. API는 `instanceName`만으로 project/application membership을 우회해 evidence를 조회하지 않는다.
6. Starter ingest/heartbeat request, starter config, accepted bucket write path는 UUID를 요구하거나 저장하도록 변경하지 않는다.
7. Heartbeat 성공만으로 `applications` 또는 `application_instances` catalog row를 만들지 않는다. 첫 accepted bucket ingest가 계속 catalog upsert source다.
8. `200` response는 top-level field `generatedAt`, `application`, `instance`, `metricData`, `starterConnection`, `starterPercentiles`, `histogramDistribution`, `resourceHints`, `applicationTriageContribution`, `endpointEvidence`, `links`를 포함한다.
9. Response는 bounded evidence bundle이며 raw bucket explorer나 raw endpoint explorer가 아니다.
10. `application`은 `projectId`, `applicationId`, `name`, `environment`, `links.dashboard`를 포함한다.
11. `instance`는 `instanceId`, `instanceName`, `firstSeenAt`, `lastSeenAt`을 포함한다.
12. `links.self`는 evidence API path이고, `links.dashboard`는 application dashboard path다.
13. `links.snapshotTrend`는 Story 5.7 endpoint가 아직 없으면 `null` 또는 omitted를 허용한다. 실제 snapshot trend endpoint를 Story 5.6에서 구현하지 않는다.
14. 현재 dashboard read model에는 instance evidence 진입 surface가 없으므로 Story 5.6은 Application Dashboard response에 bounded `instanceSummary` 또는 동등 entry block을 추가한다.
15. Dashboard instance entry item은 `instanceId`, `instanceName`, nullable `lastSeenAt`, `links.evidence`를 포함하고 최대 50개다.
16. Dashboard instance entry는 selected instance evidence API로 이동하기 위한 navigation hint다. instance health/state/priority, endpoint evidence, p95/p99 series를 dashboard entry 안에서 새로 만들지 않는다.
17. `generatedAt`은 response 생성 시각이다.
18. current window end는 query instant를 UTC 30초 bucket boundary로 floor한 `evaluationAt`이다.
19. current window는 `evaluationAt` 기준 최근 15분이며, membership은 bucket `bucketEndUtc > windowStart` 그리고 `bucketEndUtc <= windowEnd`다.
20. latest accepted bucket `endUtc`는 freshness source일 뿐 current window end를 대체하지 않는다.
21. `metricData.statusSource`는 `accepted_bucket`이다.
22. `metricData.window`는 `current_15m`, `startUtc`, `endUtc`, `bucketDurationSeconds=30`을 포함한다.
23. `metricData.lastAcceptedBucketAt`은 selected `applicationInstanceId`의 latest accepted bucket endUtc 기준이다.
24. `metricData.freshnessLabel`은 accepted bucket freshness evaluator 결과를 bounded string으로 옮긴다. 허용 후보는 `waiting_first_data`, `current`, `stale_candidate`, `down_candidate`다.
25. `metricData`는 selected instance current window의 `requestCount`, `errorCount`, nullable `errorRate`, `sampleReadiness`, optional `reason`을 담을 수 있다.
26. `requestCount=0`이면 `errorRate=null`이다. 0% 오류율처럼 오해될 값을 만들지 않는다.
27. `metricData`는 lifecycle state, health score, availability score, p95/p99, endpoint priority, recommended action을 포함하지 않는다.
28. `starterConnection.statusSource`는 `starter_heartbeat`다.
29. `starterConnection` lookup은 selected catalog instance의 `instanceName`과 application name/environment 문자열 identity로 `StarterHeartbeatTelemetryRepository.findByIdentity(projectId, name, environment, instanceName)` 또는 동등 method를 사용한다.
30. `starterConnection`은 `lastHeartbeatAt`, `lastHeartbeatStatus`, `freshnessLabel`, `connectionMeaning`, `stateImpact=none`을 포함한다.
31. recent heartbeat와 stale/missing accepted bucket 조합은 "starter는 연결됐지만 metric data는 대기/idle" 계열 reason/copy로 표현할 수 있다. host application down을 확정하지 않는다.
32. missing/stale heartbeat와 missing/stale accepted bucket 조합은 telemetry unreachable 또는 unknown 계열로 표현할 수 있다. host application down 원인을 확정하지 않는다.
33. Heartbeat 성공은 metric freshness, lifecycle state, recovery source, p95/p99, endpoint evidence source로 사용하지 않는다.
34. `starterPercentiles.source`는 public read model 의미상 `starter_canonical_percentile`이다. Persisted JSON 검증은 기존 ingest 계약의 `summary.localPercentiles.source=starter_local`을 확인한다.
35. `starterPercentiles.scope`는 `instance`, `window`는 `current_15m`, `bucketDurationSeconds=30`, `maxPointCount=30`, `displayPolicy=source_scoped_series`다.
36. `starterPercentiles.aggregatePolicy`는 `no_average_no_max_no_merge_no_histogram_recalculation` 또는 동등한 문구다.
37. `starterPercentiles.points`는 selected `applicationInstanceId`의 current 15분 안 30초 bucket p95/p99 point만 시간 오름차순으로 반환한다.
38. `starterPercentiles.points`는 최대 30개다.
39. 각 percentile point는 `bucketStartUtc`, `bucketEndUtc`, `requestCount`, `p95Ms`, `p99Ms`를 포함한다.
40. Percentile source는 persisted `local_percentiles_json`의 `scope=instance_bucket`, `source=starter_local`, `mergeable=false`, matching bucket boundary, positive request count, non-negative `p95Ms`, `p99Ms >= p95Ms`를 만족하는 row만 사용한다.
41. 여러 p95/p99 point를 평균, 최댓값, 합산, 병합해 새 percentile scalar를 만들지 않는다.
42. histogram bucket으로 p95/p99를 재계산하지 않는다.
43. point가 없는 bucket은 보간하지 않고 누락으로 둔다.
44. 표시 가능한 point가 없으면 `starterPercentiles.status=missing` 또는 `insufficient`와 reason을 반환하고 `points=[]`다.
45. `histogramDistribution.source`는 `histogram_bucket_distribution`이며 selected instance summary duration bucket distribution evidence만 담는다.
46. `histogramDistribution.scope`는 `selected_instance_current_15m` 또는 동등하게 selected instance/current window 의미가 드러나는 string이다.
47. `histogramDistribution`은 `status`, nullable `reason`, `totalCount`, `buckets[]`를 포함한다.
48. `histogramDistribution.buckets[]` item은 `leMs`, `count`만 포함한다.
49. current window 안 selected instance duration bucket rows의 boundary set이 모두 같을 때만 cumulative count를 boundary별 합산한다.
50. duration bucket JSON invalid 또는 count validation 실패는 `status=insufficient`로 수렴한다.
51. histogram boundary mismatch는 `status=unavailable`, `reason=histogram_boundary_mismatch`, `buckets=[]`로 수렴한다.
52. `histogramDistribution`에는 p95/p99, avg/max latency, delta, regression, confidence, rule 판단을 포함하지 않는다.
53. `resourceHints.source`는 `accepted_bucket_latest_sample` 또는 동등하게 latest accepted bucket sample 의미가 드러나는 string이다.
54. `resourceHints`는 selected instance current window의 latest sample 기반 `cpuUsageRatio`, `heapUsedRatio`, `datasourcePoolUsageRatio`, `bucketEndUtc`, `status`, `reason`만 담는다.
55. `resourceHints`는 max/avg/sustained pressure 계산, degraded/down 판단, instance health score, root cause 확정을 만들지 않는다.
56. `applicationTriageContribution`은 existing application-level triage evidence를 selected instance에서 관찰할 수 있는지 나타내는 bridge field다.
57. `applicationTriageContribution`은 `status`, `contributed`, `relatedRuleIds`, `reason`을 포함한다.
58. `relatedRuleIds`는 application triage cards 또는 existing triage service 결과에서 복사한 rule id만 포함한다. 새 rule id를 만들지 않는다.
59. selected instance evidence가 application triage dimension과 연결되지 않으면 `contributed=false`와 bounded reason을 반환한다.
60. application triage card가 없으면 `contributed=false`, `relatedRuleIds=[]`, `reason=no_application_triage_cards` 또는 동등 reason이다.
61. selected instance evidence가 부족하면 `status=insufficient`와 reason을 반환한다. 부족한 evidence를 host/app issue로 확정하지 않는다.
62. `endpointEvidence.source`는 `accepted_metric_buckets.endpoints_json`이다.
63. `endpointEvidence.scope`는 `instance_current_15m`이다.
64. `endpointEvidence.status`는 `available`, `missing`, `insufficient`, `suppressed`, `unavailable` 중 bounded value로 둔다.
65. application metric freshness가 `current`가 아니면 `endpointEvidence.status=suppressed`, `reason=application_freshness_not_current`, `items=[]`다.
66. endpoint evidence 조회는 selected `applicationInstanceId`와 current 15분 `(start, end]` bucket window를 기준으로 한다.
67. application endpoint aggregate는 application current 15분 `(start, end]` bucket의 `endpoints_json`을 `endpointKey = method + " " + route`로 합산한 값이다.
68. selected instance endpoint aggregate는 selected `applicationInstanceId` current 15분 `(start, end]` bucket의 `endpoints_json`을 같은 `endpointKey`로 합산한 값이다.
69. `endpointEvidence.items`는 최대 5개다.
70. `selectionPolicy`는 `application_priority_presence_then_triage_then_instance_request_count`다.
71. `displayOrderingPolicy`는 `selected_instance_signal_then_application_priority_reference`다.
72. selection priority는 application dashboard `endpointPriority` 상위 endpoint 중 selected instance presence 확인 대상, application triage contribution 연결 endpoint, selected instance current window request count 상위 endpoint 순서다.
73. Story 5.6은 dashboard endpoint priority를 보조하기 위해 Story 5.5 `EndpointPriorityService` 결과의 `endpointKey`, `rank`, `ruleIds`를 참조할 수 있다. 이 값을 다시 계산하거나 새 priority로 승격하지 않는다.
74. `endpointEvidence.items[].relatedApplicationPriorityRank`는 Story 5.5 `endpointPriority.rank`를 복사한 참조 field일 뿐이다.
75. `endpointEvidence.items[].localDisplayOrder`는 Instance Detail evidence 목록 표시 순서일 뿐 endpoint priority, root cause 순위, action priority가 아니다.
76. display order는 `presenceOnSelectedInstance=observed`, `instanceErrorShare desc null last`, `instanceErrorRate desc null last`, `instanceRequestCount desc`, `relatedApplicationPriorityRank asc null last`, `endpointKey asc` 순서를 따른다.
77. Application Dashboard `endpointPriority` 상위 endpoint는 selected instance에서 관찰되지 않았더라도 `presenceOnSelectedInstance=not_observed` item으로 포함할 수 있다.
78. `presenceOnSelectedInstance` enum은 `observed`, `not_observed`, `insufficient`로 제한한다.
79. `presenceOnSelectedInstance=observed`는 selected instance aggregate의 `requestCount > 0`인 경우다.
80. `presenceOnSelectedInstance=not_observed`는 application endpoint aggregate는 있는데 selected instance aggregate가 없거나 `requestCount=0`인 경우다.
81. `presenceOnSelectedInstance=insufficient`는 endpoint JSON parsing, route safety, count validation, histogram boundary validation 중 하나가 실패해 selected instance evidence를 신뢰할 수 없는 경우다.
82. endpoint item 허용 field는 `method`, `route`, `endpointKey`, `presenceOnSelectedInstance`, `instanceRequestCount`, `instanceErrorCount`, `instanceErrorRate`, `applicationEndpointRequestCount`, `applicationEndpointErrorCount`, `applicationEndpointErrorRate`, `instanceRequestShare`, `instanceErrorShare`, `durationBuckets`, `bucketDistributionSource`, `relatedApplicationPriorityRank`, `localDisplayOrder`, `relatedRuleIds`, `status`, `reason`으로 제한한다.
83. `instanceErrorRate = instanceErrorCount / instanceRequestCount`이며 denominator가 0이면 `null`이다.
84. `applicationEndpointErrorRate = applicationEndpointErrorCount / applicationEndpointRequestCount`이며 denominator가 0이면 `null`이다.
85. `instanceRequestShare = instanceRequestCount / applicationEndpointRequestCount`이며 denominator가 0이거나 application aggregate가 없으면 `null`이다.
86. `instanceErrorShare = instanceErrorCount / applicationEndpointErrorCount`이며 denominator가 0이거나 application aggregate가 없으면 `null`이다.
87. `durationBuckets`는 selected instance aggregate의 cumulative bucket distribution이며, 같은 endpoint/window 안 boundary set이 일치할 때만 boundary별 count를 합산한다.
88. endpoint histogram boundary mismatch는 item `status=unavailable`, `reason=histogram_boundary_mismatch`로 두고 p95/p99나 latency score를 만들지 않는다.
89. `relatedApplicationPriorityRank`와 `relatedRuleIds`는 Story 5.5 `endpointPriority` item에서 endpointKey가 일치할 때만 복사한다.
90. MVP endpoint evidence reason code는 `application_priority_endpoint_observed_on_selected_instance`, `application_priority_endpoint_not_seen_on_selected_instance`, `selected_instance_endpoint_observed`, `endpoint_evidence_insufficient`, `histogram_boundary_mismatch`, `application_freshness_not_current`로 제한한다.
91. `not_observed`는 "문제 없음"이 아니라 "선택한 instance의 current window에서 해당 endpoint evidence가 없음"이라는 뜻이다.
92. selected instance에 application priority endpoint가 없으면 다른 instance에서 나온 신호일 수 있음을 evidence로 보여줄 수 있지만 load balancer 문제, root cause, instance fault를 확정하지 않는다.
93. UI/controller/repository는 instance state, endpoint priority, rule confidence, recommended action을 계산하지 않는다.
94. Controller는 path/status mapping과 service 위임만 담당한다. repository, ObjectMapper endpoint parsing, percentile/histogram merge, endpoint evidence selection을 직접 수행하지 않는다.
95. Repository는 read-only neutral projection만 제공한다. state/rule/priority/confidence/action/p95/p99를 계산하지 않는다.
96. JPA entity는 API response, controller response DTO, service external return model로 직접 노출하지 않는다.
97. 새 공개 클래스, 공개 메서드, 핵심 helper는 AGENTS.md 지침에 따라 한국어 Javadoc/docstring으로 역할과 사용 맥락을 설명한다.
98. Focused tests가 identity/membership, response shape, current window, source axis separation, starter percentile series, histogram/resource hints, application triage contribution, endpoint evidence selection/display order/share/status, controller serialization, repository projections, MVC boundary를 검증한다.
99. `./gradlew :observability-portal:test`와 `git diff --check`가 통과한다.

## Tasks/Subtasks

- [x] Instance evidence read model shape 확정 (AC: 8~16, 21~61, 62~92, 96~97)
  - [x] `domain.instance.model` 또는 기존 패턴에 맞는 feature package에 `InstanceEvidenceReadModel` typed record/class를 추가한다.
  - [x] top-level field를 source of truth와 동일하게 유지한다: `generatedAt`, `application`, `instance`, `metricData`, `starterConnection`, `starterPercentiles`, `histogramDistribution`, `resourceHints`, `applicationTriageContribution`, `endpointEvidence`, `links`.
  - [x] `metricData`, `starterConnection`, `starterPercentiles`, `histogramDistribution`, `resourceHints`, `applicationTriageContribution`, `endpointEvidence`를 각각 별도 typed nested record로 둔다.
  - [x] constructors에서 required field, bounded enum/string, non-negative count, ratio range, max item count, defensive copy를 검증한다.
  - [x] public response model에 state/health/score/raw JSON/endpoint p95/p99/endpoint priority 신규 field가 없도록 shape를 고정한다.

- [x] Controller/API boundary 구현 (AC: 1~5, 8, 93~97)
  - [x] `InstanceEvidenceController` 후보를 추가한다. 위치는 `domain.instance.controller`를 우선하되, 기존 dashboard feature로 묶는 것이 더 일관적이면 `domain.dashboard.controller`도 허용한다.
  - [x] route는 `GET /api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/evidence`로 고정한다.
  - [x] controller는 `UUID projectId`, `UUID applicationId`, `UUID instanceId` path variable만 service에 전달한다.
  - [x] service가 empty를 반환하면 404로 매핑한다.
  - [x] controller에서 repository, ObjectMapper, window 계산, heartbeat freshness 판단, endpoint merge/selection을 호출하지 않는다.

- [x] Catalog membership와 entry link 보강 (AC: 2~7, 10~16, 29)
  - [x] `ApplicationRepository.findByIdAndProjectId`를 재사용해 project/application membership을 확인한다.
  - [x] `ApplicationInstanceRepository`에 `findByIdAndApplicationId(UUID instanceId, UUID applicationId)` 또는 동등 read-only lookup을 추가한다.
  - [x] `ApplicationInstanceEntity`에 필요하면 `applicationId()`와 `instanceName()` accessor를 추가한다. Entity를 response로 직접 반환하지 않는다.
  - [x] Application Dashboard 또는 instance summary 진입 surface가 있다면 `instanceId`, `instanceName`, `links.evidence`를 함께 반환하도록 보강한다.
  - [x] 현재 dashboard에 instance summary surface가 없다면 Story 5.6 범위 안에서 최소 bounded entry list를 추가할지, 아니면 Epic 6 UI story로 넘길지 구현 중 source 문서와 결정 문서를 기준으로 보수적으로 판단한다. 단 어떤 경우에도 `instanceName`만으로 evidence path를 만들지 않는다.

- [x] Instance-scope bucket repository projections 추가 (AC: 18~27, 34~55, 62~68, 83~88, 95)
  - [x] `MetricBucketRepository`에 selected `applicationInstanceId` scope latest bucket endUtc 조회 method를 추가한다.
  - [x] selected instance current window request/error aggregate 조회 method를 추가한다.
  - [x] selected instance current window local percentile evidence rows 조회 method를 추가한다.
  - [x] selected instance current window summary duration bucket evidence rows 조회 method를 추가한다.
  - [x] selected instance current window latest runtime ratio row 조회 method를 추가한다.
  - [x] selected instance current window endpoint evidence rows 조회 method를 추가한다.
  - [x] application current window endpoint aggregate는 기존 `findEndpointEvidenceRowsByApplicationId`를 재사용한다.
  - [x] query 조건은 `application_instance_id`, `bucket_end_utc > windowStart`, `bucket_end_utc <= windowEnd`, 관련 JSON/range field만 사용한다.
  - [x] projection model은 bucket boundary와 JSON parsing에 필요한 bounded source field만 담는다.
  - [x] repository/JPA method Javadoc에 state, rule, priority, confidence, recommended action, p95/p99를 계산하지 않는다고 명시한다.

- [x] InstanceEvidenceReadModelService orchestration 구현 (AC: 17~33, 56~61, 93~97)
  - [x] `Clock`에서 query instant를 얻고 `TimeBucketWindowCalculator.bucketContaining(queryAt).startUtc()`로 `evaluationAt`을 floor한다.
  - [x] `TimeBucketWindowCalculator.dashboardWindowEndingAt(evaluationAt)`으로 current 15분과 baseline 15분을 계산한다. Story 5.6 response는 current window만 노출하되, app triage/endpoint priority seed가 baseline을 필요로 하면 service 내부에서만 사용한다.
  - [x] selected instance latest bucket endUtc로 `AcceptedBucketFreshnessEvaluator.evaluateAt(evaluationAt, latestInstanceBucketEnd)`를 호출한다.
  - [x] selected instance current window aggregate로 `metricData`를 만든다.
  - [x] starter heartbeat는 selected catalog instanceName으로 `StarterHeartbeatTelemetryRepository.findByIdentity(projectId, application.name, application.environment, instance.instanceName)`를 호출한다.
  - [x] starter connection mapping은 DashboardReadModelService와 같은 90초 recency/meaning/stateImpact semantics를 유지한다. 중복이 커지면 shared mapper/helper로 추출하되 기존 dashboard behavior를 바꾸지 않는다.
  - [x] application triage contribution은 existing `TriageSummaryService` 결과 또는 dashboard orchestration과 같은 seed를 재사용한다. 새 rule/confidence/action을 만들지 않는다.
  - [x] dashboard endpoint priority seed가 필요하면 기존 `EndpointPriorityService`를 같은 window/freshness/sample guard로 호출하거나, DashboardReadModelService의 endpoint priority orchestration을 shared helper로 추출한다. 새 priority algorithm을 만들지 않는다.

- [x] Starter percentile series 구현 (AC: 34~44)
  - [x] selected instance current window local percentile rows를 `bucketEndUtc` 오름차순으로 검증/변환한다.
  - [x] persisted JSON의 `scope=instance_bucket`, `source=starter_local`, `mergeable=false`, matching boundary, positive request count, p95/p99 validation을 적용한다.
  - [x] public response source는 `starter_canonical_percentile`로 의미를 드러낸다.
  - [x] 최대 30개 point만 반환하고 missing bucket을 보간하지 않는다.
  - [x] invalid rows만 있으면 `status=insufficient`, rows 자체가 없으면 `status=missing`으로 reason을 제공한다.

- [x] Instance histogram/resource hints 구현 (AC: 45~55)
  - [x] selected instance duration bucket rows를 current window 안에서 boundary set match일 때만 cumulative count 합산한다.
  - [x] invalid JSON/count는 `insufficient`, boundary mismatch는 `unavailable/histogram_boundary_mismatch`로 수렴한다.
  - [x] latest runtime ratio sample은 selected instance current window 안 최신 row 1개만 사용한다.
  - [x] resource hint 값은 nullable latest sample로만 노출하고 max/avg/state/root cause 판단을 만들지 않는다.

- [x] Endpoint evidence subset 구현 (AC: 62~92)
  - [x] Story 5.5 `EndpointPriorityService`의 parsing, route safety, count validation, histogram merge 규칙을 재사용하거나 공유 helper로 추출한다.
  - [x] application current window endpoint aggregate와 selected instance current window endpoint aggregate를 같은 `endpointKey = method + " " + route` 기준으로 만든다.
  - [x] application freshness가 current가 아니면 endpoint evidence를 `suppressed/application_freshness_not_current/items=[]`로 반환한다.
  - [x] selection policy 순서대로 최대 5개 endpoint를 고른다.
  - [x] presence/status/share/error rate/duration bucket fields를 bounded item으로 변환한다.
  - [x] `relatedApplicationPriorityRank`와 `relatedRuleIds`는 existing dashboard endpoint priority item에서 endpointKey가 일치할 때만 복사한다.
  - [x] `localDisplayOrder`는 display ordering 결과만 나타내고 priority/rank/confidence/action field를 추가하지 않는다.
  - [x] `not_observed` copy/reason은 selected instance current window에 evidence가 없다는 뜻으로만 표현한다.
  - [x] invalid JSON, unsafe route, malformed count, histogram boundary mismatch는 raw detail 노출 없이 bounded status/reason으로 수렴한다.

- [x] Scope guard와 architecture guard 확인 (AC: 6~7, 27, 33, 41~43, 52, 55, 61, 89~97)
  - [x] starter ingest/heartbeat request/response schema를 변경하지 않는다.
  - [x] heartbeat repository/service가 catalog row를 생성하지 않음을 유지한다.
  - [x] `dashboard_snapshots`, snapshot repository, history/trend service dependency를 추가하지 않는다.
  - [x] raw accepted bucket JSON, raw endpoint JSON, raw path/query/trace/per-request sample field가 response에 없는지 controller/model tests로 확인한다.
  - [x] no `application`, `port`, `adapter` package guard를 유지한다.
  - [x] JPA entity를 response model로 반환하지 않는다.

- [x] Focused tests와 regression 실행 (AC: 98~99)
  - [x] `InstanceEvidenceReadModelShapeTest` 또는 동등 model test를 추가한다.
  - [x] `InstanceEvidenceReadModelServiceTest`로 membership, current window, metric/starter axis separation, percentile series, histogram/resource, triage contribution, endpoint evidence selection을 검증한다.
  - [x] `InstanceEvidenceControllerTest`로 serialization, 404 mapping, service delegation, forbidden raw fields absence를 검증한다.
  - [x] `MetricBucketRepositoryIntegrationTest` 또는 전용 integration test로 instance-scope projections와 `(start, end]` window를 검증한다.
  - [x] `StarterHeartbeatTelemetryRepository` focused test가 필요하면 exact identity lookup을 검증한다.
  - [x] 기존 `EndpointPriorityServiceTest`, `DashboardReadModelServiceTest`, `DashboardControllerTest`가 helper extraction 후에도 통과하는지 확인한다.
  - [x] `MvcLayerBoundaryTest`를 실행한다.
  - [x] `./gradlew :observability-portal:test`와 `git diff --check`를 실행한다.

## Dev Notes

### Contract Priority

- 최우선 source는 `implementation-artifacts/spec-story-5-6-instance-evidence-contract-decisions.md`다.
- 이 story와 기존 문서/코드가 충돌하면 Story 5.6 결정 문서를 우선한다.
- `read-model-contract.md`의 Instance Detail starter percentile 후보와 snapshot trend 후보는 보조 context다. Snapshot/trend는 Story 5.6 구현 범위가 아니다.
- Story 5.5 endpoint priority는 canonical endpoint ranking source다. Story 5.6은 그 결과를 selected instance evidence와 연결할 뿐 새 ranking을 만들지 않는다.

### Suggested API Shape

```http
GET /api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/evidence
Accept: application/json
```

```json
{
  "generatedAt": "2026-05-26T06:10:35Z",
  "application": {
    "projectId": "00000000-0000-0000-0000-000000005201",
    "applicationId": "00000000-0000-0000-0000-000000005211",
    "name": "orders-api",
    "environment": "prod",
    "links": {
      "dashboard": "/api/projects/{projectId}/applications/{applicationId}/dashboard"
    }
  },
  "instance": {
    "instanceId": "00000000-0000-0000-0000-000000005221",
    "instanceName": "pod-a",
    "firstSeenAt": "2026-05-26T05:00:05Z",
    "lastSeenAt": "2026-05-26T06:10:05Z"
  },
  "metricData": {
    "statusSource": "accepted_bucket",
    "window": {
      "name": "current_15m",
      "startUtc": "2026-05-26T05:55:30Z",
      "endUtc": "2026-05-26T06:10:30Z",
      "bucketDurationSeconds": 30
    },
    "lastAcceptedBucketAt": "2026-05-26T06:10:00Z",
    "freshnessLabel": "current",
    "sampleReadiness": "sufficient",
    "requestCount": 1200,
    "errorCount": 36,
    "errorRate": 0.03,
    "reason": null
  },
  "starterConnection": {
    "statusSource": "starter_heartbeat",
    "lastHeartbeatAt": "2026-05-26T06:10:20Z",
    "lastHeartbeatStatus": "received",
    "freshnessLabel": "recent",
    "connectionMeaning": "starter_connected",
    "stateImpact": "none"
  },
  "starterPercentiles": {
    "source": "starter_canonical_percentile",
    "scope": "instance",
    "window": "current_15m",
    "bucketDurationSeconds": 30,
    "maxPointCount": 30,
    "displayPolicy": "source_scoped_series",
    "aggregatePolicy": "no_average_no_max_no_merge_no_histogram_recalculation",
    "status": "available",
    "reason": null,
    "points": [
      {
        "bucketStartUtc": "2026-05-26T06:09:30Z",
        "bucketEndUtc": "2026-05-26T06:10:00Z",
        "requestCount": 120,
        "p95Ms": 480,
        "p99Ms": 960
      }
    ]
  },
  "histogramDistribution": {
    "source": "histogram_bucket_distribution",
    "scope": "selected_instance_current_15m",
    "status": "available",
    "reason": null,
    "totalCount": 1200,
    "buckets": [
      { "leMs": 500, "count": 820 },
      { "leMs": 1000, "count": 1200 }
    ]
  },
  "resourceHints": {
    "source": "accepted_bucket_latest_sample",
    "status": "available",
    "reason": null,
    "bucketEndUtc": "2026-05-26T06:10:00Z",
    "cpuUsageRatio": 0.41,
    "heapUsedRatio": 0.62,
    "datasourcePoolUsageRatio": 0.37
  },
  "applicationTriageContribution": {
    "status": "observed",
    "contributed": true,
    "relatedRuleIds": ["global_error_spike"],
    "reason": "selected_instance_has_error_evidence_for_application_triage"
  },
  "endpointEvidence": {
    "source": "accepted_metric_buckets.endpoints_json",
    "scope": "instance_current_15m",
    "selectionPolicy": "application_priority_presence_then_triage_then_instance_request_count",
    "displayOrderingPolicy": "selected_instance_signal_then_application_priority_reference",
    "status": "available",
    "reason": null,
    "items": [
      {
        "method": "POST",
        "route": "/orders",
        "endpointKey": "POST /orders",
        "presenceOnSelectedInstance": "observed",
        "instanceRequestCount": 500,
        "instanceErrorCount": 40,
        "instanceErrorRate": 0.08,
        "applicationEndpointRequestCount": 1200,
        "applicationEndpointErrorCount": 80,
        "applicationEndpointErrorRate": 0.066667,
        "instanceRequestShare": 0.416667,
        "instanceErrorShare": 0.5,
        "durationBuckets": [
          { "leMs": 500, "count": 350 },
          { "leMs": 1000, "count": 500 }
        ],
        "bucketDistributionSource": "histogram_bucket_distribution",
        "relatedApplicationPriorityRank": 1,
        "localDisplayOrder": 1,
        "relatedRuleIds": ["endpoint_error_spike"],
        "status": "available",
        "reason": "application_priority_endpoint_observed_on_selected_instance"
      }
    ]
  },
  "links": {
    "self": "/api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/evidence",
    "dashboard": "/api/projects/{projectId}/applications/{applicationId}/dashboard",
    "snapshotTrend": null
  }
}
```

Shape는 implementation 중 record/class 이름을 조정할 수 있다. 단 top-level field, source axis, current 15분 window, bounded field, raw data 금지, no new priority/rank/confidence/action 계약은 유지해야 한다.

### Current Code State

- `ApplicationDashboardReadModel`은 Story 5.5 기준으로 typed `endpointPriority`를 포함한다. 현재 instance evidence response model은 없다.
- `DashboardController`는 `/api/projects/{projectId}/applications/{applicationId}/dashboard`만 제공한다. Story 5.6은 별도 instance evidence endpoint가 필요하다.
- `DashboardReadModelService`는 application scope current/baseline window, latest bucket freshness, aggregate, local percentile latest per instance, histogram distribution, triage, endpoint priority, heartbeat를 조립한다.
- `DashboardReadModelService`의 current/baseline window end는 `clock.instant()`를 UTC 30초 boundary로 floor한 `evaluationAt`이다. Story 5.6도 같은 패턴을 사용한다.
- `ApplicationRepository.findByIdAndProjectId`는 project/application membership 확인에 이미 있다.
- `ApplicationInstanceRepository`는 현재 `findByApplicationIdAndInstanceName`만 있다. Story 5.6은 UUID path identity 검증을 위해 `findByIdAndApplicationId` 또는 동등 method가 필요하다.
- `ApplicationInstanceEntity`는 현재 `id()`, `firstSeenAt()`, `lastSeenAt()` accessor만 노출한다. response와 heartbeat lookup에 필요하면 `applicationId()`, `instanceName()` accessor를 추가한다.
- `MetricBucketRepository`는 application scope latest bucket, current aggregate, local percentile rows, histogram rows, endpoint rows, runtime ratio latest sample을 제공한다. Story 5.6은 selected `applicationInstanceId` scope projection을 추가해야 한다.
- `AcceptedMetricBucketJpaRepository`의 existing read path는 `(start, end]` bucket `bucketEndUtc` semantics와 neutral projection 패턴을 따른다.
- `LocalPercentileEvidenceRow`는 `applicationInstanceId`, `instanceName`, bucket boundary, raw local percentile JSON을 담는다. Instance evidence series는 이 패턴을 selected instance scope로 좁혀 재사용할 수 있다.
- `EndpointEvidenceRow`는 application id, bucket boundary, `endpointsJson`만 담는 neutral projection이다. Instance evidence가 selected instance endpoint evidence를 만들려면 applicationInstanceId scope variant가 필요하다.
- `EndpointPriorityService`에는 endpoint JSON parsing, safe route validation, duration bucket validation, boundary merge, endpoint priority rule/ranking이 들어 있다. Story 5.6은 parsing/merge/safe route logic을 재사용하되 ranking/confidence/action 계산은 새로 만들지 않는다.
- `TriageSummaryService`는 app-level `global_error_spike`, `global_latency_spike`, saturation hint, degraded input을 계산한다. Instance evidence는 이 service의 existing rule id를 참조할 수 있지만 새 triage rule을 만들지 않는다.
- `StarterHeartbeatTelemetryRepository.findByIdentity(projectId, applicationName, environment, instanceName)`가 exact instance heartbeat lookup에 이미 있다.
- `StarterHeartbeatTelemetryRepository`는 accepted metric bucket이나 application catalog를 만들지 않고 heartbeat 전용 table만 갱신한다. Story 5.6도 이 경계를 유지한다.
- `MvcLayerBoundaryTest`는 controller/repository/dto dependency guard와 no `application`/`port`/`adapter` package guard를 이미 포함한다.

### Existing Code / Documents To Reuse

- `_bmad/custom/project-context.md`
  - Traditional MVC + Service/Repository Layering, feature-first MVC, Spring Data JPA/Flyway 기준을 따른다.
- `implementation-artifacts/spec-story-5-6-instance-evidence-contract-decisions.md`
  - Story 5.6 source of truth다.
- `planning-artifacts/epics.md`
  - Epic 5는 server-computed dashboard read model/API epic이며 Instance Detail은 application 판단을 대체하지 않는 evidence drill-down이다.
- `planning-artifacts/sprint-plan.md`
  - Epic 5/6 dashboard alignment 흐름은 Project -> Application -> Dashboard -> Instance Evidence -> Instance Snapshot Trend -> Snapshot/History다.
- `planning-artifacts/contracts/read-model-contract.md`
  - accepted bucket/heartbeat source axis 분리, instance starter percentile candidate, snapshot/trend handoff 경계를 따른다.
- `planning-artifacts/api-surface.md`
  - instance evidence API candidate path와 boundary를 따른다.
- `planning-artifacts/contracts/time-buckets.md`
  - current 15분, baseline 15분, UTC 30초 bucket, `(start, end]`, stale 90초, down 180초 기준을 따른다.
- `planning-artifacts/contracts/state-semantics.md`
  - accepted bucket metric axis와 starter heartbeat connection axis를 분리한다.
- `planning-artifacts/contracts/ingest-envelope.md`
  - `summary.localPercentiles`, `endpoints`, raw path/query 금지, endpoint duration bucket 의미를 따른다.
- `planning-artifacts/contracts/histogram-merge.md`
  - histogram bucket은 distribution display/evidence source이며 p95/p99 계산 source가 아니다.
- `planning-artifacts/contracts/route-attribution-policy.md`
  - safe route contract와 raw path/query 금지 경계를 따른다.
- `planning-artifacts/contracts/insight-rules.md`
  - endpoint-level rule은 endpoint별 p95/p99가 아니라 duration bucket distribution, request count, error rate, freshness evidence를 사용한다.
- `planning-artifacts/stories/5-3-source-scoped-percentile-and-histogram-distribution-read-model.md`
  - current window percentile latest point와 histogram distribution evidence-only guard를 재사용한다. Full percentile series는 Story 5.6 범위로 남겨져 있었다.
- `planning-artifacts/stories/5-4-triage-summary-and-zero-insight-recovery-mapping.md`
  - typed triage shape, bounded evidence, no raw field, no p95/p99 recomputation, source axis guard를 재사용한다.
- `planning-artifacts/stories/5-5-endpoint-priority-read-model.md`
  - endpoint priority canonical source와 endpoint evidence parse/merge/ranking guard를 재사용한다.
- `observability-portal/src/main/java/com/observation/portal/common/time/TimeBucketWindowCalculator.java`
  - UTC 30초 bucket duration과 15분 dashboard window duration을 재사용한다.
- `observability-portal/src/main/java/com/observation/portal/common/time/AcceptedBucketFreshnessEvaluator.java`
  - selected instance latest accepted bucket endUtc와 evaluationAt으로 freshness candidate를 계산한다.
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ApplicationRepository.java`
  - project/application membership lookup을 재사용한다.
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ApplicationInstanceRepository.java`
  - instance UUID membership lookup을 추가할 위치다.
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java`
  - selected instance projections를 추가할 repository facade다.
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/EndpointPriorityService.java`
  - endpoint parse/safe route/histogram boundary merge logic의 reuse/extraction source다.
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/TriageSummaryService.java`
  - application triage contribution의 existing rule id source다.
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/repository/StarterHeartbeatTelemetryRepository.java`
  - selected instance heartbeat exact identity lookup source다.
- `observability-portal/src/test/java/com/observation/portal/domain/bucket/repository/MetricBucketRepositoryIntegrationTest.java`
  - PostgreSQL JSONB/Flyway/Testcontainers repository projection test 패턴을 재사용한다.
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/service/EndpointPriorityServiceTest.java`
  - endpoint parsing/route safety/boundary merge regression source다.
- `observability-portal/src/test/java/com/observation/portal/architecture/MvcLayerBoundaryTest.java`
  - controller/repository/dto dependency guard와 no hexagonal package guard를 유지한다.

## Previous Story Intelligence

- Story 5.3은 dashboard `sourceScopedPercentiles`를 current window instance별 latest point로 제한했고 full series는 Story 5.6으로 남겼다. Story 5.6은 selected instance full current 15분 series를 제공하되 p95/p99를 합치지 않는다.
- Story 5.3 histogram distribution은 evidence-only이며 p95/p99, delta, rule, confidence를 만들지 않는다. Story 5.6 instance histogram도 같은 guard를 유지한다.
- Story 5.4는 app-level triage card와 degraded input을 service layer에서 계산했다. Story 5.6은 application triage contribution을 표시할 수 있지만 selected instance state/rule을 새로 만들지 않는다.
- Story 5.4 recovery fallback은 accepted bucket gap 기반이며 snapshot source는 Story 5.8로 남겼다. Story 5.6도 snapshot/history를 current evidence에 끌어오지 않는다.
- Story 5.5는 `endpointPriority`를 typed list로 채웠고 endpoint ranking의 canonical source를 닫았다. Story 5.6 endpoint evidence는 이 ranking을 보조하는 selected instance presence/share evidence다.
- Story 5.5 endpoint parse/merge는 invalid JSON, unsafe route, malformed count, boundary mismatch를 raw 노출 없이 bounded status로 수렴하는 패턴을 만들었다. Story 5.6은 이 logic을 복제해 drift시키지 말고 재사용 또는 helper extraction을 우선한다.
- 최근 git history는 Story 5.5 endpoint priority read model merge가 마지막 주요 변경이다. Story 5.6은 dashboard/bucket/catalog/heartbeat read path를 확장하되 ingest write path, starter payload, snapshot/history를 재작성하지 않는다.

## Architecture Constraints

- Active baseline은 Traditional MVC + Service/Repository Layering이다.
- Portal package는 feature-first MVC 구조를 따른다.
- 이 프로젝트에서 `domain`은 business feature grouping namespace이며 DDD pure domain layer가 아니다.
- Instance evidence 구현 위치는 새 `domain.instance` feature package를 우선한다. 기존 dashboard read model과 강하게 묶는 것이 더 일관적이면 `domain.dashboard` 하위 service/model/controller도 허용한다.
- `application`, `port`, `adapter`, `adapter.in`, `adapter.out` package를 새로 만들지 않는다.
- Controller는 service를 호출하고 repository를 직접 호출하지 않는다.
- Service는 빠른 MVC 구현을 위해 필요하면 Spring Data repository와 JPA entity를 직접 사용할 수 있지만, JPA entity를 external return model로 노출하지 않는다.
- Repository는 Spring Data JPA/Jakarta Persistence + Hibernate 기반 read-only 조회만 담당한다.
- Flyway SQL migration이 schema source of truth다. Story 5.6은 새 migration을 기대하지 않는다.
- DB view, materialized view, trigger, stored procedure에 instance state, endpoint evidence selection, triage contribution, percentile, rule, confidence, action 계산을 숨기지 않는다.
- raw project key, access token, refresh token, GitHub OAuth token, provider raw payload, secret은 response/log/error에 노출하지 않는다.
- 새 공개 클래스/메서드/핵심 helper의 Javadoc/comment는 프로젝트 지침에 따라 한국어로 작성한다.

## Developer Guardrails

- `instanceId`는 portal catalog UUID다. starter가 보내는 `application.instance` 문자열과 혼동하지 않는다.
- `instanceName`은 표시와 heartbeat lookup identity에만 사용한다. API membership은 UUID path로 검증한다.
- heartbeat는 catalog row 생성 source가 아니다.
- `metricData`와 `starterConnection`을 합치지 않는다.
- heartbeat 성공을 accepted bucket freshness, lifecycle state, recovery source, p95/p99, endpoint evidence source로 사용하지 않는다.
- accepted bucket은 metric data freshness/read model source다.
- latest accepted bucket endUtc는 freshness source일 뿐 current window end가 아니다.
- current window는 query 시점 floor boundary 기준 current 15분이고, bucket membership은 `(start, end]`다.
- p95/p99를 평균, 최댓값, 합산, 병합하지 않는다.
- histogram bucket으로 p95/p99, avg/max latency, endpoint percentile을 계산하지 않는다.
- endpointEvidence는 dashboard endpointPriority를 보조하는 selected instance evidence다. 새 endpoint priority/rank/confidence/recommendedAction을 만들지 않는다.
- `localDisplayOrder`는 표시 순서일 뿐 priority 판단이 아니다.
- `not_observed`는 selected instance current window에서 evidence가 없다는 뜻이다. 문제 없음이나 root cause를 뜻하지 않는다.
- application freshness가 current가 아니면 stale/down 직전 endpoint evidence를 current concern처럼 보여주지 않는다.
- raw accepted bucket JSON, raw `endpoints_json`, raw path, query string, query key/value, trace id, per-request sample, raw histogram JSON string을 response evidence에 노출하지 않는다.
- dashboard snapshot persistence/query, instance snapshot trend, operational event history, raw instance timeseries, endpoint timeseries table을 추가하지 않는다.
- unrelated refactor, package 재배치, 기존 ingest/heartbeat behavior 변경을 피한다.

## Test Expectations

Focused test 대상 후보:

- `InstanceEvidenceReadModelShapeTest`
- `InstanceEvidenceReadModelServiceTest`
- `InstanceEvidenceControllerTest`
- `MetricBucketRepositoryIntegrationTest` 또는 `InstanceEvidenceMetricBucketRepositoryIntegrationTest`
- `StarterHeartbeatTelemetryRepositoryIntegrationTest` 보강
- `EndpointPriorityServiceTest` regression
- `DashboardReadModelServiceTest` regression
- `MvcLayerBoundaryTest`

필수 scenario:

- evidence endpoint는 `projectId/applicationId/instanceId` UUID path를 service에 위임한다.
- project 없음, application 없음, project/application mismatch, instance 없음, instance/application mismatch는 모두 404다.
- selected instance membership은 `application_instances.id` UUID 기준이며 `instanceName`만으로 조회하지 않는다.
- response top-level field가 모두 존재한다.
- response에는 state, healthScore, availabilityScore, hostStatus, connectedAndHealthy가 없다.
- query 시각 floor boundary와 current 15분 `(start, end]` window를 따른다.
- selected instance latest bucket endUtc가 freshness source이고 current window end를 대체하지 않는다.
- `metricData.statusSource=accepted_bucket`, `starterConnection.statusSource=starter_heartbeat`, `stateImpact=none`이다.
- recent heartbeat + missing/stale accepted bucket은 metric freshness current로 바뀌지 않는다.
- heartbeat missing/stale은 host application down 확정으로 표현되지 않는다.
- starter heartbeat exact lookup은 project/application/environment/instanceName identity를 사용한다.
- starter heartbeat만 있는 instance는 evidence API membership으로 발견되지 않는다.
- starter percentile points는 selected instance current 15분 안에서 시간 오름차순 최대 30개다.
- local percentile invalid source/scope/boundary/requestCount/p95/p99는 제외되고, all invalid는 `insufficient`, no rows는 `missing`이다.
- percentile points를 평균/최댓값/병합하거나 histogram으로 재계산하지 않는다.
- histogram distribution은 selected instance scope current window만 사용한다.
- histogram boundary mismatch는 `unavailable/histogram_boundary_mismatch`다.
- resource hints는 latest sample만 반환하고 max/avg/state/root cause를 만들지 않는다.
- application triage contribution은 existing application triage rule id만 참조하고 새 rule/confidence/action을 만들지 않는다.
- endpointEvidence는 application freshness가 current가 아니면 `suppressed/application_freshness_not_current/items=[]`다.
- endpointEvidence selection은 application priority presence, triage contribution, selected instance request count 순서를 따른다.
- application priority endpoint가 selected instance에서 없으면 `not_observed` item으로 표현할 수 있고 problem-free/root-cause copy가 없다.
- observed endpoint item은 instance/app request/error counts, error rates, shares를 bounded field로 계산한다.
- denominator 0 또는 aggregate missing은 share/rate를 `null`로 둔다.
- endpoint durationBuckets는 selected instance aggregate boundary set이 일치할 때만 제공한다.
- endpoint boundary mismatch는 item `unavailable/histogram_boundary_mismatch`이며 endpoint p95/p99를 만들지 않는다.
- `localDisplayOrder`는 deterministic display order이며 `rank`, `confidence`, `score`, `recommendedAction`이 아니다.
- endpointEvidence item에는 raw JSON, raw path, query string, traceId, per-request sample, endpointP95Ms, endpointP99Ms가 없다.
- Controller test는 service stub serialization과 404 mapping만 검증한다.
- Repository integration test는 instance scope, application scope, `(start, end]` window, JSON projection만 검증하고 rule/rank/confidence/action을 검증하지 않는다.
- helper extraction 후 기존 EndpointPriorityService behavior가 regression 없이 유지된다.
- no `application`, `port`, `adapter` package가 추가되지 않았음을 `MvcLayerBoundaryTest`로 확인한다.

Suggested commands:

```bash
./gradlew :observability-portal:test --tests '*InstanceEvidence*'
./gradlew :observability-portal:test --tests '*EndpointPriority*'
./gradlew :observability-portal:test --tests '*DashboardReadModel*'
./gradlew :observability-portal:test --tests '*MetricBucketRepository*'
./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest
./gradlew :observability-portal:test
git diff --check
```

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- 2026-05-26: bmad-create-story workflow 설정, project-context, Story 5.6 결정 문서, Epic 5/6 planning 문서, read-model/api/time/state/ingest/histogram/route/insight contracts, Story 5.5 산출물, 현재 dashboard/catalog/bucket/state/ingest code를 확인했다.
- 2026-05-26: Story 5.6 create-story 산출물을 `planning-artifacts/stories/5-6-instance-evidence-read-model.md`에 생성했다.
- 2026-05-26: 사용자 지시에 따라 Story 5.6 전체가 아니라 Phase 1만 구현하도록 범위를 제한했다.
- 2026-05-26: Instance Evidence foundation, UUID membership, dashboard instance entry, focused controller/model/service/dashboard tests를 추가했다.
- 2026-05-26: 사용자 지시에 따라 Story 5.6 전체 완료가 아니라 Phase 2 core evidence block만 구현하도록 범위를 제한했다.
- 2026-05-26: selected instance metricData, starterConnection, starterPercentiles, histogramDistribution, resourceHints, applicationTriageContribution 계산과 focused tests/repository integration regression을 추가했다.
- 2026-05-26: 사용자 지시에 따라 Story 5.6 전체 완료가 아니라 Phase 3 endpointEvidence subset만 구현하도록 범위를 제한했다.
- 2026-05-26: EndpointEvidenceAggregationService 공유 helper 추출, selected instance endpoint projection, endpointEvidence selection/presence/share/display order 구현과 focused/regression test를 완료했다.
- 2026-05-26: Phase 4 closure에서 source of truth와 Story 5.5 route 계약을 재확인했다. selected endpoint malformed evidence는 전체 endpointEvidence insufficient/items=[] 보수 정책으로 유지했고, `UNKNOWN` route는 selected instance fallback/triage hint에서 제외하도록 보정했다.
- 2026-05-26: Phase 4 focused tests, full `:observability-portal:test`, `git diff --check`를 모두 통과해 story/sprint status를 review로 전환했다.

### Completion Notes

- Phase 1 범위만 완료했다. Instance Evidence API route와 typed response shape를 추가했고, 상세 metric/starter percentile/histogram/resource/triage/endpoint 계산은 bounded missing/empty block으로 유지했다.
- Service에서 `projectId + applicationId + instanceId` UUID membership을 검증하며 mismatch는 `Optional.empty()`로 반환해 controller가 404로 매핑한다.
- Application Dashboard response에 최대 50개 `instances` entry를 추가했고, 각 entry는 `instanceId`, `instanceName`, nullable `lastSeenAt`, `links.evidence`만 노출한다.
- endpointEvidence selection, starter percentile full series, histogram/resource/triage 상세 계산, accepted bucket write path, starter ingest/heartbeat payload, snapshot/trend는 변경하지 않았다.
- Phase 2 범위로 selected instance accepted bucket latest freshness/current window aggregate, starter heartbeat exact identity lookup, starter canonical percentile series, summary histogram merge, latest runtime ratio sample, application triage rule-id bridge를 실제 read model로 채웠다.
- Phase 3 범위로 endpointEvidence subset을 구현했다. application freshness suppression, application priority/triage/selected-instance request-count selection, observed/not_observed/insufficient presence, app/instance counts/rates/shares, selected duration bucket merge, relatedApplicationPriorityRank/ruleIds copy, deterministic localDisplayOrder를 추가했다.
- Story 5.5 endpoint parsing/route safety/count validation/histogram merge 규칙은 `EndpointEvidenceAggregationService`로 공유화했고, `EndpointPriorityService`는 같은 helper를 사용하도록 변경했다. endpoint priority algorithm, rank/confidence/score/recommendedAction semantics는 새로 만들지 않았다.
- dashboard snapshot/trend/history, starter ingest/heartbeat payload 변경, accepted bucket write path 변경, migration/table 추가, endpoint p95/p99 계산은 구현하지 않았다.
- Phase 4에서 selected instance endpoint evidence의 malformed row 보수 정책을 테스트로 고정했고, Story 5.5 `UNKNOWN` route actionability 계약에 맞춰 instance endpoint fallback/triage hint에서도 `UNKNOWN` route를 제외했다.
- 필수 focused tests, full regression, `git diff --check`가 통과해 Story/sprint status를 `review`로 전환했다.

### File List

- implementation-artifacts/sprint-status.yaml
- planning-artifacts/stories/5-6-instance-evidence-read-model.md
- observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/AcceptedMetricBucketJpaRepository.java
- observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java
- observability-portal/src/main/java/com/observation/portal/domain/catalog/entity/ApplicationInstanceEntity.java
- observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ApplicationInstanceRepository.java
- observability-portal/src/main/java/com/observation/portal/domain/dashboard/model/ApplicationDashboardReadModel.java
- observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/DashboardReadModelService.java
- observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/EndpointEvidenceAggregationService.java
- observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/EndpointPriorityService.java
- observability-portal/src/main/java/com/observation/portal/domain/instance/package-info.java
- observability-portal/src/main/java/com/observation/portal/domain/instance/controller/InstanceEvidenceController.java
- observability-portal/src/main/java/com/observation/portal/domain/instance/controller/package-info.java
- observability-portal/src/main/java/com/observation/portal/domain/instance/model/InstanceEvidenceReadModel.java
- observability-portal/src/main/java/com/observation/portal/domain/instance/model/package-info.java
- observability-portal/src/main/java/com/observation/portal/domain/instance/service/InstanceEvidenceReadModelService.java
- observability-portal/src/main/java/com/observation/portal/domain/instance/service/package-info.java
- observability-portal/src/test/java/com/observation/portal/domain/dashboard/controller/DashboardControllerTest.java
- observability-portal/src/test/java/com/observation/portal/domain/dashboard/service/DashboardReadModelServiceTest.java
- observability-portal/src/test/java/com/observation/portal/domain/dashboard/service/EndpointPriorityServiceTest.java
- observability-portal/src/test/java/com/observation/portal/domain/bucket/repository/MetricBucketRepositoryIntegrationTest.java
- observability-portal/src/test/java/com/observation/portal/domain/instance/controller/InstanceEvidenceControllerTest.java
- observability-portal/src/test/java/com/observation/portal/domain/instance/model/InstanceEvidenceReadModelShapeTest.java
- observability-portal/src/test/java/com/observation/portal/domain/instance/service/InstanceEvidenceReadModelServiceTest.java

### Change Log

- 2026-05-26: Story 5.6 Instance Evidence Read Model create-story 산출물을 생성하고 ready-for-dev 상태로 전환했다.
- 2026-05-26: Phase 1 Instance Evidence API foundation과 Dashboard instance entry surface를 구현하고 story 상태를 in-progress로 전환했다.
- 2026-05-26: Phase 2 core instance evidence blocks를 구현하고 endpoint evidence/snapshot/trend 범위는 미완료로 유지했다.
- 2026-05-26: Phase 3 endpointEvidence subset, shared endpoint aggregation helper, selected instance endpoint projection, focused/regression tests를 구현했다. Story/sprint status는 in-progress로 유지했다.
- 2026-05-26: Phase 4 closure에서 endpoint malformed/UNKNOWN route checkpoint를 닫고 필수 focused/full regression 통과 후 Story/sprint status를 review로 전환했다.

## Status

review
