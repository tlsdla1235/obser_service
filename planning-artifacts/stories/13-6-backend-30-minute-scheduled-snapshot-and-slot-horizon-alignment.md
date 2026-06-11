---
artifactType: story
storyId: "13.6"
storyKey: "13-6-backend-30-minute-scheduled-snapshot-and-slot-horizon-alignment"
epic: "Epic 13. Dashboard Source of Truth Realignment"
title: "Backend 30 minute scheduled snapshot and slot horizon alignment"
architectureStyle: Traditional MVC
status: done
date: 2026-06-10
phase: P5
workType: backend
implementationScope: "scheduled snapshot cadence, capture token compatibility, and snapshot marker/history/trend horizon alignment"
productionCodeChangeThisContext: false
plannedProductionCodeChange: true
sourceOfTruthMode: read-only
dependsOn:
  - P0
  - P1
  - P2
  - P3
  - 13-2-frontend-read-model-contract-guard
  - 13-3-backend-recent-30-minutes-window-alignment
  - 13-4-backend-application-dashboard-read-model-shape-alignment
blocks:
  - P6
  - P7
  - P9
  - P10
rollbackBoundary: "scheduler cadence and repository current_window_end_utc horizon query changes must be separable rollback units"
---

# Story 13.6 - Backend 30 minute scheduled snapshot and slot horizon alignment

## Status

done

2026-06-10: P5 backend alignment story artifact를 생성하고 sprint-status를 정렬했다. 초기 artifact 생성 시점에는 backend/frontend/source code/test/migration/schema를 구현하지 않고 story artifact와 sprint-status 정렬만 수행했다.
2026-06-10: BMAD dev-story 구현으로 30분 scheduled snapshot cadence, legacy `hourly_scheduled` token 계약, `current_window_end_utc` horizon 정렬을 backend/source/test에 반영하고 review 상태로 전환했다.
2026-06-10: BMAD code review 발견 사항을 반영해 operational history projector를 slot-first timeline으로 보강하고 marker/trend 30분 slot horizon을 14일 최대치까지 정렬한 뒤 focused/story/full regression 검증을 통과해 done 상태로 전환했다.

## Story

backend 구현자로서, scheduled snapshot 저장 주기를 dashboard의 30분 slot UX에 맞추고 marker/history/trend 조회 horizon을 `dashboard_snapshots.current_window_end_utc` 기준으로 정렬하고 싶다.

그래야 P6 snapshot history/detail UI가 30분 dashboard point를 안정적으로 탐색하고, P9 retention cleanup이 `current_window_end_utc` 기준 timestamp를 그대로 사용하며, persisted `hourly_scheduled` token rename 없이 기존 persistence/API compatibility를 유지할 수 있다.

## Source of Truth

아래 문서는 이 story의 기준이다. 이 story는 해당 문서의 의미를 재정의하지 않고 backend 구현 지침으로만 옮긴다.

1. `implementation-artifacts/sprint-status.yaml`
2. `planning-artifacts/epics.md`
3. `planning-artifacts/dashboard-source-of-truth-realignment-roadmap.md`
4. `planning-artifacts/dashboard-source-of-truth-realignment-sequence.yaml`
5. `planning-artifacts/contracts/read-model-contract.md`
6. `planning-artifacts/source-of-truth/application-dashboard-read-model-mvp-source-of-truth.md`
7. `planning-artifacts/source-of-truth/dashboard-snapshot-mvp-source-of-truth.md`
8. `planning-artifacts/source-of-truth/dashboard-snapshot-retention-cleanup-source-of-truth.md`
9. `planning-artifacts/source-of-truth/instance-dashboard-read-model-mvp-source-of-truth.md`
10. `planning-artifacts/stories/13-2-frontend-read-model-contract-guard.md`
11. `planning-artifacts/stories/13-3-backend-recent-30-minutes-window-alignment.md`
12. `planning-artifacts/stories/13-4-backend-application-dashboard-read-model-shape-alignment.md`
13. `planning-artifacts/stories/13-5-frontend-application-dashboard-ia-realignment.md`
14. `_bmad/custom/project-context.md`
15. `planning-artifacts/architecture.md`
16. `planning-artifacts/architecture-implementation-supplement.md`
17. `planning-artifacts/project-structure.md`

## Background

Epic 13은 완료된 Epic 4/5/6/10 story를 다시 여는 작업이 아니라, 확정된 Dashboard Source of Truth를 새 alignment story 묶음으로 적용하는 tracking epic이다. Story 13.2는 frontend read model contract guard로 server-computed state/order/source semantics를 fail-closed로 고정했고, Story 13.3은 Application Dashboard live와 Instance live/evidence를 `recent_30_minutes` accepted bucket window로 정렬했다. Story 13.4는 live response와 snapshot 저장 payload가 공유할 canonical `dashboard_read_model.v1` shape를 닫았으며, Story 13.5는 frontend live Application Dashboard IA를 그 shape에 맞췄다.

P5의 목적은 저장된 snapshot point와 read-side horizon을 30분 dashboard slot 의미에 맞추는 것이다. 현재 코드 관찰 기준으로 `DashboardSnapshotScheduler`는 hourly target span, hourly method/comment, `utc_hourly_scheduler` trigger source를 사용하고, `DashboardSnapshotProperties`의 fallback staleness base도 1시간 cadence에 묶여 있다. 반면 Source of Truth는 dashboard 판단 window와 scheduled snapshot cadence를 모두 30분으로 두며, 30분마다 "그 시점의 최근 30분 dashboard read model"을 저장해야 한다고 고정한다.

Persistence/API compatibility는 별도 축이다. `DashboardSnapshotCaptureReason.HOURLY_SCHEDULED`의 persisted token은 계속 `hourly_scheduled`로 남긴다. 이 이름은 legacy persistence/API token일 뿐 사용자-facing 의미가 "hourly"라는 뜻이 아니다. 후속 frontend가 이 token을 "30분 정기 저장" 또는 동등한 의미로 표시할 수 있도록 backend model/API/read semantics 주석과 test expectation을 명확히 해야 한다.

Read-side horizon도 현재는 generated time 중심으로 남아 있다. `DashboardSnapshotRepository.findTrendRowsNewestFirst`, `findMarkerRows`, `findOperationalHistoryRows`는 `generatedAtSince/generatedAtUntil` 인자와 `snapshot.generatedAt` JPQL range를 사용한다. Source of Truth와 retention cleanup 문서는 UX slot과 retention 기준을 `current_window_end_utc`로 고정하고, `generated_at`은 capturedAt/provenance와 deterministic ordering 보조값으로만 유지하라고 한다.

## Aligns

- `5-8-dashboard-snapshot-persistence-and-marker-contract`: snapshot row는 dashboard read model 저장본이며 marker/history는 timeline index로만 사용한다.
- `5-8-a-dashboard-snapshot-writer-and-capture-policy`: writer duplicate identity는 `application_id + current_window_end_utc`이고 priority-aware upsert를 유지한다.
- `5-8-b-snapshot-marker-detail-recovery-source`: marker/helper column은 detail state/evidence source가 아니며 detail source는 stored `read_model_json`이다.
- `5-9-a-operational-event-history-api-skeleton-and-source-boundary`: operational history는 `DashboardSnapshotRepository` source row를 읽고 current dashboard를 재계산하지 않는다.
- `5-9-b-operational-event-promotion-suppression-and-period-folding`: event projection은 stored snapshot/read model source를 기반으로 하며 repository가 event 의미를 계산하지 않는다.
- `12-4-snapshot-delay-and-pipeline-lag-semantics`: capture delay, accepted_at cutoff, queue/ingest lag 의미를 dashboard current 판단과 섞지 않는다.
- `13-2-frontend-read-model-contract-guard`: frontend가 marker/history/trend order와 read semantics를 재계산하지 않는 guard를 유지하므로 backend는 slot-first order와 source semantics를 명확히 내려준다.
- `13-3-backend-recent-30-minutes-window-alignment`: scheduled capture target window도 recent 30 minutes dashboard point 의미를 따른다.
- `13-4-backend-application-dashboard-read-model-shape-alignment`: stored snapshot payload는 canonical `dashboard_read_model.v1` read model 복원 source다.
- `13-5-frontend-application-dashboard-ia-realignment`: frontend live dashboard가 소비하는 30분/readSemantics 계약과 snapshot 후속 surface가 같은 source 언어를 사용한다.

## Supersedes

아래 항목은 완료 story 본문이나 이력을 바꾸지 않고, P5 이후 backend 구현에서 좁게 대체하는 해석이다.

- scheduled snapshot UX를 1시간 dashboard point로 읽는 해석.
- `hourly_scheduled` persisted token 이름을 사용자-facing cadence copy로 그대로 노출해도 된다는 해석.
- marker/history/trend 조회 horizon과 retention boundary를 `generated_at` 또는 API `generatedAt` 중심으로 자르는 해석.
- `generated_at`이 `current_window_end_utc`보다 slot identity, retention, history/date map 선택 기준으로 우선한다는 해석.
- marker bucket, capture reason, helper column을 snapshot detail state/evidence source처럼 읽는 해석.

## Hardens

- Scheduler는 30분 scheduled snapshot slot을 dispatch한다.
- `hourly_scheduled` persisted token은 rename하지 않고 legacy persistence/API compatibility token으로 유지한다.
- 사용자-facing copy 계약은 `hourly_scheduled`가 "30분 정기 저장" 의미로 표시될 수 있음을 명확히 한다.
- writer identity는 계속 `application_id + current_window_end_utc`이다.
- marker/history/trend repository horizon은 `current_window_end_utc` range로 정렬한다.
- `generated_at`은 capturedAt/provenance와 deterministic ordering/tie-breaker 보조값으로 유지한다.
- P6 snapshot history/detail UI와 P9 retention cleanup이 같은 기준 timestamp인 `dashboard_snapshots.current_window_end_utc`를 사용할 수 있다.

## Rollback

- scheduler cadence 변경과 repository horizon query 변경은 분리 가능한 rollback 단위로 구현한다.
- scheduler rollback은 30분 slot dispatch 계산, scheduler naming/comment/trigger source, fallback staleness cadence 관련 변경만 되돌릴 수 있어야 한다.
- repository rollback은 marker/history/trend query의 range column과 parameter naming/order만 되돌릴 수 있어야 한다.
- token rename, writer identity 변경, Source of Truth 문서 수정, 완료 story 본문 수정, frontend guard 완화는 rollback 경로가 아니다.
- schema/migration은 기본 scope가 아니다. `current_window_end_utc` query 성능이나 index 필요성이 실제로 증명되면 이번 story에서 임의 migration을 만들지 말고 별도 승인/후속 story로 분리한다.

## Out of scope

- 이번 컨텍스트의 backend/frontend/source code/test/migration/schema 구현.
- Source of Truth 문서 수정 또는 의미 재정의.
- 완료 story 13.2/13.3/13.4/13.5 본문 수정 또는 done 상태 변경.
- cleanup physical delete, cleanup scheduler/service 구현.
- frontend snapshot history/detail UI realignment.
- Instance Dashboard live/snapshot mode split.
- frontend instance surface split.
- `hourly_scheduled` token rename, migration, API breaking change.
- writer duplicate identity 변경.
- raw snapshot explorer, endpoint timeseries table, snapshot-derived helper table, 별도 `operational_events` table.
- migration/schema 변경. 구현 조사에서 절대 필요성이 증명되면 별도 승인/후속 story로 분리한다.
- 기존 untracked `dbml-error.log` 수정, 삭제, stage.

## Acceptance Criteria

1. Given scheduler tick이 UTC 기준 30분 slot의 capture delay 이후 도달했을 때, When scheduled snapshot dispatcher가 실행되면, Then target `currentWindowEndUtc`는 직전 30분 boundary로 계산되고 eligible application마다 capture request를 한 번만 dispatch한다.
2. Given 같은 30분 target slot에 여러 scheduler tick이 발생할 때, When 첫 dispatch 이후 같은 `currentWindowEndUtc`를 다시 만나면, Then scheduler는 같은 JVM process 안에서 같은 slot을 중복 dispatch하지 않는다.
3. Given capture delay가 30분 slot boundary를 넘어서는 설정일 때, When scheduler가 target window를 계산하면, Then delay를 반영해 이미 cutoff가 지난 30분 slot을 선택하고 `snapshotCutoffAt = currentWindowEndUtc + captureDelay` 의미를 유지한다.
4. Given scheduled capture request가 생성될 때, When writer/capture policy에 reason을 전달하면, Then persisted token은 계속 `DashboardSnapshotCaptureReason.HOURLY_SCHEDULED.token() == "hourly_scheduled"`이고 token rename, `thirty_minute_scheduled` 같은 새 persisted token, migration은 만들지 않는다.
5. Given API/model/copy 계약에서 `hourly_scheduled` capture reason을 설명할 때, When 사용자-facing copy를 만들 후속 frontend가 이 token을 해석하면, Then "30분 정기 저장" 또는 동등한 scheduled 30분 의미로 표시할 수 있고 "hourly"를 사용자-facing cadence로 강제하지 않는다.
6. Given `DashboardSnapshotWriterService`가 snapshot row를 insert/update할 때, When 30분 scheduled capture와 state/high-confidence/fallback reason이 같은 window에서 경쟁하면, Then duplicate identity는 계속 `application_id + current_window_end_utc`이고 priority-aware upsert는 lower/equal priority write로 대표 row를 downgrade하지 않는다.
7. Given snapshot marker 조회 horizon이 계산될 때, When repository가 marker rows를 조회하면, Then range predicate는 `generated_at`이 아니라 `current_window_end_utc >= since && current_window_end_utc <= until` 의미를 사용하고, 응답 정렬은 slot order를 우선하며 `generated_at`과 `id`는 deterministic tie-breaker로만 사용한다.
8. Given operational event history 조회 horizon이 계산될 때, When repository가 history source rows를 조회하면, Then range predicate는 `current_window_end_utc` 기준이고 current dashboard, accepted bucket, heartbeat, lifecycle/triage/endpoint service를 조인하지 않는다.
9. Given instance snapshot trend 조회 horizon이 계산될 때, When repository가 trend rows를 조회하면, Then range predicate는 `current_window_end_utc` 기준이고 trend point는 stored `read_model_json.instanceSummary.items[]` projection으로만 생성된다.
10. Given read model/API가 `generatedAt` 또는 `generated_at`을 노출할 때, When marker/history/trend inclusion 또는 retention horizon을 평가하면, Then 해당 값은 capture/provenance와 deterministic ordering 보조값으로만 유지되고 horizon 기준으로 사용되지 않는다.
11. Given P6 snapshot history/detail UI와 P9 retention cleanup이 이어질 때, When 후속 surface가 backend repository/service contract를 사용하면, Then `currentWindowEndUtc` 또는 동등한 이름의 slot timestamp를 기준 horizon으로 받고 retention 밖 row가 남아 있더라도 read surface가 `current_window_end_utc` 기준으로 제외할 수 있다.
12. Given implementation diff를 검토할 때, When verification을 수행하면, Then Source of Truth 문서, 완료 story 13.2/13.3/13.4/13.5, frontend snapshot UI, instance dashboard mode split, cleanup physical delete, migration/schema, 기존 untracked `dbml-error.log`가 변경되지 않았음이 확인된다.

## Tasks / Subtasks

- [x] Backend scheduler를 30분 scheduled snapshot slot으로 정렬한다. (AC: 1, 2, 3, 5)
  - [x] `DashboardSnapshotScheduler`의 hourly target span, method/comment, trigger source naming을 30분 slot 의미로 정렬한다.
  - [x] `@Scheduled` minute tick 자체는 유지할 수 있지만, target window end 계산은 `ChronoUnit.HOURS` truncation이 아니라 UTC 30분 boundary를 사용한다.
  - [x] `lastDispatchedWindowEndUtc` guard가 같은 30분 slot을 중복 dispatch하지 않게 유지한다.
  - [x] capture delay가 30분보다 크거나 slot을 넘어설 때 target boundary와 cutoff를 안정적으로 계산하는 test를 추가/수정한다.
  - [x] `ApplicationRepository.findActiveApplicationsEligibleForScheduledSnapshot` comment/test naming에서 hourly 사용자-facing 해석을 제거하되, heartbeat는 snapshot 저장 eligibility gate일 뿐 read model source가 아님을 유지한다.
  - [x] `DashboardSnapshotProperties`의 fallback staleness threshold가 scheduled cadence와 묶인 값이면 30분 scheduled cadence에 맞게 조사/정렬한다.
- [x] Snapshot writer/capture policy compatibility를 보호한다. (AC: 4, 5, 6, 10)
  - [x] `DashboardSnapshotCaptureReason.HOURLY_SCHEDULED` enum 이름과 persisted token `hourly_scheduled`를 유지한다.
  - [x] 새 persisted token, migration, DB constraint 변경, API breaking rename을 만들지 않는다.
  - [x] model Javadoc/test에 `hourly_scheduled`가 legacy persistence/API token이고 사용자-facing 의미는 30분 scheduled snapshot임을 명시한다.
  - [x] `DashboardSnapshotWriterService`가 `application_id + current_window_end_utc` identity와 priority-aware upsert를 계속 사용하는지 regression test로 보호한다.
  - [x] `DashboardSnapshotCapturePolicy`가 scheduled/fallback reason 보존과 state/high-confidence/short-spike 대표 reason 승격 우선순위를 유지하는지 확인한다.
- [x] Marker/history/trend repository horizon을 `current_window_end_utc` 기준으로 정렬한다. (AC: 7, 8, 9, 10, 11)
  - [x] `DashboardSnapshotRepository.findTrendRowsNewestFirst`, `findMarkerRows`, `findOperationalHistoryRows`의 parameter naming을 `currentWindowEndSince/currentWindowEndUntil` 또는 동등한 slot horizon 이름으로 바꾼다.
  - [x] `DashboardSnapshotJpaRepository` JPQL range predicate를 `snapshot.currentWindowEndUtc` 기준으로 바꾼다.
  - [x] marker rows는 `currentWindowEndUtc ASC`, 그 다음 `generatedAt ASC`, `id ASC` 같은 deterministic order를 사용한다.
  - [x] history rows는 `currentWindowEndUtc DESC`, 그 다음 `generatedAt DESC`, `id ASC` 같은 deterministic order를 사용한다.
  - [x] trend rows는 newest-first fetch는 `currentWindowEndUtc DESC` 기준으로 가져오고 service response는 기존처럼 captured/slot ascending order로 projection하되 누락 snapshot을 보간하지 않는다.
  - [x] `generatedAt`은 model field로 유지하되 horizon inclusion 기준이 아님을 repository facade comment와 tests에 명시한다.
  - [x] existing generated_at index로 성능이 부족하다고 판단되면 이번 story에서 migration을 만들지 말고 별도 승인/후속 story로 분리한다.
- [x] Tests를 보강한다. (AC: 1~12)
  - [x] `DashboardSnapshotSchedulerTest`를 30분 slot boundary, cutoff 이후 dispatch, same-slot no duplicate, delay crossing slot case로 정렬한다.
  - [x] `DashboardSnapshotPropertiesTest`가 fallback staleness/cutoff helper의 cadence 의미를 검증하도록 보강한다.
  - [x] `DashboardSnapshotCaptureReasonTest`에 `hourly_scheduled` token 유지와 30분 scheduled display 의미를 guard하는 assertion/comment를 추가한다.
  - [x] `DashboardSnapshotWriterServiceTest`/integration test에서 writer identity가 `current_window_end_utc`이고 token rename이 없음을 보호한다.
  - [x] `DashboardSnapshotRepositoryIntegrationTest` fixture를 `generated_at`과 `current_window_end_utc`가 다르게 보이는 row로 구성해 range predicate가 `current_window_end_utc`임을 증명한다.
  - [x] `DashboardSnapshotMarkerServiceTest`, `OperationalEventHistoryServiceTest`, `OperationalEventHistoryQueryTest`, `InstanceSnapshotTrendServiceTest`, `InstanceSnapshotTrendControllerTest`의 mocked repository expectation을 slot horizon naming/value로 정렬한다.
  - [x] forbidden dependency constructor tests가 current/live source recalculation 금지 경계를 계속 보호하는지 유지한다.
- [x] Verification 후보를 실행한다. (AC: 1~12)
  - [x] Backend focused test command 후보를 실행한다.
  - [x] scheduler/repository/service/controller 관련 test 후보를 실행한다.
  - [x] `rg`로 `hourly_scheduled` rename 여부와 사용자-facing hourly copy 잔존 여부를 확인한다.
  - [x] `rg`로 `generatedAt`/`generated_at` horizon 사용처와 `current_window_end_utc` predicate 사용처를 확인한다.
  - [x] `git diff --check`를 실행한다.
  - [x] `git status --short`로 보호 대상 변경과 `dbml-error.log` 상태를 확인한다.
- [x] 보호 대상 확인을 완료한다. (AC: 12)
  - [x] Source of Truth 문서가 수정되지 않았는지 확인한다.
  - [x] 완료 story 13.2/13.3/13.4/13.5 본문과 done 상태가 수정되지 않았는지 확인한다.
  - [x] frontend snapshot history/detail UI, instance dashboard mode split, cleanup physical delete 구현이 diff에 없는지 확인한다.
  - [x] migration/schema 파일이 diff에 없거나, 포함된 경우 별도 승인/후속 story가 필요한 scope violation으로 분리되었는지 확인한다.
  - [x] 기존 untracked `dbml-error.log`를 수정/삭제/stage하지 않았는지 확인한다.

## Dev Notes

- Active implementation baseline은 Traditional MVC + Service/Repository Layering이다. Portal package는 `com.observation.portal.domain` feature-first MVC 구조를 따른다.
- 문서, Javadoc, test display name, 핵심 구현 주석은 프로젝트 `AGENTS.md` 지침에 맞춰 한국어로 작성한다. 외부 API 이름, 클래스명, persisted token처럼 원문 유지가 더 명확한 표현만 필요한 범위에서 영어를 함께 쓴다.
- Controller는 request/response/status mapping을 맡고 service를 호출한다. Repository/JPA entity는 public API response DTO나 service external return model로 직접 노출하지 않는다.
- Flyway migration이 schema source of truth다. 이번 story의 기본 scope는 migration/schema 변경 없음이다.
- `DashboardSnapshotScheduler`는 현재 `HOURLY_TARGET_SPAN = Duration.ofHours(1)`, `dispatchHourlyScheduledCaptures()`, `targetWindowEnd(...).truncatedTo(ChronoUnit.HOURS)`, `triggerSource="utc_hourly_scheduler"`를 사용한다. P5 implementation은 30분 slot target 계산으로 바꾸되 persisted capture reason token은 그대로 둔다.
- `DashboardSnapshotProperties`는 `captureDelay=120s`, `fallbackGrace=5m`, `BASE_FALLBACK_STALENESS=1h`를 사용한다. 1시간 base가 scheduled cadence compatibility에서 온 값이면 30분 scheduled cadence와 query fallback freshness 의미를 함께 조사해야 한다.
- `ApplicationRepository.findActiveApplicationsEligibleForScheduledSnapshot`는 accepted bucket 존재와 최근 starter heartbeat 조건으로 scheduled capture 후보 application을 조회한다. Heartbeat는 snapshot 저장 eligibility gate이며 read model/state/metric freshness source로 합성하지 않는다.
- `DashboardSnapshotCaptureReason.HOURLY_SCHEDULED.token()`은 `hourly_scheduled`다. Source of Truth는 이 token 이름을 legacy persistence/API compatibility로 유지하되 scheduled snapshot의 사용자-facing 의미와 cadence는 30분으로 둔다.
- `DashboardSnapshotWriterService`는 `DashboardSnapshotWriteValues.currentWindowEndUtc`를 identity로 `findByIdentityForUpdate(applicationId, currentWindowEndUtc)` 후 insert/update/no-op을 수행한다. 이 identity는 P5에서도 바꾸지 않는다.
- `DashboardSnapshotCapturePolicy`는 scheduled/fallback reason을 후보로 보존하고, state change/high-confidence/short spike가 성립하면 fixed priority로 대표 reason을 승격한다. P5는 scheduler cadence/horizon 정렬이지 reason priority 재설계가 아니다.
- `DashboardSnapshotRepository.findTrendRowsNewestFirst`, `findMarkerRows`, `findOperationalHistoryRows`는 현재 `generatedAtSince/generatedAtUntil` parameter와 `snapshot.generatedAt` range predicate를 사용한다. P5는 이 range predicate와 parameter naming을 `currentWindowEndUtc` 기준으로 바꾸는 story다.
- `DashboardSnapshotJpaRepository.findPreviousRows`와 `findPreviousActiveRows`는 이미 `snapshot.currentWindowEndUtc < :currentWindowEndUtc`와 `currentWindowEndUtc desc, generatedAt desc` ordering을 사용한다. 이 패턴을 marker/history/trend horizon 정렬의 참고로 삼는다.
- `DashboardSnapshotTrendRow`와 `DashboardSnapshotDetailRow`는 `generatedAt`과 `currentWindowEndUtc`를 모두 담는다. `generatedAt`은 capturedAt/provenance/tie-breaker로 유지하고, inclusion horizon은 `currentWindowEndUtc`가 되어야 한다.
- `OperationalEventHistoryService`와 `InstanceSnapshotTrendService`는 `DashboardSnapshotRepository`만 사용하고 forbidden live source dependency tests를 갖고 있다. P5는 이 recalculation 금지 경계를 유지하면서 repository horizon 기준만 바꾼다.
- Source of Truth에 따르면 P6 snapshot history/detail UI는 marker-first 30분 point 탐색 UI가 되고, P9 retention cleanup은 `dashboard_snapshots.current_window_end_utc`를 authoritative cutoff column으로 사용한다. P5는 이 둘에 같은 기준 timestamp를 제공해야 한다.

### Candidate Implementation File List

- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotScheduler.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotProperties.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ApplicationRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotCaptureReason.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotCaptureRequest.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotWriteCommand.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotWriteValues.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotTrendRow.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotDetailRow.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotJpaRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotMarkerService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/service/OperationalEventHistoryQuery.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/service/OperationalEventHistoryService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/instance/service/InstanceSnapshotTrendService.java`

### Candidate Read-only Reference File List

- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotCaptureService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotWriterService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotCapturePolicy.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotDetailService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotMarkerClassifier.java`
- `observability-portal/src/main/java/com/observation/portal/domain/instance/service/InstanceSnapshotTrendParser.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/service/OperationalEventHistoryProjector.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/DashboardReadModelService.java`

### Candidate Test File List

- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotSchedulerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotPropertiesTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotCaptureReasonTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotCapturePolicyTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotWriterServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotWriterServiceIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotRepositoryIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotMarkerServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/controller/DashboardSnapshotControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/repository/ApplicationRepositoryIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/history/service/OperationalEventHistoryServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/history/service/OperationalEventHistoryQueryTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/history/controller/OperationalEventHistoryControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/instance/service/InstanceSnapshotTrendServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/instance/controller/InstanceSnapshotTrendControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/instance/model/InstanceSnapshotTrendReadModelShapeTest.java`

### Suggested Verification Commands

```bash
./gradlew :observability-portal:test \
  --tests 'com.observation.portal.domain.snapshot.service.DashboardSnapshotSchedulerTest' \
  --tests 'com.observation.portal.domain.snapshot.service.DashboardSnapshotPropertiesTest' \
  --tests 'com.observation.portal.domain.snapshot.model.DashboardSnapshotCaptureReasonTest' \
  --tests 'com.observation.portal.domain.snapshot.service.DashboardSnapshotCapturePolicyTest' \
  --tests 'com.observation.portal.domain.snapshot.service.DashboardSnapshotWriterServiceTest' \
  --tests 'com.observation.portal.domain.snapshot.service.DashboardSnapshotWriterServiceIntegrationTest' \
  --tests 'com.observation.portal.domain.snapshot.repository.DashboardSnapshotRepositoryIntegrationTest' \
  --tests 'com.observation.portal.domain.snapshot.service.DashboardSnapshotMarkerServiceTest' \
  --tests 'com.observation.portal.domain.snapshot.controller.DashboardSnapshotControllerTest' \
  --tests 'com.observation.portal.domain.catalog.repository.ApplicationRepositoryIntegrationTest' \
  --tests 'com.observation.portal.domain.history.service.OperationalEventHistoryServiceTest' \
  --tests 'com.observation.portal.domain.history.service.OperationalEventHistoryQueryTest' \
  --tests 'com.observation.portal.domain.history.controller.OperationalEventHistoryControllerTest' \
  --tests 'com.observation.portal.domain.instance.service.InstanceSnapshotTrendServiceTest' \
  --tests 'com.observation.portal.domain.instance.controller.InstanceSnapshotTrendControllerTest' \
  --tests 'com.observation.portal.domain.instance.model.InstanceSnapshotTrendReadModelShapeTest'

cd frontend && npm run guard:read-model-contract

./gradlew :observability-portal:test

rg -n "HOURLY_TARGET_SPAN|dispatchHourlyScheduledCaptures|utc_hourly_scheduler|hourly scheduled|hourly_scheduled|30분 정기 저장|30 minute scheduled|30-minute scheduled" \
  observability-portal/src/main/java observability-portal/src/test/java frontend/src planning-artifacts/stories/13-6-backend-30-minute-scheduled-snapshot-and-slot-horizon-alignment.md

rg -n "generatedAtSince|generatedAtUntil|snapshot\\.generatedAt|generated_at|currentWindowEndSince|currentWindowEndUntil|snapshot\\.currentWindowEndUtc|current_window_end_utc" \
  observability-portal/src/main/java/com/observation/portal/domain/snapshot \
  observability-portal/src/main/java/com/observation/portal/domain/history \
  observability-portal/src/main/java/com/observation/portal/domain/instance \
  observability-portal/src/test/java/com/observation/portal/domain/snapshot \
  observability-portal/src/test/java/com/observation/portal/domain/history \
  observability-portal/src/test/java/com/observation/portal/domain/instance

git diff --check
git status --short
```

## References

- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/epics.md#Epic 13. Dashboard Source of Truth Realignment`
- `planning-artifacts/dashboard-source-of-truth-realignment-roadmap.md`
- `planning-artifacts/dashboard-source-of-truth-realignment-sequence.yaml`
- `planning-artifacts/contracts/read-model-contract.md`
- `planning-artifacts/source-of-truth/application-dashboard-read-model-mvp-source-of-truth.md`
- `planning-artifacts/source-of-truth/dashboard-snapshot-mvp-source-of-truth.md`
- `planning-artifacts/source-of-truth/dashboard-snapshot-retention-cleanup-source-of-truth.md`
- `planning-artifacts/source-of-truth/instance-dashboard-read-model-mvp-source-of-truth.md`
- `planning-artifacts/stories/13-2-frontend-read-model-contract-guard.md`
- `planning-artifacts/stories/13-3-backend-recent-30-minutes-window-alignment.md`
- `planning-artifacts/stories/13-4-backend-application-dashboard-read-model-shape-alignment.md`
- `planning-artifacts/stories/13-5-frontend-application-dashboard-ia-realignment.md`
- `_bmad/custom/project-context.md`
- `planning-artifacts/architecture.md`
- `planning-artifacts/architecture-implementation-supplement.md`
- `planning-artifacts/project-structure.md`

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Implementation Plan

- Scheduler는 UTC minute tick을 유지하되 target slot 계산, trigger source, fallback staleness base만 30분 cadence로 좁게 변경한다.
- Persistence/API token은 `DashboardSnapshotCaptureReason.HOURLY_SCHEDULED.token() == "hourly_scheduled"`로 유지하고, Javadoc/test로 legacy token 의미와 30분 scheduled display 의미를 분리한다.
- Marker/history/trend repository range predicate는 `currentWindowEndUtc`로 옮기고, `generatedAt`은 projection/capture provenance 및 deterministic tie-breaker로 유지한다.
- Source of Truth 문서, 완료 story 본문, migration/schema, frontend snapshot UI, cleanup physical delete는 수정하지 않는다.

### Debug Log References

- RED 확인: `./gradlew :observability-portal:test --tests 'com.observation.portal.domain.snapshot.service.DashboardSnapshotSchedulerTest' --tests 'com.observation.portal.domain.snapshot.service.DashboardSnapshotPropertiesTest' --tests 'com.observation.portal.domain.snapshot.model.DashboardSnapshotCaptureReasonTest' --tests 'com.observation.portal.domain.snapshot.service.DashboardSnapshotWriterServiceTest'`가 `dispatchThirtyMinuteScheduledCaptures()` 미구현 compile error로 실패했다.
- GREEN 확인: 위 단위 테스트 묶음 통과.
- Integration 확인: `./gradlew :observability-portal:test --tests 'com.observation.portal.domain.snapshot.repository.DashboardSnapshotRepositoryIntegrationTest' --tests 'com.observation.portal.domain.snapshot.service.DashboardSnapshotWriterServiceIntegrationTest' --tests 'com.observation.portal.domain.catalog.repository.ApplicationRepositoryIntegrationTest'` 통과.
- Story 후보 검증: story에 적힌 backend focused `./gradlew :observability-portal:test --tests ...` 명령 통과.
- Full regression: 최초 `./gradlew :observability-portal:test`에서 `ProjectNavigationResourceAuthorizationTest`의 stale `current_15m` fixture가 실패했고, fixture를 `recent_30_minutes`로 정렬한 뒤 단독 테스트와 전체 `./gradlew :observability-portal:test`가 통과했다.
- 검색 검증: `rg`로 old scheduler identifiers, `hourly_scheduled`, `generatedAt`/`generated_at`, `current_window_end_utc` 사용처를 확인했다. old scheduler identifiers는 story 기록 외 source/test에서 제거됐고, `generatedAtSince/generatedAtUntil` 및 marker/history/trend `snapshot.generatedAt` range predicate는 남지 않았다.
- Quality gate: `git diff --check` 통과, `git status --short`로 `dbml-error.log` untracked 유지와 변경 파일 범위를 확인했다.
- Review fix 확인: `OperationalEventHistoryProjectorTest`, `OperationalEventHistoryServiceTest`, `InstanceSnapshotTrendServiceTest`, `InstanceSnapshotTrendControllerTest`, `InstanceSnapshotTrendReadModelShapeTest`, `DashboardSnapshotMarkerServiceTest`, `DashboardSnapshotControllerTest` focused 묶음 통과.
- Review fix story 후보 검증: Story 13.6 backend focused `./gradlew :observability-portal:test --tests ...` 명령 통과.
- Review fix full regression: `./gradlew :observability-portal:test` 통과.
- Review fix quality gate: `git diff --check` 통과, old `generatedAt` timeline 및 168/336 horizon 잔존 패턴 검색 검증 통과.
- Done 상태 확인 후 재검증(2026-06-10): `git status --short`에서 기존 untracked `dbml-error.log`는 그대로 남아 있고 이 story 문서만 modified 상태임을 확인했다.
- Done 상태 확인 후 재검증(2026-06-10): `implementation-artifacts/sprint-status.yaml`의 `13-6-backend-30-minute-scheduled-snapshot-and-slot-horizon-alignment`는 `done`이라 reopen/status rollback 없이 유지했다.
- Done 상태 확인 후 재검증(2026-06-10): story 후보 backend focused `./gradlew :observability-portal:test --tests ...` 명령 통과, `frontend`의 `npm run guard:read-model-contract` 통과, 전체 `./gradlew :observability-portal:test` 통과.
- Done 상태 확인 후 재검증(2026-06-10): `git diff --check` 통과, `git diff --name-only`가 이 story 문서만 표시해 Source of Truth 문서, 완료 story 13.2/13.3/13.4/13.5, migration/schema, frontend snapshot UI, instance dashboard split, cleanup physical delete diff가 없음을 확인했다.
- Done 상태 확인 후 재검증(2026-06-10): `rg`로 old scheduler identifier와 generatedAt horizon predicate 잔존을 확인했고, `thirty_minute_scheduled`는 persisted token 추가가 아니라 negative guard test에만 남아 있음을 확인했다.

### Completion Notes List

- `DashboardSnapshotScheduler`를 UTC 30분 slot boundary로 정렬하고 `utc_30_minute_scheduler` trigger source, same-slot JVM guard, capture delay crossing slot case를 테스트로 보호했다.
- `DashboardSnapshotProperties` fallback staleness base를 30분 cadence로 정렬하고 cutoff helper test를 갱신했다.
- `DashboardSnapshotCaptureReason.HOURLY_SCHEDULED` enum/token은 유지하며, `hourly_scheduled`가 legacy persistence/API token이고 사용자-facing 의미는 30분 scheduled snapshot임을 Javadoc/test로 명시했다.
- `DashboardSnapshotRepository`/JPA marker, operational history, trend horizon predicate를 `currentWindowEndUtc` 기준으로 바꾸고 slot-first deterministic ordering을 적용했다.
- Marker/trend service response order metadata를 `currentWindowEndUtc_asc`로 정렬해 public read model도 slot order를 우선하도록 맞췄다.
- Operational history projection은 repository slot order를 보존하도록 `currentWindowEndUtc` 우선, `generatedAt`/snapshot id tie-breaker 순으로 정렬하고 event 발생/해소 시각도 slot end 기준으로 맞췄다.
- Marker/trend 30분 slot horizon은 7일 기본 336개, 14일 최대 672개로 정렬했다.
- Migration/schema, Source of Truth 문서, 완료 story 13.2/13.3/13.4/13.5, frontend snapshot history/detail UI, instance dashboard mode split, cleanup physical delete는 수정하지 않았다.
- 2026-06-10 done 상태 재검증에서 production code와 sprint-status는 추가 수정하지 않았고, focused backend tests, frontend read-model contract guard, 전체 backend regression, diff 보호 확인을 모두 통과했다.

### File List

- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/stories/13-6-backend-30-minute-scheduled-snapshot-and-slot-horizon-alignment.md`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ApplicationRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/service/OperationalEventHistoryProjector.java`
- `observability-portal/src/main/java/com/observation/portal/domain/instance/model/InstanceSnapshotTrendReadModel.java`
- `observability-portal/src/main/java/com/observation/portal/domain/instance/service/InstanceSnapshotTrendService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotCaptureReason.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotMarkerReadModel.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotJpaRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotMarkerService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotProperties.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotScheduler.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/controller/ProjectNavigationResourceAuthorizationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/history/service/OperationalEventHistoryProjectorTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/instance/controller/InstanceSnapshotTrendControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/instance/model/InstanceSnapshotTrendReadModelShapeTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/instance/service/InstanceSnapshotTrendServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/controller/DashboardSnapshotControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotCaptureReasonTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotRepositoryIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotFallbackCaptureServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotMarkerServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotPropertiesTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotSchedulerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotWriterServiceTest.java`
- 2026-06-10 done 상태 재검증의 추가 작업 트리 diff는 `planning-artifacts/stories/13-6-backend-30-minute-scheduled-snapshot-and-slot-horizon-alignment.md` 기록 갱신뿐이다.

### Change Log

- 2026-06-10: 30분 scheduled snapshot cadence, legacy `hourly_scheduled` token guard, `current_window_end_utc` repository horizon/order alignment, related backend tests, story/sprint status review 전환을 완료했다.
- 2026-06-10: BMAD code review 후 operational history slot-first projection, marker/trend 30분 horizon 최대치, related tests를 보강하고 story/sprint status done 전환을 완료했다.
- 2026-06-10: 이미 done인 story/sprint 상태를 유지한 채 BMAD dev-story 재검증을 수행했고 focused backend tests, frontend contract guard, full regression, diff 보호 확인을 최신화했다.
