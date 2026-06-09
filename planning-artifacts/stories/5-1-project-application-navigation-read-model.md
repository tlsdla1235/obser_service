---
artifactType: story
storyId: "5.1"
epic: "Epic 5. Dashboard Read Model and API"
title: "Project/Application Navigation Read Model"
architectureStyle: Traditional MVC
status: done
date: 2026-05-25
---

# Story 5.1 - Project/Application Navigation Read Model

## User Story

portal 구현자로서, Project Entry와 Application List가 사용할 read-only navigation read model/API를 service layer에서 제공하길 원한다.

그래야 사용자가 `project -> application -> dashboard` 흐름으로 scope를 좁힐 수 있고, navigation 화면이 Application Dashboard의 state/triage/endpoint/p95 판단을 복제하지 않는다.

## Scope / Out of Scope

포함:

- Project Entry용 read-only project list API/read model 후보
- Application List용 read-only application list API/read model 후보
- `domain.catalog` 또는 `domain.dashboard` 아래의 `ProjectApplicationNavigationService` 후보
- Project 화면에 필요한 `projectId`, `name`, `applicationCount`, setup/connection issue 후보 count, recent concern 0~1개 후보
- Application List에 필요한 `applicationId`, `name`, `environment`, dashboard link, light lifecycle badge 후보, last accepted bucket/freshness summary, starter heartbeat/connection summary, top concern 0~1개 후보
- accepted bucket freshness와 starter heartbeat summary를 별도 source/field로 내리는 response shape
- 기존 catalog, accepted bucket, heartbeat telemetry, lifecycle state typed model 재사용
- controller/service/repository 경계와 serialization contract를 검증하는 focused tests

제외:

- Project 생성 public onboarding API 구현 또는 제품 결정
- local/internal seed 또는 admin project creation 구현 변경
- Application Dashboard current read model skeleton 구현
- detailed triage, endpoint priority, p95/p99 판단, source-scoped percentile, histogram distribution 구현
- raw metric explorer, endpoint timeseries, dashboard snapshot/history, operational event API
- instance evidence read model, instance snapshot trend projection
- dashboard UI/static asset 구현
- 새 migration/table/schema 변경. 기존 schema로 구현이 불가능하면 구현하지 말고 correct-course로 올린다.
- starter ingest/heartbeat endpoint 동작 변경

## Acceptance Criteria

1. `GET /api/projects` 후보 endpoint는 Project Entry용 read-only navigation read model을 반환한다.
2. Project item은 `projectId`, `name`, `applicationCount`를 포함하고, setup/connection issue 후보 count와 recent concern 후보는 light summary로만 제공한다.
3. Project item의 recent concern은 최대 1개이며, Story 5.1에서 신뢰 가능한 service source가 없으면 `null` 또는 빈 값으로 둔다. 새 rule/triage 계산을 만들지 않는다.
4. `GET /api/projects/{projectId}/applications` 후보 endpoint는 Application List용 read-only navigation read model을 반환한다.
5. Application item은 application identity, environment, dashboard link, last accepted bucket/freshness summary, starter connection summary를 포함한다.
6. Application list의 lifecycle badge는 server-computed light state로만 제공한다. 상세 triage, endpoint priority, p95/p99 판단을 대신하지 않는다.
7. accepted bucket freshness는 `statusSource=accepted_bucket` 계열 field로, heartbeat summary는 `statusSource=starter_heartbeat` 계열 field로 분리한다. 두 값을 합쳐 하나의 `health`, `hostHealth`, `applicationHealth` 판단으로 반환하지 않는다.
8. heartbeat가 최근이어도 accepted bucket freshness를 current로 만들지 않고, heartbeat가 없거나 stale이어도 host application down을 확정하지 않는다.
9. Controller는 path/query 변환, validation error/status mapping, service 위임만 담당한다. Repository를 직접 호출하거나 state/connection/concern을 재계산하지 않는다.
10. Service는 기존 repository/model/service를 재사용하고 JPA entity를 API response, public DTO, service external result로 직접 반환하지 않는다.
11. Repository는 project/application 목록, application count, latest accepted bucket timestamp, latest heartbeat telemetry 같은 조회만 제공한다. lifecycle state, rule, p95/p99, endpoint priority를 계산하지 않는다.
12. Project creation을 public onboarding API로 열지 local/internal seed로 유지할지는 open decision으로 문서화하고, Story 5.1 구현 범위에서 닫지 않는다.
13. 새 공개 클래스, 공개 메서드, 핵심 helper는 프로젝트 지침에 따라 한국어 Javadoc/docstring으로 역할과 사용 맥락을 설명한다.
14. Focused tests가 response shape, field separation, dashboard 판단 대체 금지, controller/service/repository layer boundary, no public project creation API를 검증한다.
15. `./gradlew :observability-portal:test`와 `git diff --check`가 통과한다.

## Tasks/Subtasks

- [x] Navigation read model shape 확정 (AC: 1~8, 12)
  - [x] Project Entry response DTO/model에 `generatedAt`, `projects[]`, project identity, application count, light setup/connection issue summary, optional recent concern 0~1개를 정의한다.
  - [x] Application List response DTO/model에 `generatedAt`, `project`, `applications[]`, application identity/environment, dashboard link, metric freshness summary, starter connection summary, optional top concern 0~1개를 정의한다.
  - [x] response shape에서 accepted bucket source와 starter heartbeat source를 field 이름과 `statusSource`로 분리한다.
  - [x] Project creation public API 여부는 open decision note로 남기고 endpoint를 추가하지 않는다.
- [x] Service/repository read path 구현 (AC: 5~11)
  - [x] `domain.catalog.service`에 `ProjectApplicationNavigationService` 후보를 추가한다.
  - [x] 기존 `ProjectRepository`, `ApplicationRepository`, `ApplicationInstanceRepository`, `MetricBucketRepository`, `StarterHeartbeatTelemetryRepository`, `AcceptedBucketFreshnessEvaluator`, `LifecycleStateService` 재사용을 우선한다.
  - [x] 필요한 repository query는 read-only 조회로만 추가한다. Schema 변경이나 write path 변경은 하지 않는다.
  - [x] latest accepted bucket timestamp와 heartbeat latest row를 별도 typed input으로 service에 전달한다.
  - [x] sample/readiness나 triage source가 부족하면 active/degraded/top concern을 새로 꾸며내지 않고 unknown/null/empty light summary로 둔다.
- [x] Controller/API boundary 구현 (AC: 1, 4, 9, 10)
  - [x] Project list controller 후보는 `GET /api/projects`를 service에 위임한다.
  - [x] Application list controller 후보는 `GET /api/projects/{projectId}/applications`를 service에 위임한다.
  - [x] 404/empty result/error mapping은 기존 controller test 스타일을 따른다.
  - [x] controller는 repository, lifecycle state service, heartbeat repository를 직접 호출하지 않는다.
- [x] Architecture guard와 scope guard 확인 (AC: 7~13)
  - [x] `application`, `port`, `adapter` package를 만들지 않는다.
  - [x] JPA entity를 response DTO나 service external return model로 노출하지 않는다.
  - [x] public onboarding project creation API, admin write API, migration, dashboard snapshot/history/event API를 추가하지 않는다.
  - [x] 새 공개 production class/method에 한국어 Javadoc을 작성한다.
- [x] Focused tests와 regression 실행 (AC: 14, 15)
  - [x] service unit test 또는 slice test로 Project/Application navigation shape와 field separation을 검증한다.
  - [x] controller test로 endpoint serialization과 service delegation을 검증한다.
  - [x] repository integration test가 필요하면 read-only query만 검증한다.
  - [x] `MvcLayerBoundaryTest`와 no hexagonal package guard를 유지한다.
  - [x] `./gradlew :observability-portal:test`와 `git diff --check`를 실행한다.

## Dev Notes

### Contract Summary

- Epic 5는 UI가 소비할 server-computed read model/API를 닫는 epic이다.
- Story 5.1은 Project Entry와 Application List의 navigation read model만 다룬다.
- Project 화면은 운영 판단 화면이 아니라 scope 선택 화면이다.
- Application List는 상태 스캔과 dashboard 진입을 돕는 화면이다. Application Dashboard가 primary first-screen이다.
- Application Dashboard 판단은 후속 Story 5.2~5.5에서 닫는다.
- accepted bucket은 metric freshness/state/read-model의 data-plane source-of-truth다.
- starter heartbeat는 accepted bucket과 분리된 control-plane/liveness source다.
- heartbeat 성공/미수신은 accepted bucket freshness, host application health, dashboard snapshot, operational event, p95/p99, rule/read-model calculation을 생성하거나 암시하지 않는다.

### Pre-Dev Contract Locks

- Service/controller 위치는 `domain.catalog`로 고정한다. `ProjectApplicationNavigationService`는 catalog scope 선택 read model을 만들고, Application Dashboard 판단은 후속 dashboard service로 남긴다.
- Application List의 lifecycle badge는 sample/readiness와 triage source가 부족하면 `active` 또는 `degraded`를 새로 단정하지 않는다. Story 5.1에서는 latest accepted bucket freshness와 heartbeat summary를 분리해 보여주고, 충분한 판단 source가 없으면 `unknown` light badge를 반환한다.
- Starter heartbeat recency는 service adapter 기준으로 `90초 이내 recent`, `90초 초과 stale`, telemetry row 없음은 `missing/unknown`으로 둔다. 이 값은 starter connection summary에만 사용하며 accepted bucket freshness를 current로 바꾸지 않는다.
- Application List에서 heartbeat latest row는 project-wide latest가 아니라 `projectId + applicationName + environment` 범위의 instance heartbeat 중 가장 최근 row를 사용한다. 필요한 repository query는 read-only 조회로만 추가한다.
- `setupConnectionIssueCount`는 catalog application 기준의 light candidate count다. accepted bucket 없음/오래됨 또는 starter heartbeat 없음/오래됨을 세되, host application down 원인을 확정하지 않는다.
- `recentConcern`과 `topConcern`은 Story 5.4 triage source가 없으면 `null`로 둔다. 임시 rule engine, endpoint priority, p95/p99 판단을 만들지 않는다.

### Suggested API Shape

Project Entry 후보:

```http
GET /api/projects
Accept: application/json
```

```json
{
  "generatedAt": "2026-05-25T10:32:38Z",
  "projects": [
    {
      "projectId": "0fcf1d62-c5f2-43bd-85f7-2dd6c9e458b1",
      "name": "local-demo",
      "applicationCount": 3,
      "setupConnectionIssueCount": 1,
      "recentConcern": null,
      "links": {
        "applications": "/api/projects/0fcf1d62-c5f2-43bd-85f7-2dd6c9e458b1/applications"
      }
    }
  ]
}
```

Application List 후보:

```http
GET /api/projects/{projectId}/applications
Accept: application/json
```

```json
{
  "generatedAt": "2026-05-25T10:32:38Z",
  "project": {
    "projectId": "0fcf1d62-c5f2-43bd-85f7-2dd6c9e458b1",
    "name": "local-demo"
  },
  "applications": [
    {
      "applicationId": "5c942671-e251-4f7f-b610-18ae6ca4ef65",
      "name": "orders-api",
      "environment": "prod",
      "metricData": {
        "statusSource": "accepted_bucket",
        "lastAcceptedBucketAt": "2026-05-25T10:31:30Z",
        "freshnessLabel": "current"
      },
      "starterConnection": {
        "statusSource": "starter_heartbeat",
        "lastHeartbeatAt": "2026-05-25T10:31:45Z",
        "connectionMeaning": "starter_connected",
        "stateImpact": "none"
      },
      "lifecycleBadge": {
        "source": "server_light_navigation_read_model",
        "code": "unknown",
        "label": "Metric data unknown"
      },
      "topConcern": null,
      "links": {
        "dashboard": "/api/projects/0fcf1d62-c5f2-43bd-85f7-2dd6c9e458b1/applications/5c942671-e251-4f7f-b610-18ae6ca4ef65/dashboard"
      }
    }
  ]
}
```

Shape는 구현 중 코드 스타일에 맞게 record/class 이름을 조정할 수 있다. 단, accepted bucket summary와 starter heartbeat summary는 분리된 field로 유지해야 한다.

## Existing Code / Documents To Reuse

- `_bmad/custom/project-context.md`
  - Traditional MVC + Service/Repository Layering, feature-first MVC, Spring Data JPA/Flyway 기준을 우선한다.
- `planning-artifacts/source-of-truth/current-product-source-of-truth.md`
  - `project -> application -> instance` 흐름, Project Entry/Application List 역할, heartbeat boundary를 우선한다.
- `planning-artifacts/contracts/read-model-contract.md`
  - first-screen UI source-of-truth, accepted bucket/heartbeat 분리, UI recomputation 금지, navigation API 후보를 따른다.
- `planning-artifacts/contracts/state-semantics.md`
  - accepted bucket axis와 starter connection axis의 two-axis interpretation matrix를 따른다.
- `planning-artifacts/api-surface.md`
  - `GET /api/projects`, `GET /api/projects/{projectId}/applications`, bootstrap/public project creation open decision을 따른다.
- `planning-artifacts/architecture.md`
  - `ProjectNavigationController` 후보와 `ProjectApplicationNavigationService` 후보를 따른다.
- `planning-artifacts/architecture-implementation-supplement.md`
  - `domain.catalog.service` 또는 `domain.dashboard.service` 위치 후보, no operational events table, no raw explorer 경계를 따른다.
- `planning-artifacts/project-structure.md`
  - feature-first MVC package tree와 금지 package를 따른다.
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ProjectRepository.java`
  - project metadata lookup을 확장할 때 우선 재사용한다. raw project key surface를 늘리지 않는다.
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ApplicationRepository.java`
  - project별 application 목록/active status 조회 후보를 추가할 때 우선 재사용한다.
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ApplicationInstanceRepository.java`
  - instance count/last seen 보조 조회가 필요할 때 우선 재사용한다.
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java`
  - application scope latest accepted bucket `endUtc` 조회를 freshness input으로 재사용한다.
- `observability-portal/src/main/java/com/observation/portal/common/time/AcceptedBucketFreshnessEvaluator.java`
  - latest bucket `endUtc`와 query time만 사용해 freshness 후보를 계산한다.
- `observability-portal/src/main/java/com/observation/portal/domain/state/service/LifecycleStateService.java`
  - server-side light lifecycle badge가 필요할 때 typed input/output 경계를 재사용한다.
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/repository/StarterHeartbeatTelemetryRepository.java`
  - heartbeat latest row 조회 후보를 starter connection summary source로 재사용한다.
- `observability-portal/src/test/java/com/observation/portal/architecture/MvcLayerBoundaryTest.java`
  - controller/repository/dto dependency guard와 no `application`/`port`/`adapter` package guard를 유지한다.
- `planning-artifacts/stories/4-2-lifecycle-state-service.md`, `planning-artifacts/stories/4-3-recovery-guidance.md`, `planning-artifacts/stories/4-4-state-semantics-tests.md`
  - two-axis model, recovery guard, regression test naming과 host-down copy guard를 따른다.

### Previous Story Intelligence

- Story 4.1은 accepted bucket freshness source를 마지막 bucket `endUtc`로 고정했다. `accepted_at`, heartbeat, UI local time은 freshness source가 아니다.
- Story 4.2는 `LifecycleStateService`가 accepted bucket metric axis와 starter connection axis를 별도 typed input/output으로 받도록 구현했다.
- Story 4.3은 recovery를 top-level state가 아니라 `UNKNOWN + recovery.isRecovering=true` 조합으로 고정했다. Story 5.1은 recovery 판단을 직접 만들지 않는다.
- Story 4.4는 heartbeat가 metric state를 current/active로 만들지 못하고, `DOWN` copy가 host process down을 단정하지 않도록 regression test로 고정했다.
- 최근 docs commit은 Epic 5/6 dashboard alignment를 최신 baseline으로 맞췄다. 오래된 application-only first screen 또는 raw explorer 중심 해석을 따르지 않는다.

## Architecture Constraints

- Active baseline은 Traditional MVC + Service/Repository Layering이다.
- Portal package는 feature-first MVC 구조를 따른다.
- 이 프로젝트에서 `domain`은 business feature grouping namespace이며 DDD pure domain layer가 아니다.
- `application`, `port`, `adapter`, `adapter.in`, `adapter.out` package를 새로 만들지 않는다.
- Controller는 service를 호출하고 repository를 직접 호출하지 않는다.
- Service는 빠른 MVC 구현을 위해 필요하면 Spring Data JPA repository와 JPA entity를 직접 사용할 수 있지만, JPA entity를 external return model로 노출하지 않는다.
- Repository는 Spring Data JPA/Jakarta Persistence + Hibernate 기반 저장/조회만 담당한다.
- Flyway SQL migration이 schema source of truth다. Story 5.1은 새 migration을 기대하지 않는다.
- DB view, materialized view, trigger, stored procedure에 lifecycle state, starter connection diagnosis, p95/p99, endpoint priority 계산을 숨기지 않는다.
- raw project key, access token, refresh token, GitHub OAuth token, provider raw payload, secret은 response/log/error에 노출하지 않는다.
- 새 공개 클래스/메서드/핵심 helper의 Javadoc/comment는 프로젝트 지침에 따라 한국어로 작성한다.

## Developer Guardrails

- UI가 lifecycle state, starter connection diagnosis, rule, p95/p99, endpoint priority를 재계산하지 않는다.
- Controller가 lifecycle state, starter connection diagnosis, rule, p95/p99, endpoint priority를 재계산하지 않는다.
- heartbeat를 accepted bucket freshness나 host application health로 합치지 않는다.
- heartbeat 성공은 accepted bucket, dashboard snapshot, operational event, p95/p99, rule/read-model calculation을 만들지 않는다.
- Project/Application navigation read model은 Application Dashboard 판단을 대체하지 않는다.
- Application List는 detailed triage, endpoint priority, p95/p99 판단을 제공하지 않는다.
- raw metric explorer, endpoint timeseries, dashboard snapshot/history, operational event API는 Story 5.1 범위가 아니다.
- Project 생성 public onboarding API 여부는 open decision으로 유지한다.
- heartbeat는 application/instance catalog upsert source가 아니다. application/instance row 생성은 첫 accepted bucket 기준을 유지한다.
- setup/connection issue 후보는 starter connection 또는 accepted bucket absence/staleness의 light signal로만 표현하고 host application down 원인을 확정하지 않는다.
- recent/top concern 후보는 최대 1개다. Story 5.4 triage service가 없으면 null/empty로 두고 임시 rule engine을 만들지 않는다.
- p95/p99 scalar, histogram-derived percentile, endpoint percentile, endpoint p99 alert 기준을 만들지 않는다.
- `dashboard_snapshots`, `operational_events`, endpoint timeseries table, raw instance timeseries table, Redis/outbox를 추가하지 않는다.
- public response에 JPA entity를 직접 반환하지 않는다.
- unrelated refactor, package 재배치, 기존 ingest/heartbeat behavior 변경을 피한다.

## Test Expectations

Focused test 대상 후보:

- `ProjectApplicationNavigationServiceTest`
- `ProjectNavigationControllerTest`
- `ApplicationNavigationControllerTest` 또는 기존 naming convention에 맞는 controller test
- 필요 시 `ProjectApplicationNavigationRepositoryIntegrationTest`
- `MvcLayerBoundaryTest`

필수 scenario:

- Project list는 project id/name/application count를 반환하고 detailed dashboard 판단 field를 포함하지 않는다.
- Project list recent concern은 최대 1개이며 source가 없으면 null/empty다.
- Application list는 accepted bucket freshness summary와 starter connection summary를 별도 field로 반환한다.
- recent heartbeat + no accepted bucket은 metric freshness current/active로 표현되지 않는다.
- stale/down accepted bucket + recent heartbeat는 host application down을 확정하는 copy를 반환하지 않는다.
- current accepted bucket + stale heartbeat는 metric freshness와 starter connection warning을 분리한다.
- top concern/light issue 후보가 endpoint priority, p95/p99, detailed triage를 반환하지 않는다.
- controller는 service를 mock/stub으로 주입받아 serialization/status mapping만 검증한다.
- repository query가 추가되면 read-only 조회와 existing schema/index 사용만 검증한다.
- no public `POST /api/projects` 또는 onboarding project creation endpoint가 추가되지 않았음을 controller mapping 또는 route scan으로 확인한다.
- no `application`, `port`, `adapter` package가 추가되지 않았음을 `MvcLayerBoundaryTest`로 확인한다.

Suggested commands:

```bash
./gradlew :observability-portal:test --tests '*ProjectApplicationNavigation*'
./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest
./gradlew :observability-portal:test
git diff --check
```

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Implementation Plan

- `domain.catalog.model`에 Project Entry/Application List navigation response model을 추가한다.
- `domain.catalog.service.ProjectApplicationNavigationService`에서 catalog, latest accepted bucket endUtc, application/environment scoped latest heartbeat row를 조합한다.
- `domain.catalog.controller.ProjectNavigationController`에서 `GET /api/projects`, `GET /api/projects/{projectId}/applications`를 service 위임으로 노출한다.
- Story 5.1 범위 밖인 public project creation API, migration/schema 변경, dashboard snapshot/history/event API, endpoint priority, p95/p99, 임시 triage/rule engine은 추가하지 않는다.

### Debug Log References

- 2026-05-25T19:32:38+0900: 지정된 precedence 문서, sprint status, 최근 Epic 4 story 형식, 관련 portal code/package 구조를 확인했다.
- 2026-05-25T19:32:38+0900: Story 5.1 create-story 산출물을 `planning-artifacts/stories/5-1-project-application-navigation-read-model.md`에 생성했다.
- 2026-05-25T20:00:10+0900: RED 단계로 `ProjectApplicationNavigationServiceTest`, `ProjectNavigationControllerTest`를 추가했고, 미구현 class/query 컴파일 실패를 확인했다.
- 2026-05-25T20:00:10+0900: `domain.catalog` read model/service/controller와 application scope latest heartbeat read-only query를 구현했다.
- 2026-05-25T20:00:10+0900: Focused test, repository integration test, `MvcLayerBoundaryTest`, 전체 `:observability-portal:test`, `git diff --check`를 실행해 통과를 확인했다.
- 2026-05-25T20:13:29+0900: Review finding 반영으로 controller test를 MockMvc HTTP/JSON 검증으로 보강하고 public `POST /api/projects` route scan을 추가했다.

### Completion Notes

- Project Entry용 `ProjectNavigationReadModel`과 Application List용 `ProjectApplicationNavigationReadModel`을 추가했다.
- `ProjectApplicationNavigationService`는 latest accepted bucket freshness와 starter heartbeat summary를 `statusSource` 기준으로 분리하고, lifecycle badge는 source 부족 시 `unknown`, `recentConcern`/`topConcern`은 `null`로 둔다.
- Starter heartbeat latest row는 project-wide latest가 아니라 `projectId + applicationName + environment` 범위에서 조회한다.
- `setupConnectionIssueCount`는 accepted bucket 없음/오래됨 또는 starter heartbeat 없음/오래됨의 light candidate count로만 계산하며 host application down을 확정하지 않는다.
- Public project creation API, migration/schema 변경, dashboard snapshot/history/event API, p95/p99, endpoint priority, 임시 triage/rule engine은 추가하지 않았다.
- Review finding 반영 후 controller HTTP serialization contract와 public project creation route 부재를 테스트로 고정했다.
- `./gradlew :observability-portal:test`와 `git diff --check`가 통과했다.

### File List

- `planning-artifacts/stories/5-1-project-application-navigation-read-model.md`
- `implementation-artifacts/sprint-status.yaml`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/controller/ProjectNavigationController.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/controller/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/entity/ApplicationEntity.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/model/ProjectApplicationNavigationReadModel.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/model/ProjectNavigationReadModel.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ApplicationRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/service/ProjectApplicationNavigationService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/repository/StarterHeartbeatTelemetryJpaRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/repository/StarterHeartbeatTelemetryRepository.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/controller/ProjectNavigationControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/service/ProjectApplicationNavigationServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/repository/StarterHeartbeatTelemetryRepositoryIntegrationTest.java`

### Change Log

- 2026-05-25: Story 5.1 create-story 산출물을 생성하고 ready-for-dev 상태로 전환했다.
- 2026-05-25: Project/Application navigation read model, catalog controller/service, scoped heartbeat latest query, focused tests를 구현했다.
- 2026-05-25: Review finding을 반영해 controller HTTP boundary 테스트와 한국어 package Javadoc을 보강하고 done 상태로 전환했다.

## Status

done
