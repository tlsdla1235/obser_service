# Source Semantics Sentinel Review

Story 14.1은 production guard를 확장하지 않고, 기존 sentinel이 Epic 13 source semantics를 계속 감시하는지 확인했다.

## Command Results

| Command | Result |
|---|---|
| `cd frontend && npm run guard:read-model-contract` | pass: `read-model contract guard fixtures passed` |
| `cd frontend && npm run typecheck` | pass |
| `cd frontend && npm run build` | pass: Vite build completed in `1.07s` |
| `ruby -e 'require "yaml"; YAML.load_file("implementation-artifacts/sprint-status.yaml"); puts "yaml ok"'` | pass: `yaml ok` |

## Existing Sentinel Coverage

| Semantics | Existing guard evidence |
|---|---|
| Application Dashboard live source | `accepted_metric_buckets`, `recent_30_minutes` assertions in `frontend/scripts/read-model-contract-guard.ts` and constants in `frontend/src/app/lib/read-model-contract-guard.ts` |
| Snapshot detail stored source | `dashboard_snapshots.read_model_json`, `snapshotDetailRecalculates=false`, `markerIsStateSource=false` assertions |
| Snapshot marker/state boundary | marker bucket fixture and guard text keep marker as timeline index, not state source |
| Instance live source | `mode=live`, `window.name=recent_30_minutes`, `windowSource=live_recent_30_minutes`, `source=accepted_metric_buckets` |
| Instance snapshot mode | `acceptedAtCutoffApplied=false`, `includesLateAcceptedMetrics=true`, `mayDifferFromStoredApplicationSnapshot=true`, `applicationSnapshotRecalculated=false`, `markerIsStateSource=false` |
| Stored trend source | `dashboard_snapshots.read_model_json.instanceSummary.items[]` copy/fixture coverage |
| Forbidden instance decision fields | `healthScore`, `rootCause`, `recoveryProof` negative fixtures/assertions |

## Static Grep Classification

| Search | Result |
|---|---|
| `current_15m`, `hourly scheduled`, `hourly snapshot`, `live/current fallback`, `current dashboard fallback`, `endpoint timeseries`, `raw snapshot`, `raw metric`, `arbitrary query` | no production user-facing hit in searched frontend source/scripts |
| `marker.*state`, `healthScore`, `rootCause`, `recoveryProof` | hits are guard negative fixtures/assertions or explanatory comments that prevent forbidden semantics |
| `.sort(`, `.toSorted(`, `.reduce(` in `frontend/src/app/components frontend/src/app/lib frontend/scripts` | no hit |
| Source terms (`dashboard_snapshots.read_model_json`, `accepted_metric_buckets`, `recent_30_minutes`, flags) | present in types, guard, fixtures, and surface copy |

## Sentinel Candidates For 14.2~14.4

14.1 did not add production guard code. 후속 story에서 필요하면 아래 fixture/static sentinel을 추가한다.

- 14.2: desktop shell/rail/main conformance fixture or static selector note that detects a tab-only flow hiding Snapshot/History from the main hierarchy, if implementation keeps tabs.
- 14.3: retention expired/source absence browser fixture that proves no live/current fallback CTA appears in Snapshot detail and date/slot error state.
- 14.3: mobile slot grid visual sentinel note for 48-slot wrapping and clipped label checks.
- 14.4: wide modal visual sentinel for `1120px`-class width, sticky header, normalized endpoint table overflow, and no narrow Sheet for live/snapshot detail.
- 14.4: authenticated full-path fixture/runbook candidate for `Project -> Application -> Dashboard -> Snapshot -> Instance -> retention expired`.
