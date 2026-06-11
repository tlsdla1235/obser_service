# Mockup Principles And Gap Map

이 문서는 `source-of-truth-dashboard-mockup.html`에서 추출한 적용 원칙과 Story 14.1 기준 current observation gap을 묶는다. 14.1은 production UI를 수정하지 않았으므로, 아래 current state는 browser 관찰 가능 범위와 코드 관찰 후보를 분리해 기록한다.

## Mockup에서 추출한 Strict Targets

| Target | Mockup anchor | Conformance expectation |
|---|---|---|
| 3-column shell | `.app-grid`, Project rail, Application rail, `.main` | Desktop first viewport에서 scope rail, app rail, dashboard main이 한 hierarchy로 읽힌다 |
| Compact rail rows | `.rail-item`, `.rail-title`, `.rail-meta`, `.rail-note` | Row density가 낮아지지 않고 active indicator와 0~1개 note만 보인다 |
| Neutral panel grammar | `.panel`, `.panel-head`, `.panel-body`, `--radius: 6px` | White/neutral panels, thin border, compact padding, restrained badges |
| Small section labels | `.section-label` | 11px-ish uppercase section label이 surface hierarchy를 잡는다 |
| Context/read semantics | `#dashboardSectionLabel`, `#modeCell`, `#sourceCell`, `#recalculatesCell` | Live/snapshot mode/source/window/recalculation flags가 first-screen signal이다 |
| Snapshot/History picker | `.retention-grid`, `.date-heatmap`, `.slot-grid` | 14일/672 point summary, 48-slot day drilldown, selected summary가 primary interaction이다 |
| Snapshot detail | snapshot mode render branch | Live와 같은 dashboard surface로 stored read model을 복원한다 |
| Instance detail | `.modal`, `.modal-grid`, Instance Evidence Modal | Wide modal/detail surface이며 narrow Sheet가 아니다 |
| Retention/source absence | retention copy and no-fallback notes | Expired/source absence는 safe state이며 live/current fallback이 없다 |

## Current Browser Observation

Playwright는 `http://127.0.0.1:5173/dashboard`를 desktop/tablet/mobile에서 열었다.

| Viewport | Result |
|---|---|
| desktop `1440x1000` | HTTP 200. Project rail은 `인증 필요`, Application rail은 `Project 선택 대기`, Main은 `Project를 선택하세요`. Authenticated dashboard sections 미관찰 |
| tablet `1024x900` | HTTP 200. Desktop과 동일하게 인증 필요 상태까지만 관찰 |
| mobile `390x844` | HTTP 200. 인증 필요 상태. `body.scrollWidth=504`, `viewportWidth=390` horizontal overflow 관찰. Authenticated dashboard overflow로 단정하지 않음 |

## 영역별 Gap Map

| Area | Current state | Mockup element | Conformance target | Deviation status | Allowed category | Follow-up owner |
|---|---|---|---|---|---|---|
| Project rail | Browser: auth error row까지만 관찰. Code: `ProjectRail` has compact project rows and registration panel below rail. | `.rail-item`, Projects rail | Scope selection, application count, setup/recent concern signal, compact density, no dashboard judgment | Evidence gap. Authenticated project rail visual conformance 미검증 | N/A - coverage gap. 후속 visual 차이는 1~4 또는 blocker로 재분류 | 14.2 |
| Application rail | Browser: project 미선택 대기 state만 관찰. Code: lifecycle badge, metric freshness axis, starter connection axis, top concern row 존재. | Applications rail, `.rail-item`, badge row | Lifecycle badge, accepted bucket freshness, starter connection, top concern 0~1개, metric/heartbeat axis 분리 | Evidence gap. Code candidate exists but authenticated visual density 미검증 | N/A - coverage gap. 후속 visual 차이는 1~4 또는 blocker로 재분류 | 14.2 |
| Main live surface | Browser: project selection message only. Code: `DashboardMain` uses current/snapshots tabs and ordered current surface. | `.panel.strong`, `.info-grid`, `.state-strip`, `.metric-grid` | Context/read semantics first, data quality, lifecycle state, direct reasons, attention/first look, endpoint/resource evidence, metric detail, starter, instance entry | Potential structural gap: mockup keeps Snapshot/History in same main flow while current code uses tabs. Needs authenticated screenshot and reviewer decision | Pending. If tab split remains, reviewer must approve a category 1~4 reason or mark blocker | 14.2 |
| Snapshot/History | Browser: inaccessible without auth. Code: marker-first component with retention summary, date map, 48-slot grid, selected detail. | `.retention-grid`, `.date-heatmap`, `.slot-grid`, `Snapshot 선택` | 14일 retention summary, 672 scheduled points, 48-slot drilldown, selected snapshot summary, marker-as-index copy | Evidence gap. Code candidate aligns semantically; visual density/order needs screenshot | N/A - coverage gap. 후속 visual 차이는 1~4 또는 blocker로 재분류 | 14.3 |
| Snapshot detail | Browser: inaccessible without auth. Code: `SnapshotDetailSurface` exists and guard references stored source. | Application Dashboard / Snapshot branch | Dashboard-like skeleton with `mode=snapshot`, `source=dashboard_snapshots.read_model_json`, recomputation false | Evidence gap. Must confirm same visual skeleton and no fallback in browser | N/A - coverage gap. 후속 visual 차이는 1~4 또는 blocker로 재분류 | 14.3 |
| Instance wide modal | Browser: inaccessible without auth. Code: `DialogContent` width `min(1120px, calc(100vw - 2rem))`; Stored trend/projection trend Sheet is retired for MVP. | `.modal`, `.modal-grid`, Instance Evidence Modal | Live/snapshot detail as single wide modal, no trend/narrow Sheet entrypoint, section order preserved | Evidence gap. Code candidate aligns; visual clipping/table overflow needs screenshot | N/A - coverage gap. 후속 visual 차이는 1~4 또는 blocker로 재분류 | 14.4 |
| Retention/source absence | Browser: inaccessible without auth. Code: error copy exists in snapshot/instance surfaces; 13.11 guard evidence passed historically. | retention/expired safe copy | No live/current fallback CTA, safe empty/error state | Evidence gap. Needs targeted fixture/runbook or authenticated expired state | N/A - coverage gap. 후속 visual 차이는 1~4 또는 blocker로 재분류 | 14.3/14.4 |
| Source semantics guard | Code inspection and command available. | `accepted_metric_buckets`, `dashboard_snapshots.read_model_json`, flags | Existing guard must remain fail-closed for source/order/recalculation/forbidden fields | To be verified by command in 14.1 and repeated by each implementation story | N/A - guardrail verification, not visual deviation | 14.2~14.4 |

## Initial Handoff Assessment

- 14.2 should first close Project rail, Application rail, Main live surface visual conformance and decide whether current tab split conflicts with mockup's same-flow hierarchy. If it remains, it must be logged as approved deviation or changed.
- 14.3 should close Snapshot/History and Snapshot detail visual conformance with actual screenshots, especially date map density and retention/source absence copy.
- 14.4 should close Instance wide modal clipping/table overflow and remaining end-to-end visual QA disposition.
- Full authenticated browser path remains a QA coverage gap, not a conformance pass.
