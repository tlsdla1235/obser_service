---
artifactType: contract-decisions
projectName: Spring Boot 운영 첫 화면 포털
storyId: "6.10"
storyKey: "6-10-account-project-membership-and-scoped-project-entry"
status: closed
date: 2026-05-31
scope: Story 6.10 pre-dev contract closure before BMAD dev-story execution
---

# Story 6.10 Account-project Membership and Scoped Project Entry Contract Decisions

## Purpose

Story 6.10은 Project Entry와 project-scoped resource API의 account-project authorization 경계를 닫는다.

현재 portal은 GitHub OAuth 성공 후 service access token으로 `/api/projects` resource API에 접근할 수 있지만, Project와 Account/User가 연결되어 있지 않다. 그 결과 `GET /api/projects`가 인증된 사용자에게 전체 project catalog를 반환할 수 있다.

이 문서는 Epic 6 follow-up/open decision이었던 project ownership / account-project relation을 MVP 범위에 맞는 N:M membership model로 닫는다. 목표는 Project creation, project key 발급/회전, invite/team/org/billing 결정을 새로 열지 않고, authenticated account 기준 project visibility와 project-scoped resource authorization만 fail-closed로 고정하는 것이다.

## Authority and Non-Reopen Rules

이 문서는 아래 계약과 산출물을 기준으로 한다.

- `planning-artifacts/stories/6-10-account-project-membership-and-scoped-project-entry.md`
- `planning-artifacts/stories/6-1-account-project-entry-and-setup-guide.md`
- `planning-artifacts/stories/6-2-project-selection-ui.md`
- `implementation-artifacts/spec-story-6-1-account-project-entry-and-setup-guide-contract-decisions.md`
- `implementation-artifacts/spec-story-6-2-project-selection-ui-contract-decisions.md`
- `planning-artifacts/contracts/account-auth-policy.md`
- `planning-artifacts/api-surface.md`
- `planning-artifacts/database-schema.md`
- `implementation-artifacts/epic-6-context.md`
- `implementation-artifacts/sprint-status.yaml`

Story 6.10 dev-story는 아래 결정을 다시 열지 않는다.

- Account signup/login은 GitHub OAuth only다.
- Resource API 인증 token은 `Authorization: Bearer <access_token>` service access token이다.
- GitHub OAuth provider token은 resource API authorization에 사용하지 않는다.
- Browser token persistence, URL fragment/query token parsing, cookie server session은 도입하지 않는다.
- Account-project authorization은 `account_project_memberships` N:M table로 닫는다.
- MVP role은 `member` 단일 role이다.
- MVP membership status는 `active`, `disabled`다.
- Active membership만 project visibility와 project-scoped resource API access를 허용한다.
- Membership mismatch는 `403 Forbidden`이 아니라 `404 Not Found`로 fail-closed한다.
- Project creation, project key issuance/rotation, invite/team/org/admin UI, billing/tenant model은 이번 story에서 열지 않는다.
- Epic 5의 project/application/instance membership 표현은 catalog path 정합성으로 취급하고, Story 6.10의 membership은 account-project authorization으로 분리한다.

## Closed Decisions

### 1. N:M Account-project Membership Model

Account와 Project는 N:M relation으로 연결한다.

결정 내용:

- Table 이름은 `account_project_memberships`다.
- 한 account는 여러 project에 속할 수 있다.
- 한 project도 여러 account에 노출될 수 있다.
- Membership row는 control-plane resource API authorization source다.
- Starter ingest의 `X-OBS-Project-Key` 인증 경계와 분리한다.
- Account signup/login의 GitHub external identity 경계와도 분리한다.

권장 schema:

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

결정 이유:

- Project creation/ownership/product admin을 열지 않고도 현재 보안 결함인 "인증된 사용자에게 전체 project list 노출"을 닫을 수 있다.
- Unique account-project relation은 중복 권한 row를 막는다.
- Account 기준 list 조회와 project 기준 authorization check가 모두 필요하다.

### 2. MVP Role and Status Contract

Story 6.10은 최소 role/status model만 둔다.

결정 내용:

- `role`은 `member` 단일 값만 허용한다.
- `status`는 `active`, `disabled`만 허용한다.
- Authorization에는 `status=active` row만 사용한다.
- `status=disabled` row는 project list와 project-scoped resource API에서 membership 없음처럼 취급한다.
- Future owner/admin/viewer role은 별도 migration과 product/admin story에서 role check constraint를 확장해 도입한다.

금지:

- `owner`, `admin`, `viewer` role을 Story 6.10에서 추가하지 않는다.
- Role별 UI, role edit API, invite flow, team/org management를 만들지 않는다.
- Billing tenant, organization tenant, seat management를 만들지 않는다.

결정 이유:

- 이번 story의 목적은 project visibility와 authorization scope closure다.
- Role hierarchy를 지금 도입하면 project creation, invite, admin UI, audit policy가 함께 열려야 한다.
- 단일 `member` role은 N:M relation을 보존하면서 MVP blast radius를 작게 유지한다.

### 3. Scoped `GET /api/projects`

`GET /api/projects`는 authenticated account의 active membership project만 반환한다.

결정 내용:

- Bearer access token 검증 결과의 account id를 scoped project list service input으로 전달한다.
- Service/repository는 `account_project_memberships.status='active'`와 `projects.status='active'` 기준으로 project list를 만든다.
- Membership이 없는 account는 `200 OK`와 `projects=[]`를 받는다.
- Project Entry UI는 server response를 그대로 렌더링한다.
- Empty state는 safe copy로 처리하되 public Create Project flow를 열지 않는다.

금지:

- `projectRepository.findAll(...)`를 resource API response로 그대로 반환하지 않는다.
- UI가 hard-coded project id, previous response, URL, DOM dataset으로 project visibility를 추론하지 않는다.
- Login 직후 자동 project 생성이나 public `POST /api/projects`를 만들지 않는다.

결정 이유:

- Project Entry는 scope 선택 화면이고, scope 후보 자체가 authorization boundary 안에 있어야 한다.
- Empty project list는 정상 가능한 상태다. 이 상태를 이유로 project creation product decision을 열지 않는다.

### 4. Project-scoped Resource API Authorization

`/api/projects/{projectId}/applications`와 후속 `/api/projects/{projectId}/applications/**` resource API는 active membership이 있을 때만 허용한다.

결정 내용:

- Bearer token 검증은 기존처럼 `Authorization: Bearer <access_token>` service JWT로 한다.
- Bearer 검증 후 account id와 path `projectId`로 active membership을 확인한다.
- Membership이 없으면 `404 Not Found`로 수렴한다.
- Membership이 있으면 기존 catalog path 정합성 검증을 이어서 수행한다.
- Existing application/dashboard/instance/history/snapshot path mismatch도 계속 `404 Not Found`다.
- Query validation `400`은 membership이 확인된 scope에서만 반환한다.

적용 대상:

- `GET /api/projects/{projectId}/applications`
- `GET /api/projects/{projectId}/applications/{applicationId}/dashboard`
- `GET /api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/evidence`
- `GET /api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/snapshot-trend`
- `GET /api/projects/{projectId}/applications/{applicationId}/operational-events`
- `GET /api/projects/{projectId}/applications/{applicationId}/dashboard/snapshot-markers`
- `GET /api/projects/{projectId}/applications/{applicationId}/dashboard/snapshots/{snapshotId}`
- 새로 추가되는 `/api/projects/{projectId}/applications/**` resource API

결정 이유:

- `403`은 해당 project가 존재하고 권한만 없다는 신호가 될 수 있다.
- `404` fail-closed는 project/application/snapshot 존재 여부 노출을 줄인다.
- Existing catalog path mismatch도 이미 `404`를 사용하므로 사용자-facing behavior가 단순하다.

### 5. Token Boundary

Story 6.10은 인증 token 방식을 바꾸지 않는다.

결정 내용:

- Resource API는 `Authorization: Bearer <access_token>` header만 사용한다.
- No-token/invalid-token/expired-token은 `401 Unauthorized`와 `WWW-Authenticate: Bearer`를 유지한다.
- Membership service는 account id와 project id만 입력으로 받는다.
- Membership service는 access token 원문, refresh token 원문, GitHub OAuth token을 받거나 저장하지 않는다.

금지:

- GitHub provider token으로 resource API를 호출하지 않는다.
- Refresh token을 project resource API authorization에 사용하지 않는다.
- Browser localStorage/sessionStorage/cookie/token URL parsing을 도입하지 않는다.

결정 이유:

- Story 6.1이 service token JSON body delivery와 Bearer resource API boundary를 닫았다.
- Story 6.10은 authorization scope를 추가하는 story이지 token delivery/browser storage story가 아니다.

### 6. Safe Migration and Fixture Contract

Existing project와 account를 자동으로 모두 연결하지 않는다.

결정 내용:

- Production Flyway migration은 membership table과 constraint/index/comment만 추가한다.
- Production migration에서 모든 account에 모든 project를 grant하지 않는다.
- Existing project가 있어도 membership row가 없으면 authenticated account는 `projects=[]`를 본다.
- Local/demo/test fixture는 명시적 account id와 project id를 사용해 membership row를 만든다.
- Demo convenience가 필요하면 local/internal bootstrap profile 또는 test fixture에서만 처리한다.

금지:

- Migration 안에 raw project key를 쓰지 않는다.
- Migration 안에서 account를 임의 생성하거나 모든 project를 모든 account에 grant하지 않는다.
- Public Project creation API나 Create Project UI를 safe migration의 우회 경로로 만들지 않는다.

결정 이유:

- Wide grant migration은 이번 story의 보안 목적을 무력화한다.
- Empty project list는 safe state이며 public creation decision을 강제로 열 필요가 없다.

### 7. Catalog Path Consistency vs Account-project Authorization

Epic 5의 project/application/instance "membership" 표현은 authorization membership이 아니다.

결정 내용:

- `applications.project_id`, `application_instances.application_id`, `dashboard_snapshots.project_id/application_id` 관계는 catalog path 정합성이다.
- Story 6.10의 `account_project_memberships`는 account-project authorization이다.
- 구현 중 comment/Javadoc/test 이름에서 두 의미를 분리한다.
- 기존 path mismatch `404` behavior는 유지한다.

결정 이유:

- 같은 "membership" 단어가 catalog containment와 authorization relation을 모두 뜻하면 구현자가 account boundary를 놓치기 쉽다.
- Story 6.10의 목적은 account가 어떤 project를 볼 수 있는지를 닫는 것이다.

## Open Decisions That Remain

아래 항목은 Story 6.10에서 구현자가 임의로 닫지 않는다.

- Project creation의 실제 방식
- Project key 발급, 회전, 재발급 product workflow
- Owner/admin/viewer role hierarchy
- Invite flow, team management, organization management
- Billing/tenant model
- Browser UI가 service token을 memory, sessionStorage, localStorage 중 어디에 보관할지
- Refresh token browser storage와 logout/session persistence policy
- Audit log, membership change history, invitation email, SCIM/SSO integration

## BMAD Dev-Story Implementation Notes

Story 6.10 dev-story 실행자는 아래를 지킨다.

- Story file은 BMAD dev-story workflow 지침에 따라 Tasks/Subtasks checkboxes, Dev Agent Record, File List, Change Log, Status 영역만 수정한다.
- 계약 결정 자체를 바꾸는 문서 수정은 하지 않는다.
- 필요한 production/test code는 Traditional MVC + Service/Repository Layering과 feature-first package 구조를 따른다.
- Public class, public method, 복잡한 helper에는 AGENTS.md 지침에 따라 한국어 Javadoc/comment를 작성한다.
- Controller는 repository를 직접 호출하지 않고 service 또는 dedicated authorization component에 위임한다.
- Repository/JPA entity는 controller/dto에 의존하지 않는다.
- JPA entity를 API response나 service external model로 직접 반환하지 않는다.
- 기존 사용자 변경이 있는 migration/test 파일을 되돌리지 않는다.

## Verification Expectations

Story 6.10 completion 전 최소 아래 검증을 수행한다.

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
