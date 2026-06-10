---
artifactType: story
storyId: "13.5"
storyKey: "13-5-frontend-application-dashboard-ia-realignment"
epic: "Epic 13. Dashboard Source of Truth Realignment"
title: "Frontend Application Dashboard IA Realignment"
architectureStyle: Traditional MVC
status: done
date: 2026-06-10
phase: P4
workType: frontend
implementationScope: "Application Dashboard live surface IA, adapter/type, and frontend guard alignment"
productionCodeChangeThisContext: true
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
  - P10
rollbackBoundary: "Application Dashboard live surface component/layout and frontend canonical read model adapter alignment"
---

# Story 13.5 - Frontend Application Dashboard IA Realignment

## Status

done

2026-06-10: P4 frontend alignment story artifact를 생성하고 sprint-status를 정렬했다.
2026-06-10: BMAD dev-story implementation으로 frontend Application Dashboard IA/type/adapter/guard를 canonical `dashboard_read_model.v1` 기준에 맞춰 구현하고 review로 전환했다.
2026-06-10: Review follow-up guard hardening과 story metadata 정리를 반영한 뒤 done으로 전환했다.

## Story

frontend 구현자로서, Application Dashboard live surface를 backend canonical `dashboard_read_model.v1` shape와 운영자 질문 순서에 맞춰 재배치하고 싶다.

그래야 사용자가 첫 화면에서 판단 가능성, lifecycle state, 직접 근거, attention evidence, endpoint/resource detail, starter connection, instance entry를 순서대로 읽고, frontend가 lifecycle state, p95/p99, endpoint priority, resource pattern, snapshot/detail 의미를 다시 계산하지 않는다.

## Source of Truth

아래 문서는 이 story의 기준이다. 이 story는 해당 문서의 의미를 재정의하지 않고 frontend 구현 지침으로만 옮긴다.

1. `implementation-artifacts/sprint-status.yaml`
2. `planning-artifacts/dashboard-source-of-truth-realignment-roadmap.md`
3. `planning-artifacts/dashboard-source-of-truth-realignment-sequence.yaml`
4. `planning-artifacts/contracts/read-model-contract.md`
5. `planning-artifacts/source-of-truth/current-product-source-of-truth.md`
6. `planning-artifacts/source-of-truth/application-dashboard-read-model-mvp-source-of-truth.md`
7. `planning-artifacts/source-of-truth/dashboard-snapshot-mvp-source-of-truth.md`
8. `planning-artifacts/source-of-truth/instance-dashboard-read-model-mvp-source-of-truth.md`
9. `planning-artifacts/stories/13-2-frontend-read-model-contract-guard.md`
10. `planning-artifacts/stories/13-3-backend-recent-30-minutes-window-alignment.md`
11. `planning-artifacts/stories/13-4-backend-application-dashboard-read-model-shape-alignment.md`
12. `_bmad/custom/project-context.md`
13. `planning-artifacts/architecture.md`
14. `planning-artifacts/architecture-implementation-supplement.md`
15. `planning-artifacts/project-structure.md`

## Background

Epic 13은 완료된 Epic 4/5/6/10 story를 다시 여는 작업이 아니라, 확정된 Dashboard Source of Truth를 새 alignment story 묶음으로 적용하는 tracking epic이다. Story 13.2는 frontend read model contract guard를 완료했고, Story 13.3은 backend live window를 `recent_30_minutes` accepted bucket source로 정렬했으며, Story 13.4는 Application Dashboard live response와 snapshot 저장 payload가 공유할 canonical `dashboard_read_model.v1` top-level shape를 추가했다.

현재 frontend의 `DashboardMain`은 `current` 탭에서 상태 판단, starter 연결, recovery, scalar metric, source-scoped percentile, histogram, triage, endpoint priority를 순서대로 렌더링한다. Source of Truth의 P4 목적은 이 live surface를 운영자 질문 순서로 다시 배치하는 것이다. 상단에는 `mode=live`, `window.type=recent_30_minutes`, `readSemantics.source=accepted_metric_buckets`가 드러나야 하고, data quality/freshness, lifecycle state, state-changing direct reasons, attention evidence/first look candidates가 metric visualization보다 먼저 읽혀야 한다.

Story 13.4 완료 기준에 따라 backend `ApplicationDashboardReadModel`은 `schemaVersion`, `mode`, `window`, `thresholds`, `operatorSummary`, `dataQuality`, `signals`, `stateReasons`, `attentionEvidence`, `firstLookCandidates`, `readSemantics`를 canonical top-level field로 제공한다. 기존 `application.sourceWindow.current`, `application.sourceWindow.baseline`, `metrics`, `sourceScopedPercentiles`, `histogramDistribution`, `triageCards`, `endpointPriority`, `instances`는 frontend migration 동안 compatibility surface로 남아 있을 수 있다. P4 구현자는 canonical field를 우선 소비하고, legacy field를 조합해 더 강한 판단을 만들지 않는다.

## Aligns

- `6-4-application-dashboard-ui-integration`: Application Dashboard가 server read model을 표시하는 primary first-screen이라는 흐름을 유지하되, current surface의 독서 순서를 Source of Truth에 맞춰 재배치한다.
- `10-3-wire-types-adapters-navigation-and-dashboard`: Vite SPA의 Project -> Application -> Dashboard link chain과 server-provided dashboard link/order/value 보존 흐름을 유지한다.
- `10-4-wire-evidence-trend-and-credential-surfaces`: Instance Evidence, Snapshot/History, Snapshot Detail wiring을 되돌리지 않고 Application Dashboard live IA가 downstream snapshot/instance surface와 같은 read semantics를 공유하게 한다.
- `13-2-frontend-read-model-contract-guard`: `npm run guard:read-model-contract`가 server-computed state/order/source semantics를 계속 fail-closed로 검증하게 한다.
- `13-3-backend-recent-30-minutes-window-alignment`: live surface는 15분 current/baseline이 아니라 `recent_30_minutes` accepted bucket window를 표시한다.
- `13-4-backend-application-dashboard-read-model-shape-alignment`: frontend type/adapter/component는 canonical `dashboard_read_model.v1` top-level shape와 snapshot detail source/readSemantics 계약을 기준으로 삼는다.

## Supersedes

아래 항목은 완료 story 본문이나 이력을 바꾸지 않고, P4 이후 frontend 구현에서 좁게 대체하는 해석이다.

- Application Dashboard current tab을 상태, starter connection, metric visualization, triage 중심으로 읽는 기존 독서 순서.
- UI가 `application.sourceWindow.current/baseline`, `triageCards`, `endpointPriority`, histogram, resource hint를 조합해 dashboard 의미를 보완 계산해야 한다는 해석.
- `sourceWindow.baseline` 또는 snapshot helper baseline compatibility value를 MVP 판단 근거나 comparison UI의 근거로 승격해도 된다는 해석.
- Histogram bucket에서 p95/p99, average/max latency, latency score, regression 판단을 만들 수 있다는 해석.
- `bucketDistributionSource`가 backend/frontend guard 사이에서 drift되어도 UI copy만 맞으면 된다는 해석.
- Snapshot detail source string과 frontend guard가 서로 다른 언어를 써도 P6에서 나중에 고치면 된다는 해석.

## Hardens

- Application Dashboard 상단에서 `schemaVersion=dashboard_read_model.v1`, `mode=live`, `window.type=recent_30_minutes`, `readSemantics.source=accepted_metric_buckets` 또는 동등한 표시 의미가 드러난다.
- `operatorSummary`, `dataQuality`, `state`, `stateReasons`, `attentionEvidence`, `firstLookCandidates`를 frontend IA의 1차 입력으로 사용해, UI가 lifecycle state나 first look queue를 재계산하지 않는다.
- Starter connection과 credential lifecycle은 metric state와 섞이지 않는 보조/control-plane surface로 내려간다.
- p95/p99는 `sourceScopedPercentiles.source=starter_canonical_percentile`, `scope=instance_bucket`, `aggregatePolicy=no_average_no_max_no_merge_no_histogram_recalculation`를 만족하는 starter canonical percentile source/scope만 표시한다.
- Histogram은 `bucketDistributionSource=accepted_bucket`와 `readSemantics.histogramBucketsUsedForPercentiles=false` 의미에 맞춰 distribution/slow-share display로만 사용한다.
- Snapshot detail public source string은 `dashboard_snapshots.read_model_json`, `dashboard_snapshots.read_model_json.endpointPriority`, `dashboard_snapshots.read_model_json.instanceSummary.items`와 frontend guard의 `markerIsStateSource=false`, `snapshotDetailRecalculates=false` 의미가 drift 나지 않게 유지한다.

## Rollback

- rollback 단위는 Application Dashboard live surface component/layout, frontend canonical read model type/adapter, guard fixture 변경이다.
- P4 rollback은 backend canonical shape, backend tests, Source of Truth 문서, 완료 story 파일을 되돌리지 않는다.
- UI 재배치가 `guard:read-model-contract`를 깨면 guard 완화가 아니라 P4 adapter/component를 Source of Truth에 맞게 고치는 것이 우선이다.
- Snapshot history/detail 구현 자체는 P6 범위다. P4는 snapshot detail source/readSemantics guard가 Application Dashboard type/adapter 변경으로 drift 나지 않는지 보호하는 데 그친다.

## Out of scope

- backend code/test, Source of Truth 문서, migration/schema 구현.
- Source of Truth 문서 수정 또는 의미 재정의.
- 완료 story 파일 본문 수정 또는 done 상태 변경. 특히 13.2, 13.3, 13.4는 read-only 참고용으로만 사용한다.
- backend controller/service/model/repository, Flyway migration, schema 변경.
- P5 scheduled snapshot 30분 cadence/horizon 정렬.
- P6 snapshot history/detail frontend realignment 구현.
- P7/P8 Instance Dashboard live/snapshot mode split과 frontend instance surface split.
- P9 retention cleanup.
- 새로운 backend endpoint, Next.js API route, raw metric explorer, endpoint timeseries UI, frontend rule engine.
- lifecycle state, endpoint priority, resource pattern, p95/p99, marker/detail semantics 계산 로직을 frontend에 추가하는 작업.
- 기존 untracked `dbml-error.log` 수정, 삭제, stage.

## Acceptance Criteria

1. Given Application Dashboard live response가 로드될 때, Then frontend guard/type/adapter는 canonical `dashboard_read_model.v1` top-level field인 `schemaVersion`, `mode`, `window`, `thresholds`, `operatorSummary`, `dataQuality`, `signals`, `stateReasons`, `attentionEvidence`, `firstLookCandidates`, `readSemantics`를 이해하고, legacy skeleton field만 조합해야 화면이 의미를 알 수 있는 상태로 남기지 않는다.
2. Given live surface 상단을 렌더링할 때, Then `mode=live`, `window.type=recent_30_minutes`, `readSemantics.source=accepted_metric_buckets`, bucket end boundary 의미가 context/read semantics bar 또는 동등한 first-screen signal로 표시된다.
3. Given `application.sourceWindow.baseline == null`인 public MVP response를 받았을 때, Then frontend는 이를 정상 계약으로 처리하고 safe display copy를 사용한다. Snapshot helper baseline compatibility value나 `snapshot.baselineWindow`를 live MVP 판단 근거 또는 baseline comparison UI로 승격하지 않는다.
4. Given `operatorSummary`, `dataQuality`, `state`, `stateReasons`, `attentionEvidence`, `firstLookCandidates`가 제공될 때, Then UI는 이 server-computed value와 order를 표시한다. UI는 state-changing reason, attention-only evidence, first look candidate를 lifecycle state나 endpoint priority로 재계산하지 않는다.
5. Given starter connection과 credential lifecycle 정보가 표시될 때, Then Application metric state와 분리된 보조/control-plane surface로 렌더링되고, heartbeat 수신/미수신으로 host application down, 정상 확정, recovery complete copy를 만들지 않는다.
6. Given `sourceScopedPercentiles`가 제공될 때, Then p95/p99는 starter canonical percentile source/scope/display policy만 표시한다. 여러 instance/bucket 값을 평균, max, merge하거나 histogram bucket에서 p95/p99를 계산하지 않는다.
7. Given histogram/duration bucket evidence가 제공될 때, Then frontend는 distribution display와 500ms slow-share 설명에만 사용하고 p95/p99, average/max latency, score, regression, lifecycle rule 판단을 계산하지 않는다.
8. Given endpoint evidence와 `bucketDistributionSource=accepted_bucket`가 제공될 때, Then `frontend/src/app/lib/read-model-contract-guard.ts`, fixture, UI copy가 같은 canonical value를 기대한다. `histogram_bucket_distribution` 같은 legacy string을 새 판단 source로 허용하려면 명시적 compatibility mapping과 guard가 있어야 한다.
9. Given `endpointPriority` 또는 `firstLookCandidates` 배열을 렌더링할 때, Then frontend는 server-provided array order와 rank/source를 보존한다. `.sort()`, `.toSorted()`, `.reduce()`, confidence/score 재계산, client-side top-N 추출로 새 order를 만들지 않는다.
10. Given Snapshot Detail 관련 type/guard가 함께 변경될 때, Then public source string과 frontend guard 계약은 `dashboard_snapshots.read_model_json`, `dashboard_snapshots.read_model_json.endpointPriority`, `dashboard_snapshots.read_model_json.instanceSummary.items`, `markerIsStateSource=false`, `snapshotDetailRecalculates=false`, `liveSourcesJoined=[]`, `rawReadModelJsonExposed=false` 의미를 유지한다.
11. Given P4 implementation이 완료되면, Then 최소 verification은 `cd frontend && npm run guard:read-model-contract`, `cd frontend && npm run typecheck`, relevant frontend static grep, `git diff --check`, `git status --short`를 포함한다.
12. Given implementation diff를 검토할 때, Then Source of Truth 문서, 완료 story 파일, backend code/test, migration/schema, 기존 untracked `dbml-error.log`가 변경되지 않았음이 verification에 포함된다.

## Tasks / Subtasks

- [x] Canonical Application Dashboard frontend type gap을 정렬한다. (AC: 1, 2, 3, 8, 10)
  - [x] `frontend/src/app/lib/read-model-types.ts`의 `ApplicationDashboardReadModel`에 13.4 canonical top-level shape를 반영한다.
  - [x] `application.sourceWindow.baseline` null을 public MVP 계약으로 다루고, baseline helper compatibility value를 live 판단 근거로 사용하지 않는 type/comment/fixture를 둔다.
  - [x] `SnapshotReadSemantics`와 snapshot detail projection type이 13.4 public source string을 함께 이해하도록 guard와 타입을 맞춘다.
- [x] Dashboard adapter를 canonical read model 표시 모델로 정렬한다. (AC: 1~4, 6~8)
  - [x] `frontend/src/app/lib/read-model-adapters.ts`의 `toDashboardPresentation()`은 날짜, badge class, nullable display, histogram cumulative-to-display bucket 변환만 수행한다.
  - [x] canonical `window`, `operatorSummary`, `dataQuality`, `signals`, `stateReasons`, `attentionEvidence`, `firstLookCandidates`, `readSemantics` display field를 추가하되 state/order/source를 새로 계산하지 않는다.
  - [x] `humanizeSourceCode()`/`humanizeStatusCode()` copy가 `accepted_metric_buckets`, `dashboard_snapshots.read_model_json`, `accepted_bucket`, `starter_canonical_percentile`, `recent_30_minutes` 의미를 혼동하지 않게 한다.
- [x] Application Dashboard live IA를 운영자 질문 순서로 재배치한다. (AC: 2, 4, 5)
  - [x] `frontend/src/app/components/dashboard.tsx`의 `DashboardMain` current tab을 Context/read semantics, Data quality/freshness, Lifecycle state hero, Direct state reasons, Attention evidence/first look, Endpoint/resource evidence, Metric detail, Starter connection, Instance entry 순서로 재구성한다.
  - [x] `StarterConnectionStrip`과 `CredentialLifecyclePanel`은 metric state hero보다 아래의 control-plane/credential 보조 surface로 유지한다.
  - [x] 기존 `MetricScalars`, `SourceScopedPercentilesPanel`, `HistogramPanel`, `TriagePanel`, `EndpointPriorityPanel`, `InstancesPanel`은 필요한 경우 더 작은 display-only component로 나누되 nested card나 rule engine을 만들지 않는다.
- [x] p95/p99, histogram, endpoint/source guard를 보강한다. (AC: 6~9, 11)
  - [x] `frontend/src/app/lib/read-model-contract-guard.ts`가 canonical top-level `readSemantics`와 `bucketDistributionSource=accepted_bucket`를 검증한다.
  - [x] `frontend/src/app/lib/read-model-contract-fixtures.ts`에 canonical field sentinel data를 추가하고 server order와 natural order가 다르게 보이는 fixture를 유지한다.
  - [x] `frontend/scripts/read-model-contract-guard.ts`는 Application Dashboard component source에서 `.sort()`, `.toSorted()`, `.reduce()` 회귀와 histogram percentile 계산 후보를 계속 차단한다.
- [x] Snapshot detail source/guard drift를 막는다. (AC: 10, 12)
  - [x] `frontend/src/app/components/snapshot-detail-surface.tsx`를 직접 P6 구현으로 확장하지 않되, 타입/guard 변경이 snapshot detail stored source 계약을 깨지 않는지 확인한다.
  - [x] snapshot detail public source string과 frontend guard constants가 13.4 backend completion notes와 일치하는지 fixture로 확인한다.
- [x] Verification과 보호 대상 확인을 실행한다. (AC: 11, 12)
  - [x] `cd frontend && npm run guard:read-model-contract`
  - [x] `cd frontend && npm run typecheck`
  - [x] Static grep: `rg -n "sort\\(|toSorted\\(|reduce\\(|percentile|p95|p99|baseline|bucketDistributionSource|markerIsStateSource|snapshotDetailRecalculates|readSemantics" frontend/src/app/lib frontend/src/app/components frontend/scripts`
  - [x] `git diff --check`
  - [x] `git status --short`

## Dev Notes

- Active implementation baseline은 Traditional MVC + Service/Repository Layering이다. P4는 frontend story이며 backend controller/service/repository/model/test/migration/schema를 변경하지 않는다.
- Frontend workspace는 `frontend/` 최상위 Vite SPA다. `frontend/package.json`은 `guard:read-model-contract`, `typecheck`, `build`, `dev` script를 제공한다.
- 현재 `frontend/src/app/components/dashboard.tsx`는 `DashboardMain`, `DashboardContext`, `MetricStateStrip`, `StarterConnectionStrip`, `MetricScalars`, `SourceScopedPercentilesPanel`, `HistogramPanel`, `TriagePanel`, `EndpointPriorityPanel`, `InstancesPanel`, `SnapshotHistoryPanel`을 포함한다. P4의 주 변경 대상은 current tab live surface이며 snapshot history/detail 구현은 P6로 남긴다.
- 현재 `DashboardMain` current tab 순서는 `MetricStateStrip -> StarterConnectionStrip -> RecoveryNotice -> MetricScalars -> SourceScopedPercentilesPanel -> HistogramPanel -> TriagePanel -> EndpointPriorityPanel`이고, credential/instance surface는 오른쪽 aside에 있다. P4는 이 순서를 Source of Truth의 운영자 질문 순서로 재배치한다.
- Story 13.4 완료 기준으로 backend `ApplicationDashboardReadModel`의 canonical top-level field는 `schemaVersion`, `mode`, `window`, `thresholds`, `operatorSummary`, `dataQuality`, `signals`, `stateReasons`, `attentionEvidence`, `firstLookCandidates`, `readSemantics`다. Frontend는 이 field를 우선 소비해야 한다.
- Public `application.sourceWindow.baseline == null`은 정상 MVP 계약이다. Snapshot writer/helper column에 들어가는 baseline compatibility window는 persistence/API compatibility 값이며, frontend live judgment나 comparison UI의 근거로 사용하지 않는다.
- `readSemantics.baselineComparisonUsedForMvpDecision=false`, `helperColumnsAreStateSource=false`, `histogramBucketsUsedForPercentiles=false`, `markerIsStateSource=false`는 UI copy와 guard에서 의미를 잃지 않아야 한다.
- `bucketDistributionSource=accepted_bucket`는 13.4에서 backend/frontend guard 기준 canonical value로 닫혔다. UI copy는 이 값을 "최근 수집 데이터" 또는 동등한 display source로 표시할 수 있지만, histogram bucket percentile source로 해석하지 않는다.
- p95/p99는 starter canonical percentile source/scope/display policy가 명시된 경우에만 표시한다. Histogram bucket display conversion인 `toDisplayLatencyBuckets()`는 허용되지만 percentile, average/max latency, score, rule 판단으로 확장하지 않는다.
- Snapshot detail의 stored source는 `dashboard_snapshots.read_model_json`이다. Endpoint evidence public source는 `dashboard_snapshots.read_model_json.endpointPriority`, instance summary source는 `dashboard_snapshots.read_model_json.instanceSummary.items`다. P4 타입/guard 변경이 이 계약을 깨지 않게 한다.
- P1 guard가 실패하면 guard를 완화하기보다 P4 adapter/component가 server read model을 재계산하거나 source semantics를 오해한 지점을 고친다.

### Candidate Implementation File List

- `frontend/src/app/components/dashboard.tsx`
- `frontend/src/app/lib/read-model-types.ts`
- `frontend/src/app/lib/read-model-adapters.ts`
- `frontend/src/app/lib/read-model-contract-guard.ts`
- `frontend/src/app/lib/read-model-contract-fixtures.ts`
- `frontend/scripts/read-model-contract-guard.ts`
- `frontend/package.json`
- `frontend/tsconfig.guard.json`

### Candidate Read-only Reference File List

- `frontend/src/app/components/snapshot-detail-surface.tsx`
- `frontend/src/app/components/instance-panels.tsx`
- `frontend/src/app/lib/api.ts`
- `frontend/src/app/App.tsx`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/model/ApplicationDashboardReadModel.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotDetailReadModel.java`

### Suggested Verification Commands

```bash
cd frontend && npm run guard:read-model-contract
cd frontend && npm run typecheck
rg -n "sort\\(|toSorted\\(|reduce\\(|percentile|p95|p99|baseline|bucketDistributionSource|markerIsStateSource|snapshotDetailRecalculates|readSemantics" frontend/src/app/lib frontend/src/app/components frontend/scripts
git diff --check
git status --short
```

## References

- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/epics.md#Epic 13. Dashboard Source of Truth Realignment`
- `planning-artifacts/stories/13-doc-1-alignment-epic-story-creation.md`
- `planning-artifacts/stories/13-2-frontend-read-model-contract-guard.md`
- `planning-artifacts/stories/13-3-backend-recent-30-minutes-window-alignment.md`
- `planning-artifacts/stories/13-4-backend-application-dashboard-read-model-shape-alignment.md`
- `planning-artifacts/dashboard-source-of-truth-realignment-roadmap.md`
- `planning-artifacts/dashboard-source-of-truth-realignment-sequence.yaml`
- `planning-artifacts/contracts/read-model-contract.md`
- `planning-artifacts/source-of-truth/current-product-source-of-truth.md`
- `planning-artifacts/source-of-truth/application-dashboard-read-model-mvp-source-of-truth.md`
- `planning-artifacts/source-of-truth/dashboard-snapshot-mvp-source-of-truth.md`
- `planning-artifacts/source-of-truth/instance-dashboard-read-model-mvp-source-of-truth.md`
- `_bmad/custom/project-context.md`
- `planning-artifacts/architecture.md`
- `planning-artifacts/architecture-implementation-supplement.md`
- `planning-artifacts/project-structure.md`

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- `git status --short`, `git diff --stat`, `git diff --name-only`를 구현 전에 먼저 실행해 기존 sprint-status ready-for-dev diff, untracked story artifact, untracked `dbml-error.log` 상태를 확인했다.
- BMAD `bmad-dev-story` workflow, `_bmad/custom/project-context.md`, required story/source/architecture 문서, completed 13.2/13.3/13.4 story를 read-only 참고로 확인했다.
- `planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html`은 UI/IA reference로만 읽고, API/read model 의미는 story와 markdown/contract 문서를 우선했다.
- Baseline 및 최종 `cd frontend && npm run guard:read-model-contract`와 `cd frontend && npm run typecheck`가 통과했다.
- Static grep, `git diff --check`, `git status --short`로 frontend-only 구현 범위와 보호 대상 상태를 확인했다.

### Completion Notes List

- Frontend type/fixture/guard를 `dashboard_read_model.v1` canonical top-level shape 기준으로 확장하고, live/snapshot read semantics source drift를 fail-closed로 보호했다.
- Application Dashboard live IA를 context/read semantics, data quality/freshness, lifecycle state, direct reasons, attention/first look, endpoint/resource evidence, metric detail, starter/credential, instance entry 순서로 재배치했다.
- `sourceWindow.baseline == null`을 public MVP 계약으로 표시하고, baseline helper value를 live 판단/comparison UI 근거로 승격하지 않도록 adapter와 guard를 맞췄다.
- p95/p99는 starter canonical percentile source/scope만 표시하고, histogram bucket은 accepted bucket distribution display로만 남겼다.
- Backend code/test, migration/schema, Source of Truth 문서, 완료 story 13.2/13.3/13.4, 기존 untracked `dbml-error.log`는 수정하지 않았다.

### File List

- `frontend/src/app/components/dashboard.tsx`
- `frontend/src/app/lib/read-model-types.ts`
- `frontend/src/app/lib/read-model-adapters.ts`
- `frontend/src/app/lib/read-model-contract-guard.ts`
- `frontend/src/app/lib/read-model-contract-fixtures.ts`
- `frontend/scripts/read-model-contract-guard.ts`
- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/stories/13-5-frontend-application-dashboard-ia-realignment.md`

## Change Log

| Date | Change |
|---|---|
| 2026-06-10 | P4 frontend Application Dashboard IA realignment story artifact를 생성하고 status를 ready-for-dev로 전환했다. |
| 2026-06-10 | Frontend Application Dashboard IA, canonical read model type/adapter/guard/fixture 정렬을 구현하고 status를 review로 전환했다. |
