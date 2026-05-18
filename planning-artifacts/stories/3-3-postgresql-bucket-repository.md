---
artifactType: story
storyId: "3.3"
epic: "Epic 3. Portal Ingest Acceptance"
title: "PostgreSQL Bucket Repository"
architectureStyle: Traditional MVC
status: ready-for-dev
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
- p95/histogram merge
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
3. repository inserts a validated bucket with project, application, instance, schema version, idempotency key, payload hash, bucket boundary, counts, histogram JSON, latest runtime ratios, endpoint JSON, and accepted timestamp.
4. repository/catalog path creates or finds application and instance rows and updates last-seen timestamps.
5. `uk_buckets_project_idempotency_key` and `uk_buckets_instance_bucket_start` are enforced.
6. persisted endpoint data remains bounded JSON and does not introduce arbitrary query/explorer storage.
7. repository does not calculate p95, lifecycle state, insight rules, endpoint priority, or dashboard read model.
8. first successful ingest path can return `201 Created` with bucket id and accepted timestamp after repository insert.

## Suggested Tasks

1. `database-schema.md` accepted bucket DDL을 migration으로 옮긴다.
2. migration comment coverage test를 V003까지 확장한다.
3. accepted bucket persistence model을 추가한다.
4. catalog get-or-create service/repository method를 추가한다.
5. `MetricBucketRepository` insert/read/idempotency lookup method를 추가한다.
6. `IngestAcceptanceService` success path를 repository insert와 연결한다.
7. controller success response가 `api-surface.md` rough shape를 따르는지 확인한다.
8. Testcontainers repository integration test를 추가한다.
9. Docker 환경 실패 시 fallback evidence를 story Debug Log에 남긴다.

## Test Requirements

- `MetricBucketRepositoryIntegrationTest`
- migration table/column Korean comment coverage test
- unique idempotency constraint test
- unique instance bucket start constraint test
- catalog get-or-create and last-seen update test
- first successful ingest `201 Created` status mapping test
- `MvcLayerBoundaryTest`
- 권장 실행 명령: `./gradlew :observability-portal:test`

## Developer Guardrails

- dashboard snapshot/read model migration을 이 story에 추가하지 않는다.
- DB view/materialized view/stored procedure로 p95/state/rule/priority를 숨기지 않는다.
- raw path/query/custom metric/tag를 JSON storage에 우회 저장하지 않는다.
- V001/V002 catalog foundation을 되돌리거나 재작성하지 않는다.
- Testcontainers 실패가 Docker 환경 문제인지 코드 문제인지 구분해서 기록한다.

## Tasks/Subtasks

- [ ] V003 accepted bucket migration을 추가한다.
- [ ] migration comment/constraint/index tests를 확장한다.
- [ ] accepted bucket persistence model을 추가한다.
- [ ] catalog get-or-create path를 추가한다.
- [ ] `MetricBucketRepository`를 구현한다.
- [ ] service success path를 repository insert와 연결한다.
- [ ] controller `201 Created` mapping을 검증한다.
- [ ] Testcontainers repository integration test를 실행한다.

## Status

ready-for-dev
