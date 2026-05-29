---
artifactType: story
storyId: "6.4"
storyKey: "6-4-application-dashboard-ui-integration"
epic: "Epic 6. Dashboard User Flow and Demo Hardening"
title: "Application dashboard UI integration"
architectureStyle: Traditional MVC
status: done
date: 2026-05-28
baseline_commit: 4486996e66698010283276dcbaf2a466f7b26077
---

# Story 6.4 - Application dashboard UI integration

## Status

done

## Story

portal 사용자는 Application List에서 선택한 Application의 Dashboard를 같은 static dashboard runtime 안에서 인증된 요청으로 열고 싶다.

그래야 Application Dashboard current read model을 primary first-screen으로 보고, metric data state, starter connection, zero insight/recovery, source-scoped p95/p99, histogram evidence, triage, endpoint next-check surface, instance handoff를 UI가 재계산하지 않은 server-computed 의미 그대로 확인할 수 있다.

## Source of Truth

아래 문서를 읽고 반영한 BMAD create-story context다. 구현 중 충돌처럼 보이는 지점은 1번 Story 6.4 pre-story contract decision, current code/API shape, `read-model-contract.md`를 우선한다.

1. `implementation-artifacts/spec-story-6-4-application-dashboard-ui-integration-contract-decisions.md`
2. `implementation-artifacts/epic-6-context.md`
3. `planning-artifacts/stories/6-3-application-list-ui.md`
4. `implementation-artifacts/epic-5-retro-2026-05-27.md`
5. `planning-artifacts/contracts/read-model-contract.md`
6. `planning-artifacts/api-surface.md`
7. `planning-artifacts/epics.md`
8. `planning-artifacts/sprint-plan.md`
9. `planning-artifacts/current-product-source-of-truth.md`
10. 현재 static dashboard 코드
    - `observability-portal/src/main/resources/static/dashboard/index.html`
    - `observability-portal/src/main/resources/static/dashboard/styles.css`
    - `observability-portal/src/main/resources/static/dashboard/app.js`
11. 현재 dashboard API/model/test
    - `observability-portal/src/main/java/com/observation/portal/domain/dashboard/controller/DashboardController.java`
    - `observability-portal/src/main/java/com/observation/portal/domain/dashboard/model/ApplicationDashboardReadModel.java`
    - `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/DashboardReadModelService.java`
    - `observability-portal/src/test/java/com/observation/portal/domain/dashboard/controller/DashboardControllerTest.java`
    - `observability-portal/src/test/java/com/observation/portal/domain/dashboard/service/DashboardReadModelServiceTest.java`
    - `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ApplicationListUiContractTest.java`
    - `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ProjectSelectionUiContractTest.java`
    - `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ProjectEntryUiContractTest.java`
12. `AGENTS.md`
13. `_bmad/custom/project-context.md`

## Scope / Out of Scope

포함:

- 기존 `observability-portal/src/main/resources/static/dashboard/` static asset 안에서 Application Dashboard view/state 추가
- Application List item의 `data-dashboard-link` 또는 API item의 `links.dashboard`를 source로 사용한 authenticated Dashboard fetch
- 검증된 `/api/projects/{projectId}/applications/{applicationId}/dashboard` 내부 link만 `Authorization: Bearer <access_token>` header로 요청
- `GET /api/projects/{projectId}/applications/{applicationId}/dashboard` current `ApplicationDashboardReadModel` rendering
- Dashboard loading, auth-required, invalid-link, not-found, generic error, ready state
- Application identity/context, source window/freshness, metric state strip, starter connection strip, request/error metrics, source-scoped percentile, histogram distribution, zeroInsight/recovery, triage cards, endpoint priority, bounded instance summary handoff 표시
- Application List로 돌아가거나 다른 Application Dashboard를 선택하는 흐름 유지
- `instances[].links.evidence`와 `snapshot`은 후속 story handoff data 또는 disabled/pending action으로 보존
- `ApplicationDashboardUiContractTest` 또는 동등한 static UI contract test 신규 추가
- 6.3 `ApplicationListUiContractTest`의 disabled dashboard handoff guard를 6.4 활성화 범위에 맞춰 좁혀 갱신
- no token persistence, no URL token parsing, no frontend stack, no UI-side recomputation guard 유지

제외:

- production/test code 구현 자체는 이 create-story 작업 범위 밖이다. 이 story는 dev-story용 산출물이다.
- Instance Evidence API fetch/render
- Instance Snapshot Trend API fetch/render
- Snapshot Detail API fetch/render
- Operational Event History API fetch/render
- demo green path, failure/recovery fixture, seed/hardening
- browser token persistence, refresh token browser storage, logout/session persistence 정책 확정
- 새 client-side route, SPA routing, direct API URL navigation, URL fragment/query token parsing
- backend dashboard API/schema/read model 확장, migration 추가, `ApplicationDashboardReadModel` shape 변경
- lifecycle state, starter diagnosis, zeroInsight, recovery, rule, p95/p99, histogram percentile, endpoint priority, instance health, snapshot/history event 계산
- endpoint p95/p99, application/window p95/p99 평균/최댓값/병합, histogram-derived percentile
- raw path/query/trace/per-request sample display, raw explorer, arbitrary query UI
- React, Vite, TypeScript, `package.json`, `src/main/frontend`, 별도 frontend build/deploy, view resolver/template engine
- Project creation/onboarding, public `POST /api/projects`, 로그인 직후 자동 project 생성

## Acceptance Criteria

1. Dashboard 진입은 Application List item의 `data-dashboard-link` 또는 API item의 `links.dashboard`를 source로 사용한다.
2. Dashboard fetch endpoint는 UI가 `projectId`와 `applicationId`로 재구성하지 않는다.
3. UI는 dashboard link가 내부 `/api/projects/{projectId}/applications/{applicationId}/dashboard` shape이고 현재 선택된 Project/Application id와 일치하는지 검증한다.
4. 검증 실패 시 Dashboard API call을 만들지 않고 safe invalid-link state를 표시한다.
5. Dashboard fetch는 in-memory access token hook이 보관한 token으로 `Authorization: Bearer <access_token>` header를 구성한다.
6. Token이 없거나 `401`이 반환되면 auth-required state를 보여주고 provider token, service token, refresh token, secret, raw payload를 노출하지 않는다.
7. `404`는 project/application mismatch 또는 missing scope로 표현하고 application health, host down, 정상/장애를 단정하지 않는다.
8. Generic error state는 stack trace, repository/internal exception detail, token, provider raw payload를 노출하지 않는다.
9. Overlapping request, token clear, Project 재선택, Application 재선택 상황에서 stale Dashboard response가 현재 화면을 덮어쓰지 않는다.
10. Dashboard ready state는 current `ApplicationDashboardReadModel` top-level field만 소비한다: `generatedAt`, `application`, `state`, `starterConnection`, `zeroInsight`, `recovery`, `metrics`, `sourceScopedPercentiles`, `histogramDistribution`, `triageCards`, `endpointPriority`, `instances`, `snapshot`.
11. `application.projectId`와 `application.applicationId`가 selected Project/Application context와 다르면 fail-closed error로 처리하고 response를 렌더링하지 않는다.
12. Application context는 `application.name`, `environment`, `lastAcceptedBucketAt`, `lastHealthyAt`, `sourceWindow.current`, `sourceWindow.baseline`, `freshness`를 표시한다.
13. Metric state strip은 `state`, `application.freshness`, `application.lastAcceptedBucketAt`, `application.sourceWindow`, `metrics`를 중심으로 렌더링한다.
14. Starter connection strip은 `starterConnection.statusSource`, `lastHeartbeatAt`, `lastHeartbeatStatus`, `connectionMeaning`, `stateImpact`를 별도 strip으로 렌더링한다.
15. `starterConnection.statusSource=starter_heartbeat`와 `stateImpact=none` 의미가 화면과 static contract test에서 유지된다.
16. UI는 accepted bucket freshness와 starter heartbeat를 `health`, `hostHealth`, `applicationHealth`, synthetic status/severity로 합치지 않는다.
17. UI는 lifecycle state, starter connection diagnosis, zeroInsight reason, recovery guidance, rule, endpoint priority, p95/p99를 재계산하지 않고 서버 응답을 formatting/escaping만 해서 표시한다.
18. `triageCards=[]`이면 server-provided `zeroInsight`를 빈 화면 대신 표시한다.
19. `zeroInsight.reasonCode`, `message`, `recommendedAction`은 서버가 준 값을 표시하며 새 reason code 또는 alias를 만들지 않는다.
20. 허용 zeroInsight reason은 `no_action_needed`, `insufficient_sample`, `waiting_first_data`, `metric_data_idle`, `telemetry_unreachable`, `observing_recovery`다.
21. `recovery.isRecovering=true`는 "복구 완료"가 아니라 "회복 관찰 중" 또는 "다음 bucket까지 관찰" 의미로 표현한다.
22. `recovery.retryAfterSeconds`는 자동 예약이 아니라 다음 판단 대기 안내로 표시한다.
23. `lastHealthyAt`이 null이면 "이전 정상 시점 없음" 계열의 source absence로 표현한다.
24. `metrics`는 request/error scalar만 headline으로 표시하고 `metrics.p95Ms`, `metrics.p99Ms`, `avgMs`, `maxMs`, `latencyScore`, `healthScore`를 만들지 않는다.
25. p95/p99는 `sourceScopedPercentiles.items[]`의 `source`, `application`, `environment`, `instance`, `bucketStartUtc`, `bucketEndUtc`, `requestCount`, `p95Ms`, `p99Ms`와 함께 표시한다.
26. `sourceScopedPercentiles.status`가 `missing` 또는 `insufficient`이면 `reason`을 source absence/evidence 부족으로 표시한다.
27. `sourceScopedPercentiles.aggregatePolicy`는 화면 또는 static contract guard에서 보존한다.
28. Histogram은 `histogramDistribution`의 current/baseline bucket distribution evidence로만 표시한다.
29. UI는 histogram bucket에서 p95/p99, delta, regression, confidence, rule 판단을 계산하지 않는다.
30. `triageCards[]`는 server가 준 `ruleId`, `severity`, `title`, `summary`, `recommendation`, `confidence`, `score`, `affectedEndpoint`, bounded `evidence`를 그대로 렌더링한다.
31. `endpointPriority[]`는 server가 준 `rank`, `method`, `route`, `endpointKey`, `reason`, `ruleIds`, `confidence`, `score`, `freshness`, bounded `evidence`, `recommendedAction`을 그대로 렌더링한다.
32. Endpoint priority label은 "먼저 확인할 endpoint" 또는 "Next check" 계열 의미로 두고 root cause 확정 순위, 장애 순위, alert priority, endpoint health score로 표현하지 않는다.
33. UI는 endpoint priority를 재정렬하거나 score/confidence/severity를 다시 산출하지 않는다.
34. Endpoint evidence는 bounded count/rate/bucket distribution만 표시하고 raw path, query string, query key/value, trace id, per-request sample, endpoint p95/p99를 표시하지 않는다.
35. `instances[]`는 Application Dashboard 안의 bounded handoff surface로 표시한다.
36. `instances[].links.evidence`는 Story 6.5 Instance Evidence UI 진입을 위한 data attribute 또는 disabled/pending action으로 보존할 수 있지만, 6.4에서 fetch/render하지 않는다.
37. `snapshot` 또는 snapshot link는 Story 6.7 Snapshot/History UI handoff data로만 보존하고, 6.4에서 Snapshot Detail API를 fetch/render하지 않는다.
38. Story 6.4는 Instance Evidence, Instance Snapshot Trend, Snapshot Detail, Operational Event History API를 fetch하지 않는다.
39. 새 direct `<a href="/api/projects/.../dashboard">`, `window.location` dashboard routing, browser API JSON navigation을 만들지 않는다.
40. Browser token persistence를 만들지 않는다. `localStorage`, `sessionStorage`, cookie, URL fragment token parsing, query parameter token parsing, refresh token browser storage를 사용하지 않는다.
41. 새 client-side route, SPA router, React/Vite/TypeScript/frontend build stack을 도입하지 않는다.
42. Project Selection과 Application List의 no dashboard shortcut, no first application auto-select, no application id inference guard를 유지한다.
43. Static UI contract test는 `ApplicationDashboardUiContractTest` 또는 동등한 이름으로 추가한다.
44. Static UI contract test는 dashboard action의 `data-dashboard-link` 기반 authenticated fetch와 `Authorization: Bearer <access_token>` header를 검증한다.
45. Static UI contract test는 invalid dashboard link, no token, `401`, `404`, generic error, loading, ready state가 safe copy로 렌더링되는지 검증한다.
46. Static UI contract test는 overlapping request, token clear, Project/Application 재선택 시 stale dashboard response가 현재 화면을 덮지 않는지 검증한다.
47. Static UI contract test는 metric state strip과 starter connection strip이 별도 axis로 렌더링되는지 검증한다.
48. Static UI contract test는 `zeroInsight`/`recovery` copy가 "정상", "문제 없음", "복구 완료", host down 확정으로 번역되지 않는지 검증한다.
49. Static UI contract test는 p95/p99와 histogram을 표시만 하고 UI-side percentile 계산 helper가 없는지 검증한다.
50. Static UI contract test는 endpoint priority를 root cause 확정 순위로 표현하거나 재정렬하지 않는지 검증한다.
51. Static UI contract test는 instance evidence, trend, snapshot detail, history API를 fetch하지 않는지 검증한다.
52. 기존 `ApplicationListUiContractTest`, `ProjectSelectionUiContractTest`, `ProjectEntryUiContractTest`는 Story 6.4의 legitimate dashboard field를 허용하되 Project Selection/Application List shortcut 금지선은 유지하도록 갱신한다.
53. `DashboardControllerTest`와 `DashboardReadModelServiceTest`의 current response shape, 404 mapping, no metrics p95/p99, source-scoped percentile, histogram evidence, endpoint bounded evidence, instance handoff regression은 유지한다.
54. 새 공개 class/method 또는 이해하기 어려운 helper/test를 만들면 `AGENTS.md` 지침에 따라 한국어 Javadoc/comment를 작성한다.
55. Suggested verification이 통과해야 implementation completion으로 볼 수 있다.

## Tasks / Subtasks

- [x] Application List -> Dashboard handoff 활성화 (AC: 1~9, 39~42)
  - [x] 현재 `applicationMarkup()`이 보존하는 `data-dashboard-link`를 Dashboard action click handler의 source로 사용한다.
  - [x] dashboard link가 `/api/projects/{projectId}/applications/{applicationId}/dashboard` shape이고 selected Project/Application id와 일치하는지 검증한다.
  - [x] 검증 실패 시 fetch 없이 invalid-link state를 렌더링한다.
  - [x] disabled handoff button을 authenticated Dashboard fetch action으로 전환하되 direct `<a href>`나 API JSON navigation을 만들지 않는다.
  - [x] Application List로 돌아가기/다른 Application 선택/Project 재선택 시 current Dashboard context를 안전하게 reset한다.

- [x] Dashboard fetch state machine 추가 (AC: 5~11, 38~41, 46)
  - [x] Project request, Application List request와 별도인 Dashboard request sequence guard를 둔다.
  - [x] `clearAccessToken()`과 Project/Application 재선택 시 stale dashboard snapshot을 초기화한다.
  - [x] loading, auth-required, invalid-link, not-found, generic error, ready state를 구현한다.
  - [x] `401`은 기존 authorization loss 흐름과 일관되게 처리한다.
  - [x] `404`와 generic error copy는 application health, host down, 정상/장애를 단정하지 않는다.

- [x] Application Dashboard shell/layout 구현 (AC: 10~17, 24, 35~37)
  - [x] `index.html`에 Dashboard panel 또는 section을 추가하되 기존 Project/Application/Setup flow를 깨지 않는다.
  - [x] `styles.css`에 Dashboard panel, state strip, starter connection strip, metrics/percentile/histogram/triage/endpoint/instance handoff의 responsive layout을 추가한다.
  - [x] mobile/desktop에서 긴 application name, endpoint route, instance name, reason/action copy, source labels가 겹치거나 넘치지 않도록 wrapping/stable dimensions를 둔다.
  - [x] Dashboard panel은 현재 선택된 Application context와 generatedAt/source window를 명확히 보여준다.
  - [x] Instance evidence와 snapshot/history는 pending handoff로만 보존한다.

- [x] Metric state strip과 starter connection strip 분리 렌더링 (AC: 12~17)
  - [x] Metric strip은 `state`, `application.freshness`, `application.lastAcceptedBucketAt`, `application.sourceWindow`, `metrics`를 중심으로 표시한다.
  - [x] Starter strip은 `starterConnection.statusSource`, `lastHeartbeatAt`, `lastHeartbeatStatus`, `connectionMeaning`, `stateImpact`를 별도 표시한다.
  - [x] heartbeat success를 app health success처럼 보이게 하는 copy/class 이름을 만들지 않는다.
  - [x] `statusSource=starter_heartbeat`와 `stateImpact=none`을 테스트 fixture와 DOM/assertion에 유지한다.

- [x] ZeroInsight/recovery rendering (AC: 18~23, 48)
  - [x] `triageCards.length === 0`이면 `zeroInsight` section을 렌더링한다.
  - [x] `zeroInsight.reasonCode/message/recommendedAction`은 서버 응답을 escape/formatting만 해서 표시한다.
  - [x] `recovery.isRecovering=true`이면 "회복 관찰 중" 의미의 copy를 사용한다.
  - [x] `retryAfterSeconds`와 `lastHealthyAt`은 자동 회복/완료가 아니라 source-aware guidance로 표현한다.

- [x] Percentile/histogram rendering (AC: 24~29, 49)
  - [x] `metrics`에는 request/error scalar만 표시한다.
  - [x] `sourceScopedPercentiles.source/scope/displayPolicy/aggregatePolicy/status/reason`을 표시 또는 test guard에서 보존한다.
  - [x] `sourceScopedPercentiles.items[]`의 source/instance/bucket/requestCount/p95Ms/p99Ms를 source-scoped point로 표시한다.
  - [x] missing/insufficient 상태는 source absence/evidence 부족 copy로 표시한다.
  - [x] `histogramDistribution.current/baseline`은 bucket distribution evidence로만 시각화한다.
  - [x] histogram-derived percentile, app/window percentile merge, endpoint percentile을 만들지 않는다.

- [x] Triage card와 endpoint priority rendering (AC: 30~34, 50)
  - [x] `triageCards[]`를 server-computed card로 렌더링하고 severity/score/confidence를 재산출하지 않는다.
  - [x] `endpointPriority[]`를 server order/rank 그대로 렌더링한다.
  - [x] endpoint priority section title/copy는 "먼저 확인할 endpoint" 또는 "Next check" 의미로 둔다.
  - [x] endpoint priority empty state는 source/freshness/evidence 조건 때문에 표시할 next-check surface가 없다는 방향으로 표현한다.
  - [x] raw path/query/trace/per-request sample이나 endpoint p95/p99를 표시하지 않는다.

- [x] Static UI contract tests 작성/갱신 (AC: 1~55)
  - [x] `ApplicationDashboardUiContractTest`를 신규 추가하거나 동등한 static UI contract test를 추가한다.
  - [x] Node VM 또는 구조 검증으로 실제 `app.js` dashboard fetch/render state machine을 실행/검증한다.
  - [x] authenticated dashboard fetch, invalid link fail-closed, safe states, stale response guard를 검증한다.
  - [x] current `ApplicationDashboardReadModel` field consumption과 source-axis separation을 검증한다.
  - [x] no token persistence, no URL token parsing, no frontend stack, no UI-side recomputation, no 후속 API fetch guard를 검증한다.
  - [x] 6.3의 `ApplicationListUiContractTest`가 Dashboard 활성화를 막는 broad disabled assertion을 유지하고 있다면 6.4 scope에 맞게 좁힌다.
  - [x] `ProjectSelectionUiContractTest`와 `ProjectEntryUiContractTest`는 Project Selection 단계의 dashboard shortcut 금지와 setup guide scope를 계속 검증한다.
  - [x] 새 test class/helper에는 한국어 Javadoc/comment를 작성한다.

- [x] Backend focused regression 유지 (AC: 10, 24~38, 53~55)
  - [x] `DashboardControllerTest`의 serialization, 404 mapping, no query parameter guard를 유지한다.
  - [x] `DashboardReadModelServiceTest`의 zeroInsight/recovery, source-scoped percentile, histogram evidence, endpoint priority, instance handoff guard를 유지한다.
  - [x] backend shape 확장이 필요해 보이면 구현하지 말고 correct-course 또는 별도 contract decision으로 올린다.
  - [x] full portal test와 `git diff --check`를 통과시킨다.

## Dev Notes

### Current Code State

- 현재 `sprint-status.yaml` 기준 Epic 6은 `in-progress`, Story 6.1~6.3은 `done`, Story 6.4는 create-story 전 `backlog`였다.
- 현재 static dashboard는 `observability-portal/src/main/resources/static/dashboard/index.html`, `styles.css`, `app.js` 세 파일로 구성된다.
- 현재 `app.js`는 `window.observationPortalAuth.setAccessToken(accessToken)`과 `clearAccessToken()` in-memory hook만 제공한다. Browser token persistence, URL token parsing, cookie session은 없다.
- 현재 Project fetch와 Application List fetch는 `projectRequestHeaders()`로 `Authorization: Bearer <access_token>` header를 붙인다.
- 현재 Application List는 Project의 `data-applications-link`를 authenticated fetch로 사용하고, request sequence guard로 stale application response를 막는다.
- 현재 Application item은 `links.dashboard`를 `data-dashboard-link`로 보존하지만 button은 disabled 상태다. Story 6.4는 이 handoff를 활성화한다.
- 현재 `safeDashboardLink()`와 `isApplicationDashboardLink()`는 `/api/projects/{projectId}/applications/{applicationId}/dashboard` shape와 id 일치를 검증한다. Story 6.4는 이 검증을 재사용하거나 Dashboard fetch용 이름으로 정리할 수 있다.
- 현재 Application List rendering은 accepted bucket axis와 starter connection axis를 분리하고, malformed application payload를 fail-closed 처리한다. Dashboard response에도 같은 fail-closed 원칙을 적용한다.
- 현재 setup guide aside는 Story 6.1 범위인 dependency, portal base URL, project key, environment만 안내한다. Story 6.4에서 setup guide copy를 dashboard/instance/history 안내로 확장하지 않는다.

### Current Dashboard API and Read Model Contract

- `DashboardController`는 `GET /api/projects/{projectId}/applications/{applicationId}/dashboard`를 노출하고, `DashboardReadModelService.getDashboard(projectId, applicationId)` 결과가 없으면 `404 Not Found`로 매핑한다.
- `DashboardController`는 path variable 변환과 404 mapping만 담당한다. Dashboard 조립과 state 판단은 service 계층 책임이다.
- `DashboardReadModelService`는 query 시점 기준 current 15분과 직전 baseline 15분 window를 조립한다.
- `ApplicationDashboardReadModel` current top-level shape:
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
- `application` shape는 `projectId`, `applicationId`, `name`, `environment`, `lastAcceptedBucketAt`, `lastHealthyAt`, `sourceWindow`, `freshness`를 포함한다.
- `metrics` shape는 `requestCount`, `errorCount`, `errorRate`만 포함한다. p95/p99/avg/max scalar는 없다.
- 현재 code/test의 `sourceScopedPercentiles.source` literal은 `starter_local`이고, 계약 의미는 starter canonical/source-scoped percentile이다. UI는 source 값을 하드코딩해 바꾸지 말고 서버 응답 field와 source/scope/aggregatePolicy를 그대로 표시한다. literal rename이 필요하면 backend/contract decision을 별도로 열어야 한다.
- `sourceScopedPercentiles.items[]`는 instance별 current window latest starter percentile point이며 평균/최댓값/병합 대상이 아니다.
- `histogramDistribution`은 `histogram_bucket_distribution` source의 current/baseline bucket distribution evidence다. p95/p99, delta, regression, confidence, rule 판단을 포함하지 않는다.
- `triageCards=[]`이면 model constructor가 `zeroInsight != null`을 요구한다.
- `recovery.isRecovering=true`는 stale/down 이후 새 bucket은 왔지만 sample이 부족한 회복 관찰 상태다.
- `endpointPriority[]`는 server-computed next-check surface다. `rank`가 있어도 UI가 root cause 확정 순위로 해석하지 않는다.
- `instances[]`는 최대 50개 bounded handoff entry이며 `links.evidence`를 포함한다. Story 6.4는 이를 표시/보존만 하고 Instance Evidence API를 호출하지 않는다.
- 현재 `DashboardReadModelService` current response의 `snapshot`은 null일 수 있다. UI는 null/non-null 모두 안전하게 처리하되 Snapshot Detail API fetch는 후속 Story 6.7로 넘긴다.

### Previous Story Intelligence

- Story 6.3은 Project Selection이 보존한 `data-applications-link`를 authenticated Application List fetch로 연결했다. Story 6.4는 동일한 방식으로 Application List가 보존한 `data-dashboard-link`를 authenticated Dashboard fetch로 연결한다.
- Story 6.3 review patch는 Project mismatch response, malformed application payload, stale Project reload, invalid dashboard link 단독 gap을 fail-closed로 막았다. Dashboard response도 selected Project/Application context mismatch와 malformed payload를 fail-closed로 처리해야 한다.
- Story 6.3에서 broad static guard를 삭제만 하지 않고 Project Selection scope와 Application List scope를 분리해 갱신한 방식이 중요하다. Story 6.4도 기존 Application List disabled dashboard assertion을 "Dashboard handoff가 활성화되지만 Project Selection shortcut은 없다"로 좁혀야 한다.
- Epic 5 retrospective의 핵심 학습은 "UI가 친절해지려다가 계산을 되가져오지 않는 것"이다. Story 6.4는 이 위험이 가장 큰 화면이다.
- 최근 git history는 `feat: implement application list UI`와 `fix: harden application list UI review findings` 흐름을 보여준다. 6.4도 먼저 static contract를 세우고 payload/stale/auth edge case를 좁게 막는 패턴을 따르는 편이 안전하다.

### UI Copy Guardrails

- Dashboard는 primary first-screen이지만 "확정 장애 판정기"가 아니다. Server read model이 준 state/copy와 source를 표시한다.
- `triageCards=[]`는 "정상"이나 "문제 없음"과 같지 않다. 반드시 `zeroInsight` reason/action을 표시한다.
- `observing_recovery`와 `recovery.isRecovering=true`는 "복구 완료"가 아니라 회복 관찰 상태다.
- `telemetry_unreachable`은 host application down 확정이 아니다.
- 최근 heartbeat와 없음/오래된 accepted bucket 조합은 no recent traffic/waiting first data/metric data idle 계열로 표현한다.
- endpoint priority copy는 "먼저 확인할 endpoint", "next check", "server-computed evidence surface" 의미를 유지한다.
- empty/missing percentile, histogram, endpoint priority, instance handoff는 source absence/evidence 부족으로 말하고 current health를 단정하지 않는다.

## Architecture Constraints

- Active baseline은 Traditional MVC + Service/Repository Layering이다.
- Portal package는 feature-first MVC 구조이며 `domain`은 business feature namespace다.
- 금지 package는 `application`, `port`, `adapter`, `adapter.in`, `adapter.out`다.
- Controller는 repository를 직접 호출하지 않고 service에 위임한다.
- Repository는 controller/dto에 의존하지 않는다.
- JPA entity를 API response, public DTO, service external result로 노출하지 않는다.
- Flyway migration이 physical schema source of truth다. Story 6.4는 schema/migration 변경을 기대하지 않는다.
- Static dashboard UI는 portal runtime 안에서 제공되며 `observability-portal/src/main/resources/static/dashboard/` 안에서 구현한다.
- UI는 server read model을 표시만 한다. UI/controller/repository는 lifecycle state, starter diagnosis, zeroInsight, recovery, rule, p95/p99, endpoint priority, snapshot/history event를 계산하지 않는다.
- Browser token persistence, SPA routing, frontend build stack 도입은 open decision이며 Story 6.4에서 닫지 않는다.
- 새 공개 class/method/helper/test에는 `AGENTS.md` 기준으로 한국어 Javadoc/comment를 작성한다.

## Open Decisions / Risks

- Browser token persistence, refresh token browser storage, logout/session persistence는 여전히 open decision이다. Story 6.4에서 닫지 않는다.
- React/Vite/TypeScript/SPA routing 전환 시점과 source/build output 구조는 open decision이다. Story 6.4에서 정하지 않는다.
- Instance Evidence UI layout과 evidence drill-down interaction은 Story 6.5 책임이다.
- Instance Snapshot Trend와 Snapshot/History marker UI를 Epic 6 MVP에 포함할지 demo-only로 둘지는 제품 open decision으로 남아 있다. Story 6.4는 handoff data만 보존한다.
- Demo green path/failure-recovery fixture와 seed strategy는 Story 6.8/6.9 책임이다.
- Project creation, ownership, role model, project key 발급/회전/재발급 workflow는 open decision이다.
- `sourceScopedPercentiles.source` 문구는 일부 문서 예시의 `starter_canonical_percentile`과 current code/test의 `starter_local` 사이에 literal drift가 있다. 6.4 UI는 서버 값을 그대로 표시하고 의미를 source-scoped starter percentile로 설명해야 한다.
- `snapshot` top-level field는 현재 model에서 `Object`이고 current response에서 null일 수 있다. UI가 snapshot detail을 당겨오지 않도록 null/non-null handoff만 안전하게 처리해야 한다.
- 가장 큰 구현 위험은 static JS helper가 formatting helper를 넘어 state/rule/priority/percentile 계산 helper가 되는 것이다. `ApplicationDashboardUiContractTest`에서 금지 helper와 후속 API fetch 부재를 직접 검증해야 한다.

## Testing / Suggested commands

Focused test 대상 후보:

- `ApplicationDashboardUiContractTest` 신규 추가
- `ApplicationListUiContractTest` 갱신
- `ProjectSelectionUiContractTest` 갱신 또는 regression 유지
- `ProjectEntryUiContractTest` 갱신 또는 regression 유지
- `DashboardControllerTest`
- `DashboardReadModelServiceTest`
- `MvcLayerBoundaryTest`

필수 scenario:

- Application item의 `data-dashboard-link` 또는 `links.dashboard`가 authenticated Dashboard fetch로 이어진다.
- Dashboard fetch는 `Authorization: Bearer <access_token>` header를 사용한다.
- Invalid link, no-token, `401`, `404`, generic error, loading, ready state가 safe copy로 렌더링된다.
- Token clear, Project/Application 재선택, overlapping request에서 stale dashboard response가 현재 화면을 덮어쓰지 않는다.
- Dashboard UI가 current `ApplicationDashboardReadModel` field만 사용한다.
- selected Project/Application context와 response `application.projectId/applicationId`가 다르면 fail-closed 처리한다.
- metric state strip과 starter connection strip이 별도 axis로 표시된다.
- `triageCards=[]`일 때 `zeroInsight`가 표시된다.
- recovery copy가 "복구 완료"로 표현되지 않는다.
- p95/p99는 source/instance/bucket scope와 함께 표시되고, histogram은 distribution evidence로만 표시된다.
- endpoint priority는 server order 그대로 next-check surface로 표시된다.
- `instances[].links.evidence`, `snapshot`은 후속 story handoff로만 보존되고 관련 API를 fetch하지 않는다.
- Static UI가 token persistence, URL token parsing, refresh token browser storage를 만들지 않는다.
- Static UI가 lifecycle state, starter diagnosis, zeroInsight, recovery, rule, p95/p99, endpoint priority, instance health, snapshot/history event helper를 만들지 않는다.
- Project Selection/Application List에는 dashboard shortcut, first application auto-select, application id inference가 없다.

Suggested commands:

```bash
./gradlew :observability-portal:test --tests '*ApplicationDashboardUiContract*'
./gradlew :observability-portal:test --tests '*ApplicationListUiContract*'
./gradlew :observability-portal:test --tests '*ProjectSelectionUiContract*'
./gradlew :observability-portal:test --tests '*ProjectEntryUiContract*'
./gradlew :observability-portal:test --tests '*DashboardController*'
./gradlew :observability-portal:test --tests '*DashboardReadModelService*'
./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest
./gradlew :observability-portal:test
git diff --check
```

테스트 이름은 구현 시점의 실제 class 이름에 맞게 조정할 수 있다. 단, static UI contract guard가 dashboard link authenticated fetch, current dashboard shape consumption, source-axis separation, safe states, stale response guard, no token persistence, no URL token parsing, no frontend stack, no 후속 API fetch, no UI-side recomputation을 검증해야 한다.

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- 2026-05-28: BMAD dev-story workflow를 시작하고 sprint/story status를 in-progress로 전환했다.
- 2026-05-28: `ApplicationDashboardUiContractTest`를 추가하고 실패를 확인한 뒤 Dashboard static UI 구현을 진행했다.
- 2026-05-28: Dashboard panel, authenticated fetch state machine, current read model 렌더링, stale response guard, 후속 API fetch 금지 guard를 구현했다.
- 2026-05-28: `ApplicationListUiContractTest`, `ProjectSelectionUiContractTest`, `ProjectEntryUiContractTest` guard를 Story 6.4 dashboard field 허용 범위로 좁혔다.
- 2026-05-28: Story suggested command 전체와 `git diff --check` 최종 통과를 확인했다.
- 2026-05-28: BMAD create-story workflow와 customization을 확인했다.
- 2026-05-28: Story 6.4 contract decision, Epic 6 context, Story 6.3, Epic 5 retrospective, read model/API/sprint/product source 문서를 확인했다.
- 2026-05-28: 현재 static dashboard asset, DashboardController, ApplicationDashboardReadModel, DashboardReadModelService, 관련 focused test를 확인했다.
- 2026-05-28: Story 6.4 create-story 산출물을 `planning-artifacts/stories/6-4-application-dashboard-ui-integration.md`에 생성했다.

### Implementation Plan

1. `ApplicationDashboardUiContractTest`를 먼저 작성해 `data-dashboard-link` authenticated fetch, current dashboard shape, source-axis separation, safe state, stale response guard, no recomputation/no 후속 API fetch를 고정한다.
2. `app.js`에 Dashboard request sequence와 selected Dashboard context를 추가하고, Application item의 safe `data-dashboard-link`를 검증한 뒤 in-memory access token으로 fetch한다.
3. Dashboard panel은 Application context, metric state strip, starter connection strip, metrics, source-scoped percentile, histogram, zeroInsight/recovery, triage cards, endpoint priority, instance handoff를 표시만 한다.
4. `index.html`/`styles.css`에서 Dashboard panel과 responsive layout을 추가하고 Project/Application/Setup scope를 유지한다.
5. 기존 Application List/Project Selection/Project Entry contract guard를 Story 6.4 정상 field와 충돌하지 않게 좁히되 shortcut/recomputation 금지선은 유지한다.

### Completion Notes List

- Application List item의 `data-dashboard-link`를 source로 삼아 내부 `/api/projects/{projectId}/applications/{applicationId}/dashboard` link를 검증한 뒤 in-memory Bearer token으로 Dashboard fetch를 수행한다.
- Dashboard 전용 request sequence guard와 safe states를 추가해 invalid link/no token/401/404/generic error/overlapping request/token clear/Project 재선택 상황에서 stale response가 화면을 덮지 않게 했다.
- Application Dashboard panel은 current `ApplicationDashboardReadModel`의 top-level field를 렌더링하며 metric state strip과 starter connection strip을 분리했다.
- zeroInsight/recovery, source-scoped p95/p99, histogram distribution, triage card, endpoint next-check, instance/snapshot handoff를 표시만 하고 후속 evidence/snapshot/history API fetch와 UI-side recomputation은 만들지 않았다.
- Static UI contract test를 추가하고 기존 Project/Application guard를 6.4 dashboard field 허용 범위에 맞게 좁혔다.

### File List

- `observability-portal/src/main/resources/static/dashboard/index.html`
- `observability-portal/src/main/resources/static/dashboard/styles.css`
- `observability-portal/src/main/resources/static/dashboard/app.js`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ApplicationDashboardUiContractTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ApplicationListUiContractTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ProjectSelectionUiContractTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ProjectEntryUiContractTest.java`
- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/stories/6-4-application-dashboard-ui-integration.md`

### Change Log

- 2026-05-28: Story 6.4 Dashboard UI integration 구현을 완료하고 review 상태로 전환했다.
- 2026-05-28: Story 6.4 Application dashboard UI integration create-story 산출물을 생성하고 ready-for-dev 상태로 전환했다.
