---
artifactType: story
storyId: "7.2"
storyKey: "7-2-starter-bucket-ingest-http-client"
epic: "Epic 7. Real GitHub and Starter Smoke Verification"
title: "Starter bucket ingest HTTP client"
architectureStyle: Traditional MVC
status: done
date: 2026-06-01
---

# Story 7.2 - Starter bucket ingest HTTP client

## Status

done

## Story

local smoke operator와 starter 사용자는 host Spring Boot app에 custom `PortalMetricBucketClient` bean을 직접 만들지 않고도 starter가 닫힌 30초 bucket을 portal ingest API로 전송하길 원한다.

그래야 Story 7.1에서 만든 local smoke project key를 starter `X-OBS-Project-Key` 인증 경계에만 사용해 `POST /api/ingest/v1/buckets`를 bounded background worker에서 검증하고, Story 7.3의 smoke Spring Boot service가 heartbeat, accepted bucket, Dashboard, Instance Evidence 확인으로 이어질 수 있다.

## Source of Truth

아래 문서를 읽고 반영한 BMAD create-story context다. 구현 중 충돌처럼 보이는 지점은 starter fail-open/non-blocking 계약, ingest-envelope/API surface, 현재 starter code, Story 7.1 smoke handoff 순서로 우선한다.

1. `_bmad/custom/project-context.md`
2. `AGENTS.md`
3. `implementation-artifacts/sprint-status.yaml`
4. `planning-artifacts/epics.md`
5. `planning-artifacts/sprint-plan.md`
6. `planning-artifacts/api-surface.md`
7. `planning-artifacts/contracts/ingest-envelope.md`
8. `planning-artifacts/contracts/starter-failure-semantics.md`
9. `planning-artifacts/contracts/time-buckets.md`
10. `planning-artifacts/contracts/state-semantics.md`
11. `planning-artifacts/architecture.md`
12. `planning-artifacts/architecture-implementation-supplement.md`
13. `planning-artifacts/project-structure.md`
14. `planning-artifacts/stories/2-4-async-flush-worker.md`
15. `planning-artifacts/stories/2-5-ingest-envelope-builder-service.md`
16. `planning-artifacts/stories/4-0-2-complete-starter-heartbeat.md`
17. `planning-artifacts/stories/6-8-demo-green-path.md`
18. `planning-artifacts/stories/6-9-failure-recovery-path-demo-hardening.md`
19. `planning-artifacts/stories/7-1-real-github-oauth-smoke-seed-and-operator-runbook.md`
20. `implementation-artifacts/real-github-oauth-smoke-runbook.md`

확인한 현재 코드:

1. `build.gradle`
2. `observability-spring-boot-starter/build.gradle`
3. `observability-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
4. `observability-spring-boot-starter/src/main/java/com/observation/starter/client/PortalMetricBucketClient.java`
5. `observability-spring-boot-starter/src/main/java/com/observation/starter/client/JdkPortalHeartbeatClient.java`
6. `observability-spring-boot-starter/src/main/java/com/observation/starter/client/PortalHeartbeatException.java`
7. `observability-spring-boot-starter/src/main/java/com/observation/starter/client/HeartbeatFailureClassifier.java`
8. `observability-spring-boot-starter/src/main/java/com/observation/starter/client/http/package-info.java`
9. `observability-spring-boot-starter/src/main/java/com/observation/starter/config/MetricDrainProperties.java`
10. `observability-spring-boot-starter/src/main/java/com/observation/starter/config/MetricDrainAutoConfiguration.java`
11. `observability-spring-boot-starter/src/main/java/com/observation/starter/config/HeartbeatProperties.java`
12. `observability-spring-boot-starter/src/main/java/com/observation/starter/config/StarterHeartbeatAutoConfiguration.java`
13. `observability-spring-boot-starter/src/main/java/com/observation/starter/service/MetricBucketFlushWorker.java`
14. `observability-spring-boot-starter/src/main/java/com/observation/starter/service/IngestEnvelopeBuilderService.java`
15. `observability-spring-boot-starter/src/main/java/com/observation/starter/model/ingest/IngestEnvelopeCandidate.java`
16. `observability-spring-boot-starter/src/test/java/com/observation/starter/client/JdkPortalHeartbeatClientTest.java`
17. `observability-spring-boot-starter/src/test/java/com/observation/starter/config/MetricDrainAutoConfigurationTest.java`
18. `observability-spring-boot-starter/src/test/java/com/observation/starter/service/MetricBucketFlushWorkerTest.java`
19. `observability-spring-boot-starter/src/test/java/com/observation/starter/service/StarterNonBlockingIngestTest.java`
20. `observability-spring-boot-starter/src/test/java/com/observation/starter/architecture/StarterObservationArchitectureTest.java`
21. `observability-portal/src/main/java/com/observation/portal/domain/ingest/controller/IngestController.java`
22. `observability-portal/src/main/java/com/observation/portal/domain/ingest/dto/IngestAcceptedResponse.java`
23. `observability-portal/src/main/java/com/observation/portal/domain/ingest/dto/IngestErrorResponse.java`
24. `observability-portal/src/test/java/com/observation/portal/domain/ingest/controller/IngestControllerTest.java`

외부 최신 참고:

1. Spring Boot Creating Your Own Auto-configuration: https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html
2. Spring Boot `@ConfigurationProperties` API: https://docs.spring.io/spring-boot/api/java/org/springframework/boot/context/properties/ConfigurationProperties.html
3. Java 17 `HttpClient` API: https://docs.oracle.com/en/java/javase/17/docs/api/java.net.http/java/net/http/HttpClient.html

## Scope / Out of Scope

포함:

- `PortalMetricBucketClient`의 기본 JDK HTTP 구현
- metric bucket ingest 전용 connection properties
- custom smoke bean 없이 `MetricDrainAutoConfiguration`이 default bucket client를 만들 수 있는 auto-configuration
- `POST /api/ingest/v1/buckets`
- `X-OBS-Project-Key`, `Idempotency-Key`, `Content-Type: application/json`
- bounded connect/request timeout
- non-2xx, timeout, transport failure를 worker retry가 해석할 수 있는 runtime failure로 변환
- raw project key, access token, refresh token, GitHub provider token, OAuth credential, response body secret의 log/exception 노출 방지
- request path와 HTTP client implementation 의존 분리 architecture guard 유지
- Story 7.1 runbook handoff에 starter metric flush 설정 placeholder를 연결하는 문서 보강
- 새 public class/method/helper에는 AGENTS.md 기준 한국어 Javadoc/comment 작성

제외:

- Story 7.3의 smoke Spring Boot service module/sample runtime
- traffic endpoint(`/smoke/ok`, `/smoke/slow`, `/smoke/error-candidate`) 구현
- portal `POST /api/ingest/v1/buckets` controller/service/repository 변경
- duplicate/idempotency portal 정책 변경
- durable outbox, Kafka, Redis, disk spool
- request path에서 portal network call, retry, backoff 실행
- heartbeat endpoint 또는 heartbeat telemetry persistence 변경
- heartbeat 설정을 metric flush 설정으로 암묵 fallback하는 동작
- public project creation, project key issuance/rotation UI, browser token persistence
- Dashboard/Instance Evidence read API verification 자동화
- Spring Web/WebClient/RestClient 의존성 추가

## Acceptance Criteria

1. Story 7.2 story file은 `ready-for-dev` 상태로 생성되고 `sprint-status.yaml`의 `7-2-starter-bucket-ingest-http-client`도 `ready-for-dev`로 갱신된다.
2. `PortalMetricBucketClient`의 default HTTP 구현이 추가되어 custom bean 없이 starter가 bucket ingest API를 호출할 수 있다.
3. Default 구현은 Spring Web/WebClient/RestClient 의존성을 추가하지 않고 Java 17 `java.net.http.HttpClient`와 Jackson만 사용한다.
4. HTTP 구현 위치는 starter infrastructure boundary인 `com.observation.starter.client.http` 아래에 둔다.
5. `client.http` 구현 class가 `PortalMetricBucketClient`를 구현하더라도 request path class가 HTTP implementation에 의존하지 않는 architecture guard는 유지된다.
6. Auto-configuration은 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`의 기존 `MetricDrainAutoConfiguration` 경계를 사용한다.
7. Metric flush portal connection 설정은 `observation.metric-flush.portal-base-url`, `observation.metric-flush.project-key`, `observation.metric-flush.timeout-millis`로 명확히 바인딩한다.
8. `observation.metric-flush.project-id`는 idempotency identity component이고 raw project key가 아님을 코드/문서에서 혼동하지 않는다.
9. `observation.metric-flush.project-key`는 raw project key이며 log, exception, response, test failure message에 노출하지 않는다.
10. `observation.heartbeat.portal-base-url`와 `observation.heartbeat.project-key`는 metric flush client를 자동으로 켜는 fallback source로 사용하지 않는다.
11. Metric flush connection 설정이 없고 custom `PortalMetricBucketClient` bean도 없으면 clean starter context는 계속 로드되고 `MetricBucketFlushWorker`는 등록되지 않는다.
12. Metric flush connection 설정이 모두 있고 custom `PortalMetricBucketClient` bean이 없으면 default HTTP client bean이 등록된다.
13. Custom `PortalMetricBucketClient` bean이 있으면 default HTTP client bean은 등록되지 않는다.
14. Default HTTP client bean이 등록되어도 `MetricBucketFlushWorker`는 기존처럼 `MetricDrainProperties.validatePortalFlushIdentity(Environment)`를 통과한 뒤에만 시작된다.
15. Generic default identity(`local-project`, `application`, `default`, `local-instance`)로 portal flush가 시작되지 않는 guard를 유지한다.
16. HTTP client URI는 portal base URL 뒤에 `/api/ingest/v1/buckets`를 붙여 만든다.
17. Portal base URL은 trailing slash 유무와 관계없이 같은 endpoint URI를 만든다.
18. Timeout millis는 양수만 허용하고, 기본값은 bounded value로 둔다.
19. Client는 connect timeout과 request timeout을 모두 bounded 값으로 설정한다.
20. `flush(IngestEnvelopeCandidate)`는 `POST` method를 사용한다.
21. Request body는 `candidate.payload()` JSON이며 `Content-Type: application/json` header를 포함한다.
22. Request는 `X-OBS-Project-Key` header에 raw project key를 싣는다.
23. Request는 `Idempotency-Key` header에 `candidate.idempotencyKey()`를 싣는다.
24. Client는 candidate payload나 idempotency key를 재계산하지 않고 `IngestEnvelopeBuilderService` output을 그대로 사용한다.
25. Client는 `schemaVersion: "1.0"` payload shape를 변경하거나 heartbeat payload와 합치지 않는다.
26. HTTP `2xx`는 성공으로 처리한다. 현재 portal은 새 bucket에 `201 Created`를 반환하지만 future duplicate success `200 OK`도 client success로 해석할 수 있어야 한다.
27. HTTP non-2xx는 raw key와 response body를 노출하지 않는 starter 전용 runtime exception으로 변환한다.
28. `401`, `4xx`, `5xx`, timeout/transport failure는 worker retry/backoff가 처리할 수 있게 `RuntimeException` 계열로 전달된다.
29. Transport failure exception chain과 stack trace에 raw project key가 포함되지 않게 sanitize한다.
30. `InterruptedException`이 발생하면 interrupt flag를 복원하고 sanitized runtime exception으로 변환한다.
31. Client constructor와 properties validation은 blank portal URL, blank project key, non-positive timeout을 fail-fast local configuration error로 닫는다.
32. Configuration binding error와 transport failure message는 설정 key 이름과 일반화된 원인만 포함하고 secret 값을 포함하지 않는다.
33. `MetricBucketFlushWorker` retry/backoff와 final failure swallowing 경계는 변경하지 않는다.
34. Host request path, Micrometer observation binding, `StarterMetricIngestService`, `StarterMetricDrainScheduler`는 HTTP client implementation을 직접 호출하지 않는다.
35. Request path는 여전히 local sample record, rollup, bounded queue enqueue까지만 수행한다.
36. Queue overflow policy, finite capacity, worker poll interval guard는 유지된다.
37. Bucket ingest failure는 host app startup/request path 실패로 전파되지 않는다.
38. Heartbeat `POST /api/ingest/v1/heartbeat`와 bucket ingest `POST /api/ingest/v1/buckets`는 별도 client/properties/retry 의미로 유지된다.
39. Bucket ingest 성공은 heartbeat telemetry, dashboard snapshot, operational event, lifecycle state, p95/p99 rollup 계산을 starter에서 만들거나 암시하지 않는다.
40. Portal controller/repository code는 이 story에서 수정하지 않는다. 단, 기존 portal ingest contract test가 client expectation과 맞는지 확인한다.
41. Story 7.1의 `.private/smoke-project.env` 또는 shell env handoff는 `OBSERVATION_SMOKE_PROJECT_KEY=<key_prefix>.<secret>`처럼 starter raw project key만 담는 local-only secret으로 유지된다.
42. `.private/smoke-auth.env`의 service Bearer access token은 bucket ingest client 설정에 사용하지 않는다.
43. `.private/smoke-project.env`의 raw project key는 portal resource API Bearer token으로 사용하지 않는다.
44. Story 7.2 구현 후 runbook 또는 implementation artifact는 metric flush 설정 예시를 placeholder로만 제공하고 실제 secret 값을 쓰지 않는다.
45. Focused tests는 HTTP method/path/header/body, timeout, non-2xx, transport failure, secret redaction, auto-configuration 조건을 검증한다.
46. Existing `MetricBucketFlushWorkerTest`, `StarterNonBlockingIngestTest`, `MetricDrainAutoConfigurationTest`, `StarterObservationArchitectureTest`는 계속 통과한다.
47. New/updated tests는 request path가 network timeout을 기다리지 않는다는 기존 proof를 약화하지 않는다.
48. Starter build/test classpath에는 Spring Web, WebFlux, Actuator, Prometheus registry dependency가 추가되지 않는다.
49. Suggested verification과 `git diff --check`가 통과해야 implementation completion으로 볼 수 있다.
50. 새 public class/method/helper 또는 동작을 바로 이해하기 어려운 내부 helper에는 AGENTS.md 지침에 따라 한국어 Javadoc/comment를 작성한다.

## Tasks / Subtasks

- [x] Metric flush connection properties 확장 (AC: 7~19, 31~32, 50)
  - [x] `MetricDrainProperties`에 `portalBaseUrl`, `projectKey`, `timeoutMillis`를 추가한다.
  - [x] `bucketIngestUri()` 또는 동등 helper로 `/api/ingest/v1/buckets` endpoint URI를 만든다.
  - [x] `hasPortalConnectionSettings()` 또는 conditional property helper로 default client 등록 조건을 표현한다.
  - [x] `project-id`와 `project-key` 의미가 섞이지 않게 Javadoc/comment를 작성한다.

- [x] Default bucket ingest HTTP client 구현 (AC: 2~5, 16~30, 48, 50)
  - [x] `com.observation.starter.client.http.JdkPortalMetricBucketClient` 또는 동등 class를 추가한다.
  - [x] `PortalMetricBucketException` 또는 동등 starter 전용 runtime exception을 추가한다.
  - [x] request body는 `IngestEnvelopeCandidate.payload()`를 Jackson으로 serialize한다.
  - [x] `X-OBS-Project-Key`, `Idempotency-Key`, `Content-Type` header를 보낸다.
  - [x] non-2xx와 transport failure는 raw key/response body 없는 sanitized exception으로 변환한다.
  - [x] interruption 시 interrupt flag를 복원한다.

- [x] Auto-configuration wiring (AC: 6, 10~15, 33~38, 48, 50)
  - [x] `MetricDrainAutoConfiguration`에 default `PortalMetricBucketClient` bean을 추가하되 connection settings가 있을 때만 등록한다.
  - [x] `@ConditionalOnMissingBean(PortalMetricBucketClient.class)`를 유지해 custom smoke bean이 default client를 대체할 수 있게 한다.
  - [x] Connection settings가 없으면 no-op bucket client bean을 만들지 않는다. No-op bean이 있으면 worker가 잘못 시작될 수 있다.
  - [x] 기존 worker bean은 `@ConditionalOnBean(PortalMetricBucketClient.class)`와 explicit identity guard를 유지한다.
  - [x] Heartbeat properties를 metric flush fallback으로 읽지 않는다.

- [x] Architecture/test guard 보강 (AC: 5, 34~37, 45~49)
  - [x] `StarterObservationArchitectureTest`의 `PortalMetricBucketClient` 의존 guard를 default HTTP implementation 허용 범위까지 좁혀 갱신한다.
  - [x] request path package가 `client.http`, `java.net.http`, Jackson serialization에 의존하지 않는 guard는 유지한다.
  - [x] `NoPrometheusMvpPathTest`가 starter classpath에 Spring Web/WebFlux/Actuator/Prometheus가 없음을 계속 확인한다.

- [x] Focused tests 추가/갱신 (AC: 16~30, 45~49)
  - [x] `JdkPortalMetricBucketClientTest`에서 local `HttpServer`로 method/path/header/body를 검증한다.
  - [x] 201/200 같은 2xx status는 성공으로 처리하는지 검증한다.
  - [x] 400/401/409/5xx non-2xx는 sanitized exception으로 처리하는지 검증한다.
  - [x] delayed server 또는 unreachable endpoint로 timeout/transport failure redaction을 검증한다.
  - [x] exception message, cause chain, stack trace에 raw project key가 없는지 검증한다.
  - [x] `MetricDrainAutoConfigurationTest`에서 settings 없음, settings 있음, custom client override, generic identity rejection을 검증한다.
  - [x] 기존 non-blocking/worker tests를 재실행한다.

- [x] Story 7.1/7.3 handoff 문서 보강 (AC: 41~44, 49~50)
  - [x] `implementation-artifacts/real-github-oauth-smoke-runbook.md` 또는 동등 implementation artifact에 metric flush placeholder 설정을 추가한다.
  - [x] 실제 raw project key, access token, refresh token, provider token, OAuth credential 값은 쓰지 않는다.
  - [x] `.private/smoke-auth.env` Bearer token과 `.private/smoke-project.env` raw project key의 인증 경계를 다시 분리해 설명한다.

## Dev Notes

### Current Code State

- `PortalMetricBucketClient`는 이미 `IngestEnvelopeCandidate`를 받는 functional interface다. 현재 기본 구현은 없어서 custom bean이 없으면 `MetricBucketFlushWorker`가 등록되지 않는다.
- `MetricDrainAutoConfiguration`은 clean starter runtime에서 `BoundedMetricQueue`, `MetricBucketRollupService`, `IngestEnvelopeBuilderService`, `StarterMetricIngestService`, `StarterMetricDrainScheduler`를 등록한다.
- `MetricDrainAutoConfiguration.metricBucketFlushWorker(...)`는 `PortalMetricBucketClient` bean이 있을 때만 worker를 만들고, 만들기 직전에 `MetricDrainProperties.validatePortalFlushIdentity(Environment)`로 generic identity를 막는다.
- `MetricDrainProperties`의 `projectId`는 idempotency key component다. Raw project key와 이름이 비슷하므로 구현 중 `project-id`와 `project-key`를 절대 바꾸어 쓰지 않는다.
- `IngestEnvelopeBuilderService`는 sealed `ClosedMetricBucket`을 deterministic `IngestEnvelopeCandidate`로 변환하고, idempotency key를 `<project-id>:<application>:<environment>:<instance>:<bucket-start-utc-basic>`로 만든다.
- `MetricBucketFlushWorker`는 daemon `observation-metric-flush-worker`에서만 client를 호출하고 retry/backoff/final failure를 worker 내부에 격리한다.
- `JdkPortalHeartbeatClient`는 JDK `HttpClient`, bounded timeout, raw key redaction pattern의 좋은 참고 구현이다. 다만 heartbeat naming과 endpoint 의미는 bucket ingest로 복사하지 말고 분리한다.
- `HeartbeatFailureClassifier`와 `PortalHeartbeatException`은 heartbeat 전용 이름이다. bucket ingest 구현에서 재사용하려면 이름/의미를 일반화하거나, scope가 커지지 않게 bucket 전용 exception을 둔다.
- `client.http/package-info.java`는 "향후 포털 수집 전송 구현" 패키지로 예약되어 있다. Story 7.2 default bucket client는 여기에 두는 것이 architecture 문서와 맞다.
- `StarterObservationArchitectureTest.portalClientBoundaryIsOnlyUsedByBackgroundFlushWorker`는 현재 새 HTTP implementation이 interface를 구현하면 실패할 가능성이 있다. request path guard는 유지하되 `client.http` implementation 자체는 허용하도록 조정해야 한다.
- `observability-spring-boot-starter/build.gradle`에는 Jackson databind가 있고 Spring Web/WebFlux dependency는 없다.
- Portal `IngestController`는 `POST /api/ingest/v1/buckets`에서 `X-OBS-Project-Key`, `Idempotency-Key`, body를 `IngestAcceptanceService`에 넘긴다.
- 현재 portal implementation은 duplicate idempotency key를 `409 Conflict`로 닫는다. `api-surface.md`의 future duplicate `200 OK` 예시와 차이가 있지만, Story 7.2 client는 "2xx success, non-2xx retryable/failure"로만 해석하면 된다.

### Proposed Implementation Boundary

권장 file shape:

- `observability-spring-boot-starter/src/main/java/com/observation/starter/client/http/JdkPortalMetricBucketClient.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/client/http/PortalMetricBucketException.java`
- optional `observability-spring-boot-starter/src/main/java/com/observation/starter/client/http/PortalMetricBucketFailureClassifier.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/config/MetricDrainProperties.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/config/MetricDrainAutoConfiguration.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/client/http/JdkPortalMetricBucketClientTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/config/MetricDrainAutoConfigurationTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/architecture/StarterObservationArchitectureTest.java`
- optional `implementation-artifacts/real-github-oauth-smoke-runbook.md`

권장 property shape:

```properties
observation.metric-flush.project-id=local-smoke-project-id
observation.metric-flush.application-name=orders-api
observation.metric-flush.environment=local-smoke
observation.metric-flush.instance=orders-api-local-1
observation.metric-flush.portal-base-url=http://localhost:8080
observation.metric-flush.project-key=<key_prefix>.<secret>
observation.metric-flush.timeout-millis=1000
```

Heartbeat도 같이 검증하는 host app은 별도로 아래를 설정한다. Story 7.2에서 이 값을 metric flush fallback으로 읽지 않는다.

```properties
observation.heartbeat.portal-base-url=http://localhost:8080
observation.heartbeat.project-key=<key_prefix>.<secret>
```

### Latest Technical Notes

- Spring Boot 4.0.6 reference는 auto-configuration 후보를 published jar의 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`에 한 줄에 하나씩 나열한다고 설명한다. 이 repo는 이미 해당 imports 파일을 사용하므로 `spring.factories`를 새로 만들지 않는다.
- Spring Boot docs는 auto-configuration class가 component scanning 대상이 아니라 imports 파일을 통해 로드되어야 한다고 설명한다. 따라서 default client wiring은 기존 `MetricDrainAutoConfiguration` 안의 `@Bean`으로 둔다.
- Spring Boot bean condition docs는 `@ConditionalOnMissingBean`을 사용해 user-provided bean을 우선시하는 pattern을 제공한다. Story 7.2도 custom `PortalMetricBucketClient` bean을 default HTTP client보다 우선한다.
- `@ConfigurationProperties`는 externalized configuration binding/validation용 annotation이다. 기존 `MetricDrainProperties` 확장으로 metric flush connection 값을 받는 것이 repo pattern과 맞다.
- Java 17 `HttpClient.send(...)`는 response 수신까지 blocking한다. 이 blocking 호출은 반드시 `MetricBucketFlushWorker` background thread 안에만 있어야 하며 host request path나 scheduler tick path로 이동하면 안 된다.
- Java 17 `HttpClient`와 `HttpRequest`는 client connect timeout과 request timeout을 각각 설정할 수 있다. Story 7.2는 둘 다 bounded 값으로 둔다.

### Previous Story Intelligence

- Story 2.4는 request path와 portal transport를 분리했다. 이 story는 그 worker boundary 뒤쪽에 실제 HTTP client를 꽂는 작업이다.
- Story 2.5는 `IngestEnvelopeCandidate` payload/idempotency를 deterministic하게 닫았다. 이 story는 candidate를 serialize/send만 하고 재계산하지 않는다.
- Story 4.0.2는 heartbeat를 bucket ingest와 별도 control-plane으로 구현했다. 이 story는 heartbeat client를 복붙해 의미를 섞지 않고 bucket ingest data-plane client를 만든다.
- Story 6.8/6.9는 accepted bucket axis와 starter heartbeat axis를 분리하는 UI/read-model/copy guard를 강화했다. Story 7.2 구현은 "bucket 전송 성공 = host 정상" 같은 의미를 만들지 않는다.
- Story 7.1은 `.private/smoke-auth.env` access token과 `.private/smoke-project.env` raw project key 경계를 분리했다. Story 7.2는 raw project key만 `X-OBS-Project-Key`에 사용한다.

### Git Intelligence

최근 commit 흐름은 Epic 7 smoke seed 진입과 Epic 6 closure다.

- `922e6f3 Complete story 7.1 GitHub OAuth smoke seed`
- `1e20db6 Close out Epic 6 retrospective`
- `18b0524 Merge story 6.9 failure recovery hardening`
- `7e3b1d5 Complete story 6.9 failure recovery hardening`
- `36a6e92 Merge story 6.8 demo green path`

Story 7.2 구현자는 Story 7.1의 local-only secret redaction과 Epic 6의 accepted bucket/heartbeat axis 분리를 그대로 이어간다.

## Testing

Focused test 대상 후보:

- `JdkPortalMetricBucketClientTest`
- `MetricDrainPropertiesTest` 또는 `MetricDrainAutoConfigurationTest`
- `MetricBucketFlushWorkerTest`
- `StarterNonBlockingIngestTest`
- `StarterObservationArchitectureTest`
- `NoPrometheusMvpPathTest`

필수 scenario:

- Default client가 `POST /api/ingest/v1/buckets`에 `X-OBS-Project-Key`, `Idempotency-Key`, `Content-Type: application/json`, envelope JSON body를 보낸다.
- Portal base URL trailing slash 유무와 관계없이 endpoint path가 맞다.
- 201/200 status는 success다.
- 400/401/409/5xx는 raw project key와 response body 없이 exception으로 변환된다.
- Timeout/connection refused/DNS failure 후보는 raw key 없이 sanitized exception으로 변환된다.
- Interrupted request는 interrupt flag를 복원한다.
- Connection settings가 없으면 default bucket client와 worker가 등록되지 않는다.
- Connection settings와 explicit identity가 있으면 default client와 worker가 등록된다.
- Connection settings가 있어도 custom `PortalMetricBucketClient` bean이 있으면 custom bean을 사용한다.
- Generic identity default 상태에서 connection settings가 있으면 worker startup이 기존처럼 fail-fast로 닫힌다.
- Request path class는 `client.http`, `java.net.http`, Jackson serialization에 의존하지 않는다.
- Starter classpath에는 Spring Web/WebFlux/Actuator/Prometheus가 추가되지 않는다.

Suggested commands:

```bash
./gradlew :observability-spring-boot-starter:test --tests '*JdkPortalMetricBucketClient*'
./gradlew :observability-spring-boot-starter:test --tests '*MetricDrainAutoConfiguration*'
./gradlew :observability-spring-boot-starter:test --tests '*MetricBucketFlushWorker*'
./gradlew :observability-spring-boot-starter:test --tests '*StarterNonBlockingIngest*'
./gradlew :observability-spring-boot-starter:test --tests com.observation.starter.architecture.StarterObservationArchitectureTest
./gradlew :observability-spring-boot-starter:test --tests com.observation.starter.architecture.NoPrometheusMvpPathTest
./gradlew :observability-spring-boot-starter:test
git diff --check
```

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- 2026-06-01: BMAD create-story workflow, sprint status, Epic 7 source artifacts, Story 7.1 handoff, starter ingest/heartbeat code를 확인했다.
- 2026-06-01: Story 7.2 target key를 `7-2-starter-bucket-ingest-http-client`로 확정했다.
- 2026-06-01: Java 17 `HttpClient`, Spring Boot 4 auto-configuration imports/conditions, `@ConfigurationProperties` official docs를 확인했다.
- 2026-06-01: Story 7.2 story file을 생성하고 ready-for-dev 상태로 전환했다.
- 2026-06-01: Story 7.2 dev-story 실행을 시작하고 sprint status를 `in-progress`로 전환했다.
- 2026-06-01: RED 단계로 `JdkPortalMetricBucketClientTest`, `MetricDrainAutoConfigurationTest`, starter architecture guard를 추가했고, missing HTTP client/properties surface로 compile failure가 발생함을 확인했다.
- 2026-06-01: GREEN 단계에서 metric flush 전용 `portal-base-url`, `project-key`, `timeout-millis` properties와 default `JdkPortalMetricBucketClient`, sanitized bucket exception/classifier, auto-configuration wiring을 구현했다.
- 2026-06-01: Story 7.1 smoke runbook에 metric flush placeholder 설정과 Bearer token/raw project key 인증 경계 분리 문구를 추가했다.
- 2026-06-01: 필수 starter focused tests, full starter test, portal ingest controller focused test, `git diff --check` 통과를 확인했다.
- 2026-06-01: BMAD code review follow-up으로 partial metric-flush connection settings fail-fast guard, bucket ingest 최초 실패 WARN reporter, Story 7.2 test fixture secret-like value 정리를 적용했다.

### Completion Notes List

- `MetricDrainProperties`는 `observation.metric-flush.portal-base-url`, `observation.metric-flush.project-key`, `observation.metric-flush.timeout-millis`를 metric bucket flush 전용 설정으로 바인딩한다.
- `project-id`는 Idempotency-Key 구성요소, `project-key`는 `X-OBS-Project-Key` raw key로 주석과 tests에서 분리했다.
- `JdkPortalMetricBucketClient`는 Java 17 `HttpClient`와 Jackson만 사용해 `POST /api/ingest/v1/buckets`로 `IngestEnvelopeCandidate.payload()` JSON과 `candidate.idempotencyKey()` header를 그대로 전송한다.
- HTTP `2xx`는 성공, non-2xx/timeout/transport/interruption은 raw project key와 response body 없는 `PortalMetricBucketException`으로 변환한다.
- `MetricDrainAutoConfiguration`은 metric flush connection settings가 있을 때만 default client를 만들고, custom `PortalMetricBucketClient` bean이 있으면 default client를 만들지 않는다. Connection settings가 없으면 no-op bucket client도 만들지 않는다.
- Partial metric flush connection settings는 starter가 조용히 비활성화되지 않도록 local configuration error로 닫는다.
- `MetricBucketFlushWorker`의 `@ConditionalOnBean(PortalMetricBucketClient.class)`와 `validatePortalFlushIdentity(Environment)` guard는 유지했다.
- `MetricBucketFlushWorker`는 bucket ingest 실패 category별 최초 WARN만 남기고 반복 실패 log spam은 억제한다.
- Heartbeat `observation.heartbeat.*` 설정은 bucket ingest client fallback으로 쓰지 않도록 auto-configuration test로 고정했다.

### File List

- `implementation-artifacts/real-github-oauth-smoke-runbook.md`
- `planning-artifacts/stories/7-2-starter-bucket-ingest-http-client.md`
- `implementation-artifacts/sprint-status.yaml`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/client/PortalMetricBucketFailure.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/client/http/JdkPortalMetricBucketClient.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/client/http/PortalMetricBucketException.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/client/http/PortalMetricBucketFailureCategory.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/client/http/PortalMetricBucketFailureClassifier.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/client/http/package-info.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/config/MetricDrainAutoConfiguration.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/config/MetricDrainProperties.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/service/MetricBucketFailureReporter.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/service/Slf4jMetricBucketFailureReporter.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/client/http/JdkPortalMetricBucketClientTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/config/MetricDrainAutoConfigurationTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/service/MetricBucketFlushWorkerTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/architecture/StarterObservationArchitectureTest.java`

### Change Log

- 2026-06-01: Story 7.2 create-story context를 생성하고 ready-for-dev 상태로 전환했다.
- 2026-06-01: Starter bucket ingest default JDK HTTP client, metric flush connection auto-configuration, focused tests, smoke runbook handoff를 구현하고 review 상태로 전환했다.
- 2026-06-01: Code review follow-up으로 partial settings guard, metric bucket failure WARN reporter, fixture redaction hardening을 반영했다.
