---
title: 'Snapshot History KST Detail Open Fixes'
type: 'bugfix'
created: '2026-06-12'
status: 'done'
baseline_commit: 'ccb61ec36d24029026475a330b671998b382dab1'
context:
  - '{project-root}/_bmad/custom/project-context.md'
  - '{project-root}/planning-artifacts/source-of-truth/dashboard-snapshot-mvp-source-of-truth.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Snapshot / History grid labels saved 30-minute slots as UTC/Z times, so a snapshot stored at `13:00Z` reads as 13:00 even though the operator-facing dashboard should show the same instant as `22:00 KST`. The same panel's `Snapshot 열기` action reaches the backend and receives HTTP 200, but the detail payload serializes stored read-model `JsonNode` values as Jackson internal bean metadata, causing the frontend detail contract guard to reject the response and show "스냅샷 상세 로드 실패".

**Approach:** Keep backend stored-snapshot lookup and marker semantics intact, but make the detail API response serialize bounded stored read-model blocks as plain JSON-compatible Java values instead of Jackson tree objects. Update the frontend slot label helper and contract guard so grid slot labels display KST end-boundary times while UTC API contracts and slot indexing remain unchanged.

## Boundaries & Constraints

**Always:** Keep scope limited to Snapshot / History time display and snapshot detail response loading. Preserve server-computed snapshot marker/detail semantics, project/application scoping, and stored snapshot data as the source of truth. Add or update tests at the layer where the bug appears.

**Ask First:** Creating schema migrations, changing snapshot table structure, altering stored `read_model_json` shape at write time, or changing SoT visual alignment beyond the label bug requires explicit human approval.

**Never:** Do not recompute snapshot detail data in the frontend. Do not alter SoT mockup alignment, row styling, modal styling, unrelated instance summary UI, or existing user/untracked changes. Do not expose raw stored read-model JSON as an escape hatch.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Detail payload has Jackson tree fields | Stored snapshot row has valid `read_model_json` and detail API returns `readModel` blocks | HTTP 200 body contains normal JSON primitives/objects/arrays such as `"schemaVersion":"1.0"` and `"window":{...}`, not `JsonNode` bean fields like `nodeType` | Existing projection exception handling remains unchanged for malformed root JSON |
| Snapshot slot label crosses local timezone | Marker current window ends at `2026-06-11T13:00:00Z` and maps to slot index 25 | Grid displays the end boundary as `22:00 KST` while slot index and API filtering stay UTC-based | Invalid slot index keeps a stable fallback label rather than affecting data loading |

</frozen-after-approval>

## Code Map

- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotDetailReadModel.java` -- Public snapshot detail response DTO; currently exposes Jackson `JsonNode` fields in `StoredReadModel`, `EndpointEvidenceItem`, and `InstanceSummaryItem`.
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotDetailProjectionParser.java` -- Parses stored `read_model_json` and constructs bounded response blocks.
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/controller/DashboardSnapshotControllerTest.java` -- Controller/API serialization tests for snapshot endpoints.
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotDetailProjectionParserTest.java` -- Parser behavior tests for stored snapshot projection.
- `frontend/src/app/lib/read-model-adapters.ts` -- Slot index and label helper used by Snapshot / History grid.
- `frontend/scripts/read-model-contract-guard.ts` -- Frontend contract guard assertions for slot labels and snapshot detail shape.

## Tasks & Acceptance

**Execution:**
- [x] `DashboardSnapshotDetailReadModel.java` -- Replace public response `JsonNode` fields with JSON-serializable `Object` values where the API intentionally preserves bounded stored JSON blocks.
- [x] `DashboardSnapshotDetailProjectionParser.java` -- Convert Jackson tree nodes to plain Java values before constructing response DTOs, while retaining `JsonNode` internally for parsing/classification.
- [x] `DashboardSnapshotControllerTest.java` and/or parser tests -- Reproduce the HTTP 200 but contract-invalid detail response by asserting serialized detail JSON contains real values and does not contain Jackson tree metadata.
- [x] `frontend/src/app/lib/read-model-adapters.ts` -- Change slot end-boundary labels to KST display text without changing UTC slot indexing.
- [x] `frontend/scripts/read-model-contract-guard.ts` -- Update guard expectations to the KST label contract and keep detail guard coverage intact.

**Acceptance Criteria:**
- Given a stored snapshot detail row with valid `read_model_json`, when the detail API is requested, then `readModel.schemaVersion`, `readModel.mode`, `readModel.window`, arrays, and nested evidence blocks serialize as normal JSON values accepted by the frontend guard.
- Given a marker ending at `13:00Z`, when Snapshot / History renders the slot grid, then the selected slot label is displayed as `22:00 KST`.
- Given the existing marker API and 30-minute slot indexing, when labels are changed to KST, then marker lookup, date grouping, and snapshot selection still use the backend-provided UTC window boundaries.
- Given this bugfix, when reviewing the diff, then no SoT alignment, modal styling, row styling, schema migration, or frontend recomputation of marker data is introduced.

## Spec Change Log

## Verification

**Commands:**
- `./gradlew :observability-portal:test --tests com.observation.portal.domain.snapshot.controller.DashboardSnapshotControllerTest` -- expected: snapshot detail serialization test passes.
- `./gradlew :observability-portal:test --tests com.observation.portal.domain.snapshot.service.DashboardSnapshotDetailProjectionParserTest` -- expected: projection parser still preserves bounded stored data.
- `./gradlew :observability-portal:test` -- expected: backend regression suite passes if time allows.
- `cd frontend && npm run typecheck` -- expected: TypeScript passes.
- `cd frontend && npm run guard:read-model-contract` -- expected: frontend contract guard passes with KST labels.
- Browser on `http://127.0.0.1:8080/dashboard` -- expected: Snapshot / History shows KST slot labels and `Snapshot 열기` loads detail instead of the detail failure state.

## Suggested Review Order

**Snapshot Detail JSON Contract**

- Response DTO accepts plain JSON values, not Jackson tree objects.
  [`DashboardSnapshotDetailReadModel.java:247`](../observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotDetailReadModel.java#L247)

- Constructor guard prevents future JsonNode/POJO regression.
  [`DashboardSnapshotDetailReadModel.java:543`](../observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotDetailReadModel.java#L543)

- Parser converts stored tree nodes before building response DTOs.
  [`DashboardSnapshotDetailProjectionParser.java:55`](../observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotDetailProjectionParser.java#L55)

- Conversion helper keeps tree parsing internal to the service.
  [`DashboardSnapshotDetailProjectionParser.java:467`](../observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotDetailProjectionParser.java#L467)

**KST Slot Labels**

- Slot indexing remains UTC, display labels become KST.
  [`read-model-adapters.ts:379`](../frontend/src/app/lib/read-model-adapters.ts#L379)

- Contract guard locks 22:00 KST and D+1 boundaries.
  [`read-model-contract-guard.ts:289`](../frontend/scripts/read-model-contract-guard.ts#L289)

**Regression Tests**

- Controller serialization asserts normal JSON and no Jackson metadata.
  [`DashboardSnapshotControllerTest.java:86`](../observability-portal/src/test/java/com/observation/portal/domain/snapshot/controller/DashboardSnapshotControllerTest.java#L86)

- Parser test rejects Jackson tree values at public response boundary.
  [`DashboardSnapshotDetailProjectionParserTest.java:205`](../observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotDetailProjectionParserTest.java#L205)
