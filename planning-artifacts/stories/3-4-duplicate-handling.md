---
artifactType: story
storyId: "3.4"
epic: "Epic 3. Portal Ingest Acceptance"
title: "Duplicate Handling"
architectureStyle: Traditional MVC
status: done
date: 2026-05-18
---

# Story 3.4 - Duplicate Handling

## User Story

구현자로서, portal ingest API가 starter retry나 duplicate flush candidate를 안전하게 받아 같은 payload는 성공으로 수렴시키고 같은 idempotency key의 다른 payload는 conflict로 막길 원한다.

## Scope

이 story는 Epic 3의 idempotent acceptance contract를 닫는다.

포함:

- idempotency lookup before insert
- same key and same payload hash duplicate success
- same key and different payload hash conflict
- unique constraint race convergence
- controller `201` / `200` / `409` response mapping
- duplicate response에서 existing bucket id and accepted timestamp 반환
- duplicate/conflict integration tests

제외:

- starter duplicate flush primary mechanism 변경
- durable outbox or exactly-once delivery guarantee
- dashboard snapshot/read model refresh
- lifecycle state, p95, insight rule, endpoint priority 계산
- operational event history
- alert/delivery log dedupe
- Prometheus/scrape/query UI path

## Source Artifacts

- `implementation-artifacts/epic-2-retro-2026-05-18.md`
- `planning-artifacts/sprint-plan.md`
- `planning-artifacts/api-surface.md`
- `planning-artifacts/database-schema.md`
- `planning-artifacts/contracts/ingest-envelope.md`
- `planning-artifacts/stories/2-5-ingest-envelope-builder-service.md`
- `planning-artifacts/stories/3-1-project-key-verification-service.md`
- `planning-artifacts/stories/3-2-ingest-acceptance-service.md`
- `planning-artifacts/stories/3-3-postgresql-bucket-repository.md`

## Dependencies

- Story 3.1 verifies project key.
- Story 3.2 validates payload and calculates deterministic payload hash.
- Story 3.3 persists accepted bucket and exposes idempotency lookup.
- Epic 2 starter still owns primary duplicate flush prevention via sealed watermark/drain boundary.

## Implementation Notes

- Portal duplicate handling is a retry safety net. It is not a durable delivery or exactly-once guarantee.
- `uk_buckets_project_idempotency_key` is the primary idempotency constraint.
- If an insert races and unique constraint fails, the service should re-read by project/idempotency key and return duplicate or conflict based on payload hash.
- Same project/idempotency key and same payload hash returns existing bucket id and original accepted timestamp.
- Same project/idempotency key and different payload hash returns `409 Conflict` and must not overwrite existing row.
- Same instance/bucket start collision with a different idempotency key indicates deterministic key contract drift; handle it as conflict, not as a new bucket.
- response shape follows `planning-artifacts/api-surface.md`.

## Acceptance Criteria

1. first accepted bucket returns `201 Created`, `duplicate: false`.
2. same project/idempotency key and same payload hash returns `200 OK`, `duplicate: true`, existing bucket id, and original accepted timestamp.
3. same project/idempotency key and different payload hash returns `409 Conflict` with `idempotency_conflict`.
4. duplicate retry does not create a second `accepted_metric_buckets` row.
5. race between two inserts with the same key converges to one accepted row plus duplicate success or conflict.
6. same instance/bucket start collision with different idempotency material does not create a second bucket.
7. invalid project key, invalid payload, duplicate success, conflict, persistence failure map to `401`, `400`, `200`, `409`, `500` respectively.
8. this story does not refresh dashboard snapshots or calculate p95/state/rule/endpoint priority.

## Suggested Tasks

1. repository idempotency lookup API를 확인한다.
2. acceptance result model에 accepted/duplicate/conflict/failure 상태를 추가한다.
3. `IngestAcceptanceService` duplicate decision flow를 구현한다.
4. unique constraint exception mapping과 re-read convergence를 구현한다.
5. `IngestController` response mapping을 `api-surface.md`와 맞춘다.
6. same key/same hash duplicate test를 추가한다.
7. same key/different hash conflict test를 추가한다.
8. race 또는 unique violation simulation test를 추가한다.
9. 전체 portal test와 전체 Gradle test를 실행한다.

## Test Requirements

- `DuplicateIngestAcceptanceTest`
- same key/same payload hash duplicate success test
- same key/different payload hash conflict test
- unique constraint race convergence test
- controller status mapping test for `201`, `200`, `400`, `401`, `409`, `500`
- no second row assertion for duplicate retry
- `MvcLayerBoundaryTest`
- 권장 실행 명령: `./gradlew :observability-portal:test && ./gradlew test`

## Developer Guardrails

- duplicate handling을 starter duplicate flush primary mechanism으로 해석하지 않는다.
- accepted bucket row를 conflict path에서 overwrite하지 않는다.
- dashboard snapshot/read model refresh를 이 story에 연결하지 않는다.
- p95/histogram merge, lifecycle state, insight rule, endpoint priority 계산을 추가하지 않는다.
- operational event 저장이나 alert delivery dedupe로 scope를 넓히지 않는다.

## Tasks/Subtasks

- [x] repository idempotency lookup을 확인한다.
- [x] acceptance result model을 확장한다.
- [x] duplicate decision flow를 구현한다.
- [x] idempotency unique violation race를 duplicate key reject로 매핑한다.
- [x] controller response mapping을 완성한다.
- [x] duplicate key reject/controller 409 tests를 추가한다.
- [x] no second row assertion을 추가한다.
- [x] 전체 portal/Gradle tests를 실행한다.

### Review Findings

- [x] [Review][Patch] Repository duplicate/no-overwrite 검증이 row count에만 머문다 [observability-portal/src/test/java/com/observation/portal/domain/bucket/repository/MetricBucketRepositoryIntegrationTest.java:150]
- [x] [Review][Patch] Deferred full-idempotency 항목이 완료 체크리스트에 완료로 남아 있다 [planning-artifacts/stories/3-4-duplicate-handling.md:117]
- [x] [Review][Patch] Story frontmatter status가 review 상태와 불일치한다 [planning-artifacts/stories/3-4-duplicate-handling.md:7]

## Dev Agent Record

### Implementation Plan

- `mvp-deferred-risk-spec.md`를 Story 3.4 원문의 full idempotency 요구보다 우선 적용해 duplicate replay success를 구현하지 않는다.
- `MetricBucketRepository.findByProjectIdAndIdempotencyKey` pre-read 결과가 있으면 payload hash 비교 없이 `DUPLICATE_IDEMPOTENCY_KEY` service result로 닫고 insert를 호출하지 않는다.
- idempotency unique constraint race는 re-read convergence 대신 MVP 정책에 맞춰 duplicate key reject로 매핑한다.
- controller는 first accepted ingest를 `201 Created duplicate=false`, duplicate key reject를 `409 Conflict duplicate_idempotency_key`로 매핑한다.
- repository integration test는 unique constraint 실패 뒤에도 accepted bucket row가 하나만 남는지 확인한다.

### Debug Log

- 2026-05-20T14:18:29+0900: `implementation-artifacts/sprint-status.yaml`에서 Story 3.4 상태를 `in-progress`로 전환했다.
- `planning-artifacts/mvp-deferred-risk-spec.md`, `implementation-artifacts/deferred-work.md`, `planning-artifacts/stories/3-3-postgresql-bucket-repository.md`, `_bmad/custom/project-context.md`를 읽고 MVP duplicate 정책을 Story 3.4 실행 기준으로 확정했다.
- 기존 생산 코드에서 repository idempotency lookup, `DUPLICATE_IDEMPOTENCY_KEY` result, service duplicate pre-read reject, idempotency unique violation mapping, controller `409 duplicate_idempotency_key` mapping이 MVP 정책과 맞는지 확인했다.
- `IngestAcceptanceServiceTest`에 duplicate pre-read가 payload hash 계산과 insert를 호출하지 않는 회귀 테스트를 보강했다.
- 같은 idempotency key의 다른 payload도 payload hash 비교 없이 `duplicate_idempotency_key`로 reject되는 회귀 테스트를 추가했다.
- `IngestControllerTest`에 invalid payload `400`, invalid project key `401`, duplicate key `409` mapping 검증을 보강했다.
- `MetricBucketRepositoryIntegrationTest`에 idempotency/instance bucket unique constraint 실패 후 `accepted_metric_buckets` row count가 1개로 유지되는 assertion을 추가했다.
- `./gradlew :observability-portal:test --tests com.observation.portal.domain.ingest.service.IngestAcceptanceServiceTest --tests com.observation.portal.domain.ingest.controller.IngestControllerTest` 통과.
- `./gradlew :observability-portal:test --tests com.observation.portal.domain.bucket.repository.MetricBucketRepositoryIntegrationTest` 통과.
- `./gradlew :observability-portal:test` 통과.
- `./gradlew test` 통과.
- `git diff --check` 통과.
- 2026-05-20T14:49:06+0900: code review patch findings 3건을 반영했다.
- `MetricBucketRepositoryIntegrationTest`가 unique constraint 실패 뒤 row count뿐 아니라 기존 bucket row 내용과 catalog first/last seen 값이 그대로 유지되는지 검증하도록 보강했다.
- Story 3.4 Tasks/Subtasks 문구를 active MVP duplicate policy에 맞춰 정리하고 frontmatter/footer 상태를 `done`으로 동기화했다.
- Review patch 적용 후 `./gradlew :observability-portal:test --tests com.observation.portal.domain.bucket.repository.MetricBucketRepositoryIntegrationTest`, `./gradlew :observability-portal:test`, `./gradlew test`, `git diff --check` 통과.

### Completion Notes

- Story 3.4 원문의 `200 OK duplicate=true`, payload hash 기반 same/different payload 분류, insert race 후 re-read convergence는 active MVP 정책에 따라 구현하지 않았다.
- 현재 MVP duplicate path는 같은 `(project_id, idempotency_key)`가 있으면 payload hash 계산 없이 `409 Conflict`와 `duplicate_idempotency_key`로 reject하며 `MetricBucketRepository.insert`를 호출하지 않는다.
- first accepted ingest는 기존처럼 `201 Created`, `duplicate=false`, bucket id, accepted timestamp를 반환한다.
- idempotency unique violation race는 duplicate key reject로 매핑하고, unique constraint 실패 이후에도 기존 row overwrite, 두 번째 row 생성, catalog seen timestamp 오염이 없음을 repository integration test로 검증했다.
- dashboard snapshot/read model refresh, p95/state/rule/endpoint priority, operational event 저장은 추가하지 않았다.

### File List

- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/stories/3-4-duplicate-handling.md`
- `observability-portal/src/test/java/com/observation/portal/domain/bucket/repository/MetricBucketRepositoryIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/controller/IngestControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/service/IngestAcceptanceServiceTest.java`

### Change Log

- 2026-05-20: Story 3.4를 active MVP duplicate policy에 맞춰 완료했다. Duplicate replay success/full idempotency는 deferred로 유지하고, duplicate key reject 및 no-second-row 회귀 테스트를 보강했다.
- 2026-05-20: Code review patch findings 3건을 해결하고 Story 3.4를 done으로 동기화했다.

## Status

done
