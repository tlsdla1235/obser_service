---
artifactType: story
storyId: "14.3"
storyKey: "14-3-snapshot-history-detail-and-retention-surface-realignment"
epic: "Epic 14. Dashboard Mockup Design Parity"
title: "Snapshot, History, Detail, And Retention Surface Realignment"
architectureStyle: Traditional MVC
status: done
date: 2026-06-11
workType: frontend
implementationScope: "Snapshot/History picker, stored Snapshot detail, retention expired/source absence UI strict mockup conformance"
productionCodeChangeThisContext: true
plannedProductionCodeChange: true
sourceOfTruthMode: read-only
rollbackBoundary: "frontend snapshot/history/detail surfaces, frontend guard/fixture/static sentinel, and Epic 14 QA evidence only"
---

# Story 14.3 - Snapshot, History, Detail, And Retention Surface Realignment

## Status

done

2026-06-11: BMAD create-story 흐름으로 Epic 14의 세 번째 story artifact를 생성한다. 이번 컨텍스트에서는 production code, frontend implementation, backend code/tests, migration/schema, Source of Truth mockup HTML, 완료된 Epic 13 story 본문/status, 기존 untracked `dbml-error.log`를 수정하지 않는다.

2026-06-11: 기준은 오직 `planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html`이다. `planning-artifacts/source-of-truth/source-of-truth-dashboard-snapshot-picker.png`는 기준으로 삼지 않는다. `planning-artifacts/stories/13-ui-dashboard-source-of-truth-surface-realignment.md`가 있으면 read-only seed/handoff 자료로만 참고한다.

2026-06-11: Story 14.3 구현을 완료하고 review로 전환한다. Snapshot/History picker, selected snapshot summary, stored Snapshot detail top surface, no-fallback safe copy, 14.3 static sentinel, QA evidence를 업데이트했다. Authenticated browser fixture 부재로 full authenticated Snapshot/History/Snapshot detail/retention path는 coverage gap으로 유지한다.

2026-06-11: BMAD code review 2차 quick-dev fix로 14일 date/slot map을 active preset marker limit이 아니라 14d retention marker query 기준으로 채우고, `horizon.until=00:00Z` boundary를 slot day 기준으로 맞췄다. Static sentinel은 문자열 확인뿐 아니라 slot/date boundary helper를 실행해 검증한다. Authenticated browser fixture 부재는 coverage gap으로 유지하고, Story 14.3을 done으로 닫는다.

## Story

frontend 구현자로서, 실제 Vite Dashboard의 Snapshot/History picker, stored Snapshot detail, retention expired/source absence surface를 HTML mockup의 marker-first 30분 point 탐색과 no-fallback visual grammar에 맞춰 정렬하고 싶다.

그래야 운영자가 Application Dashboard live surface 아래에서 14일 retention 안의 30분 scheduled dashboard point를 날짜/slot 중심으로 찾고, 선택한 snapshot은 `dashboard_snapshots.read_model_json` 저장본으로 복원된 dashboard-like surface에서 확인하며, expired/404/source absence는 live/current fallback 없이 안전한 empty/error state로 이해할 수 있다.

## Background

Epic 13은 `accepted_metric_buckets`, `recent_30_minutes`, `dashboard_snapshots.read_model_json`, selected snapshot instance semantics, retention expired no-fallback 의미를 이미 닫았다. Epic 14는 이 의미를 재정의하지 않고, 실제 Vite Dashboard UI를 `source-of-truth-dashboard-mockup.html`의 IA, layout hierarchy, compact neutral panel grammar, density, spacing rhythm, information ordering에 맞추는 conformance epic이다.

Story 14.1은 `implementation-artifacts/epic-14-dashboard-design-parity-qa/` 아래에 conformance checklist, no-discretionary-redesign checklist, deviation log, gap map, handoff gates, source semantics sentinel review를 만들었다. Story 14.2는 Project rail / Application rail / Main live surface 기대 UI/UX를 code/static/auth-blocked shell evidence 기준으로 구현하고 done으로 닫았다. 다만 full authenticated browser fixture/runbook이 없어 authenticated Dashboard strict visual conformance와 `Project -> Application -> Dashboard -> Snapshot -> Instance -> retention expired` path는 여전히 coverage gap이다. 14.3 completion은 이 gap을 과장하지 않는다.

현재 frontend에는 `SnapshotHistoryPanel`과 `SnapshotDetailSurface`가 있고, 14.2 이후 `DashboardMain`은 Snapshot/History를 tab-only surface가 아니라 live main flow 하단 handoff anchor로 유지한다. 14.3은 이 구조를 버리지 않고 mockup의 `Snapshot / History`, `.retention-grid`, `.date-heatmap`, `.slot-grid`, selected summary, snapshot mode top flags, retention safe copy에 맞춰 visual hierarchy와 interaction을 재정렬한다.

Pixel-perfect DOM/CSS byte-level clone은 non-goal이다. 그러나 이는 loose similarity를 뜻하지 않는다. Snapshot/History IA, marker-first interaction, compact neutral panel grammar, visual density, information ordering, Snapshot detail dashboard-like skeleton, retention expired/source absence no-fallback state는 strict conformance target이다.

## Source Of Truth

아래 문서는 read-only 기준이다. 구현자는 의미를 재정의하지 않고 14.3 구현 지침과 completion gate로만 사용한다.

1. `_bmad/custom/project-context.md`
2. `planning-artifacts/epics.md`
3. `planning-artifacts/epic-14-dashboard-mockup-design-parity.md`
4. `planning-artifacts/stories/14-1-design-parity-baseline-and-visual-guardrails.md`
5. `planning-artifacts/stories/14-2-dashboard-shell-rails-and-live-surface-realignment.md`
6. `planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html`
7. `implementation-artifacts/sprint-status.yaml`
8. `implementation-artifacts/epic-14-dashboard-design-parity-qa/conformance-checklist.md`
9. `implementation-artifacts/epic-14-dashboard-design-parity-qa/deviation-log.md`
10. `implementation-artifacts/epic-14-dashboard-design-parity-qa/no-discretionary-redesign-checklist.md`
11. `implementation-artifacts/epic-14-dashboard-design-parity-qa/mockup-principles-and-gap-map.md`
12. `implementation-artifacts/epic-14-dashboard-design-parity-qa/handoff-gates.md`
13. `implementation-artifacts/epic-14-dashboard-design-parity-qa/source-semantics-sentinel-review.md`
14. `planning-artifacts/stories/13-ui-dashboard-source-of-truth-surface-realignment.md`가 있으면 seed/handoff 자료로만 참고한다.

`source-of-truth-dashboard-snapshot-picker.png`는 기준이 아니다. HTML mockup의 visible UI, interaction hierarchy, copy intent, responsive behavior만 기준으로 삼는다. Mockup의 `.prototype-controls`, `#scenarioSelect`, `data-prototype-mode`, hard-coded `scenarios`, temporary JavaScript `state`, demo render runtime은 production requirement가 아니다.

## Aligns / Hardens / Visualizes

### Aligns

- `13-7-frontend-snapshot-history-detail-realignment`: Snapshot history/detail을 marker-first 30분 point 탐색과 stored read model 복원 surface로 정렬한 의미를 visual conformance 대상으로 삼는다.
- `13-10-retention-cleanup-alignment`: 14일 retention UX와 physical cleanup 기준을 유지하고, cleanup으로 사라진 snapshot은 live dashboard/current accepted bucket으로 복원하지 않는다.
- `14-1-design-parity-baseline-and-visual-guardrails`: conformance checklist, deviation log, no discretionary redesign checklist, handoff gate를 14.3 completion gate로 사용한다.
- `14-2-dashboard-shell-rails-and-live-surface-realignment`: Snapshot/History가 `DashboardMain` same-flow anchor로 유지된 상태를 이어받고, Project rail / Application rail / Main live surface를 재작업하지 않는다.

### Hardens

- `dashboard_snapshots.read_model_json`은 Snapshot detail source다. `accepted_metric_buckets` live/current fallback으로 stored detail을 보정하지 않는다.
- `markerBucket`과 date/slot 색은 timeline 탐색 색인이다. lifecycle state, evidence source, health summary로 보이게 하지 않는다.
- `currentWindowEndUtc`는 30분 slot identity다. `capturedAt`/`generatedAt`은 provenance 또는 tie-breaker 표시로만 둔다.
- `captureReason=hourly_scheduled` persisted/API token은 바꾸지 않는다. user-facing copy만 "30분 scheduled" 또는 "30분 정기 저장" 의미로 표시한다.
- Operational event feed가 존재하더라도 marker/date/slot 탐색 아래의 secondary/collapsible context로 둔다.
- raw snapshot explorer, endpoint timeseries, arbitrary query UI, operational event feed가 primary surface가 되면 blocker다.

### Visualizes

- 14일 retention summary, 30분 scheduled point count, 48/day, 672 total point, default 24h, cleanup/expiry hint를 first-pass scanning 정보로 배치한다.
- Date map은 날짜별 markerBucket 요약으로 보이되 state source로 보이지 않게 한다.
- 하루 drilldown은 48-slot grid로 보이고 desktop/tablet/mobile에서 clipping, horizontal overflow, text overlap 없이 안정적이어야 한다.
- Selected snapshot summary는 `snapshotId`, `capturedAt`, `currentWindowEndUtc`, `markerBucket`, stored state, capture reason을 분리해 보여준다.
- Snapshot detail top surface에는 `mode=snapshot`, `source=dashboard_snapshots.read_model_json`, `snapshotDetailRecalculates=false`, `currentStateRecalculated=false`, `markerIsStateSource=false`, captured/window provenance가 보인다.
- Retention expired/404/source absence는 safe empty/error state로 수렴하고 live/current fallback CTA나 copy를 제공하지 않는다.

## Mockup Conformance Targets

### Snapshot / History picker

- Main live flow 안의 `Snapshot / History` section은 mockup처럼 marker-first 30분 dashboard point 탐색으로 읽혀야 한다.
- Summary grid는 retention `14일`, scheduled points `672`, cadence `30분`, `48/day`, default `24h`, cleanup/expiry `14일 이후`를 한눈에 보여준다.
- Preset control은 24h/7d/14d를 production API query로 유지하되, default view가 24h이고 전체 14일 retention 안에서 작동한다는 copy를 유지한다.
- Date map은 14일 rolling horizon의 날짜별 markerBucket summary다. 날짜 색은 해당 날짜의 가장 높은 markerBucket 요약이며, 실제 snapshot state/evidence source는 slot open 후 복원되는 stored read model이다.
- 하루 drilldown은 48개 30분 slot을 안정 grid로 보여준다. Desktop에서는 mockup의 8열 밀도에 준하고, mobile에서는 물리 제약에 따라 1~4열 adaptation을 허용할 수 있으나 순서, slot identity, no overflow를 보존하고 category 2 deviation으로 기록한다.
- Slot label은 `currentWindowEndUtc` 기준이다. `capturedAt`은 저장 provenance로만 노출한다.
- Selected snapshot summary는 chips/badges로 `snapshotId`, `capturedAt`, `currentWindowEndUtc`, `markerBucket`, stored state, capture reason을 분리한다.
- Server marker order list가 남는 경우 marker/date/slot 탐색보다 visual priority가 낮아야 하며, raw snapshot explorer처럼 보이면 blocker다.
- Operational event feed는 secondary/collapsible context로만 보인다.

### Snapshot detail

- Snapshot detail은 live dashboard와 같은 dashboard-like skeleton을 공유한다. 단, source와 flags는 snapshot mode를 명확히 드러낸다.
- Top surface는 mockup `Application Dashboard / Snapshot` branch처럼 title/subtitle/mode badge/info grid를 먼저 보여준다.
- Required top signals:
  - `mode=snapshot`
  - `source=dashboard_snapshots.read_model_json`
  - selected `snapshotId`
  - `capturedAt`
  - `currentWindowEndUtc`
  - current window start/end
  - `captureReason`
  - `snapshotDetailRecalculates=false`
  - `currentStateRecalculated=false`
  - `markerIsStateSource=false`
- Stored state/operator summary/data quality/state reasons/attention evidence/first look candidates/bounded endpoint evidence는 stored read model에서만 표시한다.
- Snapshot detail은 raw `read_model_json` dump나 arbitrary JSON explorer가 아니다. 필요한 technical detail은 collapsed/secondary surface로 유지한다.
- Instance Snapshot Dashboard CTA는 selected snapshot instance semantics로 이어지는 affordance일 뿐, stored Application Snapshot state/evidence를 검증하거나 대체한다는 copy를 쓰지 않는다.

### Retention expired / 404 / source absence

- Snapshot history marker 없음, detail 404, malformed/source absence, retention expired는 safe empty/error state로 수렴한다.
- Copy는 "보관 기간 안에서 찾을 수 없음", "보관 기간이 지났거나 저장된 snapshot을 찾을 수 없음", "저장본 부재" 계열로 유지한다.
- Live dashboard 보기, current accepted bucket으로 복원, current instance evidence로 보정, 현재 상태로 대체 같은 CTA/copy는 blocker다.
- `metric_missing`, `not_observed_in_window`, source absence는 evidence limitation으로 표현한다.
- "정상 확정", "문제 없음", "복구 완료" copy로 retention/source absence를 보정하지 않는다.

## Source Semantics Guard

14.3은 아래 의미를 재정의하지 않는다.

- Application Dashboard live source는 `accepted_metric_buckets` + `recent_30_minutes`다.
- Snapshot detail source는 `dashboard_snapshots.read_model_json`이다.
- Snapshot marker/history는 `dashboard_snapshots` helper/index row이며 state source가 아니다.
- `dashboard_snapshots.read_model_json`은 stored dashboard read model source다.
- selected snapshot instance semantics는 selected Application Snapshot row metadata와 selected instance `accepted_metric_buckets` evidence reconstruction을 분리한다.
- Retention expired/source absence는 live/current fallback 없이 처리한다.

UI는 아래를 새로 계산하지 않는다.

- lifecycle state
- endpoint priority
- p95/p99
- histogram percentile
- root cause
- health score
- marker-as-state
- retention fallback
- current dashboard/current accepted bucket fallback

## Deviation And Blocker Rules

Mockup과 다른 Snapshot/History interaction, Snapshot detail skeleton, retention expired/source absence surface 선택이 있으면 `implementation-artifacts/epic-14-dashboard-design-parity-qa/deviation-log.md`에 기록해야 한다.

Deviation entry는 최소한 아래 field를 포함한다.

| Field | Required meaning |
|---|---|
| Mockup element | HTML mockup 기준 element/section/interaction. 가능한 경우 class/id/visible label을 함께 적는다. |
| Production element | 실제 Vite Dashboard component/surface. |
| Reason | 왜 동일하게 이식할 수 없거나 이식하지 않아야 하는지 구체적으로 적는다. |
| Allowed category | 1/2/3/4 중 하나. 해당하지 않으면 `blocker`다. |
| Reviewer decision | Approved / rejected / needs follow-up 중 하나와 reviewer note. |
| Follow-up owner | 14.3 / 14.4 / named owner. |

Allowed category:

1. Production data/read model 때문에 mockup demo data를 그대로 쓸 수 없는 경우.
2. Responsive/mobile에서 물리적으로 동일 배치가 불가능한 경우.
3. Prototype controls, hard-coded JS demo data, temporary mockup runtime처럼 production에 넣으면 안 되는 목업 전용 요소인 경우.
4. 접근성/키보드/focus/ARIA를 위해 시각 구조를 해치지 않는 보강인 경우.

아래는 blocker다.

- Mockup보다 "더 예쁘게", "더 현대적으로", "더 카드스럽게", "더 마케팅스럽게" 보이도록 layout hierarchy, density, color grammar, information order를 임의 변경한다.
- Snapshot/History를 operational event feed, raw snapshot explorer, endpoint timeseries, arbitrary query UI 중심으로 바꾼다.
- Date map/slot 색을 lifecycle state source로 설명한다.
- Snapshot detail을 current dashboard/current accepted bucket/current threshold/current starter state로 보정한다.
- Retention expired/source absence에 live/current fallback CTA를 넣는다.

## Acceptance Criteria

1. Given 구현자가 14.3을 시작할 때, When `git status --short --branch --untracked-files=all`을 실행하면, Then 기존 untracked `dbml-error.log`와 seed `planning-artifacts/stories/13-ui-dashboard-source-of-truth-surface-realignment.md`는 수정, 삭제, stage하지 않는 보호 대상으로 확인된다.
2. Given Snapshot/History section을 볼 때, When live main flow를 위에서 아래로 읽으면, Then marker/date/slot 탐색이 primary surface이고 operational event feed가 있으면 secondary/collapsible context로만 보인다.
3. Given 14일 history를 볼 때, When retention summary를 확인하면, Then retention days, 30분 scheduled point count, 48/day, 672 total point, default 24h, cleanup/expiry hint가 mockup의 retention summary처럼 scan 가능하다.
4. Given date map을 볼 때, When 날짜 cell 색과 summary를 읽으면, Then 날짜별 markerBucket 요약으로 보이고 lifecycle state/evidence source로 보이지 않는다.
5. Given 하루 drilldown을 볼 때, When desktop/tablet/mobile viewport를 확인하면, Then 48-slot grid가 clipping, text overlap, horizontal page overflow 없이 안정적으로 보인다.
6. Given date map과 slot grid를 조작할 때, When slot을 선택하면, Then `currentWindowEndUtc` 30분 slot identity가 유지되고 `capturedAt`/`generatedAt`은 provenance/tie-breaker 표시로만 보인다.
7. Given selected snapshot summary를 볼 때, When snapshot이 선택되면, Then `snapshotId`, `capturedAt`, `currentWindowEndUtc`, `markerBucket`, stored state, capture reason이 분리된 compact chips/info cells로 보인다.
8. Given `captureReason=hourly_scheduled`를 표시할 때, When user-facing label을 렌더링하면, Then label은 30분 scheduled 의미이며 persisted/API token은 바꾸지 않는다.
9. Given Snapshot detail을 열 때, When top surface를 확인하면, Then live dashboard와 같은 dashboard-like skeleton에서 `mode=snapshot`, `source=dashboard_snapshots.read_model_json`, `snapshotDetailRecalculates=false`, `currentStateRecalculated=false`, `markerIsStateSource=false`, captured/window provenance가 보인다.
10. Given Snapshot detail body를 읽을 때, When stored dashboard content가 표시되면, Then state/operatorSummary/dataQuality/stateReasons/attentionEvidence/firstLookCandidates/bounded endpoint evidence는 stored `read_model_json`에서만 오고 current dashboard/current accepted bucket으로 보정되지 않는다.
11. Given Snapshot detail technical section이 있을 때, When 사용자가 열어보면, Then source/readSemantics 확인을 돕는 collapsed/secondary 정보이며 raw snapshot explorer나 arbitrary query UI가 primary surface가 아니다.
12. Given retention expired/404/source absence를 볼 때, When marker/detail/source load가 실패하면, Then live dashboard/current accepted bucket/current instance evidence fallback CTA나 copy 없이 safe empty/error state로 수렴한다.
13. Given responsive QA를 수행할 때, When date cell, slot cell, selected snapshot summary, stored note, error state text를 확인하면, Then 겹치거나 잘리지 않고 mobile `390x844`에서도 horizontal page scroll이 없다.
14. Given mockup과 다른 Snapshot/History interaction, detail skeleton, retention expired surface 선택이 있으면, When completion notes를 작성하면, Then 14.1 deviation log에 allowed category, reviewer decision, follow-up owner가 기록되어야 하며 discretionary redesign은 blocker다.
15. Given source semantics guard를 실행할 때, When frontend guard/static grep을 확인하면, Then lifecycle state, endpoint priority, p95/p99, histogram percentile, root cause, health score, marker-as-state, retention fallback을 UI에서 새로 계산하지 않는다.
16. Given authenticated fixture가 없을 때, When browser visual QA를 수행하면, Then 가능한 auth-blocked/static/code evidence 범위만 기록하고 full authenticated Snapshot/History/Snapshot detail/retention path를 닫았다고 주장하지 않는다.
17. Given implementation diff를 검토할 때, When `git diff --check`와 final `git status --short --branch --untracked-files=all`을 확인하면, Then backend code/tests, migration/schema, Source of Truth mockup HTML, completed Epic 13 story body/status, `dbml-error.log`, seed `13-ui...` story가 변경되지 않았음이 확인된다.

## Tasks / Subtasks

- [x] 시작 상태와 보호 대상을 확인한다. (AC: 1, 17)
  - [x] `git status --short --branch --untracked-files=all`을 실행하고 기존 untracked `dbml-error.log`와 seed `13-ui...` story를 보호 대상으로 기록한다.
  - [x] 변경 범위가 frontend snapshot/history/detail surface, frontend guard/fixture/static sentinel, Epic 14 QA evidence에 머무는지 확인한다.
  - [x] backend code/tests, migration/schema, Source of Truth mockup HTML, completed Epic 13 story body/status를 수정하지 않는다.

- [x] 14.1/14.2 handoff gate를 구현 시작 checklist로 연결한다. (AC: 14, 16, 17)
  - [x] `conformance-checklist.md`의 Snapshot/History, Snapshot Detail, Retention/Source Absence, Source Semantics Guard 항목을 구현 checklist로 사용한다.
  - [x] `no-discretionary-redesign-checklist.md`를 읽고 snapshot surface를 raw explorer/card-heavy UI로 재해석하지 않는다.
  - [x] `handoff-gates.md`의 14.3 Gate를 completion notes에 인용하고 각 항목을 `conformant`, `allowed deviation`, `blocker`, `coverage gap` 중 하나로 판정한다.
  - [x] 14.2의 authenticated strict visual conformance gap을 유지한다. fixture가 없으면 14.3도 full authenticated path를 닫았다고 쓰지 않는다.

- [x] Snapshot/History summary를 mockup retention summary로 정렬한다. (AC: 2, 3)
  - [x] `SnapshotHistoryPanel`의 top area가 marker-first 30분 dashboard point 탐색임을 드러내도록 section label/subtitle/copy를 정렬한다.
  - [x] 14일 retention, 30분 cadence, 48/day, 672 total point, default 24h, cleanup/expiry hint를 first-pass scanning grid로 배치한다.
  - [x] 현재 `marker limit`만 보이는 summary가 mockup의 scheduled/retention/cleanup hierarchy를 충분히 드러내는지 확인한다.
  - [x] Preset buttons는 보조 event/server marker list의 production API query로 유지하되, primary date/slot map은 14d retention marker query로 채워 default 24h와 14일 retention boundary가 모순되지 않게 한다.

- [x] Date map과 48-slot drilldown을 marker-first interaction으로 다듬는다. (AC: 4, 5, 6, 13)
  - [x] Date cell 색은 날짜별 highest markerBucket summary임을 copy와 visual hierarchy로 분명히 한다.
  - [x] Date map은 state source처럼 보이지 않게 `markerBucket`, `탐색 색인`, `stored read model에서 복원` copy를 유지한다.
  - [x] 48-slot grid는 `currentWindowEndUtc` slot identity를 유지하고, disabled/no point slot도 source absence로 오해되지 않게 표현한다.
  - [x] Desktop `1440x1000`, tablet `1024x900`, mobile `390x844` responsive risk는 code/static guard와 auth-blocked shell overflow 기준으로 확인한다. Authenticated slot cell clipping, selected summary, stored note, error state visual proof는 fixture 부재로 coverage gap에 남긴다.
  - [x] Mobile에서 8열 유지가 물리적으로 불가능하면 순서 보존 responsive grid로 조정하고 category 2 deviation을 기록한다.

- [x] Selected snapshot summary와 snapshot open affordance를 mockup처럼 분리한다. (AC: 7, 8)
  - [x] Summary는 `snapshotId`, `capturedAt`, `currentWindowEndUtc`, `markerBucket`, stored state, capture reason을 분리해 보여준다.
  - [x] `captureReason=hourly_scheduled` persisted/API token은 유지하고 user-facing copy는 "30분 scheduled" 또는 "30분 정기 저장"으로 표시한다.
  - [x] Stored dashboard detail CTA는 markerBucket이 state source가 아니라는 note와 함께 보여준다.
  - [x] Server marker order list가 남으면 secondary list로 낮추고 raw snapshot explorer처럼 보이지 않게 한다.

- [x] Operational event feed를 secondary/collapsible context로 유지한다. (AC: 2, 11)
  - [x] Operational event feed가 marker/date/slot picker보다 위에 오거나 더 큰 primary surface가 되지 않게 한다.
  - [x] Event에서 snapshot detail로 가는 link는 stored detail anchor로만 동작하며 endpoint timeseries/arbitrary query UI로 확장하지 않는다.
  - [x] Event list가 필요한 경우 `details`/collapsible 또는 낮은 visual weight로 유지한다.

- [x] Snapshot detail을 dashboard-like skeleton으로 realign한다. (AC: 9, 10, 11)
  - [x] `SnapshotDetailSurface` top surface를 mockup `Application Dashboard / Snapshot` branch에 맞춰 title/subtitle/mode badge/info grid 우선 구조로 다듬는다.
  - [x] Required top signals: `mode=snapshot`, `source=dashboard_snapshots.read_model_json`, `snapshotId`, `capturedAt`, `currentWindowEndUtc`, `window`, `captureReason`, `snapshotDetailRecalculates=false`, `currentStateRecalculated=false`, `markerIsStateSource=false`.
  - [x] Stored state, operator summary, data quality, state reasons, attention evidence, first look candidates, bounded endpoint evidence를 dashboard-like section order로 정리한다.
  - [x] Current dashboard/current accepted bucket/current threshold/current starter state로 missing stored field를 보정하지 않는다.
  - [x] Technical details는 collapsed/secondary surface로 유지하고 raw `read_model_json` explorer를 primary로 만들지 않는다.

- [x] Retention expired/source absence safe states를 no-fallback grammar로 강화한다. (AC: 12, 13)
  - [x] History marker 404/empty, detail 404, source absence/malformed state copy를 safe empty/error state로 통일한다.
  - [x] "live dashboard/current accepted bucket으로 복원하지 않습니다" 의미를 retention expired/source absence에서 유지한다.
  - [x] Retry CTA가 있더라도 current/live fallback CTA처럼 보이지 않게 한다. "현재 dashboard 보기" 같은 복원 유도 copy는 넣지 않는다.
  - [x] `metric_missing`, `not_observed_in_window`, source absence는 evidence limitation으로 표현하고 정상/복구를 단정하지 않는다.

- [x] Guard/fixture/static sentinel을 유지 또는 보강한다. (AC: 8, 10, 12, 15)
  - [x] `guard:read-model-contract`가 Snapshot history horizon/source/order, Snapshot detail stored source, flags, marker/state boundary를 계속 fail-closed로 검증하는지 확인한다.
  - [x] 필요한 경우 `frontend/scripts/read-model-contract-guard.ts`에 14.3 static sentinel을 추가해 fallback copy, raw explorer, endpoint timeseries, arbitrary query, hourly scheduled user-facing regression을 잡는다.
  - [x] `.sort()`, `.toSorted()`, `.reduce()`를 이용한 client-side reorder/recalculation을 추가하지 않는다.
  - [x] `humanizeCaptureReason("hourly_scheduled")`가 user-facing 30분 scheduled 의미를 유지하는지 확인한다.

- [x] Deviation log와 QA evidence를 업데이트한다. (AC: 14, 16, 17)
  - [x] Mockup과 다른 Snapshot/History interaction, detail skeleton, retention state가 있으면 `deviation-log.md`에 allowed category, reviewer decision, follow-up owner를 기록한다.
  - [x] Desktop/tablet/mobile screenshot 또는 observation note를 `implementation-artifacts/epic-14-dashboard-design-parity-qa/` naming convention으로 저장한다.
  - [x] authenticated fixture가 없으면 auth-blocked/static/code evidence 범위와 missing authenticated path를 Known Gap으로 분리한다.

## Candidate Files

- `frontend/src/app/components/snapshot-history-panel.tsx`
  - 현재 `SnapshotHistoryPanel`은 24h/7d/14d preset, history marker/event fetch, marker-first date map, 48-slot grid, server marker order list, secondary operational event `details`, embedded `SnapshotDetailSurface`를 갖는다.
  - 14.3의 primary 작업 후보다. Summary grid, date map density, selected summary, secondary list priority, empty/error copy, mobile slot grid stability를 mockup 기준으로 정렬한다.
- `frontend/src/app/components/snapshot-detail-surface.tsx`
  - 현재 stored snapshot detail API를 guard로 검증하고 source/flags, stored state/operator summary/data quality/stateReasons/attentionEvidence/firstLookCandidates/endpoint evidence/instance summary를 렌더링한다.
  - 14.3에서는 top surface와 dashboard-like skeleton, no-fallback error state, technical detail priority를 mockup 기준으로 다듬는다.
- `frontend/src/app/components/dashboard.tsx`
  - 14.2 이후 `DashboardMain`은 `DashboardContext -> DataQualityFreshnessStrip -> LifecycleStateHero -> DirectStateReasonsPanel -> AttentionAndFirstLookPanel -> EndpointResourceEvidencePanel -> MetricDetailSection -> StarterConnectionStrip -> InstancesPanel -> SnapshotHistoryPanel` same-flow order를 갖는다.
  - 14.3에서는 Snapshot/History anchor를 유지하는 범위에서만 조정한다. Project rail / Application rail / Main live surface를 다시 재설계하지 않는다.
- `frontend/src/app/lib/read-model-adapters.ts`
  - `HISTORY_PRESET_QUERY`는 24h=48 marker, 7d=336 marker, 14d=672 marker를 제공한다.
  - `humanizeCaptureReason("hourly_scheduled")`는 "30분 정기 저장" user-facing copy를 제공한다.
  - 표시 copy/date/slot helper만 필요한 범위로 조정한다. source/order/recalculation 의미를 바꾸지 않는다.
- `frontend/src/app/lib/read-model-contract-fixtures.ts`
  - 14.3 guard/sentinel fixture를 추가할 후보. Snapshot marker/detail/retention/source absence fixture를 만들 때 existing semantics를 재정의하지 않는다.
- `frontend/src/app/lib/read-model-contract-guard.ts`
  - Snapshot history/source/horizon/order, Snapshot detail stored source/flags/marker boundary를 검증하는 source guard다.
  - 필요한 경우 type-level/source semantics guard를 보강한다.
- `frontend/scripts/read-model-contract-guard.ts`
  - 14.2 static sentinel은 `DashboardMain` tab-only flow 재도입을 막고 same-flow anchor order를 검증한다.
  - 14.3에서는 fallback/raw explorer/hourly copy/mobile slot grid 관련 static sentinel 후보를 추가할 수 있다.
- `frontend/src/styles/tailwind.css`
- `frontend/src/styles/theme.css`
  - Snapshot/History/detail surface가 component class만으로 해결되지 않을 경우 제한적으로 사용한다.
- `frontend/src/app/components/ui/button.tsx`
- `frontend/src/app/components/ui/badge.tsx`
- `frontend/src/app/components/ui/dialog.tsx`
- `frontend/src/app/components/ui/sheet.tsx`
  - 기존 UI primitive 확인 후보. 새 dependency는 기본적으로 추가하지 않는다.
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/conformance-checklist.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/deviation-log.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/no-discretionary-redesign-checklist.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/handoff-gates.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/source-semantics-sentinel-review.md`
  - 14.3 completion gate/evidence/deviation 기록 후보.
- `planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html`
  - read-only reference. 수정하지 않는다.
- `planning-artifacts/stories/13-ui-dashboard-source-of-truth-surface-realignment.md`
  - optional read-only seed/handoff. 기준으로 삼지 않는다.

## Verification Commands

```bash
git status --short --branch --untracked-files=all

cd frontend && npm run guard:read-model-contract
cd frontend && npm run typecheck
cd frontend && npm run build

rg -n "hourly scheduled|hourly snapshot|marker state|marker.*state|current fallback|current dashboard fallback|live/current fallback|raw snapshot|endpoint timeseries|arbitrary query" frontend/src/app frontend/scripts
rg -n "healthScore|rootCause|recoveryProof|p99|p95|histogram percentile|retention fallback" frontend/src/app frontend/scripts
rg -n "\\.sort\\(|\\.toSorted\\(|\\.reduce\\(" frontend/src/app/components frontend/src/app/lib frontend/scripts
rg -n "dashboard_snapshots\\.read_model_json|currentWindowEndUtc|markerIsStateSource|snapshotDetailRecalculates|currentStateRecalculated|accepted_metric_buckets|recent_30_minutes" frontend/src/app frontend/scripts

git diff --check
ruby -e 'require "yaml"; YAML.load_file("implementation-artifacts/sprint-status.yaml"); puts "yaml ok"'
git status --short --branch --untracked-files=all
```

Static grep hit는 guard negative fixture, explanatory source semantics comment, type/source fixture일 수 있다. 구현자는 hit를 무시하지 말고 production user-facing/source semantics regression인지 분류해 completion notes에 남긴다.

YAML을 수정하지 않았다면 YAML parse는 선택이지만, sprint status 또는 QA metadata를 수정했다면 반드시 실행한다.

## Browser Visual QA Plan

1. `cd frontend && npm run dev -- --host 127.0.0.1`로 local Vite app을 실행한다.
2. Browser/Playwright로 Dashboard route를 연다. 인증 fixture가 없으면 접근 가능한 auth-blocked 화면과 차단 사유를 분리 기록한다.
3. 최소 viewport:
   - desktop: `1440x1000`
   - tablet: `1024x900`
   - mobile: `390x844`
4. Desktop `1440x1000`에서 확인한다.
   - Snapshot/History section이 Main live flow 아래의 same-flow anchor로 보인다.
   - 14일 retention summary, 30분 cadence, 48/day, 672 total, default 24h, cleanup/expiry hint가 first-pass scanning 정보로 보인다.
   - Date map은 markerBucket summary로 보이고 state source로 보이지 않는다.
   - 48-slot day grid가 mockup density에 준하고 selected slot affordance가 명확하다.
   - Selected snapshot summary와 Snapshot detail top flags가 보인다.
   - Operational event feed는 secondary/collapsible context다.
5. Tablet `1024x900`에서 확인한다.
   - Retention summary/date map/slot grid가 rail/main adaptation 안에서 clipping 없이 stack 또는 grid adaptation된다.
   - selected summary chips와 source flags가 좁은 폭에서 겹치지 않는다.
6. Mobile `390x844`에서 확인한다.
   - horizontal page scroll 없음.
   - 48-slot grid가 clipping 없이 wrap/stack되고, 가장 긴 label이 container를 밀지 않는다.
   - error/empty state text와 retry button이 fallback CTA처럼 보이지 않는다.
7. Detail/error path에서 확인한다.
   - `mode=snapshot`, `source=dashboard_snapshots.read_model_json`, `snapshotDetailRecalculates=false`, `currentStateRecalculated=false`, `markerIsStateSource=false`.
   - Retention expired/404/source absence에 live/current fallback CTA/copy 없음.
8. Evidence naming convention은 `implementation-artifacts/epic-14-dashboard-design-parity-qa/handoff-gates.md`를 따른다.
9. Full authenticated browser path를 실행하지 못하면 completion notes와 Known Gap에 그대로 남긴다.

## Known Gap

- 14.1/14.2 기준 full authenticated browser fixture/runbook이 없어 `Project -> Application -> Dashboard -> Snapshot -> Instance -> retention expired` path를 하나의 browser smoke로 닫은 evidence는 없다. 14.3은 이 gap을 닫지 못하면 known gap으로 유지해야 하며, authenticated Snapshot/History/Snapshot detail/retention visual conformance를 증명했다고 쓰지 않는다.
- 14.2는 Project rail / Application rail / Main live surface 기대 UI/UX를 code/static/auth-blocked shell evidence 기준으로 구현했다. Authenticated strict visual conformance는 coverage gap이다.
- 14.4는 Instance wide modal과 end-to-end visual QA 마감 scope다. 14.3은 Snapshot/History, Snapshot detail, retention/source absence에 집중하고 Instance modal full QA를 닫았다고 주장하지 않는다.

## Dev Notes

- Active implementation baseline은 Traditional MVC + Service/Repository Layering이다. 14.3은 frontend surface story이며 backend MVC layer, read model API, persistence, migration을 변경하지 않는다.
- Frontend root는 `frontend/`이고 React 18.3.1, Vite 6.3.5, TypeScript 5.8.3, Tailwind 4.1.12, Radix/shadcn-style UI, lucide-react를 사용한다. 새 dependency는 기본적으로 추가하지 않는다.
- Public component, helper, 새 JSDoc/comment를 추가할 때는 AGENTS.md 지침에 따라 한국어 주석을 사용한다.
- `guard:read-model-contract`는 Snapshot history/detail, Instance live/snapshot/trend, Dashboard same-flow order의 source/order/recalculation/forbidden field semantics를 감시한다. 14.3 구현 후에도 fail-closed여야 한다.
- `source-of-truth-dashboard-mockup.html`의 14.3 관련 anchors:
  - `#openSnapshotPickerButton`, `#snapshotList`, `#selectedSnapshotSummary`
  - `.retention-grid`, `.date-heatmap`, `.date-cell`, `.slot-grid`, `.slot-cell`
  - `liveHistoryTemplate`, `snapshotModeHistoryTemplate`, `selectedSnapshotSummaryTemplate`, `captureReasonLabel`
  - Snapshot mode render branch의 `modeCell`, `sourceCell`, `capturedAtCell`, `captureReasonCell`, `recalculatesCell`, `markerSourceCell`
- Mockup responsive behavior는 `@media (max-width: 860px)`에서 date map/slot grid를 1열로 전환해 clipping을 피한다. 실제 구현은 Tailwind responsive grid로 동일한 reading order와 no overflow를 만족해야 한다.

## References

- `_bmad/custom/project-context.md`
- `planning-artifacts/epics.md#Epic 14. Dashboard Mockup Design Parity`
- `planning-artifacts/epic-14-dashboard-mockup-design-parity.md`
- `planning-artifacts/stories/14-1-design-parity-baseline-and-visual-guardrails.md`
- `planning-artifacts/stories/14-2-dashboard-shell-rails-and-live-surface-realignment.md`
- `planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html`
- `implementation-artifacts/sprint-status.yaml`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/conformance-checklist.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/deviation-log.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/no-discretionary-redesign-checklist.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/mockup-principles-and-gap-map.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/handoff-gates.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/source-semantics-sentinel-review.md`
- `planning-artifacts/stories/13-ui-dashboard-source-of-truth-surface-realignment.md` (read-only seed/handoff only)

## Dev Agent Record

### Agent Model Used

GPT-5 Codex (BMAD dev-story)

### Debug Log References

- 2026-06-11 `git status --short --branch --untracked-files=all`: 기존 modified `implementation-artifacts/sprint-status.yaml`, untracked `dbml-error.log`, seed `planning-artifacts/stories/13-ui-dashboard-source-of-truth-surface-realignment.md`, Story 14.3 파일을 확인했다. `dbml-error.log`와 seed story는 수정/삭제/stage하지 않았다.
- BMAD `bmad-dev-story` workflow customization, `_bmad/custom/project-context.md`, Story 14.3, Epic 14 planning source, Story 14.1/14.2, HTML Source of Truth mockup, sprint status, 14.1 QA artifacts, optional seed `13-ui...` story를 읽고 구현 기준으로 사용했다.
- `frontend/src/app/components/snapshot-history-panel.tsx`: Snapshot/History top summary를 14일 retention, 672 scheduled points, 30분 cadence, 48/day, default 24h, cleanup hint 중심으로 정렬했다. Date map/48-slot grid copy와 selected snapshot summary를 marker-first/stored read model provenance 기준으로 보강했다.
- `frontend/src/app/components/snapshot-detail-surface.tsx`: Snapshot detail top surface를 `Application Dashboard / Snapshot` branch에 맞춰 title/subtitle/mode/source badge/info grid 우선 구조로 정렬하고 captured/window/recalculation/marker source flags를 상단에 노출했다.
- `frontend/scripts/read-model-contract-guard.ts`: Story 14.3 static sentinel을 추가해 retention summary, selected summary, secondary marker order, Snapshot detail top source flags, raw explorer/fallback forbidden terms를 검증한다.
- 2026-06-11 1차 code-review fix: Snapshot date map이 active preset horizon이 아니라 14일 retention boundary에서 항상 14개 날짜를 만들도록 조정했다. 48-slot label과 lookup은 `currentWindowEndUtc` end-boundary 기준으로 맞춰 `00:30Z`부터 `24:00Z`까지 보이고, 자정 end-boundary는 전날 `24:00Z` slot으로 묶는다.
- `frontend/scripts/read-model-contract-guard.ts`: 1차 fix 이후 static sentinel이 14일 retention date map helper, `slotDayKey`, `slotIndexFromWindowEndUtc`, `(slotIndex + 1) * SLOT_MINUTES`, `24:00Z` end label을 확인한다.
- 2026-06-11 2차 quick-dev fix: Snapshot/History primary date/slot map은 active preset marker limit이 아니라 `since=14d&limit=672` marker query를 별도로 조회해 채운다. Active preset은 보조 event/server marker list 범위로 유지한다.
- `frontend/src/app/lib/read-model-adapters.ts`: slot/date helper를 순수 함수로 이동하고 `horizon.until=00:00Z` retention day start가 marker `00:00Z -> 전날 24:00Z` bucketing과 같은 기준을 쓰게 했다.
- `frontend/scripts/read-model-contract-guard.ts`: static sentinel이 문자열 매칭에만 의존하지 않도록 `snapshotRetentionDayKeys`, `snapshotSlotDayKey`, `snapshotSlotIndexFromWindowEndUtc`, `snapshotSlotTimeLabel` boundary case를 실행 검증한다.
- QA artifacts: `deviation-log.md`에 mobile 48-slot 4-column responsive adaptation `R-20260611-143-001`을 category 2로 기록했다. `source-semantics-sentinel-review.md`, desktop/tablet/mobile side-by-side notes, Story 14.3 guard note, browser observation JSON, desktop/tablet/mobile screenshots를 업데이트했다.
- Browser QA: `cd frontend && npm run dev -- --host 127.0.0.1`로 Vite dev server를 실행하고 Browser viewport override로 desktop `1440x1000`, tablet `1024x900`, mobile `390x844`를 확인했다. 인증 fixture가 없어 `/dashboard`는 auth-blocked shell까지만 관찰했다.
- Browser evidence: `current-14-3-dashboard-auth-blocked-desktop-1440x1000.png`, `current-14-3-dashboard-auth-blocked-tablet-1024x900.png`, `current-14-3-dashboard-auth-blocked-mobile-390x844.png`, `browser-14-3-snapshot-history-detail-and-retention-surface-realignment-observations.json`. 세 viewport 모두 auth-blocked shell 기준 horizontal overflow 없음.
- `cd frontend && npm run guard:read-model-contract`: 통과. `read-model contract guard fixtures passed`.
- `cd frontend && npm run typecheck`: 통과.
- `cd frontend && npm run build`: 통과. Vite build `built in 1.04s`.
- `git diff --check`: 통과.
- `ruby -e 'require "yaml"; YAML.load_file("implementation-artifacts/sprint-status.yaml"); puts "yaml ok"'`: 통과.
- Static grep: forbidden wording hits는 guard negative fixture/assertion, read-model type/source fixture, 기존 metric display copy로 분류했다. Story 14.3 Snapshot/History/Detail 변경에는 raw snapshot explorer, endpoint timeseries, arbitrary query, fallback CTA, client-side reorder/recalculation을 추가하지 않았다.

### Completion Notes List

- Snapshot/History picker는 marker-first 30분 dashboard point 탐색으로 읽히도록 retention summary를 first-pass scanning grid로 재정렬했다. 14일, 672 scheduled points, 30분 정기 저장, 48/day, default 24h, 14일 이후 cleanup/expiry hint가 상단에 보인다.
- Date map은 날짜별 highest markerBucket 요약이라는 copy를 유지하고, active preset이 24h여도 14일 retention marker query에서 채운 날짜 map을 먼저 보여준다. 각 날짜는 state/evidence source가 아니라 slot open 후 stored read model에서 복원된다는 note를 명시했다.
- 48-slot day grid는 `currentWindowEndUtc` end-boundary label을 `00:30Z`부터 `24:00Z`까지 표시한다. `horizon.until=00:00Z`인 경우 최신 retention date도 전날 slot day로 맞춘다. Desktop 8-column, tablet 6-column, mobile 4-column responsive grid로 유지하고 slot label truncation을 제거했다. Mobile 8열 미유지 adaptation은 `deviation-log.md`에 category 2로 기록했다.
- Selected snapshot summary를 별도 surface로 추가해 `snapshotId`, `capturedAt`, `currentWindowEndUtc`, `markerBucket`, stored state, capture reason을 분리했다. `hourly_scheduled` persisted/API token은 그대로 두고 user-facing copy는 기존 `humanizeCaptureReason`의 "30분 정기 저장" 의미를 유지했다.
- Server marker order list는 `Secondary server marker order`로 낮췄고, operational event feed는 기존 `details` collapsible context로 marker/date/slot picker 아래에 유지했다.
- Snapshot detail은 dashboard-like top surface에서 `mode=snapshot`, `source=dashboard_snapshots.read_model_json`, `snapshotId`, `capturedAt`, `generatedAt`, current window start/end, `captureReason`, `snapshotDetailRecalculates=false`, `currentStateRecalculated=false`, `markerIsStateSource=false`를 먼저 보여준다.
- Stored state/operator summary/data quality/state reasons/attention evidence/first look candidates/bounded endpoint evidence는 stored read model projection에서만 렌더링한다. Current dashboard/current accepted bucket/current threshold/current starter state로 missing stored field를 보정하지 않았다.
- Retention expired/404/source absence copy는 safe empty/error state와 retry만 유지하고, "현재 dashboard 보기" 같은 live/current fallback CTA를 추가하지 않았다.
- 14.3 Handoff Gate 판정: Snapshot/History picker `code/static conformant, authenticated browser coverage gap`; selected summary `code/static conformant`; Snapshot detail `code/static conformant`; retention/source absence `code/static conformant`; mobile slot grid `allowed deviation category 2 by code/static, authenticated browser coverage gap`; full authenticated browser path `coverage gap`.
- Browser QA는 auth-blocked shell 기준으로만 수행했다. authenticated Snapshot/History picker, Snapshot detail, retention expired/source absence path는 fixture/runbook 부재로 증명하지 않았고 14.4 또는 fixture-backed follow-up gap으로 유지한다.
- Backend code/tests, migration/schema, Source of Truth mockup HTML, completed Epic 13 story body/status, 기존 untracked `dbml-error.log`, seed `13-ui...` story는 수정하지 않았다.

### File List

- `frontend/src/app/components/snapshot-history-panel.tsx`
- `frontend/src/app/components/snapshot-detail-surface.tsx`
- `frontend/scripts/read-model-contract-guard.ts`
- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/stories/14-3-snapshot-history-detail-and-retention-surface-realignment.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/deviation-log.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/source-semantics-sentinel-review.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/side-by-side-desktop-1440x1000.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/side-by-side-tablet-1024x900.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/side-by-side-mobile-390x844.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/guard-14-3-snapshot-history-detail-and-retention-surface-realignment-20260611-1859.md`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/browser-14-3-snapshot-history-detail-and-retention-surface-realignment-observations.json`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/current-14-3-dashboard-auth-blocked-desktop-1440x1000.png`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/current-14-3-dashboard-auth-blocked-tablet-1024x900.png`
- `implementation-artifacts/epic-14-dashboard-design-parity-qa/current-14-3-dashboard-auth-blocked-mobile-390x844.png`

## Change Log

| Date | Change |
|---|---|
| 2026-06-11 | Epic 14 Story 14.3 Snapshot, History, Detail, And Retention Surface Realignment story artifact를 생성하고 ready-for-dev로 전환했다. |
| 2026-06-11 | Snapshot/History picker, selected snapshot summary, Snapshot detail top source flags, retention/source absence no-fallback copy, static sentinel, QA evidence를 구현하고 review로 전환했다. |
| 2026-06-11 | Code-review 1차 수정으로 14일 date map 생성과 `currentWindowEndUtc` end-boundary 48-slot label을 mockup 기준에 맞추고 static sentinel을 보강했다. |
| 2026-06-11 | BMAD quick-dev로 14d retention marker query, midnight horizon boundary, executable slot/date guard, evidence overclaim wording을 보정하고 done으로 닫았다. |
