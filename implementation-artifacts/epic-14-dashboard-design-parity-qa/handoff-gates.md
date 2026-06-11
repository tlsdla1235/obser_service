# Epic 14 Handoff Gates

후속 14.2~14.4는 이 파일의 gate를 story completion notes에 인용해야 한다. Gate를 충족하지 못한 항목은 `deviation-log.md`에 allowed category와 reviewer decision을 남기거나 blocker로 처리한다.

## 공통 Gate

- [ ] `conformance-checklist.md`를 최신 상태로 확인했다.
- [ ] `no-discretionary-redesign-checklist.md`를 통과했다.
- [ ] `deviation-log.md`에 새 차이를 기록하거나 "새 deviation 없음"을 completion notes에 명시했다.
- [ ] Source of Truth 기준은 HTML mockup이며 PNG export를 기준으로 삼지 않았다.
- [ ] `guard:read-model-contract`, `typecheck`, `build`를 실행하거나 실행하지 못한 사유를 남겼다.
- [ ] desktop `1440x1000`, tablet `1024x900`, mobile `390x844` visual QA screenshot/note를 남겼다.
- [ ] full authenticated browser path를 닫지 못하면 그 범위를 과장하지 않았다.
- [ ] production code 외 변경 금지 범위와 `dbml-error.log` 보호를 확인했다.

## 14.2 Gate - Dashboard Shell, Rails, Live Surface

- [ ] Project rail: scope selection, application count, setup/recent concern 0~1개, compact density를 mockup에 맞췄다.
- [ ] Application rail: lifecycle badge, accepted bucket freshness, starter connection, top concern 0~1개가 scan 가능하다.
- [ ] Desktop first-screen: Project rail / Application rail / Main surface 3-column hierarchy가 보인다.
- [ ] Main live surface: context/read semantics, data quality/freshness, lifecycle state, direct reasons, attention/first look, endpoint/resource evidence, metric detail, starter connection, instance entry 순서를 보존한다.
- [ ] `accepted_metric_buckets`, `recent_30_minutes`, `baseline not used`가 first-screen signal이다.
- [ ] Mockup보다 넓고 느슨한 card redesign, nested card clutter, marketing-style visual을 만들지 않았다.

## 14.3 Gate - Snapshot, History, Detail, Retention Surface

- [ ] Snapshot/History picker는 marker-first 30분 point 탐색으로 보인다.
- [ ] 14일 retention summary, 672 scheduled point, 48-slot day grid, selected snapshot summary가 보인다.
- [ ] `currentWindowEndUtc`가 slot identity이고 `capturedAt`/`generatedAt`은 provenance/tie-breaker로만 표시된다.
- [ ] `hourly_scheduled` token은 유지하고 user-facing copy는 30분 scheduled 의미다.
- [ ] Snapshot detail은 dashboard-like skeleton이며 `mode=snapshot`, `source=dashboard_snapshots.read_model_json`, `snapshotDetailRecalculates=false`, `markerIsStateSource=false`가 보인다.
- [ ] Retention expired/404/source absence는 live/current fallback 없이 safe copy로 수렴한다.
- [ ] Date map, slot grid, selected summary가 desktop/tablet/mobile에서 clipping/wrapping 문제 없이 확인됐다.

## 14.4 Gate - Instance Wide Modal And End-To-End Visual QA

- [ ] Instance live/snapshot detail은 wide Dialog/modal 또는 동등한 wide detail surface다.
- [ ] Modal order는 Application state reference, Read semantics, selected metrics, endpoint evidence, resource evidence, starter connection, normalized endpoint table을 따른다.
- [ ] Snapshot mode note는 selected snapshot row window, late accepted metric possibility, no stored Application Snapshot override를 드러낸다.
- [ ] Stored trend/projection trend/`InstanceTrendView`/narrow Sheet 진입점이 없고, 과거 instance evidence는 Snapshot/History -> snapshot-mode wide modal 경로만 사용한다.
- [ ] Project rail, Application rail, Main surface, Snapshot picker, Snapshot detail, Instance wide modal, retention expired state별 final conformance 판정을 남겼다.
- [ ] Unresolved non-allowed deviation이 없다.

## Evidence Naming Convention

| Evidence | Filename pattern |
|---|---|
| Current Vite screenshot | `current-14-x-{surface}-{desktop|tablet|mobile}-{width}x{height}.png` |
| Mockup reference screenshot | `mockup-reference-{surface}-{desktop|tablet|mobile}-{width}x{height}.png` |
| Side-by-side note | `side-by-side-{desktop|tablet|mobile}-{width}x{height}.md` |
| Browser observation JSON | `browser-{storyKey}-observations.json` |
| Guard command note | `guard-{storyKey}-{yyyymmdd-hhmm}.md` |

Screenshot 비교는 "비슷함"이 아니라 영역별 `conformant`, `allowed deviation`, `blocker`로 기록한다.
