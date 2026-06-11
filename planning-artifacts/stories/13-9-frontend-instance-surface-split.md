---
artifactType: story
storyId: "13.9"
storyKey: "13-9-frontend-instance-surface-split"
epic: "Epic 13. Dashboard Source of Truth Realignment"
title: "Frontend Instance Surface Split"
architectureStyle: Traditional MVC
status: done
date: 2026-06-11
phase: P8
workType: frontend
implementationScope: "Instance Dashboard live detail, selected Application Snapshot instance snapshot detail, stored Instance Snapshot Trend surface, frontend route/state/adapter/type guard alignment"
productionCodeChangeThisContext: false
plannedProductionCodeChange: true
sourceOfTruthMode: read-only
dependsOn:
  - P0
  - P1
  - P6
  - P7
  - 13-2-frontend-read-model-contract-guard
  - 13-5-frontend-application-dashboard-ia-realignment
  - 13-7-frontend-snapshot-history-detail-realignment
  - 13-8-backend-instance-dashboard-live-snapshot-mode-split
blocks:
  - P10
  - 13-11-end-to-end-acceptance-and-demo-hardening
rollbackBoundary: "instance live dashboard modal, selected snapshot instance dashboard surface, stored trend surface, frontend path/type/guard split"
---

# Story 13.9 - Frontend Instance Surface Split

## Status

done

2026-06-11: BMAD code review 후 blocker 없음으로 확인했고 nested instance `state/stateCode` forbidden guard와 summary cap 밖 selected instance fixture를 보강했다. `npm run guard:read-model-contract`, `npm run typecheck`, `npm run build`, `git diff --check`를 통과해 done 상태로 전환한다.

2026-06-11: P8 frontend 구현을 완료하고 live Instance Dashboard detail, selected Application Snapshot 기준 snapshot detail, stored Instance Snapshot Trend surface를 분리했다. Contract guard, typecheck, build, static grep, local app DOM 확인을 통과해 review 상태로 전환한다.

2026-06-11: BMAD dev-story workflow에 따라 Story 13.9 구현을 시작했고 sprint-status를 `in-progress`로 전환했다.

2026-06-11: P8 frontend alignment story artifact를 생성하고 sprint-status를 `ready-for-dev`로 정렬했다. 이번 컨텍스트에서는 production code, frontend code, backend code/test, migration/schema, cleanup physical delete, Source of Truth 문서를 구현/수정하지 않고 story artifact와 sprint-status만 최소 변경한다.

## Story

frontend 구현자로서, Instance Dashboard frontend surface를 live Instance Dashboard detail, selected Application Snapshot 기준 Instance Dashboard snapshot detail, stored Instance Snapshot Trend surface로 분리하고 싶다.

그래야 사용자가 같은 selected instance를 보더라도 "현재 recent 30 minutes evidence", "선택한 Application Snapshot window 기준 재구성 evidence", "저장된 snapshot summary projection trend"를 서로 다른 source semantics로 이해하고, frontend가 Application Dashboard 판단을 대체하거나 instance 독립 lifecycle state를 만들지 않는다.

## Source of Truth

아래 문서는 이 story의 기준이다. 이 story는 해당 문서의 의미를 재정의하지 않고 frontend 구현 지침으로만 옮긴다.

1. `implementation-artifacts/sprint-status.yaml`
2. `planning-artifacts/epics.md`
3. `planning-artifacts/dashboard-source-of-truth-realignment-roadmap.md`
4. `planning-artifacts/dashboard-source-of-truth-realignment-sequence.yaml`
5. `planning-artifacts/contracts/read-model-contract.md`
6. `planning-artifacts/source-of-truth/instance-dashboard-read-model-mvp-source-of-truth.md`
7. `planning-artifacts/source-of-truth/dashboard-snapshot-mvp-source-of-truth.md`
8. `planning-artifacts/source-of-truth/dashboard-snapshot-retention-cleanup-source-of-truth.md`
9. `planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html`
10. `planning-artifacts/stories/13-2-frontend-read-model-contract-guard.md`
11. `planning-artifacts/stories/13-5-frontend-application-dashboard-ia-realignment.md`
12. `planning-artifacts/stories/13-7-frontend-snapshot-history-detail-realignment.md`
13. `planning-artifacts/stories/13-8-backend-instance-dashboard-live-snapshot-mode-split.md`
14. `_bmad/custom/project-context.md`
15. `planning-artifacts/architecture.md`
16. `planning-artifacts/architecture-implementation-supplement.md`
17. `planning-artifacts/project-structure.md`

`planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html`은 13.9 frontend UI의 주요 visual/interaction Source of Truth다. 구현자는 이 HTML의 구조, copy, visual hierarchy, layout, CSS 의도를 읽되 현재 React/Vite frontend 구조와 existing shadcn/Radix UI component pattern에 맞춰 구현한다.

PNG 파일, PNG export, screenshot image 파일은 이 story의 디자인 source of truth가 아니다. 구현/검토 중 PNG 파일을 열람, 비교, 참조, 인용하지 않는다.

## Background

Epic 13은 완료된 Epic 4/5/6/10 story를 다시 여는 작업이 아니라, 확정된 Dashboard Source of Truth를 새 alignment story 묶음으로 적용하는 tracking epic이다. Story 13.5는 Application Dashboard live IA를 운영자 질문 순서로 재배치했고, Story 13.7은 Snapshot history/detail을 marker-first 30분 point 탐색과 stored Application Snapshot Dashboard 복원 surface로 정렬했다. Story 13.8은 Instance Dashboard backend 계약을 live mode와 selected Application Snapshot 기반 snapshot mode로 분리했다.

현재 frontend는 `frontend/src/app/components/instance-panels.tsx`에서 instance evidence와 trend를 오른쪽 `Sheet`로 연다. Evidence path는 기존 `/instances/{instanceId}/evidence` 성격이고, trend path는 `/instances/{instanceId}/snapshot-trend`를 소비한다. P8 구현은 이 흐름을 Source of Truth 목업의 넓은 modal 구조와 13.8 backend `/dashboard` 계약 중심으로 재정렬한다.

P8 이후 Instance 관련 surface는 세 가지로 분리된다.

1. Live Instance Dashboard detail
   - source: `accepted_metric_buckets`
   - endpoint: `GET /api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/dashboard`
   - meaning: 현재 query 시각 기준 `recent_30_minutes` selected instance evidence
2. Selected Application Snapshot Instance Dashboard snapshot detail
   - source: selected `dashboard_snapshots` row metadata + `accepted_metric_buckets`
   - endpoint: `GET /api/projects/{projectId}/applications/{applicationId}/snapshots/{snapshotId}/instances/{instanceId}/dashboard`
   - meaning: 선택한 Application Snapshot row window 기준 selected instance evidence 재구성
3. Stored Instance Snapshot Trend
   - source: `dashboard_snapshots.read_model_json.instanceSummary.items[]`
   - endpoint: existing instance snapshot trend surface
   - meaning: 저장된 bounded summary projection trend. current state, health score, snapshot mode evidence를 재계산하지 않음.

이 세 surface는 서로 navigation으로 연결될 수는 있지만, source semantics와 adapter/type guard는 섞이지 않아야 한다.

## Aligns

- `6-5-instance-evidence-ui`: selected instance evidence detail을 Application Dashboard 판단의 하위 evidence로 읽는 흐름을 유지하되, P8 이후 primary detail은 13.8 `InstanceDashboardReadModel` live/snapshot mode를 소비한다.
- `6-6-instance-snapshot-trend-ui`: 기존 Instance Snapshot Trend는 stored `instanceSummary.items[]` projection surface로 유지한다.
- `10-4-wire-evidence-trend-and-credential-surfaces`: existing `useApiResource`, auth generation, internal path validation, stale response guard, read-model guard pattern을 재사용한다.
- `13-2-frontend-read-model-contract-guard`: frontend guard가 server-computed source/order/value와 forbidden instance decision field를 fail-closed로 보호한다.
- `13-5-frontend-application-dashboard-ia-realignment`: live/snapshot instance modal은 Application Dashboard와 같은 neutral border, compact panel, read semantics first-screen language를 따른다.
- `13-7-frontend-snapshot-history-detail-realignment`: selected Application Snapshot에서 instance drill-down할 때 snapshot id와 selected snapshot window provenance를 잃지 않는다.
- `13-8-backend-instance-dashboard-live-snapshot-mode-split`: P8 frontend는 13.8 backend가 제공한 `instance_dashboard_read_model.v1` 계약을 소비한다.

## Supersedes

아래 항목은 완료 story 본문이나 이력을 바꾸지 않고, P8 이후 frontend 구현에서 좁게 대체하는 해석이다.

- Instance detail을 기존 `/evidence` sheet만으로 충분히 표현해도 된다는 해석.
- selected Application Snapshot에서 instance를 drill down할 때 live/current instance evidence endpoint로 fallback해도 된다는 해석.
- selected snapshot instance detail의 필수 source가 `dashboard_snapshots.read_model_json.instanceSummary.items[]`라는 해석.
- Instance Snapshot Trend를 독립 instance health timeline 또는 current state history처럼 렌더링해도 된다는 해석.
- endpoint `not_observed`를 "정상"이나 "문제 없음"으로 표시해도 된다는 해석.
- frontend가 top-level instance lifecycle `state`, `stateCode`, `health`, `healthScore`, `rootCause`, `recoveryProof`를 만들거나 렌더링해도 된다는 해석.

## Hardens

- Live Instance Dashboard surface와 selected snapshot Instance Dashboard surface는 route/state/adapter/type guard에서 분리된다.
- Snapshot history/detail에서 특정 instance drill-down 시 selected `snapshotId`를 포함한 snapshot mode endpoint를 사용한다.
- Missing/expired/retention gap은 live/current fallback 없이 source absence 또는 `metric_missing`/`not_observed_in_window` 계열 UX로 표시된다.
- `applicationStateRef.lifecycleOwner=application` 경계를 UI와 guard에서 유지한다.
- Application Snapshot Detail의 stored state/evidence source는 계속 `dashboard_snapshots.read_model_json`이며, Instance Dashboard snapshot mode가 이를 검증/대체하지 않는다.
- Existing Instance Snapshot Trend는 stored trend projection으로 유지하고 Instance Dashboard snapshot detail과 섞지 않는다.

## Rollback

- rollback 단위는 instance modal/surface component, live/snapshot instance dashboard loader, path adapter, type/guard fixture, stored trend copy split 변경이다.
- P8 rollback은 Story 13.8 backend route/service/read model, Story 13.7 snapshot history/detail, Source of Truth 문서, completed story 본문/status, migration/schema를 되돌리지 않는다.
- snapshot mode instance drill-down이 문제가 되면 selected snapshot -> instance dashboard action만 비활성화하고, Application Snapshot stored detail과 existing trend surface는 유지한다.
- guard가 기존 UI를 막으면 guard 완화보다 route/state/source semantics split을 Source of Truth에 맞게 고치는 것이 우선이다.

## Out of Scope

- 이번 create-story 컨텍스트의 production code, frontend code, backend code/test, migration/schema 구현.
- Source of Truth 문서 수정 또는 의미 재정의.
- 완료 story 13.2/13.3/13.4/13.5/13.6/13.7/13.8 본문 수정 또는 done 상태 변경.
- backend endpoint, service, repository, read model, migration/schema 변경.
- retention cleanup scheduler/service/physical delete 구현.
- Application Dashboard lifecycle state, endpoint priority, resource pattern, snapshot detail stored read model을 frontend에서 재계산하는 구현.
- top-level instance lifecycle `state`, `stateCode`, `health`, `healthScore`, `rootCause`, `recoveryProof`, independent instance lifecycle state machine.
- `dashboard_snapshots.read_model_json.instanceSummary.items[]`를 Instance Dashboard snapshot detail 필수 source로 쓰는 구현.
- 기존 Instance Snapshot Trend를 current state/health score/root cause timeline으로 확장하는 구현.
- raw metric explorer, endpoint timeseries UI, arbitrary metric query UI.
- 새로운 dependency, Next.js API route, browser token persistence, URL token parsing.
- 기존 untracked `dbml-error.log` 수정, 삭제, stage.
- `planning-artifacts/stories/13-6-backend-30-minute-scheduled-snapshot-and-slot-horizon-alignment.md`의 기존 modified 상태 수정.
- PNG 파일, PNG export, screenshot image 파일 열람/비교/참조/인용.

## Acceptance Criteria

1. Given P8 frontend implementation을 시작할 때, Then `source-of-truth-dashboard-mockup.html`의 구조, copy, visual hierarchy, layout, CSS를 주요 UI 기준으로 읽고, PNG 파일/PNG export/screenshot image는 기준으로 삼지 않는다.
2. Given live Instance Dashboard detail을 열 때, Then frontend는 `GET /api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/dashboard`를 primary endpoint로 사용하고 selected Project/Application/Instance context와 internal API path를 검증한다.
3. Given live Instance Dashboard response가 로드될 때, Then adapter/guard는 `schemaVersion=instance_dashboard_read_model.v1`, `mode=live`, `readSemantics.source=accepted_metric_buckets`, `window.name=recent_30_minutes`, `window.windowSource=live_recent_30_minutes`, `readSemantics.instanceEvidenceReconstructedFromMetrics=false`를 fail-closed로 검증한다.
4. Given live Instance Dashboard response가 표시될 때, Then `snapshot` block은 null이어야 하고 `readSemantics.snapshotRowSource`는 null이어야 하며, UI는 live surface를 selected snapshot evidence처럼 표현하지 않는다.
5. Given Snapshot history/detail에서 특정 instance를 drill down할 때, Then frontend state target은 selected `snapshotId`를 포함하고 `GET /api/projects/{projectId}/applications/{applicationId}/snapshots/{snapshotId}/instances/{instanceId}/dashboard`를 호출한다.
6. Given selected snapshot Instance Dashboard response가 로드될 때, Then adapter/guard는 `mode=snapshot`, `window.windowSource=selected_application_snapshot`, `snapshot.snapshotRowSource=dashboard_snapshots`, `readSemantics.snapshotRowSource=dashboard_snapshots`를 검증한다.
7. Given selected snapshot Instance Dashboard response의 read semantics를 검증할 때, Then `readSemantics.acceptedAtCutoffApplied=false`, `readSemantics.includesLateAcceptedMetrics=true`, `readSemantics.mayDifferFromStoredApplicationSnapshot=true`, `readSemantics.applicationSnapshotRecalculated=false`, `readSemantics.instanceEvidenceReconstructedFromMetrics=true`, `readSemantics.markerIsStateSource=false`가 모두 보존된다.
8. Given selected snapshot Instance Dashboard surface가 표시될 때, Then first-screen note 또는 read semantics panel은 selected Application Snapshot row window 기준 evidence이며 late-arriving metric 때문에 stored Application Snapshot과 일부 다를 수 있음을 짧게 안내한다.
9. Given Instance Dashboard modal이 열릴 때, Then HTML mockup의 넓은 modal 정보 구조를 따른다: context note, `Application state reference`, `Read semantics`, selected instance metrics, endpoint evidence, resource evidence, starter connection, normalized endpoint evidence table 또는 동등한 순서를 제공한다.
10. Given existing frontend가 오른쪽 `Sheet` 기반 instance evidence/trend를 사용하더라도, Then P8 구현은 live/snapshot Instance Dashboard detail을 좁은 Sheet가 아니라 넓은 modal/dialog 또는 동등한 full-width detail surface로 분리한다. Stored trend는 별도 surface로 유지할 수 있다.
11. Given `applicationStateRef`가 표시될 때, Then UI는 `lifecycleOwner=application`과 application-owned state reference만 표시한다. selected instance의 top-level lifecycle state로 승격하지 않는다.
12. Given Instance Dashboard response shape 또는 UI model을 검토할 때, Then frontend는 top-level instance lifecycle `state`, `stateCode`, `health`, `healthScore`, `rootCause`, `recoveryProof` field를 만들거나 렌더링하지 않는다. 이런 field가 response/fixture에 들어오면 guard는 safe error로 수렴한다.
13. Given `observationStatus.code=not_observed_in_window`, `metric_missing`, `insufficient_evidence`, `malformed_evidence` 또는 endpoint `presenceOnSelectedInstance=not_observed`가 표시될 때, Then copy는 "selected instance에서 관찰되지 않음" 또는 evidence limitation으로 표현한다. "정상", "문제 없음", "복구 완료"로 표현하지 않는다.
14. Given selected Application Snapshot id가 없거나 404/expired/retention gap 응답을 받을 때, Then UI는 "보관 기간이 지났거나 selected snapshot/instance evidence를 찾을 수 없음" 계열 copy를 표시하고 live dashboard/current accepted bucket fallback을 만들지 않는다.
15. Given selected snapshot row는 retention 안에 있지만 해당 window의 selected instance bucket이 cleanup으로 삭제됐거나 원래 없을 때, Then snapshot mode instance detail은 `metric_missing` 또는 `not_observed_in_window` data quality/observation UX로 수렴하고 live/current evidence로 보정하지 않는다.
16. Given selected Application Snapshot stored `read_model_json.instanceSummary.items[]`에 target instance가 없을 때, Then frontend는 이를 Instance Dashboard snapshot detail의 hard failure로 해석하지 않는다. snapshot id와 selected instance id가 있으면 snapshot mode endpoint를 호출하고, detail source는 endpoint response의 `accepted_metric_buckets` semantics를 따른다.
17. Given Application Snapshot Detail을 렌더링할 때, Then stored state/evidence source는 계속 `dashboard_snapshots.read_model_json`이다. Instance Dashboard snapshot mode는 stored Application Snapshot을 검증하거나 대체하거나 current metric으로 재계산하지 않는다.
18. Given Instance Snapshot Trend를 렌더링할 때, Then trend source는 existing stored `dashboard_snapshots.read_model_json.instanceSummary.items[]` projection으로 표시한다. UI는 trend point를 current state, instance health score, root cause, recovery proof, snapshot mode evidence로 재계산하지 않는다.
19. Given trend point에서 detail을 열 수 있을 때, Then trend point의 stored Application Snapshot detail navigation과 selected instance snapshot mode drill-down은 별도 action/source copy로 구분한다. Trend list 자체가 Instance Dashboard snapshot detail source가 되지 않는다.
20. Given endpoint evidence items를 렌더링할 때, Then frontend는 server-provided order/display order를 보존하고 endpoint priority를 client-side로 재계산하지 않는다. `not_observed`는 selected instance observation absence로만 표시한다.
21. Given resource evidence를 렌더링할 때, Then resource threshold hit 단독으로 root cause claim을 만들지 않고 request symptom과 함께 있을 때만 shared resource pressure pattern hint로 표시한다.
22. Given starter connection을 렌더링할 때, Then heartbeat는 metric state, observationStatus, applicationState를 직접 바꾸지 않는 control-plane 정보로 표시한다.
23. Given selected Project/Application/Instance/Snapshot target, auth generation, reload, token clear, unmount가 바뀔 때, Then pending live/snapshot/trend request는 `AbortSignal`, request sequence, `resourceKey`, selected context validation으로 폐기되고 최신 화면을 덮지 않는다.
24. Given frontend guard fixture를 갱신할 때, Then live fixture, selected snapshot fixture, summary cap 밖 selected instance fixture, retention gap fixture, endpoint `not_observed` fixture, forbidden field fixture, stored trend projection fixture를 포함한다.
25. Given implementation diff를 검토할 때, Then Source of Truth 문서, 완료 story 13.2/13.3/13.4/13.5/13.6/13.7/13.8, backend code/test, migration/schema, cleanup physical delete, `dbml-error.log`, PNG 파일이 변경되지 않았음이 확인된다.
26. Given 새 공개 type/helper/component/test 또는 동작을 바로 이해하기 어려운 내부 helper를 추가할 때, Then AGENTS.md 기준에 따라 한국어 JSDoc/comment와 한국어 테스트 display name/copy를 사용한다.

## Tasks / Subtasks

- [x] 현재 instance frontend boundary와 13.8 backend 계약을 조사한다. (AC: 1~7, 16~18)
  - [x] `frontend/src/app/components/instance-panels.tsx`의 existing Sheet, `/evidence` loader, stored trend loader, stale guard 흐름을 확인한다.
  - [x] `frontend/src/app/components/dashboard.tsx`의 `InstancesPanel` target shape와 instance action entry를 확인한다.
  - [x] `frontend/src/app/components/snapshot-detail-surface.tsx`와 `snapshot-history-panel.tsx`에서 selected snapshot id와 instance drill-down action을 어디서 연결할지 확인한다.
  - [x] 13.8 backend `InstanceDashboardReadModel` field 이름과 route를 frontend type/guard 기준으로 고정한다.

- [x] Instance Dashboard frontend type/path/adapter를 live/snapshot mode로 추가한다. (AC: 2~8, 11~17, 20~24)
  - [x] `InstanceDashboardReadModel` type을 `frontend/src/app/lib/read-model-types.ts`에 추가하거나 기존 `InstanceEvidenceReadModel`과 분리된 type으로 둔다.
  - [x] `validateLiveInstanceDashboardPath`, `buildLiveInstanceDashboardPath`, `validateSnapshotInstanceDashboardPath`, `buildSnapshotInstanceDashboardPath` 또는 동등 helper를 추가한다.
  - [x] `guardInstanceDashboardReadModel`을 추가해 live mode와 snapshot mode read semantics를 각각 fail-closed로 검증한다.
  - [x] forbidden field deep guard에 `state`, `stateCode`, `health`, `healthScore`, `rootCause`, `recoveryProof`, `instanceState`, `currentState` 계열을 유지/확장한다.
  - [x] `dashboard_snapshots.read_model_json.instanceSummary.items[]`는 `InstanceDashboardReadModel` snapshot detail source가 아니라 trend/stored summary source임을 fixture와 guard name으로 분리한다.

- [x] Instance view state와 route surface를 세 갈래로 분리한다. (AC: 2, 5, 10, 14, 18~19, 23)
  - [x] `useInstanceView` 또는 새 hook의 view union을 live dashboard, snapshot dashboard, stored trend로 분리한다.
  - [x] live target은 projectId/applicationId/instanceId만으로 live dashboard endpoint를 만든다.
  - [x] snapshot target은 projectId/applicationId/snapshotId/instanceId와 selected snapshot provenance를 반드시 포함한다.
  - [x] trend target은 existing `snapshotTrend` link 또는 fallback build path를 유지하되 stored projection source copy를 명시한다.
  - [x] Project/Application/Snapshot/Instance target 변경 시 stale detail, selected trend point, selected snapshot instance target을 초기화한다.

- [x] HTML mockup 기준의 Instance Dashboard modal/surface를 구현한다. (AC: 1, 8~13, 20~22)
  - [x] existing right Sheet detail을 live/snapshot dashboard detail에서는 넓은 modal/dialog 또는 동등한 full-width detail surface로 대체한다.
  - [x] modal 첫 영역에 mode, window, source, snapshot row, late metric flags, marker state source false를 보여준다.
  - [x] `Application state reference` panel은 `applicationStateRef.lifecycleOwner=application`과 application state reference만 표시한다.
  - [x] selected instance metrics는 request/server error/slow/resource/starter evidence를 표시하되 instance lifecycle state나 root cause claim을 만들지 않는다.
  - [x] endpoint evidence copy는 selected instance observation semantics와 `not_observed` limitation을 명확히 표시한다.
  - [x] missing/malformed/insufficient evidence는 safe copy로 수렴하고 "정상 확정" copy를 만들지 않는다.

- [x] Snapshot history/detail에서 selected instance snapshot drill-down을 연결한다. (AC: 5, 8, 14~17, 23)
  - [x] stored Application Snapshot Detail surface에서 instance summary row 또는 selected instance entry가 있으면 selected `snapshotId`를 포함해 snapshot mode Instance Dashboard action을 제공한다.
  - [x] `instanceSummary.items[]` 부재는 snapshot mode endpoint 호출을 막는 hard failure로 쓰지 않는다. UI entry가 없어서 action을 표시할 수 없는 경우에도 이 제약을 copy/guard에 명시한다.
  - [x] snapshot detail 404/expired/retention gap과 instance snapshot detail 404/expired/retention gap을 모두 live fallback 없이 표시한다.
  - [x] snapshot mode note는 `dashboard_snapshots.read_model_json` stored Application Snapshot과 selected instance metric reconstruction의 차이를 분리해 설명한다.

- [x] Stored Instance Snapshot Trend surface를 보존하고 copy를 좁힌다. (AC: 18~19)
  - [x] trend header/source panel에 `dashboard_snapshots.read_model_json.instanceSummary.items[]` projection source를 표시한다.
  - [x] trend point는 stored application state code와 bounded instance summary evidence만 표시하고 current instance state/health/root cause를 만들지 않는다.
  - [x] trend point에서 Application Snapshot detail 또는 Instance snapshot mode로 이동하는 action이 있다면 action label/source copy를 분리한다.

- [x] Contract fixture, guard script, static regression guard를 보강한다. (AC: 3~7, 11~24)
  - [x] `read-model-contract-fixtures.ts`에 live/snapshot `InstanceDashboardReadModel` fixture를 추가한다.
  - [x] `frontend/scripts/read-model-contract-guard.ts`에서 live/snapshot mode mismatch, wrong endpoint path, forbidden field, wrong `readSemantics` flag, `not_observed` copy guard를 검증한다.
  - [x] static source guard가 instance component에서 `.sort()`, `.toSorted()`, `.reduce()`로 server order를 바꾸거나 `healthScore`/`rootCause`/`recoveryProof`를 렌더링하지 않는지 확인한다.
  - [x] `source-of-truth-dashboard-mockup.html` 기준 copy를 React UI로 옮기되 prototype controls/runtime code를 복붙하지 않았는지 확인한다.

- [x] Verification과 보호 대상 확인을 수행한다. (AC: 25~26)
  - [x] `cd frontend && npm run guard:read-model-contract`
  - [x] `cd frontend && npm run typecheck`
  - [x] `cd frontend && npm run build`
  - [x] relevant static grep으로 source semantics, forbidden fields, fallback 금지, PNG 미사용을 확인한다.
  - [x] `git diff --check`와 `git status --short`로 Source of Truth 문서, completed story, backend/migration/schema, `dbml-error.log`, PNG 파일 변경이 없는지 확인한다.

## Dev Notes

### Current Code State

- Frontend source root는 `frontend/`다. `observability-portal/src/main/frontend`는 현재 존재하지 않는다.
- `frontend/package.json`은 React 18.3.1, Vite 6.3.5, TypeScript 5.8.3, Radix/shadcn-style UI, lucide-react를 사용한다. 새 dependency는 기본적으로 추가하지 않는다.
- `frontend/src/app/components/instance-panels.tsx`는 현재 `Sheet` 기반 `InstanceEvidenceView`와 `InstanceTrendView`를 제공한다.
- `InstanceEvidenceView`는 기존 `/api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/evidence` link를 validate/fetch한다.
- `InstanceTrendView`는 existing `/instances/{instanceId}/snapshot-trend` surface와 `SnapshotDetailSurface` compact detail을 함께 사용한다.
- `frontend/src/app/lib/read-model-contract-guard.ts`에는 기존 `guardInstanceEvidenceReadModel`과 `guardInstanceSnapshotTrendReadModel`이 있으며 forbidden instance decision field guard를 이미 갖고 있다.
- `frontend/src/app/lib/read-model-adapters.ts`에는 `INSTANCE_EVIDENCE_PATH`, `INSTANCE_SNAPSHOT_TREND_PATH`, `SNAPSHOT_DETAIL_PATH` validator가 있고, P8은 new instance dashboard path validator/build helper를 추가해야 한다.
- 현재 작업트리에는 기존 modified `planning-artifacts/stories/13-6-backend-30-minute-scheduled-snapshot-and-slot-horizon-alignment.md`와 untracked `dbml-error.log`가 있다. P8 구현자는 두 파일을 되돌리거나 삭제하거나 stage하지 않는다.

### Backend Contract From Story 13.8

Live endpoint:

```http
GET /api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/dashboard
```

Required live response semantics:

- `schemaVersion=instance_dashboard_read_model.v1`
- `mode=live`
- `readSemantics.source=accepted_metric_buckets`
- `window.name=recent_30_minutes`
- `window.windowSource=live_recent_30_minutes`
- `readSemantics.snapshotRowSource=null`
- `readSemantics.acceptedAtCutoffApplied=false`
- `readSemantics.includesLateAcceptedMetrics=false`
- `readSemantics.mayDifferFromStoredApplicationSnapshot=false`
- `readSemantics.applicationSnapshotRecalculated=false`
- `readSemantics.instanceEvidenceReconstructedFromMetrics=false`
- `readSemantics.markerIsStateSource=false`
- `snapshot=null`

Snapshot endpoint:

```http
GET /api/projects/{projectId}/applications/{applicationId}/snapshots/{snapshotId}/instances/{instanceId}/dashboard
```

Required snapshot response semantics:

- `schemaVersion=instance_dashboard_read_model.v1`
- `mode=snapshot`
- `readSemantics.source=accepted_metric_buckets`
- `window.name=recent_30_minutes`
- `window.windowSource=selected_application_snapshot`
- `snapshot.snapshotRowSource=dashboard_snapshots`
- `readSemantics.snapshotRowSource=dashboard_snapshots`
- `readSemantics.acceptedAtCutoffApplied=false`
- `readSemantics.includesLateAcceptedMetrics=true`
- `readSemantics.mayDifferFromStoredApplicationSnapshot=true`
- `readSemantics.applicationSnapshotRecalculated=false`
- `readSemantics.instanceEvidenceReconstructedFromMetrics=true`
- `readSemantics.markerIsStateSource=false`

Existing trend endpoint remains stored projection:

- source: `dashboard_snapshots.read_model_json.instanceSummary.items[]`
- purpose: bounded historical trend display
- non-goal: current state, instance health score, root cause, recovery proof, snapshot mode evidence recalculation

### Source Semantics

- Application Dashboard live source is `accepted_metric_buckets`.
- Application Snapshot Detail source is `dashboard_snapshots.read_model_json`.
- Instance Dashboard live source is `accepted_metric_buckets` recent 30 minutes.
- Instance Dashboard snapshot mode source is selected `dashboard_snapshots` row metadata plus selected instance `accepted_metric_buckets` inside the selected snapshot window.
- Instance Snapshot Trend source is stored `dashboard_snapshots.read_model_json.instanceSummary.items[]`.
- `dashboard_snapshots.read_model_json.instanceSummary.items[]` may be useful for snapshot summary/trend entry display, but must not become Instance Dashboard snapshot detail's required source.
- `accepted_at` cutoff is not applied in Instance Dashboard snapshot mode, so late accepted metrics may appear and may differ from stored Application Snapshot.

### UX / Interaction Notes From HTML Mockup

- Instance detail is a wide modal, not the old narrow right Sheet.
- Modal first copy says the detail does not replace Application judgment and only shows selected instance evidence in the same window.
- Snapshot mode modal copy says Application Snapshot itself is stored `dashboard_snapshots.read_model_json`, while selected instance evidence is reconstructed from selected snapshot row metadata and `accepted_metric_buckets`.
- Modal sections are `Application state reference`, `Read semantics`, selected instance metric grid, endpoint evidence on selected instance, resource evidence, starter connection, normalized endpoint evidence table.
- The visual grammar is neutral background, compact panels, 6px-ish radius, thin borders, small uppercase section labels, restrained badges for live/snapshot/source states.
- Prototype controls in the HTML mockup are not production UI and should not be copied.

### Guard / Adapter Notes

- Add a new guard for `InstanceDashboardReadModel`; do not weaken the existing `guardInstanceEvidenceReadModel` or `guardInstanceSnapshotTrendReadModel`.
- Path validators must reject arbitrary external URLs and context-mismatched internal paths.
- A discriminated union such as `kind: "live-instance-dashboard" | "snapshot-instance-dashboard" | "instance-trend"` is preferred over boolean mode flags scattered across components.
- Existing `/evidence` link may remain as compatibility/reference if needed, but P8 primary live detail should consume `/dashboard`.
- UI adapters should format dates, badges, nullable values, and copy only. They must not calculate lifecycle state, endpoint priority, root cause, or health score.
- Do not add backend route assumptions that are not in Story 13.8.

### Project Structure / Comment Notes

- Active baseline is Traditional MVC + Service/Repository Layering, but this story is frontend-only.
- Do not create `application`, `port`, `adapter`, `adapter.in`, `adapter.out` packages.
- Do not add backend controller/service/repository, Flyway migration, schema, cleanup scheduler, or physical delete code.
- New public type/helper/component/test and non-obvious internal helper should have concise Korean comments/JSDoc per AGENTS.md.
- UI copy, docs/comments, and test display names should use Korean. Keep external API names, class names, field names, and standard terms in English only where clearer.

## Candidate Implementation File List

- `frontend/src/app/components/instance-panels.tsx`
  - Candidate update: existing Sheet-based evidence/trend state를 live Instance Dashboard, selected snapshot Instance Dashboard, stored trend view union으로 분리한다.
- `frontend/src/app/components/instance-dashboard-modal.tsx`
  - Candidate new file: HTML mockup 기준 wide modal/dialog shell과 shared live/snapshot instance dashboard surface를 둔다.
- `frontend/src/app/components/instance-dashboard-surface.tsx`
  - Candidate new file: `InstanceDashboardReadModel` display-only sections, read semantics panel, application state ref, endpoint/resource/starter evidence를 렌더링한다.
- `frontend/src/app/components/dashboard.tsx`
  - Candidate update: `InstancesPanel` live action이 `/dashboard` target을 열도록 하고 old `/evidence` action/copy를 compatibility로 낮춘다.
- `frontend/src/app/components/snapshot-detail-surface.tsx`
  - Candidate update: stored Application Snapshot detail에서 selected instance drill-down action을 제공할 때 selected snapshot id를 snapshot mode target에 포함한다.
- `frontend/src/app/components/snapshot-history-panel.tsx`
  - Candidate update only if snapshot date/slot selection state must expose selected snapshot context to instance drill-down.
- `frontend/src/app/lib/read-model-types.ts`
  - Candidate update: `InstanceDashboardReadModel`, `InstanceDashboardWindow`, `InstanceDashboardSnapshot`, `InstanceDashboardReadSemantics`, live/snapshot discriminated types를 추가한다.
- `frontend/src/app/lib/read-model-adapters.ts`
  - Candidate update: live/snapshot instance dashboard path validators/builders와 display copy helper를 추가한다.
- `frontend/src/app/lib/read-model-contract-guard.ts`
  - Candidate update: `guardInstanceDashboardReadModel` and mode-specific source semantics checks를 추가한다.
- `frontend/src/app/lib/read-model-contract-fixtures.ts`
  - Candidate update: live/snapshot instance dashboard fixture, summary cap 밖 selected instance fixture, retention gap fixture, forbidden field fixture를 추가한다.
- `frontend/scripts/read-model-contract-guard.ts`
  - Candidate update: new guard assertions and static source checks for P8.
- `frontend/src/app/lib/use-api-resource.ts`
  - Candidate read-only reference: stale request guard pattern을 재사용한다.
- `frontend/src/app/components/ui/dialog.tsx`
  - Candidate read-only reference or reuse: wide modal/dialog shell에 사용할 수 있다.
- `frontend/src/app/components/ui/sheet.tsx`
  - Candidate update only if existing stored trend continues to use Sheet; live/snapshot dashboard detail should not remain narrow Sheet by default.

## Candidate Test File List

- `frontend/src/app/lib/read-model-contract-fixtures.ts`
  - Candidate update: `instanceDashboardLiveContractFixture`, `instanceDashboardSnapshotContractFixture`, missing/retention/not_observed/forbidden field variants.
- `frontend/src/app/lib/read-model-contract-guard.ts`
  - Candidate update: runtime guard assertions for mode/source/window/readSemantics/context/forbidden fields.
- `frontend/scripts/read-model-contract-guard.ts`
  - Candidate update: fixture assertions, path validation assertions, static source guard.
- `frontend/tsconfig.guard.json`
  - Candidate update only if new guard-only files need inclusion.
- `frontend/src/app/components/instance-dashboard-surface.test.tsx`
  - Candidate new file only if the project already has or intentionally adds a frontend component test harness. Do not add a new test framework just for this story without explicit decision.

## Suggested Verification Commands

```bash
cd frontend && npm run guard:read-model-contract
cd frontend && npm run typecheck
cd frontend && npm run build

rg -n "instances/.*/evidence|instances/.*/dashboard|snapshots/.*/instances/.*/dashboard|instance_dashboard_read_model|acceptedAtCutoffApplied|includesLateAcceptedMetrics|mayDifferFromStoredApplicationSnapshot|instanceEvidenceReconstructedFromMetrics|applicationSnapshotRecalculated|markerIsStateSource" \
  frontend/src/app/lib \
  frontend/src/app/components \
  frontend/scripts

rg -n "healthScore|rootCause|recoveryProof|instanceState|currentState|\\bstateCode\\b|current_15m|not_observed.*(정상|문제 없음|복구 완료)|(정상|문제 없음|복구 완료).*not_observed|InstanceDashboard.*instanceSummary\\.items|instanceSummary\\.items.*InstanceDashboard" \
  frontend/src/app/lib \
  frontend/src/app/components \
  frontend/scripts

rg -n "\\.sort\\(|\\.toSorted\\(|\\.reduce\\(" \
  frontend/src/app/components/instance-panels.tsx \
  frontend/src/app/components/dashboard.tsx \
  frontend/src/app/components/snapshot-detail-surface.tsx \
  frontend/src/app/components/snapshot-history-panel.tsx

git diff --check
git status --short
```

## References

- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/epics.md#Epic 13. Dashboard Source of Truth Realignment`
- `planning-artifacts/dashboard-source-of-truth-realignment-roadmap.md#FE-5. Instance Surface Split`
- `planning-artifacts/dashboard-source-of-truth-realignment-sequence.yaml#P8`
- `planning-artifacts/source-of-truth/instance-dashboard-read-model-mvp-source-of-truth.md`
- `planning-artifacts/source-of-truth/dashboard-snapshot-mvp-source-of-truth.md`
- `planning-artifacts/source-of-truth/dashboard-snapshot-retention-cleanup-source-of-truth.md`
- `planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html`
- `planning-artifacts/stories/13-7-frontend-snapshot-history-detail-realignment.md`
- `planning-artifacts/stories/13-8-backend-instance-dashboard-live-snapshot-mode-split.md`
- `_bmad/custom/project-context.md`

## Dev Agent Record

### Debug Log

- 2026-06-11T11:30:36+0900: `git status --short`로 기존 modified `13-6...` story와 untracked `dbml-error.log` 상태를 확인했다.
- 2026-06-11T11:30:36+0900: `implementation-artifacts/sprint-status.yaml`, Story 13.8, Instance Dashboard/Snapshot/Retention Source of Truth, HTML mockup을 read-only context로 확인했다.
- 2026-06-11T11:30:36+0900: HTML mockup은 구조/copy/layout/CSS 기준으로만 읽었고 PNG 파일은 열람/참조하지 않았다.
- 2026-06-11T11:30:36+0900: current frontend `instance-panels.tsx`, `read-model-types.ts`, `read-model-adapters.ts`, `read-model-contract-guard.ts`, fixtures/scripts를 확인해 candidate file list를 현재 코드 구조에 맞췄다.
- 2026-06-11T11:30:36+0900: 13.8 backend `InstanceDashboardReadModel`, controller route, service/test assertions를 확인해 live/snapshot contract field 이름을 story에 반영했다.
- 2026-06-11T11:51:56+0900: BMAD dev-story workflow에 따라 sprint-status와 Story 13.9 Status를 `in-progress`로 전환했다.
- 2026-06-11T12:07:40+0900: `InstanceDashboardReadModel` frontend type, live/snapshot path helper, mode-specific guard, live/snapshot/retention/forbidden/static fixtures를 추가했다.
- 2026-06-11T12:07:40+0900: `useInstanceView` union을 live dashboard, snapshot dashboard, stored trend로 분리하고 live/snapshot detail을 wide Dialog surface로 구현했다.
- 2026-06-11T12:07:40+0900: Snapshot Detail의 stored `instanceSummary.items[]`는 summary/trend projection source로만 표시하고, selected instance drill-down은 snapshot id를 포함한 snapshot Instance Dashboard action으로 연결했다.
- 2026-06-11T12:07:40+0900: Instance Snapshot Trend는 stored `dashboard_snapshots.read_model_json.instanceSummary.items[]` projection copy를 명시하고 Application Snapshot detail action과 Instance snapshot dashboard action을 분리했다.
- 2026-06-11T12:07:40+0900: `npm run guard:read-model-contract`, `npm run typecheck`, `npm run build`, static grep, Playwright DOM 확인을 통과했다. PNG 파일은 열람/비교/참조하지 않았다.

### Implementation Plan

- Frontend type/path/guard에 `InstanceDashboardReadModel` live/snapshot contract를 추가한다.
- Existing instance view state를 live dashboard, snapshot dashboard, stored trend로 분리한다.
- Live/snapshot detail은 HTML mockup 기준 wide modal surface로 구현하고, trend는 stored projection surface로 유지한다.
- Snapshot history/detail에서 selected instance drill-down 시 selected snapshot id를 포함한 snapshot endpoint를 사용한다.
- Missing/expired/retention gap은 live fallback 없이 safe source absence UX로 표시한다.

### Completion Notes

- `InstanceDashboardReadModel` live/snapshot frontend type, `/instances/{instanceId}/dashboard`와 `/snapshots/{snapshotId}/instances/{instanceId}/dashboard` path builder/validator, mode-specific guard를 추가했다.
- live/snapshot Instance Dashboard detail은 wide Dialog surface로 분리했고, Application state reference와 Read semantics, selected instance metrics, endpoint/resource/starter evidence, normalized endpoint table 순서로 렌더링한다.
- Snapshot Detail에서 selected snapshot id를 포함한 Instance Dashboard action을 제공하고, stored `instanceSummary.items[]` 부재는 snapshot mode endpoint hard failure로 쓰지 않는 copy/fixture를 추가했다.
- Stored Instance Snapshot Trend는 `dashboard_snapshots.read_model_json.instanceSummary.items[]` projection source로 유지하고, Application Snapshot detail action과 Instance snapshot dashboard action을 분리했다.
- `not_observed`, `metric_missing`, retention gap, forbidden instance decision field는 guard/static copy로 safe error 또는 evidence limitation UX에 수렴하게 했다.

## File List

- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/stories/13-9-frontend-instance-surface-split.md`
- `frontend/scripts/read-model-contract-guard.ts`
- `frontend/src/app/components/dashboard.tsx`
- `frontend/src/app/components/instance-dashboard-surface.tsx`
- `frontend/src/app/components/instance-panels.tsx`
- `frontend/src/app/components/snapshot-detail-surface.tsx`
- `frontend/src/app/components/snapshot-history-panel.tsx`
- `frontend/src/app/lib/read-model-adapters.ts`
- `frontend/src/app/lib/read-model-contract-fixtures.ts`
- `frontend/src/app/lib/read-model-contract-guard.ts`
- `frontend/src/app/lib/read-model-types.ts`

## Change Log

- 2026-06-11: Story 13.9 P8 Frontend Instance Surface Split story artifact를 생성하고 sprint-status를 `ready-for-dev`로 정렬했다.
- 2026-06-11: Story 13.9 구현을 시작하며 sprint-status와 story Status를 `in-progress`로 전환했다.
- 2026-06-11: live/snapshot Instance Dashboard type/path/guard/fixture와 wide dialog surface를 추가하고 stored trend/snapshot drill-down source boundary를 분리했다.
- 2026-06-11: frontend guard/typecheck/build/static grep/local DOM 검증을 완료하고 story와 sprint-status를 `review`로 전환했다.
- 2026-06-11: BMAD code review follow-up으로 nested forbidden instance state guard와 summary cap 밖 snapshot fixture를 보강하고 story/sprint-status를 `done`으로 전환했다.
