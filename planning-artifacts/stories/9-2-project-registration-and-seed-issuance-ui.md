---
artifactType: story
storyId: "9.2"
storyKey: "9-2-project-registration-and-seed-issuance-ui"
epic: "Epic 9. Product Onboarding and Project Seed Issuance UI"
title: "Project registration and seed issuance UI"
architectureStyle: Traditional MVC
status: review
date: 2026-06-01
---

# Story 9.2 - Project registration and seed issuance UI

## Status

review

## Story

GitHub OAuth로 로그인한 portal 사용자는 public onboarding UI에서 project를 등록하고 starter 연결에 필요한 starter credential을 1회 표시로 발급받고 싶다.

그래야 local/operator smoke seed 없이도 자신의 account-project membership 안에서 starter를 앱에 붙이고, raw credential을 안전하게 복사한 뒤 기존 Project -> Application -> Dashboard 흐름에서 상태를 확인할 수 있다.

## Source of Truth

구현 중 충돌처럼 보이는 지점은 아래 우선순위를 따른다.

1. `planning-artifacts/epic-9-product-onboarding-and-project-seed-issuance-ui.md`
2. `planning-artifacts/contracts/account-auth-policy.md`
3. `planning-artifacts/api-surface.md`
4. `planning-artifacts/source-of-truth/current-product-source-of-truth.md`
5. `planning-artifacts/stories/6-1-account-project-entry-and-setup-guide.md`
6. `planning-artifacts/stories/6-10-account-project-membership-and-scoped-project-entry.md`
7. `planning-artifacts/stories/7-1-real-github-oauth-smoke-seed-and-operator-runbook.md`
8. `implementation-artifacts/real-github-oauth-smoke-runbook.md`
9. `planning-artifacts/database-schema.md`
10. `planning-artifacts/architecture.md`
11. `planning-artifacts/architecture-implementation-supplement.md`
12. `planning-artifacts/project-structure.md`
13. `_bmad/custom/project-context.md`
14. `AGENTS.md`

확인한 현재 코드/스키마 후보:

1. `observability-portal/src/main/resources/db/migration/V001__create_projects.sql`
2. `observability-portal/src/main/resources/db/migration/V011__create_account_project_memberships.sql`
3. `observability-portal/src/main/java/com/observation/portal/domain/catalog/entity/ProjectEntity.java`
4. `observability-portal/src/main/java/com/observation/portal/domain/catalog/model/ProjectStatus.java`
5. `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ProjectRepository.java`
6. `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/ProjectKeyVerificationService.java`
7. `observability-portal/src/main/java/com/observation/portal/security/ProjectKeyHashVerifier.java`
8. `observability-portal/src/main/java/com/observation/portal/domain/account/service/AccountProjectMembershipService.java`
9. `observability-portal/src/main/java/com/observation/portal/domain/admin/service/SmokeProjectSeedService.java`
10. `observability-portal/src/main/resources/static/dashboard/index.html`
11. `observability-portal/src/main/resources/static/dashboard/app.js`
12. `observability-portal/src/test/java/com/observation/portal/domain/account/controller/AuthSecretExposureGuardTest.java`
13. `observability-portal/src/test/java/com/observation/portal/domain/catalog/controller/ProjectNavigationResourceAuthorizationTest.java`
14. `observability-portal/src/test/java/com/observation/portal/domain/admin/service/SmokeProjectSeedServiceTest.java`

## Scope / Out of Scope

포함:

- authenticated public onboarding API로 project 등록
- 등록한 account와 project의 active membership 생성
- starter ingest용 credential 생성, BCrypt hash 저장, prefix metadata 저장
- raw credential 생성 시 1회 표시와 copy UX
- read/reload 이후 raw credential 재조회 금지
- starter credential rotation과 revocation flow
- Project Entry empty state에서 registration flow로 진입하는 UI
- credential metadata status 표시: prefix, active/revoked, issuedAt, rotatedAt, revokedAt 후보
- active account-project membership 기반 권한 확인
- no-token/invalid-token은 `401`, membership mismatch는 `404` fail-closed 유지
- smoke/admin seed와 public registration/issuance API 분리
- raw credential이 dashboard/read model/UI/snapshot/history/API/log/error/test fixture로 새지 않는 regression guard
- architecture/API/schema/source-of-truth 문서 갱신

제외:

- Story 9.1 product introduction 구현
- local-only smoke seed runner 변경 또는 제거
- invite/team/org/admin role management
- owner/admin/viewer 세분화. MVP authority는 active membership `member` 기준으로 시작한다.
- billing/tenant model
- project 삭제 workflow
- multi-credential 동시 활성화, grace period rotation
- credential audit/export/download
- browser token persistence, refresh token memo, URL token parsing, cookie server session
- GitHub provider token 저장 또는 resource API authorization에 provider token 사용
- starter heartbeat/bucket ingest contract 의미 변경
- dashboard state/rule/p95/p99/endpoint priority 계산 변경
- raw metric explorer, arbitrary query UI

## Acceptance Criteria

1. Story 9.2 planning story file은 `ready-for-dev` 상태로 존재하고, 이 작업에서는 `sprint-status.yaml`을 수정하지 않는다.
2. 구현 story 실행 시 production 변경은 Traditional MVC + Service/Repository Layering과 feature-first package 구조를 따른다.
3. Public onboarding project registration은 GitHub OAuth로 생성된 service access token의 `Authorization: Bearer <access_token>` 인증을 요구한다.
4. No-token/invalid-token/expired-token request는 `401 Unauthorized`와 `WWW-Authenticate: Bearer`로 수렴한다.
5. Project registration은 email/password, GitHub 외 provider, anonymous flow를 열지 않는다.
6. Project registration API는 project name 같은 non-secret 입력만 받는다.
7. Project registration API는 client가 raw project key, key prefix, hash, account id, membership role/status를 임의 지정하게 하지 않는다.
8. Project registration 성공 시 새 active project row와 현재 account의 active `member` membership이 함께 생성된다.
9. Project name uniqueness/normalization 정책은 DB constraint와 service validation으로 fail-closed 처리한다.
10. Project registration 실패 response/log/error는 OAuth token, service access/refresh token, starter credential raw value, hash, provider raw payload를 노출하지 않는다.
11. Starter credential raw value는 `<key_prefix>.<secret>` shape를 따른다.
12. Starter credential raw value는 BCrypt 72 byte 입력 한계를 넘지 않는다.
13. `key_prefix`는 기존 `projects.key_prefix` 길이 제약인 32자 이하를 지킨다.
14. Raw starter credential은 DB row, migration, log, exception, response body snapshot, repository lookup surface, test fixture에 저장하지 않는다.
15. DB에는 BCrypt hash와 prefix, credential status/issuedAt/rotatedAt/revokedAt 같은 non-secret metadata만 저장한다.
16. Credential active/revoked 상태는 `projects.status`와 섞지 않는다. Project를 숨기지 않고 starter credential만 폐기할 수 있어야 한다.
17. MVP schema는 기존 `projects.key_prefix`/`project_key_hash` 단일 active credential model을 유지하되, credential status metadata를 추가해 revoke/rotate를 표현할 수 있다.
18. 더 큰 변경이 필요해 별도 credential table을 도입한다면 기존 `ProjectKeyVerificationService`와 smoke seed migration path를 함께 갱신하고 raw secret 저장 금지를 동일하게 유지한다.
19. ProjectKey verification은 active project와 active starter credential을 모두 통과해야 ingest를 허용한다.
20. Revoked credential은 `POST /api/ingest/v1/buckets`와 `POST /api/ingest/v1/heartbeat`에서 `401`로 닫힌다.
21. Registration response는 raw starter credential을 생성 직후 1회 표시용 field로만 반환한다.
22. Registration response에는 `Cache-Control: no-store` 또는 동등한 no-cache secret response guard를 둔다.
23. Raw starter credential field 이름은 account `accessToken`/`refreshToken`과 혼동되지 않게 `starterCredential.displayValue` 또는 동등한 이름을 사용한다.
24. General project read API, `GET /api/projects`, Application List, Dashboard, Instance Evidence, Snapshot/History response에는 raw starter credential이 절대 포함되지 않는다.
25. Credential metadata read가 필요하면 prefix/status/issuedAt/rotatedAt/revokedAt만 반환하고 raw value/hash를 반환하지 않는다.
26. UI는 raw credential을 생성/회전 성공 화면에서만 보여주고, 화면 reload/navigation 이후 다시 볼 수 없음을 명확히 표시한다.
27. UI는 raw credential을 `localStorage`, `sessionStorage`, cookie, URL fragment/query, DOM dataset, hidden input, long-lived global state에 저장하지 않는다.
28. Copy UX는 clipboard 성공/실패 상태를 보여주되 raw credential 값을 log, error, analytics payload, aria-label, title attribute에 넣지 않는다.
29. 사용자가 1회 표시 화면을 닫기 전에 "복사했음" 또는 동등한 확인 action을 제공한다.
30. UI는 credential을 다시 볼 수 없고 필요하면 rotation으로 새 credential을 발급받아야 한다는 copy를 제공한다.
31. Rotation API는 active membership이 있는 account만 호출할 수 있다.
32. Rotation API는 기존 starter credential을 즉시 폐기하고 새 raw starter credential을 1회 표시 응답으로 반환한다.
33. Rotation response에도 `Cache-Control: no-store`를 적용하고 raw value/hash를 log/error/test fixture에 남기지 않는다.
34. Revocation API는 active membership이 있는 account만 호출할 수 있다.
35. Revocation API는 raw credential을 반환하지 않고 credential status metadata만 반환한다.
36. Revocation 후 Project는 account project list에 남을 수 있지만 starter ingest credential 검증은 실패해야 한다.
37. Membership 없는 account가 credential metadata/rotation/revocation project-scoped API를 호출하면 project 존재 여부를 드러내지 않는 `404`로 fail-closed한다.
38. Membership mismatch response는 project name, application count, credential prefix/status, snapshot/history 존재 여부를 드러내지 않는다.
39. MVP authority는 active account-project membership `member`로 시작한다. owner/admin role은 이 story에서 만들지 않는다.
40. Public UI/API는 `domain.admin.service.SmokeProjectSeedService`, `portal.smoke.seed.*`, `.private/smoke-auth.env`, `.private/smoke-project.env`, `.private/smoke-seed.properties`를 사용하지 않는다.
41. Story 7 local-only smoke seed는 계속 local/operator path로 유지되고 public registration API의 우회 경로가 되지 않는다.
42. Smoke/admin seed가 사용하는 helper를 공통화하더라도 public API에서 local-only profile guard나 `.private` secret file을 요구하지 않는다.
43. Project Entry empty state는 public registration flow로 들어갈 수 있지만 hard-coded seed나 smoke project를 만들지 않는다.
44. Registration UI는 project list를 server response 기준으로 refresh하고, 성공 후 새 project가 membership project list에 나타나야 한다.
45. Registration UI는 바로 dashboard 판단을 만들지 않고 Application List/Dashboard는 기존 server-provided links를 따른다.
46. Setup guide는 발급된 starter credential을 `X-OBS-Project-Key`/starter project key 성격으로 설명하고, portal resource API Bearer token과 분리한다.
47. Starter credential raw value는 starter configuration 예시 안에서도 placeholder 또는 현재 1회 표시 값으로만 존재한다. 문서/test fixture에 실제처럼 보이는 secret을 남기지 않는다.
48. Dashboard/read model/snapshot/history serialization tests는 raw credential, hash, access token, refresh token, provider token이 포함되지 않음을 검증한다.
49. Secret exposure guard test는 registration/rotation response를 제외한 source, fixture, runbook, static UI snapshot에 forbidden credential snippets가 없음을 검증한다.
50. Backend tests는 registration success, duplicate/invalid project name, no-token `401`, account membership creation, no raw persistence를 검증한다.
51. Backend tests는 ProjectKey verification이 revoked credential을 거부하고 rotated old credential을 거부하는지 검증한다.
52. Backend tests는 rotation success, immediate old credential invalidation, one-time raw display response, no-store header를 검증한다.
53. Backend tests는 revocation success와 revoked credential ingest/heartbeat `401`을 검증한다.
54. Backend tests는 membership mismatch `404`가 credential metadata/rotation/revocation에서 resource service lookup 전에 fail-closed 되는지 검증한다.
55. Static UI contract tests는 copy UX, one-time display, no browser persistence, no raw credential in long-lived attributes/state를 검증한다.
56. MVC layer guard는 새 controller가 repository를 직접 호출하지 않고 service에 위임하며 repository가 controller/dto에 의존하지 않음을 검증한다.
57. JPA entity는 controller response DTO, public API surface, service external result로 직접 반환하지 않는다.
58. API/schema/source docs는 public registration/credential lifecycle, one-time display, raw secret storage prohibition, smoke/admin separation을 반영한다.
59. 새 public class/method/helper와 이해하기 어려운 내부 helper에는 `AGENTS.md` 지침에 따라 한국어 Javadoc/comment를 작성한다.
60. Suggested verification과 `git diff --check`가 통과해야 implementation completion으로 볼 수 있다.

## Tasks / Subtasks

- [x] Backend API surface와 contract 문서 확정 (AC: 3~10, 21~25, 31~39, 58)
  - [x] `POST /api/projects` registration endpoint의 request/response shape를 `api-surface.md`에 고정한다.
  - [x] `GET /api/projects/{projectId}/starter-credential` 또는 동등 metadata endpoint를 열지 여부를 결정하고 raw 미반환 shape를 고정한다.
  - [x] `POST /api/projects/{projectId}/starter-credential/rotations` 또는 동등 rotation endpoint를 고정한다.
  - [x] `POST /api/projects/{projectId}/starter-credential/revocations` 또는 동등 revocation endpoint를 고정한다.
  - [x] 모든 secret-bearing response에 no-store header 기준을 문서화한다.
  - [x] account service token과 starter ingest credential 용어를 분리해 문서화한다.

- [x] Schema/persistence credential lifecycle 구현 (AC: 11~20, 36, 50~53, 57~59)
  - [x] 기존 `projects.key_prefix`/`project_key_hash` 단일 active credential model을 유지할지 별도 table을 도입할지 구현 전 최종 결정한다.
  - [x] 기본 제안대로 기존 `projects` model을 유지한다면 credential status/issuedAt/rotatedAt/revokedAt metadata migration을 추가한다.
  - [x] Credential status는 project visibility status와 분리한다.
  - [x] Flyway migration에 table/column 한국어 comment를 작성한다.
  - [x] JPA entity/service model을 갱신하되 entity를 API response로 직접 반환하지 않는다.
  - [x] ProjectKey verification은 project active와 credential active를 모두 확인한다.
  - [x] BCrypt 72 byte guard, prefix length guard, raw secret non-persistence guard를 유지한다.

- [x] Project registration service 구현 (AC: 3~15, 21~24, 39, 43~47, 50, 56~59)
  - [x] Bearer interceptor가 남긴 account id를 service input으로 받아 project를 생성한다.
  - [x] Project name validation과 uniqueness conflict mapping을 구현한다.
  - [x] Server-side generator가 prefix와 secret을 만들고 BCrypt hash만 저장한다.
  - [x] 현재 account와 새 project의 active membership row를 만든다.
  - [x] 성공 response는 project summary와 one-time `starterCredential.displayValue`만 raw value로 반환한다.
  - [x] Response/log/error/test fixture에 hash, provider token, access/refresh token, raw payload가 새지 않게 한다.

- [x] Credential metadata/rotation/revocation service 구현 (AC: 25, 31~38, 51~54, 56~59)
  - [x] Metadata read가 필요하면 prefix/status/timestamps만 반환한다.
  - [x] Rotation은 active membership 확인 후 기존 credential을 즉시 폐기하고 새 hash/prefix를 저장한다.
  - [x] Rotation response는 새 raw value를 1회 표시로만 반환한다.
  - [x] Revocation은 active membership 확인 후 credential status를 revoked로 바꾸고 raw value를 반환하지 않는다.
  - [x] Membership mismatch는 resource lookup 전에 `404` fail-closed로 닫는다.
  - [x] Revoked/rotated old credential ingest와 heartbeat는 `401`을 반환한다.

- [x] Public onboarding UI 구현 (AC: 26~30, 40~47, 55)
  - [x] Project Entry empty state 또는 onboarding panel에서 registration flow로 진입한다.
  - [x] Project name 입력, submit/loading/error/success 상태를 구현한다.
  - [x] Success state는 raw starter credential을 1회 표시하고 copy action과 "복사했음" confirmation을 제공한다.
  - [x] Reload/navigation 이후 raw credential을 복원하지 않는다.
  - [x] Rotation/revocation UI는 metadata와 one-time raw display boundary를 따른다.
  - [x] UI는 `.private` smoke files, `portal.smoke.seed.*`, smoke seed copy를 사용하지 않는다.
  - [x] 성공 후 `GET /api/projects`를 refresh해 새 membership project를 server response로 확인한다.

- [x] Secret exposure and architecture regression tests (AC: 48~60)
  - [x] Registration controller/service tests를 추가한다.
  - [x] Credential lifecycle focused tests를 추가한다.
  - [x] `ProjectKeyVerificationServiceTest` 또는 ingest/heartbeat controller tests에 revoked/rotated old credential rejection을 추가한다.
  - [x] `AuthSecretExposureGuardTest`를 확장해 docs/static/test fixtures raw secret leakage를 막는다.
  - [x] Static dashboard contract test에 one-time display, copy UX, no browser persistence guard를 추가한다.
  - [x] `ProjectNavigationResourceAuthorizationTest` 또는 동등 test에 credential project-scoped mismatch `404`를 추가한다.
  - [x] `MvcLayerBoundaryTest`를 실행해 package/layer boundary를 유지한다.
  - [x] suggested commands와 `git diff --check`를 실행한다.

## Dev Notes

### Current Code State

- `projects` table은 현재 `id`, `name`, `key_prefix`, `project_key_hash`, `status`, timestamps를 가진다.
- `ProjectEntity`는 Flyway `projects` table에 매핑되며 persistence model로만 사용되어야 한다.
- `ProjectStatus`는 `active`, `disabled`만 갖고 project visibility와 key verification에서 쓰인다.
- `ProjectKeyVerificationService`는 raw header를 `<key_prefix>.<secret>` 형태로 파싱하고 BCrypt 72 byte guard와 prefix lookup/hash verification을 수행한다.
- 현재 project key revocation만을 표현하는 별도 credential status는 없다. Story 9.2는 project visibility status와 credential status를 분리해야 한다.
- `ProjectRepository`는 `findByKeyPrefix`와 `findByName`을 제공한다.
- `account_project_memberships`는 active membership project visibility와 project-scoped authorization의 source of truth다.
- `SmokeProjectSeedService`는 local-only smoke project, BCrypt project key hash, active membership을 만든다. Public onboarding API가 이 runner나 `.private` 설정을 호출하면 안 된다.
- Static dashboard는 token을 in-memory로만 받고 `GET /api/projects`와 server links를 따라 Project -> Application -> Dashboard로 진입한다.

### Proposed API Shape

최종 endpoint 이름은 구현 전 `api-surface.md`에 고정한다. 기본 제안은 아래와 같다.

```http
POST /api/projects
Authorization: Bearer <access_token>
Content-Type: application/json
```

```json
{
  "name": "orders-prod"
}
```

성공 response 후보:

```json
{
  "project": {
    "projectId": "00000000-0000-0000-0000-000000009201",
    "name": "orders-prod",
    "links": {
      "applications": "/api/projects/00000000-0000-0000-0000-000000009201/applications"
    }
  },
  "starterCredential": {
    "displayValue": "<shown-once>",
    "keyPrefix": "obs_live_xxxxx",
    "visibleOnce": true,
    "issuedAt": "2026-06-01T00:00:00Z"
  }
}
```

Raw value는 위 create/rotation success response에서만 허용한다. Subsequent project/navigation/dashboard/read responses는 `displayValue`를 포함하지 않는다.

Rotation/revocation 후보:

```http
POST /api/projects/{projectId}/starter-credential/rotations
POST /api/projects/{projectId}/starter-credential/revocations
```

### Implementation Guardrails

- `service token`이라는 표현은 account service access/refresh token과 충돌하기 쉽다. 구현 code/API에서는 starter ingest 인증용 값을 `starterCredential`, `projectKey`, `ingestCredential` 중 하나로 고정하고 account token과 분리한다.
- Public project registration은 user-facing product surface다. Story 7 smoke seed는 local/operator verification path로 유지한다.
- Existing `projects.key_prefix`/`project_key_hash`를 재사용하는 경우에도 credential revoked 상태를 project disabled와 섞지 않는다.
- Rotation은 기존 credential을 즉시 invalid로 만든다. Grace period가 필요하면 별도 decision/story로 분리한다.
- Credential raw value는 UI copy, log, exception, data attribute, hidden input, browser storage, snapshot/read model 어디에도 오래 남지 않게 한다.
- Clipboard API 실패 시 fallback copy UX를 제공하더라도 raw value를 title/aria-label/error message에 넣지 않는다.
- Project registration 성공 후 dashboard 판단을 shortcut으로 만들지 않는다. Existing Project -> Application -> Dashboard links를 그대로 따른다.
- UI/API는 starter heartbeat와 accepted bucket freshness 의미를 바꾸지 않는다.

### Expected Implementation Touchpoints

Backend/API 후보:

- `observability-portal/src/main/resources/db/migration/V012__add_project_starter_credential_status.sql` 또는 동등한 최신 migration
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/controller/ProjectRegistrationController.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/dto/*`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/model/*`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/service/ProjectRegistrationService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/service/StarterCredentialService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ProjectRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/ProjectKeyVerificationService.java`
- `observability-portal/src/main/java/com/observation/portal/security/ProjectKeyHashVerifier.java`

Static UI 후보:

- `observability-portal/src/main/resources/static/dashboard/index.html`
- `observability-portal/src/main/resources/static/dashboard/app.js`
- `observability-portal/src/main/resources/static/dashboard/styles.css`

Docs 후보:

- `planning-artifacts/api-surface.md`
- `planning-artifacts/database-schema.md`
- `planning-artifacts/contracts/account-auth-policy.md`
- `planning-artifacts/epics.md` 또는 Epic 9 overview 문서

## Testing

Focused test 대상 후보:

- `ProjectRegistrationControllerTest`
- `ProjectRegistrationServiceTest`
- `StarterCredentialServiceTest`
- `ProjectKeyVerificationServiceTest`
- `IngestControllerTest`
- `IngestHeartbeatControllerTest`
- `ProjectNavigationResourceAuthorizationTest`
- `ProjectEntryUiContractTest`
- `AuthSecretExposureGuardTest`
- `MvcLayerBoundaryTest`

필수 scenario:

- authenticated account가 project를 등록하면 project, active membership, active hashed starter credential이 생긴다.
- registration response는 raw credential을 1회 표시 field로 반환하고 no-store header를 포함한다.
- registration 실패 response/log/error는 secret/token/hash를 노출하지 않는다.
- duplicate/invalid project name은 row write를 잘못 남기지 않고 sanitized error로 닫힌다.
- project read/list/dashboard/history response에는 raw credential이 없다.
- rotation은 old credential ingest/heartbeat를 실패시키고 new credential만 허용한다.
- revocation은 project visibility를 숨기지 않으면서 ingest/heartbeat credential verification을 실패시킨다.
- membership 없는 account는 credential metadata/rotation/revocation에서 `404` fail-closed 된다.
- static UI는 raw credential을 browser storage, URL, data attribute, hidden input에 저장하지 않는다.
- smoke/admin seed config와 public UI/API가 연결되지 않는다.

Suggested commands:

```bash
./gradlew :observability-portal:test --tests '*ProjectRegistration*'
./gradlew :observability-portal:test --tests '*StarterCredential*'
./gradlew :observability-portal:test --tests '*ProjectKeyVerification*'
./gradlew :observability-portal:test --tests '*IngestControllerTest'
./gradlew :observability-portal:test --tests '*IngestHeartbeatControllerTest'
./gradlew :observability-portal:test --tests '*ProjectNavigationResourceAuthorizationTest'
./gradlew :observability-portal:test --tests '*ProjectEntryUiContractTest'
./gradlew :observability-portal:test --tests '*AuthSecretExposureGuardTest'
./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest
./gradlew :observability-portal:test
git diff --check
```

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- 2026-06-01: BMAD create-story template, Epic 9 source 문서, Story 6.1/6.10/7.1, auth/API/schema policy, current project key verification and smoke seed code boundary를 확인해 planning story를 작성했다.
- 2026-06-01: 구현은 시작하지 않았고 `sprint-status.yaml`도 수정하지 않았다.
- 2026-06-01: Stage 1 dev-story 실행을 시작하며 orchestration context와 Stage 1 source 문서를 확인했다. `sprint-status.yaml`은 사용자 지시대로 수정하지 않았다.
- 2026-06-01: RED 단계에서 `ProjectRegistrationControllerTest`, `ProjectRegistrationServiceTest`, `StarterCredentialGeneratorTest`, inactive starter credential verification test를 추가하고 missing class/schema compile failure를 확인했다.
- 2026-06-01: GREEN 단계에서 V012 credential metadata migration, `StarterCredentialStatus`, registration service/controller/DTO, one-time response/no-store, active member membership creation, active credential verification guard를 구현했다.
- 2026-06-01: `api-surface.md`와 `database-schema.md`에 Stage 1 backend API/schema shape를 최소 정렬했다.
- 2026-06-01: Stage 1 focused verification commands, schema/JPA alignment tests, `git diff --check` 통과를 확인했다.
- 2026-06-01: Stage 2 dev-story 실행을 시작하며 orchestration context와 Stage 2 source 문서를 확인했다. Stage 1 산출물과 `sprint-status.yaml`은 되돌리거나 수정하지 않았다.
- 2026-06-01: RED/GREEN 단계에서 starter credential metadata/rotation/revocation service/controller, membership guard, old credential rejection focused tests를 구현했다.
- 2026-06-01: static dashboard Project Entry에 project registration, one-time display/copy confirmation, credential metadata/rotation/revocation UI를 추가하고 browser persistence/static secret guard tests를 보강했다.
- 2026-06-01: `api-surface.md`, `database-schema.md`, `account-auth-policy.md`를 public registration/credential lifecycle과 raw secret storage prohibition 기준으로 정렬했다.
- 2026-06-01: Stage 2 focused verification commands, full `:observability-portal:test`, `git diff --check` 통과를 확인했다.

### Completion Notes List

- Stage 1 backend core만 구현했다. Rotation/revocation backend endpoint와 UI/onboarding flow는 Stage 2로 남겼다.
- `POST /api/projects`는 Bearer account id 기준으로 active project와 active `member` membership을 생성하고, raw starter credential은 `starterCredential.displayValue` 1회 표시 response로만 반환한다.
- Credential schema는 기존 `projects.key_prefix`/`project_key_hash` 단일 model을 유지하고 `starter_credential_status`, `starter_credential_issued_at`, `starter_credential_rotated_at`, `starter_credential_revoked_at` metadata를 추가했다.
- `ProjectKeyVerificationService`는 project active와 starter credential active를 모두 통과해야 ingest/heartbeat verification success를 반환한다.
- Public registration API는 `SmokeProjectSeedService`, `portal.smoke.seed.*`, `.private/smoke-*`에 의존하지 않는다.
- 검증: `*ProjectRegistration*`, `*StarterCredential*`, `ProjectKeyVerificationServiceTest`, `IngestControllerTest`, `IngestHeartbeatControllerTest`, `AuthSecretExposureGuardTest`, `ProjectNavigationResourceAuthorizationTest`, `MvcLayerBoundaryTest`, 추가 schema/JPA alignment tests, `git diff --check`가 통과했다.
- Stage 2 handoff: credential metadata/rotation/revocation endpoint, revoked credential ingest/heartbeat e2e rejection, UI one-time display/copy confirmation/browser persistence guard, broad secret exposure guard, full docs alignment가 남아 있다.
- Stage 2 lifecycle API를 구현했다. `GET /api/projects/{projectId}/starter-credential`은 prefix/status/timestamps metadata만 반환하고, rotation/revocation은 active membership guard 뒤에서 처리된다.
- Rotation은 기존 `projects.key_prefix`/`project_key_hash`를 새 credential로 즉시 교체하고 새 raw value는 `starterCredential.displayValue` 1회 표시 response로만 반환한다. Rotation response에는 `Cache-Control: no-store`와 `Pragma: no-cache`를 적용했다.
- Revocation은 project visibility를 유지하면서 `starter_credential_status='revoked'`로 바꾸고 raw value 없이 metadata만 반환한다.
- Starter credential lifecycle endpoint는 membership mismatch를 MVC interceptor에서 resource lookup 전에 body 없는 `404`로 닫는다.
- Static dashboard는 Project Entry onboarding panel에서 registration flow를 제공하고, 성공 후 `GET /api/projects`를 refresh한다. Raw credential은 create/rotate success 화면에서만 표시하고 copy/confirmation 후 UI에서 제거한다.
- UI guard는 raw credential을 browser storage, URL, hidden input, data attribute, aria-label/title, long-lived global state에 저장하지 않음을 정적/runtime contract test로 검증한다.
- Dashboard/read model/snapshot/history/instance source에는 raw credential/hash/access token/refresh token/provider token field가 포함되지 않도록 secret exposure guard를 확장했다.
- Docs alignment: `api-surface.md`, `database-schema.md`, `account-auth-policy.md`에 credential lifecycle endpoint, one-time display, revocation/rotation, membership fail-closed, smoke/admin separation을 반영했다.
- Story 9.2 Stage 1과 Stage 2 범위의 remaining AC는 없다. Open question은 abuse guard(rate limit/CAPTCHA/allow-list)와 product-facing credential 용어 최종 결정이 후속 product/security decision으로 남아 있다.

### File List

- Planning artifact: `planning-artifacts/stories/9-2-project-registration-and-seed-issuance-ui.md`
- `observability-portal/src/main/resources/db/migration/V012__add_project_starter_credential_status.sql`
- `observability-portal/src/main/java/com/observation/portal/domain/account/service/AccountProjectMembershipService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/controller/ProjectRegistrationController.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/dto/ProjectRegistrationErrorResponse.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/dto/ProjectRegistrationRequest.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/dto/ProjectRegistrationResponse.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/entity/ProjectEntity.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/model/GeneratedStarterCredential.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/model/ProjectKeyCandidate.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/model/ProjectRegistrationCommand.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/model/ProjectRegistrationResult.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/model/StarterCredentialDisplay.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/model/StarterCredentialStatus.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/service/ProjectRegistrationException.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/service/ProjectRegistrationService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/service/StarterCredentialGenerator.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/ProjectKeyVerificationService.java`
- `observability-portal/src/test/java/com/observation/portal/domain/account/service/AccountProjectMembershipServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/controller/ProjectRegistrationControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/repository/CatalogSchemaMigrationIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/repository/ProjectRepositoryIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/service/ProjectRegistrationServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/service/StarterCredentialGeneratorTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/service/ProjectKeyVerificationServiceTest.java`
- `planning-artifacts/api-surface.md`
- `planning-artifacts/database-schema.md`
- `planning-artifacts/stories/9-2-project-registration-and-seed-issuance-ui.md`
- `observability-portal/src/main/java/com/observation/portal/domain/account/controller/BearerResourceApiWebConfig.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/controller/StarterCredentialController.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/dto/StarterCredentialMetadataResponse.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/dto/StarterCredentialRotationResponse.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/model/StarterCredentialMetadata.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/model/StarterCredentialRotationResult.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/service/StarterCredentialLifecycleException.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/service/StarterCredentialService.java`
- `observability-portal/src/main/resources/static/dashboard/index.html`
- `observability-portal/src/main/resources/static/dashboard/app.js`
- `observability-portal/src/main/resources/static/dashboard/styles.css`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/controller/StarterCredentialControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/service/StarterCredentialServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/controller/IngestControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/controller/IngestHeartbeatControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ProjectEntryUiContractTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ProjectSelectionUiContractTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/account/controller/AuthSecretExposureGuardTest.java`
- `planning-artifacts/contracts/account-auth-policy.md`

### Change Log

- 2026-06-01: Story 9.2 strict BMAD planning story file을 `ready-for-dev` 상태로 생성했다. 구현은 시작하지 않았고 `sprint-status.yaml`은 변경하지 않았다.
- 2026-06-01: Story 9.2 Stage 1 backend/API/schema/credential core를 구현하고 story status를 `in-progress`로 갱신했다. Story 9.2 전체 완료와 sprint-status 변경은 수행하지 않았다.
- 2026-06-01: Story 9.2 Stage 2 UI/integration/lifecycle hardening/docs를 구현하고 story status를 `review`로 갱신했다. `sprint-status.yaml`은 수정하지 않았다.
