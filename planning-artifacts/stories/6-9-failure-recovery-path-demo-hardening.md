---
artifactType: story
storyId: "6.9"
storyKey: "6-9-failure-recovery-path-demo-hardening"
epic: "Epic 6. Dashboard User Flow and Demo Hardening"
title: "Failure/recovery path demo hardening"
architectureStyle: Traditional MVC
status: done
date: 2026-05-31
---

# Story 6.9 - Failure/recovery path demo hardening

## Status

done

## Story

portal 사용자는 green path 이후에도 portal down, duplicate ingest, stale/down candidate, telemetry unreachable, degraded/recovery observed 같은 실패/복구 흐름을 빈 화면 없이 안전한 언어로 확인하고 싶다.

그래야 demo가 "starter/portal/control-plane에 문제가 있을 수 있음"과 "accepted bucket metric data가 stale/down/recovery 상태임"을 분리해서 보여주며, host application down, 복구 완료, 앱 정상 확정 같은 과도한 결론 없이 Application Dashboard, Instance Evidence, Instance Snapshot Trend, Snapshot/History marker 흐름을 닫을 수 있다.

## Source of Truth

아래 문서를 읽고 반영한 BMAD create-story context다. 구현 중 충돌처럼 보이는 지점은 최신 product source-of-truth, active contract 문서, Story 6.8/6.10 완료 상태, 현재 code/test shape 순서로 우선한다.

1. `_bmad/custom/project-context.md`
2. `implementation-artifacts/sprint-status.yaml`
3. `implementation-artifacts/epic-6-context.md`
4. `planning-artifacts/current-product-source-of-truth.md`
5. `planning-artifacts/api-surface.md`
6. `planning-artifacts/database-schema.md`
7. `planning-artifacts/contracts/account-auth-policy.md`
8. `planning-artifacts/contracts/read-model-contract.md`
9. `planning-artifacts/contracts/state-semantics.md`
10. `planning-artifacts/contracts/starter-failure-semantics.md`
11. `planning-artifacts/contracts/operational-event-history.md`
12. `planning-artifacts/epics.md`
13. `planning-artifacts/epic5-6-dashboard-alignment-context.md`
14. `implementation-artifacts/epic-5-retro-2026-05-27.md`
15. `planning-artifacts/stories/6-8-demo-green-path.md`
16. `planning-artifacts/stories/6-10-account-project-membership-and-scoped-project-entry.md`
17. `implementation-artifacts/spec-story-6-10-account-project-membership-and-scoped-project-entry-contract-decisions.md`

관련 story/spec handoff:

1. `planning-artifacts/stories/2-4-async-flush-worker.md`
2. `planning-artifacts/stories/3-4-duplicate-handling.md`
3. `planning-artifacts/stories/4-3-recovery-guidance.md`
4. `planning-artifacts/stories/5-2-application-dashboard-read-model-skeleton.md`
5. `planning-artifacts/stories/5-4-triage-summary-and-zero-insight-recovery-mapping.md`
6. `implementation-artifacts/spec-story-5-4-triage-zero-insight-recovery-contract-decisions.md`
7. `planning-artifacts/stories/5-6-instance-evidence-read-model.md`
8. `implementation-artifacts/spec-story-5-6-instance-evidence-contract-decisions.md`
9. `planning-artifacts/stories/5-7-instance-snapshot-trend-projection.md`
10. `implementation-artifacts/spec-story-5-7-instance-snapshot-trend-contract-decisions.md`
11. `planning-artifacts/stories/5-8-b-snapshot-marker-detail-recovery-source.md`
12. `planning-artifacts/stories/5-9-a-operational-event-history-api-skeleton-and-source-boundary.md`
13. `planning-artifacts/stories/5-9-b-operational-event-promotion-suppression-and-period-folding.md`
14. `planning-artifacts/stories/6-4-application-dashboard-ui-integration.md`
15. `planning-artifacts/stories/6-5-instance-evidence-ui.md`
16. `planning-artifacts/stories/6-6-instance-snapshot-trend-ui.md`
17. `planning-artifacts/stories/6-7-snapshot-history-marker-ui-and-deep-link.md`

확인한 현재 코드/테스트 패턴:

1. `observability-portal/src/main/resources/static/dashboard/app.js`
2. `observability-portal/src/main/resources/static/dashboard/index.html`
3. `observability-portal/src/main/resources/static/dashboard/styles.css`
4. `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ApplicationDashboardUiContractTest.java`
5. `observability-portal/src/test/java/com/observation/portal/domain/dashboard/InstanceEvidenceUiContractTest.java`
6. `observability-portal/src/test/java/com/observation/portal/domain/dashboard/InstanceSnapshotTrendUiContractTest.java`
7. `observability-portal/src/test/java/com/observation/portal/domain/dashboard/RecentHistoryUiContractTest.java`
8. `observability-portal/src/test/java/com/observation/portal/domain/dashboard/service/DashboardReadModelServiceTest.java`
9. `observability-portal/src/test/java/com/observation/portal/domain/catalog/service/ProjectApplicationNavigationServiceTest.java`
10. `observability-portal/src/test/java/com/observation/portal/domain/history/service/OperationalEventHistoryProjectorTest.java`
11. `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotMarkerServiceTest.java`
12. `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotDetailServiceTest.java`

## Scope / Out of Scope

포함:

- Story 6.8 green path success fixture와 별도인 failure/recovery demo fixture/test-data 정렬
- portal down, duplicate ingest, stale/down candidate, telemetry unreachable, degraded entered/resolved, recovery observed scenario를 prototype 상태 시나리오와 맞춘 safe demo path
- Application Dashboard에서 `telemetry_unreachable`, `metric_data_idle`, `observing_recovery`, stale/down/degraded state, no events/markers/source absence가 빈 화면 없이 설명되는지 검증
- Instance Evidence에서 accepted bucket metric data axis와 starter heartbeat control-plane axis가 failure/recovery 상태에서도 분리되는지 검증
- Instance Snapshot Trend에서 stored point의 stale/down/degraded/recovery 관련 관찰을 instance health score나 current 재판정 없이 표시하는지 검증
- Snapshot/History marker와 Operational Event feed에서 `stale_entered`, `down_entered`, `degraded_entered`, `degraded_resolved`, `recovery_observed` copy가 safe language를 유지하는지 검증
- portal down 또는 heartbeat/ingest 전송 실패가 host application request path를 막지 않는 starter fail-open guard를 demo verification에 연결
- active account-project membership fixture를 포함한 failure/recovery demo project visibility 유지
- duplicate ingest는 active MVP duplicate policy와 맞게 no-second-row/no-overwrite/error copy를 검증하고, dashboard snapshot/read model refresh trigger로 오해하지 않게 guard
- UI가 server read model을 표시만 하고 lifecycle, starter diagnosis, zeroInsight, recovery, rule, p95/p99, endpoint priority, marker/event promotion을 재계산하지 않는 regression guard
- copy guard: host application down 확정, process down 확정, 앱 정상 확정, 문제 없음, 복구 완료, 장애 해결 완료 표현 금지
- static dashboard asset, MVC controller/service/repository layering, Spring Data JPA/Flyway 기준 유지

제외:

- production/test code 구현 자체는 이 create-story 작업 범위 밖이다.
- Story 6.8 green path success fixture를 failure/recovery fixture로 바꾸거나 오염시키는 변경
- public project creation API, public `POST /api/projects`
- Create Project UI/flow, 로그인 직후 자동 project 생성
- project key issuance/rotation/reissue workflow
- invite/team/org/admin/billing/tenant model
- GitHub provider token을 resource API authorization에 사용하는 흐름
- browser token persistence, `localStorage`, `sessionStorage`, cookie token 저장
- URL fragment/query token parsing, direct API URL navigation, SPA routing
- React, Vite, TypeScript, `package.json`, `src/main/frontend`, 별도 frontend build/deploy, view resolver/template engine
- raw bucket explorer, raw snapshot JSON explorer, endpoint timeseries, arbitrary metric query UI
- endpoint p95/p99, histogram-derived percentile, application/window percentile 평균/최댓값/병합
- heartbeat success/failure/missing을 accepted bucket, dashboard snapshot, operational event, p95/p99, rule/read-model calculation source로 쓰는 변경
- new operational events table, endpoint timeseries table, materialized view, Redis/outbox, durable event/incident workflow
- Story 6.10 account-project membership, role, invite, ownership, authorization model 재설계

## Acceptance Criteria

1. Story 6.9 story file은 create-story 완료 시 `ready-for-dev`로 생성됐고, 구현 착수 시 story file과 `sprint-status.yaml`의 `6-9-failure-recovery-path-demo-hardening`이 `in-progress`로 전환된다.
2. 구현 story 실행 시 production 변경은 Traditional MVC + Service/Repository Layering과 feature-first package 구조를 따른다.
3. Static dashboard runtime은 계속 `observability-portal/src/main/resources/static/dashboard/` 아래의 HTML/CSS/JS를 사용한다.
4. Story 6.9는 React/Vite/TypeScript/frontend build stack, template engine, SPA route를 도입하지 않는다.
5. Failure/recovery fixture는 Story 6.8 green path fixture와 분리된 이름, helper, seed path, test scenario를 사용한다.
6. Story 6.8의 heartbeat-only, first bucket/insufficient sample, active/no-triage baseline success fixture는 primary failure/recovery fixture로 재사용하거나 변경하지 않는다.
7. Failure/recovery demo project는 Story 6.10 이후 조건에 맞게 Bearer account와 active `account_project_memberships` row를 명시적으로 가진다.
8. Membership 없는 account가 failure/recovery demo project resource API를 호출하면 기존처럼 `404` fail-closed를 유지한다.
9. Failure/recovery fixture는 public project creation API, Create Project UI, 로그인 직후 자동 project 생성, project key 발급/회전 workflow를 우회로 만들지 않는다.
10. 모든 UI fetch는 service `Authorization: Bearer <access_token>` header만 사용하고 GitHub provider token, refresh token, browser storage token을 사용하지 않는다.
11. Browser code는 access token persistence 위치를 새로 결정하지 않는다. in-memory hook과 Bearer header 구성만 사용한다.
12. Browser code는 `localStorage`, `sessionStorage`, cookie token 저장, URL fragment/query token parsing을 추가하지 않는다.
13. Portal down scenario는 starter fail-open 의미를 보여준다. Portal ingest/heartbeat endpoint가 timeout/down이어도 host application startup/request path가 막히지 않는 test 또는 existing guard를 demo verification에 포함한다.
14. Portal down copy는 "portal 또는 network/control-plane 도달성 확인" 계열이어야 하며 host application down, host process down, 앱 내려감으로 단정하지 않는다.
15. Portal down 또는 heartbeat failure는 accepted bucket, dashboard snapshot, operational event, p95/p99, rule/read-model calculation source처럼 취급되지 않는다.
16. Duplicate ingest scenario는 active MVP duplicate policy를 따른다. 같은 `(project_id, idempotency_key)` retry/conflict는 second `accepted_metric_buckets` row를 만들거나 기존 row를 overwrite하지 않는다.
17. Duplicate ingest copy는 "중복 전송 후보가 안전하게 거부/수렴됐다"는 의미를 전달하고, application failure, data loss 확정, recovery complete로 표현하지 않는다.
18. Duplicate ingest scenario는 dashboard snapshot refresh, operational event, endpoint priority, lifecycle state recalculation trigger가 아니다.
19. Stale candidate scenario는 accepted bucket metric data freshness 부족을 뜻하며 host application down 확정으로 표현하지 않는다.
20. Down candidate scenario는 metric data-plane freshness boundary를 뜻하며 host application process down 확정으로 표현하지 않는다.
21. Recent heartbeat + missing/stale/down accepted bucket 조합은 starter control-plane과 metric data axis를 분리해 `waiting_first_data` 또는 `metric_data_idle` 계열로 표현한다.
22. Missing/stale heartbeat + missing/stale/down accepted bucket 조합은 `telemetry_unreachable` 또는 unknown 계열로 표현하되 root cause를 portal, network, starter schedule, process 종료 중 하나로 확정하지 않는다.
23. Telemetry unreachable copy는 host application down, deleted application, 정상/장애 확정, 복구 완료를 말하지 않는다.
24. Recovery observed scenario는 stale/down 이후 current accepted bucket이 다시 들어왔지만 sample이 부족한 상태를 `recovery.isRecovering=true`와 `zeroInsight.reasonCode=observing_recovery` 또는 stored recovery marker로 표현한다.
25. Recovery observed copy는 "회복 관찰 중", "다음 bucket/sample 증가 확인" 계열이어야 하며 "복구 완료", "장애 해결 완료", "앱이 다시 정상"을 쓰지 않는다.
26. `recovery.retryAfterSeconds`는 자동 예약이 아니라 다음 판단 대기 안내로 표시한다.
27. `lastHealthyAt`은 previous active/healthy read model 또는 snapshot source만 사용하고 현재 accepted bucket, current request time, heartbeat recency로 추론하지 않는다.
28. Degraded scenario는 triage card 존재만으로 state를 재계산하지 않는다. Degraded state는 server read model이 준 `state.code=degraded`와 stored event/marker만 표시한다.
29. Dashboard triage card 노출 기준과 operational history high-confidence event 승격 기준을 UI가 다시 계산하지 않는다.
30. 30초 단발 blip은 degraded state나 `degraded_entered` event처럼 demo하지 않는다.
31. Application Dashboard는 `zeroInsight`, `recovery`, `state`, `starterConnection`, `endpointPriority`, `snapshot`을 서버 응답 그대로 렌더링하고 새 reason code 또는 alias를 만들지 않는다.
32. `triageCards=[]`이면 Application Dashboard는 반드시 server-provided `zeroInsight`를 표시한다.
33. Application Dashboard first screen은 stale/down/telemetry/recovery 상태에서도 loading/error/empty card만 남는 빈 화면처럼 보이지 않는다.
34. Metric state strip과 starter connection strip은 failure/recovery state에서도 별도 영역/source/copy로 유지한다.
35. UI는 accepted bucket freshness와 starter heartbeat recency를 하나의 `health`, `hostHealth`, `applicationHealth`, `connectedAndHealthy`, `hostStatus`로 합치지 않는다.
36. Instance Evidence는 `metricData.statusSource=accepted_bucket`, `starterConnection.statusSource=starter_heartbeat`, `stateImpact=none`을 failure/recovery fixture에서도 별도 axis로 표시한다.
37. Instance Evidence의 `endpointEvidence.status=suppressed/application_freshness_not_current`는 stale/down 직전 endpoint evidence를 current concern처럼 보이지 않게 한다.
38. Instance Evidence는 selected instance evidence drill-down이며 application 판단을 대체하거나 instance health score를 만들지 않는다.
39. Instance Snapshot Trend는 stored snapshot/read model projection만 표시하고 accepted bucket, heartbeat, resource hint를 조합해 current instance state를 만들지 않는다.
40. Instance Snapshot Trend의 `storedApplicationStateCode`, `captureReason`, `endpointEvidenceRefs`는 marker/event/recovery semantics로 UI에서 재해석하지 않는다.
41. `points=[]`, display range empty, no concern observed copy는 source absence 또는 stored point 관찰 부재로 표현하고 "문제 없음", "정상", "복구 완료"로 표현하지 않는다.
42. Snapshot/History event feed는 server-provided event type/severity/title/summary/evidence/link만 표시한다.
43. Snapshot/History marker timeline은 server-provided marker type/severity/readMeaning/captureReason/title/summary/link만 표시한다.
44. UI는 `captureReason`에서 marker type, severity, event meaning, recovery meaning을 다시 분류하지 않는다.
45. Snapshot detail은 `readSemantics.mode=stored_snapshot_detail`, `currentStateRecalculated=false`, `liveSourcesJoined=[]`, `rawReadModelJsonExposed=false`를 유지한다.
46. Snapshot detail은 stored read model summary만 표시하고 current dashboard fallback, current state 재판정, raw JSON dump를 만들지 않는다.
47. Operational event types는 `state_changed`, `degraded_entered`, `degraded_resolved`, `stale_entered`, `down_entered`, `recovery_observed`, `high_confidence_concern`만 허용한다.
48. `scheduled_snapshot`, `query_fallback_snapshot`, `stored_snapshot`, 단순 `state_observation`은 UI에서 operational event로 승격하지 않는다.
49. Event/marker empty state는 retention/source absence 또는 event 후보 없음으로 표현하고 "현재 문제 없음", "복구 완료", "장애 해결 완료"를 암시하지 않는다.
50. Event evidence와 snapshot endpoint evidence는 bounded field만 표시한다. raw snapshot JSON, raw bucket list, raw endpoint JSON, endpoint p95/p99, trace id, per-request sample, query string, query key/value를 표시하지 않는다.
51. p95/p99는 server read model의 source-scoped starter canonical point만 표시하고 평균/최댓값/병합/히스토그램 재계산을 하지 않는다.
52. Histogram bucket은 distribution evidence로만 표시하고 percentile scalar를 만들지 않는다.
53. Endpoint priority는 server-provided order/rank/reason/evidence를 표시만 하고 root cause 확정 순위나 endpoint health score로 재해석하지 않는다.
54. Project/Application/Instance/Snapshot path mismatch와 account-project membership mismatch는 기존처럼 `404` fail-closed로 유지된다.
55. `404` safe copy는 application down, deleted application, missing project existence, 문제가 없음, 정상 확정을 단정하지 않는다.
56. Generic error state는 backend detail, stack trace, token, provider raw payload, secret 없이 다시 시도 copy만 표시한다.
57. Overlapping Dashboard/Evidence/Trend/History/Detail request, token clear, Project/Application/Instance 재선택 상황에서 stale response가 현재 화면을 덮지 않는다.
58. Failure/recovery fixture가 snapshot row를 만들면 `dashboard_snapshots.read_model_json`은 bounded read model projection만 담고 raw snapshot explorer나 endpoint timeseries source로 쓰지 않는다.
59. Operational Event History는 별도 `operational_events` table 없이 `dashboard_snapshots` 기반 service-layer derived feed로 유지한다.
60. Failure/recovery demo verification은 green path success, failure/recovery path, membership fail-closed path를 서로 다른 scenario로 검증한다.
61. Static UI contract test는 no token persistence, no URL token parsing, no frontend build stack, no UI-side recomputation guard를 계속 검증한다.
62. Backend focused test는 failure/recovery read model fixture가 accepted bucket axis와 starter heartbeat axis를 분리하고 forbidden copy를 만들지 않는지 검증한다.
63. Starter focused test 또는 existing guard는 portal down/timeout이 host startup/request path를 막지 않는지 검증한다.
64. Suggested verification과 `git diff --check`가 통과해야 implementation completion으로 볼 수 있다.
65. 새 public class/method/helper/test 또는 동작을 바로 이해하기 어려운 내부 helper에는 `AGENTS.md` 지침에 따라 한국어 Javadoc/comment를 작성한다.

## Tasks / Subtasks

- [x] Failure/recovery fixture boundary 정렬 (AC: 5~12, 16~18, 58~60)
  - [x] Story 6.8 green path fixture와 별도 helper/name/seed를 사용한다.
  - [x] Failure/recovery demo account, project, active membership, application, instance, accepted bucket, heartbeat telemetry, optional snapshot rows를 서로 일관되게 연결한다.
  - [x] Green path success fixture의 `waiting_first_data`, `insufficient_sample`, `no_action_needed` baseline 값을 failure/recovery fixture로 바꾸지 않는다.
  - [x] Duplicate ingest scenario는 current MVP duplicate key reject/no-second-row policy를 따른다.
  - [x] Optional snapshot fixture는 `dashboard_snapshots.read_model_json` bounded projection으로만 만들고 raw snapshot explorer source로 쓰지 않는다.

- [x] Portal down / telemetry unreachable safe path 검증 (AC: 13~15, 21~23, 54~57, 63)
  - [x] Existing starter fail-open/non-blocking tests를 확인하고 필요하면 portal timeout/down demo scenario 이름으로 보강한다.
  - [x] Heartbeat failure/missing과 accepted bucket missing/stale 조합이 `telemetry_unreachable` 또는 unknown copy로 표현되는지 검증한다.
  - [x] Recent heartbeat + stale/down accepted bucket 조합이 host down copy 없이 metric data idle/waiting 계열로 표현되는지 검증한다.
  - [x] Error/404/generic safe copy가 application down, deleted resource, 정상/장애 확정을 말하지 않게 guard한다.

- [x] Application Dashboard failure/recovery rendering 보강 (AC: 19~35, 51~53, 57, 61~62)
  - [x] `ApplicationDashboardUiContractTest` 또는 동등 static UI contract test에 stale/down, telemetry unreachable, observing recovery, degraded fixture를 추가한다.
  - [x] `DashboardReadModelServiceTest` 또는 동등 focused backend test에 zeroInsight precedence와 recovery observed demo fixture를 추가/정리한다.
  - [x] `zeroInsight.reasonCode=observing_recovery`가 `insufficient_sample`보다 우선하고 "복구 완료" copy를 만들지 않는지 검증한다.
  - [x] Metric state strip과 starter connection strip이 failure/recovery state에서도 분리되는지 검증한다.
  - [x] UI-side lifecycle/recovery/rule/p95/p99/endpointPriority 계산 helper가 생기지 않게 guard한다.

- [x] Instance Evidence / Trend failure state guard 보강 (AC: 36~41, 51~57, 61)
  - [x] `InstanceEvidenceUiContractTest`에 missing/stale accepted bucket + heartbeat 조합, suppressed endpoint evidence, recovery/degraded handoff fixture를 추가한다.
  - [x] `InstanceSnapshotTrendUiContractTest`에 stale/down/recovery stored point, `points=[]`, no concern observed copy guard를 추가한다.
  - [x] `metricData.statusSource=accepted_bucket`, `starterConnection.statusSource=starter_heartbeat`, `stateImpact=none`이 fixture마다 보존되는지 검증한다.
  - [x] Trend point의 `storedApplicationStateCode`와 `captureReason`을 instance health/event/recovery semantics로 재분류하지 않게 guard한다.

- [x] Snapshot/History marker and event copy guard 보강 (AC: 42~50, 54~59, 61)
  - [x] `RecentHistoryUiContractTest`에 `stale_entered`, `down_entered`, `degraded_entered`, `degraded_resolved`, `recovery_observed` event/marker fixture를 추가한다.
  - [x] `OperationalEventHistoryProjectorTest`와 snapshot marker/detail tests에서 forbidden copy absence를 검증한다.
  - [x] Recovery marker/event copy는 "회복 관찰 중" 계열만 허용하고 resolvedAt 또는 recovery event를 "복구 완료"로 번역하지 않게 guard한다.
  - [x] Empty events/markers, missing anchor, expired snapshot detail이 health/recovery completion copy로 표현되지 않는지 검증한다.
  - [x] Raw snapshot/bucket/endpoint explorer, endpoint timeseries, UI-side event/marker promotion helper가 없는지 guard한다.

- [x] Regression and documentation touchpoints 정리 (AC: 1~4, 60~65)
  - [x] 필요하면 local/demo fixture 설명 또는 test fixture comment를 보강하되 public project creation/key/admin 범위를 열지 않는다.
  - [x] 새 helper/test에는 한국어 Javadoc/comment를 작성한다.
  - [x] Focused tests, full portal/starter relevant tests, `git diff --check`를 실행한다.
  - [x] Story file의 Dev Agent Record, File List, Change Log를 dev-story 완료 시 실제 변경과 맞게 갱신한다.

## Dev Notes

### Current Code State

- 현재 `sprint-status.yaml` 기준 Epic 6은 `in-progress`, Story 6.1~6.8과 6.10은 `done`, Story 6.9는 create-story 전 `backlog`였다.
- 현재 브랜치는 `codex/6-9-failure-recovery-path-demo-hardening`이다.
- Story 6.8 구현은 green path fixture와 UI/backend tests를 보강했다. 주요 변경은 static dashboard `zeroInsight` 보조 문구, membership/demo fixture guard, Dashboard/Instance Evidence focused tests다.
- Story 6.10 구현으로 `GET /api/projects`와 `/api/projects/{projectId}/applications/**` resource API는 Bearer account의 active account-project membership scope 안에서만 동작한다.
- `app.js`는 Project, Application List, Dashboard, Instance Evidence, Instance Snapshot Trend, Snapshot/History, Snapshot Detail state machine을 한 static runtime 안에서 관리한다.
- `app.js`의 `ZERO_INSIGHT_SOURCE_GUIDANCE`에는 현재 `waiting_first_data`, `insufficient_sample`, `no_action_needed` 보조 문구가 있고 unknown reason은 server-provided reason/action 안내 fallback으로 표현된다. Story 6.9 구현자는 `telemetry_unreachable`, `metric_data_idle`, `observing_recovery` safe guidance를 추가할 수 있지만 새 state 판단을 만들면 안 된다.
- `app.js`는 allowed snapshot marker/event type validation, `starterConnection.statusSource=starter_heartbeat`, `metricData.statusSource=accepted_bucket`, `stateImpact=none`, no raw field guard를 이미 갖고 있다.
- `DashboardReadModelServiceTest`에는 green path state, zeroInsight mapping, `observing_recovery`, stale/missing heartbeat scenarios, forbidden host-down copy helper가 있다. Story 6.9는 demo failure/recovery scenario 이름과 static UI copy guard를 더 선명하게 닫는 방향이 안전하다.
- `RecentHistoryUiContractTest`, `InstanceSnapshotTrendUiContractTest`, `InstanceEvidenceUiContractTest`, `ApplicationDashboardUiContractTest`는 실제 dashboard script를 Node VM에서 실행해 safe state, stale response, token boundary, no recomputation guard를 검증하는 패턴을 쓴다.

### Failure / Recovery Scenario Matrix

| Scenario | Source Axis | Expected demo meaning | Must not say |
|---|---|---|---|
| portal down / timeout | starter -> portal transport, heartbeat/ingest client | portal/network/control-plane 도달성 문제 후보. host path는 계속 진행 | host application down, process down, 앱 장애 확정 |
| duplicate ingest | portal ingest idempotency/key guard | retry/중복 후보가 second row 없이 안전하게 거부/수렴 | data loss 확정, dashboard refresh trigger, recovery complete |
| stale candidate | accepted bucket metric data freshness | metric data가 최근 기준을 벗어나 관찰 부족 | host process down |
| down candidate | accepted bucket metric data freshness | data-plane freshness boundary가 더 길어짐 | host application down 확정 |
| telemetry unreachable | heartbeat axis + accepted bucket axis both stale/missing | starter/portal/network/schedule 후보를 확인해야 함 | root cause 확정, deleted app, 정상/장애 확정 |
| degraded entered | server read model / stored snapshot/event | high-confidence/server-computed concern 관찰 | UI-side rule/threshold 재계산 |
| degraded resolved | stored snapshot/event | stored read model에서 해소 조건 관찰 | 복구 완료, 앱 정상 확정 |
| recovery observed | stale/down 이후 current bucket + insufficient sample 또는 stored recovery marker | 회복 관찰 중, 다음 bucket/sample 확인 | 복구 완료, 장애 해결 완료 |

### Fixture and Seed Guardrails

- Failure/recovery fixture는 green path fixture를 재사용해 변형하지 말고 별도 helper로 둔다. Test name에도 `FailureRecovery`, `TelemetryUnreachable`, `RecoveryObserved` 같은 목적을 드러낸다.
- Demo project visibility에는 active account-project membership이 필요하다. Membership 누락으로 `projects=[]`가 되는 것은 Story 6.10 이후 안전한 기본값이며 failure/recovery state가 아니다.
- Heartbeat telemetry는 starter control-plane/liveness observation이다. Accepted bucket metric freshness/state/read-model source와 분리한다.
- Accepted bucket fixture는 `duration_seconds=30`, UTC bucket boundary, idempotency uniqueness, `local_percentiles_json` source/scope/mergeable 의미를 지켜야 한다.
- Duplicate ingest fixture는 active MVP policy에 맞춰 no second row/no overwrite를 검증한다. Story 3.4 원문의 duplicate success shape와 현재 완료 정책이 다르면 현재 완료 정책과 code/test를 우선한다.
- Snapshot fixture가 필요하면 `dashboard_snapshots.read_model_json`에 bounded read model projection만 담고 raw JSON explorer 용도로 쓰지 않는다.
- Raw project key, access token, refresh token, GitHub provider token, provider raw payload, secret은 seed/migration/log/response에 노출하지 않는다.

### UI Copy Guardrails

허용 방향:

- "starter heartbeat와 metric freshness는 별도 source로 봅니다."
- "metric data가 최근 기준을 벗어나 관찰 부족 상태입니다."
- "starter/portal/network 연결 후보를 확인하세요. host application 상태는 이 신호만으로 확정하지 않습니다."
- "새 metric bucket이 다시 관찰됐고 sample이 충분해지는지 다음 bucket에서 확인합니다."
- "저장된 snapshot에서 stale/down/degraded/recovery 흐름이 관찰됐습니다."

금지 방향:

- "host application down", "host process down", "앱 내려감"을 heartbeat/accepted bucket 조합만으로 확정
- "앱 정상 확정", "문제 없음", "현재 정상"을 zeroInsight, empty events, no concern observed의 의미로 사용
- "복구 완료", "장애 해결 완료", "앱이 다시 정상"을 recovery observed, degraded resolved, resolvedAt에 붙이는 표현
- heartbeat success를 metric data freshness 또는 application health success로 합치는 표현
- stale/down 직전 endpoint evidence를 current endpoint priority/root cause처럼 되살리는 표현

### Boundary With Story 6.8

- Story 6.8은 30~60초 green path다: heartbeat-only, first accepted bucket/insufficient sample, active/no-triage baseline, Project -> Application -> Dashboard -> Instance Evidence flow.
- Story 6.9는 failure/recovery hardening이다: stale/down/degraded/recovery observed, telemetry unreachable, portal down, duplicate ingest, marker/history copy.
- 6.9 구현자는 6.8 green path success fixture를 열지 않는다. Green path는 regression으로 유지하고, failure/recovery는 별도 fixture로 검증한다.
- Green path의 `no_action_needed`는 "현재 우선 노출할 triage 없음"이지 "정상 확정"이 아니다. 6.9에서도 이 금지 copy를 유지한다.

### Boundary With Story 6.10

- Story 6.10은 account-project membership authorization을 이미 닫았다. 6.9는 authorization model을 다시 설계하지 않는다.
- Failure/recovery demo fixture는 active membership project 안에서만 resource API를 통과해야 한다.
- Membership mismatch는 `404` fail-closed이며, 6.9 copy는 project/application/snapshot 존재 여부를 드러내지 않는다.
- Public project creation, project key issuance/rotation/reissue, invite/team/org/admin/billing은 계속 out-of-scope다.

### Previous Story Intelligence

- Story 2.4는 portal timeout/down 상황에서도 host request path가 blocked 되지 않는 background worker/fail-open guard를 닫았다. 6.9는 이 의미를 demo verification에 연결하되 starter delivery architecture를 재설계하지 않는다.
- Story 3.4 완료 상태는 duplicate key conflict/no second row/no overwrite 중심의 active MVP duplicate policy다. Duplicate ingest demo는 이 정책을 따라야 한다.
- Story 4.3은 recovery를 `UNKNOWN + recovery.isRecovering=true`로 고정하고 heartbeat와 recovery copy를 결합하지 않게 했다.
- Story 5.2는 dashboard skeleton에서 accepted bucket axis와 starter connection axis를 분리하고, zeroInsight mapping을 service responsibility로 닫았다.
- Story 5.4는 `observing_recovery` precedence, `telemetry_unreachable`, `metric_data_idle`, triage card와 degraded state 분리를 닫았다.
- Story 5.6은 Instance Evidence를 application 판단을 대체하지 않는 drill-down으로 닫고, metricData/starterConnection axis와 endpoint evidence suppression을 고정했다.
- Story 5.7은 Instance Snapshot Trend를 stored snapshot/read model projection으로 닫고, marker/detail/recovery semantics는 5.8/5.9로 넘겼다.
- Story 5.8-b는 recovery marker, previousState, lastHealthyAt, snapshot detail source를 stored snapshot/read model 기준으로 닫았다.
- Story 5.9-b는 operational event promotion/folding/suppression을 service-layer derived feed로 닫고, recovery/degraded/stale/down copy 금지선을 고정했다.
- Story 6.4~6.7은 static dashboard에서 Dashboard, Instance Evidence, Trend, History/Detail을 authenticated fetch + safe state + no recomputation pattern으로 구현했다. 6.9는 새 architecture보다 이 contract test 패턴을 확장하는 것이 안전하다.
- 최근 git history는 Story 6.8 green path가 static `app.js` copy와 focused UI/backend tests 중심으로 닫혔고, Story 6.10 done mark와 catalog path terminology clarification이 뒤따랐다. 6.9도 용어를 account-project authorization과 catalog path consistency로 분리한다.

### Architecture Constraints

- Active baseline은 Traditional MVC + Service/Repository Layering이다.
- Portal package는 feature-first MVC 구조이며 `domain`은 business feature namespace다.
- 금지 package는 `application`, `port`, `adapter`, `adapter.in`, `adapter.out`다.
- Controller는 repository를 직접 호출하지 않고 service에 위임한다.
- Repository는 controller/dto에 의존하지 않는다.
- JPA entity를 API response, public DTO, service external result로 직접 반환하지 않는다.
- Flyway migration이 physical schema source of truth다. Story 6.9는 새 table/migration을 기대하지 않는다.
- Static dashboard UI는 portal runtime 안에서 제공되며 `observability-portal/src/main/resources/static/dashboard/` 안에서 구현한다.
- UI는 server read model을 표시만 한다. UI/controller/repository는 lifecycle state, starter diagnosis, zeroInsight, recovery, rule, p95/p99, endpoint priority, snapshot/history event를 계산하지 않는다.
- Browser token persistence, SPA routing, frontend build stack 도입은 open decision이며 Story 6.9에서 닫지 않는다.

## Open Decisions / Risks

- Project creation의 실제 방식, project ownership, project key 발급/회전/재발급 workflow는 여전히 열지 않는다.
- Browser token persistence, refresh token browser storage, logout/session persistence는 여전히 open decision이다.
- React/Vite/TypeScript/SPA routing 전환 시점과 source/build output 구조는 open decision이다.
- Failure/recovery demo를 보기 좋게 만들려다 UI가 state/rule/event/marker/recovery meaning을 client-side로 재계산할 위험이 가장 크다.
- `down` label은 data-plane freshness boundary로만 사용해야 한다. Host process down처럼 읽히는 copy가 생기면 반드시 safe copy로 제한한다.
- `degraded_resolved`와 event `resolvedAt`은 사용자가 보기에는 해결처럼 느껴질 수 있지만, Story 6.9에서는 "stored read model에서 해소 조건 관찰" 정도로 제한해야 한다.
- Duplicate ingest scenario에서 원문 Story 3.4의 duplicate success AC와 현재 완료 policy가 섞일 수 있다. 구현자는 현재 code/test와 completion notes의 active MVP policy를 우선한다.

## Testing / Suggested Verification

Focused test 대상 후보:

- `ApplicationDashboardUiContractTest`
- `InstanceEvidenceUiContractTest`
- `InstanceSnapshotTrendUiContractTest`
- `RecentHistoryUiContractTest`
- `DashboardReadModelServiceTest`
- `ProjectApplicationNavigationServiceTest`
- `OperationalEventHistoryProjectorTest`
- `OperationalEventHistoryServiceTest`
- `DashboardSnapshotMarkerServiceTest`
- `DashboardSnapshotDetailServiceTest`
- `MetricBucketRepositoryIntegrationTest`
- `IngestAcceptanceServiceTest`
- `StarterNonBlockingIngestTest`
- `StarterHeartbeatSenderTest`
- 신규 후보 `DemoFailureRecoveryUiContractTest`
- 신규 후보 `DemoFailureRecoveryFixtureTest`

필수 scenario:

- Green path success fixture가 unchanged regression으로 통과한다.
- Failure/recovery fixture는 active membership project 안에서 Project -> Application -> Dashboard -> Instance Evidence -> Trend/History로 진입한다.
- Membership 없는 account는 같은 failure/recovery project resource API에서 `404` fail-closed 된다.
- Portal down/timeout은 host request path를 막지 않고 host down copy를 만들지 않는다.
- Duplicate ingest는 second row/no overwrite를 막고 dashboard snapshot/read model refresh trigger로 오해되지 않는다.
- Stale/down candidate copy는 metric data-plane freshness 부족으로만 표현된다.
- Missing/stale heartbeat + missing/stale accepted bucket은 telemetry unreachable copy로 표현되고 root cause를 확정하지 않는다.
- Recovery observed는 `observing_recovery` 또는 `recovery_observed` marker/event로 표현되며 복구 완료를 말하지 않는다.
- Degraded entered/resolved event/marker는 server/stored source를 표시만 하고 UI가 threshold/hysteresis를 계산하지 않는다.
- Application Dashboard, Instance Evidence, Trend, Snapshot/History/Detail 모두 accepted bucket axis와 starter heartbeat axis를 분리한다.
- Empty events/markers, `points=[]`, no concern observed, missing anchor, expired snapshot detail이 health/recovery completion을 단정하지 않는다.
- UI가 lifecycle/rule/p95/p99/endpoint priority/event/marker/recovery helper를 만들지 않는다.
- Browser token persistence, URL token parsing, frontend build stack이 추가되지 않는다.

Suggested commands:

```bash
./gradlew :observability-portal:test --tests '*ApplicationDashboardUiContract*'
./gradlew :observability-portal:test --tests '*InstanceEvidenceUiContract*'
./gradlew :observability-portal:test --tests '*InstanceSnapshotTrendUiContract*'
./gradlew :observability-portal:test --tests '*RecentHistoryUiContract*'
./gradlew :observability-portal:test --tests '*DashboardReadModelService*'
./gradlew :observability-portal:test --tests '*ProjectApplicationNavigationService*'
./gradlew :observability-portal:test --tests '*OperationalEvent*'
./gradlew :observability-portal:test --tests '*DashboardSnapshot*'
./gradlew :observability-portal:test --tests '*IngestAcceptanceService*'
./gradlew :observability-portal:test --tests '*MetricBucketRepositoryIntegration*'
./gradlew :observability-spring-boot-starter:test --tests '*StarterNonBlockingIngest*'
./gradlew :observability-spring-boot-starter:test --tests '*StarterHeartbeat*'
./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest
./gradlew :observability-portal:test
./gradlew :observability-spring-boot-starter:test
git diff --check
```

테스트 이름은 구현 시점의 실제 class 이름에 맞게 조정할 수 있다. 단, demo failure/recovery verification은 green path 분리, portal down fail-open, duplicate no-second-row, safe copy, source-axis separation, full UI flow, no UI recomputation, no token persistence를 반드시 검증해야 한다.

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- 2026-05-31: BMAD create-story workflow와 customization을 확인했다.
- 2026-05-31: `sprint-status.yaml`에서 Story 6.9가 next backlog이고 Story 6.8/6.10이 done임을 확인했다.
- 2026-05-31: Epic 6 context, product source-of-truth, API/schema/account-auth policy, read-model/state/starter-failure/operational-event contracts를 확인했다.
- 2026-05-31: Story 6.8 green path와 Story 6.10 membership boundary를 확인해 6.9 scope가 두 story를 다시 열지 않도록 분리했다.
- 2026-05-31: Epic 5/6 dashboard/read-model/history/snapshot/recovery 관련 story와 current static dashboard/test patterns를 확인했다.
- 2026-05-31: Story 6.9 create-story 산출물을 `planning-artifacts/stories/6-9-failure-recovery-path-demo-hardening.md`에 생성했다.
- 2026-05-31: Phase 1 backend/read-model/fixture guard 범위로 Story 6.9 status를 `in-progress`로 전환했다.
- 2026-05-31: Dashboard/navigation read model, duplicate ingest, repository idempotency, starter fail-open/heartbeat focused guard를 보강했다.
- 2026-05-31: Phase 1 focused portal/starter tests와 `git diff --check`를 통과했다.
- 2026-06-01: Phase 2 Application Dashboard + Instance Evidence UI hardening 범위로 static dashboard runtime과 Node VM UI contract test를 확인했다.
- 2026-06-01: Dashboard failure/recovery states의 `metric_data_idle`, `telemetry_unreachable`, `observing_recovery` guidance와 metric/starter axis 분리 guard를 보강했다.
- 2026-06-01: Instance Evidence stale/down freshness suppression fixture에서 accepted bucket axis, starter heartbeat axis, `stateImpact=none`, `application_freshness_not_current` guard를 보강했다.
- 2026-06-01: Phase 3 Instance Snapshot Trend + Snapshot/History hardening 범위로 stored snapshot/read model projection, empty copy, event/marker/detail safe copy guard를 확인했다.
- 2026-06-01: Phase 3 focused UI/backend tests를 실행했고, 병렬 Gradle run의 test-results collision은 단독 재실행으로 성공 확인했다.
- 2026-06-01: Phase 4 regression sweep에서 필수 문맥 파일, Story 6.8 green path boundary, Story 6.10 membership fail-closed boundary를 재확인했다.
- 2026-06-01: Phase 4 focused portal sweep, starter focused rerun, full portal/starter rerun, static asset token/frontend/raw explorer search, `git diff --check`가 통과했다.
- 2026-06-01: Code review P2 finding인 `metric_data_idle` UI guidance 과장 가능성을 해결 상태로 확인했고, idle traffic copy가 요청 없음, bucket sample 부족, 다음 accepted bucket 확인 중심으로 유지되는지 focused tests로 재확인했다.

### Completion Notes List

- Create-story 단계에서는 production/test code를 수정하지 않았다.
- Story 6.9의 scope를 failure/recovery demo hardening으로 한정하고 Story 6.8 green path fixture와 Story 6.10 account-project membership boundary를 명시적으로 분리했다.
- Portal down, duplicate ingest, stale/down candidate, telemetry unreachable, degraded/recovery observed scenario별 safe copy와 fixture/test guard를 acceptance criteria와 tasks에 반영했다.
- UI-side lifecycle/rule/p95/p99/endpoint priority/operational event/marker/recovery 재계산 금지, raw explorer 금지, token persistence 금지, frontend build stack 금지를 반복 guard로 남겼다.
- Phase 1 구현에서는 UI hardening 없이 backend/read-model/fixture guard만 수정했다.
- Story 6.9 failure/recovery helper와 scenario 이름을 green path fixture와 분리했고, telemetry unreachable/recovery observed copy가 운영 결론을 단정하지 않도록 보강했다.
- Duplicate ingest는 conflict/no-second-row/no-overwrite 경계를 유지하며 dashboard snapshot/read model refresh나 recovery 의미로 해석되지 않도록 guard를 추가했다.
- Story 6.10 account-project membership fail-closed regression은 `ProjectNavigationResourceAuthorizationTest` focused run으로 유지 확인했다.
- Phase 2에서는 `app.js`에 failure/recovery zeroInsight 보조 문구와 metric state source copy를 추가했고, UI가 새 lifecycle/recovery/rule/percentile/endpoint 판단 helper를 만들지 않는 static guard를 유지했다.
- Application Dashboard UI contract는 stale/down, telemetry unreachable, observing recovery, degraded fixture에서 metric state strip과 starter connection strip이 별도 영역/source/copy로 렌더링되는지 검증한다.
- Instance Evidence UI contract는 stale/down accepted bucket freshness 상황에서 endpoint evidence를 `suppressed/application_freshness_not_current`로 표시하고 current concern처럼 endpoint row를 되살리지 않는지 검증한다.
- Phase 3에서는 Instance Snapshot Trend가 stale/down/degraded/recovery stored point와 `points=[]`/display range empty/no concern observed copy를 stored projection absence로만 표시하고 current instance state, event, recovery semantics로 재분류하지 않도록 보강했다.
- Snapshot/History는 `stale_entered`, `down_entered`, `degraded_entered`, `degraded_resolved`, `recovery_observed` event와 marker/detail copy를 server-provided/stored field로 표시하고 recovery observed/resolved/empty/missing/expired state가 완료나 정상 단정으로 읽히지 않도록 guard했다.
- Phase 4에서는 Story 6.9 AC 전체를 최종 점검하고 Story 6.8 green path, Story 6.10 membership fail-closed, no token persistence, no URL token parsing, no raw explorer, no frontend stack, no UI-side recomputation guard가 유지됨을 확인했다.
- Phase 4 검증은 focused portal/starter tests와 full portal/starter tests를 실제 재실행했고, `git diff --check`도 통과했다.
- Code review P2 finding을 해결했다. `metric_data_idle` guidance는 stale/down 단정처럼 읽히지 않도록 요청 없음, bucket sample 부족, 다음 accepted bucket 확인 중심의 중립 copy로 유지하며, `ApplicationDashboardUiContractTest`는 fresh current accepted bucket + idle traffic 상태에서 forbidden stale/down copy가 나오지 않는 guard를 포함한다.
- 이번 closeout에서는 full suite를 새로 실행하지 않고 focused tests만 재확인했다: `./gradlew :observability-portal:test --tests '*ApplicationDashboardUiContract*'`, `./gradlew :observability-portal:test --tests '*DashboardReadModelService*'`, `git diff --check`.

### File List

- `planning-artifacts/stories/6-9-failure-recovery-path-demo-hardening.md`
- `implementation-artifacts/sprint-status.yaml`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/DashboardReadModelService.java`
- `observability-portal/src/main/resources/static/dashboard/app.js`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ApplicationDashboardUiContractTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/InstanceEvidenceUiContractTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/InstanceSnapshotTrendUiContractTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/RecentHistoryUiContractTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/service/DashboardReadModelServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/service/ProjectApplicationNavigationServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/history/service/OperationalEventHistoryProjectorTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotMarkerServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotDetailServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/service/IngestAcceptanceServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/bucket/repository/MetricBucketRepositoryIntegrationTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/service/StarterNonBlockingIngestTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/service/StarterHeartbeatServiceTest.java`

### Change Log

- 2026-05-31: Story 6.9 Failure/recovery path demo hardening create-story 산출물을 생성하고 ready-for-dev 상태로 전환했다.
- 2026-05-31: Story 6.9를 in-progress로 전환하고 Phase 1 backend/read-model/fixture guard를 구현했다.
- 2026-06-01: Phase 2 Application Dashboard + Instance Evidence UI hardening을 추가했다.
- 2026-06-01: Phase 3 Instance Snapshot Trend + Snapshot/History hardening을 추가했다.
- 2026-06-01: Phase 4 regression sweep and story closeout을 완료하고 Story 6.9를 review 상태로 전환했다.
- 2026-06-01: Code review P2 finding resolved - `metric_data_idle` UI guidance를 중립 copy로 유지하고 focused tests로 재확인한 뒤 Story 6.9를 done 상태로 전환했다.
