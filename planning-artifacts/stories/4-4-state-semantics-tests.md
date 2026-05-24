---
artifactType: story
storyId: "4.4"
epic: "Epic 4. State Semantics and Time Windows"
title: "State Semantics Tests"
architectureStyle: Traditional MVC
status: done
date: 2026-05-24
---

# Story 4.4 - State Semantics Tests

## User Story

portal 구현자로서, Story 4.2와 Story 4.3에서 구현된 lifecycle state/recovery semantics를 계약형 회귀 테스트로 고정하길 원한다.

그래야 후속 dashboard read model/API/UI 구현자가 accepted bucket metric axis와 starter heartbeat connection axis를 섞거나, sample 부족/recovery/degraded 의미를 잘못 확장하지 않는다.

## Scope

포함:

- `LifecycleStateService`와 `domain.state.model`의 기존 typed input/output 의미를 검증하는 focused regression tests
- freshness threshold, minimum sample, baseline insufficient edge case 고정
- accepted bucket metric state와 starter heartbeat/connection axis를 분리하는 two-axis fixture
- 최근 heartbeat가 accepted bucket freshness/state를 current처럼 만들지 않는 guard test
- stale/down data-plane candidate가 host application down 확정 copy로 변하지 않는 guard test
- Story 4.3 recovery guidance trigger, non-trigger, 종료 조건, field shape 회귀 test
- `waiting_first_data -> unknown`이 recovery가 아님을 고정하는 test
- degraded hysteresis의 recovery 용어와 Story 4.3 recovery guidance를 섞지 않는 test
- `lastRecoveredAt` 또는 top-level `recovering` state를 새로 요구하지 않는 API shape test
- MVC layer guard와 portal test suite 회귀 확인

제외:

- dashboard read model/API/controller/UI 구현
- repository 조회, migration, persistence, snapshot/history/event 저장 구현
- heartbeat endpoint/sender/telemetry persistence 변경
- p95/p99, histogram merge, insight rule, endpoint priority 계산 구현
- `DashboardReadModelService`, `TriageSummaryService`, `ZeroInsight` production 구현
- 새 lifecycle state enum, top-level `recovering` state, `lastRecoveredAt` field 추가
- accepted bucket freshness를 heartbeat freshness로 보정하는 production logic

## Acceptance Criteria

1. Story 4.4는 production feature를 새로 만들지 않고, state semantics를 고정하는 focused regression test를 추가하거나 기존 `LifecycleStateServiceTest`를 명확히 보강한다.
2. freshness edge test는 마지막 accepted bucket `endUtc` 기준으로 no bucket, current, 90초 stale candidate, 180초 down candidate가 유지되는지 검증한다. heartbeat, `accepted_at`, UI local time은 freshness source가 아니다.
3. current freshness가 아니면 sample readiness, idle, degraded concern 평가가 억제되고 `stale`/`down` metric state가 우선하는지 검증한다.
4. current freshness + sample insufficient는 metric state `UNKNOWN`이며, degraded input이 강하더라도 degraded로 승격되지 않는지 검증한다.
5. current freshness + sample sufficient + traffic active + concern 없음은 `ACTIVE`, current freshness + sample sufficient + traffic idle은 `IDLE`로 유지되는지 검증한다.
6. baseline insufficient edge case는 typed concern guard가 false인 fixture로 표현한다. confidence가 높고 최근 5개 bucket이 모두 bad여도 `ruleGuardPassed=false`이면 `DEGRADED`가 아니어야 한다.
7. degraded enter는 rule guard 통과, confidence `>= 0.75`, 최근 5개 30초 bucket 중 bad bucket `>= 3`일 때만 가능하고, confidence `0.74`, bad bucket `2/5`, 30초 단발 blip은 `DEGRADED`가 아니어야 한다.
8. degraded resolve는 concern absence, confidence `< 0.60`, rule별 recovery/threshold 하회 중 하나가 5 consecutive buckets 동안 유지될 때만 `ACTIVE`로 해소되는지 검증한다.
9. two-axis fixture는 accepted bucket axis와 starter heartbeat axis를 함께 만들되, test assertion에서는 metric state와 starter connection summary를 별도 field로 확인한다.
10. no accepted bucket + recent heartbeat는 metric state `WAITING_FIRST_DATA`이고 starter connection은 connected 계열 diagnosis여야 한다. 최근 heartbeat가 metric state를 `ACTIVE` 또는 freshness `CURRENT`처럼 만들면 실패해야 한다.
11. stale/down accepted bucket candidate + recent heartbeat는 metric state `STALE`/`DOWN`으로 유지되고 starter connection은 connected/no recent traffic 계열로만 표현되어야 한다.
12. accepted bucket current + stale/unknown heartbeat는 accepted bucket metric state를 유지하고 starter connection warning만 별도로 남겨야 한다.
13. heartbeat가 없거나 stale이고 accepted bucket도 없거나 오래된 fixture는 telemetry unreachable/unknown 계열이어야 하며 host application down 원인을 확정하지 않아야 한다.
14. `DOWN` metric state의 label/rationale/recommendedAction은 data-plane freshness 부족 후보로 제한한다. test는 `host application down`, `host process down`, `앱이 내려`, `프로세스 down`, `애플리케이션 다운` 같은 단정 copy가 없는지 검사한다.
15. recovery guidance는 이전 metric state가 `STALE` 또는 `DOWN`, 현재 freshness `CURRENT`, sample `INSUFFICIENT`인 경우에만 `metricState.code=UNKNOWN` + `recovery.isRecovering=true`로 표현된다.
16. recovery true일 때 `retryAfterSeconds=30`, `recommendedAction` non-null, `lastHealthyAt`은 typed input에서 받은 이전 healthy source만 보존한다. source가 없으면 `lastHealthyAt`은 null/empty 허용이다.
17. previous state 없음, `WAITING_FIRST_DATA`, `ACTIVE`, `IDLE`, `UNKNOWN`, `DEGRADED`는 current freshness + sample insufficient여도 recovery가 아니다.
18. `waiting_first_data -> unknown`은 recovery가 아니며, 첫 accepted bucket 이후 sample 부족은 일반 insufficient sample 상태로 검증한다.
19. current freshness + sample sufficient이면 previous state가 `STALE`/`DOWN`이어도 recovery는 종료된다. freshness가 다시 stale/down candidate가 되면 recovery가 아니라 `STALE`/`DOWN` metric state다.
20. degraded hysteresis의 `recoveryConditionMet`/`recoveryConsecutiveBuckets`는 degraded 해소 전용 입력이며 Story 4.3 recovery guidance trigger와 섞이지 않는지 검증한다.
21. `RecoveryGuidance` record/component shape는 `isRecovering`, `lastHealthyAt`, `retryAfterSeconds`, `recommendedAction`으로 고정하고 `lastRecoveredAt`을 포함하지 않는다.
22. `LifecycleStateCode`에는 `RECOVERING` 또는 동등 top-level recovering state가 없어야 한다.
23. controller, repository, UI/static resource package에는 lifecycle state, starter connection, recovery guidance 재판정 test 대상이나 production code를 추가하지 않는다.
24. `./gradlew :observability-portal:test --tests com.observation.portal.domain.state.service.LifecycleStateServiceTest` 또는 새 regression test class focused command가 통과한다.
25. `./gradlew :observability-portal:test`와 `git diff --check`가 통과한다.

## Tasks/Subtasks

- [x] State semantics regression test 구조 정리 (AC: 1, 9, 23)
  - [x] 기존 `LifecycleStateServiceTest`를 보강할지, `LifecycleStateSemanticsRegressionTest` 같은 새 focused class를 추가할지 결정한다.
  - [x] 새 class를 만들 경우 package는 `observability-portal/src/test/java/com/observation/portal/domain/state/service` 아래로 둔다.
  - [x] accepted bucket axis fixture와 starter connection axis fixture를 test helper로 분리하되 production model을 새로 만들지 않는다.
- [x] Freshness/minimum sample/baseline guard tests 추가 (AC: 2~8)
  - [x] no bucket/current/stale/down threshold와 `endUtc` source를 검증한다.
  - [x] sample insufficient가 `UNKNOWN`을 만들고 degraded 평가보다 우선하는지 검증한다.
  - [x] baseline insufficient는 `DegradedHysteresisInput.of(true, false, highConfidence, 5, false, 0)` 계열 fixture로 표현한다.
  - [x] degraded enter/resolve hysteresis boundary를 semantic test name으로 고정한다.
- [x] Two-axis fixture regression 추가 (AC: 9~14)
  - [x] no accepted bucket + recent heartbeat fixture를 `WAITING_FIRST_DATA` + starter connected diagnosis로 검증한다.
  - [x] stale/down candidate + recent heartbeat fixture를 metric state와 connection summary가 분리되는지 검증한다.
  - [x] current accepted bucket + stale/unknown heartbeat fixture를 metric state 유지 + starter warning으로 검증한다.
  - [x] all copy surface에 host down 단정 표현이 없는지 helper assertion을 강화한다.
- [x] Recovery guidance regression 추가 (AC: 15~22)
  - [x] stale/down previous state + current freshness + insufficient sample만 recovery true인지 검증한다.
  - [x] waiting first data, non-stale previous state, sample sufficient, stale/down candidate 재진입은 recovery false인지 검증한다.
  - [x] degraded resolve/hysteresis와 recovery guidance가 독립인지 검증한다.
  - [x] `RecoveryGuidance` field shape와 `LifecycleStateCode` enum shape를 reflection 또는 compile-time assertion으로 고정한다.
- [x] Scope guard와 regression command 실행 (AC: 23~25)
  - [x] production code 변경이 필요하면 기존 semantics 위반을 고치는 최소 변경으로 제한하고, 그 이유를 Dev Agent Record에 남긴다.
  - [x] `./gradlew :observability-portal:test --tests com.observation.portal.domain.state.service.LifecycleStateServiceTest` 또는 새 focused test command를 실행한다.
  - [x] `./gradlew :observability-portal:test`를 실행한다.
  - [x] `git diff --check`를 실행한다.

## Dev Notes

### Contract Summary

- Story 4.4는 Story 4.2/4.3의 구현 의미를 고정하는 regression story다.
- 핵심 source-of-truth는 accepted bucket metric axis다. starter heartbeat는 별도 control-plane/liveness axis다.
- heartbeat가 최근이어도 accepted bucket이 없거나 stale/down candidate이면 metric state를 current/active로 만들면 안 된다.
- stale/down은 data-plane freshness candidate다. host application process down 확정이 아니다.
- recovery guidance는 stale/down 이후 새 accepted bucket이 current로 돌아왔지만 sample이 insufficient인 좁은 구간만 표현한다.
- recovery guidance는 top-level state가 아니라 `UNKNOWN` metric state와 `RecoveryGuidance` field 조합이다.
- baseline insufficient는 Story 4.4에서 새 baseline model을 만들지 않고, existing degraded typed input의 rule guard false fixture로 고정한다.

### Existing Code To Reuse

- `observability-portal/src/main/java/com/observation/portal/common/time/AcceptedBucketFreshnessEvaluator.java`
  - 마지막 accepted bucket `endUtc`와 query 시각으로 freshness candidate를 계산한다.
  - stale threshold는 90초 이상, down threshold는 180초 이상이다.
  - heartbeat를 입력으로 받지 않는다.
- `observability-portal/src/main/java/com/observation/portal/common/time/AcceptedBucketFreshnessStatus.java`
  - `WAITING_FIRST_DATA`, `CURRENT`, `STALE_CANDIDATE`, `DOWN_CANDIDATE` 후보를 제공한다.
- `observability-portal/src/main/java/com/observation/portal/domain/state/service/LifecycleStateService.java`
  - repository를 조회하지 않고 `MetricLifecycleInput`과 `StarterConnectionInput` typed input만 소비한다.
  - freshness ordering 이후 current일 때만 sample/idle/degraded 판단을 수행한다.
  - `decideRecoveryGuidance`는 previous state stale/down + current freshness + insufficient sample만 recovery true로 만든다.
- `observability-portal/src/main/java/com/observation/portal/domain/state/model/MetricLifecycleInput.java`
  - accepted bucket axis 입력이다. heartbeat 정보가 들어가면 안 된다.
  - `previousState`와 `previousHealthyAt`은 recovery guidance source로만 쓰인다.
- `observability-portal/src/main/java/com/observation/portal/domain/state/model/StarterConnectionInput.java`
  - heartbeat 기반 starter connection/liveness axis 입력이다.
  - accepted bucket freshness, sample readiness, degraded 판단 입력과 섞지 않는다.
- `observability-portal/src/main/java/com/observation/portal/domain/state/model/DegradedHysteresisInput.java`
  - degraded enter/resolve에 필요한 typed concern result만 담는다.
  - `ruleGuardPassed=false`는 baseline insufficient, freshness/sample/baseline/absolute threshold guard 미통과를 표현하는 test fixture로 사용할 수 있다.
  - 여기의 `recoveryConditionMet`은 degraded hysteresis 용어이며 Story 4.3 recovery guidance trigger가 아니다.
- `observability-portal/src/main/java/com/observation/portal/domain/state/model/RecoveryGuidance.java`
  - field shape는 `isRecovering`, `lastHealthyAt`, `retryAfterSeconds`, `recommendedAction`이다.
  - `lastRecoveredAt` 또는 top-level recovering state를 추가하지 않는다.
- `observability-portal/src/test/java/com/observation/portal/domain/state/service/LifecycleStateServiceTest.java`
  - Story 4.2/4.3 focused tests와 helper pattern이 이미 있다.
  - Story 4.4는 중복을 피하되 semantic fixture와 guard assertion을 더 명확히 고정한다.

### Architecture Constraints

- Active baseline은 Traditional MVC + Service/Repository Layering이다.
- Portal package는 feature-first MVC이며 이번 story의 예상 변경 위치는 `domain.state.service` test package와 필요 시 최소 production fix뿐이다.
- `domain`은 DDD pure domain layer가 아니라 업무 기능 grouping namespace다.
- Controller는 service를 호출하고 repository를 직접 호출하지 않는다.
- Repository는 저장/조회만 담당하며 lifecycle state, recovery guidance, zero-insight, p95/p99, endpoint priority를 계산하지 않는다.
- JPA entity는 persistence model이며 service result/external return model로 노출하지 않는다.
- `port`, `adapter`, `application` package를 새로 만들지 않는다.
- 새 공개 production class/method를 만들 경우 프로젝트 지침에 따라 한국어 Javadoc을 작성한다. Test helper에는 의도를 드러내는 짧은 한국어 주석만 필요할 때 추가한다.

### Previous Story Intelligence

- Story 4.1은 `common.time`에 UTC bucket/window/freshness helper와 application scope latest accepted bucket `endUtc` repository lookup을 추가했다.
- Story 4.2는 `LifecycleStateService`와 typed state/connection model을 구현했고, two-axis matrix, freshness ordering, sample/idle/active, degraded hysteresis, heartbeat guardrail을 unit test로 고정했다.
- Story 4.3은 `RecoveryGuidance`, `MetricLifecycleInput.previousHealthyAt`, `LifecycleStateDecision.recovery`를 추가했고, recovery trigger/non-trigger/field shape/copy guard를 test로 고정했다.
- 최근 commit `923c6fc feat(portal): add lifecycle state service`는 state model/service와 `LifecycleStateServiceTest`의 기반이다.
- 최근 commit `716ecd2 feat(portal): add recovery guidance`는 recovery field와 관련 tests를 확장했다.
- 최근 commit `231f60a docs: add current product source of truth`는 Epic 5/6이 accepted bucket axis와 heartbeat axis를 계속 분리해야 함을 최신 제품 기준으로 고정했다.

### Latest Technical Information

- 새 외부 dependency나 vendor API가 필요하지 않다.
- 현재 repo는 Java 17, Spring Boot BOM `4.0.6`, Spring Data JPA/Jakarta Persistence, PostgreSQL/Flyway, JUnit 5/AssertJ, ArchUnit `1.4.1`, Testcontainers `2.0.5`를 사용한다.
- Story 4.4는 JUnit 5 + AssertJ focused unit/regression test 중심이며 새 library를 추가하지 않는다.

### Suggested Implementation Shape

- 추천 test class 이름은 `LifecycleStateSemanticsRegressionTest`다. 기존 `LifecycleStateServiceTest`가 이미 충분히 읽기 좋다면 같은 class에 nested/sectioned tests를 추가해도 된다.
- two-axis fixture는 test-only helper로 둔다. 예: `MetricAxisFixture`가 `AcceptedBucketFreshness`, `MetricSampleReadiness`, `MetricTrafficActivity`, `DegradedHysteresisInput`, previous state/healthy source를 만들고, `StarterConnectionInput`은 별도 helper에서 만든다.
- assertion은 `decision.metricState()`, `decision.starterConnection()`, `decision.recovery()`를 각각 확인한다. 하나의 string/copy만 보고 통과시키지 않는다.
- host-down copy guard helper는 metric state label/rationale/action, starter connection label/rationale/action, recovery action을 모두 검사한다.
- baseline insufficient는 future baseline service를 만들지 않고 `ruleGuardPassed=false` fixture로 남긴다. 이 fixture 이름에 `baselineInsufficient`를 드러내 dev agent와 후속 reviewer가 의도를 놓치지 않게 한다.

## Developer Guardrails

- Story 4.4는 dashboard read model/API/UI/repository/persistence story가 아니다.
- 새 migration, table, repository, controller, DTO, static UI asset을 만들지 않는다.
- heartbeat를 accepted bucket freshness source로 쓰지 않는다.
- heartbeat 최근 여부로 metric state를 `ACTIVE`, freshness를 `CURRENT`, recovery를 true/false로 바꾸지 않는다.
- accepted bucket이 stale/down 후보여도 host application down을 확정하는 copy나 assertion을 만들지 않는다.
- `down` enum을 host process down으로 해석하는 test name/copy를 쓰지 않는다. data-plane freshness candidate로 표현한다.
- `RecoveryGuidance`에 `lastRecoveredAt`을 추가하지 않는다.
- `LifecycleStateCode.RECOVERING` 또는 top-level `recovering` state를 만들지 않는다.
- `waiting_first_data -> unknown`은 recovery가 아니다.
- degraded hysteresis의 `recoveryConditionMet`과 Story 4.3 recovery guidance를 섞지 않는다.
- `no_recent_traffic`은 starter connection diagnosis로는 허용되지만 zeroInsight reason code production 구현을 추가하지 않는다.
- p95/p99, histogram merge, insight rule, endpoint priority 계산을 이 story에서 만들지 않는다.
- unrelated refactor, package 재배치, 기존 ingest/heartbeat behavior 변경을 피한다.

## Test Expectations

Focused test 대상:

- `observability-portal/src/test/java/com/observation/portal/domain/state/service/LifecycleStateServiceTest.java`
- 또는 `observability-portal/src/test/java/com/observation/portal/domain/state/service/LifecycleStateSemanticsRegressionTest.java`

필수 scenario:

- no accepted bucket + recent heartbeat -> `WAITING_FIRST_DATA`, starter connected diagnosis, recovery false
- no accepted bucket + missing/unknown heartbeat -> `WAITING_FIRST_DATA`, telemetry unreachable/unknown, recovery false
- 89.999초 old bucket -> freshness current; 90초 -> stale candidate; 179.999초 -> stale; 180초 -> down candidate
- stale/down candidate + recent heartbeat -> metric state `STALE`/`DOWN`, starter connected/no recent traffic 계열, host down copy 없음
- current bucket + stale/unknown heartbeat -> metric state는 sample/concern 기준 유지, starter connection warning만 별도
- current freshness + sample insufficient + high concern input -> `UNKNOWN`, not `DEGRADED`
- current freshness + sample sufficient + idle traffic -> `IDLE`
- current freshness + sample sufficient + active traffic + no concern -> `ACTIVE`
- baseline insufficient fixture (`ruleGuardPassed=false`) + confidence high + bad bucket 5/5 -> not `DEGRADED`
- degraded enter boundary: confidence `0.75`, bad bucket 3/5 -> `DEGRADED`; confidence `0.74` 또는 bad bucket 2/5 -> not `DEGRADED`
- degraded resolve boundary: 4 consecutive recovery buckets -> still `DEGRADED`; 5 consecutive -> `ACTIVE`
- previous `STALE`/`DOWN` + current freshness + sample insufficient -> `UNKNOWN` + `recovery.isRecovering=true`
- previous `WAITING_FIRST_DATA`, `ACTIVE`, `IDLE`, `UNKNOWN`, `DEGRADED` + current freshness + sample insufficient -> recovery false
- previous `STALE`/`DOWN` + current freshness + sample sufficient -> recovery false
- previous `STALE`/`DOWN` + stale/down candidate -> recovery false and metric state `STALE`/`DOWN`
- starter connection input recent/stale/unknown을 바꿔도 accepted bucket freshness와 recovery trigger 결과가 바뀌지 않음
- `RecoveryGuidance` components에 `lastRecoveredAt` 없음
- `LifecycleStateCode`에 `RECOVERING` 없음

Suggested commands:

```bash
./gradlew :observability-portal:test --tests com.observation.portal.domain.state.service.LifecycleStateServiceTest
./gradlew :observability-portal:test --tests com.observation.portal.domain.state.service.LifecycleStateSemanticsRegressionTest
./gradlew :observability-portal:test
git diff --check
```

새 regression class를 만들지 않으면 두 번째 focused command는 생략한다.

## Source References

- `planning-artifacts/current-product-source-of-truth.md` - accepted bucket source-of-truth, heartbeat boundary, recovery UX, project/application/instance read model 흐름
- `implementation-artifacts/sprint-status.yaml` - Story 4.4 tracking status와 Epic 4 workflow notes
- `implementation-artifacts/epic-4-context.md` - Epic 4 목표, state semantics constraints, Story 4.4 cross-story dependency
- `planning-artifacts/epics.md` - Epic 4 Story 4.4 seed와 cross-epic state/read-model acceptance
- `planning-artifacts/contracts/state-semantics.md` - state enum, evaluation order, two-axis matrix, degraded hysteresis, recovery contract
- `planning-artifacts/contracts/read-model-contract.md` - starterConnection/recovery/zeroInsight shape와 host-down copy guard
- `planning-artifacts/stories/4-2-lifecycle-state-service.md` - previous lifecycle service scope, accepted bucket/heartbeat separation, test strategy
- `planning-artifacts/stories/4-3-recovery-guidance.md` - recovery guidance implementation scope, non-goals, test strategy
- `implementation-artifacts/spec-story-4-3-recovery-guidance-contract-decisions.md` - recovery closed decisions, BMAD party validation, acceptance seed
- `implementation-artifacts/deferred-work.md` - heartbeat/state semantics correction, down copy/rename residual guard
- `planning-artifacts/architecture.md` - MVC service/repository/controller responsibility, heartbeat/read flow, testing strategy
- `planning-artifacts/project-structure.md` - feature-first MVC package map and layer responsibilities
- `_bmad/custom/project-context.md` - MVC + Spring Data JPA project policy
- `observability-portal/src/main/java/com/observation/portal/common/time/AcceptedBucketFreshnessEvaluator.java`
- `observability-portal/src/main/java/com/observation/portal/domain/state/service/LifecycleStateService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/state/model/*.java`
- `observability-portal/src/test/java/com/observation/portal/domain/state/service/LifecycleStateServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/architecture/MvcLayerBoundaryTest.java`

## Out of Scope

- dashboard current read model, application list read model, instance detail read model 구현
- `DashboardReadModelService`, `TriageSummaryService`, `EndpointPriorityService`, `HistogramMergeService` 구현 또는 확장
- dashboard API/controller/DTO/static UI 구현
- repository 조회 logic, JPA entity, Flyway migration, persistence schema 변경
- dashboard snapshot/history/operational event capture 또는 API 구현
- heartbeat ingest endpoint, starter heartbeat sender, telemetry persistence 변경
- p95/p99, histogram-derived percentile, endpoint percentile, rule engine 구현
- zeroInsight production mapper 구현
- `lastRecoveredAt`, top-level `recovering`, host application down 확정 semantics 추가

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- 2026-05-24T22:35:00+0900: BMAD dev-story workflow를 시작하며 `4-4-state-semantics-tests`를 sprint status에서 `in-progress`로 전환했다.
- 2026-05-24T22:36:00+0900: Story 4.2/4.3, state-semantics/read-model contract, recovery guidance decisions, Epic 4 context를 확인했다.
- 2026-05-24T22:36:30+0900: production code 변경 없이 `LifecycleStateSemanticsRegressionTest`를 새 focused regression class로 추가했다.
- 2026-05-24T22:37:00+0900: 새 regression focused command 통과: `./gradlew :observability-portal:test --tests com.observation.portal.domain.state.service.LifecycleStateSemanticsRegressionTest`.
- 2026-05-24T22:37:15+0900: 기존 lifecycle focused command 통과: `./gradlew :observability-portal:test --tests com.observation.portal.domain.state.service.LifecycleStateServiceTest`.
- 2026-05-24T22:37:37+0900: `./gradlew :observability-portal:test` 통과.
- 2026-05-24T22:37:52+0900: Story checklist와 Dev Agent Record를 갱신했다.

### Completion Notes List

- Story 4.4는 production feature 없이 test-only regression class로 구현했다.
- accepted bucket metric axis와 starter heartbeat connection axis를 `MetricAxisFixture`/`StarterAxisFixture` test helper로 분리했다.
- accepted bucket `endUtc` 기준 freshness threshold, sample insufficient 우선순위, baseline insufficient guard, degraded enter/resolve hysteresis boundary를 고정했다.
- 최근 heartbeat가 `WAITING_FIRST_DATA`, `STALE`, `DOWN` metric state를 `CURRENT`/`ACTIVE`처럼 바꾸지 못하도록 two-axis regression을 추가했다.
- recovery guidance는 이전 state `STALE`/`DOWN`, current freshness, insufficient sample 조합에서만 `UNKNOWN + recovery.isRecovering=true`가 되도록 고정했다.
- `waiting_first_data -> unknown`, sample sufficient 종료, stale/down 재진입, degraded hysteresis recovery condition은 Story 4.3 recovery guidance가 아님을 검증했다.
- `RecoveryGuidance` field shape와 `LifecycleStateCode` enum에 `lastRecoveredAt`/`RECOVERING`이 없음을 reflection/enum assertion으로 고정했다.
- controller, repository, DTO, migration, static UI asset, production service/model 변경은 추가하지 않았다.

### File List

- `planning-artifacts/stories/4-4-state-semantics-tests.md`
- `implementation-artifacts/sprint-status.yaml`
- `observability-portal/src/test/java/com/observation/portal/domain/state/service/LifecycleStateSemanticsRegressionTest.java`

## Change Log

- 2026-05-24: Story 4.4 create-story 산출물을 생성하고 ready-for-dev 상태로 전환했다.
- 2026-05-24: State semantics regression test class를 추가하고 Story 4.4를 review 상태로 전환했다.

## Status

done
