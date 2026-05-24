---
title: 'heartbeat-telemetry-persistence'
type: 'feature'
created: '2026-05-24'
status: 'done'
baseline_commit: 'fd1ce5fe40d0f87441bc28f6ed87c79072abb07a'
context:
  - '{project-root}/AGENTS.md'
  - '{project-root}/_bmad/custom/project-context.md'
  - '{project-root}/implementation-artifacts/epic-4-context.md'
  - '{project-root}/implementation-artifacts/spec-complete-starter-heartbeat.md'
  - '{project-root}/planning-artifacts/contracts/state-semantics.md'
  - '{project-root}/planning-artifacts/contracts/time-buckets.md'
  - '{project-root}/planning-artifacts/contracts/starter-failure-semantics.md'
  - '{project-root}/planning-artifacts/contracts/ingest-envelope.md'
  - '{project-root}/planning-artifacts/api-surface.md'
  - '{project-root}/planning-artifacts/architecture.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `POST /api/ingest/v1/heartbeat`는 project key와 metadata를 검증해 response를 반환하지만, 후속 dashboard/state service가 "최근 starter heartbeat가 있었는지"를 조회할 control-plane persistence가 없다.

**Approach:** portal에 별도 heartbeat telemetry table과 JPA repository를 추가하고, valid heartbeat만 `project_id + application_name + environment + instance_name` latest row로 insert/update한다. Response의 `heartbeatStatus`와 `ingestBoundary.statusSource=accepted_bucket` 분리는 유지한다.

## Boundaries & Constraints

**Always:** heartbeat persistence는 starter/application liveness용 lightweight control-plane 저장소다. 저장 key는 `project_id + application_name + environment + instance_name`이며 저장값은 starter version, sent/received time, sequence, interval, metadata/heartbeat status 정도로 제한한다. raw project key는 DB row, response, loggable result, exception message에 남기지 않는다. 첫 accepted bucket이 catalog upsert source라는 기존 경계를 유지한다.

**Ask First:** `applications`/`application_instances` FK를 heartbeat 저장 필수 조건으로 만들거나 heartbeat로 catalog row를 생성해야 할 때, disabled project `403` 처리를 새로 열어야 할 때, dashboard/read-model/API surface를 이번 범위에 포함해야 할 때는 중단하고 확인한다.

**Never:** heartbeat를 `accepted_metric_buckets`에 저장하지 않는다. heartbeat 수신으로 dashboard snapshot, operational event, read model, `LifecycleStateService`, p95/p99/rule/endpoint priority 계산을 만들거나 호출하지 않는다. heartbeat는 accepted bucket freshness/state metric source가 아니며 application/instance catalog upsert source도 아니다. starter sender는 변경하지 않는다.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Valid first heartbeat | active project key, valid schema/application/heartbeat metadata, no existing heartbeat row | `200 OK`; separate telemetry row inserted; `heartbeatStatus=received`; `ingestBoundary.statusSource=accepted_bucket` | No accepted bucket insert, no catalog upsert, no state/read-model side effect |
| Valid repeat heartbeat | same project/application/environment/instance has existing telemetry row | latest row is updated with new sent/received time, sequence, interval, starter version/status; MVP latest 기준은 server `receivedAt` wins | Unique key conflict must not create duplicate latest rows |
| Missing/invalid project key | absent or unverifiable `X-OBS-Project-Key` | `401 Unauthorized`; telemetry store is not called | raw key not exposed in response/result/error text |
| Invalid schema/application/heartbeat metadata | verified project but unsupported schema or invalid metadata | `400 Bad Request`; telemetry store and bucket timestamp lookup are not called | validation errors stay field-level and do not persist partial heartbeat |

</frozen-after-approval>

## Code Map

- `observability-portal/src/main/resources/db/migration/V005__create_starter_heartbeat_telemetry.sql` -- new table, unique latest-row key, Korean comments, no raw key columns.
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/entity/StarterHeartbeatTelemetryEntity.java` -- JPA persistence model for heartbeat latest telemetry.
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/repository/StarterHeartbeatTelemetryJpaRepository.java` -- Spring Data lookup by heartbeat identity and optional latest query.
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/repository/StarterHeartbeatTelemetryRepository.java` -- small repository facade that upserts latest telemetry without catalog writes.
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/model/StarterHeartbeatTelemetryCommand.java` -- validated service-to-repository command.
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestHeartbeatService.java` -- after auth and shape validation, persist heartbeat then keep existing accepted bucket boundary lookup.
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/service/IngestHeartbeatServiceTest.java` -- service boundary and side-effect tests.
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/repository/StarterHeartbeatTelemetryRepositoryIntegrationTest.java` -- migration/JPA insert-update/latest-query behavior and no accepted bucket/catalog side effects.
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/repository/CatalogSchemaMigrationIntegrationTest.java` -- migration count, table/constraint/comment checks.

## Tasks & Acceptance

**Execution:**
- [x] `V005__create_starter_heartbeat_telemetry.sql` -- create `starter_heartbeat_telemetry` with unique identity and supporting indexes -- keep heartbeat separate from metric buckets and catalog.
- [x] `domain/ingest/entity`, `domain/ingest/model`, `domain/ingest/repository` -- add JPA entity, command, Spring Data repository, and facade upsert/latest methods -- match MVC repository standard.
- [x] `IngestHeartbeatService.java` -- inject telemetry repository and call it only after project key and request validation pass -- prevent invalid heartbeat persistence.
- [x] `IngestHeartbeatServiceTest.java` and `IngestHeartbeatControllerTest.java` -- update constructor expectations and verify valid/invalid/unauthorized side effects -- preserve response contract.
- [x] Repository/schema integration tests -- verify insert, update, latest lookup, table comments, and zero rows in `accepted_metric_buckets`, `applications`, `application_instances` for heartbeat-only paths.

**Acceptance Criteria:**
- Given a valid heartbeat, when the endpoint service receives it, then a separate heartbeat telemetry row is inserted or updated for the same project/application/environment/instance identity.
- Given missing or invalid project key, when heartbeat is received, then response remains `401` and heartbeat telemetry persistence is not invoked.
- Given invalid schema/application/heartbeat metadata, when heartbeat is received, then response remains `400` and heartbeat telemetry persistence is not invoked.
- Given a valid heartbeat, when persistence completes, then `accepted_metric_buckets` receives no row and `ApplicationCatalogRepository.getOrCreate` is not called.
- Given heartbeat persistence is implemented, when response is built, then `heartbeatStatus` stays separate from `ingestBoundary.statusSource=accepted_bucket` and accepted bucket timestamp lookup still uses `MetricBucketRepository.findLatestBucketEndUtcByApplicationId`.

## Spec Change Log

## Design Notes

Use application/instance names as telemetry identity instead of required catalog FKs. This preserves the contract that heartbeat can prove starter connectivity before the first accepted bucket creates catalog rows. Optional catalog references can be revisited later, but this story should not require or create them.

MVP latest semantics are intentionally simple: the last valid heartbeat received by the portal overwrites the latest row, even if starter `sequence` or `sentAtUtc` is older than the previous value. Monotonic sequence rejection and out-of-order diagnosis belong with a later connection/state surface, not this persistence slice.

## Verification

**Commands:**
- `./gradlew :observability-portal:test` -- passed.
- `./gradlew test` -- passed.
- `git diff --check` -- passed.

## Suggested Review Order

**Entry Point**

- Valid heartbeat persists after auth/validation and keeps response axes separated.
  [IngestHeartbeatService.java:78](../observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestHeartbeatService.java#L78)

- Accepted bucket timestamp remains read-only and source-tagged.
  [IngestHeartbeatService.java:124](../observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestHeartbeatService.java#L124)

**Telemetry Storage**

- Schema creates a separate latest-row heartbeat identity store.
  [V005__create_starter_heartbeat_telemetry.sql:1](../observability-portal/src/main/resources/db/migration/V005__create_starter_heartbeat_telemetry.sql#L1)

- Native upsert uses the heartbeat identity unique constraint atomically.
  [StarterHeartbeatTelemetryJpaRepository.java:37](../observability-portal/src/main/java/com/observation/portal/domain/ingest/repository/StarterHeartbeatTelemetryJpaRepository.java#L37)

- Facade exposes upsert/latest lookup without catalog dependencies.
  [StarterHeartbeatTelemetryRepository.java:33](../observability-portal/src/main/java/com/observation/portal/domain/ingest/repository/StarterHeartbeatTelemetryRepository.java#L33)

**Validation Guardrails**

- Length and timestamp bounds reject DB-breaking metadata before persistence.
  [IngestHeartbeatService.java:34](../observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestHeartbeatService.java#L34)

- Invalid metadata length proves telemetry write is skipped.
  [IngestHeartbeatServiceTest.java:169](../observability-portal/src/test/java/com/observation/portal/domain/ingest/service/IngestHeartbeatServiceTest.java#L169)

- Out-of-range timestamp proves persistence range failures stay 400.
  [IngestHeartbeatServiceTest.java:227](../observability-portal/src/test/java/com/observation/portal/domain/ingest/service/IngestHeartbeatServiceTest.java#L227)

**Side-Effect Proof**

- Repository test locks `received_at wins` latest-row semantics.
  [StarterHeartbeatTelemetryRepositoryIntegrationTest.java:67](../observability-portal/src/test/java/com/observation/portal/domain/ingest/repository/StarterHeartbeatTelemetryRepositoryIntegrationTest.java#L67)

- Heartbeat-only persistence leaves bucket and catalog tables empty.
  [StarterHeartbeatTelemetryRepositoryIntegrationTest.java:143](../observability-portal/src/test/java/com/observation/portal/domain/ingest/repository/StarterHeartbeatTelemetryRepositoryIntegrationTest.java#L143)

- Migration test locks heartbeat constraints and Korean comments.
  [CatalogSchemaMigrationIntegrationTest.java:160](../observability-portal/src/test/java/com/observation/portal/domain/catalog/repository/CatalogSchemaMigrationIntegrationTest.java#L160)
