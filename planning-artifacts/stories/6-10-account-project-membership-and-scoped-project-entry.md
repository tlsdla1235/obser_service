---
artifactType: story
storyId: "6.10"
storyKey: "6-10-account-project-membership-and-scoped-project-entry"
epic: "Epic 6. Dashboard User Flow and Demo Hardening"
title: "Account-project membership and scoped project entry"
architectureStyle: Traditional MVC
status: review
date: 2026-05-31
---

# Story 6.10 - Account-project membership and scoped project entry

## Status

review

## Story

인증된 portal 사용자는 자신에게 연결된 Project만 Project Entry와 후속 Project-scoped resource API에서 보고 싶다.

그래야 `GET /api/projects`가 전체 project catalog를 노출하지 않고, Project Selection -> Application List -> Dashboard -> Instance Evidence -> Trend/History 흐름이 service Bearer token의 account-project authorization 경계 안에서 fail-closed로 동작한다.

## Source of Truth

아래 문서를 읽고 반영한 BMAD create-story context다. 구현 중 충돌처럼 보이는 지점은 1번 Story 6.10 contract decision, `account-auth-policy.md`, 현재 Bearer resource API boundary, 그리고 기존 Epic 5 catalog path 정합성 코드를 우선한다.

1. `implementation-artifacts/spec-story-6-10-account-project-membership-and-scoped-project-entry-contract-decisions.md`
2. `planning-artifacts/epics.md`
3. `planning-artifacts/sprint-plan.md`
4. `implementation-artifacts/epic-6-context.md`
5. `implementation-artifacts/sprint-status.yaml`
6. `planning-artifacts/stories/6-1-account-project-entry-and-setup-guide.md`
7. `planning-artifacts/stories/6-2-project-selection-ui.md`
8. `implementation-artifacts/spec-story-6-1-account-project-entry-and-setup-guide-contract-decisions.md`
9. `implementation-artifacts/spec-story-6-2-project-selection-ui-contract-decisions.md`
10. `planning-artifacts/contracts/account-auth-policy.md`
11. `planning-artifacts/api-surface.md`
12. `planning-artifacts/database-schema.md`
13. `AGENTS.md`
14. `_bmad/custom/project-context.md`

확인한 현재 코드/스키마:

1. `observability-portal/src/main/resources/db/migration/V001__create_projects.sql`
2. `observability-portal/src/main/resources/db/migration/V008__create_accounts_and_refresh_tokens.sql`
3. `observability-portal/src/main/java/com/observation/portal/domain/account/controller/BearerResourceApiInterceptor.java`
4. `observability-portal/src/main/java/com/observation/portal/domain/account/controller/BearerResourceApiWebConfig.java`
5. `observability-portal/src/main/java/com/observation/portal/domain/account/service/ServiceTokenIssuer.java`
6. `observability-portal/src/main/java/com/observation/portal/domain/catalog/controller/ProjectNavigationController.java`
7. `observability-portal/src/main/java/com/observation/portal/domain/catalog/service/ProjectApplicationNavigationService.java`
8. `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ProjectRepository.java`
9. `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ApplicationRepository.java`
10. `observability-portal/src/test/java/com/observation/portal/domain/catalog/controller/ProjectNavigationResourceAuthorizationTest.java`
11. `observability-portal/src/test/java/com/observation/portal/domain/catalog/controller/ProjectNavigationControllerTest.java`
12. `observability-portal/src/test/java/com/observation/portal/domain/catalog/service/ProjectApplicationNavigationServiceTest.java`
13. `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ProjectSelectionUiContractTest.java`

## Scope / Out of Scope

포함:

- `account_project_memberships` N:M membership table 도입
- 한 account가 여러 project에 속하고, 한 project도 여러 account에 노출될 수 있는 authorization model
- MVP role/status model: `role=member` 단일 role, `status=active|disabled`
- active membership만 Project visibility와 project-scoped resource authorization에 사용
- `GET /api/projects`를 현재 access token의 account membership project로 제한
- membership 없는 project가 Project list에 나타나지 않는 regression guard
- `/api/projects/{projectId}/applications` 및 `/api/projects/{projectId}/applications/**` resource API membership guard
- membership mismatch를 정보 노출 감소를 위해 `404 Not Found`로 fail-closed 처리
- no-token/invalid-token은 기존 Bearer boundary처럼 `401 Unauthorized`
- Project Entry empty state는 server response가 빈 project list일 때 safe empty state만 렌더링
- local/demo seed 또는 test fixture가 명시적으로 account-project membership을 연결하는 safe migration/fixture 기준
- account-project authorization과 Epic 5 project/application/instance catalog path 정합성의 용어/책임 분리
- backend regression test: scoped project list, membership mismatch, no-token/invalid-token, cross-account access
- schema comment, Javadoc, public method/helper comment는 AGENTS.md 기준에 따라 한국어 작성

제외:

- 코드 구현 자체. 이 story/spec 작성 작업에서는 production/test code를 수정하지 않는다.
- public project creation API, public `POST /api/projects`, 로그인 직후 자동 project 생성
- Project key 발급, 회전, 재발급, 노출 workflow
- invite flow, team/org management, owner/admin/viewer UI
- billing/tenant model
- GitHub provider token 저장 또는 resource API 인증에 GitHub token 사용
- refresh token browser storage, browser token persistence, URL fragment/query token parsing
- Project list를 UI가 재구성하거나 authorization을 추론하는 동작
- Epic 5 read model의 lifecycle state, starter connection diagnosis, p95/p99, endpoint priority, event/marker 계산 변경
- `src/main/frontend`, React, Vite, TypeScript, 별도 frontend build/deploy, view resolver/template engine 도입

## Acceptance Criteria

1. Story 6.10 story file과 contract decision file은 `ready-for-dev` 상태로 생성된다.
2. 구현 story 실행 시 production 변경은 Traditional MVC + Service/Repository Layering과 feature-first package 구조를 따른다.
3. `account_project_memberships` table은 account-project N:M authorization relation의 source of truth다.
4. 한 account는 여러 project membership을 가질 수 있고, 한 project는 여러 account membership을 가질 수 있다.
5. Membership MVP role은 단일 `member`로 시작한다.
6. Membership status는 `active`, `disabled`만 허용하고, authorization에는 `active` membership만 사용한다.
7. Owner/admin/viewer role, invite/admin/team UI, billing/tenant model은 Story 6.10에서 만들지 않는다.
8. Membership schema는 `accounts.id`와 `projects.id`를 foreign key로 참조한다.
9. Membership schema는 같은 account-project 조합이 중복되지 않도록 unique constraint를 둔다.
10. Membership schema는 account 기준 project list 조회와 project 기준 membership check에 필요한 index를 둔다.
11. Membership migration에는 table/column comment를 한국어로 작성한다.
12. Raw project key, access token, refresh token, GitHub OAuth token, provider raw payload, secret은 membership table, migration, log, exception, response body에 저장하거나 노출하지 않는다.
13. `GET /api/projects`는 `Authorization: Bearer <access_token>`으로 검증된 account id 기준 active membership project만 반환한다.
14. Membership이 없는 project는 `GET /api/projects` response에 나타나지 않는다.
15. Disabled membership 또는 disabled project는 Project list에 나타나지 않는다.
16. Membership이 하나도 없는 authenticated account는 `200 OK`와 `projects=[]`를 받는다.
17. Membership이 하나도 없는 Project Entry UI는 safe empty state를 보여주되 public Create Project flow를 열지 않는다.
18. UI는 project list를 local cache, hard-coded fixture, previous response, URL, DOM dataset으로 재구성하지 않고 server response만 렌더링한다.
19. UI는 authorization을 추론하거나 hidden project를 요청해 찾아보는 client-side probing을 만들지 않는다.
20. Bearer token은 여전히 `Authorization: Bearer <access_token>` header만 사용한다.
21. Resource API 인증에는 GitHub provider token을 사용하지 않는다.
22. Refresh token, browser token persistence, URL fragment/query token parsing, cookie server session은 Story 6.10에서 도입하지 않는다.
23. No-token/invalid-token/expired-token request는 기존 Bearer boundary처럼 `401 Unauthorized`와 `WWW-Authenticate: Bearer`로 수렴한다.
24. Authenticated account가 membership 없는 `/api/projects/{projectId}/applications`를 호출하면 `404 Not Found`로 fail-closed 된다.
25. Authenticated account가 membership 없는 `/api/projects/{projectId}/applications/**` resource API를 호출하면 `404 Not Found`로 fail-closed 된다.
26. Membership mismatch는 `403 Forbidden`이 아니라 `404 Not Found`로 처리한다.
27. Membership mismatch response는 project 존재 여부, project name, application count, application id, snapshot/history 존재 여부를 드러내지 않는다.
28. Membership mismatch가 있는 project-scoped API에서는 application/project catalog lookup 결과가 response로 새지 않는다.
29. Membership이 있는 account가 기존 project/application/instance path mismatch를 호출하면 기존 catalog path 정합성 기준처럼 `404 Not Found`를 유지한다.
30. Existing Epic 5 project/application/instance membership 표현은 "catalog path 정합성"으로 정리하고, Story 6.10 membership은 "account-project authorization"으로 분리한다.
31. Controller는 repository를 직접 호출하지 않고 service 또는 dedicated authorization component에 위임한다.
32. Repository/JPA entity는 controller/dto에 의존하지 않는다.
33. JPA entity는 controller response DTO, public API surface, service external result로 직접 반환하지 않는다.
34. `BearerResourceApiInterceptor`는 service access token 검증과 account id request attribute 경계를 유지한다.
35. Project membership guard는 검증된 account id와 path `projectId`를 사용해야 하며 access token 원문을 재검증하거나 전달하지 않는다.
36. `GET /api/projects` collection endpoint는 path `projectId`가 없으므로 service method가 account id를 입력으로 받아 scoped list를 만든다.
37. `/api/projects/{projectId}/applications/**` guard는 path에서 project id를 찾을 수 있는 resource API에 공통 적용하거나 각 service entry에서 일관되게 적용한다.
38. Membership guard는 application/dashboard/instance/history/snapshot service가 기존 catalog path 정합성을 검증하기 전에 account-project authorization을 통과시켜야 한다.
39. Query validation `400`은 membership이 확인된 scope에서만 반환한다. Membership이 없으면 fail-closed `404`를 우선한다.
40. Project creation 방식은 여전히 public onboarding API로 열지 않는다.
41. Project key issuance/rotation/reissue workflow는 여전히 열지 않는다.
42. Invite flow, team/org management, owner/admin role, billing/tenant model은 follow-up decision으로 남긴다.
43. Existing local/demo project 또는 bootstrap project는 production migration에서 모든 account에 자동 grant하지 않는다.
44. Local/dev/test fixture는 명시적인 account id와 project id로 membership row를 만들어야 한다.
45. 기존 project가 있지만 membership row가 없으면 authenticated Project Entry는 빈 project list를 보여준다.
46. Demo convenience가 필요하면 local/internal bootstrap profile이나 test fixture에서만 membership을 연결하고 public API로 만들지 않는다.
47. `planning-artifacts/database-schema.md`, `planning-artifacts/api-surface.md`, `planning-artifacts/contracts/account-auth-policy.md`를 구현 PR에서 account-project membership boundary에 맞게 갱신한다.
48. Backend regression test는 scoped project list를 검증한다.
49. Backend regression test는 membership 없는 project가 project list에 나타나지 않음을 검증한다.
50. Backend regression test는 membership 없는 account가 `/api/projects/{projectId}/applications`를 호출하면 `404`를 받음을 검증한다.
51. Backend regression test는 membership 없는 account가 representative nested resource API를 호출하면 `404`를 받음을 검증한다.
52. Backend regression test는 no-token/invalid-token/expired-token request가 `401`을 반환하고 service/repository를 호출하지 않음을 검증한다.
53. Backend regression test는 account A의 project를 account B token으로 접근하는 cross-account access가 list와 project-scoped resource API 모두에서 fail-closed 됨을 검증한다.
54. Backend regression test는 membership이 있는 account가 기존 project/application/instance catalog mismatch를 호출할 때 `404`를 유지함을 검증한다.
55. Static UI contract test는 Project Entry가 server response `projects=[]`를 safe empty state로 렌더링하고 Create Project flow를 열지 않음을 유지한다.
56. 새 public class/method 또는 동작을 바로 이해하기 어려운 helper에는 AGENTS.md 지침에 따라 한국어 Javadoc/comment를 작성한다.
57. Suggested verification과 `git diff --check`가 통과해야 implementation completion으로 볼 수 있다.

## Tasks / Subtasks

- [x] Account-project membership schema 추가 (AC: 3~12, 43~47, 56)
  - [x] 최신 Flyway migration 번호 다음에 `account_project_memberships` 생성 migration을 추가한다.
  - [x] `id`, `account_id`, `project_id`, `role`, `status`, `created_at`, `updated_at` column을 둔다.
  - [x] `accounts(id)`와 `projects(id)` foreign key, `(account_id, project_id)` unique constraint를 추가한다.
  - [x] `role in ('member')`, `status in ('active', 'disabled')` check constraint를 추가한다.
  - [x] account 기준 scoped project list와 project 기준 membership check index를 추가한다.
  - [x] table/column comment를 한국어로 작성한다.
  - [x] production migration에서 모든 account에 모든 project를 자동 grant하지 않는다.
  - [x] local/dev/test fixture는 명시적으로 membership row를 연결한다.

- [x] Membership persistence/model/service boundary 구현 (AC: 2, 6, 31~38, 56)
  - [x] `domain.account` 또는 authorization 책임이 더 명확한 feature-first package 안에 membership entity/repository/service를 둔다.
  - [x] Service-facing model은 JPA entity를 직접 반환하지 않는다.
  - [x] active membership check API는 `accountId + projectId`를 입력으로 받고 token 원문을 받지 않는다.
  - [x] scoped project list API는 `accountId`를 입력으로 받아 active membership project만 조회한다.
  - [x] 기존 code comment에서 "membership"이 catalog path 정합성을 뜻하는 곳은 가능하면 "catalog path 정합성" 또는 "path scope consistency"로 정리한다.

- [x] `GET /api/projects` scoped list로 변경 (AC: 13~19, 36, 48~49, 55)
  - [x] `ProjectNavigationController.listProjects()`가 Bearer interceptor가 남긴 account id를 읽어 service에 전달한다.
  - [x] `ProjectApplicationNavigationService.listProjects(accountId)` 또는 동등 service method가 membership project만 반환한다.
  - [x] `projectRepository.findAll(...)`로 전체 project list를 반환하는 경로를 제거한다.
  - [x] membership 없는 account는 `projects=[]`를 반환한다.
  - [x] UI는 server response만 렌더링하고 safe empty state를 유지한다.

- [x] Project-scoped resource API membership guard 추가 (AC: 20~30, 34~39, 50~54)
  - [x] `/api/projects/{projectId}/applications` 호출 전에 account-project active membership을 확인한다.
  - [x] `/api/projects/{projectId}/applications/**` 하위 resource API에도 같은 guard를 적용한다.
  - [x] membership mismatch는 `404 Not Found`로 처리하고 `403 Forbidden`을 사용하지 않는다.
  - [x] no-token/invalid-token/expired-token은 기존 `401` 경계를 유지한다.
  - [x] membership guard 통과 후 기존 catalog path 정합성 검증을 수행한다.
  - [x] error body/copy가 project 존재 여부나 이름, application/snapshot/history 존재 여부를 드러내지 않게 한다.

- [x] Contract/docs update (AC: 30, 40~47)
  - [x] `planning-artifacts/database-schema.md`에 account-project membership table과 role/status 결정을 반영한다.
  - [x] `planning-artifacts/api-surface.md`에 scoped `GET /api/projects`와 project-scoped resource API fail-closed 기준을 반영한다.
  - [x] `planning-artifacts/contracts/account-auth-policy.md`에 resource API authorization은 Bearer account + active project membership 기준임을 추가한다.
  - [x] Project creation, key issuance/rotation, invite/team/org/billing은 여전히 out-of-scope로 남긴다.

- [x] Backend regression tests 작성/갱신 (AC: 48~57)
  - [x] Flyway/JPA schema alignment 또는 repository integration test에 membership table/comment/constraint/index를 포함한다.
  - [x] `ProjectNavigationResourceAuthorizationTest`를 확장해 scoped project list, no-token/invalid-token, membership mismatch를 검증한다.
  - [x] `ProjectApplicationNavigationServiceTest` 또는 repository test에서 account별 project filtering을 검증한다.
  - [x] Dashboard, Instance Evidence, Instance Snapshot Trend, Snapshot/History 중 representative nested resource API의 cross-account `404`를 검증한다.
  - [x] Static UI contract test가 empty project list safe state와 no Create Project flow를 계속 검증하게 유지한다.

## Dev Notes

### Current Code State

- 현재 `BearerResourceApiWebConfig`는 `/api/projects`, `/api/projects/**`에 `BearerResourceApiInterceptor`를 적용한다.
- `BearerResourceApiInterceptor`는 `Authorization: Bearer <access_token>`을 검증하고 request attribute `observation.portal.accountId`에 account id를 남긴다.
- 현재 `GET /api/projects`는 token 검증 후에도 `ProjectApplicationNavigationService.listProjects()`가 `projectRepository.findAll(PROJECT_SORT)`를 호출해 전체 project list를 반환한다.
- 현재 `GET /api/projects/{projectId}/applications`는 project 존재 여부와 application list만 보고, account-project membership을 보지 않는다.
- 현재 Dashboard/Instance/History 계열 service는 `ApplicationRepository.findByIdAndProjectId(...)`로 project/application path 정합성을 확인하고 mismatch를 `404`로 매핑한다.
- 일부 controller/service comment는 "membership"을 project/application/instance path 정합성 의미로 사용한다. Story 6.10 구현에서는 이 용어를 account-project authorization membership과 혼동하지 않도록 정리한다.
- `V001__create_projects.sql`은 `projects`를 project key/ingest boundary table로 만들며 account relation이 없다.
- `V008__create_accounts_and_refresh_tokens.sql`은 `accounts`, `external_identities`, `refresh_token_families`, `refresh_tokens`를 만들며 project relation이 없다.
- 현재 worktree에는 `V010__standardize_account_schema_comments.sql`가 미추적 파일로 존재한다. 구현 시 실제 최신 migration 번호 다음 번호를 사용하고 기존 사용자 변경을 되돌리지 않는다.

### Closed Decisions

- Account-project authorization은 N:M membership table로 닫는다.
- Table 이름은 `account_project_memberships`로 둔다.
- MVP role은 `member` 단일 role이다.
- MVP membership status는 `active`, `disabled`다.
- `active` membership만 project visibility와 project-scoped resource access를 허용한다.
- Membership mismatch는 `403`이 아니라 `404`로 fail-closed한다.
- No-token/invalid-token은 `401 Unauthorized`로 유지한다.
- GitHub provider token은 resource API authorization에 사용하지 않는다.
- Project creation, project key issuance/rotation, invite/team/org/admin UI, billing/tenant model은 이번 story 범위 밖이다.

### Schema Guidance

권장 DDL shape:

```sql
create table account_project_memberships (
  id uuid not null,
  account_id uuid not null,
  project_id uuid not null,
  role varchar(32) not null,
  status varchar(32) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint pk_account_project_memberships primary key (id),
  constraint fk_account_project_memberships_account_id
    foreign key (account_id) references accounts (id),
  constraint fk_account_project_memberships_project_id
    foreign key (project_id) references projects (id),
  constraint uk_account_project_memberships_account_project
    unique (account_id, project_id),
  constraint ck_account_project_memberships_role
    check (role in ('member')),
  constraint ck_account_project_memberships_status
    check (status in ('active', 'disabled'))
);
```

권장 index:

- `idx_account_project_memberships_account_status_project (account_id, status, project_id)`
- `idx_account_project_memberships_project_status_account (project_id, status, account_id)`

Production migration은 기존 project를 모든 account에 자동 연결하지 않는다. local/demo/test 데이터는 명시적 fixture나 internal bootstrap profile에서만 account-project membership을 연결한다.

### Authorization Guardrails

- Collection endpoint `GET /api/projects`는 path `projectId`가 없으므로 controller/service가 request attribute의 account id를 service input으로 전달해야 한다.
- Project-scoped endpoint는 path `projectId`와 account id의 active membership을 먼저 확인한 뒤 기존 application/instance/snapshot catalog path 정합성 검증을 수행한다.
- Membership mismatch와 missing project는 모두 `404`로 수렴한다.
- Membership이 통과된 뒤 application id가 다른 project에 속하거나 instance id가 다른 application에 속하면 기존처럼 `404`를 유지한다.
- Query validation error는 membership이 확인된 scope에서만 `400`을 반환한다.
- Authorization helper는 access token 원문, refresh token 원문, GitHub token을 받거나 저장하지 않는다.

### UI Guardrails

- Project Entry UI는 `GET /api/projects` response만 렌더링한다.
- `projects=[]`이면 safe empty state를 보여준다.
- Empty state는 local/internal seed 또는 admin bootstrap이 필요하다는 방향으로만 표현하고 public Create Project CTA를 만들지 않는다.
- UI는 local cache, hard-coded project id, previous response, URL, DOM dataset으로 hidden project를 추론하지 않는다.
- Browser token persistence, URL token parsing, refresh token browser storage는 계속 out-of-scope다.

### Previous Story Intelligence

- Story 6.1은 GitHub OAuth only, service token JSON body delivery, Bearer resource API boundary, static dashboard asset, no public project creation을 닫았다.
- Story 6.2는 Project selection UI가 `GET /api/projects` current shape만 표시하고 `links.applications`로 Application List에 진입하도록 닫았다.
- Story 6.3~6.7은 Application List, Dashboard, Instance Evidence, Instance Snapshot Trend, Snapshot/History가 모두 server response link와 Bearer header를 사용하고 UI-side recomputation을 만들지 않도록 static contract tests를 강화했다.
- 따라서 Story 6.10은 UI flow를 다시 설계하지 않고 server-side project visibility와 project-scoped authorization만 닫아야 한다.

### Testing

Focused test 대상 후보:

- `ProjectNavigationResourceAuthorizationTest`
- `ProjectNavigationControllerTest`
- `ProjectApplicationNavigationServiceTest`
- `AccountProjectMembershipRepositoryIntegrationTest`
- `CatalogSchemaMigrationIntegrationTest`
- `FlywayJpaSchemaAlignmentIntegrationTest`
- representative nested resource controller tests:
  - `DashboardControllerTest`
  - `InstanceEvidenceControllerTest`
  - `InstanceSnapshotTrendControllerTest`
  - `OperationalEventHistoryControllerTest`
  - `DashboardSnapshotControllerTest`
- `ProjectEntryUiContractTest`
- `ProjectSelectionUiContractTest`
- `MvcLayerBoundaryTest`

필수 scenario:

- account A token은 account A active membership project만 `GET /api/projects`에서 본다.
- account B project는 account A project list에 나타나지 않는다.
- membership 없는 account는 `200 + projects=[]`를 받는다.
- disabled membership과 disabled project는 project list와 resource API에서 제외된다.
- account A token으로 account B project의 `/api/projects/{projectId}/applications`를 호출하면 `404`다.
- account A token으로 account B project의 dashboard/instance/history/snapshot representative resource API를 호출하면 `404`다.
- no-token/invalid-token/expired-token은 `401`이고 service/repository를 호출하지 않는다.
- membership이 있는 account가 application/project path mismatch를 호출하면 기존 catalog path 정합성 `404`가 유지된다.
- Project Entry static UI는 `projects=[]`를 safe empty state로 렌더링하고 Create Project flow를 만들지 않는다.

Suggested commands:

```bash
./gradlew :observability-portal:test --tests '*ProjectNavigation*'
./gradlew :observability-portal:test --tests '*AccountProjectMembership*'
./gradlew :observability-portal:test --tests '*DashboardController*'
./gradlew :observability-portal:test --tests '*InstanceEvidence*'
./gradlew :observability-portal:test --tests '*InstanceSnapshotTrend*'
./gradlew :observability-portal:test --tests '*OperationalEventHistory*'
./gradlew :observability-portal:test --tests '*DashboardSnapshot*'
./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest
./gradlew :observability-portal:test
git diff --check
```

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- 2026-05-31: BMAD create-story workflow, project context, sprint status, Epic 6 source artifacts를 확인했다.
- 2026-05-31: Story 6.1/6.2와 contract decisions에서 no public project creation, Bearer token, static UI, no browser token persistence 경계를 확인했다.
- 2026-05-31: 현재 `GET /api/projects`가 Bearer token 검증 후에도 전체 project list를 반환하는 코드 상태를 확인했다.
- 2026-05-31: `V001` projects schema와 `V008` accounts/refresh token schema 사이에 account-project relation이 없음을 확인했다.
- 2026-05-31: Story 6.10에서 N:M membership, single `member` role, `active|disabled` status, mismatch `404` fail-closed, safe fixture 기준을 닫았다.
- 2026-05-31: dev-story workflow를 시작하며 story와 sprint status를 `in-progress`로 갱신했다.
- 2026-05-31: membership schema/persistence/service/controller guard 테스트를 먼저 추가했고, missing class/method compile failure로 RED 상태를 확인했다.
- 2026-05-31: `V011__create_account_project_memberships.sql`, membership JPA entity/repository/service, project-scoped membership interceptor를 구현했다.
- 2026-05-31: `GET /api/projects`를 Bearer account id 기준 scoped list로 바꾸고 `/api/projects/{projectId}/applications/**`에 membership guard를 공통 적용했다.
- 2026-05-31: Epic 5 catalog path 정합성을 뜻하던 code comment의 "membership" 표현을 account-project authorization과 구분되도록 정리했다.
- 2026-05-31: 문서와 UI static contract, repository/controller/service regression test를 갱신했다.
- 2026-05-31: 권장 focused test, 전체 `:observability-portal:test`, `git diff --check` 통과를 확인했다.

### Completion Notes List

- `account_project_memberships` table을 V011 migration으로 추가하고 `member`, `active|disabled`, unique/FK/check/index/comment 기준을 구현했다.
- `domain.account` feature package에 membership entity/repository/service를 추가하고, service external model에는 JPA entity를 직접 노출하지 않았다.
- `GET /api/projects`는 Bearer interceptor가 남긴 account id 기준 active membership project만 반환하도록 변경했다.
- `/api/projects/{projectId}/applications` 및 하위 resource API는 `AccountProjectMembershipResourceApiInterceptor`가 account id와 path project id로 active membership을 먼저 확인하고 mismatch를 body 없는 `404`로 fail-closed한다.
- 기존 Dashboard/Instance/History/Snapshot service의 project/application/instance 검증은 account authorization과 분리된 catalog path 정합성으로 주석을 정리했다.
- `planning-artifacts/database-schema.md`, `planning-artifacts/api-surface.md`, `planning-artifacts/contracts/account-auth-policy.md`에 Story 6.10 authorization boundary를 반영했다.
- Project Entry UI는 server response `projects=[]` empty state만 렌더링하고 public Create Project flow를 열지 않는 contract test를 추가했다.
- 검증: `*ProjectNavigation*`, `*AccountProjectMembership*`, `*DashboardController*`, `*InstanceEvidence*`, `*InstanceSnapshotTrend*`, `*OperationalEventHistory*`, `*DashboardSnapshot*`, `MvcLayerBoundaryTest`, 전체 `:observability-portal:test`, `git diff --check` 통과.

### File List

- `implementation-artifacts/sprint-status.yaml`
- `observability-portal/src/main/java/com/observation/portal/domain/account/controller/AccountProjectMembershipResourceApiInterceptor.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/controller/BearerResourceApiInterceptor.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/controller/BearerResourceApiWebConfig.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/repository/AccountProjectMembershipEntity.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/repository/AccountProjectMembershipRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/service/AccountProjectMembershipService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/controller/ProjectNavigationController.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/entity/ApplicationInstanceEntity.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/service/ProjectApplicationNavigationService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/controller/OperationalEventHistoryController.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/service/OperationalEventHistoryService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/instance/controller/InstanceEvidenceController.java`
- `observability-portal/src/main/java/com/observation/portal/domain/instance/controller/InstanceSnapshotTrendController.java`
- `observability-portal/src/main/java/com/observation/portal/domain/instance/model/InstanceEvidenceReadModel.java`
- `observability-portal/src/main/java/com/observation/portal/domain/instance/service/InstanceEvidenceReadModelService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/instance/service/InstanceSnapshotTrendService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/instance/service/InvalidSnapshotTrendQueryException.java`
- `observability-portal/src/main/java/com/observation/portal/domain/instance/service/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/controller/DashboardSnapshotController.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotJpaRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotCaptureService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotDetailService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotMarkerService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/InvalidSnapshotMarkerQueryException.java`
- `observability-portal/src/main/resources/db/migration/V011__create_account_project_memberships.sql`
- `observability-portal/src/test/java/com/observation/portal/domain/account/repository/AccountProjectMembershipRepositoryIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/account/service/AccountProjectMembershipServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/controller/ProjectNavigationControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/controller/ProjectNavigationResourceAuthorizationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/repository/CatalogSchemaMigrationIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/repository/FlywayJpaSchemaAlignmentIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/service/ProjectApplicationNavigationServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ProjectEntryUiContractTest.java`
- `planning-artifacts/api-surface.md`
- `planning-artifacts/contracts/account-auth-policy.md`
- `planning-artifacts/database-schema.md`
- `planning-artifacts/stories/6-10-account-project-membership-and-scoped-project-entry.md`

### Change Log

- 2026-05-31: Story 6.10 dev-story implementation을 완료하고 account-project membership authorization boundary를 구현했다.
