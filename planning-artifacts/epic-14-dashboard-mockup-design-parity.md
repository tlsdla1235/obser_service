---
artifactType: epic-planning-source
projectName: Spring Boot 운영 첫 화면 포털
architectureStyle: Traditional MVC
status: planning-source
date: 2026-06-11
sourcePolicy: latest-user-intent-wins
epic: "Epic 14. Dashboard Mockup Design Parity"
productionCodeChangeThisContext: false
sourceOfTruthMode: read-only
---

# Epic 14. Dashboard Mockup Design Parity

## Status

planning-source

이번 planning artifact는 Epic 13 이후 남은 dashboard mockup conformance 작업을 별도 Epic 14로 분리한다. 이 문서는 production code, frontend implementation, backend tests, migration/schema, Source of Truth 문서, 완료된 Epic 13 story 본문/status를 수정하지 않는다.

## 목적

실제 Vite Dashboard UI가 `planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html`의 IA, first-screen structure, rail density, compact neutral visual grammar, Snapshot/History picker, Instance wide modal, retention expired state를 최대한 동일하게 따르도록 strict mockup conformance 기준으로 정렬한다.

목표는 HTML/CSS/JS byte-level pixel-perfect clone이 아니다. 그러나 이는 "대충 비슷하면 됨"을 뜻하지 않는다. IA, layout hierarchy, visual density, spacing rhythm, neutral panel grammar, information ordering, wide modal, Snapshot/History picker, retention expired/source absence state는 strict conformance target이다. 운영자는 실제 Dashboard에서 Project rail -> Application rail -> Application Dashboard -> Snapshot/History -> Instance detail -> retention expired/source absence 흐름을 mockup과 같은 정보 구조와 시각 문법으로 읽을 수 있어야 한다.

Mockup과 실제 구현의 차이는 기본적으로 design choice가 아니라 deviation이다. 허용 deviation category, reviewer decision, follow-up owner가 명시되지 않은 discretionary redesign은 Epic 14 blocker다.

## Source Of Truth

아래 문서는 read-only 기준이다. Epic 14는 의미를 재정의하지 않고 mockup conformance 구현 지침으로만 옮긴다.

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

`planning-artifacts/stories/13-ui-dashboard-source-of-truth-surface-realignment.md`가 작업트리에 있으면 current single-modal contract companion 문서로 함께 확인한다. 이 파일은 Epic 14 source of truth가 아니며, Epic 14는 아래 4개 story split으로 새로 추적한다.

`planning-artifacts/source-of-truth/source-of-truth-dashboard-snapshot-picker.png`는 제거됐거나 제거 대상인 screenshot export이며 Source of Truth 기준으로 삼지 않는다. Epic 14의 기준은 HTML mockup이다.

## Epic 13 Handoff

Epic 13은 Source of Truth 의미와 read model/backend semantics를 닫았다. Epic 14는 완료된 Epic 13 story를 reopen하지 않고 `Aligns / Hardens / Visualizes` 관계로 이어받는다.

Epic 14가 반드시 유지할 Epic 13 guardrail:

- Application Dashboard live source는 `accepted_metric_buckets`와 `recent_30_minutes`다.
- Snapshot detail source는 `dashboard_snapshots.read_model_json`이며 live/current accepted bucket fallback으로 복원하지 않는다.
- `dashboard_snapshots.read_model_json`은 stored dashboard read model source다. Epic 14는 이 의미를 재정의하지 않는다.
- selected Application Snapshot 기준 Instance Dashboard snapshot mode는 selected snapshot row metadata와 selected instance `accepted_metric_buckets` evidence reconstruction을 분리해 표시한다.
- Instance Dashboard snapshot mode는 stored Application Snapshot state/evidence를 검증하거나 대체하지 않는다.
- `hourly_scheduled` persisted/API token은 rename하지 않고, 사용자-facing copy만 30분 scheduled 의미로 표시한다.
- `markerBucket`은 state source가 아니라 timeline 탐색 색인이다.
- Retention expired/404/source absence는 live dashboard/current metric/current instance evidence fallback 없이 safe empty/error state로 수렴한다.
- UI는 lifecycle state, endpoint priority, p95/p99, histogram percentile, resource pattern, marker state, instance health score, root cause, recovery proof를 계산하지 않는다.

## Non-Goals

- Pixel-perfect DOM/CSS byte-level clone. 단, 이 non-goal은 loose similarity나 discretionary redesign 허용을 뜻하지 않는다.
- Source of Truth 의미, read model, backend semantics 재정의.
- backend code/test, migration/schema, cleanup rollout decision.
- completed Epic 13 story 본문/status 수정 또는 reopen.
- `source-of-truth-dashboard-mockup.html`의 prototype controls, hard-coded JS demo data, temporary runtime을 production UI 요구사항으로 복사.
- Mockup보다 "더 예쁘게", "더 현대적으로", "더 카드스럽게", "더 마케팅스럽게" 보이도록 구조, density, 색상 문법, 정보 순서, modal/surface 형태를 임의 변경하는 discretionary redesign.
- raw metric explorer, raw snapshot explorer, endpoint timeseries, arbitrary query UI.
- browser token persistence, URL token parsing, 새 backend endpoint 또는 Next.js API route.
- 기존 untracked `dbml-error.log` 수정, 삭제, stage.

## Story Split

Epic 14는 story 파일을 즉시 4개 생성하지 않고, 이 planning source 안에 구현 가능한 story breakdown을 둔다. 구현 착수 시 필요하면 각 story를 `planning-artifacts/stories/14-x-*.md`로 승격한다.

권장 순서:

1. `14-1-design-parity-baseline-and-visual-guardrails`
2. `14-2-dashboard-shell-rails-and-live-surface-realignment`
3. `14-3-snapshot-history-detail-and-retention-surface-realignment`
4. `14-4-instance-wide-modal-and-end-to-end-visual-qa`

## Story 14.1 - Mockup Conformance Baseline And Deviation Guardrails

### 목표

실제 Dashboard UI와 HTML mockup 사이의 conformance gap을 구현 전에 baseline으로 잡고, Epic 14 전체가 따라야 할 no discretionary redesign guardrail, deviation log, conformance checklist, QA checklist, evidence 저장 방식을 정한다.

### Scope

- 현재 Vite Dashboard 첫 화면을 desktop/tablet/mobile viewport에서 캡처한다.
- mockup의 IA와 visual grammar를 production UI strict conformance checklist로 추출한다.
- Project rail, Application rail, Main live surface, Snapshot/History, Snapshot detail, Instance wide modal, retention expired state별 gap map을 작성한다.
- deviation log template을 작성하고 mockup과 다른 모든 차이를 allowed category/reviewer decision/follow-up owner로 추적하게 한다.
- Epic 14 visual QA checklist와 screenshot/evidence 저장 위치를 정한다.
- 14.2/14.3/14.4 handoff가 conformance checklist와 deviation log를 completion gate로 사용하게 한다.
- 기존 frontend guard가 Source semantics를 계속 보호하는지 확인하고, 필요한 경우 visual/source semantics sentinel fixture 후보를 정의한다.

### Non-Goals

- production UI layout 구현.
- backend/read model/API/test/schema 변경.
- mockup HTML/CSS/JS를 runtime으로 복사.
- authenticated full-path smoke gap을 이 story에서 닫았다고 주장.

### Acceptance Criteria

1. Given current Dashboard UI를 확인할 때, Then desktop `1440x1000`, tablet `1024x900`, mobile `390x844` 기준 baseline screenshot 또는 관찰 기록이 남는다.
2. Given mockup을 분석할 때, Then 3-column composition, compact rail rows, neutral border panels, small uppercase section labels, stable grids, restrained badges, wide modal, retention expired safe state가 production 적용 원칙으로 정리된다.
3. Given Epic 14 implementation을 시작할 때, Then pixel-perfect DOM/CSS byte-level clone은 non-goal이지만 IA, layout hierarchy, visual density, spacing rhythm, neutral panel grammar, information ordering, wide modal, Snapshot/History picker, retention expired state는 strict conformance target임이 checklist에 명시된다.
4. Given mockup과 실제 구현이 다르면, Then `implementation-artifacts/epic-14-dashboard-design-parity-qa/deviation-log.md` 또는 동등한 artifact에 mockup element, production element, reason, allowed category, reviewer decision, follow-up owner가 기록된다.
5. Given 차이가 production data/read model, responsive/mobile physical limit, prototype-only mockup runtime exclusion, accessibility/focus/ARIA 보강 중 하나에 속하지 않으면, Then 해당 차이는 blocker다.
6. Given screenshot 비교를 수행할 때, Then "비슷함"이 아니라 구조, 밀도, 순서, visual grammar, modal/surface form, Snapshot/History interaction 일치 여부를 mockup과 side-by-side로 판정한다.
7. Given guardrail을 검토할 때, Then `accepted_metric_buckets`, `dashboard_snapshots.read_model_json`, `recent_30_minutes`, selected snapshot instance semantics, retention no-fallback guardrail이 바뀌지 않는다.
8. Given authenticated full-path fixture가 없으면, Then `Project -> Application -> Dashboard -> Snapshot -> Instance -> retention expired` browser smoke evidence gap이 known gap으로 유지된다.

### Candidate Files

- `planning-artifacts/epic-14-dashboard-mockup-design-parity.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/README.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/conformance-checklist.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/deviation-log.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/no-discretionary-redesign-checklist.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/handoff-gates.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/*.png`
- `frontend/src/app/lib/read-model-contract-fixtures.ts`
- `frontend/src/app/lib/read-model-contract-guard.ts`
- `frontend/scripts/read-model-contract-guard.ts`

### Verification / Browser Visual QA Plan

- `cd frontend && npm run guard:read-model-contract`
- `cd frontend && npm run typecheck`
- `cd frontend && npm run build`
- Local Vite app을 실행하고 desktop/tablet/mobile screenshot을 남긴다.
- Browser QA는 mockup과 실제 Dashboard screenshot을 side-by-side로 비교하고, Project rail, Application rail, Main surface, Snapshot picker, Snapshot detail, Instance wide modal, retention expired state별로 conformance 판정을 남긴다.
- Browser QA는 text overlap, clipped badges, rail overflow, nested card clutter, horizontal scroll, modal clipping, slot grid wrapping을 확인한다.
- Responsive/mobile adaptation은 물리적 배치 제약과 allowed deviation category를 기록한다.
- 인증 fixture가 없어 full authenticated smoke를 못 닫으면 completion notes에 제약을 남긴다.

## Story 14.2 - Dashboard Shell, Rails, And Live Surface Realignment

### 목표

Project rail, Application rail, Application Dashboard live surface가 mockup의 first-screen structure와 compact neutral visual grammar를 strict conformance 기준으로 따르도록 실제 Vite UI를 정렬한다.

### Scope

- Desktop first viewport에서 Project rail, Application rail, Main dashboard surface의 3-column composition을 강화한다.
- Project rail은 scope selection과 setup/recent concern hint만 보여주고 application 판단처럼 보이지 않게 한다.
- Application rail은 lifecycle badge, accepted bucket freshness, starter connection, top concern 0~1개를 scan-first row로 표시한다.
- Main live surface는 context/read semantics bar, data quality/freshness, lifecycle state hero, direct reasons, attention/first look, endpoint/resource evidence, metric detail, starter connection, instance entry 순서를 유지한다.
- Tailwind/shadcn/Radix 패턴 안에서 neutral background, thin border, compact panel, 6px-ish radius, restrained badge, stable grid sizing을 적용한다.

### Non-Goals

- backend/read model shape 변경.
- state, endpoint priority, resource pattern, p95/p99, histogram percentile, root cause 계산.
- Snapshot/History detail 구현 변경. 필요한 visual anchor만 보존한다.
- Instance modal 세부 QA. Story 14.4에서 닫는다.

### Acceptance Criteria

1. Given Dashboard route를 desktop viewport에서 열면, Then Project rail, Application rail, Main surface가 첫 viewport의 주요 composition으로 보인다.
2. Given Project rail을 볼 때, Then project name, application count, setup/connection issue 후보, recent concern 0~1개가 compact row로 표시되고 dashboard 판단을 대체하지 않는다.
3. Given Application rail을 볼 때, Then lifecycle badge, accepted bucket freshness, starter connection, top concern 0~1개가 scan 가능하며 heartbeat와 metric freshness가 하나의 health 판단으로 합쳐지지 않는다.
4. Given Main live surface 상단을 볼 때, Then `mode=live`, `recent_30_minutes`, `accepted_metric_buckets`, `baseline not used`, project/application/environment, window/bucket boundary가 first-screen signal로 드러난다.
5. Given live surface를 읽을 때, Then data quality/freshness, lifecycle state, direct reasons, attention/first look candidates가 metric visualization보다 먼저 읽힌다.
6. Given responsive viewport를 확인할 때, Then rail/main stack이 안정적이고 rail row text, badges, context bar가 겹치거나 잘리지 않는다.
7. Given mockup과 다른 shell/rail/main surface 선택이 있으면, Then 14.1 deviation log에 allowed category, reviewer decision, follow-up owner가 기록되어야 하며 discretionary redesign은 blocker다.

### Candidate Files

- `frontend/src/app/components/dashboard.tsx`
- `frontend/src/styles/tailwind.css`
- `frontend/src/styles/theme.css`
- `frontend/src/app/components/ui/badge.tsx`
- `frontend/src/app/components/ui/button.tsx`
- `frontend/src/app/lib/read-model-adapters.ts`
- `frontend/src/app/lib/read-model-contract-fixtures.ts`
- `frontend/scripts/read-model-contract-guard.ts`

### Verification / Browser Visual QA Plan

- `cd frontend && npm run guard:read-model-contract`
- `cd frontend && npm run typecheck`
- `cd frontend && npm run build`
- Static grep for forbidden recalculation/reorder:

```bash
rg -n "current_15m|healthScore|rootCause|recoveryProof|marker.*state|live/current fallback|current dashboard fallback" frontend/src/app frontend/scripts
rg -n "\\.sort\\(|\\.toSorted\\(|\\.reduce\\(" frontend/src/app/components frontend/src/app/lib frontend/scripts
```

- Browser visual QA:
  - desktop `1440x1000`: 3-column composition, rail density, context/read semantics bar.
  - tablet `1024x900`: rails stack without clipped rows.
  - mobile `390x844`: context badges wrap cleanly and no horizontal page scroll.

## Story 14.3 - Snapshot, History, Detail, And Retention Surface Realignment

### 목표

Snapshot/History picker, stored Snapshot detail, retention expired/source absence surface가 mockup의 marker-first hierarchy와 no-fallback visual grammar를 strict conformance 기준으로 따르도록 정렬한다.

### Scope

- Snapshot/History section은 marker-first 30분 dashboard point 탐색으로 보인다.
- 14일 retention summary, 30분 scheduled point count, default preset, cleanup/expiry hint를 first-pass scanning 정보로 배치한다.
- Date map과 하루 48-slot drilldown grid를 desktop/mobile에서 안정적으로 보이게 한다.
- Selected snapshot summary와 stored dashboard detail CTA가 `markerBucket`을 state source로 오해시키지 않게 한다.
- Snapshot detail은 live dashboard와 같은 visual skeleton을 공유하되 `mode=snapshot`, `source=dashboard_snapshots.read_model_json`, captured/window/recalculation flags를 상단에 고정한다.
- Expired/404/source absence state는 live/current fallback 없이 safe copy와 empty/error state로 표시한다.

### Non-Goals

- persisted `hourly_scheduled` token rename.
- snapshot query/backend/repository/cleanup semantics 변경.
- raw snapshot explorer 또는 operational event feed 우선 UI.
- Instance modal detail 변경. selected snapshot instance entry affordance만 유지한다.

### Acceptance Criteria

1. Given Snapshot/History section을 볼 때, Then marker/date/slot 탐색이 primary surface이고 operational event feed가 있으면 secondary/collapsible context로만 보인다.
2. Given 14일 history를 볼 때, Then retention days, 30분 scheduled point count, default preset, cleanup/expiry hint가 mockup의 retention summary처럼 scan 가능하다.
3. Given date map과 slot grid를 조작할 때, Then `currentWindowEndUtc` 30분 slot identity가 유지되고 `capturedAt`/`generatedAt`은 provenance/tie-breaker 표시로만 보인다.
4. Given `captureReason=hourly_scheduled`를 표시할 때, Then 사용자-facing label은 30분 scheduled 의미이며 persisted/API token은 바꾸지 않는다.
5. Given Snapshot detail을 열 때, Then top surface에 `mode=snapshot`, `source=dashboard_snapshots.read_model_json`, `snapshotDetailRecalculates=false`, `markerIsStateSource=false`, captured/window 정보가 보인다.
6. Given retention expired/404/source absence를 볼 때, Then live dashboard/current accepted bucket/current instance evidence fallback CTA나 copy가 없다.
7. Given responsive QA를 수행할 때, Then date cell, slot cell, selected snapshot summary, stored note, error state text가 겹치거나 잘리지 않는다.
8. Given mockup과 다른 Snapshot/History interaction, detail skeleton, retention expired surface 선택이 있으면, Then 14.1 deviation log에 allowed category, reviewer decision, follow-up owner가 기록되어야 하며 discretionary redesign은 blocker다.

### Candidate Files

- `frontend/src/app/components/snapshot-history-panel.tsx`
- `frontend/src/app/components/snapshot-detail-surface.tsx`
- `frontend/src/app/components/dashboard.tsx`
- `frontend/src/app/lib/read-model-adapters.ts`
- `frontend/src/app/lib/read-model-contract-fixtures.ts`
- `frontend/src/app/lib/read-model-contract-guard.ts`
- `frontend/scripts/read-model-contract-guard.ts`
- `frontend/src/styles/tailwind.css`
- `frontend/src/styles/theme.css`

### Verification / Browser Visual QA Plan

- `cd frontend && npm run guard:read-model-contract`
- `cd frontend && npm run typecheck`
- `cd frontend && npm run build`
- Static grep:

```bash
rg -n "hourly scheduled|hourly snapshot|marker.*state|current dashboard fallback|live/current fallback|raw snapshot|endpoint timeseries|arbitrary query" frontend/src/app frontend/scripts
rg -n "dashboard_snapshots\\.read_model_json|currentWindowEndUtc|markerIsStateSource|snapshotDetailRecalculates" frontend/src/app frontend/scripts
```

- Browser visual QA:
  - desktop: retention summary, date map, 48-slot grid, selected snapshot summary.
  - mobile: slot grid wraps without text clipping.
  - detail/error: snapshot detail source flags and expired safe state are visible without fallback CTA.

## Story 14.4 - Instance Wide Modal And End-To-End Visual QA

### 목표

Instance live/snapshot surface를 mockup의 single wide modal/detail grammar로 다듬고, Epic 14 전체 Dashboard mockup conformance를 browser visual QA evidence와 deviation disposition으로 마감한다.

### Scope

- Instance live/snapshot Dashboard detail은 좁은 Sheet가 아니라 wide Dialog/modal 또는 동등한 wide detail surface로 표시한다.
- Modal section order는 context note, Application state reference, Read semantics, selected instance metrics, endpoint evidence, resource evidence, starter connection, normalized endpoint evidence table을 따른다.
- Snapshot mode note는 selected Application Snapshot row window, late accepted metric 가능성, stored Application Snapshot override 금지를 짧게 드러낸다.
- Stored Instance Snapshot Trend / projection trend / Stored trend surface는 MVP 범위 밖으로 이관됐으므로 Instance Summary나 modal 안에 추가하지 않는다. 과거 instance evidence는 Snapshot/History에서 selected snapshot을 고른 뒤 snapshot-mode wide modal로 본다.
- Dashboard shell, live surface, snapshot/history/detail, instance modal, retention expired/source absence를 desktop/tablet/mobile에서 visual QA한다.
- 가능하면 authenticated smoke fixture/runbook을 사용해 full path를 검증한다. fixture가 없으면 known gap을 유지하고 과장하지 않는다.

### Non-Goals

- Instance Dashboard backend endpoint/read model 변경.
- Instance health score, root cause, recovery proof, independent instance lifecycle state.
- `dashboard_snapshots.read_model_json.instanceSummary.items[]`를 Instance Dashboard snapshot detail 필수 source로 만드는 변경.
- Instance Snapshot Trend / projection trend / Stored trend UI를 MVP 필수 surface로 되살리는 변경.
- authenticated full-path smoke fixture 부재를 임시 browser token persistence나 URL token parsing으로 우회.

### Acceptance Criteria

1. Given Instance summary에서 detail을 열 때, Then wide modal/dialog가 열리고 narrow Sheet 중심 detail로 보이지 않는다.
2. Given live Instance Dashboard를 볼 때, Then `mode=live`, `source=accepted_metric_buckets`, `recent_30_minutes`, `applicationStateRef.lifecycleOwner=application`이 보이고 instance top-level lifecycle state를 만들지 않는다.
3. Given selected Application Snapshot 기준 Instance Dashboard snapshot mode를 볼 때, Then selected snapshot row window 기준 evidence, late accepted metric 가능성, no stored Application Snapshot override copy가 보인다.
4. Given Instance Dashboard snapshot mode가 retention gap 또는 missing metric을 만날 때, Then `metric_missing`/`not_observed_in_window` 계열 limitation UX로 수렴하고 live/current evidence로 보정하지 않는다.
5. Given Instance Summary와 Instance modal을 검토할 때, Then Stored trend / projection trend / `InstanceTrendView` / narrow Sheet / `openTrend` / `openLiveDashboard` 진입점이 없고, 과거 instance evidence는 Snapshot/History -> snapshot-mode wide modal 경로로만 열린다.
6. Given end-to-end visual QA를 수행할 때, Then Project rail, Application rail, live dashboard, Snapshot/History, Snapshot detail, Instance modal, retention expired/source absence가 desktop/tablet/mobile에서 text overlap, clipped badges, modal clipping, horizontal scroll 없이 확인된다.
7. Given authenticated full-path fixture가 없으면, Then `Project -> Application -> Dashboard -> Snapshot -> Instance -> retention expired` path를 하나의 authenticated browser smoke로 닫은 evidence가 없다는 known gap을 completion notes에 유지한다.
8. Given Epic 14 visual QA를 마감할 때, Then Project rail, Application rail, Main surface, Snapshot picker, Snapshot detail, Instance wide modal, retention expired state별 conformance 판정과 deviation log disposition이 남아야 하며 unresolved non-allowed deviation은 blocker다.

### Candidate Files

- `frontend/src/app/components/instance-panels.tsx`
- `frontend/src/app/components/instance-dashboard-surface.tsx`
- `frontend/src/app/components/snapshot-detail-surface.tsx`
- `frontend/src/app/components/snapshot-history-panel.tsx`
- `frontend/src/app/components/dashboard.tsx`
- `frontend/src/app/components/ui/dialog.tsx`
- `frontend/src/app/components/ui/sheet.tsx`
- `frontend/src/app/lib/read-model-adapters.ts`
- `frontend/src/app/lib/read-model-contract-fixtures.ts`
- `frontend/src/app/lib/read-model-contract-guard.ts`
- `frontend/scripts/read-model-contract-guard.ts`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/README.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/*.png`

### Verification / Browser Visual QA Plan

- `cd frontend && npm run guard:read-model-contract`
- `cd frontend && npm run typecheck`
- `cd frontend && npm run build`
- Static grep:

```bash
rg -n "healthScore|rootCause|recoveryProof|instanceState|\\bstateCode\\b|not_observed.*(정상|문제 없음|복구 완료)|(정상|문제 없음|복구 완료).*not_observed" frontend/src/app frontend/scripts
rg -n "acceptedAtCutoffApplied|includesLateAcceptedMetrics|mayDifferFromStoredApplicationSnapshot|applicationSnapshotRecalculated|instanceEvidenceReconstructedFromMetrics|markerIsStateSource" frontend/src/app frontend/scripts
```

- Browser visual QA:
  - desktop `1440x1000`, tablet `1024x900`, mobile `390x844`.
  - Instance modal open/close, sticky header, modal body scroll, normalized endpoint table overflow.
  - Snapshot mode modal copy and retention gap safe state.
  - Full path smoke if authenticated fixture exists. If not, record the missing fixture/runbook as follow-up.

## Cross-Story Verification

Every Epic 14 implementation story should finish with:

```bash
cd frontend && npm run guard:read-model-contract
cd frontend && npm run typecheck
cd frontend && npm run build
git diff --check
git status --short --branch --untracked-files=all
```

Scope guard for every story:

- Changed files stay in frontend dashboard surface, frontend guard/fixture/static guard, or explicit QA evidence artifacts.
- backend code/test, migration/schema, Source of Truth docs, completed Epic 13 story body/status, and `dbml-error.log` remain untouched.
- If browser visual QA cannot execute the authenticated full path, completion notes say exactly which path was not proven.
- 14.1 conformance checklist and deviation log are updated or explicitly confirmed unchanged.
- Mockup과 다른 layout, density, visual grammar, ordering, modal/surface form, Snapshot/History interaction은 allowed category와 reviewer decision 없이 merge/complete하지 않는다.

## Known Gap / Handoff

Full authenticated browser demo route/fixture가 없어 `Project -> Application -> Dashboard -> Snapshot -> Instance -> retention expired` path를 하나의 authenticated smoke로 닫은 evidence는 아직 없다.

Epic 14는 이 gap을 Source of Truth 재정의나 Epic 13 rollback으로 해결하지 않는다. Epic 14 implementation은 가능한 browser visual QA evidence를 남기되, authenticated fixture가 없으면 full-path smoke는 fixture/runbook follow-up으로 분리한다.

## Epic 14 Completion Definition

Epic 14는 아래 조건을 만족할 때 done으로 볼 수 있다.

- 실제 Vite Dashboard 첫 화면이 mockup의 Project rail / Application rail / Main surface composition과 compact neutral visual grammar를 따른다.
- Snapshot/History picker와 Snapshot detail이 marker-first 30분 point 탐색과 stored dashboard restore surface로 읽힌다.
- Instance live/snapshot detail이 single wide modal/detail surface로 읽히고 Stored trend/projection trend surface가 MVP에 다시 등장하지 않는다.
- Retention expired/404/source absence가 live/current fallback 없이 safe state로 보인다.
- Project rail, Application rail, Main surface, Snapshot picker, Snapshot detail, Instance wide modal, retention expired state별 conformance checklist가 완료된다.
- 모든 deviation은 `implementation-artifacts/epic-14-dashboard-design-parity-qa/deviation-log.md` 또는 동등한 artifact에 allowed category, reviewer decision, follow-up owner와 함께 남고, non-allowed deviation은 없다.
- Frontend guard/typecheck/build와 browser visual QA evidence가 남는다.
- authenticated full-path smoke gap이 닫히지 않았다면 과장 없이 known gap으로 유지된다.
