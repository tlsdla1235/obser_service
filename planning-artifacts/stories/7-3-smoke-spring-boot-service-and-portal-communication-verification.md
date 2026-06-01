---
artifactType: story
storyId: "7.3"
storyKey: "7-3-smoke-spring-boot-service-and-portal-communication-verification"
epic: "Epic 7. Real GitHub and Starter Smoke Verification"
title: "Smoke Spring Boot service and portal communication verification"
architectureStyle: Traditional MVC
status: done
date: 2026-06-01
---

# Story 7.3 - Smoke Spring Boot service and portal communication verification

## Status

done

## Story

local smoke operator는 repo 안의 전용 Spring Boot smoke service에 `observability-spring-boot-starter`를 실제 dependency로 붙여 portal과 통신하는 end-to-end 경로를 검증하고 싶다.

그래야 Story 7.1의 실제 GitHub OAuth account/project membership seed와 Story 7.2의 default bucket ingest HTTP client가 같은 local secret 경계 위에서 이어지고, heartbeat, accepted bucket, Project/Application navigation, Dashboard, Instance Evidence read API까지 운영자가 한 번의 local smoke 흐름으로 확인할 수 있다.

## Source of Truth

아래 문서를 읽고 반영한 BMAD create-story context다. 구현 중 충돌처럼 보이는 지점은 Epic 7 local/operator smoke validation 목표, Story 7.1 token/project-key handoff, Story 7.2 starter data-plane client, accepted bucket/heartbeat two-axis contract 순서로 우선한다.

1. `_bmad/custom/project-context.md`
2. `AGENTS.md`
3. `implementation-artifacts/sprint-status.yaml`
4. `planning-artifacts/epics.md`
5. `planning-artifacts/sprint-plan.md`
6. `planning-artifacts/architecture.md`
7. `planning-artifacts/architecture-implementation-supplement.md`
8. `planning-artifacts/project-structure.md`
9. `planning-artifacts/api-surface.md`
10. `planning-artifacts/contracts/ingest-envelope.md`
11. `planning-artifacts/contracts/state-semantics.md`
12. `planning-artifacts/contracts/read-model-contract.md`
13. `planning-artifacts/contracts/account-auth-policy.md`
14. `planning-artifacts/stories/2-1-micrometer-observation-binding.md`
15. `planning-artifacts/stories/2-4-async-flush-worker.md`
16. `planning-artifacts/stories/4-0-2-complete-starter-heartbeat.md`
17. `planning-artifacts/stories/6-8-demo-green-path.md`
18. `planning-artifacts/stories/6-9-failure-recovery-path-demo-hardening.md`
19. `planning-artifacts/stories/7-1-real-github-oauth-smoke-seed-and-operator-runbook.md`
20. `planning-artifacts/stories/7-2-starter-bucket-ingest-http-client.md`
21. `implementation-artifacts/real-github-oauth-smoke-runbook.md`

확인한 현재 코드:

1. `.gitignore`
2. `settings.gradle`
3. `build.gradle`
4. `observability-portal/build.gradle`
5. `observability-spring-boot-starter/build.gradle`
6. `scripts/smoke/write-smoke-auth-env.sh`
7. `scripts/smoke/verify-smoke-projects.sh`
8. `observability-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
9. `observability-spring-boot-starter/src/main/java/com/observation/starter/config/MetricDrainAutoConfiguration.java`
10. `observability-spring-boot-starter/src/main/java/com/observation/starter/config/MetricDrainProperties.java`
11. `observability-spring-boot-starter/src/main/java/com/observation/starter/config/StarterHeartbeatAutoConfiguration.java`
12. `observability-spring-boot-starter/src/main/java/com/observation/starter/config/HeartbeatProperties.java`
13. `observability-spring-boot-starter/src/main/java/com/observation/starter/spring/observation/MicrometerHttpServerObservationBinder.java`
14. `observability-spring-boot-starter/src/main/java/com/observation/starter/service/StarterMetricIngestService.java`
15. `observability-spring-boot-starter/src/main/java/com/observation/starter/spring/StarterMetricDrainScheduler.java`
16. `observability-spring-boot-starter/src/main/java/com/observation/starter/client/http/JdkPortalMetricBucketClient.java`
17. `observability-portal/src/main/java/com/observation/portal/domain/ingest/controller/IngestController.java`
18. `observability-portal/src/main/java/com/observation/portal/domain/ingest/controller/IngestHeartbeatController.java`
19. `observability-portal/src/main/java/com/observation/portal/domain/catalog/controller/ProjectNavigationController.java`
20. `observability-portal/src/main/java/com/observation/portal/domain/dashboard/controller/DashboardController.java`
21. `observability-portal/src/main/java/com/observation/portal/domain/instance/controller/InstanceEvidenceController.java`
22. `observability-portal/src/main/java/com/observation/portal/domain/catalog/model/ProjectNavigationReadModel.java`
23. `observability-portal/src/main/java/com/observation/portal/domain/catalog/model/ProjectApplicationNavigationReadModel.java`
24. `observability-portal/src/main/java/com/observation/portal/domain/dashboard/model/ApplicationDashboardReadModel.java`
25. `observability-portal/src/main/java/com/observation/portal/domain/instance/model/InstanceEvidenceReadModel.java`
26. `observability-portal/src/test/java/com/observation/portal/architecture/MvcLayerBoundaryTest.java`
27. `observability-spring-boot-starter/src/test/java/com/observation/starter/architecture/StarterObservationArchitectureTest.java`
28. `observability-spring-boot-starter/src/test/java/com/observation/starter/architecture/NoPrometheusMvpPathTest.java`

외부 최신 참고:

1. Spring Boot 4.0.6 Gradle Plugin: https://docs.spring.io/spring-boot/4.0/gradle-plugin/index.html
2. Spring Boot Gradle `bootRun` 실행 문서: https://docs.spring.io/spring-boot/gradle-plugin/running.html
3. Spring Boot Gradle executable archive 문서: https://docs.spring.io/spring-boot/gradle-plugin/packaging.html
4. Spring Boot Observability 문서: https://docs.spring.io/spring-boot/reference/actuator/observability.html
5. Micrometer Observation introduction: https://docs.micrometer.io/micrometer/reference/observation/introduction.html

## Scope / Out of Scope

포함:

- repo 안의 smoke 전용 Spring Boot runtime module 추가. 권장 module name은 `observability-smoke-service`다.
- smoke module은 production portal/starter 배포 artifact와 혼동되지 않도록 description, package, README/runbook 문구에서 smoke-only임을 명확히 한다.
- smoke module은 `observability-spring-boot-starter`를 project dependency로 사용한다.
- smoke module은 host app 역할이므로 `spring-boot-starter-web`을 사용할 수 있다.
- Spring MVC HTTP observation이 실제 starter collector로 들어가게 필요한 최소 wiring을 닫는다.
- 현재 starter auto-configuration이 `MicrometerHttpServerObservationBinder`를 bean으로 등록하지 않는다면, smoke module에서 수동으로 우회하지 말고 starter auto-configuration에 최소 bean wiring을 추가한다.
- starter auto-configuration 보강이 필요하더라도 starter에는 Spring Web/WebFlux/Actuator/Prometheus registry dependency를 추가하지 않는다.
- smoke module이 Spring Boot observation infrastructure를 위해 추가 dependency가 필요하면 smoke module에만 둔다. starter/portal classpath로 번지지 않게 한다.
- `/smoke/ok`, `/smoke/slow`, `/smoke/error-candidate` traffic endpoint를 둔다.
- primary completion path는 `/smoke/ok` 중심 green/no-triage baseline이다.
- `/smoke/slow`와 `/smoke/error-candidate`는 troubleshooting/failure candidate용이며 story completion을 degraded/error path에 의존하지 않는다.
- smoke service config는 portal URL, raw project key, application/environment/instance identity를 local config 또는 env로 받는다.
- metric flush 설정은 `observation.metric-flush.*`, heartbeat 설정은 `observation.heartbeat.*`로 분리한다.
- raw project key는 starter `X-OBS-Project-Key` 경계에서만 사용한다.
- `.private/smoke-auth.env`의 service Bearer access token은 portal resource read API 검증에만 사용한다.
- operator runbook 또는 script는 portal 실행, smoke service 실행, traffic generation, 30초 bucket closure wait, heartbeat 확인, accepted bucket 확인, Project -> Application List -> Dashboard -> Instance Evidence read API 확인 순서를 다룬다.
- scripts/runbook은 token/key 값을 stdout/stderr/log/response fixture에 남기지 않는다.
- read API verification은 `Authorization: Bearer <access_token>` header만 사용하고 browser storage나 URL token parsing을 만들지 않는다.
- Story 7.1 runbook에 Story 7.3 실행/검증 절차를 placeholder 중심으로 보강한다.
- 새 public class/method/helper/script에는 AGENTS.md 기준 한국어 Javadoc/comment를 작성한다.

제외:

- public project creation API, Create Project UI, project key issuance/rotation UI
- invite/admin/team/billing/tenant surface
- browser `localStorage`, `sessionStorage`, cookie token 저장, URL token parsing
- GitHub provider token, provider raw payload, OAuth credential 저장 또는 출력
- `.private/smoke-auth.env`에 refresh token 저장
- `.private/smoke-project.env`의 raw project key를 portal resource API Bearer token으로 사용하는 흐름
- `.private/smoke-auth.env`의 service Bearer token을 starter metric flush 또는 heartbeat project key로 사용하는 흐름
- production portal module이 smoke service에 의존하는 구조
- starter module이 smoke service나 Spring MVC controller에 의존하는 구조
- starter에 Spring Web/WebFlux/Actuator/Prometheus registry dependency 추가
- durable outbox, Kafka, Redis, disk spool
- request path에서 portal network call, retry, backoff, flush 실행
- heartbeat를 accepted bucket/catalog/dashboard snapshot/operational event/source-of-truth로 취급하는 구현
- dashboard read model, lifecycle state, endpoint priority, p95/p99, instance evidence 계산 logic 변경
- raw bucket explorer, arbitrary query UI, Prometheus scrape/query path
- live GitHub OAuth 자동화 테스트. 실제 로그인은 Story 7.1 runbook의 operator manual step으로 유지한다.

## Acceptance Criteria

1. Story 7.3 story file은 `ready-for-dev` 상태로 생성되고 `sprint-status.yaml`의 `7-3-smoke-spring-boot-service-and-portal-communication-verification`도 `ready-for-dev`로 갱신된다.
2. Smoke runtime은 repo 안의 별도 module로 추가한다. 권장 이름은 `observability-smoke-service`다.
3. `settings.gradle`에는 smoke module이 명시적으로 include되지만, portal/starter module이 smoke module에 의존하지 않는다.
4. Smoke module `build.gradle`은 Gradle Groovy DSL과 root Spring Boot plugin/BOM pattern을 따른다.
5. Smoke module은 `org.springframework.boot` plugin을 사용해 `bootRun`으로 local 실행 가능해야 한다.
6. Smoke module은 `implementation project(':observability-spring-boot-starter')`로 starter를 실제 dependency로 사용한다.
7. Smoke module은 host app 역할로 `spring-boot-starter-web`을 사용할 수 있다.
8. Smoke module dependency는 starter classpath에 Spring Web/WebFlux/Actuator/Prometheus registry를 추가하지 않는다.
9. Smoke module description, package, README/runbook은 smoke-only local/operator runtime임을 드러낸다.
10. Smoke module package는 `com.observation.smoke` 또는 동등한 smoke-only namespace를 사용하고 portal/starter package와 섞지 않는다.
11. Smoke service main class는 Spring Boot application entrypoint만 맡고 production portal runtime behavior를 포함하지 않는다.
12. Smoke endpoint controller는 `/smoke/ok`, `/smoke/slow`, `/smoke/error-candidate`만 최소 제공한다.
13. `/smoke/ok`는 bounded fast 200 response를 반환하고 primary green-path traffic source로 사용된다.
14. `/smoke/slow`는 bounded delay를 만든 뒤 200 response를 반환하되 무한 sleep이나 flaky timing을 만들지 않는다.
15. `/smoke/error-candidate`는 의도적 5xx 또는 handled error candidate를 만들되 primary completion path가 이 endpoint에 의존하지 않는다.
16. Smoke endpoint response body와 log에는 raw project key, access token, refresh token, provider token, OAuth credential이 포함되지 않는다.
17. Smoke endpoint implementation은 portal client, bucket flush worker, envelope builder, queue flush를 직접 호출하지 않는다.
18. HTTP request path는 Spring MVC request handling과 starter observation/queue enqueue까지만 수행한다.
19. Portal bucket ingest HTTP call은 Story 7.2의 `MetricBucketFlushWorker` background worker와 `JdkPortalMetricBucketClient` 경계에서만 수행된다.
20. Smoke service는 custom `PortalMetricBucketClient` bean을 만들지 않는다.
21. Smoke service는 custom envelope builder나 idempotency generator를 만들지 않고 starter auto-configuration output을 사용한다.
22. Smoke service가 starter sample collection을 작동시키기 위해 필요한 ObservationHandler wiring은 starter auto-configuration에 둔다.
23. 현재 starter가 `MicrometerHttpServerObservationBinder` bean을 자동 등록하지 않는다면, 기존 auto-configuration imports 경계 안에서 bean을 추가한다.
24. `MicrometerHttpServerObservationBinder` bean 등록은 `ObservationHandler<Observation.Context>` 또는 동등 Micrometer boundary로 노출되어 Spring Boot가 `ObservationRegistry`에 자동 등록할 수 있어야 한다.
25. Observation handler wiring은 `StarterMetricIngestService` 또는 `ObservationSampleCollector` bean이 있을 때만 등록된다.
26. Observation handler wiring은 starter에 Spring Web, WebFlux, Actuator, Prometheus registry dependency를 추가하지 않는다.
27. Observation handler wiring은 request path에서 network call을 하지 않는 기존 architecture guard를 약화하지 않는다.
28. Smoke module이 Spring Boot observation infrastructure를 위해 actuator가 필요하다고 판단되면 smoke module에만 추가하고, starter/portal dependency로 올리지 않는다.
29. Smoke module은 `spring.application.name`과 `observation.metric-flush.application-name`을 명확히 맞추거나, starter fallback이 같은 application identity를 만들도록 문서화한다.
30. Smoke service local config는 `observation.metric-flush.project-id`를 raw project key가 아닌 idempotency identity로 둔다.
31. Smoke service local config는 `observation.metric-flush.project-key=${OBSERVATION_SMOKE_PROJECT_KEY}` 같은 placeholder로 raw project key를 받는다.
32. Smoke service local config는 `observation.metric-flush.portal-base-url=${OBSERVATION_PORTAL_BASE_URL:http://localhost:8080}` 또는 동등 설정을 제공한다.
33. Smoke service local config는 `observation.metric-flush.environment=local-smoke`와 non-default instance identity를 제공한다.
34. Smoke service local config는 `observation.metric-flush.timeout-millis`를 bounded positive value로 둔다.
35. Smoke service heartbeat config는 `observation.heartbeat.portal-base-url`와 `observation.heartbeat.project-key`를 별도로 둔다.
36. Heartbeat config는 metric flush config로 암묵 fallback하지 않는다.
37. Metric flush config는 heartbeat config로 암묵 fallback하지 않는다.
38. `.private/smoke-project.env` 또는 shell env에는 raw project key만 들어가고 repository에 커밋되지 않는다.
39. `.private/smoke-auth.env`의 `OBSERVATION_SMOKE_ACCESS_TOKEN`은 starter config에 사용하지 않는다.
40. Raw project key는 portal resource API `Authorization` header로 사용하지 않는다.
41. Service Bearer access token은 starter `X-OBS-Project-Key`로 사용하지 않는다.
42. Operator runbook은 portal 실행, smoke seed, smoke service 실행, traffic generation, bucket closure wait, read API verification 순서를 하나의 흐름으로 제공한다.
43. Runbook은 실제 credential/token/raw project key 값을 쓰지 않고 placeholder만 사용한다.
44. Runbook은 `.private/smoke-auth.env`와 `.private/smoke-project.env` 인증 경계를 다시 분리해 설명한다.
45. Runbook은 heartbeat-only, first accepted bucket/insufficient sample, active/no-triage baseline 중 관찰 가능한 단계를 설명한다.
46. Heartbeat-only 단계는 application catalog가 아직 없으면 read API로 보이지 않을 수 있음을 설명하고, heartbeat를 accepted bucket 생성으로 해석하지 않는다.
47. First accepted bucket 이후 verification은 `GET /api/projects`에서 smoke project와 applications link를 찾는다.
48. Verification은 `GET /api/projects/{projectId}/applications`에서 smoke application/environment 항목과 dashboard link를 찾는다.
49. Verification은 `GET /api/projects/{projectId}/applications/{applicationId}/dashboard`에서 `application.lastAcceptedBucketAt`, `starterConnection.statusSource=starter_heartbeat`, `starterConnection.stateImpact=none`, `zeroInsight` 또는 bounded `triageCards` shape를 확인한다.
50. Green path verification은 `state.code`가 `waiting_first_data`, `unknown`, `idle`, `active` 중 관찰 가능한 초기 상태일 수 있음을 허용하되, host application down 확정 문구를 성공 조건으로 삼지 않는다.
51. 충분한 `/smoke/ok` traffic과 30초 bucket closure 이후 active/no-triage baseline을 확인할 수 있으면 `zeroInsight.reasonCode`는 `no_action_needed`, `insufficient_sample`, `metric_data_idle` 등 계약된 값 안에 있어야 한다.
52. Verification은 dashboard response의 `instances[]` 또는 Application List/read model에서 instance evidence link를 얻어 Instance Evidence API를 호출한다.
53. Verification은 `GET /api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/evidence`에서 `metricData.statusSource=accepted_bucket`, `starterConnection.statusSource=starter_heartbeat`, `starterConnection.stateImpact=none`을 확인한다.
54. Instance Evidence verification은 source-scoped starter percentile series가 missing/insufficient/available 중 계약된 status로 내려오는지만 확인하고 새 p95/p99 계산을 만들지 않는다.
55. Scripts는 `jq`가 없으면 명확히 실패하고 secret 값을 출력하지 않는다.
56. Scripts는 `.private/smoke-auth.env`를 shell `source`하지 않고 단일 key/value file로 파싱한다.
57. Scripts가 `.private/smoke-project.env`를 읽는다면 raw key 값을 stdout/stderr에 출력하지 않고, starter 실행용 env로만 사용한다.
58. Scripts는 `curl -v`, shell `set -x`, token echo 같은 secret leak 위험 옵션을 사용하지 않는다.
59. Scripts는 response body 전체를 실패 메시지에 그대로 출력하지 않고 필요한 shape mismatch만 일반화해서 보고한다.
60. Scripts는 traffic generation 횟수, smoke service base URL, portal base URL, wait seconds를 env로 override할 수 있다.
61. Scripts는 default wait를 30초 bucket closure와 scheduler initial delay를 고려한 값으로 둔다. 권장 기본값은 35~45초 이상이다.
62. Scripts는 portal down, smoke service down, missing token, missing project key, project not found, application not found, dashboard 404, evidence 404를 구분해 troubleshooting hint를 남긴다.
63. Tests는 smoke module context가 starter dependency와 함께 뜨고 smoke endpoints가 expected HTTP status를 반환함을 검증한다.
64. Tests는 smoke module이 custom `PortalMetricBucketClient` bean으로 Story 7.2 default client를 우회하지 않음을 검증한다.
65. Tests는 starter ObservationHandler bean wiring이 request path network boundary를 깨지 않는지 검증한다.
66. Tests는 scripts/runbook/static artifact에 forbidden token/key/credential literal이 없는지 검증한다.
67. Existing `StarterObservationArchitectureTest`, `NoPrometheusMvpPathTest`, `MetricDrainAutoConfigurationTest`, `StarterNonBlockingIngestTest`는 계속 통과한다.
68. Existing portal `MvcLayerBoundaryTest`, account/project authorization tests, dashboard/instance read model tests는 계속 통과한다.
69. Full `:observability-smoke-service:test`, relevant starter tests, relevant portal tests, full `./gradlew test`, `git diff --check`가 통과해야 implementation completion으로 볼 수 있다.
70. 새 public class/method/helper 또는 동작을 바로 이해하기 어려운 내부 helper/script에는 AGENTS.md 지침에 따라 한국어 Javadoc/comment를 작성한다.

## Tasks / Subtasks

- [x] Smoke module 추가 (AC: 2~11, 63, 69~70)
  - [x] `settings.gradle`에 `observability-smoke-service`를 include한다.
  - [x] `observability-smoke-service/build.gradle`을 Gradle Groovy DSL로 추가한다.
  - [x] Smoke module은 `project(':observability-spring-boot-starter')`와 Spring Boot web runtime을 사용한다.
  - [x] Smoke-only package와 main application class를 만든다.
  - [x] smoke module이 portal/starter의 production dependency가 되지 않음을 확인한다.

- [x] Starter observation wiring gap 닫기 (AC: 22~28, 63~67, 70)
  - [x] 현재 starter auto-configuration이 `MicrometerHttpServerObservationBinder`를 bean으로 등록하는지 확인한다.
  - [x] 등록되어 있지 않다면 starter auto-configuration에 `ObservationHandler` bean을 추가한다.
  - [x] `StarterMetricIngestService` 또는 `ObservationSampleCollector`가 있을 때만 binder가 등록되게 한다.
  - [x] starter에 Spring Web/WebFlux/Actuator/Prometheus dependency를 추가하지 않는다.
  - [x] focused test로 smoke/host app context에서 observation handler가 등록되고 request path network call은 하지 않음을 확인한다.

- [x] Smoke traffic endpoints 구현 (AC: 12~21, 63~65, 70)
  - [x] `/smoke/ok` 200 endpoint를 추가한다.
  - [x] `/smoke/slow` bounded delay 200 endpoint를 추가한다.
  - [x] `/smoke/error-candidate` intentional error candidate endpoint를 추가한다.
  - [x] endpoints가 portal client/flush/envelope code를 직접 호출하지 않게 한다.
  - [x] response/log에 secret 값이 없음을 테스트한다.

- [x] Smoke local config와 secret handoff 정리 (AC: 29~41, 66, 70)
  - [x] smoke profile 또는 application properties에 metric flush identity를 non-default 값으로 둔다.
  - [x] `OBSERVATION_PORTAL_BASE_URL`와 `OBSERVATION_SMOKE_PROJECT_KEY` placeholder를 사용한다.
  - [x] metric flush와 heartbeat 설정을 별도 namespace로 둔다.
  - [x] `.private/smoke-auth.env` Bearer token과 `.private/smoke-project.env` raw project key 경계를 문서와 script에서 분리한다.

- [x] Operator runbook/script 보강 (AC: 42~62, 66, 70)
  - [x] `implementation-artifacts/real-github-oauth-smoke-runbook.md`에 Story 7.3 section을 추가한다.
  - [x] 필요하면 `scripts/smoke/run-smoke-traffic.sh`와 `scripts/smoke/verify-smoke-portal-flow.sh`를 추가한다.
  - [x] verification script는 project list -> application list -> dashboard -> instance evidence 순서로 read API를 확인한다.
  - [x] script는 token/key를 출력하지 않고 response shape mismatch만 일반화해 보고한다.
  - [x] heartbeat-only/first bucket/active baseline의 관찰 가능성과 한계를 runbook에 설명한다.

- [x] Regression tests와 guard 실행 (AC: 63~70)
  - [x] `:observability-smoke-service:test`를 추가/실행한다.
  - [x] starter focused tests와 no-prometheus guard를 실행한다.
  - [x] portal authorization/read-model focused tests를 실행한다.
  - [x] full `./gradlew test`와 `git diff --check`를 실행한다.

## Dev Notes

### Current Code State

- Root build는 `settings.gradle`, `build.gradle` 기반 Gradle Groovy DSL multi-module 구조다. 현재 include module은 `observability-portal`, `observability-spring-boot-starter` 두 개다.
- Root `build.gradle`은 Spring Boot plugin `4.0.6`을 apply false로 선언하고 Java toolchain/release를 17로 고정한다.
- `observability-spring-boot-starter`는 `java-library` module이며 Spring Web/WebFlux/Actuator/Prometheus registry dependency가 없다.
- `observability-portal`은 Spring Boot web/JPA runtime module이며 portal controller/read model/persistence를 포함한다.
- `.private/`는 `.gitignore`에 포함되어 있으므로 local-only token/project key memo는 여기 아래에 둔다.
- Story 7.1에서 `scripts/smoke/write-smoke-auth-env.sh`와 `scripts/smoke/verify-smoke-projects.sh`가 추가됐다. 기존 script는 `.private/smoke-auth.env`를 단일 key/value file로 읽고 Bearer token을 출력하지 않는다.
- Story 7.2에서 `JdkPortalMetricBucketClient`가 구현됐다. It sends `POST /api/ingest/v1/buckets` with `X-OBS-Project-Key`, `Idempotency-Key`, JSON body, bounded timeout.
- Story 7.2에서 metric flush settings는 `observation.metric-flush.portal-base-url`, `observation.metric-flush.project-key`, `observation.metric-flush.timeout-millis`로 닫혔다.
- `MetricDrainProperties.projectId`는 idempotency key component다. Raw project key가 아니다.
- `MetricDrainAutoConfiguration`은 metric flush portal connection settings가 있을 때만 default `PortalMetricBucketClient`를 만들고, worker 시작 직전에 generic identity를 reject한다.
- `StarterHeartbeatAutoConfiguration`은 heartbeat connection settings가 없으면 no-op heartbeat client를 만들고, 설정이 있으면 `POST /api/ingest/v1/heartbeat` client를 만든다.
- Heartbeat와 bucket ingest는 endpoint, client, properties, retry 의미가 분리되어 있다.
- `MicrometerHttpServerObservationBinder` class는 존재하지만 현재 확인한 auto-configuration imports에는 binder-specific auto-configuration이 없다. Smoke service가 실제 starter dependency만으로 request observations를 bucket으로 만들려면 이 wiring gap을 먼저 확인해야 한다.
- Spring Boot Observability 공식 문서는 `ObservationHandler` bean이 `ObservationRegistry`에 자동 등록된다고 설명한다. 따라서 binder를 host app custom code가 아니라 starter auto-configuration bean으로 노출하는 것이 smoke 목표와 맞다.
- Micrometer 공식 문서는 observation이 처리되려면 `ObservationHandler`가 `ObservationRegistry`에 등록되어야 한다고 설명한다.

### Proposed File Shape

권장 신규/변경 파일:

- `settings.gradle`
- `observability-smoke-service/build.gradle`
- `observability-smoke-service/src/main/java/com/observation/smoke/SmokeServiceApplication.java`
- `observability-smoke-service/src/main/java/com/observation/smoke/web/SmokeTrafficController.java`
- `observability-smoke-service/src/main/resources/application-local-smoke.properties`
- `observability-smoke-service/src/test/java/com/observation/smoke/SmokeServiceApplicationTest.java`
- `observability-smoke-service/src/test/java/com/observation/smoke/web/SmokeTrafficControllerTest.java`
- optional `observability-spring-boot-starter/src/main/java/com/observation/starter/config/ObservationBindingAutoConfiguration.java`
- optional update `observability-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- optional starter test for binder auto-configuration
- `scripts/smoke/run-smoke-traffic.sh`
- `scripts/smoke/verify-smoke-portal-flow.sh`
- `implementation-artifacts/real-github-oauth-smoke-runbook.md`

권장 smoke config shape:

```properties
spring.application.name=observation-smoke-service
server.port=${OBSERVATION_SMOKE_SERVICE_PORT:8081}

observation.metric-flush.project-id=local-smoke-project-id
observation.metric-flush.application-name=observation-smoke-service
observation.metric-flush.environment=local-smoke
observation.metric-flush.instance=${OBSERVATION_SMOKE_INSTANCE:observation-smoke-local-1}
observation.metric-flush.portal-base-url=${OBSERVATION_PORTAL_BASE_URL:http://localhost:8080}
observation.metric-flush.project-key=${OBSERVATION_SMOKE_PROJECT_KEY}
observation.metric-flush.timeout-millis=1000

observation.heartbeat.portal-base-url=${OBSERVATION_PORTAL_BASE_URL:http://localhost:8080}
observation.heartbeat.project-key=${OBSERVATION_SMOKE_PROJECT_KEY}
observation.heartbeat.interval-seconds=30
observation.heartbeat.timeout-millis=1000
```

If Spring Boot observation infrastructure requires an extra runtime dependency, add it only to `observability-smoke-service`. Do not add it to `observability-spring-boot-starter`.

### Verification Flow

권장 operator 순서:

1. Story 7.1 runbook으로 GitHub OAuth login, `.private/smoke-auth.env`, local smoke seed를 완료한다.
2. `.private/smoke-project.env` 또는 shell env에 `OBSERVATION_SMOKE_PROJECT_KEY=<key_prefix>.<secret>`를 준비한다.
3. Portal을 local smoke profile로 실행한다.
4. Smoke service를 local smoke profile로 실행한다.
5. `/smoke/ok` traffic을 여러 번 만든다.
6. 30초 bucket closure와 scheduler initial delay를 고려해 35~45초 이상 기다린다.
7. `GET /api/projects`로 smoke project visibility를 확인한다.
8. `GET /api/projects/{projectId}/applications`로 smoke application row와 dashboard link를 확인한다.
9. `GET /api/projects/{projectId}/applications/{applicationId}/dashboard`로 accepted bucket axis와 starter heartbeat axis가 함께 표시되는지 확인한다.
10. Dashboard response에서 instance id/link를 찾아 Instance Evidence API를 확인한다.
11. 실패 시 portal down, smoke service down, project key mismatch, token missing/expired, bucket not closed yet, observation handler missing, application identity generic default rejection을 나눠 기록한다.

### Two-Axis Guardrails

- Heartbeat received는 starter/application process liveness, portal reachability, project key validity, metadata validity의 control-plane source다.
- Accepted bucket은 application metric freshness/state/read-model source-of-truth다.
- Heartbeat success는 accepted bucket, application catalog row, dashboard snapshot, operational event, p95/p99, rule/read-model calculation을 만들지 않는다.
- Accepted bucket success는 host application normal/down/recovered 확정이 아니다.
- 최근 heartbeat + accepted bucket 없음/오래됨은 waiting for traffic, metric data idle, starter connected but no accepted bucket 계열로 표현한다.
- Heartbeat도 없고 accepted bucket도 없거나 오래된 경우도 host application down 원인은 미확정이다.
- Verification copy는 "앱 정상 확정", "앱 내려감 확정", "복구 완료 확정"을 만들지 않는다.

### Previous Story Intelligence

- Story 7.1은 `.private/smoke-auth.env` access token과 `.private/smoke-project.env` raw project key 경계를 분리했다. Story 7.3 scripts/runbook은 이 경계를 유지한다.
- Story 7.1 seed는 public project creation을 열지 않고 active account-project membership을 local-only로 만든다. Story 7.3은 seed 누락을 Create Project flow로 연결하지 않는다.
- Story 7.2는 default bucket ingest client를 starter에 추가했다. Story 7.3은 custom smoke `PortalMetricBucketClient` bean을 만들지 말고 default client를 실제로 검증한다.
- Story 7.2는 metric flush 설정과 heartbeat 설정의 fallback 금지를 닫았다. Story 7.3 config도 두 namespace를 명시적으로 모두 설정한다.
- Story 2.1은 Micrometer binder class를 만들었지만 당시 scope는 synthetic observation binding이었다. Story 7.3은 real Spring Boot web request path에서 binder가 자동 등록되는지 검증해야 한다.
- Story 2.4/7.2는 request path network call 금지와 background worker boundary를 고정했다. Smoke endpoints는 이 경계를 깨지 않는다.
- Story 6.8/6.9는 green path와 failure/recovery demo copy에서 accepted bucket axis와 starter heartbeat axis를 분리했다. Story 7.3 verification도 같은 language를 사용한다.

### Git Intelligence

최근 commit 흐름:

- `8f11f39 feat(starter): add bucket ingest HTTP client`
- `922e6f3 Complete story 7.1 GitHub OAuth smoke seed`
- `1e20db6 Close out Epic 6 retrospective`
- `18b0524 Merge story 6.9 failure recovery hardening`
- `7e3b1d5 Complete story 6.9 failure recovery hardening`

Story 7.3 구현자는 Story 7.1/7.2의 local secret redaction, default starter client, Epic 6 two-axis read model semantics를 이어가야 한다.

### Latest Technical Notes

- 현재 repo는 Spring Boot Gradle plugin `4.0.6`을 사용한다. Smoke module도 root plugin/BOM pattern을 재사용한다.
- Spring Boot Gradle plugin 공식 문서는 Spring Boot app을 `bootRun`으로 실행하고 executable jar를 `bootJar`로 만들 수 있음을 설명한다. Smoke service는 local operator용이므로 `bootRun` 중심 runbook을 제공한다.
- Spring Boot Observability 공식 문서는 `ObservationHandler` bean이 `ObservationRegistry`에 자동 등록된다고 설명한다. Starter binder wiring은 이 pattern을 사용한다.
- Micrometer 공식 문서는 observation 처리를 위해 `ObservationHandler`를 `ObservationRegistry`에 등록해야 한다고 설명한다. 현재 binder class 존재만으로는 real web request smoke가 충분하지 않을 수 있으므로 context test로 검증한다.

## Testing

Focused test 대상 후보:

- `:observability-smoke-service:test`
- `SmokeTrafficControllerTest`
- smoke application context test
- optional starter observation binding auto-configuration test
- `MetricDrainAutoConfigurationTest`
- `StarterObservationArchitectureTest`
- `NoPrometheusMvpPathTest`
- `StarterNonBlockingIngestTest`
- `AuthSecretExposureGuardTest` 또는 동등 smoke artifact secret guard
- `ProjectNavigationResourceAuthorizationTest`
- dashboard/instance evidence focused read model tests

필수 scenario:

- Smoke module context가 starter dependency와 함께 load된다.
- `/smoke/ok`, `/smoke/slow`, `/smoke/error-candidate`가 expected status를 반환한다.
- Smoke endpoint request path가 `PortalMetricBucketClient`, `JdkPortalMetricBucketClient`, `MetricBucketFlushWorker`를 직접 호출하지 않는다.
- Starter observation handler가 application context에서 등록된다.
- Connection settings가 있으면 Story 7.2 default `JdkPortalMetricBucketClient`가 사용된다.
- Custom `PortalMetricBucketClient` bean으로 smoke path를 우회하지 않는다.
- Generic default identity가 남아 있으면 worker startup guard가 실패한다.
- Smoke config는 non-default `project-id`, application, environment, instance를 제공한다.
- Scripts는 token/key 값을 출력하지 않고 `.private/smoke-auth.env`를 단일 key/value로 파싱한다.
- Scripts는 Project -> Application -> Dashboard -> Instance Evidence read API response shape를 확인한다.
- Starter classpath에는 Spring Web/WebFlux/Actuator/Prometheus registry dependency가 추가되지 않는다.
- Portal/starter가 smoke module에 의존하지 않는다.

Suggested commands:

```bash
./gradlew :observability-smoke-service:test
./gradlew :observability-spring-boot-starter:test --tests '*MetricDrainAutoConfiguration*'
./gradlew :observability-spring-boot-starter:test --tests '*StarterNonBlockingIngest*'
./gradlew :observability-spring-boot-starter:test --tests com.observation.starter.architecture.StarterObservationArchitectureTest
./gradlew :observability-spring-boot-starter:test --tests com.observation.starter.architecture.NoPrometheusMvpPathTest
./gradlew :observability-portal:test --tests '*AuthSecretExposure*'
./gradlew :observability-portal:test --tests '*ProjectNavigationResourceAuthorization*'
./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest
./gradlew test
git diff --check
```

Manual smoke command candidates:

```bash
./gradlew :observability-portal:bootRun --args='--spring.profiles.active=local-smoke'
OBSERVATION_PORTAL_BASE_URL=http://localhost:8080 \
OBSERVATION_SMOKE_PROJECT_KEY=<key_prefix>.<secret> \
./gradlew :observability-smoke-service:bootRun --args='--spring.profiles.active=local-smoke'
OBSERVATION_SMOKE_SERVICE_BASE_URL=http://localhost:8081 scripts/smoke/run-smoke-traffic.sh
OBSERVATION_PORTAL_BASE_URL=http://localhost:8080 scripts/smoke/verify-smoke-portal-flow.sh
```

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- 2026-06-01: BMAD create-story workflow, sprint status, Epic 7 source artifacts, Story 7.1/7.2 handoff, starter/portal read API code를 확인했다.
- 2026-06-01: Story 7.3 target key를 `7-3-smoke-spring-boot-service-and-portal-communication-verification`로 확정했다.
- 2026-06-01: repo module vs external sample open decision은 local/operator reproducibility를 위해 `observability-smoke-service` repo module로 닫았다.
- 2026-06-01: `MicrometerHttpServerObservationBinder` class는 존재하지만 auto-configuration imports에 binder wiring이 없는 것을 확인해 Story 7.3 implementation guardrail에 포함했다.
- 2026-06-01: Spring Boot Gradle plugin, bootRun/bootJar, Spring Boot Observability, Micrometer ObservationHandler 공식 문서를 확인했다.
- 2026-06-01: Story 7.3 story file을 생성하고 ready-for-dev 상태로 전환했다.
- 2026-06-01: BMAD dev-story 실행을 시작하고 sprint status를 `in-progress`로 전환했다.
- 2026-06-01: RED 단계에서 `:observability-smoke-service:test`는 module 미등록으로 실패했고, `*ObservationBindingAutoConfiguration*`은 미구현 class로 compile failure가 발생함을 확인했다.
- 2026-06-01: GREEN 단계에서 `observability-smoke-service` module, smoke endpoint/config/tests, starter `ObservationBindingAutoConfiguration`, Story 7.3 runbook/scripts를 구현했다.
- 2026-06-01: `:observability-smoke-service:test`에서 Boot 4 test classpath에 맞춰 endpoint test를 standalone MockMvc로 정리하고 context test는 full Spring Boot context로 유지했다.
- 2026-06-01: focused starter/portal tests, full `./gradlew test`, `git diff --check` 통과를 확인했다.

### Completion Notes List

- Story 7.3은 repo-local smoke module 방식으로 작성했다.
- Story 7.3은 starter default bucket ingest client를 custom smoke client 없이 검증하도록 제한했다.
- Story 7.3은 observation binder auto-wiring gap을 개발자가 놓치지 않도록 명시했다.
- Story 7.3은 `.private/smoke-auth.env` Bearer token과 `.private/smoke-project.env` raw project key의 인증 경계를 유지한다.
- Story 7.3은 accepted bucket metric axis와 starter heartbeat axis를 end-to-end read API 검증에서도 분리한다.
- `observability-smoke-service`를 Gradle Boot app module로 추가하고 `implementation project(':observability-spring-boot-starter')`와 `spring-boot-starter-web`만 사용해 local smoke host app 역할로 분리했다.
- `/smoke/ok`, `/smoke/slow`, `/smoke/error-candidate` endpoint는 bounded response만 반환하며 portal client, flush worker, envelope builder, idempotency generator를 직접 호출하지 않는다.
- `ObservationBindingAutoConfiguration`을 starter imports에 추가해 `ObservationSampleCollector`가 있을 때만 `MicrometerHttpServerObservationBinder`를 `ObservationHandler<Observation.Context>` bean으로 노출한다.
- Smoke `application-local-smoke.properties`는 metric flush와 heartbeat namespace를 분리하고 `OBSERVATION_SMOKE_PROJECT_KEY` placeholder를 raw project key 경계로만 사용한다.
- Story 7.3 runbook section과 `run-smoke-traffic.sh`, `verify-smoke-portal-flow.sh`는 token/key를 출력하지 않고 Project -> Application -> Dashboard -> Instance Evidence read API shape를 검증한다.
- Smoke/static guard와 `AuthSecretExposureGuardTest`를 보강해 scripts/runbook의 unsafe shell, token persistence, project-key-as-Bearer 경계를 검증했다.

### File List

- `settings.gradle`
- `implementation-artifacts/real-github-oauth-smoke-runbook.md`
- `implementation-artifacts/sprint-status.yaml`
- `observability-portal/src/test/java/com/observation/portal/domain/account/controller/AuthSecretExposureGuardTest.java`
- `observability-smoke-service/build.gradle`
- `observability-smoke-service/src/main/java/com/observation/smoke/SmokeServiceApplication.java`
- `observability-smoke-service/src/main/java/com/observation/smoke/web/SmokeTrafficController.java`
- `observability-smoke-service/src/main/resources/application-local-smoke.properties`
- `observability-smoke-service/src/test/java/com/observation/smoke/SmokeModuleBoundaryTest.java`
- `observability-smoke-service/src/test/java/com/observation/smoke/SmokeServiceApplicationTest.java`
- `observability-smoke-service/src/test/java/com/observation/smoke/web/SmokeTrafficControllerTest.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/config/ObservationBindingAutoConfiguration.java`
- `observability-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/config/ObservationBindingAutoConfigurationTest.java`
- `planning-artifacts/stories/7-3-smoke-spring-boot-service-and-portal-communication-verification.md`
- `scripts/smoke/run-smoke-traffic.sh`
- `scripts/smoke/verify-smoke-portal-flow.sh`

### Change Log

- 2026-06-01: Story 7.3 create-story context를 생성하고 ready-for-dev 상태로 전환했다.
- 2026-06-01: Story 7.3 smoke service module, starter observation auto-wiring, operator scripts/runbook, regression guards를 구현하고 review 상태로 전환했다.
