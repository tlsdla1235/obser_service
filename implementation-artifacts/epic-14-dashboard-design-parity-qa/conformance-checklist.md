# Mockup Conformance Checklist

이 checklist는 Epic 14 구현자가 HTML mockup의 UI/UX를 실제 Vite Dashboard로 옮길 때 사용하는 completion gate다. 항목은 "비슷함"이 아니라 구조, 밀도, 순서, visual grammar, modal/surface form, Snapshot/History interaction 일치 여부로 판정한다.

## 공통 원칙

- [ ] 기준은 `source-of-truth-dashboard-mockup.html`이다. `source-of-truth-dashboard-snapshot-picker.png`는 기준이 아니다.
- [ ] DOM/CSS class name, temporary JavaScript runtime, hard-coded demo data, prototype controls를 복사하지 않는다.
- [ ] Pixel-perfect byte-level clone을 요구하지 않는다.
- [ ] IA, layout hierarchy, visual density, spacing rhythm, neutral panel grammar, information ordering, wide modal, Snapshot/History picker, retention expired/source absence state는 strict target이다.
- [ ] Mockup과 다른 layout, density, color/visual grammar, ordering, modal/surface form, Snapshot/History interaction은 `deviation-log.md`에 기록한다.
- [ ] allowed deviation category 1~4에 속하지 않는 차이는 blocker다.

## Mockup Visual Grammar

- [ ] Desktop first-screen은 `app-grid`와 같은 Project rail / Application rail / Main surface 3-column hierarchy를 유지한다.
- [ ] Rail은 `rail-item`처럼 compact row, 2px active indicator, small meta, 0~1개 note hierarchy를 갖는다.
- [ ] Surface는 white/neutral `panel`, thin border, compact `panel-head`/`panel-body`, 6px-ish radius grammar를 따른다.
- [ ] `section-label`처럼 작은 uppercase label과 restrained icon/text hierarchy를 사용한다.
- [ ] Badge/chip은 low-contrast neutral grammar와 restrained semantic color만 사용한다.
- [ ] Stable grid를 사용해 metric cells, info cells, date map, slot grid, endpoint table이 hover/data 변경으로 흔들리지 않는다.
- [ ] Cards-inside-cards, oversized marketing hero, decorative gradient/orb, excessive rounded-card redesign을 만들지 않는다.

## Application Dashboard Ordering

- [ ] Context/read semantics bar가 first-screen anchor다.
- [ ] `mode=live`, `recent_30_minutes`, `accepted_metric_buckets`, `baseline not used`, project/application/environment, generated/window/bucket boundary가 보인다.
- [ ] Data quality/freshness가 metric detail보다 먼저 읽힌다.
- [ ] Lifecycle state hero와 direct state reasons가 first look보다 먼저 읽힌다.
- [ ] Attention/first look candidates는 bounded queue로 보인다.
- [ ] Endpoint/resource evidence는 full raw explorer가 아니라 stateReason/attentionEvidence에 연결된 후보로 보인다.
- [ ] Metric detail은 state/evidence를 압도하지 않는다.
- [ ] Starter connection은 control-plane only로 분리된다.
- [ ] Instance entry는 Application Dashboard 판단을 대체하지 않는 detail entry로 보인다.

## Snapshot/History

- [ ] Snapshot/History는 marker-first 30분 dashboard point 탐색이다.
- [ ] 14일 retention summary, 30분 scheduled point, 48/day, 672 total point, default 24h, cleanup/expiry hint가 보인다.
- [ ] Date map은 날짜별 가장 높은 markerBucket 요약으로 보이되 state source로 보이지 않는다.
- [ ] 하루 drilldown은 48-slot grid로 보인다.
- [ ] Selected snapshot summary는 `snapshotId`, `capturedAt`, `currentWindowEndUtc`, `markerBucket`, stored state, capture reason을 분리해 보여준다.
- [ ] `hourly_scheduled` persisted/API token은 변경하지 않고 사용자-facing copy는 30분 scheduled 의미로 표시한다.
- [ ] Operational event feed가 있으면 marker/date/slot 탐색보다 우선하지 않는다.

## Snapshot Detail

- [ ] Snapshot detail은 live dashboard와 같은 dashboard-like skeleton을 공유한다.
- [ ] Top surface에 `mode=snapshot`, `source=dashboard_snapshots.read_model_json`, captured/window/recalculation flags가 보인다.
- [ ] `snapshotDetailRecalculates=false`, `currentStateRecalculated=false`, `markerIsStateSource=false` 의미가 보인다.
- [ ] Stored `dashboard_snapshots.read_model_json`의 state/operatorSummary/dataQuality/stateReasons/attentionEvidence/firstLookCandidates/bounded evidence만 사용한다.
- [ ] Current dashboard, current accepted bucket, current threshold, current starter state로 보완하지 않는다.

## Instance Wide Modal

- [ ] Live/snapshot Instance Dashboard detail은 narrow right Sheet가 아니라 wide Dialog/modal 또는 동등한 wide detail surface다.
- [ ] Width target은 mockup의 `min(1120px, 100%)`에 준하는 넓은 surface다.
- [ ] Modal header는 sticky 또는 동등하게 stable하고, body scroll과 clipping이 없다.
- [ ] Section order는 context note, Application state reference, Read semantics, selected instance metrics, endpoint evidence, resource evidence, starter connection, normalized endpoint evidence table이다.
- [ ] Snapshot mode note는 selected Application Snapshot row window, late accepted metric 가능성, no stored Application Snapshot override를 드러낸다.
- [ ] Stored trend/projection trend/`InstanceTrendView`/narrow Sheet 진입점을 추가하지 않는다. 과거 instance evidence는 Snapshot/History -> snapshot-mode wide modal 경로로만 본다.

## Retention / Source Absence

- [ ] Retention expired, 404, malformed, source absence는 safe empty/error state로 수렴한다.
- [ ] Live dashboard/current accepted bucket/current instance evidence fallback CTA나 copy를 만들지 않는다.
- [ ] `metric_missing`, `not_observed_in_window`, source absence는 evidence limitation으로 표현한다.
- [ ] "정상 확정", "문제 없음", "복구 완료" copy로 보정하지 않는다.

## Source Semantics Guard

- [ ] Live Application Dashboard source는 `accepted_metric_buckets` + `recent_30_minutes`다.
- [ ] Snapshot detail source는 `dashboard_snapshots.read_model_json`이다.
- [ ] Snapshot marker/history는 `dashboard_snapshots` helper/index row이며 state source가 아니다.
- [ ] Instance live source는 `accepted_metric_buckets` recent 30 minutes다.
- [ ] Instance snapshot mode는 selected Application Snapshot row metadata + selected instance `accepted_metric_buckets` reconstruction이다.
- [ ] Instance snapshot mode는 stored Application Snapshot state/evidence를 검증하거나 대체하지 않는다.
- [ ] Retention expired/source absence는 live/current fallback 없이 처리한다.
- [ ] `guard:read-model-contract`가 source/order/recalculation/forbidden field semantics를 계속 감시한다.

## Viewport QA

| Viewport | 필수 확인 |
|---|---|
| desktop `1440x1000` | Project rail / Application rail / Main surface composition, first-screen density, Snapshot picker, wide modal |
| tablet `1024x900` | Rail/main stacking 또는 2-column adaptation, no clipped badges, no tab/card clutter |
| mobile `390x844` | No horizontal page scroll, badges wrap, rail rows readable, slot grid stable, modal header/body not clipped |

## Completion 판정

각 영역은 아래 중 하나로 기록한다.

- `conformant`: mockup 구조/밀도/순서/문법을 따른다.
- `allowed deviation`: category 1~4, reviewer decision, follow-up owner가 `deviation-log.md`에 있다.
- `blocker`: allowed category가 없거나 reviewer decision이 rejected/needs follow-up인 차이다.
