---
artifactType: story
storyId: "3.4"
epic: "Epic 3. Portal Ingest Acceptance"
title: "Duplicate Handling"
architectureStyle: Traditional MVC
status: ready-for-dev
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

- [ ] repository idempotency lookup을 확인한다.
- [ ] acceptance result model을 확장한다.
- [ ] duplicate decision flow를 구현한다.
- [ ] unique constraint race convergence를 구현한다.
- [ ] controller response mapping을 완성한다.
- [ ] duplicate success/conflict tests를 추가한다.
- [ ] no second row assertion을 추가한다.
- [ ] 전체 portal/Gradle tests를 실행한다.

## Status

ready-for-dev
