---
artifactType: story
storyId: "2.6"
epic: "Epic 2. Starter Direct Ingest Producer"
title: "Negative Path Guard"
architectureStyle: Traditional MVC
status: ready-for-dev
date: 2026-05-10
---

# Story 2.6 - Negative Path Guard

## User Story

구현자로서, Epic 2가 direct ingest producer 경로를 닫은 뒤에도 Prometheus scrape, pull metric query, arbitrary query UI 같은 MVP 역행 경로가 starter에 들어오지 않도록 테스트로 고정하고 싶다.

## Scope

이 story는 negative MVP path guard다. Epic 2의 producer path가 구현된 후 source/build/resource level에서 금지 경로를 검증한다.

포함:

- starter forbidden package guard
- starter web controller absence guard
- Prometheus/scrape/export/query path absence guard
- arbitrary query UI path absence guard
- high-cardinality custom metric/tag ingestion absence guard
- existing Epic 2 producer tests가 함께 통과하는지 확인

제외:

- Prometheus future integration 설계
- dashboard UI 구현
- portal query API 구현
- portal ingest acceptance 구현
- read model/p95/state/rule 계산 구현
- runtime code refactor beyond guard requirements

## Source Artifacts

- `planning-artifacts/sprint-plan.md`
- `planning-artifacts/epics.md`
- `planning-artifacts/architecture.md`
- `planning-artifacts/architecture-implementation-supplement.md`
- `planning-artifacts/acceptance-traceability.md`
- `planning-artifacts/contracts/metric-taxonomy.md`
- `planning-artifacts/contracts/ingest-envelope.md`
- `planning-artifacts/stories/2-1-micrometer-observation-binding.md`
- `planning-artifacts/stories/2-2-route-normalization-and-low-cardinality-guard.md`
- `planning-artifacts/stories/2-3-bucket-rollup-service.md`
- `planning-artifacts/stories/2-4-async-flush-worker.md`
- `planning-artifacts/stories/2-5-ingest-envelope-builder-service.md`

## Dependencies

- Story 2.1-2.5 should be implemented first so guard tests can inspect the completed producer path.
- Epic 3/4/5 remain out of scope.

## Implementation Notes

- Negative guard는 active source/build/resource 경로를 대상으로 한다.
- planning docs와 archive docs에 과거 Prometheus pivot 설명이 있어도 test failure로 보지 않는다.
- starter module은 host app library이며 MVC web controller를 만들지 않는다.
- MVP 필수 경로는 direct ingest producer다.
- arbitrary metric query UI, PromQL query builder, scrape target config는 MVP 경로가 아니다.
- `domain`, `application`, `port`, `adapter` package는 starter에 만들지 않는다.

## Acceptance Criteria

1. starter source tree에는 `application`, `port`, `adapter` package가 없다.
2. starter에는 MVC web controller 또는 metrics scrape controller가 없다.
3. starter build dependencies에 MVP producer path용 Prometheus scrape/export/query dependency가 없다.
4. starter resources에는 scrape config, Prometheus target config, PromQL query profile이 없다.
5. starter code에는 arbitrary metric query UI 또는 query builder path가 없다.
6. envelope candidate model에는 free-form tag map, arbitrary custom metric map, raw timeseries array가 없다.
7. high-cardinality custom label ingestion path가 테스트로 차단된다.
8. Story 2.4 non-blocking test와 Story 2.5 contract tests가 guard 추가 후에도 통과한다.
9. portal dashboard read model, p95, state, rule, endpoint priority 계산을 이 story에 추가하지 않는다.
10. guard 결과와 MVP 제외 범위가 story completion notes에 남는다.

## Suggested Tasks

1. Epic 2 producer source/build/resource paths를 확인한다.
2. forbidden starter package guard를 추가한다.
3. starter web controller absence guard를 추가한다.
4. Prometheus/scrape/export/query dependency/resource absence guard를 추가한다.
5. arbitrary query UI/query builder absence guard를 추가한다.
6. envelope candidate model에서 free-form tag/custom metric/raw timeseries가 없는지 테스트한다.
7. high-cardinality custom label ingestion 차단 테스트를 추가한다.
8. Epic 2 producer tests를 함께 실행한다.
9. guard가 planning/archive 문서를 오탐하지 않도록 범위를 조정한다.
10. completion notes에 MVP 제외 범위를 기록한다.

## Test Requirements

- `NoPrometheusMvpPathTest`
- starter forbidden package architecture/static test
- starter web controller absence test
- envelope free-form tag/custom metric absence test
- high-cardinality custom label rejection test
- Story 2.4 non-blocking test
- Story 2.5 envelope contract test
- 권장 실행 명령: `./gradlew test`

## Developer Guardrails

- negative guard를 통과시키려고 planning docs나 archive docs를 삭제하지 않는다.
- future Prometheus integration을 MVP path에 다시 넣지 않는다.
- starter에 controller package나 web endpoint를 만들지 않는다.
- high-cardinality custom metric support를 extension point로 열어두지 않는다.
- UI/read model 계산 scope를 Epic 2로 당겨오지 않는다.
- guard test가 너무 넓어 정상적인 direct ingest code를 막지 않도록 active starter source/build/resource로 범위를 제한한다.

## Tasks/Subtasks

- [ ] Epic 2 producer source/build/resource paths를 확인한다.
- [ ] forbidden starter package guard를 추가한다.
- [ ] starter web controller absence guard를 추가한다.
- [ ] Prometheus/scrape/export/query dependency/resource absence guard를 추가한다.
- [ ] arbitrary query UI/query builder absence guard를 추가한다.
- [ ] envelope free-form tag/custom metric/raw timeseries absence test를 추가한다.
- [ ] high-cardinality custom label ingestion 차단 테스트를 추가한다.
- [ ] Epic 2 producer tests를 함께 실행한다.
- [ ] planning/archive 문서 오탐이 없는지 범위를 조정한다.
- [ ] completion notes에 MVP 제외 범위를 기록한다.

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
