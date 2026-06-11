---
artifactType: story
storyId: "13.11"
storyKey: "13-11-end-to-end-acceptance-and-demo-hardening"
epic: "Epic 13. Dashboard Source of Truth Realignment"
title: "End-to-end Acceptance and Demo Hardening"
architectureStyle: Traditional MVC
status: done
date: 2026-06-11
phase: P10
workType: validation
implementationScope: "Epic 13 Application Dashboard live, Snapshot history/detail, Instance Dashboard live/snapshot/trend, retention cleanup end-to-end acceptance, demo smoke, and guard verification"
productionCodeChangeThisContext: false
plannedProductionCodeChange: true
sourceOfTruthMode: read-only
dependsOn:
  - D2
  - P0
  - P1
  - P2
  - P3
  - P4
  - P5
  - P6
  - P7
  - P8
  - P9
  - 13-2-frontend-read-model-contract-guard
  - 13-3-backend-recent-30-minutes-window-alignment
  - 13-4-backend-application-dashboard-read-model-shape-alignment
  - 13-5-frontend-application-dashboard-ia-realignment
  - 13-6-backend-30-minute-scheduled-snapshot-and-slot-horizon-alignment
  - 13-7-frontend-snapshot-history-detail-realignment
  - 13-8-backend-instance-dashboard-live-snapshot-mode-split
  - 13-9-frontend-instance-surface-split
  - 13-10-retention-cleanup-alignment
blocks:
  - D3
  - 13-doc-3-final-planning-status-consolidation
rollbackBoundary: "acceptance fixture, demo route, smoke data, verification script, and completion-evidence note changes are separable rollback units"
---

# Story 13.11 - End-to-end Acceptance and Demo Hardening

## Status

done

2026-06-11: P10 acceptance/demo hardening story artifact를 생성하고 sprint-status를 `ready-for-dev`로 정렬했다. 이번 create-story 컨텍스트에서는 production code, tests, frontend implementation, migration/schema, Source of Truth 문서, 완료 story 13.2~13.10 본문/status, `dbml-error.log`를 수정하지 않는다.
2026-06-11: BMAD dev-story 검증으로 P10 acceptance/demo/smoke/guard evidence를 남기고 status를 `review`로 전환했다. Production code, migration/schema, Source of Truth 문서, 완료 story 13.2~13.10 본문/status, 기존 untracked `dbml-error.log`는 수정하지 않았다.
2026-06-11: BMAD review finding을 반영해 demo/smoke evidence 과장을 낮추고 누락된 실행 증거를 보강했다. 리뷰 승인 후 13.11 status를 `done`으로 전환하고, full browser demo route/fixture gap은 D3/follow-up handoff evidence로 남긴다.

## Story

acceptance 검증자로서, Epic 13에서 정렬된 Application Dashboard live, Snapshot history/detail, Instance Dashboard live/snapshot/trend, Retention cleanup 흐름이 Source of Truth 기준으로 end-to-end 연결되는지 검증하고 demo path를 단단하게 만들고 싶다.

그래야 13.2~13.10에서 완료된 read model guard, 30분 live window, canonical dashboard shape, 30분 snapshot slot, stored snapshot detail, instance surface split, cleanup/read-surface guard가 따로는 맞지만 실제 운영자 demo 흐름에서 서로 어긋나는 회귀를 P10에서 잡고, D3 final planning/status consolidation의 근거 evidence를 남길 수 있다.

## Source of Truth

아래 문서는 이 story의 기준이다. 이 story는 Source of Truth를 재정의하지 않고, 이미 승인된 문서와 완료 story 구현 결과를 acceptance/demo 검증 지침으로만 옮긴다.

1. `implementation-artifacts/sprint-status.yaml`
2. `_bmad/custom/project-context.md`
3. `planning-artifacts/epics.md`
4. `planning-artifacts/dashboard-source-of-truth-realignment-roadmap.md`
5. `planning-artifacts/dashboard-source-of-truth-realignment-sequence.yaml`
6. `planning-artifacts/source-of-truth/dashboard-snapshot-mvp-source-of-truth.md`
7. `planning-artifacts/source-of-truth/dashboard-snapshot-retention-cleanup-source-of-truth.md`
8. `planning-artifacts/source-of-truth/application-dashboard-read-model-mvp-source-of-truth.md`
9. `planning-artifacts/source-of-truth/instance-dashboard-read-model-mvp-source-of-truth.md`
10. `planning-artifacts/contracts/read-model-contract.md`
11. `planning-artifacts/stories/13-2-frontend-read-model-contract-guard.md`
12. `planning-artifacts/stories/13-3-backend-recent-30-minutes-window-alignment.md`
13. `planning-artifacts/stories/13-4-backend-application-dashboard-read-model-shape-alignment.md`
14. `planning-artifacts/stories/13-5-frontend-application-dashboard-ia-realignment.md`
15. `planning-artifacts/stories/13-6-backend-30-minute-scheduled-snapshot-and-slot-horizon-alignment.md`
16. `planning-artifacts/stories/13-7-frontend-snapshot-history-detail-realignment.md`
17. `planning-artifacts/stories/13-8-backend-instance-dashboard-live-snapshot-mode-split.md`
18. `planning-artifacts/stories/13-9-frontend-instance-surface-split.md`
19. `planning-artifacts/stories/13-10-retention-cleanup-alignment.md`
20. `planning-artifacts/stories/10-7-run-acceptance-gate.md`
21. `planning-artifacts/stories/6-8-demo-green-path.md`
22. `planning-artifacts/stories/6-9-failure-recovery-path-demo-hardening.md`
23. `planning-artifacts/stories/7-3-smoke-spring-boot-service-and-portal-communication-verification.md`

## Background

Epic 13은 완료된 Epic 4/5/6/10 story를 다시 여는 작업이 아니라, 확정된 Dashboard Source of Truth를 새 alignment story 묶음으로 적용하는 tracking epic이다. P10은 새 Source of Truth를 만들거나 완료 story의 의미를 덮어쓰는 단계가 아니다. P10은 P1~P9와 D2 문서 정렬이 실제 dashboard 운영 흐름에서 함께 맞물리는지 검증하고, 남은 final consolidation인 D3의 evidence를 준비하는 단계다.

2026-06-11 기준 완료된 Epic 13 결과는 아래와 같다.

- Story 13.2는 frontend read model contract guard를 추가해 UI가 server-computed state/order/source semantics를 재계산하지 않도록 fail-closed guard를 만들었다.
- Story 13.3은 Application Dashboard와 Instance live/evidence window를 `recent_30_minutes` accepted bucket 기준으로 정렬하고 baseline 비교를 MVP primary 판단에서 제거했다.
- Story 13.4는 live response와 snapshot stored payload가 공유하는 canonical `dashboard_read_model.v1` shape와 live/snapshot `readSemantics`를 구현했다.
- Story 13.5는 Application Dashboard live IA를 context/read semantics, data quality, lifecycle state, direct reasons, attention/first look, endpoint/resource evidence 순서로 정렬했다.
- Story 13.6은 30분 scheduled snapshot slot, legacy persisted `hourly_scheduled` token 유지, `current_window_end_utc` marker/history/trend horizon/order를 정렬했다.
- Story 13.7은 Snapshot history/date/slot 탐색을 30분 point와 14일 672-slot horizon으로 맞추고, Snapshot Detail을 stored `dashboard_snapshots.read_model_json` 복원 surface로 격상했다.
- Story 13.8은 Instance Dashboard backend를 live mode와 selected Application Snapshot 기반 snapshot mode로 분리하고, snapshot mode에서 `accepted_at` cutoff 없이 selected instance metric evidence를 재구성하게 했다.
- Story 13.9는 frontend Instance Dashboard live detail, selected Application Snapshot 기준 snapshot detail, stored Instance Snapshot Trend surface를 분리했다.
- Story 13.10은 retention cleanup scheduler/service/properties/result, physical delete repository method, retention read-surface guard를 구현했다. Cleanup physical delete rollout은 기본 disabled이고, enabled/dry-run은 schedule/cutoff 의미를 바꾸지 않는 운영 제어다.
- 13-doc-2 D2 documentation alignment는 Story 13.2~13.10 완료 구현 결과와 planning/source-of-truth 문서의 상태, 용어, 참조를 맞췄다. D2는 Source of Truth 의미를 재정의하지 않았고 완료 story의 구현 의미도 바꾸지 않았다.

P10 acceptance는 아래 end-to-end 운영자 흐름을 기준으로 검증한다.

```text
Project -> Application -> live Application Dashboard
  -> Snapshot history/date map/detail
  -> selected Application Snapshot -> Instance Dashboard snapshot mode
  -> live Instance Dashboard detail
  -> stored Instance Snapshot Trend
  -> retention expired/source absence path
```

## Aligns

- `dashboard-snapshot-mvp-source-of-truth.md`: dashboard는 최근 30분 read model이고 snapshot은 저장된 dashboard read model 복원본이라는 기준에 맞춘다.
- `application-dashboard-read-model-mvp-source-of-truth.md`: live source는 `accepted_metric_buckets`, snapshot detail source는 `dashboard_snapshots.read_model_json`, UI는 lifecycle state/endpoint priority/resource/data quality를 재계산하지 않는다는 기준에 맞춘다.
- `instance-dashboard-read-model-mvp-source-of-truth.md`: Instance Dashboard는 Application Dashboard 판단의 하위 evidence detail이며, live/snapshot mode와 Instance Snapshot Trend가 서로 다른 read surface라는 기준에 맞춘다.
- `dashboard-snapshot-retention-cleanup-source-of-truth.md`: cleanup schedule, UTC cutoff, `current_window_end_utc`, `bucket_end_utc`, 30분 evidence grace, disabled-by-default rollout control 기준에 맞춘다.
- `read-model-contract.md`: first-screen UI는 server read model을 표시하고 state/rule/p95/p99/endpoint priority/snapshot event를 계산하지 않는다는 MVC boundary에 맞춘다.
- Story 13.2~13.10: 각 slice에서 완료된 guard/backend/frontend/cleanup 구현 결과를 P10 end-to-end acceptance chain으로 연결한다.
- 13-doc-2 D2 documentation alignment: 완료 구현 결과와 문서 표현이 정렬된 상태를 P10 검증의 출발점으로 사용한다.
- Story 6.8, 6.9, 7.3, 10.7: 기존 demo, smoke, Epic 10 acceptance gate를 되돌리지 않고 Source of Truth 기준 검증 범위를 강화한다.

## Supersedes

아래 항목은 완료 story 본문이나 이력을 바꾸지 않고, P10 acceptance 기준에서 좁게 대체하는 과거 해석이다.

- Application Dashboard 또는 Instance Dashboard의 사용자-facing contract를 15분 current/baseline, `current_15m`, 직전 window baseline 비교 중심으로 읽는 해석.
- persisted `hourly_scheduled` token 이름 때문에 사용자-facing snapshot cadence를 hourly로 설명해도 된다는 해석. Token rename은 하지 않지만 copy와 acceptance 의미는 30분 정기 저장이다.
- marker bucket, marker severity, transition tag, helper column을 lifecycle state나 snapshot detail state/evidence source로 읽는 marker-as-state 해석.
- expired snapshot/detail/history/date map/instance evidence를 live dashboard, current accepted bucket, heartbeat, current instance evidence로 대체 복원해도 된다는 live/current fallback 해석.
- cleanup cutoff를 `generated_at`, `created_at`, `accepted_at`, captured/provenance timestamp 기준으로 삼아도 된다는 해석.
- Instance Dashboard snapshot mode에서 `accepted_at` cutoff를 적용해 late metric을 숨기거나, late metric 때문에 Application Snapshot stored state/evidence를 재계산/override해도 된다는 해석.
- Instance Snapshot Trend를 독립 instance health timeline, current state history, health score, root cause surface로 확장해도 된다는 해석.

## Hardens

- Epic 10 acceptance gate를 Source of Truth 기준으로 재사용해, Vite SPA가 state/order/source semantics를 UI에서 다시 계산하지 않는지 검증한다.
- Application Dashboard guard를 강화해 live source/window가 `accepted_metric_buckets` + `recent_30_minutes`로 보이고, frontend가 state/order/priority를 재계산하지 않는지 확인한다.
- Snapshot guard를 강화해 history/date map/detail이 30분 slot, 14일 horizon, `current_window_end_utc`, stored `dashboard_snapshots.read_model_json` 복원 의미를 유지하는지 확인한다.
- Instance guard를 강화해 live mode, selected Application Snapshot 기반 snapshot mode, stored Instance Snapshot Trend가 서로 다른 surface로 보이는지 확인한다.
- Retention guard를 강화해 retention 밖 snapshot/detail/history/date map/instance evidence가 live/current fallback 없이 expired/source absence 또는 metric data-quality limitation으로 수렴하는지 확인한다.
- Cleanup rollout guard를 강화해 `0 15 1 * * *` / `Asia/Seoul`, 14일 horizon, 30분 evidence grace, disabled-by-default physical delete, dry-run 의미가 회귀하지 않는지 확인한다.
- Demo/smoke/guard verification 결과를 story completion notes에 남겨 D3 final planning/status consolidation의 evidence로 사용할 수 있게 한다.

## Rollback

- P10 hardening rollback 단위는 acceptance fixture, demo route, smoke data, verification script, completion-evidence note 변경이다.
- 검증 중 gap이 발견되면 guard를 완화하거나 Source of Truth를 재정의하지 않는다. 해당 gap이 13.2~13.10 구현 회귀인지, demo fixture 부족인지, 별도 follow-up이 필요한 product decision인지 분류한다.
- demo/smoke fixture만 문제가 있으면 fixture를 되돌리고 production semantics는 유지한다.
- frontend guard나 backend focused regression이 실패하면 P10에서 무리하게 Epic 13을 done으로 닫지 않고 follow-up 또는 blocker로 남긴다.
- rollback은 Source of Truth 문서, 완료 story 13.2~13.10 본문/status, `hourly_scheduled` persisted token, `dashboard_snapshots.read_model_json` source boundary, Instance Dashboard snapshot mode contract, retention cleanup cutoff 기준을 되돌리지 않는다.

## Out of Scope

- 이번 create-story 컨텍스트의 production code, tests, frontend implementation, migration/schema 수정.
- Source of Truth 문서 의미 재정의 또는 재승인.
- 완료 story 13.2~13.10 본문 수정 또는 done 상태 변경.
- 새 Epic 생성 또는 Epic 13 backlog 밖 신규 tracking key 추가.
- cleanup physical delete를 production에서 기본 enabled로 바꾸는 rollout decision.
- retention cutoff 전용 index 추가 또는 migration/schema 변경. 필요하면 query plan/row volume evidence와 함께 별도 follow-up으로 분리한다.
- raw metric explorer, endpoint timeseries UI, arbitrary metric query UI, long-term analytics, baseline/adaptive threshold, incident folding.
- `dashboard_snapshots.read_model_json`을 무시하고 current metric으로 snapshot detail을 재생성하는 구현.
- Instance Dashboard snapshot mode를 Application Snapshot stored state/evidence 검증/대체 surface로 바꾸는 구현.
- Instance Snapshot Trend를 current state, health score, root cause, recovery proof timeline으로 확장하는 구현.
- 기존 untracked `dbml-error.log` 수정, 삭제, stage.

## Acceptance Criteria

1. Given live Application Dashboard를 확인할 때, Then 화면과 response/read semantics는 `accepted_metric_buckets` source와 `recent_30_minutes` window를 표시하고, UI는 lifecycle state, endpoint/resource order, priority, data quality를 재계산하지 않는다.
2. Given Snapshot history/date map/detail을 확인할 때, Then 30분 slot, 14일 horizon, `current_window_end_utc` 기준 탐색/order, stored `dashboard_snapshots.read_model_json` 복원 의미가 유지된다.
3. Given scheduled snapshot capture reason을 확인할 때, Then persisted/API token `hourly_scheduled`는 rename하지 않고, 사용자-facing copy와 demo 설명은 30분 정기 저장 의미로 검증된다.
4. Given Instance 관련 surface를 확인할 때, Then Instance Dashboard live mode, selected Application Snapshot 기반 Instance Dashboard snapshot mode, stored Instance Snapshot Trend가 서로 다른 source/read surface로 검증된다.
5. Given Instance Dashboard snapshot mode를 확인할 때, Then selected Application Snapshot window 기준으로 `accepted_metric_buckets`를 재구성하되 Application Snapshot stored state/evidence를 대체하거나 검증하지 않는다.
6. Given Instance Dashboard snapshot mode의 selected instance metric evidence를 확인할 때, Then `accepted_at` cutoff는 적용하지 않고 late accepted metric 포함 가능성을 `readSemantics`와 UI copy에 드러낸다.
7. Given retention 밖 snapshot/detail/history/date map/instance evidence를 확인할 때, Then live/current fallback 없이 404/expired/source absence 또는 `metric_missing`/`not_observed_in_window` 계열 data-quality/observation UX로 수렴한다.
8. Given cleanup scheduler/service/read guard를 확인할 때, Then cleanup은 매일 `01:15 KST`, cron `0 15 1 * * *`, 14일 horizon, snapshot `current_window_end_utc`, metric `bucket_end_utc` + 30분 evidence grace 기준을 유지한다.
9. Given cleanup rollout control을 확인할 때, Then cleanup physical delete는 기본 disabled이며 dry-run은 cutoff 의미를 유지하되 physical delete 호출 여부만 제어한다.
10. Given P10 구현/검증을 완료할 때, Then demo/smoke/guard 검증 결과, 실행 명령, 통과/실패 여부, 남은 follow-up을 story completion notes에 남긴다.

## Tasks / Subtasks

- [x] P10 시작 전 status와 보호 대상을 확인한다. (AC: 10)
  - [x] `git status --short`로 기존 untracked `dbml-error.log`와 작업 범위를 확인한다.
  - [x] `implementation-artifacts/sprint-status.yaml`에서 Story 13.2~13.10 `done`, 13.11 실행 상태, 13-doc-3 backlog 상태를 확인한다.
  - [x] Source of Truth 문서와 완료 story 13.2~13.10은 read-only 기준으로만 읽는다.

- [x] Source of Truth acceptance checklist를 작성하고 end-to-end flow에 매핑한다. (AC: 1~10)
  - [x] Application live: `accepted_metric_buckets`, `recent_30_minutes`, server-computed state/order/priority 표시만 검증한다.
  - [x] Snapshot: 30분 slot, 14일/672 point horizon, `current_window_end_utc`, stored detail 복원, `hourly_scheduled` copy를 검증한다.
  - [x] Instance: live/snapshot/trend surface 분리, selected snapshot window reconstruction, late metric semantics, no Application Snapshot override를 검증한다.
  - [x] Retention: expired/source absence, cleanup schedule/cutoff/grace, disabled/dry-run rollout control을 검증한다.

- [x] Frontend guard와 demo smoke를 실행한다. (AC: 1~7, 10)
  - [x] `cd frontend && npm run guard:read-model-contract`를 실행하고 guard 결과를 completion notes에 남긴다.
  - [x] `cd frontend && npm run typecheck`를 실행하고 결과를 completion notes에 남긴다.
  - [x] `cd frontend && npm run build`를 실행하고 결과를 completion notes에 남긴다.
  - [x] 필요하면 local browser smoke로 Project -> Application -> Dashboard -> Snapshot -> Instance 흐름의 copy/source semantics를 확인하고, 인증/fixture 제약이 있으면 그 제약을 기록한다.

- [x] Backend focused regression과 필요 시 full regression을 실행한다. (AC: 1~9, 10)
  - [x] Story 13.3~13.10에서 도입된 dashboard/snapshot/instance/cleanup focused tests를 우선 실행한다.
  - [x] 필요하면 `./gradlew :observability-portal:test` 전체 regression을 실행한다.
  - [x] 실패가 있으면 P10 completion notes에 failing test, Source of Truth 영향, fix/follow-up 분류를 남긴다.

- [x] Static grep으로 과거 해석 회귀를 확인한다. (AC: 1~9)
  - [x] `current_15m`이 Instance Dashboard/Application Dashboard P10 user-facing contract로 재승격되지 않았는지 확인한다.
  - [x] `hourly scheduled` 사용자-facing copy가 30분 정기 저장 의미와 충돌하지 않는지 확인한다.
  - [x] `generated_at <`, `created_at <`, `accepted_at <`가 cleanup cutoff predicate로 재도입되지 않았는지 확인한다.
  - [x] `live/current fallback`, `marker.*state`, `healthScore`, `rootCause`가 금지된 surface 의미로 재도입되지 않았는지 확인한다.

- [x] P10 completion evidence를 story에 남기고 D3 handoff를 준비한다. (AC: 10)
  - [x] Dev Agent Record의 Debug Log References에 실행 명령과 결과를 남긴다.
  - [x] Completion Notes List에 Application live, Snapshot, Instance, Retention 각각의 acceptance 결과를 남긴다.
  - [x] 남은 follow-up이 있으면 P10 완료 판정을 `Done`, `Needs Follow-up`, `Blocked` 중 하나로 분류할 수 있게 근거를 남긴다.
  - [x] 13-doc-3 final planning/status consolidation에서 사용할 문서/status 정리 후보를 기록하되, P10 구현 중 Source of Truth 의미를 바꾸지 않는다.

## Dev Notes

- Active implementation baseline은 Traditional MVC + Service/Repository Layering이다. Portal source packages는 feature-first MVC 아래에 두며, Flyway SQL migration이 schema source of truth다.
- P10은 acceptance/demo hardening story다. 검증 중 좁은 fixture/demo/smoke/guard 변경이 필요할 수 있으나, Source of Truth 재정의나 완료 story 본문 수정으로 해결하지 않는다.
- Frontend guard harness는 Story 13.2에서 `npm run guard:read-model-contract`로 추가됐다. 이 guard는 Application Dashboard, snapshot history/detail, instance evidence/dashboard/trend의 source/order/readSemantics drift를 fail-closed로 검증한다.
- Application Dashboard live는 Story 13.3~13.5 결과를 따른다. Source/window는 `accepted_metric_buckets` + `recent_30_minutes`이며 baseline은 MVP primary 판단 기준이 아니다.
- Snapshot은 Story 13.6~13.7 결과를 따른다. Persisted `hourly_scheduled` token은 유지하되 user-facing cadence는 30분 정기 저장이고, marker/history/date map/trend horizon은 `current_window_end_utc`와 14일 672-slot max를 따른다.
- Instance Dashboard는 Story 13.8~13.9 결과를 따른다. Live mode와 selected Application Snapshot snapshot mode는 `InstanceDashboardReadModel` source semantics로 분리되고, Instance Snapshot Trend는 stored `dashboard_snapshots.read_model_json.instanceSummary.items[]` projection이다.
- Retention cleanup은 Story 13.10 결과를 따른다. Scheduler는 `0 15 1 * * *` / `Asia/Seoul`, retention horizon은 14일, snapshot delete predicate는 `current_window_end_utc < snapshotCutoffUtc`, metric delete predicate는 `bucket_end_utc < metricEvidenceCutoffUtc`, metric evidence grace는 30분이다.
- Cleanup physical delete rollout은 기본 disabled다. P10에서 enabled 기본값으로 바꾸거나 production rollout decision을 닫지 않는다.
- `dbml-error.log`는 기존 untracked 보호 대상이다. 수정, 삭제, stage하지 않는다.

### Suggested Verification Commands

```bash
git diff --check
git status --short

cd frontend && npm run guard:read-model-contract
cd frontend && npm run typecheck
cd frontend && npm run build

./gradlew :observability-portal:test --tests 'com.observation.portal.common.time.TimeBucketWindowCalculatorTest'
./gradlew :observability-portal:test --tests 'com.observation.portal.domain.dashboard.service.DashboardReadModelServiceTest'
./gradlew :observability-portal:test --tests 'com.observation.portal.domain.dashboard.service.TriageSummaryServiceTest'
./gradlew :observability-portal:test --tests 'com.observation.portal.domain.dashboard.service.EndpointPriorityServiceTest'
./gradlew :observability-portal:test --tests 'com.observation.portal.domain.snapshot.service.DashboardSnapshotSchedulerTest'
./gradlew :observability-portal:test --tests 'com.observation.portal.domain.snapshot.service.DashboardSnapshotMarkerServiceTest'
./gradlew :observability-portal:test --tests 'com.observation.portal.domain.snapshot.service.DashboardSnapshotDetailServiceTest'
./gradlew :observability-portal:test --tests 'com.observation.portal.domain.instance.service.InstanceDashboardReadModelServiceTest'
./gradlew :observability-portal:test --tests 'com.observation.portal.domain.instance.service.InstanceSnapshotTrendServiceTest'
./gradlew :observability-portal:test --tests 'com.observation.portal.domain.cleanup.service.RetentionCleanupServiceTest'
./gradlew :observability-portal:test --tests 'com.observation.portal.domain.cleanup.service.RetentionCleanupSchedulerTest'
./gradlew :observability-portal:test --tests 'com.observation.portal.domain.cleanup.service.RetentionCleanupPropertiesTest'
./gradlew :observability-portal:test

rg -n "current_15m|hourly scheduled|generated_at <|created_at <|accepted_at <|live/current fallback|marker.*state|healthScore|rootCause" frontend observability-portal planning-artifacts
```

## References

- `implementation-artifacts/sprint-status.yaml`
- `_bmad/custom/project-context.md`
- `planning-artifacts/epics.md`
- `planning-artifacts/dashboard-source-of-truth-realignment-roadmap.md`
- `planning-artifacts/dashboard-source-of-truth-realignment-sequence.yaml`
- `planning-artifacts/source-of-truth/dashboard-snapshot-mvp-source-of-truth.md`
- `planning-artifacts/source-of-truth/dashboard-snapshot-retention-cleanup-source-of-truth.md`
- `planning-artifacts/source-of-truth/application-dashboard-read-model-mvp-source-of-truth.md`
- `planning-artifacts/source-of-truth/instance-dashboard-read-model-mvp-source-of-truth.md`
- `planning-artifacts/contracts/read-model-contract.md`
- `planning-artifacts/stories/13-2-frontend-read-model-contract-guard.md`
- `planning-artifacts/stories/13-3-backend-recent-30-minutes-window-alignment.md`
- `planning-artifacts/stories/13-4-backend-application-dashboard-read-model-shape-alignment.md`
- `planning-artifacts/stories/13-5-frontend-application-dashboard-ia-realignment.md`
- `planning-artifacts/stories/13-6-backend-30-minute-scheduled-snapshot-and-slot-horizon-alignment.md`
- `planning-artifacts/stories/13-7-frontend-snapshot-history-detail-realignment.md`
- `planning-artifacts/stories/13-8-backend-instance-dashboard-live-snapshot-mode-split.md`
- `planning-artifacts/stories/13-9-frontend-instance-surface-split.md`
- `planning-artifacts/stories/13-10-retention-cleanup-alignment.md`
- `planning-artifacts/stories/10-7-run-acceptance-gate.md`
- `planning-artifacts/stories/6-8-demo-green-path.md`
- `planning-artifacts/stories/6-9-failure-recovery-path-demo-hardening.md`
- `planning-artifacts/stories/7-3-smoke-spring-boot-service-and-portal-communication-verification.md`

## Dev Agent Record

### Agent Model Used

GPT-5 Codex (BMAD dev-story)

### Debug Log References

- 2026-06-11T14:54:51+0900 `git status --short`: 기존 untracked `dbml-error.log`만 확인. 보호 대상 파일은 수정/삭제/stage하지 않음.
- 2026-06-11T14:54:51+0900 `implementation-artifacts/sprint-status.yaml`: 13.2~13.10 `done`, 13.11 `ready-for-dev`에서 `in-progress`로 전환, 13-doc-3 `backlog` 확인.
- 2026-06-11T14:55+0900 Source of Truth/roadmap/sequence/completed story grep: P10 기준이 `accepted_metric_buckets`, `recent_30_minutes`, 30분 slot, `current_window_end_utc`, stored `dashboard_snapshots.read_model_json`, no `accepted_at` cutoff, retention cleanup `bucket_end_utc`/30분 grace/disabled rollout과 일치함을 확인.
- 2026-06-11T15:13+0900 `git diff --check`: 통과. 출력 없음.
- 2026-06-11T15:13+0900 `git status --short`: `implementation-artifacts/sprint-status.yaml`, 이 story 파일만 modified이고 기존 untracked `dbml-error.log`만 남아 있음을 확인.
- 2026-06-11T15:13+0900 `cd frontend && npm run guard:read-model-contract`: 통과. `read-model contract guard fixtures passed`.
- 2026-06-11T15:13+0900 `cd frontend && npm run typecheck`: 통과.
- 2026-06-11T15:13+0900 `cd frontend && npm run build`: 통과. Vite production build `built in 1.08s`.
- 2026-06-11T15:14+0900 focused Gradle bundle: `./gradlew :observability-portal:test --tests 'com.observation.portal.common.time.TimeBucketWindowCalculatorTest' --tests 'com.observation.portal.domain.dashboard.service.DashboardReadModelServiceTest' --tests 'com.observation.portal.domain.dashboard.service.TriageSummaryServiceTest' --tests 'com.observation.portal.domain.dashboard.service.EndpointPriorityServiceTest' --tests 'com.observation.portal.domain.snapshot.service.DashboardSnapshotSchedulerTest' --tests 'com.observation.portal.domain.snapshot.service.DashboardSnapshotMarkerServiceTest' --tests 'com.observation.portal.domain.snapshot.service.DashboardSnapshotDetailServiceTest' --tests 'com.observation.portal.domain.instance.service.InstanceDashboardReadModelServiceTest' --tests 'com.observation.portal.domain.instance.service.InstanceSnapshotTrendServiceTest' --tests 'com.observation.portal.domain.cleanup.service.RetentionCleanupServiceTest' --tests 'com.observation.portal.domain.cleanup.service.RetentionCleanupSchedulerTest' --tests 'com.observation.portal.domain.cleanup.service.RetentionCleanupPropertiesTest'`: 통과. `BUILD SUCCESSFUL in 1s`.
- 2026-06-11T15:15+0900 `./gradlew :observability-portal:test`: 통과. Full backend regression `BUILD SUCCESSFUL in 1m 9s`.
- 2026-06-11T15:16+0900 smoke focused bundle: `./gradlew :observability-portal:test --tests 'com.observation.portal.PortalModuleSmokeTest' --tests 'com.observation.portal.domain.catalog.controller.ProjectNavigationResourceAuthorizationTest' --tests 'com.observation.portal.domain.admin.service.SmokeProjectSeedServiceTest'`: 통과. `BUILD SUCCESSFUL in 2s`.
- 2026-06-11T15:16+0900 static grep: 요청된 `rg -n "current_15m|hourly scheduled|generated_at <|created_at <|accepted_at <|live/current fallback|marker.*state|healthScore|rootCause" frontend observability-portal planning-artifacts`는 hit이 있으며, build 이후 `frontend/dist` generated bundle까지 포함하면 출력이 커진다. `-g '!dist/**' -g '!node_modules/**'` source-only 재확인에서는 문서화된 legacy/superseded 해석, guard negative fixture/assertion, excluded capability, snapshot capture cutoff 문맥으로 분류했다. Cleanup cutoff predicate는 `current_window_end_utc < snapshotCutoffUtc`, `bucket_end_utc < metricEvidenceCutoffUtc` 기준 유지 확인.

### Completion Notes List

- Application live acceptance: `DashboardReadModelServiceTest`, `DashboardControllerTest`, frontend guard fixture가 `readSemantics.source=accepted_metric_buckets`, `window.type=recent_30_minutes`, bucket boundary 표시를 검증한다. Frontend guard/typecheck/build가 통과했고 UI-side state/order/priority 재계산 회귀는 static guard와 source grep에서 발견되지 않았다.
- Snapshot acceptance: Snapshot history/detail guard와 focused tests가 30분 slot, 14일/672 horizon, `current_window_end_utc` 탐색/order, stored `dashboard_snapshots.read_model_json` detail 복원 의미를 유지한다. `hourly_scheduled` persisted token은 그대로 두고 frontend user-facing label은 `30분 정기 저장`으로 확인했다.
- Instance acceptance: live Instance Dashboard, selected Application Snapshot 기반 snapshot mode, stored Instance Snapshot Trend가 서로 다른 surface로 유지된다. Backend/frontend guard는 snapshot mode `acceptedAtCutoffApplied=false`, `includesLateAcceptedMetrics=true`, `mayDifferFromStoredApplicationSnapshot=true`, `applicationSnapshotRecalculated=false`, trend source `dashboard_snapshots.read_model_json.instanceSummary.items[]`를 검증한다.
- Retention/cleanup acceptance: retention 밖 snapshot/detail/history/date map/instance evidence는 live/current fallback 없이 404/source absence 또는 `metric_missing`/`not_observed_in_window` 계열로 수렴한다. Cleanup scheduler/service/properties는 `0 15 1 * * *` / `Asia/Seoul`, 14일 horizon, snapshot `current_window_end_utc`, metric `bucket_end_utc` + 30분 evidence grace, default `portal.retention.cleanup.enabled=false`, dry-run no physical delete 의미를 유지한다.
- Static grep 판정: `current_15m`, `hourly scheduled`, `generated_at <`, `created_at <`, `accepted_at <`, `live/current fallback`, `marker.*state`, `healthScore`, `rootCause`는 production user-facing/cleanup predicate 회귀로 발견되지 않았다. `accepted_at <= :snapshotCutoffAt` hit은 scheduled snapshot 후보 선정 경로이며 retention cleanup cutoff가 아니다. `healthScore`/`rootCause` hit은 guard negative field 또는 test absence assertion 문맥이다.
- Demo/smoke evidence: local browser smoke는 별도 authenticated live fixture/demo route가 없어 실행하지 않았다. 기존 smoke bundle 중 `ProjectNavigationResourceAuthorizationTest`는 Project -> Application -> live Dashboard -> Instance Evidence auth/navigation path와 accepted bucket/starter heartbeat axis 분리를 검증하지만, Snapshot detail, selected Application Snapshot 기반 Instance Dashboard snapshot mode, retention expired path의 full browser demo를 검증하지는 않는다. 해당 Snapshot/Instance/Retention 의미는 focused backend tests와 frontend contract guard/build로 검증했다.
- Scope guard: production code, frontend implementation, backend tests, migration/schema, Source of Truth 문서, 완료 story 13.2~13.10 본문/status, 기존 untracked `dbml-error.log`는 수정하지 않았다.
- Final 판정: Done. 남은 production code blocker는 없고 13.11은 review 승인 후 닫는다. 다만 full authenticated browser demo route/fixture가 없어 Project -> Application -> Dashboard -> Snapshot -> Instance -> retention expired path를 하나의 browser smoke로 닫은 evidence는 없으므로, 다음 handoff는 `13-doc-3-final-planning-status-consolidation`에서 이 evidence와 demo gap을 과장 없이 반영해 planning/status 문서를 최종 정리하는 것이다.

### File List

- `planning-artifacts/stories/13-11-end-to-end-acceptance-and-demo-hardening.md`
- `implementation-artifacts/sprint-status.yaml`

### Change Log

| Date | Change |
|---|---|
| 2026-06-11 | P10 end-to-end acceptance/demo hardening story artifact를 생성하고 sprint-status를 `ready-for-dev`로 정렬했다. |
| 2026-06-11 | P10 acceptance/demo/smoke/guard 검증을 완료하고 story/sprint-status를 `review`로 전환했다. |
| 2026-06-11 | BMAD review finding에 맞춰 demo/smoke evidence 범위를 정정하고 누락된 verification evidence를 보강했다. |
| 2026-06-11 | 리뷰 승인 후 13.11 story/sprint-status를 `done`으로 전환하고 full browser demo gap은 D3/follow-up handoff evidence로 남겼다. |
