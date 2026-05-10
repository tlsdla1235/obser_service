---
artifactType: story
storyId: "2.3"
epic: "Epic 2. Starter Direct Ingest Producer"
title: "Bucket Rollup Service"
architectureStyle: Traditional MVC
status: ready-for-dev
date: 2026-05-10
---

# Story 2.3 - Bucket Rollup Service

## User Story

구현자로서, starter가 low-cardinality observation input을 UTC 30초 bucket으로 집계해 app summary와 endpoint histogram bucket을 만들 수 있길 원한다.

## Scope

이 story는 starter local aggregation boundary다. Story 2.2를 통과한 normalized route input만 집계한다.

포함:

- UTC 30초 bucket boundary 계산
- app-level request/error/duration histogram rollup
- endpoint-level method/normalized route histogram rollup
- JVM CPU/heap, datasource pool usage ratio sample 집계 경계
- cumulative histogram bucket count 유지
- closed bucket을 flush 대상 후보로 만드는 service 경계

제외:

- Micrometer binding 재구현
- raw path/tag normalization 재구현
- bounded queue와 async flush worker
- HTTP ingest client
- retry/backoff
- ingest envelope serialization
- portal ingest acceptance/persistence
- p95 calculation
- dashboard read model calculation

## Source Artifacts

- `planning-artifacts/sprint-plan.md`
- `planning-artifacts/epics.md`
- `planning-artifacts/architecture.md`
- `planning-artifacts/architecture-implementation-supplement.md`
- `planning-artifacts/contracts/time-buckets.md`
- `planning-artifacts/contracts/metric-taxonomy.md`
- `planning-artifacts/contracts/ingest-envelope.md`
- `planning-artifacts/stories/2-1-micrometer-observation-binding.md`
- `planning-artifacts/stories/2-2-route-normalization-and-low-cardinality-guard.md`

## Dependencies

- Story 2.1 provides observation inputs.
- Story 2.2 provides normalized route and low-cardinality tag guard.
- Story 2.4 consumes closed buckets through bounded queue/worker.
- Story 2.5 serializes closed buckets into ingest envelope.

## Implementation Notes

- bucket duration은 30초로 고정한다.
- bucket boundary는 UTC 기준 `:00` 또는 `:30` 초에 정렬한다.
- bucket interval은 `[startUtc, endUtc)` semantics를 따른다.
- endpoint key는 `method + normalized route`다.
- app-level histogram과 endpoint-level histogram은 cumulative count shape로 유지한다.
- JVM/datasource ratio는 MVP에서 bucket 안의 latest valid sample 또는 명시적 aggregate 정책으로 둔다. absent sample은 host request processing을 막지 않는다.
- p95는 starter에서 계산하지 않는다. server-side histogram merge는 Epic 5 scope다.

## Acceptance Criteria

1. `Instant` input은 UTC 30초 boundary의 `startUtc`와 `endUtc`로 매핑된다.
2. `01:00:00Z`와 `01:00:29.999Z` sample은 `01:00:00Z-01:00:30Z` bucket에 속한다.
3. `01:00:30Z` sample은 `01:00:30Z-01:01:00Z` bucket에 속한다.
4. app-level request count와 error count가 bucket 단위로 집계된다.
5. endpoint-level request count와 error count가 `method + normalized route` 단위로 집계된다.
6. duration histogram bucket은 cumulative count 형태를 유지한다.
7. rollup service는 raw path 또는 high-cardinality tag를 입력으로 받지 않는다.
8. closed bucket을 flush candidate로 반환할 수 있다.
9. 이 story에서는 network call, HTTP client, queue worker, envelope builder를 구현하지 않는다.
10. p95, lifecycle state, insight rule, endpoint priority 계산을 구현하지 않는다.

## Suggested Tasks

1. Story 2.2 normalized route model과 metric taxonomy를 확인한다.
2. UTC bucket boundary utility/model을 추가한다.
3. app-level rollup model을 추가한다.
4. endpoint-level histogram rollup model을 추가한다.
5. `MetricBucketRollupService`를 추가한다.
6. sample record와 closed bucket retrieval 경계를 구현한다.
7. cumulative histogram bucket count 테스트를 추가한다.
8. UTC boundary edge case 테스트를 추가한다.
9. raw path/high-cardinality input이 rollup으로 들어오지 못하는 guard를 확인한다.
10. 기존 starter/portal tests를 실행한다.

## Test Requirements

- UTC 30초 boundary edge case test
- `[startUtc, endUtc)` bucket inclusion test
- app summary rollup test
- endpoint histogram cumulative count test
- normalized route only input test
- no p95/state/rule calculation guard
- 권장 실행 명령: `./gradlew test`

## Developer Guardrails

- local p95를 starter에서 계산하지 않는다.
- bucket duration을 config로 열더라도 MVP default와 acceptance는 30초로 고정한다.
- JVM/datasource sample 부재를 host request failure로 만들지 않는다.
- raw path 또는 arbitrary label을 rollup key로 사용하지 않는다.
- queue, HTTP client, envelope builder를 당겨오지 않는다.
- portal accepted bucket storage는 Epic 3에서 구현한다.

## Tasks/Subtasks

- [ ] Story 2.2 normalized route model과 metric taxonomy를 확인한다.
- [ ] UTC bucket boundary utility/model을 추가한다.
- [ ] app-level rollup model을 추가한다.
- [ ] endpoint-level histogram rollup model을 추가한다.
- [ ] `MetricBucketRollupService`를 추가한다.
- [ ] sample record와 closed bucket retrieval 경계를 구현한다.
- [ ] cumulative histogram bucket count 테스트를 추가한다.
- [ ] UTC boundary edge case 테스트를 추가한다.
- [ ] raw path/high-cardinality input guard를 확인한다.
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
