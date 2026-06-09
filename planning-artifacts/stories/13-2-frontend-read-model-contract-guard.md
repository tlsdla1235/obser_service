---
artifactType: story
storyId: "13.2"
storyKey: "13-2-frontend-read-model-contract-guard"
epic: "Epic 13. Dashboard Source of Truth Realignment"
title: "Frontend Read Model Contract Guard"
architectureStyle: Traditional MVC
status: done
date: 2026-06-09
phase: P1
workType: frontend
implementationScope: "frontend adapter/type/fixture guard"
productionCodeChangeThisContext: true
sourceOfTruthMode: read-only
dependsOn:
  - D1
  - P0
blocks:
  - P4
  - P6
  - P8
  - P10
rollbackBoundary: "frontend adapter/type/fixture guard commit"
---

# Story 13.2 - Frontend Read Model Contract Guard

## Status

done

2026-06-09: P1 story artifact를 생성했다. 이번 컨텍스트에서는 production code, frontend code, backend code, test code를 구현하지 않고, story artifact와 sprint/status 정렬만 수행한다.
2026-06-09: P1 frontend adapter/type/fixture guard 구현을 완료했고 review 상태로 전환했다.
2026-06-10: BMAD code review findings를 quick-dev로 반영하고 guard/typecheck/build/static grep을 재통과해 done으로 전환했다.

## Story

프론트엔드 구현자로서, server read model을 소비하는 adapter/type/fixture guard를 먼저 잠그고 싶다.

그래야 P2/P3 backend shape 변경과 P4/P6/P8 UI realignment 전에 UI가 server-computed state, order, source semantics를 재계산하거나 재정렬하지 않는 fail-closed 안전망을 갖는다.

## Background

Epic 13은 완료된 Epic 4/5/6/10 story를 다시 여는 작업이 아니라, 확정된 Dashboard Source of Truth를 새 alignment story 묶음으로 적용하는 tracking epic이다. D1과 P0는 완료됐고, `13-2-frontend-read-model-contract-guard`는 P1로서 backend shape 변경과 UI 재배치보다 먼저 놓인다.

현재 frontend는 Story 10.3/10.4에서 Vite SPA 기반 Project -> Application -> Dashboard -> Instance Evidence -> Instance Trend/History 흐름을 기존 backend API에 연결했다. 이 연결은 유지하되, P1은 adapter/type/fixture 경계에서 UI가 server read model의 lifecycle state, endpoint priority, marker/trend order, instance evidence/status를 새로 판단하지 못하게 고정한다.

이 story는 Source of Truth 문서를 재정의하지 않는다. Source of Truth 문서는 read-only 기준이고, 완료 story 본문과 done 상태도 수정하지 않는다.

## Aligns

- `6-4-application-dashboard-ui-integration`: Application Dashboard가 server-computed state, starter connection, zeroInsight, triage, endpoint priority, instance handoff를 표시만 한다는 UI boundary를 유지한다.
- `6-5-instance-evidence-ui`: Instance Detail은 Application Dashboard 판단을 대체하지 않는 evidence drill-down이라는 경계를 유지한다.
- `6-6-instance-snapshot-trend-ui`: Instance trend는 stored snapshot/read model projection이며 instance health score나 current state 재판정이 아니라는 경계를 유지한다.
- `6-7-snapshot-history-marker-ui-and-deep-link`: Snapshot/history marker와 detail은 저장된 dashboard read model을 탐색/복원하는 surface이며 marker type/severity/state를 UI에서 다시 만들지 않는다는 경계를 유지한다.
- `10-3-wire-types-adapters-navigation-and-dashboard`: `frontend/src/app/lib/read-model-types.ts`와 `read-model-adapters.ts`가 Java record/API surface를 우선하고 server-provided dashboard link/order/value를 보존한다는 흐름을 유지한다.
- `10-4-wire-evidence-trend-and-credential-surfaces`: Evidence, Trend, Snapshot/History, Snapshot Detail wiring은 기존 endpoint와 server-provided link/read model을 source로 삼는다는 흐름을 유지한다.
- `10-7-run-acceptance-gate`: Epic 10 acceptance의 no UI-side lifecycle/p95/p99/endpoint priority/snapshot-history 계산 guard를 더 좁고 반복 가능한 fixture guard로 강화한다.

## Supersedes

아래 항목은 완료 story의 본문이나 이력을 바꾸지 않고, P1 이후 frontend 구현에서 허용하지 않는 해석을 좁게 대체한다.

- UI adapter가 `state.code`, accepted bucket freshness, heartbeat, resource hint, triage field를 조합해 lifecycle state 또는 recovery/zeroInsight를 새로 판단해도 된다는 해석.
- `endpointPriority`를 보기 좋게 만들기 위해 `.sort()`, `.reduce()`, index 기반 rank, confidence/score 재계산으로 재정렬하거나 축약해도 된다는 해석.
- Snapshot marker bucket, marker severity, capture reason, transition tag를 lifecycle state나 snapshot detail evidence source처럼 읽어도 된다는 해석.
- Instance trend point를 client에서 임의 시간순/역시간순으로 다시 정렬하거나, point field를 조합해 instance state/health score를 만들어도 된다는 해석.
- Instance evidence의 `observationStatus`, endpoint presence, resource evidence, application contribution을 UI가 새 status/order/root cause로 승격해도 된다는 해석.

## Hardens

- P2/P3 backend shape 변경 전에 frontend가 server-computed field를 그대로 소비하는지 먼저 fail-closed로 검증한다.
- P4 Application Dashboard IA realignment가 UI 독서 순서를 바꾸더라도 state, direct evidence, attention evidence, endpoint/resource evidence의 판단 owner를 frontend로 옮기지 않게 한다.
- P6 Snapshot history/detail realignment가 marker-first UI로 바뀌더라도 marker를 lifecycle state source로 오해하지 않게 한다.
- P8 Instance surface split이 live/snapshot/trend surface를 나누더라도 instance lifecycle state machine이나 health score를 만들지 않게 한다.
- Epic 10 Vite SPA 인수 결과를 되돌리지 않고, `read-model-types.ts`, `read-model-adapters.ts`, dashboard/instance/snapshot components의 display-only contract를 강화한다.

## Rollback

- 이번 story artifact 생성분의 rollback은 `planning-artifacts/stories/13-2-frontend-read-model-contract-guard.md` 추가와 `implementation-artifacts/sprint-status.yaml`의 `13-2` status/`last_updated` 변경만 되돌린다.
- 향후 P1 구현 rollback은 `frontend adapter/type/fixture guard` commit 단위로 되돌린다.
- P1 rollback은 backend API/read model shape, Source of Truth 문서, 완료 story 파일을 건드리지 않는다.
- P1 guard가 P4/P6/P8 구현을 막으면 해당 downstream story를 고치는 것이 우선이며, guard를 완화하려면 Source of Truth와 Epic 13 dependency를 다시 검토한다.

## Out of scope

- Source of Truth 문서 의미, 우선순위, response contract 재정의.
- P2/P3 backend window, DTO, mapper, service, scheduler, repository 변경.
- P4 Application Dashboard IA 재배치 구현.
- P6 Snapshot history/detail UI realignment 구현.
- P8 Instance live/snapshot/trend surface split 구현.
- 새로운 backend endpoint, Next.js API route, frontend auth/fetch foundation 교체.
- lifecycle state, rule, p95/p99, endpoint priority, marker/event, instance status 계산 로직을 frontend에 추가하는 작업.
- 완료된 Epic 4/5/6/10 story 파일 본문 또는 done 상태 수정.
- 기존 untracked `dbml-error.log` 수정, 삭제, stage.

## Acceptance Criteria

1. Frontend adapter/type/fixture guard는 `ApplicationDashboardReadModel`, snapshot/history, instance evidence, instance trend read model의 server-computed field와 order 보존을 검증한다.
2. UI와 adapter는 lifecycle state, starter connection diagnosis, zeroInsight reason, recovery guidance, insight rule을 accepted bucket, heartbeat, resource hint, triage field 조합으로 재계산하지 않는다.
3. `endpointPriority`는 server-provided array order와 `rank`를 그대로 표시한다. `.sort()`, `.reduce()`, index 기반 rank, score/confidence 재계산, client-side top-N 축약으로 새 order를 만들지 않는다.
4. Snapshot marker/history list와 instance trend point는 server response의 source/order/horizon semantics를 보존한다. Client는 marker/trend point를 임의 chronological, reverse chronological, severity, confidence, captureReason 순서로 재정렬하지 않는다.
5. Snapshot marker는 lifecycle state가 아니라 timeline 탐색 색인이다. Guard는 `markerIsStateSource=false`, snapshot detail의 stored read model source, marker/detail source 분리를 깨뜨리면 실패해야 한다.
6. Instance evidence UI는 `observationStatus`, endpoint presence, resource evidence, application contribution을 표시만 하고 instance lifecycle state, health score, root cause, endpoint priority를 새로 만들지 않는다.
7. Instance trend UI는 stored `instanceSummary.items[]` projection과 `storedApplicationStateCode`를 표시만 한다. Point field를 조합해 current state, instance state, recovery proof, health score를 만들지 않는다.
8. 허용되는 UI 변환은 날짜 formatting, badge class mapping, humanized copy, nullable display copy, histogram cumulative-to-display bucket 변환으로 제한한다. Histogram bucket에서 p95/p99, avg/max latency, latency score, regression, rule 판단을 계산하지 않는다.
9. Fixture는 server order와 natural order가 일부러 다르게 보이는 sentinel data를 포함해, adapter/component가 server order를 보존하는지 확인한다.
10. Guard는 malformed, missing, contradictory read semantics를 safe error/empty/limitation state로 수렴시키며, health/normal/recovered/host down 같은 확정 copy를 만들지 않는다.
11. P1 구현은 Source of Truth 문서, 완료 story 파일, backend code, backend test, migration을 수정하지 않는다.
12. Verification은 최소 `cd frontend && npm run typecheck`, `cd frontend && npm run build`, recompute 의심 static grep, `git diff --check`, `git status --short`를 포함한다. 테스트 harness를 추가하면 dependency와 실행 명령을 story completion notes에 남긴다.

## Tasks / Subtasks

- [x] 현재 frontend read model boundary 확인 (AC: 1~8)
  - [x] `frontend/src/app/lib/read-model-types.ts`의 dashboard, snapshot/history, instance evidence, instance trend DTO shape를 Source of Truth 기준으로 점검한다.
  - [x] `frontend/src/app/lib/read-model-adapters.ts`의 표시용 helper가 허용 변환만 수행하는지 점검한다.
  - [x] `frontend/src/app/components/dashboard.tsx`, `instance-panels.tsx`, `snapshot-detail-surface.tsx`에서 server-computed order/value를 바꾸는 helper 후보를 분류한다.
- [x] Adapter/type/fixture guard 설계 및 구현 (AC: 1, 8~10, 12)
  - [x] 기존 npm-only Vite/TypeScript tooling을 우선 사용한다.
  - [x] 별도 test script가 필요하면 최소 범위로 추가하고, dependency 추가 여부와 이유를 completion notes에 남긴다.
  - [x] server order와 natural order가 다르게 보이는 dashboard endpoint priority, snapshot marker, trend point, instance evidence fixture를 만든다.
  - [x] Guard fixture는 date/badge/copy/histogram display 변환은 허용하고 state/order/source 재계산은 실패시키도록 둔다.
- [x] Application Dashboard guard 추가 (AC: 2, 3, 8, 10)
  - [x] lifecycle state, starter connection, zeroInsight, recovery, triage, endpoint priority가 server field display에 머무는지 검증한다.
  - [x] endpoint priority `rank`와 array order가 보존되는지 검증한다.
  - [x] histogram display 변환이 p95/p99 또는 rule 판단으로 확장되지 않는지 검증한다.
- [x] Snapshot/history/detail guard 추가 (AC: 4, 5, 8, 10)
  - [x] marker/history response의 source/order/horizon metadata를 검증하고 client reorder를 금지한다.
  - [x] marker bucket/severity/captureReason이 lifecycle state나 detail evidence source로 승격되지 않는지 검증한다.
  - [x] Snapshot detail은 stored read model 복원이며 current dashboard fallback이나 raw JSON explorer가 아님을 guard한다.
- [x] Instance evidence/trend guard 추가 (AC: 4, 6~8, 10)
  - [x] Instance Evidence는 Application Dashboard보다 강한 판단을 만들지 않는지 검증한다.
  - [x] Instance Trend는 server order와 stored projection semantics를 보존하고 health score/current state/recovery proof를 만들지 않는지 검증한다.
  - [x] `storedApplicationStateCode`를 instance state처럼 표시하거나 재해석하지 않는지 검증한다.
- [x] 보호 대상 및 verification 확인 (AC: 11, 12)
  - [x] Source of Truth 문서와 완료 story 파일이 diff에 포함되지 않았는지 확인한다.
  - [x] backend code/test/migration이 diff에 포함되지 않았는지 확인한다.
  - [x] `dbml-error.log`가 기존 untracked 상태로 남아 있는지 확인한다.
  - [x] `cd frontend && npm run typecheck`, `cd frontend && npm run build`, static grep, `git diff --check`, `git status --short` 결과를 기록한다.

## Dev Notes

- Active implementation baseline은 Traditional MVC + Service/Repository Layering이다. P1은 frontend guard story이며 backend controller/service/repository/migration을 변경하지 않는다.
- Frontend workspace는 `frontend/` 최상위 Vite SPA다. `frontend/package.json`은 `typecheck`, `build`, `dev` script를 제공하며 현재 별도 `test` script는 없다.
- 주요 frontend read model boundary 후보는 `frontend/src/app/lib/read-model-types.ts`, `frontend/src/app/lib/read-model-adapters.ts`, `frontend/src/app/components/dashboard.tsx`, `frontend/src/app/components/instance-panels.tsx`, `frontend/src/app/components/snapshot-detail-surface.tsx`다.
- Story 10.3은 Project/Application/Dashboard를 server-provided `links.applications`와 `links.dashboard` chain으로 연결하고, `endpointPriority` server order/rank 보존을 acceptance로 잠갔다.
- Story 10.4는 Instance Evidence, Instance Snapshot Trend, Snapshot/History, Snapshot Detail을 기존 endpoint로 연결했고, static grep에서 `sort(`/`reduce(` 0건을 acceptance evidence로 기록했다.
- 현재 workspace inspection 기준 `frontend/src/app/components/instance-panels.tsx`의 `TrendReadyView`와 `frontend/src/app/components/dashboard.tsx`의 `SnapshotHistoryReady`에는 display order를 바꾸는 `.sort()` 사용 후보가 보인다. P1 구현자는 이 match를 server order 위반인지 검토하고, 위반이면 guard-first 방식으로 실패를 재현한 뒤 수정해야 한다.
- `toDashboardPresentation()` 같은 adapter는 날짜 formatting, nullable display, badge class, display bucket 변환 정도만 맡아야 한다. Lifecycle state, endpoint priority, marker severity/type, instance status를 새로 계산하는 helper로 확장하지 않는다.
- Snapshot marker는 `markerBucket`/severity/readMeaning 같은 탐색 색인이다. Detail state/evidence source는 저장된 dashboard read model이며, marker helper column이나 marker bucket이 source of truth가 아니다.
- Instance Dashboard는 Application Dashboard 판단을 대체하지 않는다. Instance에는 application lifecycle state와 같은 top-level `state`를 만들지 않고, `observationStatus`와 `applicationContribution`만 표시한다.
- Instance trend의 source는 stored `dashboard_snapshots.read_model_json.instanceSummary.items[]` projection이다. Trend point order는 response의 `horizon.order`와 server-provided point order를 따라야 하며, client가 임의로 health timeline을 재구성하지 않는다.
- P1은 P2/P3 backend shape 변경과 P4/P6/P8 UI realignment 전에 놓는 fail-closed guard다. 이후 story가 guard를 깨면 downstream story가 Source of Truth에 맞게 조정되어야 한다.

### Suggested Verification Commands

```bash
cd frontend && npm run typecheck
cd frontend && npm run build
rg -n "sort\\(|toSorted\\(|reduce\\(|percentile|p95|p99|endpointPriority|state.code|captureReason|resolvedAt|markerBucket|storedApplicationStateCode" frontend/src/app/lib frontend/src/app/components
git diff --check
git status --short
```

## References

- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/epics.md`
- `planning-artifacts/stories/13-doc-1-alignment-epic-story-creation.md`
- `planning-artifacts/dashboard-source-of-truth-realignment-sequence.yaml`
- `planning-artifacts/dashboard-source-of-truth-realignment-roadmap.md`
- `planning-artifacts/source-of-truth/current-product-source-of-truth.md`
- `planning-artifacts/source-of-truth/application-dashboard-read-model-mvp-source-of-truth.md`
- `planning-artifacts/source-of-truth/dashboard-snapshot-mvp-source-of-truth.md`
- `planning-artifacts/source-of-truth/instance-dashboard-read-model-mvp-source-of-truth.md`
- `planning-artifacts/contracts/read-model-contract.md`
- `_bmad/custom/project-context.md`
- `planning-artifacts/stories/6-4-application-dashboard-ui-integration.md`
- `planning-artifacts/stories/6-5-instance-evidence-ui.md`
- `planning-artifacts/stories/6-6-instance-snapshot-trend-ui.md`
- `planning-artifacts/stories/6-7-snapshot-history-marker-ui-and-deep-link.md`
- `planning-artifacts/stories/10-3-wire-types-adapters-navigation-and-dashboard.md`
- `planning-artifacts/stories/10-4-wire-evidence-trend-and-credential-surfaces.md`
- `planning-artifacts/stories/10-7-run-acceptance-gate.md`

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- `cd frontend && npm run guard:read-model-contract` RED: guard module/type 확장 전 missing module 및 `markerIsStateSource` type mismatch로 실패 확인.
- `cd frontend && npm run guard:read-model-contract`: 통과. 새 dependency 없이 TypeScript compiler와 Node로 sentinel fixture 실행.
- `cd frontend && npm run guard:read-model-contract`: 통과. dashboard policy drift, marker-as-state, nested instance decision field negative sentinel과 adapter/static source guard를 추가 검증했다.
- `cd frontend && npm run typecheck`: 통과.
- `cd frontend && npm run build`: 통과.
- `rg -n "sort\\(|toSorted\\(|reduce\\(|percentile|p95|p99|endpointPriority|state.code|captureReason|resolvedAt|markerBucket|storedApplicationStateCode" frontend/src/app/lib frontend/src/app/components`: `sort/toSorted/reduce` 매치 없음. 남은 매치는 DTO field, 표시 copy, guard assertion, sentinel fixture reference다.
- `git diff --check`: 통과.
- `git status --short`: frontend/story/sprint 변경과 기존 untracked `dbml-error.log` 확인.

### Completion Notes List

- `read-model-contract-guard.ts`를 추가해 Application Dashboard, snapshot history/detail, instance evidence, instance trend 응답의 source/context/horizon/readSemantics를 runtime에서 fail-closed로 검증한다.
- `read-model-contract-fixtures.ts`와 `npm run guard:read-model-contract` harness를 추가했다. 새 dependency는 추가하지 않았고 기존 `typescript` compiler와 Node만 사용한다.
- dashboard snapshot history와 instance trend의 client-side `.sort()`를 제거해 event/marker/trend point를 server response array order 그대로 표시한다.
- `endpointPriority`는 array order와 item `rank`를 그대로 보존하도록 guard fixture에서 rank/natural order가 어긋난 sentinel data로 검증한다.
- snapshot detail은 `markerIsStateSource=false`, stored detail mode, live source 미결합, raw JSON 미노출, marker/detail state helper 일치 조건을 guard한다.
- instance evidence/trend는 top-level `state`, health score, root cause, recovery proof, endpoint priority 승격 field를 거부한다.
- dashboard percentile/histogram source policy drift를 fail-closed로 막고, nullable baseline window를 display copy로 수렴시켰다.
- snapshot marker/history는 `readMeaning=timeline_index`만 허용해 marker-as-state 해석을 차단한다.
- instance evidence/trend는 nested `rootCause`, `currentState`, `healthScore`, `endpointPriority` 같은 stronger judgement field도 deep guard로 거부한다.
- instance trend limit/maxLimit은 30분 scheduled snapshot contract 기준 `7d=336`, `14d=672`, `maxLimit=672`로 맞췄다.
- guard harness가 adapter presentation reference 보존과 component source의 `.sort()`/`.toSorted()`/`.reduce()` 회귀를 함께 검사하게 했다.
- guard 전용 TypeScript build는 `strict=true`로 올려 fixture/type drift를 더 빨리 드러내게 했다.
- `capturedAt_asc` order copy를 "오래된 기록 먼저"로 바로잡아 server order metadata를 그대로 설명하게 했다.
- Source of Truth 문서, 완료 story 파일, backend code/test, migration은 수정하지 않았다. 기존 untracked `dbml-error.log`도 수정/삭제/stage하지 않았다.

### File List

- `frontend/package.json`
- `frontend/tsconfig.guard.json`
- `frontend/scripts/read-model-contract-guard.ts`
- `frontend/src/app/lib/read-model-contract-fixtures.ts`
- `frontend/src/app/lib/read-model-contract-guard.ts`
- `frontend/src/app/lib/read-model-types.ts`
- `frontend/src/app/lib/read-model-adapters.ts`
- `frontend/src/app/components/dashboard.tsx`
- `frontend/src/app/components/instance-panels.tsx`
- `frontend/src/app/components/snapshot-detail-surface.tsx`
- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/stories/13-2-frontend-read-model-contract-guard.md`

### Change Log

| Date | Change |
|---|---|
| 2026-06-09 | P1 frontend read model contract guard와 sentinel fixture harness를 추가하고 snapshot/history/trend client reorder를 제거했다. |
| 2026-06-10 | BMAD review findings를 반영해 dashboard policy, snapshot marker semantics, instance nested decision field, trend 30분 limit guard를 보강하고 story를 done으로 전환했다. |
