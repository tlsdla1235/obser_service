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
