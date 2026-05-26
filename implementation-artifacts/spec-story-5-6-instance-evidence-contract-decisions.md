---
artifactType: contract-decisions
projectName: Spring Boot 운영 첫 화면 포털
storyId: "5.6"
storyKey: "5-6-instance-evidence-read-model"
status: closed
date: 2026-05-26
scope: Story 5.6 pre-story contract closure
---

# Story 5.6 Instance Evidence Contract Decisions

## Purpose

Story 5.6은 Instance Detail 화면이 사용할 bounded evidence read model/API를 만들기 전에 instance identity, response depth, time range, source axis separation, endpoint evidence subset, snapshot/trend handoff 경계를 닫는다.

이 문서는 Story 5.6 story 파일을 만들기 전에 사용자가 직접 결정한 계약을 기록한다. 목표는 Instance Detail이 Application Dashboard 판단을 대체하지 않고, 사용자가 특정 instance의 근거를 더 깊게 확인할 수 있는 drill-down surface로 유지되게 하는 것이다.

## Pending Decision Queue

1. ~~Instance Identity and Path Contract~~
2. ~~Response Depth and Bounded Evidence Contract~~
3. ~~Current Window and Percentile Series Contract~~
4. ~~Metric Data and Starter Connection Separation Contract~~
5. ~~Instance Endpoint Evidence Subset Contract~~
6. ~~Snapshot/Trend Handoff Contract~~

## Closed Decisions

### 1. Instance Identity and Path Contract

Story 5.6의 Instance Evidence API는 portal catalog가 생성한 `application_instances.id` UUID를 path identity로 사용한다.

결정 내용:

- API path의 `{instanceId}`는 `application_instances.id` UUID다.
- Starter 설정과 ingest/heartbeat payload는 UUID를 보내지 않고 기존처럼 `application.name`, `application.environment`, `application.instance` 문자열 identity를 보낸다.
- Portal은 첫 accepted bucket 수용 시 `applicationId + instanceName` 조합으로 `application_instances` row를 찾거나 만들고, 없으면 `application_instances.id`를 application-generated UUID로 생성한다.
- 같은 application 안에서 사용자가 정한 `instanceName`과 portal-generated `application_instances.id`는 catalog row 기준 1:1로 매칭된다.
- Application Dashboard 또는 instance summary 진입 surface는 UI가 클릭에 사용할 수 있도록 `instanceId`, `instanceName`, `links.evidence`를 함께 내려준다.
- UI는 `instanceName`을 표시하고, Instance Detail 진입 시 `instanceId`를 path에 넣어 `GET /api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/evidence`를 호출한다.
- Server는 `projectId + applicationId + instanceId` membership을 검증하고, project/application/instance가 없거나 서로 맞지 않으면 모두 `404`로 매핑한다.
- Heartbeat는 application/instance catalog row 생성 source가 아니다. 첫 accepted bucket ingest가 계속 catalog upsert source다.

결정 이유:

- 현재 portal catalog 구현은 accepted bucket 수용 시 `applicationName + environment + instanceName`으로 application/instance row를 찾거나 만들고, row가 없으면 portal 내부에서 UUID를 생성한다.
- 사용자가 starter properties에 portal UUID를 입력하게 만들면 onboarding/registration 흐름이 새로 필요해지고, 기존 "첫 accepted bucket이 catalog source" 계약을 흔든다.
- `instanceName`은 사람이 보고 설정하기 좋은 값이고, UUID는 API navigation과 membership 검증에 안정적인 row identity다.
- UUID path를 쓰면 instance name의 escaping, 길이, 표시명 성격, URL segment 충돌 문제를 API 계약 밖으로 둘 수 있다.

금지:

- Story 5.6에서 "Add instance 클릭 후 UUID를 발급하고 사용자가 properties에 입력하는" registration flow를 만들지 않는다.
- Starter ingest/heartbeat payload에 `applicationInstanceId` UUID field를 새로 요구하지 않는다.
- Heartbeat 성공만으로 `application_instances` catalog row를 만들지 않는다.
- UI나 controller가 `instanceName`만으로 project/application membership을 우회해 evidence를 조회하지 않는다.

### 2. Response Depth and Bounded Evidence Contract

Story 5.6의 Instance Evidence API는 얕은 metadata summary가 아니라 bounded evidence bundle을 반환한다.

결정 내용:

- response는 아래 top-level field를 포함한다.
  - `generatedAt`
  - `application`
  - `instance`
  - `metricData`
  - `starterConnection`
  - `starterPercentiles`
  - `histogramDistribution`
  - `resourceHints`
  - `applicationTriageContribution`
  - `endpointEvidence`
  - `links`
- `application`은 project/application/environment identity와 dashboard link를 담는다.
- `instance`는 `instanceId`, `instanceName`, `firstSeenAt`, `lastSeenAt` 같은 catalog identity와 관측 시각을 담는다.
- `metricData`는 accepted bucket axis의 last accepted bucket, freshness label, current window sample summary를 담는다.
- `starterConnection`은 heartbeat axis의 last heartbeat, heartbeat status, connection meaning, `stateImpact=none`을 담는다.
- `starterPercentiles`는 current window 안에서 해당 instance가 보낸 starter canonical p95/p99 series를 담는다.
- `histogramDistribution`은 해당 instance의 summary duration bucket distribution evidence를 담되 p95/p99 scalar를 계산하지 않는다.
- `resourceHints`는 latest sample 기반 CPU/heap/datasource ratio hint만 담고, state나 score로 변환하지 않는다.
- `applicationTriageContribution`은 이 instance가 application triage 근거에 기여했는지와 관련 rule id 또는 reason을 bounded field로 표현한다.
- `endpointEvidence`는 해당 instance에서 관찰된 bounded endpoint evidence subset이다. endpoint priority ranking을 새로 만들지 않는다.
- `links`는 dashboard, evidence self link, 후속 story가 제공할 수 있는 snapshot trend link 후보를 담을 수 있다.

결정 이유:

- Story 5.6의 목적은 Instance Detail이 Application Dashboard 판단을 대체하지 않는 evidence drill-down이 되게 하는 것이다.
- 얕은 metadata만 제공하면 Epic 6 Instance Evidence UI가 사용자에게 "왜 이 instance를 봐야 하는지"를 설명하기 어렵다.
- 반대로 raw bucket explorer로 확장하면 Epic 5/6의 server-computed read model 원칙과 raw explorer 금지 경계를 깨뜨린다.
- bounded evidence bundle은 사용자가 instance 단위 근거를 충분히 확인하게 하면서도 lifecycle state, endpoint priority, p95/p99, health score를 재계산하지 않게 하는 균형점이다.

금지:

- Instance Evidence API에 `state`, `healthScore`, `availabilityScore`, instance-level lifecycle 판단 field를 만들지 않는다.
- raw accepted bucket JSON, raw histogram JSON string, raw `endpoints_json`, raw path, query string, query key/value, trace id, per-request sample을 반환하지 않는다.
- endpoint priority ranking, lifecycle state, rule confidence, operational event를 instance API에서 새로 계산하지 않는다.
- histogram bucket으로 p95/p99, avg/max latency, endpoint percentile을 계산하지 않는다.
- `resourceHints`를 degraded/down 판단이나 instance health score로 합성하지 않는다.

### 3. Current Window and Percentile Series Contract

Story 5.6의 instance starter percentile evidence는 dashboard current window와 같은 current 15분 안의 30초 bucket series로 제공한다.

결정 내용:

- `starterPercentiles.window`는 `current_15m`이다.
- window membership은 bucket `bucketEndUtc` 기준 `(start, end]`를 따른다.
- `starterPercentiles.points`는 해당 `applicationInstanceId`의 current 15분 안 30초 bucket p95/p99 point를 시간 오름차순으로 반환한다.
- current 15분은 30초 bucket 기준 최대 30개 point를 반환한다.
- 각 point는 `bucketStartUtc`, `bucketEndUtc`, `requestCount`, `p95Ms`, `p99Ms`를 포함한다.
- p95/p99 source는 starter가 보낸 canonical local percentile인 `summary.localPercentiles`만 사용한다.
- point가 없는 bucket은 보간하지 않고 누락으로 둔다.
- 표시 가능한 point가 없으면 `status=missing` 또는 `status=insufficient`와 reason을 반환한다.

결정 이유:

- Story 5.3은 Application Dashboard에서 instance별 latest percentile point만 노출하고, full series는 Story 5.6 Instance Evidence 범위로 남겼다.
- Instance Detail은 사용자가 특정 instance의 최근 흐름을 확인하는 drill-down이므로 latest 1개보다 current 15분 series가 더 유용하다.
- current 15분은 Application Dashboard의 판단 window와 일치해 사용자가 dashboard와 instance evidence를 같은 시간 맥락에서 해석할 수 있다.
- 24h/7d/14d 장기 흐름은 Story 5.7 Instance Snapshot Trend Projection의 stored snapshot/read model projection 범위이므로 Story 5.6에서 구현하지 않는다.

금지:

- 여러 p95/p99 point를 평균, 최댓값, 병합해 새 percentile scalar를 만들지 않는다.
- histogram bucket으로 p95/p99를 재계산하지 않는다.
- baseline percentile series를 만들지 않는다.
- 24h/7d/14d trend, snapshot trend, raw 30초 bucket explorer를 Story 5.6에서 만들지 않는다.
- 누락 bucket을 보간하거나 synthetic point로 채우지 않는다.

### 4. Metric Data and Starter Connection Separation Contract

Story 5.6의 Instance Evidence API는 accepted bucket metric data axis와 starter heartbeat connection axis를 분리해서 반환한다.

결정 내용:

- `metricData.statusSource`는 `accepted_bucket`이다.
- `starterConnection.statusSource`는 `starter_heartbeat`다.
- `metricData`는 `lastAcceptedBucketAt`, `freshnessLabel`, current window request/error sample summary, sample readiness reason을 담을 수 있다.
- `starterConnection`은 `lastHeartbeatAt`, `lastHeartbeatStatus`, `connectionMeaning`, `stateImpact=none`을 담는다.
- recent heartbeat와 missing/stale accepted bucket 조합은 "starter는 연결됐지만 metric data는 대기/idle" 계열 copy로 표현한다.
- missing/stale heartbeat와 missing/stale accepted bucket 조합은 telemetry unreachable 또는 unknown 계열 copy로 표현하되 host application down을 확정하지 않는다.
- `stateImpact=none`은 heartbeat axis가 metric state를 바꾸지 않는 diagnostic/control-plane source라는 뜻으로 유지한다.

결정 이유:

- Epic 4 이후 계약은 accepted bucket metric axis와 starter heartbeat control-plane axis를 분리하는 것이다.
- Heartbeat가 최근이라고 해서 metric data freshness가 current인 것은 아니며, heartbeat가 없다고 host application down을 확정할 수도 없다.
- Instance Detail은 Application Dashboard 판단을 보조하는 evidence drill-down이므로 dashboard보다 강한 instance state 판단을 만들면 안 된다.
- 두 axis를 분리해야 first data waiting, metric data idle, telemetry unreachable, recovery 관찰 copy가 서로 다른 원인을 정확히 설명할 수 있다.

금지:

- `metricData`와 `starterConnection`을 합쳐 `instanceHealth`, `hostStatus`, `processDown`, `applicationDown`, `connectedAndHealthy` 같은 단일 상태 field를 만들지 않는다.
- Heartbeat 성공을 accepted bucket freshness, lifecycle state, recovery source, p95/p99, endpoint evidence source로 사용하지 않는다.
- Heartbeat 미수신을 host application down 확정으로 표현하지 않는다.
- UI/controller가 두 axis를 조합해 instance state를 재계산하지 않는다.

### 5. Instance Endpoint Evidence Subset Contract

Story 5.6의 Instance Evidence API는 Application Dashboard의 endpoint concern이 선택된 instance에서도 관찰됐는지 확인할 수 있는 bounded endpoint evidence subset을 반환한다.

결정 내용:

- `endpointEvidence.source`는 `accepted_metric_buckets.endpoints_json`이다.
- `endpointEvidence.scope`는 `instance_current_15m`이다.
- endpoint evidence 조회는 selected `applicationInstanceId`와 current 15분 `(start, end]` bucket window를 기준으로 한다.
- `endpointEvidence.items`는 최대 `5개`다.
- `selectionPolicy`는 `application_priority_presence_then_triage_then_instance_request_count`로 둔다.
- `displayOrderingPolicy`는 `selected_instance_signal_then_application_priority_reference`로 둔다.
- selection priority는 아래 순서를 따른다.
  1. Application Dashboard `endpointPriority` 상위 endpoint 중 selected instance에서 observed/not_observed 여부를 확인해야 하는 endpoint
  2. Application triage contribution과 연결된 endpoint
  3. 남는 자리는 selected instance current window request count 상위 endpoint
- `endpointEvidence.items`는 Instance Detail 안에서 읽기 좋은 deterministic display order로 반환할 수 있다.
- `localDisplayOrder`는 Instance Detail evidence 목록의 표시 순서이며, Story 5.5의 `endpointPriority.rank`나 새 priority 판단이 아니다.
- display order는 아래 순서를 따른다.
  1. `presenceOnSelectedInstance=observed`
  2. `instanceErrorShare` descending, null last
  3. `instanceErrorRate` descending, null last
  4. `instanceRequestCount` descending
  5. `relatedApplicationPriorityRank` ascending, null last
  6. `endpointKey` ascending
- Application Dashboard `endpointPriority` 상위 endpoint는 selected instance에서 관찰되지 않았더라도 `presenceOnSelectedInstance=not_observed` item으로 포함할 수 있다.
- selected instance에서 관찰된 endpoint는 `presenceOnSelectedInstance=observed`로 두고 instance-side bounded evidence를 포함한다.
- 허용 field 후보는 아래로 제한한다.
  - `method`
  - `route`
  - `endpointKey`
  - `presenceOnSelectedInstance`
  - `instanceRequestCount`
  - `instanceErrorCount`
  - `instanceErrorRate`
  - `applicationEndpointRequestCount`
  - `applicationEndpointErrorCount`
  - `applicationEndpointErrorRate`
  - `instanceRequestShare`
  - `instanceErrorShare`
  - `durationBuckets`
  - `bucketDistributionSource`
  - `relatedApplicationPriorityRank`
  - `localDisplayOrder`
  - `relatedRuleIds`
  - `status`
  - `reason`
- `presenceOnSelectedInstance` enum은 MVP에서 `observed`, `not_observed`, `insufficient`로 제한한다.
- `instanceRequestShare`와 `instanceErrorShare`는 application endpoint aggregate 대비 selected instance의 bounded share다. 값이 계산 불가능하면 `null`로 두고 reason을 제공한다.
- endpoint evidence 계산 seed는 아래로 고정한다.
  - application endpoint aggregate는 application current 15분 `(start, end]` bucket의 `endpoints_json`을 `endpointKey = method + " " + route`로 합산한 값이다.
  - selected instance endpoint aggregate는 selected `applicationInstanceId`의 current 15분 `(start, end]` bucket `endpoints_json`을 같은 `endpointKey`로 합산한 값이다.
  - `instanceErrorRate = instanceErrorCount / instanceRequestCount`이며 denominator가 0이면 `null`이다.
  - `applicationEndpointErrorRate = applicationEndpointErrorCount / applicationEndpointRequestCount`이며 denominator가 0이면 `null`이다.
  - `instanceRequestShare = instanceRequestCount / applicationEndpointRequestCount`이며 denominator가 0이거나 application aggregate가 없으면 `null`이다.
  - `instanceErrorShare = instanceErrorCount / applicationEndpointErrorCount`이며 denominator가 0이거나 application aggregate가 없으면 `null`이다.
  - `presenceOnSelectedInstance=observed`는 selected instance aggregate의 `requestCount > 0`인 경우다.
  - `presenceOnSelectedInstance=not_observed`는 application endpoint aggregate는 있는데 selected instance aggregate가 없거나 `requestCount=0`인 경우다.
  - `presenceOnSelectedInstance=insufficient`는 endpoint JSON parsing, route safety, count validation, histogram boundary validation 중 하나가 실패해 selected instance evidence를 신뢰할 수 없는 경우다.
  - `durationBuckets`는 selected instance aggregate의 cumulative bucket distribution이며, 같은 endpoint/window 안 boundary set이 일치할 때만 boundary별 count를 합산한다.
  - histogram boundary mismatch는 `status=unavailable`, `reason=histogram_boundary_mismatch`로 두고 p95/p99나 latency score를 만들지 않는다.
  - `relatedApplicationPriorityRank`와 `relatedRuleIds`는 Story 5.5 `endpointPriority` item에서 endpointKey가 일치할 때만 복사한다.
  - application metric freshness가 `current`가 아니면 `endpointEvidence.status=suppressed`, `reason=application_freshness_not_current`, `items=[]`로 둔다.
- MVP reason code는 아래 값으로 제한한다.
  - `application_priority_endpoint_observed_on_selected_instance`
  - `application_priority_endpoint_not_seen_on_selected_instance`
  - `selected_instance_endpoint_observed`
  - `endpoint_evidence_insufficient`
  - `histogram_boundary_mismatch`
  - `application_freshness_not_current`
- selected instance에 application priority endpoint가 없으면 "다른 instance에서 나온 신호일 수 있음"을 추론할 수 있는 evidence를 제공하지만, load balancer 문제나 root cause로 확정하지 않는다.
- application metric freshness가 `current`가 아니면 stale/down 직전 endpoint evidence를 current concern처럼 보여주지 않고 `endpointEvidence.status`와 reason으로 억제한다.
- 같은 application의 instances는 같은 endpoint set을 제공할 수 있다고 기대할 수 있지만, current evidence는 instance별로 독립 관찰한다.
- `not_observed`는 "문제 없음"이 아니라 "선택한 instance의 current window에서 해당 endpoint evidence가 없음"이라는 뜻이다.
- selected instance 자체의 endpoint evidence가 심하면 application aggregate와 비교할 수 있는 observed evidence로 보여준다. 단 새 priority/rank/root cause는 만들지 않는다.

결정 이유:

- 여러 instance를 운영할 때 Application Dashboard의 endpoint concern이 특정 selected instance에서는 관찰되지 않을 수 있다.
- 이 경우 Instance Detail은 load balancer 또는 traffic distribution 문제를 확정하지 않더라도, "이 instance에는 해당 endpoint evidence가 없거나 작다"는 사실을 보여줘야 사용자가 다른 instance를 확인할 수 있다.
- Story 5.5의 `endpointPriority`가 canonical endpoint ranking이므로 Story 5.6은 새 ranking이 아니라 dashboard concern과 selected instance evidence의 연결을 담당한다.
- Application-level concern 대비 selected instance share를 보여주면 raw explorer 없이도 instance contribution 여부를 이해할 수 있다.
- Instance Detail에서는 사용자가 선택한 instance 안에서 두드러지는 evidence를 먼저 읽고 싶어 하므로, bounded field 기반 display order는 유용하다.
- 이 display order는 application dashboard 판단을 대체하지 않고, `relatedApplicationPriorityRank`와 함께 selected instance contribution을 해석하기 쉽게 만드는 표현 계층이다.

금지:

- Story 5.6에서 instance-level endpoint priority ranking을 새로 만들지 않는다.
- `rank`, `confidence`, `score`, `recommendedAction`을 새로 계산하지 않는다. `relatedApplicationPriorityRank`는 Story 5.5의 application `endpointPriority.rank`를 참조하는 field일 뿐이고, `localDisplayOrder`는 evidence 표시 순서일 뿐이다.
- `localDisplayOrder`를 root cause 순위, endpoint priority, action priority로 표현하지 않는다.
- Load balancer misconfiguration, root cause, instance fault를 확정하는 copy나 field를 만들지 않는다.
- raw `endpoints_json`, raw path, query string, query key/value, trace id, per-request sample을 반환하지 않는다.
- endpoint p95/p99, endpoint percentile rollup, endpoint p99 alert 기준을 만들지 않는다.
- UI/controller/repository가 endpoint ranking, rule confidence, recommended action을 계산하지 않는다.

### 6. Snapshot/Trend Handoff Contract

Story 5.6은 current instance evidence read model만 닫고, snapshot persistence와 instance trend는 후속 Story 5.7/5.8 책임으로 남긴다.

결정 내용:

- Story 5.6은 catalog, accepted bucket, heartbeat telemetry 기반 current evidence API만 구현한다.
- Story 5.6은 `dashboard_snapshots` repository를 runtime dependency로 만들지 않는다.
- Story 5.6은 snapshot row를 생성, 조회, 갱신, 삭제하지 않는다.
- Story 5.6은 24h/7d/14d instance trend를 구현하지 않는다.
- Story 5.6은 snapshot marker, snapshot detail, operational event history를 구현하지 않는다.
- Story 5.6 response의 `links.snapshotTrend`는 후속 endpoint가 준비됐을 때 연결할 수 있는 후보 link로만 둔다. 실제 endpoint가 없으면 `null` 또는 omitted 상태를 허용한다.
- Story 5.6에서 정의한 `applicationTriageContribution`, `starterPercentiles`, `resourceHints`, `endpointEvidence` field의 의미는 Story 5.8 bounded instance summary가 재사용할 수 있도록 안정적인 이름과 source 의미를 유지한다.
- Story 5.7은 `dashboard_snapshots.read_model_json`의 bounded instance summary를 7d/14d stored trend로 projection한다.
- Story 5.8은 snapshot persistence, marker, bounded endpoint/instance summary 저장 shape를 닫는다.

결정 이유:

- Story 5.6의 책임은 Instance Detail이 현재 15분 evidence를 보여주는 read model/API를 닫는 것이다.
- Snapshot/trend를 Story 5.6에서 미리 구현하면 Story 5.7 Instance Snapshot Trend Projection과 Story 5.8 Dashboard Snapshot Persistence and Marker Contract의 책임이 겹친다.
- Story 5.7/5.8은 stored dashboard read model과 snapshot persistence 정책을 전제로 해야 하므로, Story 5.6이 임시 snapshot/trend 경로를 만들면 나중에 제거하거나 재정렬해야 할 가능성이 크다.
- Current evidence와 stored trend/history의 source를 분리해야 raw bucket explorer나 raw instance timeseries로 확장되는 것을 막을 수 있다.

금지:

- `dashboard_snapshots` table, repository, scheduler, fallback snapshot capture를 Story 5.6에서 구현하지 않는다.
- instance snapshot trend endpoint, snapshot detail endpoint, operational event history endpoint를 Story 5.6에서 구현하지 않는다.
- Raw instance timeseries table, endpoint timeseries table, snapshot-derived helper table을 Story 5.6에서 만들지 않는다.
- Current 15분 accepted bucket evidence를 7d/14d trend처럼 장기 조회하지 않는다.
- Snapshot/trend UI를 가능하게 하려고 Story 5.6 response에 large history array를 넣지 않는다.
