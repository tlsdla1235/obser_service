---
artifactType: story
storyId: "6.2"
storyKey: "6-2-project-selection-ui"
epic: "Epic 6. Dashboard User Flow and Demo Hardening"
title: "Project selection UI"
architectureStyle: Traditional MVC
status: done
date: 2026-05-28
---

# Story 6.2 - Project selection UI

## Status

done

## Story

portal 사용자는 로그인 이후 Project 목록에서 project scope를 명확하게 선택하고 싶다.

그래야 Project 화면이 Application Dashboard 판단을 대신하지 않으면서, `GET /api/projects` navigation read model이 제공하는 project identity, application count, setup/connection issue candidate, recent concern, Application List link를 안전하게 확인하고 다음 단계로 이동할 수 있다.

## Source of Truth

아래 문서를 읽고 반영한 BMAD create-story context다. 구현 중 충돌처럼 보이는 지점은 `implementation-artifacts/spec-story-6-2-project-selection-ui-contract-decisions.md`를 최우선으로 둔다.

1. `implementation-artifacts/spec-story-6-2-project-selection-ui-contract-decisions.md`
2. `implementation-artifacts/sprint-status.yaml`
3. `planning-artifacts/epics.md`
4. `planning-artifacts/sprint-plan.md`
5. `planning-artifacts/source-of-truth/current-product-source-of-truth.md`
6. `planning-artifacts/stories/6-1-account-project-entry-and-setup-guide.md`
7. `implementation-artifacts/spec-story-6-1-account-project-entry-and-setup-guide-contract-decisions.md`
8. `planning-artifacts/stories/5-1-project-application-navigation-read-model.md`
9. `planning-artifacts/api-surface.md`
10. `planning-artifacts/project-structure.md`
11. `planning-artifacts/contracts/account-auth-policy.md`
12. `planning-artifacts/contracts/read-model-contract.md`
13. `planning-artifacts/contracts/dashboard-read-model.md`
14. `implementation-artifacts/epic-5-retro-2026-05-27.md`
15. `AGENTS.md`
16. `_bmad/custom/project-context.md`

확인한 현재 코드:

1. `observability-portal/src/main/java/com/observation/portal/domain/catalog/controller/ProjectNavigationController.java`
2. `observability-portal/src/main/java/com/observation/portal/domain/catalog/model/ProjectNavigationReadModel.java`
3. `observability-portal/src/main/java/com/observation/portal/domain/catalog/service/ProjectApplicationNavigationService.java`
4. `observability-portal/src/main/resources/static/dashboard/index.html`
5. `observability-portal/src/main/resources/static/dashboard/app.js`
6. `observability-portal/src/main/resources/static/dashboard/styles.css`
7. `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ProjectEntryUiContractTest.java`
8. `observability-portal/src/test/java/com/observation/portal/domain/catalog/controller/ProjectNavigationControllerTest.java`
9. `observability-portal/src/test/java/com/observation/portal/architecture/MvcLayerBoundaryTest.java`

## Scope / Out of Scope

포함:

- 기존 `observability-portal/src/main/resources/static/dashboard/` static asset의 Project selection list/card polish
- `GET /api/projects` current shape 소비
- reload와 optional project name filter
- loading, empty, auth-required, error state rendering
- 각 project item의 `links.applications`를 그대로 사용하는 Application List 이동
- Story 6.1 setup guide aside 유지
- responsive layout polish
- static UI contract test 확장 또는 `ProjectSelectionUiContractTest` 추가
- `ProjectNavigationControllerTest` 등 focused regression 유지
- no frontend stack, no token persistence, no UI-side operational recomputation, no project creation guard

제외:

- Application List UI 구현
- Application Dashboard UI 또는 direct dashboard shortcut
- 첫 application 자동 선택
- application id 추론
- project creation/onboarding flow
- public `POST /api/projects`
- 로그인 직후 자동 project 생성
- project ownership, role model, project key 발급/회전/재발급 workflow
- lifecycle state, starter connection diagnosis, project/application status, project priority, severity, endpoint priority, p95/p99, snapshot/history event 계산
- `src/main/frontend`, `package.json`, React, Vite, TypeScript, 별도 frontend build/deploy
- view resolver/template engine
- browser token persistence, URL fragment/query token parsing, cookie/session persistence policy 확정

## Acceptance Criteria

1. Project selection UI는 `GET /api/projects` current shape만 소비한다.
2. UI와 static contract test는 표준 field인 `generatedAt`, `projects[].projectId`, `name`, `applicationCount`, `setupConnectionIssueCount`, `recentConcern`, `links.applications`만 Project selection 계약으로 사용한다.
3. UI는 `setupIssueCount`, `recentConcernCount` 같은 legacy alias fallback을 사용하지 않는다.
4. Project item의 primary navigation은 `links.applications`만 사용한다.
5. UI는 Application Dashboard direct link, dashboard shortcut, 첫 application auto-select, application id 추론을 만들지 않는다.
6. UI는 Project creation flow, `Create Project` CTA, public `POST /api/projects`, 로그인 직후 자동 project 생성을 만들지 않는다.
7. Project가 없으면 local/internal seed 또는 admin bootstrap decision이 필요하다는 safe empty state를 보여준다.
8. Project selection copy는 project/application health를 단정하지 않는다.
9. `setupConnectionIssueCount`는 setup/connection issue candidate count로만 표현한다.
10. Copy와 test fixture는 `정상`, `문제 없음`, `앱 다운`, `장애`, `복구 완료`, `healthy`, `unhealthy`, `degraded`, `critical`, `Project health`를 Project selection 상태 표현으로 사용하지 않는다.
11. Static dashboard asset boundary를 유지하고 `observability-portal/src/main/resources/static/dashboard/` 안의 `index.html`, `styles.css`, `app.js`를 개선한다.
12. Story 6.2는 `src/main/frontend`, `package.json`, React, Vite, TypeScript, 별도 frontend build/deploy, view resolver/template engine을 도입하지 않는다.
13. Static UI는 in-memory access token hook과 `Authorization: Bearer <access_token>` header 구성, 401 safe state까지만 다룬다.
14. UI는 `localStorage`, `sessionStorage`, cookie, URL fragment token parsing, query param token parsing, refresh token browser storage, logout/session persistence policy를 만들지 않는다.
15. Client-side code는 formatting, escaping, loading/empty/error rendering, auth header 구성, optional project name filter, `links.applications` navigation 같은 presentation helper만 둔다.
16. UI는 lifecycle state, setup/connection diagnosis, project health/status/priority/severity, `recentConcern` 생성/요약, p95/p99, endpoint priority, snapshot/history event, project ranking을 계산하지 않는다.
17. Candidate count가 0보다 큰 경우의 시각 강조는 운영 severity가 아니라 neutral attention style로만 취급한다.
18. Setup guide aside는 Story 6.1 dependency, `observation.heartbeat.portal-base-url`, `observation.heartbeat.project-key`, `observation.metric-flush.environment` 안내에 머문다.
19. Setup guide는 project-id/application-name/instance, queue/drop/heartbeat interval, route allowlist, dashboard tuning, alert delivery, raw query/explorer, p95/p99 계산법, endpoint priority 판단 안내를 추가하지 않는다.
20. Project selection UI는 application/dashboard 판단을 대신하지 않고 Application List로 scope를 좁히는 화면으로 유지한다.
21. `ProjectNavigationController`와 `ProjectApplicationNavigationService`의 read-only API shape는 Story 5.1 계약을 유지하며, Story 6.2 구현은 backend API/schema 확장을 기본 범위로 삼지 않는다.
22. Static UI contract test는 current shape field, legacy alias 부재, `links.applications` navigation, dashboard shortcut 부재, first application auto-select 부재, project creation flow 부재를 검증한다.
23. Static UI contract test는 frontend build stack 부재, token persistence/URL token parsing 부재, UI-side operational recomputation helper 부재, project/application health 단정 copy 부재를 검증한다.
24. Focused regression은 `GET /api/projects` serialization과 public project creation route 부재를 계속 검증한다.
25. 새 공개 class/method 또는 이해하기 어려운 helper를 만들면 `AGENTS.md` 지침에 따라 한국어 Javadoc/comment를 작성한다.
26. Suggested verification이 통과해야 implementation completion으로 볼 수 있다.

## Tasks / Subtasks

- [x] Project selection list/card polish (AC: 1~10, 15~17, 20)
  - [x] 현재 `app.js`의 project rendering이 `projectId`, `name`, `applicationCount`, `setupConnectionIssueCount`, `recentConcern`, `links.applications`만 소비하도록 유지한다.
  - [x] `setupConnectionIssueCount > 0` 표현은 neutral attention style로만 표시하고 severity/status처럼 보이지 않게 한다.
  - [x] `recentConcern`이 없을 때는 source 부재 중심 copy를 사용하고 current health를 단정하지 않는다.
  - [x] `setupIssueCount`, `recentConcernCount`, `projectHealth`, `status`, `priority`, `severity` field fallback을 만들지 않는다.

- [x] Reload와 optional project name filter 구현 (AC: 1~6, 15~17)
  - [x] reload button은 `GET /api/projects`를 다시 호출하고 loading state를 표시한다.
  - [x] optional project name filter는 이미 받은 project list의 표시 범위만 좁히고 API shape나 backend query를 새로 요구하지 않는다.
  - [x] filter는 project ranking, risk sorting, recentConcern 생성/요약을 하지 않는다.
  - [x] filter empty result는 "표시할 Project가 없음" 계열 copy로 처리하고 health/status 의미를 붙이지 않는다.

- [x] Loading/empty/auth/error state 개선 (AC: 7~10, 13~14, 20)
  - [x] loading skeleton 또는 loading text가 layout shift 없이 표시되도록 정리한다.
  - [x] 401은 GitHub login 후 Project 목록을 볼 수 있다는 safe auth-required state로 표시한다.
  - [x] empty state는 local/internal seed 또는 admin bootstrap decision 필요성을 안내하고 project creation CTA를 만들지 않는다.
  - [x] error state는 provider token, access/refresh token, secret, internal payload를 노출하지 않는 일반화된 copy를 사용한다.

- [x] `links.applications` navigation 유지 (AC: 4~6, 20~22)
  - [x] Project card action은 response의 `links.applications`를 사용한다.
  - [x] link는 안전한 내부 `/api/projects/{projectId}/applications` 형태인지 검증한 뒤 사용한다.
  - [x] Application Dashboard direct link, dashboard shortcut, first application auto-select, application id 추론은 추가하지 않는다.

- [x] Setup guide aside 유지 (AC: 18~19)
  - [x] dependency coordinate copy를 Story 6.1 기준으로 유지한다.
  - [x] `observation.heartbeat.portal-base-url`, `observation.heartbeat.project-key`, `observation.metric-flush.environment`만 핵심 설정 안내로 유지한다.
  - [x] raw project key 발급/회전/재발급 flow, dashboard tuning, raw explorer, p95/p99 계산법, endpoint priority 해석 안내를 추가하지 않는다.

- [x] Responsive layout polish (AC: 11~12, 20)
  - [x] `index.html`, `styles.css`, `app.js` 안에서만 layout polish를 수행한다.
  - [x] mobile과 desktop에서 project card, filter, reload, setup aside가 겹치지 않도록 responsive constraints를 둔다.
  - [x] text overflow는 `overflow-wrap`, stable dimensions, 적절한 line-height로 처리한다.
  - [x] 별도 frontend source/build stack이나 template rendering을 만들지 않는다.

- [x] Static UI contract test 확장 또는 `ProjectSelectionUiContractTest` 추가 (AC: 1~23)
  - [x] current field 이름만 사용하는지 검증한다.
  - [x] legacy alias fallback 문자열이 없는지 검증한다.
  - [x] `links.applications` navigation만 있고 dashboard shortcut/auto-select가 없는지 검증한다.
  - [x] project creation flow, frontend build stack, token persistence, URL token parsing이 없는지 검증한다.
  - [x] UI-side operational recomputation helper와 project/application health 단정 copy가 없는지 검증한다.
  - [x] setup guide copy가 Story 6.1 property 범위에 머무르는지 검증한다.

- [x] ProjectNavigation focused regression 유지 (AC: 21, 24)
  - [x] `ProjectNavigationControllerTest`의 `GET /api/projects` response shape 검증을 유지하거나 필요한 경우 강화한다.
  - [x] public `POST /api/projects` route 부재 검증을 유지한다.
  - [x] backend API/schema 확장이 필요해 보이면 구현하지 말고 contract/correct-course로 올린다.

- [x] no frontend stack/no token persistence/no recomputation/no project creation guard (AC: 6, 11~17, 22~24)
  - [x] `src/main/frontend`, `package.json`, React/Vite/TypeScript 도입 부재를 test로 고정한다.
  - [x] `localStorage`, `sessionStorage`, cookie, URL fragment/query token parsing 부재를 test로 고정한다.
  - [x] lifecycle/setup diagnosis/project status/priority/severity/p95/p99/endpoint priority/snapshot event/project ranking 계산 helper 부재를 test로 고정한다.
  - [x] `Create Project` copy와 public `POST /api/projects` route 부재를 test로 고정한다.

## Dev Notes

### Current Code State

- 현재 `sprint-status.yaml` 기준 Epic 6은 `in-progress`, Story 6.1은 `done`, Story 6.2는 create-story 전 `backlog`였다.
- Story 6.1 구현으로 `observability-portal/src/main/resources/static/dashboard/index.html`, `styles.css`, `app.js`가 이미 존재한다.
- 현재 static UI는 `GET /api/projects`를 호출하고, `window.observationPortalAuth.setAccessToken(accessToken)` in-memory hook으로 Bearer header를 구성한다.
- 현재 static UI는 reload button, GitHub login CTA, project list rendering, safe empty/auth/error state, setup guide aside를 이미 갖고 있다.
- 현재 `app.js`는 `project.links.applications`, `project.recentConcern`, `project.setupConnectionIssueCount`를 사용한다.
- 현재 `app.js`의 `escapeAttribute`는 `/api/projects/`로 시작하지 않는 link를 `#`로 제한한다. Story 6.2 구현자는 이 검증을 Application List link semantics에 맞게 유지하거나 더 명확히 할 수 있다.
- 현재 `ProjectEntryUiContractTest`는 static asset 존재, `fetch('/api/projects')`, Bearer header, `links.applications`, no frontend build stack, no token persistence, no project creation, setup guide copy, no UI recomputation helper를 일부 검증한다.
- 현재 `ProjectNavigationControllerTest`는 `GET /api/projects` field shape, `GET /api/projects/{projectId}/applications`, no public `POST /api/projects`를 검증한다.
- 현재 `MvcLayerBoundaryTest`는 Traditional MVC package boundary, no hexagonal package, account schema unsupported surface 부재, stored read model/history 관련 금지선을 검증한다.

### API and Read Model Contract

- `GET /api/projects` response shape는 Story 5.1 구현 결과를 따른다.
- 표준 Project field는 `generatedAt`, `projects[].projectId`, `name`, `applicationCount`, `setupConnectionIssueCount`, `recentConcern`, `links.applications`다.
- `recentConcern`은 최대 1개 object 또는 `null`이다.
- `links.applications`는 Project selection에서 Application List로 이동하는 유일한 navigation link다.
- Story 6.2는 `GET /api/projects/{projectId}/applications` UI를 구현하지 않는다. 그 화면은 Story 6.3 책임이다.
- Story 6.2는 Application Dashboard API나 dashboard link를 project card에 추가하지 않는다.
- API response가 부족해 보여도 `setupIssueCount`, `recentConcernCount`, project-level status/priority/severity alias를 UI에서 만들지 않는다.

### Static UI Guardrails

- Runtime UI asset 위치는 `observability-portal/src/main/resources/static/dashboard/`다.
- Story 6.2는 static HTML/CSS/JS를 개선하지만 이를 장기 frontend architecture로 확정하지 않는다.
- Epic 6 완료와 retrospective 이후 Post-MVP SPA 전환을 검토할 수 있지만, 이 story에서 `src/main/frontend`, `package.json`, React, Vite, TypeScript를 도입하지 않는다.
- Browser token persistence는 open decision이다. Story 6.2는 in-memory access token hook과 request header 구성, 401 safe state만 다룬다.
- UI helper는 formatting, escaping, null/empty fallback, loading/empty/error rendering, auth header 구성, project name filter, `links.applications` navigation에 제한한다.

### Copy Guardrails

- Project selection은 운영 판단 화면이 아니라 scope 선택 화면이다.
- `setupConnectionIssueCount`는 setup/connection issue candidate count이며 severity나 확정 원인이 아니다.
- Candidate count가 0보다 큰 경우도 neutral attention style로만 강조한다.
- 최근 concern이 없으면 "최근 concern 없음"처럼 source 부재만 표현한다. current health를 단정하지 않는다.
- 금지 copy: `정상`, `문제 없음`, `앱 다운`, `장애`, `복구 완료`, `healthy`, `unhealthy`, `degraded`, `critical`, `Project health`.
- Error/auth/empty copy는 provider token, service token, secret, raw payload를 드러내지 않는다.

### Previous Story Intelligence

- Story 6.1은 GitHub OAuth only, Bearer access token/JWT, refresh token rotation 기준을 세웠고 static dashboard asset을 만들었다.
- Story 6.1은 setup guide를 dependency, `observation.heartbeat.portal-base-url`, `observation.heartbeat.project-key`, `observation.metric-flush.environment`로 제한했다.
- Story 6.1은 public project creation, login 직후 자동 project 생성, Create Project flow를 만들지 않는 방향으로 구현됐다.
- Story 6.1은 browser token storage 위치를 닫지 않았다. `localStorage`/`sessionStorage`/cookie/URL token parsing을 Story 6.2에서 추가하지 않는다.
- Story 5.1은 Project/Application navigation read model과 `GET /api/projects` / `GET /api/projects/{projectId}/applications` endpoint를 구현했다.
- Story 5.1의 `ProjectApplicationNavigationService`는 `setupConnectionIssueCount`를 application별 accepted bucket freshness 또는 starter heartbeat absence/staleness 기반 light candidate count로 만들지만, host application down이나 dashboard state를 확정하지 않는다.
- Epic 5 retrospective의 핵심 학습은 Epic 6 UI가 server read model을 표시해야 하며, UI가 계산을 되가져오면 안 된다는 점이다.

### Architecture Constraints

- Active baseline은 Traditional MVC + Service/Repository Layering이다.
- Portal package는 feature-first MVC 구조이며, `domain`은 business feature namespace다.
- 금지 package는 `application`, `port`, `adapter`, `adapter.in`, `adapter.out`다.
- Controller는 repository를 직접 호출하지 않고 service에 위임한다.
- Repository는 controller/dto에 의존하지 않는다.
- JPA entity를 API response, public DTO, service external result로 노출하지 않는다.
- Flyway migration이 schema source of truth다. Story 6.2는 schema/migration 변경을 기대하지 않는다.
- Public class, public method, 복잡한 helper에는 `AGENTS.md` 기준으로 한국어 Javadoc/comment를 작성한다.

### Open Decisions / Risks

- Project creation의 실제 방식, ownership, role model, project key 발급/회전/재발급 product workflow는 여전히 open decision이다.
- Browser UI가 service token을 memory, sessionStorage, localStorage 중 어디에 보관할지는 아직 open decision이다.
- React/Vite/TypeScript SPA 전환 시점과 source/build output 구조는 Epic 6 이후 Post-MVP decision으로 남는다.
- 가장 큰 구현 위험은 UI polish 과정에서 dashboard shortcut, project status/priority/severity, token persistence, project creation CTA가 조용히 추가되는 것이다.
- 현재 static UI가 이미 Story 6.1에서 Project Entry 기본 기능을 갖고 있으므로 Story 6.2는 큰 architecture 변경보다 polish와 negative guard 강화를 우선한다.

## Testing

Focused test 대상 후보:

- `ProjectEntryUiContractTest` 확장
- `ProjectSelectionUiContractTest` 신규 추가
- `ProjectNavigationControllerTest`
- `MvcLayerBoundaryTest`

필수 scenario:

- Static UI가 `GET /api/projects` current shape만 소비한다.
- Static UI가 `setupConnectionIssueCount`, `recentConcern`, `links.applications` 표준 field를 사용한다.
- Static UI가 `setupIssueCount`, `recentConcernCount` legacy alias fallback을 사용하지 않는다.
- Static UI가 dashboard shortcut, dashboard direct link, first application auto-select, application id 추론을 만들지 않는다.
- Static UI가 public project creation flow, `Create Project`, `POST /api/projects`, 로그인 직후 자동 project 생성 copy를 만들지 않는다.
- Static UI가 project/application health를 단정하는 copy를 쓰지 않는다.
- Static UI가 frontend build stack을 도입하지 않는다.
- Static UI가 `localStorage`, `sessionStorage`, cookie, URL fragment/query token parsing, refresh token browser storage를 만들지 않는다.
- Static UI가 lifecycle state, setup/connection diagnosis, p95/p99, endpoint priority, snapshot/history event, project ranking helper를 만들지 않는다.
- Setup guide copy가 Story 6.1의 dependency와 세 property에 머문다.
- `GET /api/projects` serialization과 public `POST /api/projects` 부재가 regression으로 유지된다.

Suggested commands:

```bash
./gradlew :observability-portal:test --tests '*ProjectNavigation*'
./gradlew :observability-portal:test --tests '*ProjectEntryUiContract*'
./gradlew :observability-portal:test --tests '*ProjectSelectionUiContract*'
./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest
./gradlew :observability-portal:test
git diff --check
```

테스트 이름은 구현 시점의 실제 class 이름에 맞게 조정할 수 있다. 단, static UI contract guard가 legacy alias, dashboard shortcut, project creation flow, token persistence, frontend build stack, UI-side recomputation, project/application health/status copy를 검증해야 한다.

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- 2026-05-28: BMAD create-story workflow와 customization을 확인했다.
- 2026-05-28: Story 6.2 사전 계약 결정 문서, sprint-status, Epic 6 source-of-truth, Story 6.1/5.1 handoff, API/read-model/project-structure/account-auth 계약을 확인했다.
- 2026-05-28: 현재 catalog navigation controller/model/service와 static dashboard asset, Project Entry UI contract test, ProjectNavigation focused test, MVC layer guard를 확인했다.
- 2026-05-28: 기존 미추적 파일 `implementation-artifacts/prompt-story-6-2-bmad-create-story.md`와 `implementation-artifacts/spec-story-6-2-project-selection-ui-contract-decisions.md`는 사용자/사전 산출물로 보고 되돌리지 않았다.
- 2026-05-28: `ProjectSelectionUiContractTest`를 RED로 추가해 generatedAt/filter/link/copy guard 실패를 확인했다.
- 2026-05-28: static dashboard asset에서 Project selection polish, local name filter, loading/auth/empty/error state, applications link validation을 구현했다.
- 2026-05-28: focused regression과 전체 `:observability-portal:test`를 실행해 통과를 확인했다.

### Implementation Plan

- `GET /api/projects` 응답은 current shape 그대로 받아 `generatedAt`과 `projects[]`만 표시하고, project card는 표준 field와 `links.applications`만 참조한다.
- Project name filter는 이미 메모리에 받은 `loadedProjects`의 표시 범위만 좁히며 정렬, ranking, recent concern 요약, backend query 확장을 하지 않는다.
- `links.applications`는 `/api/projects/{projectId}/applications`와 정확히 일치할 때만 href로 사용하고, dashboard/application id 추론 경로는 만들지 않는다.
- static UI guard는 신규 `ProjectSelectionUiContractTest`로 current field, legacy alias 부재, no dashboard shortcut, no project creation, no token persistence, no frontend stack, no UI-side recomputation을 고정한다.

### Completion Notes List

- BMAD create-story 산출물로 Story 6.2 Project selection UI developer guide를 생성했다.
- Project card는 `generatedAt`, `projectId`, `name`, `applicationCount`, `setupConnectionIssueCount`, `recentConcern`, `links.applications` current contract만 사용하도록 유지했다.
- Reload 시 loading state를 표시하고, optional project name filter는 기존 응답 목록에 대한 local display filter로만 동작하게 했다.
- Loading/empty/auth/error copy를 safe state로 정리하고, candidate count는 neutral attention style로만 강조했다.
- Story 6.1 setup guide aside와 static dashboard asset boundary를 유지했다.
- `ProjectSelectionUiContractTest`를 추가해 Story 6.2의 negative guard를 고정했다.

### File List

- `implementation-artifacts/sprint-status.yaml`
- `observability-portal/src/main/resources/static/dashboard/app.js`
- `observability-portal/src/main/resources/static/dashboard/index.html`
- `observability-portal/src/main/resources/static/dashboard/styles.css`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ProjectSelectionUiContractTest.java`
- `planning-artifacts/stories/6-2-project-selection-ui.md`

### Change Log

- 2026-05-28: Story 6.2 Project selection UI create-story 산출물을 생성했다.
- 2026-05-28: Project selection UI polish, reload/name filter, safe states, applications link validation, responsive layout, static UI contract guard를 구현했다.
