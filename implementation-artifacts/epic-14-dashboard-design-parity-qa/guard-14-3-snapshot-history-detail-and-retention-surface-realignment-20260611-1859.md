# Guard Evidence - Story 14.3 Snapshot/History/Detail/Retention

## Scope

- 기준은 `planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html`이다.
- `source-of-truth-dashboard-snapshot-picker.png`는 기준으로 삼지 않았다.
- `dbml-error.log`와 `planning-artifacts/stories/13-ui-dashboard-source-of-truth-surface-realignment.md`는 수정하지 않았다.
- Backend code/tests, migration/schema, Source of Truth mockup HTML, completed Epic 13 story body/status는 수정하지 않았다.

## Command Results

| Command | Result |
|---|---|
| `cd frontend && npm run guard:read-model-contract` | pass: `read-model contract guard fixtures passed` |
| `cd frontend && npm run typecheck` | pass |
| `cd frontend && npm run build` | pass: Vite build completed in `1.04s` |

Final `git diff --check`, YAML parse, and final status are recorded in Story 14.3 Dev Agent Record after story document synchronization.

## Static Grep Classification

| Search | Classification |
|---|---|
| `hourly scheduled`, `hourly snapshot`, `marker state`, `current fallback`, `raw snapshot`, `endpoint timeseries`, `arbitrary query`, `retention fallback` | No production user-facing regression in Snapshot/History or Snapshot detail. New guard explicitly rejects raw explorer/fallback terms in those components. |
| `healthScore`, `rootCause`, `recoveryProof` | Hits are guard negative fixtures/assertions or forbidden-field guard constants. |
| `p95`, `p99`, `histogram percentile` | Existing dashboard/instance display fields and guard fixtures; Story 14.3 did not add p95/p99/histogram percentile calculation to Snapshot/History or Snapshot detail. |
| `.sort()`, `.toSorted()`, `.reduce()` in `frontend/src/app/components frontend/src/app/lib frontend/scripts` | No hits. |
| source terms (`dashboard_snapshots.read_model_json`, `currentWindowEndUtc`, `markerIsStateSource`, `snapshotDetailRecalculates`, `currentStateRecalculated`, `accepted_metric_buckets`, `recent_30_minutes`) | Present in guard, fixtures, types, and source-semantics copy. |
| Snapshot/History date/slot structure | Guard now asserts 14d retention marker query wiring and executes date/slot boundary helpers: `horizon.until=00:00Z` starts at the previous slot day, `00:00Z` markers map to previous-day `24:00Z`, and labels run from `00:30Z` through `24:00Z`. |

## Browser Evidence

| Viewport | Evidence | Result |
|---|---|---|
| desktop `1440x1000` | `current-14-3-dashboard-auth-blocked-desktop-1440x1000.png` | `/dashboard` auth-blocked shell only. `bodyScrollWidth=1440`, `viewportWidth=1440`, no horizontal overflow. |
| tablet `1024x900` | `current-14-3-dashboard-auth-blocked-tablet-1024x900.png` | `/dashboard` auth-blocked shell only. `bodyScrollWidth=1024`, `viewportWidth=1024`, no horizontal overflow. |
| mobile `390x844` | `current-14-3-dashboard-auth-blocked-mobile-390x844.png` | `/dashboard` auth-blocked shell only. `bodyScrollWidth=390`, `viewportWidth=390`, no horizontal overflow. |

Observation JSON: `browser-14-3-snapshot-history-detail-and-retention-surface-realignment-observations.json`

## Coverage Gap

Authenticated fixture/token/runbook was not available. Therefore this story does **not** claim browser-proven authenticated conformance for `Project -> Application -> Dashboard -> Snapshot -> Instance -> retention expired`, Snapshot/History picker, Snapshot detail, or retention expired/source absence paths. Those surfaces are covered by code/static guard evidence in Story 14.3 and remain browser visual QA follow-up for Story 14.4 or a fixture-backed run.
