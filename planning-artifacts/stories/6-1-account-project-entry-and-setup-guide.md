---
artifactType: story
storyId: "6.1"
storyKey: "6-1-account-project-entry-and-setup-guide"
epic: "Epic 6. Dashboard User Flow and Demo Hardening"
title: "Account/project entry and setup guide"
architectureStyle: Traditional MVC
status: done
date: 2026-05-28
---

# Story 6.1 - Account/project entry and setup guide

## Status

done

## Story

portal 사용자는 GitHub OAuth로만 signup/login을 시작하고, Project Entry에서 project scope를 선택하거나 최소 setup guide로 진입하고 싶다.

그래야 email/password나 다른 provider, anonymous flow 없이 MVP account 경계를 지키면서, 운영 판단 화면으로 들어가기 전에 starter dependency, portal base URL, project key, environment 설정만 빠르게 확인할 수 있다.

## Source of Truth

아래 문서를 읽고 반영한 create-story context다. 구현 중 충돌처럼 보이는 지점은 `account-auth-policy.md`, Epic 5/6 dashboard alignment, 그리고 `sprint-status.yaml`의 최신 tracker 상태를 우선한다.

1. `implementation-artifacts/spec-story-6-1-account-project-entry-and-setup-guide-contract-decisions.md`
2. `implementation-artifacts/sprint-status.yaml`
3. `planning-artifacts/epics.md`
4. `planning-artifacts/sprint-plan.md`
5. `planning-artifacts/contracts/account-auth-policy.md`
6. `planning-artifacts/api-surface.md`
7. `planning-artifacts/architecture.md`
8. `planning-artifacts/architecture-implementation-supplement.md`
9. `planning-artifacts/project-structure.md`
10. `planning-artifacts/database-schema.md`
11. `planning-artifacts/acceptance-traceability.md`
12. `planning-artifacts/epic5-6-dashboard-alignment-context.md`
13. `implementation-artifacts/epic-5-retro-2026-05-27.md`
14. `_bmad/custom/project-context.md`

코드 확인 대상:

1. `observability-portal/build.gradle`
2. `build.gradle`
3. `settings.gradle`
4. `observability-portal/src/main/java/com/observation/portal/domain/catalog/controller/ProjectNavigationController.java`
5. `observability-portal/src/main/java/com/observation/portal/domain/catalog/service/ProjectApplicationNavigationService.java`
6. `observability-portal/src/main/java/com/observation/portal/domain/catalog/model/ProjectNavigationReadModel.java`
7. `observability-portal/src/main/java/com/observation/portal/domain/catalog/model/ProjectApplicationNavigationReadModel.java`
8. `observability-portal/src/main/java/com/observation/portal/domain/dashboard/controller/DashboardController.java`
9. `observability-portal/src/main/java/com/observation/portal/domain/dashboard/model/ApplicationDashboardReadModel.java`
10. `observability-portal/src/test/java/com/observation/portal/architecture/MvcLayerBoundaryTest.java`

닫힌 계약:

1. Account signup/login은 GitHub OAuth only다.
2. Email/password, local password, password reset, email verification required for signup, magic link, GitHub 외 provider, anonymous flow는 MVP 범위 밖이다.
3. GitHub OAuth token과 우리 서비스 access token/refresh token은 다른 token이다.
4. GitHub OAuth token, provider raw payload, access token, refresh token, secret은 response/log/error에 노출하지 않는다.
5. MVP resource API 인증은 cookie 기반 server session이 아니라 `Authorization: Bearer <access_token>` 기준이다.
6. Refresh token은 rotation, 만료, revoke, reuse detection을 갖춘 token store 기준이다.
7. Token issuance/refresh response는 우리 서비스 access token/refresh token을 JSON response body로 전달한다.
8. Project Entry는 운영 판단 화면이 아니라 scope 선택과 minimal setup guide 진입 화면이다.
9. Story 6.1 UI는 `observability-portal/src/main/resources/static/dashboard/` static asset으로 구현하고, API controller는 `@RestController` JSON boundary를 유지한다.
10. Setup guide는 dependency 추가, `observation.heartbeat.portal-base-url`, `observation.heartbeat.project-key`, `observation.metric-flush.environment` 설정 안내에 집중한다.
11. Project creation은 public onboarding API로 열지 않는다. Project 생성 방식, ownership, project key 발급/회전/재발급은 follow-up decision으로 남긴다.
12. Epic 6 UI는 Epic 5 read model/API를 소비한다. UI는 lifecycle state, starter connection diagnosis, rule, p95/p99, endpoint priority, snapshot/history event를 재계산하지 않는다.
13. Project/Application navigation API는 light summary와 link를 제공할 수 있지만 Application Dashboard 판단을 대체하지 않는다.
14. 로컬 GitHub OAuth App credential은 Git 추적 대상이 아닌 `.private/github-oauth.properties`에 두고, 구현은 `portal.auth.github.*` 설정 key를 참조한다.

## Scope / Out of Scope

포함:

- GitHub OAuth only account entry surface
- GitHub OAuth App credential을 gitignored local properties file에서 읽는 config boundary
- unsupported signup/login method가 화면, API, 테스트 fixture에 새로 열리지 않도록 하는 guard
- `domain.account` 후보 package와 AccountAuth controller/service/model/repository boundary 설계 또는 최소 구현
- token/secret/provider raw payload 노출 금지 guard
- Project Entry 화면 또는 route에서 existing `GET /api/projects` read-only navigation API 소비
- project가 없거나 project creation decision이 닫히지 않았을 때 local/internal seed 또는 admin 경로를 안내하는 safe empty state
- minimal setup guide copy와 UI: dependency 추가, `observation.heartbeat.portal-base-url`, `observation.heartbeat.project-key`, `observation.metric-flush.environment` 설정
- setup guide가 raw project key를 log/error/long-lived UI state에 남기지 않도록 하는 copy/implementation guard
- Application List 진입 link와 Dashboard 진입 link는 existing navigation read model의 `links`를 사용
- `observability-portal/src/main/resources/static/dashboard/` 아래 static dashboard asset
- API controller는 `@RestController`로 JSON을 반환하고, UI HTML은 Spring Boot static resource serving으로 제공하는 boundary
- no UI recomputation, no public project creation API, no secret exposure regression tests

제외:

- email/password signup/login
- local account registration, local password, password reset, email verification required for signup
- magic link signup/login
- GitHub 외 provider, Google/Kakao/Naver OAuth
- anonymous user flow
- cookie 기반 server session
- cookie 기반 server session, redirect fragment token 전달, local storage 저장 전제
- 별도 frontend build, `src/main/frontend`, React, Vite, TypeScript 도입
- View resolver, template engine 기반 HTML rendering
- GitHub OAuth token/provider raw payload 저장. GitHub API 호출이 필요해 저장해야 하면 별도 contract decision이 먼저 필요하다
- public project creation/onboarding API
- public `POST /api/projects`, 로그인 직후 자동 project 생성, UI의 "Create Project" flow
- project key 생성/회전/재발급 product workflow
- Application Dashboard, Instance Evidence, Instance Snapshot Trend, Snapshot/History UI 구현
- UI-side lifecycle state, starter connection diagnosis, insight rule, p95/p99, endpoint priority, event/marker 계산
- raw metric explorer, arbitrary query UI, endpoint timeseries UI
- 새 `operational_events` table/repository, raw instance timeseries table, materialized view, Redis/outbox

## Acceptance Criteria

1. Story 6.1은 story file과 sprint tracking이 생성된 상태에서 `ready-for-dev`가 된다.
2. 구현 story 실행 시 production 변경은 Traditional MVC + Service/Repository Layering과 feature-first package 구조를 따른다.
3. Account entry는 GitHub OAuth signup/login만 노출한다.
4. Email/password, local password, password reset, email verification required for signup, magic link, GitHub 외 provider, anonymous flow는 UI control, public API endpoint, DTO, repository schema, test fixture로 추가하지 않는다.
5. GitHub OAuth 실패, 취소, 지원하지 않는 provider 요청은 account row나 external identity row를 생성하지 않는다.
6. GitHub user id 또는 provider subject를 external identity stable key로 사용하고, email/display name/avatar는 profile metadata로만 취급한다.
7. GitHub OAuth token과 우리 서비스 access token/refresh token을 코드, model, DTO 이름과 저장 정책에서 구분한다.
8. GitHub OAuth token은 MVP에서 GitHub API 호출이 필요 없으면 저장하지 않는다.
9. GitHub OAuth token 저장이 필요하다고 판단되면 암호화, 최소 scope, 만료/회전, 폐기 기준을 별도 decision으로 먼저 닫고 이 story 안에서 임의 저장하지 않는다.
10. 일반 resource API response/log/error에는 access token, refresh token, GitHub OAuth token, provider raw payload, secret이 노출되지 않는다.
11. OAuth 실패/거부/취소 메시지는 provider 내부 payload, token 값, secret 상태를 드러내지 않는 일반화된 copy를 사용한다.
12. API 인증 기준은 cookie 기반 server session이 아니라 `Authorization: Bearer <access_token>`이다.
13. Access token은 짧은 만료 JWT, refresh token은 rotation/revoke/reuse detection 가능한 token store 기준을 따른다.
14. Token issuance/refresh response는 우리 서비스 access token/refresh token을 JSON body로 전달하고, cookie server session이나 redirect fragment 전달을 사용하지 않는다.
15. Project Entry는 project scope 선택과 setup guide 진입만 담당한다.
16. Project Entry는 lifecycle state, starter connection diagnosis, triage rule, p95/p99, endpoint priority, snapshot/history event를 계산하거나 상세 판단으로 보여주지 않는다.
17. Project Entry는 existing `GET /api/projects` navigation read model을 우선 소비하고, `projectId`, `name`, `applicationCount`, `setupConnectionIssueCount`, `recentConcern`, `links.applications` 이상의 판단을 새로 만들지 않는다.
18. Project가 비어 있으면 public project creation API를 만들지 않고, local/internal seed 또는 admin-only bootstrap decision이 필요하다는 safe empty state를 보여준다.
19. Story 6.1은 public `POST /api/projects`, 로그인 직후 자동 project 생성, UI의 "Create Project" flow를 추가하지 않는다.
20. Project creation 방식, project ownership, project key 발급/회전/재발급 정책은 follow-up decision으로 남긴다.
21. Setup guide는 dependency 추가, `observation.heartbeat.portal-base-url`, `observation.heartbeat.project-key`, `observation.metric-flush.environment` 설정만 안내한다.
22. Setup guide는 `observation.metric-flush.project-id`, `observation.metric-flush.application-name`, `observation.metric-flush.instance`, queue/drop/heartbeat interval, route allowlist, dashboard tuning, alert delivery, endpoint priority 해석, p95/p99 계산법, raw query/explorer 사용법을 핵심 안내로 포함하지 않는다.
23. Project key 안내 copy는 "발급된 project key를 starter 설정에 넣는다" 수준으로 제한하고, raw key를 DB row, migration, log, exception, response body, repository lookup surface에 남기지 않는 guard를 유지한다.
24. Portal base URL과 environment 값은 starter 설정 예시로 보여주되, secret과 provider token을 예시 값으로 넣지 않는다.
25. Project Entry에서 Application List로 이동할 때는 navigation read model의 `links.applications`를 사용한다.
26. Application Dashboard로 이동할 때는 후속 Application List UI 또는 existing `links.dashboard`를 사용하고, Project Entry가 dashboard 판단을 shortcut으로 복제하지 않는다.
27. Static dashboard asset 위치는 `observability-portal/src/main/resources/static/dashboard/`로 고정한다.
28. Story 6.1은 별도 frontend build, `src/main/frontend`, React, Vite, TypeScript를 도입하지 않는다.
29. API controller는 `@RestController`로 JSON을 반환하고, UI HTML은 Spring Boot static resource serving으로 제공한다. View resolver/template engine은 도입하지 않는다.
30. `/dashboard` 또는 `/dashboard/` 편의 route가 필요하면 `/dashboard/index.html`로 보내는 얇은 redirect controller까지만 허용한다.
31. 새 account code를 추가한다면 `com.observation.portal.domain.account` 아래의 `controller`, `dto`, `model`, `repository`, `service` 책임을 따른다.
32. `application`, `port`, `adapter`, `adapter.in`, `adapter.out` package를 만들지 않는다.
33. Controller는 repository를 직접 호출하지 않고 service에 위임한다.
34. Repository는 controller/dto에 의존하지 않고 state/rule/p95/p99/endpoint priority를 계산하지 않는다.
35. JPA entity를 controller response DTO, public API surface, service external result로 직접 반환하지 않는다.
36. Account/auth schema가 필요하면 internal account row와 external identity row를 분리하고, provider는 MVP에서 `github`만 허용한다.
37. Local password hash, password reset token, email verification required for signup, magic link, 다른 provider, anonymous user를 위한 schema는 만들지 않는다.
38. Refresh token 저장소는 Redis에 고정하지 않고 token store 추상으로 둔다. 초기 후보는 RDBMS hashed refresh token 또는 token family metadata다.
39. GitHub OAuth App credential 실제 값은 `.private/github-oauth.properties`에서 읽고, repository에는 실제 `client_id`/`client_secret` 값을 커밋하지 않는다.
40. Epic 5 read model/API field를 UI에서 조합해 lifecycle state, starter connection diagnosis, rule, p95/p99, endpoint priority를 재계산하지 않는다.
41. Empty state copy는 "현재 문제 없음", "앱 정상 확정", "복구 완료"처럼 current health를 단정하지 않는다.
42. Public class, public method, 복잡한 helper에는 AGENTS.md 지침에 따라 한국어 Javadoc/comment를 남긴다.
43. Focused tests는 GitHub OAuth only entry, unsupported method absence, OAuth failure no-account-creation, token/secret exposure absence를 검증한다.
44. Focused tests는 Project Entry가 `GET /api/projects` read model을 표시만 하고 dashboard 판단을 대체하지 않음을 검증한다.
45. Focused tests는 setup guide copy가 dependency, `observation.heartbeat.portal-base-url`, `observation.heartbeat.project-key`, `observation.metric-flush.environment`에만 머무르고 raw explorer나 metric 계산 안내를 포함하지 않음을 검증한다.
46. Regression tests는 no public project creation API, no email/password/magic link/provider 확장 API, no cookie server session policy를 검증한다.
47. Architecture guard는 account package와 UI/static code가 MVC package boundary, no forbidden package, no repository direct controller call을 유지함을 검증한다.
48. `./gradlew :observability-portal:test --tests '*ProjectNavigation*'`, account/auth focused tests, `./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest`, full `./gradlew :observability-portal:test`, `git diff --check`가 통과해야 implementation completion으로 볼 수 있다.

## Tasks / Subtasks

- [x] Account entry/auth policy boundary 구현 (AC: 3~14, 31, 36~39, 42~43, 46)
  - [x] GitHub OAuth only CTA/entry route 또는 API start boundary를 추가한다.
  - [x] email/password, magic link, 다른 OAuth provider, anonymous flow control을 만들지 않는다.
  - [x] OAuth failure/cancel/unsupported provider가 account row를 만들지 않는 service guard를 둔다.
  - [x] provider subject를 stable identity key로 쓰고 email/profile field는 metadata로만 취급한다.
  - [x] provider token/service token/raw payload/secret이 response/log/error에 노출되지 않는 guard를 둔다.
  - [x] 우리 서비스 access/refresh token issuance/refresh response는 JSON body로 반환하고 cookie session/redirect fragment 전제를 만들지 않는다.
  - [x] GitHub OAuth App credential은 `.private/github-oauth.properties`의 `portal.auth.github.*` 설정 key를 통해 읽는다.

- [x] Project Entry 화면/route 구현 (AC: 15~20, 25~30, 40~41, 44)
  - [x] existing `GET /api/projects` read-only navigation read model을 소비한다.
  - [x] project list item은 project identity, application count, setup/connection issue 후보, recentConcern 0~1개, application list link만 표시한다.
  - [x] lifecycle state, starter connection diagnosis, rule, p95/p99, endpoint priority, snapshot/history event 계산 helper를 만들지 않는다.
  - [x] empty state는 public project creation API를 열지 않고 local/internal seed 또는 admin bootstrap 필요성을 안내한다.
  - [x] static asset은 `observability-portal/src/main/resources/static/dashboard/`에 두고 별도 frontend build를 만들지 않는다.
  - [x] API controller는 JSON `@RestController` boundary를 유지하고 HTML view resolver/template engine을 추가하지 않는다.

- [x] Minimal setup guide 구현 (AC: 21~24, 45)
  - [x] starter dependency 추가 안내를 제공한다.
  - [x] `observation.heartbeat.portal-base-url` 설정 안내를 제공한다.
  - [x] `observation.heartbeat.project-key` 설정 안내를 제공하되 raw key/secret을 log/error/long-lived state에 남기지 않는다.
  - [x] `observation.metric-flush.environment` 설정 안내를 제공한다.
  - [x] project-id/application-name/instance, queue/drop/heartbeat interval, route allowlist, dashboard tuning, alert delivery, raw explorer, p95/p99 계산법, endpoint priority 판단 안내를 넣지 않는다.

- [x] MVC/package/schema guard 유지 (AC: 2, 32~35, 42, 47)
  - [x] `domain.account`를 추가할 경우 feature-first MVC package만 사용한다.
  - [x] controller는 service 위임만 하고 repository를 직접 호출하지 않는다.
  - [x] repository는 controller/dto에 의존하지 않는다.
  - [x] JPA entity를 API response나 service external model로 반환하지 않는다.
  - [x] 공개 production class/method와 복잡한 helper에 한국어 Javadoc/comment를 작성한다.

- [x] Verification (AC: 43~48)
  - [x] account/auth focused tests를 추가하거나 기존 test naming에 맞춰 작성한다.
  - [x] Project Entry/static UI contract test 또는 HTML/content guard test를 추가한다.
  - [x] `MvcLayerBoundaryTest`에 account/static UI boundary가 필요한 경우 보강한다.
  - [x] suggested command를 실행하고 실패 시 story completion으로 표시하지 않는다.

## Dev Notes

### Current Code State

- 현재 branch는 `codex/story-6-1-account-project-entry-and-setup-guide`다.
- `implementation-artifacts/sprint-status.yaml` 기준 Epic 1~5와 각 retrospective는 done이고, Epic 6은 backlog였다.
- Story 6.1은 Epic 6의 첫 story이므로 create-story 과정에서 `epic-6`은 `in-progress`, `6-1-account-project-entry-and-setup-guide`는 `ready-for-dev`가 된다.
- `domain.account` package는 아직 없다. Account/auth 구현을 시작하면 새 feature package로 추가한다.
- `ProjectNavigationController`는 이미 `GET /api/projects`, `GET /api/projects/{projectId}/applications`를 제공한다.
- `ProjectApplicationNavigationService`는 project/application navigation read model을 만들되 dashboard state/triage 판단을 만들지 않는다.
- `ProjectNavigationReadModel`은 Project Entry용 light summary이며 dashboard state나 triage 판단을 포함하지 않는다.
- `ProjectApplicationNavigationReadModel`은 accepted bucket freshness와 starter heartbeat summary를 별도 field로 제공한다.
- `DashboardController`는 `GET /api/projects/{projectId}/applications/{applicationId}/dashboard`를 제공하고 dashboard 조립/state 판단은 service에 위임한다.
- `ApplicationDashboardReadModel`은 zeroInsight, recovery, sourceScopedPercentiles, histogramDistribution, triageCards, endpointPriority, instances, snapshot field를 server read model로 제공한다.
- `observability-portal/src/main/resources/static/dashboard/`는 현재 존재하지 않는다. Story 6.1 구현이 static dashboard asset을 추가하는 첫 작업일 수 있다.
- `observability-portal/build.gradle`에는 Spring Security OAuth2 Client/Resource Server/Jose dependency가 아직 없다. Auth backend를 구현할 때는 project BOM 기준으로 필요한 dependency만 추가한다.

### Account/Auth Guardrails

- Account signup/login은 GitHub OAuth only다.
- 로컬 GitHub OAuth App credential은 Git 추적 대상이 아닌 `.private/github-oauth.properties`의 `portal.auth.github.client-id`, `portal.auth.github.client-secret`, `portal.auth.github.redirect-uri`, `portal.auth.github.homepage-url`에서 읽는다.
- `GET /api/auth/github/authorize`, `GET /api/auth/github/callback`, `POST /api/auth/token/refresh`, `POST /api/auth/logout`는 `api-surface.md`의 후보 surface다. 구현 여부와 깊이는 token delivery decision과 story scope를 확인하고 진행한다.
- OAuth callback에서 provider subject 검증이 성공하기 전에는 internal account나 external identity를 만들지 않는다.
- OAuth 실패, 취소, unsupported provider는 account 생성 없이 일반화된 오류로 수렴한다.
- Provider token/raw payload/secret은 DTO, exception message, log, test assertion snapshot에 남기지 않는다.
- Access/refresh token도 일반 resource API response/log/error에는 노출하지 않는다.
- Token issuance/refresh response 자체의 token 전달 방식은 JSON response body로 닫는다. 구현자는 cookie server session, redirect fragment, browser local storage 저장 전제를 만들지 않는다.
- Cookie 기반 server session은 MVP 인증 기준이 아니다.
- Refresh token store는 Redis 고정이 아니다. RDBMS hashed refresh token 또는 token family metadata가 초기 후보이며, fully stateless refresh token을 이유로 logout/revoke/reuse detection을 약화하지 않는다.

### Project Entry / Setup Guide Guardrails

- Project Entry는 운영 판단 화면이 아니다.
- Project Entry는 scope 선택과 setup guide 진입만 담당한다.
- Project Entry는 existing navigation read model을 표시하고, dashboard state/triage/endpoint priority/p95 판단을 새로 만들지 않는다.
- `setupConnectionIssueCount`는 light candidate count다. host application down 원인 확정이나 dashboard health 판단으로 표현하지 않는다.
- Project creation은 public onboarding API로 열지 않는다. Story 6.1은 public `POST /api/projects`, 로그인 직후 자동 project 생성, UI의 "Create Project" flow를 추가하지 않는다.
- Setup guide는 dependency, `observation.heartbeat.portal-base-url`, `observation.heartbeat.project-key`, `observation.metric-flush.environment` 설정 안내까지만 제공한다.
- `observation.metric-flush.project-id`, `observation.metric-flush.application-name`, `observation.metric-flush.instance`, queue/drop/heartbeat interval, route allowlist는 이번 story의 핵심 setup guide 안내에서 제외한다.
- Project key는 raw secret이다. guide copy와 implementation은 raw key 전체를 DB, migration, log, exception, response body, repository lookup surface에 남기지 않는다.
- Empty state는 "project가 없으니 local/internal seed 또는 admin bootstrap decision이 필요하다"는 방향으로 둔다. public signup 직후 자동 project 생성 정책을 만들지 않는다.

### Epic 5 Read Model Consumption

- Epic 6 UI는 Epic 5 read model/API를 소비한다.
- UI는 lifecycle state, starter connection diagnosis, insight rule, p95/p99, endpoint priority, snapshot/history event를 재계산하지 않는다.
- Percentile은 source-scoped starter canonical point다. UI helper가 histogram bucket에서 p95/p99를 만들거나 여러 instance/window percentile을 평균/최댓값/병합하지 않는다.
- Starter heartbeat는 accepted bucket freshness나 host application health가 아니다. UI는 metric data axis와 starter connection axis를 분리한다.
- Empty/missing history나 setup state는 current health를 단정하지 않는다.

### Project Structure Notes

- Active architecture는 Traditional MVC + Service/Repository Layering이다.
- Portal package는 feature-first MVC이며 `domain`은 business feature namespace다.
- 금지 package는 `application`, `port`, `adapter`, `adapter.in`, `adapter.out`다.
- Account code는 `com.observation.portal.domain.account` 아래에 둔다.
- Static dashboard runtime asset 위치는 `observability-portal/src/main/resources/static/dashboard/`다.
- Story 6.1은 별도 frontend build, `src/main/frontend`, React, Vite, TypeScript를 도입하지 않는다.
- API controller는 `@RestController` JSON boundary를 유지하고, UI HTML은 Spring Boot static resource serving으로 제공한다. View resolver/template engine은 도입하지 않는다.
- `/dashboard` 또는 `/dashboard/` 편의 route가 필요하면 `/dashboard/index.html`로 redirect하는 얇은 controller까지만 허용한다.
- Java baseline은 17이고 root build는 Gradle Groovy DSL을 사용한다.

### Open Decisions / Risks

- Project creation을 public onboarding API로 열지 않는 결정은 닫혔다. 실제 생성 방식, ownership, project key 발급/회전/재발급 정책은 follow-up decision으로 남긴다.
- Browser UI가 response body로 받은 service token을 메모리, sessionStorage, localStorage 중 어디에 보관할지는 아직 open decision이다. Story 6.1은 localStorage 저장 전제를 계약으로 만들지 않는다.
- GitHub OAuth token 저장 필요성이 생기면 암호화, 최소 scope, 만료/회전, 폐기 기준을 먼저 닫아야 한다.
- React + TypeScript 전환은 Story 6.1 이후 UI 상태/라우팅/컴포넌트 복잡도가 커질 때 follow-up decision으로 검토한다. 이번 story의 runtime delivery는 static asset으로 닫는다.
- Epic 6의 가장 큰 위험은 UI가 친절해지려다가 server read model 계산을 되가져오는 것이다. UI convenience helper가 계산 helper로 변하지 않게 테스트와 리뷰에서 확인한다.

## Testing

Focused test 대상 후보:

- `AccountAuthPolicyTest`
- `GithubOAuthFailureTest`
- `UnsupportedSignupMethodTest`
- `AuthSecretExposureGuardTest`
- `AuthTokenBodyDeliveryPolicyTest`
- `ServiceTokenPolicyTest`
- `ProjectEntryUiContractTest`
- `ProjectNavigationControllerTest`
- `ProjectApplicationNavigationServiceTest`
- `MvcLayerBoundaryTest`

필수 scenario:

- GitHub OAuth entry만 노출된다.
- email/password, magic link, GitHub 외 provider, anonymous flow endpoint/control이 없다.
- OAuth failure/cancel/unsupported provider가 account/external identity row를 만들지 않는다.
- Provider subject가 stable identity key이며 email은 stable identity key가 아니다.
- response/log/error에 GitHub OAuth token, provider raw payload, access token, refresh token, secret이 없다.
- token issuance/refresh response는 JSON body로 service access/refresh token을 전달하고 cookie session/redirect fragment를 만들지 않는다.
- Project Entry는 `GET /api/projects` read model을 표시하고 dashboard 판단을 대체하지 않는다.
- Project Entry empty state가 public project creation API를 만들거나 자동 project 생성 정책을 암시하지 않는다.
- Setup guide copy가 dependency, `observation.heartbeat.portal-base-url`, `observation.heartbeat.project-key`, `observation.metric-flush.environment`에 집중한다.
- Static UI가 별도 frontend build, `src/main/frontend`, React, Vite, TypeScript, View resolver/template engine을 도입하지 않는다.
- UI/static code에 lifecycle state, starter connection diagnosis, rule, p95/p99, endpoint priority, snapshot/history event 계산이 없다.
- `domain.account` package가 Traditional MVC boundary를 지키고 금지 package가 추가되지 않는다.

Suggested commands:

```bash
./gradlew :observability-portal:test --tests '*ProjectNavigation*'
./gradlew :observability-portal:test --tests '*AccountAuth*'
./gradlew :observability-portal:test --tests '*GithubOAuth*'
./gradlew :observability-portal:test --tests '*UnsupportedSignupMethod*'
./gradlew :observability-portal:test --tests '*AuthSecretExposure*'
./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest
./gradlew :observability-portal:test
git diff --check
```

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- 2026-05-28: BMAD create-story workflow, project context, sprint status, Epic 6 story target를 확인했다.
- 2026-05-28: `6-1-account-project-entry-and-setup-guide`가 Epic 6의 첫 backlog story임을 확인했다.
- 2026-05-28: Epic 1~5와 각 retrospective가 done이고 Epic 6이 backlog임을 확인했다.
- 2026-05-28: account auth policy, API surface, architecture, project structure, database schema, acceptance traceability, Epic 5 retrospective를 확인했다.
- 2026-05-28: 현재 catalog navigation API, dashboard API/model, root/portal Gradle 설정, account package 부재, static dashboard asset 부재를 확인했다.
- 2026-05-28: Story 6.1 사전 계약 결정으로 token JSON body delivery, no public project creation API, static dashboard asset, 최소 setup guide property, no UI recalculation boundary를 반영했다.
- 2026-05-28: 기존 미추적 파일 `implementation-artifacts/epic-5-dbml-snapshot-2026-05-28.dbml`은 작업 범위와 무관하므로 건드리지 않았다.
- 2026-05-28: BMAD dev-story workflow를 시작하고 사전 계약 문서를 1회 읽은 뒤 구현 기준으로 고정했다.
- 2026-05-28: `sprint-status.yaml`의 Story 6.1 상태를 `in-progress`로 전환했다.
- 2026-05-28: RED 단계에서 account/auth focused test와 Project Entry static UI contract test를 추가했고, production class/static asset 부재로 실패함을 확인했다.
- 2026-05-28: GREEN 단계에서 `domain.account` MVC package, GitHub OAuth only JSON controller, RDBMS refresh token store 후보, service token JSON body delivery를 구현했다.
- 2026-05-28: static dashboard asset에서 `GET /api/projects` read model 표시, GitHub CTA, minimal setup guide를 구현했다.
- 2026-05-28: `MvcLayerBoundaryTest`와 migration test를 Story 6.1 account/schema boundary에 맞게 보강했다.
- 2026-05-28: pre-existing modified file `planning-artifacts/contracts/account-auth-policy.md`와 unrelated untracked implementation artifacts는 건드리지 않았다.
- 2026-05-28: 필수 focused tests, full `:observability-portal:test`, `git diff --check`를 통과했다.

### Implementation Plan

- GitHub OAuth App 진입은 `GET /api/auth/github/authorize`, callback은 `GET /api/auth/github/callback` JSON API로 두고, email/password 등 다른 auth method route는 추가하지 않는다.
- OAuth provider token은 GitHub user id 조회에만 쓰고 저장하지 않으며, 내부 account/external identity row는 provider subject 확인 뒤에만 생성한다.
- 우리 서비스 access token은 짧은 만료 JWT로 만들고 refresh token 원문은 JSON body로만 전달한 뒤 DB에는 SHA-256 hash와 family metadata만 저장한다.
- Project Entry UI는 `static/dashboard/`의 정적 HTML/CSS/JS로 구현하고, browser JS는 Epic 5 read model을 표시만 하며 운영 판단을 재계산하지 않는다.

### Completion Notes List

- GitHub OAuth only account entry를 `@RestController` JSON boundary로 추가했고, token issuance/refresh response는 cookie나 redirect fragment 없이 JSON body로 반환한다.
- Account/external identity schema와 refresh token family/token schema를 추가했다. Provider subject는 stable identity key로 사용하고 email/display/avatar는 metadata로만 저장한다.
- Refresh token 원문은 저장하지 않고 SHA-256 hash와 rotation/revoke/reuse detection metadata만 RDBMS 후보 store에 저장한다.
- OAuth failure/cancel/unsupported route는 account/external identity row를 만들지 않고, error response는 provider payload/token/secret을 노출하지 않는 일반화된 copy를 사용한다.
- Project Entry static UI는 existing `GET /api/projects` read model을 소비해 project identity, application count, setup candidate count, recent concern, applications link만 표시한다.
- Setup guide는 starter dependency, `observation.heartbeat.portal-base-url`, `observation.heartbeat.project-key`, `observation.metric-flush.environment`만 안내한다.
- UI/static guard와 architecture guard는 React/Vite/TypeScript/frontend build, public `POST /api/projects`, unsupported auth method, UI-side Epic 5 judgement recomputation을 막도록 보강했다.
- Verification 통과: `*ProjectNavigation*`, `*AccountAuth*`, `*GithubOAuth*`, `*UnsupportedSignupMethod*`, `*AuthSecretExposure*`, `MvcLayerBoundaryTest`, full `:observability-portal:test`, `git diff --check`.

### File List

- `planning-artifacts/stories/6-1-account-project-entry-and-setup-guide.md`
- `implementation-artifacts/sprint-status.yaml`
- `implementation-artifacts/spec-story-6-1-account-project-entry-and-setup-guide-contract-decisions.md`
- `observability-portal/src/main/resources/application.properties`
- `observability-portal/src/main/resources/db/migration/V008__create_accounts_and_refresh_tokens.sql`
- `observability-portal/src/main/resources/db/migration/V009__create_oauth_state_nonces.sql`
- `observability-portal/src/main/resources/static/dashboard/index.html`
- `observability-portal/src/main/resources/static/dashboard/styles.css`
- `observability-portal/src/main/resources/static/dashboard/app.js`
- `observability-portal/src/main/java/com/observation/portal/domain/account/controller/BearerResourceApiInterceptor.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/controller/BearerResourceApiWebConfig.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/controller/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/controller/AccountAuthController.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/dto/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/dto/AccountTokenResponse.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/dto/AuthErrorResponse.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/dto/GithubAuthorizeResponse.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/dto/LogoutRequest.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/dto/RefreshTokenRequest.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/model/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/model/AccountAuthResult.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/model/GithubAuthorizationStart.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/model/GithubOAuthCallbackCommand.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/model/ServiceTokenPair.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/model/VerifiedGithubIdentity.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/repository/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/repository/AccountEntity.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/repository/AccountJpaRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/repository/ExternalIdentityEntity.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/repository/ExternalIdentityJpaRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/repository/OAuthStateNonceEntity.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/repository/OAuthStateNonceJpaRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/repository/RefreshTokenEntity.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/repository/RefreshTokenFamilyEntity.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/repository/RefreshTokenFamilyJpaRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/repository/RefreshTokenJpaRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/service/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/service/AccountAuthException.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/service/AccountAuthService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/service/GithubOAuthAppProperties.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/service/GithubOAuthClient.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/service/HttpGithubOAuthClient.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/service/OAuthStateSigner.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/service/ServiceTokenIssuer.java`
- `observability-portal/src/test/java/com/observation/portal/domain/account/controller/AccountAuthControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/account/controller/AuthSecretExposureGuardTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/account/controller/UnsupportedSignupMethodTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/account/service/AccountAuthRefreshTokenLifecycleIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/account/service/GithubOAuthFailureTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/controller/ProjectNavigationResourceAuthorizationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ProjectEntryUiContractTest.java`
- `observability-portal/src/test/java/com/observation/portal/architecture/MvcLayerBoundaryTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/repository/CatalogSchemaMigrationIntegrationTest.java`
- `planning-artifacts/contracts/account-auth-policy.md`

### Change Log

- 2026-05-28: Story 6.1 Account/project entry and setup guide create-story 산출물을 생성했다.
- 2026-05-28: Story 6.1 account auth, Project Entry static UI, minimal setup guide, MVC/schema guard를 구현하고 review 상태로 전환했다.
- 2026-05-28: BMAD code review findings를 반영해 refresh lifecycle, OAuth state nonce, Bearer resource API guard를 보강하고 done 상태로 전환했다.
