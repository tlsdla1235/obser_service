---
artifactType: story
storyId: "6.3"
storyKey: "6-3-application-list-ui"
epic: "Epic 6. Dashboard User Flow and Demo Hardening"
title: "Application list UI"
architectureStyle: Traditional MVC
status: done
date: 2026-05-28
baseline_commit: f8dbd4e6ca81b90d890fbca268cf1d20ff0b75fc
---

# Story 6.3 - Application list UI

## Status

done

## Story

portal 사용자는 Project를 선택한 뒤 해당 Project 안의 Application 목록을 인증된 요청으로 불러와 빠르게 스캔하고 싶다.

그래야 Project Selection이 보존한 `data-applications-link` 또는 `links.applications`를 사용해 `GET /api/projects/{projectId}/applications` read-only navigation read model을 안전하게 소비하고, Application Dashboard 구현을 앞당기지 않으면서 dashboard 진입 전 scope를 application/environment 단위로 좁힐 수 있다.

## Source of Truth

아래 문서를 읽고 반영한 BMAD create-story context다. 구현 중 충돌처럼 보이는 지점은 `implementation-artifacts/epic-6-context.md`, Story 6.2 review fix spec, 그리고 현재 `GET /api/projects/{projectId}/applications` 코드 shape를 우선한다.

1. `implementation-artifacts/sprint-status.yaml`
2. `implementation-artifacts/epic-6-context.md`
3. `planning-artifacts/epics.md`
4. `planning-artifacts/sprint-plan.md`
5. `planning-artifacts/current-product-source-of-truth.md`
6. `planning-artifacts/stories/6-2-project-selection-ui.md`
7. `implementation-artifacts/spec-6-2-project-selection-ui-code-review-fixes.md`
8. `implementation-artifacts/spec-story-6-2-project-selection-ui-contract-decisions.md`
9. `planning-artifacts/stories/5-1-project-application-navigation-read-model.md`
10. `planning-artifacts/api-surface.md`
11. `planning-artifacts/project-structure.md`
12. `planning-artifacts/contracts/account-auth-policy.md`
13. `planning-artifacts/contracts/read-model-contract.md`
14. `planning-artifacts/contracts/dashboard-read-model.md`
15. `AGENTS.md`
16. `_bmad/custom/project-context.md`

추가로 architecture guard 맥락을 확인하기 위해 `planning-artifacts/architecture.md`, `planning-artifacts/architecture-implementation-supplement.md`, `implementation-artifacts/epic-5-retro-2026-05-27.md`를 참고했다.

확인한 현재 코드:

1. `observability-portal/src/main/java/com/observation/portal/domain/catalog/controller/ProjectNavigationController.java`
2. `observability-portal/src/main/java/com/observation/portal/domain/catalog/model/ProjectApplicationNavigationReadModel.java`
3. `observability-portal/src/main/java/com/observation/portal/domain/catalog/model/ProjectNavigationReadModel.java`
4. `observability-portal/src/main/java/com/observation/portal/domain/catalog/service/ProjectApplicationNavigationService.java`
5. `observability-portal/src/main/resources/static/dashboard/index.html`
6. `observability-portal/src/main/resources/static/dashboard/app.js`
7. `observability-portal/src/main/resources/static/dashboard/styles.css`
8. `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ProjectSelectionUiContractTest.java`
9. `observability-portal/src/test/java/com/observation/portal/domain/catalog/controller/ProjectNavigationControllerTest.java`
10. `observability-portal/src/test/java/com/observation/portal/architecture/MvcLayerBoundaryTest.java`

## Scope / Out of Scope

포함:

- 기존 `observability-portal/src/main/resources/static/dashboard/` static asset 안에서 Application List view/state 추가
- Story 6.2가 보존한 Project item의 `data-applications-link` 또는 response `links.applications`를 사용한 authenticated `fetch`
- `GET /api/projects/{projectId}/applications` current shape 소비
- Application List loading, auth-required, error, empty, project-not-found, ready state
- Project context, generatedAt, application name, environment, lifecycle badge, last accepted bucket, starter connection summary, top concern 0~1개 표시
- Application item의 `links.dashboard`를 dashboard 진입 link로 보존
- Project Selection으로 돌아가기 또는 다른 Project 선택 흐름 유지
- optional application name/environment display filter
- static UI contract test 신규 추가 또는 기존 dashboard contract test 확장
- `GET /api/projects/{projectId}/applications` serialization focused regression 유지
- no dashboard shortcut/auto-select/application id inference, no token persistence, no frontend stack, no UI-side recomputation guard

제외:

- Application Dashboard UI 구현
- dashboard current API fetch/rendering
- Project Selection 단계의 dashboard shortcut
- 첫 application 자동 선택
- application id를 URL, dashboard link, index, 이름, environment에서 추론하는 동작
- Project creation/onboarding, public `POST /api/projects`, 로그인 직후 자동 project 생성
- backend API/schema/read model 확장
- lifecycle state, starter connection diagnosis, recovery, zeroInsight, insight rule, p95/p99, endpoint priority, snapshot/history event, project/application ranking 계산
- React, Vite, TypeScript, `package.json`, `src/main/frontend`, 별도 frontend build/deploy
- view resolver/template engine
- `localStorage`, `sessionStorage`, cookie, URL fragment/query token parsing, refresh token browser storage, logout/session persistence policy 확정

## Acceptance Criteria

1. Application List UI는 `GET /api/projects/{projectId}/applications` current shape만 소비한다.
2. Application List fetch endpoint는 Story 6.2가 보존한 Project action의 `data-applications-link` 또는 Project response의 `links.applications`에서 가져온다.
3. UI는 application list path를 `projectId`로 재구성하지 않고, 서버가 준 link가 안전한 내부 `/api/projects/{projectId}/applications` shape인지 검증한 뒤 사용한다.
4. Application List fetch는 in-memory access token hook이 보관한 access token으로 `Authorization: Bearer <access_token>` header를 구성한다.
5. Token이 없거나 `401`이 반환되면 safe auth-required state를 보여주고, token/provider/internal payload를 노출하지 않는다.
6. Project Selection의 pending Application List action은 실제 Application List fetch로 연결되지만, direct unauthenticated browser navigation to `/api/projects/**`를 만들지 않는다.
7. Overlapping request, token clear, Project 재선택 상황에서 stale Project/Application response가 현재 화면을 덮어쓰지 않는다.
8. `GET /api/projects/{projectId}/applications`가 `404`를 반환하면 project-not-found state를 보여주고 project/application health를 단정하지 않는다.
9. Applications가 비어 있으면 "catalog/first accepted bucket/source absence" 중심의 empty state를 보여주고 application 정상/장애를 단정하지 않는다.
10. Generic error state는 provider token, service token, secret, raw payload, stack trace, repository/internal exception detail을 노출하지 않는다.
11. UI와 static contract test는 current field인 `generatedAt`, `project.projectId`, `project.name`, `applications[].applicationId`, `name`, `environment`, `metricData`, `starterConnection`, `lifecycleBadge`, `topConcern`, `links.dashboard`를 Application List 계약으로 사용한다.
12. `metricData.statusSource`는 `accepted_bucket` source로 표시하고, `lastAcceptedBucketAt`과 `freshnessLabel`은 서버 응답을 formatting/escaping만 해서 보여준다.
13. `starterConnection.statusSource`는 `starter_heartbeat` source로 표시하고, `lastHeartbeatAt`, `heartbeatStatus`, `freshnessLabel`, `connectionMeaning`, `stateImpact`는 서버 응답을 formatting/escaping만 해서 보여준다.
14. UI는 accepted bucket freshness와 starter heartbeat summary를 하나의 `health`, `hostHealth`, `applicationHealth`, `status`, `severity` 판단으로 합치지 않는다.
15. `lifecycleBadge`는 server-computed light navigation badge로만 렌더링한다.
16. UI는 lifecycle badge code/label/source를 새로 계산하거나, accepted bucket/heartbeat/topConcern을 조합해 lifecycle badge를 만들지 않는다.
17. `topConcern`은 서버가 준 최대 0~1개 light summary만 표시한다.
18. `topConcern=null`이면 concern source absence로 표현하고 "문제 없음", "정상", "복구 완료" 같은 current 판단으로 번역하지 않는다.
19. Application List는 dashboard 진입 전 상태 스캔만 돕고 상세 triage, endpoint priority, p95/p99 판단을 대체하지 않는다.
20. Application item의 `links.dashboard`는 해당 item의 Dashboard 진입 link로만 보존한다.
21. `links.dashboard`는 Project Selection 단계나 Project card로 역류하지 않는다.
22. Story 6.3은 Application Dashboard UI, dashboard current API fetch, dashboard read model rendering을 구현하지 않는다. 필요하면 `links.dashboard`를 후속 Story 6.4 handoff data로 보존한다.
23. UI는 Project Selection에서 dashboard shortcut, first application auto-select, application id 추론, "최근/위험 application 자동 진입"을 만들지 않는다.
24. Application List는 application id를 API item의 `applicationId` field에서만 읽는다. `links.dashboard`, row index, name/environment, DOM id에서 application id를 추론하지 않는다.
25. Optional application filter는 이미 받은 application list의 표시 범위만 좁히며, backend query, ranking, risk sorting, topConcern 생성/요약을 하지 않는다.
26. Static dashboard asset boundary를 유지하고 `index.html`, `styles.css`, `app.js` 안에서만 UI를 구현한다.
27. `src/main/frontend`, `package.json`, React, Vite, TypeScript, 별도 frontend build/deploy, view resolver/template engine을 도입하지 않는다.
28. Browser token persistence를 만들지 않는다. `localStorage`, `sessionStorage`, cookie, URL fragment token parsing, query param token parsing, refresh token browser storage를 사용하지 않는다.
29. Client-side code는 formatting, escaping, loading/empty/error rendering, auth header 구성, safe link validation, local filter, selected Project/Application handoff 같은 presentation helper만 둔다.
30. UI-side lifecycle state, starter connection diagnosis, zeroInsight, recovery, rule, p95/p99, endpoint priority, snapshot/history event, project/application ranking 계산 helper를 만들지 않는다.
31. Story 6.1 setup guide aside는 dependency, `observation.heartbeat.portal-base-url`, `observation.heartbeat.project-key`, `observation.metric-flush.environment` 범위에 머문다.
32. Public project creation flow, `Create Project`, `POST /api/projects`, 로그인 직후 자동 project 생성 copy/API를 만들지 않는다.
33. Static UI contract test는 applications link authenticated fetch, current application shape field, source-axis separation, safe states, `links.dashboard` handoff, no Project Selection dashboard shortcut, no auto-select, no app id inference를 검증한다.
34. 기존 `ProjectSelectionUiContractTest`와 `ProjectEntryUiContractTest`는 Story 6.3 이후의 legitimate Application List field(`applicationId`, `links.dashboard`, `lifecycleBadge`)를 허용하되, Project Selection 단계에서 해당 field가 shortcut으로 쓰이지 않는지 더 좁게 검증하도록 갱신한다.
35. Focused controller regression은 `GET /api/projects/{projectId}/applications` serialization과 `404` mapping, `GET /api/projects` shape, no public `POST /api/projects`를 계속 검증한다.
36. 새 공개 class/method 또는 이해하기 어려운 helper/test를 만들면 `AGENTS.md` 지침에 따라 한국어 Javadoc/comment를 작성한다.
37. Suggested verification이 통과해야 implementation completion으로 볼 수 있다.

## Tasks / Subtasks

- [x] Project Selection -> Application List handoff 구현 (AC: 2~7, 20~24)
  - [x] Story 6.2가 만든 `data-applications-link`를 click handler의 source로 사용한다.
  - [x] link가 `/api/projects/{projectId}/applications` shape이고 Project item과 일치하는지 검증한다.
  - [x] 검증 실패 시 Application List fetch를 하지 않고 safe invalid-link state를 표시한다.
  - [x] direct `<a href="/api/projects/...">` navigation 대신 authenticated `fetch`로 전환한다.
  - [x] Project Selection 단계에는 `links.dashboard`, dashboard shortcut, first application auto-select를 만들지 않는다.

- [x] Application List fetch state machine 추가 (AC: 4~10, 28~30)
  - [x] current Project request와 Application List request를 구분하는 latest-request guard를 둔다.
  - [x] `clearAccessToken()`과 Project 재선택 시 stale application snapshot/filter를 초기화한다.
  - [x] loading, auth-required, generic error, project-not-found, empty, ready, filtered-empty state를 구현한다.
  - [x] error/auth copy는 token/provider/internal payload를 노출하지 않는다.
  - [x] reload는 현재 선택된 Project의 applications link를 다시 fetch한다.

- [x] Application List rendering 구현 (AC: 1, 11~20, 24~25, 29~30)
  - [x] response `generatedAt`과 `project.projectId/name`을 표시한다.
  - [x] application row/card에 `name`, `environment`, `applicationId`를 표시하되 id는 API field에서만 읽는다.
  - [x] `metricData.statusSource`, `lastAcceptedBucketAt`, `freshnessLabel`을 accepted bucket axis로 표시한다.
  - [x] `starterConnection.statusSource`, `lastHeartbeatAt`, `heartbeatStatus`, `freshnessLabel`, `connectionMeaning`, `stateImpact`를 starter heartbeat axis로 표시한다.
  - [x] `lifecycleBadge.source/code/label`은 server-computed light badge로만 표시한다.
  - [x] `topConcern`은 있으면 최대 1개만 표시하고, 없으면 source absence copy로 처리한다.
  - [x] application name/environment local filter를 추가하는 경우 표시 범위만 좁히고 ranking/sorting/recomputation을 하지 않는다.

- [x] Dashboard handoff boundary 보존 (AC: 20~23)
  - [x] Application item의 `links.dashboard`를 item-level Dashboard action의 `data-dashboard-link` 또는 후속 route handoff data로 보존한다.
  - [x] Dashboard action은 Story 6.4가 dashboard read model fetch/render를 구현할 수 있도록 link를 유지한다.
  - [x] Story 6.3에서 dashboard current API fetch/rendering, dashboard panel, zeroInsight/triage/endpointPriority UI를 구현하지 않는다.
  - [x] `links.dashboard`가 Project Selection card/action/test fixture로 역류하지 않게 한다.

- [x] Static dashboard layout polish (AC: 8~10, 19, 26~31)
  - [x] `index.html`, `styles.css`, `app.js` 안에서 Project Selection과 Application List를 같은 static dashboard shell에 배치한다.
  - [x] mobile/desktop에서 Project list, Application list, setup aside, reload/filter controls가 겹치지 않도록 responsive constraints를 둔다.
  - [x] 긴 application name, environment, badge, source label, concern label이 card 밖으로 넘치지 않도록 wrapping/stable dimensions를 둔다.
  - [x] Story 6.1 setup guide aside scope를 변경하지 않는다.

- [x] Static UI contract tests 작성/갱신 (AC: 1~34, 36)
  - [x] `ApplicationListUiContractTest` 신규 추가 또는 기존 dashboard UI contract test를 확장한다.
  - [x] Project Selection action의 `data-applications-link`가 authenticated fetch로 연결되는지 Node VM 또는 구조 검증으로 확인한다.
  - [x] Application List가 current application response field만 사용하는지 검증한다.
  - [x] `links.dashboard`가 application item handoff에만 있고 Project Selection shortcut으로 쓰이지 않는지 검증한다.
  - [x] dashboard current API fetch/render, first application auto-select, application id inference, project creation flow가 없는지 검증한다.
  - [x] no frontend stack, no token persistence, no URL token parsing, no UI-side recomputation guard를 유지한다.
  - [x] Story 6.2 테스트의 broad `doesNotContain("applicationId", "links.dashboard", "/dashboard", "lifecycle")` 계열 assertion은 Project Selection scope assertion으로 좁혀 Story 6.3 정상 field 렌더링과 충돌하지 않게 한다.
  - [x] 새 test class/helper에는 한국어 Javadoc/comment를 작성한다.

- [x] Backend focused regression 유지 (AC: 1, 8, 11~16, 35~36)
  - [x] `ProjectNavigationControllerTest`의 `GET /api/projects/{projectId}/applications` serialization 검증을 유지하거나 필요한 경우 강화한다.
  - [x] `404` project-not-found mapping을 계속 검증한다.
  - [x] `GET /api/projects` shape와 no public `POST /api/projects` route guard를 유지한다.
  - [x] backend API/schema/read model 확장이 필요해 보이면 구현하지 말고 correct-course로 올린다.

### Review Findings

- [x] [Review][Patch] Application List 응답의 Project mismatch를 거르지 않아 다른 Project의 metadata/dashboard handoff를 렌더링할 수 있다 [observability-portal/src/main/resources/static/dashboard/app.js:269]
- [x] [Review][Patch] Application item shape validation이 lifecycleBadge/topConcern malformed payload를 ready 상태로 렌더링할 수 있다 [observability-portal/src/main/resources/static/dashboard/app.js:518]
- [x] [Review][Patch] Project 목록 reload 후 선택된 Project가 사라져도 이전 Application List가 stale 상태로 남을 수 있다 [observability-portal/src/main/resources/static/dashboard/app.js:91]
- [x] [Review][Patch] Node VM contract가 valid applicationId + invalid dashboard link 단독 fail-closed 회귀를 직접 검증하지 않는다 [observability-portal/src/test/java/com/observation/portal/domain/dashboard/ApplicationListUiContractTest.java:316]

## Dev Notes

### Current Code State

- 현재 `sprint-status.yaml` 기준 Epic 6은 `in-progress`, Story 6.1과 6.2는 `done`, Story 6.3은 create-story 전 `backlog`였다.
- 현재 static dashboard는 `observability-portal/src/main/resources/static/dashboard/index.html`, `styles.css`, `app.js` 세 파일로 구성된다.
- 현재 `app.js`는 `window.observationPortalAuth.setAccessToken(accessToken)`과 `clearAccessToken()` in-memory hook을 제공하고, `GET /api/projects`에만 `Authorization: Bearer <access_token>` header를 붙인다.
- 현재 `app.js`는 Project view state로 loading/auth-required/error/empty/ready/filtered-empty를 갖고, request sequence guard로 stale Project response를 무시한다.
- Story 6.2 review fix 이후 Project item의 Applications button은 disabled pending action이며, 서버가 준 `links.applications` 값을 `data-applications-link`에 보존한다. Story 6.3은 이 pending boundary를 실제 authenticated Application List fetch로 이어야 한다.
- 현재 `safeApplicationsLink`는 `/api/projects/{projectId}/applications` shape와 project id 일치를 검증한다. Story 6.3은 이 검증을 재사용하거나 이름을 더 명확히 바꿔 사용할 수 있다.
- 현재 `ProjectSelectionUiContractTest`는 6.2 시점 기준으로 `links.dashboard`, `/dashboard`, `applicationId`, `lifecycle`이 static page에 없다고 넓게 검증한다. Story 6.3 구현 시 이 assertion은 반드시 범위를 좁혀야 한다. Application List UI는 `applications[].applicationId`, `lifecycleBadge`, `links.dashboard`를 current shape로 소비해야 하기 때문이다.
- 현재 `ProjectEntryUiContractTest.staticUiDoesNotRecalculateEpicFiveReadModelJudgement`도 `lifecycle` 문자열 부재를 broad guard로 둔다. Story 6.3에서는 `lifecycleBadge` field 렌더링은 허용하고 `calculateLifecycle`, transition table, diagnosis/ranking/recompute helper 부재를 검증하도록 바꿔야 한다.
- 현재 setup guide aside는 Story 6.1 범위인 dependency와 세 property만 안내한다. Story 6.3에서 application-name/instance/dashboard tuning/raw explorer/p95/p99/endpoint priority 안내를 추가하지 않는다.

### Current API and Read Model Contract

- `ProjectNavigationController`는 `GET /api/projects`와 `GET /api/projects/{projectId}/applications`를 read-only endpoint로 노출한다.
- `GET /api/projects/{projectId}/applications`는 service가 project를 찾지 못하면 `404 Not Found`를 반환한다.
- `ProjectApplicationNavigationReadModel` current shape:
  - top-level: `generatedAt`, `project`, `applications`
  - project: `projectId`, `name`
  - application item: `applicationId`, `name`, `environment`, `metricData`, `starterConnection`, `lifecycleBadge`, `topConcern`, `links`
  - metricData: `statusSource`, `lastAcceptedBucketAt`, `freshnessLabel`
  - starterConnection: `statusSource`, `lastHeartbeatAt`, `heartbeatStatus`, `freshnessLabel`, `connectionMeaning`, `stateImpact`
  - lifecycleBadge: `source`, `code`, `label`
  - topConcern: `code`, `label`, `source` 또는 `null`
  - links: `dashboard`
- `ProjectApplicationNavigationService`는 application list를 `ApplicationRepository.findByProjectIdOrderByNameAscEnvironmentAsc` 순서로 반환한다. UI가 risk sorting/ranking을 만들 필요가 없다.
- `ProjectApplicationNavigationService`는 metric freshness source를 accepted bucket latest end time에서 얻고, starter connection source를 `projectId + applicationName + environment` 범위의 latest heartbeat에서 얻는다.
- Story 5.1 현재 구현은 lifecycle badge를 `server_light_navigation_read_model / unknown / Metric data unknown`으로 반환하고, `topConcern`은 아직 `null`이다. Story 6.3 UI는 이 값을 표시만 해야 하며 더 채우려고 계산하지 않는다.
- Application item `links.dashboard` path는 `/api/projects/{projectId}/applications/{applicationId}/dashboard`다. 이 link는 Story 6.4 Application Dashboard UI integration의 handoff다.

### Static UI Guardrails

- Runtime UI asset 위치는 `observability-portal/src/main/resources/static/dashboard/`다.
- Story 6.3은 static HTML/CSS/JS를 개선하지만 장기 frontend architecture를 확정하지 않는다.
- Epic 6 완료와 retrospective 이후 Post-MVP SPA 전환을 검토할 수 있지만, 이 story에서 `src/main/frontend`, `package.json`, React, Vite, TypeScript를 도입하지 않는다.
- Browser token persistence는 open decision이다. Story 6.3도 in-memory access token hook과 request header 구성, 401 safe state까지만 다룬다.
- Application List helper는 formatting, escaping, null/empty fallback, loading/empty/error rendering, auth header 구성, safe link validation, local display filter, selected Project/Application handoff에 제한한다.
- Direct browser navigation to API JSON endpoint는 token persistence/URL workaround를 유도할 수 있으므로 Application List fetch는 authenticated `fetch`로 구현한다.

### Copy and UX Guardrails

- Application List는 상태 스캔과 dashboard 진입을 돕는 화면이다. Application Dashboard 판단을 대체하지 않는다.
- Application List copy는 server light summary의 source를 흐리지 않아야 한다. accepted bucket axis와 starter heartbeat axis를 시각/문구로 분리한다.
- 최근 heartbeat와 없음/오래된 accepted bucket 조합을 host application down으로 단정하지 않는다.
- `topConcern=null`, applications empty, 404, error, marker absence를 "정상", "문제 없음", "복구 완료"로 번역하지 않는다.
- `lifecycleBadge.label`은 서버가 준 표시값으로 렌더링할 수 있지만, UI가 임의로 `healthy`, `critical`, `degraded` 같은 severity copy를 만들어 붙이지 않는다.
- Application List에서 보여줄 수 있는 copy 후보는 source/scan 중심이어야 한다: `Metric data`, `Accepted bucket`, `Starter connection`, `Dashboard`, `표시할 Application 없음`, `Application 목록을 불러오지 못했습니다`.
- Dashboard action copy는 "Dashboard 판단을 여기서 미리 보여준다"가 아니라 "이 Application의 Dashboard로 이어지는 링크" 의미에 머문다.

### Previous Story Intelligence

- Story 6.2는 Project Selection UI가 `GET /api/projects` current shape만 소비하도록 닫았고, `links.applications`를 Application List handoff boundary로 보존했다.
- Story 6.2 code review fix는 unsafe direct application navigation, non-ready filter overwrite, stale async response를 막기 위해 view state, latest request guard, clear-token reset, pending Application List action을 추가했다.
- Story 6.2의 pending action은 Story 6.3의 입력이다. 구현자는 disabled button을 단순 `<a href>`로 바꾸지 말고 authenticated fetch flow로 발전시켜야 한다.
- Story 5.1은 Project/Application navigation read model과 `GET /api/projects/{projectId}/applications` endpoint를 이미 구현했다. Story 6.3은 backend shape를 새로 만들지 않고 이 current shape를 소비한다.
- Story 5.1은 accepted bucket freshness와 starter heartbeat summary를 별도 field/source로 분리했다. Story 6.3 UI가 두 축을 하나의 health 판단으로 합치면 회귀다.
- Epic 5 retrospective의 핵심 학습은 Epic 6 UI가 server read model을 표시해야 하며, UI가 계산을 되가져오면 안 된다는 점이다.

### Architecture Constraints

- Active baseline은 Traditional MVC + Service/Repository Layering이다.
- Portal package는 feature-first MVC 구조이며, `domain`은 business feature namespace다.
- 금지 package는 `application`, `port`, `adapter`, `adapter.in`, `adapter.out`다.
- Controller는 repository를 직접 호출하지 않고 service에 위임한다.
- Repository는 controller/dto에 의존하지 않는다.
- JPA entity를 API response, public DTO, service external result로 노출하지 않는다.
- Flyway migration이 schema source of truth다. Story 6.3은 schema/migration 변경을 기대하지 않는다.
- Static dashboard UI는 portal runtime 안에서 제공되며, UI는 read model을 표시만 한다.
- 새 공개 class/method/helper/test에는 `AGENTS.md` 기준으로 한국어 Javadoc/comment를 작성한다.

### Open Decisions / Risks

- Application List에서 state summary를 얼마나 계산해 보여줄지는 sprint-plan의 open decision이었지만, Story 6.3은 현재 server-computed light summary만 표시하는 방식으로 범위를 닫는다. 더 풍부한 summary field가 필요하면 correct-course가 필요하다.
- Browser token persistence, refresh token browser storage, logout/session persistence는 여전히 open decision이다. Story 6.3에서 닫지 않는다.
- Application Dashboard UI layout/component model은 Story 6.4 책임이다. Story 6.3에서 `links.dashboard`를 실제 dashboard rendering으로 소비하려는 요구가 생기면 Story 6.4 또는 correct-course로 넘긴다.
- 가장 큰 구현 위험은 기존 6.2 contract test를 broad string guard 그대로 둔 채 Application List field를 추가해 test false positive가 나거나, 반대로 broad guard를 삭제하면서 Project Selection dashboard shortcut 금지선까지 잃는 것이다. 테스트는 "Project Selection scope"와 "Application List scope"를 분리해야 한다.

## Testing

Focused test 대상 후보:

- `ApplicationListUiContractTest` 신규 추가
- `ProjectSelectionUiContractTest` 갱신
- `ProjectEntryUiContractTest` 갱신
- `ProjectNavigationControllerTest`
- `MvcLayerBoundaryTest`

필수 scenario:

- Project action의 `data-applications-link` 또는 `links.applications`가 authenticated Application List fetch로 이어진다.
- Application List fetch는 `Authorization: Bearer <access_token>` header를 사용한다.
- No-token/401/loading/error/empty/project-not-found/ready/filtered-empty state가 safe copy로 렌더링된다.
- Token clear, Project 재선택, overlapping request에서 stale applications response가 현재 화면을 덮어쓰지 않는다.
- Application List UI가 `GET /api/projects/{projectId}/applications` current shape field만 사용한다.
- accepted bucket `metricData`와 starter heartbeat `starterConnection`이 별도 axis로 표시된다.
- UI가 lifecycle badge, topConcern, freshness, connectionMeaning, stateImpact를 재계산하지 않고 서버 응답을 표시한다.
- `topConcern=null`과 empty applications를 current health 판단으로 번역하지 않는다.
- Application item `links.dashboard`는 item-level handoff로만 보존된다.
- Project Selection 단계에는 `links.dashboard`, dashboard shortcut, first application auto-select, application id inference가 없다.
- Static UI가 frontend build stack을 도입하지 않는다.
- Static UI가 token persistence, URL token parsing, refresh token browser storage를 만들지 않는다.
- Static UI가 lifecycle state, starter connection diagnosis, p95/p99, endpoint priority, snapshot/history event, project/application ranking helper를 만들지 않는다.
- Setup guide copy가 Story 6.1의 dependency와 세 property에 머문다.
- 수동 smoke 검증으로 실제 브라우저에서 GitHub OAuth login flow를 한 번 수행해 service access token과 refresh token이 정상 발급되는지 확인하되, provider token 노출이나 browser persistence를 만들지 않는다.
- `GET /api/projects/{projectId}/applications` serialization과 `404`, `GET /api/projects`, public `POST /api/projects` 부재가 regression으로 유지된다.

Suggested commands:

```bash
./gradlew :observability-portal:test --tests '*ProjectNavigation*'
./gradlew :observability-portal:test --tests '*ProjectEntryUiContract*'
./gradlew :observability-portal:test --tests '*ProjectSelectionUiContract*'
./gradlew :observability-portal:test --tests '*ApplicationListUiContract*'
./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest
./gradlew :observability-portal:test
git diff --check
```

테스트 이름은 구현 시점의 실제 class 이름에 맞게 조정할 수 있다. 단, static UI contract guard가 applications link authenticated fetch, current application shape, source-axis separation, safe states, `links.dashboard` handoff, no Project Selection dashboard shortcut, no first application auto-select, no application id inference, no token persistence, no frontend stack, no UI-side recomputation을 검증해야 한다.

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- 2026-05-28: BMAD create-story workflow와 customization을 확인했다.
- 2026-05-28: 지정된 sprint status, Epic 6 context, Epic/sprint/source-of-truth 문서, Story 6.2 handoff, Story 6.2 code review fix spec, Story 5.1 navigation read model, API/project-structure/auth/read-model 계약을 확인했다.
- 2026-05-28: 현재 catalog navigation controller/model/service, static dashboard asset, Project Selection UI contract test, ProjectNavigation focused test, MVC layer guard를 확인했다.
- 2026-05-28: Story 6.3 create-story 산출물을 `planning-artifacts/stories/6-3-application-list-ui.md`에 생성했다.
- 2026-05-28: `ApplicationListUiContractTest`를 먼저 추가하고 red phase에서 기존 static UI가 Application List shell/runtime 계약을 만족하지 못함을 확인했다.
- 2026-05-28: static dashboard asset 안에서 Project handoff click handler, Application List authenticated fetch state machine, current shape rendering, item-level dashboard handoff를 구현했다.
- 2026-05-28: `ProjectSelectionUiContractTest`와 `ProjectEntryUiContractTest`의 broad guard를 Project Selection scope와 recomputation guard로 좁혔다.
- 2026-05-28: 권장 focused test, full `:observability-portal:test`, `git diff --check`를 통과했다.
- 2026-05-28: Safari 실제 브라우저로 GitHub OAuth authorize/callback smoke를 수행했고 service access token/refresh token 발급 및 active refresh token 저장을 확인했다.
- 2026-05-28: BMAD code-review finding을 반영해 Application List 응답의 Project mismatch guard와 contract test를 추가했다.
- 2026-05-28: BMAD quick-dev review pass에서 나온 malformed payload/XSS/dashboard handoff test gap을 보강하고 full `:observability-portal:test`, `git diff --check`를 통과했다.
- 2026-05-28: BMAD code-review 최종 pass에서 나온 Application item nested shape validation, Project reload stale Application reset, invalid dashboard link 단독 contract gap을 quick-dev로 보강하고 focused/full test와 `git diff --check`를 통과했다.

### Implementation Plan

1. `ApplicationListUiContractTest`를 신규 작성해 `data-applications-link` authenticated fetch, current application shape, source-axis separation, safe states, dashboard handoff, no shortcut/no persistence/no recomputation guard를 먼저 고정한다.
2. `app.js`에서 Project request와 Application request sequence를 분리하고, Project button의 `data-applications-link`를 검증한 뒤 in-memory access token으로 Application List를 fetch한다.
3. Application List panel은 `generatedAt`, `project`, `applications[].applicationId/name/environment`, `metricData`, `starterConnection`, `lifecycleBadge`, `topConcern`, `links.dashboard`를 표시만 하며 accepted bucket과 starter heartbeat를 별도 axis로 렌더링한다.
4. `index.html`/`styles.css` 안에서 Application List panel, reload/filter controls, responsive wrapping을 추가하고 setup guide scope는 유지한다.
5. 기존 Project Selection/Entry contract의 broad guard를 Application List 정상 field와 충돌하지 않는 scope 기반 guard로 갱신한다.

### Completion Notes List

- Project card의 Applications button을 direct link가 아닌 authenticated Application List fetch trigger로 전환했고, malformed/external/mismatched link는 fetch 없이 invalid-link state로 처리한다.
- Application List는 loading/auth-required/error/project-not-found/empty/ready/filtered-empty 상태를 별도 state machine으로 렌더링하고, token clear/Project 재선택/overlapping request stale response를 `applicationRequestSequence`로 차단한다.
- Application item은 API `applicationId` field만 id로 표시하고, `metricData` accepted bucket axis와 `starterConnection` heartbeat axis를 합산 health 판단 없이 분리 표시한다.
- `links.dashboard`는 Application item의 disabled Dashboard handoff button `data-dashboard-link`로만 보존하며 Story 6.4 dashboard current fetch/rendering은 구현하지 않았다.
- Application List 응답의 `project.projectId`가 선택한 Project와 다르면 safe generic error로 전환해 cross-project metadata/dashboard handoff 렌더링을 막았다.
- Application item의 mandatory nested read model field와 nullable `topConcern` shape를 렌더링 전 검증해 malformed payload를 fail-closed로 처리한다.
- Project 목록 reload 후 선택된 Project가 사라지거나 applications link가 바뀌면 Application List snapshot과 reload handoff를 reset해 stale Application UI가 남지 않게 했다.
- Browser token persistence, URL token parsing, refresh token browser storage, frontend build stack, UI-side lifecycle/diagnosis/ranking/recomputation helper는 추가하지 않았다.
- 수동 smoke는 smoke 전용 PostgreSQL 컨테이너와 Safari에서 수행했다. fresh DB에서 migration SQL을 수동 적용한 뒤 GitHub authorize/callback을 완료했고, callback response에 service access token과 refresh token이 포함되며 DB에 account 1건과 active refresh token 1건이 생성됨을 확인했다. provider token은 노출하지 않았고 browser storage 코드는 만들지 않았다.

### File List

- implementation-artifacts/sprint-status.yaml
- observability-portal/src/main/resources/static/dashboard/app.js
- observability-portal/src/main/resources/static/dashboard/index.html
- observability-portal/src/main/resources/static/dashboard/styles.css
- observability-portal/src/test/java/com/observation/portal/domain/dashboard/ApplicationListUiContractTest.java
- observability-portal/src/test/java/com/observation/portal/domain/dashboard/ProjectEntryUiContractTest.java
- observability-portal/src/test/java/com/observation/portal/domain/dashboard/ProjectSelectionUiContractTest.java
- planning-artifacts/stories/6-3-application-list-ui.md

### Change Log

- 2026-05-28: Story 6.3 Application list UI create-story 산출물을 생성하고 ready-for-dev 상태로 전환했다.
- 2026-05-28: Application List static UI, authenticated applications fetch, source-axis rendering, dashboard handoff boundary, contract/regression tests를 구현하고 story 상태를 review로 전환했다.
- 2026-05-28: Code review finding patch로 Project mismatch response guard와 invalid applications link DOM exposure 축소를 반영했다.
- 2026-05-28: Application response shape, item id/dashboard handoff validation, malicious field escaping contract를 fail-closed 기준으로 보강했다.
- 2026-05-28: Final review patch로 nested application item validation, Project reload stale Application reset, invalid dashboard link 단독 Node VM contract를 보강했다.

## Suggested Review Order

**Payload Boundary**

- Application response mismatch and malformed payloads fail closed before rendering.
  [`app.js:270`](../../observability-portal/src/main/resources/static/dashboard/app.js#L270)

- Item-level dashboard handoff must match selected Project and application id.
  [`app.js:493`](../../observability-portal/src/main/resources/static/dashboard/app.js#L493)

- Application items require nonblank API ids and valid dashboard links.
  [`app.js:518`](../../observability-portal/src/main/resources/static/dashboard/app.js#L518)

**Project Handoff**

- Invalid Project applications links are not preserved as DOM handoff data.
  [`app.js:169`](../../observability-portal/src/main/resources/static/dashboard/app.js#L169)

- Project Selection contract verifies invalid handoff data is blanked.
  [`ProjectSelectionUiContractTest.java:68`](../../observability-portal/src/test/java/com/observation/portal/domain/dashboard/ProjectSelectionUiContractTest.java#L68)

**Runtime Contracts**

- Node VM clicks the rendered Project handoff instead of synthetic-only data.
  [`ApplicationListUiContractTest.java:198`](../../observability-portal/src/test/java/com/observation/portal/domain/dashboard/ApplicationListUiContractTest.java#L198)

- Malicious Application fields remain escaped while valid handoff survives.
  [`ApplicationListUiContractTest.java:265`](../../observability-portal/src/test/java/com/observation/portal/domain/dashboard/ApplicationListUiContractTest.java#L265)

- Malformed arrays, missing ids, and cross-project payloads render safe errors.
  [`ApplicationListUiContractTest.java:306`](../../observability-portal/src/test/java/com/observation/portal/domain/dashboard/ApplicationListUiContractTest.java#L306)
