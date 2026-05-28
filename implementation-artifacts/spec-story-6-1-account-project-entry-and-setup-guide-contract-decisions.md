---
artifactType: contract-decisions
projectName: Spring Boot 운영 첫 화면 포털
storyId: "6.1"
storyKey: "6-1-account-project-entry-and-setup-guide"
status: closed
date: 2026-05-28
scope: Story 6.1 pre-dev contract closure before BMAD dev-story execution
---

# Story 6.1 Account Project Entry and Setup Guide Contract Decisions

## Purpose

Story 6.1은 GitHub OAuth only account entry, Project Entry, minimal setup guide, static dashboard UI의 경계를 닫는다.

이 문서는 Story 6.1 dev-story 실행 전에 사용자가 직접 결정한 계약을 기록한다. 목표는 구현자가 auth token 전달 방식, project creation, frontend stack, setup guide property, Epic 5 read model 소비 경계를 다시 열지 않게 하는 것이다.

Story 6.1은 로그인/계정 진입과 Project Entry의 최소 흐름을 구현하되, public project creation onboarding, full dashboard UI, frontend build stack, UI-side 운영 판단 계산을 만들지 않는다.

## Authority and Non-Reopen Rules

이 문서는 아래 계약과 산출물을 기준으로 한다.

- `planning-artifacts/stories/6-1-account-project-entry-and-setup-guide.md`
- `planning-artifacts/contracts/account-auth-policy.md`
- `planning-artifacts/api-surface.md`
- `planning-artifacts/project-structure.md`
- `planning-artifacts/database-schema.md`
- `planning-artifacts/epic5-6-dashboard-alignment-context.md`
- `planning-artifacts/contracts/dashboard-read-model.md`
- `planning-artifacts/contracts/read-model-contract.md`
- `implementation-artifacts/sprint-status.yaml`

Story 6.1 dev-story는 아래 결정을 다시 열지 않는다.

- Account signup/login은 GitHub OAuth only다.
- GitHub OAuth App을 사용한다. GitHub App은 repository 설치, webhook, bot actor, fine-grained repository permission이 필요해질 때 후속 선택지로 둔다.
- 우리 서비스 access token/refresh token은 JSON response body로 전달한다.
- Cookie 기반 server session, redirect fragment token 전달, browser local storage 저장 전제는 Story 6.1 계약에 포함하지 않는다.
- Refresh token 저장소는 Redis에 고정하지 않는다. 초기 후보는 PostgreSQL/RDBMS hashed refresh token 또는 token family metadata다.
- Public `POST /api/projects`, 로그인 직후 자동 project 생성, UI의 "Create Project" flow는 만들지 않는다.
- Story 6.1 UI는 `observability-portal/src/main/resources/static/dashboard/` static asset으로 구현한다.
- 별도 frontend build, `src/main/frontend`, React, Vite, TypeScript는 도입하지 않는다.
- API controller는 `@RestController` JSON boundary를 유지한다. View resolver/template engine은 도입하지 않는다.
- Setup guide는 starter dependency, `observation.heartbeat.portal-base-url`, `observation.heartbeat.project-key`, `observation.metric-flush.environment`만 핵심 안내로 둔다.
- Epic 6 UI는 Epic 5 API/read model을 표시만 한다. UI는 lifecycle state, starter connection diagnosis, rule, p95/p99, endpoint priority, snapshot/history event를 재계산하지 않는다.

## Closed Decisions

### 1. GitHub OAuth App and Service Token Contract

Story 6.1 account entry는 GitHub OAuth App 기반으로 구현한다.

결정 내용:

- GitHub OAuth App의 `client_id`, `client_secret`, `redirect_uri`, `homepage-url`은 Git 추적 대상이 아닌 `.private/github-oauth.properties`에 둔다.
- 구현은 아래 설정 key를 참조한다.
  - `portal.auth.github.client-id`
  - `portal.auth.github.client-secret`
  - `portal.auth.github.redirect-uri`
  - `portal.auth.github.homepage-url`
- Repository에는 실제 OAuth App credential 값을 커밋하지 않는다.
- 계약 문서와 테스트는 property key와 gitignored file path만 고정한다.
- GitHub OAuth provider token과 우리 서비스 access/refresh token은 이름, DTO, 저장 정책에서 구분한다.
- MVP에서 GitHub API 호출이 필요 없다면 GitHub OAuth access token/refresh token은 저장하지 않는다.
- Provider subject 또는 GitHub user id를 external identity stable key로 사용한다.
- Email, display name, avatar는 profile metadata로만 취급한다.

우리 서비스 token 결정:

- Access token은 짧은 만료 JWT다.
- Refresh token은 client에 JSON body로 전달한다.
- Refresh token 원문은 저장하지 않는다.
- 서버 저장소는 token store 추상으로 두고, 초기 후보는 PostgreSQL/RDBMS hashed refresh token 또는 token family metadata다.
- Refresh token rotation, revoke, reuse detection 기준을 둔다.
- Redis는 고성능 revoke list, distributed token state, reuse detection 최적화가 필요할 때 후속 선택지로 둔다.

금지:

- Email/password signup/login, local password, password reset, magic link, GitHub 외 OAuth provider, anonymous flow를 만들지 않는다.
- Cookie 기반 server session을 만들지 않는다.
- Token을 redirect fragment로 전달하지 않는다.
- Browser localStorage 저장을 Story 6.1 계약으로 고정하지 않는다.
- Provider token/raw payload/secret을 response, log, exception, test snapshot에 노출하지 않는다.

결정 이유:

- 이번 story는 GitHub 계정 진입과 service token boundary를 닫는 story다.
- GitHub App은 repository 설치형 integration에 맞고, 현재 제품 요구는 account signup/login이다.
- Redis를 지금 추가하면 auth보다 인프라 결정이 커진다. 기존 프로젝트의 PostgreSQL/Flyway/JPA 흐름과 맞게 RDBMS token store 후보를 유지한다.

### 2. Project Entry and No Public Project Creation Contract

Story 6.1은 project creation을 public onboarding API로 열지 않는다.

결정 내용:

- Project Entry는 existing `GET /api/projects` navigation read model을 소비한다.
- Project Entry는 project identity, application count, setup connection issue candidate count, recent concern 0~1개, application list link를 표시한다.
- Project가 없을 때는 local/internal seed 또는 admin-only bootstrap decision이 필요하다는 safe empty state를 보여준다.
- Project creation 방식, ownership, project key 발급/회전/재발급 정책은 follow-up decision으로 남긴다.

금지:

- Public `POST /api/projects`를 만들지 않는다.
- 로그인 직후 자동 project 생성을 만들지 않는다.
- UI에 "Create Project" flow를 만들지 않는다.
- Project key 생성, 회전, 재발급 product workflow를 만들지 않는다.
- GitHub account와 project ownership/role model을 임의로 묶지 않는다.

결정 이유:

- Project creation을 열면 owner, role, key issuance, duplicate project name, account-project relation이 함께 닫혀야 한다.
- Story 6.1은 demo hardening의 entry story이므로 read-only Project Entry와 safe empty state가 적절하다.

### 3. Static Dashboard UI and Controller Boundary Contract

Story 6.1 UI는 portal static asset으로 구현한다.

결정 내용:

- Runtime UI asset 위치는 `observability-portal/src/main/resources/static/dashboard/`로 고정한다.
- 기본 파일 후보는 아래와 같다.
  - `index.html`
  - `styles.css`
  - `app.js`
- API controller는 `@RestController`로 JSON을 반환한다.
- HTML은 Spring Boot static resource serving으로 제공한다.
- `/dashboard` 또는 `/dashboard/` 편의 route가 필요하면 `/dashboard/index.html`로 redirect하는 얇은 controller까지만 허용한다.
- React + TypeScript 전환은 Story 6.1 이후 UI 상태, routing, component complexity가 커질 때 follow-up decision으로 검토한다.

금지:

- 별도 frontend build를 만들지 않는다.
- `src/main/frontend`를 만들지 않는다.
- React, Vite, TypeScript를 도입하지 않는다.
- View resolver 또는 template engine 기반 HTML rendering을 도입하지 않는다.
- 별도 frontend deployable을 만들지 않는다.

결정 이유:

- Story 6.1은 Project Entry와 setup guide 중심의 작은 UI다.
- 지금 frontend build stack을 추가하면 story의 본론보다 tooling 결정이 커진다.
- Static delivery로 시작해도 URL/API boundary를 유지하면 후속 React + TypeScript 전환을 막지 않는다.

### 4. Minimal Setup Guide Contract

Story 6.1 setup guide는 starter를 portal에 연결하기 위한 최소 설정만 안내한다.

결정 내용:

- Dependency 추가 안내를 제공한다.
- Portal base URL은 기존 starter property인 `observation.heartbeat.portal-base-url`로 안내한다.
- Project key는 기존 starter property인 `observation.heartbeat.project-key`로 안내한다.
- Environment는 기존 starter property인 `observation.metric-flush.environment`로 안내한다.
- Dependency coordinate 예시는 현재 root Gradle group/version 기준으로 `com.sst:observability-spring-boot-starter:0.1.0-SNAPSHOT` 후보를 사용할 수 있다.
- Maven publish 방식, repository URL, production artifact distribution은 follow-up/open decision으로 남긴다.
- Project key 값은 portal/admin/local seed가 발급한 값을 사용한다고 표현한다.

금지:

- Project key 발급/회전/재발급 flow를 안내하지 않는다.
- Raw project key 전체를 DB row, migration, log, exception, response body, repository lookup surface, long-lived UI state에 남기지 않는다.
- `observation.metric-flush.project-id`, `observation.metric-flush.application-name`, `observation.metric-flush.instance`를 핵심 guide 항목으로 만들지 않는다.
- Queue capacity, drop policy, heartbeat interval, route allowlist, dashboard tuning, alert delivery, p95/p99 계산법, endpoint priority 해석, raw query/explorer 사용법을 안내하지 않는다.
- 아직 없는 starter property 이름을 새 계약처럼 확정하지 않는다.

결정 이유:

- Setup guide는 사용자가 "dependency와 최소 설정"만 확인하고 진입하게 하는 화면이다.
- 기존 starter 설정 이름을 재사용해야 후속 구현자가 임의 onboarding property를 만들지 않는다.

### 5. Epic 5 Read Model Display-Only UI Contract

Epic 6 UI는 Epic 5 API/read model을 소비해 표시만 한다.

결정 내용:

- Project Entry는 `GET /api/projects` read model을 표시한다.
- Application/dashboard 판단이 필요한 화면은 existing dashboard/history/snapshot API가 제공한 값을 그대로 사용한다.
- UI helper는 formatting, empty/null handling, link navigation까지만 허용한다.
- Empty state는 "현재 문제 없음", "앱 정상 확정", "복구 완료"처럼 current health를 단정하지 않는다.
- Starter heartbeat는 accepted bucket freshness나 host application health로 합성하지 않는다.

금지:

- UI에서 lifecycle state를 계산하지 않는다.
- UI에서 starter connection diagnosis를 계산하지 않는다.
- UI에서 insight rule을 계산하지 않는다.
- UI에서 histogram bucket 또는 raw metric으로 p95/p99를 계산하지 않는다.
- UI에서 endpoint priority를 계산하지 않는다.
- UI에서 snapshot/history event를 만들거나 재해석하지 않는다.
- JS에 `calculateState`, `computeP95`, `rankEndpoint`, `diagnoseConnection`, `buildHistoryEvent` 같은 계산 helper를 만들지 않는다.

결정 이유:

- Epic 5가 server-side read model과 운영 판단의 source of truth다.
- UI가 판단을 복제하면 server와 browser가 서로 다른 운영 결론을 낼 수 있다.

## Open Decisions That Remain

아래 항목은 Story 6.1에서 구현자가 임의로 닫지 않는다.

- Project creation의 실제 방식, ownership, role model
- Project key 발급/회전/재발급 product workflow
- Browser UI가 JSON body로 받은 service token을 memory, sessionStorage, localStorage 중 어디에 보관할지
- GitHub provider token 저장이 필요한 경우의 encryption, scope, expiry, rotation, revoke policy
- React + TypeScript 전환 시점과 source/build output 구조
- Maven publish/repository distribution 방식

## BMAD Dev-Story Implementation Notes

Story 6.1 dev-story 실행자는 아래를 지킨다.

- Story file은 BMAD dev-story workflow 지침에 따라 Tasks/Subtasks checkboxes, Dev Agent Record, File List, Change Log, Status 영역만 수정한다.
- 계약 결정 자체를 바꾸는 문서 수정은 하지 않는다.
- 필요한 production/test code는 Traditional MVC + Service/Repository Layering과 feature-first package 구조를 따른다.
- Public class, public method, 복잡한 helper에는 AGENTS.md 지침에 따라 한국어 Javadoc/comment를 작성한다.
- 실제 GitHub OAuth credential 값은 `.private/github-oauth.properties`에서만 읽고, 응답이나 최종 보고에 출력하지 않는다.

## Verification Expectations

Story 6.1 completion 전 최소 아래 검증을 수행한다.

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
