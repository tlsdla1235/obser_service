---
artifactType: story
storyId: "2.4"
epic: "Epic 2. Starter Direct Ingest Producer"
title: "Async Flush Worker"
architectureStyle: Traditional MVC
status: ready-for-dev
date: 2026-05-10
---

# Story 2.4 - Async Flush Worker

## User Story

구현자로서, starter가 closed metric bucket을 bounded queue에 넣고 background worker에서 전송하게 만들어 portal timeout/down 상황에서도 host app request path가 막히지 않게 하고 싶다.

## Scope

이 story는 Epic 2의 핵심 non-blocking acceptance를 닫는다. Story 2.3의 closed bucket을 queue에 넣고 worker thread에서만 flush한다.

포함:

- in-memory bounded metric queue
- queue overflow drop policy
- background flush worker
- worker-local retry/backoff policy
- portal timeout/down fake client 기반 non-blocking proof test
- request path와 network call 분리 architecture guard

제외:

- durable outbox
- Kafka/Redis/별도 worker runtime
- final ingest envelope serialization
- portal ingest API/controller/repository
- accepted bucket persistence
- dashboard read model refresh
- p95/state/rule/endpoint priority 계산

## Source Artifacts

- `planning-artifacts/sprint-plan.md`
- `planning-artifacts/epics.md`
- `planning-artifacts/architecture.md`
- `planning-artifacts/architecture-implementation-supplement.md`
- `planning-artifacts/acceptance-traceability.md`
- `planning-artifacts/contracts/time-buckets.md`
- `planning-artifacts/contracts/ingest-envelope.md`
- `planning-artifacts/stories/2-3-bucket-rollup-service.md`

## Dependencies

- Story 2.3 provides closed bucket candidates.
- Story 2.5 finalizes envelope payload and idempotency header.
- Story 2.6 adds negative MVP path guard after producer path exists.

## Implementation Notes

- request path는 local record 또는 bounded queue enqueue 이상을 수행하지 않는다.
- HTTP timeout, retry, backoff는 worker thread 안에서만 실행한다.
- queue full 상황은 host business request 실패로 전파하지 않는다.
- drop policy는 bounded and observable하게 둔다. MVP에서는 durable guarantee보다 host app safety를 우선한다.
- worker는 fake/test client로 timeout/down behavior를 증명할 수 있어야 한다.
- final `ingest-envelope` shape는 Story 2.5에서 닫는다. 이 story의 client boundary는 worker 분리와 retry/backoff를 검증할 수 있을 정도로만 둔다.
- Story 2.4의 client boundary는 testable flush command 또는 placeholder payload를 받을 수 있지만, contract JSON serialization과 idempotency header 생성은 Story 2.5로 넘긴다.

## Acceptance Criteria

1. closed bucket flush candidate를 받을 bounded queue가 있다.
2. queue capacity는 finite value로 설정 가능하고 unbounded queue를 사용하지 않는다.
3. queue full 상황에서 configured drop policy가 적용되고 host request path는 예외/대기 없이 반환된다.
4. portal client 호출은 background worker에서만 수행된다.
5. request path code는 portal HTTP client 구현을 직접 호출하지 않는다.
6. fake portal client가 timeout/down 상태여도 request path call은 HTTP timeout을 기다리지 않는다.
7. retry/backoff는 worker-local behavior이며 request thread에 실행되지 않는다.
8. failed flush는 host business flow로 예외를 전파하지 않는다.
9. durable outbox, Kafka, Redis, 별도 worker deployable을 만들지 않는다.
10. 이 story에서는 portal ingest controller/repository, final envelope contract serialization, idempotency header generation을 구현하지 않는다.

## Suggested Tasks

1. Story 2.3 closed bucket model을 확인한다.
2. bounded queue model/component를 추가한다.
3. drop policy enum/value object를 추가한다.
4. background flush worker를 추가한다.
5. worker가 사용할 testable portal client boundary를 추가한다.
6. retry/backoff policy를 worker-local로 구현한다.
7. fake timeout/down client 기반 `StarterNonBlockingIngestTest`를 추가한다.
8. queue overflow test를 추가한다.
9. request path가 `client.http` 구현을 직접 호출하지 않는 guard를 추가한다.
10. 기존 starter/portal tests를 실행한다.

## Test Requirements

- `StarterNonBlockingIngestTest`
- `BoundedMetricQueueOverflowTest`
- `MetricBucketFlushWorkerTest`
- request path to `client.http` direct-call guard
- worker retry/backoff unit test
- 권장 실행 명령: `./gradlew test`

## Developer Guardrails

- wall-clock timing assertion만으로 non-blocking을 증명하지 않는다. request thread와 worker thread 분리, fake client blocking delay보다 빠른 request path return을 함께 확인한다.
- queue overflow를 host request failure로 만들지 않는다.
- retry/backoff를 request path에서 실행하지 않는다.
- final envelope builder를 과하게 당겨오지 않는다. Story 2.5에서 contract를 닫는다.
- Story 2.4에서 만든 worker/client boundary는 Story 2.5 builder output을 받을 수 있게 열어두되, JSON shape나 idempotency key 정책을 여기서 확정하지 않는다.
- durable delivery를 목표로 outbox/Kafka/Redis를 추가하지 않는다.
- portal 저장/idempotency는 Epic 3으로 넘긴다.

## Tasks/Subtasks

- [ ] Story 2.3 closed bucket model을 확인한다.
- [ ] bounded queue model/component를 추가한다.
- [ ] drop policy enum/value object를 추가한다.
- [ ] background flush worker를 추가한다.
- [ ] testable portal client boundary를 추가한다.
- [ ] retry/backoff policy를 worker-local로 구현한다.
- [ ] fake timeout/down client 기반 non-blocking test를 추가한다.
- [ ] queue overflow test를 추가한다.
- [ ] request path direct client call guard를 추가한다.
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
