---
artifactType: story
storyId: "4.3"
epic: "Epic 4. State Semantics and Time Windows"
title: "Recovery Guidance"
architectureStyle: Traditional MVC
status: done
date: 2026-05-24
---

# Story 4.3 - Recovery Guidance

## User Story

portal 구현자로서, stale/down 이후 새 accepted bucket이 다시 들어왔지만 sample이 아직 부족한 구간을 `LifecycleStateService`가 recovery guidance로 명확히 표현하길 원한다.

그래야 dashboard/read-model 구현이 "복구 관찰 중"과 "첫 데이터 대기", "최근 트래픽 없음", starter connection 문제를 섞지 않고, host application down 같은 원인 확정을 하지 않은 채 사용자가 다음 판단까지 기다릴 수 있다.

## Scope

포함:

- `observability-portal/src/main/java/com/observation/portal/domain/state/model`의 recovery guidance typed model 추가
- `LifecycleStateDecision`에 recovery field 추가
- `MetricLifecycleInput` 또는 동등 typed input에 이전 healthy 시각 source를 전달할 수 있는 field 추가
- `LifecycleStateService`가 existing typed input만 사용해 recovery guidance를 생성
- recovery trigger/종료 조건과 `lastHealthyAt`, `retryAfterSeconds`, `recommendedAction` field shape 고정
- recovery와 starter connection guidance의 source-of-truth 분리 유지
- focused unit tests로 recovery trigger, non-trigger, 종료 조건, copy guardrail을 검증

제외:

- dashboard API/controller/UI 구현
- repository 조회 또는 persistence 추가
- dashboard snapshot/history/event 구현
- heartbeat를 accepted bucket freshness source로 사용하는 구현
- p95/p99, insight rule, endpoint priority 계산 추가
- `DashboardReadModelService`, `TriageSummaryService`, `ZeroInsight` read model 실제 구현
- 별도 top-level `recovering` lifecycle state 추가
- `lastRecoveredAt` field 추가

## Acceptance Criteria

1. `RecoveryGuidance` 또는 동등 record를 `domain.state.model`에 추가하고 field shape를 `isRecovering`, `lastHealthyAt`, `retryAfterSeconds`, `recommendedAction`으로 고정한다.
2. `LifecycleStateDecision`은 기존 `metricState`, `starterConnection` 결과를 보존하면서 `recovery` field를 추가로 반환한다.
3. Recovery는 이전 metric state가 `STALE` 또는 `DOWN`이고, 현재 accepted bucket freshness가 `CURRENT`이며, 현재 sample readiness가 `INSUFFICIENT`일 때만 `isRecovering=true`다.
4. Recovery 활성화 시 현재 metric state는 별도 `recovering`이 아니라 기존 Story 4.2 흐름의 `UNKNOWN`으로 유지된다.
5. `waiting_first_data -> unknown`, 이전 state 없음, 이전 state `WAITING_FIRST_DATA`, 이전 state `ACTIVE`, 이전 state `IDLE`, 이전 state `UNKNOWN`은 recovery가 아니다.
6. `degraded -> active` 또는 degraded 해소/유지 흐름은 Story 4.2 `DegradedHysteresisInput` 범위로 유지하고 Story 4.3 recovery guidance와 섞지 않는다.
7. Recovery trigger가 더 이상 성립하지 않으면 `isRecovering=false`다. 특히 현재 freshness가 `CURRENT`이고 sample readiness가 `SUFFICIENT`이면 recovery는 종료된다.
8. 현재 freshness가 `STALE_CANDIDATE`, `DOWN_CANDIDATE`, `WAITING_FIRST_DATA`이면 recovery가 아니라 기존 metric state `STALE`, `DOWN`, `WAITING_FIRST_DATA`로 표현한다.
9. `lastHealthyAt`은 이전 healthy/active read model 또는 snapshot에서 typed input으로 받은 값만 보존한다. 현재 request의 accepted bucket freshness, `lastAcceptedBucketEndUtc`, query time으로 추론하지 않는다.
10. 이전 healthy 시각 source가 없으면 recovery 중에도 `lastHealthyAt=null`을 허용한다.
11. `retryAfterSeconds`는 `isRecovering=true`일 때 `30`, recovery가 아니면 `null`이다.
12. `recommendedAction`은 recovery 중일 때 "다음 판단까지 약 30초 기다린 뒤 accepted bucket 수용과 sample 증가를 확인하세요." 계열의 한국어 copy를 제공하고, recovery가 아니면 `null`이다.
13. `lastRecoveredAt`은 model, decision, test fixture 어디에도 추가하지 않는다.
14. Recovery guidance copy와 starter connection copy는 별도 field/surface로 유지한다. 어떤 `label`, `rationale`, `recommendedAction`도 host application down, host process down, 앱 내려감으로 단정하지 않는다.
15. `no_recent_traffic`은 `StarterConnectionDiagnosis`로 유지할 수 있지만 MVP zero-insight reason code로 새로 만들지 않는다. 후속 read model 구현자는 recovery + `triageCards=[]`를 `observing_recovery`, no recent traffic/idle을 `metric_data_idle`로 매핑해야 한다.
16. Controller, repository, UI/static resource package에는 recovery 재판정 로직을 추가하지 않는다.
17. Focused unit tests가 recovery trigger, non-trigger, 종료 조건, `lastHealthyAt` source, retry/action nullability, host-down copy 금지를 검증한다.
18. `./gradlew :observability-portal:test --tests com.observation.portal.domain.state.service.LifecycleStateServiceTest`가 통과하고, 가능하면 `./gradlew :observability-portal:test`와 `git diff --check`를 함께 실행한다.

## Tasks/Subtasks

- [x] Recovery typed model 설계 및 추가 (AC: 1, 9, 10, 11, 12, 13)
  - [x] `RecoveryGuidance` record를 `domain.state.model`에 추가한다.
  - [x] `boolean isRecovering`, `Optional<Instant> lastHealthyAt`, nullable 또는 `Optional<Integer> retryAfterSeconds`, nullable 또는 `Optional<String> recommendedAction` 중 repo 스타일에 맞는 표현을 선택하되 외부 field shape는 `null` 가능 계약을 유지한다.
  - [x] public record/class와 factory/helper에는 한국어 Javadoc을 작성한다.
  - [x] `lastRecoveredAt`은 추가하지 않는다.
- [x] Previous healthy source typed input 추가 (AC: 3, 9, 10)
  - [x] `MetricLifecycleInput`에 이전 read model/snapshot에서 전달된 마지막 healthy 시각을 담을 수 있는 field를 추가하거나, 동등한 typed recovery input을 추가한다.
  - [x] 이 값은 service 내부 repository 조회로 채우지 않는다.
  - [x] 현재 request의 accepted bucket, freshness age, `evaluatedAtUtc`로 `lastHealthyAt`을 추론하지 않는다.
- [x] `LifecycleStateDecision` 확장 (AC: 2)
  - [x] 기존 `metricState`와 `starterConnection` field를 유지한다.
  - [x] 새 `recovery` field를 추가하고 null 없이 기본 non-recovery object를 반환한다.
  - [x] 기존 Story 4.2 test 기대값이 migration된 constructor/factory에 맞게 유지되도록 한다.
- [x] `LifecycleStateService` recovery 판단 구현 (AC: 3, 4, 5, 6, 7, 8, 11, 12, 14)
  - [x] recovery 판단은 `previousState in [STALE, DOWN]`, freshness `CURRENT`, sample readiness `INSUFFICIENT` 조합만 true로 한다.
  - [x] recovery true일 때 metric state는 `UNKNOWN`으로 유지한다.
  - [x] sample sufficient, stale/down candidate 재진입, waiting first data, 이전 state 없음/비대상 state는 false로 만든다.
  - [x] degraded hysteresis branch는 Story 4.2 로직 그대로 두고 recovery guidance trigger에 포함하지 않는다.
  - [x] starter connection diagnosis/copy는 기존 Story 4.2 결과를 유지하고 recovery copy와 결합하지 않는다.
- [x] Zero-insight mapping은 구현하지 않고 dev note로 연결 (AC: 15)
  - [x] Story 4.3 범위에서는 read model/API가 없으므로 `observing_recovery` reason 생성 코드를 만들지 않는다.
  - [x] 단, source comment/test name/dev note로 후속 `TriageSummaryService`/`DashboardReadModelService`가 recovery + empty triage를 `observing_recovery`로 매핑해야 함을 남긴다.
- [x] Focused unit tests 추가/갱신 (AC: 3~18)
  - [x] `LifecycleStateServiceTest`에 recovery trigger test를 추가한다.
  - [x] non-trigger와 종료 조건 test를 추가한다.
  - [x] `lastHealthyAt` source/null behavior test를 추가한다.
  - [x] `retryAfterSeconds`/`recommendedAction` nullability test를 추가한다.
  - [x] copy가 host down을 단정하지 않는 기존 assertion에 recovery copy도 포함한다.
- [x] Regression command 실행 (AC: 18)
  - [x] `./gradlew :observability-portal:test --tests com.observation.portal.domain.state.service.LifecycleStateServiceTest`
  - [x] 가능하면 `./gradlew :observability-portal:test`
  - [x] `git diff --check`

## Dev Notes

### Contract Summary

- Story 4.3은 `implementation-artifacts/spec-story-4-3-recovery-guidance-contract-decisions.md`에서 닫은 recovery guidance 결정을 구현 가능한 story로 옮긴다.
- 외부 observability 제품 패턴은 no data, loss of signal, recovering/recovered, monitoring unavailable을 실제 host 장애 확정과 분리한다는 방향으로 수렴한다.
- BMAD party 검증은 8개 결정을 채택하되 recovery 종료 조건을 acceptance constraint로 추가하라는 결론이었다.
- Story 4.3의 핵심은 "현재 metric data는 다시 들어오지만 아직 sample이 부족하다"를 `unknown + recovery.isRecovering=true`로 표현하는 것이다.

### Existing Code To Reuse

- `LifecycleStateService`
  - 현재 repository를 조회하지 않고 `MetricLifecycleInput`과 `StarterConnectionInput` typed input만 소비한다.
  - freshness ordering은 `WAITING_FIRST_DATA`, `STALE_CANDIDATE`, `DOWN_CANDIDATE`, `CURRENT` 순서로 먼저 평가한다.
  - freshness가 current일 때만 sample readiness, idle traffic, degraded hysteresis를 평가한다.
  - Story 4.3은 이 service에 recovery guidance 판단을 추가하되 repository/API/UI 경계로 확장하지 않는다.
- `MetricLifecycleInput`
  - accepted bucket axis 입력이다.
  - heartbeat 정보가 들어가지 않는 구조를 유지한다.
  - 이미 `previousState`를 가지고 있으므로 recovery trigger의 이전 state 판정에 재사용한다.
  - `lastHealthyAt` source를 전달해야 하면 이 record에 `Optional<Instant>`를 추가하거나, 같은 package의 별도 typed input으로 명시한다.
- `LifecycleStateDecision`
  - 현재 `metricState`, `starterConnection`만 담는다.
  - Story 4.3에서 `recovery` field를 추가하는 대상이다.
- `MetricLifecycleState`
  - `code`, `label`, `rationale`, `recommendedAction`을 담는다.
  - recovery는 top-level state가 아니므로 `LifecycleStateCode`에 새 enum을 추가하지 않는다.
- `StarterConnectionInput` / `StarterConnectionSummary`
  - heartbeat connection/liveness 축을 accepted bucket metric axis와 분리해 둔 기존 모델이다.
  - recovery copy와 starter connection copy를 합치지 않는다.
- `DegradedHysteresisInput`
  - degraded 진입/해소 전용 typed input이다.
  - 여기의 `recoveryConditionMet`/`recoveryConsecutiveBuckets`는 degraded hysteresis 용어이며 Story 4.3 recovery guidance trigger와 별도다.
- `AcceptedBucketFreshness` / `AcceptedBucketFreshnessStatus`
  - Story 4.1의 accepted bucket freshness source다.
  - heartbeat, `accepted_at`, UI local time이 아니라 마지막 accepted bucket `endUtc` 기준을 유지한다.

### Architecture Constraints

- Active baseline은 Traditional MVC + Service/Repository Layering이다.
- Portal package는 feature-first MVC이며 `domain.state.model`과 `domain.state.service`가 이번 story의 주된 변경 위치다.
- `domain`은 DDD pure domain layer가 아니라 업무 기능 grouping namespace다.
- Controller는 service를 호출하고 repository를 직접 호출하지 않는다.
- Repository는 저장/조회만 담당하며 lifecycle state, recovery guidance, zero-insight, p95/p99, endpoint priority를 계산하지 않는다.
- JPA entity는 persistence model이며 service result/external return model로 노출하지 않는다.
- `port`, `adapter`, `application` package를 새로 만들지 않는다.
- 새 공개 record/class/method와 핵심 helper의 주석/Javadoc은 프로젝트 지침에 따라 한국어로 작성한다.

### Implementation Shape Recommendation

- `RecoveryGuidance`는 아래 의미를 표현한다.

```json
{
  "isRecovering": true,
  "lastHealthyAt": "2026-05-08T01:08:30Z",
  "retryAfterSeconds": 30,
  "recommendedAction": "다음 판단까지 약 30초 기다린 뒤 accepted bucket 수용과 sample 증가를 확인하세요."
}
```

- Non-recovery 기본값은 `isRecovering=false`, `lastHealthyAt=<typed input value 또는 null>`, `retryAfterSeconds=null`, `recommendedAction=null` 중 하나로 정한다. 단, read-model contract에서는 recovery가 아니면 retry/action은 null이어야 한다.
- `lastHealthyAt`은 recovery 중에 특히 중요하지만, source가 없으면 null이 허용된다.
- `lastHealthyAt`을 non-recovery에도 보존할지 여부는 service model 일관성을 기준으로 선택한다. 다만 현재 accepted bucket만으로 새 값을 만들면 안 된다.
- `recommendedAction`은 자동 재시도 예약처럼 읽히면 안 된다. "다음 판단까지 약 30초"라는 관찰 안내여야 한다.
- `LifecycleStateService.decide(...)` signature가 바뀌면 기존 test helper를 함께 갱신하되, repository/service boundary를 넓히지 않는다.

### Zero-Insight Boundary

- Story 4.3은 dashboard read model/API 구현 story가 아니다.
- 이 story에서 `zeroInsight.reasonCode=observing_recovery`를 생성하는 production code를 만들지 않는다.
- 후속 Epic 5 `TriageSummaryService` 또는 `DashboardReadModelService` 구현자는 `recovery.isRecovering=true`이고 `triageCards=[]`일 때 `observing_recovery`를 우선해야 한다.
- `StarterConnectionDiagnosis.NO_RECENT_TRAFFIC`은 starter connection diagnosis로 유지 가능하지만, MVP zero-insight reason code로 `no_recent_traffic`을 새로 추가하지 않는다. read model reason은 `metric_data_idle`로 수렴한다.

### Previous Story Intelligence

- Story 4.2는 `LifecycleStateService`와 typed state models를 이미 구현했고 status는 `done`이다.
- Story 4.2의 deliberate gap은 "recovery guidance 전체 구현: stale/down 이후 sample 부족 안내의 상세 field와 copy polish는 Story 4.3 범위"였다.
- Story 4.2 test는 two-axis matrix, freshness ordering, sample/idle/active, degraded hysteresis, heartbeat guardrail을 이미 고정했다.
- Story 4.3 구현자는 기존 `assertDoesNotDeclareHostDown` helper에 recovery copy까지 포함해 regression signal을 확장하는 것이 좋다.
- 최근 commit `923c6fc feat(portal): add lifecycle state service`가 이번 story의 직접 기반이다.
- 최근 commit `6e74e03 feat(portal): persist starter heartbeat telemetry`는 heartbeat persistence를 완료했지만, Story 4.3은 해당 repository를 조회하거나 persistence를 추가하지 않는다.

### Latest Technical Information

- 새 외부 dependency나 최신 vendor API가 필요하지 않다.
- 현재 repo는 Java 17, Spring Boot BOM `4.0.6`, Spring Data JPA/Jakarta Persistence, PostgreSQL/Flyway, JUnit 5/AssertJ, ArchUnit `1.4.1`, Testcontainers `2.0.5`를 사용한다.
- Story 4.3은 service/model unit test 중심이며 새 library를 추가하지 않는다.

## Developer Guardrails

- `LifecycleStateCode.RECOVERING` 또는 top-level `recovering` state를 만들지 않는다.
- Recovery는 `metricState.code=UNKNOWN`과 `decision.recovery.isRecovering=true` 조합으로 표현한다.
- `lastRecoveredAt`을 추가하지 않는다. `planning-artifacts/api-surface.md`의 오래된 rough shape에 `lastRecoveredAt`이 보여도 최신 계약은 `read-model-contract.md`와 Story 4.3 contract decisions다.
- `lastHealthyAt`을 현재 accepted bucket, current request, freshness timestamp, query timestamp로 추론하지 않는다.
- Recovery 판단을 위해 repository, snapshot repository, dashboard history, persistence를 조회하지 않는다.
- Heartbeat freshness를 accepted bucket freshness source로 쓰지 않는다.
- Heartbeat 최근/미수신 여부로 recovery trigger를 켜거나 끄지 않는다.
- Recovery copy와 starter connection copy를 합쳐 원인을 단정하지 않는다.
- "host application down", "host process down", "앱 내려감", "프로세스 down" 같은 표현을 label/rationale/action에 넣지 않는다.
- Degraded recovery/hysteresis와 stale/down recovery guidance를 혼동하지 않는다.
- p95/p99, insight rule, endpoint priority, dashboard snapshot/history/event 코드를 추가하지 않는다.
- Controller, repository, UI/static resource package에 recovery 재판정 로직을 두지 않는다.
- unrelated refactor, package 재배치, 기존 ingest/heartbeat behavior 변경을 피한다.

## Test Requirements

Focused unit test 중심으로 작성한다. 우선 대상은 `observability-portal/src/test/java/com/observation/portal/domain/state/service/LifecycleStateServiceTest.java`다.

필수 test cases:

- 이전 state `STALE` + freshness `CURRENT` + sample `INSUFFICIENT` -> metric state `UNKNOWN`, `recovery.isRecovering=true`, `retryAfterSeconds=30`, action non-null
- 이전 state `DOWN` + freshness `CURRENT` + sample `INSUFFICIENT` -> metric state `UNKNOWN`, `recovery.isRecovering=true`
- recovery true일 때 typed input의 previous healthy timestamp가 `recovery.lastHealthyAt`으로 보존됨
- recovery true이고 previous healthy timestamp가 없으면 `recovery.lastHealthyAt`은 null/empty
- previous state 없음 + freshness `CURRENT` + sample `INSUFFICIENT` -> recovery false
- previous state `WAITING_FIRST_DATA` + freshness `CURRENT` + sample `INSUFFICIENT` -> recovery false
- previous state `ACTIVE`/`IDLE`/`UNKNOWN` + freshness `CURRENT` + sample `INSUFFICIENT` -> recovery false
- previous state `DEGRADED` + degraded resolve 또는 sample sufficient 흐름 -> recovery false, Story 4.2 hysteresis behavior 유지
- previous state `STALE`/`DOWN` + freshness `CURRENT` + sample `SUFFICIENT` -> recovery false, metric state는 existing active/degraded/idle ordering에 따름
- previous state `STALE`/`DOWN` + freshness `STALE_CANDIDATE` 또는 `DOWN_CANDIDATE` -> recovery false, metric state `STALE`/`DOWN`
- previous state `STALE`/`DOWN` + freshness `WAITING_FIRST_DATA` -> recovery false, metric state `WAITING_FIRST_DATA`
- non-recovery일 때 `retryAfterSeconds`와 `recommendedAction`은 null/empty
- recovery copy와 starter connection copy 전체가 host down을 단정하지 않음
- changing `StarterConnectionInput` between recent/stale/unknown does not change accepted bucket freshness or recovery trigger result
- `lastRecoveredAt` field가 모델에 없는지 compile-time/API shape 관점에서 확인한다. 별도 reflection test는 과하면 생략 가능하다.

Suggested commands:

```bash
./gradlew :observability-portal:test --tests com.observation.portal.domain.state.service.LifecycleStateServiceTest
./gradlew :observability-portal:test
git diff --check
```

## Source References

- `implementation-artifacts/spec-story-4-3-recovery-guidance-contract-decisions.md` - external product evidence, BMAD party validation, closed recovery decisions, acceptance seed
- `implementation-artifacts/epic-4-context.md` - Epic 4 goal, Story 4.3 cross-story dependency, recovery guidance constraints
- `implementation-artifacts/sprint-status.yaml` - Story 4.3 status source
- `planning-artifacts/epics.md` - Epic 4 Story 4.3 foundation and cross-epic state/read-model constraints
- `planning-artifacts/contracts/state-semantics.md` - recovery trigger/end condition, two-axis state semantics, MVC boundary
- `planning-artifacts/contracts/read-model-contract.md` - recovery field shape, zero-insight reason, freshness/read-model boundary
- `planning-artifacts/contracts/starter-failure-semantics.md` - starter fail-open and recovery/starter copy separation
- `planning-artifacts/contracts/time-buckets.md` - UTC 30초 bucket cadence, accepted bucket freshness source, stale/down threshold
- `planning-artifacts/stories/4-2-lifecycle-state-service.md` - previous story scope, guardrails, implementation notes, test strategy
- `planning-artifacts/architecture.md` - Traditional MVC, service/repository/controller responsibility, dashboard read flow
- `planning-artifacts/architecture-implementation-supplement.md` - feature-first MVC package and guard test constraints
- `planning-artifacts/project-structure.md` - package tree and layer responsibilities
- `observability-portal/src/main/java/com/observation/portal/domain/state/service/LifecycleStateService.java` - current lifecycle state service to extend
- `observability-portal/src/main/java/com/observation/portal/domain/state/model/*.java` - existing typed input/output model to reuse
- `observability-portal/src/test/java/com/observation/portal/domain/state/service/LifecycleStateServiceTest.java` - focused unit test location and helper patterns
- `_bmad/custom/project-context.md` - MVC + Spring Data JPA project policy

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Implementation Plan

- `RecoveryGuidance` record를 `domain.state.model`에 추가하고 Optional 기반으로 nullable read model field 계약을 표현한다.
- `MetricLifecycleInput`에 이전 read model/snapshot에서 전달받은 `previousHealthyAt` source를 추가하되 기존 5-argument constructor를 유지한다.
- `LifecycleStateDecision`에 `recovery` field를 추가하고 `LifecycleStateService`가 typed input만 사용해 recovery guidance를 만든다.
- focused unit test로 trigger, non-trigger, 종료 조건, degraded 분리, heartbeat 분리, copy guardrail, field shape를 고정한다.

### Debug Log References

- 2026-05-24T21:41:41+0900: BMAD dev-story workflow를 시작하며 sprint-status의 `4-3-recovery-guidance`를 `in-progress`로 전환했다.
- 2026-05-24T21:42:00+0900: RED 단계에서 `LifecycleStateServiceTest`에 recovery 계약 테스트를 추가했고, `LifecycleStateDecision.recovery()`와 `MetricLifecycleInput.previousHealthyAt` 부재로 compile 실패하는 것을 확인했다.
- 2026-05-24T21:43:00+0900: `RecoveryGuidance`, `MetricLifecycleInput.previousHealthyAt`, `LifecycleStateDecision.recovery`, `LifecycleStateService` recovery 판단을 구현했다.
- 2026-05-24T21:44:00+0900: `LifecycleStateServiceTest` focused command 통과.
- 2026-05-24T21:45:41+0900: `./gradlew :observability-portal:test` 통과.
- 2026-05-24T21:48:01+0900: Step 9 full regression gate로 `./gradlew :observability-portal:test --rerun-tasks` 통과.
- 2026-05-24T21:48:10+0900: `git diff --check` 통과.

### Completion Notes List

- Recovery guidance를 `metricState=UNKNOWN`과 `recovery.isRecovering=true` 조합으로 표현하고, 별도 top-level `recovering` lifecycle state는 추가하지 않았다.
- Recovery trigger는 이전 state `STALE`/`DOWN`, current accepted bucket freshness, insufficient sample 조합으로만 제한했다.
- `lastHealthyAt`은 `MetricLifecycleInput.previousHealthyAt`으로 받은 typed source만 보존하며, current bucket/freshness/query time으로 추론하지 않는다.
- Recovery 중 `retryAfterSeconds=30`과 한국어 recommendedAction을 제공하고, non-recovery에서는 retry/action을 비운다.
- Degraded hysteresis, starter connection guidance, heartbeat freshness source, repository/controller/UI 경계는 Story 4.2 흐름 그대로 유지했다.
- Story 4.3 범위 밖인 zero-insight/read-model/API/controller/UI/repository/persistence 구현은 추가하지 않았다.

### File List

- `planning-artifacts/stories/4-3-recovery-guidance.md`
- `implementation-artifacts/sprint-status.yaml`
- `observability-portal/src/main/java/com/observation/portal/domain/state/model/RecoveryGuidance.java`
- `observability-portal/src/main/java/com/observation/portal/domain/state/model/MetricLifecycleInput.java`
- `observability-portal/src/main/java/com/observation/portal/domain/state/model/LifecycleStateDecision.java`
- `observability-portal/src/main/java/com/observation/portal/domain/state/service/LifecycleStateService.java`
- `observability-portal/src/test/java/com/observation/portal/domain/state/service/LifecycleStateServiceTest.java`

### Change Log

- 2026-05-24: Story 4.3 create-story 산출물을 생성하고 ready-for-dev 상태로 전환했다.
- 2026-05-24: Recovery guidance typed model, input source, service trigger, focused tests를 구현하고 review 상태로 전환했다.

## Status

done
