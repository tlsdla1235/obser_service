---
artifactType: story
storyId: "10.3"
storyKey: "10-3-wire-types-adapters-navigation-and-dashboard"
epic: "Epic 10. Figma Make Acceptance And Frontend Hardening"
title: "Wire types, adapters, navigation, and dashboard"
architectureStyle: "Vite SPA on Spring static root"
status: done
date: 2026-06-02
baselineCommits:
  story10_1: "cc7d87a frontend: adopt figma workspace and routing"
  story10_2: "7850d88 frontend: port auth and fetch foundation"
---

# Story 10.3 - Wire types, adapters, navigation, and dashboard

## Status

done

이 문서는 Story 10.3 구현과 리뷰 보완까지 완료한 BMAD story 산출물이다.

## Story

Figma Make 인수 프론트 구현자는 Story 10.2의 `AuthProvider`, `authFetch`, `useApiResource` foundation 위에서 Project Entry, Application List, Application Dashboard를 실제 server read model로 연결하고 싶다.

그래야 사용자가 GitHub OAuth 로그인 후 mock seed가 아니라 기존 backend API와 Java record contract를 source of truth로 삼아 `Project -> Application -> Dashboard` 흐름을 사용할 수 있고, UI가 lifecycle state, p95/p99, endpoint priority 같은 운영 판단을 재계산하지 않도록 Epic 10 acceptance를 잠글 수 있다.

## Source of Truth

아래 문서를 읽고 반영한 create-story context다. 충돌처럼 보이는 지점은 실제 Java record/API surface와 Story 10.2 완료 커밋의 frontend foundation을 우선한다.

1. `/Users/tlsdla1235/Desktop/study/observation/AGENTS.md`
2. `/Users/tlsdla1235/Desktop/study/observation/_bmad/custom/project-context.md`
3. `/Users/tlsdla1235/Desktop/study/observation/implementation-artifacts/sprint-status.yaml`
4. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/figma-make-acceptance-sprint-plan.md`
5. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/10-1-adopt-workspace-and-routing.md`
6. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/10-2-port-auth-and-fetch-foundation.md`
7. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/api-surface.md`
8. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/acceptance-traceability.md`
9. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/epics.md`
10. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/architecture.md`
11. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/contracts/read-model-contract.md`
12. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/5-1-project-application-navigation-read-model.md`
13. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/6-2-project-selection-ui.md`
14. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/6-3-application-list-ui.md`
15. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/6-4-application-dashboard-ui-integration.md`

확인한 backend source:

1. `observability-portal/src/main/java/com/observation/portal/domain/catalog/controller/ProjectNavigationController.java`
2. `observability-portal/src/main/java/com/observation/portal/domain/catalog/model/ProjectNavigationReadModel.java`
3. `observability-portal/src/main/java/com/observation/portal/domain/catalog/model/ProjectApplicationNavigationReadModel.java`
4. `observability-portal/src/main/java/com/observation/portal/domain/catalog/service/ProjectApplicationNavigationService.java`
5. `observability-portal/src/main/java/com/observation/portal/domain/dashboard/controller/DashboardController.java`
6. `observability-portal/src/main/java/com/observation/portal/domain/dashboard/model/ApplicationDashboardReadModel.java`
7. `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/DashboardReadModelService.java`
8. `observability-portal/src/main/java/com/observation/portal/domain/account/controller/AccountProjectMembershipResourceApiInterceptor.java`
9. `observability-portal/src/main/java/com/observation/portal/domain/state/model/LifecycleStateCode.java`

확인한 frontend source:

1. `frontend/src/app/lib/api.ts`
2. `frontend/src/app/lib/auth.tsx`
3. `frontend/src/app/lib/use-api-resource.ts`
4. `frontend/src/app/App.tsx`
5. `frontend/src/app/components/nav.tsx`
6. `frontend/src/app/components/dashboard.tsx`
7. `frontend/src/app/components/dashboard-data.ts`
8. `frontend/src/app/components/instance-panels.tsx`

## Current Code State

- Story 10.1 완료 커밋 `cc7d87a` 기준 `frontend/`는 최상위 Vite SPA workspace이고 root `BrowserRouter`로 `/`, `/dashboard`, `/docs`를 제공한다.
- Story 10.2 완료 커밋 `7850d88` 기준 `AuthProvider`, `useAuth`, `authFetch`, `authGeneration`, `useApiResource`가 존재한다.
- `frontend/src/app/lib/api.ts`는 auth endpoint와 shared fetch option/error만 갖고 있다. 이 story에서 read model endpoint/type/helper를 추가할 수 있지만 auth token persistence나 backend endpoint를 추가하지 않는다.
- `frontend/src/app/lib/use-api-resource.ts`는 `authFetch`, `authGeneration`, `authenticated`, request sequence, `AbortController`, unmount guard를 제공한다. caller의 `request`는 `useCallback`으로 안정화해야 불필요한 fetch loop를 만들지 않는다.
- `frontend/src/app/components/dashboard.tsx`는 현재 `projectsSeed`, `applicationsByProject`, `dashboardByApplication`을 `dashboard-data.ts`에서 import해 Project/Application/Dashboard를 초기화한다.
- `dashboard.tsx`는 현재 `recentConcern`, `topConcern`, `lifecycleBadge`를 문자열로 가정해 렌더링한다. 실제 backend는 object 또는 null이다.
- `dashboard-data.ts`는 Figma mock state code `STEADY`, `DEGRADED`, `RECOVERING`, `INSUFFICIENT_SAMPLE`와 mock percentile/endpoint/instance/snapshot data를 갖는다. Project/Application/Dashboard wiring source로 더 이상 사용하면 안 된다.
- `instance-panels.tsx`는 `instanceEvidenceById`, `snapshotTrendByInstance` mock을 사용한다. Instance evidence/trend wiring은 Story 10.4 범위이므로 10.3에서는 실 API 연결을 하지 않는다. 단, `Dashboard`가 Project/Application/Dashboard mock을 제거하는 과정에서 `dashboard-data.ts` 의존을 분리하거나 비활성/pending handoff로 바꾸는 것은 허용된다.
- 현재 dashboard top bar에는 fake token/account expiry copy가 있다. Story 10.3은 token 값을 표시하지 않고 Story 10.2 auth state를 safe auth-required/loading state로만 표현해야 한다.
- 현재 작업 트리에는 아래 known baseline blocker가 untracked로 남아 있다. 이 story 구현자는 삭제/수정/되돌리지 않는다.
  - `observability-portal/src/main/java/com/observation/portal/config/DashboardStaticWebConfig 2.java`
  - `observability-portal/src/main/java/com/observation/portal/domain/account/dto/GithubCallbackTokenRelayRequest 2.java`
  - `observability-portal/src/main/java/com/observation/portal/domain/account/dto/GithubCallbackSessionResponse 2.java`
  - `observability-portal/src/main/java/com/observation/portal/domain/account/controller/GithubCallbackTokenRelay 2.java`

## Scope

- 실제 Java record/API surface 기준 TypeScript DTO type을 작성한다.
- Project list는 `GET /api/projects`로 로드한다.
- Application list는 selected project의 `ProjectNavigationReadModel.ProjectItem.links.applications`로만 로드한다.
- Dashboard read model은 selected application의 `ProjectApplicationNavigationReadModel.ApplicationItem.links.dashboard`로만 로드한다.
- Story 10.2의 `authFetch`와 `useApiResource`를 사용해 loading/error/data, 401, stale response guard를 유지한다.
- `projectsSeed`, `applicationsByProject`, `dashboardByApplication` 기반 Project/Application/Dashboard 초기화를 제거한다.
- Project/Application/Dashboard의 loading, empty, filtered empty, generic error, auth-required/no-token, 401, 404 상태를 같은 story 안에서 처리한다.
- Adapter는 backend DTO를 Figma UI가 소비하기 쉬운 presentation model로 옮기되 rename, nullable 방어, 표시용 formatting만 수행한다.
- Dashboard screen에서 server read model의 `state`, `starterConnection`, `metrics`, `sourceScopedPercentiles`, `triageCards`, `zeroInsight`, `endpointPriority`, `instances`를 표시한다.
- `instances[].links.evidence`는 Story 10.4 handoff로만 보존한다. Evidence/trend/history/credential fetch는 하지 않는다.

## Non-scope

- 새 backend endpoint 생성
- Next.js 전환 또는 Next.js API route 생성
- Gradle node build integration
- Spring static fallback 변경
- legacy static dashboard 삭제
- `observability-portal/src/main/resources/static/dashboard/*` 수정 또는 삭제
- Instance Evidence API fetch/render
- Instance Snapshot Trend API fetch/render
- Snapshot Detail API fetch/render
- Operational Event History API fetch/render
- Starter credential lifecycle wiring
- Project credential create/rotation/revocation wiring
- Starter ingest API 호출
- lifecycle state, starter connection diagnosis, zeroInsight reason, recovery guidance, rule, p95/p99, endpoint priority, snapshot/history event, instance health 계산
- endpoint priority client-side sort/rank/reduce 계산
- histogram bucket으로 percentile 재계산
- accepted bucket freshness와 starter heartbeat를 하나의 host/application health로 합성
- known `* 2.java` duplicate baseline blocker 삭제/수정/되돌리기

## Backend Contract Summary

### Project Navigation

`GET /api/projects`는 `ProjectNavigationReadModel`을 반환한다.

Top-level:

- `generatedAt`
- `projects: ProjectItem[]`

`ProjectNavigationReadModel.ProjectItem`은 아래 field를 가진다.

- `projectId`
- `name`
- `applicationCount`
- `setupConnectionIssueCount`
- `recentConcern`
- `links.applications`

`recentConcern`은 `ConcernSummary { code, label, source } | null`이다. 현재 service는 reliable triage source가 없으면 `null`로 둔다.

### Application Navigation

`GET /api/projects/{projectId}/applications`는 `ProjectApplicationNavigationReadModel`을 반환한다.

Top-level:

- `generatedAt`
- `project: { projectId, name }`
- `applications: ApplicationItem[]`

`ProjectApplicationNavigationReadModel.ApplicationItem`은 아래 field를 가진다.

- `applicationId`
- `name`
- `environment`
- `metricData`
- `starterConnection`
- `lifecycleBadge`
- `topConcern`
- `links.dashboard`

Nested shapes:

- `metricData: { statusSource, lastAcceptedBucketAt, freshnessLabel }`
- `starterConnection: { statusSource, lastHeartbeatAt, heartbeatStatus, freshnessLabel, connectionMeaning, stateImpact }`
- `lifecycleBadge: LifecycleBadge { source, code, label }`
- `topConcern: ConcernSummary { code, label, source } | null`
- `links: { dashboard }`

현재 `ProjectApplicationNavigationService`는 `lifecycleBadge`를 `server_light_navigation_read_model / unknown / Metric data unknown`으로 반환할 수 있고, `topConcern`은 `null`일 수 있다. UI는 이를 계산으로 채우지 않는다.

### Dashboard

`GET /api/projects/{projectId}/applications/{applicationId}/dashboard`는 `ApplicationDashboardReadModel`을 반환한다.

Top-level:

- `generatedAt`
- `application`
- `state`
- `starterConnection`
- `zeroInsight`
- `recovery`
- `metrics`
- `sourceScopedPercentiles`
- `histogramDistribution`
- `triageCards`
- `endpointPriority`
- `instances`
- `snapshot`

중요한 current code details:

- `application`은 `projectId`, `applicationId`, `name`, `environment`, `lastAcceptedBucketAt`, `lastHealthyAt`, `sourceWindow`, `freshness`를 포함한다.
- `state.code`는 backend lowercase 7-value만 허용한다: `waiting_first_data`, `unknown`, `idle`, `active`, `stale`, `down`, `degraded`.
- `LifecycleStateCode`의 code 값은 accepted bucket metric data axis의 판정이며 starter heartbeat 연결 상태를 대체하지 않는다.
- `RECOVERING`은 `state.code`가 아니다. 회복 관찰은 `recovery.isRecovering` 별도 축으로만 표현한다.
- `metrics`는 `requestCount`, `errorCount`, `errorRate`만 포함한다. `errorRate`는 request count가 0이면 `null`일 수 있다.
- p95/p99는 `sourceScopedPercentiles.items[]`에서만 표시한다. 현재 service literal은 `source=starter_local`, `scope=instance_bucket`, `aggregatePolicy=no_average_no_max_no_merge_no_histogram_recalculation`일 수 있다.
- `sourceScopedPercentiles.items[]` item은 `source`, `application`, `environment`, `instance`, `bucketStartUtc`, `bucketEndUtc`, `requestCount`, `p95Ms`, `p99Ms`를 가진다.
- `histogramDistribution`은 bucket distribution evidence다. p95/p99 계산 source가 아니다.
- `triageCards=[]`이면 backend record constructor는 `zeroInsight != null`을 요구한다. UI는 빈 triage 화면 대신 `zeroInsight.reasonCode/message/recommendedAction`을 렌더링해야 한다.
- `endpointPriority[]`는 server-computed order/rank를 가진 next-check surface다. UI는 `item.rank`와 server order를 그대로 표시한다.
- `instances[]`는 최대 50개 bounded handoff entry이며 `instanceId`, `instanceName`, `lastSeenAt`, `links.evidence`를 포함한다.
- `snapshot`은 현재 code에서 `Object`이고 null일 수 있다. 이 story는 null-safe handoff만 처리하고 snapshot detail/history를 호출하지 않는다.

## Acceptance Criteria

1. DTO type은 실제 Java record와 현재 API surface를 기준으로 작성한다.
2. TypeScript DTO는 계획 문서의 rough shape보다 `ProjectNavigationReadModel`, `ProjectApplicationNavigationReadModel`, `ApplicationDashboardReadModel` Java record field를 우선한다.
3. `GET /api/projects`를 Story 10.2 `authFetch`로 호출하고 `ProjectNavigationReadModel`을 로드한다.
4. no-token 상태는 request storm 없이 `AuthRequiredError`/auth-required UI로 수렴한다.
5. `401`은 Story 10.2 auth foundation에 따라 token clear/auth-required state로 표현하고 project absence, application absence, application health, host down으로 번역하지 않는다.
6. `ProjectNavigationReadModel.ProjectItem`은 `projectId`, `name`, `applicationCount`, `setupConnectionIssueCount`, `recentConcern`, `links.applications`를 가진다.
7. Project list는 server order를 유지한다. UI에서 risk sort/ranking을 만들지 않는다.
8. Project item click은 `links.applications`를 source로 Application list를 로드한다. UI가 `projectId`로 application endpoint를 새로 조립하지 않는다.
9. Application list endpoint link는 내부 `/api/projects/{projectId}/applications` shape와 selected project context를 검증한 뒤 사용한다.
10. Project list loading state를 표시한다.
11. Project list empty state는 active membership project가 없다는 의미로만 표현하고 no-token/401과 섞지 않는다.
12. Project filter empty state는 현재 loaded project list에 대한 표시 결과 없음으로만 표현한다.
13. Project list generic error는 token/provider/internal payload를 노출하지 않는다.
14. `recentConcern`은 `ConcernSummary { code, label, source } | null`로 취급한다.
15. `recentConcern`이 있으면 `label`을 primary display로 사용하고 `code/source`를 보조 표시할 수 있다.
16. `recentConcern`이 null이면 source absence로 표현하고 "문제 없음", "정상", "복구 완료"로 번역하지 않는다.
17. `recentConcern` object가 `[object Object]`로 렌더링되지 않는다.
18. `ProjectApplicationNavigationReadModel.ApplicationItem`은 `applicationId`, `name`, `environment`, `metricData`, `starterConnection`, `lifecycleBadge`, `topConcern`, `links.dashboard`를 가진다.
19. Application list는 selected project의 `links.applications` response만 사용한다.
20. Application list response의 `project.projectId`가 selected project와 다르면 fail-closed error로 처리하고 렌더링하지 않는다.
21. Application list loading, empty, filtered empty, generic error, auth-required, 401, 404 상태를 처리한다.
22. Application list `404`는 membership mismatch/project missing fail-closed로 표현하고 hidden project detail, application health, host down을 추론하지 않는다.
23. Application list empty state는 catalog/first accepted bucket/source absence 중심으로 표현하고 application 정상/장애를 단정하지 않는다.
24. Application filter는 loaded application list의 표시 범위만 좁힌다. backend query, ranking, risk sorting, topConcern 생성/요약을 하지 않는다.
25. `metricData.statusSource`, `lastAcceptedBucketAt`, `freshnessLabel`은 accepted bucket axis로 표시한다.
26. `starterConnection.statusSource`, `lastHeartbeatAt`, `heartbeatStatus`, `freshnessLabel`, `connectionMeaning`, `stateImpact`는 starter heartbeat axis로 별도 표시한다.
27. `metricData`와 `starterConnection`을 하나의 `health`, `hostHealth`, `applicationHealth`, synthetic status/severity로 합치지 않는다.
28. `lifecycleBadge`는 `LifecycleBadge { source, code, label }`로 취급한다.
29. `lifecycleBadge.label`을 primary display로 사용하고, `code/source`는 badge style 또는 보조 copy로만 사용한다.
30. UI는 `lifecycleBadge`를 accepted bucket/heartbeat/topConcern 조합으로 새로 계산하지 않는다.
31. `lifecycleBadge` object가 `[object Object]`로 렌더링되지 않는다.
32. `topConcern`은 `ConcernSummary { code, label, source } | null`로 취급한다.
33. `topConcern`이 있으면 `label`을 primary display로 사용하고, null이면 source absence로 표현한다.
34. `topConcern` object가 `[object Object]`로 렌더링되지 않는다.
35. Application item click은 `links.dashboard`를 source로 Dashboard read model을 로드한다. UI가 project/application id로 dashboard endpoint를 새로 조립하지 않는다.
36. Dashboard link는 내부 `/api/projects/{projectId}/applications/{applicationId}/dashboard` shape와 selected project/application context를 검증한 뒤 사용한다.
37. 빠른 Project 전환, Application 전환, reload, token clear, component unmount 상황에서 stale Project/Application/Dashboard response가 최신 선택 상태를 덮지 않는다.
38. 구현은 `useApiResource`의 request sequence guard를 사용하거나, 동등한 sequence guard를 명확히 유지한다.
39. `useApiResource`에 넘기는 request callback은 `useCallback` 등으로 안정화해 dependency 변화로 fetch storm을 만들지 않는다.
40. selected project가 바뀌면 selected application과 dashboard state를 안전하게 reset한다.
41. selected application이 현재 application list에 없으면 dashboard를 stale 상태로 남기지 않는다.
42. Dashboard fetch loading, generic error, auth-required, 401, 404, ready state를 처리한다.
43. Dashboard `404`는 selected application scope가 없거나 membership fail-closed일 수 있다는 safe copy로 표현하고 application down/deleted/healthy를 단정하지 않는다.
44. Dashboard response의 `application.projectId`와 `application.applicationId`가 selected context와 다르면 fail-closed error로 처리하고 렌더링하지 않는다.
45. Dashboard `state.code`는 backend lowercase 7-value `waiting_first_data`, `unknown`, `idle`, `active`, `stale`, `down`, `degraded` 기준으로 처리한다.
46. Figma mock의 대문자 `STEADY`, `DEGRADED`, `RECOVERING`, `INSUFFICIENT_SAMPLE` state 분기는 제거하거나 실제 backend code mapping으로 대체한다.
47. `RECOVERING`은 state code가 아니라 `recovery.isRecovering` 별도 축으로만 표현한다.
48. State style mapping은 7개 lowercase code를 받아 graceful default를 가진다. Unknown future code가 오더라도 UI가 깨지지 않고 server label을 표시한다.
49. Metric state strip은 `state.label`, `state.rationale`, `state.recommendedAction`, `state.scope`, `application.freshness`, `application.sourceWindow`, `metrics`를 표시한다.
50. Starter connection strip은 `starterConnection`을 metric state strip과 분리한다.
51. `starterConnection.stateImpact=none`을 application health success 또는 failure로 합성하지 않는다.
52. `metrics.requestCount`, `metrics.errorCount`, `metrics.errorRate`만 headline scalar로 표시한다.
53. `metrics.errorRate=null`일 때 안전한 placeholder를 표시한다.
54. `metrics.p95Ms`, `metrics.p99Ms`, `avgMs`, `maxMs`, `latencyScore`, `healthScore` 같은 client-side scalar를 만들지 않는다.
55. p95/p99는 `sourceScopedPercentiles.items[]`에서만 표시한다.
56. `sourceScopedPercentiles.status`가 `missing` 또는 `insufficient`이면 `reason`을 source absence/evidence 부족으로 표시한다.
57. `sourceScopedPercentiles.aggregatePolicy`는 UI 또는 static review에서 보존된다.
58. Histogram bucket에서 p95/p99를 계산하거나 `percentile`, `p95`, `p99` helper를 만들지 않는다.
59. `histogramDistribution`은 current/baseline bucket distribution evidence로만 표시한다.
60. `triageCards=[]`이면 빈 화면이나 generic "특이 사항 없음" fallback 대신 server-provided `zeroInsight`를 렌더링한다.
61. `zeroInsight.reasonCode`, `message`, `recommendedAction`은 서버가 준 값을 표시하고 새 reason alias를 만들지 않는다.
62. 허용 zero insight reason은 `no_action_needed`, `insufficient_sample`, `waiting_first_data`, `metric_data_idle`, `telemetry_unreachable`, `observing_recovery`다.
63. `recovery.isRecovering=true`는 "복구 완료"가 아니라 "회복 관찰 중" 또는 다음 bucket까지 관찰 의미로 표현한다.
64. `recovery.retryAfterSeconds`는 자동 예약이 아니라 다음 판단 대기 안내로 표시한다.
65. `endpointPriority`는 server order와 `rank`를 그대로 표시한다.
66. UI는 endpointPriority를 `.sort()`, `.reduce()`, index 기반 rank, score 재계산, confidence 재계산으로 재정렬하거나 축약하지 않는다.
67. Endpoint priority copy는 "먼저 확인할 endpoint" 또는 "Next check" 계열로 두고 root cause 확정 순위, 장애 순위, alert priority, endpoint health score로 표현하지 않는다.
68. Endpoint evidence는 bounded count/rate/bucket distribution만 표시하고 raw path, query string, query key/value, trace id, per-request sample, endpoint p95/p99를 표시하지 않는다.
69. `instances[]`는 Dashboard의 bounded handoff surface로 표시할 수 있다.
70. `instances[].links.evidence`는 Story 10.4 handoff로 보존하되, 10.3에서 evidence/trend API를 fetch/render하지 않는다.
71. `snapshot`은 null-safe로 처리하고 Snapshot Detail/History API를 fetch/render하지 않는다.
72. `projectsSeed`, `applicationsByProject`, `dashboardByApplication` 기반 초기화를 제거한다.
73. `dashboard-data.ts`가 Project/Application/Dashboard read model source로 남지 않는다.
74. 10.4 범위의 instance/trend mock이 compile placeholder로 꼭 필요하면 Project/Application/Dashboard mock과 분리된 파일/이름으로 격리하고, 10.3 implementation notes에 후속 handoff로 기록한다.
75. Dashboard top bar의 fake token/account expiry copy를 제거한다. token 값, refresh token, provider token, starter credential은 UI에 표시하지 않는다.
76. Project registration dialog와 starter credential lifecycle controls는 이 story에서 API wiring하지 않는다. 남겨야 한다면 disabled/pending 또는 후속 story handoff로만 둔다.
77. 새 backend endpoint, Next.js API route, Gradle integration, Spring static fallback, legacy dashboard 삭제를 하지 않는다.
78. `react-router` v7 package boundary를 유지하고 `react-router-dom`을 추가하지 않는다.
79. 새 소스 파일 또는 복잡한 helper를 만들면 AGENTS.md 지침에 따라 한국어 주석/JSDoc으로 역할과 사용 맥락을 짧게 설명한다.
80. `* 2.java` duplicate Java baseline blocker는 삭제/수정/되돌리지 않는다.

## Tasks / Subtasks

- [x] DTO type과 endpoint helper 작성 (AC: 1~6, 18, 35, 45, 52~60, 65~68, 78~79)
  - [x] 실제 Java record 기준으로 Project/Application/Dashboard DTO type을 작성한다.
  - [x] `ProjectNavigationReadModel`, `ProjectApplicationNavigationReadModel`, `ApplicationDashboardReadModel` top-level과 nested object를 모두 type으로 표현한다.
  - [x] `ConcernSummary`, `LifecycleBadge`, `MetricDataSummary`, `StarterConnectionSummary`, `SourceScopedPercentiles`, `EndpointPriorityItem`, `InstanceEntry`를 object type으로 둔다.
  - [x] `state.code`는 lowercase 7-value union과 graceful default 처리로 둔다.
  - [x] endpoint constants 또는 helper는 기존 `frontend/src/app/lib/api.ts`에 추가하거나, read model 전용 파일을 새로 만들되 auth foundation과 충돌하지 않게 한다.
  - [x] `authFetch` response parsing helper가 필요하면 status별 `ApiRequestError`를 유지하고 token/secret을 error message에 넣지 않는다.

- [x] Adapter/presentation model boundary 작성 (AC: 14~17, 25~34, 45~68, 72~75, 79)
  - [x] Adapter는 rename, nullable 방어, 표시용 formatting만 수행한다.
  - [x] `recentConcern`, `topConcern`, `lifecycleBadge`는 `label` 중심으로 표시하고 object 자체를 React child로 넘기지 않는다.
  - [x] Figma mock의 uppercase state 분기를 backend lowercase state mapping으로 교체한다.
  - [x] `RECOVERING` state branch를 제거하고 `recovery.isRecovering` branch로 옮긴다.
  - [x] p95/p99 표시 helper는 `sourceScopedPercentiles.items[]`만 입력으로 받는다.
  - [x] histogram distribution helper는 bar width/label formatting만 수행하고 percentile을 만들지 않는다.
  - [x] endpointPriority adapter는 server order/rank를 보존한다. sort/rank/reduce 계산을 만들지 않는다.

- [x] Project list wiring (AC: 3~17, 37~41, 72~75)
  - [x] `GET /api/projects`를 `useApiResource`와 `authFetch`로 로드한다.
  - [x] no-token/401/loading/empty/filtered-empty/error state를 분리한다.
  - [x] Project filter는 loaded list만 대상으로 삼고 정렬/ranking을 하지 않는다.
  - [x] Project selection은 `links.applications`를 보존하고 validated internal link만 Application list request source로 사용한다.
  - [x] selected project 변경 시 selected application/dashboard stale state를 clear한다.
  - [x] fake token/account expiry copy를 제거한다.

- [x] Application list wiring (AC: 18~44, 72~75)
  - [x] selected project의 `links.applications`로 Application list를 로드한다.
  - [x] response `project.projectId`와 selected project가 맞지 않으면 fail-closed error state로 처리한다.
  - [x] loading/auth-required/401/404/generic error/empty/filtered-empty/ready state를 렌더링한다.
  - [x] accepted bucket `metricData`와 starter heartbeat `starterConnection`을 별도 axis로 렌더링한다.
  - [x] `lifecycleBadge.label`, `topConcern.label`을 표시하고 object string 렌더링을 막는다.
  - [x] Application filter는 loaded list만 대상으로 삼고 ranking/sorting/recompute를 하지 않는다.
  - [x] selected application 변경 시 Dashboard request를 `links.dashboard` 기준으로 다시 로드하고 stale dashboard를 clear한다.

- [x] Dashboard read model wiring (AC: 35~71)
  - [x] selected application의 `links.dashboard`로 Dashboard read model을 로드한다.
  - [x] dashboard link와 response application context를 selected Project/Application과 검증한다.
  - [x] loading/auth-required/401/404/generic error/ready state를 렌더링한다.
  - [x] metric state strip과 starter connection strip을 별도 section으로 유지한다.
  - [x] `metrics`는 request/error scalar만 headline으로 표시한다.
  - [x] `sourceScopedPercentiles.status/reason/items[]/aggregatePolicy`를 표시하고 p95/p99는 items[]에서만 표시한다.
  - [x] `triageCards=[]`이면 `zeroInsight`를 표시한다.
  - [x] `recovery.isRecovering`은 회복 관찰 중 copy로 표시한다.
  - [x] `endpointPriority`는 server `rank`와 order 그대로 표시한다.
  - [x] `instances[]`와 `links.evidence`는 10.4 handoff로만 보존하고 evidence/trend API를 호출하지 않는다.
  - [x] `snapshot`은 null-safe로만 처리한다.

- [x] Mock seed 제거와 10.4 scope 분리 (AC: 70~77)
  - [x] `Dashboard`의 `projectsSeed`, `applicationsByProject`, `dashboardByApplication` import/state를 제거한다.
  - [x] `dashboard-data.ts`가 Project/Application/Dashboard read model source로 남지 않게 한다.
  - [x] `instance-panels.tsx`는 Story 10.4 범위이므로 실 API wiring하지 않는다.
  - [x] 기존 instance/trend mock이 compile placeholder로 필요하면 `dashboard-data.ts`와 분리하고 Story 10.4 handoff임을 명확히 한다.
  - [x] Project registration/credential lifecycle control은 API wiring하지 않는다.

- [x] Scope/static guard verification 수행 (AC: 전체)
  - [x] typecheck와 build를 실행한다.
  - [x] mock seed, recomputation, Next.js route, router package, duplicate Java blocker grep/find를 실행한다.
  - [x] `* 2.java` duplicate files는 그대로 남겨 known baseline blocker로만 기록한다.

## Dev Notes

### Recommended File Structure

구현자는 기존 code style에 맞춰 최소 변경을 우선한다. 아래는 권장 경계다.

- `frontend/src/app/lib/api.ts`
  - 기존 auth endpoint/fetch option/error를 유지한다.
  - 필요하면 read model endpoint constant와 JSON parsing helper를 추가한다.
- `frontend/src/app/lib/read-model-types.ts` 또는 동등 파일
  - 실제 Java record 기준 TypeScript DTO type을 둔다.
- `frontend/src/app/lib/read-model-adapters.ts` 또는 동등 파일
  - DTO -> presentation model 변환을 둔다.
  - 계산이 아니라 rename/null 방어/표시 formatting만 허용한다.
- `frontend/src/app/components/dashboard.tsx`
  - Project/Application/Dashboard resource state machine과 렌더링을 실제 API 기반으로 전환한다.
  - 큰 rewrite보다 현재 Figma layout의 section을 살리되 seed state를 제거한다.
- `frontend/src/app/components/instance-panels.tsx`
  - Story 10.4 전까지 evidence/trend fetch를 하지 않는다.
  - 10.3에서 건드린다면 mock panel open을 막거나 pending handoff로만 조정한다.

새 파일을 만들거나 helper가 복잡해지면 파일 상단 또는 helper 근처에 한국어로 짧은 역할 주석을 둔다.

### Adapter Guardrails

Adapter에서 해도 되는 일:

- DTO field rename: 예를 들어 `application.sourceWindow.current.endUtc`를 화면의 current window label로 옮김
- nullable 방어: `lastAcceptedBucketAt=null`, `errorRate=null`, `zeroInsight=null`, `snapshot=null` 처리
- 표시 formatting: ISO timestamp, percent display, count display, empty placeholder
- style token mapping: backend `state.code`나 `lifecycleBadge.code`를 CSS class로 매핑하되 meaning을 새로 만들지 않음
- internal link validation: server link가 현재 selected context와 일치하는지 확인

Adapter에서 하면 안 되는 일:

- lifecycle state 계산
- starter connection diagnosis 계산
- no-token/401을 project/application absence로 변환
- 404를 application down 또는 deleted로 변환
- zeroInsight reason 계산 또는 alias 생성
- recovery guidance 계산
- p95/p99 계산, 평균, 최댓값, 병합, histogram recalculation
- endpointPriority sort/rank/reduce 계산
- snapshot/history event 계산
- accepted bucket freshness와 heartbeat를 하나의 health로 합성

### Previous Story Intelligence

- Story 10.1은 Vite SPA, npm/typecheck, root `BrowserRouter`, Nav auth action boundary를 만들었다.
- Story 10.2는 React `AuthProvider`, GitHub OAuth popup relay, in-memory access token, `authFetch`, `useApiResource` sequence guard를 만들었다.
- Story 10.2의 `authFetch`는 token이 없으면 `AuthRequiredError`, resource `401`이면 token clear와 `AuthorizationLostError`로 수렴한다. 10.3은 이 상태를 Project/Application/Dashboard absence로 바꾸면 안 된다.
- Story 10.2의 `useApiResource`는 dependency/token 변경, parent selection 변경, unmount 뒤 stale response가 최신 state를 덮지 않게 설계됐다. 10.3은 이 hook을 실제 read model fetch에 사용해야 한다.
- Story 5.1은 Project/Application navigation API를 이미 구현했다. 10.3은 backend shape를 새로 만들지 않고 current shape를 소비한다.
- Story 6.2~6.4 static dashboard UI는 Project/Application/Dashboard wiring에서 stale guard, context mismatch fail-closed, source-axis separation, object field 표시 guard를 이미 배웠다. React/Vite 구현도 같은 실패 모드를 막아야 한다.

### State and Copy Guardrails

- `waiting_first_data`: 첫 accepted bucket을 기다리는 metric data axis 상태다. 최근 heartbeat가 있더라도 host app 정상으로 단정하지 않는다.
- `unknown`: 판단 source/sample 부족 또는 회복 관찰 중일 수 있다. `recovery.isRecovering`을 따로 확인한다.
- `idle`: current freshness지만 traffic idle 축이다. 오류 없음 또는 정상 확정이 아니다.
- `active`: backend service가 판단한 metric data active다. UI가 만든 정상 판정이 아니다.
- `stale`, `down`: accepted bucket freshness data-plane 상태다. host process down 확정 copy를 만들지 않는다.
- `degraded`: backend lifecycle/triage service가 server-side hysteresis와 concern evidence로 판단한 상태다.

Figma mock label을 그대로 쓰지 말고 server `state.label`, `lifecycleBadge.label`, `zeroInsight.message`, `recommendedAction`을 우선 표시한다.

### Known Baseline Blocker

아래 파일들은 기존 workspace baseline blocker이며 삭제/수정 금지다.

```text
observability-portal/src/main/java/com/observation/portal/config/DashboardStaticWebConfig 2.java
observability-portal/src/main/java/com/observation/portal/domain/account/dto/GithubCallbackTokenRelayRequest 2.java
observability-portal/src/main/java/com/observation/portal/domain/account/dto/GithubCallbackSessionResponse 2.java
observability-portal/src/main/java/com/observation/portal/domain/account/controller/GithubCallbackTokenRelay 2.java
```

이 blocker 때문에 Gradle Java compile/bootJar 검증이 실패할 수 있다. Story 10.3 verification은 frontend typecheck/build와 static grep을 중심으로 수행하고, duplicate Java files는 find 결과로 known blocker를 기록한다.

## Verification

반드시 실행할 명령:

```bash
git status --short
cd frontend && npm run typecheck
cd frontend && npm run build
rg -n "projectsSeed|applicationsByProject|dashboardByApplication|dashboard-data" frontend/src/app
rg -n "sort\\(|reduce\\(|percentile|p95|p99|endpointPriority|state.code|RECOVERING|STEADY|DEGRADED|INSUFFICIENT_SAMPLE" frontend/src/app/lib frontend/src/app/components
rg -n "NextResponse|next/server|app/api|pages/api|react-router-dom" frontend
find observability-portal/src/main/java -name '* 2.java' -print
```

Expected review 기준:

- `git status --short`에서 기존 unrelated untracked 파일과 Story 10.3 변경을 분리해 기록한다.
- `npm run typecheck`와 `npm run build`는 통과해야 한다.
- `projectsSeed`, `applicationsByProject`, `dashboardByApplication`은 남지 않아야 한다.
- `dashboard-data` match가 남으면 Project/Application/Dashboard read model source가 아닌지 수기 검토한다. 남은 이유가 10.4 instance/trend placeholder뿐이어야 하며, 가능하면 `dashboard-data.ts` 이름을 retire해 혼동을 줄인다.
- recomputation grep은 harmless display text와 실제 계산 helper를 구분해 수기 리뷰한다. `sort(`, `reduce(`, endpointPriority rank/index 계산, histogram percentile 계산, uppercase mock state branch가 남으면 수정한다.
- Next.js API route 또는 `react-router-dom` 추가가 없어야 한다.
- `find ... '* 2.java'`는 known baseline blocker 네 개를 그대로 반환할 수 있다. 삭제/수정하지 않는다.

수동 검증 후보:

1. Spring-served origin 또는 `/api` proxy가 명시된 dev setup에서 GitHub login 후 `/dashboard`에 진입한다.
2. Network 탭에서 `GET /api/projects`가 호출되는지 확인한다.
3. Project 선택 시 response의 `links.applications` URL이 호출되는지 확인한다.
4. Application 선택 시 response의 `links.dashboard` URL이 호출되는지 확인한다.
5. no-token, 401, 404 상태가 project absence/application health/host down으로 표현되지 않는지 확인한다.
6. `recentConcern`, `topConcern`, `lifecycleBadge`가 `[object Object]`로 보이지 않는지 확인한다.
7. Dashboard state code `waiting_first_data`, `unknown`, `idle`, `active`, `stale`, `down`, `degraded` fixture 또는 실제 response에서 badge/label이 깨지지 않는지 확인한다.
8. `triageCards=[]` response에서 `zeroInsight`가 표시되는지 확인한다.
9. 빠른 Project/Application 전환에서 늦게 도착한 이전 response가 최신 선택 화면을 덮지 않는지 확인한다.

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- 2026-06-02: BMAD create-story workflow customization, config, persistent project context를 확인했다.
- 2026-06-02: 요청된 planning artifacts, Story 10.1/10.2, API surface, acceptance traceability, epics, architecture, read model contract를 읽었다.
- 2026-06-02: 요청된 backend Project/Application navigation, Dashboard read model, membership interceptor source를 확인했다.
- 2026-06-02: 요청된 frontend auth/fetch foundation, App/Nav/Dashboard/dashboard-data/instance-panels source를 확인했다.
- 2026-06-02: 현재 `git status --short`에서 기존 unrelated untracked 항목과 네 개의 duplicate Java baseline blocker를 확인했다.
- 2026-06-02: `implementation-artifacts/sprint-status.yaml`에서 story 10.3을 `in-progress`로 전환했다.
- 2026-06-02: Java record 기준 DTO type, read model adapter, internal link validation helper를 작성했다.
- 2026-06-02: Dashboard route를 `useApiResource` 기반 Project/Application/Dashboard server read model chain으로 전환했다.
- 2026-06-02: `dashboard-data.ts` mock seed를 제거하고 instance detail drawer를 Story 10.4 handoff-only pending panel로 분리했다.
- 2026-06-02: `npm run typecheck`, `npm run build`, 필수 `rg`/`find` guard를 실행했다.

### Implementation Plan

- Story 10.2 auth/fetch foundation 위에 `GET /api/projects` resource를 먼저 연결하고, server-provided `links.applications`와 `links.dashboard`만 후속 request source로 사용했다.
- Java record field를 TypeScript DTO로 옮긴 뒤 adapter에서 label/nullable/formatting/internal link validation만 수행하게 했다.
- Project/Application/Dashboard 각각의 loading, empty, filter-empty, auth-required, 404, generic error copy를 분리해 no-token/401/404를 health나 absence로 번역하지 않게 했다.
- Source-scoped p95/p99, histogram distribution, triage/zeroInsight, recovery, endpointPriority, instances, snapshot 영역은 server response를 표시하거나 Story 10.4 handoff로만 보존했다.

### Completion Notes List

- Story 10.3 create-story 산출물을 생성했다.
- 구현 범위는 Project/Application/Dashboard server read model wiring으로 한정했다.
- Story 10.4 범위인 instance evidence, trend, history, credential lifecycle wiring은 분리했다.
- `frontend/src/app/lib/read-model-types.ts`에 실제 Java record/API surface 기준 Project/Application/Dashboard DTO type을 추가했다.
- `frontend/src/app/lib/read-model-adapters.ts`에 object display guard, nullable formatting, lowercase state style mapping, internal link validation을 추가했다.
- `frontend/src/app/components/dashboard.tsx`는 mock seed 대신 `AuthProvider`, `authFetch`, `useApiResource`를 사용해 `/api/projects` -> `links.applications` -> `links.dashboard` 순서로 로드한다.
- `recentConcern`, `topConcern`, `lifecycleBadge`는 label 중심으로 렌더링해 `[object Object]` 표시를 막았다.
- `metrics` headline은 request/error/errorRate만 표시하고 p95/p99는 `sourceScopedPercentiles.items[]`에서만 표시한다.
- `endpointPriority`는 server order와 `rank`를 그대로 표시하며 client-side sort/reduce/rank 계산을 만들지 않았다.
- `instance-panels.tsx`는 evidence/trend API를 호출하지 않고 `instances[].links.evidence` handoff만 보존한다.
- 리뷰 보완 후 `implementation-artifacts/sprint-status.yaml`의 `10-3-wire-types-adapters-navigation-and-dashboard`를 `done`으로 갱신했다.

### File List

- `planning-artifacts/stories/10-3-wire-types-adapters-navigation-and-dashboard.md`
- `implementation-artifacts/sprint-status.yaml`
- `frontend/src/app/lib/api.ts`
- `frontend/src/app/lib/read-model-types.ts`
- `frontend/src/app/lib/read-model-adapters.ts`
- `frontend/src/app/components/dashboard.tsx`
- `frontend/src/app/components/instance-panels.tsx`
- `frontend/src/app/components/landing.tsx`
- `frontend/src/app/components/dashboard-data.ts` (deleted)

### Change Log

- 2026-06-02: Story 10.3 create-story 산출물을 생성하고 ready-for-dev 상태로 전환했다.
- 2026-06-02: Story 10.3 Project/Application/Dashboard read model wiring을 구현하고 review 상태로 전환했다.
- 2026-06-02: 리뷰에서 확인된 stale child resource guard와 story 상태 문구를 보완하고 done 상태로 전환했다.
