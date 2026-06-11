# Side-by-Side Note - Tablet 1024x900

## Evidence

- Current Vite: `baseline-tablet-1024x900.png`
- Mockup reference: `mockup-reference-tablet-1024x900.png`

## Current Observation

`/dashboard` returned HTTP 200, but no access token/authenticated fixture was available. The visible UI stops at the same auth-required state as desktop:

- Project rail auth error.
- Application rail project-selection wait state.
- Main project-selection wait state.

## Tablet Judgment Fields

| Area | Judgment | Note |
|---|---|---|
| Rail/main adaptation | coverage gap | Auth-blocked shell is visible, authenticated rail/main stacking unverified |
| Project rail | coverage gap | Auth error only |
| Application rail | coverage gap | Project not selected |
| Main surface | coverage gap | No dashboard data rendered |
| Snapshot/History | coverage gap | Not reachable |
| Instance modal | coverage gap | Not reachable |
| Retention/source absence | coverage gap | Not reachable |

## Follow-up

14.2 should confirm tablet rail/main layout does not clip row text, context badges, or section headings. 14.3 should confirm date map and 48-slot grid wrapping. 14.4 should confirm wide modal adapts without header/body clipping.

## 14.2 Update - Dashboard Shell/Rails/Live Surface

- Current Vite evidence: `current-14-2-dashboard-auth-blocked-tablet-1024x900.png`
- Observation JSON: `browser-14-2-dashboard-shell-rails-and-live-surface-realignment-observations.json`
- Result: `/dashboard` remains auth-blocked without an authenticated fixture.
- Shell evidence in auth-blocked state: Project rail and Application rail render as two columns, Main renders in the next full-width row. Measured widths are `455 / 569 / 1024`.
- Page overflow: `bodyScrollWidth=1024`, `viewportWidth=1024`, no overflowing text candidates.

| Area | 14.2 Judgment | Note |
|---|---|---|
| Rail/main adaptation | conformant in auth-blocked shell | Matches Story 14.2 tablet expectation: rail 2-column + main next row |
| Project rail | coverage gap | Auth error row only |
| Application rail | coverage gap | Project not selected |
| Main live surface | coverage gap + guard evidence | Authenticated order not browser-proven; static sentinel guards same-flow order |
| Snapshot/History anchor | guard evidence | Tab split removed; anchor remains in `DashboardMain` |

## 14.3 Update - Snapshot/History/Detail/Retention

- Current Vite evidence: `current-14-3-dashboard-auth-blocked-tablet-1024x900.png`
- Observation JSON: `browser-14-3-snapshot-history-detail-and-retention-surface-realignment-observations.json`
- Result: `/dashboard` remains auth-blocked without an authenticated fixture, so authenticated Snapshot/History picker, Snapshot detail, and retention/source absence paths are not browser-proven.
- Auth-blocked viewport check: `bodyScrollWidth=1024`, `viewportWidth=1024`, `hasHorizontalOverflow=false`.
- Code/static evidence: Snapshot/History retention summary, 14d retention marker query wiring, executable date/slot boundary helpers, currentWindowEndUtc end-boundary slot labels, selected summary, secondary marker list, event details, and Snapshot detail source flags are guarded by `guard:read-model-contract`.

| Area | 14.3 Judgment | Note |
|---|---|---|
| Snapshot/History picker | code/static conformant, browser coverage gap | Authenticated picker not rendered |
| 48-slot grid | code/static conformant, browser coverage gap | Tablet class uses 6-column adaptation; authenticated clipping not browser-proven |
| Snapshot detail | code/static conformant, browser coverage gap | Stored source flags are visible in code and guard |
| Retention/source absence | code/static conformant, browser coverage gap | Safe copy has no fallback CTA; authenticated 404/expired path not browser-proven |
