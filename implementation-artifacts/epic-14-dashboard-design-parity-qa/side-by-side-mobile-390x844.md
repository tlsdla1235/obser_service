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
