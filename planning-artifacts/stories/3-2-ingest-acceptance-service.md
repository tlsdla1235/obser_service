---
artifactType: story
storyId: "3.2"
epic: "Epic 3. Portal Ingest Acceptance"
title: "Ingest Acceptance Service"
architectureStyle: Traditional MVC
status: review
baseline_commit: "9a9ad3ab1a6f6fd790e5f1a120728e94c28ce3ad"
date: 2026-05-18
---

# Story 3.2 - Ingest Acceptance Service

## User Story

구현자로서, portal이 starter가 보낸 ingest envelope를 그대로 신뢰하지 않고 `ingest-envelope`, `metric-taxonomy`, `time-buckets` 계약에 맞게 다시 검증하길 원한다.

## Scope

이 story는 portal-side validation과 acceptance orchestration boundary를 닫는다.

포함:

- ingest request DTO/model
- `IngestAcceptanceService`
- `ProjectKeyVerificationService` 연동
- Story 2.5 golden ingest envelope success fixture
- schemaVersion `1.0` validation
- UTC 30초 bucket boundary validation
- metric taxonomy validation
- idempotency key format and payload consistency validation
- invalid request `400` / invalid project key `401` mapping 기준

제외:

- `accepted_metric_buckets` migration/repository insert
- duplicate success/conflict persistence handling
- dashboard snapshot/read model refresh
- p95/histogram merge
- lifecycle state, insight rule, endpoint priority 계산
- Post-MVP runtime aggregate schemaVersion `1.1`
- Prometheus/scrape/query UI path

## Source Artifacts

- `implementation-artifacts/epic-2-retro-2026-05-18.md`
- `planning-artifacts/sprint-plan.md`
- `planning-artifacts/api-surface.md`
- `planning-artifacts/architecture.md`
- `planning-artifacts/architecture-implementation-supplement.md`
- `planning-artifacts/contracts/ingest-envelope.md`
- `planning-artifacts/contracts/metric-taxonomy.md`
- `planning-artifacts/contracts/time-buckets.md`
- `planning-artifacts/stories/2-5-ingest-envelope-builder-service.md`
- `planning-artifacts/stories/2-6-negative-path-guard.md`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/service/IngestEnvelopeContractJsonTest.java`

## Dependencies

- Story 3.1 verifies project key and returns project context.
- Story 2.5 provides golden JSON and deterministic idempotency key examples.
- Story 3.3 consumes the validated ingest candidate for persistence.

## Implementation Notes

- Validation belongs in service/model code. Controller DTO annotations may reject malformed input early, but source-of-truth business validation is `IngestAcceptanceService`.
- `schemaVersion`은 MVP에서 `1.0`만 허용한다.
- `bucket.durationSeconds`는 `30`이고 `endUtc - startUtc`도 30초여야 한다.
- `startUtc`와 `endUtc`는 UTC 30초 boundary에 정렬되어야 한다.
- `Idempotency-Key`는 5개 component 형식과 header-safe 문자 집합을 검증한다.
- idempotency key의 application/environment/instance/bucket-start component는 payload와 일치해야 한다.
- payload hash 계산이 이 story에서 시작되면 validated canonical representation 기준으로 deterministic하게 만든다.
- DTO는 unknown field를 허용하지 않아 free tag map, arbitrary custom metric map, raw timeseries array, Post-MVP aggregate field를 차단한다.
- Story 2.5 golden JSON은 portal acceptance success fixture로 재사용한다.
- persistence success response는 Story 3.3에서 repository와 연결한다.

## Acceptance Criteria

1. Story 2.5 golden envelope and idempotency key pass portal validation.
2. `schemaVersion` other than `1.0` is rejected.
3. non-UTC, non-30-second-aligned, wrong duration, or mismatched start/end bucket is rejected.
4. blank application name, environment, or instance is rejected.
5. negative counts, `errorCount > requestCount`, non-monotonic cumulative histogram buckets, or invalid ratio range is rejected.
6. endpoint method/route/count/histogram fields follow `metric-taxonomy` and `ingest-envelope` rules.
7. route with query string, absolute URL, raw identifier candidate, or non-normalized shape is rejected where the MVP contract can detect it.
8. free-form tags, arbitrary custom metric map, raw timeseries array, and schemaVersion `1.1` runtime aggregate fields are rejected.
9. invalid project key result from Story 3.1 maps to unauthorized acceptance result.
10. this story does not write `accepted_metric_buckets` or create dashboard snapshots.

## Suggested Tasks

1. Story 2.5 golden JSON test payload를 portal test fixture로 옮기거나 공유 가능한 형태로 재사용한다.
2. ingest request DTO/model을 추가한다.
3. validation error model을 추가한다.
4. `IngestAcceptanceService`를 추가한다.
5. project key verification result를 acceptance context로 연결한다.
6. schema/bucket/metric/idempotency key validation tests를 추가한다.
7. unknown field/free tag/custom metric/raw timeseries rejection test를 추가한다.
8. controller status mapping이 필요한 경우 invalid path 중심 slice test를 추가한다.
9. `MvcLayerBoundaryTest`를 실행한다.

## Test Requirements

- `IngestAcceptanceServiceTest`
- `PortalIngestValidationFixtureTest`
- Story 2.5 golden envelope success fixture
- invalid schema/bucket/route/idempotency key tests
- free tag/custom metric/raw timeseries unknown field rejection test
- invalid project key unauthorized mapping test
- `MvcLayerBoundaryTest`
- 권장 실행 명령: `./gradlew :observability-portal:test`

## Developer Guardrails

- starter payload를 신뢰만 하고 저장하지 않는다. Portal validation은 starter contract를 mirror한다.
- schemaVersion `1.1` runtime aggregate 후보를 MVP validation에 섞지 않는다.
- raw path/query/high-cardinality tag를 "나중에 쓸 수 있게" 보관하지 않는다.
- accepted bucket repository, duplicate conflict, dashboard read model을 이 story에서 끝까지 구현하려고 범위를 넓히지 않는다.
- controller가 repository를 직접 호출하지 않는다.

## Tasks/Subtasks

- [x] Story 2.5 golden fixture를 확인한다.
- [x] ingest DTO/model을 추가한다.
- [x] validation error/result model을 추가한다.
- [x] `IngestAcceptanceService`를 구현한다.
- [x] project key verification 연동을 추가한다.
- [x] schema/bucket/metric/idempotency validation tests를 추가한다.
- [x] forbidden field rejection tests를 추가한다.
- [x] portal architecture tests를 실행한다.

## Dev Agent Record

### Implementation Plan

- Story 2.5 `IngestEnvelopeContractJsonTest`의 representative golden JSON과 deterministic idempotency key를 portal test fixture로 복제해 success path를 고정한다.
- service가 controller DTO에 의존하지 않도록 `domain.ingest.service` 안에 ingest envelope request model, validation error/result model, validated candidate model을 둔다.
- `IngestAcceptanceService`는 `ProjectKeyVerificationService`를 먼저 호출하고, unauthorized project key는 payload validation 전에 닫는다.
- service validation은 schemaVersion, UTC 30초 bucket, application identity, app/endpoint counts, cumulative histogram, runtime ratio, endpoint method/route, Idempotency-Key format과 payload consistency를 검증한다.
- unknown field/free tag/custom metric/raw timeseries/Post-MVP runtime aggregate shape는 Jackson request model boundary에서 거부한다.
- `accepted_metric_buckets` migration/repository, duplicate success/conflict persistence, dashboard snapshot/read model, p95/state/insight/endpoint priority 계산은 추가하지 않는다.

### Debug Log

- 2026-05-19T16:43:21+0900: `implementation-artifacts/sprint-status.yaml`에서 Story 3.2 상태를 `in-progress`로 전환하고 story frontmatter에 baseline commit을 기록했다.
- Story 2.5 starter golden JSON과 idempotency key를 `PortalIngestValidationFixture`로 옮겨 portal validation success fixture를 만들었다.
- `IngestEnvelopeRequest`, `IngestValidationError`, `IngestAcceptanceResult`, `ValidatedIngestCandidate`, `IngestAcceptanceService`를 추가했다.
- `IngestAcceptanceServiceTest` 최초 실행에서 idempotency key 끝의 control character가 `trim()` 이후 검사되어 통과될 수 있음을 확인했고, header 원문을 먼저 검사하도록 수정했다.
- `./gradlew :observability-portal:test --tests com.observation.portal.domain.ingest.service.IngestAcceptanceServiceTest` 통과.
- `./gradlew :observability-portal:test --tests com.observation.portal.domain.ingest.service.PortalIngestValidationFixtureTest` 통과.
- `./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest` 통과.
- `./gradlew :observability-portal:test --tests com.observation.portal.domain.ingest.service.IngestAcceptanceServiceTest --tests com.observation.portal.domain.ingest.service.PortalIngestValidationFixtureTest --tests com.observation.portal.architecture.MvcLayerBoundaryTest` 통과.
- `./gradlew :observability-portal:test` 통과.
- `git diff --check` 통과.
- 2026-05-19T16:53:47+0900: BMAD 3종 review 절차를 수행하지 않은 상태에서 `done`으로 전환했던 기록을 정정하고 Story 3.2를 `review`로 되돌렸다.

### Completion Notes

- Portal validation은 Story 2.5 golden envelope와 `project-123:orders-api:prod:orders-api-7f9c9c8c9d-x2p4k:20260508T010000Z` idempotency key를 accepted result로 통과시킨다.
- invalid project key는 `ProjectKeyVerificationResult.unauthorized()`를 받아 payload validation 전에 `IngestAcceptanceResult.unauthorized()`로 닫는다.
- schemaVersion은 `1.0`만 허용하며, `bucket.durationSeconds = 30`, `[startUtc, endUtc)` 30초 간격, UTC `Z` timestamp, 30초 boundary 정렬을 검증한다.
- application name/environment/instance blank, 음수 count, `errorCount > requestCount`, empty/non-monotonic cumulative histogram, requestCount 초과 histogram count, `0.0..1.0` 밖 JVM/datasource ratio를 거부한다.
- endpoint method는 bounded uppercase HTTP method로 제한하고, endpoint route는 `UNKNOWN` 또는 normalized route template만 허용한다. query string, absolute URL, numeric/UUID/long-hex identifier segment, trailing slash, double slash, malformed template shape를 거부한다.
- `Idempotency-Key`는 5개 component와 header-safe 문자 집합을 검증하고, application/environment/instance/bucket-start component가 payload와 일치해야 한다.
- request model은 unknown field를 허용하지 않아 free tag map, arbitrary custom metric map, raw timeseries array, schemaVersion `1.1` runtime aggregate object shape를 service validation 전에 거부한다.
- invalid result/error model과 `toString()`은 raw project key, raw route/query 값을 보관하거나 출력하지 않는다.
- 이번 story에서는 persistence, duplicate handling, dashboard snapshot/read model, p95/state/insight/endpoint priority 계산을 구현하지 않았다.

### File List

- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/stories/3-2-ingest-acceptance-service.md`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestAcceptanceService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestAcceptanceResult.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestEnvelopeRequest.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestValidationError.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/ValidatedIngestCandidate.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/service/IngestAcceptanceServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/service/PortalIngestValidationFixture.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/service/PortalIngestValidationFixtureTest.java`

### Change Log

- 2026-05-19: Story 3.2 Ingest Acceptance Service를 구현하고 portal validation fixture, service tests, MVC boundary test, portal 전체 테스트를 통과해 review 상태로 전환했다.
- 2026-05-19: BMAD review 미수행 상태에서 `done`으로 올린 기록을 정정하고, review 완료 전까지 status를 `review`로 유지하도록 수정했다.

## Status

review
