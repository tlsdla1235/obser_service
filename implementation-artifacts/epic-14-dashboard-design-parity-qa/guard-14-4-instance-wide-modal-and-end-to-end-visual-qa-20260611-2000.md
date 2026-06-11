# Guard Evidence - Story 14.4 Instance Wide Modal And End-To-End Visual QA

## Command Results

| Command | Result |
|---|---|
| `cd frontend && npm run guard:read-model-contract` | pass: `read-model contract guard fixtures passed` |
| `cd frontend && npm run typecheck` | pass |
| `cd frontend && npm run build` | pass: Vite build completed in `1.32s` |
| `git diff --check` | pass |
| `ruby -e 'require "yaml"; YAML.load_file("implementation-artifacts/sprint-status.yaml"); puts "yaml ok"'` | pass: `yaml ok` |

## 14.4 Static Sentinel Coverage

| Target | Evidence |
|---|---|
| Instance live/snapshot wide modal | `frontend/scripts/read-model-contract-guard.ts` verifies `DialogContent`, `w-[min(1120px,calc(100vw-2rem))]`, and sticky `DialogHeader` |
| Modal body order | Sentinel verifies `InstanceContextNote -> ApplicationStateReferencePanel -> ReadSemanticsPanel -> MetricGrid -> EndpointEvidencePanel -> ResourceEvidencePanel -> StarterConnectionPanel -> NormalizedEndpointEvidenceTable` |
| No extra body header before Application state reference | Sentinel rejects `<ContextHeader` in `InstanceDashboardSurface` |
| Snapshot mode note | Sentinel verifies selected Application Snapshot copy and no stored Application Snapshot state/evidence override copy |
| Instance top-level state | Sentinel verifies explicit `instance top-level state=없음` and source/mode/window cells |
| Trend surface absence | Sentinel rejects `InstanceTrendView`, `Stored trend`, `SheetContent`, and `snapshotTrend` in `InstancePanels` |

## Static Grep Classification

| Search | Classification |
|---|---|
| `healthScore`, `rootCause`, `recoveryProof`, `instanceState`, `currentState`, `stateCode` | Hits are guard negative fixtures/assertions, stored Snapshot state fields, type definitions, and Snapshot detail read-semantics fields. No new Instance Dashboard health/root cause/recovery UI was added. |
| Snapshot semantic flags | Expected hits in type definitions, fixtures, guard assertions, `SnapshotDetailSurface`, and `InstanceDashboardSurface` info cells. |
| Modal/source terms | Expected hits in 14.4 static sentinel and production copy for wide Dialog, trend surface absence, snapshot note, and normalized endpoint table. |
| `.sort(`, `.toSorted(`, `.reduce(` | no hits in searched frontend component/lib/script paths |

## Browser QA Scope

`browser-14-4-instance-wide-modal-and-end-to-end-visual-qa-observations.json` records desktop `1440x1000`, tablet `1024x900`, and mobile `390x844` auth-blocked `/dashboard` screenshots. All three accessible route states had `hasHorizontalOverflow=false`.

No executable `.private/smoke-auth.env` access token fixture was present, so `Project -> Application -> Dashboard -> Snapshot -> Instance -> retention expired` was not exercised and remains a coverage gap, not a conformance pass claim.
