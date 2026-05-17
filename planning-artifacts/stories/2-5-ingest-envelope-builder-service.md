---
artifactType: story
storyId: "2.5"
epic: "Epic 2. Starter Direct Ingest Producer"
title: "Ingest Envelope Builder Service"
architectureStyle: Traditional MVC
status: ready-for-dev
date: 2026-05-10
---

# Story 2.5 - Ingest Envelope Builder Service

## User Story

구현자로서, starter가 grace 이후 drain된 sealed 30초 bucket을 `ingest-envelope` contract에 맞는 payload와 idempotency header로 변환해 portal ingest API로 보낼 준비를 하길 원한다.

## Scope

이 story는 starter-side contract builder를 닫는다. Portal이 payload를 저장하고 idempotency를 판정하는 구현은 Epic 3으로 넘긴다.

포함:

- `IngestEnvelopeBuilderService`
- `schemaVersion: 1.0` payload 생성
- application identity, environment, instance identity mapping
- UTC 30초 bucket metadata serialization
- app summary serialization
- endpoint histogram serialization
- `Idempotency-Key` 후보 생성
- envelope contract/golden fixture test
- async flush worker와 builder 연결

제외:

- portal ingest controller implementation
- `ProjectKeyVerificationService`
- `IngestAcceptanceService`
- `accepted_metric_buckets` migration/repository
- duplicate success/conflict handling
- dashboard snapshot/read model
- p95/state/rule/endpoint priority calculation
- arbitrary custom metric/tag ingestion

## Source Artifacts

- `planning-artifacts/sprint-plan.md`
- `planning-artifacts/api-surface.md`
- `planning-artifacts/epics.md`
- `planning-artifacts/architecture.md`
- `planning-artifacts/architecture-implementation-supplement.md`
- `planning-artifacts/contracts/ingest-envelope.md`
- `planning-artifacts/contracts/metric-taxonomy.md`
- `planning-artifacts/contracts/time-buckets.md`
- `planning-artifacts/stories/2-2-route-normalization-and-low-cardinality-guard.md`
- `planning-artifacts/stories/2-3-bucket-rollup-service.md`
- `planning-artifacts/stories/2-4-async-flush-worker.md`

## Dependencies

- Story 2.2 ensures normalized route and low-cardinality tag policy.
- Story 2.3 provides grace 이후 drain된 sealed UTC 30초 bucket data.
- Story 2.4 provides async worker/client boundary.
- Epic 3 consumes the envelope through portal ingest acceptance.

## Implementation Notes

- payload contract source of truth is `planning-artifacts/contracts/ingest-envelope.md`.
- request endpoint is `POST /api/ingest/v1/buckets`, but portal implementation is Epic 3 scope.
- `X-OBS-Project-Key` comes from starter configuration and is sent as a header by the client boundary.
- `Idempotency-Key` format follows `planning-artifacts/api-surface.md`: `<project-id>:<application>:<environment>:<instance>:<bucket-start-utc>` or the closest configured project identity available to the starter.
- builder input은 이미 Story 2.3/2.4 경계에서 grace 이후 drain된 sealed bucket이어야 한다.
- idempotency key는 drain/flush 시각이 아니라 `bucket.startUtc` 기준으로 deterministic하게 만든다.
- envelope payload와 idempotency key는 drain/flush 시각과 무관하게 deterministic해야 한다.
- 같은 application/environment/instance/`bucket.startUtc` 조합에 대한 duplicate flush 1차 방지는 Story 2.3 sealed watermark와 Story 2.4 drain/enqueue 경계에서 수행한다. Story 2.5 builder는 stateful dedupe 저장소를 소유하지 않고 deterministic payload/idempotency key를 보장한다.
- The starter must not call the portal to resolve project identity while building the idempotency key; project identity material must come from local starter configuration.
- `bucket.startUtc` and `bucket.endUtc` must be UTC 30초 boundary values.
- `bucket.durationSeconds` is `30`.
- endpoint `route` must already be normalized.
- free tag map, arbitrary custom metric map, raw timeseries array are not allowed.
- MVP `schemaVersion: 1.0`은 JVM/datasource ratio를 latest sample shape로만 직렬화한다. Post-MVP latest/max/avg/sampleCount aggregate는 별도 schema version과 story가 열릴 때까지 builder에 추가하지 않는다.

## Acceptance Criteria

1. `IngestEnvelopeBuilderService` creates payload with `schemaVersion` equal to `1.0`.
2. envelope `application.name`, `environment`, `instance` are nonblank or fail local validation before send.
3. envelope bucket has `startUtc`, `endUtc`, and `durationSeconds: 30`.
4. bucket timestamps are UTC and aligned to the 30초 boundary.
5. summary contains request count, error count, HTTP server duration cumulative buckets, and available latest JVM/datasource ratios.
6. endpoint entries contain method, normalized route, request count, error count, and cumulative duration buckets.
7. envelope builder cannot serialize raw path, query string, arbitrary tag map, or high-cardinality custom metric payload.
8. builder creates or returns the idempotency header value needed by the flush client without any portal lookup.
9. idempotency key는 `bucket.startUtc` 기준이며 drain/flush 시각과 무관하게 동일 입력에 대해 동일 값을 만든다.
10. 같은 sealed bucket 입력은 drain/flush 시각과 무관하게 같은 payload와 idempotency key로 직렬화되며, builder는 stateful dedupe 저장소를 새로 만들지 않는다.
11. contract/golden fixture tests verify representative JSON shape.
12. this story does not implement portal ingest acceptance, repository storage, duplicate handling, p95/state/rule/read model calculation.

## Suggested Tasks

1. ingest envelope, metric taxonomy, and time bucket contracts를 다시 확인한다.
2. application/instance identity input model을 확인하거나 추가한다.
3. envelope DTO/model shape를 starter package에 추가한다.
4. `IngestEnvelopeBuilderService`를 추가한다.
5. idempotency key builder를 추가한다.
6. grace 이후 drain된 sealed bucket to envelope mapping을 구현한다.
7. low-cardinality guard를 통과한 endpoint만 serialize하도록 연결한다.
8. same sealed bucket 입력이 drain/flush 시각과 무관하게 동일 payload/idempotency key를 만드는 guard를 추가한다.
9. golden fixture 또는 JSON serialization test를 추가한다.
10. async flush worker가 builder output을 client boundary에 전달하도록 연결한다.
11. 기존 starter/portal tests를 실행한다.

## Test Requirements

- ingest envelope builder unit test
- UTC 30초 bucket serialization test
- normalized route only serialization test
- arbitrary tag/custom metric absence test
- idempotency key builder test, including no portal lookup requirement
- idempotency key determinism test independent of drain/flush time
- same sealed bucket deterministic payload/idempotency test independent of drain/flush time
- JSON golden fixture test
- 권장 실행 명령: `./gradlew test`

## Developer Guardrails

- portal storage success/failure semantics를 이 story에서 구현하지 않는다.
- duplicate success와 idempotency conflict는 Epic 3으로 넘긴다.
- portal duplicate/conflict handling은 재전송 안전망이며 starter duplicate flush 설계의 primary mechanism으로 삼지 않는다.
- builder가 p95를 계산하지 않는다.
- builder가 lifecycle state, insight rule, endpoint priority를 계산하지 않는다.
- free-form tags/custom metrics를 "future extension"이라는 이름으로 추가하지 않는다.
- route normalization 실패를 builder에서 raw path로 보정하지 않는다.
- idempotency key generation을 위해 host request path 또는 builder에서 portal network call을 하지 않는다.
- drain/flush 시각을 payload나 idempotency key 결정에 사용하지 않는다.
- builder에 stateful duplicate tracking을 추가하지 않는다. duplicate flush 1차 방지는 Story 2.3 sealed watermark와 Story 2.4 drain/enqueue 경계 책임이다.

## Tasks/Subtasks

- [ ] ingest envelope, metric taxonomy, time bucket contracts를 확인한다.
- [ ] application/instance identity input model을 확인하거나 추가한다.
- [ ] envelope DTO/model shape를 starter package에 추가한다.
- [ ] `IngestEnvelopeBuilderService`를 추가한다.
- [ ] idempotency key builder를 추가한다.
- [ ] grace 이후 drain된 sealed bucket to envelope mapping을 구현한다.
- [ ] low-cardinality guard를 통과한 endpoint만 serialize하도록 연결한다.
- [ ] same sealed bucket deterministic payload/idempotency guard를 추가한다.
- [ ] JSON golden fixture 또는 contract test를 추가한다.
- [ ] async flush worker와 builder output을 연결한다.
- [ ] 기존 starter/portal tests를 실행한다.

## Dev Agent Record

### Implementation Plan

TBD by dev-story.

### Debug Log

TBD by dev-story.

### Completion Notes

TBD by dev-story.

### File List

TBD by dev-story.

## Status

ready-for-dev
