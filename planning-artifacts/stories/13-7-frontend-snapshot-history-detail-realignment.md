---
artifactType: story
storyId: "13.7"
storyKey: "13-7-frontend-snapshot-history-detail-realignment"
epic: "Epic 13. Dashboard Source of Truth Realignment"
title: "Frontend snapshot history/detail realignment"
architectureStyle: Traditional MVC
status: done
date: 2026-06-10
phase: P6
workType: frontend
implementationScope: "Snapshot history/date picker/detail surface, frontend adapter/type, and guard alignment"
productionCodeChangeThisContext: true
plannedProductionCodeChange: true
sourceOfTruthMode: read-only
dependsOn:
  - P0
  - P1
  - P3
  - P4
  - P5
  - 13-2-frontend-read-model-contract-guard
  - 13-4-backend-application-dashboard-read-model-shape-alignment
  - 13-5-frontend-application-dashboard-ia-realignment
  - 13-6-backend-30-minute-scheduled-snapshot-and-slot-horizon-alignment
blocks:
  - P8
  - P10
rollbackBoundary: "snapshot history/date picker/detail component, loader, adapter, and frontend guard changes"
---

# Story 13.7 - Frontend Snapshot History/Detail Realignment

## Status

done

2026-06-10: P6 frontend alignment story artifact를 생성하고 sprint-status를 정렬했다. 이번 컨텍스트에서는 production code, frontend code, backend code, migration/schema, Source of Truth 문서를 구현/수정하지 않고 story artifact와 sprint-status만 최소 변경한다.
2026-06-10: BMAD dev-story 구현으로 marker-first 30분 snapshot history/date/slot 탐색, stored snapshot dashboard detail surface, 30분 slot guard fixture, stale context reset 보강을 완료하고 review 상태로 전환했다.
2026-06-10: BMAD quick-dev review fix로 rolling horizon date map 누락, stored read model state fallback, marker slot/order guard, expired/404 safe copy guard를 보강하고 검증 통과 후 done 상태로 닫았다.

## Story

frontend 구현자로서, Snapshot history를 marker-first 30분 dashboard point 탐색 UI로 정렬하고 Snapshot detail을 저장된 dashboard read model 복원 surface로 격상하고 싶다.

그래야 사용자가 14일 retention 안에서 24h/7d/14d 또는 date map/picker로 특정 30분 snapshot point를 선택하고, 선택한 detail이 현재 dashboard/current accepted bucket으로 재계산되지 않은 `dashboard_snapshots.read_model_json` 저장본임을 일관되게 이해할 수 있다.

## Source of Truth

아래 문서는 이 story의 기준이다. 이 story는 해당 문서의 의미를 재정의하지 않고 frontend 구현 지침으로만 옮긴다.

1. `implementation-artifacts/sprint-status.yaml`
2. `planning-artifacts/epics.md`
3. `planning-artifacts/dashboard-source-of-truth-realignment-roadmap.md`
4. `planning-artifacts/dashboard-source-of-truth-realignment-sequence.yaml`
5. `planning-artifacts/contracts/read-model-contract.md`
6. `planning-artifacts/source-of-truth/application-dashboard-read-model-mvp-source-of-truth.md`
7. `planning-artifacts/source-of-truth/dashboard-snapshot-mvp-source-of-truth.md`
8. `planning-artifacts/source-of-truth/dashboard-snapshot-retention-cleanup-source-of-truth.md`
9. `planning-artifacts/source-of-truth/instance-dashboard-read-model-mvp-source-of-truth.md`
10. `planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html`
11. `planning-artifacts/stories/13-2-frontend-read-model-contract-guard.md`
12. `planning-artifacts/stories/13-3-backend-recent-30-minutes-window-alignment.md`
13. `planning-artifacts/stories/13-4-backend-application-dashboard-read-model-shape-alignment.md`
14. `planning-artifacts/stories/13-5-frontend-application-dashboard-ia-realignment.md`
15. `planning-artifacts/stories/13-6-backend-30-minute-scheduled-snapshot-and-slot-horizon-alignment.md`
16. `planning-artifacts/stories/6-7-snapshot-history-marker-ui-and-deep-link.md`
17. `planning-artifacts/stories/10-4-wire-evidence-trend-and-credential-surfaces.md`
18. `_bmad/custom/project-context.md`
19. `planning-artifacts/architecture.md`
20. `planning-artifacts/architecture-implementation-supplement.md`
21. `planning-artifacts/project-structure.md`

`planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html`은 13.7 frontend 구현의 주요 visual/interaction Source of Truth다. 단, HTML mockup을 그대로 복붙하지 않는다. 현재 React/Vite frontend 구조, 기존 shadcn/Radix UI component 사용 방식, `read-model-types.ts` / `read-model-adapters.ts` / `read-model-contract-guard.ts` 패턴에 맞춰 구현한다.

최신 사용자 지시에 따라 `planning-artifacts/source-of-truth/source-of-truth-dashboard-snapshot-picker.png`는 이번 story의 기준에서 제외한다.

## Background

Epic 13은 완료된 Epic 4/5/6/10 story를 다시 여는 작업이 아니라, 확정된 Dashboard Source of Truth를 새 alignment story 묶음으로 적용하는 tracking epic이다. Story 13.2는 frontend guard로 server-computed source/order/value를 fail-closed로 고정했고, Story 13.3/13.4는 backend live window와 canonical `dashboard_read_model.v1` shape를 정렬했으며, Story 13.5는 Application Dashboard live IA를 그 shape에 맞췄다. Story 13.6은 scheduled snapshot cadence와 marker/history/trend horizon을 30분 slot과 `current_window_end_utc` 기준으로 정렬했다.

현재 frontend는 root `frontend/` 아래의 Vite SPA다. Snapshot 관련 구현은 주로 `frontend/src/app/components/dashboard.tsx`의 `SnapshotHistoryPanel`, `SnapshotHistoryReady`, `frontend/src/app/components/snapshot-detail-surface.tsx`, `frontend/src/app/lib/read-model-types.ts`, `read-model-adapters.ts`, `read-model-contract-guard.ts`, `read-model-contract-fixtures.ts`에 있다. 기존 구현은 6.7/10.4 흐름을 받아 operational event feed와 marker list를 함께 보여주고, detail은 bounded projection 중심의 별도 surface로 표시한다.

P6의 목적은 이를 marker-first 30분 dashboard point 탐색과 stored dashboard 복원 surface로 재정렬하는 것이다. History의 1차 역할은 incident/event feed가 아니라 14일 retention 안의 30분 dashboard point 탐색이다. Snapshot detail의 source는 `dashboard_snapshots.read_model_json`이며, current dashboard, current accepted bucket, 현재 threshold, 현재 starter 상태, marker helper column으로 저장 당시 dashboard를 재계산하지 않는다.

## Aligns

- `6-7-snapshot-history-marker-ui-and-deep-link`: 기존 Snapshot/History deep link와 safe-state, link validation, no raw explorer 경계를 유지하되 history의 1차 정보 구조를 marker-first 30분 point 탐색으로 정렬한다.
- `10-3-wire-types-adapters-navigation-and-dashboard`: Vite SPA의 Project -> Application -> Dashboard link chain과 server-provided dashboard link/order/value 보존 흐름을 유지한다.
- `10-4-wire-evidence-trend-and-credential-surfaces`: Snapshot marker/detail wiring, in-memory token, `useApiResource` stale guard, adapter/type boundary를 되돌리지 않고 강화한다.
- `13-2-frontend-read-model-contract-guard`: `mode=snapshot`, `source=dashboard_snapshots.read_model_json`, `snapshotDetailRecalculates=false`, `markerIsStateSource=false` 의미가 UI/adapter/test에서 깨지면 fail-closed로 막는다.
- `13-4-backend-application-dashboard-read-model-shape-alignment`: Snapshot detail이 canonical `dashboard_read_model.v1` 저장본을 dashboard-like surface로 복원한다.
- `13-5-frontend-application-dashboard-ia-realignment`: live dashboard와 snapshot dashboard가 같은 운영자 질문 순서와 read semantics bar를 공유하되 source/mode/copy만 분기한다.
- `13-6-backend-30-minute-scheduled-snapshot-and-slot-horizon-alignment`: frontend history/date map/detail selection 기준은 `currentWindowEndUtc` / `current_window_end_utc` 30분 slot horizon이다.

## Supersedes

아래 항목은 완료 story 본문이나 이력을 바꾸지 않고, P6 이후 frontend 구현에서 좁게 대체하는 해석이다.

- Snapshot history에서 marker와 operational event를 동등한 history surface로 놓고 client가 표시 편의에 맞게 재정렬해도 된다는 해석.
- `24h/7d/14d` marker query limit을 1시간 scheduled cadence의 `24/168/336` 의미로 계속 읽는 해석. P6 frontend는 30분 scheduled point 기준으로 `24h=48`, `7d=336`, `14d=672` 또는 backend가 명시한 동등한 30분 slot horizon을 따라야 한다.
- Snapshot detail을 live dashboard와 다른 compact evidence projection으로만 두고, missing field를 current dashboard/current accepted bucket으로 보완해도 된다는 해석.
- `markerBucket`, marker severity, capture reason, transition tag를 lifecycle state/evidence source처럼 읽는 해석.
- `hourly_scheduled` persisted/API token을 사용자-facing copy에서 그대로 hourly cadence로 노출해도 된다는 해석.
- Retention 밖 또는 404 snapshot detail을 live/current dashboard fallback처럼 복원해도 된다는 해석.

## Hardens

- Snapshot history는 marker-first 30분 dashboard point 탐색 UI로 읽힌다.
- Snapshot detail은 stored Application Snapshot Dashboard, 즉 `dashboard_snapshots.read_model_json` 복원 surface로 읽힌다.
- `mode=snapshot`, `source=dashboard_snapshots.read_model_json`, `snapshotDetailRecalculates=false`, `markerIsStateSource=false` 의미가 화면 copy, adapter display model, guard fixture에 모두 남는다.
- Marker bucket은 timeline 색상/필터/빠른 탐색용 색인으로만 표시되고 state/evidence source가 되지 않는다.
- P5가 정렬한 `currentWindowEndUtc` slot horizon을 history/date map/detail navigation의 기준 timestamp로 사용한다.
- `hourly_scheduled` token은 backend persisted/API legacy token으로 유지하고, 사용자-facing label은 "30분 정기 저장" 또는 동등한 scheduled 30분 의미로 표시한다.
- selected Project/Application context, history preset, snapshot target, auth generation, reload, token clear, unmount가 바뀌면 stale history/detail response가 최신 화면을 덮지 않는다.

## Rollback

- rollback 단위는 snapshot history/date picker/detail component, loader, adapter/type, guard fixture 변경이다.
- UI surface 변경과 read model guard 변경은 가능하면 commit/subtask를 분리해 rollback할 수 있게 한다.
- P6 rollback은 backend P5 scheduler/repository horizon 정렬, Source of Truth 문서, 완료 story 13.2/13.3/13.4/13.5/13.6 본문, migration/schema를 되돌리지 않는다.
- P6 guard가 기존 UI를 막으면 guard 완화보다 snapshot history/detail UI와 adapter를 Source of Truth에 맞게 고치는 것이 우선이다.

## Out of Scope

- backend scheduler/repository/service 변경.
- instance dashboard live/snapshot mode split.
- frontend instance surface split.
- cleanup physical delete 또는 retention cleanup 구현.
- migration/schema 변경.
- raw snapshot explorer.
- endpoint timeseries table 또는 arbitrary metric query UI.
- snapshot detail에서 state/evidence를 client가 재계산하는 구현.
- 새로운 backend endpoint, Next.js API route, browser token persistence, URL token parsing.
- `hourly_scheduled` persisted token rename.
- Source of Truth 문서 수정 또는 의미 재정의.
- 완료 story 13.2/13.3/13.4/13.5/13.6 본문 수정 또는 done 상태 변경.
- 기존 untracked `dbml-error.log` 수정, 삭제, stage.

## Acceptance Criteria

1. Given 13.7 frontend implementation을 시작할 때, Then `source-of-truth-dashboard-mockup.html`을 주요 visual/interaction 기준으로 읽고, React/Vite component와 기존 adapter/API 패턴에 맞춰 구현한다. HTML을 그대로 복사하거나 별도 prototype runtime으로 붙이지 않는다.
2. Given Snapshot history surface가 표시될 때, Then history의 primary surface는 marker-first 30분 dashboard point 탐색이다. Operational event feed를 유지하더라도 secondary/collapsible context로만 남기고 marker/date/slot 선택보다 우선하지 않는다.
3. Given 24h/7d/14d 탐색을 선택할 때, Then marker/date map/picker는 14일 retention 안에서만 동작하고 30분 scheduled point horizon을 따른다. 24h는 48개, 7d는 336개, 14d는 672개 30분 slot 또는 backend가 명시한 동등한 30분 slot limit/maxLimit을 기준으로 한다.
4. Given marker/history response가 로드될 때, Then frontend는 `currentWindowEndUtc`를 30분 slot 선택과 grouping의 기준 timestamp로 사용한다. `capturedAt`/`generatedAt`은 provenance와 tie-breaker 표시로만 사용하고 retention/date map inclusion 기준으로 승격하지 않는다.
5. Given marker items를 렌더링할 때, Then server-provided order와 `currentWindowEndUtc` slot meaning을 보존한다. UI는 `.sort()`, `.toSorted()`, `.reduce()` 기반 severity/state/captureReason 재정렬이나 client-side top-N 추출로 새 의미 order를 만들지 않는다.
6. Given marker row 또는 date cell이 표시될 때, Then `markerBucket`은 timeline 색상/필터/탐색 색인으로만 표현된다. 실제 lifecycle state label은 stored `stateCode` 또는 detail의 stored read model에서 온 값으로만 표시한다.
7. Given `markerBucket=normal`이지만 stored read model에 500번대 attention evidence가 있는 snapshot을 선택할 때, Then history marker는 정상 범위 색인일 수 있고 detail은 저장된 attention evidence를 복원한다. UI는 marker bucket만 보고 evidence absence 또는 현재 정상 확정을 말하지 않는다.
8. Given `captureReason=hourly_scheduled` snapshot을 표시할 때, Then persisted/API token은 변경하지 않고 사용자-facing label은 "30분 정기 저장", "30분 scheduled" 또는 동등한 30분 scheduled 의미로 표시한다.
9. Given snapshot detail target을 선택할 때, Then detail fetch는 현재 selected Project/Application id와 internal snapshot detail path가 일치할 때만 수행한다. DOM/user input/arbitrary URL에서 온 snapshot path를 그대로 fetch하지 않는다.
10. Given Snapshot detail response가 성공할 때, Then detail surface는 stored Application Snapshot Dashboard로 렌더링된다. 화면 context/read semantics bar에는 `mode=snapshot`, `source=dashboard_snapshots.read_model_json`, `capturedAt`, `currentWindowEndUtc`/window, `snapshotDetailRecalculates=false`, `markerIsStateSource=false` 또는 동등한 first-screen signal이 드러난다.
11. Given backend wrapper `readSemantics.mode=stored_snapshot_detail`와 stored `readModel.mode=snapshot`이 함께 내려올 때, Then frontend는 wrapper mode를 API compatibility metadata로 검증하고, operator-facing dashboard surface는 stored read model의 `mode=snapshot` 의미로 표시한다.
12. Given Snapshot detail을 렌더링할 때, Then state, operatorSummary, dataQuality, stateReasons, attentionEvidence, firstLookCandidates, endpoint/resource/instance summary는 `dashboard_snapshots.read_model_json` 저장본에서 온 bounded field만 사용한다. current dashboard, current accepted bucket, current threshold, current route metadata, heartbeat를 새로 fetch하거나 조인해 보완하지 않는다.
13. Given Snapshot detail read semantics가 `source=dashboard_snapshots.read_model_json`, `snapshotDetailRecalculates=false`, `currentStateRecalculated=false`, `liveSourcesJoined=[]`, `rawReadModelJsonExposed=false`, `markerIsStateSource=false`, `baselineComparisonUsedForMvpDecision=false` 중 하나라도 깨질 때, Then adapter/guard는 safe error로 수렴하고 화면은 stored dashboard처럼 렌더링하지 않는다.
14. Given Snapshot detail `404` 또는 retention-expired 응답을 받을 때, Then UI는 "보관 기간이 지났거나 저장된 snapshot을 찾을 수 없음" 계열 copy를 표시하고 live dashboard/current accepted bucket fallback을 만들지 않는다.
15. Given history/date map이 비어 있거나 marker가 없을 때, Then UI는 retention/source absence 또는 저장된 point 없음으로 표현한다. "현재 문제 없음", "정상 확정", "복구 완료" copy를 만들지 않는다.
16. Given selected Project/Application context, history preset, selected snapshot target, auth generation, reload, token clear, unmount가 바뀔 때, Then pending history/detail response는 `AbortSignal`, request sequence, `resourceKey`, selected context validation으로 폐기되고 최신 화면을 덮지 않는다.
17. Given Project/Application을 재선택하면, Then selected snapshot, date map selected day, detail target, stale history/detail data는 fail-closed로 초기화된다. 이전 application의 snapshot detail이 새 application 화면에 남지 않는다.
18. Given frontend guard fixture를 갱신할 때, Then 30분 slot sentinel data를 포함한다: server order와 natural time/order가 일부러 다르게 보이는 marker, `active + attention evidence`, `hourly_scheduled`, expired/404-safe copy, `markerBucket != storedApplicationStateCode` 케이스를 검증한다.
19. Given implementation diff를 검토할 때, Then Source of Truth 문서, 완료 story 13.2/13.3/13.4/13.5/13.6, backend code/test, migration/schema, 기존 untracked `dbml-error.log`가 변경되지 않았음이 확인된다.
20. Given 새 공개 type/helper/component/test 또는 동작을 바로 이해하기 어려운 내부 helper를 추가할 때, Then AGENTS.md 기준에 따라 한국어 JSDoc/comment와 한국어 테스트 display name/copy를 사용한다.

## Tasks / Subtasks

- [x] 현재 snapshot history/detail frontend boundary를 조사한다. (AC: 1~6, 9~13, 16~17)
  - [x] `frontend/src/app/components/dashboard.tsx`의 `SnapshotHistoryPanel`/`SnapshotHistoryReady`가 operational event feed와 marker list를 어떻게 배치하는지 확인한다.
  - [x] `frontend/src/app/components/snapshot-detail-surface.tsx`가 bounded projection만 렌더링하는 현재 상태와 stored dashboard 복원 surface로 격상해야 할 부분을 분리한다.
  - [x] `frontend/src/app/lib/use-api-resource.ts`의 stale response guard를 P6 history/detail picker에도 그대로 재사용할 수 있는지 확인한다.

- [x] Marker-first 30분 history/date map/picker를 구현한다. (AC: 1~8, 15~18)
  - [x] 24h/7d/14d preset 또는 HTML mockup의 date map/picker 상호작용을 React component로 구현한다.
  - [x] 14일 date map은 날짜별 markerBucket summary를 보여주고, 하루 drill-down은 48개 30분 slot 또는 backend-provided 30분 marker point를 기준으로 한다.
  - [x] `currentWindowEndUtc`를 slot identity로 사용하고 `capturedAt`/`generatedAt`은 provenance/tie-breaker 표시로만 둔다.
  - [x] `hourly_scheduled` display label은 30분 scheduled 의미로 mapping한다.
  - [x] operational event feed를 유지한다면 marker-first 탐색 아래 secondary/collapsible 영역으로 내려놓고 event promotion/reordering을 만들지 않는다.

- [x] Snapshot detail을 stored dashboard read model 복원 surface로 격상한다. (AC: 9~14)
  - [x] selected snapshot detail response의 `readModel`을 `dashboard_read_model.v1` snapshot-mode dashboard surface로 렌더링한다.
  - [x] live dashboard와 공통으로 쓸 수 있는 read surface가 있다면 추출하되, data loader/source/copy는 `mode=live`와 `mode=snapshot`으로 명확히 분기한다.
  - [x] detail header/context bar에 `mode=snapshot`, `source=dashboard_snapshots.read_model_json`, window/currentWindowEnd, capturedAt, captureReason, recomputation flags를 노출한다.
  - [x] `marker`, `previousState`, `lastHealthyAt`, `snapshotEndpointEvidence`, `instanceSummary`는 stored read model을 보조 설명하는 bounded section으로만 둔다.
  - [x] 404/expired/malformed detail은 live dashboard fallback 없이 safe copy로 수렴한다.

- [x] Type/adapter/guard fixture를 P6 semantics로 갱신한다. (AC: 3~8, 10~13, 18)
  - [x] `HistoryPreset` query와 marker horizon guard가 30분 slot 기준 limit/maxLimit을 기대하도록 정렬한다.
  - [x] `DashboardSnapshotMarkerItem` 또는 display model에 `markerBucket`/`currentWindowEndUtc`/stored state 의미가 충분히 드러나는지 확인하고 필요한 타입만 최소 확장한다.
  - [x] `read-model-contract-fixtures.ts`에 marker-first sentinel data, 14d 672-slot horizon, `hourly_scheduled -> 30분 정기 저장`, markerBucket/state divergence fixture를 추가한다.
  - [x] `read-model-contract-guard.ts`가 snapshot detail source/recalculation/marker state source 의미와 stored `readModel.mode=snapshot`을 fail-closed로 검증하게 한다.

- [x] Stale response와 context reset guard를 보강한다. (AC: 9, 16~17)
  - [x] history preset, selected date/day/slot, detail target을 Project/Application 변경 시 초기화한다.
  - [x] history/detail fetch context에 selected Project/Application/snapshot id/auth generation/resourceKey를 포함한다.
  - [x] overlapping request, reload, token clear, unmount 뒤 도착한 response가 state를 덮지 않는지 guard fixture 또는 component-level check로 확인한다.

- [x] Verification과 scope guard를 수행한다. (AC: 19~20)
  - [x] `npm run guard:read-model-contract`, `npm run typecheck`, `npm run build`를 통과시킨다.
  - [x] static grep으로 raw snapshot explorer, endpoint timeseries, UI-side state/evidence recalculation, generatedAt horizon 재승격, `hourly_scheduled` hourly copy가 없는지 확인한다.
  - [x] `git diff --check`와 `git status --short`로 Source of Truth 문서, 완료 story, backend/migration/schema, `dbml-error.log`가 변경되지 않았는지 확인한다.

## Dev Notes

### Current Code State

- Sprint status 기준 13.7은 backlog였고, 이 create-story 작업으로 ready-for-dev가 된다.
- 현재 작업트리에는 `planning-artifacts/stories/13-6-backend-30-minute-scheduled-snapshot-and-slot-horizon-alignment.md` 수정분과 untracked `dbml-error.log`가 이미 있다. P6 구현자는 두 파일을 되돌리거나 삭제하거나 stage하지 않는다.
- Frontend source root는 `frontend/`다. `observability-portal/src/main/frontend`는 현재 존재하지 않는다.
- `frontend/package.json`은 React 18.3.1, Vite 6.3.5, TypeScript 5.8.3, Radix/shadcn-style UI, lucide-react를 사용한다. 새 dependency는 기본적으로 추가하지 않는다.
- `useApiResource`는 `authFetch`, `authGeneration`, `AbortController`, request sequence, unmount guard, `resourceKey`를 이미 제공한다. P6 stale guard는 이 hook 또는 동등 패턴을 재사용한다.
- `SnapshotHistoryPanel`은 현재 `buildSnapshotHistoryPaths()`로 operational events와 snapshot markers를 함께 fetch한다.
- `SnapshotDetailSurface`는 current dashboard fallback 없이 stored snapshot detail API를 읽고 bounded projection을 렌더링한다. P6는 이 안전 경계를 유지하되 surface를 dashboard-like snapshot mode로 격상한다.
- `read-model-contract-guard.ts`는 이미 snapshot detail의 `dashboard_snapshots.read_model_json`, `snapshotDetailRecalculates=false`, `markerIsStateSource=false`, `liveSourcesJoined=[]`를 검증한다. P6는 30분 slot horizon과 marker-first semantics를 더한다.

### Source Semantics

- Live dashboard source는 `accepted_metric_buckets`다.
- Snapshot detail source는 `dashboard_snapshots.read_model_json`이다.
- Snapshot marker/history source는 `dashboard_snapshots` helper/index row이며 state/evidence source가 아니다.
- `markerBucket`은 timeline 탐색 색인이다. `storedApplicationStateCode` 또는 stored read model의 `state.code`만 저장 당시 state 의미를 가진다.
- `currentWindowEndUtc`는 P6 history/date map/detail navigation의 기준 slot timestamp다.
- `generatedAt`/`capturedAt`은 provenance와 deterministic ordering/tie-breaker 표시다.
- `hourly_scheduled`는 backend persisted/API legacy token이다. 사용자-facing copy는 30분 scheduled cadence를 말할 수 있다.

### UX / Interaction Notes From HTML Mockup

- HTML mockup은 Application Dashboard 안의 Snapshot/History section에서 marker copy를 먼저 보여주고, "Snapshot 선택" action으로 date map/picker를 연다.
- History는 14일 retention summary, 30분 scheduled points, default 24h, cleanup/expiry hint를 보여준다.
- Date map은 날짜별 가장 높은 markerBucket을 요약하고, 하루 drill-down은 48개 30분 slot grid로 이동한다.
- Snapshot mode에서는 같은 dashboard surface가 `mode=snapshot`으로 바뀌며, `source=dashboard_snapshots.read_model_json`, recomputation false, marker state source false 의미를 상단에 고정한다.
- 이 UX는 구현 방향이다. 실제 구현은 현재 React component 구조와 existing API/adapter/guard pattern에 맞춰 component를 분리하거나 재사용한다.

### API / Adapter Notes

- Existing history endpoints:
  - `GET /api/projects/{projectId}/applications/{applicationId}/operational-events?since={24h|7d|14d}&limit={...}`
  - `GET /api/projects/{projectId}/applications/{applicationId}/dashboard/snapshot-markers?since={24h|7d|14d}&limit={...}`
- Existing detail endpoint:
  - `GET /api/projects/{projectId}/applications/{applicationId}/dashboard/snapshots/{snapshotId}`
- P6 frontend should inspect the backend response after Story 13.6. If marker `maxLimit`/`limit` now reflects 30분 cadence, frontend constants and guard fixtures must follow it. Legacy 14d marker limit `336` was hourly-cadence-shaped and should not remain as the 14d 30분 point ceiling.
- Link validation must stay internal-path only. `links.snapshot` is usable only after it matches selected Project/Application/Snapshot context.

### Project Structure / Comment Notes

- Active baseline is Traditional MVC + Service/Repository Layering, but this story is frontend-only.
- Do not create `application`, `port`, `adapter`, `adapter.in`, `adapter.out` packages.
- Do not add backend controller/service/repository, Flyway migration, schema, cleanup scheduler, or instance dashboard mode split code.
- New public type/helper/component/test and non-obvious internal helper should have concise Korean comments/JSDoc per AGENTS.md.
- UI copy, docs/comments, and test display names should use Korean. Keep external API names, class names, and standard terms in English only where clearer.

## Candidate Implementation File List

- `frontend/src/app/components/dashboard.tsx`
  - Candidate update: `SnapshotHistoryPanel`/`SnapshotHistoryReady`를 marker-first date map/picker 중심으로 재배치하거나 새 component로 추출한다.
- `frontend/src/app/components/snapshot-detail-surface.tsx`
  - Candidate update: stored snapshot detail을 dashboard-like `mode=snapshot` surface로 격상하고 expired/404 fallback copy를 유지한다.
- `frontend/src/app/components/snapshot-history-panel.tsx`
  - Candidate new file if extraction keeps `dashboard.tsx` readable.
- `frontend/src/app/components/snapshot-date-picker.tsx`
  - Candidate new file for 14일 date map and 30분 slot picker if component size justifies it.
- `frontend/src/app/lib/read-model-types.ts`
  - Candidate update: marker bucket/currentWindowEndUtc/date map display type 또는 stored snapshot dashboard shape를 필요한 만큼만 보강한다.
- `frontend/src/app/lib/read-model-adapters.ts`
  - Candidate update: 30분 marker preset query, capture reason display mapping, marker/date display model, snapshot path validation/display formatting.
- `frontend/src/app/lib/read-model-contract-guard.ts`
  - Candidate update: 30분 slot horizon, marker-first semantics, stored `readModel.mode=snapshot`, source/recalculation flags guard.
- `frontend/src/app/lib/read-model-contract-fixtures.ts`
  - Candidate update: P6 sentinel fixtures and negative cases for guard coverage.
- `frontend/src/styles/index.css` or colocated Tailwind class usage
  - Candidate update only if date map/slot picker needs stable responsive sizing not expressible in existing components.

## Candidate Test File List

- `frontend/src/app/lib/read-model-contract-guard.ts`
  - Existing guard script target. Add assertions that fail on 1시간-shaped marker limit, marker-as-state, detail recalculation, wrong source, or wrong stored mode.
- `frontend/src/app/lib/read-model-contract-fixtures.ts`
  - Add fixture cases for `24h=48`, `7d=336`, `14d=672`, `hourly_scheduled`, marker/state divergence, expired/404-safe copy expectations.
- `frontend/src/app/components/dashboard.tsx`
  - If no component test harness exists, keep rendering logic simple and cover state/adapter semantics through guard fixtures plus manual/browser verification.
- Optional new frontend test harness only if the project already supports it without broad dependency churn.

## Suggested Verification Commands

```bash
cd frontend && npm run guard:read-model-contract
cd frontend && npm run typecheck
cd frontend && npm run build
rg -n "current dashboard fallback|raw snapshot|endpoint timeseries|healthScore|rootCause|hourly scheduled|hourly snapshot" frontend/src
rg -n "markerLimit: 336|maxLimit: 336|capturedAt_asc|generatedAt" frontend/src/app/lib frontend/src/app/components
git diff --check
git status --short
```

If implementation changes rendered UI, also run the local Vite app and verify the Snapshot/History section and Snapshot detail in browser at desktop and mobile widths. The browser check must confirm no text overlap, date map/slot grid stability, correct mode/source copy, and expired/detail error safe states.

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- 필수 사전 확인: `git status --short`, `implementation-artifacts/sprint-status.yaml`, Story 13.7, HTML mockup, `dashboard.tsx`, `snapshot-detail-surface.tsx`, read-model type/adapter/guard/fixture, `use-api-resource.ts` 확인.
- Red phase: `npm run guard:read-model-contract`가 30분 marker horizon 기대값 변경 후 `snapshot_history_context_mismatch`로 실패함을 확인.
- Guard green: marker `24h=48`, `7d=336`, `14d=672`, `maxLimit=672`, `currentWindowEndUtc_asc`, `stored_read_model_point` safe meaning, stored snapshot detail window/source/recalculation guard 통과.
- Verification: `cd frontend && npm run guard:read-model-contract` 통과.
- Verification: `cd frontend && npm run typecheck` 통과.
- Verification: `cd frontend && npm run build` 통과.
- Browser smoke: Vite dev server `http://127.0.0.1:5173/`에서 Playwright로 첫 화면 HTTP 200, console error/warning 없음 확인. 로그인 전 화면이라 실제 authenticated snapshot data state는 backend/auth 없이 직접 확인하지 못함.
- Scope guard: static grep에서 raw snapshot explorer, endpoint timeseries, hourly user-facing copy, generatedAt horizon 재승격 없음 확인. `healthScore`/`rootCause`는 guard negative field 목록으로만 남아 있음.
- Scope guard: `git diff --check` 통과, `git status --short` 확인.

### Completion Notes List

- `SnapshotHistoryPanel`을 `frontend/src/app/components/snapshot-history-panel.tsx`로 추출하고 marker-first 30분 snapshot 탐색 surface를 구현했다. Preset은 24h/7d/14d, marker limit은 48/336/672 30분 point 기준이며, date map과 48-slot grid는 `currentWindowEndUtc`를 기준으로 동작한다.
- Operational event feed는 marker/date/slot 탐색 아래의 secondary `<details>` context로 이동했고, marker list는 서버 순서를 재정렬하지 않는다. 새 history component도 guard script의 `.sort()`/`.toSorted()`/`.reduce()` 금지 대상에 포함했다.
- Snapshot detail은 stored Application Snapshot Dashboard surface로 격상했다. `mode=snapshot`, `source=dashboard_snapshots.read_model_json`, `capturedAt`, `currentWindowEndUtc`/window, `snapshotDetailRecalculates=false`, `currentStateRecalculated=false`, `markerIsStateSource=false`를 상단에 노출한다.
- Detail rendering은 stored `readModel`의 state/operatorSummary/dataQuality/stateReasons/attentionEvidence/firstLookCandidates와 bounded endpoint/instance summary만 사용한다. current dashboard/current accepted bucket/live source join fallback은 만들지 않았다.
- `hourly_scheduled` persisted/API token은 유지하고 사용자-facing display label만 "30분 정기 저장"으로 표시한다.
- Stale response guard는 `useApiResource`의 `AbortSignal`, request sequence, `authGeneration`, `resourceKey`를 유지하고, Project/Application/preset 변경 시 selected day/slot/detail target을 fail-closed로 초기화한다.
- Source of Truth 문서, backend code/test, scheduler/repository/service, migration/schema, 완료 story 13.2/13.3/13.4/13.5/13.6 본문은 이번 구현에서 수정하지 않았다. 단, 작업 시작 전부터 수정 상태였던 보호 대상 13.6 story diff는 그대로 남겨 두었다.
- 기존 untracked `dbml-error.log`와 잘못된 `planning-artifacts/source-of-truth/source-of-truth-dashboard-snapshot-picker.png`는 수정/삭제/stage하지 않았다.
- Review fix: rolling 14일 horizon이 15개 UTC date에 걸치는 경우도 date map에서 누락하지 않도록 하고, stored read model state가 없으면 helper metadata로 보완하지 않고 guard에서 fail-closed 처리한다.
- Review fix: marker `currentWindowEndUtc`가 30분 boundary와 horizon 안에 있는지, `currentWindowEndUtc_asc` 실제 순서를 지키는지 guard fixture로 검증한다. 404/expired detail safe copy도 live fallback 금지 문구로 고정했다.

### File List

- `frontend/scripts/read-model-contract-guard.ts`
- `frontend/src/app/components/dashboard.tsx`
- `frontend/src/app/components/snapshot-detail-surface.tsx`
- `frontend/src/app/components/snapshot-history-panel.tsx`
- `frontend/src/app/lib/read-model-adapters.ts`
- `frontend/src/app/lib/read-model-contract-fixtures.ts`
- `frontend/src/app/lib/read-model-contract-guard.ts`
- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/stories/13-7-frontend-snapshot-history-detail-realignment.md`
- 보호 대상 기존 변경으로 작업 전부터 modified 상태이며 이번 구현에서 의도적으로 건드리지 않음: `planning-artifacts/stories/13-6-backend-30-minute-scheduled-snapshot-and-slot-horizon-alignment.md`

### Change Log

- 2026-06-10: Marker-first 30분 snapshot history/date/slot 탐색 UI, stored snapshot dashboard detail surface, 30분 slot adapter/guard fixture, stale context reset, verification/scope guard를 완료하고 story/sprint 상태를 review로 전환했다.
- 2026-06-10: BMAD code review 후속 patch를 적용하고 `npm run guard:read-model-contract`, `npm run typecheck`, `npm run build`, `git diff --check`를 통과해 story/sprint 상태를 done으로 전환했다.
