---
artifactType: story
storyId: "4.2"
epic: "Epic 4. State Semantics and Time Windows"
title: "Lifecycle State Service"
architectureStyle: Traditional MVC
status: done
date: 2026-05-24
---

# Story 4.2 - Lifecycle State Service

## User Story

portal 구현자로서, accepted bucket 기반 application metric state와 starter heartbeat 기반 connection/liveness를 `LifecycleStateService`에서 별도 typed input/output으로 판단하길 원한다.

그래야 dashboard/read-model 구현이 metric data freshness와 starter connection을 섞지 않고, 요청이 없어서 bucket이 오지 않는 상황을 host application down으로 잘못 단정하지 않는다.

## Scope

포함:

- `domain.state.service`의 `LifecycleStateService`
- `domain.state.model`의 application metric state typed input/output
- `domain.state.model`의 starter connection/liveness typed input/output
- Story 4.1의 `AcceptedBucketFreshness`, `AcceptedBucketFreshnessStatus`, UTC query 시각 모델 재사용
- accepted bucket axis와 heartbeat axis를 함께 담되 서로의 source-of-truth를 침범하지 않는 result model
- freshness, sample readiness, idle, active, stale/down data-plane candidate, degraded hysteresis의 service-level 판정
- heartbeat telemetry가 typed input으로 들어왔을 때 connection copy/recommended action 후보를 별도 축으로 만드는 판단
- focused unit tests와 MVC layer guard 회귀 확인

제외:

- heartbeat telemetry persistence 재구현 또는 migration/repository 추가
- `POST /api/ingest/v1/heartbeat` endpoint, sender, request/response 재구현
- heartbeat를 accepted bucket freshness source로 사용
- heartbeat를 host business health, degraded, p95/p99, insight rule, endpoint priority, dashboard snapshot, operational event source로 사용
- dashboard read model/API/controller/UI 구현
- dashboard snapshot persistence 또는 operational event history 구현
- p95/p99 계산, histogram merge, endpoint priority, insight rule engine 구현
- recovery guidance 전체 구현: stale/down 이후 sample 부족 안내의 상세 field와 copy polish는 Story 4.3 범위

## Acceptance Criteria

1. `LifecycleStateService`는 `com.observation.portal.domain.state.service` 아래에 추가되고, 공개 input/output model은 `com.observation.portal.domain.state.model` 아래에 둔다.
2. service input은 accepted bucket metric axis와 starter connection axis를 별도 typed object로 받는다. heartbeat field가 metric freshness input 안으로 들어가면 안 된다.
3. service output은 application metric state와 starter connection/liveness summary를 별도 typed field로 반환한다. starter connection result는 metric state enum을 덮어쓰거나 대체하지 않는다.
4. metric state는 Story 4.1의 `AcceptedBucketFreshnessStatus`를 사용해 `waiting_first_data`, `stale`, `down` 후보를 최종 lifecycle state로 승격한다. freshness age는 마지막 accepted bucket `endUtc` 기준이며 heartbeat, `accepted_at`, UI local time을 사용하지 않는다.
5. accepted bucket이 없으면 metric state는 `waiting_first_data`로 판정한다. 이때 starter heartbeat가 최근이어도 metric state를 `active`로 바꾸지 않는다.
6. `STALE_CANDIDATE`는 `stale`, `DOWN_CANDIDATE`는 `down`으로 판정하되, `down`의 rationale/label/recommended action은 data-plane freshness 부족 후보로 제한하고 host application process down 확정 문구를 만들지 않는다.
7. freshness가 current일 때만 sample readiness와 concern signal을 평가한다. sample 부족은 `unknown`, idle traffic은 `idle`, 충분한 sample과 concern 없음은 `active`로 판정한다.
8. degraded 진입은 typed concern input이 rule guard 통과, confidence `>= 0.75`, 최근 5개 30초 bucket 중 3개 이상 bad를 모두 만족할 때만 가능하다. 30초 단발 blip 또는 5개 중 2개 bad는 `degraded`가 아니다.
9. degraded 해소는 concern absence, confidence `< 0.60`, rule별 recovery/threshold 하회 중 하나가 5 consecutive buckets 동안 유지될 때만 가능하다. service는 rule 계산 자체를 만들지 않고 typed concern/hysteresis input만 소비한다.
10. starter connection input이 최근 heartbeat를 나타내고 accepted bucket이 없거나 오래된 경우 output은 `starter connected but no accepted bucket`, `waiting for traffic`, `metric data idle`, `no recent traffic` 계열 diagnosis를 제공하고 host down으로 단정하지 않는다.
11. starter connection input이 없거나 stale이고 accepted bucket도 없거나 오래된 경우 output은 `starter disconnected`, `telemetry unreachable`, `unknown` 계열 diagnosis를 제공하고 host application down 원인은 미확정으로 둔다.
12. accepted bucket이 최근이지만 heartbeat가 없거나 stale인 경우 metric state는 accepted bucket axis 기준으로 유지하고, starter connection warning만 별도 output에 담는다.
13. heartbeat telemetry persistence는 기존 `StarterHeartbeatTelemetryRepository`와 `starter_heartbeat_telemetry` table을 재사용 대상으로만 문서화한다. Story 4.2는 새 heartbeat table, migration, upsert path를 만들지 않는다.
14. controller, repository, UI/static resource package에는 lifecycle state, current/baseline/stale/down, starter connection diagnosis 재판정 로직을 추가하지 않는다.
15. focused unit tests가 two-axis matrix, freshness ordering, sample/idle/active, degraded enter/resolve hysteresis, heartbeat guardrail을 검증한다.
16. `./gradlew :observability-portal:test`가 통과하고, 필요 시 focused test command와 `git diff --check`를 함께 실행한다.

## Tasks/Subtasks

- [x] State model 설계 및 추가 (AC: 1, 2, 3)
  - [x] `LifecycleStateCode` 또는 동등 enum에 `WAITING_FIRST_DATA`, `UNKNOWN`, `IDLE`, `ACTIVE`, `STALE`, `DOWN`, `DEGRADED`를 정의한다.
  - [x] `MetricLifecycleInput` 또는 동등 record에 `AcceptedBucketFreshness`, sample readiness, idle 여부, degraded/hysteresis typed signal을 담는다.
  - [x] `StarterConnectionInput` 또는 동등 record에 heartbeat connection freshness/status/source 정보를 담되 metric freshness와 별도 타입으로 유지한다.
  - [x] `LifecycleStateDecision` 또는 동등 result에 `metricState`, `starterConnection`, rationale/recommended action 후보를 분리해 담는다.
- [x] `LifecycleStateService` metric axis 판정 구현 (AC: 4, 5, 6, 7)
  - [x] Story 4.1의 `AcceptedBucketFreshnessStatus.WAITING_FIRST_DATA`, `STALE_CANDIDATE`, `DOWN_CANDIDATE`, `CURRENT`를 재사용한다.
  - [x] freshness 부족이면 stale/down metric state가 우선하고 일반 concern/degraded 평가를 억제한다.
  - [x] sample 부족과 idle은 accepted bucket freshness가 current인 경우에만 평가한다.
  - [x] `down`은 data-plane freshness 후보로만 표현하고 host process down copy를 만들지 않는다.
- [x] Degraded hysteresis typed input 처리 (AC: 8, 9)
  - [x] rule guard, confidence, 최근 5개 bucket bad count, resolve consecutive count를 typed input으로 받는다.
  - [x] service는 threshold와 ordering만 적용하고 p95/p99, rule, endpoint priority 계산을 만들지 않는다.
  - [x] 30초 단발 blip이 degraded로 승격되지 않는 regression test를 추가한다.
- [x] Starter connection axis 판정 구현 (AC: 2, 3, 10, 11, 12, 13)
  - [x] heartbeat telemetry를 metric freshness input에 넣지 않고 `StarterConnectionInput`으로만 받는다.
  - [x] 최근 heartbeat + 없음/오래된 accepted bucket 조합은 traffic 대기/metric data idle/no recent traffic 계열 diagnosis로 수렴시킨다.
  - [x] heartbeat 없음/stale + accepted bucket 없음/오래됨 조합은 telemetry unreachable/unknown 계열 diagnosis로 수렴시킨다.
  - [x] accepted bucket이 최근이면 heartbeat stale 여부가 metric state를 바꾸지 않고 connection warning만 남는지 검증한다.
- [x] 기존 구현 재사용 경계 확인 (AC: 13, 14)
  - [x] `AcceptedBucketFreshnessEvaluator`와 `MetricBucketRepository.findLatestBucketEndUtcByApplicationId`를 freshness source로 유지한다.
  - [x] `StarterHeartbeatTelemetryRepository.findLatestByProjectId` 또는 `findByIdentity`는 후속 read-model adapter가 connection input을 만들 때 재사용할 수 있는 기존 저장소로만 둔다.
  - [x] `V005__create_starter_heartbeat_telemetry.sql`, heartbeat endpoint/service, catalog upsert 경로를 변경하지 않는다.
- [x] Focused tests와 회귀 확인 (AC: 15, 16)
  - [x] `LifecycleStateServiceTest` 또는 동등 test class를 추가한다.
  - [x] `MvcLayerBoundaryTest`가 새 state 계산 class 위치를 계속 허용하는지 확인한다.
  - [x] `./gradlew :observability-portal:test`를 실행한다.

## Dev Notes

### Existing Code To Reuse

- `observability-portal/src/main/java/com/observation/portal/common/time/AcceptedBucketFreshnessEvaluator.java`
  - 현재 마지막 accepted bucket `endUtc`와 query 시각만 사용해 freshness 후보를 계산한다.
  - threshold는 stale 90초 이상, down 180초 이상이다.
  - 최종 lifecycle state 판정은 의도적으로 하지 않는다. Story 4.2가 이 후보를 state로 승격한다.
- `observability-portal/src/main/java/com/observation/portal/common/time/AcceptedBucketFreshness.java`
  - `evaluatedAtUtc`, `lastAcceptedBucketEndUtc`, `age`, `status`만 담는다.
  - heartbeat 또는 `accepted_at`을 추가하지 않는다.
- `observability-portal/src/main/java/com/observation/portal/common/time/AcceptedBucketFreshnessStatus.java`
  - 현재 후보는 `WAITING_FIRST_DATA`, `CURRENT`, `STALE_CANDIDATE`, `DOWN_CANDIDATE`다.
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java`
  - `findLatestBucketEndUtcByApplicationId(UUID applicationId)`가 application scope 마지막 accepted bucket timestamp만 조회한다.
  - repository는 freshness/state 의미를 판단하지 않는다.
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/repository/StarterHeartbeatTelemetryRepository.java`
  - heartbeat telemetry persistence는 이미 완료됐다.
  - `findByIdentity`와 `findLatestByProjectId`는 connection input adapter가 재사용할 수 있는 조회 경계다.
  - 이 repository는 accepted bucket, catalog, snapshot, event를 만들지 않는다.
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/model/StarterHeartbeatTelemetryRecord.java`
  - heartbeat latest row를 JPA entity 밖으로 복사한 immutable model이다.
  - Story 4.2에서 직접 사용하더라도 metric freshness model로 섞지 않는다.

### Architecture Constraints

- Active baseline은 Traditional MVC + Service/Repository Layering이다.
- `domain`은 feature-first MVC namespace이며 DDD pure domain layer가 아니다.
- lifecycle state, insight rule, endpoint priority, p95 같은 계산 class는 `service` 또는 `model` package에 있어야 한다.
- Controller는 service를 호출하고 repository를 직접 호출하지 않는다.
- Repository는 저장/조회만 담당하며 state, rule, p95/p99, endpoint priority를 계산하지 않는다.
- JPA entity는 persistence model이며 service result/external return model로 직접 반환하지 않는다.
- 새 공개 클래스, 공개 메서드, 핵심 helper의 Javadoc/comment는 프로젝트 지침에 따라 한국어로 작성한다.

### Two-Axis Decision Guide

| Accepted bucket metric axis | Starter connection axis | Required result |
|---|---|---|
| no accepted bucket | recent heartbeat | metric state `waiting_first_data`; connection `starter_connected`; diagnosis `waiting_for_traffic` 또는 `starter_connected_but_no_accepted_bucket` |
| stale/down candidate | recent heartbeat | metric state `stale` 또는 `down` as data-plane freshness; connection `starter_connected`; diagnosis `no_recent_traffic` 또는 `metric_data_idle`; host down 확정 금지 |
| no accepted bucket 또는 stale/down candidate | no/stale heartbeat | metric state는 accepted bucket 기준 유지; connection `starter_disconnected`/`telemetry_unreachable`/`unknown`; host down 원인 미확정 |
| current accepted bucket | no/stale heartbeat | metric state는 sample/concern 기준 유지; connection warning만 별도 표시; `stateImpact=none` 계열 |
| current accepted bucket | recent heartbeat | metric state와 connection state 모두 정상 축으로 표시 |

### Implementation Shape Recommendation

- `LifecycleStateService`는 repository를 직접 조회하기보다 typed input을 평가하는 순수 service에 가깝게 유지한다.
- `MetricLifecycleInput`에는 `AcceptedBucketFreshness`를 필수로 넣고, sample readiness와 degraded signal은 enum/record로 명시한다.
- `StarterConnectionInput`에는 `statusSource=starter_heartbeat`, `lastHeartbeatAt`, `lastHeartbeatStatus`, connection freshness/meaning을 담되 optional/unknown 상태를 명확히 표현한다.
- heartbeat recency의 정확한 숫자 정책은 현재 계약에 고정되어 있지 않다. Story 4.2 구현에서는 `StarterConnectionInput`이 `RECENT`, `STALE`, `UNKNOWN` 같은 typed freshness를 이미 담도록 하고, telemetry row에서 이 input을 만드는 adapter 정책은 DashboardReadModelService 연동 시점에 명시한다.
- `LifecycleStateDecision`은 후속 `DashboardReadModelService`가 그대로 read model에 담을 수 있게 state code, label/rationale/recommended action 후보, starter connection summary를 분리해서 제공한다.
- `down` enum을 유지하더라도 label/rationale은 "metric data-plane disconnected/unreachable" 계열이어야 한다. "앱이 내려감"으로 읽히는 copy는 Story 4.3 또는 후속 rename 검토로 남긴다.

### Previous Story Intelligence

- Story 4.1은 `common.time`에 UTC 30초 bucket/window/freshness 모델을 추가했고, `MetricBucketRepository`에 application-level latest bucket timestamp 조회를 추가했다.
- Story 4.1의 guardrail은 최종 lifecycle state를 만들지 않는 것이었다. Story 4.2는 그 후보를 받아 최종 state를 만들되 같은 time/freshness source를 유지해야 한다.
- Story 4.1 test에서 age 90초는 stale 후보, 180초는 down 후보로 고정됐다. 4.2 test는 이 후보가 각각 `stale`, `down`으로 승격되는 ordering을 검증해야 한다.
- Story 4.1은 controller/UI에 freshness 재판정 로직을 넣지 않았다. 이 경계는 계속 유지한다.

### Git Intelligence

- 최근 관련 commit `6e74e03 feat(portal): persist starter heartbeat telemetry`에서 heartbeat persistence가 완료됐다.
- 해당 commit은 `V005__create_starter_heartbeat_telemetry.sql`, `StarterHeartbeatTelemetryEntity`, `StarterHeartbeatTelemetryRepository`, `StarterHeartbeatTelemetryRecord`, `IngestHeartbeatService`, repository/service tests를 추가했다.
- Story 4.2는 이 persistence를 재사용 대상으로만 다루고, table/repository/upsert를 다시 만들면 안 된다.
- 최근 commit `6fe685f docs: align heartbeat deferred work status`는 deferred work에서 heartbeat persistence 완료와 two-axis state/liveness 구현을 분리했다.

### Latest Technical Information

- 새 외부 dependency나 최신 vendor API가 필요하지 않다.
- 현재 repo는 Java 17, Spring Boot BOM `4.0.6`, Spring Data JPA/Jakarta Persistence, PostgreSQL/Flyway, JUnit 5/AssertJ, ArchUnit `1.4.1`, Testcontainers `2.0.5`를 사용한다.
- Story 4.2는 service/model unit test 중심이며 새 library를 추가하지 않는다.

## Source References

- `implementation-artifacts/epic-4-context.md` - Epic 4 목표, Story 4.2 two-axis dependency, heartbeat guardrail
- `implementation-artifacts/deferred-work.md` - heartbeat telemetry persistence 완료, Story 4.2 two-axis implementation, down copy/rename guard
- `implementation-artifacts/spec-heartbeat-telemetry-persistence.md` - 완료된 heartbeat telemetry table/repository/service code map과 side-effect 금지
- `planning-artifacts/contracts/state-semantics.md` - state enum, evaluation order, degraded hysteresis, two-axis interpretation matrix
- `planning-artifacts/contracts/time-buckets.md` - UTC 30초 bucket, current/baseline 15분, freshness source, stale/down threshold
- `planning-artifacts/contracts/starter-failure-semantics.md` - starter fail-open, heartbeat 미수신/성공 의미, host down 오판 금지
- `planning-artifacts/contracts/read-model-contract.md` - `starterConnection`, zeroInsight reason, read model boundary, heartbeat/source separation
- `planning-artifacts/stories/4-1-time-bucket-contract-implementation.md` - 기존 freshness helper와 repository timestamp lookup, 이전 story guardrails
- `planning-artifacts/epics.md` - Epic 4 Story 4.2 acceptance foundation
- `_bmad/custom/project-context.md` - MVC + Spring Data JPA implementation policy

## Test Requirements

- `LifecycleStateServiceTest` 또는 동등 focused unit test
  - no accepted bucket + recent heartbeat -> metric state `waiting_first_data`, connection `starter_connected`, diagnosis `waiting_for_traffic`/`starter_connected_but_no_accepted_bucket`, not host down
  - no accepted bucket + no/stale heartbeat -> metric state는 accepted bucket 기준 유지, connection `telemetry_unreachable`/`unknown`, host down 원인 미확정
  - stale candidate + recent heartbeat -> metric state `stale`, connection `starter_connected`, diagnosis `no_recent_traffic`/`metric_data_idle`
  - down candidate + recent heartbeat -> metric state `down` as data-plane candidate, connection `starter_connected`, host process down copy 금지
  - current bucket + stale heartbeat -> metric state는 sample/concern 기준 유지, connection warning만 별도 output
  - current freshness + insufficient sample -> `unknown`
  - current freshness + idle traffic -> `idle`
  - current freshness + enough sample + no concern -> `active`
  - degraded enter: guard passed, confidence `0.75`, bad buckets `3/5` -> `degraded`
  - degraded blocked: confidence below `0.75`, guard failed, bad buckets `2/5`, or 30초 단발 blip -> not `degraded`
  - degraded resolve: absence/confidence `< 0.60`/threshold recovery 중 하나가 5 consecutive buckets일 때만 active로 해소
  - heartbeat input을 바꿔도 `AcceptedBucketFreshness`의 age/status는 바뀌지 않음
- `MvcLayerBoundaryTest`
  - 새 `LifecycleState*` 계산 class가 `domain.state.service` 또는 `domain.state.model`에만 있는지 확인
  - controller/repository/dto dependency boundary 회귀 확인
- Suggested commands:
  - `./gradlew :observability-portal:test --tests com.observation.portal.domain.state.service.LifecycleStateServiceTest`
  - `./gradlew :observability-portal:test`
  - `git diff --check`

## Developer Guardrails

- heartbeat telemetry persistence는 이미 완료됐다. 새 migration/table/repository/upsert를 만들지 않는다.
- heartbeat는 accepted bucket freshness, p95/p99, rule, endpoint priority, dashboard snapshot, operational event source가 아니다.
- heartbeat는 host business health/degraded 판단 근거가 아니다.
- accepted bucket metric state와 starter connection/liveness는 typed input/output 모두에서 분리한다.
- `LifecycleStateService`는 Story 4.1 freshness helper를 재사용하고 heartbeat로 freshness age를 보정하지 않는다.
- `LifecycleStateService`는 p95/p99, histogram merge, endpoint priority, insight rule service를 만들지 않는다.
- UI, controller, repository에 state 재판정 로직을 두지 않는다.
- `waiting_first_data`, `stale`, `down`은 accepted bucket data-plane state다. heartbeat가 최근이어도 `active`로 승격하지 않는다.
- `down` copy는 host application process down 확정처럼 쓰지 않는다.
- stale/down 이후 recovery guidance 상세 구현은 Story 4.3으로 남긴다.
- unrelated refactor와 기존 ingest/heartbeat behavior 변경을 피한다.

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Implementation Plan

- `domain.state.model`에 metric axis와 starter connection axis를 분리한 input/output record와 enum을 추가한다.
- `domain.state.service.LifecycleStateService`에서 freshness ordering, sample/idle/active, degraded hysteresis, two-axis diagnosis를 구현한다.
- focused unit tests로 state matrix와 guardrail을 고정한 뒤 portal test suite를 실행한다.

### Debug Log

- 2026-05-24T20:49:26+0900: BMAD dev-story workflow를 시작하며 Story 4.2 sprint status를 `in-progress`로 전환했다.
- 2026-05-24T20:49:26+0900: RED 단계에서 `LifecycleStateServiceTest`를 추가했고, 신규 model/service class 부재로 compile 실패하는 것을 확인했다.
- 2026-05-24T20:49:26+0900: `domain.state.model`에 metric lifecycle input/output, starter connection input/output, degraded hysteresis typed input을 추가했다.
- 2026-05-24T20:49:26+0900: `LifecycleStateService`에서 accepted bucket freshness ordering, sample/idle/active, degraded enter/resolve hysteresis, starter connection diagnosis를 구현했다.
- 2026-05-24T20:49:26+0900: `MvcLayerBoundaryTest`에 state feature class 위치 guard를 추가했다.
- 2026-05-24T20:49:26+0900: focused test command 통과: `./gradlew :observability-portal:test --tests com.observation.portal.domain.state.service.LifecycleStateServiceTest`.
- 2026-05-24T20:49:26+0900: architecture focused test command 통과: `./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest`.
- 2026-05-24T20:49:26+0900: `./gradlew :observability-portal:test` 통과.
- 2026-05-24T20:49:26+0900: `git diff --check` 통과.

### Completion Notes

- Ultimate context engine analysis completed - comprehensive developer guide created.
- accepted bucket metric axis와 starter heartbeat connection axis를 `MetricLifecycleInput`/`StarterConnectionInput`으로 분리했다.
- `LifecycleStateDecision`은 `metricState`와 `starterConnection`을 별도 typed field로 반환하며 starter connection 결과가 metric state enum을 덮어쓰지 않는다.
- Story 4.1의 `AcceptedBucketFreshnessStatus`를 재사용해 `WAITING_FIRST_DATA`, `STALE_CANDIDATE`, `DOWN_CANDIDATE`, `CURRENT` ordering을 적용했다.
- freshness가 current일 때만 sample 부족, idle traffic, degraded concern/hysteresis를 평가한다.
- degraded 진입/해소는 typed hysteresis input의 guard/confidence/bucket count/recovery count threshold만 소비하며 p95/p99, rule, endpoint priority 계산은 추가하지 않았다.
- 최근 heartbeat와 bucket 없음/오래됨, heartbeat 없음/stale와 bucket 없음/오래됨, current bucket과 stale heartbeat 조합을 별도 starter connection diagnosis로 표현했다.
- 새 migration/table/repository/upsert, heartbeat endpoint/service 변경, controller/repository/UI state 재판정 로직은 추가하지 않았다.

### File List

- `planning-artifacts/stories/4-2-lifecycle-state-service.md`
- `implementation-artifacts/sprint-status.yaml`
- `observability-portal/src/main/java/com/observation/portal/domain/state/model/DegradedHysteresisInput.java`
- `observability-portal/src/main/java/com/observation/portal/domain/state/model/LifecycleStateCode.java`
- `observability-portal/src/main/java/com/observation/portal/domain/state/model/LifecycleStateDecision.java`
- `observability-portal/src/main/java/com/observation/portal/domain/state/model/MetricLifecycleInput.java`
- `observability-portal/src/main/java/com/observation/portal/domain/state/model/MetricLifecycleState.java`
- `observability-portal/src/main/java/com/observation/portal/domain/state/model/MetricSampleReadiness.java`
- `observability-portal/src/main/java/com/observation/portal/domain/state/model/MetricTrafficActivity.java`
- `observability-portal/src/main/java/com/observation/portal/domain/state/model/StarterConnectionDiagnosis.java`
- `observability-portal/src/main/java/com/observation/portal/domain/state/model/StarterConnectionFreshness.java`
- `observability-portal/src/main/java/com/observation/portal/domain/state/model/StarterConnectionInput.java`
- `observability-portal/src/main/java/com/observation/portal/domain/state/model/StarterConnectionMeaning.java`
- `observability-portal/src/main/java/com/observation/portal/domain/state/model/StarterConnectionSummary.java`
- `observability-portal/src/main/java/com/observation/portal/domain/state/model/StarterHeartbeatStatus.java`
- `observability-portal/src/main/java/com/observation/portal/domain/state/model/StarterStateImpact.java`
- `observability-portal/src/main/java/com/observation/portal/domain/state/service/LifecycleStateService.java`
- `observability-portal/src/test/java/com/observation/portal/architecture/MvcLayerBoundaryTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/state/service/LifecycleStateServiceTest.java`

### Change Log

- 2026-05-24: Story 4.2 create-story 산출물을 생성하고 ready-for-dev 상태로 전환했다.
- 2026-05-24: Lifecycle state typed model/service와 focused/architecture tests를 구현하고 portal regression suite를 통과시켰다.

## Status

review
