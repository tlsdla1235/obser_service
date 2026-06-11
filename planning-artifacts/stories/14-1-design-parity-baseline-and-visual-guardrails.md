---
artifactType: story
storyId: "14.1"
storyKey: "14-1-design-parity-baseline-and-visual-guardrails"
epic: "Epic 14. Dashboard Mockup Design Parity"
title: "Mockup Conformance Baseline And Deviation Guardrails"
architectureStyle: Traditional MVC
status: done
date: 2026-06-11
workType: planning-qa
implementationScope: "Dashboard mockup conformance baseline, deviation guardrails, conformance checklist, deviation log, gap map, QA checklist, and browser visual evidence convention"
productionCodeChangeThisContext: false
plannedProductionCodeChange: false
sourceOfTruthMode: read-only
rollbackBoundary: "story artifact, sprint status, and non-production QA evidence artifacts only"
---

# Story 14.1 - Mockup Conformance Baseline And Deviation Guardrails

## Status

done

2026-06-11: BMAD create-story 흐름으로 Epic 14의 첫 번째 story artifact를 생성한다. 이번 컨텍스트에서는 production code, frontend implementation, backend code/tests, migration/schema, Source of Truth mockup, 완료된 Epic 13 story 본문/status, 기존 untracked `dbml-error.log`를 수정하지 않는다.
2026-06-11: Story 14.1을 단순 design parity baseline이 아니라 `source-of-truth-dashboard-mockup.html` 기준의 strict mockup conformance baseline, explicit deviation log, no discretionary redesign gate로 강화한다.
2026-06-11: BMAD code review의 AC7 traceability finding을 반영해 gap map에 allowed category / coverage-gap 분류를 보강하고 story를 done으로 닫는다.

## Story

frontend/QA 구현자로서, 실제 Vite Dashboard UI와 `source-of-truth-dashboard-mockup.html` 사이의 mockup conformance gap을 구현 전에 desktop/tablet/mobile baseline으로 잡고 싶다.

그래야 Epic 14 후속 story가 자유로운 디자인 개선이 아니라 HTML mockup의 IA, layout hierarchy, visual density, spacing rhythm, compact neutral panel grammar, information ordering, wide modal, Snapshot/History picker, retention expired/source absence state를 strict conformance target으로 삼고, Epic 13에서 닫은 source/read-model semantics를 깨지 않는 visual QA checklist와 evidence 방식을 공유할 수 있다.

## Source of Truth

아래 문서는 read-only 기준이다. 이 story는 의미를 재정의하지 않고 baseline/guardrail 작성 지침으로만 옮긴다.

1. `_bmad/custom/project-context.md`
2. `planning-artifacts/epics.md`
3. `planning-artifacts/epic-14-dashboard-mockup-design-parity.md`
4. `implementation-artifacts/sprint-status.yaml`
5. `planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html`
6. `planning-artifacts/dashboard-source-of-truth-realignment-roadmap.md`
7. `planning-artifacts/dashboard-source-of-truth-realignment-sequence.yaml`
8. `planning-artifacts/stories/13-5-frontend-application-dashboard-ia-realignment.md`
9. `planning-artifacts/stories/13-7-frontend-snapshot-history-detail-realignment.md`
10. `planning-artifacts/stories/13-9-frontend-instance-surface-split.md`
11. `planning-artifacts/stories/13-11-end-to-end-acceptance-and-demo-hardening.md`
12. `implementation-artifacts/epic-13-retro-2026-06-11.md`
13. `planning-artifacts/stories/13-ui-dashboard-source-of-truth-surface-realignment.md`가 있으면 seed/handoff 자료로만 참고한다.

`planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html`은 Epic 14 UI/UX conformance 기준이다. `planning-artifacts/source-of-truth/source-of-truth-dashboard-snapshot-picker.png`는 제거됐거나 제거 대상인 screenshot export이며, Source of Truth 기준으로 삼지 않는다.

## Background

Epic 13은 Application Dashboard live source, Snapshot detail source, Instance live/snapshot split, retention expired no-fallback 의미를 닫았다. 13.11 completion evidence는 frontend `guard:read-model-contract`, typecheck, build, focused/full backend regression, smoke focused bundle 통과로 남아 있다. 단, full authenticated browser demo route/fixture가 없어 `Project -> Application -> Dashboard -> Snapshot -> Instance -> retention expired` path를 하나의 browser smoke로 닫은 evidence는 없다.

Epic 14는 이 backend/read-model 의미를 다시 열지 않는다. 목표는 실제 Vite Dashboard 첫 화면을 `source-of-truth-dashboard-mockup.html`의 정보 구조, first-screen composition, rail density, compact neutral visual grammar, Snapshot/History picker, Instance wide modal, retention expired/source absence state에 최대한 동일하게 맞추는 strict mockup conformance다.

Epic 14의 목표는 "대충 비슷한 디자인"이나 "더 나은 디자인"을 만드는 것이 아니다. mockup과 실제 구현의 차이는 기본적으로 design choice가 아니라 deviation이며, 허용 범주와 reviewer decision이 남아야 한다.

14.1은 구현 story가 아니다. 후속 14.2~14.4가 안전하게 움직일 수 있도록 현재 화면 baseline, mockup에서 추출한 적용 원칙, 영역별 gap map, conformance checklist, no discretionary redesign checklist, deviation log template, visual QA checklist, screenshot/evidence 저장 위치, source semantics guard 확인 방식을 먼저 고정한다.

## Aligns / Hardens / Visualizes

### Aligns

- `13-5-frontend-application-dashboard-ia-realignment`: Application Dashboard live surface의 context/read semantics, freshness, lifecycle state, direct reasons, attention/first look, metric detail, starter, instance entry 순서를 visual baseline에 포함한다.
- `13-7-frontend-snapshot-history-detail-realignment`: Snapshot/History가 marker-first 30분 point 탐색이며 Snapshot detail source가 `dashboard_snapshots.read_model_json`임을 visual checklist에 포함한다.
- `13-9-frontend-instance-surface-split`: Instance live/snapshot detail은 wide modal/detail surface로 보고, stored trend는 `dashboard_snapshots.read_model_json.instanceSummary.items[]` projection으로 분리한다.
- `13-11-end-to-end-acceptance-and-demo-hardening`: 통과한 guard/typecheck/build/regression evidence를 기반으로 삼되, authenticated full-path browser smoke gap은 계속 known gap으로 남긴다.

### Hardens

- `accepted_metric_buckets`, `recent_30_minutes`, `dashboard_snapshots.read_model_json`, selected snapshot instance semantics, retention expired no-fallback guardrail을 visual QA checklist에 반복 기입한다.
- UI가 lifecycle state, endpoint priority, p95/p99, marker bucket, instance state, root cause, health score를 재계산하지 않는지 기존 frontend guard와 static check 후보로 확인한다.
- `source-of-truth-dashboard-mockup.html`의 prototype controls, hard-coded JS demo data, temporary runtime, endpoint sort/limit demo controls를 production requirement로 만들지 않는다.
- No discretionary redesign guardrail을 추가해 "더 예쁘게", "더 현대적으로", "더 카드스럽게", "더 마케팅스럽게" 보이도록 임의 변경하는 것을 blocker로 취급한다.
- Mockup과 다른 구조, 밀도, 색상 문법, 정보 순서, modal/surface form, Snapshot/History interaction은 허용 deviation category와 reviewer decision 없이는 후속 story로 넘기지 않는다.

### Visualizes

- Project rail, Application rail, Main live surface, Snapshot/History, Instance wide modal, retention expired/source absence state의 current-vs-mockup gap map을 만든다.
- Mockup의 3-column composition, compact rail rows, neutral border panels, small uppercase section labels, stable grids, restrained badges, wide modal, safe empty/error state를 production 적용 원칙으로 추출한다.
- Epic 14 후속 story가 같은 desktop/tablet/mobile viewport와 screenshot naming convention을 사용하게 한다.
- 후속 14.2/14.3/14.4 handoff가 conformance checklist와 deviation log를 completion gate로 인용하게 한다.

## Mockup Conformance And Deviation Guardrails

### Pixel-perfect interpretation

Pixel-perfect DOM/CSS byte-level clone은 non-goal이다. HTML, CSS class name, temporary JavaScript runtime, demo data object, prototype-only controls를 그대로 복사하라는 뜻이 아니다.

하지만 아래 항목은 strict conformance target이다. "비슷한 느낌"이나 "현대적인 재해석"만으로 통과할 수 없다.

- IA와 3-column first-screen layout hierarchy.
- Project rail / Application rail row density, active indicator, metadata/note hierarchy.
- Compact visual density, spacing rhythm, neutral background/panel grammar, thin border, restrained badge/chip language, 6px-ish radius.
- Application Dashboard information ordering: context/read semantics, data quality/freshness, lifecycle state, direct reasons, attention/first look, endpoint/resource evidence, metric detail, starter connection, instance entry.
- Snapshot/History picker: 14일 retention summary, 30분 scheduled points, date map, 48-slot day drilldown, selected snapshot summary, marker-as-index copy.
- Snapshot detail: same dashboard-like surface with `mode=snapshot`, `source=dashboard_snapshots.read_model_json`, recomputation false, marker state source false.
- Instance detail: wide modal/dialog or equivalent wide detail surface, not a narrow right Sheet for live/snapshot dashboard detail.
- Retention expired/404/source absence safe state with no live/current fallback CTA.

### Allowed deviation categories

Mockup과 다르게 구현해야 하는 경우는 아래 사유 중 하나에 속해야 하며, deviation log에 남아야 한다.

1. Production data/read model 때문에 mockup demo data를 그대로 쓸 수 없는 경우.
2. Responsive/mobile에서 물리적으로 동일 배치가 불가능한 경우.
3. Prototype controls, hard-coded JS demo data, temporary mockup runtime처럼 production에 넣으면 안 되는 목업 전용 요소인 경우.
4. 접근성/키보드/focus/ARIA를 위해 시각 구조를 해치지 않는 보강인 경우.

위 사유가 없으면 mockup과 다른 layout, density, visual grammar, ordering, modal form, Snapshot/History interaction은 blocker다.

### Deviation log artifact

14.1 산출물은 아래 파일 또는 동등한 non-production QA artifact에 deviation log template을 만들어야 한다.

- `implementation-artifacts/epic-14-dashboard-design-parity-qa/deviation-log.md`

각 deviation은 최소한 아래 field를 가져야 한다.

| Field | Required meaning |
|---|---|
| Mockup element | HTML mockup 기준 element/section/interaction. 가능한 경우 class/id/visible label을 함께 적는다. |
| Production element | 실제 Vite Dashboard element/component/surface. |
| Reason | 왜 동일하게 이식할 수 없거나 이식하지 않아야 하는지 구체적으로 적는다. |
| Allowed category | 위 1~4 중 하나. 해당하지 않으면 blocker로 표시한다. |
| Reviewer decision | Approved / rejected / needs follow-up 중 하나와 reviewer note. |
| Follow-up owner | 14.2/14.3/14.4 또는 named owner. |

### No discretionary redesign

구현자는 mockup보다 "더 나은 디자인"이라고 판단해 구조, 밀도, 색상 문법, 정보 순서, modal/surface 형태를 임의 변경하지 않는다. Tailwind/shadcn/Radix idiom으로 옮기는 과정에서도 mockup conformance가 우선이며, 임의 개선은 deviation으로 기록하고 reviewer approval을 받아야 한다.

## Non-goals / Out of Scope

- production UI layout 구현.
- frontend implementation, backend code/tests, read model/API 변경, migration/schema 변경.
- Source of Truth 의미 재정의.
- `source-of-truth-dashboard-mockup.html` 수정 또는 HTML/CSS/JS runtime 복사.
- Pixel-perfect DOM/CSS byte-level clone. 단, 이 non-goal은 loose similarity나 discretionary redesign 허용을 뜻하지 않는다.
- Mockup의 prototype controls, hard-coded scenario data, temporary JS state, endpoint sort/limit demo controls를 production UI 요구사항으로 승격.
- authenticated full-path smoke gap을 이 story에서 닫았다고 주장.
- completed Epic 13 story 본문/status 수정 또는 reopen.
- 기존 untracked `dbml-error.log` 수정, 삭제, stage.

## Acceptance Criteria

1. Given 14.1 baseline 작업을 시작할 때, Then `git status --short --branch --untracked-files=all`로 기존 modified/untracked 파일을 기록하고 보호 대상을 변경하지 않는다.
2. Given current Vite Dashboard first screen을 확인할 때, Then desktop `1440x1000`, tablet `1024x900`, mobile `390x844` 기준 baseline screenshot 또는 관찰 기록을 남긴다.
3. Given authenticated Dashboard route 접근이 막히면, Then 가능한 화면/DOM/guard 관찰 범위와 인증 fixture 부재를 evidence note에 명시하고 full authenticated browser smoke를 닫았다고 쓰지 않는다.
4. Given mockup을 분석할 때, Then 3-column composition, layout hierarchy, compact rail rows, neutral border panels, small uppercase section labels, stable grids, restrained badges, wide modal, retention expired/source absence safe state를 strict conformance target으로 정리한다.
5. Given pixel-perfect clone non-goal을 checklist에 쓸 때, Then DOM/CSS byte-level clone과 temporary mockup runtime 복사는 non-goal이지만 IA, layout hierarchy, visual density, spacing rhythm, neutral panel grammar, information ordering, wide modal, Snapshot/History picker, retention expired state는 strict conformance target임을 함께 명시한다.
6. Given mockup의 `prototype-controls`, scenario select, hard-coded JavaScript data, endpoint sort/limit demo controls를 볼 때, Then 이를 production runtime이나 requirement로 복사하지 말라는 guardrail을 QA checklist와 deviation allowed category에 포함한다.
7. Given gap map을 작성할 때, Then Project rail, Application rail, Main live surface, Snapshot/History, Snapshot detail, Instance wide modal, retention expired/source absence state별 current state, mockup element, conformance target, deviation 여부, allowed category, 후속 story owner 후보를 기록한다.
8. Given mockup과 실제 구현이 다르면, Then 해당 차이는 `implementation-artifacts/epic-14-dashboard-design-parity-qa/deviation-log.md` 또는 동등한 deviation log에 기록되어야 하며, mockup element, production element, reason, allowed category, reviewer decision, follow-up owner를 포함한다.
9. Given 차이가 allowed deviation category 1~4에 속하지 않으면, Then 해당 차이는 blocker이며 "디자인 선택" 또는 "더 나은 디자인"으로 승인하지 않는다.
10. Given Epic 14 implementation을 시작할 때, Then 14.2~14.4 구현 전 conformance checklist, no discretionary redesign checklist, deviation log template이 존재하고 후속 story handoff gate로 연결되어 있다.
11. Given screenshot 비교를 수행할 때, Then "비슷함"을 주관적으로 판단하지 않고 구조, 밀도, 순서, visual grammar, modal/surface form, Snapshot/History interaction 일치 여부를 mockup과 side-by-side로 판정한다.
12. Given responsive/mobile 차이가 있으면, Then viewport별로 물리적 배치 제약과 선택한 adaptation을 deviation log 또는 conformance note에 기록한다.
13. Given source semantics guardrail을 검토할 때, Then `accepted_metric_buckets`, `dashboard_snapshots.read_model_json`, `recent_30_minutes`, selected snapshot instance semantics, retention expired no-fallback 기준이 바뀌지 않았음을 기록한다.
14. Given frontend guard를 확인할 때, Then `guard:read-model-contract`가 계속 source/order/recalculation/forbidden field semantics를 감시하는지 확인하고, 필요하면 visual/source semantics sentinel fixture 후보를 제안한다.
15. Given browser visual QA evidence 위치를 정할 때, Then `implementation-artifacts/epic-14-dashboard-design-parity-qa/` 또는 동등한 non-production evidence directory와 filename convention을 기록한다.
16. Given 14.1이 완료될 때, Then story completion notes에는 baseline artifact paths, conformance checklist path, deviation log path, browser visual QA 결과/제약, guard command 결과, known gap, 다음 14.2~14.4 handoff가 포함된다.
17. Given implementation diff를 검토할 때, Then production code, frontend implementation, backend tests, migration/schema, Source of Truth mockup, completed Epic 13 story, `dbml-error.log`가 변경되지 않았음이 확인된다.

## Tasks / Subtasks

- [x] 시작 상태와 보호 대상을 기록한다. (AC: 1, 17)
  - [x] `git status --short --branch --untracked-files=all`로 기존 modified/untracked 상태를 completion notes에 남긴다.
  - [x] `dbml-error.log`, seed `13-ui...` story, completed Epic 13 story, mockup HTML을 read-only 보호 대상으로 기록한다.

- [x] current Vite Dashboard baseline을 남긴다. (AC: 2, 3, 15, 16)
  - [x] local Vite app을 실행하고 dashboard route 접근 방식을 확인한다.
  - [x] desktop `1440x1000`, tablet `1024x900`, mobile `390x844`에서 screenshot 또는 관찰 기록을 남긴다.
  - [x] 인증/fixture 부재로 실제 Dashboard first screen을 캡처하지 못하면 그 제약을 명시하고 available fallback evidence를 분리한다.

- [x] Mockup conformance checklist를 작성한다. (AC: 4, 5, 10, 11)
  - [x] `app-grid` 3-column rail/main composition, `rail-item` compact row, thin border panel, 6px-ish radius, small uppercase `section-label`, compact badge/chip, stable grid, wide modal 원칙을 정리한다.
  - [x] Snapshot/History의 14일 retention summary, 48-slot day grid, selected snapshot summary, stored detail source note를 후속 story 적용 원칙으로 분리한다.
  - [x] pixel-perfect DOM/CSS byte-level clone은 non-goal이지만 IA, layout hierarchy, density, spacing rhythm, neutral panel grammar, ordering, wide modal, Snapshot/History picker, retention expired state는 strict target이라고 기록한다.
  - [x] `planning-artifacts/source-of-truth/source-of-truth-dashboard-snapshot-picker.png`는 Source of Truth가 아니며 HTML mockup만 기준이라고 기록한다.

- [x] Deviation log template을 작성한다. (AC: 7, 8, 9, 12)
  - [x] `implementation-artifacts/epic-14-dashboard-design-parity-qa/deviation-log.md` 또는 동등한 path를 만들고 mockup element, production element, reason, allowed category, reviewer decision, follow-up owner field를 둔다.
  - [x] allowed category 1~4에 속하지 않는 차이는 blocker로 표시하는 template guidance를 둔다.
  - [x] responsive/mobile adaptation은 viewport, 물리적 제약, chosen adaptation, reviewer decision을 남기게 한다.

- [x] No discretionary redesign checklist를 작성한다. (AC: 9, 10, 11)
  - [x] "더 예쁘게/더 현대적으로/더 카드스럽게/더 마케팅스럽게" 바꾸는 임의 변경 금지 항목을 둔다.
  - [x] mockup과 다른 layout, density, color/visual grammar, ordering, modal/surface form, Snapshot/History interaction은 deviation으로 기록하게 한다.
  - [x] prototype controls, hard-coded demo data, temporary JS runtime은 production 요구사항이 아니라고 기록한다.

- [x] 영역별 visual/design conformance gap map을 작성한다. (AC: 7, 8, 13)
  - [x] Project rail gap: scope selection, application count, setup/recent concern signal, rail density.
  - [x] Application rail gap: lifecycle badge, accepted bucket freshness, starter connection, top concern 0~1개, metric axis와 heartbeat axis 분리.
  - [x] Main live surface gap: context/read semantics bar, freshness, lifecycle state, direct reasons, attention/first look, endpoint/resource evidence, metric detail dominance.
  - [x] Snapshot detail gap: dashboard-like skeleton, `mode=snapshot`, source/recalculation/marker-state-source signal, stored provenance.
  - [x] Snapshot/History gap: marker-first hierarchy, 14일/672 point summary, date map, 48-slot grid, marker-as-state 방지, expired/404 safe copy.
  - [x] Instance wide modal gap: wide Dialog, application state reference, read semantics, selected metrics, endpoint/resource/starter/table order, stored trend source separation.
  - [x] Retention/source absence gap: no live/current fallback CTA, safe empty/error copy, authenticated full-path evidence limitation.

- [x] Epic 14 visual QA checklist와 evidence convention을 정한다. (AC: 10, 11, 15, 16)
  - [x] Desktop/tablet/mobile viewport, screenshot filename, notes file, guard command output 위치를 정한다.
  - [x] mockup과 current UI screenshot을 side-by-side로 비교하는 convention을 둔다.
  - [x] Project rail, Application rail, Main surface, Snapshot picker, Snapshot detail, Instance wide modal, retention expired state별 conformance 판정란을 둔다.
  - [x] text overlap, clipped badges, rail overflow, nested card clutter, horizontal scroll, modal clipping, slot grid wrapping, retention expired safe copy 확인 항목을 체크리스트화한다.
  - [x] 후속 story가 같은 checklist를 completion notes에 인용하게 한다.

- [x] 14.2/14.3/14.4 handoff에 conformance gate를 연결한다. (AC: 10, 16)
  - [x] 14.2 handoff는 Project rail, Application rail, Main live surface conformance checklist와 deviation log update를 요구한다.
  - [x] 14.3 handoff는 Snapshot/History picker, Snapshot detail, retention expired/source absence conformance checklist와 deviation log update를 요구한다.
  - [x] 14.4 handoff는 Instance wide modal, end-to-end visual QA, remaining deviation disposition을 요구한다.

- [x] Source semantics sentinel 후보를 검토한다. (AC: 13, 14, 17)
  - [x] 기존 `read-model-contract-guard.ts`, fixtures, script가 `accepted_metric_buckets`, `dashboard_snapshots.read_model_json`, `recent_30_minutes`, `markerIsStateSource=false`, `snapshotDetailRecalculates=false`, selected snapshot instance semantics를 계속 감시하는지 확인한다.
  - [x] 필요한 경우 후속 14.2~14.4에서 추가할 sentinel fixture 후보만 기록하고, 14.1에서 production UI 구현으로 확장하지 않는다.

## Candidate Files

- `planning-artifacts/stories/14-1-design-parity-baseline-and-visual-guardrails.md`
- `implementation-artifacts/sprint-status.yaml`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/README.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/baseline-desktop-1440x1000.png`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/baseline-tablet-1024x900.png`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/baseline-mobile-390x844.png`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/conformance-checklist.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/deviation-log.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/no-discretionary-redesign-checklist.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/handoff-gates.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/mockup-principles-and-gap-map.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/side-by-side-desktop-1440x1000.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/side-by-side-tablet-1024x900.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/side-by-side-mobile-390x844.md`
- `frontend/src/app/lib/read-model-contract-fixtures.ts`
- `frontend/src/app/lib/read-model-contract-guard.ts`
- `frontend/scripts/read-model-contract-guard.ts`
- `frontend/package.json`
- `planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html` (read-only reference)
- `planning-artifacts/stories/13-ui-dashboard-source-of-truth-surface-realignment.md` (optional read-only seed/handoff)

## Verification Commands

```bash
git status --short --branch --untracked-files=all

cd frontend && npm run guard:read-model-contract
cd frontend && npm run typecheck
cd frontend && npm run build

rg -n "current_15m|hourly scheduled|hourly snapshot|live/current fallback|current dashboard fallback|marker.*state|healthScore|rootCause|recoveryProof|endpoint timeseries|raw snapshot|raw metric|arbitrary query" frontend/src frontend/scripts
rg -n "\\.sort\\(|\\.toSorted\\(|\\.reduce\\(" frontend/src/app/components frontend/src/app/lib frontend/scripts
rg -n "dashboard_snapshots\\.read_model_json|accepted_metric_buckets|recent_30_minutes|markerIsStateSource|snapshotDetailRecalculates|acceptedAtCutoffApplied|includesLateAcceptedMetrics|mayDifferFromStoredApplicationSnapshot" frontend/src/app/components frontend/src/app/lib frontend/scripts

git diff --check
git status --short --branch --untracked-files=all
```

YAML을 수정하면 아래 중 하나로 parse를 확인한다.

```bash
ruby -e 'require "yaml"; YAML.load_file("implementation-artifacts/sprint-status.yaml"); puts "yaml ok"'
```

## Browser visual QA plan

14.1 구현자는 local Vite app을 실행하고 가능한 route에서 실제 렌더링을 확인한다.

1. `cd frontend && npm run dev -- --host 127.0.0.1`로 dev server를 실행한다.
2. Browser/Playwright로 Dashboard route를 연다. 인증 fixture가 없으면 접근 가능한 화면과 차단 사유를 분리 기록한다.
3. 최소 viewport:
   - desktop: `1440x1000`
   - tablet: `1024x900`
   - mobile: `390x844`
4. 각 viewport에서 아래 항목을 확인한다.
   - Project rail / Application rail / Main surface composition
   - rail row density, active indicator, recent concern note, badge clipping
   - Application Dashboard context/read semantics bar와 source/window badges
   - live surface section order와 metric detail dominance 여부
   - Snapshot/History retention summary, date map, 48-slot grid, selected snapshot summary
   - Snapshot detail `mode=snapshot`, `source=dashboard_snapshots.read_model_json`, recomputation false copy
   - Instance wide modal open/close, sticky header, normalized endpoint table overflow
   - retention expired/404/source absence safe copy와 no live/current fallback CTA
   - text overlap, horizontal scroll, clipped badges, nested card clutter, modal clipping 없음
5. Mockup HTML과 실제 Vite Dashboard screenshot을 side-by-side로 비교한다. 판정 단위는 "비슷함"이 아니라 구조, 밀도, 순서, visual grammar, modal/surface form, Snapshot/History interaction 일치 여부다.
6. Project rail, Application rail, Main surface, Snapshot picker, Snapshot detail, Instance wide modal, retention expired state별로 `conformant`, `allowed deviation`, `blocker` 중 하나를 기록한다.
7. Viewport별 responsive adaptation이 있으면 allowed deviation category, 물리적 제약, reviewer decision을 deviation log 또는 side-by-side note에 남긴다.
8. Screenshot과 note는 `implementation-artifacts/epic-14-dashboard-design-parity-qa/` 아래에 저장한다. production source tree와 섞지 않는다.
9. Full authenticated browser path를 실행하지 못하면 `Known gap / Handoff`에 그대로 남긴다.

## Known gap / Handoff

Full authenticated browser demo route/fixture가 없어 `Project -> Application -> Dashboard -> Snapshot -> Instance -> retention expired` path를 하나의 authenticated smoke로 닫은 evidence는 아직 없다.

14.1은 이 gap을 닫는 story가 아니다. 14.1은 baseline과 QA evidence 방식을 고정하고, 후속 14.2~14.4가 mockup conformance 구현을 진행할 때 같은 guardrail을 사용하도록 만든다. authenticated full-path smoke가 막히면 fixture/runbook follow-up으로 분리하고, Epic 13 완료 상태나 Source of Truth 의미를 되돌리지 않는다.

## Dev Notes

- Active implementation baseline은 Traditional MVC + Service/Repository Layering이다. 이 story는 planning/QA baseline story이며 backend MVC layer를 변경하지 않는다.
- Frontend root는 `frontend/`이며 React 18.3.1, Vite 6.3.5, TypeScript 5.8.3, Radix/shadcn-style UI, lucide-react, Playwright dev dependency를 사용한다. 새 dependency는 기본적으로 추가하지 않는다.
- 현재 frontend에는 `ProjectRail`, `ApplicationRail`, `DashboardMain`, `SnapshotHistoryPanel`, `SnapshotDetailSurface`, `InstancePanels`, `InstanceDashboardSurface`가 있다. 14.1은 이 구조를 구현 변경 없이 baseline/gap map 대상으로 삼는다.
- `frontend/package.json`은 `guard:read-model-contract`, `typecheck`, `build`, `dev` script를 제공한다.
- Existing frontend guard는 Application Dashboard, Snapshot history/detail, Instance Dashboard live/snapshot/trend의 source/order/readSemantics drift를 fail-closed로 감시한다.
- Mockup visual grammar는 neutral background, compact white panels, thin borders, small uppercase labels, 6px-ish radius, restrained badges/chips, stable grids, wide modal이다. 이 grammar는 discretionary redesign 대상이 아니라 conformance target이다.
- Mockup `retentionPolicy` demo data는 14일, 30분 cadence, 48 points/day, 672 total scheduled point를 보여준다. 이는 Epic 13 semantics와 일치하는 visual cue로만 쓰고 JS runtime은 복사하지 않는다.
- `source-of-truth-dashboard-snapshot-picker.png` screenshot export는 Source of Truth가 아니다. HTML mockup을 기준으로 side-by-side 비교한다.
- UI copy, docs/comments, test display name은 한국어를 기본으로 한다. source string, endpoint path, schema version, field name은 원문을 유지한다.
- `planning-artifacts/epics.md`에는 Epic 14 요약이 이미 있으므로, 명백한 누락/불일치가 없으면 이 story에서는 수정하지 않는다.

## References

- `_bmad/custom/project-context.md`
- `planning-artifacts/epics.md#Epic 14. Dashboard Mockup Design Parity`
- `planning-artifacts/epic-14-dashboard-mockup-design-parity.md`
- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html`
- `planning-artifacts/dashboard-source-of-truth-realignment-roadmap.md`
- `planning-artifacts/dashboard-source-of-truth-realignment-sequence.yaml`
- `planning-artifacts/stories/13-5-frontend-application-dashboard-ia-realignment.md`
- `planning-artifacts/stories/13-7-frontend-snapshot-history-detail-realignment.md`
- `planning-artifacts/stories/13-9-frontend-instance-surface-split.md`
- `planning-artifacts/stories/13-11-end-to-end-acceptance-and-demo-hardening.md`
- `implementation-artifacts/epic-13-retro-2026-06-11.md`
- `planning-artifacts/stories/13-ui-dashboard-source-of-truth-surface-realignment.md`

## Dev Agent Record

### Agent Model Used

GPT-5 Codex (BMAD dev-story)

### Debug Log References

- 2026-06-11T17:16:04+0900 `git status --short --branch --untracked-files=all`: 기존 modified `implementation-artifacts/sprint-status.yaml`, `planning-artifacts/epics.md`, deleted `source-of-truth-dashboard-snapshot-picker.png`, untracked `dbml-error.log`, Epic 14 planning/story artifacts, seed `13-ui...` story를 확인했다.
- BMAD `bmad-dev-story` workflow customization과 `_bmad/custom/project-context.md`를 확인했다.
- 필수 문서: Epic 14 planning source, Story 14.1, HTML mockup, `epics.md`, `sprint-status.yaml`, Story 13.5/13.7/13.9/13.11, Epic 13 retro, seed `13-ui...` story를 read-only context로 확인했다.
- `cd frontend && npm run dev -- --host 127.0.0.1`로 Vite dev server를 실행하고 Playwright로 `/dashboard` desktop/tablet/mobile screenshot과 DOM 관찰 JSON을 저장했다.
- 인증 token/fixture 부재로 실제 authenticated Dashboard data path는 열지 못했다. `/dashboard`는 HTTP 200이지만 Project rail auth-required state까지만 렌더링됐다.
- HTML mockup reference screenshots를 desktop/tablet/mobile, snapshot picker, instance modal 기준으로 저장했다.
- `cd frontend && npm run guard:read-model-contract`: 통과. `read-model contract guard fixtures passed`.
- `cd frontend && npm run typecheck`: 통과.
- `cd frontend && npm run build`: 통과. Vite build `built in 1.07s`.
- Static grep: forbidden wording hits는 guard negative fixture/assertion 또는 explanatory comment로 분류했다. `.sort()`/`.toSorted()`/`.reduce()` search는 frontend target 범위에서 hit 없음.
- `ruby -e 'require "yaml"; YAML.load_file("implementation-artifacts/sprint-status.yaml"); puts "yaml ok"'`: 통과.
- 2026-06-11T17:38:25+0900 quick dev follow-up: BMAD code review P3 finding에 따라 gap map의 `Allowed category` traceability를 보강하고 Story 14.1 / sprint status를 done으로 정렬했다.

### Completion Notes List

- Story 14.1은 production 구현 story가 아니라 non-production QA baseline/handoff story로 완료했다.
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/` 아래에 conformance checklist, deviation log, no discretionary redesign checklist, gap map, handoff gates, side-by-side notes, guard result, source sentinel review, screenshots를 생성했다.
- Mockup 기준은 `source-of-truth-dashboard-mockup.html`로 고정했고 `source-of-truth-dashboard-snapshot-picker.png`는 Source of Truth가 아니라고 반복 명시했다.
- Pixel-perfect DOM/CSS byte-level clone과 mockup temporary runtime 복사는 non-goal이지만 IA, layout hierarchy, density, spacing rhythm, neutral panel grammar, information ordering, wide modal, Snapshot/History picker, retention/source absence는 strict conformance target이라고 명시했다.
- Deviation allowed category 1~4와 reviewer decision/follow-up owner template을 만들고, category 밖 차이는 blocker로 기록했다.
- Gap map 각 영역에 `Allowed category` 또는 `N/A - coverage gap` / `Pending` 분류를 명시해 AC7 traceability를 보강했다.
- Browser baseline: Vite `/dashboard`는 desktop/tablet/mobile에서 열렸으나 인증 fixture가 없어 authenticated dashboard first screen, Snapshot picker/detail, Instance modal, retention expired path는 browser evidence로 닫지 못했다. Mobile auth-blocked state에서 horizontal overflow warning(`body.scrollWidth=504`, viewport `390`)을 known follow-up으로 기록했다.
- Source semantics guard: `guard:read-model-contract`가 `accepted_metric_buckets`, `dashboard_snapshots.read_model_json`, `recent_30_minutes`, selected snapshot instance semantics, retention no-fallback 관련 source/order/recalculation/forbidden field semantics를 계속 감시함을 확인했다.
- 후속 14.2는 Project rail/Application rail/Main live surface, 14.3은 Snapshot/History/Snapshot detail/retention source absence, 14.4는 Instance wide modal/end-to-end visual QA와 deviation disposition을 completion gate로 삼아야 한다.
- Production code, frontend implementation, backend code/tests, migration/schema, Source of Truth mockup HTML, completed Epic 13 story body/status, 기존 untracked `dbml-error.log`는 수정하지 않았다.

### File List

- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/stories/14-1-design-parity-baseline-and-visual-guardrails.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/README.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/conformance-checklist.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/deviation-log.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/no-discretionary-redesign-checklist.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/handoff-gates.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/mockup-principles-and-gap-map.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/side-by-side-desktop-1440x1000.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/side-by-side-tablet-1024x900.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/side-by-side-mobile-390x844.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/source-semantics-sentinel-review.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/guard-14-1-20260611-1716.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/browser-baseline-observations.json`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/baseline-desktop-1440x1000.png`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/baseline-tablet-1024x900.png`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/baseline-mobile-390x844.png`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/mockup-reference-desktop-1440x1000.png`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/mockup-reference-tablet-1024x900.png`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/mockup-reference-mobile-390x844.png`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/mockup-reference-desktop-snapshot-picker-1440x1000.png`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/mockup-reference-desktop-instance-modal-1440x1000.png`

## Change Log

| Date | Change |
|---|---|
| 2026-06-11 | Epic 14 Story 14.1 design parity baseline and visual guardrails story artifact를 생성하고 ready-for-dev로 전환했다. |
| 2026-06-11 | Story 14.1을 strict mockup conformance baseline, explicit deviation log, no discretionary redesign guardrail 중심으로 강화했다. |
| 2026-06-11 | Story 14.1 QA baseline artifacts, browser baseline evidence, conformance/deviation/handoff gates를 생성하고 review로 전환했다. |
| 2026-06-11 | BMAD code review 후 gap map allowed category traceability를 보강하고 Story 14.1을 done으로 전환했다. |
