---
title: 'Dashboard RED concern attention evidence'
type: 'bugfix'
created: '2026-06-15'
status: 'done'
baseline_commit: '4e290b0064779b941f33725ebc72d75521e8515c'
context:
  - '{project-root}/_bmad/custom/project-context.md'
---

<frozen-after-approval reason="human-owned intent - do not modify unless human renegotiates">

## Intent

**Problem:** Application Dashboard가 recent 30분 RED 오류/지연 신호를 가지고도 degraded hysteresis를 넘지 못하면 lifecycle state를 `active`로만 표시하고, First Look Candidates/Endpoint Evidence가 비어 보일 수 있다.

**Approach:** degraded threshold는 유지하되, triage concern이 존재하고 guard/confidence가 UI 노출 수준이면 `attention` 상태를 추가한다. app dashboard와 instance dashboard가 같은 normalized endpoint evidence를 공유하도록 route guard와 endpoint priority fallback을 맞춘다.

## Boundaries & Constraints

**Always:** degraded 진입/해소 hysteresis threshold는 그대로 둔다. `attention`은 `DEGRADED`보다 낮고 `ACTIVE`보다 높은 의미로만 사용한다. 5xx endpoint evidence는 root cause 확정이 아니라 "먼저 볼 단서"로 노출한다. raw query/path, UUID, absolute URL, 명백한 식별자 노출 방어는 유지한다.

**Ask First:** schema contract가 `attention` 외 새 state 이름을 요구하거나, frontend가 server read model을 재계산해야 하는 상황이 생기면 멈춘다.

**Never:** degraded threshold를 낮춰서 해결하지 않는다. instance dashboard에서 application state나 endpoint priority를 새로 판정하지 않는다.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| RED concern below degraded | current, requestCount >= 30, errorRate >= 5%, bad bucket threshold 미충족 | lifecycle state `attention`, label "주의 필요" 계열, app triage/first look 유지 | 없음 |
| Degraded precedence | current concern이 degraded hysteresis 진입 조건 충족 | lifecycle state `degraded` | `attention`으로 downgrade하지 않음 |
| No concern or insufficient sample | requestCount 부족 또는 RED concern 없음 | 기존 `active/unknown/idle` 판단 유지 | 없음 |
| Active 5xx endpoint fallback | lifecycle `active`, endpoint errorCount > 0 | Endpoint Evidence/First Look에 낮은 우선순위 후보 노출 | unsafe route는 계속 제외 |
| Normalized smoke route | `/api/ecc-smoke/error-500`, `/api/ecc-smoke/slow-p99` | app endpointPriority와 instance endpoint evidence에서 누락하지 않음 | raw id/query/UUID/absolute URL은 제외 |

</frozen-after-approval>

## Code Map

- `observability-portal/src/main/java/com/observation/portal/domain/state/model/LifecycleStateCode.java` -- lifecycle state enum contract.
- `observability-portal/src/main/java/com/observation/portal/domain/state/service/LifecycleStateService.java` -- current metric state precedence and hysteresis handling.
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/model/ApplicationDashboardReadModel.java` -- state reasons, attention evidence, first look candidates derived from server read model.
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/EndpointEvidenceAggregationService.java` -- shared endpoint route safety/parser for app and instance surfaces.
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/EndpointPriorityService.java` -- app endpoint priority/fallback 5xx ranking.
- `observability-portal/src/main/resources/db/migration` -- dashboard snapshot state code constraint.
- `frontend/src/app/lib/read-model-types.ts`, `frontend/src/app/lib/read-model-adapters.ts` -- frontend state union and display mapping.

## Tasks & Acceptance

**Execution:**
- [x] `LifecycleStateCode.java` / `LifecycleStateService.java` -- add `ATTENTION("attention")` and choose it when concern exists but degraded cannot enter.
- [x] `ApplicationDashboardReadModel.java` / `DashboardReadModelService.java` -- expose triage first look for attention and keep zeroInsight copy from denying low-priority evidence.
- [x] `EndpointEvidenceAggregationService.java` / `EndpointPriorityService.java` -- keep normalized smoke routes, preserve unsafe-route suppression, and sort fallback 5xx evidence after primary concerns.
- [x] `V013__allow_attention_dashboard_snapshot_state.sql` / frontend types/adapters -- allow persisted and rendered `attention`.
- [x] Unit/regression tests -- cover lifecycle, dashboard read model, endpoint priority, and route guard behavior.

**Acceptance Criteria:**
- Given recent 30분 sample이 충분하고 errorRate >= 5%이지만 degraded bad bucket threshold를 만족하지 않을 때, when app dashboard read model을 만들면, then state code는 `attention`이고 label은 "주의 필요" 계열이다.
- Given degraded hysteresis 조건을 만족할 때, then state는 `degraded`가 `attention`보다 우선한다.
- Given requestCount가 부족하거나 RED concern이 없을 때, then 기존 `active/unknown/idle` 판단은 유지된다.
- Given lifecycle state가 `attention`일 때, then First Look Candidates는 app triage/evidence를 비우지 않는다.
- Given lifecycle state가 `active`라도 5xx endpoint evidence가 있을 때, then Endpoint Evidence에는 낮은 우선순위 후보가 표시된다.
- Given `/api/ecc-smoke/error-500` 또는 `/api/ecc-smoke/slow-p99` endpoint evidence가 accepted bucket에 있을 때, then app dashboard endpointPriority/endpoint evidence에서 누락되지 않는다.
- Given raw id, UUID, query string, absolute URL 등 unsafe route evidence가 들어올 때, then 기존처럼 노출하지 않는다.

## Verification

**Commands:**
- `./gradlew :observability-portal:test :observability-portal:frontendBuild` -- backend tests and frontend production build succeed.

## Suggested Review Order

**Lifecycle State**

- attention state enters after degraded hysteresis fails but concern remains.
  [`LifecycleStateService.java:97`](../observability-portal/src/main/java/com/observation/portal/domain/state/service/LifecycleStateService.java#L97)

- concern input keeps degraded thresholds separate from attention confidence.
  [`DegradedHysteresisInput.java:78`](../observability-portal/src/main/java/com/observation/portal/domain/state/model/DegradedHysteresisInput.java#L78)

**Dashboard Evidence**

- first look now treats attention and degraded as concern states.
  [`ApplicationDashboardReadModel.java:1663`](../observability-portal/src/main/java/com/observation/portal/domain/dashboard/model/ApplicationDashboardReadModel.java#L1663)

- zeroInsight no longer denies low-priority endpoint evidence.
  [`DashboardReadModelService.java:1197`](../observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/DashboardReadModelService.java#L1197)

**Endpoint Evidence**

- normalized diagnostic route segments survive app dashboard safety filtering.
  [`EndpointEvidenceAggregationService.java:37`](../observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/EndpointEvidenceAggregationService.java#L37)

- fallback 5xx evidence sorts after primary concerns by explicit counts.
  [`EndpointPriorityService.java:271`](../observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/EndpointPriorityService.java#L271)

**Contract And Tests**

- snapshot state constraint accepts persisted attention states.
  [`V013__allow_attention_dashboard_snapshot_state.sql:1`](../observability-portal/src/main/resources/db/migration/V013__allow_attention_dashboard_snapshot_state.sql#L1)

- read model test captures attention and first-look regression.
  [`DashboardReadModelServiceTest.java:827`](../observability-portal/src/test/java/com/observation/portal/domain/dashboard/service/DashboardReadModelServiceTest.java#L827)

- endpoint test protects smoke routes and unsafe route suppression.
  [`EndpointPriorityServiceTest.java:179`](../observability-portal/src/test/java/com/observation/portal/domain/dashboard/service/EndpointPriorityServiceTest.java#L179)
