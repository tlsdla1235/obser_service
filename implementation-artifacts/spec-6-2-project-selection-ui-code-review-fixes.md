---
title: 'Story 6.2 Project selection UI code-review fixes'
type: 'bugfix'
created: '2026-05-28'
status: 'done'
baseline_commit: 'c8a4f74abfe0430b81f4728f990d92725c04180e'
context:
  - '{project-root}/implementation-artifacts/epic-6-context.md'
  - '{project-root}/planning-artifacts/stories/6-2-project-selection-ui.md'
  - '{project-root}/implementation-artifacts/spec-story-6-2-project-selection-ui-contract-decisions.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Story 6.2 review found that the static Project selection UI can expose unsafe application navigation, let filter input overwrite loading/auth/error state, and accept stale async project responses after token changes. The focused contract tests also leave false positives around `links.applications`, state transitions, responsive badge wrapping, and Korean test intent documentation.

**Approach:** Keep the static dashboard boundary and `GET /api/projects` current shape, but add an explicit UI view state, latest-request guard, clear-token reset, and a disabled/pending Application List action that preserves `links.applications` without direct browser navigation. Strengthen focused contract tests to execute or structurally validate the rendered behavior rather than only searching for loose strings.

## Boundaries & Constraints

**Always:** Consume only `generatedAt`, `projects[].projectId`, `name`, `applicationCount`, `setupConnectionIssueCount`, `recentConcern`, and `links.applications`. Keep in-memory Bearer header behavior for `GET /api/projects`; render loading, auth-required, error, empty, ready, and filtered-empty states without leaking token/provider/internal payloads. Preserve Story 6.1 setup guide scope: dependency, `observation.heartbeat.portal-base-url`, `observation.heartbeat.project-key`, `observation.metric-flush.environment`.

**Ask First:** If a real Application List UI, public project creation route, browser token persistence policy, new frontend stack, or backend API/schema change appears necessary, stop and ask before implementation.

**Never:** Do not add dashboard shortcuts, first application auto-select, application id inference, direct unauthenticated navigation to `/api/projects/**`, `setupIssueCount` or `recentConcernCount` fallback, project creation flow, `POST /api/projects`, login-time project creation, localStorage/sessionStorage/cookie/URL token parsing, or UI-side lifecycle/setup diagnosis/project health/status/priority/severity/p95/p99/endpoint priority/snapshot event/project ranking calculations.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Ready list | Valid token fetch returns projects with `links.applications` | Cards show standard fields and a pending/disabled Application List action that keeps the API link value in DOM data for future authenticated fetch UI | Invalid or missing link disables the action without reconstructing from `projectId` |
| Non-ready filter | Loading, auth-required, or error state is visible | Filter input is disabled and typing cannot replace safe copy with empty or stale project list | Existing safe state copy remains visible |
| Token clear | `clearAccessToken()` is called after projects loaded or request in flight | In-memory projects/generatedAt are cleared and auth-required state renders | Any older fetch result is ignored |
| Request race | Earlier 401/error/list response resolves after a newer request | Only the latest request can change view state and DOM | Stale responses return without rendering |

</frozen-after-approval>

## Code Map

- `observability-portal/src/main/resources/static/dashboard/app.js` -- Static Project selection state, authenticated fetch, card rendering, filter, and pending Application List action.
- `observability-portal/src/main/resources/static/dashboard/index.html` -- Static dashboard shell, Project filter/reload controls, and Story 6.1 setup guide copy.
- `observability-portal/src/main/resources/static/dashboard/styles.css` -- Responsive layout and badge/action wrapping constraints.
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ProjectSelectionUiContractTest.java` -- Focused Story 6.2 static UI contract guard.
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ProjectEntryUiContractTest.java` -- Existing broader static entry guard to keep aligned if shared strings change.

## Tasks & Acceptance

**Execution:**
- [x] `app.js` -- introduce explicit view state, filter enablement, token clear reset, latest request guard, and pending Application List action that stores `project.links.applications` without direct `<a href>` navigation.
- [x] `styles.css` -- constrain long badge/counter text on mobile with max-width, wrapping, and stable inline-flex behavior.
- [x] `ProjectSelectionUiContractTest.java` -- add Korean Javadoc and stronger JS/structure checks for pending application action, non-ready filter safety, token clear reset, and stale response guard.
- [x] `ProjectEntryUiContractTest.java` -- adjust shared static UI expectations only if the safer application action changes required contract strings.

**Acceptance Criteria:**
- Given a rendered project card, when the application action is inspected, then it preserves the response `links.applications` value but does not navigate the browser directly to `/api/projects/**`.
- Given loading, 401/auth-required, or generic error state, when the filter changes, then safe state copy remains visible and stale/empty list rendering does not replace it.
- Given token clear or overlapping project fetches, when older responses settle, then memory/DOM remain controlled by the latest auth/view state.
- Given long `recentConcern.label` or count strings on small screens, when badges wrap, then text stays within the card.

## Design Notes

The Application List action should be a disabled/pending button or equivalent static-shell boundary. It should keep the server-provided `links.applications` as data for Story 6.3 without adding URL token workarounds, cookie persistence, dashboard shortcuts, or application id derivation.

## Verification

**Commands:**
- `./gradlew :observability-portal:test --tests '*ProjectNavigation*'` -- expected: pass
- `./gradlew :observability-portal:test --tests '*ProjectEntryUiContract*'` -- expected: pass
- `./gradlew :observability-portal:test --tests '*ProjectSelectionUiContract*'` -- expected: pass
- `./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest` -- expected: pass
- `./gradlew :observability-portal:test` -- expected: pass
- `git diff --check` -- expected: no whitespace errors

## Suggested Review Order

**Auth And Request State**

- Start here: token-gated fetch and latest-response ownership.
  [`app.js:42`](../observability-portal/src/main/resources/static/dashboard/app.js#L42)

- Token set/clear now resets stale list, timestamp, filter, and in-flight ownership.
  [`app.js:23`](../observability-portal/src/main/resources/static/dashboard/app.js#L23)

- Filter renders only while ready or filtered-empty.
  [`app.js:236`](../observability-portal/src/main/resources/static/dashboard/app.js#L236)

**Application Boundary**

- Project cards preserve `links.applications` without API navigation.
  [`app.js:146`](../observability-portal/src/main/resources/static/dashboard/app.js#L146)

- Internal link guard rejects external, mismatched, query/hash, and malformed links.
  [`app.js:176`](../observability-portal/src/main/resources/static/dashboard/app.js#L176)

**Responsive Polish**

- Project toolbar adds generated-at and initially disabled filter affordance.
  [`index.html:27`](../observability-portal/src/main/resources/static/dashboard/index.html#L27)

- Badge wrapping keeps long concern/count text inside cards.
  [`styles.css:249`](../observability-portal/src/main/resources/static/dashboard/styles.css#L249)

**Contract Tests**

- Story 6.2 Javadoc explains static UI boundary intent.
  [`ProjectSelectionUiContractTest.java:15`](../observability-portal/src/test/java/com/observation/portal/domain/dashboard/ProjectSelectionUiContractTest.java#L15)

- Focused tests lock pending action, token gating, stale response, and link guard behavior.
  [`ProjectSelectionUiContractTest.java:65`](../observability-portal/src/test/java/com/observation/portal/domain/dashboard/ProjectSelectionUiContractTest.java#L65)
