---
artifactType: story
storyId: "2.3"
epic: "Epic 2. Starter Direct Ingest Producer"
title: "Bucket Rollup Service"
architectureStyle: Traditional MVC
status: done
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
- 1 bucket grace 이후 drain 대상이 된 bucket을 flush candidate로 만드는 service 경계

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
- Story 2.4 consumes drain-eligible sealed buckets through bounded queue/worker.
- Story 2.5 serializes grace 이후 drain된 sealed buckets into ingest envelope.

## Implementation Notes

- bucket duration은 30초로 고정한다.
- MVP drain grace window는 1 bucket duration, 즉 30초로 고정한다.
- bucket boundary는 UTC 기준 `:00` 또는 `:30` 초에 정렬한다.
- bucket interval은 `[startUtc, endUtc)` semantics를 따른다.
- `drainClosedBuckets(nowUtc)`는 `bucket.endUtc + bucketDuration <= nowUtc`인 interval만 flush candidate로 반환한다.
- grace window 안에 도착한 HTTP/JVM/datasource late sample은 기존 bucket에 정상 집계한다.
- drain 이후 interval은 sealed로 간주하며, sealed interval에 이후 도착한 HTTP/JVM/datasource sample은 drop하고 같은 `bucket.startUtc`의 duplicate flush candidate를 만들지 않는다.
- sealed watermark는 단조 증가해야 한다.
- endpoint key는 `method + normalized route`다.
- app-level histogram과 endpoint-level histogram은 cumulative count shape로 유지한다.
- JVM/datasource ratio는 MVP에서 bucket 안의 latest valid sample로 둔다. absent sample은 host request processing을 막지 않는다.
- Post-MVP에서 latest/max/avg/sampleCount runtime aggregate를 추가하려면 `metric-taxonomy`와 `ingest-envelope`의 Runtime Gauge Aggregate Extension 후보를 별도 story로 구현한다.
- p95는 starter에서 계산하지 않는다. server-side histogram merge는 Epic 5 scope다.

## Acceptance Criteria

1. `Instant` input은 UTC 30초 boundary의 `startUtc`와 `endUtc`로 매핑된다.
2. `01:00:00Z`와 `01:00:29.999Z` sample은 `01:00:00Z-01:00:30Z` bucket에 속한다.
3. `01:00:30Z` sample은 `01:00:30Z-01:01:00Z` bucket에 속한다.
4. app-level request count와 error count가 bucket 단위로 집계된다.
5. endpoint-level request count와 error count가 `method + normalized route` 단위로 집계된다.
6. duration histogram bucket은 cumulative count 형태를 유지한다.
7. rollup service는 `method + normalized route`만 endpoint key로 사용하며 raw path, raw path candidate, query string, high-cardinality tag, attribution raw detail을 입력이나 key로 받지 않는다.
8. `drainClosedBuckets(nowUtc)`는 `bucket.endUtc + bucketDuration <= nowUtc`인 interval만 flush candidate로 반환한다.
9. grace window 안에 도착한 HTTP/JVM/datasource late sample은 기존 bucket에 포함된다.
10. drain 이후 같은 interval sample은 drop되며 duplicate flush candidate를 만들지 않는다.
11. sealed watermark는 단조 증가한다.
12. 이 story에서는 network call, HTTP client, queue worker, envelope builder를 구현하지 않는다.
13. p95, lifecycle state, insight rule, endpoint priority 계산을 구현하지 않는다.

## Suggested Tasks

1. Story 2.2 normalized route model과 metric taxonomy를 확인한다.
2. UTC bucket boundary utility/model을 추가한다.
3. app-level rollup model을 추가한다.
4. endpoint-level histogram rollup model을 추가한다.
5. `MetricBucketRollupService`를 추가한다.
6. sample record와 grace 이후 drain-eligible bucket retrieval 경계를 구현한다.
7. cumulative histogram bucket count 테스트를 추가한다.
8. UTC boundary edge case 테스트를 추가한다.
9. raw path/high-cardinality input이 rollup으로 들어오지 못하는 guard를 확인한다.
10. 기존 starter/portal tests를 실행한다.

## Test Requirements

- UTC 30초 boundary edge case test
- `[startUtc, endUtc)` bucket inclusion test
- app summary rollup test
- endpoint histogram cumulative count test
- grace window 안의 late sample inclusion test
- drain 이후 sealed interval late sample drop test
- 동일 `bucket.startUtc` duplicate flush candidate prevention test
- sealed watermark monotonicity test
- normalized route only input test
- no p95/state/rule calculation guard
- 권장 실행 명령: `./gradlew test`

## Developer Guardrails

- local p95를 starter에서 계산하지 않는다.
- bucket duration을 config로 열더라도 MVP default와 acceptance는 30초로 고정한다.
- MVP drain grace window도 1 bucket duration, 즉 30초로 고정한다.
- `drainClosedBuckets(nowUtc)`를 bucket end 도달 즉시 drain으로 해석하지 않는다.
- JVM/datasource sample 부재를 host request failure로 만들지 않는다.
- Post-MVP runtime aggregate 후보를 MVP `schemaVersion: 1.0` payload에 섞지 않는다.
- sealed interval을 다시 열거나 같은 `bucket.startUtc`의 두 번째 flush candidate를 만들지 않는다.
- raw path, raw path candidate, query string, attribution raw detail, arbitrary label을 rollup input이나 key로 사용하지 않는다.
- queue, HTTP client, envelope builder를 당겨오지 않는다.
- portal accepted bucket storage는 Epic 3에서 구현한다.

## Tasks/Subtasks

- [x] Story 2.2 normalized route model과 metric taxonomy를 확인한다.
- [x] UTC bucket boundary utility/model을 추가한다.
- [x] app-level rollup model을 추가한다.
- [x] endpoint-level histogram rollup model을 추가한다.
- [x] `MetricBucketRollupService`를 추가한다.
- [x] sample record와 기존 drain retrieval 경계를 구현한다.
- [x] 정책 변경 후 grace 이후 drain eligibility와 late sample inclusion 경계를 구현한다.
- [x] cumulative histogram bucket count 테스트를 추가한다.
- [x] UTC boundary edge case 테스트를 추가한다.
- [x] raw path/high-cardinality input guard를 확인한다.
- [x] 기존 starter/portal tests를 실행한다.

### Review Findings

- [x] [Review][Decision] 닫힌 bucket drain 이후 같은 interval의 늦은 샘플 처리 정책 필요 — MVP 정책은 `1 bucket drain grace window + sealed interval + late sample drop`으로 정렬한다. 목표 정책상 `drainClosedBuckets(nowUtc)`는 `bucket.endUtc + bucketDuration <= nowUtc`인 interval만 flush candidate로 반환하며, grace window 안에 도착한 HTTP/JVM/datasource late sample은 기존 bucket에 포함한다. 한 번 drain된 interval은 sealed로 간주하고 sealed watermark를 단조 증가시킨다. 이후 같은 interval sample은 새 bucket을 재생성하지 않고 drop하며, 동일 `bucket.startUtc`의 두 번째 flush candidate는 만들지 않는다.
- [x] [Review][Policy Follow-up] correct-course 정책 보정 후 drain eligibility를 `bucket.endUtc + bucketDuration <= nowUtc`로 변경했고, grace window 안의 HTTP/JVM/datasource late sample inclusion test를 추가했다.

## Dev Agent Record

### Implementation Plan

- Story 2.2의 `LowCardinalityHttpServerObservation`, `EndpointKey`, `NormalizedRoute` public contract를 rollup 입력 경계로 사용한다.
- `MetricBucketInterval`로 UTC 30초 bucket boundary와 `[startUtc, endUtc)` 포함 의미를 고정한다.
- `AppMetricRollup`, `EndpointMetricRollup`, `HistogramBucket`, `ClosedMetricBucket` 모델은 app/endpoint 누적 histogram snapshot과 drain된 bucket flush candidate 경계를 표현한다.
- correct-course 정책 보정 후 `MetricBucketRollupService`는 low-cardinality HTTP observation과 JVM/datasource ratio sample만 기록하고, `bucket.endUtc + bucketDuration <= nowUtc`인 interval만 `drainClosedBuckets`로 반환한다.
- network call, HTTP client, queue worker, ingest envelope builder, p95/lifecycle/insight/endpoint priority 계산은 추가하지 않고 테스트/architecture guard로 경계를 고정한다.

### Debug Log

- 2026-05-14: `_bmad/custom/project-context.md`, sprint-status, Story 2.2, `time-buckets`, `metric-taxonomy`, `ingest-envelope` 계약을 확인했다.
- 2026-05-14: Story 2.3 및 sprint-status를 `in-progress`로 갱신했다.
- 2026-05-14: Red phase로 `MetricBucketRollupServiceTest`를 먼저 추가했고, 아직 구현되지 않은 rollup/time/model 타입 때문에 `./gradlew :observability-spring-boot-starter:test --tests com.observation.starter.service.MetricBucketRollupServiceTest --rerun-tasks`가 compile failure로 실패함을 확인했다.
- 2026-05-14: `MetricBucketInterval`, `HistogramBucket`, app/endpoint/`ClosedMetricBucket` rollup 모델, `MetricBucketRollupService`를 추가했다.
- 2026-05-14: `./gradlew :observability-spring-boot-starter:test --tests com.observation.starter.service.MetricBucketRollupServiceTest --tests com.observation.starter.architecture.StarterObservationArchitectureTest --rerun-tasks` 실행 결과 `BUILD SUCCESSFUL`, 4 actionable tasks executed.
- 2026-05-14: starter 전체 테스트 중 이전 build output의 `*Test 2.class` 산출물 때문에 테스트 런처가 실패함을 확인했고, 소스 중복이 아닌 build 산출물 문제라 `./gradlew :observability-spring-boot-starter:clean :observability-spring-boot-starter:test`로 정리 후 `BUILD SUCCESSFUL`, 5 actionable tasks executed를 확인했다.
- 2026-05-14: `./gradlew test` 실행 결과 `BUILD SUCCESSFUL`, 8 actionable tasks up-to-date.
- 2026-05-14: `./gradlew test --rerun-tasks` 실행 결과 `BUILD SUCCESSFUL`, 8 actionable tasks executed.
- 2026-05-14: `git diff --check` 실행 결과 문제 없음.
- 2026-05-17: review decision에 따라 sealed interval + late sample drop 정책을 구현했다. 이후 correct-course 정책 변경으로 drain eligibility 의미를 `endUtc` 즉시 drain에서 `endUtc + 1 bucket duration` grace 이후 drain으로 문서 보정했으며, 후속 구현에서 이 목표 정책을 닫았다. `./gradlew :observability-spring-boot-starter:test --tests com.observation.starter.service.MetricBucketRollupServiceTest --rerun-tasks` 실행 결과 `BUILD SUCCESSFUL`, 4 actionable tasks executed.
- 2026-05-17: `MetricBucketRollupService`의 drain predicate를 `bucket.endUtc + bucketDuration <= nowUtc`로 변경하고, grace window 안 late HTTP/JVM/datasource sample 집계와 `endUtc` 즉시 drain 방지 테스트를 추가했다. `./gradlew :observability-spring-boot-starter:test --tests com.observation.starter.service.MetricBucketRollupServiceTest --rerun-tasks` 실행 결과 `BUILD SUCCESSFUL`, 4 actionable tasks executed.

### Completion Notes

- UTC 30초 bucket boundary는 `MetricBucketInterval.containing`에서 epoch second 기준으로 계산하며, `01:00:00Z`와 `01:00:29.999Z`는 `01:00:00Z-01:00:30Z`, `01:00:30Z`는 다음 bucket으로 매핑된다.
- app-level rollup은 request/error count와 cumulative HTTP duration histogram bucket을 집계한다.
- endpoint-level rollup은 `EndpointKey(method + normalized route)`만 key로 사용하며, raw path, query string, high-cardinality tag, raw attribution detail을 입력이나 key로 받지 않는다.
- JVM CPU/heap 및 datasource pool usage ratio는 bucket 안의 latest valid sample로 snapshot에 담고, sample 부재는 request rollup을 막지 않는다.
- correct-course 목표 정책상 `drainClosedBuckets(nowUtc)`는 `bucket.endUtc + bucketDuration <= nowUtc`인 interval만 flush candidate snapshot으로 반환한다.
- grace window 안의 late HTTP/JVM/datasource sample은 기존 bucket에 포함된다.
- 한 번 drain된 interval은 sealed로 유지하고 sealed watermark는 단조 증가하며, 같은 interval의 late HTTP/JVM/datasource sample은 duplicate flush candidate를 만들지 않도록 drop한다.
- late sample drop은 `lateSampleDroppedCount()`로 확인할 수 있다.
- network call, HTTP client, queue worker, ingest envelope builder, p95/lifecycle/insight/endpoint priority 계산은 구현하지 않았다.

### File List

- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/stories/2-3-bucket-rollup-service.md`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/time/package-info.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/time/MetricBucketInterval.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/metric/HistogramBucket.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/metric/AppMetricRollup.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/metric/EndpointMetricRollup.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/metric/ClosedMetricBucket.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/service/MetricBucketRollupService.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/service/MetricBucketRollupServiceTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/architecture/StarterObservationArchitectureTest.java`

## Change Log

- 2026-05-14: Story 2.3 implementation completed; UTC 30초 bucket boundary, app/endpoint cumulative histogram rollup, latest runtime ratio sample boundary, drain candidate boundary, and guard tests added.
- 2026-05-17: Original review finding resolved for sealed interval watermark and late sample drop counter; correct-course grace window policy follow-up is now implemented.
- 2026-05-17: Correct-course 정책 변경에 따라 MVP drain eligibility를 `bucket.endUtc + bucketDuration <= nowUtc`로 구현하고, grace window 안의 late sample을 정상 집계하도록 후속 구현을 완료했다.

## Status

done
