---
artifactType: story
storyId: "13.ui"
storyKey: "13-ui-dashboard-source-of-truth-surface-realignment"
epic: "Epic 13. Dashboard Source of Truth Realignment"
title: "Dashboard Source of Truth Surface Realignment"
architectureStyle: Traditional MVC
status: frontend-verified-doc-aligned
date: 2026-06-11
workType: frontend
implementationScope: "실제 Dashboard 화면의 Project rail, Application rail, Main surface, Snapshot/History, Instance wide modal, retention expired visual QA 재정렬"
productionCodeChangeThisContext: false
plannedProductionCodeChange: false
implementationStatus: "frontend-only complete; docs aligned to single wide modal contract"
sourceOfTruthMode: read-only
rollbackBoundary: "frontend dashboard layout/component/style/fixture/browser-visual-qa 변경 단위"
---

# Story 13.UI - Dashboard Source of Truth Surface Realignment

## Status

frontend-verified-doc-aligned

2026-06-11: 이 story artifact는 처음에는 BMAD create-story 흐름으로 생성됐으나, 현재는 완료된 frontend-only 구현 결과에 맞춘 문서 정렬 상태를 기록한다. 이번 문서 정렬에서는 production code, frontend implementation, backend tests, migration/schema, Source of Truth 문서, 완료 story 13.2~13.11 본문/status, 기존 untracked `dbml-error.log`를 수정하지 않는다.

2026-06-11 문서 정렬: frontend-only 구현과 검증은 이미 완료됐다. 이 story는 현재 MVP 계약을 반영한다. Instance Summary 상세 진입점은 SoT `openModal`에 대응하는 단일 wide modal뿐이며, Stored trend / projection trend / `InstanceTrendView` / narrow Sheet / `openTrend` / `openLiveDashboard` surface는 MVP에서 retire된 상태로 취급한다.

## Story 목표

frontend 구현자로서, 실제 Dashboard UI를 `planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html`의 정보 구조와 화면 구성에 맞게 재편하고 싶다.

그래야 사용자가 첫 화면에서 Project rail -> Application rail -> Application Dashboard main surface를 한 번에 이해하고, Snapshot/History, Instance detail wide modal, retention expired 상태가 Source of Truth mockup의 visual hierarchy와 source semantics를 충분히 따라간다는 evidence를 남길 수 있다.

## 배경 / 왜 지금 필요한지

Epic 13은 Story 13.2~13.11과 13-doc-0~13-doc-3까지 완료됐다. backend/read model 의미, 30분 window, snapshot slot, instance live/snapshot split, retention cleanup semantics는 이미 정렬됐다.

남은 관심사는 새로운 backend 의미 변경이 아니다. 실제 frontend dashboard surface가 Source of Truth mockup의 IA와 시각적 구성을 충분히 닮았는지, 그리고 운영자가 `Project -> Application -> Dashboard -> Snapshot -> Instance -> retention expired` 흐름을 화면에서 자연스럽게 읽을 수 있는지다.

현재 frontend는 이미 `frontend/src/app/components/dashboard.tsx`의 `ProjectRail`, `ApplicationRail`, `DashboardMain`, `frontend/src/app/components/snapshot-history-panel.tsx`, `frontend/src/app/components/snapshot-detail-surface.tsx`, `frontend/src/app/components/instance-panels.tsx`, `frontend/src/app/components/instance-dashboard-surface.tsx`로 구현되어 있다. 다음 구현은 이 기존 구조를 버리지 않고, Source of Truth mockup의 compact rail, neutral border, main 3-column surface, marker-first history, wide instance modal visual grammar에 맞춰 재배치/정돈하는 작업이다.

## Source of Truth

아래 문서는 read-only 기준이다. 이 story는 의미를 재정의하지 않고 구현 지침으로만 옮긴다.

1. `_bmad/custom/project-context.md`
2. `planning-artifacts/epics.md`
3. `planning-artifacts/dashboard-source-of-truth-realignment-roadmap.md`
4. `planning-artifacts/dashboard-source-of-truth-realignment-sequence.yaml`
5. `planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html`
6. `planning-artifacts/stories/13-5-frontend-application-dashboard-ia-realignment.md`
7. `planning-artifacts/stories/13-7-frontend-snapshot-history-detail-realignment.md`
8. `planning-artifacts/stories/13-9-frontend-instance-surface-split.md`
9. `planning-artifacts/stories/13-11-end-to-end-acceptance-and-demo-hardening.md`
10. `implementation-artifacts/epic-13-retro-2026-06-11.md`

## Source of Truth guardrail

- `source-of-truth-dashboard-mockup.html`은 실제 Dashboard surface의 정보 구조, visual hierarchy, compact spacing, neutral border, rail/main/modal composition 기준이다.
- HTML mockup의 prototype controls, 임시 JavaScript runtime, hard-coded demo data를 production UI에 그대로 복사하지 않는다.
- Source of Truth 문서와 기존 완료 story의 의미를 바꾸지 않는다.
- UI는 lifecycle state, endpoint priority, p95/p99, marker bucket, instance state, retention expired 의미를 재계산하지 않는다. 기존 server read model, adapter, guard semantics를 표시한다.
- `markerBucket`은 state source가 아니라 timeline 탐색 색인이다.
- Snapshot detail source는 `dashboard_snapshots.read_model_json`이며 live/current accepted bucket fallback으로 복원하지 않는다.
- Instance Dashboard live source는 `accepted_metric_buckets` recent 30 minutes이고, snapshot mode는 selected Application Snapshot row metadata + selected instance metric evidence reconstruction이다. stored Application Snapshot state/evidence를 검증하거나 대체하지 않는다.
- Retention expired/404/source absence는 live dashboard/current metric으로 보정하지 않는다.

## Acceptance Criteria

1. Given 실제 Dashboard route를 열면, Then 첫 viewport는 Source of Truth mockup처럼 Project rail, Application rail, Main dashboard surface의 3-column composition을 우선 보여준다. 레일은 scope 선택/탐색 surface이고 Application 판단을 대체하지 않는다.
2. Given desktop viewport에서 dashboard를 볼 때, Then Project rail은 project name, application count, setup/connection issue 후보, recent concern 0~1개를 compact row로 표시하고, Application rail은 lifecycle badge, accepted bucket freshness, starter connection, top concern 0~1개를 scan 가능하게 표시한다.
3. Given Main surface를 볼 때, Then Application Dashboard 상단에는 `mode`, `recent_30_minutes`, `accepted_metric_buckets`, `baseline not used`, project/application/environment, generated/window/bucket boundary가 mockup의 context/read semantics bar에 준하는 first-screen signal로 드러난다.
4. Given live Application Dashboard current surface를 읽을 때, Then data quality/freshness, lifecycle state hero, direct state reasons, attention/first look candidates, endpoint/resource evidence, metric detail, starter connection, instance entry 순서가 유지되고, metric visualization이 state/evidence보다 먼저 지배적으로 보이지 않는다.
5. Given Snapshot/History section을 볼 때, Then marker-first 30분 point 탐색, 14일 retention summary, date map, 48-slot day drilldown, selected snapshot summary가 mockup의 hierarchy에 맞게 표시된다. Operational event feed가 있다면 marker/date/slot 탐색보다 우선하지 않는다.
6. Given Snapshot detail을 열 때, Then live dashboard와 같은 visual skeleton을 공유하되 `mode=snapshot`, `source=dashboard_snapshots.read_model_json`, `capturedAt`, `currentWindowEndUtc`, `snapshotDetailRecalculates=false`, `markerIsStateSource=false`가 상단에 드러난다.
7. Given `captureReason=hourly_scheduled`가 표시될 때, Then persisted/API token은 바꾸지 않고 사용자-facing copy는 30분 scheduled 의미로 표시한다.
8. Given Instance summary에서 instance detail을 열 때, Then 좁은 Sheet가 아니라 mockup의 단일 wide modal/dialog로 표시한다. modal은 Application state reference, Read semantics, selected instance metrics, endpoint evidence, resource evidence, starter connection, normalized endpoint evidence table 순서 또는 동등한 순서를 제공한다.
9. Given Instance Dashboard snapshot mode를 열 때, Then selected Application Snapshot window 기준 evidence이며 late accepted metric 때문에 stored Application Snapshot과 다를 수 있다는 copy를 보여준다. Application Snapshot stored state/evidence를 대체하지 않는다.
10. Given MVP scope를 검토할 때, Then Instance Snapshot Trend / Stored trend / projection trend는 이 story의 필수 acceptance가 아니며 MVP 밖으로 이관된 항목으로 취급한다. 과거 instance evidence는 Snapshot/History에서 스냅샷을 선택한 뒤 snapshot-mode wide modal로 확인한다.
11. Given retention expired/404/source absence state를 볼 때, Then "보관 기간이 지났거나 저장된 snapshot/instance evidence를 찾을 수 없음" 계열 safe copy와 empty/error state를 보여주며 live/current fallback CTA를 만들지 않는다.
12. Given desktop, tablet, mobile viewport visual QA를 수행할 때, Then text overlap, clipped badges, overflowing rail rows, collapsed slot grid, modal header/body overlap, card-inside-card clutter가 없어야 한다.
13. Given implementation diff를 검토할 때, Then backend code/test, migration/schema, Source of Truth 문서, 완료 story 13.2~13.11 본문/status, 기존 untracked `dbml-error.log`가 변경되지 않았음이 확인된다.
14. Given 새 public component/helper 또는 동작이 바로 이해되지 않는 internal helper를 추가할 때, Then AGENTS.md 기준에 따라 한국어 JSDoc/comment를 사용한다.

## 구현 범위

- 실제 Vite SPA dashboard route의 레이아웃과 visual hierarchy를 Source of Truth mockup에 맞춰 재정렬한다.
- 기존 `ProjectRail`, `ApplicationRail`, `DashboardMain`, `SnapshotHistoryPanel`, `SnapshotDetailSurface`, `InstancePanels`, `InstanceDashboardSurface`를 우선 재사용/분리한다.
- mockup의 3-column composition을 desktop first-screen 기준으로 강화하고, tablet/mobile에서는 rail/main이 안정적으로 stack 되게 한다.
- neutral background, thin border, compact panel, 6px-ish radius, small uppercase section label, restrained badge grammar를 현재 Tailwind/shadcn/Radix component pattern 안에서 적용한다.
- Snapshot/History의 date map/slot grid, selected snapshot summary, expired/empty/error state가 실제 화면에서 mockup과 같은 사용 흐름으로 읽히도록 정리한다.
- Instance live/snapshot detail wide modal을 mockup 수준으로 polished하게 정돈하고, Stored trend/projection trend Sheet 진입점은 제공하지 않는다.
- 필요한 경우 frontend guard fixture/static guard에 visual/source semantics regression sentinel을 추가한다.

## 비범위 / 금지사항

- 이번 create-story 컨텍스트에서 production code를 수정하지 않는다.
- 다음 구현에서도 backend/read model 의미 변경, backend tests, migration/schema, cleanup physical delete rollout decision은 범위 밖이다.
- Source of Truth 문서 의미를 재정의하지 않는다.
- 완료 story 13.2~13.11 본문/status를 되돌리거나 재작성하지 않는다.
- `implementation-artifacts/sprint-status.yaml` 갱신은 이 story 파일 생성 범위에 포함하지 않는다. 필요하면 별도 명시 요청/후속 컨텍스트에서 처리한다.
- 새 backend endpoint, Next.js API route, browser token persistence, URL token parsing을 만들지 않는다.
- raw metric explorer, raw snapshot explorer, endpoint timeseries UI, arbitrary query UI를 만들지 않는다.
- lifecycle state, endpoint priority, p95/p99, root cause, health score, marker-as-state, retention fallback을 frontend에서 계산하지 않는다.
- 기존 untracked `dbml-error.log`를 수정, 삭제, stage하지 않는다.

## 예상 변경 파일 후보

주요 후보:

- `frontend/src/app/components/dashboard.tsx`
  - Project/Application rail와 Main dashboard composition, current surface order/spacing, rail row density, context bar, instance entry 재정돈.
- `frontend/src/app/components/snapshot-history-panel.tsx`
  - marker-first history/date map/slot grid, selected snapshot summary, expired/empty/error state visual hierarchy 정돈.
- `frontend/src/app/components/snapshot-detail-surface.tsx`
  - stored dashboard detail skeleton, source/recalculation guard copy, selected instance drill-down affordance 정돈.
- `frontend/src/app/components/instance-panels.tsx`
  - live/snapshot Instance Dashboard wide modal shell 정돈. Stored trend/projection trend Sheet는 MVP 밖으로 이관된 surface라 추가하지 않는다.
- `frontend/src/app/components/instance-dashboard-surface.tsx`
  - modal 내부 section order, read semantics, endpoint/resource/starter/normalized table layout 정돈.
- `frontend/src/app/lib/read-model-adapters.ts`
  - 표시용 copy, badge class, date/slot display helper만 필요한 범위로 보강.
- `frontend/src/app/lib/read-model-contract-guard.ts`
  - source/order/recalculation/forbidden field guard가 UI 재배치 후에도 유지되는지 보강.
- `frontend/src/app/lib/read-model-contract-fixtures.ts`
  - rail/main/snapshot/instance/retention visual semantics에 필요한 sentinel fixture 보강.
- `frontend/scripts/read-model-contract-guard.ts`
  - `.sort()`, `.toSorted()`, `.reduce()`, health/rootCause, live fallback, marker-as-state, hourly user-facing copy 회귀 static guard 보강.

Style 후보:

- `frontend/src/styles/tailwind.css`
- `frontend/src/styles/theme.css`
- colocated Tailwind class changes in the components above

Read-only reference/reuse 후보:

- `frontend/src/app/components/ui/dialog.tsx`
- `frontend/src/app/components/ui/sheet.tsx`
- `frontend/src/app/components/ui/button.tsx`
- `frontend/src/app/components/ui/badge.tsx`
- `frontend/src/app/components/ui/tooltip.tsx`
- `frontend/src/app/lib/use-api-resource.ts`
- `planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html`

## 구현 태스크 체크리스트

- [ ] 현재 dashboard first-screen을 캡처하고 mockup과 비교한다. (AC: 1~4, 12)
  - [ ] desktop/tablet/mobile viewport에서 Project rail, Application rail, Main surface의 현재 배치를 확인한다.
  - [ ] rail row, context bar, current surface section order, Snapshot/History, Instance modal, retention expired state의 차이를 짧은 implementation note로 정리한다.

- [ ] Project rail / Application rail을 mockup의 scan-first rail로 정돈한다. (AC: 1~2)
  - [ ] Project rail은 scope 선택과 setup/recent concern만 보여주고 dashboard 판단처럼 보이지 않게 한다.
  - [ ] Application rail은 metric freshness axis와 starter connection axis를 분리해 표시한다.
  - [ ] rail row 높이, left active indicator, muted meta, recent concern note가 desktop에서 안정적으로 보이게 한다.

- [ ] Main Application Dashboard surface를 mockup의 read order와 visual grammar로 정돈한다. (AC: 3~4)
  - [ ] context/read semantics bar를 first-screen anchor로 유지하고 과도한 tab/card hierarchy를 줄인다.
  - [ ] data quality, state hero, direct reasons, attention/first look, endpoint/resource evidence, metric detail, starter, instance entry 순서를 보존한다.
  - [ ] nested cards와 과한 rounded/marketing-style section을 줄이고 compact neutral panel을 유지한다.

- [ ] Snapshot / History surface를 mockup 수준으로 재정렬한다. (AC: 5~7, 11)
  - [ ] 14일 retention summary, 30분 scheduled point count, default preset, cleanup/expiry hint를 한눈에 보이게 한다.
  - [ ] date map과 48-slot grid가 mobile/desktop에서 overflow 없이 안정적으로 보이게 한다.
  - [ ] selected snapshot summary와 stored dashboard detail CTA가 marker-as-state로 오해되지 않게 한다.
  - [ ] expired/404/detail error는 live/current fallback 없이 safe copy로 수렴한다.

- [ ] Instance live/snapshot surface를 wide modal 중심으로 다듬는다. (AC: 8~10)
  - [ ] live/snapshot Instance Dashboard는 wide Dialog로만 열고, Stored trend/projection trend Sheet/surface는 추가하지 않는다.
  - [ ] modal sections는 Application state reference, Read semantics, selected metrics, endpoint evidence, resource evidence, starter connection, normalized endpoint table 순서로 읽힌다.
  - [ ] snapshot mode note는 selected snapshot row window, late metric possibility, no stored Application Snapshot override를 드러낸다.

- [ ] Guard/fixture/static check를 유지 또는 보강한다. (AC: 4~11, 13)
  - [ ] frontend guard가 `accepted_metric_buckets`, `dashboard_snapshots.read_model_json`, selected snapshot instance semantics를 계속 fail-closed로 검증한다.
  - [ ] static guard가 client-side reorder/recalculation, marker-as-state, hourly user-facing copy, health/rootCause, live/current fallback 후보를 잡는다.

- [ ] Visual QA와 verification evidence를 남긴다. (AC: 12~14)
  - [ ] desktop/tablet/mobile screenshot을 저장하거나 completion notes에 경로와 확인 결과를 남긴다.
  - [ ] browser visual QA에서 text overlap, rail overflow, modal clipping, slot grid wrapping, retention expired safe copy를 확인한다.
  - [ ] `git diff --check`, `git status --short --untracked-files=all`로 변경 범위를 확인한다.

## 검증 계획

필수 명령:

```bash
cd frontend && npm run guard:read-model-contract
cd frontend && npm run typecheck
cd frontend && npm run build
git diff --check
git status --short --untracked-files=all
```

정적 회귀 확인:

```bash
rg -n "current_15m|hourly scheduled|hourly snapshot|live/current fallback|current dashboard fallback|marker.*state|healthScore|rootCause|recoveryProof|endpoint timeseries|raw snapshot|raw metric|arbitrary query" frontend/src frontend/scripts
rg -n "\\.sort\\(|\\.toSorted\\(|\\.reduce\\(" frontend/src/app/components frontend/src/app/lib frontend/scripts
rg -n "dashboard_snapshots\\.read_model_json|accepted_metric_buckets|recent_30_minutes|markerIsStateSource|snapshotDetailRecalculates|acceptedAtCutoffApplied|includesLateAcceptedMetrics|mayDifferFromStoredApplicationSnapshot" frontend/src/app/components frontend/src/app/lib frontend/scripts
```

Scope guard:

- 변경 파일이 frontend dashboard surface와 guard/fixture/style 범위에 제한됐는지 확인한다.
- backend code/test, migration/schema, Source of Truth 문서, 완료 story 13.2~13.11, `dbml-error.log`가 변경되지 않았음을 completion notes에 적는다.

## Browser visual QA 계획

다음 구현 컨텍스트에서는 local Vite app을 실행하고, Browser/Playwright로 실제 렌더링을 확인한다.

권장 절차:

1. `cd frontend && npm run dev -- --host 127.0.0.1`로 dev server를 띄운다.
2. 인증이 필요한 경우 기존 local auth/smoke fixture 제약을 먼저 기록한다. 인증 fixture가 없으면 authenticated full-path를 과장하지 않는다.
3. 최소 viewport:
   - desktop: `1440x1000`
   - tablet: `1024x900`
   - mobile: `390x844`
4. 확인 항목:
   - Project rail / Application rail / Main 3-column composition
   - Application Dashboard context bar와 read semantics badges
   - current surface section order와 metric detail dominance 여부
   - Snapshot/History date map, 48-slot grid, selected snapshot summary
   - Snapshot detail `mode=snapshot` / source / recomputation false copy
   - Instance wide modal open/close, sticky header, normalized endpoint table overflow
   - retention expired/404/source absence safe copy
   - text overlap, clipped badges, horizontal scroll, modal body/header overlap 없음
5. 가능한 경우 screenshot artifact를 `implementation-artifacts/` 아래 별도 QA evidence로 저장한다. 저장 위치는 구현자가 변경 범위와 팀 규칙에 맞춰 결정하되, production code와 섞지 않는다.

## Known gap / Handoff

Full authenticated browser demo route/fixture가 없어 `Project -> Application -> Dashboard -> Snapshot -> Instance -> retention expired` path를 하나의 authenticated smoke로 닫는 evidence는 아직 없다.

이 story는 그 gap을 Source of Truth 재정의나 완료 story rollback으로 해결하지 않는다. 다음 구현 컨텍스트는 실제 frontend surface를 mockup의 IA/visual composition에 맞춘 뒤, 가능한 범위의 browser visual QA evidence를 남기고 authenticated full-path smoke가 막히면 fixture/runbook follow-up으로 분리한다.

## Dev Notes

- Active implementation baseline은 Traditional MVC + Service/Repository Layering이다. 이 story는 frontend surface story이며 backend MVC layer를 변경하지 않는다.
- Frontend root는 `frontend/`이고 React 18.3.1, Vite 6.3.5, TypeScript 5.8.3, Radix/shadcn-style UI, lucide-react를 사용한다. 새 dependency는 기본적으로 추가하지 않는다.
- Existing completion notes 기준 Story 13.11 frontend `guard:read-model-contract`, typecheck, build, focused/full backend regression, smoke focused bundle은 통과했다. 다만 full authenticated browser demo route/fixture evidence는 없다.
- HTML mockup의 visual grammar는 neutral background, white compact panels, thin borders, small uppercase section labels, stable grids, 6px-ish radius, wide instance modal이다.
- UI copy, docs/comments, test display name은 한국어를 기본으로 한다. 외부 API 이름, field name, schema version, source string은 원문을 유지한다.

## References

- `_bmad/custom/project-context.md`
- `planning-artifacts/epics.md#Epic 13. Dashboard Source of Truth Realignment`
- `planning-artifacts/dashboard-source-of-truth-realignment-roadmap.md`
- `planning-artifacts/dashboard-source-of-truth-realignment-sequence.yaml`
- `planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html`
- `planning-artifacts/stories/13-5-frontend-application-dashboard-ia-realignment.md`
- `planning-artifacts/stories/13-7-frontend-snapshot-history-detail-realignment.md`
- `planning-artifacts/stories/13-9-frontend-instance-surface-split.md`
- `planning-artifacts/stories/13-11-end-to-end-acceptance-and-demo-hardening.md`
- `implementation-artifacts/epic-13-retro-2026-06-11.md`

## Dev Agent Record

### Agent Model Used

TBD

### Debug Log References

- TBD

### Completion Notes List

- TBD

### File List

- TBD

## Change Log

| Date | Change |
|---|---|
| 2026-06-11 | 실제 Dashboard UI surface를 Source of Truth mockup의 IA와 visual composition에 맞춰 재편하기 위한 초기 story artifact를 생성했다. |
| 2026-06-11 | frontend-only 구현 완료 계약에 맞춰 단일 wide modal, Stored trend retire, AC#10 MVP 밖 이관을 문서화했다. |
