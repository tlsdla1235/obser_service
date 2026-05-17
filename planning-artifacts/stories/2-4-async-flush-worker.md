---
artifactType: story
storyId: "2.4"
epic: "Epic 2. Starter Direct Ingest Producer"
title: "Async Flush Worker"
architectureStyle: Traditional MVC
status: review
date: 2026-05-10
---

# Story 2.4 - Async Flush Worker

## User Story

구현자로서, starter가 grace 이후 drain 대상이 된 sealed metric bucket을 bounded queue에 넣고 background worker에서 전송하게 만들어 portal timeout/down 상황에서도 host app request path가 막히지 않게 하고 싶다.

## Scope

이 story는 Epic 2의 핵심 non-blocking acceptance를 닫는다. Story 2.3의 drain-eligible sealed bucket을 queue에 넣고 worker thread에서만 flush한다.

포함:

- in-memory bounded metric queue
- queue overflow drop policy
- background flush worker
- 새 샘플이 없는 idle 상태에서도 due bucket을 queue에 넣을 수 있는 scheduled/tick drain 경계
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

- Story 2.3 provides drain-eligible sealed bucket candidates after the 1 bucket grace window.
- Story 2.5 finalizes envelope payload and idempotency header.
- Story 2.6 adds negative MVP path guard after producer path exists.

## Implementation Notes

- request path는 local record 또는 bounded queue enqueue 이상을 수행하지 않는다.
- request path는 샘플 기록 후 drain/enqueue를 수행할 수 있지만, 새 샘플이 없어도 grace 이후 due bucket을 queue에 넣을 수 있도록 scheduled/tick drain 경계를 둔다.
- drain 대상 bucket은 Story 2.3 정책에 따라 `bucket.endUtc + bucketDuration <= nowUtc`인 interval뿐이다.
- HTTP timeout, retry, backoff는 worker thread 안에서만 실행한다.
- queue full 상황은 host business request 실패로 전파하지 않는다.
- drop policy는 bounded and observable하게 둔다. MVP에서는 durable guarantee보다 host app safety를 우선한다.
- worker는 fake/test client로 timeout/down behavior를 증명할 수 있어야 한다.
- final `ingest-envelope` shape는 Story 2.5에서 닫는다. 이 story의 client boundary는 worker 분리와 retry/backoff를 검증할 수 있을 정도로만 둔다.
- Story 2.4의 client boundary는 testable flush command 또는 placeholder payload를 받을 수 있지만, contract JSON serialization과 idempotency header 생성은 Story 2.5로 넘긴다.

## Acceptance Criteria

1. drain-eligible sealed bucket flush candidate를 받을 bounded queue가 있다.
2. queue capacity는 finite value로 설정 가능하고 unbounded queue를 사용하지 않는다.
3. queue full 상황에서 configured drop policy가 적용되고 host request path는 예외/대기 없이 반환된다.
4. portal client 호출은 background worker에서만 수행된다.
5. request path code는 portal HTTP client 구현을 직접 호출하지 않는다.
6. fake portal client가 timeout/down 상태여도 request path call은 HTTP timeout을 기다리지 않는다.
7. retry/backoff는 worker-local behavior이며 request thread에 실행되지 않는다.
8. failed flush는 host business flow로 예외를 전파하지 않는다.
9. 새 샘플이 없는 idle 상태에서도 scheduled/tick drain이 `bucket.endUtc + bucketDuration <= nowUtc`인 due bucket을 queue에 넣을 수 있다.
10. durable outbox, Kafka, Redis, 별도 worker deployable을 만들지 않는다.
11. 이 story에서는 portal ingest controller/repository, final envelope contract serialization, idempotency header generation을 구현하지 않는다.

## Suggested Tasks

1. Story 2.3 drain-eligible sealed bucket model과 1 bucket grace 정책을 확인한다.
2. bounded queue model/component를 추가한다.
3. drop policy enum/value object를 추가한다.
4. background flush worker를 추가한다.
5. worker가 사용할 testable portal client boundary를 추가한다.
6. retry/backoff policy를 worker-local로 구현한다.
7. request path record 후 drain/enqueue 경계를 추가하되 portal client boundary는 알지 못하게 둔다.
8. 새 샘플이 없는 idle 상태에서도 scheduled/tick drain이 due bucket을 enqueue하는 경계를 추가한다.
9. fake timeout/down client 기반 `StarterNonBlockingIngestTest`를 추가한다.
10. queue overflow test를 추가한다.
11. request path가 `client.http` 구현을 직접 호출하지 않는 guard를 추가한다.
12. 기존 starter/portal tests를 실행한다.

## Test Requirements

- `StarterNonBlockingIngestTest`
- `BoundedMetricQueueOverflowTest`
- `MetricBucketFlushWorkerTest`
- request path to `client.http` direct-call guard
- worker retry/backoff unit test
- scheduled/tick drain이 새 샘플 없이도 grace 이후 due bucket을 enqueue하는 test
- request path drain/enqueue가 final envelope serialization/idempotency header generation을 수행하지 않는 guard
- 권장 실행 명령: `./gradlew test`

## Developer Guardrails

- wall-clock timing assertion만으로 non-blocking을 증명하지 않는다. request thread와 worker thread 분리, fake client blocking delay보다 빠른 request path return을 함께 확인한다.
- queue overflow를 host request failure로 만들지 않는다.
- retry/backoff를 request path에서 실행하지 않는다.
- sample-triggered request path drain만으로 due bucket enqueue를 보장했다고 간주하지 않는다. idle 상태에서도 scheduled/tick drain이 due bucket을 queue에 넣을 수 있어야 한다.
- Story 2.4는 drain-eligible sealed bucket을 queue로 넘기는 경계까지만 담당하며, 같은 `bucket.startUtc` duplicate envelope candidate 방지는 Story 2.3/2.5 정책과 맞물리도록 보존한다.
- final envelope builder를 과하게 당겨오지 않는다. Story 2.5에서 contract를 닫는다.
- Story 2.4에서 만든 worker/client boundary는 Story 2.5 builder output을 받을 수 있게 열어두되, JSON shape나 idempotency key 정책을 여기서 확정하지 않는다.
- durable delivery를 목표로 outbox/Kafka/Redis를 추가하지 않는다.
- portal 저장/idempotency는 Epic 3으로 넘긴다.

## Tasks/Subtasks

- [x] Story 2.3 drain-eligible sealed bucket model을 확인한다.
- [x] bounded queue model/component를 추가한다.
- [x] drop policy enum/value object를 추가한다.
- [x] background flush worker를 추가한다.
- [x] testable portal client boundary를 추가한다.
- [x] retry/backoff policy를 worker-local로 구현한다.
- [x] fake timeout/down client 기반 non-blocking test를 추가한다.
- [x] queue overflow test를 추가한다.
- [x] request path direct client call guard를 추가한다.
- [x] 기존 starter/portal tests를 실행한다.
- [x] 정책 변경 후 scheduled/tick drain이 idle 상태의 due bucket을 enqueue하는 requirement와 test를 확인한다.

### Review Findings

- [x] [Review][Policy Follow-up] correct-course 정책 보정으로 scheduled/tick drain 경계가 새 AC로 추가되었다. `StarterMetricIngestService.drainDueBuckets()` 구현과 테스트로 새 샘플이 없는 idle 상태에서도 grace 이후 due bucket이 enqueue되는 것을 확인했다.

## Dev Agent Record

### Implementation Plan

- Story 2.3의 grace 이후 drain된 sealed `ClosedMetricBucket` snapshot을 그대로 소비하는 bounded in-memory queue를 추가한다.
- queue capacity는 생성자에서 finite positive value로 받고, overflow 시 `DROP_NEWEST` 또는 `DROP_OLDEST` 정책을 적용한다.
- request path용 `StarterMetricIngestService`는 guard/rollup/drain/enqueue까지만 수행하고 portal client boundary를 알지 못하게 둔다.
- correct-course 정책 보정 후 scheduled/tick drain 경계는 새 샘플이 없는 idle 상태에서도 grace 이후 due bucket을 enqueue할 수 있도록 `StarterMetricIngestService.drainDueBuckets()`로 구현한다.
- `MetricBucketFlushWorker`는 background thread에서 queue를 poll하고 `PortalMetricBucketClient`를 호출한다.
- retry/backoff는 `MetricFlushRetryPolicy`와 `MetricFlushBackoff`로 worker 내부에 격리하고, 최종 실패는 host flow로 전파하지 않는다.
- final ingest envelope JSON serialization, idempotency header, portal ingest controller/repository/persistence는 구현하지 않는다.

### Debug Log

- 2026-05-17: `AGENTS.md`, `_bmad/custom/project-context.md`, sprint status, Story 2.4/2.3, `time-buckets`, `ingest-envelope`, `starter-failure-semantics` 계약을 확인했다.
- 2026-05-17: Story 2.4 sprint status를 `ready-for-dev`에서 `in-progress`로 갱신했다.
- 2026-05-17: Red phase로 `StarterNonBlockingIngestTest`, `BoundedMetricQueueOverflowTest`, `MetricBucketFlushWorkerTest`, request path architecture guard를 먼저 추가했고, 미구현 타입 때문에 targeted test compile failure가 발생함을 확인했다.
- 2026-05-17: bounded queue/drop policy, portal client boundary, background flush worker, worker-local retry/backoff, request path ingest service를 구현했다.
- 2026-05-17: targeted test 실행 결과 `./gradlew :observability-spring-boot-starter:test --tests com.observation.starter.queue.BoundedMetricQueueOverflowTest --tests com.observation.starter.service.MetricBucketFlushWorkerTest --tests com.observation.starter.service.StarterNonBlockingIngestTest --tests com.observation.starter.architecture.StarterObservationArchitectureTest --rerun-tasks`가 `BUILD SUCCESSFUL`로 통과했다.
- 2026-05-17: starter 전체 테스트 `./gradlew :observability-spring-boot-starter:test --rerun-tasks` 실행 결과 `BUILD SUCCESSFUL`, 4 actionable tasks executed.
- 2026-05-17: 전체 테스트 `./gradlew test --rerun-tasks` 실행 결과 `BUILD SUCCESSFUL`, 8 actionable tasks executed.
- 2026-05-17: Story 2.4와 sprint status를 `review`로 갱신했다.
- 2026-05-17: correct-course 후속 구현으로 `StarterMetricIngestService.drainDueBuckets()` scheduler/tick 경계를 추가하고, 2.3 grace drain semantics에 맞춰 non-blocking/overflow/idle tick 테스트와 architecture guard를 갱신했다.
- 2026-05-17: targeted test `./gradlew :observability-spring-boot-starter:test --tests com.observation.starter.service.StarterNonBlockingIngestTest --tests com.observation.starter.service.MetricBucketFlushWorkerTest --tests com.observation.starter.queue.BoundedMetricQueueOverflowTest --tests com.observation.starter.architecture.StarterObservationArchitectureTest --rerun-tasks` 실행 결과 `BUILD SUCCESSFUL`을 확인했다.
- 2026-05-17: starter 전체 테스트 `./gradlew :observability-spring-boot-starter:test --rerun-tasks` 실행 결과 `BUILD SUCCESSFUL`, 전체 테스트 `./gradlew test --rerun-tasks` 실행 결과 `BUILD SUCCESSFUL`, `git diff --check` 통과를 확인했다.

### Completion Notes

- `BoundedMetricQueue`는 `ArrayBlockingQueue` 기반 finite capacity queue이며, full 상태에서 `DROP_NEWEST` 또는 `DROP_OLDEST`를 적용하고 즉시 반환한다.
- `StarterMetricIngestService`는 `ObservationSampleCollector` 구현으로 guard/rollup 후 drain-eligible sealed bucket만 queue에 넣으며, portal client 또는 `client.http` 구현을 호출하지 않는다.
- `StarterMetricIngestService.drainDueBuckets()`는 scheduler/tick 경계에서 `nowUtcSupplier` 기준으로 `bucket.endUtc + bucketDuration <= nowUtc`인 due bucket만 queue에 넣으며, 새 샘플이 없는 idle 상태에서도 호출할 수 있다.
- grace 이전 request path 또는 scheduled/tick drain 호출은 queue에 bucket을 넣지 않는다.
- `MetricBucketFlushWorker`는 daemon background thread에서만 `PortalMetricBucketClient.flush`를 호출하고, retry/backoff와 최종 실패를 worker-local로 처리한다. 현재 wait loop는 `BlockingQueue.poll(timeout)` 기반 timeout 있는 blocking wait이며 CPU busy waiting은 아니다.
- fake timeout/down client 기반 non-blocking test는 request thread 반환, worker thread client 호출, blocking client release 전 request completion을 함께 확인한다.
- architecture guard는 request path의 `client.http`/HTTP transport 직접 의존과 worker 외부의 portal client boundary 의존을 막는다.
- durable outbox, Kafka, Redis, 별도 worker deployable, portal ingest controller/repository/persistence, final envelope serialization, idempotency header generation은 추가하지 않았다.
- Post-MVP worker wake-up/shutdown 전략 비교(`poll(timeout)` vs `take() + interrupt` 등)는 `planning-artifacts/epics.md`의 `Starter Flush Worker Wake-up Strategy` 후보 backlog로 남겼다.

### File List

- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/stories/2-4-async-flush-worker.md`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/client/package-info.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/client/PortalMetricBucketClient.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/queue/package-info.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/queue/BoundedMetricQueue.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/queue/MetricQueueDropPolicy.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/queue/MetricQueueOfferOutcome.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/queue/MetricQueueOfferResult.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/service/MetricBucketFlushWorker.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/service/MetricFlushBackoff.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/service/MetricFlushRetryPolicy.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/service/StarterMetricIngestService.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/architecture/StarterObservationArchitectureTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/queue/BoundedMetricQueueOverflowTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/service/MetricBucketFlushWorkerTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/service/StarterNonBlockingIngestTest.java`

## Change Log

- 2026-05-17: Story 2.4 implementation completed; bounded flush queue, overflow drop policy, background worker, worker-local retry/backoff, portal client boundary, request path enqueue service, non-blocking/overflow/retry/architecture tests added.
- 2026-05-17: Correct-course 정책 변경에 따라 이전 닫힘 표현을 drain-eligible sealed bucket으로 정렬하고, idle 상태에서도 scheduled/tick drain이 grace 이후 due bucket을 enqueue해야 한다는 AC/guardrail/test requirement를 추가했다. Final envelope serialization과 idempotency header generation은 계속 Story 2.5 scope로 둔다.
- 2026-05-17: Correct-course 후속 구현 완료; `drainDueBuckets()` scheduler/tick 경계를 추가하고, `01:00:00Z-01:00:30Z` bucket이 `01:00:31Z`에는 enqueue되지 않고 `01:01:00Z`부터 enqueue되는 테스트로 정렬했다.
- 2026-05-17: Worker idle 대기는 현재 `BlockingQueue.poll(timeout)` 기반 blocking wait로 기록하고, 더 정교한 wake-up/shutdown 전략은 Post-MVP backlog로 분리했다.

## Status

review
