# Side-by-Side Note - Desktop 1440x1000

## Evidence

- Current Vite: `baseline-desktop-1440x1000.png`
- Mockup reference: `mockup-reference-desktop-1440x1000.png`
- Mockup snapshot picker: `mockup-reference-desktop-snapshot-picker-1440x1000.png`
- Mockup instance modal: `mockup-reference-desktop-instance-modal-1440x1000.png`

## Current Observation

`/dashboard` returned HTTP 200, but no access token/authenticated fixture was available. The visible UI stops at:

- Project rail: `인증 필요`, `GitHub 로그인 후 사용할 수 있습니다.`
- Application rail: `Project 선택 대기`
- Main surface: `Project를 선택하세요`

Authenticated Project rail, Application rail, Main live surface, Snapshot picker, Snapshot detail, Instance modal, retention expired/source absence were not proven in browser.

## Desktop Judgment Fields

| Area | Judgment | Note |
|---|---|---|
| Project rail | coverage gap | Auth error only; authenticated rail density/order unverified |
| Application rail | coverage gap | Project not selected; authenticated application row unverified |
| Main surface | coverage gap | No dashboard data rendered |
| Snapshot picker | coverage gap | Mockup reference captured; Vite authenticated picker unverified |
| Snapshot detail | coverage gap | Mockup reference captured through live/snapshot reference; Vite unverified |
| Instance wide modal | coverage gap | Mockup reference captured; Vite wide modal unverified |
| Retention/source absence | coverage gap | Vite authenticated expired/source absence unverified |

## Follow-up

14.2~14.4 must add authenticated or fixture-backed desktop screenshots. If fixture remains unavailable, completion notes must keep this as a known gap and must not claim desktop conformance.

## 14.2 Update - Dashboard Shell/Rails/Live Surface

- Current Vite evidence: `current-14-2-dashboard-auth-blocked-desktop-1440x1000.png`
- Observation JSON: `browser-14-2-dashboard-shell-rails-and-live-surface-realignment-observations.json`
- Result: `/dashboard` still renders auth-blocked state only. Full authenticated Project/Application/Main live surface path is not proven.
- Shell evidence in auth-blocked state: Project rail / Application rail / Main appear as 3 columns with measured widths `233 / 350 / 817`, no horizontal page scroll, no overflowing text candidates.
- Code/guard evidence: `DashboardMain` no longer imports or renders `Tabs`; `SnapshotHistoryPanel` remains in the same main flow after live instance entry.

| Area | 14.2 Judgment | Note |
|---|---|---|
| Desktop shell hierarchy | conformant in auth-blocked shell, coverage gap for authenticated data | 3-column rail/main composition is visible before auth data |
| Project rail density | coverage gap | Auth error row only; compact authenticated project rows verified by code/static review, not browser fixture |
| Application rail density | coverage gap | Project not selected; compact authenticated application rows verified by code/static review, not browser fixture |
| Main live surface order | coverage gap + guard evidence | Authenticated surface not rendered; static sentinel guards context -> data quality -> lifecycle -> reasons -> attention -> evidence -> metric -> starter -> instance -> Snapshot/History order |
| Snapshot/History anchor | guard evidence | Tab split removed; same-flow anchor preserved in `DashboardMain` |

## 14.3 Update - Snapshot/History/Detail/Retention

- Current Vite evidence: `current-14-3-dashboard-auth-blocked-desktop-1440x1000.png`
- Observation JSON: `browser-14-3-snapshot-history-detail-and-retention-surface-realignment-observations.json`
- Result: `/dashboard` remains auth-blocked without an authenticated fixture, so authenticated Snapshot/History picker, Snapshot detail, and retention/source absence paths are not browser-proven.
- Auth-blocked viewport check: `bodyScrollWidth=1440`, `viewportWidth=1440`, `hasHorizontalOverflow=false`.
- Code/static evidence: `SnapshotHistoryPanel` now exposes 14일 retention, 672 scheduled points, 30분 cadence, 48/day, default 24h, cleanup hint, marker-first date/slot copy, selected snapshot summary fields, secondary server marker order, and collapsible event context. The primary date/slot map is populated from the 14d retention marker query while the active preset narrows the secondary event/server marker list. Slot labels use currentWindowEndUtc end-boundaries from `00:30Z` through `24:00Z`, and the guard executes midnight boundary cases.
- Code/static evidence: `SnapshotDetailSurface` top surface now shows `Application Dashboard / Snapshot`, `mode=snapshot`, `source=dashboard_snapshots.read_model_json`, `snapshotId`, `capturedAt`, `currentWindowStartUtc`, `currentWindowEndUtc`, `captureReason`, `snapshotDetailRecalculates=false`, `currentStateRecalculated=false`, `markerIsStateSource=false`.

| Area | 14.3 Judgment | Note |
|---|---|---|
| Snapshot/History picker | code/static conformant, browser coverage gap | Authenticated picker not rendered; static sentinel verifies 14d retention query wiring, boundary helpers, retention/summary/source copy |
| 48-slot grid | code/static conformant, browser coverage gap | Desktop class keeps 8-column grid; authenticated clipping not browser-proven |
| Snapshot detail | code/static conformant, browser coverage gap | Stored source flags are visible in code and guard; authenticated detail route not browser-proven |
| Retention/source absence | code/static conformant, browser coverage gap | Error copy has no live/current fallback CTA; authenticated 404/expired path not browser-proven |
