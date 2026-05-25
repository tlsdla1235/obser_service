---
artifactType: story
storyId: "5.2"
epic: "Epic 5. Dashboard Read Model and API"
title: "Application Dashboard Read Model Skeleton"
architectureStyle: Traditional MVC
status: done
date: 2026-05-25
---

# Story 5.2 - Application Dashboard Read Model Skeleton

## User Story

portal 구현자로서, Application Dashboard가 사용할 current dashboard read model API skeleton을 service layer에서 제공하길 원한다.

그래야 UI가 `project -> application -> dashboard` 흐름의 primary first-screen을 `read-model-contract` 응답 그대로 렌더링하고, state, zero-insight, recovery, metric freshness, starter connection 의미를 controller나 frontend에서 다시 계산하지 않는다.

## Scope / Out of Scope

포함:

- `GET /api/projects/{projectId}/applications/{applicationId}/dashboard` endpoint
- `applicationId`를 environment까지 포함한 `applications` row identity로 해석하는 project/application scoped lookup
- `read-model-contract`의 top-level field를 유지하는 current dashboard response skeleton
- 항상 포함되는 top-level field: `generatedAt`, `application`, `state`, `starterConnection`, `zeroInsight`, `recovery`, `metrics`, `sourceScopedPercentiles`, `triageCards`, `endpointPriority`, `snapshot`
- Story 5.2 placeholder: `sourceScopedPercentiles.items=[]`, `triageCards=[]`, `endpointPriority=[]`, `snapshot=null`
- query 시점 기준 UTC 30초 bucket boundary로 floor한 `evaluationAt`/`currentWindowEndUtc`
- `evaluationAt` 기준 current 15분 window와 직전 baseline 15분 window
- latest accepted bucket `endUtc`, current window `requestCount`/`errorCount` aggregate, latest starter heartbeat 조회
- accepted bucket 기반 metric data axis와 starter heartbeat 기반 connection axis 분리
- `DashboardReadModelService` orchestration과 `LifecycleStateService` state 판단 위임
- `triageCards=[]`에서 항상 non-null `zeroInsight`
- controller/service/repository 경계와 serialization contract를 검증하는 focused tests

제외:

- 별도 `environment` query parameter
- project 없음, application 없음, project/application mismatch를 200/empty로 반환하는 동작
- host application alive/down 단일 확정값
- Story 5.2에서 새 degraded state 의미, degraded 진입/해소 rule, confidence, 5-bucket bad count 구현
- p95/p99 평균/최댓값/병합, histogram percentile 계산, endpoint percentile rollup
- rule confidence, triage ranking, endpoint priority 계산
- dashboard snapshot persistence, marker, retention, snapshot detail link
- dashboard query 시 snapshot 읽기 또는 쓰기
- health score, availability score, avg/max latency, metrics p95/p99 scalar
- dashboard UI/static asset 구현
- 새 migration/table/schema 변경. 기존 schema로 구현이 불가능하면 구현하지 말고 correct-course로 올린다.
- starter ingest/heartbeat endpoint 동작 변경

## Acceptance Criteria

1. `GET /api/projects/{projectId}/applications/{applicationId}/dashboard` endpoint를 제공한다.
2. `applicationId`는 environment까지 포함한 `applications` row이며, 별도 `environment` query parameter를 두지 않는다.
3. project 없음, application 없음, project/application mismatch는 모두 `404`로 매핑한다.
4. `200` response는 항상 `generatedAt`, `application`, `state`, `starterConnection`, `zeroInsight`, `recovery`, `metrics`, `sourceScopedPercentiles`, `triageCards`, `endpointPriority`, `snapshot` top-level field를 포함한다.
5. Story 5.2 placeholder로 `sourceScopedPercentiles.items=[]`, `triageCards=[]`, `endpointPriority=[]`, `snapshot=null`을 반환한다.
6. `generatedAt`은 실제 응답 생성 시각이고, `application.sourceWindow.current.endUtc` 및 service 내부 `evaluationAt/currentWindowEndUtc`는 query 시각을 가장 최근 UTC 30초 bucket boundary로 floor한 값이다.
7. current window는 `evaluationAt` 기준 최근 15분이고, baseline window는 그 직전 15분이다.
8. latest accepted bucket `endUtc`는 freshness source로만 사용하며 current window end를 대체하지 않는다.
9. Dashboard response는 host application alive/down 단일 확정값을 반환하지 않는다.
10. accepted bucket은 metric freshness/state/read model source-of-truth이며, starter heartbeat는 `starterConnection` 전용 control-plane/liveness evidence로만 사용한다.
11. `DashboardReadModelService`는 dashboard 응답을 조립하되 state 의미 판단은 `LifecycleStateService`에 맡긴다.
12. Story 5.2는 degraded 진입/해소, rule guard, confidence, 5-bucket bad count를 구현하지 않는다. degraded 계약은 Story 5.4에서 닫는다.
13. `zeroInsight`는 항상 채워지고 `triageCards=[]`와 함께 반환된다.
14. accepted bucket 없음 + recent heartbeat는 `zeroInsight.reasonCode=waiting_first_data`를 반환한다.
15. accepted bucket 없음 + missing/stale heartbeat는 `zeroInsight.reasonCode=telemetry_unreachable`을 반환한다.
16. current bucket + insufficient sample은 `zeroInsight.reasonCode=insufficient_sample`을 반환한다.
17. current bucket + idle traffic은 `zeroInsight.reasonCode=metric_data_idle`을 반환한다.
18. current bucket + sufficient active sample은 `zeroInsight.reasonCode=no_action_needed`를 반환한다.
19. recent heartbeat + stale accepted bucket은 `zeroInsight.reasonCode=metric_data_idle`을 반환한다.
20. missing/stale heartbeat + stale accepted bucket은 `zeroInsight.reasonCode=telemetry_unreachable`을 반환한다.
21. Story 5.2는 `observing_recovery`를 반환하지 않는다. recovery 활성화와 zero-insight 우선순위는 후속 story에서 다룬다.
22. `metrics`는 current 15분 window의 `requestCount`, `errorCount`, `errorRate`만 포함한다.
23. `errorRate`는 `requestCount > 0`이면 `errorCount / requestCount`, `requestCount = 0`이면 `null`이다.
24. `metrics`에 p95/p99, avg/max latency, health score, availability score를 포함하지 않는다.
25. Repository는 application/project membership, latest accepted bucket `endUtc`, current window request/error aggregate, latest heartbeat 조회만 제공한다.
26. Repository 반환 모델은 `WindowBucketAggregate` 같은 중립적 조회 모델로 두며 state, sample readiness, traffic activity, zeroInsight, rule, percentile, endpoint priority를 판단하지 않는다.
27. Controller는 path/status mapping과 service 위임만 담당하고 repository, lifecycle service, clock/window 계산을 직접 호출하지 않는다.
28. JPA entity는 API response, controller response DTO, service external return model로 직접 노출하지 않는다.
29. 새 공개 클래스, 공개 메서드, 핵심 helper는 프로젝트 지침에 따라 한국어 Javadoc/docstring으로 역할과 사용 맥락을 설명한다.
30. Focused tests가 endpoint/status mapping, top-level response shape, window boundary floor, field placeholder, zeroInsight mapping, metrics calculation, source axis separation, layer boundary를 검증한다.
31. `./gradlew :observability-portal:test`와 `git diff --check`가 통과한다.

## Tasks/Subtasks

- [x] Dashboard read model shape 확정 (AC: 4~5, 22~24, 28)
  - [x] `domain.dashboard.model`에 current dashboard response record/class를 추가한다.
  - [x] top-level field를 `read-model-contract`와 동일하게 유지하고 `snapshot`은 nullable field로 둔다.
  - [x] `sourceScopedPercentiles`, `triageCards`, `endpointPriority`는 5.2 placeholder가 명확히 드러나는 empty collection shape로 둔다.
  - [x] `metrics`에는 `requestCount`, `errorCount`, nullable `errorRate`만 둔다.
- [x] Dashboard service orchestration 구현 (AC: 6~21, 25~26)
  - [x] `domain.dashboard.service.DashboardReadModelService`를 추가한다.
  - [x] query 시각과 `generatedAt`을 분리한다. query 시각은 `Clock`에서 얻고 `evaluationAt/currentWindowEndUtc`는 UTC 30초 bucket boundary로 floor한다.
  - [x] `TimeBucketWindowCalculator.dashboardWindowEndingAt(evaluationAt)` 또는 동등한 공유 helper로 current/baseline 15분 window를 계산한다.
  - [x] application/project membership을 확인한 뒤 latest accepted bucket `endUtc`, current window aggregate, latest heartbeat를 조회한다.
  - [x] accepted bucket freshness와 starter connection 입력을 별도 typed input으로 만들어 `LifecycleStateService`에 전달한다.
  - [x] Story 5.2에서는 degraded concern input을 항상 "degraded enter 불가"로 두고 새 rule 판단을 만들지 않는다.
  - [x] `LifecycleStateService`의 metric state/starter connection/recovery 결과를 dashboard response field로 옮긴다.
  - [x] `triageCards=[]`일 때 contract-safe zeroInsight reason mapping을 적용한다.
- [x] Repository read path 추가 (AC: 25~26, 28)
  - [x] 기존 `ApplicationRepository`로 project/application membership 조회를 우선 재사용하거나 필요한 read-only query를 추가한다.
  - [x] 기존 `MetricBucketRepository`/`AcceptedMetricBucketJpaRepository`에 application scope latest bucket `endUtc`와 current window request/error aggregate 조회를 추가한다.
  - [x] aggregate 반환 타입은 `WindowBucketAggregate`처럼 persistence와 의미 판단이 분리된 neutral model로 둔다.
  - [x] 기존 `StarterHeartbeatTelemetryRepository.findLatestByApplicationScope(projectId, applicationName, environment)`를 latest heartbeat source로 재사용한다.
- [x] Controller/API boundary 구현 (AC: 1~3, 27)
  - [x] `domain.dashboard.controller.DashboardController`를 추가한다.
  - [x] controller는 `UUID projectId`, `UUID applicationId` path variable을 service에 전달하고 `Optional.empty()` 또는 service-level not found 결과를 `404`로 매핑한다.
  - [x] controller에서 repository 조회, state 판단, heartbeat freshness 판단, window 계산을 하지 않는다.
- [x] Scope guard 확인 (AC: 8~12, 21, 24~28)
  - [x] `environment` query parameter, snapshot read/write, snapshot detail link, endpoint priority/rule/ranking, percentile 계산을 추가하지 않는다.
  - [x] host application down/alive 확정 copy 또는 `health`, `hostHealth`, `applicationHealth` 단일 필드를 만들지 않는다.
  - [x] `application`, `port`, `adapter` package를 만들지 않는다.
  - [x] JPA entity를 dashboard read model로 직접 반환하지 않는다.
- [x] Focused tests와 regression 실행 (AC: 30~31)
  - [x] `DashboardReadModelServiceTest`로 window floor, current aggregate metrics, zeroInsight mapping, source axis separation을 검증한다.
  - [x] `DashboardControllerTest`로 endpoint serialization, 404 mapping, service delegation을 검증한다.
  - [x] repository query가 추가되면 integration test로 DB sum/count query와 application scope filtering만 검증한다.
  - [x] `MvcLayerBoundaryTest`와 no hexagonal package guard를 유지한다.
  - [x] `./gradlew :observability-portal:test`와 `git diff --check`를 실행한다.

## Dev Notes

### Contract Summary

- Epic 5는 UI가 소비할 server-computed read model/API를 닫는 epic이다.
- Story 5.2는 Application Dashboard current read model skeleton만 다룬다.
- Application Dashboard는 primary first-screen이다.
- Project Entry와 Application List는 scope 선택/스캔 surface이고, 상세 dashboard 판단은 이 API에서만 온다.
- accepted bucket은 application metric freshness/state/read-model의 data-plane source-of-truth다.
- starter heartbeat는 accepted bucket과 분리된 control-plane/liveness source다.
- heartbeat 성공/미수신은 accepted bucket freshness, host business health, dashboard snapshot, operational event, p95/p99, rule/read-model calculation을 생성하거나 암시하지 않는다.
- `DashboardReadModelService`는 response assembly를 담당하고, state 의미 판단은 `LifecycleStateService`가 담당한다.

### Pre-Dev Contract Locks

- API path는 `GET /api/projects/{projectId}/applications/{applicationId}/dashboard`로 고정한다.
- `applicationId`는 `applications.id`이며 `name + environment` lookup이나 query parameter가 아니다.
- project 없음, application 없음, mismatch는 모두 404다. mismatch는 application row의 `projectId`가 path `projectId`와 다른 경우다.
- `generatedAt`과 `evaluationAt`은 같은 값으로 가정하지 않는다. `generatedAt`은 response 생성 instant이고, `evaluationAt/currentWindowEndUtc`는 query instant를 UTC 30초 bucket boundary로 floor한 값이다.
- current/baseline window end는 latest accepted bucket `endUtc`가 아니라 `evaluationAt`이다.
- latest accepted bucket `endUtc`는 `application.lastAcceptedBucketAt`, freshness, state input에만 사용한다.
- Story 5.2 zeroInsight는 `triageCards=[]` 기본값과 함께 항상 non-null이다.
- Story 5.2 recovery는 `LifecycleStateService` 결과를 response에 담을 수 있지만 `observing_recovery` zeroInsight reason은 반환하지 않는다. stale/down 이후 recovery UX 우선순위는 후속 story에서 닫는다.
- Story 5.2에서 degraded state를 새로 만들지 않는다. 기존 `LifecycleStateService`를 호출할 때 degraded hysteresis input은 "enter 불가, resolve 판단 없음"으로 전달한다.
- sample readiness와 traffic activity는 dashboard service가 current window aggregate에서 typed input으로 판단한다. threshold 상수가 필요하면 service/model 내부에 명명해 두고 테스트로 고정한다. Repository는 enum이나 zeroInsight를 반환하지 않는다.
- `requestCount=0`은 `errorRate=null`이다. 0으로 나누거나 `0.0` error rate처럼 "오류 없음"으로 오해될 값을 반환하지 않는다.
- `triageCards`, `endpointPriority`, `sourceScopedPercentiles.items` empty placeholder는 후속 story 구현 위치를 안정화하기 위한 API contract다. 임시 계산으로 채우지 않는다.
- `snapshot`은 5.2에서 `null`이다. `dashboard_snapshots` repository 조회/저장, marker, retention, detail link는 Story 5.8 범위다.

### ZeroInsight Mapping for 5.2

Story 5.2는 triage가 비어 있는 dashboard skeleton이므로 아래 mapping을 service-level contract로 고정한다.

| Metric data axis | Starter connection axis | Sample/traffic axis | `zeroInsight.reasonCode` |
|---|---|---|---|
| accepted bucket 없음 | recent heartbeat | 해당 없음 | `waiting_first_data` |
| accepted bucket 없음 | missing/stale heartbeat | 해당 없음 | `telemetry_unreachable` |
| stale/down accepted bucket | recent heartbeat | 해당 없음 | `metric_data_idle` |
| stale/down accepted bucket | missing/stale heartbeat | 해당 없음 | `telemetry_unreachable` |
| current accepted bucket | any heartbeat | insufficient sample | `insufficient_sample` |
| current accepted bucket | any heartbeat | sufficient sample + idle traffic | `metric_data_idle` |
| current accepted bucket | any heartbeat | sufficient sample + active traffic | `no_action_needed` |

`no_recent_traffic`은 starter connection diagnosis로는 가능하지만 zeroInsight reason으로 새로 추가하지 않는다. recent heartbeat + stale accepted bucket 조합은 `metric_data_idle`로 수렴한다.

### Suggested API Shape

```http
GET /api/projects/{projectId}/applications/{applicationId}/dashboard
Accept: application/json
```

```json
{
  "generatedAt": "2026-05-25T10:32:38.421Z",
  "application": {
    "projectId": "0fcf1d62-c5f2-43bd-85f7-2dd6c9e458b1",
    "applicationId": "5c942671-e251-4f7f-b610-18ae6ca4ef65",
    "name": "orders-api",
    "environment": "prod",
    "lastAcceptedBucketAt": "2026-05-25T10:31:30Z",
    "lastHealthyAt": null,
    "sourceWindow": {
      "current": {
        "startUtc": "2026-05-25T10:17:30Z",
        "endUtc": "2026-05-25T10:32:30Z"
      },
      "baseline": {
        "startUtc": "2026-05-25T10:02:30Z",
        "endUtc": "2026-05-25T10:17:30Z"
      }
    },
    "freshness": {
      "lastObservedAt": "2026-05-25T10:31:30Z",
      "staleAt": "2026-05-25T10:33:00Z",
      "downAt": "2026-05-25T10:34:30Z"
    }
  },
  "state": {
    "code": "active",
    "label": "Metric data active",
    "rationale": "Freshness와 sample이 충분하고 degraded concern이 Story 5.2에서 평가되지 않았습니다.",
    "recommendedAction": "현재 metric data state 관련 우선 조치는 없습니다.",
    "scope": "application"
  },
  "starterConnection": {
    "statusSource": "starter_heartbeat",
    "lastHeartbeatAt": "2026-05-25T10:32:15Z",
    "lastHeartbeatStatus": "received",
    "connectionMeaning": "starter_connected",
    "stateImpact": "none"
  },
  "zeroInsight": {
    "reasonCode": "no_action_needed",
    "message": "현재 우선 조치가 필요한 신호는 없습니다.",
    "recommendedAction": "트래픽이 유지되는지 다음 bucket까지 관찰하세요."
  },
  "recovery": {
    "isRecovering": false,
    "lastHealthyAt": null,
    "retryAfterSeconds": null,
    "recommendedAction": null
  },
  "metrics": {
    "requestCount": 42000,
    "errorCount": 1302,
    "errorRate": 0.031
  },
  "sourceScopedPercentiles": {
    "source": "starter_canonical_percentile",
    "scope": "instance_bucket",
    "displayPolicy": "source_scoped_points",
    "aggregatePolicy": "no_average_no_max_no_merge_no_histogram_recalculation",
    "items": [],
    "applicationScopeFallback": "bucket_distribution_only_when_multiple_sources"
  },
  "triageCards": [],
  "endpointPriority": [],
  "snapshot": null
}
```

Shape는 구현 중 record/class 이름을 조정할 수 있다. 단, top-level field, empty placeholder, `snapshot=null`, metric/starter source axis 분리는 유지해야 한다.

## Existing Code / Documents To Reuse

- `_bmad/custom/project-context.md`
  - Traditional MVC + Service/Repository Layering, feature-first MVC, Spring Data JPA/Flyway 기준을 우선한다.
- `planning-artifacts/contracts/read-model-contract.md`
  - first-screen UI source-of-truth, top-level response shape, zeroInsight contract, accepted bucket/heartbeat 분리, metrics/sourceScopedPercentiles/snapshot 경계를 따른다.
- `planning-artifacts/contracts/state-semantics.md`
  - accepted bucket axis와 starter connection axis의 two-axis interpretation matrix를 따른다.
- `planning-artifacts/api-surface.md`
  - Dashboard Query API path, applicationId/environment query parameter 경계, 404 mapping, MVC boundary를 따른다.
- `planning-artifacts/epics.md`
  - Epic 5 목표와 Story 5.2 skeleton 범위를 따른다.
- `planning-artifacts/epic5-6-dashboard-alignment-context.md`
  - Application Dashboard primary first-screen과 Epic 5/6 story sequencing을 따른다.
- `planning-artifacts/stories/5-1-project-application-navigation-read-model.md`
  - navigation story가 만든 `domain.catalog` patterns, scoped heartbeat lookup, source axis separation, layer boundary tests를 재사용한다.
- `observability-portal/src/main/java/com/observation/portal/common/time/TimeBucketWindowCalculator.java`
  - 30초 bucket duration과 15분 dashboard window duration을 재사용한다. 5.2에서는 raw query instant가 아니라 floor된 `evaluationAt`을 넘겨야 한다.
- `observability-portal/src/main/java/com/observation/portal/common/time/AcceptedBucketFreshnessEvaluator.java`
  - latest accepted bucket `endUtc`와 `evaluationAt`으로 freshness candidate를 계산한다.
- `observability-portal/src/main/java/com/observation/portal/domain/state/service/LifecycleStateService.java`
  - metric state, starter connection summary, recovery guidance 판단을 위임한다.
- `observability-portal/src/main/java/com/observation/portal/domain/state/model/*`
  - `MetricLifecycleInput`, `MetricSampleReadiness`, `MetricTrafficActivity`, `StarterConnectionInput`, `LifecycleStateDecision` typed boundary를 재사용한다.
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ApplicationRepository.java`
  - application/project membership 조회를 추가하거나 재사용한다.
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java`
  - latest bucket `endUtc`와 current window aggregate read-only query를 추가하는 repository facade 후보로 재사용한다.
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/repository/StarterHeartbeatTelemetryRepository.java`
  - project/application/environment scope latest heartbeat row 조회를 starter connection source로 재사용한다.
- `observability-portal/src/test/java/com/observation/portal/architecture/MvcLayerBoundaryTest.java`
  - controller/repository/dto dependency guard와 no `application`/`port`/`adapter` package guard를 유지한다.

### Previous Story Intelligence

- Story 5.1은 Project/Application navigation read model에서 accepted bucket freshness와 starter heartbeat summary를 별도 field/source로 분리했다. Story 5.2도 같은 source separation을 유지하되 상세 dashboard response로 확장한다.
- Story 5.1은 application dashboard link를 `/api/projects/{projectId}/applications/{applicationId}/dashboard`로 생성했다. Story 5.2는 이 링크의 실제 read model endpoint를 구현한다.
- Story 5.1은 `StarterHeartbeatTelemetryRepository.findLatestByApplicationScope(projectId, applicationName, environment)`가 project-wide latest가 아니라 application/environment scoped latest heartbeat임을 테스트로 고정했다. Story 5.2도 project-wide heartbeat를 사용하지 않는다.
- Story 4.1은 accepted bucket freshness source를 마지막 bucket `endUtc`로 고정했다. `accepted_at`, heartbeat, UI local time은 freshness source가 아니다.
- Story 4.2는 `LifecycleStateService`가 accepted bucket metric axis와 starter connection axis를 별도 typed input/output으로 받도록 구현했다.
- Story 4.3은 recovery를 top-level state가 아니라 `UNKNOWN + recovery.isRecovering=true` 조합으로 고정했다. Story 5.2는 `observing_recovery` zeroInsight를 아직 다루지 않는다.
- Story 4.4는 heartbeat가 metric state를 current/active로 만들지 못하고, `DOWN` copy가 host process down을 단정하지 않도록 regression test로 고정했다.
- 최근 commit은 Story 5.1 navigation read model 구현을 merge했다. dashboard 구현은 `domain.dashboard`로 추가하되 catalog navigation API를 재작성하지 않는다.

## Architecture Constraints

- Active baseline은 Traditional MVC + Service/Repository Layering이다.
- Portal package는 feature-first MVC 구조를 따른다.
- 이 프로젝트에서 `domain`은 business feature grouping namespace이며 DDD pure domain layer가 아니다.
- 구현 위치는 `domain.dashboard.controller.DashboardController`, `domain.dashboard.service.DashboardReadModelService`, `domain.dashboard.model` read model type으로 둔다.
- `application`, `port`, `adapter`, `adapter.in`, `adapter.out` package를 새로 만들지 않는다.
- Controller는 service를 호출하고 repository를 직접 호출하지 않는다.
- Service는 빠른 MVC 구현을 위해 필요하면 Spring Data JPA repository와 JPA entity를 직접 사용할 수 있지만, JPA entity를 external return model로 노출하지 않는다.
- Repository는 Spring Data JPA/Jakarta Persistence + Hibernate 기반 read-only 조회만 담당한다.
- Flyway SQL migration이 schema source of truth다. Story 5.2는 새 migration을 기대하지 않는다.
- DB view, materialized view, trigger, stored procedure에 lifecycle state, starter connection diagnosis, sample readiness, traffic activity, zeroInsight, p95/p99, endpoint priority 계산을 숨기지 않는다.
- raw project key, access token, refresh token, GitHub OAuth token, provider raw payload, secret은 response/log/error에 노출하지 않는다.
- 새 공개 클래스/메서드/핵심 helper의 Javadoc/comment는 프로젝트 지침에 따라 한국어로 작성한다.

## Developer Guardrails

- UI가 lifecycle state, starter connection diagnosis, zeroInsight, recovery, p95/p99, endpoint priority를 재계산하지 않는다.
- Controller가 lifecycle state, starter connection diagnosis, zeroInsight, recovery, p95/p99, endpoint priority를 재계산하지 않는다.
- heartbeat를 accepted bucket freshness나 host application health로 합치지 않는다.
- heartbeat 성공은 accepted bucket, dashboard snapshot, operational event, p95/p99, rule/read-model calculation을 만들지 않는다.
- latest accepted bucket `endUtc`는 freshness source일 뿐 dashboard current window end가 아니다.
- `TimeBucketWindowCalculator.dashboardWindowAtCurrentTime()`를 그대로 쓰면 raw clock instant가 window end가 될 수 있다. Story 5.2는 floor된 `evaluationAt`을 명시적으로 사용해야 한다.
- current aggregate query는 current window에 포함되는 accepted bucket만 합산한다. baseline aggregate는 5.2에서 state/rule에 쓰지 않으므로 불필요하면 조회하지 않는다.
- Repository aggregate query는 request/error sum/count만 반환한다. sample readiness, traffic activity, zeroInsight reason은 service가 판단한다.
- `sourceScopedPercentiles.items`, `triageCards`, `endpointPriority`를 채우기 위해 임시 percentile/rule/ranking을 만들지 않는다.
- `metrics.errorRate=null`은 traffic 없음/분모 없음의 의미다. UI가 "0% error"로 오해할 수 있는 값을 만들지 않는다.
- `state.code=down`이 나와도 label/rationale/recommendedAction은 metric data-plane unreachable 계열이어야 하며 host application process down을 단정하지 않는다.
- public response에 `health`, `hostHealth`, `applicationHealth`, `alive`, `downConfirmed` 같은 단일 확정 필드를 추가하지 않는다.
- `dashboard_snapshots`, `operational_events`, endpoint timeseries table, raw instance timeseries table, Redis/outbox를 추가하지 않는다.
- unrelated refactor, package 재배치, 기존 ingest/heartbeat behavior 변경을 피한다.

## Test Expectations

Focused test 대상 후보:

- `DashboardReadModelServiceTest`
- `DashboardControllerTest`
- 필요 시 `MetricBucketRepositoryDashboardAggregateIntegrationTest`
- `MvcLayerBoundaryTest`

필수 scenario:

- `GET /api/projects/{projectId}/applications/{applicationId}/dashboard`는 service 결과를 serialization하고 top-level contract field를 모두 포함한다.
- project 없음은 404다.
- application 없음은 404다.
- project/application mismatch는 404다.
- `applicationId`만 path variable로 받고 `environment` query parameter에 의존하지 않는다.
- query 시각 `2026-05-25T10:32:38.421Z`는 `evaluationAt/currentWindowEndUtc=2026-05-25T10:32:30Z`로 floor된다.
- current window는 `2026-05-25T10:17:30Z`부터 `2026-05-25T10:32:30Z`까지이고 baseline은 그 직전 15분이다.
- latest accepted bucket이 `2026-05-25T10:31:30Z`여도 current window end는 `2026-05-25T10:32:30Z`다.
- response의 `generatedAt`은 floor된 evaluationAt이 아니라 실제 생성 시각이다.
- accepted bucket 없음 + recent heartbeat는 `zeroInsight.reasonCode=waiting_first_data`다.
- accepted bucket 없음 + missing/stale heartbeat는 `zeroInsight.reasonCode=telemetry_unreachable`다.
- stale accepted bucket + recent heartbeat는 `zeroInsight.reasonCode=metric_data_idle`이고 host down copy를 반환하지 않는다.
- stale accepted bucket + missing/stale heartbeat는 `zeroInsight.reasonCode=telemetry_unreachable`이고 host down 원인을 확정하지 않는다.
- current accepted bucket + insufficient sample은 `zeroInsight.reasonCode=insufficient_sample`다.
- current accepted bucket + idle traffic은 `zeroInsight.reasonCode=metric_data_idle`다.
- current accepted bucket + sufficient active sample은 `zeroInsight.reasonCode=no_action_needed`다.
- `triageCards=[]`인 모든 5.2 response에서 `zeroInsight`는 non-null이다.
- `sourceScopedPercentiles.items=[]`, `triageCards=[]`, `endpointPriority=[]`, `snapshot=null`이다.
- current aggregate `requestCount=100`, `errorCount=3`이면 `errorRate=0.03`이다.
- current aggregate `requestCount=0`, `errorCount=0`이면 `errorRate=null`이다.
- metrics field에 p95/p99, avg/max latency, health score, availability score가 없다.
- controller는 service를 mock/stub으로 주입받아 serialization/status mapping만 검증한다.
- repository query가 추가되면 read-only sum/count query와 application scope filtering만 검증한다.
- no `application`, `port`, `adapter` package가 추가되지 않았음을 `MvcLayerBoundaryTest`로 확인한다.

Suggested commands:

```bash
./gradlew :observability-portal:test --tests '*DashboardReadModel*'
./gradlew :observability-portal:test --tests '*DashboardController*'
./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest
./gradlew :observability-portal:test
git diff --check
```

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Implementation Plan

- `domain.dashboard.model`에 `read-model-contract` top-level field를 유지하는 dashboard response model을 추가한다.
- `domain.dashboard.service.DashboardReadModelService`에서 project/application membership, latest accepted bucket, current window aggregate, scoped heartbeat를 조회하고 `LifecycleStateService` 결과와 zeroInsight skeleton을 조립한다.
- `domain.dashboard.controller.DashboardController`에서 `GET /api/projects/{projectId}/applications/{applicationId}/dashboard`를 service에 위임하고 not-found를 404로 매핑한다.
- Story 5.2 범위 밖인 percentile/rule/triage/endpoint priority/snapshot persistence/history/event/API/UI는 추가하지 않는다.

### Debug Log References

- 2026-05-25: bmad-create-story workflow 설정, project-context, sprint status, Epic 5 계약 문서, Story 5.1 구현/문서, 관련 time/state/catalog code를 확인했다.
- 2026-05-25: Story 5.2 create-story 산출물을 `planning-artifacts/stories/5-2-application-dashboard-read-model-skeleton.md`에 생성했다.
- 2026-05-25: RED focused tests를 추가하고 dashboard/model/service/controller/repository 구현 전 컴파일 실패를 확인했다.
- 2026-05-25: dashboard read model skeleton, current window aggregate repository, service orchestration, controller boundary를 구현했다.
- 2026-05-25: focused tests, `MvcLayerBoundaryTest`, 전체 `./gradlew :observability-portal:test`, `git diff --check`를 통과했다.

### Completion Notes

- `GET /api/projects/{projectId}/applications/{applicationId}/dashboard` endpoint를 추가하고 project/application membership 불일치를 404로 매핑했다.
- `DashboardReadModelService`가 floor된 30초 evaluationAt, current/baseline 15분 window, latest accepted bucket, current request/error aggregate, scoped heartbeat를 조립하도록 구현했다.
- `LifecycleStateService` 위임 결과를 response state/starterConnection/recovery로 옮기고, Story 5.2 zeroInsight mapping과 placeholder fields를 고정했다.
- percentile, triage ranking, endpoint priority 계산, snapshot persistence/query, UI 구현, environment query parameter는 추가하지 않았다.

### File List

- `implementation-artifacts/sprint-status.yaml`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/model/WindowBucketAggregate.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/AcceptedMetricBucketJpaRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ApplicationRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/controller/DashboardController.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/model/ApplicationDashboardReadModel.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/model/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/DashboardReadModelService.java`
- `observability-portal/src/test/java/com/observation/portal/domain/bucket/repository/MetricBucketRepositoryIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/controller/DashboardControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/service/DashboardReadModelServiceTest.java`
- `planning-artifacts/stories/5-2-application-dashboard-read-model-skeleton.md`

### Change Log

- 2026-05-25: Story 5.2 Application Dashboard Read Model Skeleton create-story 산출물을 생성하고 ready-for-dev 상태로 전환했다.
- 2026-05-25: Application Dashboard read model skeleton API와 repository/service/controller/test를 구현하고 review 상태로 전환했다.
- 2026-05-25: Review blocker/major를 수정하고 검증을 통과해 done 상태로 전환했다.

## Status

done
