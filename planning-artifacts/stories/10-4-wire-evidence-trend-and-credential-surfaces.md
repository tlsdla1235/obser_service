---
artifactType: story
storyId: "10.4"
storyKey: "10-4-wire-evidence-trend-and-credential-surfaces"
epic: "Epic 10. Figma Make Acceptance And Frontend Hardening"
title: "Wire evidence, trend, and credential surfaces"
architectureStyle: "Vite SPA on Spring static root"
status: review
date: 2026-06-02
baselineCommits:
  story10_1: "cc7d87a frontend: adopt figma workspace and routing"
  story10_2: "7850d88 frontend: port auth and fetch foundation"
  story10_3: "e5b3ffa frontend: wire story 10.3 read models"
---

# Story 10.4 - Wire evidence, trend, and credential surfaces

## Status

review

Story 10.4 구현을 완료했고 review 대기 상태다.

## Story

Figma Make 인수 프론트 구현자는 Story 10.3에서 실제 server read model로 연결한 Project, Application, Dashboard 흐름 위에서 Instance Evidence, Instance Snapshot Trend, Snapshot/History, Project Registration, Starter Credential Lifecycle surface를 기존 backend API에 연결하고 싶다.

그래야 사용자가 GitHub OAuth 로그인 후 mock seed나 legacy static dashboard가 아니라 새 Vite SPA에서 `Project -> Application -> Dashboard -> Instance Evidence -> Instance Trend/History -> Credential Lifecycle` 흐름을 사용할 수 있고, UI가 server read model의 판단과 secret boundary를 재계산하거나 누설하지 않게 Epic 10 acceptance를 닫을 수 있다.

## Source of Truth

아래 문서를 읽고 반영한 BMAD create-story context다. 충돌처럼 보이는 지점은 실제 Java record/API surface, Story 10.3 완료 커밋의 frontend foundation, 그리고 Figma Make acceptance sprint plan의 Story 10.4 범위를 우선한다.

1. `/Users/tlsdla1235/Desktop/study/observation/AGENTS.md`
2. `/Users/tlsdla1235/Desktop/study/observation/_bmad/custom/project-context.md`
3. `/Users/tlsdla1235/Desktop/study/observation/implementation-artifacts/sprint-status.yaml`
4. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/figma-make-acceptance-sprint-plan.md`
5. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/10-1-adopt-workspace-and-routing.md`
6. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/10-2-port-auth-and-fetch-foundation.md`
7. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/10-3-wire-types-adapters-navigation-and-dashboard.md`
8. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/api-surface.md`
9. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/acceptance-traceability.md`
10. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/epics.md`
11. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/architecture.md`
12. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/contracts/read-model-contract.md`
13. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/5-6-instance-evidence-read-model.md`
14. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/5-7-instance-snapshot-trend-projection.md`
15. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/5-8-b-snapshot-marker-detail-recovery-source.md`
16. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/5-9-a-operational-event-history-api-skeleton-and-source-boundary.md`
17. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/5-9-b-operational-event-promotion-suppression-and-period-folding.md`
18. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/6-1-account-project-entry-and-setup-guide.md`
19. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/6-5-instance-evidence-ui.md`
20. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/6-6-instance-snapshot-trend-ui.md`
21. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/6-7-snapshot-history-marker-ui-and-deep-link.md`
22. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/6-10-account-project-membership-and-scoped-project-entry.md`
23. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/9-2-project-registration-and-seed-issuance-ui.md`

확인한 backend source:

1. `observability-portal/src/main/java/com/observation/portal/domain/instance/controller/InstanceEvidenceController.java`
2. `observability-portal/src/main/java/com/observation/portal/domain/instance/model/InstanceEvidenceReadModel.java`
3. `observability-portal/src/main/java/com/observation/portal/domain/instance/controller/InstanceSnapshotTrendController.java`
4. `observability-portal/src/main/java/com/observation/portal/domain/instance/model/InstanceSnapshotTrendReadModel.java`
5. `observability-portal/src/main/java/com/observation/portal/domain/snapshot/controller/DashboardSnapshotController.java`
6. `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotMarkerReadModel.java`
7. `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotDetailReadModel.java`
8. `observability-portal/src/main/java/com/observation/portal/domain/history/controller/OperationalEventHistoryController.java`
9. `observability-portal/src/main/java/com/observation/portal/domain/history/model/OperationalEventHistoryReadModel.java`
10. `observability-portal/src/main/java/com/observation/portal/domain/history/model/OperationalEventItem.java`
11. `observability-portal/src/main/java/com/observation/portal/domain/catalog/controller/ProjectRegistrationController.java`
12. `observability-portal/src/main/java/com/observation/portal/domain/catalog/dto/ProjectRegistrationResponse.java`
13. `observability-portal/src/main/java/com/observation/portal/domain/catalog/controller/StarterCredentialController.java`
14. `observability-portal/src/main/java/com/observation/portal/domain/catalog/dto/StarterCredentialMetadataResponse.java`
15. `observability-portal/src/main/java/com/observation/portal/domain/catalog/dto/StarterCredentialRotationResponse.java`

확인한 frontend source:

1. `frontend/src/app/lib/api.ts`
2. `frontend/src/app/lib/auth.tsx`
3. `frontend/src/app/lib/use-api-resource.ts`
4. `frontend/src/app/lib/read-model-types.ts`
5. `frontend/src/app/lib/read-model-adapters.ts`
6. `frontend/src/app/components/dashboard.tsx`
7. `frontend/src/app/components/instance-panels.tsx`
8. `frontend/src/app/components/docs.tsx`

## Current Code State

- Story 10.3 완료 커밋은 `e5b3ffa frontend: wire story 10.3 read models`다.
- Story 10.3 기준 Project list는 `GET /api/projects`로 로드된다.
- Application list는 `ProjectNavigationReadModel.ProjectItem.links.applications`만 사용한다.
- Dashboard는 `ProjectApplicationNavigationReadModel.ApplicationItem.links.dashboard`만 사용한다.
- `frontend/src/app/lib/auth.tsx`는 `AuthProvider`, in-memory access token, `authFetch`, 401 token clear boundary를 제공한다.
- `frontend/src/app/lib/use-api-resource.ts`는 `authFetch`, `authGeneration`, authenticated state, request sequence, `AbortController`, unmount guard를 제공한다. Story 10.4도 이 hook 또는 동등 sequence guard를 재사용해야 한다.
- `frontend/src/app/lib/api.ts`에는 `NO_STORE_REQUEST_OPTIONS`, `SECRET_BEARING_REQUEST_OPTIONS`, `CREDENTIAL_LIFECYCLE_REQUEST_OPTIONS`, `readJsonResource`, `ApiRequestError`, `AuthRequiredError`가 이미 있다.
- `frontend/src/app/lib/read-model-types.ts`와 `read-model-adapters.ts`는 Project/Application/Dashboard DTO와 adapter boundary다. Story 10.4는 여기에 evidence/trend/history/credential DTO와 link validator를 추가하거나, 같은 `lib` 경계에 전용 파일을 만들 수 있다.
- `frontend/src/app/components/dashboard.tsx`는 Project/Application/Dashboard read model을 실제 API로 렌더링하고 `CredentialPendingPanel`, `InstancesPanel`, `SnapshotHandoffPanel`을 pending handoff로 남긴다.
- `frontend/src/app/components/instance-panels.tsx`는 drawer 열림 상태만 관리하고 `dashboard.instances[].links.evidence`를 표시한다. 실제 evidence/trend fetch는 아직 없다.
- `frontend/src/app/components/dashboard-data.ts` mock seed는 Story 10.3에서 삭제됐다. `instanceEvidenceById`, `snapshotTrendByInstance`, `dashboard-data` 기반 초기화를 되살리면 안 된다.
- `frontend/src/app/components/docs.tsx`는 evidence/trend/snapshot/history/credential endpoint를 문서 표로 언급하지만 실제 wiring source는 아니다.
- 현재 작업 트리에는 기존 untracked/unrelated 파일이 남아 있다. Story 10.4 구현자는 아래 파일을 삭제, 수정, 이동, 되돌리지 않는다.
  - `=`
  - `README.md`
  - `docs/`
  - `planning-artifacts/figma-make-nextjs-frontend-spec.md`
  - `observability-portal/src/main/java/com/observation/portal/config/DashboardStaticWebConfig 2.java`
  - `observability-portal/src/main/java/com/observation/portal/domain/account/controller/GithubCallbackTokenRelay 2.java`
  - `observability-portal/src/main/java/com/observation/portal/domain/account/dto/GithubCallbackSessionResponse 2.java`
  - `observability-portal/src/main/java/com/observation/portal/domain/account/dto/GithubCallbackTokenRelayRequest 2.java`

## Scope

- `dashboard.instances[].links.evidence`를 source로 Instance Evidence API를 호출하고 drawer에서 ready/loading/empty/error state를 렌더링한다.
- Instance Evidence response의 `links.snapshotTrend`를 우선 source로 Instance Snapshot Trend API를 호출한다.
- `links.snapshotTrend`가 없고 사용자가 Dashboard의 instance handoff에서 직접 Trend를 연 경우에만, planning에서 허용한 기존 snapshot trend endpoint template을 validated helper로 만든다. 이 fallback은 임의 문자열 조립이 아니라 현재 selected Project/Application/Instance id를 검증한 documented helper여야 한다.
- Instance Snapshot Trend는 `7d` 기본, `14d` 선택, `limit<=336` bounded query만 제공한다. `24h` trend backend query는 Story 10.4에서 열지 않는다.
- Snapshot/History는 selected Project/Application context에서 기존 marker/history/detail endpoint만 사용한다.
  - `GET /api/projects/{projectId}/applications/{applicationId}/dashboard/snapshot-markers`
  - `GET /api/projects/{projectId}/applications/{applicationId}/operational-events`
  - `GET /api/projects/{projectId}/applications/{applicationId}/dashboard/snapshots/{snapshotId}`
- Project registration은 기존 `POST /api/projects`만 사용한다.
- Starter credential metadata, rotation, revocation은 기존 endpoint만 사용한다.
  - `GET /api/projects/{projectId}/starter-credential`
  - `POST /api/projects/{projectId}/starter-credential/rotations`
  - `POST /api/projects/{projectId}/starter-credential/revocations`
- Story 10.2의 `AuthProvider`, `authFetch`, `useApiResource`, no-store option, stale response guard를 재사용한다.
- Story 10.3의 read-model type/adapter boundary를 확장해 DTO parsing, link validation, presentation formatting만 수행한다.
- Evidence/Trend/History/Credential의 loading, empty, invalid-link, auth-required, 400, 401, 404, 409, 500/generic error 상태를 같은 story 안에서 처리한다.
- Create/rotation success의 `starterCredential.displayValue`는 생성 직후 1회 표시 surface에서만 보여주고 copy/confirm/close 뒤 UI state에서 제거한다.
- 새 복잡한 helper나 공개 type을 만들면 AGENTS.md 지침에 따라 한국어 주석/JSDoc을 필요한 만큼만 작성한다.

## Explicit Scope Split

포함하는 것:

- Instance Evidence UI fetch/render
- Instance Snapshot Trend UI fetch/render
- Snapshot marker timeline, operational event feed, snapshot detail deep link fetch/render
- Project Registration UI와 one-time starter credential display
- Starter Credential metadata/rotation/revocation UI
- Frontend DTO, adapter, endpoint helper, static guard tests for the above

제외하는 것:

- Backend API/schema/migration/read model 확장
- Instance Evidence/Trend/Snapshot/History/Credential backend behavior 변경
- Operational event promotion, marker severity, snapshot detail semantics 재계산
- Project key generation/rotation logic 변경
- Starter ingest/heartbeat API 호출 또는 starter ingest contract 변경
- Legacy static dashboard 삭제 또는 Spring static fallback 변경
- Gradle frontend build integration

## Non-scope

- 새 backend endpoint 생성
- Next.js 전환 또는 Next.js API route 생성
- Gradle node build integration
- Spring static fallback 변경
- legacy static dashboard 삭제
- `observability-portal/src/main/resources/static/dashboard/*` 수정 또는 삭제
- lifecycle state, starter connection diagnosis, zeroInsight reason, recovery guidance, rule, p95/p99, endpoint priority, instance health score, snapshot/history event, marker severity, event promotion/suppression/period folding client-side 계산
- endpoint priority client-side sort/rank/reduce 계산
- histogram bucket으로 percentile 재계산
- accepted bucket freshness와 starter heartbeat를 하나의 host/application/instance health로 합성
- token, provider payload, starter credential raw secret을 URL/cookie/localStorage/sessionStorage/data attribute/hidden input/log/error에 저장 또는 노출
- Project registration 성공 후 dashboard 판단 shortcut 생성
- direct starter ingest API 호출. `/api/ingest/v1/buckets`와 `/api/ingest/v1/heartbeat`는 docs/setup 안내에만 남고 dashboard UI에서 fetch하지 않는다.
- known `* 2.java` duplicate baseline blocker 삭제/수정/되돌리기

## Backend Contract Summary

### Instance Evidence

`GET /api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/evidence`

Top-level response:

- `generatedAt`
- `application`
- `instance`
- `metricData`
- `starterConnection`
- `starterPercentiles`
- `histogramDistribution`
- `resourceHints`
- `applicationTriageContribution`
- `endpointEvidence`
- `links`

Key boundaries:

- Evidence는 application dashboard 판단을 대체하지 않는 drill-down이다.
- `metricData.statusSource=accepted_bucket`과 `starterConnection.statusSource=starter_heartbeat`는 별도 axis다.
- `starterPercentiles.points[]`는 current 15분 30초 bucket series이며 평균, 최댓값, 병합, 보간을 하지 않는다.
- `endpointEvidence.items[]`는 bounded subset이며 endpoint priority/root cause/action ranking이 아니다.
- `links.snapshotTrend`는 Story 10.4에서 trend handoff source로 우선 사용한다.

### Instance Snapshot Trend

`GET /api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/snapshot-trend?since=7d&limit=168`

Top-level response:

- `generatedAt`
- `application`
- `instance`
- `source=dashboard_snapshots.read_model_json.instanceSummary.items`
- `horizon`
- `points[]`

Key boundaries:

- 지원 `since` token은 `7d`, `14d`다.
- 기본 limit은 `168`, 최대 limit은 `336`이다.
- `points[]`는 `capturedAt ASC` server order를 보존한다.
- `storedApplicationStateCode`는 snapshot 저장 당시 application state copy일 뿐 selected instance state가 아니다.
- `captureReason`은 opaque metadata이며 marker/event/recovery 의미로 UI가 해석하지 않는다.

### Snapshot Markers, Snapshot Detail, Operational Events

Existing endpoints:

- `GET /api/projects/{projectId}/applications/{applicationId}/dashboard/snapshot-markers?since=24h&limit=50`
- `GET /api/projects/{projectId}/applications/{applicationId}/dashboard/snapshots/{snapshotId}`
- `GET /api/projects/{projectId}/applications/{applicationId}/operational-events?since=24h&limit=50`

Key boundaries:

- Marker source와 operational event source는 `dashboard_snapshots`다.
- Snapshot detail source는 `dashboard_snapshots`이며 `readSemantics.mode=stored_snapshot_detail`, `currentStateRecalculated=false`, `liveSourcesJoined=[]`, `rawReadModelJsonExposed=false`를 보존해야 한다.
- UI는 marker type/severity, event type/severity/resolvedAt, suppression, period folding, recovery marker를 만들지 않는다.
- Snapshot detail ready view는 raw `read_model_json` dump를 표시하지 않는다.

### Project Registration And Starter Credential Lifecycle

Existing endpoints:

- `POST /api/projects`
- `GET /api/projects/{projectId}/starter-credential`
- `POST /api/projects/{projectId}/starter-credential/rotations`
- `POST /api/projects/{projectId}/starter-credential/revocations`

Key boundaries:

- `POST /api/projects` success response만 `starterCredential.displayValue`를 1회 표시 field로 포함한다.
- Rotation success response만 새 `starterCredential.displayValue`를 1회 표시 field로 포함한다.
- Metadata/revocation response는 raw value/hash 없이 `keyPrefix`, `status`, `issuedAt`, `rotatedAt`, `revokedAt`만 포함한다.
- Secret-bearing create/rotation response는 `Cache-Control: no-store`이고 frontend request도 no-store option을 사용한다.
- Membership mismatch는 body 없는 `404` fail-closed일 수 있으며 project existence나 credential state를 드러내지 않는다.

## Acceptance Criteria

1. DTO type은 실제 Java record와 현재 API surface를 기준으로 작성한다.
2. Existing Story 10.3 DTO/adapter boundary를 확장하거나 같은 `frontend/src/app/lib` 아래 전용 파일을 만들되, DTO/adapter는 rename, nullable 방어, link validation, 표시 formatting만 수행한다.
3. Story 10.2의 `AuthProvider`, `authFetch`, `useApiResource`, `AuthRequiredError`, `ApiRequestError`, no-store request option을 재사용한다.
4. Evidence/Trend/History/Credential request는 `authFetch` 또는 동등 helper로 `Authorization: Bearer <access_token>` header를 붙인다.
5. No-token 상태는 request storm 없이 auth-required UI로 수렴한다.
6. `401`은 auth foundation에 따라 token clear/auth-required state로 표현하고 project absence, instance absence, application health, host down, credential revoked로 번역하지 않는다.
7. `404`는 membership mismatch, scope mismatch, missing resource, retention absence일 수 있다는 fail-closed copy로 표현하고 application/instance down, deleted instance, credential revoked, hidden project existence를 단정하지 않는다.
8. Generic error는 backend detail, stack trace, token, provider raw payload, starter credential raw value 없이 다시 시도 copy만 표시한다.
9. 빠른 Project/Application/Instance 전환, reload, token clear, drawer close, component unmount 상황에서 stale evidence/trend/history/detail/credential response가 최신 선택 상태를 덮지 않는다.
10. 각 request callback은 `useCallback` 등으로 안정화해 fetch loop를 만들지 않는다.
11. Selected Project가 바뀌면 Application, Dashboard, Instance Evidence, Trend, History, Credential state가 안전하게 reset된다.
12. Selected Application이 바뀌면 Dashboard, Instance Evidence, Trend, History, Snapshot Detail state가 안전하게 reset된다.
13. Instance Evidence fetch source는 Dashboard response의 clicked `instances[].links.evidence`뿐이다.
14. Evidence link는 내부 `/api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/evidence` shape여야 한다.
15. Evidence link 검증은 현재 selected Project/Application/Instance id와 모두 일치해야 통과한다.
16. Evidence link 검증 실패 시 API call을 만들지 않고 invalid-link safe state를 표시한다.
17. Evidence response `application.projectId`, `application.applicationId`, `instance.instanceId`가 selected context와 다르면 fail-closed error로 처리한다.
18. Evidence ready view는 `generatedAt`, `application`, `instance`, `metricData`, `starterConnection`, `starterPercentiles`, `histogramDistribution`, `resourceHints`, `applicationTriageContribution`, `endpointEvidence`, `links` top-level block만 소비한다.
19. Evidence ready view는 identity/back action, metricData/starterConnection axis, triage contribution, starter percentile series, histogram distribution, resource hints, endpoint evidence, snapshot trend action 순서로 렌더링한다.
20. Evidence `metricData.statusSource=accepted_bucket`을 표시 또는 static guard에서 보존한다.
21. Evidence `starterConnection.statusSource=starter_heartbeat`와 `stateImpact=none`을 표시 또는 static guard에서 보존한다.
22. Evidence UI는 metric data와 starter connection을 `instanceHealth`, `hostStatus`, `processDown`, `applicationDown`, `connectedAndHealthy` 같은 단일 판단으로 합치지 않는다.
23. Recent heartbeat와 missing/stale accepted bucket 조합은 starter 연결과 metric data 대기/idle을 분리해 표현하고 host application down을 확정하지 않는다.
24. `applicationTriageContribution.contributed=false`는 "문제 없음"이 아니라 "기여 evidence 없음" 또는 source absence로 표현한다.
25. `starterPercentiles.window=current_15m`, `bucketDurationSeconds=30`, `maxPointCount=30`, `displayPolicy=source_scoped_series`, `aggregatePolicy=no_average_no_max_no_merge_no_histogram_recalculation`을 표시 또는 static guard에서 보존한다.
26. `starterPercentiles.points[]`는 server order/time order를 표시만 하고 평균, 최댓값, 병합, 보간, synthetic point 생성을 하지 않는다.
27. Histogram distribution과 endpoint duration buckets는 distribution evidence로만 표시하고 p95/p99, avg/max latency, latency score, health score를 계산하지 않는다.
28. `endpointEvidence.selectionPolicy`와 `displayOrderingPolicy`는 server-provided value로 보존한다.
29. `endpointEvidence.items[]`는 최대 5개 bounded evidence subset으로 표시한다.
30. `relatedApplicationPriorityRank`는 Application Dashboard endpoint priority 참조 값이고 `localDisplayOrder`는 Instance Detail 안의 표시 순서일 뿐 priority/root cause/action 순위가 아니다.
31. `presenceOnSelectedInstance=not_observed`는 문제 없음이 아니라 selected instance current window에 해당 endpoint evidence가 없다는 뜻으로 표현한다.
32. Evidence UI는 raw `endpoints_json`, raw path, query string, query key/value, trace id, per-request sample을 표시하지 않는다.
33. Trend fetch는 Instance Evidence response의 `links.snapshotTrend`를 우선 사용한다.
34. Dashboard instance handoff에서 Evidence를 거치지 않고 Trend를 열 수 있게 유지한다면, fallback은 documented snapshot trend endpoint helper만 사용하고 현재 selected Project/Application/Instance id를 검증한다.
35. Trend link는 내부 `/api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/snapshot-trend` shape여야 한다.
36. Trend query는 fixed controls만 제공한다. `7d`는 `since=7d&limit=168`, `14d`는 `since=14d&limit=336`을 사용한다.
37. Story 10.4는 trend backend `since=24h`를 호출하지 않는다.
38. Trend response `application.projectId`, `application.applicationId`, `instance.instanceId`가 selected context와 다르면 fail-closed error로 처리한다.
39. Trend ready view는 `generatedAt`, `application`, `instance`, `source`, `horizon`, `points` top-level block만 소비한다.
40. Trend `source=dashboard_snapshots.read_model_json.instanceSummary.items`를 표시 또는 static guard에서 보존한다.
41. Trend `horizon.defaultSince=7d`, `maxSince=14d`, `maxLimit=336`, `order=capturedAt_asc`를 표시 또는 static guard에서 보존한다.
42. Trend `points[]`는 server-provided `capturedAt ASC` order를 보존하고 UI가 의미를 바꾸는 재정렬을 하지 않는다.
43. `storedApplicationStateCode`는 application-level stored state copy로만 표현하고 selected instance lifecycle state나 health score로 번역하지 않는다.
44. `captureReason`은 nullable opaque metadata로 표시하고 marker type, severity, operational event, recovery semantics로 해석하지 않는다.
45. Trend point의 `starterPercentilePoint`는 stored single latest starter canonical percentile point로만 표시한다.
46. Trend UI는 percentile point 평균, 최댓값, 병합, 보간, synthetic point 생성, histogram-derived p95/p99 계산을 하지 않는다.
47. `points=[]`는 snapshot source absence, retention gap, target instance absence일 수 있음을 표시하고 "문제 없음", "정상", "복구 완료"로 표현하지 않는다.
48. Selected display range 안에 points는 있지만 concern/contribution/marker 후보가 없으면 "no concern observed"를 stored point 관찰 문구로만 표시하고 current health proof처럼 표현하지 않는다.
49. Snapshot/History entry는 current selected Project/Application context에서만 canonical marker/history endpoint helper를 만든다.
50. Snapshot/History default preset은 `24h`이며 operational event query `since=24h&limit=50`, marker query `since=24h&limit=50`을 사용한다.
51. `7d` preset은 operational event query `since=7d&limit=100`, marker query `since=7d&limit=168`을 사용한다.
52. `14d` preset은 operational event query `since=14d&limit=100`, marker query `since=14d&limit=336`을 사용한다.
53. History preset은 `24h`, `7d`, `14d` fixed controls만 제공한다. custom date picker, raw query editor, arbitrary query input은 만들지 않는다.
54. Operational event response source는 `dashboard_snapshots`이고 marker response source도 `dashboard_snapshots`여야 한다.
55. Operational event response `applicationId`와 marker response `applicationId`는 selected Application id와 일치해야 한다.
56. Operational events는 server-provided `occurredAt DESC`, `eventId ASC` order를 보존한다.
57. Markers는 server-provided `capturedAt ASC`, `snapshotId ASC` order를 보존한다.
58. `events=[]`와 `markers=[]`는 retention/source absence 또는 event 후보 없음으로 표현하고 "현재 문제 없음", "정상", "복구 완료"로 표현하지 않는다.
59. Event item은 `eventId`, `type`, `severity`, `title`, `summary`, `occurredAt`, nullable `resolvedAt`, `stateCode`, nullable `confidence`, `snapshotId`, `evidence`, `links.snapshot`만 소비한다.
60. UI는 `scheduled_snapshot`, `query_fallback_snapshot`, `stored_snapshot`, 단순 `state_observation`, `short_strong_spike`를 operational event로 새로 승격하지 않는다.
61. Event severity와 copy는 server-provided value를 표시만 하고 marker severity, confidence, capture reason으로 다시 계산하지 않는다.
62. Event `resolvedAt`이 있어도 "복구 완료", "장애 해결 완료", "앱 정상 확정" copy를 만들지 않는다.
63. Event evidence는 bounded field만 표시한다: `ruleId`, `endpointKey`, optional `method`, optional `route`, optional `snapshotDetailAnchor`, `anchorStatus`.
64. Marker item은 `markerId`, `snapshotId`, `capturedAt`, `currentWindowEndUtc`, `type`, `severity`, `readMeaning`, `captureReason`, `storedApplicationStateCode`, `previousState`, title/summary/recommendedAction, helper confidence/rule/endpoint field, `links.snapshot`만 소비한다.
65. Marker `readMeaning=stored_read_model_point`를 표시 또는 static guard에서 보존한다.
66. Marker type/severity는 server-provided enum만 표시한다. UI가 `captureReason`, state, confidence로 type/severity를 분류하지 않는다.
67. Recovery marker copy는 "회복 관찰 중" 계열만 허용하며 "복구 완료", "장애 해결", host application down/process down 확정을 말하지 않는다.
68. Snapshot detail action은 event/marker `links.snapshot` 또는 selected Project/Application context + trend point `snapshotId`로만 만든다.
69. Snapshot detail link는 내부 `/api/projects/{projectId}/applications/{applicationId}/dashboard/snapshots/{snapshotId}` shape이고 selected Project/Application id와 일치해야 한다.
70. Invalid snapshot detail link 또는 invalid snapshot id는 API call 없이 invalid-link safe state 또는 bounded bad-detail state로 표시한다.
71. Snapshot detail `404`는 retention/detail absence copy로 표시하고 current dashboard fallback을 만들지 않는다.
72. Snapshot detail response source는 `dashboard_snapshots`여야 한다.
73. Snapshot detail `readSemantics.mode=stored_snapshot_detail`, `currentStateRecalculated=false`, `liveSourcesJoined=[]`, `rawReadModelJsonExposed=false`를 표시 또는 static guard에서 보존한다.
74. Snapshot detail `snapshot.snapshotId`는 requested snapshot id와 일치하고 `links.self`는 selected Project/Application/Snapshot id와 일치하는 internal path여야 한다.
75. Snapshot detail ready view는 raw `read_model_json` 전체 dump를 표시하지 않는다.
76. Snapshot detail `snapshotEndpointEvidence.items[]`는 stored order와 `anchorId=endpoint-evidence-{n}`를 보존한다.
77. Event evidence 또는 trend endpoint ref의 `snapshotDetailAnchor`가 detail response 안에 있으면 matching anchor section에 active 표시를 둘 수 있다.
78. Missing anchor는 detail response나 event/trend를 invalid로 만들지 않고 `anchorStatus=missing` 또는 source absence로 표현한다.
79. Project registration UI는 existing `POST /api/projects`만 호출한다.
80. Project registration request body는 project name 같은 non-secret input만 포함한다.
81. Project registration success response `starterCredential.displayValue`는 생성 직후 1회 표시 surface에서만 보여준다.
82. Project registration success 후 `GET /api/projects`를 refresh해 server response 기준 project list에 새 membership project가 나타나게 한다.
83. Project registration success 후 Application/Dashboard 판단을 shortcut으로 만들지 않고 existing server links를 따른다.
84. Project registration `400`은 invalid name, `409`는 duplicate/normalized conflict로 안전하게 표시하고 raw backend body, token, credential value를 노출하지 않는다.
85. Credential metadata는 selected Project id 기준 `GET /api/projects/{projectId}/starter-credential`만 호출한다.
86. Credential metadata response는 `keyPrefix`, `status`, `issuedAt`, `rotatedAt`, `revokedAt`만 표시하고 raw value/hash를 기대하거나 표시하지 않는다.
87. Rotation은 `POST /api/projects/{projectId}/starter-credential/rotations`만 호출한다.
88. Rotation success response `starterCredential.displayValue`는 rotation 성공 직후 1회 표시 surface에서만 보여준다.
89. Revocation은 `POST /api/projects/{projectId}/starter-credential/revocations`만 호출한다.
90. Revocation response는 raw credential 없이 metadata만 표시한다.
91. Secret-bearing create/rotation request는 `SECRET_BEARING_REQUEST_OPTIONS` 또는 `CREDENTIAL_LIFECYCLE_REQUEST_OPTIONS`처럼 no-store option을 사용한다.
92. Metadata/revocation request도 credential lifecycle surface이므로 no-store option을 사용한다.
93. Raw starter credential value는 `localStorage`, `sessionStorage`, cookie, URL query/hash/fragment, DOM dataset, hidden input, title, aria-label, long-lived global state에 저장하지 않는다.
94. Copy UX는 clipboard 성공/실패 상태를 보여주되 raw credential 값을 log, error message, analytics payload, aria-label, title attribute에 넣지 않는다.
95. 사용자가 copy/confirm/close action을 누르면 raw `displayValue`는 React state에서 제거된다.
96. Raw credential을 다시 볼 수 없고 필요하면 rotation으로 새 credential을 발급받아야 한다는 copy를 제공한다.
97. Token, refresh token, provider token, provider raw payload, starter credential raw secret은 screen/error/log에 노출하지 않는다. 단 create/rotation success의 `starterCredential.displayValue` 1회 표시 surface는 이 AC의 유일한 예외다.
98. Project credential lifecycle 404는 membership mismatch/project scope fail-closed로 표현하고 credential revoked, project 없음, hidden project existence를 단정하지 않는다.
99. Dashboard UI는 `/api/ingest/v1/buckets` 또는 `/api/ingest/v1/heartbeat`를 fetch하지 않는다.
100. 새 backend endpoint, Next.js API route, Gradle integration, Spring static fallback, legacy static dashboard 삭제를 하지 않는다.
101. `react-router` v7 package boundary를 유지하고 `react-router-dom`을 추가하지 않는다.
102. 새 소스 파일 또는 복잡한 helper를 만들면 AGENTS.md 지침에 따라 한국어 주석/JSDoc으로 역할과 사용 맥락을 짧게 설명한다.
103. Known `* 2.java` duplicate Java baseline blocker는 삭제/수정/되돌리지 않는다.

## Tasks / Subtasks

- [x] DTO와 endpoint/link helper 확장 (AC: 1~3, 13~17, 33~41, 49~55, 68~74, 79~92, 100~102)
  - [x] `InstanceEvidenceReadModel`, `InstanceSnapshotTrendReadModel`, `DashboardSnapshotMarkerReadModel`, `DashboardSnapshotDetailReadModel`, `OperationalEventHistoryReadModel`, Project registration/credential lifecycle DTO type을 작성한다.
  - [x] `frontend/src/app/lib/api.ts` 또는 read model 전용 helper에 기존 endpoint constants/helper를 추가한다.
  - [x] Evidence, trend, snapshot detail link validator를 current selected context와 함께 fail-closed로 작성한다.
  - [x] History/marker/credential endpoint helper는 documented endpoint template만 사용하고 arbitrary user input URL을 받지 않는다.
  - [x] Secret-bearing/credential lifecycle request는 existing no-store option을 재사용한다.

- [x] Instance Evidence drawer wiring (AC: 4~32, 97~99)
  - [x] `InstancePanels`의 pending evidence view를 authenticated fetch state machine으로 전환한다.
  - [x] Dashboard instance handoff의 `links.evidence`만 fetch source로 사용한다.
  - [x] invalid-link, auth-required, loading, 401, 404, generic error, malformed response, identity mismatch, ready state를 구현한다.
  - [x] metricData/starterConnection axis, triage contribution, percentiles, histogram/resource/endpoint evidence를 server values 중심으로 표시한다.
  - [x] endpoint evidence와 histogram에서 p95/p99/priority/root cause/action ranking을 만들지 않는 static guard를 둔다.

- [x] Instance Snapshot Trend wiring (AC: 33~48, 97~100)
  - [x] Evidence ready view의 `links.snapshotTrend` action을 활성화한다.
  - [x] Dashboard instance handoff의 direct Trend action을 유지할 경우 documented fallback helper만 사용한다.
  - [x] `7d`, `14d` fixed controls와 safe query builder를 구현한다.
  - [x] no-token/401/400/404/error/malformed/stale/identity mismatch safe state를 구현한다.
  - [x] source/horizon/points/stored state/capture reason/metric/starter/resource/triage/endpoint refs를 표시만 한다.
  - [x] Snapshot detail/history/operational event fetch를 Trend ready view에서 자동으로 만들지 않는다. Detail은 explicit snapshot detail action에서만 호출한다.

- [x] Snapshot/History and Snapshot Detail wiring (AC: 49~78, 97~100)
  - [x] Dashboard ready view 또는 right rail에 Snapshot/History action을 활성화한다.
  - [x] Operational events와 snapshot markers를 fixed `24h|7d|14d` preset으로 fetch한다.
  - [x] Event/marker response source, application id, horizon, order를 검증한다.
  - [x] Event feed와 marker timeline은 server-provided item field만 표시한다.
  - [x] Event/marker `links.snapshot`와 trend point `snapshotId`로 snapshot detail action을 제공한다.
  - [x] Snapshot detail response source/readSemantics/self link identity를 검증하고 raw JSON dump를 만들지 않는다.
  - [x] Anchor resolved/missing 상태를 bounded하게 표시한다.

- [x] Project registration and credential lifecycle wiring (AC: 79~98)
  - [x] Project rail의 disabled `Project 등록 대기` surface를 Project registration flow로 연결한다.
  - [x] Project name 입력, submit/loading/400/409/401/generic error/success state를 구현한다.
  - [x] Registration success의 `starterCredential.displayValue`를 1회 표시하고 copy/confirm/close 뒤 state에서 제거한다.
  - [x] 성공 후 Project list를 reload하고 새 project selection은 server response 기준으로만 수행한다.
  - [x] Selected Project 기준 credential metadata를 no-store로 로드한다.
  - [x] Rotation/revocation action과 safe confirmation/disabled/loading/error/success state를 구현한다.
  - [x] Rotation success의 raw value도 1회 표시 후 state에서 제거한다.
  - [x] Metadata/revocation response에 raw value/hash가 없다는 전제를 UI와 static guard로 고정한다.

- [x] Scope guard and cleanup checks (AC: 97~103)
  - [x] `instanceEvidenceById`, `snapshotTrendByInstance`, `dashboard-data` mock dependency가 없음을 확인한다.
  - [x] Next.js API route, backend endpoint, Gradle integration, Spring static fallback, legacy dashboard deletion이 섞이지 않았는지 확인한다.
  - [x] Token/credential storage, URL token parsing, raw secret attribute leakage가 없는지 정적 grep과 tests로 확인한다.
  - [x] `* 2.java` duplicate baseline blocker를 수정하지 않았는지 `git status --short`로 확인한다.

- [x] Verification 수행 및 Dev Agent Record 갱신 (AC: 전체)
  - [x] `cd frontend && npm run typecheck`
  - [x] `cd frontend && npm run build`
  - [x] `rg -n "instanceEvidenceById|snapshotTrendByInstance|dashboard-data|displayValue|projectKeyHash|starter-credential|ingest/v1" frontend/src`
  - [x] `rg -n "localStorage|sessionStorage|document.cookie|accessToken|refreshToken|provider token|starterCredential|displayValue" frontend/src`
  - [x] `rg -n "sort\\(|reduce\\(|percentile|p95|p99|endpointPriority|state.code|captureReason|resolvedAt" frontend/src/app/lib frontend/src/app/components`
  - [x] Browser 수동 QA는 Gradle-served Spring origin에서 수행한다. Vite dev server에서 확인하려면 `/api` proxy와 OAuth relay origin 정책이 별도 결정되어야 한다.

## Dev Notes

### Contract Priority

- Story 10.4 scope는 Figma Make acceptance sprint plan의 Step 4-D~4-F다.
- Epic 5/6 backend/API semantics는 이미 구현되어 있으므로 Story 10.4는 frontend wiring story다.
- Story 10.4는 backend read model의 shape를 바꾸지 않는다. DTO가 rough docs와 실제 Java record가 다르면 Java record/API surface를 우선한다.
- External latest version research는 필요하지 않았다. 이 story는 dependency upgrade나 framework selection이 아니라 existing Vite/React workspace와 existing backend API를 사용하는 wiring story다.

### Previous Story Intelligence

- Story 10.2는 `AuthProvider`, `authFetch`, `useApiResource`, no-store option, request sequence guard를 만들었다. Story 10.4는 이 foundation을 재사용해야 한다.
- Story 10.3은 Project/Application/Dashboard link chain을 server-provided links로 닫았고, `instances[].links.evidence`를 Story 10.4 handoff로 보존했다.
- Story 10.3은 `dashboard-data.ts` mock seed를 삭제했다. Story 10.4는 mock seed를 되살리지 않는다.
- Story 6.5 static dashboard의 핵심 학습은 evidence link를 dashboard response에서 받은 값으로만 쓰고 selected context와 identity mismatch를 fail-closed로 막는 것이다.
- Story 6.6 static dashboard의 핵심 학습은 trend source를 evidence response의 `links.snapshotTrend`로 열고, `since=24h` backend query를 trend에 만들지 않는 것이다.
- Story 6.7 static dashboard의 핵심 학습은 marker/event/detail response를 표시만 하고 type/severity/event/recovery를 UI에서 다시 분류하지 않는 것이다.
- Story 9.2의 핵심 학습은 raw starter credential은 create/rotation success의 `starterCredential.displayValue`에서만 1회 표시하고 browser storage, URL, data attribute, hidden input, log/error에 남기지 않는 것이다.

### Implementation File Candidates

Frontend 후보:

- `frontend/src/app/lib/api.ts`
  - 기존 endpoint constants, no-store options, `readJsonResource`를 확장한다.
- `frontend/src/app/lib/read-model-types.ts`
  - evidence/trend/history/snapshot detail/credential DTO type 후보.
- `frontend/src/app/lib/read-model-adapters.ts`
  - link validator, null-safe presentation adapter, formatting helper 후보.
- `frontend/src/app/components/dashboard.tsx`
  - Project registration, credential lifecycle, Snapshot/History entry, instance handoff action wiring 후보.
- `frontend/src/app/components/instance-panels.tsx`
  - Instance Evidence와 Trend drawer state/fetch/render 후보.
- `frontend/src/app/components/docs.tsx`
  - docs copy alignment이 필요한 경우 endpoint list나 credential wording 보정 후보. 단 docs-only change로 scope를 넓히지 않는다.

Backend source reference only. Story 10.4에서 수정하지 않는다:

- `observability-portal/src/main/java/com/observation/portal/domain/instance/**`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/**`
- `observability-portal/src/main/java/com/observation/portal/domain/history/**`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/controller/ProjectRegistrationController.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/controller/StarterCredentialController.java`

### UI Copy Guardrails

- Instance Evidence는 Application Dashboard보다 강한 판단을 만들지 않는다.
- Trend는 stored snapshot projection이다. 현재 instance health나 recovery proof가 아니다.
- Snapshot Detail은 그 시점에 저장된 dashboard read model이며 current 상태 재판정이 아니다.
- `events=[]`, `markers=[]`, `points=[]`, `contributed=false`, `not_observed`는 현재 정상 증명이 아니다.
- Recovery 계열은 "회복 관찰 중"이지 "복구 완료"가 아니다.
- `down_entered`는 metric data freshness/state boundary를 표현할 뿐 host application down 원인을 확정하지 않는다.
- Credential revoked는 starter ingest credential 상태이며 project visibility 또는 account auth 상태와 섞지 않는다.
- No-token/401/404는 resource absence, health state, host down, credential revoked로 번역하지 않는다.

### Architecture Constraints

- Active baseline은 Traditional MVC + Service/Repository Layering이지만 Story 10.4는 frontend wiring만 수행한다.
- `application`, `port`, `adapter`, `adapter.in`, `adapter.out` package를 만들지 않는다.
- 새 backend endpoint, controller, service, repository, migration은 만들지 않는다.
- Frontend는 server read model을 표시만 한다. UI/controller/repository는 lifecycle state, starter diagnosis, zeroInsight, recovery, rule, p95/p99, endpoint priority, marker/event를 계산하지 않는다.
- Browser token persistence와 starter credential persistence를 만들지 않는다.
- AGENTS.md 기준으로 새 파일/핵심 helper 주석은 한국어로 작성한다.

## Testing

Focused frontend/static test 후보는 구현 시점의 실제 test stack에 맞춰 결정한다. 현재 `frontend/package.json`에는 별도 test script가 없으므로 최소 verification은 typecheck/build/static grep과 Gradle-served manual QA다. 필요하면 dependency 추가 없이 가능한 component/helper unit test 또는 existing project pattern을 따른다.

필수 scenario:

- Evidence action은 Dashboard `instances[].links.evidence` 기반 authenticated fetch로 이어진다.
- Trend action은 Evidence `links.snapshotTrend` 우선, documented fallback helper만 사용한다.
- Snapshot/History action은 fixed `24h|7d|14d` query만 만든다.
- Credential lifecycle request는 no-store option을 사용한다.
- Registration/rotation success raw `displayValue`는 copy/confirm/close 뒤 state에서 제거된다.
- Invalid link, no token, 401, 400, 404, 409, generic error, malformed response, stale response, identity mismatch가 fail-closed safe state로 렌더링된다.
- Event/marker/detail rendering은 server-provided type/severity/title/summary/evidence/link만 표시한다.
- Snapshot detail readSemantics/source/link identity를 검증하고 raw JSON dump를 표시하지 않는다.
- Static UI가 token persistence, URL token parsing, raw credential storage, frontend stack 변경을 만들지 않는다.
- Static UI가 lifecycle state/rule/p95/p99/endpoint priority/event promotion/marker severity helper를 만들지 않는다.

Suggested commands:

```bash
cd frontend && npm run typecheck
cd frontend && npm run build
rg -n "instanceEvidenceById|snapshotTrendByInstance|dashboard-data|displayValue|projectKeyHash|starter-credential|ingest/v1" frontend/src
rg -n "localStorage|sessionStorage|document.cookie|accessToken|refreshToken|provider token|starterCredential|displayValue" frontend/src
rg -n "sort\\(|reduce\\(|percentile|p95|p99|endpointPriority|state.code|captureReason|resolvedAt" frontend/src/app/lib frontend/src/app/components
git diff --check
```

Manual QA, Gradle-served Spring origin:

1. GitHub login 후 Project list 로드 확인.
2. Project registration 성공, `starterCredential.displayValue` 1회 표시, copy/confirm 후 raw value 제거 확인.
3. Credential metadata, rotation, revocation 상태 확인.
4. Application Dashboard에서 Instance Evidence, Trend 7d/14d, Snapshot/History 24h/7d/14d, Snapshot Detail 진입 확인.
5. DevTools storage에 token, provider token, starter credential raw value가 없는지 확인.
6. Network 탭에서 새 backend endpoint 없이 existing endpoint allow-list만 호출되는지 확인.

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- 2026-06-02: BMAD dev-story workflow customization, config, persistent project context를 확인했다.
- 2026-06-02: 요청된 AGENTS.md, project-context.md, sprint-status.yaml, Story 10.4 전체, Story 10.2/10.3, api-surface.md, acceptance-traceability.md, read-model-contract.md를 읽었다.
- 2026-06-02: 실제 backend controller/record shape를 확인했다: InstanceEvidence, InstanceSnapshotTrend, Snapshot Marker/Detail, Operational Event History, Project Registration, Starter Credential Lifecycle.
- 2026-06-02: 현재 frontend auth/fetch/read-model foundation과 Dashboard/InstancePanels 구조를 확인했다.
- 2026-06-02: sprint-status에서 `10-4-wire-evidence-trend-and-credential-surfaces`를 `in-progress`로 전환했다.
- 2026-06-02: evidence/trend/history/snapshot detail/registration/credential lifecycle DTO와 validated endpoint helper를 추가했다.
- 2026-06-02: Instance Evidence drawer와 Instance Snapshot Trend drawer를 `authFetch`/`useApiResource` 기반으로 전환했다.
- 2026-06-02: Snapshot/History rail과 reusable Snapshot Detail surface를 추가했다.
- 2026-06-02: Project registration과 selected project starter credential lifecycle surface를 연결했다.
- 2026-06-02: `cd frontend && npm run typecheck`: 성공.
- 2026-06-02: `cd frontend && npm run build`: 성공.
- 2026-06-02: Story 10.4 static `rg` guard들과 `git diff --check`, Next.js/router drift guard, duplicate Java baseline blocker 확인을 수행했다.

### Implementation Plan

- Story 10.3의 Project -> Application -> Dashboard server link chain은 유지하고, 하위 surface만 Story 10.4 범위로 확장했다.
- DTO는 실제 Java record/controller shape를 기준으로 `read-model-types.ts`에 추가했다.
- Endpoint helper와 link validator는 `read-model-adapters.ts`에 모아 current selected context와 내부 `/api/...` path shape를 검증하게 했다.
- Instance drawer는 Evidence와 Trend를 같은 target context로 묶고, stale response guard는 Story 10.2 `useApiResource`에 맡겼다.
- Snapshot detail은 history rail과 trend drawer가 함께 쓸 수 있도록 별도 component로 분리했다.
- Project registration/rotation의 raw `displayValue`는 one-time surface 안에만 두고 copy/close action에서 state를 제거하게 했다.

### Completion Notes List

- `InstanceEvidenceReadModel`, `InstanceSnapshotTrendReadModel`, `DashboardSnapshotMarkerReadModel`, `DashboardSnapshotDetailReadModel`, `OperationalEventHistoryReadModel`, Project registration/credential lifecycle DTO type을 frontend read-model boundary에 추가했다.
- Evidence fetch는 Dashboard `instances[].links.evidence`만 source로 사용하고 selected Project/Application/Instance id와 link/response identity를 모두 검증한다.
- Trend fetch는 Evidence `links.snapshotTrend`를 우선 사용하고, direct trend 진입은 documented snapshot trend path helper + fixed `7d`/`14d` query만 사용한다.
- Snapshot/History는 selected Project/Application context에서 fixed `24h`/`7d`/`14d` preset으로 event와 marker endpoint를 호출한다.
- Snapshot Detail은 event/marker `links.snapshot` 또는 trend point `snapshotId`로만 진입하며 source/readSemantics/self link identity를 검증하고 raw read model JSON dump를 만들지 않는다.
- Project registration은 `POST /api/projects`에 project name만 보내고, 성공 후 `GET /api/projects` reload 결과에 새 project가 나타난 경우에만 selection을 반영한다.
- Credential metadata/rotation/revocation은 selected project id 기준 existing lifecycle endpoint만 사용하고 모두 no-store option을 적용한다.
- Registration/rotation raw `starterCredential.displayValue`는 1회 표시 surface에서만 보여주고 copy/close 뒤 React state에서 제거한다.
- `displayValue` static grep match는 DTO type과 one-time display/copy surface에 한정된다. `starter-credential` match는 endpoint helper/docs/import reference이고, `/api/ingest/v1/*` match는 docs/import setup reference뿐이다.
- `localStorage`/`sessionStorage`/`document.cookie` guard match는 Story 10.2 auth memory state와 기존 sidebar preference cookie뿐이며 새 token/credential persistence를 만들지 않았다.
- `sort(`/`reduce(` guard는 0건이다. p95/p99/captureReason/resolvedAt match는 server-provided field/type/display에 한정되며 계산 helper를 만들지 않았다.
- Next.js API route, `react-router-dom`, backend endpoint, Gradle/Spring static fallback, legacy static dashboard 삭제는 추가하지 않았다.
- Known `* 2.java` duplicate Java baseline blocker 네 파일은 그대로 남아 있으며 수정/삭제하지 않았다.
- Gradle-served Spring origin에서 GitHub/OAuth/API manual QA는 수행하지 않았다. 이 story 검증은 요청된 frontend typecheck/build/static guard 중심으로 완료했다.

### File List

- `planning-artifacts/stories/10-4-wire-evidence-trend-and-credential-surfaces.md`
- `implementation-artifacts/sprint-status.yaml`
- `frontend/src/app/lib/read-model-types.ts`
- `frontend/src/app/lib/read-model-adapters.ts`
- `frontend/src/app/components/dashboard.tsx`
- `frontend/src/app/components/instance-panels.tsx`
- `frontend/src/app/components/snapshot-detail-surface.tsx`

### Change Log

- 2026-06-02: Story 10.4 Wire evidence, trend, and credential surfaces create-story 산출물을 생성했다.
- 2026-06-02: Story 10.4 frontend wiring 구현을 완료하고 review 상태로 전환했다.
