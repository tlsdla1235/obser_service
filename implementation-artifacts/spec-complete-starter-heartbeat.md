---
title: 'complete-starter-heartbeat'
type: 'feature'
created: '2026-05-24'
status: 'done'
context:
  - '{project-root}/AGENTS.md'
  - '{project-root}/_bmad/custom/project-context.md'
  - '{project-root}/implementation-artifacts/epic-4-context.md'
  - '{project-root}/planning-artifacts/stories/4-0-starter-heartbeat-and-instance-level-ingest-contract-reassessment.md'
  - '{project-root}/planning-artifacts/contracts/ingest-envelope.md'
  - '{project-root}/planning-artifacts/contracts/state-semantics.md'
  - '{project-root}/planning-artifacts/contracts/time-buckets.md'
  - '{project-root}/planning-artifacts/contracts/starter-failure-semantics.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Story 4.2 LifecycleStateService 전에 starter heartbeat의 전송/수신 경계가 코드로 닫혀 있지 않아, liveness 신호와 accepted bucket freshness/state source가 섞일 위험이 있다.

**Approach:** starter에는 metric bucket ingest와 분리된 heartbeat request/client/background sender를 추가하고, portal에는 `POST /api/ingest/v1/heartbeat` controller/service/DTO를 추가한다. Portal은 project key와 metadata shape만 검증하고 accepted bucket timestamp는 `ingestBoundary`에 분리해 반환하되 저장/계산 side effect를 만들지 않는다.

## Boundaries & Constraints

**Always:** heartbeat는 control-plane/liveness 신호다. Valid heartbeat response는 `ingestBoundary.lastAcceptedBucketAt`와 `statusSource=accepted_bucket`을 통해 accepted bucket 기준 metric freshness를 분리한다. Heartbeat는 starter/application process liveness, portal reachability, project key validity, metadata validity의 source로 해석할 수 있다. Starter sender는 bounded timeout client와 background fail-open path를 사용한다. Raw project key는 response, error model, result `toString`, log에 노출하지 않는다.

**Ask First:** heartbeat telemetry persistence, disabled project `403`, heartbeat-driven catalog upsert, heartbeat UI/read model surface가 필요해지면 중단하고 확인한다.

**Never:** LifecycleStateService, dashboard read model/API, snapshot/event persistence, operational event history, accepted_metric_buckets heartbeat 저장, catalog upsert, heartbeat만으로 accepted bucket freshness 또는 metric state 판단, UI, p95/p99/rule/endpoint priority 계산을 만들지 않는다.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Valid heartbeat | Valid `X-OBS-Project-Key`, schema `1.0`, heartbeat fields, application metadata | `200 OK`, project id, server time, supported schema versions, metadata/heartbeat received status, accepted bucket boundary field | No bucket insert, no catalog upsert, no state/read-model side effect |
| Missing/invalid key | Header absent or verification fails | `401 Unauthorized` generic response | Raw key value absent from response/result/loggable strings |
| Invalid body | Unsupported schema or malformed application/heartbeat shape | `400 Bad Request` with field-level validation errors | No catalog lookup beyond auth, no bucket timestamp query |
| Existing accepted bucket | Matching application catalog row has accepted buckets | Response includes last accepted bucket timestamp under `ingestBoundary.lastAcceptedBucketAt` | Timestamp is not used as heartbeat status |

</frozen-after-approval>

## Code Map

- `observability-spring-boot-starter/src/main/java/com/observation/starter/client/PortalMetricBucketClient.java` -- existing bucket data-plane client boundary to keep separate from heartbeat.
- `observability-spring-boot-starter/src/main/java/com/observation/starter/config/MetricDrainProperties.java` -- existing starter application/environment/instance identity source to reuse for heartbeat metadata.
- `observability-spring-boot-starter/src/main/java/com/observation/starter/config/MetricDrainAutoConfiguration.java` -- current runtime drain wiring; heartbeat should use separate auto-configuration.
- `observability-spring-boot-starter/src/main/java/com/observation/starter/service/MetricBucketFlushWorker.java` -- background/fail-open worker pattern for starter-side non-blocking behavior.
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/controller/IngestController.java` -- bucket ingest controller kept separate from heartbeat controller.
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestAcceptanceService.java` -- validation vocabulary reference; heartbeat must not call bucket acceptance.
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/ProjectKeyVerificationService.java` -- project key verification boundary to reuse.
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java` -- Story 4.1 last accepted bucket timestamp lookup to reuse only after metadata validation.
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ApplicationRepository.java` -- read-only application lookup to avoid heartbeat catalog upsert.

## Tasks & Acceptance

**Execution:**
- [x] `observability-spring-boot-starter` heartbeat model/client/service/config -- add request shape, bounded JDK client, fail-open background sender, auto-configuration, and tests.
- [x] `observability-portal` heartbeat model/dto/service/controller -- add request validation, response contract, project key reuse, accepted bucket boundary lookup, and tests.
- [x] `implementation-artifacts/spec-complete-starter-heartbeat.md` -- record final file list, verification, and done status.

**Acceptance Criteria:**
- Given valid project key and valid metadata, when `POST /api/ingest/v1/heartbeat` is called, then portal returns `200 OK` and response separates `heartbeatStatus` from `ingestBoundary.statusSource=accepted_bucket`.
- Given missing or invalid project key, when heartbeat is called, then portal returns `401` without exposing raw key material.
- Given invalid schema or application metadata, when heartbeat is called, then portal returns `400` and performs no bucket/catalog write.
- Given starter heartbeat is enabled, when sender starts, then network work runs on a background daemon path and failures do not escape to host startup/request callers.
- Given heartbeat succeeds or fails, no accepted bucket insert, dashboard snapshot/event/read-model/state calculation, catalog upsert, or p95/p99/rule/priority calculation is added.

## Verification

**Commands:**
- `./gradlew :observability-spring-boot-starter:test` -- passed.
- `./gradlew :observability-portal:test` -- passed.
- `./gradlew test` -- passed.
- `git diff --check` -- passed.

## Dev Agent Record

### Completion Notes

- Starter heartbeat request/client/service/sender를 bucket flush path와 별도 타입으로 추가했다.
- Starter default HTTP client는 JDK HttpClient와 bounded connect/request timeout을 사용하며 raw project key를 exception message에 넣지 않는다.
- Heartbeat background sender는 daemon thread에서 실행되고 client failure를 `sendOnce=false`로 격리한다.
- Portal heartbeat endpoint는 `POST /api/ingest/v1/heartbeat`로 분리했고 project key verification을 재사용한다.
- Portal service는 request shape 검증 후 기존 application catalog row가 있을 때만 마지막 accepted bucket timestamp를 조회한다.
- Heartbeat path는 accepted bucket insert, catalog upsert, snapshot/event/state calculation, dashboard surface를 만들지 않는다.
- 후속 state/read-model 구현은 heartbeat를 metric freshness source가 아니라 starter connection/liveness 축으로만 사용할 수 있다.

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
