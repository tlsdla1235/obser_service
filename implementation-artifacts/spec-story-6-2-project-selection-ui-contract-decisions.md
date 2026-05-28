---
artifactType: contract-decisions
projectName: Spring Boot 운영 첫 화면 포털
storyId: "6.2"
storyKey: "6-2-project-selection-ui"
status: closed
date: 2026-05-28
scope: Story 6.2 pre-create-story contract closure before BMAD create-story execution
---

# Story 6.2 Project Selection UI Contract Decisions

## Purpose

Story 6.2는 Epic 6의 Project selection UI 경계를 닫는다.

이 문서는 Story 6.2 create-story 실행 전에 사용자가 직접 결정한 계약을 기록한다. 목표는 Story 6.2가 Project scope 선택 UI에 집중하고, Application List, Application Dashboard, project creation, SPA 전환, browser token persistence, UI-side 운영 판단 계산을 다시 열지 않게 하는 것이다.

Story 6.2는 기존 static dashboard asset 기반으로 MVP Project selection UI를 완성한다. 후속 React/Vite/TypeScript SPA 전환은 Epic 6 완료와 retrospective 이후 별도 Post-MVP frontend architecture decision으로 다룬다.

## Authority and Non-Reopen Rules

이 문서는 아래 계약과 산출물을 기준으로 한다.

- `planning-artifacts/stories/6-1-account-project-entry-and-setup-guide.md`
- `implementation-artifacts/spec-story-6-1-account-project-entry-and-setup-guide-contract-decisions.md`
- `planning-artifacts/stories/5-1-project-application-navigation-read-model.md`
- `planning-artifacts/epics.md`
- `planning-artifacts/sprint-plan.md`
- `planning-artifacts/current-product-source-of-truth.md`
- `planning-artifacts/api-surface.md`
- `planning-artifacts/project-structure.md`
- `planning-artifacts/contracts/account-auth-policy.md`
- `planning-artifacts/contracts/read-model-contract.md`
- `planning-artifacts/contracts/dashboard-read-model.md`
- `implementation-artifacts/epic-5-retro-2026-05-27.md`
- `implementation-artifacts/sprint-status.yaml`

Story 6.2 create-story/dev-story는 아래 결정을 다시 열지 않는다.

- Story 6.2는 `GET /api/projects` read-only navigation read model을 소비해 Project scope 선택 UI를 완성한다.
- Story 6.2는 Application List, Application Dashboard, Project creation/onboarding, project ownership/key issuance flow를 구현하지 않는다.
- Project selection UI는 `GET /api/projects` current shape만 소비한다.
- Project selection UI의 primary navigation은 `links.applications`를 그대로 사용해 Application List로 이동하는 것이다.
- Story 6.2는 public `POST /api/projects`, 로그인 직후 자동 project 생성, "Create Project" flow를 만들지 않는다.
- Project selection UI copy는 project/application health를 단정하지 않는다.
- Story 6.2는 기존 `observability-portal/src/main/resources/static/dashboard/` static asset을 개선한다.
- Story 6.2는 `src/main/frontend`, `package.json`, React, Vite, TypeScript, 별도 frontend build/deploy, view resolver/template engine을 도입하지 않는다.
- Story 6.2 client-side code는 presentation helper만 둘 수 있고 운영 판단을 계산하지 않는다.
- Story 6.2는 browser token persistence 정책을 닫지 않는다.
- Story 6.2 completion은 static UI contract test 중심으로 검증한다.

## Closed Decisions

### 1. Story Scope Contract

Story 6.2는 Project selection UI만 다룬다.

결정 내용:

- 기존 `GET /api/projects` read-only navigation read model을 소비해 Project scope 선택 UI를 완성한다.
- Project list/card polish, loading/empty/auth/error state, reload, optional project name filter, setup guide aside, responsive layout, Application List 이동 action을 개선한다.
- Story 6.3 Application List UI와 Story 6.4 Application Dashboard UI integration의 경계를 침범하지 않는다.

금지:

- Application List 구현
- Application Dashboard shortcut 또는 dashboard direct link
- Project creation/onboarding
- project ownership/role/key issuance flow
- 운영 판단 계산

결정 이유:

- Epic 6 story 순서는 Project selection -> Application List -> Application Dashboard다.
- Story 6.2가 application/dashboard 판단을 가져오면 후속 story 경계가 흐려진다.

### 2. Project List API Shape Contract

Project selection UI는 `GET /api/projects`의 현재 navigation read model shape만 소비한다.

표준 field:

- `generatedAt`
- `projects[].projectId`
- `projects[].name`
- `projects[].applicationCount`
- `projects[].setupConnectionIssueCount`
- `projects[].recentConcern`
- `projects[].links.applications`

결정 내용:

- `setupConnectionIssueCount`를 표준 이름으로 사용한다.
- `recentConcern`은 최대 1개 object 또는 `null`이다.
- `links.applications`는 Application List 진입의 유일한 navigation link다.

금지:

- legacy alias field fallback을 만들지 않는다.
- `setupIssueCount`, `recentConcernCount`를 새 UI 계약으로 사용하지 않는다.
- `projectHealth`, `status`, `priority`, `severity` 같은 project-level 판단 field를 요구하거나 만들지 않는다.

결정 이유:

- Story 5.1에서 현재 navigation read model shape가 이미 구현됐다.
- Alias fallback은 contract drift를 숨긴다.

### 3. Project Selection Navigation Contract

Project selection UI의 primary navigation은 각 project item의 `links.applications`를 그대로 사용하는 것이다.

결정 내용:

- Project card 전체 클릭 또는 명확한 `Applications` action은 `links.applications`로 이동한다.
- UI는 response link를 재구성하지 않고, 안전한 내부 `/api/projects/{projectId}/applications` link인지 검증한 뒤 사용한다.

금지:

- Application Dashboard direct link
- 첫 application 자동 선택
- application id 추론
- dashboard shortcut
- 최근/위험 application 추정 navigation

결정 이유:

- Project selection은 scope 선택이고, application 선택은 Story 6.3의 책임이다.
- 첫 application 자동 선택은 UI가 application selection 판단을 몰래 수행하게 만든다.

### 4. No Project Creation Contract

Story 6.2는 Project creation을 열지 않는다.

결정 내용:

- Project가 없을 때 UI는 local/internal seed 또는 admin bootstrap decision이 필요하다는 safe empty state를 보여준다.
- Project creation 방식, ownership, role model, project key 발급/회전/재발급 정책은 후속 decision으로 남긴다.

금지:

- public `POST /api/projects`
- 로그인 직후 자동 project 생성
- "Create Project" button/flow
- project ownership/role model
- project key 발급/회전/재발급 workflow

결정 이유:

- Project creation을 열면 account-project relation, role, key issuance, secret exposure boundary가 함께 열려야 한다.
- Story 6.2는 UI selection story로 유지한다.

### 5. Copy and State Meaning Contract

Project selection UI copy는 project/application health를 단정하지 않는다.

결정 내용:

- `setupConnectionIssueCount`는 setup/connection issue candidate count로만 표현한다.
- Empty/auth/error copy는 일반화된 안내를 사용한다.
- 최근 concern이 없으면 "최근 concern 없음"처럼 concern source 부재만 표현하고, 운영 정상 상태를 단정하지 않는다.

허용 copy 후보:

- `Setup candidates`
- `Connection/setup candidates`
- `Application count`
- `Recent concern`
- `최근 concern 없음`
- `Project 목록을 불러오지 못했습니다`
- `GitHub 로그인 후 Project 목록을 볼 수 있습니다`
- `local/internal seed 또는 admin bootstrap decision이 필요합니다`

금지 copy:

- `정상`
- `문제 없음`
- `앱 다운`
- `장애`
- `복구 완료`
- `healthy` / `unhealthy`
- `degraded` / `critical`
- `Project health`

결정 이유:

- Project 화면은 운영 판단 화면이 아니라 scope 선택 화면이다.
- 운영 판단은 Application Dashboard의 server-computed read model이 담당한다.

### 6. Static UI and Post-MVP SPA Contract

Story 6.2는 기존 static dashboard asset을 개선해 MVP Project selection UI를 완성한다.

결정 내용:

- Runtime UI asset 위치는 `observability-portal/src/main/resources/static/dashboard/`로 유지한다.
- 기존 `index.html`, `styles.css`, `app.js`를 개선하거나 필요한 범위에서 같은 static asset 경계 안에 둔다.
- React/Vite/TypeScript SPA 전환은 Epic 6 완료와 retrospective 이후 별도 Post-MVP frontend architecture decision으로 다룬다.
- Story 6.2 구현은 후속 SPA가 재사용할 API 계약, navigation semantics, copy guard, UI recomputation 금지 원칙을 고정한다.
- Static HTML/CSS/JS를 장기 frontend architecture로 확정하지 않는다.

금지:

- `src/main/frontend`
- `package.json`
- React
- Vite
- TypeScript
- 별도 frontend build/deploy
- view resolver/template engine

결정 이유:

- Epic 6 MVP는 product flow와 server API/read model 계약을 닫는 데 집중한다.
- SPA 전환은 MVP closure 이후 이미 닫힌 API와 UX 계약을 컴포넌트로 옮기는 별도 작업으로 다루는 편이 안전하다.

### 7. Client-Side Helper Contract

Story 6.2 client-side code는 presentation helper만 둘 수 있다.

허용:

- formatting
- escaping
- null/empty fallback
- loading/empty/error rendering
- auth header 구성
- project name filter
- `links.applications` navigation
- candidate count가 0보다 큰 경우의 neutral attention style

금지:

- lifecycle state 계산
- setup/connection diagnosis 계산
- project health/status/priority 계산
- operational severity 계산
- recentConcern 생성/요약
- p95/p99 계산
- endpoint priority 계산
- snapshot/history event 생성
- project ranking/sorting by risk

결정 이유:

- Epic 5 read model이 server-side 운영 판단의 source of truth다.
- Browser helper는 표시와 이동을 돕되 판단을 만들지 않는다.

### 8. UI Layout and Interaction Contract

Story 6.2 UI는 Project selection list/card 중심으로 개선한다.

포함:

- Project selection list/card
- reload
- optional project name filter
- loading/empty/auth/error state
- setup guide aside
- responsive layout
- Application List 이동 action

제외:

- tabs
- dashboard preview
- application preview
- create project modal
- onboarding wizard
- project detail drawer
- sorting/ranking
- full workspace shell

결정 이유:

- Story 6.2는 선택 화면 품질을 올리되 interaction surface를 작게 유지한다.
- Application preview와 dashboard preview는 후속 story의 책임을 흐린다.

### 9. Browser Token Handling Contract

Story 6.2는 browser token persistence 정책을 닫지 않는다.

결정 내용:

- Static UI는 in-memory access token hook을 사용할 수 있다.
- API request에는 `Authorization: Bearer <access_token>` header를 구성할 수 있다.
- Token이 없거나 만료되어 401이 오면 safe auth-required state를 보여준다.

금지:

- `localStorage`
- `sessionStorage`
- cookie
- URL fragment token parsing
- query param token parsing
- refresh token browser storage
- logout/session persistence policy 확정

결정 이유:

- 6.1에서 service token JSON body delivery는 닫았지만 browser storage는 open decision으로 남았다.
- Token persistence는 Epic 6 이후 SPA/auth frontend decision에서 함께 닫는다.

### 10. Test Guard Contract

Story 6.2 completion은 static UI contract test 중심으로 검증한다.

검증 기대:

- Project selection UI가 `GET /api/projects` current shape만 소비한다.
- 표준 field 이름을 사용하고 legacy alias를 사용하지 않는다.
- `links.applications` navigation만 사용한다.
- dashboard shortcut을 만들지 않는다.
- Project creation flow를 만들지 않는다.
- browser token persistence와 URL token parsing을 만들지 않는다.
- frontend build stack을 도입하지 않는다.
- UI-side operational recomputation helper를 만들지 않는다.
- project-level health/status copy를 추가하지 않는다.
- setup guide는 Story 6.1의 최소 property 안내에 머문다.

결정 이유:

- Story 6.2는 "하지 말아야 할 것"이 많은 UI story다.
- Static UI 단계에서는 HTML/JS contract guard가 가장 작은 비용으로 regression을 막는다.

## Open Decisions That Remain

아래 항목은 Story 6.2에서 구현자가 임의로 닫지 않는다.

- Project creation의 실제 방식, ownership, role model
- Project key 발급/회전/재발급 product workflow
- Browser UI가 JSON body로 받은 service token을 memory, sessionStorage, localStorage 중 어디에 보관할지
- Refresh token browser storage와 logout/session persistence policy
- React/Vite/TypeScript SPA 전환 시점, source 구조, build output 구조
- Application List UI의 state scan depth
- Application Dashboard UI integration layout and component model

## BMAD Create-Story Notes

Story 6.2 create-story 실행자는 아래를 지킨다.

- Story 6.2 story file은 `planning-artifacts/stories/6-2-project-selection-ui.md` 후보로 생성한다.
- Source of Truth에 이 계약 문서를 최상위 근거로 포함한다.
- Acceptance Criteria에는 Project selection scope, API field shape, navigation, no project creation, copy guard, static UI boundary, no token persistence, no UI recomputation, static UI contract tests를 포함한다.
- Tasks/Subtasks는 UI polish와 test guard 중심으로 작성하고 backend API/schema 확장은 기본 범위에서 제외한다.
- Story 6.2는 `ready-for-dev`가 되더라도 이 계약 결정을 다시 열지 않는다.
- Public class, public method, 복잡한 helper를 새로 만들면 AGENTS.md 지침에 따라 한국어 Javadoc/comment를 작성한다.

## Verification Expectations

Story 6.2 completion 전 최소 아래 검증을 수행한다.

```bash
./gradlew :observability-portal:test --tests '*ProjectNavigation*'
./gradlew :observability-portal:test --tests '*ProjectEntryUiContract*'
./gradlew :observability-portal:test --tests '*ProjectSelectionUiContract*'
./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest
./gradlew :observability-portal:test
git diff --check
```

테스트 이름은 구현 시점의 실제 test class에 맞게 조정할 수 있다. 단, static UI contract guard가 legacy alias, dashboard shortcut, project creation flow, token persistence, frontend build stack, UI-side recomputation, project-level health/status copy를 검증해야 한다.
