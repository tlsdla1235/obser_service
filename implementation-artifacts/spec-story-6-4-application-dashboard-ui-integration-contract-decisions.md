---
artifactType: contract-decisions
projectName: Spring Boot 운영 첫 화면 포털
storyId: "6.4"
storyKey: "6-4-application-dashboard-ui-integration"
status: closed
date: 2026-05-28
scope: Story 6.4 pre-create-story contract closure before BMAD create-story execution
---

# Story 6.4 Application Dashboard UI Integration Contract Decisions

## Purpose

Story 6.4는 Epic 5에서 닫은 Application Dashboard current read model을 Epic 6 static dashboard UI 안에서 primary first-screen으로 렌더링하는 경계를 닫는다.

이 문서는 Story 6.4 create-story 실행 전에 사용자가 직접 선택했거나 Codex가 프로젝트 계약 기준으로 권장해 닫은 계약을 기록한다. 목표는 구현자가 dashboard 진입 방식, 화면 범위, metric state와 starter connection 의미, zero insight/recovery copy, percentile/histogram 표시, endpoint priority 의미, static UI test guard를 다시 열지 않게 하는 것이다.

Story 6.4는 Application Dashboard current API를 표시하는 UI integration story다. Instance Evidence, Instance Snapshot Trend, Snapshot/History marker/detail, demo fixture/hardening, browser token persistence, SPA routing, frontend build stack은 만들지 않는다.

## Authority and Non-Reopen Rules

이 문서는 아래 계약과 산출물을 기준으로 한다.

- `implementation-artifacts/sprint-status.yaml`
- `implementation-artifacts/epic-6-context.md`
- `implementation-artifacts/epic-5-retro-2026-05-27.md`
- `planning-artifacts/stories/6-3-application-list-ui.md`
- `implementation-artifacts/spec-story-6-2-project-selection-ui-contract-decisions.md`
- `implementation-artifacts/spec-story-6-1-account-project-entry-and-setup-guide-contract-decisions.md`
- `planning-artifacts/epics.md`
- `planning-artifacts/sprint-plan.md`
- `planning-artifacts/current-product-source-of-truth.md`
- `planning-artifacts/api-surface.md`
- `planning-artifacts/project-structure.md`
- `planning-artifacts/contracts/account-auth-policy.md`
- `planning-artifacts/contracts/read-model-contract.md`
- `planning-artifacts/contracts/dashboard-read-model.md`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/controller/DashboardController.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/model/ApplicationDashboardReadModel.java`
- `observability-portal/src/main/resources/static/dashboard/`
- `AGENTS.md`
- `_bmad/custom/project-context.md`

Story 6.4 create-story/dev-story는 아래 결정을 다시 열지 않는다.

- Dashboard 진입은 Application List item의 `data-dashboard-link` 또는 API item의 `links.dashboard`를 source로 삼는다.
- UI는 dashboard path를 재구성하지 않고, 안전한 내부 `/api/projects/{projectId}/applications/{applicationId}/dashboard` link인지 검증한 뒤 기존 in-memory Bearer token으로 authenticated `fetch`한다.
- Direct API URL navigation, browser URL token parsing, token persistence, 새 client-side route는 Story 6.4 범위에 포함하지 않는다.
- Story 6.4는 `GET /api/projects/{projectId}/applications/{applicationId}/dashboard` current response를 렌더링한다.
- Story 6.4는 Instance Evidence API, Instance Snapshot Trend API, Snapshot Detail API, Operational Event History API를 fetch/render하지 않는다.
- Metric data-plane state strip과 starter connection strip은 시각, 문구, DOM/test guard에서 분리한다.
- UI는 lifecycle state, starter connection diagnosis, zeroInsight, recovery, rule, p95/p99, histogram percentile, endpoint priority, instance health, snapshot/history event를 재계산하지 않는다.
- `triageCards=[]`이면 server-provided `zeroInsight`를 빈 화면 대신 표시한다.
- `recovery.isRecovering=true`는 "복구 완료"가 아니라 "회복 관찰 중"으로 표현한다.
- p95/p99는 `sourceScopedPercentiles.items[]`의 source/instance/bucket scope와 함께 표시한다. Histogram은 distribution evidence로만 표시한다.
- Endpoint priority는 root cause 확정 순위가 아니라 server-computed next-check surface로 표현한다.
- Story 6.4 completion은 static UI contract test를 포함해 검증한다.

## Closed Decisions

### 1. Dashboard Entry Contract

Story 6.4 Dashboard 진입은 `data-dashboard-link` 기반 authenticated fetch로 닫는다.

결정 내용:

- Application List item의 Dashboard action은 6.3이 보존한 `data-dashboard-link`를 사용한다.
- API response item의 `links.dashboard`는 `/api/projects/{projectId}/applications/{applicationId}/dashboard` shape여야 한다.
- UI는 project id와 application id가 현재 선택된 Application item과 일치하는지 검증한다.
- 검증된 dashboard link만 기존 in-memory access token으로 `Authorization: Bearer <access_token>` header를 붙여 `fetch`한다.
- Token이 없거나 `401`이 반환되면 safe auth-required dashboard state를 표시한다.
- `404`는 project/application mismatch 또는 missing scope로 표현하고 application health를 단정하지 않는다.
- Overlapping request, token clear, Project/Application 재선택 상황에서는 stale dashboard response가 현재 화면을 덮어쓰지 않게 한다.

금지:

- `<a href="/api/projects/.../dashboard">` 직접 이동
- 브라우저가 API JSON response를 직접 여는 UX
- `window.location` 기반 dashboard routing
- URL fragment/query token parsing
- localStorage/sessionStorage/cookie token persistence
- first application auto-select 또는 dashboard path 재구성

결정 이유:

- Story 6.1~6.3은 static asset, in-memory Bearer token, no token persistence 계약을 유지했다.
- Story 6.4는 frontend routing/auth architecture를 여는 story가 아니라 Application Dashboard read model을 표시하는 story다.
- Direct API navigation은 인증 header를 붙이기 어렵고 API JSON 페이지로 사용자를 떨어뜨릴 수 있다.

### 2. Dashboard Surface Scope Contract

Story 6.4는 Application Dashboard current read model 화면만 구현한다.

결정 내용:

- `GET /api/projects/{projectId}/applications/{applicationId}/dashboard` response를 화면의 canonical source로 사용한다.
- Dashboard 화면은 application identity, source window/freshness, metric state, starter connection, metrics, source-scoped percentile, histogram distribution, zero insight/recovery, triage cards, endpoint priority, bounded instance summary를 표시할 수 있다.
- `instances[]`는 Application Dashboard 안의 bounded handoff surface로만 렌더링한다.
- `instances[].links.evidence`는 Story 6.5 Instance Evidence UI 진입을 위한 data attribute 또는 disabled/pending action으로 보존할 수 있다.
- `snapshot` 또는 snapshot link가 있으면 Story 6.7 Snapshot/History UI를 위한 handoff data로만 보존한다.
- Application List로 돌아가는 흐름은 유지한다.

금지:

- Instance Evidence API fetch/render
- Instance Snapshot Trend API fetch/render
- Snapshot Detail API fetch/render
- Operational Event History API fetch/render
- demo fixture, seed, green path/failure path hardening
- dashboard response shape를 확장하기 위한 backend API/schema/migration 변경
- Application List에서 dashboard 판단을 미리 보여주는 shortcut

결정 이유:

- Story 6.4의 목적은 Application Dashboard를 primary first-screen으로 닫는 것이다.
- Instance Evidence는 Story 6.5, Trend는 Story 6.6, Snapshot/History는 Story 6.7의 책임이다.
- Dashboard response가 큰 read model이므로 이번 story의 책임과 후속 handoff를 분리하지 않으면 scope가 쉽게 커진다.

### 3. Metric State Strip and Starter Connection Strip Contract

Metric data-plane state와 starter heartbeat control-plane summary는 분리해 표시한다.

결정 내용:

- Metric state strip은 `state`, `application.freshness`, `application.lastAcceptedBucketAt`, `application.sourceWindow`, `metrics`를 중심으로 렌더링한다.
- Starter connection strip은 `starterConnection.statusSource`, `lastHeartbeatAt`, `lastHeartbeatStatus`, `connectionMeaning`, `stateImpact`를 중심으로 별도 렌더링한다.
- `starterConnection.statusSource=starter_heartbeat`와 `stateImpact=none`은 화면과 contract test에서 유지한다.
- 최근 heartbeat와 없음/오래된 accepted bucket 조합은 no recent traffic, waiting first data, metric data idle 계열로 표현한다.
- heartbeat도 없고 accepted bucket도 오래된 조합은 telemetry unreachable/unknown 계열로 표현하되 host application down을 확정하지 않는다.

금지:

- `health`, `hostHealth`, `applicationHealth` 같은 합성 field/copy 생성
- heartbeat success를 app health success로 표현
- heartbeat missing을 host application down으로 표현
- accepted bucket freshness를 starter heartbeat freshness로 대체
- starter connection warning으로 metric state를 직접 변경

결정 이유:

- Epic 4~5에서 accepted bucket axis와 starter heartbeat axis 분리는 핵심 계약으로 반복 검증됐다.
- Story 6.4는 사용자가 처음 운영 판단을 보는 화면이므로 두 축을 더 명확히 분리해야 한다.

### 4. Zero Insight and Recovery Copy Contract

Triage card가 없을 때도 dashboard는 빈 화면처럼 보이지 않아야 한다.

결정 내용:

- `triageCards=[]`이면 `zeroInsight`는 반드시 표시한다.
- UI는 `zeroInsight.reasonCode`, `message`, `recommendedAction`을 server-provided copy로 렌더링한다.
- 허용 reason code는 `read-model-contract.md` 기준을 따른다.
  - `no_action_needed`
  - `insufficient_sample`
  - `waiting_first_data`
  - `metric_data_idle`
  - `telemetry_unreachable`
  - `observing_recovery`
- `recovery.isRecovering=true`이면 recovery section 또는 state support copy를 표시하고, `retryAfterSeconds`는 자동 예약이 아니라 다음 판단 대기 안내로 표현한다.
- `lastHealthyAt`이 없으면 "이전 정상 시점 없음" 계열의 source absence로 표현한다.

금지:

- `triageCards=[]`를 "정상", "문제 없음", "복구 완료"로 단순 번역
- `observing_recovery`를 recovery complete로 표현
- `telemetry_unreachable`을 host application down으로 확정
- zeroInsight reason을 UI에서 새로 계산하거나 alias reason code를 추가
- triage 없음 상태에서 dashboard 본문을 비워두기

결정 이유:

- Epic 6 demo는 first accepted bucket, insufficient sample, no-triage baseline, recovery observation을 빈 화면 없이 설명해야 한다.
- "조치 없음"과 "판단할 source가 부족함"은 사용자 행동이 다르므로 copy에서 분리해야 한다.

### 5. Percentile and Histogram Display Contract

p95/p99와 histogram은 같은 latency surface에 보일 수 있지만 의미는 섞지 않는다.

결정 내용:

- `metrics`는 request/error scalar만 headline으로 사용한다.
- p95/p99는 `sourceScopedPercentiles.items[]`의 `source`, `instance`, `bucketStartUtc`, `bucketEndUtc`, `requestCount`, `p95Ms`, `p99Ms`를 함께 표시한다.
- `sourceScopedPercentiles.status`가 `missing` 또는 `insufficient`이면 reason을 source absence/evidence 부족으로 표시한다.
- `sourceScopedPercentiles.aggregatePolicy`는 화면 또는 test guard에서 보존한다.
- `histogramDistribution`은 current/baseline window의 bucket distribution evidence로 표시한다.
- Histogram bucket은 distribution visualization 또는 bounded evidence로만 사용한다.

금지:

- top-level `metrics.p95Ms` 또는 `metrics.p99Ms` 생성
- 여러 instance percentile 평균, 최댓값, 병합
- histogram bucket에서 p95/p99 scalar 계산
- endpoint p95/p99 표시
- p95/p99와 histogram-derived value를 같은 canonical latency number처럼 병렬 표시
- `avgMs`, `maxMs`, `latencyScore`, `healthScore` 같은 UI 계산 field 생성

결정 이유:

- Epic 5는 source-scoped starter canonical percentile과 histogram distribution evidence를 분리해 닫았다.
- 보기 좋은 단일 숫자를 만들면 source와 scope가 지워지고 UI가 server read model 계약을 깨게 된다.

### 6. Triage and Endpoint Priority Meaning Contract

Triage card와 endpoint priority는 server-computed next action surface로만 표시한다.

결정 내용:

- `triageCards[]`는 server가 준 `ruleId`, `severity`, `title`, `summary`, `recommendation`, `confidence`, `score`, `affectedEndpoint`, bounded `evidence`를 그대로 렌더링한다.
- `endpointPriority[]`는 server가 준 `rank`, `method`, `route`, `endpointKey`, `reason`, `ruleIds`, `confidence`, `score`, `freshness`, bounded `evidence`, `recommendedAction`을 그대로 렌더링한다.
- Endpoint priority label은 "먼저 확인할 endpoint" 또는 "Next check" 계열 의미로 둔다.
- Endpoint priority가 비어 있으면 current freshness/source 조건 때문에 표시할 endpoint priority가 없다는 source-aware empty state로 표현한다.
- Confidence와 score는 server-computed evidence confidence로 표시할 수 있지만 UI가 severity를 새로 산출하지 않는다.

금지:

- endpoint priority를 root cause 확정 순위로 표현
- endpoint priority를 장애 순위, alert priority, endpoint health score로 표현
- endpoint item 재정렬, client-side ranking, score recomputation
- raw path, query string, query key/value, trace id, per-request sample 표시
- endpoint p95/p99 계산 또는 표시
- endpoint evidence를 raw explorer처럼 펼치는 UI

결정 이유:

- Endpoint priority는 "원인 확정"이 아니라 사용자가 먼저 확인할 bounded evidence surface다.
- Story 6.4가 endpoint evidence를 과하게 해석하면 후속 Instance Evidence와 Snapshot/History의 경계도 흐려진다.

### 7. Static UI Contract Test Guard

Story 6.4 completion은 Application Dashboard static UI contract test를 포함해야 한다.

결정 내용:

- `ApplicationDashboardUiContractTest`를 새로 추가하거나 동등한 이름의 static UI contract test를 추가한다.
- Node VM 또는 구조 검증으로 실제 `app.js`의 dashboard fetch/render state machine을 검증한다.
- 6.3의 `ApplicationListUiContractTest`는 Dashboard handoff가 6.4에서 활성화되는 범위에 맞게 좁혀 갱신한다.
- Project Selection과 Application List의 no-shortcut, no-auto-select, safe link validation guard는 유지한다.

검증 기대:

- Dashboard action은 `data-dashboard-link` 기반 authenticated fetch를 수행한다.
- Dashboard fetch는 `Authorization: Bearer <access_token>` header를 사용한다.
- invalid dashboard link는 fail-closed 되고 API call을 만들지 않는다.
- no token, `401`, `404`, generic error, loading, ready state가 safe copy로 렌더링된다.
- overlapping request, token clear, Application 재선택 상황에서 stale dashboard response가 현재 화면을 덮어쓰지 않는다.
- Dashboard UI가 current `ApplicationDashboardReadModel` field만 소비한다.
- metric state strip과 starter connection strip이 별도 axis로 렌더링된다.
- `zeroInsight`와 `recovery` copy가 "정상", "문제 없음", "복구 완료", host down 확정으로 번역되지 않는다.
- `sourceScopedPercentiles`와 `histogramDistribution`은 표시만 하고 p95/p99를 재계산하지 않는다.
- endpoint priority를 재정렬하거나 root cause 확정 순위로 표현하지 않는다.
- instance evidence, trend, snapshot detail, history API를 fetch하지 않는다.
- no token persistence, no URL token parsing, no frontend stack, no UI-side recomputation guard를 유지한다.

금지:

- 수동 브라우저 smoke만으로 completion 처리
- 기존 Application List test의 broad disabled dashboard assertion을 삭제만 하고 새 dashboard guard를 만들지 않는 것
- dashboard UI를 구현하면서 Project Selection/Application List의 no shortcut guard를 약화하는 것

결정 이유:

- Story 6.4의 주요 위험은 backend API보다 static JS가 표시 helper를 계산 helper로 바꾸는 데 있다.
- Epic 5의 negative guard 학습을 Epic 6 UI에도 유지해야 한다.

## Open Decisions That Remain

아래 항목은 Story 6.4에서 구현자가 임의로 닫지 않는다.

- Browser token persistence, refresh token browser storage, logout/session persistence
- React, Vite, TypeScript, SPA routing 전환 시점과 source/build output 구조
- Instance Evidence UI layout과 evidence drill-down interaction
- Instance Snapshot Trend를 Epic 6 MVP에 포함할지 demo-only로 둘지의 제품 판단
- Snapshot/History marker UI를 Epic 6 MVP에 포함할지 demo-only로 둘지의 제품 판단
- Alert/Discord surface의 MVP 포함 여부
- Demo green path/failure-recovery fixture와 seed strategy
- Project creation, ownership, role model, project key 발급/회전/재발급 workflow

## BMAD Create-Story Notes

Story 6.4 create-story 산출물은 아래를 반영해야 한다.

- Source of Truth에 이 문서를 최우선 Story 6.4 pre-story contract decision으로 포함한다.
- Acceptance Criteria에 dashboard entry, scope, axis separation, zeroInsight/recovery, percentile/histogram, endpoint priority, static test guard를 각각 명시한다.
- Tasks/Subtasks는 `observability-portal/src/main/resources/static/dashboard/index.html`, `styles.css`, `app.js` 중심으로 작성한다.
- Backend API/schema/read model 확장이 필요해 보이면 구현하지 말고 correct-course 또는 별도 contract decision으로 올린다.
- 새 public class/method/helper/test에는 `AGENTS.md` 기준으로 한국어 Javadoc/comment를 작성한다.

## Verification Expectations

Story 6.4 completion 전 최소 아래 검증을 수행한다.

```bash
./gradlew :observability-portal:test --tests '*ApplicationDashboardUiContract*'
./gradlew :observability-portal:test --tests '*ApplicationListUiContract*'
./gradlew :observability-portal:test --tests '*ProjectSelectionUiContract*'
./gradlew :observability-portal:test --tests '*ProjectEntryUiContract*'
./gradlew :observability-portal:test --tests '*DashboardController*'
./gradlew :observability-portal:test --tests '*DashboardReadModelService*'
./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest
./gradlew :observability-portal:test
git diff --check
```
