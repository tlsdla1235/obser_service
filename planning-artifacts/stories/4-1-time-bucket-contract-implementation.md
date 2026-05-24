---
artifactType: story
storyId: "4.1"
epic: "Epic 4. State Semantics and Time Windows"
title: "Time Bucket Contract Implementation"
architectureStyle: Traditional MVC
status: done
date: 2026-05-24
---

# Story 4.1 - Time Bucket Contract Implementation

## User Story

portal 구현자로서, dashboard/state/read-model service가 같은 UTC 30초 bucket과 15분 current/baseline window 계산을 재사용하길 원한다.

그래야 stale/down/current/baseline 의미가 controller, UI, repository마다 다시 계산되지 않고, 후속 `LifecycleStateService`와 `DashboardReadModelService`가 잠긴 contract를 같은 방식으로 사용할 수 있다.

## Scope

포함:

- portal service/model 계층에서 공유할 time bucket/window 계산 모델 또는 utility
- UTC 기준 30초 bucket boundary 계산
- `[startUtc, endUtc)` interval semantics 모델링
- dashboard query 시점 기준 recent 15분 current window 계산
- current 바로 이전 15분 baseline window 계산
- 마지막 accepted bucket `endUtc` 기반 freshness 계산
- injectable `Clock` 또는 기존 패턴에 맞는 UTC clock bean
- repository의 마지막 accepted bucket `endUtc` 조회 메서드
- focused unit tests

제외:

- 새 contract gate 작성
- `LifecycleStateService` 전체 구현
- recovery guidance 구현
- degraded enter/resolve hysteresis 구현
- dashboard read model/API 구현
- snapshot persistence 또는 operational event history 구현
- heartbeat를 freshness/state source로 연결하는 것
- p95/p99, histogram merge, endpoint priority, insight rule 계산

## Acceptance Criteria

1. portal service/model 계층에서 공유 가능한 time bucket/window 모델 또는 utility가 추가된다.
2. 모든 bucket 계산은 UTC 30초 boundary를 사용하고 `[startUtc, endUtc)` interval semantics를 명시한다.
3. query 시점 기준 current window는 최근 15분이며, baseline window는 current 바로 이전 15분이다.
4. current/baseline window 계산은 `Clock` 또는 UTC clock bean을 통해 테스트 가능한 query 시각을 사용한다.
5. freshness 계산은 starter heartbeat가 아니라 마지막 accepted bucket의 `endUtc`만 입력으로 사용한다.
6. freshness threshold는 stale 후보 90초 이상, down 후보 180초 이상을 표현하되, lifecycle state enum 판정 전체는 Story 4.2로 남긴다.
7. repository는 application scope 마지막 accepted bucket `endUtc`를 저장/조회할 수 있게만 확장하고 freshness/state 의미를 판단하지 않는다.
8. controller/UI 경계에는 stale/down/current/baseline 재판정 로직이 추가되지 않는다.
9. focused unit tests가 UTC boundary, interval 포함/제외, current/baseline window, injectable clock, freshness threshold edge를 검증한다.
10. 기존 ingest/bucket repository/service 테스트가 회귀 없이 통과한다.

## Tasks/Subtasks

- [x] 공유 time model과 UTC clock 경계 구현 (AC: 1, 2, 3, 4)
  - [x] `common.time` 또는 service/model 계층에 30초 bucket duration, 15분 window, UTC instant 계산 모델을 추가한다.
  - [x] current window와 baseline window를 query 시점 기준으로 계산하되 window interval은 `[startUtc, endUtc)`로 고정한다.
  - [x] system clock은 injectable `Clock` bean으로 두고 production 기본값은 UTC를 사용한다.
- [x] freshness 계산 모델 구현 (AC: 5, 6, 8)
  - [x] 마지막 accepted bucket `endUtc`와 query 시각으로 freshness age를 계산한다.
  - [x] `current`, `stale_candidate`, `down_candidate`, `waiting_first_data` 후보 수준만 표현하고 최종 lifecycle state 판정은 구현하지 않는다.
  - [x] heartbeat 입력, starter liveness, controller/UI 재판정 로직을 추가하지 않는다.
- [x] repository timestamp 조회 경계 추가 (AC: 7, 8)
  - [x] `MetricBucketRepository`에 application scope 마지막 accepted bucket `endUtc` 조회 메서드를 추가한다.
  - [x] Spring Data JPA repository는 timestamp 조회만 수행하고 freshness/state 판단을 하지 않는다.
  - [x] 기존 insert/idempotency/catalog 동작을 보존한다.
- [x] focused tests와 회귀 확인 (AC: 1-10)
  - [x] time bucket/window/freshness unit tests를 추가한다.
  - [x] repository timestamp 조회가 마지막 accepted bucket `endUtc`를 반환하는지 integration test를 확장한다.
  - [x] `./gradlew :observability-portal:test`와 필요 시 `./gradlew test`를 실행한다.

## Dev Notes

- Active baseline은 Traditional MVC + Service/Repository Layering이다.
- `domain`은 DDD 순수 domain layer가 아니라 feature-first MVC namespace다.
- time boundary 계산은 `DashboardReadModelService`, `LifecycleStateService`, `HistogramMergeService`가 공유할 수 있는 time model/utility에서 수행해야 한다.
- `observability-portal/src/main/java/com/observation/portal/common/time/package-info.java`가 이미 UTC/bucket helper package marker로 존재한다. 새 공통 time utility는 이 위치를 우선 검토한다.
- 계산 class 이름이 `LifecycleState`, `InsightRule`, `EndpointPriority`, `P95` 패턴에 걸리면 `service` 또는 `model` package에 있어야 한다는 ArchUnit guard가 있다.
- repository 구현 표준은 PostgreSQL 위의 Spring Data JPA/Jakarta Persistence + Hibernate다.
- repository는 timestamp 저장/조회까지만 맡고 freshness/state 의미 판단을 하지 않는다.
- JPA entity는 persistence model이며 controller DTO, public API surface, service result/external return model로 직접 반환하지 않는다.
- existing accepted bucket persistence는 `MetricBucketRepository`, `AcceptedMetricBucketJpaRepository`, `AcceptedMetricBucketEntity`, `AcceptedMetricBucketWriteCommand`를 사용한다.
- `IngestAcceptanceService`는 현재 `OffsetDateTime.now(ZoneOffset.UTC)`를 직접 사용한다. Story 4.1 범위에서는 production clock bean을 추가할 수 있지만 ingest accepted timestamp semantics를 불필요하게 바꾸지 않는다.
- freshness source는 `accepted_metric_buckets.bucket_end_utc`다. `accepted_at`, starter heartbeat, heartbeat telemetry, UI local time은 source가 아니다.
- `current`와 `baseline`은 dashboard query 시점 기준이다. snapshot/history horizon과 섞지 않는다.
- baseline이 충분하지 않으면 comparative rule을 끄는 판단은 후속 insight/lifecycle story 범위다. 이 story는 window interval을 제공하는 데 그친다.
- `stale_candidate`는 마지막 accepted bucket `endUtc`가 query 시점보다 90초 이상 과거일 때, `down_candidate`는 180초 이상 과거일 때를 표현한다. `down` 우선순위 같은 최종 state ordering은 Story 4.2에서 닫는다.
- controller와 UI package에는 current/baseline/stale/down 계산을 추가하지 않는다.

## Source References

- `planning-artifacts/epics.md` - Epic 4 Story 4.1
- `planning-artifacts/contracts/time-buckets.md` - bucket duration, UTC boundary, current/baseline, freshness source, MVC boundary
- `planning-artifacts/contracts/state-semantics.md` - state source-of-truth, freshness thresholds, heartbeat exclusion
- `planning-artifacts/contracts/ingest-envelope.md` - accepted bucket contract and controller/service/repository boundary
- `planning-artifacts/stories/4-0-starter-heartbeat-and-instance-level-ingest-contract-reassessment.md` - heartbeat and starter percentile guardrails
- `_bmad/custom/project-context.md` - MVC + Spring Data JPA implementation policy
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java`
- `observability-portal/src/test/java/com/observation/portal/domain/bucket/repository/MetricBucketRepositoryIntegrationTest.java`

## Test Requirements

- `TimeBucketWindowCalculatorTest` 또는 동등한 focused unit test
  - query 시각이 30초 boundary에 있지 않아도 current window end는 query 시각 자체로 유지되는지 검토한다.
  - current window가 `[queryAt - 15m, queryAt)`이고 baseline이 `[queryAt - 30m, queryAt - 15m)`인지 검증한다.
  - interval contains가 start inclusive, end exclusive인지 검증한다.
  - 30초 boundary floor/next calculation이 UTC epoch 기준으로 동작하는지 검증한다.
- `AcceptedBucketFreshnessTest` 또는 동등한 focused unit test
  - last accepted bucket이 없으면 waiting-first-data 후보로 표현한다.
  - age 89.999초는 current 후보, 90초는 stale 후보, 179.999초는 stale 후보, 180초는 down 후보로 표현한다.
  - freshness age는 마지막 bucket `endUtc` 기준이고 `accepted_at` 또는 heartbeat 입력을 받지 않는다.
- `MetricBucketRepositoryIntegrationTest`
  - 같은 application 여러 instance/bucket 중 가장 큰 `bucket_end_utc`를 조회한다.
  - repository method가 state/freshness enum을 반환하지 않고 timestamp만 반환한다.

## Developer Guardrails

- 새 contract gate를 만들지 않는다.
- `LifecycleStateService` 전체 구현은 Story 4.2로 남긴다.
- recovery guidance는 Story 4.3으로 남긴다.
- degraded enter/resolve hysteresis는 Story 4.2/4.4로 남긴다.
- dashboard read model/API, snapshot persistence, operational event history를 구현하지 않는다.
- heartbeat를 freshness/state source로 연결하지 않는다.
- p95/p99, histogram merge, endpoint priority, insight rule 계산을 구현하지 않는다.
- controller/UI boundary에서 stale/down/current/baseline을 재판정하지 않는다.
- 불필요한 코드 변경이나 unrelated refactor를 하지 않는다.

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Implementation Plan

- `common.time`에 UTC 30초 bucket/window/freshness 모델을 추가하고 `Clock` bean을 구성한다.
- repository는 application id 기준 마지막 accepted bucket `endUtc` 조회만 추가한다.
- time model unit test와 repository integration test를 확장한 뒤 portal test suite를 실행한다.

### Debug Log

- 2026-05-24T17:05:02+0900: Story 4.1 파일이 없어 BMAD create-story workflow로 생성하고 `ready-for-dev` 상태로 시작했다.
- 2026-05-24T17:05:02+0900: BMAD dev-story workflow를 시작하며 Story 4.1 상태를 `in-progress`로 전환했다.
- 2026-05-24T17:07:57+0900: RED 단계에서 time window/freshness/repository timestamp 테스트가 새 class와 repository method 부재로 compile 실패하는 것을 확인했다.
- 2026-05-24T17:07:57+0900: `common.time`에 UTC interval, dashboard window, bucket calculator, freshness evaluator/status/result, UTC Clock bean을 추가했다.
- 2026-05-24T17:07:57+0900: `MetricBucketRepository`와 Spring Data JPA repository에 application id 기준 마지막 accepted bucket `endUtc` timestamp 조회만 추가했다.
- 2026-05-24T17:07:57+0900: focused test command 통과: `./gradlew :observability-portal:test --tests com.observation.portal.common.time.TimeBucketWindowCalculatorTest --tests com.observation.portal.common.time.AcceptedBucketFreshnessTest --tests com.observation.portal.domain.bucket.repository.MetricBucketRepositoryIntegrationTest`.
- 2026-05-24T17:08:18+0900: `./gradlew :observability-portal:test` 통과.
- 2026-05-24T17:08:35+0900: `./gradlew test` 통과.
- 2026-05-24T17:08:35+0900: `git diff --check` 통과.

### Completion Notes

- Ultimate context engine analysis completed - comprehensive developer guide created.
- UTC 30초 bucket boundary와 `[startUtc, endUtc)` interval semantics를 `UtcTimeInterval`/`TimeBucketWindowCalculator`로 고정했다.
- query 시점 기준 current 15분과 직전 baseline 15분을 `DashboardTimeWindow`로 제공하고 `Clock` 주입 기반 UTC system clock bean을 추가했다.
- 마지막 accepted bucket `endUtc`만 사용해 `CURRENT`, `STALE_CANDIDATE`, `DOWN_CANDIDATE`, `WAITING_FIRST_DATA` freshness 후보를 계산한다.
- repository는 application scope 마지막 `bucket_end_utc` timestamp만 조회하며 freshness/state 판단은 하지 않는다.
- controller/UI, dashboard read model/API, lifecycle state service, heartbeat 연결, p95/p99/rule/priority 계산은 추가하지 않았다.

### File List

- `planning-artifacts/stories/4-1-time-bucket-contract-implementation.md`
- `implementation-artifacts/sprint-status.yaml`
- `observability-portal/src/main/java/com/observation/portal/common/time/AcceptedBucketFreshness.java`
- `observability-portal/src/main/java/com/observation/portal/common/time/AcceptedBucketFreshnessEvaluator.java`
- `observability-portal/src/main/java/com/observation/portal/common/time/AcceptedBucketFreshnessStatus.java`
- `observability-portal/src/main/java/com/observation/portal/common/time/DashboardTimeWindow.java`
- `observability-portal/src/main/java/com/observation/portal/common/time/TimeBucketWindowCalculator.java`
- `observability-portal/src/main/java/com/observation/portal/common/time/UtcClockConfiguration.java`
- `observability-portal/src/main/java/com/observation/portal/common/time/UtcTimeInterval.java`
- `observability-portal/src/main/java/com/observation/portal/common/time/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/AcceptedMetricBucketJpaRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java`
- `observability-portal/src/test/java/com/observation/portal/common/time/AcceptedBucketFreshnessTest.java`
- `observability-portal/src/test/java/com/observation/portal/common/time/TimeBucketWindowCalculatorTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/bucket/repository/MetricBucketRepositoryIntegrationTest.java`

### Change Log

- 2026-05-24: Story 4.1 create-story 산출물을 생성하고 ready-for-dev 상태로 전환했다.
- 2026-05-24: UTC time bucket/window/freshness 공유 모델과 application-level last bucket timestamp repository 조회를 구현하고 focused/unit/integration/regression tests를 통과시켰다.

## Status

done
