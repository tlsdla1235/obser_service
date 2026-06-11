---
artifactType: story
storyId: "14.2"
storyKey: "14-2-dashboard-shell-rails-and-live-surface-realignment"
epic: "Epic 14. Dashboard Mockup Design Parity"
title: "Dashboard Shell, Rails, And Live Surface Realignment"
architectureStyle: Traditional MVC
status: done
date: 2026-06-11
workType: frontend
implementationScope: "Project rail, Application rail, and Application Dashboard live surface strict mockup conformance"
productionCodeChangeThisContext: false
plannedProductionCodeChange: true
sourceOfTruthMode: read-only
rollbackBoundary: "frontend dashboard shell/layout/component/style/guard/evidence changes only"
---

# Story 14.2 - Dashboard Shell, Rails, And Live Surface Realignment

## Status

done

2026-06-11: BMAD create-story 흐름으로 Epic 14의 두 번째 story artifact를 생성한다. 이번 컨텍스트에서는 production code, frontend implementation, backend code/tests, migration/schema, Source of Truth mockup HTML, 완료된 Epic 13 story 본문/status, 기존 untracked `dbml-error.log`를 수정하지 않는다.
2026-06-11: BMAD code review 기준 blocking finding 없음으로 확인하고 Story 14.2를 done으로 닫는다. 기대 UI/UX는 code/static/auth-blocked shell evidence 기준 구현됐지만, authenticated dashboard strict visual conformance는 fixture 부재로 coverage gap으로 남긴다.

## Story

frontend 구현자로서, 실제 Vite Dashboard의 Project rail, Application rail, Main live dashboard surface를 `planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html`의 first-screen structure와 compact neutral visual grammar에 맞춰 재정렬하고 싶다.

그래야 운영자가 desktop 첫 viewport에서 Project scope -> Application scan -> Application Dashboard live read model을 mockup과 같은 정보 구조로 읽고, 후속 14.3 Snapshot/History와 14.4 Instance wide modal handoff가 같은 main flow hierarchy를 깨지 않게 이어질 수 있다.

## Background

Epic 13은 `accepted_metric_buckets`, `recent_30_minutes`, `dashboard_snapshots.read_model_json`, selected snapshot instance semantics, retention expired no-fallback 의미를 이미 닫았다. Epic 14는 이 의미를 재정의하지 않고 실제 Vite Dashboard UI를 HTML mockup의 IA와 visual grammar에 맞추는 conformance epic이다.

Story 14.1은 `implementation-artifacts/epic-14-dashboard-design-parity-qa/` 아래에 conformance checklist, no-discretionary-redesign checklist, deviation log, gap map, handoff gates, side-by-side notes를 만들었다. 14.1 기준 authenticated browser fixture가 없어 실제 authenticated Dashboard first screen은 browser evidence로 닫히지 않았고, 이는 14.2에서도 과장하면 안 되는 known gap이다.

HTML mockup은 `.app-grid` desktop 3-column shell, `.rail-item` compact row, `.panel` thin-border surface, `.section-label` small uppercase label, restrained badge/chip, `info-grid` context/read semantics bar, `state-strip`, `metric-grid`, same-flow Snapshot/History anchor, wide `.modal` handoff를 보여준다. Pixel-perfect DOM/CSS byte-level clone은 non-goal이지만, IA, first-screen layout hierarchy, rail density, spacing rhythm, neutral thin-border panel grammar, information ordering, Snapshot/History 위치감, wide modal handoff anchor는 strict target이다.

현재 `frontend/src/app/components/dashboard.tsx`에는 `ProjectRail`, `ApplicationRail`, `DashboardMain`이 있고, `DashboardMain`은 `current`와 `snapshots`를 `Tabs`로 분리한다. 이 tab split이 mockup의 same-flow hierarchy와 충돌하면 구현자는 이를 단순 design choice로 넘기지 말고 14.1 deviation log에 allowed category, reviewer decision, follow-up owner를 기록하거나 blocker로 처리해야 한다.

## Source of Truth

아래 문서는 read-only 기준이다. 구현자는 의미를 재정의하지 않고 14.2 구현 지침과 completion gate로만 사용한다.

1. `_bmad/custom/project-context.md`
2. `planning-artifacts/epics.md`
3. `planning-artifacts/epic-14-dashboard-mockup-design-parity.md`
4. `planning-artifacts/stories/14-1-design-parity-baseline-and-visual-guardrails.md`
5. `planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html`
6. `implementation-artifacts/sprint-status.yaml`
7. `implementation-artifacts/epic-14-dashboard-design-parity-qa/README.md`
8. `implementation-artifacts/epic-14-dashboard-design-parity-qa/conformance-checklist.md`
9. `implementation-artifacts/epic-14-dashboard-design-parity-qa/deviation-log.md`
10. `implementation-artifacts/epic-14-dashboard-design-parity-qa/no-discretionary-redesign-checklist.md`
11. `implementation-artifacts/epic-14-dashboard-design-parity-qa/mockup-principles-and-gap-map.md`
12. `implementation-artifacts/epic-14-dashboard-design-parity-qa/handoff-gates.md`
13. `implementation-artifacts/epic-14-dashboard-design-parity-qa/source-semantics-sentinel-review.md`
14. `implementation-artifacts/epic-14-dashboard-design-parity-qa/side-by-side-desktop-1440x1000.md`
15. `implementation-artifacts/epic-14-dashboard-design-parity-qa/side-by-side-tablet-1024x900.md`
16. `implementation-artifacts/epic-14-dashboard-design-parity-qa/side-by-side-mobile-390x844.md`
17. `planning-artifacts/stories/13-ui-dashboard-source-of-truth-surface-realignment.md`가 있으면 seed/handoff 자료로만 참고한다.

`planning-artifacts/source-of-truth/source-of-truth-dashboard-snapshot-picker.png`는 Source of Truth가 아니다. 14.2의 기준은 HTML mockup이다.

## Acceptance Criteria

1. Given 구현자가 14.2를 시작할 때, When `git status --short --branch --untracked-files=all`을 실행하면, Then 기존 untracked `dbml-error.log`와 seed `planning-artifacts/stories/13-ui-dashboard-source-of-truth-surface-realignment.md`는 수정, 삭제, stage하지 않는 보호 대상으로 확인된다.
2. Given desktop viewport `1440x1000`에서 authenticated Dashboard route를 열 때, When Project rail, Application rail, Main surface가 렌더링되면, Then mockup의 `.app-grid`처럼 Project rail / Application rail / Main live surface가 첫 viewport의 주 hierarchy로 보인다.
3. Given Project rail을 볼 때, When project row가 렌더링되면, Then project name, application count, setup/connection issue 후보, recent concern 0~1개만 compact scope row로 보이고 application 판단이나 triage surface를 대체하지 않는다.
4. Given Application rail을 볼 때, When application row가 렌더링되면, Then lifecycle badge, accepted bucket freshness, starter connection, top concern 0~1개가 scan 가능하며 accepted bucket metric axis와 heartbeat control-plane axis가 하나의 health 판단으로 합쳐지지 않는다.
5. Given Main live surface 상단을 볼 때, When context/read semantics bar가 렌더링되면, Then `mode=live`, `recent_30_minutes`, `accepted_metric_buckets`, `baseline not used`, project/application/environment, generated/window/bucket boundary가 first-screen signal로 드러난다.
6. Given Main live surface를 위에서 아래로 읽을 때, When section order를 확인하면, Then context/read semantics bar -> data quality/freshness -> lifecycle state -> direct reasons -> attention/first look -> endpoint/resource evidence -> metric detail -> starter connection -> instance entry 순서가 유지된다.
7. Given metric visualization이 표시될 때, When first viewport와 scroll 시작 영역을 비교하면, Then metric grid, percentile, histogram, chart/detail이 data quality, lifecycle state, direct reasons, attention/first look, endpoint/resource evidence보다 먼저 지배적으로 보이지 않는다.
8. Given Snapshot/History는 14.3에서 본격 구현될 때, When 14.2가 shell/main flow를 바꾸면, Then mockup의 same-flow Snapshot/History 위치감과 후속 handoff anchor를 깨지 않고 `SnapshotHistoryPanel` 진입이 별도 앱처럼 밀려나지 않는다.
9. Given Instance modal은 14.4에서 본격 구현될 때, When 14.2가 instance entry를 조정하면, Then instance entry는 Application Dashboard 판단을 대체하지 않는 detail entry로 보이고 wide modal handoff anchor를 유지한다.
10. Given 현재 `DashboardMain` tab split이 mockup same-flow hierarchy와 충돌할 때, When reviewer가 14.2 completion을 판단하면, Then `deviation-log.md`에 allowed category 1~4, reviewer decision, follow-up owner가 있어야 하며 category 밖 차이는 blocker다.
11. Given mockup과 다른 layout, density, visual grammar, ordering, modal/surface form, Snapshot/History interaction이 남을 때, When completion notes를 작성하면, Then 이를 design choice로 쓰지 않고 `implementation-artifacts/epic-14-dashboard-design-parity-qa/deviation-log.md`에 기록하거나 blocker로 남긴다.
12. Given tablet `1024x900`과 mobile `390x844` viewport에서 확인할 때, When rail/main이 stack 또는 adapted layout으로 바뀌면, Then row text, badges, context bar, section labels가 겹치거나 잘리지 않고 unavoidable physical adaptation은 category 2 deviation으로 기록된다.
13. Given authenticated fixture가 없을 때, When browser visual QA를 수행하면, Then 가능한 auth-blocked 또는 fixture-less evidence 범위만 기록하고 full authenticated `Project -> Application -> Dashboard -> Snapshot -> Instance -> retention expired` browser path를 닫았다고 주장하지 않는다.
14. Given source semantics guard를 검토할 때, When frontend guard와 static grep을 실행하면, Then `accepted_metric_buckets`, `recent_30_minutes`, `dashboard_snapshots.read_model_json`, selected snapshot instance semantics, retention expired no-fallback guardrail을 재정의하지 않는다.
15. Given implementation diff를 검토할 때, When `git diff --check`와 final `git status --short --branch --untracked-files=all`을 확인하면, Then backend code/tests, migration/schema, Source of Truth mockup HTML, completed Epic 13 story body/status, `dbml-error.log`가 변경되지 않았음이 확인된다.

## Tasks/Subtasks

- [x] 시작 상태와 보호 대상을 확인한다. (AC: 1, 15)
  - [x] `git status --short --branch --untracked-files=all`을 실행하고 기존 untracked `dbml-error.log`와 seed `13-ui...` story를 보호 대상으로 기록한다.
  - [x] 이번 story 구현 diff가 frontend dashboard shell/layout/component/style/guard/evidence 범위에 머무는지 확인한다.
  - [x] Source of Truth mockup HTML과 completed Epic 13 story는 read-only로 유지한다.

- [x] 14.1 QA gate를 구현 시작 checklist로 연결한다. (AC: 10, 11, 13, 14)
  - [x] `conformance-checklist.md`의 공통 원칙, Mockup Visual Grammar, Application Dashboard Ordering, Source Semantics Guard를 구현 체크리스트로 사용한다.
  - [x] `no-discretionary-redesign-checklist.md`를 읽고 layout/density/order/visual grammar 임의 개선을 금지한다.
  - [x] mockup과 다른 차이가 생기면 `deviation-log.md`에 allowed category, reviewer decision, follow-up owner를 기록한다.
  - [x] `handoff-gates.md`의 14.2 Gate를 completion notes에 인용하고 각 항목을 `conformant`, `allowed deviation`, `blocker` 중 하나로 판정한다.

- [x] Project rail을 mockup의 compact scope rail로 정렬한다. (AC: 2, 3, 12)
  - [x] `ProjectRail` row를 mockup `.rail-item`처럼 compact padding, 2px-ish active indicator, small meta, 0~1개 note hierarchy로 다듬는다.
  - [x] project name, application count, setup/connection issue 후보, recent concern 0~1개만 보이게 하고 application lifecycle 판단처럼 보이는 요소를 제거하거나 낮춘다.
  - [x] Project 등록/credential/setup affordance가 first-screen rail density를 깨면 위치, 접힘, deviation 필요 여부를 판단한다.
  - [x] empty/auth/error/search states도 rail density와 neutral thin-border grammar를 유지하게 한다.

- [x] Application rail을 mockup의 compact application scan rail로 정렬한다. (AC: 2, 4, 12)
  - [x] `ApplicationRail` row를 lifecycle badge, starter badge, application/environment title, last accepted bucket, heartbeat, top concern 0~1개 순서로 압축한다.
  - [x] 현재 2-cell mini grid가 rail density를 낮추거나 nested card clutter로 보이면 mockup badge/meta/note hierarchy로 재정렬한다.
  - [x] accepted bucket freshness와 starter connection을 같은 health score로 합치지 않고 별도 meta/chip/copy로 유지한다.
  - [x] tablet/mobile에서 row text와 badge가 clipped 되지 않도록 stable widths/wrapping을 확인한다.

- [x] Desktop first-screen shell을 mockup의 3-column hierarchy로 맞춘다. (AC: 2, 12)
  - [x] 현재 `grid-cols-12` shell이 desktop에서 `Project rail 2 / Application rail 3 / Main 7` hierarchy를 유지하는지 확인하고 mockup의 `minmax(170px,2fr) minmax(260px,3fr) minmax(0,7fr)`에 준하는 밀도로 조정한다.
  - [x] rail border, background, main background, main padding을 neutral thin-border grammar로 맞춘다.
  - [x] tablet에서는 mockup처럼 rail 2-column + main next row 또는 reviewer-approved adaptation을 사용한다.
  - [x] mobile에서는 Project -> Application -> Main order를 보존하고 horizontal page scroll이 없음을 확인한다.

- [x] Main live context/read semantics bar를 first-screen anchor로 고정한다. (AC: 5, 6, 14)
  - [x] `DashboardContext` 또는 동등한 top panel을 mockup `.panel.strong`과 `info-grid`에 준하는 compact context panel로 정렬한다.
  - [x] `mode=live`, `recent_30_minutes`, `accepted_metric_buckets`, `baseline not used`, project/application/environment, generated/window/bucket boundary를 상단에서 바로 보이게 한다.
  - [x] `schemaVersion` 등 production field는 필요하면 유지하되, mockup보다 정보 우선순위가 흐트러지면 category 1 deviation 또는 reviewer decision을 남긴다.
  - [x] snapshot-only flags인 `snapshotDetailRecalculates=false`, `markerIsStateSource=false`를 live first-screen에서 어떻게 표시할지는 mockup/source semantics와 14.1 gate 기준으로 결정한다.

- [x] Main live surface order를 mockup 기준으로 재정렬한다. (AC: 6, 7)
  - [x] `DataQualityFreshnessStrip`이 lifecycle보다 먼저 읽히는지 확인하고, stale/down/sample/minimum request/baseline not used가 compact하게 보이게 한다.
  - [x] `LifecycleStateHero`를 mockup `state-strip`처럼 direct state summary와 action이 함께 읽히는 thin-border hero로 유지한다.
  - [x] `DirectStateReasonsPanel`, `AttentionAndFirstLookPanel`, `EndpointResourceEvidencePanel`이 metric detail보다 먼저 렌더링되게 한다.
  - [x] `MetricDetailSection`은 request/error/slow share, source-scoped percentile, histogram을 state/evidence 이후의 detail로 낮춘다.
  - [x] `StarterConnectionStrip`은 control-plane only copy를 유지하고 accepted bucket freshness/state와 섞지 않는다.
  - [x] `InstancesPanel`은 Application Dashboard 판단을 대체하지 않는 detail entry로 보이고 wide modal handoff가 자연스럽게 이어지게 한다.

- [x] Snapshot/History same-flow handoff anchor를 보존한다. (AC: 8, 10, 11)
  - [x] 현재 `Tabs` 분리가 mockup의 same-flow hierarchy와 충돌하는지 desktop/tablet/mobile screenshot으로 판정한다.
  - [x] 14.2에서 tab split을 해소하거나, 유지해야 한다면 `deviation-log.md`에 allowed category, reviewer decision, follow-up owner를 남긴다.
  - [x] `SnapshotHistoryPanel`의 상세 재정렬은 14.3 scope로 넘기되, Main surface 안에서 Snapshot/History 위치감과 CTA anchor가 사라지지 않게 한다.
  - [x] Snapshot/History가 operational event feed, raw snapshot explorer, endpoint timeseries, arbitrary query UI처럼 보이지 않도록 handoff copy를 확인한다.

- [x] Guard/static sentinel을 유지하거나 필요한 최소 후보를 추가한다. (AC: 14)
  - [x] `guard:read-model-contract`가 Application Dashboard source/order/recalculation/forbidden field semantics를 계속 fail-closed로 감시하는지 확인한다.
  - [x] tab-only flow가 Snapshot/History same-flow anchor를 숨기는 경우, 14.1 `source-semantics-sentinel-review.md`의 14.2 candidate에 따라 static/browser sentinel 추가 여부를 검토한다.
  - [x] UI 재배치 중 `.sort()`, `.toSorted()`, `.reduce()`로 server order/rank를 다시 만들지 않는다. 필요한 display-only 변환은 기존 adapter/guard 의미를 확인한다.

- [x] Browser visual QA evidence를 남긴다. (AC: 2, 3, 4, 5, 6, 7, 8, 12, 13)
  - [x] desktop `1440x1000`, tablet `1024x900`, mobile `390x844`에서 current Vite Dashboard screenshot과 observation note를 남긴다.
  - [x] mockup reference와 side-by-side로 Project rail, Application rail, Main surface, Snapshot/History handoff anchor를 판정한다.
  - [x] text overlap, clipped badges, rail overflow, nested card clutter, metric dominance, horizontal scroll, context bar wrapping을 확인한다.
  - [x] authenticated fixture가 없으면 auth-blocked 범위와 missing authenticated path를 known gap으로 분리한다.

## Candidate Files

- `frontend/src/app/components/dashboard.tsx`
  - `ProjectRail`, `ApplicationRail`, `DashboardMain`, `DashboardContext`, live surface section order, `Tabs` split decision, `InstancesPanel`, starter/credential placement 후보.
- `frontend/src/app/components/snapshot-history-panel.tsx`
  - 14.2에서는 same-flow Snapshot/History handoff anchor 확인 후보. 본격 visual/detail 재정렬은 14.3 scope다.
- `frontend/src/app/components/instance-panels.tsx`
  - wide modal handoff가 14.2 shell/instance entry와 맞는지 확인 후보. 본격 modal QA는 14.4 scope다.
- `frontend/src/app/components/instance-dashboard-surface.tsx`
  - instance entry copy/source semantics handoff 확인 후보. 본격 section polish는 14.4 scope다.
- `frontend/src/app/components/ui/tabs.tsx`
  - `DashboardMain`의 tab split을 유지, 제거, 또는 same-flow anchor로 대체할 때 확인 후보.
- `frontend/src/app/components/ui/badge.tsx`
- `frontend/src/app/components/ui/button.tsx`
- `frontend/src/app/components/ui/dialog.tsx`
- `frontend/src/app/components/ui/sheet.tsx`
- `frontend/src/styles/tailwind.css`
- `frontend/src/styles/theme.css`
- `frontend/src/app/lib/read-model-adapters.ts`
  - display copy/badge/date/source label helper만 필요한 범위로 조정한다. source/order/recalculation 의미를 바꾸지 않는다.
- `frontend/src/app/lib/read-model-contract-fixtures.ts`
- `frontend/src/app/lib/read-model-contract-guard.ts`
- `frontend/scripts/read-model-contract-guard.ts`
  - source semantics guard 또는 static sentinel 보강 후보.
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/conformance-checklist.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/deviation-log.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/no-discretionary-redesign-checklist.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/handoff-gates.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/side-by-side-desktop-1440x1000.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/side-by-side-tablet-1024x900.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/side-by-side-mobile-390x844.md`
- `planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html` (read-only reference)

## Verification Commands

```bash
git status --short --branch --untracked-files=all

cd frontend && npm run guard:read-model-contract
cd frontend && npm run typecheck
cd frontend && npm run build

rg -n "current_15m|hourly scheduled|hourly snapshot|live/current fallback|current dashboard fallback|marker.*state|healthScore|rootCause|recoveryProof|endpoint timeseries|raw snapshot|raw metric|arbitrary query" frontend/src/app frontend/scripts
rg -n "\\.sort\\(|\\.toSorted\\(|\\.reduce\\(" frontend/src/app/components frontend/src/app/lib frontend/scripts
rg -n "dashboard_snapshots\\.read_model_json|accepted_metric_buckets|recent_30_minutes|baseline not used|markerIsStateSource|snapshotDetailRecalculates|acceptedAtCutoffApplied|includesLateAcceptedMetrics|mayDifferFromStoredApplicationSnapshot" frontend/src/app/components frontend/src/app/lib frontend/scripts
rg -n "Tabs|TabsList|TabsTrigger|SnapshotHistoryPanel|ProjectRail|ApplicationRail|DashboardMain|DashboardContext" frontend/src/app/components/dashboard.tsx

git diff --check
ruby -e 'require "yaml"; YAML.load_file("implementation-artifacts/sprint-status.yaml"); puts "yaml ok"'
git status --short --branch --untracked-files=all
```

Static grep 결과가 guard negative fixture, explanatory comment, prototype-only reference일 수 있다. 구현자는 hit를 무시하지 말고 production user-facing/source semantics regression인지 분류해 completion notes에 남긴다.

## Browser Visual QA Plan

1. `cd frontend && npm run dev -- --host 127.0.0.1`로 local Vite app을 실행한다.
2. Browser/Playwright로 Dashboard route를 연다. 인증 fixture가 없으면 접근 가능한 auth-blocked 화면과 차단 사유를 분리 기록한다.
3. 최소 viewport:
   - desktop: `1440x1000`
   - tablet: `1024x900`
   - mobile: `390x844`
4. Desktop `1440x1000`에서 확인한다.
   - Project rail / Application rail / Main surface 3-column hierarchy.
   - Project rail compact scope row, 2px-ish active indicator, application count, setup/recent concern 0~1개.
   - Application rail lifecycle badge, accepted bucket freshness, starter connection, top concern 0~1개.
   - Main context/read semantics bar와 `mode=live`, `recent_30_minutes`, `accepted_metric_buckets`, `baseline not used`, generated/window/bucket boundary.
   - Main live surface order와 metric detail dominance 여부.
   - Snapshot/History same-flow handoff anchor와 Instance wide modal handoff anchor가 깨지지 않았는지.
5. Tablet `1024x900`에서 rail/main adaptation을 확인한다.
   - rail row text, badges, context bar, section labels clipping 없음.
   - Project -> Application -> Main reading order 보존.
6. Mobile `390x844`에서 mobile QA warning을 확인한다.
   - horizontal page scroll 없음.
   - badges wrap, compact rows readable, context info cells wrap or stack cleanly.
   - 14.1에서 auth-blocked baseline `body.scrollWidth=504` 경고가 있었으므로 authenticated/fixture state에서 재확인한다.
7. Evidence naming convention은 `implementation-artifacts/epic-14-dashboard-design-parity-qa/handoff-gates.md`를 따른다.
8. 각 viewport side-by-side note에는 `conformant`, `allowed deviation`, `blocker` 중 하나를 영역별로 기록한다.
9. Full authenticated browser path를 실행하지 못하면 completion notes와 Known Gap에 그대로 남긴다.

## Known Gap

- 14.1 기준 full authenticated browser fixture/runbook이 없어 `Project -> Application -> Dashboard -> Snapshot -> Instance -> retention expired` path를 하나의 browser smoke로 닫은 evidence는 없다. 14.2는 이 gap을 닫지 못하면 known gap으로 유지해야 하며, authenticated dashboard conformance를 증명했다고 쓰지 않는다.
- 14.1 mobile auth-blocked baseline에서 `body.scrollWidth=504`, `viewportWidth=390` horizontal overflow warning이 있었다. 이는 authenticated Dashboard overflow proof가 아니지만 14.2 mobile QA에서 반드시 재확인해야 한다.
- Snapshot/History detail, retention expired/source absence surface는 14.3 scope다. 14.2는 same-flow handoff anchor와 tab/deviation gate만 깨지 않게 한다.
- Instance live/snapshot wide modal full QA는 14.4 scope다. 14.2는 instance entry와 wide modal handoff anchor만 보존한다.

## Dev Notes

- Active implementation baseline은 Traditional MVC + Service/Repository Layering이다. 14.2는 frontend surface story이며 backend MVC layer, read model API, persistence, migration을 변경하지 않는다.
- Frontend root는 `frontend/`이고 React 18.3.1, Vite 6.3.5, TypeScript 5.8.3, Tailwind 4.1.12, Radix/shadcn-style UI, lucide-react를 사용한다. 새 dependency는 기본적으로 추가하지 않는다.
- `frontend/src/app/components/dashboard.tsx`는 shell, rail, main live surface의 primary 작업 후보 파일이다. 현재 `DashboardMain`은 `Tabs`로 `current`와 `snapshots`를 분리한다.
- `SnapshotHistoryPanel`은 marker-first 30분 dashboard point 탐색 UI를 갖고 있으나 14.2에서는 main same-flow handoff anchor 중심으로만 다룬다.
- `InstancePanels`는 live/snapshot dashboard를 `DialogContent` width `min(1120px, calc(100vw - 2rem))`로 열고 stored trend는 `Sheet`로 분리한다. 14.2는 instance entry가 이 wide modal handoff를 깨지 않는지만 본다.
- HTML mockup의 prototype controls, `scenarioSelect`, hard-coded `scenarios`, temporary render runtime, endpoint sort/limit demo controls는 production requirement가 아니다.
- Mockup visual grammar는 `#fafafa` page background, white panels, thin `#e5e5e5` border, compact 12px panel padding, 6px-ish radius, 11px uppercase labels, restrained neutral/semantic badges, stable grids다. 이를 "더 예쁘게" 재해석하지 않는다.
- Application Dashboard live source는 `accepted_metric_buckets` + `recent_30_minutes`다. Snapshot detail source는 `dashboard_snapshots.read_model_json`이다. Retention/source absence는 live/current fallback 없이 처리한다.
- UI는 lifecycle state, endpoint priority, p95/p99, histogram percentile, root cause, health score, marker-as-state, retention fallback을 client에서 계산하지 않는다.
- Public component, helper, 새 JSDoc/comment를 추가할 때는 AGENTS.md 지침에 따라 한국어 주석을 사용한다.

## References

- `_bmad/custom/project-context.md`
- `planning-artifacts/epics.md#Epic 14. Dashboard Mockup Design Parity`
- `planning-artifacts/epic-14-dashboard-mockup-design-parity.md`
- `planning-artifacts/stories/14-1-design-parity-baseline-and-visual-guardrails.md`
- `planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html`
- `implementation-artifacts/sprint-status.yaml`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/README.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/conformance-checklist.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/deviation-log.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/no-discretionary-redesign-checklist.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/mockup-principles-and-gap-map.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/handoff-gates.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/source-semantics-sentinel-review.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/side-by-side-desktop-1440x1000.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/side-by-side-tablet-1024x900.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/side-by-side-mobile-390x844.md`
- `planning-artifacts/stories/13-ui-dashboard-source-of-truth-surface-realignment.md` (read-only seed/handoff)

## Dev Agent Record

### Agent Model Used

GPT-5 Codex (BMAD dev-story)

### Debug Log References

- 2026-06-11 `git status --short --branch --untracked-files=all`: 기존 modified `implementation-artifacts/sprint-status.yaml`, untracked `dbml-error.log`, seed `planning-artifacts/stories/13-ui-dashboard-source-of-truth-surface-realignment.md`, untracked Story 14.2를 확인했다. `dbml-error.log`와 seed story는 수정/삭제/stage하지 않았다.
- BMAD `bmad-dev-story` workflow customization, `_bmad/custom/project-context.md`, Story 14.2, Epic 14 planning source, Story 14.1, HTML Source of Truth mockup, sprint status, 14.1 QA artifacts를 읽고 구현 기준으로 사용했다.
- `frontend/src/app/components/dashboard.tsx`: Project/Application rail density, desktop/tablet/mobile shell grid, DashboardContext context/read semantics panel, main live surface same-flow order, Snapshot/History tab split 제거, Instance wide modal handoff anchor를 조정했다.
- `frontend/scripts/read-model-contract-guard.ts`: DashboardMain이 tab-only flow를 다시 도입하지 않고 live surface anchor order를 유지하는 static sentinel을 추가했다.
- `frontend/src/app/components/nav.tsx`: mobile auth-blocked route에서 전역 nav가 page horizontal scroll을 만들던 문제를 wrap 가능한 header layout으로 조정했다.
- Browser QA: `cd frontend && npm run dev -- --host 127.0.0.1`로 Vite dev server를 실행하고 Browser viewport override로 desktop `1440x1000`, tablet `1024x900`, mobile `390x844`를 확인했다. 인증 fixture가 없어 `/dashboard`는 auth-blocked shell까지만 관찰했다.
- Browser QA evidence: `current-14-2-dashboard-auth-blocked-desktop-1440x1000.png`, `current-14-2-dashboard-auth-blocked-tablet-1024x900.png`, `current-14-2-dashboard-auth-blocked-mobile-390x844.png`, `browser-14-2-dashboard-shell-rails-and-live-surface-realignment-observations.json`.
- `cd frontend && npm run guard:read-model-contract`: 통과. `read-model contract guard fixtures passed`.
- `cd frontend && npm run typecheck`: 통과.
- `cd frontend && npm run build`: 통과. Vite build `built in 1.22s`.
- Static grep forbidden terms: hits는 guard negative fixture/assertion, source semantics comment, marker-as-index explanatory comment로 분류했다. 새 production fallback/raw explorer/health score/root cause/recovery proof UI는 추가하지 않았다.
- Static grep `.sort()`/`.toSorted()`/`.reduce()`: 대상 범위 hit 없음.

### Completion Notes List

- Project rail은 mockup `.rail-item`처럼 2px active indicator, compact padding, project name, app count/setup signal meta, recent concern 0~1개 note로 압축했다.
- Application rail은 lifecycle badge, starter badge, app/environment title, accepted bucket freshness, heartbeat, top concern note 순서로 재정렬했다. accepted bucket metric axis와 starter heartbeat control-plane axis는 별도 meta/copy로 유지했다.
- Desktop shell은 `minmax(170px,2fr) minmax(260px,3fr) minmax(0,7fr)`에 준하는 3-column grid로 바꾸고, tablet은 rail 2-column + main next row, mobile은 Project -> Application -> Main stack으로 맞췄다.
- DashboardContext는 `.panel.strong` + `info-grid`에 준하는 compact top anchor로 바꾸고 `mode=live`, `recent_30_minutes`, `accepted_metric_buckets`, `baseline not used`, project/application/environment, generated/window/bucket boundary를 first-screen signal로 배치했다.
- Live main surface order는 context/read semantics -> data quality/freshness -> lifecycle state -> direct reasons -> attention/first look -> endpoint/resource evidence -> metric detail -> starter connection -> instance entry -> Snapshot/History handoff로 고정했다.
- Metric detail은 state/evidence 이후로 내려 metric visualization이 first-screen 판단을 지배하지 않게 했다. Source-scoped percentile table은 mobile page width를 밀지 않도록 내부 overflow container에 둔다.
- 기존 `Tabs` split은 mockup same-flow hierarchy와 충돌한다고 판단해 제거했다. `SnapshotHistoryPanel`은 Main flow 하단 handoff anchor로 유지하고, 상세 visual 재정렬은 14.3 scope로 남겼다. 이로 인해 tab split deviation은 남기지 않았다.
- Instance entry는 Application Dashboard 판단을 대체하지 않는 detail entry로 다듬고, `Wide modal` CTA가 기존 wide `DialogContent` handoff로 이어지게 했다.
- 14.2 Handoff Gate 판정: Project rail `conformant by code/static, browser coverage gap for authenticated data`; Application rail `conformant by code/static, browser coverage gap`; Desktop shell `conformant in auth-blocked shell`; Main live order `conformant by code/static guard, browser coverage gap`; source semantics `conformant`; Browser full authenticated path `known coverage gap`.
- 새 allowed deviation은 추가하지 않았다. `deviation-log.md`에는 tab split 제거와 authenticated fixture gap, mobile auth-blocked overflow 해소를 기록했다.
- Browser QA는 auth-blocked shell 기준으로 desktop 3-column, tablet rail 2-column + main next row, mobile stack과 horizontal scroll 없음(`bodyScrollWidth=390`, `viewportWidth=390`)을 확인했다. authenticated `Project -> Application -> Dashboard -> Snapshot -> Instance -> retention expired` path는 fixture/runbook 부재로 증명하지 않았다.
- Backend code/tests, migration/schema, Source of Truth mockup HTML, completed Epic 13 story body/status, 기존 untracked `dbml-error.log`, seed `13-ui...` story는 수정하지 않았다.

### File List

- `frontend/src/app/components/dashboard.tsx`
- `frontend/src/app/components/nav.tsx`
- `frontend/scripts/read-model-contract-guard.ts`
- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/stories/14-2-dashboard-shell-rails-and-live-surface-realignment.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/deviation-log.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/side-by-side-desktop-1440x1000.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/side-by-side-tablet-1024x900.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/side-by-side-mobile-390x844.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/source-semantics-sentinel-review.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/guard-14-2-20260611-1800.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/browser-14-2-dashboard-shell-rails-and-live-surface-realignment-observations.json`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/current-14-2-dashboard-auth-blocked-desktop-1440x1000.png`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/current-14-2-dashboard-auth-blocked-tablet-1024x900.png`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/current-14-2-dashboard-auth-blocked-mobile-390x844.png`

## Change Log

| Date | Change |
|---|---|
| 2026-06-11 | Epic 14 Story 14.2 dashboard shell, rails, and live surface realignment story artifact를 생성하고 ready-for-dev로 전환했다. |
| 2026-06-11 | Project/Application rail, Dashboard shell, live main same-flow order, Snapshot/History handoff anchor, mobile nav overflow, static sentinel, Browser QA evidence를 구현하고 review로 전환했다. |
| 2026-06-11 | BMAD code review 결과 blocking finding 없음으로 확인하고 authenticated visual QA coverage gap을 유지한 채 done으로 전환했다. |
