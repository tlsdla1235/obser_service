---
artifactType: story
storyId: "3.1"
epic: "Epic 3. Portal Ingest Acceptance"
title: "Project Key Verification Service"
architectureStyle: Traditional MVC
status: done
date: 2026-05-18
---

# Story 3.1 - Project Key Verification Service

## User Story

구현자로서, portal ingest API가 `X-OBS-Project-Key` header를 active project로 검증한 뒤에만 ingest validation과 저장을 진행하길 원한다.

## Scope

이 story는 portal ingest의 project authorization boundary를 닫는다.

포함:

- `ProjectKeyVerificationService`
- project key prefix extraction
- `projects.key_prefix` lookup
- BCrypt `project_key_hash` verification
- active/disabled project status handling
- raw project key 비저장/비반환/비로그/repository lookup surface guard
- service unit test와 repository lookup test

제외:

- public project onboarding API
- application/application instance catalog upsert
- ingest payload validation
- `accepted_metric_buckets` migration/repository
- duplicate/idempotency handling
- dashboard snapshot/read model
- lifecycle state, p95, insight rule, endpoint priority 계산

## Source Artifacts

- `implementation-artifacts/sprint-status.yaml`
- `implementation-artifacts/epic-2-retro-2026-05-18.md`
- `planning-artifacts/sprint-plan.md`
- `planning-artifacts/epics.md`
- `planning-artifacts/architecture.md`
- `planning-artifacts/architecture-implementation-supplement.md`
- `planning-artifacts/api-surface.md`
- `planning-artifacts/database-schema.md`
- `planning-artifacts/contracts/account-auth-policy.md`

## Dependencies

- Epic 1 `projects` schema foundation.
- Epic 2 closure status.
- Story 3.2 consumes the verified project context.

## Implementation Notes

- Project key verification belongs in portal service/security boundary, not controller logic.
- 이 story의 인증은 starter ingest용 project key 검증이다. 사용자 account signup/login은 별도 account auth story에서 GitHub OAuth only 기준으로 다룬다.
- Controller는 header string을 service에 전달하고, service 결과를 기준으로 `401` mapping만 수행한다.
- `key_prefix`는 project 후보 조회용이다. raw project key 원문은 DB와 repository lookup surface에 남기지 않는다.
- `project_key_hash` 검증은 BCrypt를 사용한다. 필요한 경우 최소 crypto dependency를 추가하되 product auth surface를 넓히지 않는다.
- disabled project는 missing/invalid key와 같은 unauthorized result로 처리한다.
- service result는 project UUID, project name/status 같은 내부 식별 정보만 포함하고 raw key를 포함하지 않는다.
- test fixture raw key는 테스트 코드 안에서만 사용하고 migration에 평문 secret을 고정하지 않는다.
- Correct-course 이후 repository 구현 표준은 Spring Data JPA/Jakarta Persistence + Hibernate다. Service는 Traditional MVC 기준에서 Spring Data repository와 JPA entity를 직접 사용할 수 있다.
- Flyway SQL migration이 schema source of truth다. Hibernate DDL auto 생성/갱신은 사용하지 않는다.
- JPA entity는 persistence model이며 `VerifiedProject`, service result/external return model, public API surface, controller response DTO로 노출하지 않는다.

## Acceptance Criteria

1. missing or blank `X-OBS-Project-Key` is rejected.
2. unknown prefix or missing project candidate is rejected.
3. BCrypt hash mismatch is rejected.
4. `status != active` project is rejected.
5. active project with matching hash returns a verified project context.
6. raw project key is not persisted, returned in result objects, logged by the service, or passed through as repository lookup material.
7. controller calls service instead of repository, and repository remains independent from controller/dto boundary types.
8. this story does not validate payload schema or write accepted metric buckets.

## Suggested Tasks

1. `projects` schema와 existing catalog package를 확인한다.
2. project key verification result model을 추가한다.
3. `ProjectRepository` lookup method를 추가하거나 구현한다.
4. `ProjectKeyVerificationService`를 추가한다.
5. BCrypt verification dependency가 필요한지 확인하고 최소 범위로 추가한다.
6. missing/unknown/mismatch/disabled/active test를 추가한다.
7. raw key가 result/log/persistence/repository lookup surface로 새지 않는 guard를 추가한다.
8. portal architecture test를 실행한다.

## Test Requirements

- `ProjectKeyVerificationServiceTest`
- `ProjectRepository` lookup test 또는 focused repository integration test
- disabled project unauthorized test
- raw key 비노출 및 repository lookup surface guard test
- `MvcLayerBoundaryTest`
- 권장 실행 명령: `./gradlew :observability-portal:test`

## Developer Guardrails

- raw project key를 migration, DB row, log, exception message, response body, repository lookup surface에 남기지 않는다.
- project key verification을 dashboard나 admin product API로 확장하지 않는다.
- 사용자 account signup/login, email/password, magic link, GitHub 외 provider, anonymous flow를 이 story에 섞지 않는다.
- 사용자 account signup/login을 문서에서 언급해야 한다면 GitHub OAuth only, Bearer access token/JWT, refresh token rotation/token store 기준을 따른다.
- application catalog 생성과 bucket persistence를 이 story에 섞지 않는다.
- Epic 2 starter configuration/key generation을 되돌리거나 재설계하지 않는다.
- lifecycle state, p95, insight rule, endpoint priority 계산을 추가하지 않는다.

## Tasks/Subtasks

- [x] `projects` schema와 catalog package를 확인한다.
- [x] project key verification result model을 추가한다.
- [x] project lookup repository method를 추가한다.
- [x] `ProjectKeyVerificationService`를 구현한다.
- [x] BCrypt verification dependency와 configuration 범위를 확인한다.
- [x] missing/unknown/mismatch/disabled/active tests를 추가한다.
- [x] raw key 비노출 guard와 repository lookup surface guard를 추가한다.
- [x] `./gradlew :observability-portal:test`를 실행한다.
- [x] [Correct Course Follow-up] JDBC 기반 `ProjectRepository` 구현을 Spring Data JPA/Jakarta Persistence + Hibernate 기반 repository로 교체한다.

## Dev Agent Record

### Implementation Plan

- active MVC 문서와 `projects` migration을 기준으로 `catalog.model`, `catalog.repository`, `security`, `ingest.service`에만 Story 3.1 구현을 둔다.
- RED 단계에서 service/repository lookup 테스트를 먼저 추가하고, GREEN 단계에서 `ProjectKeyVerificationService`, verified result model, Spring Data JPA repository, BCrypt verifier를 최소 구현한다.
- raw project key는 service stack 안에서만 BCrypt 검증에 사용하고 repository lookup에는 `<key_prefix>.<secret>` 형식에서 추출한 non-secret prefix만 전달한다.
- Story 3.2 이후 payload validation/bucket persistence가 붙을 수 있도록 성공 결과는 `VerifiedProject` context로만 반환한다.

### Debug Log

- 2026-05-19T09:05:57+0900: `implementation-artifacts/sprint-status.yaml`에서 Story 3.1 상태를 `in-progress`로 전환했다.
- `./gradlew :observability-portal:test --tests com.observation.portal.domain.ingest.service.ProjectKeyVerificationServiceTest --tests com.observation.portal.domain.catalog.repository.ProjectRepositoryIntegrationTest`를 RED 확인용으로 실행했고, 구현 클래스 부재로 compile 실패를 확인했다.
- `./gradlew :observability-portal:test --tests com.observation.portal.domain.ingest.service.ProjectKeyVerificationServiceTest --tests com.observation.portal.domain.catalog.repository.ProjectRepositoryIntegrationTest` 실행 중 Docker daemon 미기동으로 Testcontainers 초기화 실패를 확인했다.
- `docker info`에서 `/Users/tlsdla1235/.docker/run/docker.sock` 연결 실패를 확인한 뒤 `open -a Docker`로 Docker Desktop을 시작했다.
- `./gradlew :observability-portal:test --tests com.observation.portal.domain.ingest.service.ProjectKeyVerificationServiceTest` 통과.
- 당시 JDBC 기반 repository lookup test, `ProjectKeyVerificationServiceTest`, `MvcLayerBoundaryTest` 조합으로 targeted test가 통과했다.
- Docker daemon 기동 확인 후 `./gradlew :observability-portal:test --tests com.observation.portal.domain.catalog.repository.ProjectRepositoryIntegrationTest` 통과.
- `./gradlew :observability-portal:test` 통과.
- `./gradlew test` 통과.
- `git diff --check` 통과.
- 2026-05-19T10:24: JDBC 기반 repository 기록을 Spring Data JPA 기반 `ProjectRepository`와 `domain.catalog.entity.ProjectEntity` 구조로 교체했다.
- 2026-05-19T10:25: `./gradlew :observability-portal:test --tests com.observation.portal.domain.ingest.service.ProjectKeyVerificationServiceTest --tests com.observation.portal.architecture.MvcLayerBoundaryTest` 통과.
- 2026-05-19T10:25: `./gradlew :observability-portal:test --tests com.observation.portal.domain.catalog.repository.ProjectRepositoryIntegrationTest` 실행 중 Docker daemon 미기동으로 Testcontainers 초기화 실패를 확인했다.
- 2026-05-19T10:26: `docker info`에서 daemon socket 부재를 확인한 뒤 `open -a Docker`로 Docker Desktop을 시작했다.
- 2026-05-19T10:27: Testcontainers 기반 `ProjectRepositoryIntegrationTest`를 Flyway `clean/migrate` 후 JPA repository 저장/조회 검증으로 조정했고 통과했다.
- 2026-05-19T10:27: `./gradlew :observability-portal:test` 통과.
- 2026-05-19T10:27: `git diff --check` 통과.
- 2026-05-19T16:34: review P1 blocker 대응으로 `ProjectKeyVerificationService`가 header trim 후 UTF-8 72 byte 초과 key를 repository lookup/BCrypt 검증 전에 unauthorized로 닫도록 수정했다.
- 2026-05-19T16:34: project key 형식을 `<key_prefix>.<secret>`로 고정하고 separator 없음, 빈 prefix, 빈 secret, 32자 초과 prefix를 repository lookup 전에 unauthorized로 닫도록 수정했다.
- 2026-05-19T16:34: `ProjectKeyHashVerifier`가 UTF-8 72 byte 초과 raw key에 대해 BCrypt 비교 없이 `false`를 반환하도록 방어 로직을 추가했다.
- 2026-05-19T16:34: `./gradlew :observability-portal:test --tests com.observation.portal.domain.ingest.service.ProjectKeyVerificationServiceTest` 통과.
- 2026-05-19T16:34: `./gradlew :observability-portal:test --tests com.observation.portal.security.ProjectKeyHashVerifierTest` 통과.
- 2026-05-19T16:34: `./gradlew :observability-portal:test` 통과.
- 2026-05-19T16:34: `git diff --check` 통과.

### Completion Notes

- `ProjectKeyVerificationService`는 missing/blank header, malformed key, UTF-8 72 byte 초과 key, unknown prefix, BCrypt mismatch, disabled project를 모두 unauthorized result로 닫고, active project + matching hash일 때만 `VerifiedProject` context를 반환한다.
- project key는 trim 후 `<key_prefix>.<secret>` 형식만 허용한다. separator 없음, 빈 prefix, 빈 secret, 32자 초과 prefix는 repository lookup 전에 거부되며, repository에는 separator 앞의 non-secret prefix만 전달된다.
- `ProjectRepository`는 `projects.key_prefix`로 candidate row를 조회하며, service는 JPA entity를 `ProjectKeyCandidate`로 변환해 외부 반환 모델에는 entity를 노출하지 않는다.
- `ProjectEntity`는 `domain.catalog.entity`, Spring Data repository는 `domain.catalog.repository.ProjectRepository`에 두었다.
- `spring.jpa.hibernate.ddl-auto=none`으로 Hibernate DDL 생성/갱신을 막고, Testcontainers repository integration test는 Flyway migration을 적용한 뒤 JPA mapping과 lookup behavior를 검증한다.
- BCrypt 검증은 `ProjectKeyHashVerifier`에 격리했고, UTF-8 72 byte 초과 key, hash 형식 오류, mismatch는 예외/로그 대신 `false`로만 반환한다.
- result/model `toString()`과 service field/repository interaction guard 테스트로 raw project key가 반환/로그/repository lookup surface에 남지 않도록 고정했다.
- 이 story에서는 ingest payload validation, accepted metric buckets migration/repository, duplicate/idempotency handling, dashboard snapshot/read model, p95/state/rule/endpoint priority, Prometheus/scrape/query UI 경로를 추가하지 않았다.

### Correct Course Follow-up

- 2026-05-19 repository 표준이 Spring Data JPA/Jakarta Persistence + Hibernate로 고정되었다.
- `JdbcProjectRepository`, `JdbcProjectRepositoryTest`, JDBC 기반 integration test wiring을 제거하고 JPA 기반 구조로 교체했다.
- `ProjectEntity`는 `domain.catalog.entity`에 추가했고, Spring Data repository는 `domain.catalog.repository.ProjectRepository`에 두었다.
- Traditional MVC 기준상 `ProjectKeyVerificationService`가 Spring Data repository와 JPA entity를 직접 아는 것은 허용한다. 다만 entity는 controller DTO, public API surface, service result/external return model로 노출하지 않는다.
- `spring-boot-starter-data-jpa`를 도입하고 Hibernate DDL auto 생성/갱신 금지를 명시했다. Flyway migration과 Testcontainers repository integration test 기준은 유지한다.

### File List

- `observability-portal/build.gradle`
- `observability-portal/src/main/resources/application.properties`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/model/ProjectKeyCandidate.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/model/ProjectStatus.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ProjectRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/entity/ProjectEntity.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/entity/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/model/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/ProjectKeyVerificationResult.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/ProjectKeyVerificationService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/VerifiedProject.java`
- `observability-portal/src/main/java/com/observation/portal/security/ProjectKeyHashVerifier.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/repository/ProjectRepositoryIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/service/ProjectKeyVerificationServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/security/ProjectKeyHashVerifierTest.java`
- `planning-artifacts/stories/3-1-project-key-verification-service.dbml`
- `planning-artifacts/stories/3-1-project-key-verification-service.md`
- `implementation-artifacts/sprint-status.yaml`

### Change Log

- 2026-05-19: Story 3.1 Project Key Verification Service를 구현하고 service/repository/MVC boundary 테스트 및 전체 Gradle 테스트 통과 후 review 상태로 전환했다.
- 2026-05-19: Correct-course로 repository 구현 표준을 Spring Data JPA/Jakarta Persistence + Hibernate로 정렬하고, 당시 JDBC 구현을 후속 교체 대상으로 기록했다.
- 2026-05-19: Story 3.1 repository 구현을 JDBC에서 Spring Data JPA/Jakarta Persistence + Hibernate 기반 repository로 교체하고 follow-up 항목을 완료 처리했다.
- 2026-05-19: review P1 blocker였던 BCrypt 72 byte 초과 입력과 dot 없는 raw key repository lookup surface를 차단하고 Story 3.1을 done으로 전환했다.

## Review Findings

- [x] [Review][P1][Approval Blocker] BCrypt 입력 길이가 제한되지 않아 72-byte 초과 project key가 같은 BCrypt 입력으로 잘려 충돌 인증될 수 있다. `ProjectKeyVerificationService`와 `ProjectKeyHashVerifier`에서 UTF-8 byte length를 제한하고 초과 입력은 repository lookup/BCrypt 검증 전에 unauthorized 또는 `false`로 닫았다.
- [x] [Review][P1][Approval Blocker] dot 없는 짧은 project key는 raw key 전체가 `key_prefix` 후보가 되어 repository lookup surface에 전달될 수 있다. Project key 형식을 `<key_prefix>.<secret>`로 고정하고 separator 없음, 빈 prefix, 빈 secret, 32자 초과 prefix를 repository lookup 전에 차단했다.
- [x] [Review][Reclassified][Architecture Note] adapter형 repository 계약을 강제하지 않는다는 이전 finding은 새 Traditional MVC 기준에서는 결함이 아니다. Service가 Spring Data repository/JPA entity를 직접 사용할 수 있으며, review 기준은 entity가 controller DTO, public API surface, service result/external return model로 노출되는지 여부다.
- [x] [Review][Reclassified][Test Scope] `MvcLayerBoundaryTest`가 service -> entity/JPA 의존을 잡지 못한다는 finding은 필수 결함이 아니다. 현재 guard의 책임은 controller가 repository를 직접 호출하지 않는지, repository가 controller/dto에 의존하지 않는지, service가 controller/dto에 의존하지 않는지를 확인하는 것이다.
- [x] [Review][Documentation Drift] Story record에 남아 있던 예전 repository 하위 package와 JPA adapter phantom 구조를 현재 feature-first MVC 기준의 `domain.catalog.entity.ProjectEntity`와 `domain.catalog.repository.ProjectRepository` 기록으로 정리했다.

## Status

done
