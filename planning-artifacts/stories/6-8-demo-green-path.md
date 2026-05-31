---
artifactType: story
storyId: "6.8"
storyKey: "6-8-demo-green-path"
epic: "Epic 6. Dashboard User Flow and Demo Hardening"
title: "Demo green path"
architectureStyle: Traditional MVC
status: done
date: 2026-05-31
---

# Story 6.8 - Demo green path

## Status

done

## Story

portal 사용자는 starter를 추가한 뒤 30~60초 안에 Project Selection, Application List, Application Dashboard, Instance Evidence로 끊김 없이 진입하며 heartbeat 수신, 첫 accepted bucket, insufficient sample, active/no-triage baseline을 빈 화면 없이 확인하고 싶다.

그래야 demo가 "starter 연결은 보이지만 metric 판단 source는 아직 없다"는 대기 상태부터 "accepted bucket이 들어왔지만 sample guard가 아직 부족하다"는 상태, 그리고 "freshness/sample은 충분하지만 우선 노출할 triage가 없다"는 green path baseline까지 자연스럽게 보여주고, failure/recovery hardening이나 project creation/admin 범위를 열지 않고 Epic 6의 사용자 흐름을 닫을 수 있다.

## Source of Truth

아래 문서를 읽고 반영한 BMAD create-story context다. 구현 중 충돌처럼 보이는 지점은 Story 6.10 account-project membership 계약, `account-auth-policy.md`, 최신 Epic 5/6 source-of-truth, 그리고 현재 static dashboard/API shape를 우선한다.

1. `_bmad/custom/project-context.md`
2. `implementation-artifacts/sprint-status.yaml`
3. `planning-artifacts/epics.md`
4. `planning-artifacts/sprint-plan.md`
5. `implementation-artifacts/epic-6-context.md`
6. `planning-artifacts/current-product-source-of-truth.md`
7. `planning-artifacts/api-surface.md`
8. `planning-artifacts/database-schema.md`
9. `planning-artifacts/contracts/account-auth-policy.md`
10. `planning-artifacts/stories/6-1-account-project-entry-and-setup-guide.md`
11. `planning-artifacts/stories/6-2-project-selection-ui.md`
12. `planning-artifacts/stories/6-3-application-list-ui.md`
13. `planning-artifacts/stories/6-4-application-dashboard-ui-integration.md`
14. `planning-artifacts/stories/6-5-instance-evidence-ui.md`
15. `planning-artifacts/stories/6-6-instance-snapshot-trend-ui.md`
16. `planning-artifacts/stories/6-7-snapshot-history-marker-ui-and-deep-link.md`
17. `planning-artifacts/stories/6-10-account-project-membership-and-scoped-project-entry.md`
18. `implementation-artifacts/spec-story-6-10-account-project-membership-and-scoped-project-entry-contract-decisions.md`

보조 참고:

- `implementation-artifacts/observation-context-pr-explanation.html`
- `implementation-artifacts/portal-dbml-snapshot-2026-05-29.dbml`
- `implementation-artifacts/portal-dbml-snapshot-2026-05-31.dbml`

DBML snapshot은 schema 흐름 확인용으로만 참고한다. Schema source of truth는 Flyway migration과 `planning-artifacts/database-schema.md`다.

확인한 현재 코드/테스트:

1. `observability-portal/src/main/resources/static/dashboard/index.html`
2. `observability-portal/src/main/resources/static/dashboard/styles.css`
3. `observability-portal/src/main/resources/static/dashboard/app.js`
4. `observability-portal/src/main/java/com/observation/portal/domain/account/controller/BearerResourceApiInterceptor.java`
5. `observability-portal/src/main/java/com/observation/portal/domain/account/controller/AccountProjectMembershipResourceApiInterceptor.java`
6. `observability-portal/src/main/java/com/observation/portal/domain/account/service/AccountProjectMembershipService.java`
7. `observability-portal/src/main/java/com/observation/portal/domain/catalog/controller/ProjectNavigationController.java`
8. `observability-portal/src/main/java/com/observation/portal/domain/catalog/service/ProjectApplicationNavigationService.java`
9. `observability-portal/src/main/java/com/observation/portal/domain/dashboard/controller/DashboardController.java`
10. `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/DashboardReadModelService.java`
11. `observability-portal/src/main/java/com/observation/portal/domain/instance/controller/InstanceEvidenceController.java`
12. `observability-portal/src/main/java/com/observation/portal/domain/instance/service/InstanceEvidenceReadModelService.java`
13. `observability-portal/src/main/resources/db/migration/V011__create_account_project_memberships.sql`
14. `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ApplicationDashboardUiContractTest.java`
15. `observability-portal/src/test/java/com/observation/portal/domain/dashboard/InstanceEvidenceUiContractTest.java`
16. `observability-portal/src/test/java/com/observation/portal/domain/catalog/controller/ProjectNavigationResourceAuthorizationTest.java`
17. `observability-portal/src/test/java/com/observation/portal/domain/account/repository/AccountProjectMembershipRepositoryIntegrationTest.java`

## Scope / Out of Scope

포함:

- 30~60초 demo path 기준의 fixture/seed/test-data 정렬
- GitHub OAuth 후 받은 service access token을 `Authorization: Bearer <access_token>` header로 쓰는 기존 resource API flow 유지
- `GET /api/projects`가 active account-project membership project만 반환하는 Story 6.10 이후 조건을 green path fixture에 반영
- local/demo/test fixture가 `accounts`, `projects`, `account_project_memberships`, `applications`, `application_instances`, `starter_heartbeat_telemetry`, `accepted_metric_buckets`, 필요한 최소 `dashboard_snapshots`를 서로 일관되게 연결하도록 정리
- Project Selection -> Application List -> Application Dashboard -> Instance Evidence 경로가 같은 static dashboard runtime 안에서 끊기지 않는지 검증
- 첫 화면의 `waiting_first_data`, `insufficient_sample`, `no_action_needed` 상태가 빈 화면처럼 보이지 않도록 safe copy와 fixture를 정렬
- starter heartbeat/control-plane axis와 accepted bucket metric data axis를 분리해 표시하는 UI/contract guard
- Application Dashboard와 Instance Evidence가 server read model field를 표시만 하고 lifecycle/state/rule/p95/p99/endpoint priority/operational event를 재계산하지 않도록 regression guard
- demo smoke 또는 focused integration/contract test에서 "heartbeat -> first accepted bucket -> insufficient sample -> active/no-triage baseline"을 검증
- 기존 static dashboard asset, MVC controller/service/repository layering, Spring Data JPA/Flyway 기준 유지

제외:

- production/test code 구현 자체는 이 create-story 작업 범위 밖이다.
- public project creation API, public `POST /api/projects`
- 로그인 직후 자동 project 생성
- Create Project UI/flow
- project key issuance/rotation/reissue product workflow
- invite/team/org/admin/billing/tenant model
- GitHub 외 provider, email/password, local password, magic link, anonymous user flow
- GitHub OAuth provider token을 resource API authorization에 사용하는 흐름
- browser token persistence, `localStorage`, `sessionStorage`, cookie token 저장
- URL fragment/query token parsing, direct API URL navigation, SPA routing
- React, Vite, TypeScript, `package.json`, `src/main/frontend`, 별도 frontend build/deploy, view resolver/template engine
- lifecycle state, starter connection diagnosis, zeroInsight, recovery, rule, p95/p99, endpoint priority, operational event, marker/event promotion을 UI에서 재계산하는 helper
- endpoint p95/p99, histogram-derived percentile, application/window percentile 평균/최댓값/병합
- raw bucket explorer, raw snapshot JSON explorer, endpoint timeseries, arbitrary metric query UI
- Story 6.9의 failure/recovery hardening: portal down, duplicate ingest, stale/down candidate, telemetry unreachable, recovery observed, degraded/recovery marker polish
- host application down, 정상 확정, 복구 완료를 단정하는 demo copy

## Acceptance Criteria

1. Story 6.8 story file은 `ready-for-dev` 상태로 생성되고 `sprint-status.yaml`의 `6-8-demo-green-path`도 `ready-for-dev`로 갱신된다.
2. 구현 story 실행 시 production 변경은 Traditional MVC + Service/Repository Layering과 feature-first package 구조를 따른다.
3. Static dashboard runtime은 계속 `observability-portal/src/main/resources/static/dashboard/` 아래의 HTML/CSS/JS를 사용한다.
4. Story 6.8은 React/Vite/TypeScript/frontend build stack, template engine, SPA route를 도입하지 않는다.
5. Demo flow는 `Project -> Application List -> Application Dashboard -> Instance Evidence`를 기본 green path로 검증한다.
6. Demo path는 starter 추가 후 heartbeat 수신, 첫 accepted bucket 수용, insufficient sample, active/no-triage baseline, Application Dashboard 진입을 30~60초 안에 보여줄 수 있어야 한다.
7. Demo fixture는 `GET /api/projects`가 비어 있지 않도록 Bearer account와 demo project 사이에 `account_project_memberships` active row를 명시적으로 만든다.
8. Demo fixture는 disabled membership, missing membership, disabled project를 green path 성공 fixture로 사용하지 않는다.
9. Membership row가 없을 때 Project Entry가 `projects=[]` safe empty state를 보여주는 Story 6.10 regression은 유지한다.
10. Demo fixture는 public project creation API, 로그인 직후 자동 project 생성, Create Project UI를 우회로 만들지 않는다.
11. Demo project key가 필요하면 local/internal seed 또는 test fixture 안에서만 다루고 raw project key를 migration, DB row, log, exception, response body, repository lookup surface에 남기지 않는다.
12. Resource API는 GitHub provider token이 아니라 service `Authorization: Bearer <access_token>` 기준으로만 호출된다.
13. Browser code는 access token persistence 위치를 새로 결정하지 않는다. in-memory hook과 Bearer header 구성만 사용한다.
14. Browser code는 `localStorage`, `sessionStorage`, cookie token 저장, URL fragment/query token parsing을 추가하지 않는다.
15. Heartbeat fixture는 `starter_heartbeat_telemetry` 또는 기존 heartbeat API/storage 경계에만 연결되고 accepted bucket, dashboard snapshot, operational event, p95/p99, rule/read-model calculation source처럼 취급되지 않는다.
16. 첫 heartbeat-only state는 `waiting_first_data` 또는 동등 server read model reason으로 표시되고, accepted bucket 없음이 빈 화면처럼 보이지 않는다.
17. Heartbeat-only state copy는 "starter 연결 신호는 있지만 metric 판단 source인 accepted bucket은 아직 없다"는 의미를 유지한다.
18. First accepted bucket state는 `insufficient_sample` zeroInsight 또는 current code의 동등 server-provided reason/action으로 표시된다.
19. Insufficient sample state는 accepted bucket이 수용됐지만 minimum sample guard가 부족한 상태로 표현하고, host application health를 단정하지 않는다.
20. Active/no-triage baseline은 `state.code=active`, `triageCards=[]`, `zeroInsight.reasonCode=no_action_needed` 또는 current code의 동등 server read model로 표시된다.
21. Active/no-triage baseline은 "현재 우선 노출할 triage 없음"으로 표현하고 "앱 정상 확정", "문제 없음", "복구 완료"로 번역하지 않는다.
22. `triageCards=[]`이면 Application Dashboard는 반드시 server-provided `zeroInsight`를 표시한다.
23. Application Dashboard first screen은 data 부족 상태에서도 loading/error/empty card만 남는 빈 화면처럼 보이지 않는다.
24. Project Selection과 Application List는 scope 선택/스캔 화면으로 유지하고 Application Dashboard 판단을 앞당겨 복제하지 않는다.
25. Application List는 accepted bucket metric data axis와 starter heartbeat axis를 별도 field/source로 표시한다.
26. Application Dashboard는 metric state strip과 starter connection strip을 분리한다.
27. Instance Evidence는 `metricData.statusSource=accepted_bucket`, `starterConnection.statusSource=starter_heartbeat`, `stateImpact=none`을 별도 axis로 표시한다.
28. UI는 accepted bucket freshness와 heartbeat recency를 하나의 `health`, `hostHealth`, `applicationHealth`, `connectedAndHealthy`, `hostStatus`로 합치지 않는다.
29. UI는 lifecycle state, starter diagnosis, zeroInsight reason, recovery guidance, rule, p95/p99, endpoint priority, operational event, snapshot/history marker를 재계산하지 않는다.
30. p95/p99는 server read model의 source-scoped starter canonical point만 표시하고 평균/최댓값/병합/히스토그램 재계산을 하지 않는다.
31. Histogram bucket은 distribution evidence로만 표시하고 percentile scalar를 만들지 않는다.
32. Endpoint priority는 server-provided order/rank/reason/evidence를 표시만 하고 root cause 확정 순위나 endpoint health score로 재해석하지 않는다.
33. Demo fixture의 endpoint/resource/sample 값은 green path baseline에 맞게 low-risk/no-triage 상태를 만들되 Story 6.9 failure/recovery fixture를 끌어오지 않는다.
34. Demo green path는 `telemetry_unreachable`, `stale`, `down`, `degraded`, `observing_recovery`, recovery marker, failure marker를 primary success scenario로 사용하지 않는다.
35. Snapshot/history UI가 green path에서 보조로 노출되더라도 raw explorer가 아니라 stored read model history로만 표현된다.
36. Demo fixture가 snapshot row를 만들면 `dashboard_snapshots.read_model_json`은 bounded read model projection만 담고 raw snapshot explorer나 endpoint timeseries source로 쓰지 않는다.
37. Project/Application/Instance/Snapshot path mismatch와 account-project membership mismatch는 기존처럼 `404` fail-closed로 유지된다.
38. Epic 5의 project/application/instance catalog path 정합성과 Story 6.10의 account-project authorization membership을 문서, test name, helper name에서 혼동하지 않는다.
39. Demo verification은 membership fixture 누락 시 Project List가 비어 green path가 시작되지 않는 실패를 명확히 잡아야 한다.
40. Demo verification은 heartbeat-only, insufficient sample, active/no-triage 세 상태의 copy가 host application down/정상 확정/복구 완료를 단정하지 않는지 검증한다.
41. Static UI contract test는 no token persistence, no URL token parsing, no frontend build stack, no UI-side recomputation guard를 계속 검증한다.
42. Backend focused test는 demo fixture 또는 seed가 account-project membership row를 명시적으로 연결하는지 검증한다.
43. Backend focused test는 active membership account가 demo project와 application/dashboard/instance evidence resource API를 모두 통과하는지 검증한다.
44. Backend focused test는 membership 없는 account가 같은 demo project resource API를 호출하면 `404`를 받는지 검증한다.
45. Suggested verification과 `git diff --check`가 통과해야 implementation completion으로 볼 수 있다.
46. 새 public class/method/helper/test 또는 동작을 바로 이해하기 어려운 내부 helper에는 AGENTS.md 지침에 따라 한국어 Javadoc/comment를 작성한다.

## Tasks / Subtasks

- [x] Demo fixture/seed boundary 정렬 (AC: 6~15, 33~39, 42~44)
  - [x] Demo account, project, active `account_project_memberships` row를 명시적으로 연결한다.
  - [x] Demo project와 application/environment/instance catalog row가 첫 accepted bucket source와 일치하도록 정렬한다.
  - [x] Heartbeat-only 단계는 heartbeat telemetry만으로 application/instance catalog를 생성한다고 가정하지 않는다.
  - [x] Accepted bucket fixture는 `accepted_metric_buckets`의 30초 UTC boundary, idempotency key, application/instance FK, local percentile source/scope를 현재 schema와 맞춘다.
  - [x] 필요한 snapshot fixture는 `dashboard_snapshots`의 stored read model projection으로만 만든다.
  - [x] Production migration에서 모든 account에 모든 project를 grant하거나 raw project key를 심지 않는다.
  - [x] Local/demo convenience가 필요하면 local/internal profile, test fixture, 문서화된 seed script 중 하나로 제한한다.

- [x] Green path state sequence 검증 추가 (AC: 16~23, 33~35, 40)
  - [x] heartbeat-only read model이 `waiting_first_data` 또는 동등 safe reason/action을 반환/렌더링하는지 검증한다.
  - [x] first accepted bucket 이후 sample이 부족한 read model이 `insufficient_sample` 또는 동등 safe reason/action을 반환/렌더링하는지 검증한다.
  - [x] 충분한 current window sample과 low-risk evidence에서 `active + triageCards=[] + zeroInsight=no_action_needed`가 표시되는지 검증한다.
  - [x] 각 상태가 blank/empty first screen이 아니라 source-aware guidance를 보여주는지 검증한다.
  - [x] host application down, 정상 확정, 복구 완료 copy가 green path fixture/UI에 들어오지 않도록 guard한다.

- [x] Project -> Application -> Dashboard -> Instance Evidence flow smoke (AC: 5~7, 12~14, 24~28, 37~39, 43~44)
  - [x] `GET /api/projects`는 Bearer account의 active membership project만 반환한다.
  - [x] Project card의 `links.applications`로 authenticated Application List fetch가 이어진다.
  - [x] Application item의 `links.dashboard`로 authenticated Dashboard fetch가 이어진다.
  - [x] Dashboard `instances[].links.evidence`로 authenticated Instance Evidence fetch가 이어진다.
  - [x] 모든 fetch는 in-memory service access token으로 Bearer header를 구성하고 token storage/URL token parsing을 만들지 않는다.
  - [x] membership 없는 account 또는 wrong project path는 `404` fail-closed를 유지한다.

- [x] Static dashboard UI/copy guard 보강 (AC: 21~32, 35, 40~41)
  - [x] `ApplicationDashboardUiContractTest`, `ApplicationListUiContractTest`, `InstanceEvidenceUiContractTest`, `ProjectEntryUiContractTest` 중 적절한 위치에 demo green path assertions를 추가한다.
  - [x] first data waiting, insufficient sample, no-triage baseline copy가 source-aware guidance로 렌더링되는지 검증한다.
  - [x] accepted bucket axis와 starter heartbeat axis가 별도 DOM/copy/source로 남는지 검증한다.
  - [x] UI-side lifecycle/rule/p95/p99/endpoint priority/operational event 계산 helper 부재를 유지한다.
  - [x] endpoint/root cause/health score/정상 확정/복구 완료/host down 단정 copy가 없는지 검증한다.

- [x] Backend regression과 documentation touchpoints 정리 (AC: 2, 10~11, 15, 36~46)
  - [x] `ProjectNavigationResourceAuthorizationTest` 또는 membership-focused test에 demo membership fixture 누락 회귀를 추가한다.
  - [x] `DashboardReadModelServiceTest`, `InstanceEvidenceReadModelServiceTest`, `ProjectApplicationNavigationServiceTest` 중 green path state에 가장 가까운 focused test를 보강한다.
  - [x] 필요하면 local/demo seed 문서 또는 internal fixture 설명을 갱신하되 public project creation/key rotation/invite/admin 범위를 열지 않는다.
  - [x] 새 helper/test에 한국어 Javadoc/comment를 작성한다.
  - [x] full portal test와 `git diff --check`를 실행한다.

## Dev Notes

### Current Code State

- 현재 `sprint-status.yaml` 기준 Epic 6은 `in-progress`, Story 6.1~6.7과 6.10은 `done`, Story 6.8은 create-story 전 `backlog`, Story 6.9는 `backlog`다.
- 현재 브랜치는 `codex/6-8-demo-green-path`다.
- Story 6.10 구현으로 `GET /api/projects`와 `/api/projects/{projectId}/applications/**`는 Bearer account의 active account-project membership scope 안에서만 동작한다.
- `account_project_memberships`는 V011 migration으로 추가됐고, active membership만 project visibility/resource authorization에 사용한다.
- Demo fixture가 project/application/bucket을 만들더라도 account-project membership row가 없으면 Project Entry는 빈 project list를 보여야 한다. 이 상태는 버그가 아니라 Story 6.10 이후 안전한 기본값이다.
- 현재 static dashboard runtime은 `window.observationPortalAuth.setAccessToken(accessToken)` in-memory hook과 `clearAccessToken()`만 제공한다. Browser token persistence는 없다.
- `app.js`는 Project, Application List, Dashboard, Instance Evidence, Instance Snapshot Trend, Snapshot/History, Snapshot Detail state machine을 한 static runtime 안에서 관리한다.
- `ProjectApplicationNavigationService`는 accepted bucket freshness와 starter heartbeat summary를 분리한다. `staleMetricDataAndRecentHeartbeatDoNotDeclareHostApplicationDown` test가 이 경계를 이미 지키고 있다.
- `DashboardController`는 `GET /api/projects/{projectId}/applications/{applicationId}/dashboard`를 제공하고 `DashboardReadModelService`에 위임한다.
- `ApplicationDashboardReadModel`은 `state`, `starterConnection`, `zeroInsight`, `recovery`, `metrics`, `sourceScopedPercentiles`, `histogramDistribution`, `triageCards`, `endpointPriority`, `instances`, `snapshot`을 server read model로 제공한다.
- `InstanceEvidenceReadModel`은 selected instance evidence를 제공하며 Application Dashboard 판단을 대체하지 않는다.
- 보조 DBML 2026-05-31 snapshot은 `account_project_memberships`가 membership authorization source이고 `operational_events`, `endpoint_timeseries`, `raw_snapshots`, `raw_metric_buckets` table이 없다는 점을 확인하는 참고 자료다. 실제 schema 변경 판단은 Flyway migration과 `database-schema.md`를 따른다.

### Demo Green Path Sequence

목표 시퀀스는 아래 네 단계다.

1. Starter 추가 직후 heartbeat 수신: `starterConnection.statusSource=starter_heartbeat`, `connectionMeaning=starter_connected`, `stateImpact=none`; accepted bucket은 아직 없고 Dashboard/List는 `waiting_first_data` 계열 guidance를 보여준다.
2. 첫 accepted bucket 수용: catalog row와 instance row가 첫 accepted bucket 기준으로 생기거나 갱신되고, metric data axis는 current로 들어오지만 request count가 낮아 `insufficient_sample` 계열 guidance를 보여준다.
3. Low-risk traffic 누적: current 15분 window 안에 sample guard를 통과할 만큼 30초 bucket이 쌓이고 error/latency/resource evidence는 low-risk로 유지된다.
4. Active/no-triage baseline: Application Dashboard는 `state.code=active`, `triageCards=[]`, `zeroInsight.reasonCode=no_action_needed`를 표시하고 Instance Evidence로 drill-down할 수 있다.

### Fixture and Seed Guardrails

- Project 생성 방식은 여전히 public product surface가 아니다. Demo convenience는 local/internal seed 또는 test fixture로 제한한다.
- Story 6.8 구현자가 seed를 추가한다면 `accounts`, `projects`, `account_project_memberships`를 함께 준비해야 한다.
- `applications`와 `application_instances`는 heartbeat만으로 만들지 않는다. 첫 accepted bucket ingest/catalog upsert source를 따른다.
- Heartbeat telemetry는 starter control-plane/liveness observation이다. Accepted bucket metric freshness/state/read-model source와 분리한다.
- Accepted bucket fixture는 `duration_seconds=30`, UTC bucket boundary, idempotency uniqueness, `local_percentiles_json` source/scope/mergeable 의미를 지켜야 한다.
- Source-scoped p95/p99 값을 넣더라도 application/window p95/p99 scalar를 새로 만들지 않는다.
- Snapshot fixture가 필요하면 `dashboard_snapshots.read_model_json`에 bounded read model projection만 담고 raw JSON explorer 용도로 쓰지 않는다.
- Raw project key, access token, refresh token, GitHub provider token, provider raw payload, secret은 seed/migration/log/response에 노출하지 않는다.

### UI Copy Guardrails

- 허용 방향:
  - "starter heartbeat는 수신됐지만 metric 판단 source인 accepted bucket은 아직 없습니다."
  - "accepted bucket은 들어왔지만 판단 표본이 부족합니다."
  - "현재 우선 노출할 triage는 없습니다."
  - "starter connection과 metric freshness는 분리해서 봅니다."
- 금지 방향:
  - "앱 정상 확정", "문제 없음"을 current health proof처럼 쓰는 표현
  - "host application down", "process down"을 heartbeat/accepted bucket 조합만으로 확정하는 표현
  - "복구 완료", "장애 해결 완료"를 green path baseline에서 쓰는 표현
  - heartbeat success를 metric data freshness 또는 application health success로 합치는 표현
  - insufficient sample을 failure/recovery hardening 시나리오처럼 확장하는 표현

### Previous Story Intelligence

- Story 6.1은 GitHub OAuth only, service token JSON body delivery, Bearer resource API boundary, static dashboard asset, no public project creation을 닫았다.
- Story 6.2는 Project Selection이 `GET /api/projects` current shape만 소비하고 `links.applications`로 Application List에 진입하도록 닫았다.
- Story 6.3은 Application List authenticated fetch와 accepted bucket/starter heartbeat axis separation을 static UI로 구현했다.
- Story 6.4는 Application Dashboard authenticated fetch/rendering을 구현하고 metric state strip과 starter connection strip을 분리했다.
- Story 6.5는 Instance Evidence drill-down을 같은 `dashboard-detail` 영역의 mode 전환으로 구현했다.
- Story 6.6과 6.7은 trend/history/detail을 stored read model projection으로 연결했다. Green path에서 이 화면들은 보조 확인 대상이며 raw explorer가 아니다.
- Story 6.10은 account-project membership authorization을 닫았다. Story 6.8 fixture는 이 최신 authorization boundary를 반드시 만족해야 한다.

### Architecture Constraints

- Active baseline은 Traditional MVC + Service/Repository Layering이다.
- Portal package는 feature-first MVC 구조이며 `domain`은 business feature namespace다.
- 금지 package는 `application`, `port`, `adapter`, `adapter.in`, `adapter.out`다.
- Controller는 repository를 직접 호출하지 않고 service에 위임한다.
- Repository는 controller/dto에 의존하지 않는다.
- JPA entity를 API response, public DTO, service external result로 노출하지 않는다.
- Flyway migration이 physical schema source of truth다. Demo fixture가 schema 변경을 요구한다면 migration source와 `database-schema.md`를 먼저 확인한다.
- Static dashboard UI는 portal runtime 안에서 제공되며 `observability-portal/src/main/resources/static/dashboard/` 안에서 구현한다.
- UI는 server read model을 표시만 한다. UI/controller/repository는 lifecycle state, starter diagnosis, zeroInsight, recovery, rule, p95/p99, endpoint priority, snapshot/history event를 계산하지 않는다.
- Browser token persistence, SPA routing, frontend build stack 도입은 open decision이며 Story 6.8에서 닫지 않는다.
- 새 공개 class/method/helper/test에는 AGENTS.md 기준으로 한국어 Javadoc/comment를 작성한다.

## Open Decisions / Risks

- Project creation의 실제 방식, project ownership, project key 발급/회전/재발급 workflow는 여전히 열지 않는다.
- Browser token persistence, refresh token browser storage, logout/session persistence는 여전히 open decision이다.
- Demo fixture를 쉽게 만들기 위해 public admin/project creation surface를 열고 싶어지는 것이 가장 큰 scope risk다.
- Green path를 보기 좋게 만들려다 UI가 no-triage 상태를 "정상 확정"처럼 말하거나, heartbeat와 accepted bucket을 합쳐 health score를 만들 위험이 있다.
- Story 6.9 failure/recovery hardening이 바로 다음 backlog이므로, stale/down/degraded/recovery observed 시나리오를 6.8에 당겨오면 story 경계가 흐려진다.
- Demo가 snapshot/history를 함께 보여주더라도 그것은 stored read model projection 확인이며 raw metric explorer나 current rejudgement가 아니다.

## Testing / Suggested Verification

Focused test 대상 후보:

- `ProjectNavigationResourceAuthorizationTest`
- `AccountProjectMembershipRepositoryIntegrationTest`
- `ProjectApplicationNavigationServiceTest`
- `DashboardReadModelServiceTest`
- `DashboardControllerTest`
- `InstanceEvidenceControllerTest`
- `InstanceEvidenceReadModelServiceTest`
- `ApplicationListUiContractTest`
- `ApplicationDashboardUiContractTest`
- `InstanceEvidenceUiContractTest`
- 신규 후보 `DemoGreenPathFixtureTest`
- 신규 후보 `DemoGreenPathUiContractTest`

필수 scenario:

- Demo account가 active account-project membership으로 demo project를 볼 수 있다.
- Membership row가 없으면 동일 demo project는 `GET /api/projects`에 나타나지 않고 project-scoped resource API가 `404`로 fail-closed 된다.
- Project Selection -> Application List -> Application Dashboard -> Instance Evidence가 Bearer header 기반 authenticated fetch로 이어진다.
- Heartbeat-only state가 first data waiting guidance를 보여주고 blank first screen이 되지 않는다.
- First bucket/low-sample state가 insufficient sample guidance를 보여주고 host health를 단정하지 않는다.
- Active/no-triage baseline이 `zeroInsight`를 표시하고 정상 확정/복구 완료 copy를 만들지 않는다.
- Accepted bucket metric data axis와 starter heartbeat control-plane axis가 UI와 response fixture에서 분리된다.
- UI가 lifecycle/rule/p95/p99/endpoint priority/event/marker 계산 helper를 만들지 않는다.
- Browser token persistence, URL token parsing, frontend build stack이 추가되지 않는다.

Suggested commands:

```bash
./gradlew :observability-portal:test --tests '*ProjectNavigation*'
./gradlew :observability-portal:test --tests '*AccountProjectMembership*'
./gradlew :observability-portal:test --tests '*DemoGreenPath*'
./gradlew :observability-portal:test --tests '*DashboardReadModelService*'
./gradlew :observability-portal:test --tests '*DashboardController*'
./gradlew :observability-portal:test --tests '*InstanceEvidence*'
./gradlew :observability-portal:test --tests '*ApplicationListUiContract*'
./gradlew :observability-portal:test --tests '*ApplicationDashboardUiContract*'
./gradlew :observability-portal:test --tests '*InstanceEvidenceUiContract*'
./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest
./gradlew :observability-portal:test
git diff --check
```

테스트 이름은 구현 시점의 실제 class 이름에 맞게 조정할 수 있다. 단, demo green path verification은 membership fixture, safe copy, source-axis separation, full Project -> Application -> Dashboard -> Instance Evidence flow, no UI recomputation, no token persistence를 반드시 검증해야 한다.

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- 2026-05-31: BMAD create-story workflow와 customization을 확인했다.
- 2026-05-31: Story 6.8 target이 `sprint-status.yaml`에서 `backlog`임을 확인했다.
- 2026-05-31: Epic 6 context, product source-of-truth, API/schema/account-auth policy, Story 6.1~6.7, Story 6.10 및 6.10 contract decision을 확인했다.
- 2026-05-31: 현재 static dashboard runtime, account-project membership migration/service/interceptor, Project/Application/Dashboard/Instance Evidence 관련 tests를 확인했다.
- 2026-05-31: Story 6.8 create-story 산출물을 `planning-artifacts/stories/6-8-demo-green-path.md`에 생성했다.
- 2026-05-31: Stage 1 membership/demo fixture guard, Stage 2 backend read-model state verification, Stage 3 static UI/copy contract guard의 diff를 전체 검토했다.
- 2026-05-31: production 변경이 static dashboard `zeroInsight` 보조 문구에 한정되고 API/schema/public creation/token persistence 범위를 열지 않음을 확인했다.
- 2026-05-31: Story 6.9 failure/recovery hardening success scenario가 6.8 green path fixture에 섞이지 않았고 stale/down/degraded/recovery marker를 primary success path로 쓰지 않음을 확인했다.
- 2026-05-31: 요청된 focused Gradle test, 전체 `:observability-portal:test`, `git diff --check` 통과를 확인했다.

### Completion Notes List

- Stage 1은 `AccountProjectMembershipRepositoryIntegrationTest`와 `ProjectNavigationResourceAuthorizationTest`에서 active account-project membership, accepted bucket catalog/instance alignment, membership 누락 `projects=[]` 및 project-scoped `404` fail-closed를 검증하도록 닫았다.
- Stage 2는 `DashboardReadModelServiceTest`와 `InstanceEvidenceReadModelServiceTest`에서 `waiting_first_data`, `insufficient_sample`, `active + no_action_needed` read model 상태와 accepted bucket/starter heartbeat axis 분리를 검증하도록 닫았다.
- Stage 3은 static dashboard `app.js`의 server-provided `zeroInsight.reasonCode` 보조 문구와 UI contract tests로 source-aware copy, no token persistence, no URL token parsing, no UI-side p95/p99/state/rule/endpoint 재계산 guard를 보강했다.
- Production/API/UI 범위는 static dashboard asset 안의 안전한 안내 copy로 제한했고, React/Vite/TypeScript/template engine, public project creation, Create Project UI, project key issuance/rotation, invite/admin/billing, browser token persistence, URL token parsing을 열지 않았다.
- Snapshot fixture는 새로 만들지 않았고, green path 검증은 current read model과 existing stored read model boundary를 침범하지 않는 테스트 fixture로 제한했다.
- 검증: 요청된 focused test 10개, 전체 `:observability-portal:test`, `git diff --check`가 모두 통과했다.

### File List

- `planning-artifacts/stories/6-8-demo-green-path.md`
- `implementation-artifacts/sprint-status.yaml`
- `observability-portal/src/main/resources/static/dashboard/app.js`
- `observability-portal/src/test/java/com/observation/portal/domain/account/repository/AccountProjectMembershipRepositoryIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/controller/ProjectNavigationResourceAuthorizationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ApplicationDashboardUiContractTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ApplicationListUiContractTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/InstanceEvidenceUiContractTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ProjectEntryUiContractTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/service/DashboardReadModelServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/instance/service/InstanceEvidenceReadModelServiceTest.java`

### Change Log

- 2026-05-31: Story 6.8 Demo green path create-story 산출물을 생성하고 ready-for-dev 상태로 전환했다.
- 2026-05-31: Demo green path membership fixture, backend read-model state, static UI/copy guard를 통합 검증하고 story를 review 상태로 전환했다.
- 2026-05-31: 최종 리뷰 PASS 후 Story 6.8과 sprint status를 done 상태로 전환했다.
