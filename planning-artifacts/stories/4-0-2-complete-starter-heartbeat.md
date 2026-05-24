---
artifactType: story
storyId: "4.0.2"
epic: "Epic 4. State Semantics and Time Windows"
title: "Complete Starter Heartbeat"
architectureStyle: Traditional MVC
status: done
date: 2026-05-24
trackingNote: "sprint-status에는 없는 Story 4.0과 4.2 사이의 구현 완료형 bridge story"
---

# Story 4.0.2 - Complete Starter Heartbeat

## Story Goal

Story 4.0에서 계약으로 잠근 starter heartbeat를 starter 전송부터 portal 수신/응답까지 실제 구현으로 닫는다.

이 story는 Story 4.2 `LifecycleStateService` 구현 전에 남겨둔 선행 gap을 문서화한 **구현 완료형 bridge story**다. sprint-status의 공식 backlog item은 아니지만, 후속 BMAD/dev agent가 heartbeat 구현 맥락과 source-of-truth 경계를 stories 디렉터리에서 바로 찾을 수 있도록 같은 story 양식으로 남긴다.

## User Story

Spring Boot 앱 운영자로서, starter가 실행 중인 동안 portal 연결과 project key 설정이 유효한지 주기적으로 확인하고 싶다.

그래야 첫 dashboard가 비어 있거나 수집이 끊겼을 때 starter liveness/control-plane 신호와 accepted bucket 기반 freshness/application state를 섞지 않고 분리해서 이해할 수 있다.

## Scope

포함:

- starter heartbeat request model
- metric bucket ingest와 분리된 `PortalHeartbeatClient` 경계
- JDK `HttpClient` 기반 bounded timeout heartbeat client
- portal 설정이 없을 때 startup-safe no-op heartbeat client
- host startup/request path를 막지 않는 daemon background heartbeat sender
- heartbeat payload의 `schemaVersion`, `starterVersion`, `heartbeat.sentAtUtc`, `sequence`, `intervalSeconds`
- heartbeat payload의 `application.name`, `environment`, `instance`
- portal `POST /api/ingest/v1/heartbeat`
- portal project key verification 재사용
- portal heartbeat request shape validation
- `ingestBoundary.lastAcceptedBucketAt`와 `statusSource=accepted_bucket` response 분리
- 기존 application catalog row가 있을 때만 Story 4.1의 마지막 accepted bucket timestamp 조회 재사용
- controller/service/sender/client focused tests

제외:

- `LifecycleStateService`
- dashboard read model/API
- snapshot persistence
- operational event history
- heartbeat telemetry persistence
- heartbeat를 `accepted_metric_buckets`에 저장하는 것
- heartbeat로 application/instance catalog row를 upsert하는 것
- heartbeat로 `waiting_first_data`, `stale`, `down`, `current`를 판단하는 것
- heartbeat UI surface
- p95/p99, rule, endpoint priority 계산
- disabled project `403` 정책 분리
- jitter/backoff/rate-limited logging hardening

## Acceptance Criteria

1. starter heartbeat sender가 bucket ingest와 별도 경로로 존재한다.
2. heartbeat sender는 background daemon path에서 동작하고 host startup/request path를 막지 않는다.
3. starter heartbeat HTTP client는 bounded connect/request timeout을 사용한다.
4. starter heartbeat 실패는 fail-open으로 격리되어 host caller로 전파되지 않는다.
5. portal heartbeat endpoint는 유효한 project key와 valid metadata shape에 `200 OK`를 반환한다.
6. missing/invalid project key는 `401`을 반환하고 raw key material을 response/result/exception message에 노출하지 않는다.
7. invalid `schemaVersion`, heartbeat shape, application metadata shape는 `400` validation error로 처리한다.
8. response의 accepted bucket 참고 시각은 `ingestBoundary.lastAcceptedBucketAt`에만 담고 `heartbeatStatus`와 분리한다.
9. response의 `ingestBoundary.statusSource`는 `accepted_bucket`으로 고정한다.
10. heartbeat 성공은 accepted bucket 저장, catalog upsert, snapshot/event/read-model/state 계산을 만들지 않는다.
11. last accepted bucket 조회는 기존 application catalog row가 있을 때만 Story 4.1 repository timestamp method를 재사용한다.
12. controller/service/sender/client tests가 non-blocking/fail-open 경계와 side effect 부재를 검증한다.

## Tasks/Subtasks

- [x] starter heartbeat model/client 경계 추가 (AC: 1, 3, 4, 6)
  - [x] `HeartbeatRequest`로 request contract를 표현한다.
  - [x] `PortalHeartbeatClient`를 bucket ingest client와 분리한다.
  - [x] `JdkPortalHeartbeatClient`가 `POST /api/ingest/v1/heartbeat`를 bounded timeout으로 호출한다.
  - [x] `NoopPortalHeartbeatClient`가 설정 누락 시 startup-safe no-op 경로를 제공한다.
- [x] starter heartbeat service/sender/auto-configuration 추가 (AC: 1, 2, 4)
  - [x] `HeartbeatProperties`로 portal base URL, project key, starter version, interval, timeout을 설정한다.
  - [x] `StarterHeartbeatService`가 sequence와 payload를 만들고 client failure를 boolean 결과로 격리한다.
  - [x] `StarterHeartbeatSender`가 daemon background thread에서 주기 전송한다.
  - [x] `StarterHeartbeatAutoConfiguration`을 starter auto-configuration imports에 등록한다.
- [x] portal heartbeat endpoint/service/response 구현 (AC: 5-11)
  - [x] `IngestHeartbeatController`를 bucket ingest controller와 분리한다.
  - [x] `IngestHeartbeatService`가 project key verification과 request shape validation을 수행한다.
  - [x] `IngestHeartbeatReceipt`로 service 내부 결과를 표현하고 controller에서만 DTO로 변환한다.
  - [x] `IngestHeartbeatResponse`가 `heartbeatStatus`와 `ingestBoundary`를 분리한다.
  - [x] existing application row read-only lookup 후 `MetricBucketRepository.findLatestBucketEndUtcByApplicationId`만 호출한다.
- [x] tests와 BMAD tracking 산출물 추가 (AC: 1-12)
  - [x] starter service/sender/client/auto-config tests를 추가한다.
  - [x] portal service/controller tests를 추가한다.
  - [x] quick-dev spec과 Epic 4 context를 implementation-artifacts에 기록한다.

## Dev Notes

- Active baseline은 Traditional MVC + Service/Repository Layering이다.
- heartbeat는 metric data plane이 아니라 control-plane/liveness signal이다.
- heartbeat 성공/실패/미수신은 accepted bucket freshness나 application lifecycle state source가 아니다.
- portal service는 controller/dto package에 의존하지 않는다. Controller가 `IngestHeartbeatReceipt`를 `IngestHeartbeatResponse`로 변환한다.
- `MetricBucketRepository`는 timestamp 조회만 수행하며 freshness/state 의미를 판단하지 않는다.
- heartbeat는 catalog bootstrap source가 아니다. 첫 accepted bucket ingest가 application/instance catalog upsert source다.
- raw project key는 `JdkPortalHeartbeatClient` exception message, portal error response, heartbeat result `toString`에 남기지 않는다.
- `observation.heartbeat.portal-base-url`와 `observation.heartbeat.project-key`가 모두 없으면 default client는 no-op이다. 설정 누락으로 host app startup을 실패시키지 않는다.
- `jitter/backoff/rate-limited logging`은 운영 hardening 후보로 남아 있으며 이번 bridge story의 blocker가 아니다.
- disabled project `403`은 후보 정책으로 남아 있으며, 기존 `ProjectKeyVerificationService`는 missing/unknown/mismatch/disabled를 모두 `401`로 수렴한다.

## Source References

- `planning-artifacts/stories/4-0-starter-heartbeat-and-instance-level-ingest-contract-reassessment.md` - heartbeat contract gate
- `planning-artifacts/contracts/ingest-envelope.md` - heartbeat와 bucket ingest 분리 boundary
- `planning-artifacts/contracts/state-semantics.md` - heartbeat가 state source가 아님을 고정
- `planning-artifacts/contracts/time-buckets.md` - accepted bucket freshness source와 last bucket timestamp 의미
- `planning-artifacts/contracts/starter-failure-semantics.md` - fail-open, bounded timeout, host path non-blocking 원칙
- `planning-artifacts/stories/4-1-time-bucket-contract-implementation.md` - repository timestamp method 재사용 맥락
- `implementation-artifacts/spec-complete-starter-heartbeat.md` - quick-dev implementation spec와 completion notes
- `implementation-artifacts/epic-4-context.md` - Epic 4 implementation context
- `_bmad/custom/project-context.md` - MVC + Spring Data JPA implementation policy

## Test Requirements

- `StarterHeartbeatServiceTest`
  - starter identity와 sequence를 heartbeat payload로 만든다.
  - client failure를 fail-open boolean result로 격리한다.
  - disabled/no-op client는 send를 시도하지 않는다.
- `StarterHeartbeatSenderTest`
  - `start()`가 slow heartbeat client 완료를 기다리지 않는다.
  - client 호출은 caller thread가 아닌 `observation-heartbeat-sender` daemon path에서 실행된다.
- `JdkPortalHeartbeatClientTest`
  - `POST /api/ingest/v1/heartbeat`에 `X-OBS-Project-Key`와 JSON body를 전송한다.
  - non-success status exception message에 raw project key가 노출되지 않는다.
- `StarterHeartbeatAutoConfigurationTest`
  - auto-configuration import가 등록된다.
  - portal connection 설정 누락 시 disabled client로 context가 뜬다.
  - custom heartbeat client가 configured metadata를 받는다.
- `IngestHeartbeatServiceTest`
  - valid heartbeat가 received response와 `ingestBoundary.statusSource=accepted_bucket`을 반환한다.
  - application catalog row가 없으면 `lastAcceptedBucketAt=null`을 반환하고 bucket repository를 호출하지 않는다.
  - invalid project key는 unauthorized로 수렴하고 shape validation/repository lookup을 건너뛴다.
  - invalid schema/application/heartbeat shape는 invalid request로 수렴한다.
  - valid heartbeat path에서 accepted bucket insert를 호출하지 않는다.
- `IngestHeartbeatControllerTest`
  - service result를 `200`, `400`, `401` response로 mapping한다.

## Developer Guardrails

- heartbeat와 bucket ingest retry/logging/idempotency 의미를 섞지 않는다.
- heartbeat를 `accepted_metric_buckets`에 저장하지 않는다.
- heartbeat 수신으로 application/instance catalog를 만들거나 갱신하지 않는다.
- heartbeat success/failure/missing을 freshness/state/source-of-truth로 사용하지 않는다.
- controller나 UI에서 stale/down/current/baseline을 재판정하지 않는다.
- `LifecycleStateService`, dashboard read model/API, snapshot/event history를 이 story 범위에 끌어오지 않는다.
- disabled project `403`, heartbeat telemetry persistence, jitter/backoff/rate-limited logging은 후속 hardening으로 다룬다.

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Implementation Summary

- starter에 heartbeat request model, bounded JDK HTTP client, no-op client, heartbeat properties, background sender, auto-configuration을 추가했다.
- portal에 bucket ingest와 분리된 heartbeat controller/service/request/response/result 모델을 추가했다.
- heartbeat response는 `heartbeatStatus`와 `ingestBoundary.lastAcceptedBucketAt`을 분리하고 `statusSource=accepted_bucket`을 명시한다.
- portal heartbeat service는 accepted bucket insert, catalog upsert, state/read-model/snapshot/event 계산 경로를 호출하지 않는다.
- MVC layer guard 실패를 발견해 service가 DTO에 의존하지 않도록 `IngestHeartbeatReceipt` 내부 모델을 추가하고 controller에서만 DTO 변환하도록 수정했다.

### Commit

- `842bc57a37e560eca107381ee1042a9968a6768a` - `Implement starter heartbeat boundary`

### Verification

- `./gradlew :observability-spring-boot-starter:test` - 성공
- `./gradlew :observability-portal:test` - 성공
- `./gradlew test` - 성공
- `git diff --check` - 성공
- `git diff --cached --check` - 성공

### Residual Risk

- heartbeat telemetry persistence는 없다. 현재 story에서는 state/freshness source 오염을 피하기 위해 의도적으로 제외했다.
- jitter/backoff/rate-limited logging은 없다. bounded timeout + background fail-open은 구현됐고, 운영 log hardening은 후속 후보로 남긴다.
- starter 기본 client는 `observation.heartbeat.portal-base-url`와 `observation.heartbeat.project-key`가 있어야 실제 HTTP 전송을 켠다.
- disabled project `403`은 구현하지 않았고 기존 project key verification 정책에 따라 `401`로 수렴한다.

### File List

- `implementation-artifacts/epic-4-context.md`
- `implementation-artifacts/spec-complete-starter-heartbeat.md`
- `observability-spring-boot-starter/build.gradle`
- `observability-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/client/PortalHeartbeatClient.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/client/JdkPortalHeartbeatClient.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/client/NoopPortalHeartbeatClient.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/config/HeartbeatProperties.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/config/StarterHeartbeatAutoConfiguration.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/heartbeat/HeartbeatRequest.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/heartbeat/package-info.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/service/StarterHeartbeatService.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/service/StarterHeartbeatSender.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/client/JdkPortalHeartbeatClientTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/config/StarterHeartbeatAutoConfigurationTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/service/StarterHeartbeatServiceTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/service/StarterHeartbeatSenderTest.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/controller/IngestHeartbeatController.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/dto/IngestHeartbeatResponse.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/dto/IngestErrorResponse.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/model/IngestHeartbeatRequest.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestHeartbeatReceipt.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestHeartbeatResult.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestHeartbeatService.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/controller/IngestHeartbeatControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/service/IngestHeartbeatServiceTest.java`

### Change Log

- 2026-05-24: Story 4.0과 4.2 사이의 starter heartbeat implementation 내용을 BMAD 참조용 story로 기록했다.

## Status

done
