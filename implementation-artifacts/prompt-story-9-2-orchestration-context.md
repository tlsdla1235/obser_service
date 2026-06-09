---
artifactType: orchestration-context
projectName: Spring Boot 운영 첫 화면 포털
storyId: "9.2"
storyKey: "9-2-project-registration-and-seed-issuance-ui"
date: 2026-06-01
purpose: Two-stage BMAD dev-story orchestration prompt context
---

# Story 9.2 - 2단계 Dev Story Orchestration Context

이 문서는 Story 9.2를 한 번에 구현하지 않기 위한 실행 컨텍스트다. 아래 Stage 1, Stage 2 프롬프트를 각각 새 컨텍스트에 그대로 붙여 넣어 dev-story 실행을 시작한다.

현재 문서 생성 작업은 구현이 아니다. Production/test code, `planning-artifacts/stories/9-2-project-registration-and-seed-issuance-ui.md`, `implementation-artifacts/sprint-status.yaml`은 이 문서 작성 범위 밖이다.

## Story 9.2 전체 목표

GitHub OAuth로 로그인한 portal 사용자가 public onboarding UI에서 project를 등록하고, starter 연결에 필요한 starter credential을 생성 시 1회 표시로 발급받게 한다. 등록 성공 시 active project와 현재 account의 active account-project membership이 함께 만들어져야 하며, raw starter credential은 DB, migration, log, exception, fixture, read model, dashboard, history/snapshot response에 저장되거나 새면 안 된다.

핵심 경계:

- Resource API 인증은 `Authorization: Bearer <access_token>` service token 기준이다.
- Starter ingest 인증은 `X-OBS-Project-Key` 성격의 starter credential 기준이다.
- 두 인증 경계는 이름, response field, UI copy, 문서에서 섞지 않는다.
- Raw starter credential은 create/rotate 성공 response와 해당 UI의 1회 표시 상태에서만 허용한다.
- Public registration API는 Story 7 local/operator smoke seed와 분리한다.

## Non-goals

- Story 9.1 product introduction 구현 또는 수정
- email/password, GitHub 외 provider, anonymous flow
- owner/admin/viewer role, invite/team/org/admin, billing/tenant model
- project 삭제 workflow
- multi-credential 동시 활성화, rotation grace period
- credential audit/export/download
- browser localStorage/sessionStorage/cookie/URL token persistence
- GitHub provider token 저장 또는 resource API authorization 사용
- starter heartbeat/bucket ingest contract 의미 변경
- dashboard state/rule/p95/p99/endpoint priority 계산 변경
- raw metric explorer, arbitrary query UI
- local-only smoke seed runner 변경 또는 제거
- `implementation-artifacts/sprint-status.yaml` 수정
- 범위 밖 untracked Epic 7 문서, runtime docs, Story 9.1 구현 결과 수정

## Source of Truth 우선순위

대상 story file은 실행 컨텍스트로 읽되, 구현 중 source 문서끼리 충돌처럼 보이는 지점은 Story 9.2에 적힌 아래 순서를 따른다.

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

## 2단계 구현 분해

### Stage 1 - Backend/API/schema/credential core

목표는 authenticated public registration API와 starter credential 저장/검증의 backend core를 먼저 닫는 것이다.

Stage 1은 rotation/revocation backend endpoint를 포함하지 않는다. 이유는 registration, schema, membership write, raw secret one-time response, no-store header, ProjectKey verification active credential guard만으로도 backend blast radius가 크기 때문이다. Stage 1은 credential status/timestamp schema와 verification guard를 준비해 Stage 2의 rotation/revocation backend가 얹힐 수 있게 만든다.

Stage 1이 닫는 AC:

- AC 2~19
- AC 21~25
- AC 39~43
- AC 50
- AC 56~57
- AC 59
- AC 60 중 Stage 1 focused verification과 `git diff --check`

Stage 1이 준비만 하고 Stage 2에서 최종 closure할 AC:

- AC 20: revoked credential ingest/heartbeat `401`는 credential status guard 수준까지 준비하고, 실제 revoke path와 end-to-end rejection은 Stage 2에서 닫는다.
- AC 31~38, 51~54: rotation/revocation backend/API는 Stage 2에서 구현한다.
- AC 48~49: registration 관련 backend secret exposure guard는 Stage 1에서 추가할 수 있지만 dashboard/static/docs 전체 guard는 Stage 2에서 최종 확장한다.
- AC 58: API/schema 문서는 Stage 1에서 backend shape 최소 갱신 가능, account-auth/UI/lifecycle 문서 정렬은 Stage 2에서 최종 정리한다.

범위:

- `POST /api/projects` public registration API
- Bearer service access token 필수 인증, no-token/invalid-token/expired-token `401` + `WWW-Authenticate: Bearer`
- project name 같은 non-secret request만 허용
- client-supplied account id, role/status, prefix/hash/raw key 거부
- project name normalization/validation/uniqueness fail-closed
- active project 생성
- 현재 account와 project의 active `member` membership 생성
- starter credential raw value 생성: `<key_prefix>.<secret>` shape
- BCrypt 72 byte 입력 한계와 `key_prefix <= 32` guard
- DB에는 BCrypt hash, prefix, credential status/timestamp 같은 non-secret metadata만 저장
- raw credential은 registration success response의 `starterCredential.displayValue`로만 1회 반환
- secret-bearing response에 `Cache-Control: no-store`
- `GET /api/projects` 등 일반 read API에는 raw value/hash 미반환
- ProjectKey verification은 active project와 active starter credential만 허용하도록 변경
- Smoke/admin seed와 public registration API 분리
- backend secret exposure guard

범위 밖:

- rotation/revocation endpoint와 service
- registration UI, copy UX, 다시 볼 수 없음 confirmation
- dashboard/static UI regression 전체 확장
- full docs alignment
- full regression suite
- Story 9.2 status 완료 처리

완료 기준:

- Stage 1 AC에 해당하는 tests가 통과한다.
- raw credential은 persistence, log, exception, repository lookup, read response에 남지 않는다.
- `POST /api/projects` 성공 response만 one-time raw display field를 포함하고 no-store header를 가진다.
- ProjectKey verification이 project status와 starter credential status를 모두 확인한다.
- public API가 `SmokeProjectSeedService`, `portal.smoke.seed.*`, `.private/smoke-*`에 의존하지 않는다.
- Stage 2가 rotation/revocation backend와 UI를 이어서 구현할 수 있는 handoff note를 남긴다.
- `git diff --check`가 통과한다.

수정 가능성이 높은 파일 후보:

- `observability-portal/src/main/resources/db/migration/V012__add_project_starter_credential_status.sql`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/entity/ProjectEntity.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/model/ProjectKeyCandidate.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/model/ProjectStatus.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/model/StarterCredentialStatus.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ProjectRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/controller/ProjectRegistrationController.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/dto/*`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/service/ProjectRegistrationService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/service/StarterCredentialGenerator.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/service/AccountProjectMembershipService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/repository/AccountProjectMembershipRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/ProjectKeyVerificationService.java`
- `observability-portal/src/main/java/com/observation/portal/security/ProjectKeyHashVerifier.java`
- `planning-artifacts/api-surface.md`
- `planning-artifacts/database-schema.md`

Focused tests 후보:

- `observability-portal/src/test/java/com/observation/portal/domain/catalog/controller/ProjectRegistrationControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/service/ProjectRegistrationServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/service/StarterCredentialServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/repository/ProjectRepositoryIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/service/ProjectKeyVerificationServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/controller/IngestControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/controller/IngestHeartbeatControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/account/controller/AuthSecretExposureGuardTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/controller/ProjectNavigationResourceAuthorizationTest.java`
- `observability-portal/src/test/java/com/observation/portal/architecture/MvcLayerBoundaryTest.java`

필수 command 후보:

```bash
./gradlew :observability-portal:test --tests '*ProjectRegistration*'
./gradlew :observability-portal:test --tests '*StarterCredential*'
./gradlew :observability-portal:test --tests com.observation.portal.domain.ingest.service.ProjectKeyVerificationServiceTest
./gradlew :observability-portal:test --tests com.observation.portal.domain.ingest.controller.IngestControllerTest
./gradlew :observability-portal:test --tests com.observation.portal.domain.ingest.controller.IngestHeartbeatControllerTest
./gradlew :observability-portal:test --tests com.observation.portal.domain.account.controller.AuthSecretExposureGuardTest
./gradlew :observability-portal:test --tests com.observation.portal.domain.catalog.controller.ProjectNavigationResourceAuthorizationTest
./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest
git diff --check
```

### Stage 2 - UI/integration/lifecycle hardening/docs

목표는 Stage 1 backend core 위에 onboarding UI와 credential lifecycle을 완성하고, secret exposure regression과 문서 정렬, full verification까지 닫는 것이다.

Stage 2는 rotation/revocation backend endpoint를 포함한다. Stage 1에서 credential status/timestamp와 active verification guard를 준비했다는 전제 위에서, Stage 2가 lifecycle API, UI integration, regression guard를 한 번에 맞춰 닫는다.

Stage 2가 닫는 AC:

- AC 20
- AC 26~38
- AC 44~49
- AC 51~55
- AC 58
- AC 60
- Stage 1에서 부분 처리된 AC 48~49, 58의 최종 closure

범위:

- Project Entry empty state 또는 onboarding panel에서 registration flow 진입
- project name submit/loading/error/success UI
- registration success 후 project list server refresh
- one-time raw credential display
- copy UX, clipboard success/failure state
- "다시 볼 수 없음" copy와 "복사했음" confirmation
- raw credential을 localStorage/sessionStorage/cookie/URL/DOM dataset/hidden input/global long-lived state에 저장하지 않는 guard
- credential metadata read가 필요하면 prefix/status/issuedAt/rotatedAt/revokedAt만 반환
- rotation backend/API: active membership account만 호출, old credential 즉시 폐기, new raw display response, no-store
- revocation backend/API: active membership account만 호출, raw 미반환, metadata만 반환
- membership mismatch는 credential metadata/rotation/revocation에서 resource lookup 전에 `404` fail-closed
- revoked/rotated old credential ingest/heartbeat `401`
- dashboard/read model/snapshot/history에 raw secret/hash/token이 새지 않는 regression guard
- API/schema/account-auth 문서 정렬
- full portal regression과 `git diff --check`

범위 밖:

- Story 9.1 재구현
- starter ingest/heartbeat payload semantics 변경
- full frontend build stack 도입
- owner/admin/viewer role, team/org/invite
- rotation grace period 또는 multi active credential
- raw metric explorer
- sprint-status 수정

완료 기준:

- Registration UI와 lifecycle UI가 backend API와 통합된다.
- Raw credential은 create/rotate success state에서만 표시되고 reload/navigation 이후 복원되지 않는다.
- UI contract tests가 browser persistence, DOM dataset, hidden input, long-lived attribute/state 저장 금지를 검증한다.
- Rotation 후 old credential은 ingest/heartbeat에서 실패하고 new credential만 통과한다.
- Revocation 후 project는 project list에 남을 수 있지만 ingest/heartbeat credential verification은 실패한다.
- Membership 없는 account는 credential metadata/rotation/revocation에서 body 없는 `404` 또는 정보 노출 없는 `404`로 닫힌다.
- Docs가 public registration/credential lifecycle, one-time display, raw secret storage prohibition, smoke/admin separation을 반영한다.
- `./gradlew :observability-portal:test`와 `git diff --check`가 통과한다.
- Story 9.2 전체 completion 여부는 Stage 1 결과와 Stage 2 결과를 함께 보고 판단한다.

수정 가능성이 높은 파일 후보:

- `observability-portal/src/main/resources/static/dashboard/index.html`
- `observability-portal/src/main/resources/static/dashboard/app.js`
- `observability-portal/src/main/resources/static/dashboard/styles.css`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/controller/ProjectRegistrationController.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/controller/StarterCredentialController.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/dto/*`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/service/StarterCredentialService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/controller/BearerResourceApiWebConfig.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/controller/AccountProjectMembershipResourceApiInterceptor.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/ProjectKeyVerificationService.java`
- `planning-artifacts/api-surface.md`
- `planning-artifacts/database-schema.md`
- `planning-artifacts/contracts/account-auth-policy.md`
- `planning-artifacts/epic-9-product-onboarding-and-project-seed-issuance-ui.md`

Focused tests 후보:

- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ProjectEntryUiContractTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ProjectSelectionUiContractTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/controller/ProjectRegistrationControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/controller/StarterCredentialControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/service/StarterCredentialServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/service/ProjectKeyVerificationServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/controller/IngestControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/controller/IngestHeartbeatControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/controller/ProjectNavigationResourceAuthorizationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/account/controller/AuthSecretExposureGuardTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/controller/DashboardControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/controller/DashboardSnapshotControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/history/controller/OperationalEventHistoryControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/architecture/MvcLayerBoundaryTest.java`

필수 command 후보:

```bash
./gradlew :observability-portal:test --tests com.observation.portal.domain.dashboard.ProjectEntryUiContractTest
./gradlew :observability-portal:test --tests com.observation.portal.domain.dashboard.ProjectSelectionUiContractTest
./gradlew :observability-portal:test --tests '*ProjectRegistration*'
./gradlew :observability-portal:test --tests '*StarterCredential*'
./gradlew :observability-portal:test --tests com.observation.portal.domain.ingest.service.ProjectKeyVerificationServiceTest
./gradlew :observability-portal:test --tests com.observation.portal.domain.ingest.controller.IngestControllerTest
./gradlew :observability-portal:test --tests com.observation.portal.domain.ingest.controller.IngestHeartbeatControllerTest
./gradlew :observability-portal:test --tests com.observation.portal.domain.catalog.controller.ProjectNavigationResourceAuthorizationTest
./gradlew :observability-portal:test --tests com.observation.portal.domain.account.controller.AuthSecretExposureGuardTest
./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest
./gradlew :observability-portal:test
git diff --check
```

## Handoff 조건

Stage 1에서 Stage 2로 넘어갈 수 있는 조건:

- `POST /api/projects`가 authenticated account 기준으로 active project와 active membership을 만든다.
- registration response가 `starterCredential.displayValue`를 1회 표시 field로 반환하고 no-store header를 가진다.
- DB에는 raw credential이 없고 BCrypt hash/prefix/metadata만 있다.
- ProjectKey verification이 active project와 active credential만 통과시킨다.
- 일반 project read response에는 raw value/hash가 없다.
- Smoke/admin seed와 public API가 분리되어 있다.
- Stage 1 focused tests와 `git diff --check`가 통과하거나, 못 돌린 command와 이유가 명확히 기록된다.

Stage 2 완료 조건:

- Stage 1 완료 조건이 유지된다.
- UI registration, one-time display, copy confirmation, rotation/revocation flow가 동작한다.
- rotation/revocation backend와 UI integration이 membership guard와 no-store/raw-secret guard를 통과한다.
- full portal regression과 `git diff --check`가 통과하거나, 못 돌린 command와 이유가 명확히 기록된다.
- Story 9.2 전체 AC 중 남은 항목과 open question이 명시된다.

## Stage 1 Dev Story Prompt

```text
BMAD dev story로 Story 9.2의 Stage 1만 구현해줘. Story 9.2 전체를 한 번에 구현하지 마.

작업 디렉터리:
/Users/tlsdla1235/Desktop/study/observation

현재 브랜치:
codex/onboarding-seed-epic

Stage context:
/Users/tlsdla1235/Desktop/study/observation/implementation-artifacts/prompt-story-9-2-orchestration-context.md

Story file:
/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/9-2-project-registration-and-seed-issuance-ui.md

Stage 1 목표:
Backend/API/schema/credential core만 구현한다. Authenticated public project registration API, project 생성, active account-project membership 생성, starter credential 생성/저장 guard, registration one-time raw display response, no-store header, ProjectKey verification active credential guard, smoke/admin seed와 public API 분리를 닫는다.

이 Stage가 닫아야 하는 AC:
- AC 2~19
- AC 21~25
- AC 39~43
- AC 50
- AC 56~57
- AC 59
- AC 60 중 Stage 1 focused verification과 git diff --check

이 Stage에서 준비만 하고 Stage 2에 넘길 AC:
- AC 20은 credential status guard와 verification 설계까지만 준비한다. 실제 revoke path와 revoked ingest/heartbeat end-to-end는 Stage 2에서 닫는다.
- AC 31~38, 51~54 rotation/revocation backend/API는 Stage 2에서 구현한다.
- AC 26~30, 44~49, 55 UI와 broad regression guard는 Stage 2에서 구현한다.
- AC 58 docs는 필요한 backend API/schema 최소 갱신만 허용하고 최종 account-auth/UI/lifecycle 문서 정렬은 Stage 2로 남긴다.

반드시 먼저 읽을 문서:
1. /Users/tlsdla1235/Desktop/study/observation/implementation-artifacts/prompt-story-9-2-orchestration-context.md
2. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/epic-9-product-onboarding-and-project-seed-issuance-ui.md
3. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/9-2-project-registration-and-seed-issuance-ui.md
4. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/contracts/account-auth-policy.md
5. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/api-surface.md
6. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/source-of-truth/current-product-source-of-truth.md
7. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/6-10-account-project-membership-and-scoped-project-entry.md
8. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/7-1-real-github-oauth-smoke-seed-and-operator-runbook.md
9. /Users/tlsdla1235/Desktop/study/observation/implementation-artifacts/real-github-oauth-smoke-runbook.md
10. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/database-schema.md
11. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/architecture.md
12. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/architecture-implementation-supplement.md
13. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/project-structure.md
14. /Users/tlsdla1235/Desktop/study/observation/_bmad/custom/project-context.md
15. /Users/tlsdla1235/Desktop/study/observation/AGENTS.md

반드시 확인할 현재 코드:
1. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/resources/db/migration/V001__create_projects.sql
2. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/resources/db/migration/V011__create_account_project_memberships.sql
3. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/java/com/observation/portal/domain/account/controller/BearerResourceApiInterceptor.java
4. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/java/com/observation/portal/domain/account/controller/BearerResourceApiWebConfig.java
5. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/java/com/observation/portal/domain/account/service/AccountProjectMembershipService.java
6. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/java/com/observation/portal/domain/account/repository/AccountProjectMembershipRepository.java
7. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/java/com/observation/portal/domain/account/repository/AccountProjectMembershipEntity.java
8. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/java/com/observation/portal/domain/catalog/entity/ProjectEntity.java
9. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/java/com/observation/portal/domain/catalog/model/ProjectKeyCandidate.java
10. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/java/com/observation/portal/domain/catalog/model/ProjectStatus.java
11. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ProjectRepository.java
12. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/java/com/observation/portal/domain/ingest/service/ProjectKeyVerificationService.java
13. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/java/com/observation/portal/security/ProjectKeyHashVerifier.java
14. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/java/com/observation/portal/domain/admin/service/SmokeProjectSeedService.java
15. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/test/java/com/observation/portal/domain/ingest/service/ProjectKeyVerificationServiceTest.java
16. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/test/java/com/observation/portal/domain/account/controller/AuthSecretExposureGuardTest.java
17. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/test/java/com/observation/portal/domain/catalog/controller/ProjectNavigationResourceAuthorizationTest.java
18. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/test/java/com/observation/portal/architecture/MvcLayerBoundaryTest.java

닫힌 결정:
- Stage 1은 rotation/revocation backend endpoint를 만들지 않는다.
- MVP schema는 기본적으로 기존 projects.key_prefix/project_key_hash 단일 credential model을 유지하고 credential status/issuedAt/rotatedAt/revokedAt metadata를 추가한다.
- 별도 credential table은 기존 model로 도저히 AC를 만족할 수 없을 때만 선택한다. 그 경우 rationale, migration path, verification changes를 명확히 남긴다.
- Credential status는 projects.status와 섞지 않는다.
- ProjectKey verification은 active project와 active starter credential을 모두 통과해야 ingest/heartbeat를 허용한다.
- Raw starter credential은 registration success response의 starterCredential.displayValue에만 담고, DB/log/exception/repository/read response/test fixture에 남기지 않는다.
- response field 이름은 account accessToken/refreshToken과 혼동되지 않게 starterCredential.displayValue 또는 동등한 이름을 사용한다.
- Secret-bearing response에는 Cache-Control: no-store를 둔다.
- Public registration API는 SmokeProjectSeedService, portal.smoke.seed.*, .private/smoke-* 파일을 호출하거나 요구하지 않는다.
- UI files는 Stage 1에서 수정하지 않는다.
- Story 9.1 구현 결과, 범위 밖 Epic 7 prompt/runtime docs, sprint-status.yaml은 건드리지 않는다.

권장 구현 방향:
- 새 public controller는 domain.catalog.controller 아래에 두고 HTTP mapping/status/header/DTO 변환만 담당하게 한다.
- registration orchestration은 domain.catalog.service의 ProjectRegistrationService 같은 service에 둔다.
- starter credential raw generation/hash/prefix guard는 별도 helper/service로 분리하되 raw value를 long-lived field로 남기지 않는다.
- Account id는 BearerResourceApiInterceptor.requiredAccountId(request) 또는 기존 request attribute boundary를 사용한다.
- active membership create는 AccountProjectMembershipService/repository boundary를 확장하되, access token 원문이나 provider token을 전달하지 않는다.
- ProjectRepository는 prefix/name uniqueness lookup과 save에만 사용하고 controller에서 직접 호출하지 않는다.
- DTO와 service result는 JPA entity를 직접 반환하지 않는다.
- 새 public class/method/helper와 이해하기 어려운 내부 helper에는 AGENTS.md 기준 한국어 Javadoc/comment를 작성한다.

Focused test requirements:
- authenticated account가 POST /api/projects로 project를 등록하면 active project, active member membership, active hashed starter credential이 생성된다.
- no-token/invalid-token/expired-token registration request는 401 + WWW-Authenticate: Bearer로 닫힌다.
- client가 account id, role/status, keyPrefix, hash, raw credential을 지정해도 반영되지 않는다.
- duplicate/invalid project name은 sanitized error로 fail-closed되고 잘못된 row write를 남기지 않는다.
- registration success response는 starterCredential.displayValue를 1회 표시 field로 반환하고 Cache-Control: no-store를 포함한다.
- registration failure response/log/error는 OAuth token, service access/refresh token, provider payload, raw starter credential, hash를 노출하지 않는다.
- raw starter credential은 BCrypt 72 byte 한계와 key_prefix 32자 한계를 지킨다.
- ProjectKey verification은 inactive credential status를 거부한다.
- GET /api/projects 등 일반 read API에는 raw credential/hash가 없다.
- public API가 SmokeProjectSeedService나 .private smoke config에 의존하지 않는다.
- MvcLayerBoundaryTest가 controller/service/repository 방향을 유지한다.

완료 전 실행할 command:
./gradlew :observability-portal:test --tests '*ProjectRegistration*'
./gradlew :observability-portal:test --tests '*StarterCredential*'
./gradlew :observability-portal:test --tests com.observation.portal.domain.ingest.service.ProjectKeyVerificationServiceTest
./gradlew :observability-portal:test --tests com.observation.portal.domain.ingest.controller.IngestControllerTest
./gradlew :observability-portal:test --tests com.observation.portal.domain.ingest.controller.IngestHeartbeatControllerTest
./gradlew :observability-portal:test --tests com.observation.portal.domain.account.controller.AuthSecretExposureGuardTest
./gradlew :observability-portal:test --tests com.observation.portal.domain.catalog.controller.ProjectNavigationResourceAuthorizationTest
./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest
git diff --check

테스트 패턴이 아직 존재하지 않아 실패하면, Stage 1 범위에 맞는 focused test를 추가한 뒤 다시 실행해.

Story/spec update 규칙:
- 이 실행은 Stage 1만 완료한다. Story 9.2 전체 Status를 done으로 바꾸지 마.
- BMAD dev-story 규칙상 planning story file을 갱신해야 한다면 Stage 1로 실제 완료된 Tasks/Subtasks와 Dev Agent Record/File List/Change Log만 갱신한다.
- sprint-status.yaml은 수정하지 마.

완료 보고에는 다음만 짧게 정리해줘:
- Stage 1에서 닫은 AC와 Stage 2로 넘긴 AC
- 구현/테스트/문서 파일 목록
- raw credential non-persistence와 one-time response/no-store를 어떻게 검증했는지
- ProjectKey verification active credential guard를 어떻게 검증했는지
- smoke/admin seed와 public API 분리를 어떻게 유지했는지
- 통과한 검증 command
- 못 돌린 검증이 있으면 이유
- Stage 2 handoff note
```

## Stage 2 Dev Story Prompt

```text
BMAD dev story로 Story 9.2의 Stage 2만 구현해줘. Stage 1 결과를 전제로 UI/integration/lifecycle hardening/docs를 닫고, Story 9.2 전체를 무리하게 재작성하지 마.

작업 디렉터리:
/Users/tlsdla1235/Desktop/study/observation

현재 브랜치:
codex/onboarding-seed-epic

Stage context:
/Users/tlsdla1235/Desktop/study/observation/implementation-artifacts/prompt-story-9-2-orchestration-context.md

Story file:
/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/9-2-project-registration-and-seed-issuance-ui.md

Stage 2 목표:
Stage 1 backend core 위에 Project Entry empty state/onboarding panel registration flow, one-time raw credential display, copy UX, 다시 볼 수 없음 confirmation, rotation/revocation backend와 UI integration, secret exposure regression guard, API/schema/account-auth docs alignment, full regression을 구현한다.

이 Stage가 닫아야 하는 AC:
- AC 20
- AC 26~38
- AC 44~49
- AC 51~55
- AC 58
- AC 60
- Stage 1에서 부분 처리된 AC 48~49, 58의 최종 closure

Stage 1 완료 조건을 먼저 확인해:
- POST /api/projects registration API가 존재하고 active project와 active membership을 만든다.
- registration response가 starterCredential.displayValue one-time field와 no-store header를 가진다.
- DB에는 raw credential이 없고 BCrypt hash/prefix/metadata만 있다.
- ProjectKey verification이 active project와 active credential만 통과시킨다.
- public API가 SmokeProjectSeedService, portal.smoke.seed.*, .private/smoke-*에 의존하지 않는다.

반드시 먼저 읽을 문서:
1. /Users/tlsdla1235/Desktop/study/observation/implementation-artifacts/prompt-story-9-2-orchestration-context.md
2. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/epic-9-product-onboarding-and-project-seed-issuance-ui.md
3. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/9-2-project-registration-and-seed-issuance-ui.md
4. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/contracts/account-auth-policy.md
5. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/api-surface.md
6. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/source-of-truth/current-product-source-of-truth.md
7. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/6-1-account-project-entry-and-setup-guide.md
8. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/6-10-account-project-membership-and-scoped-project-entry.md
9. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/7-1-real-github-oauth-smoke-seed-and-operator-runbook.md
10. /Users/tlsdla1235/Desktop/study/observation/implementation-artifacts/real-github-oauth-smoke-runbook.md
11. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/database-schema.md
12. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/architecture.md
13. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/architecture-implementation-supplement.md
14. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/project-structure.md
15. /Users/tlsdla1235/Desktop/study/observation/_bmad/custom/project-context.md
16. /Users/tlsdla1235/Desktop/study/observation/AGENTS.md

반드시 확인할 현재 코드:
1. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/resources/static/dashboard/index.html
2. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/resources/static/dashboard/app.js
3. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/resources/static/dashboard/styles.css
4. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/java/com/observation/portal/domain/catalog/controller/ProjectRegistrationController.java
5. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/java/com/observation/portal/domain/catalog/service/ProjectRegistrationService.java
6. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/java/com/observation/portal/domain/catalog/service/StarterCredentialService.java
7. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/java/com/observation/portal/domain/account/controller/BearerResourceApiWebConfig.java
8. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/java/com/observation/portal/domain/account/controller/AccountProjectMembershipResourceApiInterceptor.java
9. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/java/com/observation/portal/domain/account/service/AccountProjectMembershipService.java
10. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/java/com/observation/portal/domain/ingest/service/ProjectKeyVerificationService.java
11. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/test/java/com/observation/portal/domain/dashboard/ProjectEntryUiContractTest.java
12. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/test/java/com/observation/portal/domain/dashboard/ProjectSelectionUiContractTest.java
13. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/test/java/com/observation/portal/domain/account/controller/AuthSecretExposureGuardTest.java
14. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/test/java/com/observation/portal/domain/ingest/service/ProjectKeyVerificationServiceTest.java
15. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/test/java/com/observation/portal/domain/ingest/controller/IngestControllerTest.java
16. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/test/java/com/observation/portal/domain/ingest/controller/IngestHeartbeatControllerTest.java
17. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/test/java/com/observation/portal/domain/catalog/controller/ProjectNavigationResourceAuthorizationTest.java
18. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/test/java/com/observation/portal/architecture/MvcLayerBoundaryTest.java

닫힌 결정:
- Stage 2는 rotation/revocation backend endpoint를 포함한다.
- Rotation은 기존 starter credential을 즉시 폐기하고 새 raw starter credential을 1회 표시 response로만 반환한다. Grace period는 만들지 않는다.
- Revocation은 raw credential을 반환하지 않고 metadata만 반환한다.
- Credential metadata read가 필요하면 prefix/status/issuedAt/rotatedAt/revokedAt만 반환한다.
- Membership 없는 account가 credential metadata/rotation/revocation project-scoped API를 호출하면 resource lookup 전에 404 fail-closed 한다.
- UI는 raw credential을 localStorage, sessionStorage, cookie, URL fragment/query, DOM dataset, hidden input, long-lived global state에 저장하지 않는다.
- Copy UX는 raw credential 값을 log, error, analytics payload, aria-label, title attribute에 넣지 않는다.
- Registration 성공 후 dashboard shortcut을 만들지 않고 GET /api/projects refresh와 server-provided links를 따른다.
- Public UI/API는 Story 7 smoke seed runner, .private files, portal.smoke.seed.* 설정을 사용하지 않는다.
- Story 9.1 결과, 범위 밖 Epic 7 prompt/runtime docs, sprint-status.yaml은 건드리지 않는다.

UI 구현 방향:
- 기존 static dashboard stack(index.html/app.js/styles.css)을 유지한다. React/Vite/TypeScript/frontend build stack을 도입하지 않는다.
- Project Entry empty state 또는 onboarding panel에서 registration flow로 진입한다.
- Project name input, submit/loading/error/success state를 구현한다.
- Success state는 raw starter credential을 1회만 표시하고 copy action과 복사 확인 action을 제공한다.
- reload/navigation 이후 raw credential을 복원하지 않는다.
- Starter setup guide copy는 X-OBS-Project-Key/starter project key 성격과 portal resource API Bearer token을 분리해 설명한다.
- UI는 server response를 기준으로 project list를 refresh하고 새 project가 membership project list에 나타나는지 확인한다.

Backend lifecycle 구현 방향:
- POST /api/projects/{projectId}/starter-credential/rotations 또는 Stage 1/docs에서 고정한 동등 endpoint를 구현한다.
- POST /api/projects/{projectId}/starter-credential/revocations 또는 Stage 1/docs에서 고정한 동등 endpoint를 구현한다.
- credential metadata endpoint를 열었다면 raw/hash 없이 metadata만 반환한다.
- credential project-scoped API는 active Bearer account + active membership을 먼저 확인한다.
- no-token/invalid-token은 401 + WWW-Authenticate: Bearer, membership mismatch는 정보 노출 없는 404다.
- secret-bearing rotation response에는 Cache-Control: no-store를 둔다.
- 새 public class/method/helper와 이해하기 어려운 내부 helper에는 AGENTS.md 기준 한국어 Javadoc/comment를 작성한다.

Focused test requirements:
- UI는 registration flow 진입, project name submit/loading/error/success state, one-time display, copy success/failure, 복사 확인 action을 검증한다.
- UI는 raw credential을 localStorage/sessionStorage/cookie/URL/DOM dataset/hidden input/long-lived global state/title/aria-label에 저장하지 않음을 검증한다.
- registration 성공 후 GET /api/projects refresh와 server-provided links 사용을 검증한다.
- rotation success는 old credential 즉시 invalidation, new one-time raw display response, no-store header를 검증한다.
- revocation success는 raw value를 반환하지 않고 metadata만 반환하며 이후 ingest/heartbeat가 401을 반환함을 검증한다.
- membership mismatch는 credential metadata/rotation/revocation에서 resource service lookup 전에 404 fail-closed 됨을 검증한다.
- Dashboard/read model/snapshot/history serialization에는 raw credential, hash, access token, refresh token, provider token이 포함되지 않음을 검증한다.
- AuthSecretExposureGuardTest는 registration/rotation response를 제외한 source, fixture, runbook, static UI snapshot에 forbidden credential snippets가 없음을 검증한다.
- MvcLayerBoundaryTest가 새 controller/service/repository 방향을 유지한다.

완료 전 실행할 command:
./gradlew :observability-portal:test --tests com.observation.portal.domain.dashboard.ProjectEntryUiContractTest
./gradlew :observability-portal:test --tests com.observation.portal.domain.dashboard.ProjectSelectionUiContractTest
./gradlew :observability-portal:test --tests '*ProjectRegistration*'
./gradlew :observability-portal:test --tests '*StarterCredential*'
./gradlew :observability-portal:test --tests com.observation.portal.domain.ingest.service.ProjectKeyVerificationServiceTest
./gradlew :observability-portal:test --tests com.observation.portal.domain.ingest.controller.IngestControllerTest
./gradlew :observability-portal:test --tests com.observation.portal.domain.ingest.controller.IngestHeartbeatControllerTest
./gradlew :observability-portal:test --tests com.observation.portal.domain.catalog.controller.ProjectNavigationResourceAuthorizationTest
./gradlew :observability-portal:test --tests com.observation.portal.domain.account.controller.AuthSecretExposureGuardTest
./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest
./gradlew :observability-portal:test
git diff --check

테스트 패턴이 아직 존재하지 않아 실패하면, Stage 2 범위에 맞는 focused test를 추가한 뒤 다시 실행해.

Story/spec update 규칙:
- 이 실행은 Stage 2다. Stage 1 산출물을 되돌리지 마.
- BMAD dev-story 규칙상 planning story file을 갱신해야 한다면 실제 완료된 Tasks/Subtasks와 Dev Agent Record/File List/Change Log만 갱신한다.
- Stage 1과 Stage 2의 AC가 모두 통과했을 때만 Story 9.2 전체 Status 완료 여부를 판단한다.
- sprint-status.yaml은 수정하지 마.

완료 보고에는 다음만 짧게 정리해줘:
- Stage 2에서 닫은 AC와 Story 9.2에 남은 AC/open question
- 구현/테스트/문서 파일 목록
- one-time display와 browser persistence 금지를 어떻게 검증했는지
- rotation/revocation lifecycle과 revoked/rotated old credential rejection을 어떻게 검증했는지
- dashboard/read model/snapshot/history secret leakage guard를 어떻게 검증했는지
- API/schema/account-auth docs 정렬 내용
- 통과한 검증 command
- 못 돌린 검증이 있으면 이유
```

## Open Questions

1. Public project registration abuse guard(rate limit, CAPTCHA, organization allow-list)를 MVP에 포함할지 별도 security/backoffice story로 분리할지 결정이 필요하다.
2. Product-facing 용어를 `starter credential`, `project key`, `ingest credential` 중 무엇으로 고정할지 최종 copy decision이 필요하다. 구현 boundary에서는 account `accessToken`/`refreshToken`과 혼동되지 않는 이름을 우선한다.
3. Stage 2에서 credential metadata read endpoint를 별도로 열지, rotation/revocation response와 project list adjunct metadata만으로 충분한지 결정이 필요하다.
