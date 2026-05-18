---
artifactType: story
storyId: "3.1"
epic: "Epic 3. Portal Ingest Acceptance"
title: "Project Key Verification Service"
architectureStyle: Traditional MVC
status: ready-for-dev
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
- raw project key 비저장/비반환/비로그 guard
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

## Dependencies

- Epic 1 `projects` schema foundation.
- Epic 2 closure status.
- Story 3.2 consumes the verified project context.

## Implementation Notes

- Project key verification belongs in portal service/security boundary, not controller logic.
- Controller는 header string을 service에 전달하고, service 결과를 기준으로 `401` mapping만 수행한다.
- `key_prefix`는 project 후보 조회용이다. raw project key 원문은 DB에 저장하지 않는다.
- `project_key_hash` 검증은 BCrypt를 사용한다. 필요한 경우 최소 crypto dependency를 추가하되 product auth surface를 넓히지 않는다.
- disabled project는 missing/invalid key와 같은 unauthorized result로 처리한다.
- service result는 project UUID, project name/status 같은 내부 식별 정보만 포함하고 raw key를 포함하지 않는다.
- test fixture raw key는 테스트 코드 안에서만 사용하고 migration에 평문 secret을 고정하지 않는다.

## Acceptance Criteria

1. missing or blank `X-OBS-Project-Key` is rejected.
2. unknown prefix or missing project candidate is rejected.
3. BCrypt hash mismatch is rejected.
4. `status != active` project is rejected.
5. active project with matching hash returns a verified project context.
6. raw project key is not persisted, returned in result objects, or logged by the service.
7. controller/repository dependency direction still satisfies `MvcLayerBoundaryTest`.
8. this story does not validate payload schema or write accepted metric buckets.

## Suggested Tasks

1. `projects` schema와 existing catalog package를 확인한다.
2. project key verification result model을 추가한다.
3. `ProjectRepository` lookup method를 추가하거나 구현한다.
4. `ProjectKeyVerificationService`를 추가한다.
5. BCrypt verification dependency가 필요한지 확인하고 최소 범위로 추가한다.
6. missing/unknown/mismatch/disabled/active test를 추가한다.
7. raw key가 result/log/persistence로 새지 않는 guard를 추가한다.
8. portal architecture test를 실행한다.

## Test Requirements

- `ProjectKeyVerificationServiceTest`
- `ProjectRepository` lookup test 또는 focused repository integration test
- disabled project unauthorized test
- raw key 비노출 guard test
- `MvcLayerBoundaryTest`
- 권장 실행 명령: `./gradlew :observability-portal:test`

## Developer Guardrails

- raw project key를 migration, DB row, log, exception message, response body에 남기지 않는다.
- project key verification을 dashboard나 admin product API로 확장하지 않는다.
- application catalog 생성과 bucket persistence를 이 story에 섞지 않는다.
- Epic 2 starter configuration/key generation을 되돌리거나 재설계하지 않는다.
- lifecycle state, p95, insight rule, endpoint priority 계산을 추가하지 않는다.

## Tasks/Subtasks

- [ ] `projects` schema와 catalog package를 확인한다.
- [ ] project key verification result model을 추가한다.
- [ ] project lookup repository method를 추가한다.
- [ ] `ProjectKeyVerificationService`를 구현한다.
- [ ] BCrypt verification dependency와 configuration 범위를 확인한다.
- [ ] missing/unknown/mismatch/disabled/active tests를 추가한다.
- [ ] raw key 비노출 guard를 추가한다.
- [ ] `./gradlew :observability-portal:test`를 실행한다.

## Status

ready-for-dev
