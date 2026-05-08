---
artifactType: story
storyId: "1.4"
epic: "Epic 1. Architecture Foundation"
title: "Portal Physical Schema Foundation"
architectureStyle: Lightweight Hexagonal
status: draft-for-ir
date: 2026-05-08
---

# Story 1.4 - Portal Physical Schema Foundation

## User Story

구현자로서, portal persistence adapter가 Lightweight Hexagonal 경계를 지키며 시작할 수 있도록 PostgreSQL migration 기반과 catalog physical schema를 먼저 마련하고 싶다.

## Scope

이 story는 Epic 1의 foundation 작업이다. DB 전체를 한 번에 구현하지 않는다.

포함:

- portal module의 migration 도구 세팅
- local/test PostgreSQL runtime 기준 확정
- `projects`, `applications`, `application_instances` physical schema 구현
- table/column 한국어 `COMMENT ON` 추가
- catalog persistence adapter가 붙을 수 있는 package와 test 위치 확정

제외:

- `accepted_metric_buckets` 구현
- `dashboard_snapshots` 구현
- ingest idempotency conflict handling
- p95, lifecycle state, insight rule, endpoint priority 계산
- Redis, outbox, materialized view, Prometheus/scrape/query UI

## Source Artifacts

- `planning-artifacts/architecture.md`
- `planning-artifacts/architecture-implementation-supplement.md`
- `planning-artifacts/database-schema.md`
- `planning-artifacts/contracts/ingest-envelope.md`
- `planning-artifacts/contracts/read-model-contract.md`

## Implementation Notes

- UUID는 application-generated UUID로 만든다. PostgreSQL `pgcrypto` extension을 요구하지 않는다.
- project key 검증은 `key_prefix`로 project 후보를 조회한 뒤 `project_key_hash`에 저장된 BCrypt hash로 검증하는 경계로 둔다.
- raw project key는 DB에 저장하지 않는다.
- migration 파일은 Flyway 기준으로 시작한다.
- migration naming은 `V001__create_projects.sql`, `V002__create_applications_and_instances.sql`를 따른다.
- table과 column에는 모두 한국어 `COMMENT ON`을 추가한다.
- persistence adapter package는 `com.observation.portal.adapter.out.persistence.catalog` 아래에 둔다.
- core package는 persistence framework 타입을 참조하지 않는다.

## Acceptance Criteria

1. `projects` table이 `database-schema.md`의 physical DDL과 일치한다.
2. `applications` table이 `database-schema.md`의 physical DDL과 일치한다.
3. `application_instances` table이 `database-schema.md`의 physical DDL과 일치한다.
4. 세 table의 모든 column에 한국어 `COMMENT ON COLUMN`이 존재한다.
5. 세 table에 한국어 `COMMENT ON TABLE`이 존재한다.
6. `applications(project_id, name, environment)` unique constraint가 존재한다.
7. `application_instances(application_id, instance_name)` unique constraint가 존재한다.
8. `projects.key_prefix`와 `projects.project_key_hash` unique constraint가 존재한다.
9. ArchUnit/package boundary test는 portal domain/application이 persistence adapter를 참조하지 않음을 검증한다.
10. 이 story에서는 `accepted_metric_buckets`, `dashboard_snapshots`, p95/state/rule 계산을 구현하지 않는다.

## Suggested Tasks

1. portal module에 migration 도구를 추가한다.
2. local/test PostgreSQL 실행 방식을 정한다.
3. `V001__create_projects.sql`을 작성한다.
4. `V002__create_applications_and_instances.sql`을 작성한다.
5. migration 실행 테스트를 추가한다.
6. catalog persistence adapter package skeleton을 만든다.
7. ArchUnit/package boundary test에 persistence boundary 규칙을 추가한다.

## Test Requirements

- migration clean database 적용 테스트
- unique constraint 검증 테스트
- foreign key 검증 테스트
- table/column comment 존재 검증 테스트
- package boundary test

## Developer Guardrails

- DB trigger, view, materialized view로 lifecycle state를 계산하지 않는다.
- catalog schema 안에 endpoint metric, bucket payload, dashboard read model을 섞지 않는다.
- public project onboarding API를 이 story에 포함하지 않는다.
- project seed는 local/demo story에서 다룬다.
- Simple MVC나 service/repository layered architecture로 package를 되돌리지 않는다.

