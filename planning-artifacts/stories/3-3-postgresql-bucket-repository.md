---
artifactType: story
storyId: "3.3"
epic: "Epic 3. Portal Ingest Acceptance"
title: "PostgreSQL Bucket Repository"
architectureStyle: Traditional MVC
status: done
date: 2026-05-18
---

# Story 3.3 - PostgreSQL Bucket Repository

## User Story

구현자로서, portal이 검증을 통과한 30초 ingest bucket을 PostgreSQL `accepted_metric_buckets`에 저장하고 이후 read model 계산의 입력으로 사용할 수 있길 원한다.

## Scope

이 story는 accepted bucket persistence를 닫는다.

포함:

- `V003__create_accepted_metric_buckets.sql`
- Korean table/column comments
- idempotency and bucket identity unique constraints
- `MetricBucketRepository`
- application/application instance catalog get-or-create
- `accepted_at`, `payload_hash`, latest runtime ratio persistence
- endpoint bucket JSON persistence
- repository integration test with Testcontainers
- first successful ingest persistence path

제외:

- duplicate success/conflict final handling
- `dashboard_snapshots` migration/repository
- dashboard read model refresh
- starter canonical percentile display policy와 bucket distribution merge
- lifecycle state, insight rule, endpoint priority 계산
- operational event table/repository
- Post-MVP aggregate max/avg/sampleCount columns
- arbitrary endpoint query/explorer API

## Source Artifacts

- `planning-artifacts/sprint-plan.md`
- `planning-artifacts/database-schema.md`
- `planning-artifacts/api-surface.md`
- `planning-artifacts/contracts/ingest-envelope.md`
- `planning-artifacts/contracts/metric-taxonomy.md`
- `planning-artifacts/contracts/time-buckets.md`
- `planning-artifacts/stories/3-1-project-key-verification-service.md`
- `planning-artifacts/stories/3-2-ingest-acceptance-service.md`
- `observability-portal/src/main/resources/db/migration/V001__create_projects.sql`
- `observability-portal/src/main/resources/db/migration/V002__create_applications_and_instances.sql`

## Dependencies

- Story 3.1 provides verified project context.
- Story 3.2 provides validated ingest candidate and payload hash.
- Existing catalog migrations V001 and V002 are already present.
- Story 3.4 uses repository idempotency lookup and unique constraint behavior.

## Implementation Notes

- Migration name follows existing Flyway sequence: `V003__create_accepted_metric_buckets.sql`.
- Repository 구현 표준은 PostgreSQL 위의 Spring Data JPA/Jakarta Persistence + Hibernate다.
- Flyway SQL migration이 schema source of truth다. 이 story에서 Hibernate DDL auto create/update를 사용하지 않는다.
- JPA entity와 Spring Data repository는 feature-first package 안에서 실제 구현 기준에 맞춰 둔다.
- Service는 빠른 MVC 구현을 위해 필요하면 Spring Data repository와 JPA entity를 직접 사용할 수 있다.
- JPA entity는 persistence model이다. controller response DTO, public API surface, service result/external return model로 직접 반환하지 않는다.
- UUID is application-generated; do not add PostgreSQL extension dependency for UUID generation.
- `duration_buckets_json` and `endpoints_json` store bounded validated JSON. DB does not validate histogram monotonicity; service validation owns that.
- `cpu_usage_ratio`, `heap_used_ratio`, `datasource_pool_usage_ratio` store schemaVersion `1.0` latest sample values only.
- Do not add Post-MVP max/avg/sampleCount columns in this story.
- Application/application instance rows may be created when a valid bucket arrives, but this is catalog identity maintenance, not public onboarding.
- Repository returns persisted bucket id and accepted timestamp for controller response mapping.
- If Testcontainers cannot access Docker, record the environment failure in the story Debug Log and still run non-container unit/static tests.

## Acceptance Criteria

1. `V003__create_accepted_metric_buckets.sql` creates the table, constraints, indexes, and Korean comments defined in `planning-artifacts/database-schema.md`.
2. migration does not create `dashboard_snapshots`, `operational_events`, read model views, triggers, or stored procedures.
3. repository는 Spring Data JPA/Jakarta Persistence + Hibernate로 validated bucket의 project, application, instance, schema version, idempotency key, payload hash, bucket boundary, counts, histogram JSON, latest runtime ratios, endpoint JSON, accepted timestamp를 저장한다.
4. repository/catalog path creates or finds application and instance rows and updates last-seen timestamps.
5. `uk_buckets_project_idempotency_key` and `uk_buckets_instance_bucket_start` are enforced.
6. persisted endpoint data remains bounded JSON and does not introduce arbitrary query/explorer storage.
7. repository does not calculate p95, lifecycle state, insight rules, endpoint priority, or dashboard read model.
8. JPA entities do not leak into controller DTOs, public API surface, service results, or external return models.
9. first successful ingest path can return `201 Created` with bucket id and accepted timestamp after repository insert.

## Suggested Tasks

1. `database-schema.md` accepted bucket DDL을 migration으로 옮긴다.
2. migration comment coverage test를 V003까지 확장한다.
3. accepted bucket persistence model을 추가한다.
4. accepted bucket JPA entity를 feature-first package 안의 persistence 책임 위치에 추가한다.
5. Spring Data repository를 feature repository 위치에 추가한다.
6. catalog get-or-create service/repository method를 JPA 기반으로 추가한다.
7. `MetricBucketRepository` insert/read/idempotency lookup method를 추가하되 entity를 service result/external return model로 노출하지 않는다.
8. `IngestAcceptanceService` success path를 repository insert와 연결한다.
9. controller success response가 `api-surface.md` rough shape를 따르는지 확인한다.
10. Testcontainers repository integration test를 추가한다.
11. Docker 환경 실패 시 fallback evidence를 story Debug Log에 남긴다.

## Test Requirements

- `MetricBucketRepositoryIntegrationTest`
- migration table/column Korean comment coverage test
- Flyway-managed PostgreSQL schema 기준 JPA mapping 검증
- unique idempotency constraint test
- unique instance bucket start constraint test
- catalog get-or-create and last-seen update test
- first successful ingest `201 Created` status mapping test
- `MvcLayerBoundaryTest`
- 권장 실행 명령: `./gradlew :observability-portal:test`

## Developer Guardrails

- dashboard snapshot/read model migration을 이 story에 추가하지 않는다.
- DB view/materialized view/stored procedure로 p95/state/rule/priority를 숨기지 않는다.
- Hibernate DDL auto create/update로 schema를 만들거나 갱신하지 않는다.
- JPA entity를 controller response DTO, request DTO, public API surface, service result/external return model로 노출하지 않는다.
- raw path/query/custom metric/tag를 JSON storage에 우회 저장하지 않는다.
- V001/V002 catalog foundation을 되돌리거나 재작성하지 않는다.
- Testcontainers 실패가 Docker 환경 문제인지 코드 문제인지 구분해서 기록한다.

## Tasks/Subtasks

- [x] V003 accepted bucket migration을 추가한다.
- [x] migration comment/constraint/index tests를 확장한다.
- [x] accepted bucket persistence model을 추가한다.
- [x] accepted bucket JPA entity와 Spring Data repository interface를 추가한다.
- [x] catalog get-or-create path를 JPA 기반으로 추가한다.
- [x] `MetricBucketRepository`를 JPA 기반으로 구현한다.
- [x] service success path를 repository insert와 연결한다.
- [x] controller `201 Created` mapping을 검증한다.
- [x] Testcontainers repository integration test를 실행한다.

## Dev Agent Record

### Implementation Plan

- `database-schema.md`의 `accepted_metric_buckets` DDL만 Flyway V003 migration으로 옮기고, dashboard/read-model/operational event schema는 추가하지 않는다.
- accepted bucket 저장 입력은 `AcceptedMetricBucketWriteCommand`, 저장 결과는 `AcceptedMetricBucketReceipt`로 분리해 JPA entity가 service result/controller DTO로 노출되지 않게 한다.
- catalog get-or-create는 `applications`와 `application_instances` JPA repository를 통해 수행하고, successful ingest 수용 시각으로 first/last seen을 관리한다.
- `MetricBucketRepository`는 Spring Data JPA repository와 catalog repository를 감싼 repository facade로 두고, duration/endpoints는 bounded JSON으로 저장한다.
- `IngestAcceptanceService` success path에서 payload hash를 계산하고 repository insert를 호출한 뒤 receipt를 반환한다.
- `IngestController`는 first successful ingest를 `201 Created`와 `{ status, duplicate, bucketId, acceptedAt }` response DTO로 매핑한다.

### Debug Log

- 2026-05-20T09:09:31+0900: `implementation-artifacts/sprint-status.yaml`에서 Story 3.3 상태를 `in-progress`로 전환했다.
- 2026-05-20T09:11:46+0900: RED 단계로 확장한 `CatalogSchemaMigrationIntegrationTest` 실행이 Testcontainers 초기화에서 실패했다. 원인은 `Could not find a valid Docker environment`, `/var/run/docker.sock` 부재였고 코드 assertion 실패와 구분했다.
- `V003__create_accepted_metric_buckets.sql`에 accepted bucket table, FK/unique/check constraints, indexes, Korean comments를 추가했다. dashboard_snapshots, operational_events, view, trigger, stored procedure는 추가하지 않았다.
- `CatalogSchemaMigrationIntegrationTest`를 V003 migration count, excluded table absence, accepted bucket Korean comments, unique/check constraints, index coverage까지 확장했다.
- `AcceptedMetricBucketWriteCommand`/`AcceptedMetricBucketReceipt`, accepted bucket JPA entity, Spring Data JPA repository, catalog application/instance entity와 repository, `ApplicationCatalogRepository`, `MetricBucketRepository`를 추가했다.
- `MetricBucketRepositoryIntegrationTest` 최초 실행에서 Docker가 아니라 Spring context의 `ObjectMapper` bean 누락으로 실패했고, `PortalJsonConfiguration`을 추가해 JSON persistence/hash 직렬화 구성을 명시했다.
- `IngestPayloadHasher`를 추가해 validated request model 기준 SHA-256 payload hash를 계산하고, unknown field가 hash/persistence 후보에 반영되지 않음을 테스트했다.
- `IngestAcceptanceService` success path를 repository insert와 연결하고, `IngestAcceptanceResult`에 entity가 아닌 receipt를 추가했다.
- `IngestController`, `IngestAcceptedResponse`, `IngestErrorResponse`를 추가하고 first successful ingest가 `201 Created`와 `duplicate=false` body로 매핑됨을 검증했다.
- `./gradlew :observability-portal:test --tests com.observation.portal.domain.bucket.model.AcceptedMetricBucketWriteCommandTest` 통과.
- `./gradlew :observability-portal:test --tests com.observation.portal.domain.ingest.service.IngestPayloadHasherTest --tests com.observation.portal.domain.ingest.service.IngestAcceptanceServiceTest --tests com.observation.portal.domain.ingest.service.IngestEnvelopeRequestJsonTest` 통과.
- `./gradlew :observability-portal:test --tests com.observation.portal.domain.ingest.controller.IngestControllerTest` 통과.
- `./gradlew :observability-portal:test --tests com.observation.portal.domain.bucket.repository.MetricBucketRepositoryIntegrationTest` 통과.
- `./gradlew :observability-portal:test --tests com.observation.portal.domain.catalog.repository.CatalogSchemaMigrationIntegrationTest` 통과.
- `./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest` 통과.
- `./gradlew :observability-portal:test` 통과.
- `./gradlew test` 통과.
- `git diff --check` 통과.
- 2026-05-20T14:10:19+0900: MVP duplicate idempotency policy review에서 blocker가 없음을 확인하고 Story 3.3을 `done`으로 전환했다.

### Completion Notes

- Flyway V003가 `accepted_metric_buckets`만 생성하며, accepted bucket constraints/indexes/comments를 PostgreSQL Testcontainers 기반으로 검증한다.
- JPA persistence는 accepted bucket, catalog application, catalog instance entity/repository를 사용하지만 controller DTO/public API/service result에는 JPA entity를 반환하지 않는다.
- `MetricBucketRepository`는 project/application/instance/schema/idempotency/payload hash/bucket boundary/counts/histogram JSON/latest runtime ratios/endpoint JSON/accepted timestamp를 저장한다.
- catalog get-or-create path는 valid bucket 수용 시 application/application instance row를 생성하거나 찾고 last-seen timestamps를 갱신한다.
- first successful ingest path는 validation 이후 payload hash를 계산하고 repository insert receipt를 `201 Created` response로 매핑한다.
- duplicate success/conflict final handling, dashboard snapshot/read model, p95/state/rule/priority 계산은 구현하지 않았다.

### File List

- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/stories/3-3-postgresql-bucket-repository.md`
- `observability-portal/build.gradle`
- `observability-portal/src/main/resources/db/migration/V003__create_accepted_metric_buckets.sql`
- `observability-portal/src/main/java/com/observation/portal/config/PortalJsonConfiguration.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/entity/AcceptedMetricBucketEntity.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/model/AcceptedMetricBucketReceipt.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/model/AcceptedMetricBucketWriteCommand.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/AcceptedMetricBucketJpaRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/entity/ApplicationEntity.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/entity/ApplicationInstanceEntity.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/model/ApplicationCatalogEntry.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ApplicationCatalogRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ApplicationInstanceRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ApplicationRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/controller/IngestController.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/dto/IngestAcceptedResponse.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/dto/IngestErrorResponse.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestAcceptanceResult.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestAcceptanceService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestPayloadHasher.java`
- `observability-portal/src/test/java/com/observation/portal/domain/bucket/model/AcceptedMetricBucketWriteCommandTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/bucket/repository/MetricBucketRepositoryIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/repository/CatalogSchemaMigrationIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/controller/IngestControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/service/IngestAcceptanceServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/service/IngestEnvelopeRequestJsonTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/service/IngestPayloadHasherTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/service/PortalIngestValidationFixture.java`

### Change Log

- 2026-05-20: Story 3.3 PostgreSQL accepted bucket repository 구현을 완료하고 V003 migration, JPA persistence, catalog get-or-create, first successful ingest persistence path, `201 Created` mapping, repository integration tests를 추가했다.
- 2026-05-20: MVP duplicate idempotency policy review 결과 Done을 막는 blocker가 없어 Story 3.3을 완료 처리했다.

## Status

done
