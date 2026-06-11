---
artifactType: story
storyId: "14.4"
storyKey: "14-4-instance-wide-modal-and-end-to-end-visual-qa"
epic: "Epic 14. Dashboard Mockup Design Parity"
title: "Instance Wide Modal And End-To-End Visual QA"
architectureStyle: Traditional MVC
status: review
date: 2026-06-11
workType: frontend-qa
implementationScope: "Instance live/snapshot wide modal, stored Instance Snapshot Trend, retention/source absence final QA, Epic 14 end-to-end visual QA evidence/handoff/status synchronization"
productionCodeChangeThisContext: false
plannedProductionCodeChange: true
sourceOfTruthMode: read-only
rollbackBoundary: "frontend instance modal/trend surface, frontend guard/fixture/static sentinel, Epic 14 QA evidence, story artifact, and sprint status only"
---

# Story 14.4 - Instance Wide Modal And End-To-End Visual QA

## Status

review

2026-06-11: BMAD create-story 흐름으로 Epic 14의 네 번째 story artifact를 생성한다. 이번 컨텍스트에서는 구현하지 않고 story artifact와 `implementation-artifacts/sprint-status.yaml` 상태만 동기화한다.

2026-06-11: 기준은 오직 `planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html`이다. `planning-artifacts/source-of-truth/source-of-truth-dashboard-snapshot-picker.png`는 기준으로 삼지 않는다.

2026-06-11: `planning-artifacts/stories/13-ui-dashboard-source-of-truth-surface-realignment.md`는 read-only seed/handoff 자료로만 참고한다. backend code/tests, migration/schema, Source of Truth mockup HTML, completed Epic 13 story body/status, `dbml-error.log`는 수정하지 않는 보호 대상이다.

2026-06-11: BMAD dev-story 구현을 완료하고 review로 전환한다. Instance live/snapshot detail wide modal grammar, modal section order, snapshot note, stored trend source separation, 14.4 static sentinel, final visual QA evidence를 업데이트했다. `.private/smoke-auth.env` access token fixture가 없어 full authenticated Project -> Application -> Dashboard -> Snapshot -> Instance -> retention expired browser path는 known gap으로 유지한다.

## Story

frontend 구현자/QA 담당자로서, 실제 Vite Dashboard의 Instance live/snapshot detail을 HTML mockup의 wide modal/detail surface로 정렬하고 Epic 14 전체 visual QA evidence를 마감하고 싶다.

그래야 운영자가 Project rail -> Application rail -> Application Dashboard live surface -> Snapshot/History -> Snapshot detail -> Instance wide modal -> retention/source absence 흐름을 mockup과 같은 visible IA, layout hierarchy, compact density, spacing rhythm, neutral panel grammar, badge/chip language, section order, copy intent, responsive behavior로 읽을 수 있고, 남은 deviation은 allowed category/reviewer decision/follow-up owner 없이 통과하지 못한다.

## Source Of Truth

아래 문서는 read-only 기준이다. 14.4는 의미를 재정의하지 않고 구현 지침과 completion gate로만 사용한다.

1. `_bmad/custom/project-context.md`
2. `planning-artifacts/epic-14-dashboard-mockup-design-parity.md`
3. `planning-artifacts/stories/14-1-design-parity-baseline-and-visual-guardrails.md`
4. `planning-artifacts/stories/14-2-dashboard-shell-rails-and-live-surface-realignment.md`
5. `planning-artifacts/stories/14-3-snapshot-history-detail-and-retention-surface-realignment.md`
6. `planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html`
7. `implementation-artifacts/sprint-status.yaml`
8. `implementation-artifacts/epic-14-dashboard-design-parity-qa/README.md`
9. `implementation-artifacts/epic-14-dashboard-design-parity-qa/conformance-checklist.md`
10. `implementation-artifacts/epic-14-dashboard-design-parity-qa/deviation-log.md`
11. `implementation-artifacts/epic-14-dashboard-design-parity-qa/no-discretionary-redesign-checklist.md`
12. `implementation-artifacts/epic-14-dashboard-design-parity-qa/mockup-principles-and-gap-map.md`
13. `implementation-artifacts/epic-14-dashboard-design-parity-qa/handoff-gates.md`
14. `implementation-artifacts/epic-14-dashboard-design-parity-qa/source-semantics-sentinel-review.md`
15. `implementation-artifacts/epic-14-dashboard-design-parity-qa/side-by-side-desktop-1440x1000.md`
16. `implementation-artifacts/epic-14-dashboard-design-parity-qa/side-by-side-tablet-1024x900.md`
17. `implementation-artifacts/epic-14-dashboard-design-parity-qa/side-by-side-mobile-390x844.md`
18. `implementation-artifacts/epic-14-dashboard-design-parity-qa/guard-14-3-snapshot-history-detail-and-retention-surface-realignment-20260611-1859.md`
19. `implementation-artifacts/epic-14-dashboard-design-parity-qa/browser-14-3-snapshot-history-detail-and-retention-surface-realignment-observations.json`
20. `planning-artifacts/stories/13-ui-dashboard-source-of-truth-surface-realignment.md`가 있으면 read-only seed/handoff 자료로만 참고한다.

`source-of-truth-dashboard-mockup.html`의 UI/UX를 "참고해서 비슷하게" 만드는 것이 아니다. 실제 Vite UI는 mockup HTML의 visible IA, layout hierarchy, modal width/form, compact density, spacing rhythm, neutral panel grammar, badge/chip language, section order, copy intent, responsive behavior를 기준 디자인으로 그대로 구현해야 한다. Pixel-perfect DOM/CSS byte-level clone이나 mockup runtime 복사는 non-goal이지만, UI/UX 판단 기준은 mockup HTML 그 자체다. 임의 재해석, 더 예쁜 redesign, 더 modern/card-heavy/marketing-style 변경은 blocker다.

## Background

Story 14.1은 strict mockup conformance checklist, no-discretionary-redesign gate, deviation log, side-by-side evidence convention, source semantics sentinel review를 만들고 done으로 닫았다.

Story 14.2는 Project rail, Application rail, Main live surface를 code/static/auth-blocked shell evidence 기준으로 정렬했고 done으로 닫았다. `DashboardMain` tab split은 제거됐고 Snapshot/History는 same-flow anchor로 남아 있다. Authenticated dashboard strict visual conformance는 fixture 부재로 coverage gap이다.

Story 14.3은 Snapshot/History picker, selected snapshot summary, Snapshot detail top flags, retention/source absence no-fallback copy, static sentinel, QA evidence를 구현하고 done으로 닫았다. 14.3의 authenticated browser fixture gap은 과장하지 않는다. 14.4에서 authenticated fixture/runbook이 있으면 full path를 닫고, 없으면 known gap으로 남긴다.

현재 구현 후보는 이미 존재한다. `InstancePanels`는 live/snapshot dashboard를 `DialogContent` width `min(1120px, calc(100vw - 2rem))`로 열고, stored trend는 `Sheet`로 분리한다. `InstanceDashboardSurface`는 context note -> Application state reference -> Read semantics -> selected instance metrics -> endpoint evidence -> resource evidence -> starter connection -> normalized endpoint evidence table 순서에 가까운 구조를 갖는다. 14.4는 이 구조를 mockup HTML의 wide modal/detail grammar에 맞춰 끝까지 다듬고, desktop/tablet/mobile visual evidence와 deviation disposition으로 Epic 14를 마감하는 story다.

## Aligns / Hardens / Visualizes

### Aligns

- `14-1-design-parity-baseline-and-visual-guardrails`: conformance checklist, deviation log, no discretionary redesign checklist, handoff gates를 14.4 completion gate로 사용한다.
- `14-2-dashboard-shell-rails-and-live-surface-realignment`: Project rail / Application rail / Main live surface의 done handoff를 이어받고 재설계하지 않는다.
- `14-3-snapshot-history-detail-and-retention-surface-realignment`: Snapshot/History, Snapshot detail, retention/source absence done handoff를 이어받고 Instance wide modal과 final visual QA에서 다시 확인한다.
- `13-9-frontend-instance-surface-split`: Instance live/snapshot detail은 wide detail surface이며 selected Application Snapshot row metadata + selected instance metric evidence reconstruction을 구분한다.
- `13-11-end-to-end-acceptance-and-demo-hardening`: guard/typecheck/build/regression evidence를 기반으로 삼되, authenticated full-path fixture가 없으면 full browser smoke를 닫았다고 쓰지 않는다.

### Hardens

- Instance live source는 `accepted_metric_buckets` + `recent_30_minutes`다.
- Instance snapshot mode는 selected Application Snapshot row metadata와 selected instance `accepted_metric_buckets` evidence reconstruction이다.
- Instance snapshot mode는 stored Application Snapshot state/evidence를 검증하거나 대체하지 않는다.
- Stored Instance Snapshot Trend는 `dashboard_snapshots.read_model_json.instanceSummary.items[]` projection source로 분리한다.
- UI는 Instance health score, root cause, recovery proof, independent instance lifecycle state, current state timeline을 만들지 않는다.
- Retention/source absence는 live/current accepted bucket/current instance evidence fallback 없이 safe empty/error state로 수렴한다.

### Visualizes

- Instance live/snapshot Dashboard detail은 narrow Sheet가 아니라 mockup의 wide modal/detail surface로 보인다.
- Modal header/body는 desktop/tablet/mobile에서 clipped 되지 않고, table overflow는 modal body 안에서만 처리한다.
- Modal section order는 context note, Application state reference, Read semantics, selected instance metrics, endpoint evidence, resource evidence, starter connection, normalized endpoint evidence table이다.
- Snapshot mode note는 selected Application Snapshot row window, late accepted metric 가능성, stored Application Snapshot override 금지를 짧고 명확하게 드러낸다.
- Epic 14 final visual QA는 Project rail, Application rail, Main live surface, Snapshot/History, Snapshot detail, Instance wide modal, retention/source absence를 desktop/tablet/mobile에서 판정한다.

## Scope

- Instance live/snapshot wide modal/detail surface visual conformance.
- Instance modal internal section order, compact neutral panel grammar, sticky/stable header, modal body scroll, normalized endpoint table overflow.
- Instance snapshot mode copy: selected Application Snapshot row window, `includesLateAcceptedMetrics`, `mayDifferFromStoredApplicationSnapshot`, no stored Application Snapshot override.
- Stored Instance Snapshot Trend source separation and copy.
- Retention/source absence final QA, including `metric_missing`/`not_observed_in_window` limitation UX.
- Epic 14 final visual QA evidence, side-by-side note updates, deviation log disposition, handoff/status synchronization.

## Non-Goals / Protected Scope

- backend code/tests 변경.
- migration/schema 변경.
- Source of Truth mockup HTML 변경.
- completed Epic 13 story body/status 수정 또는 reopen.
- `planning-artifacts/stories/13-ui-dashboard-source-of-truth-surface-realignment.md` 수정. 이 파일은 read-only seed/handoff only다.
- `planning-artifacts/source-of-truth/source-of-truth-dashboard-snapshot-picker.png`를 기준으로 삼거나 되살리는 작업.
- 기존 untracked `dbml-error.log` 수정, 삭제, stage.
- Instance Dashboard backend endpoint/read model 의미 변경.
- `dashboard_snapshots.read_model_json.instanceSummary.items[]`를 Instance Dashboard snapshot detail 필수 source로 만드는 변경.
- Instance health score, root cause, recovery proof, independent instance lifecycle state, current state timeline.
- authenticated full-path smoke fixture 부재를 browser token persistence, URL token parsing, 임시 auth bypass로 우회.
- raw metric explorer, raw snapshot explorer, endpoint timeseries, arbitrary query UI.

## Mockup Conformance Targets

### Instance wide modal

- HTML mockup anchors: `.modal-backdrop`, `.modal`, `.modal-head`, `.modal-body`, `.modal-grid`, `#instanceModal`, `openModal(instanceId)`.
- Width target은 mockup의 `width: min(1120px, 100%)`에 준한다. Production은 현재 `DialogContent`의 `w-[min(1120px,calc(100vw-2rem))]` 후보를 유지하거나 동등하게 넓은 detail surface로 맞춘다.
- Header는 sticky 또는 동등하게 stable해야 한다. Close affordance, title, section label, subtitle이 clipped 되지 않는다.
- Body는 compact `panel`/thin border/neutral grammar를 유지하며 nested card-heavy redesign을 만들지 않는다.
- Desktop `1440x1000`, tablet `1024x900`, mobile `390x844`에서 modal clipping, body scroll trap, table overflow, text overlap, clipped badges를 확인한다.

### Modal section order

1. Context note.
2. Application state reference.
3. Read semantics.
4. Selected instance metrics.
5. Endpoint evidence on selected instance.
6. Resource evidence.
7. Starter connection.
8. Normalized endpoint evidence table.

이 순서는 mockup의 visible IA 기준이다. Production field가 달라 label/copy가 일부 바뀌는 것은 category 1 deviation으로 기록할 수 있지만, section order와 읽기 흐름을 임의 재해석하면 blocker다.

### Snapshot mode note

Snapshot mode note는 최소 아래 의미를 드러낸다.

- selected Application Snapshot row window 기준 evidence다.
- selected instance evidence는 accepted metric evidence reconstruction일 수 있다.
- late accepted metric이 포함될 수 있다.
- stored Application Snapshot state/evidence를 override, 검증, 대체하지 않는다.
- `includesLateAcceptedMetrics=true`, `mayDifferFromStoredApplicationSnapshot=true`, `applicationSnapshotRecalculated=false`, `markerIsStateSource=false`가 Read semantics에서 확인 가능하다.

### Stored Instance Snapshot Trend

- Stored trend는 `dashboard_snapshots.read_model_json.instanceSummary.items[]` projection source로 표시한다.
- Trend는 current state, health score, root cause, recovery proof timeline처럼 보이면 안 된다.
- Trend point에서 Application Snapshot detail / Instance snapshot dashboard로 이동할 수 있어도, stored trend 자체가 snapshot mode evidence를 재계산한다고 표현하지 않는다.

### Retention / source absence

- Snapshot instance evidence 404, retention gap, missing metric, malformed/source absence는 safe empty/error state로 수렴한다.
- `metric_missing`, `not_observed_in_window`, source absence는 evidence limitation이다.
- "정상 확정", "문제 없음", "복구 완료"로 보정하지 않는다.
- Live dashboard/current accepted bucket/current instance evidence fallback CTA나 copy는 blocker다.

## Acceptance Criteria

1. Given 14.4 구현자가 작업을 시작할 때, When `git status --short --branch --untracked-files=all`을 실행하면, Then 기존 untracked `dbml-error.log`와 seed `planning-artifacts/stories/13-ui-dashboard-source-of-truth-surface-realignment.md`는 수정, 삭제, stage하지 않는 보호 대상으로 확인된다.
2. Given 기준 디자인을 확인할 때, When source artifact를 선택하면, Then 기준은 오직 `planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html`이며 `source-of-truth-dashboard-snapshot-picker.png`는 기준으로 삼지 않는다.
3. Given Instance summary에서 live/snapshot detail을 열 때, When detail surface가 렌더링되면, Then narrow Sheet가 아니라 mockup의 wide Dialog/modal 또는 동등한 wide detail surface로 구현한다.
4. Given Instance modal을 desktop/tablet/mobile에서 볼 때, When header/body/table을 확인하면, Then modal width/form은 mockup wide modal grammar에 준하고 header clipping, body clipping, normalized endpoint table page overflow가 없다.
5. Given modal body를 위에서 아래로 읽을 때, When section order를 확인하면, Then context note, Application state reference, Read semantics, selected instance metrics, endpoint evidence, resource evidence, starter connection, normalized endpoint evidence table 순서를 유지한다.
6. Given live Instance Dashboard를 볼 때, When top context와 Read semantics를 확인하면, Then `mode=live`, `source=accepted_metric_buckets`, `recent_30_minutes`, `applicationStateRef.lifecycleOwner=application`이 보이고 instance top-level lifecycle state를 만들지 않는다.
7. Given selected Application Snapshot 기준 Instance Dashboard snapshot mode를 볼 때, When snapshot note와 Read semantics를 확인하면, Then selected Application Snapshot row window, late accepted metric 가능성, no stored Application Snapshot override가 명확히 보인다.
8. Given Instance Dashboard snapshot mode를 볼 때, When semantic flags를 확인하면, Then `includesLateAcceptedMetrics=true`, `mayDifferFromStoredApplicationSnapshot=true`, `applicationSnapshotRecalculated=false`, `instanceEvidenceReconstructedFromMetrics=true`, `markerIsStateSource=false`가 유지된다.
9. Given Instance Dashboard snapshot mode가 retention gap 또는 missing metric을 만날 때, When error/empty/limitation UX를 확인하면, Then `metric_missing`/`not_observed_in_window`/source absence limitation으로 수렴하고 live/current evidence로 보정하지 않는다.
10. Given Stored Instance Snapshot Trend를 볼 때, When source/copy와 trend point를 확인하면, Then stored projection source는 `dashboard_snapshots.read_model_json.instanceSummary.items[]`로 분리되고 current state/health score/root cause/recovery proof timeline처럼 보이지 않는다.
11. Given endpoint/resource/starter evidence를 볼 때, When selected instance evidence를 읽으면, Then Application Dashboard 판단을 대체하지 않고 endpoint priority, resource pattern, starter heartbeat 의미를 client에서 재계산하지 않는다.
12. Given normalized endpoint evidence table을 볼 때, When rows/order/controls를 확인하면, Then server-provided order/source를 보존하고 raw path/query/per-request sample, endpoint timeseries, arbitrary query UI로 확장하지 않는다.
13. Given Project rail, Application rail, Main live surface, Snapshot/History, Snapshot detail, Instance wide modal, retention/source absence를 검토할 때, Then desktop `1440x1000`, tablet `1024x900`, mobile `390x844`에서 final visual QA evidence를 남긴다.
14. Given final visual QA를 수행할 때, Then text overlap, clipped badges, rail overflow, nested card clutter, horizontal page scroll, modal clipping, modal header/body overlap, normalized table page overflow, slot grid wrapping, retention safe copy를 확인한다.
15. Given authenticated fixture/runbook이 있으면, When `Project -> Application -> Dashboard -> Snapshot -> Instance -> retention expired` full path를 browser로 실행하면, Then evidence에 route, viewport, screenshot/note, pass/fail을 남긴다.
16. Given authenticated fixture가 없으면, When completion notes를 작성하면, Then full path를 닫았다고 쓰지 않고 known gap으로 유지한다.
17. Given mockup과 다른 layout, density, visual grammar, ordering, modal/surface form, Snapshot/History interaction, retention/source absence copy가 있으면, Then `deviation-log.md`에 allowed category, reviewer decision, follow-up owner가 있어야 하며 없으면 통과하지 못한다.
18. Given deviation disposition을 마감할 때, Then unresolved non-allowed deviation은 blocker이며 "더 예쁘게", "더 modern", "더 card-heavy", "더 marketing-style" redesign은 승인 사유가 아니다.
19. Given source semantics guard를 실행할 때, Then `guard:read-model-contract`는 Instance live/snapshot/trend, stored trend source, forbidden health/rootCause/recovery fields, no client reorder/recalculation을 계속 fail-closed로 검증한다.
20. Given implementation diff를 검토할 때, When `git diff --check`와 final `git status --short --branch --untracked-files=all`을 확인하면, Then backend code/tests, migration/schema, Source of Truth mockup HTML, completed Epic 13 story body/status, `dbml-error.log`, seed `13-ui...` story가 변경되지 않았음이 확인된다.

## Tasks / Subtasks

- [x] 시작 상태와 보호 대상을 확인한다. (AC: 1, 2, 20)
  - [x] `git status --short --branch --untracked-files=all`을 실행하고 `dbml-error.log`, seed `13-ui...` story, Source of Truth mockup HTML, completed Epic 13 story body/status를 보호 대상으로 기록한다.
  - [x] 기준이 HTML mockup뿐임을 completion notes에 명시하고 snapshot picker PNG를 기준으로 삼지 않는다.
  - [x] 변경 범위가 frontend instance modal/trend surface, frontend guard/fixture/static sentinel, Epic 14 QA evidence에 머무는지 확인한다.

- [x] 14.1~14.3 done handoff gate를 구현 시작 checklist로 연결한다. (AC: 13, 17, 18)
  - [x] `conformance-checklist.md`의 Instance Wide Modal, Retention/Source Absence, Source Semantics Guard 항목을 구현 checklist로 사용한다.
  - [x] `no-discretionary-redesign-checklist.md`를 읽고 narrow Sheet 유지, card-heavy redesign, health/rootCause/recovery timeline, fallback CTA를 금지한다.
  - [x] `handoff-gates.md`의 14.4 Gate를 completion notes에 인용하고 각 항목을 `conformant`, `allowed deviation`, `blocker`, `coverage gap` 중 하나로 판정한다.
  - [x] 14.3의 authenticated browser fixture gap은 과장하지 않는다. fixture/runbook이 있으면 닫고, 없으면 known gap으로 남긴다.

- [x] Instance live/snapshot detail을 mockup wide modal grammar로 정렬한다. (AC: 3, 4)
  - [x] `InstancePanels`의 live/snapshot dashboard가 `DialogContent` wide surface로 열리는지 확인하고 narrow Sheet로 회귀하지 않게 한다.
  - [x] Modal width는 mockup `min(1120px, 100%)`에 준하게 유지하고, viewport padding은 mobile에서 horizontal page overflow를 만들지 않게 한다.
  - [x] Header는 sticky 또는 동등한 stable behavior를 제공하고 title/subtitle/close affordance가 clipped 되지 않게 한다.
  - [x] Dialog focus/ESC/ARIA 보강이 필요하면 category 4로 기록하되 visual hierarchy를 해치지 않는다.

- [x] Modal section order와 compact neutral panel grammar를 맞춘다. (AC: 5, 11, 12)
  - [x] `InstanceDashboardSurface`가 context note -> Application state reference -> Read semantics -> selected instance metrics -> endpoint evidence -> resource evidence -> starter connection -> normalized endpoint table 순서를 유지하는지 확인한다.
  - [x] `ApplicationStateReferencePanel`은 lifecycle owner가 application임을 보여주고 selected instance가 Application state를 대체하지 않는 copy를 유지한다.
  - [x] `ReadSemanticsPanel`은 source/window/snapshot flags를 compact info cells로 보여준다.
  - [x] `MetricGrid`는 selected instance scope metric으로 읽히고 application state hero나 health score처럼 보이지 않게 한다.
  - [x] Endpoint/resource/starter sections는 mockup의 thin border panel, compact label, restrained badge language를 따른다.
  - [x] Normalized endpoint evidence table은 modal 내부 horizontal scroll로 제한하고 page-level overflow를 만들지 않는다.

- [x] Snapshot mode note와 retention/source absence UX를 강화한다. (AC: 7, 8, 9)
  - [x] Snapshot context note는 selected Application Snapshot row window 기준임을 첫 문장에 드러낸다.
  - [x] late accepted metric possibility와 stored Application Snapshot override 금지를 짧게 노출한다.
  - [x] `metric_missing`, `not_observed_in_window`, malformed/source absence 상태는 limitation UX로 표현한다.
  - [x] "현재 dashboard 보기", "현재 accepted bucket으로 복원", "문제 없음", "복구 완료" 같은 보정 copy를 넣지 않는다.

- [x] Stored Instance Snapshot Trend를 source-separated surface로 유지한다. (AC: 10)
  - [x] `InstanceTrendView`/`TrendReadyView`는 source가 `dashboard_snapshots.read_model_json.instanceSummary.items[]` projection임을 first-pass scanning 정보로 보여준다.
  - [x] Trend point card가 stored Application state와 instance projection을 보여주더라도 current state, health score, root cause, recovery proof timeline처럼 보이지 않게 한다.
  - [x] Application Snapshot detail CTA와 Instance snapshot dashboard CTA는 source 차이를 copy로 분리한다.
  - [x] Stored trend가 Sheet로 남는 경우 live/snapshot detail wide modal과 source separation이 명확한지 QA 판정에 기록한다.

- [x] Guard/fixture/static sentinel을 보강한다. (AC: 6, 8, 10, 11, 12, 19)
  - [x] `guard:read-model-contract`가 `InstanceDashboardSurface`, `InstancePanels`, `SnapshotDetailSurface`의 source/order/recalculation/forbidden field semantics를 계속 확인하는지 실행한다.
  - [x] 필요하면 14.4 static sentinel을 추가해 `DialogContent` wide width, no narrow Sheet for live/snapshot detail, modal order anchors, stored trend source copy, no `healthScore`/`rootCause`/`recoveryProof`, no `.sort()`/`.toSorted()`/`.reduce()` 회귀를 잡는다.
  - [x] Static grep hit는 guard negative fixture/assertion, explanatory comment, production regression으로 분류해 completion notes에 남긴다.

- [x] End-to-end visual QA evidence를 마감한다. (AC: 13, 14, 15, 16, 17, 18)
  - [x] desktop `1440x1000`, tablet `1024x900`, mobile `390x844`에서 Project rail, Application rail, Main surface, Snapshot picker, Snapshot detail, Instance wide modal, retention/source absence를 확인한다.
  - [x] authenticated fixture/runbook이 있으면 full path `Project -> Application -> Dashboard -> Snapshot -> Instance -> retention expired`를 실행하고 evidence를 남긴다.
  - [x] fixture가 없으면 auth-blocked/code/static evidence 범위와 missing authenticated path를 known gap으로 분리한다.
  - [x] `side-by-side-desktop-1440x1000.md`, `side-by-side-tablet-1024x900.md`, `side-by-side-mobile-390x844.md` 또는 동등한 note에 final conformance 판정을 남긴다.
  - [x] `deviation-log.md`에 새 deviation을 기록하거나 "새 deviation 없음"을 completion notes에 명시한다.

## Candidate Files

- `frontend/src/app/components/instance-panels.tsx`
  - 현재 live/snapshot dashboard는 wide `DialogContent`로 열리고 stored trend는 right `Sheet`로 분리된다.
  - 14.4 primary 후보. Dialog width/header/body scroll, trend Sheet source separation, modal open/close QA를 다룬다.
- `frontend/src/app/components/instance-dashboard-surface.tsx`
  - 현재 Instance Dashboard live/snapshot API를 읽고 context note, Application state reference, Read semantics, metrics, endpoint/resource/starter evidence, normalized endpoint table을 렌더링한다.
  - 14.4 primary 후보. Section order, snapshot note, retention/missing metric limitation, compact neutral panel grammar, table overflow를 mockup 기준으로 다듬는다.
- `frontend/src/app/components/snapshot-detail-surface.tsx`
  - Snapshot detail의 stored source flags와 Instance snapshot drilldown handoff 확인 후보. 14.4에서는 modal entry와 retention/source absence final QA 범위에서만 조정한다.
- `frontend/src/app/components/snapshot-history-panel.tsx`
  - Snapshot/History done handoff 확인 후보. 14.4에서는 end-to-end QA와 Instance modal entry path를 확인한다.
- `frontend/src/app/components/dashboard.tsx`
  - Project rail, Application rail, Main live surface, InstancesPanel entry, SnapshotHistoryPanel order final QA 후보. 14.4에서 shell/main을 재설계하지 않는다.
- `frontend/src/app/components/ui/dialog.tsx`
  - Dialog focus/ARIA/close behavior 확인 후보. Primitive 자체 수정은 신중히 한다.
- `frontend/src/app/components/ui/sheet.tsx`
  - Stored trend Sheet 확인 후보. Live/snapshot Instance detail을 Sheet로 되돌리지 않는다.
- `frontend/src/app/lib/read-model-adapters.ts`
  - 표시용 copy/date/source label helper 후보. source/order/recalculation 의미를 바꾸지 않는다.
- `frontend/src/app/lib/read-model-contract-fixtures.ts`
  - Instance live/snapshot/trend/retention gap fixture와 visual/source sentinel 후보.
- `frontend/src/app/lib/read-model-contract-guard.ts`
  - Instance dashboard/trend guard semantics 후보. backend contract 의미를 새로 만들지 않는다.
- `frontend/scripts/read-model-contract-guard.ts`
  - 14.4 static sentinel 후보.
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/README.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/conformance-checklist.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/deviation-log.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/no-discretionary-redesign-checklist.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/handoff-gates.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/source-semantics-sentinel-review.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/side-by-side-desktop-1440x1000.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/side-by-side-tablet-1024x900.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/side-by-side-mobile-390x844.md`
- `planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html` (read-only reference)
- `planning-artifacts/stories/13-ui-dashboard-source-of-truth-surface-realignment.md` (read-only seed/handoff only)

## Verification Commands

```bash
git status --short --branch --untracked-files=all

cd frontend && npm run guard:read-model-contract
cd frontend && npm run typecheck
cd frontend && npm run build

rg -n "healthScore|rootCause|recoveryProof|instanceState|\\bstateCode\\b|currentState|not_observed.*(정상|문제 없음|복구 완료)|(정상|문제 없음|복구 완료).*not_observed" frontend/src/app frontend/scripts
rg -n "acceptedAtCutoffApplied|includesLateAcceptedMetrics|mayDifferFromStoredApplicationSnapshot|applicationSnapshotRecalculated|instanceEvidenceReconstructedFromMetrics|markerIsStateSource" frontend/src/app frontend/scripts
rg -n "dashboard_snapshots\\.read_model_json\\.instanceSummary\\.items\\[\\]|DialogContent|SheetContent|Normalized endpoint evidence table|selected Application Snapshot|late accepted|stored Application Snapshot" frontend/src/app/components frontend/scripts
rg -n "\\.sort\\(|\\.toSorted\\(|\\.reduce\\(" frontend/src/app/components frontend/src/app/lib frontend/scripts

git diff --check
ruby -e 'require "yaml"; YAML.load_file("implementation-artifacts/sprint-status.yaml"); puts "yaml ok"'
git status --short --branch --untracked-files=all
```

Static grep hit는 guard negative fixture, explanatory source semantics comment, type/source fixture일 수 있다. 구현자는 hit를 무시하지 말고 production user-facing/source semantics regression인지 분류해 completion notes에 남긴다.

## Browser Visual QA Plan

1. `cd frontend && npm run dev -- --host 127.0.0.1`로 local Vite app을 실행한다.
2. Browser/Playwright로 Dashboard route를 연다. 인증 fixture가 없으면 접근 가능한 auth-blocked 화면과 차단 사유를 분리 기록한다.
3. 최소 viewport:
   - desktop: `1440x1000`
   - tablet: `1024x900`
   - mobile: `390x844`
4. Desktop `1440x1000`에서 확인한다.
   - Project rail / Application rail / Main surface composition.
   - Main live surface order와 Snapshot/History same-flow anchor.
   - Instance summary entry가 wide modal을 여는지.
   - Instance modal width, header, body scroll, modal clipping, normalized endpoint table overflow.
   - Snapshot mode modal note와 Read semantics flags.
   - Stored trend source copy와 trend point CTA source separation.
   - Retention/source absence safe state에 fallback CTA/copy 없음.
5. Tablet `1024x900`에서 확인한다.
   - Rail/main adaptation이 유지된다.
   - Modal이 viewport 안에서 stable하게 열리고 header/body가 겹치지 않는다.
   - Application state reference / Read semantics grid가 clipping 없이 stack된다.
   - Normalized endpoint table overflow가 modal 내부로 제한된다.
6. Mobile `390x844`에서 확인한다.
   - horizontal page scroll 없음.
   - Modal header, close affordance, title/subtitle가 clipped 되지 않음.
   - Info cell, badge, section label, note text가 container를 밀지 않음.
   - Table은 modal 내부 horizontal scroll 또는 readable stack으로 처리되고 page overflow를 만들지 않음.
7. Full path smoke:
   - authenticated fixture/runbook이 있으면 `Project -> Application -> Dashboard -> Snapshot -> Instance -> retention expired`를 실행한다.
   - fixture/runbook이 없으면 completion notes에 "not exercised: no authenticated browser fixture/token/runbook available"처럼 정확히 쓴다.
8. Evidence naming convention은 `handoff-gates.md`를 따른다.
9. 각 viewport side-by-side note에는 영역별 `conformant`, `allowed deviation`, `blocker`, `coverage gap` 중 하나를 기록한다.

## Known Gap

- 14.1~14.3 기준 full authenticated browser fixture/runbook이 없어 `Project -> Application -> Dashboard -> Snapshot -> Instance -> retention expired` path를 하나의 browser smoke로 닫은 evidence는 없다.
- 14.3의 browser evidence는 auth-blocked shell 기준 desktop/tablet/mobile horizontal overflow 없음까지만 증명했다. Authenticated Snapshot/History, Snapshot detail, Instance modal, retention/source absence visual conformance는 fixture-backed browser proof가 아니다.
- 14.4에서 fixture/runbook이 있으면 이 gap을 닫는다. 없으면 known gap으로 유지하고, code/static/guard evidence와 authenticated browser conformance를 혼동하지 않는다.

## Dev Notes

- Active implementation baseline은 Traditional MVC + Service/Repository Layering이다. 14.4는 frontend surface/QA story이며 backend MVC layer, read model API, persistence, migration을 변경하지 않는다.
- Frontend root는 `frontend/`이고 React 18.3.1, Vite 6.3.5, TypeScript 5.8.3, Tailwind 4.1.12, Radix/shadcn-style UI, lucide-react를 사용한다. 새 dependency는 기본적으로 추가하지 않는다.
- Public component, helper, 새 JSDoc/comment를 추가할 때는 AGENTS.md 지침에 따라 한국어 주석을 사용한다.
- `guard:read-model-contract`는 Application Dashboard, Snapshot history/detail, Instance live/snapshot/trend, same-flow order의 source/order/recalculation/forbidden field semantics를 감시한다. 14.4 구현 후에도 fail-closed여야 한다.
- `InstancePanels` current state:
  - live/snapshot Instance Dashboard는 `DialogContent` `w-[min(1120px,calc(100vw-2rem))] max-w-none` wide dialog로 열린다.
  - stored trend는 `SheetContent side="right" sm:max-w-[660px]`로 분리된다.
  - 14.4는 live/snapshot detail을 Sheet로 되돌리지 않는다.
- `InstanceDashboardSurface` current state:
  - `buildLiveInstanceDashboardPath` / `buildSnapshotInstanceDashboardPath`를 사용하고 `guardInstanceDashboardReadModel`로 contract를 검증한다.
  - Snapshot mode note는 selected Application Snapshot row window, accepted metrics reconstruction, late-arriving metric possibility를 이미 설명한다. 14.4는 mockup copy intent에 맞춰 concise/visible하게 다듬는다.
  - `ApplicationStateReferencePanel`, `ReadSemanticsPanel`, `MetricGrid`, `EndpointEvidencePanel`, `ResourceEvidencePanel`, `StarterConnectionPanel`, `NormalizedEndpointEvidenceTable`이 modal body order 후보로 존재한다.
- `InstanceTrendView` current state:
  - Trend source copy는 `dashboard_snapshots.read_model_json.instanceSummary.items[] stored projection`을 표시한다.
  - 14.4는 이 surface가 current state/health score/root cause/recovery proof timeline처럼 보이지 않음을 QA한다.
- `read-model-contract-guard.ts` current state:
  - Instance Dashboard root/deep forbidden fields로 `state`, `stateCode`, `health`, `lifecycleState`, `instanceState`, `currentState`, `healthScore`, `cause`, `rootCauseCandidate`, `recoveryProof`, `rootCause`, `endpointPriority`, `instanceSummary`를 거부한다.
  - Snapshot mode는 `acceptedAtCutoffApplied=false`, `includesLateAcceptedMetrics=true`, `mayDifferFromStoredApplicationSnapshot=true`, `applicationSnapshotRecalculated=false`, `instanceEvidenceReconstructedFromMetrics=true`, `markerIsStateSource=false`를 요구한다.
  - Instance Snapshot Trend는 `dashboard_snapshots.read_model_json.instanceSummary.items` source와 no forbidden decision fields를 검증한다.
- HTML mockup responsive behavior는 `@media (max-width: 860px)`에서 `.modal-grid`를 1열로 전환한다. Production도 mobile에서 order를 보존하며 clipping/page overflow를 피해야 한다.

## References

- `_bmad/custom/project-context.md`
- `planning-artifacts/epic-14-dashboard-mockup-design-parity.md#Story 14.4 - Instance Wide Modal And End-To-End Visual QA`
- `planning-artifacts/stories/14-1-design-parity-baseline-and-visual-guardrails.md`
- `planning-artifacts/stories/14-2-dashboard-shell-rails-and-live-surface-realignment.md`
- `planning-artifacts/stories/14-3-snapshot-history-detail-and-retention-surface-realignment.md`
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
- `planning-artifacts/stories/13-ui-dashboard-source-of-truth-surface-realignment.md` (read-only seed/handoff only)

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- `git status --short --branch --untracked-files=all` 시작/종료 상태 확인. 기존 `dbml-error.log`와 seed `13-ui...` story는 보호 대상으로 유지했다.
- RED: 14.4 static sentinel 추가 직후 `cd frontend && npm run guard:read-model-contract`가 snapshot no-override copy/order sentinel 부재로 실패함을 확인했다.
- GREEN: Instance modal body order와 sticky header 구현 후 `cd frontend && npm run guard:read-model-contract` 통과.
- 최종 검증: `cd frontend && npm run guard:read-model-contract`, `cd frontend && npm run typecheck`, `cd frontend && npm run build`, `git diff --check`, sprint YAML parse 통과.
- Browser visual QA: Vite dev server `http://127.0.0.1:5173/`에서 desktop `1440x1000`, tablet `1024x900`, mobile `390x844` auth-blocked `/dashboard`를 캡처하고 no horizontal overflow를 기록했다.

### Completion Notes List

- 기준은 오직 `planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html`이다. `source-of-truth-dashboard-snapshot-picker.png`는 기준으로 삼지 않았다.
- 보호 범위 준수: backend code/tests, migration/schema, Source of Truth mockup HTML, completed Epic 13 story body/status, `dbml-error.log`, seed `planning-artifacts/stories/13-ui-dashboard-source-of-truth-surface-realignment.md`는 수정하지 않았다.
- `InstancePanels` live/snapshot detail은 wide `DialogContent` `w-[min(1120px,calc(100vw-2rem))]`를 유지하고, sticky header와 body scroll containment를 보강했다. Stored trend는 right `Sheet`로 남겨 source separation을 유지했다.
- `InstanceDashboardSurface` body order를 context note -> Application state reference -> Read semantics -> selected instance metrics -> endpoint evidence -> resource evidence -> starter connection -> normalized endpoint evidence table로 정렬했다. 기존 extra body header panel은 context note로 흡수했다.
- Snapshot context note는 selected Application Snapshot row window, accepted metric reconstruction, late accepted metric 가능성, stored Application Snapshot state/evidence override/검증/대체 금지를 명시한다.
- Read semantics는 `mode`, `source`, `window`, snapshot flags를 compact info cells로 보여주고, Application state reference는 lifecycle owner가 application이며 instance top-level state가 없음을 표시한다.
- Stored Instance Snapshot Trend는 `dashboard_snapshots.read_model_json.instanceSummary.items[]` stored projection copy를 유지하며 current state/health score/root cause/recovery proof timeline처럼 보이게 하지 않았다.
- 14.4 static sentinel은 wide Dialog, sticky header, modal order anchors, no extra `ContextHeader`, snapshot no-override copy, source/mode/window cells, explicit no instance top-level state, stored trend source copy를 fail-closed로 검증한다.
- Static grep hit 분류: `healthScore`/`rootCause`/`recoveryProof`/`currentState`/`stateCode` hit는 guard negative fixtures/assertions, stored Snapshot fields, type/source fields이며 production Instance Dashboard regression은 아니다. `.sort()`/`.toSorted()`/`.reduce()` hit는 없다.
- Handoff gate 판정: Instance wide modal은 code/static `conformant`; modal order는 `conformant`; snapshot mode note는 `conformant`; stored trend source separation은 `conformant`; final viewport evidence는 auth-blocked browser + code/static evidence로 기록했으며 authenticated visual proof는 `coverage gap`; unresolved non-allowed deviation은 없다.
- 새 deviation 없음. `deviation-log.md`에는 14.4에서 추가 deviation이 없음을 기록했고, authenticated Instance modal/retention path 미검증은 deviation이 아니라 QA coverage gap으로 유지했다.
- `.private/smoke-auth.env` access token fixture가 없어 full authenticated `Project -> Application -> Dashboard -> Snapshot -> Instance -> retention expired` browser path는 실행하지 않았다. Completion evidence에서 이 path를 닫았다고 주장하지 않는다.

### BMAD Code Review

- 2026-06-11 review outcome: blocking/high/medium/low finding 없음. Instance live/snapshot detail wide Dialog, modal section order, snapshot semantics copy, stored trend source separation, retention no-fallback copy, static sentinel, deviation disposition, story/sprint review status를 Story 14.4 acceptance criteria 기준으로 확인했다.
- Verification: `git status --short --branch --untracked-files=all`, `cd frontend && npm run guard:read-model-contract`, `cd frontend && npm run typecheck`, `cd frontend && npm run build`, `git diff --check`, sprint YAML parse 통과.
- Review Follow-ups: authenticated fixture/runbook 부재로 `Project -> Application -> Dashboard -> Snapshot -> Instance -> retention expired` browser visual path는 계속 coverage gap이다. 현재 evidence는 auth-blocked viewport + code/static guard proof이며 authenticated modal/retention visual conformance로 과장하지 않는다.

### File List

- `frontend/src/app/components/instance-panels.tsx`
- `frontend/src/app/components/instance-dashboard-surface.tsx`
- `frontend/scripts/read-model-contract-guard.ts`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/README.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/deviation-log.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/source-semantics-sentinel-review.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/side-by-side-desktop-1440x1000.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/side-by-side-tablet-1024x900.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/side-by-side-mobile-390x844.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/guard-14-4-instance-wide-modal-and-end-to-end-visual-qa-20260611-2000.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/browser-14-4-instance-wide-modal-and-end-to-end-visual-qa-observations.json`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/current-14-4-dashboard-auth-blocked-desktop-1440x1000.png`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/current-14-4-dashboard-auth-blocked-tablet-1024x900.png`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/current-14-4-dashboard-auth-blocked-mobile-390x844.png`
- `planning-artifacts/stories/14-4-instance-wide-modal-and-end-to-end-visual-qa.md`
- `implementation-artifacts/sprint-status.yaml`

## Change Log

| Date | Change |
|---|---|
| 2026-06-11 | Epic 14 Story 14.4 Instance Wide Modal And End-To-End Visual QA story artifact를 생성하고 ready-for-dev로 전환했다. |
| 2026-06-11 | Instance wide modal order/sticky header/snapshot note/source sentinel/final visual QA evidence를 구현하고 story와 sprint status를 review로 전환했다. |
