---
artifactType: story
storyId: "7.1"
storyKey: "7-1-real-github-oauth-smoke-seed-and-operator-runbook"
epic: "Epic 7. Real GitHub and Starter Smoke Verification"
title: "Real GitHub OAuth smoke seed and operator runbook"
architectureStyle: Traditional MVC
status: done
date: 2026-06-01
---

# Story 7.1 - Real GitHub OAuth smoke seed and operator runbook

## Status

done

## Story

local smoke operator는 실제 GitHub OAuth 로그인을 완료한 portal account를 local smoke project와 명시적으로 연결하고 싶다.

그래야 public project creation, project key lifecycle UI, browser token persistence를 열지 않고도 `.private/smoke-auth.env`의 service Bearer access token으로 `GET /api/projects`가 active membership project를 반환하는지 검증하고, Story 7.2/7.3의 starter ingest smoke가 같은 account/project 계약 위에서 이어질 수 있다.

## Source of Truth

아래 문서를 읽고 반영한 BMAD create-story context다. 구현 중 충돌처럼 보이는 지점은 Story 6.10 account-project membership boundary, `account-auth-policy.md`, `api-surface.md`, Epic 7 sprint plan, 현재 account/catalog code 순서로 우선한다.

1. `_bmad/custom/project-context.md`
2. `implementation-artifacts/sprint-status.yaml`
3. `planning-artifacts/epics.md`
4. `planning-artifacts/sprint-plan.md`
5. `planning-artifacts/contracts/account-auth-policy.md`
6. `planning-artifacts/api-surface.md`
7. `planning-artifacts/database-schema.md`
8. `planning-artifacts/architecture.md`
9. `planning-artifacts/architecture-implementation-supplement.md`
10. `planning-artifacts/project-structure.md`
11. `planning-artifacts/current-product-source-of-truth.md`
12. `planning-artifacts/stories/6-1-account-project-entry-and-setup-guide.md`
13. `planning-artifacts/stories/6-8-demo-green-path.md`
14. `planning-artifacts/stories/6-9-failure-recovery-path-demo-hardening.md`
15. `planning-artifacts/stories/6-10-account-project-membership-and-scoped-project-entry.md`
16. `implementation-artifacts/spec-story-6-1-account-project-entry-and-setup-guide-contract-decisions.md`
17. `implementation-artifacts/spec-story-6-10-account-project-membership-and-scoped-project-entry-contract-decisions.md`
18. `implementation-artifacts/epic-6-retro-2026-06-01.md`
19. `AGENTS.md`

확인한 현재 코드/스키마:

1. `.gitignore`
2. `build.gradle`
3. `observability-portal/build.gradle`
4. `observability-portal/src/main/resources/application.properties`
5. `observability-portal/src/main/resources/db/migration/V001__create_projects.sql`
6. `observability-portal/src/main/resources/db/migration/V008__create_accounts_and_refresh_tokens.sql`
7. `observability-portal/src/main/resources/db/migration/V011__create_account_project_memberships.sql`
8. `observability-portal/src/main/java/com/observation/portal/domain/account/controller/AccountAuthController.java`
9. `observability-portal/src/main/java/com/observation/portal/domain/account/controller/BearerResourceApiInterceptor.java`
10. `observability-portal/src/main/java/com/observation/portal/domain/account/controller/AccountProjectMembershipResourceApiInterceptor.java`
11. `observability-portal/src/main/java/com/observation/portal/domain/account/service/AccountAuthService.java`
12. `observability-portal/src/main/java/com/observation/portal/domain/account/service/HttpGithubOAuthClient.java`
13. `observability-portal/src/main/java/com/observation/portal/domain/account/service/GithubOAuthAppProperties.java`
14. `observability-portal/src/main/java/com/observation/portal/domain/account/service/OAuthStateSigner.java`
15. `observability-portal/src/main/java/com/observation/portal/domain/account/service/ServiceTokenIssuer.java`
16. `observability-portal/src/main/java/com/observation/portal/domain/account/service/AccountProjectMembershipService.java`
17. `observability-portal/src/main/java/com/observation/portal/domain/account/repository/ExternalIdentityJpaRepository.java`
18. `observability-portal/src/main/java/com/observation/portal/domain/account/repository/AccountProjectMembershipRepository.java`
19. `observability-portal/src/main/java/com/observation/portal/domain/catalog/entity/ProjectEntity.java`
20. `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ProjectRepository.java`
21. `observability-portal/src/main/java/com/observation/portal/domain/catalog/controller/ProjectNavigationController.java`
22. `observability-portal/src/main/java/com/observation/portal/domain/catalog/service/ProjectApplicationNavigationService.java`
23. `observability-portal/src/main/java/com/observation/portal/security/ProjectKeyHashVerifier.java`
24. `observability-portal/src/test/java/com/observation/portal/domain/account/repository/AccountProjectMembershipRepositoryIntegrationTest.java`
25. `observability-portal/src/test/java/com/observation/portal/domain/catalog/controller/ProjectNavigationResourceAuthorizationTest.java`

외부 최신 참고:

1. GitHub Docs - Authorizing OAuth apps: https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps
2. Spring Boot Externalized Configuration: https://docs.spring.io/spring-boot/reference/features/external-config.html
3. Spring Security advisory CVE-2025-22228: https://spring.io/security/cve-2025-22228/

## Scope / Out of Scope

포함:

- 실제 GitHub OAuth App과 local portal 실행 절차를 다루는 operator runbook
- `.private/github-oauth.properties` 기반 local GitHub OAuth App 설정 안내
- 실제 브라우저에서 GitHub OAuth authorize/callback을 수동 완료하는 절차
- callback JSON의 service `accessToken`만 `.private/smoke-auth.env`에 ephemeral memo하는 helper 또는 명확한 runbook 절차
- `.private/smoke-auth.env`는 `OBSERVATION_SMOKE_ACCESS_TOKEN`만 저장하고 가능하면 owner-only 권한으로 만든다
- local-only smoke seed command 또는 runner로 기존 `external_identities` row에서 account를 찾아 smoke project와 active `account_project_memberships` row를 연결
- smoke project의 `projects.project_key_hash`는 operator가 제공한 raw project key를 BCrypt hash로 저장
- raw smoke project key는 `.private/smoke-project.env` 또는 operator shell env에만 두고 DB row, migration, log, exception, response body, repository lookup surface에 남기지 않음
- `GET /api/projects`가 `.private/smoke-auth.env`의 Bearer access token으로 smoke project를 반환하는 verification 절차
- membership mismatch, missing token, expired token의 기존 `401`/`404` fail-closed behavior 보존
- Story 7.2/7.3이 사용할 smoke auth/project key material의 local-only handoff 문서화
- Traditional MVC + Service/Repository Layering, feature-first package 구조 유지
- 새 public class/method/helper 또는 복잡한 script에는 AGENTS.md 기준에 따라 한국어 설명 주석 작성

제외:

- public project creation API, public `POST /api/projects`
- login 직후 자동 project 생성
- Project key issuance/rotation/reissue UI 또는 product workflow
- invite/team/org/admin/billing/tenant model
- GitHub 외 provider, email/password, local password, magic link, anonymous user flow
- GitHub provider token을 resource API authorization에 사용하는 흐름
- GitHub provider token, provider raw payload, OAuth credential 저장
- refresh token을 `.private/smoke-auth.env`나 다른 memo file에 저장
- access token을 browser `localStorage`, `sessionStorage`, cookie, URL fragment/query에 저장하거나 파싱하는 구현
- cookie 기반 server session
- Story 7.2의 starter bucket ingest HTTP client 구현
- Story 7.3의 smoke Spring Boot service, traffic endpoint, bucket closure wait, Dashboard/Instance Evidence end-to-end verification
- React/Vite/TypeScript/frontend build stack 또는 static dashboard token persistence 변경
- production migration이 모든 account에 모든 project를 grant하는 convenience seed
- raw project key를 repository 문서, test fixture, log assertion, error body에 남기는 구현

## Acceptance Criteria

1. Story 7.1 story file은 `ready-for-dev` 상태로 생성되고 `sprint-status.yaml`의 `7-1-real-github-oauth-smoke-seed-and-operator-runbook`도 `ready-for-dev`로 갱신된다.
2. Epic 7 첫 story이므로 `sprint-status.yaml`의 `epic-7`은 `in-progress`로 전환된다.
3. 구현 story 실행 시 production 변경은 Traditional MVC + Service/Repository Layering과 feature-first package 구조를 따른다.
4. Operator runbook은 local GitHub OAuth App 생성/설정, portal 실행, 로그인, token memo, smoke seed, `GET /api/projects` 검증 순서를 포함한다.
5. Runbook은 실제 credential, access token, refresh token, provider token, raw project key 값을 문서에 적지 않는다.
6. Runbook의 예시는 placeholder만 사용하고, copy/paste 가능한 명령은 secret 값을 echo하지 않는다.
7. `.private/`가 Git 추적 대상이 아님을 확인하고, 새 secret 파일도 `.private/` 아래에만 두도록 안내한다.
8. `.private/github-oauth.properties`는 `portal.auth.github.client-id`, `portal.auth.github.client-secret`, `portal.auth.github.redirect-uri`, `portal.auth.github.homepage-url` 설정 key를 사용한다.
9. GitHub OAuth App callback URL은 local portal callback endpoint와 일치해야 한다.
10. GitHub OAuth start는 기존 `GET /api/auth/github/authorize` JSON boundary를 사용하고 email/password 또는 다른 provider start route를 만들지 않는다.
11. GitHub OAuth callback은 기존 `GET /api/auth/github/callback` JSON boundary를 사용한다.
12. Callback JSON에서 operator가 smoke 자동화에 memo할 수 있는 값은 service `accessToken`뿐이다.
13. Callback JSON의 `refreshToken`은 화면에서 보이더라도 `.private/smoke-auth.env`, script output, runbook 예시, test fixture에 저장하지 않는다.
14. `.private/smoke-auth.env`는 `OBSERVATION_SMOKE_ACCESS_TOKEN=<service-access-token>` 형식만 허용한다.
15. Token memo helper가 추가되면 기존 파일 권한을 가능한 범위에서 owner-only로 만들고, token 값을 stdout/stderr에 출력하지 않는다.
16. Token memo helper는 `OBSERVATION_SMOKE_REFRESH_TOKEN`, GitHub provider token, provider raw payload, OAuth credential key를 쓰거나 보존하지 않는다.
17. Service access token 만료 시 runbook은 refresh token memo가 아니라 GitHub OAuth 재로그인 또는 operator 수동 재발급 절차로 안내한다.
18. Resource API 인증은 계속 `Authorization: Bearer <access_token>` header만 사용한다.
19. Browser token persistence, URL token parsing, cookie server session은 Story 7.1에서 도입하지 않는다.
20. Local-only smoke seed는 public HTTP project creation endpoint가 아니다.
21. Local-only smoke seed는 login으로 생성된 `external_identities` row에서 account를 찾은 뒤에만 project membership을 연결한다.
22. Seed account selector는 `provider='github'`와 `provider_subject`를 우선한다.
23. `display_name` 또는 GitHub login 기반 selector를 지원한다면 정확히 1개 identity와 매칭될 때만 허용하고, 0개 또는 2개 이상이면 row를 쓰지 않고 실패한다.
24. Seed는 account가 `active` 상태인 경우에만 membership을 만들거나 갱신한다.
25. Seed는 project name, raw project key, optional stable project id를 local config 또는 env에서 받는다.
26. Raw project key는 `<key_prefix>.<secret>` shape를 따라야 하고 `key_prefix`는 `projects.key_prefix` 길이 제약을 지킨다.
27. Raw project key는 BCrypt 72 byte 입력 한계를 넘으면 seed가 실패해야 한다.
28. Seed는 raw project key를 `projects.project_key_hash`에 BCrypt hash로만 저장한다.
29. Seed는 raw project key를 DB row, migration, log, exception, response body, repository lookup surface에 남기지 않는다.
30. Seed는 `projects.key_prefix`에는 prefix만 저장하고 raw secret suffix를 저장하지 않는다.
31. Seed는 smoke project가 없으면 active project row를 만들 수 있다.
32. Seed는 같은 project name 또는 key prefix가 이미 있으면 기존 project를 재사용하거나 명확히 fail-closed 하되 중복 row를 만들지 않는다.
33. Seed는 account-project membership row가 없으면 `role='member'`, `status='active'`로 만든다.
34. Seed는 같은 account-project membership이 이미 active이면 idempotent success로 처리한다.
35. Seed는 disabled membership을 operator가 명시적으로 허용하지 않는 한 active로 몰래 승격하지 않는다.
36. Seed는 production migration에서 모든 account에 모든 project를 grant하지 않는다.
37. Seed는 GitHub provider token, refresh token, service access token을 DB에 저장하지 않는다.
38. Seed command는 `local-smoke` 같은 명시적인 local profile 또는 `portal.smoke.seed.enabled=true`와 동등한 local-only guard 없이는 실행되지 않는다.
39. Seed command가 production-like profile에서 켜지면 row를 쓰지 않고 실패해야 한다.
40. Seed command의 성공 출력은 project id/name, membership 연결 성공 여부, 다음 verification command만 포함하고 raw project key나 token을 포함하지 않는다.
41. Seed command의 실패 출력은 필요한 설정 key 이름과 일반화된 원인만 포함하고 secret 값을 포함하지 않는다.
42. `GET /api/projects` verification은 `.private/smoke-auth.env`의 access token을 Bearer header로 사용한다.
43. Verification 성공 기준은 response `projects[]`에 smoke project가 있고 `links.applications`가 `/api/projects/{projectId}/applications` shape를 갖는 것이다.
44. Verification은 project list를 hard-coded fixture, browser storage, previous response, URL, DOM dataset으로 재구성하지 않는다.
45. Membership 없는 account로 같은 project-scoped API를 호출하면 기존처럼 body 없는 `404` fail-closed가 유지된다.
46. No-token/invalid-token/expired-token request는 기존 Bearer boundary처럼 `401 Unauthorized`와 `WWW-Authenticate: Bearer`를 유지한다.
47. `GET /api/projects`가 empty list를 반환하는 상태는 seed 누락 또는 membership 누락으로 해석하고 public Create Project flow로 연결하지 않는다.
48. Runbook은 Story 7.2/7.3에서 raw smoke project key를 사용할 때 `.private/smoke-project.env` 또는 shell env를 쓰도록 안내한다.
49. `.private/smoke-project.env`가 사용되면 raw project key만 local secret으로 두고 repository에 커밋하지 않는다.
50. Runbook은 GitHub OAuth provider token과 portal service token을 구분해서 설명한다.
51. Runbook은 starter ingest `X-OBS-Project-Key`와 portal resource API Bearer token을 서로 다른 인증 경계로 설명한다.
52. Focused tests는 local-only seed가 account, project, active membership을 연결하고 disabled/missing/ambiguous identity를 fail-closed 처리하는지 검증한다.
53. Focused tests는 seed/test output과 runbook fixture에 refresh token, provider token, OAuth credential, raw project key가 노출되지 않는지 검증한다.
54. Focused tests는 `GET /api/projects`가 seeded account token으로 smoke project를 반환하고 unlinked account token으로는 반환하지 않는지 검증한다.
55. Existing `ProjectNavigationResourceAuthorizationTest` 또는 동등한 controller test는 token boundary와 membership fail-closed behavior를 계속 통과한다.
56. `MvcLayerBoundaryTest`는 새 admin/smoke seed code가 controller -> service -> repository 방향과 feature-first MVC boundary를 깨지 않는지 통과한다.
57. Suggested verification과 `git diff --check`가 통과해야 implementation completion으로 볼 수 있다.
58. 새 public class/method/helper/test 또는 동작을 바로 이해하기 어려운 내부 helper에는 AGENTS.md 지침에 따라 한국어 Javadoc/comment를 작성한다.

## Tasks / Subtasks

- [x] Operator runbook 작성 (AC: 4~19, 48~51, 57~58)
  - [x] `implementation-artifacts/real-github-oauth-smoke-runbook.md` 또는 동등한 implementation artifact를 추가한다.
  - [x] GitHub OAuth App local callback/homepage 설정과 `.private/github-oauth.properties` placeholder를 문서화한다.
  - [x] `GET /api/auth/github/authorize` response의 `authorizationUrl`을 브라우저에서 여는 절차를 문서화한다.
  - [x] callback JSON에서 `accessToken`만 `.private/smoke-auth.env`에 메모하고 `refreshToken`은 저장하지 않는 절차를 문서화한다.
  - [x] access token 만료 시 refresh token memo 대신 재로그인 절차로 갱신하도록 안내한다.
  - [x] service Bearer token, GitHub provider token, starter project key의 인증 경계를 분리해 설명한다.

- [x] Local-only smoke auth/project secret file helper 정리 (AC: 7, 12~18, 40~41, 48~49, 53, 57~58)
  - [x] 필요하면 `scripts/smoke/` 아래에 token memo 또는 verification helper를 추가한다.
  - [x] helper는 token/raw key 값을 stdout/stderr에 출력하지 않는다.
  - [x] `.private/smoke-auth.env`에는 `OBSERVATION_SMOKE_ACCESS_TOKEN`만 쓴다.
  - [x] `.private/smoke-project.env`를 사용한다면 `OBSERVATION_SMOKE_PROJECT_KEY` 같은 local-only raw project key만 두고 repository에 커밋하지 않는다.
  - [x] helper와 runbook 예시가 refresh token, provider token, OAuth credential을 저장하지 않도록 static guard를 둔다.

- [x] Local-only smoke seed command 구현 (AC: 20~41, 52~53, 56~58)
  - [x] `domain.admin` 또는 더 명확한 feature-first local smoke package 아래에 seed service/runner를 둔다.
  - [x] public HTTP project creation endpoint를 만들지 않는다.
  - [x] `local-smoke` profile 또는 `portal.smoke.seed.enabled=true` 계열 guard가 없으면 실행되지 않게 한다.
  - [x] production-like profile에서는 row를 쓰지 않고 실패한다.
  - [x] `external_identities.provider='github'`와 provider subject 우선 selector로 account를 찾는다.
  - [x] display name/login selector를 열면 정확히 하나의 identity와 매칭될 때만 허용한다.
  - [x] raw project key shape, prefix 길이, BCrypt 72 byte 입력 한계를 검증한다.
  - [x] raw project key를 BCrypt hash로만 저장하고 output/log/exception에는 싣지 않는다.
  - [x] project row와 account-project membership row를 idempotent하게 만들거나 재사용한다.
  - [x] disabled membership/project/account는 명시 정책 없이 성공으로 취급하지 않는다.

- [x] `GET /api/projects` smoke verification 정리 (AC: 42~47, 54~55, 57)
  - [x] `.private/smoke-auth.env`를 source한 뒤 Bearer header로 `GET /api/projects`를 호출하는 절차를 문서화한다.
  - [x] response에 smoke project와 `links.applications`가 있는지 확인한다.
  - [x] membership 없는 account 또는 wrong project resource API가 `404` fail-closed 되는 기존 regression을 유지한다.
  - [x] no-token/invalid/expired token `401` behavior를 유지한다.

- [x] Tests and regression guard (AC: 52~58)
  - [x] Smoke seed service focused test를 추가해 account selector, project upsert, membership idempotency를 검증한다.
  - [x] ambiguous/missing identity, disabled account/project/membership, invalid raw key shape, 72 byte 초과 key를 fail-closed로 검증한다.
  - [x] Secret exposure guard test 또는 static test로 runbook/helper/test output에 forbidden token/key material이 없는지 검증한다.
  - [x] Existing `ProjectNavigationResourceAuthorizationTest`, `AccountProjectMembershipRepositoryIntegrationTest`, `MvcLayerBoundaryTest` 영향 여부를 확인한다.
  - [x] Suggested commands와 `git diff --check`를 실행한다.

## Dev Notes

### Current Code State

- `.gitignore`는 이미 `.private/`를 무시한다.
- `application.properties`는 `optional:file:.private/github-oauth.properties`와 `optional:file:../.private/github-oauth.properties`를 import한다.
- `GithubOAuthAppProperties`는 `portal.auth.github.client-id`, `client-secret`, `redirect-uri`, `homepage-url`을 필수 key로 읽는다.
- `HttpGithubOAuthClient`는 GitHub authorization URL을 만들고 `code`를 provider access token으로 교환한 뒤 `/user` profile에서 GitHub user id를 provider subject로 사용한다.
- `HttpGithubOAuthClient`는 GitHub provider access token을 user id 조회 직후 폐기하고 저장소/response model에 싣지 않는 경계를 이미 갖고 있다.
- `AccountAuthController` callback response는 service `accessToken`과 `refreshToken`을 JSON body로 반환한다. Story 7.1 memo file에는 `accessToken`만 허용한다.
- `OAuthStateSigner`는 server session 없이 signed expiring state와 nonce hash를 만든다. Story 7.1은 이 boundary를 재사용하고 cookie session을 추가하지 않는다.
- `ServiceTokenIssuer`는 HS256 JWT access token과 SHA-256 hash 저장용 refresh token을 발급/검증한다.
- `BearerResourceApiInterceptor`는 `/api/projects`와 `/api/projects/**`에서 `Authorization: Bearer <access_token>`을 검증하고 account id request attribute를 남긴다.
- `AccountProjectMembershipService`는 access token 원문이 아니라 검증된 account id와 path project id로 active membership을 확인한다.
- `ProjectApplicationNavigationService.listProjects(accountId)`는 active account-project membership project만 반환한다.
- `ProjectKeyVerificationService`는 `X-OBS-Project-Key`를 `<key_prefix>.<secret>` shape로 파싱하고 raw key가 BCrypt 72 byte 입력 한계를 넘으면 unauthorized로 닫는다. Smoke seed도 같은 제약을 따라야 한다.
- `projects` table은 raw project key가 아니라 `key_prefix`와 BCrypt `project_key_hash`만 저장한다.
- `account_project_memberships` table은 `role='member'`, `status in ('active','disabled')`만 허용하고 active row만 project visibility/resource authorization에 쓰인다.
- 현재 `smoke-tests/starter-to-snapshot`에는 build output만 남아 있고 source file은 없다. Story 7.1은 smoke service 구현이 아니라 portal auth/project seed와 runbook을 먼저 닫는다.

### Proposed Implementation Boundary

권장 구현은 docs-only SQL runbook보다 local-only seed command다. 이유는 raw project key BCrypt hash 생성, identity ambiguity guard, idempotent membership 연결, secret redaction을 Java service/test로 검증할 수 있기 때문이다.

권장 code shape:

- `observability-portal/src/main/java/com/observation/portal/domain/admin/service/SmokeProjectSeedProperties.java`
- `observability-portal/src/main/java/com/observation/portal/domain/admin/service/SmokeProjectSeedService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/admin/service/SmokeProjectSeedRunner.java`
- `observability-portal/src/test/java/com/observation/portal/domain/admin/service/SmokeProjectSeedServiceTest.java`
- `implementation-artifacts/real-github-oauth-smoke-runbook.md`
- optional `scripts/smoke/write-smoke-auth-env.sh`
- optional `scripts/smoke/verify-smoke-projects.sh`

`domain.admin`은 architecture에서 local/internal bootstrap 후보로 이미 언급되어 있다. Public controller를 추가하지 말고 `ApplicationRunner`나 command-style service로 제한한다.

### Seed Property Guidance

권장 property names:

```properties
portal.smoke.seed.enabled=true
portal.smoke.seed.account-provider-subject=<github-user-id>
portal.smoke.seed.account-display-name=<github-login-only-if-unique>
portal.smoke.seed.project-name=local-smoke
portal.smoke.seed.raw-project-key=<key_prefix.secret>
```

`raw-project-key`는 local-only `.private/smoke-seed.properties`, `.private/smoke-project.env`, command-line argument, 또는 shell env에서만 온다. Story 7.1 구현자는 실제 값을 commit하지 않는다.

If `.private/smoke-seed.properties`를 지원하려면 `spring.config.import`에 optional import를 추가할 수 있다. Spring Boot official docs 기준 `optional:file:` import는 파일이 없어도 application start를 막지 않고, import된 config value는 import 선언 파일보다 높은 우선순위를 가진다.

### Operator Runbook Flow

권장 runbook 순서:

1. GitHub OAuth App을 만들고 callback URL을 local portal callback과 맞춘다.
2. `.private/github-oauth.properties`에 GitHub OAuth App credential과 service/oauth state signing key를 둔다.
3. local PostgreSQL과 portal을 `local-smoke` profile로 실행한다.
4. `GET /api/auth/github/authorize`를 호출해 `authorizationUrl`을 얻는다.
5. 브라우저에서 authorization URL을 열고 실제 GitHub OAuth를 완료한다.
6. callback JSON에서 `accessToken`만 `.private/smoke-auth.env`로 memo한다.
7. DB의 `external_identities`에서 provider subject 또는 unique display name을 확인한다.
8. local-only smoke seed command를 실행해 project, BCrypt project key hash, active account-project membership을 만든다.
9. `.private/smoke-auth.env`를 source하고 `GET /api/projects`를 Bearer header로 호출한다.
10. response에 smoke project와 applications link가 보이면 Story 7.2/7.3으로 넘긴다.

### Latest Technical Notes

- GitHub OAuth Apps official docs는 browser-based standard app에는 web application flow를 사용한다고 설명하고, implicit grant는 지원하지 않는다고 명시한다. 현재 `GET /api/auth/github/authorize` -> browser -> callback JSON flow는 이 전제와 맞는다.
- GitHub token exchange endpoint는 `client_id`, `client_secret`, `code`를 요구한다. 따라서 `.private/github-oauth.properties`에는 credential이 필요하지만 repository와 runbook output에는 실제 값을 남기면 안 된다.
- GitHub docs는 `state` parameter가 추측 불가능한 값이어야 하며 request forgery 방지에 쓰인다고 설명한다. 기존 `OAuthStateSigner`와 `oauth_state_nonces` hash 저장 경계를 재사용한다.
- Spring Boot external config docs는 `spring.config.import=optional:file:...`를 지원한다. local smoke properties를 추가하더라도 optional import로 두어 일반 app start를 깨지 않게 한다.
- Spring Security 2025 advisory는 BCrypt match가 72자 초과 입력에서 문제가 될 수 있음을 다룬다. 이 repo는 raw project key를 72 byte 이하로 제한하는 guard를 이미 갖고 있으므로 smoke seed에서도 hash 생성 전 같은 제약을 적용한다.

### Previous Story Intelligence

- Story 6.1은 GitHub OAuth only, service token JSON body delivery, no provider token storage, no cookie session boundary를 닫았다.
- Story 6.8은 green path fixture에서 account, project, active membership, accepted bucket, heartbeat axis를 연결하되 public project creation을 열지 않는 guard를 강화했다.
- Story 6.9는 failure/recovery demo copy와 token/secret exposure guard를 강화했다.
- Story 6.10은 `GET /api/projects`와 `/api/projects/{projectId}/applications/**`를 Bearer account의 active account-project membership 안으로 제한했다.
- 따라서 Story 7.1은 account/project authorization 모델을 다시 설계하지 않고, 실제 GitHub OAuth account를 local smoke project에 안전하게 연결하는 operator path만 닫아야 한다.

### Git Intelligence

최근 commit 흐름은 Epic 6 closure와 demo hardening에 집중되어 있다.

- `1e20db6 Close out Epic 6 retrospective`
- `18b0524 Merge story 6.9 failure recovery hardening`
- `7e3b1d5 Complete story 6.9 failure recovery hardening`
- `36a6e92 Merge story 6.8 demo green path`
- `8bb11c1 Complete story 6.8 demo green path`

Story 7.1 구현자는 Epic 6의 safe copy, no token persistence, membership fail-closed, no public project creation 패턴을 그대로 이어간다.

### Testing

Focused test 대상 후보:

- `SmokeProjectSeedServiceTest`
- `SmokeProjectSeedRunnerTest`
- `ProjectNavigationResourceAuthorizationTest`
- `AccountProjectMembershipRepositoryIntegrationTest`
- `AuthSecretExposureGuardTest` 또는 new smoke secret exposure guard
- `MvcLayerBoundaryTest`

필수 scenario:

- provider subject로 active GitHub identity를 찾아 smoke project와 active membership을 만든다.
- display name selector는 정확히 한 identity일 때만 허용한다.
- missing/ambiguous identity는 row write 없이 실패한다.
- disabled account/project/membership은 성공으로 취급하지 않는다.
- raw project key shape invalid, prefix too long, BCrypt 72 byte 초과는 실패한다.
- seed output, runbook, helper stdout/stderr에는 raw project key, access token, refresh token, GitHub provider token, OAuth credential이 없다.
- seeded account access token으로 `GET /api/projects`가 smoke project를 반환한다.
- unlinked account access token은 smoke project를 보지 못하고 project-scoped resource API는 `404`로 fail-closed 된다.

Suggested commands:

```bash
./gradlew :observability-portal:test --tests '*SmokeProjectSeed*'
./gradlew :observability-portal:test --tests '*ProjectNavigationResourceAuthorization*'
./gradlew :observability-portal:test --tests '*AccountProjectMembership*'
./gradlew :observability-portal:test --tests '*AuthSecretExposure*'
./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest
./gradlew :observability-portal:test
git diff --check
```

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- 2026-06-01: BMAD create-story workflow, project context, sprint status, Epic 7 source artifacts를 확인했다.
- 2026-06-01: Story 7.1 target key를 `7-1-real-github-oauth-smoke-seed-and-operator-runbook`으로 확정했다.
- 2026-06-01: Story 6.10 membership boundary와 current account/catalog code state를 확인했다.
- 2026-06-01: GitHub OAuth App, Spring Boot optional config import, BCrypt 72 byte guard 관련 공식 문서를 확인했다.
- 2026-06-01: RED 단계에서 `SmokeProjectSeedServiceTest`와 smoke secret exposure guard를 추가하고 실패를 확인했다.
- 2026-06-01: GREEN 단계에서 local-only seed service/runner, runbook, smoke helper scripts를 구현했다.
- 2026-06-01: `*SmokeProjectSeed*`, `*ProjectNavigationResourceAuthorization*`, `*AccountProjectMembership*`, `*AuthSecretExposure*`, `MvcLayerBoundaryTest`, full portal test, `git diff --check`를 실행했다.

### Completion Notes List

- `implementation-artifacts/real-github-oauth-smoke-runbook.md`에 실제 GitHub OAuth App 설정, authorize/callback, accessToken-only memo, local-only seed, `GET /api/projects` verification, Story 7.2/7.3 handoff를 정리했다.
- `scripts/smoke/write-smoke-auth-env.sh`와 `scripts/smoke/verify-smoke-projects.sh`를 추가해 token 값을 출력하지 않고 `.private/smoke-auth.env` access token memo와 project list 검증을 수행하게 했다.
- `domain.admin.service` 아래에 `SmokeProjectSeedService`/`SmokeProjectSeedRunner`를 추가해 `portal.smoke.seed.enabled=true`와 `local-smoke` profile guard, provider subject 우선 selector, display name exact-one fallback, raw key shape/BCrypt 72 byte guard, BCrypt hash-only project 저장, active membership idempotency, disabled account/project/membership fail-closed를 구현했다.
- Secret exposure guard와 focused tests가 runbook/helper forbidden snippet, result redaction, local-only guard, disabled/ambiguous/invalid input fail-closed, existing active membership idempotency를 검증한다.
- Existing resource authorization tests가 seeded account project visibility, unlinked account empty project list, project-scoped `404` fail-closed, no/invalid/expired token `401` boundary를 계속 검증한다.

### File List

- `implementation-artifacts/real-github-oauth-smoke-runbook.md`
- `implementation-artifacts/sprint-status.yaml`
- `observability-portal/src/main/resources/application.properties`
- `observability-portal/src/main/java/com/observation/portal/domain/admin/service/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/admin/service/SmokeProjectSeedException.java`
- `observability-portal/src/main/java/com/observation/portal/domain/admin/service/SmokeProjectSeedProperties.java`
- `observability-portal/src/main/java/com/observation/portal/domain/admin/service/SmokeProjectSeedResult.java`
- `observability-portal/src/main/java/com/observation/portal/domain/admin/service/SmokeProjectSeedRunner.java`
- `observability-portal/src/main/java/com/observation/portal/domain/admin/service/SmokeProjectSeedService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/repository/AccountEntity.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/repository/ExternalIdentityEntity.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/repository/ExternalIdentityJpaRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/repository/AccountProjectMembershipEntity.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/repository/AccountProjectMembershipRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ProjectRepository.java`
- `observability-portal/src/test/java/com/observation/portal/domain/account/controller/AuthSecretExposureGuardTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/admin/service/SmokeProjectSeedServiceTest.java`
- `planning-artifacts/stories/7-1-real-github-oauth-smoke-seed-and-operator-runbook.md`
- `scripts/smoke/write-smoke-auth-env.sh`
- `scripts/smoke/verify-smoke-projects.sh`

### Change Log

- 2026-06-01: Story 7.1 create-story context를 생성하고 ready-for-dev 상태로 전환했다.
- 2026-06-01: Story 7.1 local GitHub OAuth smoke seed, operator runbook, secret helper, verification guard를 구현하고 review 상태로 전환했다.
