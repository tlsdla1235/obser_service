---
artifactType: story
storyId: "6.0"
storyKey: "6-0-starter-to-snapshot-preflight-integration-smoke"
epic: "Epic 6. Dashboard User Flow and Demo Hardening"
title: "Starter-to-snapshot preflight integration smoke"
architectureStyle: Traditional MVC
status: done
date: 2026-05-28
baselineCommit: "c8a4f74abfe0430b81f4728f990d92725c04180e"
---

# Story 6.0 - Starter-to-snapshot preflight integration smoke

## Status

done

## Story

프로젝트 팀은 최소 사용자 Spring Boot 서비스에 `observability-spring-boot-starter`를 실제 dependency로 붙여보는 일회성 preflight smoke를 실행하고 싶다.

그래야 Story 6.1 setup guide가 문서상 안내로만 끝나지 않고, 실제 사용자 서비스에서 starter auto-configuration이 붙고, heartbeat와 metric ingest가 portal로 전송되며, accepted metric bucket이 영속되고, dashboard read model과 dashboard snapshot까지 생성되는지 Epic 6 UI/demo 작업 전에 확인할 수 있다.

## Workflow Note

Story 6.0은 번호상 6.1 앞에 있지만, Story 6.1 완료 이후 뒤늦게 추가된 일회성 preflight integration smoke다.

이 story는 Epic 6 UI/demo 작업을 계속하기 전에 setup guide 실제 연결을 한 번 확인하기 위한 opt-in 검증이다. 이후 모든 Epic 6 story마다 반복 실행해야 하는 regression suite 요구사항이 아니다. `implementation-artifacts/sprint-status.yaml`에서도 이 예외성과 반복 금지 범위를 workflow note로 남겼다.

## Source of Truth

아래 문서를 읽고 반영한 create-story context다. 구현 중 충돌처럼 보이는 지점은 `sprint-status.yaml`의 최신 tracker 상태, Story 6.1 setup guide 계약, 그리고 `read-model-contract.md`의 heartbeat/accepted bucket 분리 원칙을 우선한다.

1. `implementation-artifacts/sprint-status.yaml`
2. `planning-artifacts/stories/6-1-account-project-entry-and-setup-guide.md`
3. `planning-artifacts/epics.md`
4. `planning-artifacts/sprint-plan.md`
5. `planning-artifacts/contracts/ingest-envelope.md`
6. `planning-artifacts/contracts/read-model-contract.md`
7. `planning-artifacts/contracts/starter-failure-semantics.md`
8. `implementation-artifacts/epic-5-retro-2026-05-27.md`
9. `_bmad/custom/project-context.md`
10. `AGENTS.md`

코드 확인 대상:

1. `settings.gradle`
2. `build.gradle`
3. `observability-spring-boot-starter/build.gradle`
4. `observability-portal/build.gradle`
5. `observability-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
6. `observability-spring-boot-starter/src/main/java/com/observation/starter/config/RouteAttributionAutoConfiguration.java`
7. `observability-spring-boot-starter/src/main/java/com/observation/starter/config/MetricDrainAutoConfiguration.java`
8. `observability-spring-boot-starter/src/main/java/com/observation/starter/config/StarterHeartbeatAutoConfiguration.java`
9. `observability-spring-boot-starter/src/main/java/com/observation/starter/spring/observation/MicrometerHttpServerObservationBinder.java`
10. `observability-spring-boot-starter/src/main/java/com/observation/starter/client/PortalMetricBucketClient.java`
11. `observability-portal/src/main/java/com/observation/portal/domain/ingest/controller/IngestHeartbeatController.java`
12. `observability-portal/src/main/java/com/observation/portal/domain/ingest/controller/IngestController.java`
13. `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java`
14. `observability-portal/src/main/java/com/observation/portal/domain/dashboard/controller/DashboardController.java`
15. `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/DashboardReadModelService.java`
16. `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotFallbackCaptureService.java`
17. `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotWriterService.java`
18. `observability-portal/src/main/resources/db/migration/V001__create_projects.sql`
19. `observability-portal/src/main/resources/db/migration/V003__create_accepted_metric_buckets.sql`
20. `observability-portal/src/main/resources/db/migration/V005__create_starter_heartbeat_telemetry.sql`
21. `observability-portal/src/main/resources/db/migration/V006__create_dashboard_snapshots.sql`
22. `observability-portal/src/main/resources/db/migration/V007__extend_dashboard_snapshots_for_writer_policy.sql`

## Scope / Out of Scope

포함:

- 실제 사용자 서비스를 흉내 내는 별도 smoke test용 최소 Spring Boot app 생성
- Spring Initializr 또는 그에 준하는 initializer 방식으로 smoke app skeleton 생성
- 현재 repo 기준에 맞춘 Java 17, Gradle Groovy DSL, Spring Boot plugin/BOM alignment
- smoke app의 `spring-boot-starter-web` 기반 HTTP endpoint 1개 이상
- smoke app이 `observability-spring-boot-starter`를 실제 dependency로 사용
- smoke app이 Story 6.1 setup guide 핵심 설정인 `observation.heartbeat.portal-base-url`, `observation.heartbeat.project-key`, `observation.metric-flush.environment`를 사용
- smoke app의 endpoint 호출로 starter HTTP observation 수집, 30초 bucket 생성, portal ingest, read model, snapshot까지 이어지는 경로 검증
- starter auto-configuration이 사용자 custom bean 없이 붙는지 검증
- heartbeat telemetry persistence와 accepted metric bucket persistence 검증
- dashboard current read model에서 accepted bucket metric axis와 starter heartbeat connection axis가 분리되어 있는지 검증
- dashboard query fallback 또는 명시적인 test trigger로 snapshot row와 `read_model_json` shape 검증
- opt-in preflight 실행 명령 또는 수동 절차 문서화

제외:

- Epic 5 reopen 또는 Epic 5 story status 변경
- Story 6.1 reopen, status downgrade, done 상태 변경
- public project creation API
- UI-side lifecycle state, starter connection diagnosis, rule, p95/p99, endpoint priority, snapshot/history event 계산
- heartbeat를 accepted bucket freshness, host health, dashboard snapshot eligibility, recovery source, operational event source로 합성하는 것
- `operational_events` table
- raw explorer, raw bucket explorer, raw snapshot explorer, endpoint timeseries UI/API
- snapshot writer/read model 경계 재설계
- 실제 1시간 hourly cadence를 기다리는 smoke 절차
- 기본 fast `./gradlew test`에 heavy smoke를 항상 포함시키는 것
- smoke app을 장기 product module처럼 확장하는 것

## Acceptance Criteria

1. `implementation-artifacts/sprint-status.yaml`에 `6-0-starter-to-snapshot-preflight-integration-smoke`가 추가되고, 완료 시 `done`으로 갱신되며, `6-1-account-project-entry-and-setup-guide: done`은 그대로 유지된다.
2. story 문서가 6.0이 6.1 이후 추가된 일회성 preflight이며 이후 모든 story마다 반복해야 하는 regression suite가 아님을 명시한다.
3. Codex가 Spring Initializr 또는 그에 준하는 initializer 방식으로 최소 smoke test용 Spring Boot 사용자 서버를 생성하는 작업이 task에 포함된다.
4. smoke server는 `observability-spring-boot-starter` dependency와 `observation.heartbeat.portal-base-url`, `observation.heartbeat.project-key`, `observation.metric-flush.environment` 설정을 사용한다.
5. smoke server의 HTTP endpoint 호출로 starter observation 수집 경로를 검증할 수 있다.
6. starter auto-configuration은 smoke app 사용자 custom bean 없이 붙어야 한다.
7. smoke app이 portal로 heartbeat를 보내고, portal이 heartbeat telemetry를 영속한다.
8. smoke app의 HTTP endpoint 호출 후 starter가 metric bucket을 만들고, portal이 accepted metric bucket을 영속한다.
9. dashboard read model은 accepted bucket metric axis와 starter heartbeat connection axis를 분리해서 보여준다.
10. dashboard snapshot row가 생성된다.
11. `dashboard_snapshots.read_model_json`이 bounded dashboard read model shape를 보존한다.
12. snapshot JSON 안에서도 `starterConnection.statusSource=starter_heartbeat`는 accepted bucket freshness/state와 분리되어야 한다.
13. preflight 실행 명령 또는 수동 절차가 story 구현 결과에 명확히 기록된다.
14. raw project key나 secret은 Git 추적 파일, log, exception, response body에 남지 않는다.
15. preflight는 opt-in 또는 일회성 절차로 남고 기본 fast `./gradlew test`에 항상 포함되지 않는다.

## Tasks / Subtasks

- [x] Smoke app fixture 생성 (AC: 3~6, 14~15)
  - [x] Spring Initializr 또는 equivalent initializer 방식으로 최소 Spring Boot web app을 생성한다.
  - [x] 권장 위치는 `smoke-tests/starter-to-snapshot/`이며, root `settings.gradle`의 기본 product module로 무심코 편입하지 않는다. 다른 위치를 택하면 story completion note에 이유를 남긴다.
  - [x] Java 17, Gradle Groovy DSL, repo의 Spring Boot plugin/BOM version과 호환되는 dependency 조합을 사용한다.
  - [x] smoke app은 `spring-boot-starter-web`과 `observability-spring-boot-starter`를 실제 dependency로 둔다.
  - [x] smoke app은 최소 `GET /smoke/ping` 같은 endpoint 하나를 제공하고, 해당 endpoint 호출이 Micrometer HTTP server observation으로 흘러갈 수 있게 한다.
  - [x] smoke app production/test source의 공개 class/method와 복잡한 helper에는 AGENTS.md 기준에 맞춰 한국어 Javadoc/comment를 남긴다.

- [x] Starter auto-configuration 실제 연결 경로 검증 및 필요한 최소 보강 (AC: 4~8)
  - [x] smoke app에 사용자 custom `PortalMetricBucketClient`, `ObservationHandler`, `ObservationRegistry` wiring bean을 만들지 않는다.
  - [x] 현재 starter auto-configuration만으로 HTTP observation binder가 host app `ObservationRegistry`에 붙는지 확인한다.
  - [x] 현재 starter auto-configuration만으로 portal bucket flush client가 생성되는지 확인한다.
  - [x] 자동 연결이 비어 있으면 smoke app이 아니라 starter 쪽의 production auto-configuration을 최소 보강한다.
  - [x] metric bucket flush가 Story 6.1 setup guide 핵심 설정과 충돌하지 않도록 한다. 추가 local-only 설정이 꼭 필요하면 tracked guide copy를 확장하지 않고 smoke 전용 config/example에 이유를 명시한다.
  - [x] heartbeat client와 bucket flush worker는 bucket ingest와 heartbeat 의미를 섞지 않는다.

- [x] Local preflight config와 secret guard 구성 (AC: 4, 14~15)
  - [x] raw project key는 `.private/` 또는 environment variable에만 두고 Git 추적 파일에는 placeholder만 둔다.
  - [x] smoke app example config에는 `observation.heartbeat.portal-base-url`, `observation.heartbeat.project-key=<issued-project-key>`, `observation.metric-flush.environment=preflight` 수준만 남긴다.
  - [x] project seed는 public `POST /api/projects`를 만들지 않고 local/internal seed, direct SQL, test helper, 또는 admin-only bootstrap 방식 중 하나로 처리한다.
  - [x] seed 과정은 `projects.project_key_hash`에 BCrypt hash를 저장하고 raw key를 DB/log/response에 남기지 않는다.
  - [x] preflight runner는 raw key를 echo하지 않고 실패 message에도 key 값을 포함하지 않는다.

- [x] Portal end-to-end smoke orchestration 작성 (AC: 7~13, 15)
  - [x] 구현 결과는 opt-in 명령 하나로 실행할 수 있게 한다. 권장 명령은 `./scripts/preflight/starter-to-snapshot-smoke.sh`다.
  - [x] runner는 portal, local PostgreSQL 또는 Testcontainers 기반 DB, generated smoke app을 함께 띄우거나, 같은 검증을 수행하는 명확한 수동 절차를 문서화한다.
  - [x] smoke app endpoint를 충분히 호출해 request count가 있는 closed 30초 bucket 후보를 만든다.
  - [x] 실제 1시간 hourly snapshot cadence를 기다리지 않는다. dashboard query fallback 또는 명시적인 test trigger로 snapshot 생성을 검증한다.
  - [x] heartbeat persistence는 `starter_heartbeat_telemetry` 또는 repository/API assertion으로 확인한다.
  - [x] accepted bucket persistence는 `accepted_metric_buckets` 또는 repository/API assertion으로 확인한다.
  - [x] dashboard current API를 조회해 `starterConnection.statusSource=starter_heartbeat`, `starterConnection.stateImpact=none`, application freshness/metric state가 accepted bucket source인 것을 확인한다.
  - [x] snapshot row를 조회해 `dashboard_snapshots.read_model_json`에 bounded dashboard read model shape가 저장됐는지 확인한다.
  - [x] snapshot JSON에서도 `starterConnection`과 accepted bucket 기반 state/freshness가 별도 field/source로 남는지 확인한다.

- [x] Focused tests와 non-regression guard (AC: 6~15)
  - [x] starter auto-configuration focused test로 smoke app 사용자 custom bean 없이 required runtime beans가 생기는지 확인한다.
  - [x] portal focused test 또는 opt-in smoke assertion으로 heartbeat persistence와 accepted bucket persistence를 확인한다.
  - [x] dashboard read model/snapshot assertion은 기존 `DashboardReadModelService`, `DashboardSnapshotFallbackCaptureService`, `DashboardSnapshotWriterService` 경계를 사용한다.
  - [x] no public project creation API, no `operational_events` table, no raw explorer, no heartbeat-derived metric state/snapshot/event source를 guard한다.
  - [x] heavy smoke command는 `./gradlew test`의 기본 실행 경로에 연결하지 않는다.

- [x] Documentation and completion record (AC: 2, 13, 15)
  - [x] story completion note에 이 smoke가 6.1 이후 추가된 one-time preflight임을 다시 남긴다.
  - [x] 실행 명령, 필요한 env var, 수동 절차, 예상 성공 assertion을 문서화한다.
  - [x] 실패 시 Epic 5나 Story 6.1 status를 낮추지 말고, 발견된 구현 gap을 이 Story 6.0 범위의 작업으로 처리한다.

## Dev Notes

### Current Sprint / Tracker State

- 현재 branch는 `codex/story-6-0-preflight-integration-smoke`다.
- `implementation-artifacts/sprint-status.yaml` 기준 Epic 6은 `in-progress`다.
- Story 6.0은 Checkpoint 4 검증 완료 후 `done`으로 갱신했다.
- `6-1-account-project-entry-and-setup-guide`는 이미 `done`이다. 이 story 작업 중 절대 reopen하거나 status를 낮추지 않는다.
- Story 6.0은 sprint status에서 6.1 위에 삽입됐지만, workflow 의미상 6.1 이후 추가된 one-time preflight다.
- 기존 untracked file `implementation-artifacts/epic-5-dbml-snapshot-2026-05-28.dbml`은 이 story와 무관하므로 건드리지 않는다.

### Repository / Build Baseline

- root project는 Gradle multi-project이며 `observability-portal`, `observability-spring-boot-starter` 두 module을 포함한다.
- root `build.gradle`은 Spring Boot Gradle plugin `4.0.6`, Java toolchain 17, Groovy DSL을 사용한다.
- `observability-spring-boot-starter`는 `java-library` module이고 group/version은 `com.sst:observability-spring-boot-starter:0.1.0-SNAPSHOT`이다.
- `observability-portal`은 Spring Boot app이며 `spring-boot-starter-web`, Spring Data JPA, Flyway, PostgreSQL, Testcontainers 기반 test를 사용한다.
- `.private/`는 `.gitignore`에 포함되어 있으므로 local secret/config 후보 위치로 사용할 수 있다.

### Existing Starter Runtime Signals

- auto-configuration imports에는 `RouteAttributionAutoConfiguration`, `MetricDrainAutoConfiguration`, `StarterHeartbeatAutoConfiguration`이 등록돼 있다.
- `StarterHeartbeatAutoConfiguration`은 `observation.heartbeat.portal-base-url`과 `observation.heartbeat.project-key`가 있으면 `JdkPortalHeartbeatClient`를 생성하고, 없으면 startup-safe no-op client를 쓴다.
- `MetricDrainAutoConfiguration`은 bounded queue, rollup service, envelope builder, `StarterMetricIngestService`, scheduler를 구성한다.
- `MetricBucketFlushWorker`는 `PortalMetricBucketClient` bean이 있을 때만 생성된다.
- 현재 확인한 starter production source에는 `PortalMetricBucketClient` interface는 있지만, bucket ingest용 JDK HTTP client auto-configuration 구현은 보이지 않는다. 구현자는 이 gap을 smoke app custom bean으로 덮지 말고 starter production auto-configuration 또는 동등한 starter 경계에서 닫아야 한다.
- `MicrometerHttpServerObservationBinder`는 HTTP server observation을 `ObservationSampleCollector`로 전달하는 class로 존재한다. 다만 production auto-configuration에서 host app `ObservationRegistry`에 자동 등록되는 bean/wiring은 확인되지 않는다. smoke의 핵심 검증 대상이다.
- `MetricDrainProperties.validatePortalFlushIdentity`는 worker가 뜰 때 generic `project-id`, `application-name`, `environment`, `instance` default를 막는다. Story 6.1 setup guide 핵심 copy는 `project-id/application-name/instance`를 안내하지 않으므로, 구현 중 이 요구가 실제 starter 사용성과 충돌하는지 반드시 확인한다.

### Existing Portal Runtime Signals

- heartbeat endpoint는 `POST /api/ingest/v1/heartbeat`이며 `IngestHeartbeatService`가 project key와 heartbeat shape를 검증하고 `starter_heartbeat_telemetry` latest row를 upsert한다.
- metric ingest endpoint는 `POST /api/ingest/v1/buckets`이며 `IngestAcceptanceService`가 project key, schema version, 30초 UTC bucket, taxonomy, normalized route, idempotency key를 검증한다.
- `MetricBucketRepository.insert`는 accepted bucket 저장 전에 application/application instance catalog row를 get-or-create한다.
- `accepted_metric_buckets`는 metric freshness/state/read model source-of-truth다.
- `starter_heartbeat_telemetry`는 heartbeat control-plane/liveness source이며 catalog upsert source나 metric health source가 아니다.
- `DashboardController`는 `GET /api/projects/{projectId}/applications/{applicationId}/dashboard` current read model을 제공한다.
- `DashboardReadModelService`는 query path에서 `DashboardSnapshotFallbackCaptureService`가 있으면 missing/stale latest snapshot에 대해 `query_fallback` 저장을 시도한다.
- `dashboard_snapshots.read_model_json`은 stored dashboard read model projection이며 raw bucket store, raw snapshot explorer, event store가 아니다.

### Smoke App Requirements

- smoke app은 실제 사용자 Spring Boot app처럼 작게 유지한다. 장기 product module, sample catalog app, UI fixture로 확장하지 않는다.
- smoke app은 최소 web endpoint 하나를 갖는다. 권장 endpoint는 `GET /smoke/ping`이며 response body에는 project key, token, secret, raw ingest payload를 포함하지 않는다.
- smoke app은 `spring.application.name`과 `observation.metric-flush.environment`를 사용해 application/environment identity가 portal catalog와 dashboard에서 추적 가능해야 한다.
- smoke app이 application instance identity를 추가로 설정해야 하면 `.private` 또는 smoke-only config에 둔다. Story 6.1 setup guide core copy를 무단으로 넓히지 않는다.
- smoke app은 starter HTTP observation capture와 bucket flush를 custom user bean으로 대신하면 안 된다. 그 경우 "사용자가 setup guide대로 starter를 붙였을 때"라는 preflight 목적이 깨진다.

### Axis Separation Guardrails

- Heartbeat 성공은 accepted bucket, dashboard snapshot, operational event, p95/p99, rule/read model calculation을 생성하거나 암시하지 않는다.
- Accepted bucket은 metric freshness/state/read model source-of-truth다.
- Dashboard current read model과 snapshot JSON은 `starterConnection.statusSource=starter_heartbeat`와 accepted bucket freshness/state를 별도 field/source로 유지해야 한다.
- 최근 heartbeat와 없음/오래된 accepted bucket 조합은 `starter connected but no accepted bucket`, `waiting for traffic`, `metric data idle` 계열로 표현할 수 있지만 host application down으로 단정하지 않는다.
- heartbeat도 끊기고 accepted bucket도 오래된 경우에도 host application down 원인은 확정하지 않는다.
- snapshot row 생성은 기존 dashboard snapshot/read model writer 경계를 사용한다. Heartbeat 수신 자체를 snapshot eligibility source로 만들지 않는다.

### Project Structure Notes

- Portal active architecture는 Traditional MVC + Service/Repository Layering이다.
- Portal source package는 feature-first MVC이며 `domain`은 business feature namespace다.
- Controller는 repository를 직접 호출하지 않고 service에 위임한다.
- Repository/JPA 구현은 controller/dto에 의존하지 않는다.
- JPA entity를 controller response DTO, public API surface, service external result로 직접 반환하지 않는다.
- 금지 package는 `application`, `port`, `adapter`, `adapter.in`, `adapter.out`다.
- smoke app은 별도 사용자 서비스 fixture이므로 portal feature package 구조를 따를 필요는 없지만, 자체 코드에도 AGENTS.md 한국어 주석 지침을 적용한다.

### Suggested Preflight Procedure

구현 결과는 아래처럼 opt-in command 하나로 수렴하는 것을 목표로 한다.

```bash
./scripts/preflight/starter-to-snapshot-smoke.sh
```

권장 runner 흐름:

1. local PostgreSQL 또는 Testcontainers 기반 DB를 준비하고 Flyway migration을 적용한다.
2. local/internal seed로 project row를 만든다. raw project key는 env var 또는 `.private` file에서 읽고 DB에는 BCrypt hash만 저장한다.
3. portal을 local random 또는 fixed port로 기동한다.
4. generated smoke app을 `observability-spring-boot-starter` dependency와 함께 기동한다.
5. smoke app config에는 `observation.heartbeat.portal-base-url`, `observation.heartbeat.project-key`, `observation.metric-flush.environment`가 들어간다.
6. `GET /smoke/ping`을 여러 번 호출해 HTTP observation을 만든다.
7. closed 30초 bucket flush가 끝난 뒤 heartbeat row와 accepted bucket row를 확인한다.
8. Project/Application id를 catalog에서 확인한 뒤 dashboard current API를 호출한다.
9. dashboard query fallback 또는 explicit test trigger로 snapshot row를 생성한다.
10. `dashboard_snapshots.read_model_json`에서 bounded read model shape와 axis separation을 검증한다.

실제 command는 구현 과정에서 repo toolchain에 맞춰 조정할 수 있지만, completion note에는 최종 command, 필요한 env var, 검증 assertion을 반드시 남긴다.

## Testing

Focused test 대상 후보:

- `StarterToSnapshotPreflightSmokeTest`
- `StarterAutoConfigurationHttpObservationBindingTest`
- `PortalMetricBucketClientAutoConfigurationTest`
- `StarterHeartbeatToPortalSmokeTest`
- `StarterMetricBucketToPortalSmokeTest`
- `DashboardSnapshotPreflightSmokeTest`
- `PreflightSecretExposureGuardTest`

필수 scenario:

- smoke app이 starter dependency를 붙인 상태로 기동된다.
- smoke app 사용자 코드에 custom metric client/binder bean이 없다.
- starter auto-configuration이 heartbeat sender, HTTP observation capture, bucket drain/flush path를 구성한다.
- heartbeat가 portal에 수신되고 `starter_heartbeat_telemetry`에 저장된다.
- smoke endpoint 호출 후 accepted metric bucket이 `accepted_metric_buckets`에 저장된다.
- accepted bucket persistence가 application/application instance catalog row와 연결된다.
- dashboard current API response에서 `starterConnection.statusSource=starter_heartbeat`와 metric state/freshness source가 분리된다.
- dashboard snapshot row가 query fallback 또는 explicit trigger로 생성된다.
- `dashboard_snapshots.read_model_json`은 raw explorer dump가 아니라 bounded dashboard read model shape다.
- snapshot JSON의 `starterConnection.statusSource=starter_heartbeat`와 `state`/`application.freshness`/accepted bucket evidence가 분리되어 있다.
- raw project key, OAuth token, service token, provider raw payload, secret이 response/log/error/assertion snapshot에 노출되지 않는다.
- public project creation API, `operational_events` table, raw explorer, endpoint timeseries API가 추가되지 않는다.

Suggested commands:

```bash
./gradlew :observability-spring-boot-starter:test --tests '*AutoConfiguration*'
./gradlew :observability-portal:test --tests '*DashboardSnapshot*'
./scripts/preflight/starter-to-snapshot-smoke.sh
git diff --check
```

`./scripts/preflight/starter-to-snapshot-smoke.sh`는 구현 결과로 추가되는 opt-in 절차다. 기본 `./gradlew test`가 매번 이 heavy smoke를 실행하도록 연결하지 않는다.

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- 2026-05-28: BMAD create-story workflow, project context, sprint status, Story 6.1 done 상태를 확인했다.
- 2026-05-28: 현재 branch가 `codex/story-6-0-preflight-integration-smoke`임을 확인했다.
- 2026-05-28: `implementation-artifacts/sprint-status.yaml`에 Story 6.0을 Epic 6 아래 6.1 위에 `ready-for-dev`로 삽입했고, Story 6.1 `done`과 Epic 6 `in-progress`는 유지했다.
- 2026-05-28: workflow note에 Story 6.0이 Story 6.1 완료 이후 추가된 일회성 preflight이며 반복 regression suite가 아님을 기록했다.
- 2026-05-28: Story 6.1 setup guide, Epic 6, ingest/read-model/starter failure semantics contract, Epic 5 retrospective, project context를 확인했다.
- 2026-05-28: starter auto-configuration imports, heartbeat client, metric drain config, portal heartbeat/ingest/dashboard/snapshot 경계를 확인했다.
- 2026-05-28: starter production source에서 metric bucket HTTP client auto-configuration과 HTTP observation binder runtime registration이 preflight 핵심 리스크임을 확인했다.
- 2026-05-28: 기존 untracked file `implementation-artifacts/epic-5-dbml-snapshot-2026-05-28.dbml`은 작업 범위와 무관하므로 건드리지 않았다.
- 2026-05-28: Checkpoint 1 Probe / RED 범위로 `smoke-tests/starter-to-snapshot/` 독립 Gradle smoke fixture를 추가했다.
- 2026-05-28: smoke app에는 `PortalMetricBucketClient`, `MicrometerHttpServerObservationBinder`, `ObservationHandler`, `ObservationRegistry` 사용자 custom bean을 추가하지 않았다.
- 2026-05-28: focused RED test에서 starter auto-configuration만으로 `PortalMetricBucketClient` bean과 `MicrometerHttpServerObservationBinder` runtime handler가 등록되지 않는 gap을 확인했다.
- 2026-05-28: Checkpoint 2 시작 시 focused smoke를 재실행해 `PortalMetricBucketClient` 0개, `MicrometerHttpServerObservationBinder` 0개 RED 상태를 재확인했다.
- 2026-05-28: starter production auto-configuration에서 `observation.heartbeat.portal-base-url`과 `observation.heartbeat.project-key`가 있을 때 `JdkPortalMetricBucketClient`를 등록하도록 보강했다.
- 2026-05-28: `StarterMetricIngestService`가 준비된 runtime에서 `MicrometerHttpServerObservationBinder`를 `ObservationHandler` bean으로 등록하도록 보강했다.
- 2026-05-28: Story 6.1 setup guide 핵심 속성과 충돌하지 않도록 portal metric flush identity 검증을 `observation.metric-flush.environment` 필수 검증으로 좁혔다.
- 2026-05-28: metric bucket HTTP client 실패 예외는 raw project key를 message/cause chain에 남기지 않도록 별도 예외로 감쌌다.
- 2026-05-28: Checkpoint 2 focused smoke와 starter focused tests가 production starter wiring 수정만으로 GREEN이 됨을 확인했다.
- 2026-05-28: Checkpoint 3에서 `portalE2eTest`가 heartbeat persistence, accepted bucket persistence, dashboard current HTTP GET, query fallback snapshot row, bounded `read_model_json` shape, UTC instant timestamp assertion까지 GREEN임을 확인했다.
- 2026-05-28: dashboard current HTTP GET은 portal `ServiceTokenIssuer` Bearer token으로 통과했고, public project creation API는 추가하지 않았다.
- 2026-05-28: Checkpoint 4에서 `scripts/preflight/starter-to-snapshot-smoke.sh` opt-in runner와 smoke README 실행/전제/실패 해석/secret 취급 문서를 추가했다.
- 2026-05-28: opt-in runner는 `--rerun-tasks`로 focused smoke와 portal E2E를 명시 재실행하며, secret 값을 입력받거나 출력하지 않는다.
- 2026-05-28: `./gradlew test`는 root product module 테스트만 대상으로 성공했고, `smoke-tests/starter-to-snapshot` 독립 build의 heavy smoke는 기본 경로에 포함되지 않음을 확인했다.

### Completion Notes List

- Checkpoint 1은 production starter wiring fix 없이 Probe / RED만 수행했다.
- smoke fixture는 root `settings.gradle` product module에 포함하지 않는 독립 Gradle build이므로 기본 `./gradlew test` 실행 경로에 heavy smoke가 자동 포함되지 않는다.
- focused RED command는 `./gradlew -p smoke-tests/starter-to-snapshot test --tests com.observation.smoke.startertosnapshot.StarterAutoConfigurationRequiredBeansSmokeTest`다.
- RED 결과는 `PortalMetricBucketClient` bean 0개, `MicrometerHttpServerObservationBinder` bean 0개로 실패하며, 다음 checkpoint에서 starter production auto-configuration 보강 범위를 판단할 수 있다.
- smoke app 설정은 Story 6.1 setup guide 핵심 속성인 `observation.heartbeat.portal-base-url`, `observation.heartbeat.project-key`, `observation.metric-flush.environment`만 사용한다.
- Checkpoint 2는 Starter Wiring Fix만 수행했고, smoke app에는 우회용 custom bean을 추가하지 않았다.
- `PortalMetricBucketClient`는 heartbeat portal base URL과 project key가 모두 설정되고 사용자 custom bean이 없을 때 starter가 `JdkPortalMetricBucketClient`로 자동 등록한다.
- `MicrometerHttpServerObservationBinder`는 `ObservationSampleCollector` runtime bean이 있을 때 `ObservationHandler` bean으로 등록되어 Spring Boot observation flow에 참여할 수 있다.
- Story 6.1 setup guide가 안내하지 않는 `observation.metric-flush.project-id`, `observation.metric-flush.application-name`, `observation.metric-flush.instance`를 필수로 요구하지 않도록 정리했다.
- Checkpoint 3는 Portal E2E assertions만 수행했고, heartbeat persistence와 accepted bucket persistence, dashboard current read model, query fallback snapshot, snapshot JSON axis separation까지 검증했다.
- focused test 실행 중 heartbeat connection failure WARN에는 endpoint alias와 failure category만 남고 raw project key 값은 노출되지 않았다.
- Checkpoint 4는 Opt-in Runner / Docs만 수행했고, production starter wiring이나 portal E2E 의미를 변경하지 않았다.
- 최종 opt-in command는 `./scripts/preflight/starter-to-snapshot-smoke.sh`다.
- runner가 실행하는 정확한 명령은 `./gradlew -p smoke-tests/starter-to-snapshot --rerun-tasks test --tests com.observation.smoke.startertosnapshot.StarterAutoConfigurationRequiredBeansSmokeTest`와 `./gradlew -p smoke-tests/starter-to-snapshot --rerun-tasks portalE2eTest --tests com.observation.smoke.startertosnapshot.StarterToPortalDashboardAxisSeparationE2ETest`다.
- `portalE2eTest`는 테스트 내부 seed로 BCrypt hash만 저장하고 raw project key가 response, snapshot JSON, log/exception output에 노출되지 않음을 assertion한다.
- Story 6.0은 one-time opt-in preflight로 완료했으며, Story 6.1 `done` 상태와 Epic 5 종료 상태는 변경하지 않았다.

### File List

- `planning-artifacts/stories/6-0-starter-to-snapshot-preflight-integration-smoke.md`
- `implementation-artifacts/sprint-status.yaml`
- `scripts/preflight/starter-to-snapshot-smoke.sh`
- `smoke-tests/starter-to-snapshot/settings.gradle`
- `smoke-tests/starter-to-snapshot/build.gradle`
- `smoke-tests/starter-to-snapshot/README.md`
- `smoke-tests/starter-to-snapshot/src/main/java/com/observation/smoke/startertosnapshot/StarterToSnapshotSmokeApplication.java`
- `smoke-tests/starter-to-snapshot/src/main/java/com/observation/smoke/startertosnapshot/SmokePingController.java`
- `smoke-tests/starter-to-snapshot/src/main/resources/application.properties`
- `smoke-tests/starter-to-snapshot/src/test/java/com/observation/smoke/startertosnapshot/StarterAutoConfigurationRequiredBeansSmokeTest.java`
- `smoke-tests/starter-to-snapshot/src/test/java/com/observation/smoke/startertosnapshot/StarterToPortalDashboardAxisSeparationE2ETest.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/client/JdkPortalMetricBucketClient.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/client/PortalMetricBucketException.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/config/HttpServerObservationAutoConfiguration.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/config/MetricDrainAutoConfiguration.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/config/MetricDrainProperties.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/spring/web/StarterHttpServerObservationFilter.java`
- `observability-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/client/JdkPortalMetricBucketClientTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/config/MetricDrainAutoConfigurationTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/architecture/StarterObservationArchitectureTest.java`

### Change Log

- 2026-05-28: Story 6.0 Starter-to-snapshot preflight integration smoke create-story 산출물을 생성했다.
- 2026-05-28: Checkpoint 1 Probe / RED smoke fixture와 focused auto-configuration gap test를 추가했다.
- 2026-05-28: Checkpoint 2 Starter Wiring Fix로 production starter auto-configuration의 metric bucket client와 HTTP observation handler wiring을 보강했다.
- 2026-05-28: Checkpoint 3 Portal E2E Assertions로 starter heartbeat, accepted bucket, dashboard current, snapshot read model axis separation 검증을 추가했다.
- 2026-05-28: Checkpoint 4 Opt-in Runner / Docs로 `scripts/preflight/starter-to-snapshot-smoke.sh`와 smoke README 절차를 추가하고 Story 6.0 완료 기록을 갱신했다.
