# Side-by-Side Note - Mobile 390x844

## Evidence

- Current Vite: `baseline-mobile-390x844.png`
- Mockup reference: `mockup-reference-mobile-390x844.png`
- Observation JSON: `browser-baseline-observations.json`

## Current Observation

`/dashboard` returned HTTP 200, but no access token/authenticated fixture was available. The visible UI stops at auth-required state. Playwright measured:

- `body.scrollWidth=504`
- `viewportWidth=390`

This is an auth-blocked route observation. It must not be used as proof that the authenticated Dashboard has the same horizontal overflow, but it is a mobile QA warning for follow-up.

## Mobile Judgment Fields

| Area | Judgment | Note |
|---|---|---|
| Page horizontal scroll | needs follow-up | Auth-blocked baseline has body wider than viewport |
| Project rail | coverage gap | Auth error only |
| Application rail | coverage gap | Project not selected |
| Main surface | coverage gap | No dashboard data rendered |
| Snapshot slot grid | coverage gap | Not reachable |
| Snapshot detail | coverage gap | Not reachable |
| Instance modal | coverage gap | Not reachable |
| Retention/source absence | coverage gap | Not reachable |

## Follow-up

14.2 must check mobile page width after authenticated data renders. 14.3 must check date map and slot grid wrapping. 14.4 must check modal body/header and endpoint table overflow. Any unavoidable mobile adaptation must be recorded in `deviation-log.md` as category 2 with reviewer decision.

## 14.2 Update - Dashboard Shell/Rails/Live Surface

- Current Vite evidence: `current-14-2-dashboard-auth-blocked-mobile-390x844.png`
- Observation JSON: `browser-14-2-dashboard-shell-rails-and-live-surface-realignment-observations.json`
- Result: `/dashboard` remains auth-blocked without an authenticated fixture.
- Shell evidence in auth-blocked state: Project rail -> Application rail -> Main order is preserved as a single-column stack, each measured at `390px`.
- Page overflow: global nav wrapping was adjusted; latest measurement is `bodyScrollWidth=390`, `viewportWidth=390`, `hasHorizontalPageScroll=false`, and no overflowing text candidates.

| Area | 14.2 Judgment | Note |
|---|---|---|
| Page horizontal scroll | conformant in auth-blocked shell | 14.1 warning resolved for accessible route state |
| Project -> Application -> Main order | conformant in auth-blocked shell | Stack order preserved |
| Project rail | coverage gap | Auth error row only; authenticated compact row not browser-proven |
| Application rail | coverage gap | Project not selected; authenticated badge wrapping not browser-proven |
| Context bar / main live surface | coverage gap + guard evidence | Authenticated context/read semantics bar not rendered in browser |
| Snapshot/History anchor | guard evidence | Tab split removed; same-flow static sentinel added |

## 14.3 Update - Snapshot/History/Detail/Retention

- Current Vite evidence: `current-14-3-dashboard-auth-blocked-mobile-390x844.png`
- Observation JSON: `browser-14-3-snapshot-history-detail-and-retention-surface-realignment-observations.json`
- Result: `/dashboard` remains auth-blocked without an authenticated fixture, so authenticated Snapshot/History picker, Snapshot detail, and retention/source absence paths are not browser-proven.
- Auth-blocked viewport check: `bodyScrollWidth=390`, `viewportWidth=390`, `hasHorizontalOverflow=false`.
- Code/static evidence: `SnapshotHistoryPanel` uses wrapping slot labels and mobile 4-column 48-slot grid. Slot labels follow currentWindowEndUtc end-boundaries from `00:30Z` through `24:00Z`; guard boundary cases cover exact `00:00Z` as previous-day `24:00Z`. The responsive adaptation is recorded as `R-20260611-143-001` in `deviation-log.md`.
- Code/static evidence: selected snapshot summary cells and Snapshot detail info cells use wrapping text instead of truncation to reduce clipping risk on narrow screens.

| Area | 14.3 Judgment | Note |
|---|---|---|
| Page horizontal scroll | conformant in auth-blocked shell | No horizontal overflow in accessible route state |
| Snapshot/History picker | code/static conformant, browser coverage gap | Authenticated picker not rendered |
| 48-slot grid | allowed deviation + browser coverage gap | Mobile 4-column adaptation category 2; authenticated clipping not browser-proven |
| Snapshot detail | code/static conformant, browser coverage gap | Top source flags wrap instead of truncating |
| Retention/source absence | code/static conformant, browser coverage gap | Safe copy has no fallback CTA; authenticated expired path not browser-proven |
