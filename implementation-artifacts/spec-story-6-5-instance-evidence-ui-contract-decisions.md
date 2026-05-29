---
artifactType: contract-decisions
projectName: Spring Boot 운영 첫 화면 포털
storyId: "6.5"
storyKey: "6-5-instance-evidence-ui"
status: closed
date: 2026-05-29
scope: Story 6.5 pre-create-story contract closure before BMAD create-story execution
---

# Story 6.5 Instance Evidence UI Contract Decisions

## Purpose

Story 6.5는 Application Dashboard의 bounded instance summary에서 Instance Detail evidence drill-down으로 진입하는 UI 경계를 닫는다.

이 문서는 Story 6.5 create-story 실행 전에 사용자가 직접 선택했거나 Codex가 프로젝트 계약 기준으로 권장해 닫은 계약을 기록한다. 목표는 구현자가 Instance Evidence UI를 만들면서 navigation mode, evidence link source, copy 의미, percentile/trend 경계, endpoint evidence 의미, static UI guard를 다시 열지 않게 하는 것이다.

Story 6.5는 Epic 5 Story 5.6에서 구현한 current Instance Evidence API를 static dashboard runtime 안에서 표시하는 UI story다. Backend API/schema/migration 확장, Instance Snapshot Trend UI, Snapshot/History marker/detail, demo fixture/hardening, browser token persistence, SPA routing, frontend build stack은 만들지 않는다.

## Authority and Non-Reopen Rules

이 문서는 아래 계약과 산출물을 기준으로 한다.

- `implementation-artifacts/sprint-status.yaml`
- `implementation-artifacts/epic-6-context.md`
- `implementation-artifacts/spec-story-5-6-instance-evidence-contract-decisions.md`
- `planning-artifacts/stories/5-6-instance-evidence-read-model.md`
- `implementation-artifacts/spec-story-6-4-application-dashboard-ui-integration-contract-decisions.md`
- `planning-artifacts/stories/6-4-application-dashboard-ui-integration.md`
- `planning-artifacts/epics.md`
- `planning-artifacts/sprint-plan.md`
- `planning-artifacts/current-product-source-of-truth.md`
- `planning-artifacts/api-surface.md`
- `planning-artifacts/project-structure.md`
- `planning-artifacts/contracts/account-auth-policy.md`
- `planning-artifacts/contracts/read-model-contract.md`
- `planning-artifacts/contracts/dashboard-read-model.md`
- `planning-artifacts/contracts/operational-event-history.md`
- `observability-portal/src/main/java/com/observation/portal/domain/instance/controller/InstanceEvidenceController.java`
- `observability-portal/src/main/java/com/observation/portal/domain/instance/model/InstanceEvidenceReadModel.java`
- `observability-portal/src/main/resources/static/dashboard/`
- `AGENTS.md`
- `_bmad/custom/project-context.md`

Story 6.5 create-story/dev-story는 아래 결정을 다시 열지 않는다.

- Instance Evidence 화면은 같은 `dashboard-detail` 영역을 Instance Detail mode로 전환해 표시한다.
- Instance Detail은 항상 Application Dashboard로 돌아가는 action을 제공한다.
- Evidence fetch source는 Application Dashboard `instances[].links.evidence`뿐이다.
- UI는 evidence path를 재구성하지 않고, 현재 selected project/application/instance와 일치하는 내부 evidence link만 authenticated `fetch`한다.
- UI는 `GET /api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/evidence` response를 표시만 한다.
- UI는 lifecycle state, starter diagnosis, instance health, rule, p95/p99, endpoint priority, snapshot/history event를 재계산하지 않는다.
- Metric data axis와 starter connection axis는 Instance Detail에서도 분리한다.
- Instance Detail은 current 15분 evidence surface이며, 24h/7d/14d trend나 snapshot/history를 렌더링하지 않는다.
- `endpointEvidence`는 bounded evidence subset이지 새 instance endpoint ranking이나 root cause surface가 아니다.
- Story 6.5 completion은 static UI contract test를 포함해 검증한다.

## Closed Decisions

### 1. Instance Detail Mode and Back Navigation Contract

Story 6.5는 Application Dashboard의 instance handoff에서 같은 `dashboard-detail` 영역을 Instance Detail mode로 전환한다.

결정 내용:

- Application Dashboard `instances[]` item의 Evidence action을 활성화한다.
- Evidence action은 같은 static dashboard runtime 안에서 `dashboard-detail` 영역을 Instance Detail mode로 바꾼다.
- Instance Detail 상단에는 selected instance identity와 Application Dashboard로 돌아가는 action을 항상 노출한다.
- 돌아가기는 기존 `loadedDashboard` read model을 다시 렌더링한다.
- Instance Detail 진입 중 Project/Application 재선택, token clear, dashboard reload가 발생하면 instance detail state는 폐기한다.

금지:

- 별도 browser route, URL query/fragment state, SPA routing 도입
- modal로 Instance Evidence 표시
- Application Dashboard 안에서 여러 instance evidence를 inline expansion으로 동시에 펼치기
- Dashboard와 독립된 네 번째 상시 panel을 추가해 첫 화면 구조를 확장하기
- browser back/forward navigation에 Instance Detail state를 의존시키기

결정 이유:

- Epic 6의 흐름은 `project -> application -> dashboard -> instance evidence` drill-down이다.
- 같은 `dashboard-detail` 영역의 mode 전환은 6.4 static runtime과 가장 잘 맞고, frontend routing/auth architecture를 새로 열지 않는다.
- Instance Detail은 application 판단을 대체하지 않는 보조 evidence 화면이므로, Dashboard로 돌아가는 흐름을 항상 보여줘야 한다.

### 2. Evidence Fetch and Link Validation Contract

Story 6.5는 server-provided `links.evidence`를 Instance Evidence API의 유일한 fetch source로 사용한다.

결정 내용:

- Dashboard `instances[]` item은 `instanceId`, `instanceName`, `links.evidence`를 data attribute로 보존한다.
- Evidence action은 `links.evidence`가 내부 `/api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/evidence` shape인지 검증한다.
- 검증에는 현재 selected project id, selected application id, clicked instance id가 모두 포함된다.
- 검증된 evidence link만 기존 in-memory access token으로 `Authorization: Bearer <access_token>` header를 붙여 `fetch`한다.
- Token이 없거나 `401`이 반환되면 safe auth-required instance detail state를 표시한다.
- `404`는 project/application/instance scope mismatch 또는 missing scope로 표현하고 instance health를 단정하지 않는다.
- Response `application.projectId`, `application.applicationId`, `instance.instanceId`가 현재 selected context와 다르면 fail-closed 처리한다.
- Overlapping request, token clear, Project/Application/Instance 재선택 상황에서는 stale evidence response가 현재 화면을 덮어쓰지 않게 한다.

금지:

- UI에서 evidence path를 `projectId/applicationId/instanceId`로 재구성
- `instanceName`만으로 evidence 조회
- `<a href="/api/projects/.../evidence">` 직접 이동
- 브라우저가 API JSON response를 직접 여는 UX
- `window.location` 기반 instance routing
- URL fragment/query token parsing
- localStorage/sessionStorage/cookie token persistence

결정 이유:

- Story 6.4 Dashboard fetch는 server-provided link와 authenticated fetch를 source로 닫았다.
- Instance Evidence도 같은 pattern을 따르면 static UI가 API routing knowledge를 새로 소유하지 않는다.
- `application_instances.id` UUID path와 membership 검증은 Story 5.6에서 이미 닫힌 backend contract다.

### 3. Instance Detail Reading Order Contract

Instance Detail은 API field 순서가 아니라 운영자가 source 축을 먼저 확인한 뒤 bounded evidence를 읽는 순서로 렌더링한다.

결정 내용:

- 최상단은 instance identity, generatedAt, Application Dashboard back action이다.
- 다음은 `metricData`와 `starterConnection`을 별도 axis로 나란히 또는 연속 section으로 표시한다.
- 그 다음 순서는 아래로 고정한다.
  1. `applicationTriageContribution`
  2. `starterPercentiles` current 15분 series
  3. `histogramDistribution`
  4. `resourceHints`
  5. `endpointEvidence`
  6. `links.snapshotTrend` pending handoff
- `application` block의 dashboard link는 back action의 source로만 사용한다.
- Empty/missing/insufficient block은 section 자체를 숨기지 않고 source absence 또는 evidence 부족으로 표현한다.

금지:

- API top-level field를 무조건 그대로 나열하는 raw response view
- endpoint evidence를 최상단에 올려 root cause 화면처럼 보이게 하는 layout
- `links` block을 raw API link 목록처럼 노출
- missing evidence section을 제거해 화면이 정상처럼 보이게 하기

결정 이유:

- Instance Detail은 "이 instance가 무엇인가"와 "metric data와 heartbeat source가 각각 있는가"를 먼저 확인해야 한다.
- Source axis를 먼저 분리하면 뒤의 triage/percentile/endpoint evidence를 instance health 판단으로 오해할 가능성이 줄어든다.

### 4. Metric Data and Starter Connection Copy Contract

Instance Detail은 accepted bucket metric data axis와 starter heartbeat connection axis를 분리해 표시한다.

결정 내용:

- `metricData.statusSource=accepted_bucket`을 화면과 test guard에서 보존한다.
- `starterConnection.statusSource=starter_heartbeat`와 `stateImpact=none`을 화면과 test guard에서 보존한다.
- Recent heartbeat와 missing/stale accepted bucket 조합은 "starter는 연결됐지만 metric data는 대기/idle" 계열로 표현한다.
- Missing/stale heartbeat와 missing/stale accepted bucket 조합은 telemetry unreachable 또는 unknown 계열로 표현하되 host application down을 확정하지 않는다.
- `applicationTriageContribution.contributed=false`는 "기여하지 않음" 또는 "연결할 evidence 없음"이지 "문제 없음"이 아니다.
- `resourceHints`는 CPU/heap/datasource ratio hint로만 표시하고 state나 score로 합성하지 않는다.

금지:

- `instanceHealth`, `hostStatus`, `processDown`, `applicationDown`, `connectedAndHealthy` 같은 합성 copy나 helper 생성
- heartbeat success를 metric freshness, lifecycle state, recovery source, p95/p99, endpoint evidence source로 사용
- heartbeat missing을 host application down 확정으로 표현
- `resourceHints`를 degraded/down 판단이나 health score로 표현
- UI/controller가 metric axis와 heartbeat axis를 조합해 instance state를 재계산

결정 이유:

- Epic 4 이후 핵심 계약은 accepted bucket metric axis와 starter heartbeat control-plane axis 분리다.
- Instance Detail은 Dashboard보다 강한 instance state 판단을 만들면 안 된다.

### 5. Percentile and Histogram Evidence Display Contract

Story 6.5는 current 15분 source-scoped percentile series와 histogram distribution evidence를 표시하되 새 latency scalar를 만들지 않는다.

결정 내용:

- `starterPercentiles.window=current_15m`, `bucketDurationSeconds=30`, `maxPointCount=30`을 화면 또는 test guard에서 보존한다.
- Percentile point는 시간 오름차순 current 15분 series로 표시한다.
- 각 point는 `bucketStartUtc`, `bucketEndUtc`, `requestCount`, `p95Ms`, `p99Ms`를 함께 표시한다.
- Point가 없으면 missing/insufficient reason을 source absence/evidence 부족으로 표시한다.
- Compact sparkline 또는 table 형태는 허용하지만, 표시 helper는 API point를 재계산하지 않는다.
- `histogramDistribution`은 selected instance current 15분 duration bucket distribution evidence로 표시한다.
- Histogram bucket은 distribution visualization 또는 bounded evidence로만 사용한다.

금지:

- 여러 percentile point 평균, 최댓값, 병합
- histogram bucket에서 p95/p99 scalar 계산
- baseline percentile series 생성
- 24h/7d/14d trend처럼 보이는 chart 생성
- 누락 bucket 보간 또는 synthetic point 생성
- endpoint p95/p99, latency score, health score 생성

결정 이유:

- Story 5.6은 current 15분 evidence API만 닫았고, 장기 trend는 Story 5.7/6.6의 stored snapshot projection 책임이다.
- p95/p99는 starter canonical point이므로 UI가 다시 계산하면 source/scope contract가 깨진다.

### 6. Endpoint Evidence Meaning and Display Contract

Story 6.5는 `endpointEvidence`를 selected instance와 application concern의 bounded 연결 evidence로 표시한다.

결정 내용:

- `endpointEvidence.source=accepted_metric_buckets.endpoints_json`과 `scope=instance_current_15m`을 화면 또는 test guard에서 보존한다.
- `endpointEvidence.items`는 최대 5개로 표시한다.
- `selectionPolicy`와 `displayOrderingPolicy`는 server-provided contract value로 표시하거나 test guard에서 보존한다.
- Endpoint section label은 "Instance endpoint evidence" 또는 "Application concern 연결 evidence" 계열로 둔다.
- `relatedApplicationPriorityRank`는 application dashboard endpoint priority 참조 값으로만 표현한다.
- `localDisplayOrder`는 Instance Detail 안의 표시 순서이며 endpoint priority가 아니라고 취급한다.
- `presenceOnSelectedInstance=observed`는 selected instance current window에서 관찰됨으로 표현한다.
- `presenceOnSelectedInstance=not_observed`는 selected instance current window에서 해당 endpoint evidence가 관찰되지 않음으로 표현한다.
- `presenceOnSelectedInstance=insufficient`는 endpoint evidence를 신뢰할 수 없는 상태로 표현한다.
- `application_freshness_not_current`이면 stale/down 직전 endpoint evidence를 current concern처럼 보이지 않게 suppressed state로 표시한다.

금지:

- instance-level endpoint priority ranking 생성
- `localDisplayOrder`를 root cause 순위, endpoint priority, action priority로 표현
- load balancer misconfiguration, root cause, instance fault를 확정하는 copy
- `not_observed`를 "문제 없음" 또는 "정상"으로 표현
- `rank`, `confidence`, `score`, `recommendedAction`을 UI에서 새로 계산
- raw `endpoints_json`, raw path, query string, query key/value, trace id, per-request sample 표시
- endpoint p95/p99, endpoint percentile rollup, endpoint p99 alert 기준 생성

결정 이유:

- Story 5.6의 endpoint evidence는 Application Dashboard concern이 selected instance에서도 관찰됐는지 확인하는 bounded subset이다.
- 새 ranking이나 root cause copy를 만들면 Instance Detail이 application 판단을 대체하게 된다.

### 7. Snapshot Trend and History Handoff Contract

Story 6.5는 snapshot trend/history로 이어지는 handoff만 보존하고 API를 호출하지 않는다.

결정 내용:

- `links.snapshotTrend`가 있으면 Instance Detail 하단에 pending handoff action 또는 data attribute로 보존할 수 있다.
- Pending handoff copy는 "후속 trend 화면에서 사용될 link" 정도의 source-aware 표현으로 둔다.
- Story 6.5는 trend/history가 준비되지 않은 경우에도 current Instance Evidence 화면이 완결되게 표시한다.

금지:

- Instance Snapshot Trend API fetch/render
- Snapshot Detail API fetch/render
- Operational Event History API fetch/render
- 24h/7d/14d selector 또는 trend chart 생성
- raw instance timeseries, raw bucket explorer, endpoint timeseries UI 생성
- `links.snapshotTrend`를 현재 instance state 판단 source로 사용

결정 이유:

- Story 6.6은 Instance Snapshot Trend UI, Story 6.7은 Snapshot/History marker UI 책임이다.
- 6.5에서 trend/history를 미리 열면 current 15분 evidence와 stored snapshot projection 경계가 흐려진다.

### 8. Safe State and Error Copy Contract

Instance Evidence UI는 실패와 부족한 source를 안전한 상태로 표현한다.

결정 내용:

- Instance Detail view state 후보는 `idle`, `loading`, `auth-required`, `invalid-link`, `not-found`, `error`, `ready`로 둔다.
- `idle`은 Dashboard instance handoff를 선택하기 전 상태다.
- `loading`은 selected instance identity를 유지하되 evidence를 불러오는 중으로 표시한다.
- `auth-required`는 GitHub login/service token이 필요하다는 copy만 표시하고 token 값을 노출하지 않는다.
- `invalid-link`는 현재 Project/Application/Instance와 일치하는 내부 evidence link만 사용할 수 있다고 표현한다.
- `not-found`는 project/application/instance scope를 찾을 수 없다고 표현한다.
- `error`는 잠시 후 다시 시도하라는 generic copy로 표현하고 backend detail을 노출하지 않는다.
- `ready`에서도 block별 missing/insufficient/suppressed/unavailable 상태를 source-aware copy로 표시한다.

금지:

- API error body, exception message, provider payload, token, secret을 화면에 표시
- `404`를 instance down, application down, deleted instance 확정으로 표현
- empty endpoint/percentile/histogram evidence를 정상 또는 문제 없음으로 표현
- malformed response를 일부 렌더링하다가 stale 또는 mismatched identity를 보여주기

결정 이유:

- Epic 6 UI는 demo와 운영 첫 화면 모두에서 빈 화면이나 단정 copy를 피해야 한다.
- Instance Detail은 evidence source가 부족할 때도 사용자가 다음 행동을 이해할 수 있어야 한다.

### 9. Static UI Contract Test Guard

Story 6.5 completion은 Instance Evidence static UI contract test를 포함해야 한다.

결정 내용:

- `ApplicationDashboardUiContractTest`를 확장하거나 `InstanceEvidenceUiContractTest`를 새로 추가한다.
- Node VM 또는 구조 검증으로 실제 `app.js`의 instance evidence fetch/render state machine을 검증한다.
- Test는 Dashboard instance action이 `links.evidence` 기반 authenticated fetch로 이어지는지 검증한다.
- Test는 invalid link, no-token, `401`, `404`, generic error, malformed response, stale response guard를 검증한다.
- Test는 response identity mismatch가 fail-closed 되는지 검증한다.
- Test는 metric data axis와 starter connection axis가 분리되어 표시되는지 검증한다.
- Test는 percentile/histogram/endpoint/resource evidence가 표시만 되고 UI-side recomputation helper가 없는지 검증한다.
- Test는 trend/snapshot/history API를 fetch하지 않는지 검증한다.

금지:

- 테스트 없이 static JS 상태 전환을 구현
- fixture HTML string만 보고 실제 click/fetch/request sequence를 검증하지 않기
- broad string assertion만 추가하고 invalid/stale/mismatch guard를 생략
- 기존 Project/Application/Dashboard guard를 약화해 Instance Evidence 구현을 통과시키기

결정 이유:

- Story 6.4의 주요 위험은 static JS helper가 표시 helper를 넘어 계산 helper가 되는 것이었다.
- Story 6.5도 같은 위험이 있으며, 특히 instance health/root cause/endpoint ranking copy가 생기지 않도록 guard가 필요하다.

## BMAD Create-Story Notes

Story 6.5 create-story 산출물은 아래를 반영해야 한다.

- Source of Truth에 이 문서를 최우선 Story 6.5 pre-story contract decision으로 포함한다.
- Acceptance Criteria에 mode 전환/back navigation, evidence link validation, reading order, axis separation, percentile/histogram, endpoint evidence meaning, trend handoff, safe states, static test guard를 각각 명시한다.
- Tasks/Subtasks는 `observability-portal/src/main/resources/static/dashboard/index.html`, `styles.css`, `app.js`와 static UI contract test 중심으로 작성한다.
- Backend API/schema/migration 확장이 필요해 보이면 구현하지 말고 correct-course 또는 별도 contract decision으로 올린다.
- 새 public class/method/helper/test에는 `AGENTS.md` 기준으로 한국어 Javadoc/comment를 작성한다.

## Verification Expectations

Story 6.5 completion 전 최소 아래 검증을 수행한다.

```bash
./gradlew :observability-portal:test --tests '*InstanceEvidenceUiContract*'
./gradlew :observability-portal:test --tests '*ApplicationDashboardUiContract*'
./gradlew :observability-portal:test --tests '*InstanceEvidenceController*'
./gradlew :observability-portal:test --tests '*InstanceEvidenceReadModelShape*'
./gradlew :observability-portal:test --tests '*InstanceEvidenceReadModelService*'
./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest
./gradlew :observability-portal:test
git diff --check
```
